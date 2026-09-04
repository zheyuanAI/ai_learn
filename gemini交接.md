# Gemini 前端界面交接与实施计划（阶段 2–7）

> 执行角色：Gemini，可按阶段拆分多个界面子代理；统一由一个前端集成人维护路由、公共类型和样式。  
> 协作对象：Luna Max 负责后端逻辑与冻结接口，详见根目录 `luna-max交接.md`。  
> 基线日期：2026-09-03。  
> Git 约束：只修改工作区，不执行 `git commit`、`git push`、建分支或合并，由用户统一审查。  
> 核心原则：界面展示和发起命令，后端决定租户、权限、状态迁移、库存结果和可执行动作。

## 1. 可直接交给 Gemini 的角色提示词

```text
你是本项目阶段 2–7 的前端界面负责人。只负责 frontend/** 与 docs/prototype/**，不修改 backend/**、Flyway、Redis、Mosquitto 或后端规格。

先完整阅读根 AGENTS.md、正式项目计划、词汇定义表、原型说明、对应领域规格和本文件。检查现有 Vue 3/Vite/TypeScript 结构与未提交改动，沿用现有暗色工业工作台视觉，不引入与仓库不一致的大型 UI 框架。

必须等待 Luna Max 发布对应 READY-Sn 契约后再接真实接口。不得猜接口、枚举、权限或错误码；有疑问形成契约问题单。页面只根据后端返回的状态、累计数量和 allowedActions 决定操作入口，不在前端复制库存、预留、质量处置、工单或告警状态机。

可以按阶段使用多个子代理，但同一时刻一个文件只有一个所有者。公共入口 frontend/src/router/index.ts、frontend/src/stores/auth.ts、frontend/src/api/request.ts、frontend/src/styles.css 只允许前端集成人修改。各阶段子代理独占自己的 views/api/types 文件，不互相改公共入口。

先做失败测试或可重复验证，再写最小实现。新增方法用中文注释说明用途、出入参和流程；修改已有方法在改动处用中文说明用途。每批完成后运行类型检查、构建和页面验证，交付截图/步骤、改动文件、结果和已知限制。

不提交 Git，不触碰用户已有改动。
```

## 2. 角色边界与文件所有权

### 2.1 Gemini 独占

- `frontend/src/**`
- `frontend/package.json` 与前端配置文件
- `frontend/README.md`
- `docs/prototype/README.md`
- `docs/prototype/pages/master-data.html`
- `docs/prototype/pages/purchase-inbound.html`
- `docs/prototype/pages/sales-outbound.html`
- `docs/prototype/pages/work-order.html`
- `docs/prototype/pages/device-alarm.html`
- `docs/prototype/pages/site-map.html`
- `docs/prototype/pages/dashboard.html`
- 必要时新增追溯页面原型，但不得改阶段 8 AI 页面

### 2.2 Gemini 禁止修改

- `backend/**`
- `deploy/**`
- `runtime/**`
- `docs/specs/10-erp-wms/**`
- `docs/specs/20-mes/**`
- `docs/specs/30-iot-digital-twin/**`
- `docs/specs/40-gis-dashboard/**`
- 任何数据库迁移、权限缓存或 Mosquitto 配置

### 2.3 集成人独占的公共前端文件

即使 Gemini 使用多个子代理，下列文件也只能由一个“前端集成人”修改：

- `frontend/src/router/index.ts`
- `frontend/src/stores/auth.ts`
- `frontend/src/api/request.ts`
- `frontend/src/utils/request.ts`
- `frontend/src/components/Layout/AppLayout.vue`
- `frontend/src/styles.css`
- `frontend/package.json`

阶段子代理只能给集成人提交所需路由、菜单、公共组件和依赖建议。

### 2.4 与 Luna Max 的协作规则

- `READY-Sn` 之前：只可做静态布局、交互草图和明确标注的本地 fixture，不可声称真实可用。
- `READY-Sn` 之后：以冻结 DTO、枚举、权限和错误码接入，不做字段兼容猜测。
- 契约冲突：提交问题单，包含接口、期望、实际、复现步骤和建议；不得自行改后端。
- 后端破坏性变更：等 Luna Max 重新发布闸门后再调整前端。
- 联调数据：使用 Luna Max 提供的租户、角色和跨阶段种子数据，不在前端伪造成功业务事实。

