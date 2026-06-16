#!/bin/bash
set -euo pipefail

SCRIPT_DIR=$(cd "$(dirname "$0")" && pwd)
COMPOSE_FILE="$SCRIPT_DIR/docker-compose-server.yml"
ENV_FILE="$SCRIPT_DIR/.env.server"
# project 名与 docker compose 默认值(目录名)保持一致,确保脚本能接管已存在的容器。
# 否则 stop/down 只作用于 "openjiuwen-server" project,管不到此前用裸 docker compose
# (默认 project=目录名,即 "compose")起的服务,出现 stop 后容器仍在运行的情况。
# 如需自定义,可通过环境变量 COMPOSE_PROJECT_NAME 覆盖。
PROJECT_NAME="${COMPOSE_PROJECT_NAME:-$(basename "$SCRIPT_DIR")}"

INFRA_SERVICES=(mysql redis minio)
APP_SERVICES=(studio-manager studio-service studio-runtime studio-console)
RUNNING_SERVICES=("${INFRA_SERVICES[@]}" "${APP_SERVICES[@]}")

RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m'

log_info()  { echo -e "${GREEN}[INFO]${NC}  $*"; }
log_warn()  { echo -e "${YELLOW}[WARN]${NC}  $*"; }
log_error() { echo -e "${RED}[ERROR]${NC} $*"; }

detect_compose_cmd() {
    if docker compose version &> /dev/null; then
        COMPOSE_CMD=(docker compose)
    elif command -v docker-compose &> /dev/null; then
        COMPOSE_CMD=(docker-compose)
    else
        COMPOSE_CMD=()
    fi
}

get_env_value() {
    local key="$1"
    local default_value="${2:-}"
    local value
    value=$(grep -E "^${key}=" "$ENV_FILE" 2>/dev/null | tail -n 1 | cut -d'=' -f2- || true)
    value=${value%$'\r'}
    if [ -n "$value" ]; then
        printf "%s" "$value"
    else
        printf "%s" "$default_value"
    fi
}

require_env_value() {
    local key="$1"
    local value
    value=$(get_env_value "$key" "")
    if [ -z "$value" ]; then
        log_error "$key is required"
        exit 1
    fi
}

prepare_compose_env() {
    local image_source image_tag ghcr_repo dockerhub_repo
    image_source=$(get_env_value IMAGE_SOURCE ghcr)
    image_tag=$(get_env_value IMAGE_TAG latest)
    ghcr_repo=$(get_env_value GHCR_IMAGE_REPOSITORY "")
    dockerhub_repo=$(get_env_value DOCKERHUB_IMAGE_REPOSITORY "")

    case "$image_source" in
        ghcr|github)
            require_env_value GHCR_IMAGE_REPOSITORY
            export STUDIO_CONSOLE_IMAGE="${ghcr_repo}/studio-console:${image_tag}"
            export STUDIO_MANAGER_IMAGE="${ghcr_repo}/studio-manager:${image_tag}"
            export STUDIO_SERVICE_IMAGE="${ghcr_repo}/studio-service:${image_tag}"
            export STUDIO_RUNTIME_IMAGE="${ghcr_repo}/studio-runtime:${image_tag}"
            ;;
        dockerhub|docker)
            require_env_value DOCKERHUB_IMAGE_REPOSITORY
            export STUDIO_CONSOLE_IMAGE="${dockerhub_repo}:studio-console-${image_tag}"
            export STUDIO_MANAGER_IMAGE="${dockerhub_repo}:studio-manager-${image_tag}"
            export STUDIO_SERVICE_IMAGE="${dockerhub_repo}:studio-service-${image_tag}"
            export STUDIO_RUNTIME_IMAGE="${dockerhub_repo}:studio-runtime-${image_tag}"
            ;;
        custom)
            require_env_value STUDIO_CONSOLE_IMAGE
            require_env_value STUDIO_MANAGER_IMAGE
            require_env_value STUDIO_SERVICE_IMAGE
            require_env_value STUDIO_RUNTIME_IMAGE
            export STUDIO_CONSOLE_IMAGE="$(get_env_value STUDIO_CONSOLE_IMAGE "")"
            export STUDIO_MANAGER_IMAGE="$(get_env_value STUDIO_MANAGER_IMAGE "")"
            export STUDIO_SERVICE_IMAGE="$(get_env_value STUDIO_SERVICE_IMAGE "")"
            export STUDIO_RUNTIME_IMAGE="$(get_env_value STUDIO_RUNTIME_IMAGE "")"
            ;;
        *)
            log_error "Unsupported IMAGE_SOURCE '$image_source'. Use ghcr, dockerhub, or custom."
            exit 1
            ;;
    esac
}

