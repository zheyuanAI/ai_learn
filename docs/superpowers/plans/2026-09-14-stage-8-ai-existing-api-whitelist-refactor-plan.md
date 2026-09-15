# 阶段 8 AI 复用现有业务接口与单一白名单改造方案

> 日期：2026-09-14  
> 状态：A–F 与文档同步已实现并通过自动化测试；G 旧工具删除等待真实端到端验收  
> 范围：Open WebUI 工具目录、Gateway 委托认证、Core AI 编排、现有业务查询接口复用、工具审计与相关文档  
> 基线：Java 21、Spring Boot 3.3.5、Spring Cloud 2023.0.3、SpringDoc 2.6.0、Open WebUI、DeepSeek V4 Flash

## 一、改造目标

将当前“为 AI 单独编写 10 个查询端点和 10 个工具适配器”的实现，调整为：

1. 业务查询能力只实现一次，页面和 AI 共用现有业务 Controller、ApplicationService、QueryService 与 DTO。
2. AI 不再要求 `@AiReadableTool` 等接口标记，不在代码中维护第二套角色权限。
3. 所有允许 AI 调用的业务 API 只维护在一份 YAML 白名单中，并按业务模块分组。
4. WMS 使用该白名单执行运行时拦截；Open WebUI 使用由同一白名单生成的过滤 OpenAPI，不再手工维护工具清单。
5. Open WebUI 自主决定调用哪个接口、调用顺序以及是否继续联查；WMS 不为常见问题预编排一套 AI 诊断接口。
6. Open WebUI 工具调用恢复为发起对话的 WMS 用户，由现有 Spring Security、`@PreAuthorize`、租户和业务数据范围继续完成最终授权。
7. 当前阶段仍只在白名单中登记查询操作；未来允许写操作时扩展同一清单，不另建第二套工具体系。

核心原则：

> 白名单决定 AI 可以尝试调用哪些业务操作；Spring Security 决定当前用户是否有权执行；业务接口决定事实和结果；大模型决定如何联查和解释。

## 二、当前问题与保留能力

### 2.1 当前问题

当前 Open WebUI 只读取 Core 暴露的 `/v3/api-docs/ai-tools`，该文档仅包含 `/internal/ai/tools/**` 下的 10 个专用端点。虽然多数工具内部复用了已有应用服务，但仍存在以下重复：

- `OpenWebUiToolController` 为每个查询声明一个入口；
- 每个查询都有一个 `AiReadTool` 实现；
- 每类参数都有一份 AI 专用请求 DTO；
- 工具名同时存在于代码注册表、环境变量、OpenAPI 和 Open WebUI 配置逻辑；
- 新增业务查询后，若继续沿用当前方式，通常还要再次实现 AI 接口层。

### 2.2 必须保留

- `POST /api/ai/chat` 与 `POST /api/ai/chat/stream`；
- WMS 自己的 AI 会话与消息持久化；
- Open WebUI Agent、Knowledge 和 DeepSeek 模型预设；
- 当前登录用户、租户、有效 JTI 和最新权限的委托恢复；
- SSE 的 `tool_started`、`tool_finished`、`done` 和 `error`；
- AI 工具调用次数、超时和审计；
- 普通浏览器 JWT 请求原有链路及所有领域 `@PreAuthorize`；
- 业务表、库存流水、工序事实和设备事实作为唯一业务事实源。

### 2.3 不再保留为长期入口

- 10 个 `/internal/ai/tools/{toolName}` 专用查询端点；
- `AiReadTool` 的逐业务实现模式；
- `WMS_AI_ENABLED_TOOLS` 与代码注册表共同维护的工具名单；
- 为普通业务查询单独定义的 AI 请求/返回 DTO；
- 为日报、诊断等常见问题预先编写固定多接口编排。

## 三、目标架构