## 3. 当前前端事实

- 当前为 Vue 3 + Vite 5 + TypeScript + Pinia + Vue Router + Axios。
- `frontend/package.json` 只有开发、构建和预览脚本，尚无 Vitest、组件测试或 E2E 配置。
- `frontend/src/router/index.ts` 目前主要是阶段 1 系统页和四个 `DomainView` 占位入口。
- `frontend/src/stores/auth.ts` 同时含真实接口恢复逻辑和演示角色预设；真实阶段页面不能以预设权限作为安全依据。
- `frontend/src/components/Layout/AppLayout.vue` 已支持动态菜单树。
- `frontend/src/styles.css` 已形成暗色工业工作台基础风格，应扩展而不是整体推翻。
- HTML 原型是目标设计和演示数据，不代表接口已实现。

## 4. 界面架构

建议新增：

```text
frontend/src
├─ api
│  ├─ masterData.ts
│  ├─ inventory.ts
│  ├─ purchasing.ts
│  ├─ sales.ts
│  ├─ manufacturing.ts
│  ├─ iot.ts
│  └─ insights.ts
├─ types
│  ├─ common.ts
│  ├─ inventory.ts
│  ├─ purchasing.ts
│  ├─ sales.ts
│  ├─ manufacturing.ts
│  ├─ iot.ts
│  └─ insights.ts
├─ components
│  ├─ common
│  │  ├─ PageHeader.vue
│  │  ├─ FilterBar.vue
│  │  ├─ DataTable.vue
│  │  ├─ StatusBadge.vue
│  │  ├─ EmptyState.vue
│  │  ├─ ErrorState.vue
│  │  ├─ ConfirmDialog.vue
│  │  └─ QuantityText.vue
│  └─ domain
└─ views
   ├─ masterdata
   ├─ inventory
   ├─ purchasing
   ├─ sales
   ├─ manufacturing
   ├─ iot
   └─ insights
```

短小且只用一次的逻辑留在页面中；只有明确复用才抽公共组件。不要搭建低代码页面引擎、通用工作流编辑器或任意公式看板。

## 5. 前端统一规范

### 5.1 权限和安全

- 前端权限只用于隐藏/禁用入口和改善体验，后端 `hasAuthority(...)` 才是最终授权。
- 不发送 `X-Authorities` 或 `X-Permissions`。
- 不发送 `tenant_id`、操作用户、会话 JTI 或库存余额等可信上下文字段。
- 业务按钮优先依据详情响应的 `allowedActions`，权限数组只作路由/菜单初筛。
- 401：清理会话并跳转登录；403：保留页面并显示无权；503 权限上下文异常：显示暂不可用，不清除登录态，不自动重试写命令。

### 5.2 数量、时间和状态

- 数量使用字符串接收与展示，避免 JavaScript 浮点误差。
- 输入保持字符串，提交前只做格式、必填和非负等基础校验。
- 不在浏览器计算库存最终余额、预留最终值或订单最终状态。
- 日期明确展示时区；看板使用后端返回的实际时间边界。
- 状态文案和颜色集中映射；未知状态显示“未知（原值）”，不能当正常状态。

### 5.3 命令与幂等

- 每次用户主动写操作生成新的 `Idempotency-Key`。
- 双击、重复点击和请求处理中禁止重复发起。
- 网络结果不确定时不生成新键盲重试；先查详情，再复用原键或让用户明确处理。
- 命令成功先使用响应状态/数量更新，再重新拉详情核对。

### 5.4 页面状态

所有数据页面都有加载、空、错误、无权限四态。写操作还要覆盖处理中、成功、业务冲突和结果不确定。不得用空表冒充接口失败。

## 6. 前置批次：公共基线与 `READY-S0`

### Task UI0.1：测试基线

**修改/新增文件：**

- `frontend/package.json`
- `frontend/vitest.config.ts`
- `frontend/src/test/setup.ts`
- `frontend/src/components/common/__tests__/**`

如允许增加开发依赖，引入与 Vue 3/Vite 5 匹配的 Vitest、Vue Test Utils 和 jsdom；先为权限按钮、状态徽标、错误态和请求错误映射写失败测试。若当前环境不能安装依赖，则保留 `npm run build` 加可重复的浏览器验证，并明确未建立自动化测试，不能伪称已测。