compose() {
    prepare_compose_env
    "${COMPOSE_CMD[@]}" --project-name "$PROJECT_NAME" --env-file "$ENV_FILE" -f "$COMPOSE_FILE" "$@"
}

check_docker_prerequisites() {
    log_info "Checking Docker prerequisites..."
    local missing=0

    if ! command -v docker &> /dev/null; then
        log_error "docker is not installed"
        missing=1
    fi

    detect_compose_cmd
    if [ ${#COMPOSE_CMD[@]} -eq 0 ]; then
        log_error "docker compose or docker-compose is not installed"
        missing=1
    fi

    if [ ! -f "$ENV_FILE" ]; then
        log_error ".env.server not found in $SCRIPT_DIR"
        log_error "Please copy or create .env.server and configure it for your environment"
        missing=1
    fi

    if [ $missing -eq 1 ]; then
        exit 1
    fi

    log_info "Docker prerequisites satisfied"
}

check_runtime_prerequisites() {
    check_docker_prerequisites
    local missing=0

    if [ ! -f "$SCRIPT_DIR/config/nginx-docker.conf" ]; then
        log_error "nginx config not found at $SCRIPT_DIR/config/nginx-docker.conf"
        missing=1
    fi

    if [ ! -f "$SCRIPT_DIR/../init.sql" ]; then
        log_error "init.sql not found at $SCRIPT_DIR/../init.sql"
        missing=1
    fi

    if [ $missing -eq 1 ]; then
        exit 1
    fi

    log_info "Runtime prerequisites satisfied"
}

check_curl_prerequisite() {
    if ! command -v curl &> /dev/null; then
        log_error "curl is not installed"
        exit 1
    fi
}

check_verify_prerequisites() {
    check_docker_prerequisites
    check_curl_prerequisite

    log_info "Verification prerequisites satisfied"
}

login_registries() {
    local ghcr_username ghcr_token dockerhub_username dockerhub_token
    ghcr_username=$(get_env_value GHCR_USERNAME "")
    ghcr_token=$(get_env_value GHCR_TOKEN "")
    dockerhub_username=$(get_env_value DOCKERHUB_USERNAME "")
    dockerhub_token=$(get_env_value DOCKERHUB_TOKEN "")

    if [ -n "$ghcr_username" ] && [ -n "$ghcr_token" ]; then
        log_info "Logging in to GHCR..."
        echo "$ghcr_token" | docker login ghcr.io -u "$ghcr_username" --password-stdin
    fi

    if [ -n "$dockerhub_username" ] && [ -n "$dockerhub_token" ]; then
        log_info "Logging in to Docker Hub..."
        echo "$dockerhub_token" | docker login -u "$dockerhub_username" --password-stdin
    fi
}

pull_images() {
    log_info "Pulling Docker images..."
    cd "$SCRIPT_DIR"
    login_registries
    compose pull "$@"
    log_info "Images pulled successfully"
}

container_is_ready() {
    local service="$1"
    local container_id state exit_code

    container_id=$(compose ps -q "$service" 2>/dev/null || true)
    if [ -z "$container_id" ]; then
        return 1
    fi

    state=$(docker inspect --format '{{if .State.Health}}{{.State.Health.Status}}{{else}}{{.State.Status}}{{end}}' "$container_id" 2>/dev/null || true)
    exit_code=$(docker inspect --format '{{.State.ExitCode}}' "$container_id" 2>/dev/null || true)

    case "$state" in
        healthy|running)
            return 0
            ;;
        exited)
            [ "$exit_code" = "0" ]
            return
            ;;
        unhealthy)
            return 1
            ;;
        *)
            return 1
            ;;
    esac
}