```text
WMS 用户浏览器（JWT）
        │
        ▼
POST /api/ai/chat/stream
        │
        ▼
Core AI 会话编排
  - 保存会话
  - 保存短期委托上下文
  - 调用 Open WebUI Agent
        │
        ▼
Open WebUI + DeepSeek
  - 理解问题
  - 查看过滤后的业务 OpenAPI
  - 自主多轮选择接口
        │
        │ ServiceKey + ChatId + MessageId
        ▼
Gateway AI 委托调用分支
  1. 读取唯一白名单
  2. 校验 method + path
  3. 恢复原 WMS 用户、租户和有效 JTI
  4. 注入可信下游身份 Header
  5. 记录工具开始/结束元数据
        │
        ├───────────────┐
        ▼               ▼
Core 现有业务 API    IoT 现有业务 API
        │               │
        ▼               ▼
原有 @PreAuthorize、租户与领域校验
        │               │
        └───────┬───────┘
                ▼
         原始业务接口结果
                │
                ▼
       Open WebUI 继续联查或回答
```

### 3.1 为什么统一入口放在 Gateway

- Gateway 已经是用户 JWT、会话有效性和下游可信身份 Header 的统一入口；
- Core 与 IoT 的业务路径已经由 Gateway 路由；
- 白名单和委托身份只实现一次，不需要在 Core、IoT 分别复制过滤器；
- Open WebUI 只需要调用一个 WMS 地址；
- 下游服务仍按现有方式从 Redis 读取最新权限并执行 `@PreAuthorize`。

Gateway 的 AI 分支不重新判断角色权限，只负责确认“这是受控 Open WebUI 调用、路径在白名单、委托会话仍有效”，随后把请求恢复成原用户。最终功能权限仍由下游 Spring Security 判断。

## 四、唯一白名单设计

### 4.1 唯一事实源

新增：

```text
deploy/openwebui/wms-ai-api-whitelist.yml
```

该文件是唯一需要人工维护的 AI API 清单。不得再在 Java 常量、`application.yml`、环境变量或 Open WebUI 后台重复列出相同操作。

Open WebUI 不能直接把自定义 YAML 当作工具协议，因此采用以下方式共同使用该文件：

```text
wms-ai-api-whitelist.yml
   ├─ Gateway 读取：执行运行时 method + path 拦截
   ├─ Gateway 读取：从各服务完整 OpenAPI 中生成过滤后的 AI OpenAPI
   └─ configure-windows.ps1 读取：校验并注册过滤后的 Tool Server
```

Open WebUI 最终读取的是该 YAML 自动生成的 OpenAPI 视图，不需要人工复制接口列表。

### 4.2 建议格式

```yaml
version: 1

services:
  core:
    openapi-url: http://127.0.0.1:10003/v3/api-docs
    catalog-path: /v3/api-docs/ai-tools/core
  iot:
    openapi-url: http://127.0.0.1:10004/v3/api-docs
    catalog-path: /v3/api-docs/ai-tools/iot

groups:
  sales:
    description: 销售订单与履约查询
    operations:
      - id: salesOrderSearch
        service: core
        method: GET
        path: /api/sales-orders
        summary: 按关键字、状态、客户和履约状态查询销售订单
      - id: salesOrderDetail
        service: core
        method: GET
        path: /api/sales-orders/{id}
        summary: 查询销售订单详情

  inventory:
    description: 库存余额、预留、流水和低库存查询
    operations:
      - id: inventoryBalanceSearch
        service: core
        method: GET
        path: /api/inventory/balances
        summary: 查询库存余额
      - id: inventoryLowStockSearch
        service: core
        method: GET
        path: /api/inventory/low-stock
        summary: 查询低于安全库存的产品

  iot:
    description: 设备、状态、遥测和告警查询
    operations:
      - id: deviceAlarmSearch
        service: iot
        method: GET
        path: /api/device-alarms
        summary: 查询设备告警
```

### 4.3 白名单规则

- 按业务模块分组，不按页面或角色分组；
- 分组只用于阅读、工具说明和前端能力展示，不参与授权；
- 每个 `id` 全局唯一，并作为 Open WebUI 看到的稳定 `operationId`；
- `method + path` 必须精确登记，不允许 `/**` 等宽泛规则；
- `{id}` 只能匹配一个路径段，不允许越级匹配；
- 查询参数不参与路由放行判断，具体参数合法性继续由现有 Controller/DTO 校验；
- 不根据 GET/POST 推断读写性质，是否开放完全以清单为准；
- 当前清单只登记查询操作；
- 不在清单中写角色、权限编码或租户规则，避免复制 Spring Security 权限事实；
- 清单语法错误、重复 ID、重复 `method + path`、未知服务和非法路径时，Gateway 启动失败；
- 业务接口是否存在、请求/响应 Schema 是否完整，在所有服务启动后由目录生成和部署校验失败关闭。

