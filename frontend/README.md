# Frontend

## 当前状态
- **工程与基础设施**：基于 Vue 3 + TypeScript + Vite + Pinia 构建，已建立统一请求拦截器 (`src/api/request.ts` 与 `src/utils/request.ts`) 及全局认证状态管理 (`src/stores/auth.ts`)。
- **登录与会话**：已接入真实登录页 (`LoginView.vue`)，支持租户编码/用户名/密码登录、单有效会话管理、Token 存储与登出重定向。
- **后台管理系统（阶段1基础接口已接入；本轮修复中，阶段验收未完成）**：
  1. **租户设置**（`/system/tenant`）：端点统一为 `GET/PUT /api/auth/admin/tenants/current`，查询并更新当前企业租户名称与基础配置。
  2. **用户管理**（`/system/users`）：用户增删改查、分配角色、重置密码。分页查询入参统一为 `page`, `size`, `roleId`；重置密码端点统一为 `POST /api/auth/admin/users/{id}/reset-password`（入参字段 `newPassword`）；删除统一为逻辑软删除（`isdel = 1`）；并在前端提供防自删与最后管理员安全拦截提示。
  3. **角色与授权管理**（`/system/roles`）：角色增删改查，支持为角色分配权限点（Role-Permission）与绑定菜单（Role-Menu）。授权提交严格匹配后端全量 ID 前置校验机制（遇非法/跨租户/已删除/停用 ID 整体拒绝）。
  4. **权限目录查询**（`/system/permissions`）：只读展示系统内置权限树/列表，采用统一冒号分段规范（如 `auth:user:view`、`auth:role:edit` 等），权限目录不可修改，由角色负责授权。
  5. **菜单管理**（`/system/menus`）：前端动态菜单树的管理与维护，基于 Flyway V5 迁移实现的 `tenant_id` 多租户菜单物理隔离；普通更新维护 `menuCode`、`visible`，启停接口维护 `status`（`ACTIVE`/`DISABLED`）。
- **权限与租户边界**：
  - 前端接口统一对接后端 `/api/auth/admin/**` 路由；
  - 租户管理员只管理当前租户数据，一期无平台超级管理员；
  - 全量 ID 校验与逻辑软删除（`isdel = 1`，禁止物理 `DELETE`）前后端严格对齐；
  - 全局路由守卫自动校验 `requiresAuth` 与登录态，未登录自动重定向至登录页。

## 规格入口
- 当前开发计划与一期范围：`../docs/specs/00-project/正式项目计划.md`
- 页面范围与交互基线：`../docs/specs/00-project/原型与交互说明.md`、`../docs/prototype/README.md`
- 领域规则、接口与验收：对应领域目录的 `概述.md`、`领域模型.md`、`接口契约.md`、`验收标准.md`

## 本地开发环境基线
- 本前端工程的 Node 版本由 `package.json` 的 `engines` 约束为 20.x。
- Java、Maven、PostgreSQL、服务端口及参考工程隔离规则以 `../docs/specs/00-project/架构设计.md` 和根目录 `../README.md` 为准。

## 下一步
1. 沿“销售需求—采购—生产—仓储—发货”黄金业务闭环逐页接入 Core 与 IoT 真实接口
2. 接入主数据（客户、供应商、物料、仓库库位、工作中心）管理视图
3. 接入库存流水、采购收货质检、销售直接拣货发货等闭环交互页面
