# 阶段 8 AI 助手 Open WebUI、知识库与只读工具演进实施计划

> 日期：2026-09-12  
> 状态：执行中（Windows Open WebUI、硅基流动模型、Knowledge、六类角色模块问答、真实只读工具、SSE 工具进度、来源摘要和关键权限拒绝链路已连通；完整故障与全组合矩阵待收口）  
> 关联计划：`docs/superpowers/plans/2026-09-12-stage-8-ai-assistant-implementation-plan.md`  
> 变更与验证摘要：`docs/verification/2026-09-12-13-阶段8-AI与OpenWebUI周末变更总结.md`  
> 适用范围：`ai_learn_developProject` 阶段 8 AI 助手后续演进

## 1. 计划目标

在保留 WMS 自有 Vue AI 页面、Spring Security、多租户边界和业务事实源的前提下，引入自托管 Open WebUI，形成以下能力：

- DeepSeek V4 Flash 不再只生成文本，而是由 Open WebUI 驱动读取受控项目知识、选择 WMS 查询工具、执行多轮查询并综合回答；
- Open WebUI 只作为模型、知识和 Agent 工具编排平台，不作为最终业务页面，也不成为 WMS 业务事实来源；
- AI 查询继承当前 WMS 登录用户的租户、会话和领域查看权限；
- 在用户原有权限之上增加不可突破的 AI 查询工具白名单，即使用户本身拥有新增、修改或删除权限，AI 也不能调用写接口；
- AI 第一阶段继续严格只读，不提供页面跳转按钮，不创建、修改、提交、审核、确认、完成或删除任何业务数据；
- WMS 保留聊天记录、来源摘要、工具调用审计和业务操作审计；Open WebUI 中间会话不作为正式审计依据。

## 2. 已确认的设计决策

### 2.1 产品职责

```text
WMS Vue
  └─ AI 对话、流式展示、来源和工具摘要

Spring Boot Core AI
  ├─ WMS 用户认证、租户与权限上下文
  ├─ 对外 `/api/ai/chat` 与 `/api/ai/chat/stream`
  ├─ Open WebUI 调用适配
  ├─ WMS AI 查询工具真实执行
  ├─ 查询白名单、参数校验和结果脱敏
  └─ 会话、消息与工具调用审计

Open WebUI
  ├─ 硅基流动模型连接与 `wms-assistant` 模型预设
  ├─ `docs/ai-knowledge/` 知识检索
  ├─ 查询工具选择
  └─ 多轮 Agent 调用编排

WMS 领域应用服务
  └─ 返回订单、库存、制造、质量、IoT、追溯和操作审计事实
```

### 2.2 暂停页面跳转能力

- 本阶段不返回 `NavigationAction`，不展示 AI 页面跳转按钮；
- AI 可以用文本说明涉及角色、主责角色、协同角色和建议处理顺序；
- 一个问题涉及多个角色时，由用户根据文字建议自行进入相应模块；
- 已实现的导航策略和前端组件先做兼容性下线，不在本计划中直接删除，确认无其他调用方后再决定保留或移除；
- 规格、原型、接口响应和验收项必须同步移除“本阶段必须提供导航按钮”的要求。

### 2.3 第一阶段继续只读

- Open WebUI 不注册通用 HTTP、Shell、文件系统、代码执行或数据库工具；
- 不向模型提供任意 URL、任意 OpenAPI 文档或整个 WMS API；
- 只注册由 WMS 代码固定定义、经过安全评审的查询工具；
- 写操作后续如需开放，必须另立方案，采用“生成操作草案 → WMS 预校验 → 用户明确确认 → 现有业务命令接口执行”的路径，不能在本计划中提前注册写工具。

### 2.4 权限取交集

AI 实际可用工具必须同时满足：

```text
当前用户具有 `ai:chat:query`
∩ 当前用户具有目标领域查看权限
∩ 工具存在于服务端固定注册表
∩ 工具存在于部署配置启用名单
∩ 本次请求未进一步禁用该工具
∩ 目标数据属于当前租户且在用户数据范围内
```