### 4.4 配置加载

- 使用 `WMS_AI_API_WHITELIST_PATH` 指向外部 YAML；
- Windows IDEA 一键启动时，Gateway 与 Core 共用 `deploy/openwebui/windows.env`；
- 生产部署时把同一文件只读挂载给 Gateway 和 Open WebUI 配置脚本；
- 修改白名单后重启 Gateway，并重新运行 Open WebUI 配置脚本刷新 Tool Server；
- 本轮不实现文件热更新，避免请求执行期间目录版本漂移。

## 五、过滤 OpenAPI 的生成方式

### 5.1 Gateway 输出目录

Gateway 提供：

```text
GET /v3/api-docs/ai-tools
GET /v3/api-docs/ai-tools/core
GET /v3/api-docs/ai-tools/iot
```

- 根路径返回可用服务目录和清单版本；
- 服务路径读取对应下游服务的完整 `/v3/api-docs`；
- 只保留 YAML 中属于该服务的 `method + path`；
- 使用 YAML 的 `id` 重写 `operationId`，解决不同 Controller 方法名重复问题；
- 使用 YAML 的 `summary` 覆盖或补充模型可理解的操作描述；
- 保留对应参数、请求体、响应和所需 `components/schemas`；
- 输出的 `servers` 指向 Gateway `http://127.0.0.1:20001`，工具执行始终回到统一入口。

按服务分别生成 Tool Server，避免直接合并 Core、IoT OpenAPI 时出现同名 Schema 冲突。以后增加 Auth 查询时，只需要在同一 YAML 增加 `auth` 服务和对应操作。

### 5.2 目录生成失败策略

- 下游服务尚未启动：返回明确 503，不让 Open WebUI 使用陈旧或空目录；
- 白名单接口在下游 OpenAPI 中不存在：返回配置错误并使 `configure-windows.ps1` 失败；
- 同一个操作在下游文档中找不到对应 HTTP Method：失败；
- 过滤后出现清单外路径：失败；
- 清单版本发生变化：目录缓存整体失效并在 Gateway 重启后重建。

由于 IDEA 一键启动的多个服务是并行启动，不在 Gateway 启动阶段强制访问尚未就绪的下游 OpenAPI；跨服务存在性校验放在目录首次读取和 Open WebUI 配置阶段完成。

## 六、Open WebUI 委托认证与运行时拦截

### 6.1 普通用户请求保持不变

```text
浏览器 JWT
  → JwtAuthGlobalFilter 验签和校验 JTI
  → Gateway 注入可信身份 Header
  → 下游 DownstreamSecurityFilter 加载最新权限
  → @PreAuthorize
```

### 6.2 Open WebUI 工具请求

Open WebUI 继续携带：

```text
X-WMS-AI-Service-Key
X-WMS-OpenWebUI-Chat-Id
X-WMS-OpenWebUI-Message-Id
```

Gateway 在正常 JWT 分支之前识别该请求，执行：

1. 删除调用方可能伪造的 `X-User-Id`、`X-Tenant-Id`、`X-Session-Id` 等上下文 Header；
2. 恒定时间比较 `X-WMS-AI-Service-Key`；
3. 使用请求 method 和具体 path 匹配唯一白名单；
4. 根据 ChatId + MessageId 从 Redis 读取 Core 保存的短期委托上下文；
5. 检查本次请求允许操作集合和最大调用次数；
6. 检查 Redis 中当前有效 JTI，防止账号退出或被后登录替换后继续调用；
7. 注入可信用户、租户、JTI 和 requestId Header；
8. 转发到原有业务接口；
9. 下游 `DownstreamSecurityFilter` 重新读取最新权限并建立 `SecurityContext`；
10. 原有 `@PreAuthorize`、租户和领域查询规则决定结果。

这里不新增角色判断，不解析 `@PreAuthorize`，也不把权限编码写入 YAML。

### 6.3 共享委托上下文

