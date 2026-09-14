# 阶段 8 AI 只读助手与调用审计实施计划

> 日期：2026-09-12  
> 状态：历史实施计划；初版直连纵向切片已完成，当前实施与验收以 Open WebUI 演进计划和周末变更总结为准  
> 适用范围：`ai_learn_developProject` 阶段 8 真实前后端实现  
> 历史直连模型：`deepseek-v4-flash`；当前部署模型：硅基流动 `deepseek-ai/DeepSeek-V4-Flash`

> 后续决策说明（2026-09-12）：本计划记录直连 DeepSeek 首个纵向切片的实施历史。后续架构已改为引入自托管 Open WebUI、同步 `docs/ai-knowledge/` 受控知识、通过 WMS 用户权限与固定查询白名单的交集执行工具，并暂停页面导航动作。后续任务以 `2026-09-12-stage-8-openwebui-knowledge-and-readonly-tools-plan.md` 为准；本文件中“无知识库、直连 Provider、导航动作”的未完成任务不再作为当前目标。
>
> 当前实现与验证摘要见 `docs/verification/2026-09-12-13-阶段8-AI与OpenWebUI周末变更总结.md`。下文保留为实施历史，不应据此恢复页面跳转、九工具目录或直连 Provider 默认路径。

## 1. 目标

在不改变采购、销售、库存、制造、质量和 IoT 业务事实的前提下，交付一个受认证、租户和领域权限约束的 AI 只读助手：

- 用户可以用自然语言查询库存、订单、工单、质量、设备告警和跨域追溯事实；
- 回答通过 SSE 流式展示，明确呈现分析进度、工具调用、回答正文、数据来源和实际时间范围；
- AI 可以建议下一步进入哪个业务页面，但不能执行创建、提交、审核、确认、完成、删除等业务写操作；
- 页面导航动作必须由服务端白名单和权限策略生成，不能使用模型输出的任意 URL；
- 成功、失败、超时和拒绝的工具调用全部留痕，并与回答共享同一个 `request_id`；
- 模型接入通过 Provider 端口隔离，默认使用 DeepSeek Responses API，后续可通过配置或新适配器更换模型。

## 2. 当前仓库基线

> 以下为计划启动时基线；2026-09-12 已开始按里程碑实施，实际进度见文末“实施记录”。

- `docs/specs/50-ai-assistant/` 已定义九个目标只读工具、会话、消息和工具审计模型，但仍固定描述为 PokeAPI/Grok，且聊天契约尚未定义 SSE 与导航动作；
- Gateway 已放行 `/api/ai/**` 到 Core；Auth 已有 `ai:chat:query`、`ai:trace:view`、`ai:audit:view` 权限目录与菜单数据；
- `platform-core` 尚无 `ai` Java 包、AI Controller、AI 数据表和模型客户端；
- 阶段 7 已实现 `FactsQueryContext`、领域 Facts 端口、Dashboard、ExceptionCenter 和跨域 Traceability，可作为 AI 只读事实入口；
- `/ai`、`/ai/chat`、`/ai/audit` 当前仍由 `DomainView` 占位，真实 Vue 页面和 `frontend/src/api/ai.ts` 尚未实现；
- 工作区存在用户未提交的登录页和布局样式改动，阶段 8 实施必须避免覆盖或格式化这些无关改动。

## 3. 冻结设计决策

### 3.1 模型与协议

- 默认 Base URL：`https://api.deepseek.com`；
- 默认模型：`deepseek-v4-flash`；
- 上游协议：DeepSeek Responses API；
- 上游与浏览器下游均使用 SSE 流式传输；
- DeepSeek API 是无状态接口，服务端从本地会话中选取受限历史上下文，每次请求显式发送；
- 只向模型发送回答所需的最小业务摘要，不发送数据库记录全集、认证凭据、设备密钥或无关个人信息；
- 不启用联网搜索、文件搜索、代码执行、MCP 或模型写工具，只注册 WMS 自有 function 工具。

目标环境变量：

```text
WMS_AI_ENABLED=false
WMS_AI_BASE_URL=https://api.deepseek.com
WMS_AI_MODEL=deepseek-v4-flash
WMS_AI_API_KEY=<仅从安全环境读取>
WMS_AI_CONNECT_TIMEOUT=5s
WMS_AI_STREAM_TIMEOUT=60s
WMS_AI_MAX_TOOL_ROUNDS=4
WMS_AI_MAX_TOOL_CALLS=8
```

