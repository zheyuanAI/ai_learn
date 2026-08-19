# 项目目录与关键文件说明

## 顶层目录

- `backend/`：后端 Spring Boot 多模块工程，承载认证、核心业务、IoT 和网关服务。
- `frontend/`：前端 Vue 3 + Vite + TypeScript 工程，当前主要用于承接原型导航和后续业务页面。
- `deploy/`：本地依赖与部署编排目录，当前主要维护 Docker Compose。
- `runtime/`：仅保存本机开发使用的中间件包；除 `runtime/README.md` 外均不纳入 Git。
- `docs/`：计划、规则和项目说明文档入口。
- `openspec/`：显式 OpenSpec 任务使用的协作规则、schema、模板与长期协作 spec。
- `prototype/`：低保真原型与演示页面基线。
- `specs/`：项目级和各业务域的规格文档，属于事实源。
- `.agents/`：代理相关本地配置目录，不属于项目业务代码。
- `.git/`：Git 元数据目录。

## 顶层关键文件

- `AGENTS.md`：本项目当前生效的协作规则，约束 AI 与人工的分工方式。
- `openspec/README.md`：OpenSpec 详细协作规则，仅在显式 OpenSpec 任务下读取。
- `README.md`：仓库总说明，给出基础入口和阅读顺序。
- `.gitignore`：Git 忽略规则。

## docs

- `docs/README.md`：文档总入口。
- `docs/project-structure.md`：当前这份目录与关键文件说明。
- `docs/plans/10_week_project_plan.md`：10 周学习与开发主计划。
- `docs/superpowers/plans/2026-06-30-ai-learn-project.md`：面向 AI/代理执行的实现计划。
- `docs/rules/AGENTS_General.md`：通用规则模板参考，不直接作为本项目生效规则。

## openspec

- `openspec/README.md`：OpenSpec 协作入口、触发条件、角色分工和交接规则。
- `openspec/config.yaml`：当前项目的 OpenSpec 上下文与工件生成规则。
- `openspec/guides/antigravity-apply.md`：给执行器的应用提示模板。
- `openspec/guides/codex-review.md`：给 Codex 的审核提示模板。
- `openspec/specs/collaboration/spec.md`：当前仓库的长期协作 spec。
- `openspec/schemas/ai-learn-platform/schema.yaml`：项目专用 schema。
- `openspec/schemas/ai-learn-platform/templates/*.md`：proposal、spec、design、tasks、review 等模板。

## specs

### 项目级规格

- `specs/00-project/overview.md`：项目目标、范围、非目标和成功标准。
- `specs/00-project/architecture.md`：服务边界、横切约束和整体架构。
- `specs/00-project/prototype.md`：原型范围和页面清单。

### 领域规格目录规则

每个领域目录都按同样结构组织：

- `overview.md`：该领域的目标和范围。
- `domain.md`：核心实体、字段约束、状态机和核心业务规则。
- `api.md`：接口契约、查询过滤、错误码和返回约束。
- `acceptance.md`：验收场景和自动化测试点。

当前领域包括：

- `specs/10-erp-wms/`：ERP/WMS。
- `specs/20-mes/`：MES。
- `specs/30-iot-digital-twin/`：IoT / 数字孪生。
- `specs/40-gis-dashboard/`：GIS / 综合看板。
- `specs/50-ai-assistant/`：AI 助手、知识库、只读工具和审计。

## prototype

- `prototype/README.md`：原型目标、页面清单、演示主线和冻结原则。
- `prototype/index.html`：原型总览入口。
- `prototype/assets/styles.css`：原型共用样式。
- `prototype/assets/app.js`：原型共用导航脚本。

### 原型页面

