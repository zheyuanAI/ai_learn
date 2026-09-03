# 制造与仓储协同执行平台

面向中小型离散装配制造企业的学习型一期项目，以“销售需求—人工供需关联—采购入库—生产领料与执行—成品入库—销售发货”为黄金业务闭环。当前仓库仍处于规格、原型和工程骨架阶段，实际已实现能力以代码与配置为准。

## 工作区定位
- 当前开发项目根目录为 `D:\AI\ai_learn_wms_ai\ai_learn_developProject`；本项目的代码、配置和文档均在该目录内维护。
- `D:\AI\ai_learn_wms_ai` 是旧工作区容器，不再作为当前项目的开发入口。
- `D:\AI\ai_learn_wms_ai\ai_learn_referenceProjects` 是学习参考目录，仅供只读阅读和对照；参考工程的代码、配置和版本不代表本项目已实现事实。

## 文档入口
- `AGENTS.md`: 本项目协作规则
- `CLAUDE.md`: 面向 Claude 的同步协作规则
- `docs/词汇定义表.md`：已确认的项目术语
- `docs/specs/00-project/正式项目计划.md`: 当前一个月开发计划与一期范围
- `docs/specs/00-project/全模块业务规则总览.md`：七组一期业务能力、跨模块关系和详细规则索引
- `docs/specs/00-project/阶段决策与续聊入口.md`：已确认阶段决策与新对话续聊入口
- `docs/业务架构/index.html`：交互式业务架构预览入口；`总览.html` 展示跨模块关系与流程
- `docs/prototype/index.html`：静态操作原型主入口
- `docs/openspec/README.md`: 仅在显式 OpenSpec 任务下使用的协作说明

## 本地开发环境基线
- 详细版本、端口与中间件边界以 `docs/specs/00-project/架构设计.md` 为准，以下为快速摘要。
- 当前本机开发实例使用 Java 21、Maven 3.9.1、Node 20 与 PostgreSQL 12.1（`127.0.0.1:5433/ai_learn`）；SQL/Flyway 兼容性以下限 12.1 为准。
- Gateway、Auth、Core、IoT 分别使用 20001、10002、10003、10004；Redis 和 Mosquitto 的项目内手动启动位置见 `runtime/README.md`。
- 参考工程可继续保留各自的 Node 22 与 PostgreSQL 12.1 及以上兼容配置；其中出现的历史端口或高版本示例不代表当前项目事实。

## 文件结构

以下树结构是本项目唯一维护的目录说明入口，只展开主要目录和关键文件；`node_modules/`、`target/`、`dist/` 及 `runtime/` 下的本地中间件包不展开，`.agents/`、`.superpowers/` 和 `output/` 等本地辅助目录也不作为业务代码展开。

```text
ai_learn_developProject/
├── README.md                         # 项目总览、边界与阅读顺序
├── AGENTS.md                         # 当前生效的 AI 协作规则
├── CLAUDE.md                         # 面向 Claude 的同步协作规则
├── .gitignore                        # Git 忽略规则
├── backend/                          # Spring Boot 后端多模块工程
│   ├── pom.xml                       # Maven 聚合配置与依赖入口
│   ├── README.md                     # 后端模块说明
│   ├── platform-gateway/             # 统一网关、鉴权转发与异常包装
│   ├── platform-auth/                # 认证、用户、角色、菜单与租户上下文
│   ├── platform-core/                # ERP/WMS、MES、质量、追溯与 AI 只读工具
│   ├── platform-iot/                 # IoT 设备、遥测、状态与告警
│   └── platform-shared/              # 后端公共基础库
├── frontend/                         # Vue 3 + Vite + TypeScript 前端工程
│   ├── package.json                  # 前端依赖与脚本入口
│   ├── vite.config.ts                # Vite 构建配置
│   ├── index.html                    # 前端入口模板
│   └── src/
│       ├── main.ts                   # 前端启动入口
│       ├── App.vue                   # 应用根组件
│       ├── router/index.ts           # 前端路由入口
│       └── views/                    # 页面视图组件
├── docs/                             # 规格、原型、计划与协作文档
│   ├── 词汇定义表.md                  # 已确认的统一业务术语
│   ├── 架构思考卡.md                  # 架构边界与思考记录
│   ├── specs/                         # 项目级与领域级长期规格
│   │   ├── 00-project/               # 项目目标、架构边界与里程碑
│   │   ├── 10-erp-wms/               # ERP/WMS 领域规格
│   │   ├── 20-mes/                   # MES 领域规格
│   │   ├── 30-iot-digital-twin/      # IoT 与数字孪生规格
│   │   ├── 40-gis-dashboard/         # GIS 与综合看板规格
│   │   └── 50-ai-assistant/          # AI 助手与审计规格
│   ├── 业务架构/                     # 业务关系、流程、事实边界与模块架构图
│   │   ├── index.html                # 业务架构预览入口
│   │   ├── 总览.html                 # 模块拓扑、黄金闭环与事实流总览
│   │   └── assets/                   # 业务架构数据、渲染脚本与独立样式
│   ├── prototype/                    # 静态交互原型与演示页面
│   │   ├── index.html                # 原型首页
│   │   ├── pages/                    # 各业务页面原型
│   │   └── assets/                   # 原型共用资源
│   ├── openspec/                     # 显式 OpenSpec 任务的协作工件
│   │   ├── README.md
│   │   ├── config.yaml
│   │   ├── guides/
│   │   ├── schemas/
│   │   └── specs/
│   └── superpowers/                  # 设计规格与实施计划
│       ├── specs/
│       └── plans/
├── deploy/                           # 本地基础设施与部署编排
│   ├── docker-compose.yml            # PostgreSQL、Redis、Mosquitto 编排
│   ├── local/                        # 本机运行配置
│   └── docker/                       # Compose 使用的配置
└── runtime/                          # 本机运行时依赖与启动说明
    └── README.md
```