### 3.2 流式接口

- 保留 `POST /api/ai/chat` 作为非流式兼容和自动化测试入口；
- 新增 `POST /api/ai/chat/stream`，响应类型为 `text/event-stream`；
- 浏览器使用 `fetch + ReadableStream`，不使用无法方便携带 POST 请求体和 Bearer Token 的原生 `EventSource`；
- 下游事件固定为：`meta`、`progress`、`tool_started`、`tool_finished`、`delta`、`actions`、`done`、`error`；
- `meta` 最先返回 `request_id`、`session_id` 和 `model_id`；
- `actions` 只能由后端导航策略生成，并在所有权限校验完成后发送；
- `done` 携带来源摘要、实际数据时间范围、工具摘要、Token 用量和完成状态；
- 连接中断时保留已完成审计，将助手消息标记为 `Interrupted`，不能伪装成完整回答。

### 3.3 业务与安全边界

- AI 模块只能依赖领域应用服务或阶段 7 Facts 端口，禁止注入跨域 Mapper/Repository；
- 模型和客户端不能声明或覆盖 `tenant_id`、`user_id`、权限、Base URL、API Key 和模型编号；
- `tool_whitelist` 只能缩小服务端计算出的可用工具集合；
- 业务结论、状态阻塞和可执行能力优先复用领域应用服务返回的真实状态，不在 Prompt 中复制第二套状态机；
- 所有工具参数强制校验时间范围、分页数量、实体标识和租户边界；
- 允许使用业务单号提问，业务单号到 UUID 的解析必须通过对应领域应用服务完成；
- 导航按钮只是入口，目标页面路由守卫和目标业务 API 仍须独立鉴权。

## 4. 实施里程碑

## M0：同步规格、原型与接口契约

目标：先冻结 DeepSeek、SSE、页面上下文和导航动作语义，防止代码与文档再次分叉。

涉及文件：

- `docs/specs/50-ai-assistant/概述.md`
- `docs/specs/50-ai-assistant/领域模型.md`
- `docs/specs/50-ai-assistant/接口契约.md`
- `docs/specs/50-ai-assistant/验收标准.md`
- `docs/specs/50-ai-assistant/AI助手业务规则.md`
- `docs/specs/00-project/项目概述.md`
- `docs/specs/00-project/架构设计.md`
- `docs/specs/00-project/原型与交互说明.md`
- `docs/prototype/README.md`
- `docs/prototype/pages/ai-assistant.html`
- `docs/prototype/pages/tool-audit.html`

交付：

- 将 PokeAPI/Grok 改为 DeepSeek 默认 Provider；
- 增加流式事件协议、`PageContext`、`NavigationAction` 和会话历史接口；
- 补充状态解释、阻塞诊断、角色待处理、权限诊断和页面导航验收；
- 明确一期不支持 AI 自动写业务数据。

验收：规格、原型、权限、接口字段和页面行为使用同一套术语。

## M1：AI 会话、消息、审计与配置基础

目标：模型尚未接入时，也能稳定记录会话和工具调用治理信息。

后端主要改动：

- 新增 `backend/platform-core/src/main/resources/db/migration/core/V9__ai_chat_and_tool_audit.sql`；
- 新增 `com.ailearn.platform.core.ai.domain`：会话、消息、工具审计、状态枚举和导航动作值对象；
- 新增 `com.ailearn.platform.core.ai.application`：会话服务、审计服务和可信 AI 请求上下文；
- 新增 `com.ailearn.platform.core.ai.infrastructure`：沿用当前首个切片的 `JdbcTemplate` PostgreSQL Repository；若后续查询复杂度明显上升，再按项目既有模式评估 MyBatis，不在同一阶段混用两套 AI 持久层风格；
- 在 `application.yml` 增加 `platform.ai` 配置，只引用环境变量，不出现真实密钥；
- 必要时在 Auth 新迁移中补齐缺失的 AI 菜单路由或权限绑定，但不改写已应用迁移。

数据表：

