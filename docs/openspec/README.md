# ai_learn OpenSpec 协作说明

本文件是当前仓库唯一的 OpenSpec / openspec 详细规则入口。
当且仅当用户明确以 `OpenSpec` 或 `openspec` 作为任务开头时，才需要按本文件执行。

## 触发与输入

- 未出现 `OpenSpec` 或 `openspec` 前缀时，默认按普通任务处理，不自动进入 OpenSpec。
- 用户进入 OpenSpec 流程时，只需要描述需求、范围、限制和验收条件，不需要手动输入 `/opsx:*` 或 `openspec *` 命令。
- 若目标、禁改范围、验收标准、是否需要归档或是否允许 `Codex` 直接实现等信息不足，`Codex` 必须先补齐，再进入工件阶段。
- 若用户明确要求“不要再确认、直接出工件”，且上下文已足够清楚，`Codex` 才可直接生成工件。

## 角色分工

- `Codex`：负责需求澄清、生成或补齐 `proposal / spec / design / tasks / worker-brief / self-review / review / retrospective`、输出交接提示词、最终审核、返工意见和归档判断。
- `Antigravity`：负责读取工件、按 `tasks.md` 实施修改、更新任务状态、填写 `self-review.md`、先做执行器自检，再回传给 `Codex` 审核。
- 用户：负责需求边界、核心业务规则确认、关键实现授权、验收与最终取舍。
- `OpenSpec`：负责承载 `changes/`、模板、schema 和长期协作 spec，让每次需求都有稳定工件和可追踪输出。

默认分工是：`Codex` 决策与审核，用户手动调用 `Antigravity` 执行。
除非用户明确要求，否则 `Codex` 不默认直接承担核心业务代码实现。

## 与当前仓库约束的衔接

- 项目事实以 `docs/specs/00-project/{项目概述,架构设计,正式项目计划,原型与交互说明,阶段决策与续聊入口}.md` 和各领域 `docs/specs/*/{概述,领域模型,接口契约,验收标准}.md` 为准。
- 领域行为变更若影响状态机、错误码、接口字段、权限点或验收条件，必须同步回写现有 `docs/specs/` 文档，不能只停留在 OpenSpec 工件中。
- 页面或交互变更若影响字段、状态或按钮，必须同步检查 `docs/prototype/README.md`、`docs/prototype/pages/*.html` 和对应前端页面。
- 登录、JWT、租户上下文、权限菜单、库存冻结/释放/扣减/回补、采购入库、销售出库、工单执行、幂等、租户隔离、库存一致性、AI 工具审计属于高风险范围；若要由 AI 直接实现，必须有用户明确授权。
- AI 相关能力默认只读，必须保留审计痕迹。

## 目录说明

```text
docs/openspec/
├── README.md
├── config.yaml
├── guides/
│   ├── antigravity-apply.md
│   └── codex-review.md
├── schemas/
│   └── ai-learn-platform/
│       ├── schema.yaml
│       └── templates/
└── specs/
    └── collaboration/
        └── spec.md
```

## OpenSpec 工作目录

本仓库将 OpenSpec 工件放在 `docs/openspec/`，因此从当前项目根目录执行时，OpenSpec CLI
的受支持工作目录是 `docs/`，不是项目仓库根目录。项目构建、前端和后端命令仍按各自
README 的说明从项目仓库根目录或对应子目录执行。

```powershell
cd docs
openspec list --specs --json
openspec schemas --json
openspec templates --json
openspec validate --specs --no-interactive --json
# 存在具体 change 时再执行：openspec status --change <change-name> --json
```

以上命令用于确认当前 `docs/openspec/` 工件可被 CLI 识别、schema/template 可读取且长期
协作 spec 符合当前工具格式；不要把从项目仓库根目录运行 CLI 的结果表述为已验证。

## 常用命令

当前环境已确认存在 `openspec` 命令入口，可使用：

```powershell
cd docs
openspec update
```

```text
/opsx:new <change-name>
/opsx:ff <change-name>
/opsx:propose <change-name>
/opsx:verify <change-name>
/opsx:archive <change-name>
```

说明：

- 用户不需要自己记这些命令；进入 OpenSpec 流程后，可直接让 `Codex` 代为决定当前步骤。
- 若当前是手动协作模式，`Codex` 应直接给出可转发给 `Antigravity` 的提示词，而不是只给命令名。

