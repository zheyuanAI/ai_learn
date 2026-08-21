# Prototype 原型说明

## 定位

- 本目录展示一期目标设计，不代表对应业务接口或功能已经实现
- 所有页面围绕一条黄金业务闭环表达业务意图、执行过程与事实记录
- 页面中的字段、状态、动作和接口占位应以正式 `docs/specs/00-project/原型与交互说明.md`、对应领域的 `概述.md`、`领域模型.md`、`接口契约.md`、`验收标准.md` 与实际代码为准

## 一期黄金业务闭环

```text
销售订单 100 件需求
-> 人工关联来源销售行与生产工单
-> 人工创建采购订单并关联来源工单
-> 收货确认进入收货暂存位
-> 上架只移动到目标库位
-> 生产领料
-> 派工与 OperationExecution
-> MQTT 遥测 / 设备状态 / 设备告警
-> 报工、质检与成品入库
-> 销售库存预留
-> 拣货移动到发货暂存位
-> 发货扣减企业总实物库存并释放预留
-> 地图、看板、追溯与 AI 只读查询
```

一期只提供人工供需关联，不包含自动 MRP、自动缺口计算、自动工单生成或自动采购建议。

## 关键库存语义

- `available_qty = on_hand_qty - reserved_qty`，且 `available_qty >= 0`
- 收货确认增加企业总实物库存，并进入 `ReceivingStaging` 收货暂存位
- 上架确认只移动位置，不重复增加企业总库存
- 销售预留增加 `reserved_qty`，不改变 `on_hand_qty`
- 拣货确认只移动到 `ShippingStaging` 发货暂存位，不扣减企业总库存
- 发货确认才扣减企业总实物库存，并释放对应预留
- 生产领料减少仓库库存，生产退料增加退回库位库存

## 页面清单

- 总览与身份：`index.html`、`pages/login.html`、`pages/dashboard.html`
- 供需与仓储：`pages/master-data.html`、`pages/purchase-inbound.html`、`pages/sales-outbound.html`
- 制造执行：`pages/work-order.html`
- 设备事实：`pages/device-alarm.html`
- 一期只读展示：`pages/site-map.html`、`pages/ai-assistant.html`、`pages/tool-audit.html`
- 二期设计资产：`pages/digital-twin.html`、`pages/knowledge-base.html`

## 页面审查顺序

1. 从销售订单行确认现货、生产与采购的人工来源关系
2. 核对采购收货、收货暂存位、上架移动和库存流水
3. 核对生产领退料、派工、`OperationExecution`、报工、质检和成品入库
4. 核对设备消息去重、遥测、状态、告警与工序执行上下文
5. 核对销售预留、拣货移动、发货扣减及预留释放
6. 最后核对地图、看板、追溯与 AI 是否只读并沿用同一事实来源

## 二期原型资产

- `digital-twin.html`：仅用于 Ditto/Cesium 与三维数字孪生方案讨论，不进入一期开发、导航摘要或验收
- `knowledge-base.html`：仅用于 RAG、文档解析和向量检索方案讨论，不进入一期开发、导航摘要或验收
- 静态原型导航保留这两个入口，并明确标记“二期设计资产”

## 使用说明

直接打开 `prototype/index.html` 即可浏览。页面数据均为目标设计示例；真实实现状态必须核对前后端代码、配置与自动化验证结果。