- `ai_chat_session`：租户、用户、会话号、消息数、最近消息时间；
- `ai_chat_message`：角色、正文、来源摘要、时间范围、工具摘要、模型、完成状态；
- `ai_tool_audit_log`：用户、会话、请求、工具、脱敏输入/输出、来源、耗时、状态和错误；
- 所有表包含 `tenant_id`、审计时间和逻辑删除字段，并建立租户范围索引。

验收：租户 A 不能读取租户 B 的会话和审计；失败、拒绝、超时均可保存；迁移兼容 PostgreSQL 12.1。

## M2：受控工具注册表与首个纵向切片

目标：先完成“销售订单为什么不能发货”的真实闭环，再扩展其余工具。

新增核心组件：

- `AiReadTool`：工具名、参数 Schema、所需权限、超时和执行入口；
- `AiToolRegistry`：只注册服务端受控工具，拒绝任意工具名；
- `AiToolExecutor`：参数校验、权限校验、超时、结果脱敏、审计落库；
- `AiNavigationPolicy`：根据工具结果、对象可见性和用户权限生成动作编码；
- `AiActionCode`：固定动作白名单，不保存或返回任意 URL。

首个纵向切片调用：

1. `querySalesOrderStatus` 查询销售订单状态和履约数量；
2. `queryInventoryByProductAndWarehouse` 查询实物、预留和可用库存；
3. `queryTrace` 查询关联工单、采购和设备事实；
4. 服务端形成可验证的阻塞事实与导航动作；
5. 输出“查看订单详情”“去履约处理”“查看关联工单”等当前用户获准动作。

验收场景：

- 销售人员能看到订单和库存说明，但没有仓库确认权限时不能获得伪造的确认按钮；
- 仓库人员有目标页面权限时可获得履约工作台导航；
- 无销售订单查看权限时工具返回 `Denied` 且不泄露订单是否存在；
- 任何工具执行前后，销售、库存、生产和 IoT 业务事实数量与状态保持不变。

## M3：DeepSeek Provider 与工具调用循环

目标：把已验证工具接到 DeepSeek Responses API，同时保持 Provider 可替换。

后端主要组件：

- `AiModelClient`：模型无关端口；
- `AiModelRequest`、`AiModelEvent`：内部统一请求和流事件；
- `DeepSeekResponsesClient`：DeepSeek Responses API 适配器；
- `AiChatOrchestrator`：上下文裁剪、模型调用、function call 执行、结果回填和最终回答；
- `AiPromptPolicy`：中文业务助手边界、来源约束和写操作拒绝规则。

实现约束：

- 使用 Java 21 与 Spring Boot 3.3.5 可用客户端能力，不引入重量级 Agent 框架；
- Provider 只负责协议转换，不包含租户、权限和业务规则；
- 服务端自己限制工具轮次和总调用数，不依赖上游忽略或支持某个限制参数；
- 解析 `response.output_text.delta` 和 function call 参数事件；
- 仅把最终回答文本向浏览器作为 `delta` 输出，不向用户展示模型思维链；
- 最终事件显式处理 `completed`、`incomplete`、`failed`，不能依赖 `[DONE]`；
- API Key 缺失时返回 `AI_PROVIDER_001`，不影响其他 Core API。

测试使用 Fake Provider 重放固定流，不访问真实 DeepSeek；真实 API 只做配置密钥后的人工冒烟验证。

## M4：扩展九个只读工具与角色化诊断

目标：在首个切片稳定后按领域逐组扩展。

顺序：

1. 库存：低库存、指定商品/仓库库存、预留来源；
2. 销售：订单状态、履约阻塞、已拣未发；
3. 采购质量：采购状态、待收货、质量隔离、待处置和待上架；
4. 制造：工单进度、待派工、待报工、待质检、待成品入库；
5. IoT：活动告警、离线设备和关联工序上下文；
6. 追溯与日报：跨域链路、部分来源失败提示、按需日报。

`generateDailyOperationReport` 只聚合本次用户有权调用的工具结果，不创建第二套报表事实；角色化待处理同样动态计算，不新增万能任务表。

## M5：真实 Vue 流式聊天页、审计页和导航卡

目标：替换当前 `DomainView` 占位页面，形成可操作但不越权的真实前端。

新增或修改：