配置只能缩小代码注册表，不能通过填写任意路径、HTTP 方法或 URL 扩大能力。

## 3. 知识库范围与目录

新增受控知识目录：

```text
docs/ai-knowledge/
├─ README.md
├─ 00-项目边界与事实源.md
├─ 10-统一术语.md
├─ 20-角色职责与权限.md
├─ 30-黄金业务闭环.md
├─ 40-状态与阻塞规则.md
├─ 50-常见问题与处理建议.md
├─ 60-AI工具目录.md
└─ knowledge-manifest.yaml
```

约束：

- `docs/specs/` 和实际代码仍是正式事实源，`docs/ai-knowledge/` 是面向模型整理的受控知识视图；
- 不直接索引整个仓库，不摄入历史计划、废止规则、构建产物、密钥、数据库备份和运行日志；
- 每个知识文档记录来源文件、适用阶段、更新时间和维护责任；
- `knowledge-manifest.yaml` 记录允许同步到 Open WebUI 的文件、版本摘要和启用状态；
- 业务规则改变时先修改正式领域规格，再同步知识文档，禁止只改 AI 知识副本；
- 第一版使用 Open WebUI 自有持久化与知识能力，不让 Open WebUI 直连 WMS PostgreSQL，也不在本项目中自建 MinIO、pgvector 或第二套业务数据库；
- 本决策使“知识库”从二期候选提前进入阶段 8，实施前必须同步修订项目级计划和 AI 领域规格中的原一期边界。

## 4. AI 查询白名单设计

### 4.1 固定注册表与配置启用名单

服务端代码继续以 `AiToolRegistry` 作为不可突破的工具上限。每个工具固定声明：

- `toolCode`；
- 中文用途；
- 参数 JSON Schema；
- 对应 WMS AI Tool 端点；
- 必需领域查看权限；
- 最大结果数量、时间范围和超时；
- 返回字段脱敏策略；
- 允许的数据来源。

部署配置只按工具编码启停：

```yaml
platform:
  ai:
    enabled-tools:
      - queryTrace
      - queryOperationAudit
      - querySalesOrderStatus
      - queryPurchaseOrderStatus
      - queryInventoryByProductAndWarehouse
      - queryWorkOrderProgress
      - queryQualityStatistics
      - queryDeviceAlarm
      - queryLowStock
      - generateDailyOperationReport
```

禁止出现以下配置能力：

- `/api/**` 等宽泛路径；
- 用户或模型提交任意 URL；
- 仅根据 GET/POST 判断读写性质；
- 将未经注册的业务 Controller 自动导出为 Tool；
- 运行时从互联网导入社区 Tool 或 Function。

### 4.2 AI Tool 专用端点

Open WebUI 只能看到单独发布的 AI 查询 OpenAPI 文档，例如：

```text
GET  /v3/api-docs/ai-tools
POST /internal/ai/tools/queryTrace
POST /internal/ai/tools/queryOperationAudit
```

这里的 POST 只用于提交结构化查询条件，不表示业务写入。工具端点内部必须调用现有领域 ApplicationService/Facts 端口，禁止直连 Mapper、Repository 或拼接 SQL。

## 5. WMS 用户身份委托方案

### 5.1 禁止共享业务身份

- Spring Boot 调用 Open WebUI 可以使用独立的 Open WebUI API Key，但该 Key 只表示模型平台调用身份，不能代表 WMS 用户；
- Open WebUI 调用 WMS Tool Server 时使用独立的服务间认证凭据；
- 不能把固定 Open WebUI 用户、固定 AI 服务账号或固定管理员 Token 当成全部 WMS 用户；
- 不能把原始 WMS JWT、权限集合、`tenant_id` 或 `user_id` 作为模型可控制的工具参数。

### 5.2 服务端上下文映射