- `prototype/pages/login.html`：登录页。
- `prototype/pages/dashboard.html`：首页综合看板。
- `prototype/pages/master-data.html`：商品、仓库、库位等主数据页。
- `prototype/pages/purchase-inbound.html`：采购单、入库单、上架任务页。
- `prototype/pages/sales-outbound.html`：销售单、冻结、拣货、出库页。
- `prototype/pages/work-order.html`：MES 工单、派工、报工页。
- `prototype/pages/device-alarm.html`：设备、状态、告警页。
- `prototype/pages/site-map.html`：厂区地图页。
- `prototype/pages/digital-twin.html`：三维展示页。
- `prototype/pages/ai-assistant.html`：AI 聊天页。
- `prototype/pages/knowledge-base.html`：知识库管理页。
- `prototype/pages/tool-audit.html`：工具调用审计页。

## frontend

- `frontend/package.json`：前端依赖与开发、构建脚本入口。
- `frontend/vite.config.ts`：Vite 构建配置。
- `frontend/tsconfig.json`：TypeScript 配置。
- `frontend/index.html`：Vite 前端入口模板。
- `frontend/README.md`：前端模块说明。

### frontend/src

- `frontend/src/main.ts`：前端启动入口。
- `frontend/src/App.vue`：应用根组件。
- `frontend/src/styles.css`：前端全局样式。
- `frontend/src/router/index.ts`：前端路由入口，当前承接领域导航。
- `frontend/src/views/PrototypeHome.vue`：原型首页视图。
- `frontend/src/views/DomainView.vue`：领域说明视图。

## backend

- `backend/pom.xml`：后端父工程聚合入口，定义模块和公共构建配置。
- `backend/README.md`：后端模块说明。

### 公共模块

- `backend/platform-shared/pom.xml`：共享模块依赖声明。
- `backend/platform-shared/src/main/java/com/ailearn/platform/shared/SharedMarker.java`：共享模块占位类，用于标识公共代码模块。

### 网关模块

- `backend/platform-gateway/pom.xml`：网关模块依赖声明。
- `backend/platform-gateway/src/main/java/com/ailearn/platform/gateway/GatewayApplication.java`：网关启动类。
- `backend/platform-gateway/src/main/java/com/ailearn/platform/gateway/HealthController.java`：网关探活接口。
- `backend/platform-gateway/src/main/resources/application.yml`：网关配置。

### 认证模块

- `backend/platform-auth/pom.xml`：认证模块依赖声明。
- `backend/platform-auth/src/main/java/com/ailearn/platform/auth/AuthApplication.java`：认证服务启动类。
- `backend/platform-auth/src/main/java/com/ailearn/platform/auth/HealthController.java`：认证服务探活接口。
- `backend/platform-auth/src/main/resources/application.yml`：认证服务配置。

### 核心业务模块

- `backend/platform-core/pom.xml`：核心业务模块依赖声明。
- `backend/platform-core/src/main/java/com/ailearn/platform/core/CoreApplication.java`：核心服务启动类。
- `backend/platform-core/src/main/java/com/ailearn/platform/core/HealthController.java`：核心服务探活接口。
- `backend/platform-core/src/main/resources/application.yml`：核心服务配置。

### IoT 模块

- `backend/platform-iot/pom.xml`：IoT 模块依赖声明。
- `backend/platform-iot/src/main/java/com/ailearn/platform/iot/IotApplication.java`：IoT 服务启动类。
- `backend/platform-iot/src/main/java/com/ailearn/platform/iot/HealthController.java`：IoT 服务探活接口。
- `backend/platform-iot/src/main/resources/application.yml`：IoT 服务配置。

## deploy

- `deploy/docker-compose.yml`：本地基础依赖编排文件，当前包含 PostgreSQL、Redis、RabbitMQ、Mosquitto、MinIO。

## 本地开发环境基线

- 自研项目使用 Node 20 与 PostgreSQL 16（端口 5433）。
- 参考工程可继续保留各自的 Node 22 与 PostgreSQL 16（端口 5323）配置。

## 维护建议

- 业务事实优先维护在 `specs/`，不要把规则散落到多个说明文档。
- 结构有变化时，优先同步本文件、`docs/README.md` 和相关模块 `README.md`。
- 新增关键模块或入口文件时，建议在本文件补一行说明，避免后续理解成本上升。
