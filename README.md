# 制造与仓储协同、设备数字孪生与 AI 助手平台

## 文档入口
- `docs/README.md`: 文档总目录
- `AGENTS.md`: 本项目协作规则
- `openspec/README.md`: 仅在显式 OpenSpec 任务下使用的协作说明

## 目录说明
- `openspec`: OpenSpec 协作规则、schema、模板与长期协作 spec
- `specs/00-project`: 项目总目标、架构边界、原型范围、里程碑
- `specs/10-erp-wms`: ERP/WMS 领域规格
- `specs/20-mes`: MES 领域规格
- `specs/30-iot-digital-twin`: IoT 与数字孪生规格
- `specs/40-gis-dashboard`: GIS、三维与综合看板规格
- `specs/50-ai-assistant`: AI 助手、RAG、工具调用规格
- `docs/superpowers/plans`: 面向 AI/代理执行的实施计划

## 开发原则
- 功能不减少，但分阶段交付，每个模块先完成 P0 可演示闭环
- 使用 spec 驱动开发：先原型，再数据模型、接口契约、测试验收、功能实现
- AI 负责非核心代码编码、生成测试、文档样板；人工负责核心代码编码、规则定义、验收和关键决策
- 普通任务默认不走 OpenSpec；仅显式以 `OpenSpec` 或 `openspec` 开头时，才读取 `openspec/README.md`
- 所有测试前移，核心链路每周都要有自动化回归

## 建议的执行顺序
1. 阅读 `specs/00-project/overview.md`
2. 完成 `specs/00-project/prototype.md` 中的页面原型
3. 冻结各模块 `domain.md` 与 `api.md`
4. 参考 `docs/plans/10_week_project_plan.md` 与 `docs/superpowers/plans/2026-06-30-ai-learn-project.md` 逐步实现