- `frontend/src/api/ai.ts`
- `frontend/src/types/ai.ts`
- `frontend/src/composables/useAiStream.ts`
- `frontend/src/views/ai/AiChatView.vue`
- `frontend/src/views/ai/ToolAuditView.vue`
- `frontend/src/views/ai/components/AiMessage.vue`
- `frontend/src/views/ai/components/AiToolProgress.vue`
- `frontend/src/views/ai/components/AiNavigationActions.vue`
- `frontend/src/router/aiNavigationMap.ts`
- `frontend/src/router/index.ts`

交互：

- 发送后立即建立助手消息气泡，并展示阶段状态；
- 对 `delta` 做短缓冲后批量追加，避免每个字符触发 Vue 重绘；
- 流式阶段按纯文本安全展示，完成后再做受控排版；
- 工具卡只展示工具名、状态、耗时和安全摘要；
- `actions` 事件到达后展示按钮，点击先显示确认文案，再通过动作编码映射到正式命名路由；
- 任意未知动作编码、外部 URL 或缺失实体 ID 都在前端拒绝；
- 支持停止生成、保留中断内容、复制 `request_id` 和重新提问；
- 页面具备加载、流式、完成、空数据、无权限、模型不可用和连接中断状态。

## M6：页面上下文入口与阶段验收

目标：从高价值业务详情页进入 AI，并完成角色、租户和断流回归。

首批上下文入口：

- 销售订单详情；
- 采购订单详情；
- 生产工单详情；
- 设备和告警详情；
- 库存余额或预留明细；
- 跨域追溯页面。

页面只传明确的业务对象类型、UUID 和页面编码，不传整个页面状态。AI 页面接收上下文后生成建议问题，但不会自动发起模型请求。

## 5. API 目标清单

- `POST /api/ai/chat`
- `POST /api/ai/chat/stream`
- `GET /api/ai/capabilities`
- `GET /api/ai/chat-sessions`
- `GET /api/ai/chat-sessions/{sessionId}/messages`
- `GET /api/ai/tool-audit-logs`
- `POST /api/ai/tools/queryLowStock`
- `POST /api/ai/tools/queryInventoryByProductAndWarehouse`
- `POST /api/ai/tools/querySalesOrderStatus`
- `POST /api/ai/tools/queryPurchaseOrderStatus`
- `POST /api/ai/tools/queryWorkOrderProgress`
- `POST /api/ai/tools/queryQualityStatistics`
- `POST /api/ai/tools/queryDeviceAlarm`
- `POST /api/ai/tools/queryTrace`
- `POST /api/ai/tools/generateDailyOperationReport`

工具查询使用 POST 只是为了提交结构化查询条件，不代表业务写操作。

## 6. 验证方案

### 后端

- 工具权限矩阵：AI 总权限与各领域查看权限组合；
- 两租户隔离：会话、消息、审计、实体查询和追溯逐项验证；
- Provider Fake 流：文本增量、function call、部分成功、超时、断流和失败；
- 导航动作：未知动作、无权限对象、跨租户对象和缺少目标权限时不返回按钮；
- 写操作防御：模型请求任意写工具时统一拒绝并审计；
- 不变量：AI 请求前后关键业务表数量和状态不变；
- PostgreSQL 12.1 迁移测试。

命令：

```powershell
cd backend
D:\ruanjian\apache-maven-3.9.1\bin\mvn.cmd -pl platform-core -am test
```

### 前端

- SSE 分帧与跨 chunk JSON 解析；
- `delta` 累积、停止生成、失败保留和重试；
- 动作编码白名单与路由参数校验；
- 未知动作和外部 URL 拒绝；
- AI 页面与审计页生产构建；
- 销售、仓库、生产质检和 IoT 角色浏览器回归。

命令：

```powershell
cd frontend
npm run test:unit
npm run build
npm run test:e2e:golden
```

### 真实模型冒烟

仅在本地安全注入 `WMS_AI_API_KEY` 后执行：

- 纯文本流式回答；
- 单工具调用；
- 多工具调用；
- 用户要求修改库存时拒绝并给出人工页面入口；
- DeepSeek 不可用时 AI 页面降级且其他业务正常。

真实密钥不得写入命令输出、测试报告、Git 或截图。

## 7. 实施顺序与完成门槛

核心依赖按 `M0 → M1 → M2 → M3` 推进；M3 协议稳定后可提前完成 M5 的聊天壳和流式交互，用于尽早验证端到端协议，再按 `M4 → M5 补齐 → M6` 收口。每个里程碑至少满足：