建议脚本：

```json
{
  "test": "vitest run",
  "test:watch": "vitest"
}
```

### Task UI0.2：统一类型、请求和错误

**修改/新增文件：**

- `frontend/src/api/request.ts`
- `frontend/src/utils/request.ts`
- `frontend/src/types/common.ts`
- `frontend/src/components/common/ErrorState.vue`
- `frontend/src/stores/auth.ts`

定义 `ApiResponse<T>`、`PageResult<T>`、`AllowedAction`、错误结构、数量字符串和日期类型。处理 401/403/409/503；保留后端 `requestId`。删除业务代码对权限 Header 的依赖。

### Task UI0.3：路由和布局

**修改文件：**

- `frontend/src/router/index.ts`
- `frontend/src/components/Layout/AppLayout.vue`
- `frontend/src/styles.css`

把 `DomainView` 占位入口逐阶段替换为真实懒加载路由；动态菜单仍由 Auth 返回，路由表只做组件映射。窄屏保证关键操作可浏览、可检索、可确认，复杂表格可横向滚动。

**完成条件：**`READY-S0` 权限错误语义已接入；页面不发权限 Header；401/403/503 可区分；公共组件、测试（若有）和构建通过。

## 7. 阶段 2：主数据、库存、调拨和盘点

依赖：`READY-S2`。

### Task UI2.1：主数据中心

**新增文件：**

- `frontend/src/api/masterData.ts`
- `frontend/src/views/masterdata/MasterDataView.vue`
- `frontend/src/views/masterdata/components/MasterDataTable.vue`
- `frontend/src/views/masterdata/components/MasterDataEditor.vue`

统一承载 UOM、商品、客户、供应商、仓库和库位。支持分页、关键词、状态筛选、创建、编辑、启停。库位编辑先选仓库并说明类型；停用失败展示后端引用或非零库存原因。参考 `docs/prototype/pages/master-data.html`，字段以冻结契约为准。

### Task UI2.2：库存工作台

**新增文件：**

- `frontend/src/api/inventory.ts`
- `frontend/src/types/inventory.ts`
- `frontend/src/views/inventory/InventoryBalanceView.vue`
- `frontend/src/views/inventory/InventoryTransactionView.vue`
- `frontend/src/views/inventory/ReservationView.vue`

余额同时展示实物、预留、可用、可分配；`QualityHold` 明确不可分配。流水支持来源单据跳转，移动展示来源和目标。绝不提供“直接改余额”按钮。

### Task UI2.3：调拨与盘点

**新增文件：**

- `frontend/src/views/inventory/TransferListView.vue`
- `frontend/src/views/inventory/TransferDetailView.vue`
- `frontend/src/views/inventory/StocktakeListView.vue`
- `frontend/src/views/inventory/StocktakeDetailView.vue`

调拨详情区分意图和确认事实；确认前二次展示来源、目标、批次、数量。盘点展示系统快照、实盘、差异和原因；快照版本冲突后禁提并引导重新开始。

**验收：**无直接改库存入口；按钮按 `allowedActions`；数量无精度损失；防重复；真实接口冒烟通过。

## 8. 阶段 3：采购、到货、质检处置与上架

依赖：`READY-S3`。

**新增文件：**

- `frontend/src/api/purchasing.ts`
- `frontend/src/types/purchasing.ts`
- `frontend/src/views/purchasing/PurchaseOrderListView.vue`
- `frontend/src/views/purchasing/PurchaseOrderDetailView.vue`
- `frontend/src/views/purchasing/ReceiptConfirmView.vue`
- `frontend/src/views/purchasing/QualityDispositionView.vue`
- `frontend/src/views/purchasing/PutawayTaskView.vue`

采购详情用时间线展示提交、审核、收货、质检、处置、上架。收货录入只做基础等式提示，最终关系由后端确认。质检事实、处置决定、仓库执行分区，不能合成一个按钮。上架只选合法目标库位，不出现“增加库存”。参考 `docs/prototype/pages/purchase-inbound.html`。