将当前 Core 私有的 Redis key 计算和存储契约下沉为共享、最小、版本化的数据结构，供 Core 写入、Gateway 读取。只共享：

- 租户 ID；
- 用户 ID；
- 当前 JTI；
- WMS AI 会话 ID；
- requestId；
- 本次请求可选的操作子集；
- 过期时间或 TTL；
- 调用次数。

不把角色和权限快照作为最终授权依据；Gateway 和下游始终以当前有效会话及下游 Redis 最新权限为准。

## 七、大模型自主联查规则

不再提供固定“订单诊断”或“每日运营报告事实聚合”工具。模型根据问题自主完成：

```text
搜索业务对象
  → 取得对象 UUID
  → 查询详情
  → 查询关联任务、库存、流水、告警或审计
  → 判断是否需要继续查询
  → 综合回答
```

例如“销售订单为什么没有完成”由模型自行选择：

1. `GET /api/sales-orders?keyword=SO...`；
2. `GET /api/sales-orders/{id}`；
3. 根据返回事实调用库存、拣货、发货、工单或追溯接口；
4. 根据必要性查询操作审计；
5. 输出当前状态、阻塞点、相关角色和建议。

服务端只保留资源约束：

- 单次问答最大工具调用数；
- 单次工具超时；
- 整体 Agent 超时；
- 最大分页大小继续由业务接口自身限制；
- 失败、拒绝和超时必须可观察。

## 八、现有 10 个工具迁移映射

| 当前工具 | 目标现有业务接口 | 处理方式 |
| --- | --- | --- |
| `querySalesOrderStatus` | `GET /api/sales-orders`、`GET /api/sales-orders/{id}` | 删除专用工具，由模型先搜索再查详情 |
| `queryPurchaseOrderStatus` | `GET /api/purchase-orders`、`GET /api/purchase-orders/{id}` | 删除专用工具，由模型先搜索再查详情 |
| `queryInventoryByProductAndWarehouse` | `GET /api/inventory/balances` | 直接登记现有接口 |
| `queryWorkOrderProgress` | `GET /api/work-orders`、`GET /api/work-orders/{id}` | 删除专用工具，由模型联查 |
| `queryQualityStatistics` | `GET /api/dashboard/quality` | 直接登记现有接口 |
| `queryDeviceAlarm` | `GET /api/device-alarms`、`GET /api/device-alarms/{id}`，必要时配合 Dashboard | 直接登记 IoT 现有接口 |
| `queryTrace` | `GET /api/traceability` | 直接登记现有接口 |
| `generateDailyOperationReport` | `/api/dashboard/inventory`、`fulfillment`、`manufacturing`、`quality`、`device`、`alarms` | 删除固定聚合工具，由模型多次查询后生成文字报告 |
| `queryLowStock` | 新增普通业务接口 `GET /api/inventory/low-stock` | 复用现有 `LowStockQueryService`，页面与 AI 均可使用 |
| `queryOperationAudit` | 新增普通业务接口 `GET /api/operation-audits` | 复用现有 `OperationAuditApplicationService`，不再作为 AI 私有能力 |

销售、采购、工单不新增“按单号查询 AI 接口”。现有分页接口已支持关键字时，由模型自行完成“关键字搜索 → 取得 UUID → 查询详情”。

低库存和操作审计之所以增加接口，是因为仓库目前只有应用服务、没有普通 REST 查询入口；新增的是通用业务能力，不是 AI 副本。

## 九、实施步骤

### 阶段 A：建立单一白名单及契约测试

**结果：** 仓库只有一份 AI API 清单，能够稳定解析并拒绝错误配置。

新增或调整：

- 新增 `deploy/openwebui/wms-ai-api-whitelist.yml`；
- 在 `platform-shared` 增加不依赖具体服务的清单模型、路径规范化与单段模板匹配组件；
- Gateway 增加 `WMS_AI_API_WHITELIST_PATH` 配置；
- 删除 `WMS_AI_ENABLED_TOOLS` 的长期配置定义，迁移期间可暂时读取但不得再作为最终名单；
- 为重复 ID、重复路径、非法 Method、未知服务、`/**`、空描述和缺失文件增加单元测试；
- 更新 IDEA `GatewayApplication.run.xml`，使 Gateway 加载本地 Open WebUI 环境配置；
- 不修改或提交本机 `deploy/openwebui/windows.env` 中的密钥。