- 对应规格已同步；
- 代码只修改该里程碑必要范围；
- 自动化测试覆盖主要成功与拒绝路径；
- 未把模型输出当作权限或业务事实；
- 未修改任何既有核心状态机或库存不变量；
- 未提交 Git commit。

首轮实施先完成 M0，并开始 M1/M2 的后端骨架；在首个销售履约纵向切片通过后，再批量扩展其他业务工具和真实页面。

## 8. 实施记录

### 2026-09-12 首个纵向切片

- M0：DeepSeek、SSE、页面上下文、导航动作与安全边界规格已同步；
- M1：已增加 V9 会话/消息/工具审计迁移、服务端环境配置、会话消息存储和脱敏工具审计；历史会话与审计查询接口尚未实现；
- M2：已实现服务端工具注册表、权限裁剪、执行审计、动作白名单及首个 `queryTrace` 工具；销售/库存组合诊断等其余工具待 M4 扩展；
- M3：已实现模型无关端口、DeepSeek Responses SSE 解析、function call 循环、轮次/调用次数限制和聊天流式 Controller；真实密钥冒烟未执行；
- M5：已替换 `/ai` 与 `/ai/chat` 占位页，完成前端 SSE 分帧、短缓冲渲染、停止生成、工具进度、来源元数据和确认式导航；审计页与历史会话待继续；
- 当前自动验证：阶段 8 后端聚焦测试 13 项通过，Core/Shared 全量测试 190 项通过，前端单元测试 17 项通过，前端生产构建通过。

## 9. 剩余任务执行单

以下任务是后续实施的唯一顺序入口。每个任务完成后先执行该任务的聚焦验证，再进入下一项；若触及状态机、库存写服务或 IoT 写入链路，立即停止并重新评审，因为阶段 8 只允许新增只读适配。

### AI-01：补齐会话与审计治理闭环

目标：让当前已经落库的会话、消息和工具审计能够被安全查询，并补齐中断、超时与失败语义。

主要改动：

- 扩展 `AiConversationStore` 与 `PostgresAiConversationStore`，增加当前用户会话分页、消息分页和租户/用户双重约束；
- 扩展 `AiToolAuditStore` 与 `PostgresAiToolAuditStore`，增加当前租户范围内的脱敏审计分页查询；
- 新增会话、消息、审计查询 DTO 与 Controller；
- `AiToolExecutor` 增加服务端工具超时控制，明确写入 `Timeout`；
- 浏览器主动停止或连接中断时，将助手消息收口为 `Interrupted`，不误记为 `Failed` 或 `Completed`；
- 若 V9 已在任何环境应用，后续字段调整只能新增 V10，禁止改写已应用迁移。

接口：

- `GET /api/ai/chat-sessions`
- `GET /api/ai/chat-sessions/{sessionId}/messages`
- `GET /api/ai/tool-audit-logs`

验收：

- 普通用户只能读取当前租户、当前用户自己的聊天正文；
- `ai:audit:view` 只能读取当前租户脱敏工具记录，不因此获得其他用户完整聊天正文；
- 成功、拒绝、失败、超时和中断状态均有自动化测试；
- 分页大小、时间范围和筛选字段有上限，非法参数返回稳定 AI 错误码。

### AI-01B：统一业务操作审计与 AI 操作历史查询

目标：让 AI 在读取当前业务事实之外，能够回答“哪个账号在什么时间对哪个对象执行了什么操作，以及结果如何”。

架构边界：

- Auth、Core、IoT 分别保存本服务拥有的操作审计，禁止四个服务共同写一张万能审计表；
- 首批只实现 `core_operation_audit_log` 和销售订单代表性切片，稳定后再覆盖 Core 其他领域、Auth 管理操作和 IoT 管控操作；
- 业务表、库存流水、工序事件和设备告警仍是业务事实源，操作审计只补充操作者、会话、请求、动作和结果，不成为第二套业务账；
- 成功审计与本地业务事务一致提交；幂等重放不得重复生成成功记录；失败/拒绝审计使用独立受控路径保存；
- 系统只能识别账号、用户 ID 和会话，企业共享账号时不得声称识别了实际自然人。

结构化字段：