wait_for_services() {
    local timeout="${WAIT_TIMEOUT_SECONDS:-300}"
    local interval="${WAIT_INTERVAL_SECONDS:-5}"
    local deadline=$((SECONDS + timeout))
    local services=("$@")

    log_info "Waiting for services to become healthy/running: ${services[*]}"
    while [ $SECONDS -lt $deadline ]; do
        local pending=()
        local service
        for service in "${services[@]}"; do
            if ! container_is_ready "$service"; then
                pending+=("$service")
            fi
        done

        if [ ${#pending[@]} -eq 0 ]; then
            log_info "All requested services are healthy/running"
            return 0
        fi

        log_warn "Still waiting for: ${pending[*]}"
        sleep "$interval"
    done

    log_error "Timed out waiting for services: ${services[*]}"
    compose ps
    return 1
}

wait_for_url() {
    local name="$1"
    local url="$2"
    local expected_codes="$3"
    local timeout="${WAIT_TIMEOUT_SECONDS:-300}"
    local interval="${WAIT_INTERVAL_SECONDS:-5}"
    local deadline=$((SECONDS + timeout))
    local code

    log_info "Checking $name: $url"
    while [ $SECONDS -lt $deadline ]; do
        code=$(curl -k -L -sS -o /dev/null -w "%{http_code}" --max-time 10 "$url" || true)
        if [[ " $expected_codes " == *" $code "* ]]; then
            log_info "$name is ready (HTTP $code)"
            return 0
        fi
        log_warn "$name is not ready yet (HTTP ${code:-000})"
        sleep "$interval"
    done

    log_error "$name did not become ready: $url"
    return 1
}

local_http_base_url() {
    local port
    port=$(get_env_value HTTP_PORT 80)
    if [ "$port" = "80" ]; then
        printf "http://localhost"
    else
        printf "http://localhost:%s" "$port"
    fi
}

verify_deployment() {
    local base_url service_port frontend_url nginx_health_url key_api_url
    base_url=$(local_http_base_url)
    service_port=$(get_env_value SERVICE_PORT 31113)
    frontend_url=$(get_env_value FRONTEND_URL "${base_url}/openjiuwen/")
    nginx_health_url=$(get_env_value NGINX_HEALTH_URL "${base_url}/actuator/health")
    key_api_url=$(get_env_value KEY_API_URL "http://localhost:${service_port}/v1/health")

    log_info "Running post-start HTTP verification..."
    wait_for_url "Frontend" "$frontend_url" "200 301 302"
    wait_for_url "Nginx health" "$nginx_health_url" "200"
    wait_for_url "Key API" "$key_api_url" "200"
}

init_database() {
    log_info "Initializing database and object storage..."
    compose up -d "${INFRA_SERVICES[@]}"
    wait_for_services "${INFRA_SERVICES[@]}"

    log_info "Executing docker/init.sql inside mysql..."
    compose exec -T mysql sh -c 'if [ -n "$MYSQL_ROOT_PASSWORD" ]; then mysql -uroot -p"$MYSQL_ROOT_PASSWORD" < /docker-entrypoint-initdb.d/init.sql; else mysql -uroot < /docker-entrypoint-initdb.d/init.sql; fi'

    log_info "Ensuring MinIO bucket exists..."
    compose run --rm minio-init
    log_info "Database and object storage initialized"
}

start_infra() {
    log_info "Starting infrastructure services..."
    cd "$SCRIPT_DIR"
    init_database
}

start_services() {
    log_info "Starting all services..."
    cd "$SCRIPT_DIR"
    init_database
    compose up -d "${APP_SERVICES[@]}"
    wait_for_services "${RUNNING_SERVICES[@]}"
    verify_deployment

    log_info "============================================"
    log_info " Services started and verified successfully!"
    log_info "============================================"
    log_info ""
    log_info "  Frontend : $(local_http_base_url)/openjiuwen/"
    log_info "  Health   : $(local_http_base_url)/actuator/health"
    log_info "  Key API  : $(get_env_value KEY_API_URL "http://localhost:$(get_env_value SERVICE_PORT 31113)/v1/health")"
    log_info "  Manager  : http://localhost:$(get_env_value MANAGER_PORT 31111)"
    log_info "  Service  : http://localhost:$(get_env_value SERVICE_PORT 31113)"
    log_info "  Runtime  : http://localhost:$(get_env_value RUNTIME_PORT 31014)"
    log_info "  MinIO    : http://localhost:$(get_env_value MINIO_CONSOLE_PORT 9001)"
    log_info ""
    log_info "  Stop     : $0 stop"
    log_info "  Logs     : $0 logs [service]"
    log_info "============================================"
}

stop_services() {
    log_info "Stopping services..."
    cd "$SCRIPT_DIR"
    # --remove-orphans: 顺带清理本 project 下可能残留的孤儿容器
    compose down --remove-orphans
    log_info "Services stopped"
}

# 彻底清理:删除容器、网络、卷(以及可选的镜像)。卷被删意味着数据库等持久化数据丢失,不可逆。
clean_all() {
    local mode="${1:-data}"
    local down_flags="-v --remove-orphans"

    if [ "$mode" = "images" ] || [ "$mode" = "all" ]; then
        down_flags="$down_flags --rmi all"
    elif [ "$mode" != "data" ]; then
        log_error "Unknown clean target '$mode'. Usage: $0 clean [data|images|all]"
        exit 1
    fi

    log_warn "============================================================"
    log_warn " 即将删除 project '$PROJECT_NAME' 的资源 (不可逆):"
    log_warn "   - 容器 / 网络 / 卷 (mysql_data, minio_data 等数据将丢失)"
    if [ "$mode" = "images" ] || [ "$mode" = "all" ]; then
        log_warn "   - 镜像 (下次部署需重新拉取)"
    else
        log_warn "   - 镜像默认保留"
    fi
    log_warn "============================================================"

    # 破坏性操作:默认要求交互确认;设置 FORCE_CLEAN=yes 可跳过(供脚本/CI 调用)
    if [ "${FORCE_CLEAN:-}" != "yes" ]; then
        local confirm=""
        if ! read -r -p "输入 YES 确认彻底清理: " confirm; then
            log_warn "非交互环境或读取失败,已取消。设置 FORCE_CLEAN=yes 可跳过确认。"
            return 0
        fi
        if [ "$confirm" != "YES" ]; then
            log_info "已取消,未删除任何内容"
            return 0
        fi
    fi

    log_info "Removing containers, networks and volumes..."
    cd "$SCRIPT_DIR"
    compose down $down_flags

    log_info "============================================================"
    log_info " 清理完成 (容器 + 网络 + 卷)"
    [ "$mode" = "images" ] || [ "$mode" = "all" ] && log_info " 镜像已一并删除"
    log_info " 重新部署: $0 start"
    log_info "============================================================"
}

restart_services() {
    stop_services
    start_services
}

show_logs() {
    cd "$SCRIPT_DIR"
    if [ -n "${1:-}" ]; then
        compose logs -f "$1"
    else
        compose logs -f
    fi
}

show_status() {
    cd "$SCRIPT_DIR"
    compose ps
}

update_services() {
    log_info "Updating services..."
    pull_images
    cd "$SCRIPT_DIR"
    init_database
    compose up -d --remove-orphans "${APP_SERVICES[@]}"
    wait_for_services "${RUNNING_SERVICES[@]}"
    verify_deployment
    log_info "Services updated and verified successfully"
}

print_usage() {
    echo "Usage: $0 {infra|init-db|start|stop|restart|pull|update|clean [data|images|all]|logs [service]|status|verify|all}"
    echo ""
    echo "  infra   - Start MySQL, Redis and MinIO, then run init-db"
    echo "  init-db - Initialize the database schema bootstrap and MinIO bucket"
    echo "  start   - Run init-db, start all services, and verify HTTP endpoints"
    echo "  stop    - Stop all services (keeps volumes/data)"
    echo "  clean   - Remove containers, networks and VOLUMES (data lost). Optional: clean images|all"
    echo "  restart - Restart all services and verify HTTP endpoints"
    echo "  pull    - Pull configured Docker images"
    echo "  update  - Pull images, restart services, and verify HTTP endpoints"
    echo "  logs    - Tail service logs (optional: service name)"
    echo "  status  - Show service status"
    echo "  verify  - Verify frontend, health endpoint, and key API"
    echo "  all     - Pull images, run init-db, start all services, and verify HTTP endpoints"
}

detect_compose_cmd

case "${1:-}" in
    infra)
        check_runtime_prerequisites
        start_infra
        ;;
    init-db)
        check_runtime_prerequisites
        init_database
        ;;
    start)
        check_runtime_prerequisites
        check_curl_prerequisite
        start_services
        ;;
    stop)
        check_docker_prerequisites
        stop_services
        ;;
    clean)
        check_docker_prerequisites
        clean_all "${2:-}"
        ;;
    restart)
        check_runtime_prerequisites
        check_curl_prerequisite
        restart_services
        ;;
    pull)
        check_docker_prerequisites
        shift
        pull_images "$@"
        ;;
    update)
        check_runtime_prerequisites
        check_curl_prerequisite
        update_services
        ;;
    logs)
        check_docker_prerequisites
        show_logs "${2:-}"
        ;;
    status)
        check_docker_prerequisites
        show_status
        ;;
    verify)
        check_verify_prerequisites
        verify_deployment
        ;;
    all)
        check_runtime_prerequisites
        check_curl_prerequisite
        pull_images
        start_services
        ;;
    *)
        print_usage
        exit 1
        ;;
esac
