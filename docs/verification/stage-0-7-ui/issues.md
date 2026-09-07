# 阶段 0–7 页面操作贯通问题台账

基线日期：2026-09-07。角色与账号只记录非敏感的租户/角色标识；不记录密码、JWT、HMAC 或完整凭据。表格保留第 0 批问题快照；第 1 批结果见文末，未列出的后续状态不能视为已修复。

| 编号 | 优先级 | 账号角色 | 来源方式 | 菜单 / URL / 按钮 | HTTP 状态 / 业务码 | 实际结果 | 预期结果 | 证据等级 |
| --- | --- | --- | --- | --- | --- | --- | --- | --- |
| F01 | P0 | `tenant_demo_a` / `tenant.admin` | 菜单点击 | “采购订单” → `/purchase/orders` | 无业务请求；Router 最终 `/` | 点击后停留首页；同类旧路径共 9 条未注册 | 迁移到 `/purchasing/orders`，保留业务语义与查询上下文 | A+B，浏览器复核 |
| F02 | P0 | `tenant_demo_a` / `tenant.admin` | 手输 URL | `/dashboard`；另测 `/gis/site-maps` | 七个 dashboard 请求均 HTTP 404；页面日志为“请求的静态资源或接口不存在” | 看板七卡错误；地图/追溯事实源在当前运行方式下不一定装配 | 先取证 active profile 和条件装配，再让已注册端点按权限返回真实数据或明确拒绝 | A+B+C，浏览器复核 |
| F03 | P1 | `tenant_demo_a` / `tenant.admin` | 手输 URL | `/mes/dispatch`；“新建派工单” | 列表请求 HTTP 403，业务提示“没有操作权限” | 无权限态仍展示可打开的创建入口 | 读/写权限分别反映在页面，缺写权限不显示或禁用写按钮 | A+B，浏览器复核 |
| F04 | P1 | `tenant_demo_a` / `tenant.admin` | 手输详情 URL / 列表抽屉 | 采购、销售、调拨 `/:id` 详情 | 本批未执行写入；代码直接确认根组件 `visible=false` | 抽屉可看不等于详情直达、刷新和追溯穿透可用 | 由页面宿主读取 `route.params.id`，主动加载详情并传入 `visible=true` | B，代码确认 |
| F05 | P1 | `tenant_demo_a` / `tenant.admin` | 手输 URL | `/master-data`；商品、库位、客户、供应商分支 | 商品读取及创建表单打开；仓库/UOM入口未调用 | 不能只靠当前视图从零建立仓库和 UOM；UOM 是自由文本 | 主数据页签覆盖仓库/UOM，真实目录可建、改、停用并供业务选择 | A+B，浏览器与代码复核 |
| F06 | P1 | `tenant_demo_a` / `tenant.admin` | 手输 URL | `/sales/picks`；表格行 | 列表可读；本批未执行写入 | 仅有订单队列；任务号和更新时间为 `-`，无详情/拣货/发货可操作入口 | 订单驱动履约队列展示真实数量并可进入既有直接拣货/发货操作 | A+B，浏览器复核 |
| F07 | P1 | `tenant_demo_a` / `tenant.admin` | 手输 URL / 表单打开 | `/mes/dispatch`；工单、工序、操作员、设备 UUID 输入 | 列表 HTTP 403；创建表单仍可打开 | 普通用户需要猜 UUID，非法关联最终只能看到后端拒绝 | 工单冻结工艺、受控操作员目录、真实设备选择器；允许无设备人工工序 | A+B，浏览器与代码复核 |
| F08 | P1 | 生产质检角色待运行 | 手输工单详情 URL | 工单详情后的生产质检入口 | 本批未执行；API 已定义 create/submit/close，视图无调用 | 报工后没有生产质检页面入口，不能形成成品入库前质量阻塞 | 选择真实 `workReportId`，创建/提交/关闭质检并复读工单事实 | B，代码确认 |
| F09 | P1 | 正式角色待运行 | 手输/页面动作待运行 | MES 动作按钮与写请求 | 本批未执行；代码中部分 `isActionAllowed` 缺动作时返回 true，普通 Error 丢状态/业务码/request_id | 按钮可能误开放；超时重试会自动换幂等键，无法区分结果不确定 | 契约承诺的动作缺失/未知即拒绝；保留错误元数据；同一用户命令复用幂等键 | B，代码确认 |
| F10 | P1 | `buyer.chen` / `wh.operator` 待运行 | 菜单点击待运行 | 采购详情、建单选项、质量隔离/收货暂存库位 | 本批未执行；代码含 QH-01/RS-01 兜底及 `Promise.all` 可选来源耦合 | 可能把默认库位/未返回的数量当作事实；可选工单失败会拖垮必填选项 | 使用真实投影、逐项加载态与合法库位选择，未返回数量不伪造为已发生 0 | A+B，代码确认 |
| F11 | P1 | `tenant_demo_a` / `tenant.admin` | 手输 URL | `/dashboard`；SummaryCard 新鲜度标签 | 七个源请求 HTTP 404；卡片仍显示“实时” | 错误状态与实时新鲜度矛盾 | 只有成功且明确 fresh 才显示“实时”；错误/旧缓存显示不可用或 stale | A+B，浏览器与代码复核 |
| F12 | P1 | `iot.engineer` 待运行 | 手输 URL / MQTT 待运行 | IoT 遥测、告警、GIS/追溯上下文 | 本批未执行；当前 `application.yml` 默认 MQTT 关闭，真实 ACL/QoS1/生产区域 Facts 未取证 | 不能以 HTTP simulate 或服务 health=UP 宣布实机闭环 | 真实 Mosquitto QoS1 消息去重、告警生命周期、上下文补链和事实源缺口均有证据 | C，待运行验证 |

## 本批红灯结果

使用同一 `tenant_demo_a / tenant.admin` 真实页面复核：

- F01：点击“采购订单”后最终 URL 仍为 `http://localhost:5173/`；
- F02：`/dashboard` 的 `inventory`、`fulfillment`、`manufacturing`、`quality`、`device`、`alarms`、`traceability` 七个请求均收到 HTTP 404；
- F03：`/mes/dispatch` 列表收到 HTTP 403，页面仍显示“新建派工单”；
- F06：`/sales/picks` 只显示订单队列，按钮列表无详情、直接拣货或发货动作；
- F11：F02 的 404 错误卡片仍标记“实时”。

前端错误日志中可见脱敏后的 HTTP 状态与消息；页面没有展示可复制 `request_id`，因此本批不虚构业务码或请求号。后续第 1–3 批修复后，应重新填写对应行的浏览器步骤、HTTP 状态/业务码和证据位置。

## 第 1 批结果（2026-09-07）

- F01 已修复：旧 `/purchase/orders` 和真实菜单点击均最终进入 `/purchasing/orders`，采购列表成功请求真实 `/api/purchase-orders`。
- F04 已修复：采购、销售、调拨详情均支持列表复用、详情直链和刷新；直链使用真实详情 ID，请求分别返回 200，关闭后返回对应业务列表。
- 旧主数据路由已覆盖：`/master-data/warehouses` 最终进入 `/master-data?tab=warehouses`，页面激活仓库与库位页签；当前验证角色无该 API 权限时显示明确的 403 提示。
- 未完成项：F02/F11 归入第 2 批；F03/F09 归入第 3 批；F05/F07/F10 归入第 4 批。第 1 批没有把这些断点记作已修复。