## 推荐工件

- 基础工件至少包括：`proposal`、`spec`、`design`、`tasks`
- 若任务交给执行器实现，或范围控制严格、返工风险较高，应额外补齐：`worker-brief`、`self-review`
- 按需要补充：`review`、`retrospective`

## 标准流程

`Proposal -> Spec -> Design -> Tasks -> Implementation -> Validation`

推荐落地顺序：

1. `Codex` 与用户确认目标、范围、限制、验收条件，以及是否允许 AI 直接实现核心业务。
2. `Codex` 生成或补齐本次 change 的工件。
3. `Codex` 输出可转发给 `Antigravity` 的执行提示词。
4. 用户手动调用 `Antigravity` 执行实现。
5. `Antigravity` 更新 `tasks.md`、填写 `self-review.md` 并回传审核提示词。
6. `Codex` 审核代码、工件和自检记录，决定通过、返工或补充复盘。
7. 若用户要求归档，审核通过后再归档；否则先汇报结果。

## Codex 规则

- 进入 OpenSpec 流程后，`Codex` 必须先确认需求理解，再生成工件。
- 若任务拆解不足以支撑执行器稳定落地，必须先补工件，不得把模糊需求直接转交出去。
- 涉及多租户、权限、库存一致性、关键状态流转和 AI 审计时，`Codex` 必须在工件中写清允许修改范围、禁改范围、验证要求和失败停止条件。
- 审核时必须以 `proposal.md`、`spec.md`、`design.md`、`tasks.md`、用户验收条件、`self-review.md` 和实际 diff 为准，不得只看表面能否运行。
- 若 Antigravity 已达到 2 次自修上限仍未通过，`Codex` 必须给出明确返工意见，必要时补充 `review.md` 或 `retrospective.md`。

## Antigravity 规则

- `Antigravity` 只负责按既有工件执行实现，不负责擅自改写需求、重写设计或跳过 `tasks.md`。
- 实现时必须严格限制在 `tasks.md` 和交接提示词指定范围内，禁止顺手重构、扩大改动范围、修改无关文件或引入未确认的新功能。
- 若遇到设计缺口、任务歧义、代码现状与工件冲突、验证失败或无法继续实现，必须停止自行扩展，并回传可转发给 `Codex` 的阻塞提示词。
- 完成实现后，必须先更新 `tasks.md`、填写 `self-review.md`，再回传给 `Codex` 最终审核。

## 交接规则

- OpenSpec 默认采用“可转发提示词”方式交接。
- `Codex` 完成工件后，必须直接给出可转发给 `Antigravity` 的执行提示词。
- `Antigravity` 完成实现后，必须直接给出可转发给 `Codex` 的审核提示词。
- 若出现失败、阻塞、返工或重新审核，当前执行方也必须给出可转发给另一方的新提示词。

### Codex -> Antigravity 交接至少包含

- 当前 change 目标
- 允许修改范围
- 禁止修改范围
- 必读工件
- 必须同步更新的现有 `docs/specs/`、`docs/prototype/` 或 `docs/` 文件
- 验证要求
- 失败时的停止条件
- 完成后必须回写的文件

### Antigravity -> Codex 交接至少包含

- 修改文件列表
- 已完成任务
- 未完成任务
- 验证命令
- 验证结果
- 剩余风险
- 建议重点审核的位置
- 当前自修轮次，是否已达到 2 次上限

## 执行器自检与返工

- `Antigravity` 完成实现后，必须先做执行器自检，再进入 `Codex` 审核。
- 自检至少覆盖：
  - 是否严格限制在允许修改范围内
  - 是否满足 `proposal / spec / design / tasks` 的目标要求
  - 是否同步更新了必须回写的现有 spec、`docs/prototype/` 原型或说明文档
  - 已执行或已确认的验证动作
  - 剩余风险、假设和建议重点审核项
- 若自检未通过，允许先自行修复，但默认自修重试最多 `2` 次。
- 若达到 `2` 次自修后仍未通过，必须停止继续试错，并回传给 `Codex` 等待返工意见。

## 执行提示模板

- 执行提示模板见 `./guides/antigravity-apply.md`
- 审核提示与检查框架见 `./guides/codex-review.md`
