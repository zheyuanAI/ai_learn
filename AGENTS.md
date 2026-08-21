# AGENTS.md

## 项目概览

- 项目名称：制造与仓储协同执行平台
- 当前工程基线：Java 21、Maven 3.9.1、Spring Boot 3.3.5、Node 20、Vue 3、Vite 5、TypeScript、PostgreSQL 16、Redis、Mosquitto
- 项目目标：以销售需求、人工供需关联、采购、库存、制造执行、IoT 事实和销售交付组成可追溯黄金业务闭环
- 一期边界：RabbitMQ、MinIO、pgvector、三维数字孪生、RAG、MRP、APS 和完整财务均不进入一期

## 本地开发环境基线

- 自研项目在 IDEA 中使用 Java 21、Maven 3.9.1 和 Node 20；PostgreSQL 16 使用端口 5433。
- Gateway、Auth、Core、IoT 分别使用 10001、10002、10003、10004。
- Redis 与 Mosquitto 的项目内位置和手动启动命令以 `runtime/README.md` 为准。
- 参考工程可继续保留各自的 Node 22 与 PostgreSQL 16（端口 5323）配置。

## 任务路由与 OpenSpec

- 默认按普通任务处理；仅当用户明确以 `OpenSpec` 或 `openspec` 作为任务开头时，才进入 OpenSpec 流程。
- 进入 OpenSpec 流程后，必须先阅读 `docs/openspec/README.md`，再继续需求澄清、工件生成、实现交接、执行器自检、审核、返工和归档。

## 常用命令

已在仓库中确认：

- 前端安装：`cd frontend && npm i`
- 前端开发：`cd frontend && npm run dev`
- 前端构建：`cd frontend && npm run build`
- 前端预览：`cd frontend && npm run preview`
- 基础依赖启动：`cd deploy && docker compose up -d`

未在仓库中以脚本形式确认，但可按当前工程结构使用：

- 后端聚合构建：`cd backend && mvn package`
- 单模块启动：`cd backend && mvn -pl platform-auth spring-boot:run`

## 必读上下文

- 普通任务默认先读 `docs/specs/00-project/正式项目计划.md`、`CONTEXT.md`，再按需读 `项目概述.md`、`架构设计.md`、`原型与交互说明.md`
- 领域任务再读对应目录的 `概述.md`、`领域模型.md`、`接口契约.md`、`验收标准.md`
- 页面或交互任务同步检查 `prototype/README.md` 与 `prototype/pages/*.html`
- 跨模块行为、服务边界、统一鉴权或异常包装相关任务，同步检查 `docs/specs/00-project/架构设计.md`
- 涉及执行顺序、阶段目标或演示主线时，同步检查 `docs/specs/00-project/正式项目计划.md`；`docs/plans/十周学习计划-历史.md` 为历史十周学习资料，不作为当前实施入口，`docs/superpowers/plans/2026-06-30-ai-learn-project.md` 仅作历史追溯

## 文档事实源

按以下优先级读取和判断事实：

1. `frontend/`、`backend/`、`deploy/` 中实际生效的代码与配置（判断“已实现”）
2. `backend/pom.xml`、`frontend/package.json` 等构建与依赖配置
3. `docs/specs/00-project/*.md` 与各领域 `docs/specs/*/{概述,领域模型,接口契约,验收标准}.md`（约束“应实现”）
4. `prototype/README.md` 与 `prototype/pages/*.html`
5. `docs/specs/00-project/正式项目计划.md`；`docs/plans/十周学习计划-历史.md` 仅作历史十周学习资料，不作为当前实施入口
6. 已标记为历史或废止的计划只用于变更追溯

## 禁止推断原则

- 无法从仓库确认的事实，必须明确写“未在仓库中确认”，禁止编造。
- 若仓库事实与通用模板、历史经验或口头习惯冲突，以当前仓库中的事实源为准。
- 若只看到原型、未看到代码实现，不得把原型占位行为表述成“已实现”。

## 协作模式

- 用户是主开发，优先亲自编写核心业务代码。
- AI 默认不接管整块核心业务实现，除非用户明确要求。
- AI 主要承担：任务收窄、设计约束、代码 review、测试样板、假数据、DTO、非核心配套代码、文档整理、缺口检查。

以下内容默认视为核心业务，优先由用户亲自实现：

- 登录、JWT、租户上下文、权限菜单
- 库存预留、释放、移动、扣减、回补
- 采购入库、销售出库、工单执行等关键状态流转
- 幂等、租户隔离、库存一致性、AI 工具审计约束

## 通用工作规则

如果仓库内没有更强、更具体且不冲突的项目规则，项目专用 `AGENTS.md` 必须保留以下要求；若确实不适用，必须明确写出“不适用”或“未在仓库中确认”。

### 1. 沟通与注释

- 必须尽量使用中文；命令、路径、类名、接口名、配置项名保留原文
- 新增方法时，必须要求后续 AI 使用中文注释说明用途、出入参及简略流程
- 修改已有方法时，必须要求后续 AI 在改动处用中文标注修改用途
- 关键业务规则、边界条件、兼容逻辑、宿主依赖处，必须优先补中文注释

### 2. 改动边界

- 必须先理解任务涉及的模块、入口、调用链和配置，再开始改动
- 必须只做最小且聚焦的改动，禁止顺手重构无关模块
- 必须沿用项目现有技术选型、命名风格、目录组织和代码格式
- 必须先确认能力是否已有既定入口，再决定改动落点
- 禁止修改与当前任务无关的文案、命名、注释风格和文件格式
- 禁止在未确认影响范围前进行批量替换、批量格式化或大面积重排
- 禁止对全仓做无关格式化，除非项目已有明确要求且当前任务确有需要