- `tenant_id`、`actor_type`、`actor_id`、`actor_account`；
- `session_id`、`request_id`、`action_code`；
- `entity_type`、`entity_id`、`entity_no`；
- `before_status`、`after_status`、`operation_summary`、`changed_fields_summary`；
- `result`、`error_code`、`error_reason`、`occurred_at`；
- `idempotency_key_hash` 只保存摘要，不保存原始幂等键。

记录范围：

1. 必记：创建、修改、提交、审核、确认、完成、取消、库存变化、告警处理和权限调整；
2. 按需记录：敏感信息查看与导出；
3. 不进入业务审计：普通页面点击、展开、筛选等前端交互。

AI 工具：

- 新增 `queryOperationAudit`，只允许按已登记实体类型、实体 UUID、时间范围和数量上限查询；
- 工具先验证当前用户对目标实体的领域查看权限，再查询当前租户操作历史；
- 返回脱敏的操作时间线、来源摘要和实际时间范围，不向模型发送请求体、Authorization、密码、设备凭证或原始幂等键；
- AI 回答必须把“当前状态事实”和“历史操作记录”分开表述。

首批验收：

- 销售订单创建、修改、提交、审核和人工完成成功后各形成一条结构化审计；
- 相同幂等键成功重放不重复生成成功审计；
- 租户 A 无法查询租户 B 的记录；无销售查看权限时 `queryOperationAudit` 返回拒绝且不泄露对象是否存在；
- AI 能回答账号、会话、动作、前后状态、时间和结果，但共享账号场景只声明账号身份；
- 审计写入前后销售状态机和库存事实不变量保持不变。

### AI-02：完成销售履约阻塞诊断纵向切片

目标：优先完成已约定的核心问题——“这张销售订单为什么不能发货”。

新增工具：

- `querySalesOrderStatus`
- `queryInventoryByProductAndWarehouse`
- 保留并组合现有 `queryTrace`

只读依赖：

- `SalesOrderApplicationService.detail/page`
- `InventoryQueryService` 或 `InventoryApplicationService` 已有查询入口
- `TraceabilityApplicationService`

实现要求：

- 支持 UUID；若支持业务单号，必须通过销售应用服务解析，AI 不得直接查销售表；
- 返回订单状态、行数量、预留、已拣、已发、相关库存与上下游追溯摘要；
- 阻塞原因由真实数量与状态组合解释，不在 Prompt 中另建状态机；
- 只能生成用户有权访问的订单详情、履约工作台、库存余额和追溯动作；
- 不向 AI 注册拣货、发货、完成订单等写函数。

验收场景：

- 库存不足、未拣货、已拣未发、订单已完成、无权查看五种场景；
- 销售角色只能得到查看与建议，不得到仓库确认能力；
- AI 调用前后订单、库存余额、预留和流水完全不变；
- Fake Provider 完成“模型选工具 → 工具返回事实 → 模型解释 → 导航动作”全链回归。

### AI-03：按领域补齐其余只读工具

目标：在 AI-02 稳定后，按事实依赖分批补齐一期九个工具，避免一次性跨域扩张。

执行顺序：

1. 库存：`queryLowStock`，复用库存查询入口并限制仓库、产品和返回数量；
2. 采购：`queryPurchaseOrderStatus`，复用 `PurchaseOrderApplicationService`；
3. 质量：`queryQualityStatistics`，复用 `PurchaseQualityApplicationService` 和已有质量事实；
4. 制造：`queryWorkOrderProgress`，组合工单、派工、工序执行、报工、质检和成品入库只读入口；
5. IoT：`queryDeviceAlarm`，通过 `IotFactsPort`/受控 HTTP Facts 边界读取，不注入 IoT Repository；
6. 日报：`generateDailyOperationReport`，只组合本次已授权工具结果，不单独建立日报事实表。

每个工具必须同时交付：

- 独立参数 JSON Schema、字段长度/数量/时间范围限制；
- 领域查看权限集合和拒绝测试；
- 来源摘要、实际数据时间、部分来源失败说明；
- 可选导航动作及其权限测试；
- 成功、空数据、无权限、非法参数、领域服务异常五类测试。

### AI-04：补齐历史会话与审计页面

目标：在现有聊天壳基础上完成可日常使用的会话恢复和审计查看。

前端范围：