### 阶段 B：生成按服务过滤的 OpenAPI

**结果：** Open WebUI 只能看到 YAML 中登记的现有业务接口。

新增或调整：

- Gateway 增加 AI OpenAPI 目录 Controller 和目录生成服务；
- 读取 Core/IoT 原始 `/v3/api-docs` 并按 service、method、path 过滤；
- 用 YAML `id` 生成稳定且唯一的 `operationId`；
- 保留当前操作引用的参数与 Schema；
- 输出 Gateway `servers` 地址；
- 增加契约测试，断言过滤文档不含任何未登记操作和写操作；
- 暂时保留 Core 当前 `/v3/api-docs/ai-tools`，待新目录端到端通过后删除。

### 阶段 C：在 Gateway 增加 Open WebUI 委托调用分支

**结果：** Open WebUI 调用现有业务路径时，可以恢复成原用户并进入原有 Spring Security。

新增或调整：

- 在 `JwtAuthGlobalFilter` 前后职责清晰地增加 AI 委托认证分支，优先复用其 Header 清理、JTI 校验和下游转发逻辑；
- 共享当前 `OpenWebUiInvocationContextStore` 的 Redis key 与序列化契约；
- method + path 不在白名单时返回 403；
- 服务密钥、关联信息或 JTI 无效时返回 401；
- Redis/权限基础设施不可用时返回 503；
- 通过白名单后只注入身份，不注入权限；
- 验证下游仍由 `DownstreamSecurityFilter` 读取最新权限，并由原有 `@PreAuthorize` 返回允许或 403；
- 增加普通 JWT 路径回归测试，确保新分支不改变现有页面请求。

### 阶段 D：调整 Core AI 编排和工具过程观察

**结果：** Core 不再计算代码工具注册表，Open WebUI 使用业务 API Tool Server，自主执行多轮调用。

新增或调整：

- `OpenWebUiAgentClient` 支持绑定配置脚本生成的多个 Tool Server ID；
- `OpenWebUiInvocationContextStore` 写入共享版本化上下文；
- `tool_whitelist` 兼容保留为“本次请求对 YAML operation ID 的可选缩小”，它不是第二份部署白名单；
- 未传 `tool_whitelist` 时允许 YAML 中全部操作尝试执行，实际权限在下游判断；
- `/api/ai/capabilities` 返回白名单分组和操作目录，不再声称已预先计算每个接口的角色权限；最终授权明确标记为运行时 Spring Security 判定；
- Gateway 在 Redis 中写入 operation ID、请求开始、HTTP 状态、耗时和结束状态；
- Core 继续轮询观察结果并发出 `tool_started`、`tool_finished`；
- 工具审计由统一过程记录器保存，不解析或复制业务响应 DTO。

### 阶段 E：补齐真正缺失的普通查询接口

**结果：** 低库存与操作审计成为通用 WMS 查询能力，而不是 AI 私有实现。

新增或调整：

- 在现有 `InventoryController` 增加 `GET /api/inventory/low-stock`，调用现有 `LowStockQueryService`；
- 增加普通操作审计查询 Controller，调用现有 `OperationAuditApplicationService`；
- 为两个接口补 `@PreAuthorize`、租户过滤、分页/limit 边界和接口测试；
- 同步库存与 AI 领域接口契约及验收标准；
- 不重写 SQL，不复制已有 Service。

### 阶段 F：切换 Open WebUI 配置

**结果：** Open WebUI 从 Gateway 读取新目录，并且工具列表完全由同一 YAML 驱动。

调整 `deploy/openwebui/configure-windows.ps1`：

- 读取 `wms-ai-api-whitelist.yml`；
- 请求 Gateway 根目录和各服务过滤 OpenAPI；
- 校验输出 operation ID 集合与 YAML 完全一致；
- 自动创建或更新 `server:wms_core`、`server:wms_iot`；
- Tool Server URL 指向 Gateway `http://127.0.0.1:20001`；
- 两个 Tool Server 使用相同服务密钥及 ChatId/MessageId 模板；
- `wms-assistant` 自动绑定生成的 Tool Server ID 列表；
- 不在 PowerShell 中再次硬编码工具名或接口路径。