推荐采用“Open WebUI 会话标识 + WMS 服务端短期上下文”的方式：

1. WMS `/api/ai/chat/stream` 先通过现有 Spring Security 恢复当前用户、当前 `jti` 和租户；
2. WMS 创建本次 Open WebUI 临时 chat/message，并取得其 `chat_id`、`message_id`；
3. WMS 在 Redis 保存短期映射：

   ```text
   Open WebUI chat_id/message_id
       → WMS request_id、user_id、tenant_id、jti、允许工具集合、过期时间
   ```

4. Open WebUI 调用工具时携带固定服务认证，并通过连接模板转发 `X-WMS-OpenWebUI-Chat-Id`、`X-WMS-OpenWebUI-Message-Id`；
5. WMS 只把这两个 Header 当关联键，不把它们单独当认证凭据；
6. WMS 同时校验服务间认证、Redis 映射、映射有效期、当前有效 `jti`、用户实时权限、工具白名单和租户数据范围；
7. 工具调用结束后写入 `ai_tool_audit_log`；聊天完成后删除或短期保留映射，超时记录明确失败状态。

服务间认证优先复用项目已有受控签名模式；若当前模式不能满足重放保护，再增加 AI Tool 专用 HMAC/短期服务凭据。真实凭据只能来自运行环境。

### 5.3 Open WebUI 会话保留策略

- WMS 的 `ai_chat_session`、`ai_chat_message` 是面向用户和审计的正式会话记录；
- Open WebUI chat 仅为完成原生多轮工具调用而创建，不作为业务会话事实；
- 第一版优先每次 WMS 请求创建临时 Open WebUI chat，并显式发送经过裁剪的 WMS 历史上下文；
- 请求完成后尽力删除临时 chat，失败时进入清理任务和运维告警；
- Open WebUI 数据卷不得保存 WMS Token、数据库凭据或未经脱敏的完整业务对象。

## 6. 与当前代码的演进关系

当前仓库已经实现 `AiChatOrchestrator`、`AiModelClient`、`DeepSeekResponsesClient`、`AiToolRegistry`、`AiToolExecutor`、SSE 页面和首批查询工具。本计划不把这些事实描述成“尚未开始”。

引入 Open WebUI 后必须避免两套 Agent 循环同时运行：

- WMS 继续拥有对外聊天契约、认证、会话、审计、白名单和工具执行；
- Open WebUI 接管模型预设、知识检索、工具选择和模型—工具多轮循环；
- 现有 `AiChatOrchestrator` 调整为 Open WebUI 调用协调器或拆分公共会话/审计逻辑；
- `DeepSeekResponsesClient` 只作为早期诊断回退代码保留；默认路径为 Open WebUI，不能与其同时执行；
- `AiToolRegistry` 和 `AiToolExecutor` 保留，并继续作为 WMS 最终安全边界；
- `AiNavigationPolicy`、`NavigationAction` 和前端导航组件从本阶段输出链路下线，但不在未确认引用关系前直接删除。

## 7. 分阶段实施

### OWUI-00：规格与范围同步

目标：先把新决策写入长期事实源，避免代码、计划和验收继续描述不同架构。

同步检查和修改：

- `docs/specs/00-project/正式项目计划.md`
- `docs/specs/00-project/项目概述.md`
- `docs/specs/00-project/架构设计.md`
- `docs/specs/00-project/原型与交互说明.md`
- `docs/specs/00-project/阶段决策与续聊入口.md`
- `docs/specs/50-ai-assistant/概述.md`
- `docs/specs/50-ai-assistant/领域模型.md`
- `docs/specs/50-ai-assistant/接口契约.md`
- `docs/specs/50-ai-assistant/验收标准.md`
- `docs/specs/50-ai-assistant/AI助手业务规则.md`
- `docs/prototype/README.md`
- `backend/README.md`
- `frontend/README.md`

验收：所有文档统一说明 Open WebUI 定位、知识库提前范围、无导航按钮、严格只读和双重白名单。

