# Prototype 原型说明

## 目标
- 基于 `specs/00-project/prototype.md` 产出第一批可浏览原型
- 先固定页面结构、字段区、状态标签、操作按钮、接口占位、权限点
- 为后续冻结 `domain.md`、`api.md`、状态机和验收场景提供直接基线

## 文件结构
- `prototype/index.html`：原型总览、导航图、六条演示主线
- `prototype/assets/styles.css`：共享视觉样式
- `prototype/assets/app.js`：共享导航脚本
- `prototype/pages/*.html`：各业务页面原型

## 页面清单
- 登录页：`pages/login.html`
- 首页综合看板：`pages/dashboard.html`
- 商品/仓库/库位管理：`pages/master-data.html`
- 采购单、入库单、上架任务：`pages/purchase-inbound.html`
- 销售单、拣货任务、出库单：`pages/sales-outbound.html`
- 工单列表、详情、派工报工：`pages/work-order.html`
- 设备列表、设备详情、告警：`pages/device-alarm.html`
- 厂区地图：`pages/site-map.html`
- 三维展示：`pages/digital-twin.html`
- AI 助手聊天：`pages/ai-assistant.html`
- 知识库管理：`pages/knowledge-base.html`
- 工具调用审计：`pages/tool-audit.html`

## 页面导航图
1. 登录页 -> 首页综合看板
2. 首页综合看板 -> ERP/WMS、MES、IoT、GIS、AI 各域页面
3. 采购入库页 -> 销售出库页 -> 首页库存指标
4. 工单执行页 -> 设备与告警页 -> 厂区地图/三维展示 -> 首页设备指标
5. AI 聊天页 -> 知识库管理页 / 调用审计页

## 六条演示主线
1. 采购单 -> 入库确认 -> 上架确认 -> 库存增加 -> 应付生成
2. 销售单 -> 库存冻结 -> 拣货确认 -> 出库确认 -> 应收生成
3. BOM/工艺路线 -> 工单 -> 派工 -> 报工 -> 质检 -> 成品入库
4. 设备上报 -> 状态变化 -> 告警触发 -> 地图与看板联动
5. 厂区地图 / 三维页查看设备与仓库状态
6. AI 助手问答 -> 只读工具调用 -> 生成日报 / 告警解释 -> 审计留痕

## 原型阶段冻结内容
- 字段：页面中表格列、筛选条件、详情区字段即第一版冻结候选
- 状态：页面中的状态标签和流转顺序即第一版状态机候选
- 接口：每页“关键接口”卡片即第一版 API 冻结候选
- 权限：每页“权限点”卡片即第一版菜单/按钮权限候选

## 下一步
1. 逐页审查字段是否缺失，优先补边界字段
2. 回写各模块 `domain.md` 和 `api.md`，冻结命名与状态机
3. 在冻结后的页面基础上搭前后端项目骨架
