# 本地开发环境基线设计

## 目标

在开始功能设计与原型制作前，先统一自研项目的本地开发工具链、基础中间件、服务端口和运行边界，避免后续因环境差异反复调整。

本文只定义环境基线，不安装前后端依赖、不执行项目构建、不启动任何服务，也不进入具体功能或原型设计。

## 已确认的工具链

| 工具 | 版本或位置 | 使用方式 |
| --- | --- | --- |
| Java | Java 21，`D:\ruanjian\IntelliJ IDEA 2025.1\jbr` | 由 IntelliJ IDEA 指定 Project SDK 和 Maven Runner JRE |
| Maven | 3.9.1，`D:\ruanjian\apache-maven-3.9.1` | 由 IntelliJ IDEA 指定 Maven home |
| Node.js | 20，当前本机入口为 `D:\ruanjian\nvm\nodejs` | 用于自研项目前端开发 |

`local-dev` 中为参考工程保留的 Java、Node 22 等工具链配置不在本次调整范围内，不因自研项目使用 Node 20 而删除。

## 已确认的基础服务

一期本地开发只准备 PostgreSQL、Redis 和 Mosquitto。RabbitMQ、MinIO、pgvector 暂不作为本次环境准备的阻塞条件。

| 服务 | 版本 | 地址 | 运行方式 |
| --- | --- | --- | --- |
| PostgreSQL | 16 | `127.0.0.1:5433` | 使用现有本地实例，由用户手动管理 |
| Redis | Windows 3.0.504 | `127.0.0.1:6379` | 下载到项目内部，由用户手动启动 |
| Mosquitto | 2.1.2 x64 | `127.0.0.1:1883` | 下载到项目内部，由用户手动启动 |

Redis 与 Mosquitto 的本地配置仅允许监听回环地址，避免把无鉴权的开发服务暴露到局域网。数据目录、日志目录和下载的二进制文件不提交 Git。

## 运行目录设计

```text
ai_learn_developProject/
├─ runtime/
│  ├─ redis-3.0.504/
│  └─ mosquitto-2.1.2/
└─ deploy/
   └─ local/
      ├─ redis.conf
      └─ mosquitto.conf
```

- `runtime/` 保存本机可执行文件、运行数据和日志，通过 `.gitignore` 排除。
- `deploy/local/` 保存可复核的本地开发配置，纳入 Git。
- 现有 `deploy/docker-compose.yml` 暂时保留，作为未来可选运行方式；本地进程与 Compose 不得同时绑定同一端口。
- 下载完成后只提供可执行文件位置、配置位置和手动启动命令，不自动启动服务，不注册 Windows 服务。

## 后端服务端口

| 模块 | 端口 |
| --- | ---: |
| `platform-gateway` | 10001 |
| `platform-auth` | 10002 |
| `platform-core` | 10003 |
| `platform-iot` | 10004 |

各模块通过自身 `application.yml` 明确设置 `server.port`，避免同时使用 Spring Boot 默认端口 8080。Gateway 路由规则不在本次环境基线中定义，需等功能和接口边界确认后设计。

## 配置一致性

本次实施需要同步整理以下事实：

- 自研项目使用 Node 20；参考工程专用的 Node 22 配置继续保留。
- PostgreSQL 对自研项目统一使用 5433；原来描述自研项目为 5323 的文档需要修正。
- Redis 统一使用 6379，Mosquitto 统一使用 1883。
- 四个 Spring Boot 服务使用 10001 至 10004 的已确认端口。
- Compose 配置继续保留，但不是当前手动开发环境的默认启动入口。

## 端口冲突处理

环境盘点时，6379 和 1883 已存在监听进程。下载 Redis 与 Mosquitto 不受影响，但手动启动新实例前，用户需要先停止原占用进程，或明确选择继续复用原实例。

如果目标端口仍被占用，启动命令应直接失败并提示端口冲突，不自动终止任何已有进程。

## 验收标准

环境基线实施完成后，应满足：

1. Redis 3.0.504 和 Mosquitto 2.1.2 位于约定的项目内部目录。
2. 下载来源和文件校验信息被记录。
3. Redis 与 Mosquitto 均有只监听 `127.0.0.1` 的本地配置。
4. 用户可以根据文档中的单条命令手动启动每个服务。
5. 四个后端模块分别声明 10001、10002、10003、10004。
6. 项目文档中的 Node、PostgreSQL 和服务端口描述一致。
7. 不安装前端依赖，不执行 Maven/npm 构建，不启动或停止任何服务。

## 后续边界

环境基线完成并经用户确认后，再单独开始功能范围、领域模型、接口契约和页面原型设计。本次工作不提前定义数据库表、Gateway 路由、Redis Key、MQTT Topic 或业务规则。