### OWUI-01：最小可行性验证

目标：在改造现有编排器之前证明 Open WebUI 可以满足关键链路。验证代码与配置保持隔离，不先接真实核心写服务。

必须逐项验证：

1. Open WebUI 通过硅基流动 OpenAI 兼容 API 调用目标 DeepSeek 模型；
2. 原生模式能够完成“模型选工具 → 服务端执行 → 读取结果 → 再选工具 → 最终回答”的多轮循环；
3. WMS 后端能够获得最终文本增量或构造兼容现有 SSE 契约的进度事件；
4. API 模式能够使用绑定的 Knowledge；
5. Open WebUI 能向外部 OpenAPI Tool Server 转发 chat/message 标识；
6. WMS 能使用服务认证和 Redis 映射恢复原 WMS 用户，并在用户退出或 `jti` 失效后拒绝调用；
7. 临时 Open WebUI chat 能够可靠清理；
8. DeepSeek 对中文 WMS 工具名称、参数和连续调用的正确率达到验收样例要求。

停点：任何一项不满足时暂停业务改造，记录差距并决定是补充可信桥接 Tool，还是保留 Spring `AiChatOrchestrator` 作为 Agent 执行器。不得退化为共享管理员身份。

### OWUI-02：部署与配置基础

目标：将 Open WebUI 作为 Windows 本地 Python 服务接入，默认关闭，不影响现有 WMS 启动。

涉及：

- `deploy/openwebui/install-windows.ps1`
- `deploy/openwebui/start-windows.ps1`
- `deploy/openwebui/windows.env.example`
- `deploy/README.md`、根 `README.md`
- `backend/platform-core/src/main/resources/application.yml`
- `AiProperties` 与配置校验

配置至少覆盖：启用开关、Open WebUI Base URL、API Key、模型预设 ID、超时、最大工具轮次、临时会话清理、服务签名、启用工具编码和知识库标识。密钥不得写入仓库。

验收：Open WebUI 未启动或不可用时，AI 返回稳定错误且订单、库存、制造和 IoT 接口继续正常。

### OWUI-03：建立受控知识集

目标：创建 `docs/ai-knowledge/`，并建立可重复的同步和版本核对流程。

步骤：

1. 从项目级规格和领域规格提炼首批知识文档；
2. 建立 `knowledge-manifest.yaml`；
3. 编写同步脚本或受控操作说明，只同步清单内文件；
4. 在 Open WebUI 建立 WMS Knowledge 并绑定到专用模型预设；
5. 通过含来源的问题验证检索效果；
6. 验证历史计划、密钥、日志和非授权文件无法被检索。

首批验收问题：库存数量语义、采购拒收与质量隔离区别、销售直接拣货时点、六类角色职责、AI 当前只读边界。

### OWUI-04：身份委托与工具网关

目标：完成服务认证、Redis 上下文映射和 AI Tool 专用安全链。

主要后端组件建议：

- `OpenWebUiClient`：创建临时会话、发送聊天、读取事件和清理会话；
- `AiInvocationContextStore`：保存短期 Open WebUI/WMS 请求映射；
- `AiToolServiceAuthenticationFilter`：验证 Open WebUI 服务来源；
- `AiToolInvocationContextResolver`：恢复 WMS 用户并重新检查有效会话；
- `AiToolAccessPolicy`：计算代码注册表与配置启用名单的交集；
- 现有 `AiToolExecutor`：继续完成领域权限、参数、超时、脱敏和审计。

验收：销售、仓库、生产质检、IoT 和多角色账号分别只能调用自己具备查看权限的工具；拥有删除权限的账号仍无法通过 AI 调用任何删除接口。

### OWUI-05：工具发布与多轮诊断

目标：仅向 Open WebUI 发布专用查询 OpenAPI 文档，逐步迁移现有和后续工具。

顺序：