**验收：**全拒收不显示入库流水；分批累计来自后端；人工完成后待处置货物仍可继续；角色无权时只读或隐藏命令。

## 9. 阶段 4：销售、预留、拣货和发货

依赖：`READY-S4`。

**新增文件：**

- `frontend/src/api/sales.ts`
- `frontend/src/types/sales.ts`
- `frontend/src/views/sales/SalesOrderListView.vue`
- `frontend/src/views/sales/SalesOrderDetailView.vue`
- `frontend/src/views/sales/ReservationDetailView.vue`
- `frontend/src/views/sales/PickTaskView.vue`
- `frontend/src/views/sales/ShipmentConfirmView.vue`

订单展示订购、已预留、已拣、已发和剩余量，全部来自后端。直接拣货说明会自动补预留并移至发货暂存位，不让用户拼库存步骤。发货只选择后端允许的已拣明细。人工完成若有未发暂存量，按后端错误引导先退回。参考 `docs/prototype/pages/sales-outbound.html`。

**验收：**并发冲突提示刷新；重复提交不产生第二命令；部分拣/发正确；页面不自行改订单状态。

## 10. 阶段 5：MES、BOM、工艺和工单

依赖：`READY-S5`。

**新增文件：**

- `frontend/src/api/manufacturing.ts`
- `frontend/src/types/manufacturing.ts`
- `frontend/src/views/manufacturing/BomListView.vue`
- `frontend/src/views/manufacturing/RoutingListView.vue`
- `frontend/src/views/manufacturing/WorkOrderListView.vue`
- `frontend/src/views/manufacturing/WorkOrderDetailView.vue`
- `frontend/src/views/manufacturing/DispatchView.vue`
- `frontend/src/views/manufacturing/OperationExecutionView.vue`
- `frontend/src/views/manufacturing/MaterialMovementView.vue`
- `frontend/src/views/manufacturing/FinishedGoodsReceiptView.vue`

工单详情分开展示生产意图、冻结版本、派工、执行、报工、质检和库存动作。BOM/工艺冻结后显示快照，不能用当前主数据冒充。领退料和成品入库显示关联库存流水，但不编辑库存。参考 `docs/prototype/pages/work-order.html`。

**验收：**销售来源可跳转；按钮来自 `allowedActions`；暂停/恢复/完成不混淆；不出现排产、返工、WIP 或线边仓入口。

## 11. 阶段 6：设备、遥测、状态和告警

依赖：`READY-S6`。

**新增文件：**

- `frontend/src/api/iot.ts`
- `frontend/src/types/iot.ts`
- `frontend/src/views/iot/DeviceProfileView.vue`
- `frontend/src/views/iot/DeviceListView.vue`
- `frontend/src/views/iot/DeviceDetailView.vue`
- `frontend/src/views/iot/DeviceCredentialDialog.vue`
- `frontend/src/views/iot/TelemetryView.vue`
- `frontend/src/views/iot/AlarmListView.vue`
- `frontend/src/views/iot/AlarmDetailView.vue`

设备详情分开显示生命周期、在线、运行和告警状态。凭证明文只在创建成功对话框显示一次；关闭后不回显，不写浏览器持久存储或日志。

遥测展示指标、单位、来源时间和接收时间；延迟数据提示但不覆盖当前状态。告警详情区分触发、确认、恢复和生产上下文；人工不提供“恢复”。simulate 只在开发/测试环境且有权限时出现。参考 `docs/prototype/pages/device-alarm.html`；不实现三维数字孪生。

**验收：**凭证撤销后入口不可用；重复消息不重复展示；活动/恢复未确认清楚；Core 上下文不可用不影响遥测和告警。

## 12. 阶段 7：追溯、二维 GIS 与综合看板

依赖：`READY-S7`。

### Task UI7.1：追溯链

**新增文件：**

- `frontend/src/api/insights.ts`
- `frontend/src/types/insights.ts`
- `frontend/src/views/insights/TraceabilityView.vue`
- `frontend/src/views/insights/components/TraceNodeCard.vue`

支持销售单、工单、库存流水和设备告警入口。按时间或关系展示来源、事实时间、完整性和有权跳转。无权节点只显示后端给出的隐藏数量；缺失来源显示缺口，不补造节点。

### Task UI7.2：二维地图