### 阶段 G：删除旧专用工具链

**前置条件：** 六类角色、10 个原场景和至少一个多接口联查场景均通过新链路。

删除或收敛：

- `OpenWebUiToolController`；
- `AiToolRegistry`、`AiReadTool`、`AiToolExecutor` 及逐业务实现；
- `Query*AiTool`、`GenerateDailyOperationReportAiTool` 与 `AiToolArguments`；
- 仅服务旧 10 个工具的 AI 请求 DTO；
- `AiToolOpenApiConfiguration` 中只匹配 `/internal/ai/tools/**` 的旧目录；
- `WMS_AI_ENABLED_TOOLS`；
- 对应旧测试和文档清单。

直连 DeepSeek 的历史工具循环不得继续依赖被删除的代码工具注册表。本轮建议把直连 Provider 降为模型连通诊断，不再承诺业务工具能力；正式业务 Agent 唯一路径为 Open WebUI。若后续确实需要直连 Agent，再基于同一过滤 OpenAPI 实现通用 HTTP 工具客户端，不恢复逐业务工具类。

### 阶段 H：文档同步与验收收口

至少同步：

- `docs/specs/00-project/架构设计.md`；
- `docs/specs/00-project/正式项目计划.md`；
- `docs/specs/00-project/阶段决策与续聊入口.md`；
- `docs/specs/50-ai-assistant/概述.md`；
- `docs/specs/50-ai-assistant/领域模型.md`；
- `docs/specs/50-ai-assistant/接口契约.md`；
- `docs/specs/50-ai-assistant/验收标准.md`；
- `docs/specs/50-ai-assistant/AI助手业务规则.md`；
- `docs/prototype/README.md` 与 AI 助手原型中的工具说明；
- `backend/README.md`；
- `deploy/openwebui/README.md`；
- 阶段 8 变更总结与验证记录。

文档统一删除“代码固定注册表 + 部署启用名单”的旧事实，改为“单一 YAML API 白名单 + Gateway 委托身份 + 下游 Spring Security”。

## 十、主要影响文件

### 新增候选

- `deploy/openwebui/wms-ai-api-whitelist.yml`
- `backend/platform-shared/src/main/java/com/ailearn/platform/shared/ai/*`
- `backend/platform-gateway/src/main/java/com/ailearn/platform/gateway/ai/*`
- `backend/platform-gateway/src/test/java/com/ailearn/platform/gateway/ai/*`
- Core 普通操作审计查询 Controller 及对应测试

### 重点修改

- `backend/platform-gateway/src/main/java/com/ailearn/platform/gateway/filter/JwtAuthGlobalFilter.java`
- `backend/platform-gateway/src/main/resources/application.yml`
- `backend/.run/GatewayApplication.run.xml`
- `backend/platform-core/src/main/java/com/ailearn/platform/core/ai/application/OpenWebUiInvocationContextStore.java`
- `backend/platform-core/src/main/java/com/ailearn/platform/core/ai/infrastructure/OpenWebUiAgentClient.java`
- `backend/platform-core/src/main/java/com/ailearn/platform/core/ai/application/AiChatOrchestrator.java`
- `backend/platform-core/src/main/java/com/ailearn/platform/core/ai/controller/AiCapabilitiesController.java`
- `backend/platform-core/src/main/java/com/ailearn/platform/core/ai/config/AiProperties.java`
- `backend/platform-core/src/main/java/com/ailearn/platform/core/inventory/controller/InventoryController.java`
- `deploy/openwebui/configure-windows.ps1`
- `deploy/openwebui/windows.env.example`

### 删除候选

- `backend/platform-core/src/main/java/com/ailearn/platform/core/ai/controller/OpenWebUiToolController.java`
- `backend/platform-core/src/main/java/com/ailearn/platform/core/ai/tool/*`
- 仅为旧工具存在的 `Ai*ToolRequest.java`
- `AiReadTool.java`、`AiToolRegistry.java`、`AiToolExecutor.java`
- 对应旧工具单元测试

删除前必须使用引用搜索确认直连 Provider、测试和文档没有继续依赖。

## 十一、验证计划

### 11.1 自动化测试