1. `queryTrace`；
2. `queryOperationAudit`；
3. `querySalesOrderStatus`；
4. `queryInventoryByProductAndWarehouse`；
5. `queryPurchaseOrderStatus`；
6. `queryWorkOrderProgress`；
7. `queryQualityStatistics`；
8. `queryDeviceAlarm`；
9. `queryLowStock`；
10. `generateDailyOperationReport`。

每个工具必须完成成功、空数据、非法参数、无权限、跨租户、超时和审计测试后，才允许加入配置启用名单。

### OWUI-06：替换聊天内部编排并下线导航输出

目标：保持前端 `/api/ai/chat/stream` 契约稳定，将内部模型调用切换到 Open WebUI。

要求：

- 前端继续使用现有 WMS JWT，不接触 Open WebUI API Key；
- 保留 `meta`、`progress`、`tool_started`、`tool_finished`、`delta`、`done`、`error`；
- 移除 `actions` 事件及 `navigation_actions` 业务要求，或在兼容期固定返回空集合；
- 回答展示知识来源、业务事实来源、实际时间范围、工具摘要、模型编号和 `request_id`；
- 不展示模型思维链；
- 一个问题涉及多个角色时，回答按“当前状态、阻塞原因、主责角色、协同角色、建议顺序”组织。

### OWUI-07：安全、故障与验收收口

目标：证明 Open WebUI 是可降级的只读辅助系统，不扩大 WMS 权限，不影响黄金闭环。

重点验证：

- Prompt Injection 要求删除、调整库存、执行 SQL、访问文件或调用任意 URL；
- 模型构造未注册工具、伪造工具编码、扩大 `tool_whitelist`；
- 普通用户、拥有写权限的用户、租户管理员和多角色用户的权限交集；
- 跨租户 UUID、业务单号猜测和关联对象越权；
- 伪造 Open WebUI chat/message Header、过期映射、失效 `jti`、服务签名错误和请求重放；
- Open WebUI/硅基流动不可用、429、5xx、超时、半途中断和临时会话清理失败；
- AI 调用前后关键业务表、库存余额、预留、流水和状态完全不变；
- 工具成功、失败、拒绝和超时均产生脱敏审计。

## 8. 验证命令与证据

后端：

```powershell
cd backend
D:\ruanjian\apache-maven-3.9.1\bin\mvn.cmd -pl platform-core -am test
```

前端：

```powershell
cd frontend
npm run test:unit
npm run build
```

部署：

```powershell
cd deploy
docker compose config
docker compose up -d
```

真实模型和 Open WebUI 冒烟测试只能在本地安全注入密钥后进行。验收记录不得包含硅基流动 Key、Open WebUI Key、WMS JWT、服务签名原文或完整敏感业务载荷。

## 9. 分批交付与停点

| 批次 | 内容 | 独立验收结果 | 停点 |
| --- | --- | --- | --- |
| A | OWUI-00 | 长期规格与新决策一致 | 先检查范围变化 |
| B | OWUI-01 | Open WebUI API、Knowledge、多轮工具和身份关联可行 | 验证失败不进入业务改造 |
| C | OWUI-02～03 | 可选部署与受控知识集 | 检查知识来源和数据保留 |
| D | OWUI-04 | 用户身份委托与双重白名单生效 | 完成权限矩阵后再开放工具 |
| E | OWUI-05 | 十个只读工具逐项接入 | 每个工具单独验收 |
| F | OWUI-06 | 现有 WMS AI 页面切换 Open WebUI 且无导航 | 前后端流式回归 |
| G | OWUI-07 | 安全、故障和只读不变量通过 | 输出阶段验收证据 |

## 10. 完成定义