**新增文件：**

- `frontend/src/views/insights/SiteMapListView.vue`
- `frontend/src/views/insights/SiteMapView.vue`
- `frontend/src/views/insights/SiteMapEditorView.vue`

使用受控图片底图与百分比坐标；缩放后点位不漂移。状态色直接展示后端结果，不重新组合。管理态先校验图片类型和 5 MiB 上限，后端仍最终校验。参考 `docs/prototype/pages/site-map.html`。

### Task UI7.3：七类看板

**新增文件：**

- `frontend/src/views/insights/DashboardView.vue`
- `frontend/src/views/insights/components/SummaryCard.vue`
- `frontend/src/views/insights/components/StaleDataBanner.vue`

固定库存、履约、制造、质量、设备、告警、追溯七类；范围仅 today/7d/30d。默认 30 秒刷新并支持手动刷新。每张卡独立显示生成时间、来源更新时间、失败来源和陈旧标记；来源失败绝不把值清零。参考 `docs/prototype/pages/dashboard.html`。

**验收：**七类卡片互不掩盖失败；stale 数据显示时间；无旧结果显示错误；地图和追溯跳转仍受目标权限控制。

## 13. Gemini 多子代理划分

| 子代理 | 独占范围 | 启动条件 |
| --- | --- | --- |
| UI-A 公共集成 | 路由、Auth、请求、公共类型、布局、全局样式、package | 立即；独占公共文件 |
| UI-B ERP/WMS | `masterdata/**`、`inventory/**`、`purchasing/**`、`sales/**` 与领域 API/type | READY-S2/S3/S4 |
| UI-C MES/IoT | `manufacturing/**`、`iot/**` 与领域 API/type | READY-S5/S6 |
| UI-D Insights | `insights/**`、追溯/GIS/看板 | READY-S7 |

UI-B/C/D 不得改公共入口；路由、菜单、公共组件和依赖需求交 UI-A。`types/common.ts` 由 UI-A 独占。一个原型页同一时刻只给一个代理。合并前检查工作区，不覆盖、不批量格式化他人改动。

## 14. 页面路由建议

最终以 Auth 菜单和冻结契约为准：

```text
/master-data
/inventory/balances
/inventory/reservations
/inventory/transactions
/inventory/transfers
/inventory/stocktakes
/purchasing/orders
/purchasing/receipts
/purchasing/quality
/purchasing/putaway
/sales/orders
/sales/picks
/sales/shipments
/mes/boms
/mes/routings
/mes/work-orders
/mes/executions
/iot/devices
/iot/telemetry
/iot/alarms
/traceability
/gis/site-maps
/dashboard
```

现有 `/erp-wms`、`/mes`、`/iot`、`/gis` 占位页在真实子路由就绪后改为重定向或领域首页，不能长期保留两套入口。

## 15. 前端验证矩阵

```powershell
cd frontend
npm run test
npm run build
```

若尚无测试脚本，至少运行 `npm run build` 并记录人工冒烟，不能把构建成功等同业务验收。

浏览器验证覆盖：六类角色菜单和路由；401/403/409/503；列表四态；写命令防双击/幂等/结果不确定；1440px 与 960px 以下；S2–S7 每阶段一条真实链；凭证明文一次性显示、看板陈旧数据、盘点版本冲突；最后执行 `git diff --check` 并确认未碰 `backend/**` 和用户 `.run` 改动。

## 16. 子代理交付格式

```text
负责切片：
依赖的 READY 闸门：
修改文件：
路由建议：
接入接口：
权限和 allowedActions：
加载/空/错/无权状态：
测试命令与结果：
浏览器验证与截图：
未验证事项：
契约问题：
是否触碰公共文件：否（除 UI-A）
```

## 17. 完成定义

只有 S2–S7 真实接口全部接入、不再依赖页面假数据，操作均依据后端 `allowedActions`，权限 Header 已移除，401/403/503 正确，测试（若建立）、类型检查和构建通过，关键角色与跨阶段场景完成浏览器验证，且未修改后端、未提交 Git、未覆盖用户改动，才可报告界面完成。

阶段 8 AI 页面、阶段 9 完整端到端验收、三维数字孪生、通用低代码和任意看板设计器不在本计划范围。