- `frontend/src/api/ai.ts`：增加会话、消息和审计分页 API；
- `frontend/src/types/ai.ts`：增加分页与审计视图类型；
- `frontend/src/views/ai/AiChatView.vue`：增加会话列表、恢复历史、复制请求编号和重新提问；
- 新增 `frontend/src/views/ai/ToolAuditView.vue`：筛选工具、状态、时间和请求号；
- `frontend/src/router/index.ts`：把 `/ai/audit` 从占位页替换为真实页面，并要求 `ai:audit:view`；
- 保留现有 `AiNavigationActions` 白名单，不接受服务端 URL 或模型文本路由。

验收：

- 刷新后能够恢复自己的会话；
- 无审计权限不显示审计入口，直接访问也被路由与后端共同拒绝；
- 审计页不展示原始密钥、认证头、设备凭证和未脱敏大对象；
- 加载、空数据、无权限、接口失败和分页状态完整。

### AI-05：增加业务页面上下文入口

目标：让用户从业务对象出发询问 AI，但不自动请求、不上传整页状态。

首批页面：

- 销售订单详情；
- 采购订单详情；
- 生产工单详情；
- 设备详情与设备告警详情；
- 库存余额/预留页；
- 跨域追溯页。

统一行为：

- 页面按钮只导航到 `/ai/chat`，携带 `entityType`、`entityId`、`entryPageCode`；
- AI 页面根据上下文生成建议问题，等待用户确认发送；
- 后端只接受已登记实体类型，工具仍重新校验租户、实体可见性和领域权限；
- 返回原业务页面依赖路由历史，不在聊天记录保存任意来源 URL。

### AI-06：故障、安全与端到端收口

目标：证明 AI 是核心业务之外的可降级只读能力，不影响黄金业务闭环。

自动验证：

- DeepSeek 未启用、密钥缺失、401/429/5xx、超时、非法 SSE、缺少终止事件；
- 单工具、多工具、超过轮次、超过调用次数、模型请求未知工具；
- 两租户会话/消息/审计隔离和各角色权限矩阵；
- 浏览器跨 chunk SSE、停止生成、断流保留、未知动作拒绝、确认式导航；
- AI 请求前后销售、采购、库存、制造和 IoT 关键事实不变量对账；
- Core AI 故障时，既有订单、库存、制造、IoT 和看板接口仍正常。

真实 DeepSeek 冒烟：

- 仅在受保护进程注入 `WMS_AI_API_KEY`；
- 验证纯文本、单工具、多工具、拒绝写操作、模型不可用降级；
- 不在日志、截图、报告和 Git 中记录密钥或完整 Authorization；
- 实际模型名以当次 Provider 响应和 `/models` 可用结果为准，并随消息与审计记录。

完成门槛：

- 后端全量测试、前端单元测试与生产构建通过；
- 阶段 8 浏览器角色回归通过或对外部依赖形成明确门禁记录；
- 九个只读工具、三个查询治理接口、聊天页、审计页和上下文入口全部有代码证据；
- 规格、原型、README 与实际实现状态一致；
- 没有 Git commit，由用户统一检查和提交。

## 10. 执行批次与停点

| 批次 | 包含任务 | 本批可独立验收的结果 | 完成后停点 |
| --- | --- | --- | --- |
| A | AI-01 | 会话、消息、AI 工具审计和中断/超时治理闭环 | 提交测试结果与接口清单，等待检查 |
| B | AI-01B | Core 操作审计基础、销售订单切片与 `queryOperationAudit` | 校验事务、幂等、租户和领域权限 |
| C | AI-02 | 销售订单不能发货的真实诊断闭环 | 用固定事实样本演示回答与动作 |
| D | AI-03 | 其余领域工具按库存→采购质量→制造→IoT→日报完成 | 每个领域完成即跑权限与空数据测试 |
| E | AI-04 | 历史会话和审计真实页面 | 前端构建和角色权限检查 |
| F | AI-05 | 业务详情页上下文入口 | 检查不上传整页、不自动发问 |
| G | AI-06 | 故障、安全、真实模型与端到端收口 | 输出阶段 8 验收证据，不自动提交 |

建议先完成批次 A，再进入已确认的批次 B。批次 B 只在销售应用服务成功事务中追加审计，不改变任何销售状态迁移；扩展到采购、库存、制造、Auth 和 IoT 前必须分别核对对应领域契约。