- WMS 用户无需登录 Open WebUI，也不会接触 Open WebUI 或硅基流动密钥；
- DeepSeek 能结合受控知识和实时 WMS 工具完成至少两轮查询分析；
- 用户权限、代码固定注册表、配置启用名单和租户数据范围全部生效；
- 任意拥有写权限的用户都不能通过 AI 调用写接口；
- Open WebUI 只读取清单内知识，不直连 WMS 数据库；
- AI 回答能区分“知识规则”和“实时业务事实”，并显示来源与时间范围；
- 页面没有导航动作或写操作入口；
- Open WebUI 或硅基流动故障不影响 WMS 核心业务；
- 规格、README、原型、代码和测试证据一致；
- 不自动提交 Git，由用户统一检查和提交。

## 11. 当前执行记录（2026-09-13）

已完成：

- 长期规格、原型说明和 README 已统一到“Open WebUI + 受控 Knowledge + WMS 只读工具、无导航按钮”的目标；
- 已创建 `docs/ai-knowledge/`、同步清单和只读校验脚本，当前 7 个清单文件通过路径、存在性、扩展名、常见密钥格式与 SHA-256 核对；
- 已在 Windows Python 3.12 独立虚拟环境安装固定版本 `open-webui==0.11.3`，运行目录位于用户本地应用数据目录，不影响 WMS 基础依赖；
- 已提供 Windows 安装、启动、内部密钥初始化和 Core 环境加载脚本；两项内部密钥已在 Git 忽略文件中本地生成且未输出原文；
- Open WebUI 当前仅监听 `127.0.0.1`，CORS 已收敛到本机地址，公开注册保持关闭，本地 API Key 功能已启用；
- `AiToolRegistry` 已实现“代码注册表 ∩ 部署启用名单 ∩ 当前用户权限 ∩ 请求缩小名单”；
- 已建立 Open WebUI 服务密钥、chat/message 短期 Redis 映射、活跃 JTI 复核、最新权限复取和最大工具调用次数限制；
- 已发布只包含十个已实现查询或汇总工具的 `/v3/api-docs/ai-tools`，没有向 Open WebUI 暴露整个 Core OpenAPI；
- `AiChatOrchestrator` 已支持 `open-webui` Provider，并按原生多轮 Agent 协议创建聊天、绑定上下文、轮询文本增量、回写 WMS 会话并尽力删除临时聊天；
- 工具执行器已增加独立超时上限、取消和 `Timeout` 脱敏审计；早期导航策略、路由映射和跳转组件已删除，兼容字段固定为空；
- `queryLowStock` 已实现租户隔离的专用聚合读模型，采用“可用库存小于安全库存”提示口径，不触发自动补货；
- 硅基流动模型编号已按官方接口配置为 `deepseek-ai/DeepSeek-V4-Flash`，基础地址为 `https://api.siliconflow.cn/v1`；
- 硅基流动 `/v1/models` 与最小 Chat Completions 已真实通过；Open WebUI `/api/models` 可见目标基础模型，`wms-assistant` 基础问答已返回实际模型编号和用量；
- Open WebUI 本地管理员、API Key、`wms` Tool Server、7 份受控 Knowledge 和 `wms-assistant` 模型预设已创建，Knowledge 检索验证返回 2 个来源和 1 次知识工具调用；
- 新版 Core 已在临时端口真实启动，PostgreSQL 12.1 Flyway 版本为 V10，`/v3/api-docs/ai-tools` 返回且仅返回 10 个 `/internal/ai/tools/**` 路径；
- 已新增 `configure-windows.ps1`，可在正式 Core 重启后幂等复核工具目录、Knowledge 文件数和模型预设，并把 Tool Server 切回正式 `10003`；
- Core 调用 Open WebUI 已固定使用 HTTP/1.1，消除了 JDK 默认 h2c 升级被本机 Uvicorn 拒绝而导致的 HTTP 400；
- IDEA 的 Core 与 IoT Run Configuration 已自动导入被 Git 忽略的 `windows.env`；初始化脚本会幂等生成两端配对的随机 Facts HMAC，且不覆盖已存在的模型/API Key；
- 正式 `10003` Core、`10004` IoT 和 `3000` Open WebUI 已重启并通过健康检查；销售角色登录后可见 8 个授权 AI 工具；
- 已真实跑通 WMS JWT → Gateway → Core → Open WebUI → DeepSeek → `querySalesOrderStatus` → WMS SSE：回答正确返回订单状态、已发货与未发货数量，`done` 同时返回实际工具名、业务来源和数据时间；
- 已真实跑通 `queryTrace` 的 Open WebUI 多轮调用和 Core → IoT HMAC Facts 链路；销售权限下返回 2 个可见节点，仅报告 4 个隐藏节点数量而不泄露内容；
- 已真实验证销售角色请求采购工具会被请求级白名单拒绝并写入 `Denied / AI_TOOL_002` 审计；成功、依赖失败和名单拒绝三类审计均已在 PostgreSQL 中核对；
- 已使用六个演示账号核对能力接口：管理员、销售、采购、仓库、生产质检、IoT 分别返回 10、8、8、10、10、4 个权限交集工具；同一销售账号二次登录后，旧 Token 返回 401、新 Token 返回 200；
- 多次真实问答后通过 Open WebUI API 核对，标题为 `WMS AI 临时问答` 的残留聊天数为 0，正常完成路径会主动清理临时 chat；
- 错误 Open WebUI 服务密钥稳定返回 401，正确服务密钥配合伪造 chat/message 关联返回 403；拒绝结果不会被误包装为 500；
- 已真实注入错误 Open WebUI 服务密钥和伪造 chat/message：分别稳定返回 401 与 403，均不能触发业务查询；
- Open WebUI 工具回调完成后只在 Redis 短暂保存工具名、来源、时间范围和状态，不缓存业务结果正文，并用于最终回答来源汇总；
- SSE 的 Servlet ASYNC 二次派发已避免重复业务鉴权，真实流式请求返回 200 且 Core 日志中 `AccessDeniedException` 与“response is already committed”均为 0；
- `platform-shared` 18 项、`platform-core` 215 项、`platform-iot` 70 项测试分别全绿；前端单元测试、类型检查和生产构建已通过，导航运行时输出固定为空。

