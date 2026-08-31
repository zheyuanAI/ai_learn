# AI 学习项目实施计划（历史）

> **状态：历史学习计划。** 本文只记录前期十周学习路线和 2026-06-30 时的旧方案，仅用于变更追溯，不再代表当前项目开发周期；当前正式交付计划见 `docs/specs/00-project/正式项目计划.md`。

> 文中的原始工程路径仅为历史快照，不能据此在当前项目创建文件；当前项目根目录和有效入口以根 `README.md` 为准。

> **供代理式执行使用：** 必须配合 `superpowers:subagent-driven-development`（推荐）或 `superpowers:executing-plans` 子技能按任务逐项执行。步骤使用复选框（`- [ ]`）进行跟踪。

**目标：** 在 10 周内通过 spec 驱动与原型优先的方式，完成制造协同、仓储、数字孪生与 AI 助手全链路演示系统。

**架构：** 先冻结完整 spec 和可点击原型，再按纵向业务链路实现后端服务与前端页面。自动化测试前移，让每条闭环业务链路都具备持续回归能力。

**技术栈：** Vue、Java/Spring Boot 或同类后端框架、PostgreSQL、Redis、RabbitMQ、MQTT、MinIO、pgvector、Docker Compose

---

### 任务 1：冻结项目级规格

**文件：**
- 修改：`旧工程根/specs/00-project/overview.md`
- 修改：`旧工程根/specs/00-project/architecture.md`
- 修改：`旧工程根/specs/00-project/prototype.md`

- [ ] 确认最终范围、非目标、成功标准，以及 AI 协作开发方式下的原型页面清单。
- [ ] 补充最终采用的前后端技术栈与部署约定。
- [ ] 补充最终角色定义、租户模型与权限矩阵引用。

### 任务 2：完成可点击原型

**文件：**
- 创建：`当前项目根/docs/prototype/README.md`
- 创建：`当前项目根/docs/prototype/pages/*`

- [ ] 为登录、首页看板、ERP/WMS、MES、IoT、GIS、AI 页面创建低保真或中保真原型。
- [ ] 在每个页面标注所需接口、权限点与状态流转。
- [ ] 对照六条最终演示主线审查整套原型。

### 任务 3：冻结领域模型与 API 契约

**文件：**
- 修改：`旧工程根/specs/10-erp-wms/*.md`
- 修改：`旧工程根/specs/20-mes/*.md`
- 修改：`旧工程根/specs/30-iot-digital-twin/*.md`
- 修改：`旧工程根/specs/40-gis-dashboard/*.md`
- 修改：`旧工程根/specs/50-ai-assistant/*.md`

- [ ] 完善各模块字段、状态、业务规则、错误码和租户约束。
- [ ] 为写接口补充幂等规则，为 AI 工具补充审计规则。
- [ ] 为每条闭环流程补充验收场景与自动化测试目标。

### 任务 4：实现基础能力与横切能力

**文件：**
- 创建：`旧工程根/backend/*`
- 创建：`旧工程根/frontend/*`
- 创建：`旧工程根/deploy/docker-compose.yml`

- [ ] 搭建 Gateway、Auth、Core、IoT、Vue 前端与中间件运行环境骨架。
- [ ] 实现登录、JWT、用户、角色、菜单、租户上下文与统一异常处理。
- [ ] 增加适合 CI 的测试启动能力、初始化数据与本地一键启动方案。

### 任务 5：按业务纵切片实现并补齐自动化回归

**文件：**
- 创建：`旧工程根/tests/*`

- [ ] 先交付 ERP/WMS 的采购与销售闭环，再依次交付 MES、IoT、GIS、AI 助手。
- [ ] 每个切片落地时同步补齐单元测试、API 测试与 E2E 测试。
- [ ] 保证六条演示主线始终能基于初始化数据跑通。

### 任务 6：最终集成、演示与面试材料

**文件：**
- 创建：`旧工程根/docs/demo-script.md`
- 创建：`旧工程根/docs/interview-notes.md`
- 创建：`旧工程根/docs/api-summary.md`

- [ ] 验证全链路启动、租户隔离、权限控制、幂等性和 AI 工具审计。
- [ ] 准备演示数据、截图、讲解要点与简历项目摘要。
- [ ] 固化采购、销售、MES、IoT、GIS 与 AI 场景的最终演示脚本。
