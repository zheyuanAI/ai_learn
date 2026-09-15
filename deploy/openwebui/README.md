# Open WebUI Windows 本地部署与阶段 8 验证

> 固定版本：`open-webui==0.11.3`  
> 本地地址：`http://127.0.0.1:3000`  
> Python：本机已确认 3.12；运行环境与数据默认位于 `%LOCALAPPDATA%\AiLearnWms\openwebui`

## 安全定位

- Open WebUI 只作为硅基流动 DeepSeek V4 Flash、受控知识和 WMS 查询工具的 Agent 编排平台；
- WMS 用户不直接登录 Open WebUI，也不接触 Open WebUI 或硅基流动 Key；
- 不启用 Open Terminal、代码执行、联网搜索、通用 HTTP、文件系统或社区 Tool；
- 不把整个项目目录交给模型，只导入 `docs/ai-knowledge/knowledge-manifest.yaml` 启用的 Markdown；
- Open WebUI Tool 连接只能指向 Gateway 从唯一 YAML 白名单生成的裁剪 OpenAPI，不能导入完整 WMS API。

## 1. 安装与本地密钥

在 PowerShell 中执行：

```powershell
cd deploy\openwebui
powershell -ExecutionPolicy Bypass -File .\install-windows.ps1
```

脚本从官方 PyPI 安装固定版本，并创建被 Git 忽略的 `windows.env`。其中以下本地密钥由脚本自动生成，不需要用户提供：

- `OPENWEBUI_SECRET_KEY`；
- `WMS_AI_TOOL_SERVICE_SECRET`；
- 成对且值相同的 `CORE_FACTS_IOT_HMAC_SECRET` 与 `IOT_INTERNAL_S7_HMAC_SECRET`，仅用于 Core → IoT 只读追溯 Facts。

对旧版 `windows.env` 再次执行 `initialize-windows-config.ps1` 会只补缺失项，不覆盖已有模型 Key；若两端已有 Facts HMAC 却不一致，脚本会停止并要求先修正。

用户只需打开 `windows.env` 填写：

```text
SILICONFLOW_API_KEY=你的硅基流动 API Key
```

建议在第一次启动 Open WebUI 前填写。Open WebUI 会把连接配置持久化到自己的本地数据库；如果已经在 Key 为空时启动过，则创建管理员后还需在“管理员面板 → 设置 → 连接”中新增或更新 OpenAI 兼容连接，Base URL 填 `https://api.siliconflow.cn/v1`，Key 填硅基流动 API Key。只修改 `windows.env` 不会覆盖已经持久化的连接配置。

真实密钥不得出现在 Git、终端截图、普通日志、测试报告或聊天记录中。

## 2. 启动和初始化管理员

```powershell
powershell -ExecutionPolicy Bypass -File .\start-windows.ps1
```

浏览器访问 `http://127.0.0.1:3000`，创建首个本地管理员账号。确认 OpenAI 兼容连接的 Base URL 为 `https://api.siliconflow.cn/v1`，基础模型为 `deepseek-ai/DeepSeek-V4-Flash`。Base URL 不要重复追加 `/chat/completions`。

随后在管理员账号设置中创建 Open WebUI API Key，把它写入本地 `windows.env`：

```text
WMS_OPENWEBUI_API_KEY=刚创建的 Open WebUI API Key
```

该 Key 只供 Core 调用本机 Open WebUI，不代表任何 WMS 用户。
启动脚本会显式启用 Open WebUI API Key 功能；服务只监听 `127.0.0.1`，该 Key 不得复制到前端或提交到 Git。
Core 对每次 WMS 工具查询默认设置 10 秒上限，超时会中断等待、返回稳定错误并记录 `Timeout` 审计；可在本地配置中缩短该值，但不建议随意放大。

## 3. Knowledge 同步

先校验受控清单：

```powershell
powershell -ExecutionPolicy Bypass -File .\validate-knowledge.ps1
```

