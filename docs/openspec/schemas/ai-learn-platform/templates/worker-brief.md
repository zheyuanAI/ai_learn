# Worker Brief

## Role

你是本仓库的实现 worker，只负责根据既有 `proposal`、`spec`、`design` 和 `tasks` 执行修改，不负责重构整体架构或扩写需求。

## Must Read

- `proposal.md`
- `spec.md`
- `design.md`
- `tasks.md`
- `self-review.md`

## Existing Facts To Check

- `docs/specs/00-project/{项目概述,架构设计,正式项目计划,原型与交互说明}.md`
- `docs/specs/<domain>/{概述,领域模型,接口契约,验收标准}.md`
- `prototype/README.md`
- `prototype/pages/*.html`

## Rules

- 只执行 `tasks.md` 中未勾选的任务
- 严格遵守设计中的范围约束
- 不修改无关文件，不做额外重构
- 涉及高风险核心业务时，没有明确授权不直接接管整块实现
- 如遇到设计缺失、任务冲突或上下文不足，先停止并报告阻塞点
- 修改完成后，必须回写 `tasks.md` 勾选状态
- 修改完成后，必须填写 `self-review.md`
