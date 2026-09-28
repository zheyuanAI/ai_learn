# 本地依赖与可选 Open WebUI

`docker-compose.yml` 只负责 PostgreSQL、Redis 和 Mosquitto。阶段 8 的 Open WebUI 按已确认方案作为 Windows 本地 Python 服务运行，不依赖 Docker。

## 基础依赖

```powershell
cd deploy
docker compose up -d
```

## Windows Open WebUI

Open WebUI 属于可选 AI 能力；普通业务、综合看板以及 Core / IoT Facts 联调均不要求启动它。Core / IoT 的本地配对 HMAC 统一通过被 Git 忽略的 `deploy/local/runtime.env` 注入，配置方法见 [`../runtime/README.md`](../runtime/README.md)。

```powershell
cd deploy\openwebui
powershell -ExecutionPolicy Bypass -File .\install-windows.ps1
powershell -ExecutionPolicy Bypass -File .\start-windows.ps1
```

- 默认安装到 `%LOCALAPPDATA%\AiLearnWms\openwebui`，不会把 Python 依赖或运行数据写入仓库；
- `initialize-windows-config.ps1` 自动生成 Open WebUI 签名密钥和 WMS 工具服务密钥；脚本中保留的历史 Core / IoT 变量不再作为服务启动配置源；
- 用户只需在被 Git 忽略的 `windows.env` 中填写硅基流动 API Key；
- 首次登录后生成的 Open WebUI API Key 也写入 `windows.env`，仅供可选 AI 链路使用；
- 完整初始化、Knowledge 和 Tool Server 配置见 [openwebui/README.md](./openwebui/README.md)。

Open WebUI 只监听 `127.0.0.1:3000`。不得向其开放文件系统、数据库、Shell、代码执行、联网搜索、通用 HTTP 或社区工具。