在 Open WebUI 中创建 WMS Knowledge，只导入清单中 `enabled: true` 的文件，并绑定到专用模型预设 `wms-assistant`。本地文件存在不等于已进入知识库，必须完成导入和绑定。

当新版 Gateway、Core、IoT 已经启动后，可使用幂等配置脚本自动校验裁剪 OpenAPI、创建或更新 Tool Server、首次导入 Knowledge 并创建模型预设：

```powershell
powershell -ExecutionPolicy Bypass -File .\configure-windows.ps1
```

脚本默认连接 Gateway `http://127.0.0.1:20001`；临时验证实例可通过 `-GatewayBaseUrl` 指定。已存在且文件数正确的 Knowledge 不会重复上传；文件数异常时脚本会停止，避免静默覆盖知识数据。

首批核对问题：

- 收货前拒收与质量隔离有什么区别？
- 上架为什么不增加企业总库存？
- 销售直接拣货和发货分别在什么时候改变库存？
- 采购质量退回涉及哪些角色，处理顺序是什么？
- AI 当前能否删除订单或调整库存？

## 4. WMS Tool Server 与唯一白名单

业务 API 只在以下文件维护一次：

```text
deploy/openwebui/wms-ai-api-whitelist.yml
```

文件按业务模块分组，角色权限不写入清单；每个操作必须使用精确 method + path。Gateway 读取该文件并发布：

```text
http://127.0.0.1:20001/v3/api-docs/ai-tools/core
http://127.0.0.1:20001/v3/api-docs/ai-tools/iot
```

`configure-windows.ps1` 会自动创建 `wms_core`、`wms_iot` 两个 Tool Server，并移除旧 `wms*` 配置后重新绑定模型预设，不需要在 Open WebUI 中维护第二份接口清单。

连接只配置以下固定 Header：

```json
{
  "X-WMS-AI-Service-Key": "<与 windows.env 中 WMS_AI_TOOL_SERVICE_SECRET 相同>",
  "X-WMS-OpenWebUI-Chat-Id": "{{CHAT_ID}}",
  "X-WMS-OpenWebUI-Message-Id": "{{MESSAGE_ID}}"
}
```

服务密钥只证明请求来自本机 Open WebUI；chat/message 只作为 Redis 短期关联键。Gateway 重新验证有效 `jti`、唯一 YAML 白名单、本次请求限制和最大调用次数，再恢复当前用户与租户；业务服务继续使用现有 Spring Security 和数据范围做最终权限判断。

## 5. 加载 Gateway、Core 与 IoT 环境变量

在启动 Core、IoT 的同一个 PowerShell 进程中执行：

```powershell
. .\load-wms-ai-env.ps1
```

仓库根目录作为 IDEA 项目时，对应 Gateway、Core 与 IoT 的 `.run` 配置会引用 `deploy/openwebui/windows.env`；以 `backend` 作为 IDEA 项目时，实际使用 `backend/.run/*.run.xml`，引用 `$PROJECT_DIR$/../deploy/openwebui/windows.env`。Gateway 还会显式接收唯一 YAML 的绝对路径。因此完成本文件配置后，使用对应项目的一键启动即可加载 AI 配置和 Core/IoT 配对 HMAC；运行配置本身不会保存或打印密钥。若本机没有该文件，各模块按默认值保持 AI 与跨服务追溯关闭。

## 6. 停止

在运行 Open WebUI 的 PowerShell 窗口按 `Ctrl+C`。数据保存在 `%LOCALAPPDATA%\AiLearnWms\openwebui\data`；删除该目录会永久删除本地账号、配置、聊天和 Knowledge，未经明确授权不得执行。

## 7. 日常使用与维护注意事项

### 7.1 它是独立服务，不是 Core 内嵌组件

