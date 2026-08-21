# 制造与仓储协同执行平台

面向中小型离散装配制造企业的学习型一期项目，以“销售需求—人工供需关联—采购入库—生产领料与执行—成品入库—销售发货”为黄金业务闭环。当前仓库仍处于规格、原型和工程骨架阶段，实际已实现能力以代码与配置为准。

## 文档入口
- `docs/README.md`: 文档总目录
- `AGENTS.md`: 本项目协作规则
- `CONTEXT.md`: 已确认的项目术语
- `docs/specs/00-project/正式项目计划.md`: 当前一个月开发计划与一期范围
- `docs/plans/十周学习计划-历史.md`: 历史十周学习资料，不作为当前实施入口
- `docs/openspec/README.md`: 仅在显式 OpenSpec 任务下使用的协作说明

## 本地开发环境基线
- 自研项目统一使用 Java 21、Maven 3.9.1、Node 20 与 PostgreSQL 16（端口 5433）。
- Gateway、Auth、Core、IoT 分别使用 10001、10002、10003、10004；Redis 和 Mosquitto 的项目内手动启动位置见 `runtime/README.md`。
- 参考工程可继续保留各自的 Node 22 与 PostgreSQL 16（端口 5323）配置。

## 目录说明
- `docs/openspec`: OpenSpec 协作规则、schema、模板与长期协作 spec
- `docs/specs/00-project`: 项目总目标、架构边界、原型范围、里程碑与当前计划
- `docs/specs/10-erp-wms`: ERP/WMS 领域规格
- `docs/specs/20-mes`: MES 领域规格
- `docs/specs/30-iot-digital-twin`: 一期 IoT 设备事实规格；数字孪生为二期候选
- `docs/specs/40-gis-dashboard`: 一期二维地图与综合看板规格
- `docs/specs/50-ai-assistant`: 一期 AI 只读工具与审计规格；RAG 为二期候选
- `docs/superpowers/plans`: 面向 AI/代理执行的实施计划

## 开发原则
- 黄金业务闭环优先于功能数量；一期不实现 MRP、APS、三维数字孪生、RAG 或完整财务
- 使用 spec 驱动开发：先确认业务语义与验收，再同步原型、数据模型、接口契约和实现
- AI 负责非核心代码编码、生成测试、文档样板；人工负责核心代码编码、规则定义、验收和关键决策
- 普通任务默认不走 OpenSpec；仅显式以 `OpenSpec` 或 `openspec` 开头时，才读取 `docs/openspec/README.md`
- 所有测试前移，核心链路每周都要有自动化回归

## 建议的执行顺序
1. 阅读 `docs/specs/00-project/正式项目计划.md` 与 `CONTEXT.md`
2. 按黄金业务闭环核对 `docs/specs/00-project/项目概述.md`、`架构设计.md` 和领域规格
3. `docs/plans/十周学习计划-历史.md` 为历史十周学习资料，不作为当前实施入口
4. 遇到术语、库存时点、状态迁移或跨域关联不清时，先沟通再编码