### 目录职责与关键入口

- `backend/`：Spring Boot 后端多模块工程。`platform-gateway` 负责统一入口，`platform-auth` 负责认证与租户模块，`platform-core` 承载 ERP/WMS、MES 等核心业务，`platform-iot` 承载设备与遥测告警，`platform-shared` 提供公共基础库。
- `frontend/`：Vue 3 + Vite + TypeScript 前端工程。`frontend/src/main.ts` 是启动入口，`frontend/src/App.vue` 是根组件，`frontend/src/router/index.ts` 是路由入口，`frontend/src/views/` 承载页面视图。
- `docs/specs/`：项目级和领域级目标规格事实源，描述批准后的范围、模型、接口和验收要求；不能据此声称代码已经实现。
- `docs/业务架构/`：以交互图说明模块关系、业务流程、事实归属和边界；全部一期模块的业务规则已经确认，架构图后续应按正式规格同步更新。
- `docs/prototype/`：静态交互原型和演示页面基线，页面位于 `pages/`，共用资源位于 `assets/`。
- `docs/openspec/`：仅在用户明确以 `OpenSpec` 或 `openspec` 开头时使用的协作规则、schema、模板和工件入口。
- `docs/superpowers/`：设计规格与实施计划记录，服务于 AI/代理协作和变更追溯，不替代 `docs/specs/`。
- `deploy/docker-compose.yml`：一期本地基础依赖编排，当前包含 PostgreSQL、Redis 和 Mosquitto；`deploy/local/` 保存本机配置，`deploy/docker/` 保存 Compose 使用的配置。
- `runtime/README.md`：本机 Redis、Mosquitto 等运行时依赖的校验信息、手动启动命令和回退说明；`runtime/` 下的中间件包不纳入 Git。

### 关键后端模块入口

- `backend/platform-gateway`：`GatewayApplication.java`、`HealthController.java` 和 `application.yml`。
- `backend/platform-auth`：`AuthApplication.java`、`HealthController.java` 和 `application.yml`。
- `backend/platform-core`：`CoreApplication.java`、`HealthController.java` 和 `application.yml`。
- `backend/platform-iot`：`IotApplication.java`、`HealthController.java` 和 `application.yml`。
- `backend/platform-shared`：公共基础库及共享代码入口。

### 文档、原型与配置入口

- `docs/specs/00-project/`：项目级规格，包含 `正式项目计划.md`、`项目概述.md`、`架构设计.md`、`原型与交互说明.md`、`全模块业务规则总览.md` 和 `阶段决策与续聊入口.md`。
- `docs/specs/*/`：领域规格统一包含 `概述.md`、`领域模型.md`、`接口契约.md` 和 `验收标准.md`；当前领域为 ERP/WMS、MES、IoT、GIS/综合看板和 AI 助手。
- `docs/业务架构/index.html`、`docs/业务架构/总览.html`：分别作为业务架构预览和总体关系入口；七个模块页共用数据与渲染资源，并与原型空间仅通过双方主入口互相跳转。
- `docs/prototype/README.md`、`docs/prototype/index.html`：原型目标、页面清单、演示主线和入口；`assets/` 保存共用脚本与样式，`pages/` 保存各业务页面，具体页面分类与审查顺序以该 README 为准。
- `docs/openspec/README.md`：仅显式 OpenSpec 任务使用的协作入口；`config.yaml`、`guides/`、`schemas/` 和 `specs/` 分别保存配置、提示模板、工件模板和长期协作 spec。
- `frontend/package.json`、`frontend/vite.config.ts`、`frontend/tsconfig.json`：前端依赖、构建和 TypeScript 配置；`frontend/src/main.ts`、`App.vue`、`router/index.ts` 和 `views/` 是前端启动、根组件、路由和页面入口。
- `backend/pom.xml`：后端父工程聚合入口；各 `platform-*` 模块的 `pom.xml`、启动类、探活接口和 `application.yml` 位于对应模块的 `src/` 目录。

### 文档事实边界

- 已实现事实以 `frontend/`、`backend/`、`deploy/` 中实际生效的代码和配置为准。
- `docs/specs/` 记录目标规格，`docs/业务架构/` 解释业务关系与边界，`docs/prototype/` 记录目标交互；三者都不能替代代码实现状态。
- `README.md` 的本节是当前项目唯一的目录与关键入口说明；目录或入口变化时，同步更新本文件。
- 参考工程位于 `D:\AI\ai_learn_wms_ai\ai_learn_referenceProjects`，只读用于学习和对照，不属于本项目目录树。

## 开发原则
- 黄金业务闭环优先于功能数量；一期不实现 MRP、APS、三维数字孪生、RAG 或完整财务
- 使用 spec 驱动开发：先确认业务语义与验收，再同步原型、数据模型、接口契约和实现
- AI 负责非核心代码编码、生成测试、文档样板；人工负责核心代码编码、规则定义、验收和关键决策
- 普通任务默认不走 OpenSpec；仅显式以 `OpenSpec` 或 `openspec` 开头时，才读取 `docs/openspec/README.md`
- 所有测试前移，核心链路每周都要有自动化回归

## 建议的执行顺序
1. 阅读 `docs/specs/00-project/正式项目计划.md` 与 `docs/词汇定义表.md`
2. 按黄金业务闭环核对 `docs/specs/00-project/项目概述.md`、`架构设计.md` 和领域规格
3. 遇到术语、库存时点、状态迁移或跨域关联不清时，先沟通再编码