```powershell
cd backend
D:\ruanjian\apache-maven-3.9.1\bin\mvn.cmd -pl platform-shared,platform-gateway,platform-core,platform-iot -am test
```

重点测试矩阵：

1. YAML 正常加载及各种非法配置失败；
2. 过滤 OpenAPI 的路径、Method、operationId 与 Schema 完整性；
3. 清单中没有写接口时，输出文档不得出现任何创建、修改、审批、确认或删除操作；
4. Open WebUI 猜测清单外路径时 Gateway 返回 403；
5. 无效服务密钥、无效关联、过期关联、会话被替换分别返回稳定错误；
6. 同一接口：有权限用户成功，无权限用户由下游 `@PreAuthorize` 返回 403；
7. 跨租户对象保持不可见；
8. 普通 JWT 请求行为与改造前一致；
9. 最大调用次数与超时仍然生效；
10. 工具成功、失败、拒绝、超时均留下过程事件和审计。

### 11.2 Open WebUI 配置验证

```powershell
powershell -ExecutionPolicy Bypass -File deploy\openwebui\configure-windows.ps1
```

验证：

- Tool Server 操作集合与 YAML 完全一致；
- 不需要在 Open WebUI 后台逐个维护工具；
- 修改 YAML、重启 Gateway、重新运行脚本后，工具目录按预期增减；
- Open WebUI 无法通过 Tool Server 调用未登记接口。

### 11.3 真实角色端到端验证

至少覆盖：

- 销售人员：销售订单搜索、详情、库存联查、操作审计；
- 采购人员：采购订单搜索、详情、收货与库存事实联查；
- 仓库人员：余额、预留、流水、低库存查询；
- 生产质检人员：工单、派工、执行、质量和看板联查；
- IoT 人员：设备、状态、遥测和告警查询；
- 租户管理员：没有业务权限时，AI 调用业务接口应得到 403，而不是继承管理员名称获得越权；
- 第二租户：不能查询第一租户对象；
- 多步问题：模型至少连续调用两个现有业务接口后完成回答；
- 日报问题：模型分别读取多个 Dashboard 接口后自行汇总，不调用固定日报工具。

## 十二、验收标准

1. 仓库中只有一份人工维护的 AI API 白名单；
2. Java 代码、环境变量和 PowerShell 不再硬编码完整工具名单；
3. Open WebUI 展示的所有业务工具均来源于白名单生成的 OpenAPI；
4. 清单外 method + path 即使被模型猜中也无法调用；
5. 清单内接口仍必须通过原用户的现有 `@PreAuthorize`；
6. 新增普通业务查询后，只需完成业务接口并在 YAML 增加一条操作，无需再写 AI Controller、AI Service 或 AI DTO；
7. 模型能够自主执行搜索、详情和跨模块多步联查；
8. 低库存和操作审计只有普通业务接口实现，不保留 AI 查询副本；
9. 旧 10 个专用端点与逐业务工具实现完成删除；
10. SSE 工具进度、来源摘要、会话、调用上限和审计能力无回退；
11. 普通 WMS 页面和 API 的 JWT、权限、租户及业务行为无回归；
12. 文档、Open WebUI 配置说明和实际代码保持一致。

## 十三、回退策略

- 在新业务 API Tool Server 完成端到端验证前保留旧 10 工具，但 Open WebUI 模型预设一次只绑定一套 Tool Server，避免同一问答重复调用；
- 新链路异常时，可把模型预设临时切回旧 `server:wms`，不回滚业务接口；
- 新链路通过六类角色验收后再删除旧工具代码和配置；
- 不修改数据库业务事实，不需要数据库回退；
- 本地密钥文件 `deploy/openwebui/windows.env` 保持不纳入版本控制。

## 十四、本轮不做

- 不部署自有大模型，只使接口层对后续切换模型透明；
- 不新增页面跳转按钮；
- 不开放创建、修改、审核、确认、删除等写操作；
- 不为模型重新裁剪普通业务响应 DTO；
- 不预先实现固定诊断工作流或固定日报编排；
- 不引入 Dify、RabbitMQ、MinIO、pgvector 或新的数据库事实表；
- 不改采购、库存、销售、制造和 IoT 的核心状态机。