尚未完成或受阻：

- 六类角色的能力目录和单角色主模块问答已真实验证；多角色账号、全部关联对象数据范围以及每个工具的跨租户组合仍需继续补齐；
- 被顶替 Token 的普通 AI 请求、错误服务密钥和伪造关联 ID 已完成真实拒绝验证；工具回调进行中的 JTI 失效、工具超时、硅基流动 429/5xx、Open WebUI 中断和临时聊天清理失败仍需做真实故障注入；
- 十个只读工具已注册并有自动化覆盖，但尚未逐个完成“成功、空数据、非法参数、无权限、跨租户、超时、审计”的全组合实机矩阵；
- 后端聚合 `mvn test` 本次停在 Auth 的外部 PostgreSQL 12 迁移夹具 `127.0.0.1:55432` 未启动；与本次改动相关的 Shared、Core、IoT 分模块全量测试均已通过，不能把缺失隔离夹具表述为代码测试通过。

## 12. 模块链条 AI 交互验证与完善（2026-09-14）

本轮只验证和完善 AI 交互链，不修改采购、库存、制造、质量和 IoT 的核心状态流转。所有问题均从 WMS JWT 用户入口发起，经 Gateway、Core、Open WebUI、DeepSeek 和受控工具返回，不以直接调用 Java 方法代替端到端证据。

### 12.1 真实模块问答矩阵

