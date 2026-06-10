#!/bin/bash
set -e

SCRIPT_DIR=$(cd "$(dirname "$0")" && pwd)

detect_compose_cmd() {
    if docker compose version &> /dev/null; then
        COMPOSE_CMD="docker compose"
    elif command -v docker-compose &> /dev/null; then
        COMPOSE_CMD="docker-compose"
    else
        COMPOSE_CMD=""
    fi
}

detect_compose_cmd

RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m'

log_info()  { echo -e "${GREEN}[INFO]${NC}  $*"; }
log_warn()  { echo -e "${YELLOW}[WARN]${NC}  $*"; }
log_error() { echo -e "${RED}[ERROR]${NC} $*"; }

check_prerequisites() {
    log_info "Checking prerequisites..."
    local missing=0

    if ! command -v docker &> /dev/null; then
        log_error "docker is not installed"
        missing=1
    fi

    detect_compose_cmd
    if [ -z "$COMPOSE_CMD" ]; then
        log_error "docker compose or docker-compose is not installed"
        missing=1
    fi

    if [ ! -f "$SCRIPT_DIR/.env.server" ]; then
        log_error ".env.server not found in $SCRIPT_DIR"
        log_error "Please copy .env.server and configure it for your environment"
        missing=1
    fi

    if [ ! -f "$SCRIPT_DIR/config/nginx-docker.conf" ]; then
        log_error "nginx.conf not found at $SCRIPT_DIR/config/nginx-docker.conf"
        missing=1
    fi

    if [ ! -f "$SCRIPT_DIR/../init.sql" ]; then
        log_error "init.sql not found at $SCRIPT_DIR/../init.sql"
        missing=1
    fi

    if [ $missing -eq 1 ]; then
        exit 1
    fi

    log_info "All prerequisites satisfied"
}

pull_images() {
    log_info "Pulling Docker images..."
    cd "$SCRIPT_DIR"

    GHCR_USERNAME=$(grep -E "^GHCR_USERNAME=" .env.server 2>/dev/null | cut -d'=' -f2-)
    GHCR_TOKEN=$(grep -E "^GHCR_TOKEN=" .env.server 2>/dev/null | cut -d'=' -f2-)

    if [ -n "${GHCR_USERNAME:-}" ] && [ -n "${GHCR_TOKEN:-}" ]; then
        log_info "Logging in to GHCR..."
        echo "$GHCR_TOKEN" | docker login ghcr.io -u "$GHCR_USERNAME" --password-stdin || true
    fi

    $COMPOSE_CMD --env-file .env.server -f docker-compose-server.yml pull studio-console studio-manager studio-service studio-runtime
    log_info "Images pulled successfully"
}

start_services() {
    log_info "Starting services..."
    cd "$SCRIPT_DIR"

    $COMPOSE_CMD --env-file .env.server -f docker-compose-server.yml up -d

    log_info "============================================"
    log_info " Services started successfully!"
    log_info "============================================"
    log_info ""
    log_info "  Frontend : http://<server-ip>/openjiuwen/"
    log_info "  Manager  : http://<server-ip>:31111"
    log_info "  Service  : http://<server-ip>:31113"
    log_info "  Runtime  : http://<server-ip>:31014"
    log_info "  MinIO    : http://<server-ip>:9001"
    log_info ""
    log_info "  Stop     : $0 stop"
    log_info "  Logs     : $0 logs [service]"
    log_info "============================================"
}

stop_services() {
    log_info "Stopping services..."
    cd "$SCRIPT_DIR"
    $COMPOSE_CMD --env-file .env.server -f docker-compose-server.yml down
    log_info "Services stopped"
}

restart_services() {
    stop_services
    start_services
}

show_logs() {
    cd "$SCRIPT_DIR"
    $COMPOSE_CMD --env-file .env.server -f docker-compose-server.yml logs -f "${1:-}"
}

show_status() {
    cd "$SCRIPT_DIR"
    $COMPOSE_CMD --env-file .env.server -f docker-compose-server.yml ps
}

update_services() {
    log_info "Updating services..."
    pull_images
    cd "$SCRIPT_DIR"
    $COMPOSE_CMD --env-file .env.server -f docker-compose-server.yml up -d --remove-orphans
    log_info "Services updated successfully"
}

case "${1:-}" in
    start)
        check_prerequisites
        start_services
        ;;
    stop)
        stop_services
        ;;
    restart)
        check_prerequisites
        restart_services
        ;;
    pull)
        check_prerequisites
        pull_images
        ;;
    update)
        check_prerequisites
        update_services
        ;;
    logs)
        show_logs "${2:-}"
        ;;
    status)
        show_status
        ;;
    all)
        check_prerequisites
        pull_images
        start_services
        ;;
    *)
        echo "Usage: $0 {start|stop|restart|pull|update|logs [service]|status|all}"
        echo ""
        echo "  start   - Start all services"
        echo "  stop    - Stop all services"
        echo "  restart - Restart all services"
        echo "  pull    - Pull latest images"
        echo "  update  - Pull images and restart (zero-downtime update)"
        echo "  logs    - Tail service logs (optional: service name)"
        echo "  status  - Show service status"
        echo "  all     - Pull images and start services"
        exit 1
        ;;
esac