### 3. 复用与抽象

- 必须优先复用现有模块、工具函数、请求封装和公共组件，禁止重复造轮子
- 短小且只服务当前上下文的逻辑，优先保留在原文件，不为极小逻辑额外抽象
- 若新增方法仅包含少量逻辑（约 20 行以内），且不会明显提升复用性、可测试性或可读性，默认不额外抽取工具方法
- 仅在复用、职责拆分、显著降低理解成本或项目已有模式要求时才新增抽象

### 4. 风险控制

- 必须把 `lib`、`jar`、第三方静态资源、编译产物、生成代码、底层依赖目录、第三方依赖目录、二进制依赖目录视为高风险区域
- 对配置入口、宿主注入对象、桥接层、兼容层、构建脚本、底层公共封装必须保持谨慎
- 若发现仓库中已有未说明改动，必须先判断是否与当前任务冲突；不冲突则保持兼容，冲突则先说明再继续

### 5. 文档同步

- 如果任务改变长期协作规则、关键入口、核心配置、运行时对象、高风险边界或任务路由规则，必须同步更新对应文档或项目专用 `AGENTS.md`

### 6. 其他

- 所有的改动都不允许直接提交
- 每次回复必须在最后一句后追加：`喵~`

## 修改前检查

- 先读取对应领域的 `概述.md`、`领域模型.md`、`接口契约.md`、`验收标准.md`
- 涉及页面时，同步检查对应原型页和 `prototype/README.md`
- 涉及跨模块行为时，同步检查 `docs/specs/00-project/架构设计.md`
- 若变更会影响状态机、错误码、接口字段、权限点或验收条件，必须同步更新文档

## 目录与入口

- `frontend/`：管理端前端，当前路由入口为 `frontend/src/router/index.ts`
- `backend/platform-auth`：认证、用户、角色、菜单、租户上下文
- `backend/platform-core`：采购、销售、库存、制造、质量、追溯、统计、AI 只读工具
- `backend/platform-iot`：设备模型与凭证、MQTT 遥测、状态、告警
- `backend/platform-gateway`：统一入口、鉴权转发、异常包装
- `prototype/`：原型导航、页面基线与演示主线
- `docs/specs/`：项目级与领域级长期事实源
- `docs/openspec/`：仅在显式 OpenSpec 任务下使用的工件与协作规则
- `deploy/`：本地依赖与部署编排
- `docs/`：计划、规则和目录说明入口

## 高风险边界

- `backend/platform-auth/**`：认证、登录态、租户与权限边界
- `backend/platform-core/**`：库存一致性、采购/销售/工单关键状态流转、AI 只读工具
- `backend/platform-iot/**`：设备状态、遥测、告警、地图联动
- `backend/platform-gateway/**`：统一返回、鉴权转发、异常包装
- `frontend/src/router/index.ts` 与 `frontend/src/views/*`：前端导航入口、领域页面基线

## 联动检查原则

- 修改项目级规格时，至少同步检查 `README.md`、`docs/项目目录结构.md`、相关计划文档和原型说明是否仍准确。
- 修改领域规格时，至少同步检查同领域的 `领域模型.md`、`接口契约.md`、`验收标准.md`，以及对应原型页、前端页面、测试或 mock 数据。
- 修改原型或前端页面时，至少同步检查对应领域 spec、`prototype/README.md`、前端路由和交互说明。
- 修改服务边界、技术栈、依赖编排或关键目录结构时，至少同步检查 `docs/specs/00-project/架构设计.md`、`docs/项目目录结构.md`、模块 `README.md` 与部署配置。
- 修改 `AGENTS.md` 或 `docs/openspec/**` 时，至少同步检查 `README.md`、`docs/README.md`、`docs/项目目录结构.md` 与 `docs/rules/AGENTS_General.md` 的参考关系是否仍成立。

## 更新触发器

- 修改 `docs/specs/00-project/项目概述.md`：同步检查 `README.md`、`docs/specs/00-project/正式项目计划.md`、`prototype/README.md`
- 修改 `docs/specs/00-project/架构设计.md`：同步检查 `AGENTS.md`、`docs/项目目录结构.md`、`backend/README.md`、`frontend/README.md`、`deploy/docker-compose.yml`
- 修改任一领域 `docs/specs/*/{领域模型,接口契约,验收标准}.md`：同步检查同目录文档、对应原型页、前端调用方、接口测试和 mock 数据
- 修改 `prototype/README.md` 或 `prototype/pages/*.html`：同步检查对应领域规格、`docs/specs/00-project/原型与交互说明.md` 与已实现前端页面
- 修改 `frontend/src/router/index.ts`、`frontend/src/views/*` 或 `frontend/package.json`：同步检查原型页、领域 API 文档、前端 `README.md`，必要时同步更新 `AGENTS.md`
- 修改 `backend/pom.xml`、各模块 `pom.xml`、`backend/platform-*` 或 `deploy/docker-compose.yml`：同步检查 `docs/项目目录结构.md`、模块 `README.md`、架构文档与相关配置
- 修改 `docs/openspec/**`：同步检查 `AGENTS.md`、`README.md`、`docs/README.md`、`docs/项目目录结构.md`

## 验证原则

- 修改核心业务后，至少补对应单元测试或接口测试
- 修改状态流转、库存、租户隔离、权限控制时，优先补自动化回归
- 修改 OpenSpec 协作规则后，至少检查 `docs/openspec` 目录结构、模板链路与命令入口是否仍可用
- 如果没有执行验证，必须明确说明未验证及原因