| 模块链条 | 登录角色 | 自然语言问题 | 模型实际选择工具 | 实机结果 |
| --- | --- | --- | --- | --- |
| 采购 | `buyer.chen` | 查询指定采购单状态及已收/待收数量 | `queryPurchaseOrderStatus` | 返回 `Completed`、订购 100、已收 100、待收 0，来源和完成时间完整 |
| 库存 | `wh.operator` | 查询指定产品在指定仓库的实物、预留和可用量 | `queryInventoryByProductAndWarehouse` | 返回 2 条授权库存余额，来源和最近库存事实时间完整 |
| 低库存 | `wh.operator` | 查询当前安全库存缺口 | `queryLowStock` | 返回 1 条低库存事实，安全库存 10、可用 9、缺口 1；浏览器页面同步显示工具卡 |
| 制造 | `mes.inspector` | 查询指定工单计划、报工、合格、不良、入库及阻塞 | `queryWorkOrderProgress` | 返回计划 20、报工 11、合格 10、不良 1、入库 10、无质量阻塞 |
| 质量 | `mes.inspector` | 查询最近 7 天质量统计 | `queryQualityStatistics` | 返回 4 笔、检验 205、合格 205、不合格 0，并携带统计区间 |
| IoT 告警 | `iot.engineer` | 查询最近 30 天设备告警 | `queryDeviceAlarm` | 当前租户无告警，工具明确返回零值事实，模型未按常识编造告警 |
| 综合日报 | `admin.zhang` | 生成今日库存、履约、制造、质量、设备和告警日报 | `generateDailyOperationReport` | 六段事实均返回；无数据段明确标零，库存陈旧时间由回答显式提示 |
| 销售、追溯、操作审计 | `sales.liu` 等 | 销售履约、跨链追溯和谁操作了销售订单 | `querySalesOrderStatus`、`queryTrace`、`queryOperationAudit` | 已在前一轮实机验证，继续保留为十工具完整覆盖证据 |

上述新调用均在 `ai_tool_audit_log` 核对为 `Success`，并与回答使用同一 `request_id`。AI 调用前后关键事实数量保持采购订单 3、采购明细 3、库存余额 15、工单 3、工单生命周期 3、生产质检 4；本轮未通过 AI 创建、修改或删除业务数据。

### 12.2 流式交互缺口与修复

实机复现发现 Open WebUI 路径虽然在 `done.tool_call_summary` 返回了工具名，但浏览器 SSE 缺少 `tool_started/tool_finished`，导致前端无法展示正在查询哪个模块。根因是 Core 只在 Open WebUI 后台任务结束后读取 Redis 工具结果，没有轮询并转发工具生命周期。

最小修复如下：

- `OpenWebUiToolController` 在受控工具开始、成功和失败时，仅记录工具名、随机调用标识、状态、来源与时间等非敏感元数据；
- `OpenWebUiAgentClient` 在轮询 Open WebUI 回答时同步读取新增观察结果，按顺序发出 `tool_started` 和 `tool_finished`；
- 开始和结束事件使用同一 `call_id`，前端现有 `useAiStream` 无需修改即可把运行中工具更新为成功或失败；
- 最终来源、时间和工具摘要只聚合 `Success` 观察结果，不把 `Running/Failed` 当成回答依据；
- 工具超时原先错误映射为 `AI_TOOL_001`，现已按正式契约改为 `AI_TOOL_003`，审计和外部错误保持一致。

修复后真实 SSE 顺序已验证为：

```text
meta -> progress -> tool_started -> tool_finished -> delta -> done
```

采购工具开始与结束使用同一 `call_id`，最终仍返回 `queryPurchaseOrderStatus`、采购事实来源和实际完成时间。Playwright 浏览器实机验证中，仓库角色页面显示“查询低库存 / 已完成”、来源摘要、数据时间、工具名和请求编号。

### 12.3 本轮自动化证据

- 工具生命周期映射、上下文元数据、工具回调入口和超时错误码定向测试：16 项通过；
- 真实采购问答修复后事件序列通过；
- 真实低库存浏览器页面通过；
- `platform-shared` 全量 18 项、`platform-core` 全量 215 项通过，均为 0 失败、0 错误、0 跳过；
- 前端单元测试 15 项通过，生产构建成功；
- 代码差异检查无空白错误；仍需继续完成本计划列出的故障注入和全组合实机矩阵。
