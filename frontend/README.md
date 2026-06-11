# jiuwen-agent-console

## 前提
node升级到22.12.0或24.0.0及以上版本

## 本地调试
### 1. 安装依赖
```bash
pnpm install
```

### 2. 配置代理
`.staging/proxy.json` 是 `ng serve` 使用的本地代理配置，已随代码提交，不需要手工创建。

默认配置把 `/v1`、`/v2` 转发到 `http://localhost`，也就是 Docker Compose 启动的 `studio-console` Nginx 网关。这里配置 `/v1`、`/v2` 是正确的，前端实际接口形态通常是 `/v1/{projectId}/agent-manager/...`、`/v1/{projectId}/agent-runtime/...`、`/v2/...`，生产 Nginx 会继续把这些路径分发到不同后端。

如果没有启动 Nginx 网关，而是直接在本机启动 Java 服务，需要维护一份按路径分流到 `31111`、`31113` 的本地 proxy 配置，不能简单把所有 `/v1`、`/v2` 都指向某一个端口。

### 3. 启动
```bash
pnpm start
```