- 启动 WMS 后端不代表 Open WebUI 一定已启动；AI 不可用时先分别检查 `20001`、`10003`、`10004`、`3000` 和硅基流动连接；
- `.run` 文件只帮助 Gateway/Core/IoT 加载本地环境变量，不负责安装或自动升级 Open WebUI；
- Open WebUI 故障必须只让 AI 降级，不能影响订单、库存、制造和 IoT 的正常业务接口。

### 7.2 配置分为三层

- `windows.env` 保存本机运行密钥和 Core 连接参数；
- Open WebUI 自己的本地数据库保存管理员、硅基流动连接、Knowledge、Tool Server 和模型预设；
- 唯一 YAML 白名单、Gateway 委托过滤和下游 Spring Security 决定最终可调用接口。

修改 `windows.env` 不会自动覆盖 Open WebUI 已持久化的硅基流动连接。连接、Knowledge 或工具配置变化后，应重新执行 `configure-windows.ps1` 并核对模型预设实际绑定结果。

### 7.3 密钥与外部数据边界

- 硅基流动 API Key、Open WebUI API Key、Tool Service Secret 和 Facts HMAC 必须只保存在被 Git 忽略的本机配置或秘密管理设施；一旦出现在聊天、截图或日志中，应立即轮换；
- 浏览器前端永远不持有上述 Key，也不能直接调用 Open WebUI；
- Open WebUI 最终会把必要的提示、知识片段和业务摘要发送给外部硅基流动。不得把密码、Token、设备凭证、完整数据库记录或与问题无关的敏感信息放进提示或知识库；
- Open WebUI 聊天和 WMS 工具审计不是同一种记录。WMS 审计必须以 Core 保存的 `request_id`、工具和来源摘要为准。

### 7.4 工具和知识不能随意扩展

- 不要导入 WMS 完整 OpenAPI，不要添加通用 HTTP、Shell、Python、文件系统、联网搜索、社区 Tool 或任何写工具；
- 即使当前用户拥有业务写权限，AI 仍只能调用唯一 YAML 中精确登记的查询接口；不能仅靠 Prompt 声明“不要修改数据”；
- 业务规则变化时先更新正式规格，再同步 `docs/ai-knowledge/`、提高 manifest revision、执行 `validate-knowledge.ps1`，最后重新导入或更新 Knowledge；
- Knowledge 是静态规则摘要，不包含实时库存、订单状态、告警或操作历史，实时问题必须调用 WMS 工具。

### 7.5 版本、备份和清理

- 当前固定 `open-webui==0.11.3`，不要在演示前自动升级。升级前应阅读目标版本变更、备份 `%LOCALAPPDATA%\AiLearnWms\openwebui\data`，再验证 API、Knowledge、Tool Server 和 `wms-assistant`；
- 数据目录包含本地管理员、连接配置、聊天和 Knowledge，应限制本机访问权限。备份也按敏感数据处理；
- WMS 会尽力删除用于一次问答的 Open WebUI 临时聊天，但不能把“临时聊天清理成功”当作权限或审计保证；
- 删除数据目录、重装 Open WebUI、重建管理员或轮换签名密钥都可能使既有 API Key、Knowledge 和 Tool Server 失效，操作前必须确认影响范围。

### 7.6 推荐排查顺序

1. `GET /api/ai/capabilities` 是否显示 AI 启用和唯一 YAML 的 operation 目录；
2. Gateway、Core 与 IoT 是否加载了正确的 `windows.env`，但不要把变量值打印到日志；
3. `http://127.0.0.1:3000` 是否可访问，Open WebUI 中硅基流动连接和 `wms-assistant` 是否可用；
4. `configure-windows.ps1` 是否能核对 7 份 Knowledge、`wms_core`/`wms_iot` Tool Server 和模型预设；
5. Gateway `/v3/api-docs/ai-tools` 是否报告 36 个 operation，两个子目录是否只发布 YAML 登记接口；
6. 根据 WMS `request_id` 查询 AI 工具审计，区分权限拒绝、工具失败、超时和 Provider 故障。
