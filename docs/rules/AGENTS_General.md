# 新项目 `AGENTS.md` + `openspec/README.md` 生成模板

> 用途：本文件不是项目最终使用的规则文档，而是指导 `Codex` 为其他仓库生成“轻量主规则 + OpenSpec 自动化流程”的迁移模板。  
> 目标：减少常驻 token，把项目事实与 OpenSpec 细则分层，并让用户只提需求就能跑通 `Codex 决策/审核 + Antigravity 执行` 的自动化协作。

## 设计目标

给新项目生成规则时，优先实现以下结果：

1. 普通任务默认只读取 `AGENTS.md`
2. 只有用户明确写 `OpenSpec` 或 `openspec` 前缀时，才进入 OpenSpec 流程
3. 进入 OpenSpec 后，用户只提需求，不需要自己输入 `/opsx:*` 或 `openspec *` 命令
4. `Codex` 自动负责澄清、工件生成、执行提示词、审核、返工意见和归档判断
5. `Antigravity` 只按工件实现，不擅自扩需求、改设计或跳过自检

## 为什么推荐这种结构

相比把所有规则都堆进一个 `AGENTS.md`，推荐拆成：

- `AGENTS.md`：只保留项目事实、通用工作规则、最小 OpenSpec 触发提示
- `openspec/README.md`：承载完整 OpenSpec / openspec 协作规则、目录说明、常用命令、交接规则、审核归档规则

这样做的好处：

- 避免 OpenSpec 细则在普通任务中反复占用上下文
- 避免协作规则和项目事实混写后长期漂移
- 更容易迁移到其他仓库
- 更容易替换执行器，例如从 `Gemini CLI` 迁到 `Antigravity`

## 生成新项目规则前必须做的探索

在给新仓库生成规则前，`Codex` 至少要确认：

- 项目名称、仓库类型、技术栈、Node / 包管理器基线
- 常用开发、构建、预览、测试命令
- 构建产物目录、部署基路径、静态资源路径
- 应用入口、路由入口、请求入口、状态入口、关键业务目录
- 当前实际生效页面和主业务入口
- 运行时全局对象、宿主对象、桥接层、Electron / GIS / 编辑器等特殊边界
- 第三方静态资源目录、构建产物目录、历史兼容目录
- 文档事实源优先级，以及哪些文档只是弱参考

无法确认的事实，必须明确写 `未在仓库中确认`，禁止编造。

## 新项目推荐产物结构

若项目启用 OpenSpec，推荐至少生成：

```text
AGENTS.md
openspec/
├── README.md
├── config.yaml
├── guides/
│   ├── antigravity-apply.md
│   └── codex-review.md
├── schemas/
│   └── <project-schema-name>/
│       ├── schema.yaml
│       └── templates/
│           ├── proposal.md
│           ├── spec.md
│           ├── design.md
│           ├── tasks.md
│           ├── worker-brief.md
│           ├── self-review.md
│           ├── review.md
│           └── retrospective.md
└── specs/
    └── collaboration/
        └── spec.md
```

## `AGENTS.md` 的职责

新项目的 `AGENTS.md` 应聚焦以下内容：

1. 项目概览
2. 常用命令
3. 构建与产物约束
4. 必读上下文
5. 文档与事实源
6. 禁止推断原则
7. 修改前检查
8. 通用工作规则
9. 目录与入口
10. 关键模块、运行时边界、高风险目录
11. 联动检查原则
12. 更新触发器
13. 验证原则

其中 OpenSpec 只保留最小触发规则，例如：

- 仅当用户明确以 `OpenSpec` 或 `openspec` 作为任务开头时，才进入 OpenSpec 处理流程
- 进入 OpenSpec 流程后，必须先阅读 `openspec/README.md`，再继续需求澄清、工件生成、实现交接、执行器自检、审核、返工和归档

## `openspec/README.md` 的职责

`openspec/README.md` 应承载完整 OpenSpec 细则，至少包括：

1. 触发条件
2. 用户输入规则
3. 角色分工
4. 目录说明
5. 常用命令
6. 推荐工件
7. 标准流程
8. `Codex` 规则
9. 执行器规则
10. 交接规则
11. 自检与返工规则
12. 审核与归档规则
13. 与当前仓库约束的衔接

不要再为这些细则额外生成 `AGENTS_OpenSpec.md`，避免三份文档并行维护。

## OpenSpec 自动化流程要求

如果用户要求新项目支持 OpenSpec 自动化流程，`Codex` 应按以下顺序处理：

1. 探索仓库，提炼项目事实和高风险边界
2. 检查项目是否已安装或初始化 OpenSpec；若缺失，则补齐 `openspec/` 基础结构
3. 生成项目专用 `AGENTS.md`
4. 生成项目专用 `openspec/README.md`
5. 生成 `openspec/config.yaml`
6. 生成 `guides/`、`schema.yaml` 与 `templates/`
7. 生成 `specs/collaboration/spec.md`
8. 校验 OpenSpec 目录是否能支撑 `proposal -> design -> tasks -> implementation -> validation`
9. 把触发规则写回 `AGENTS.md`
10. 明确告诉后续 AI：普通任务不走 OpenSpec，只有前缀触发才读取 `openspec/README.md`

## 用户只提需求时的自动行为

启用自动化后，用户不需要自己手写命令。进入 OpenSpec 流程时应这样工作：

1. 用户说：`OpenSpec 修复 A 功能`
2. `Codex` 先确认范围、限制、验收条件、是否自动归档
3. `Codex` 自动生成或补齐工件：`proposal / design / tasks`，必要时补 `worker-brief / self-review / review / retrospective`
4. `Codex` 直接输出一段可转发给 `Antigravity` 的执行提示词
5. 用户把提示词交给 `Antigravity`
6. `Antigravity` 按工件实现，更新 `tasks.md`、填写 `self-review.md`
7. `Antigravity` 再输出一段可转发给 `Codex` 的审核提示词
8. 用户把审核提示词发回 `Codex`
9. `Codex` 审核并给出通过、返工或补充复盘结论
10. 若用户要求自动归档，审核通过后再归档；否则先汇报

## `Codex` 在 OpenSpec 自动化中的强制职责

- 先澄清后出工件；除非上下文已足够明确或用户明确要求，否则不能直接跳到工件阶段
- OpenSpec 流程中，除非用户明确要求，否则 `Codex` 不默认直接承担 Implementation
- `Codex` 必须保证 `tasks.md` 可顺序执行，不能写成抽象口号
- `Codex` 必须保证 `worker-brief.md` 明确写出允许修改范围、禁止修改范围、输出要求、验证要求和阻塞处理方式
- `Codex` 必须保证 `self-review.md` 只记录执行器自检，不与最终审核结论混写
- `Codex` 审核时必须以 `proposal.md`、`design.md`、`tasks.md`、用户验收条件、执行器返回结果和 `self-review.md` 为准
- 若执行器自修 2 次后仍未通过，`Codex` 必须给出明确返工意见，而不是只写笼统结论

## `Antigravity` 在 OpenSpec 自动化中的强制职责

- 只按 `tasks.md` 和交接提示词执行，不擅自扩写需求、重写设计或脱离工件自行实现
- 严格限制在允许修改范围内，禁止顺手重构、扩大改动范围或修改无关文件
- 若遇到设计缺口、任务歧义、代码现状与工件冲突、验证失败或无法继续实现，必须停止自行扩展
- 完成实现后，必须先更新 `tasks.md`、填写 `self-review.md`，再回传给 `Codex`
- 自检未通过时，默认最多允许 `2` 次自修；超过后必须停止试错并等待 `Codex` 返工意见

## 交接提示词要求

OpenSpec 默认采用“可转发提示词”方式。

`Codex -> Antigravity` 的提示词至少应包含：

- 当前目标
- 允许修改范围
- 禁止修改范围
- 必读工件
- 验证要求
- 失败时停止条件
- 完成后必须回写的文件

`Antigravity -> Codex` 的提示词至少应包含：

- 修改文件列表
- 已完成任务
- 未完成任务
- 验证命令
- 验证结果
- 剩余风险
- 建议重点审核位置
- 当前自修轮次，是否已达到 2 次上限

若失败、阻塞、返工或多轮修复，当前执行方也必须继续输出新的可转发提示词，而不是让用户自己总结。

## 推荐复用与必须项目化的文件

迁移到其他项目时，可把 OpenSpec 相关文件分成两类：

### 可直接作为参考复用

- `openspec/guides/antigravity-apply.md`
- `openspec/guides/codex-review.md`
- `openspec/specs/collaboration/spec.md`
- `openspec/schemas/<schema>/templates/*.md` 的结构与字段组织方式

### 必须按新项目重写

- `AGENTS.md`
- `openspec/README.md`
- `openspec/config.yaml`
- `openspec/schemas/<schema>/schema.yaml` 中的名称、描述、工件要求
- `worker-brief.md`、`self-review.md`、`review.md` 中涉及项目边界、目录、运行时对象的约束

原则是：结构可以复用，项目事实必须重写。

## 初始化与校验建议

若新项目要真正落地自动流程，建议 `Codex` 在生成完文档后至少再做一次校验：

1. `openspec update` 是否可执行
2. `openspec/config.yaml` 是否与当前项目事实一致
3. `openspec/README.md` 是否已经写明“用户只提需求即可”
4. `AGENTS.md` 是否只保留最小 OpenSpec 触发规则
5. 目录、schema、templates 是否完整
6. 是否仍残留旧执行器名称，例如 `Gemini CLI`
7. 是否把项目事实和 OpenSpec 细则分层，避免重复

## 文档优化原则

为减少 token 占用，给新项目生成规则时应优先遵守：

- 不重复写同一条“必须同步检查”，改为“联动检查原则”
- 运行时事实集中写一处，不在项目概览、GIS 约束、运行时约束里重复出现
- 目录说明只保留高价值业务目录，不堆过细路径解说
- 明确写出“禁止推断原则”
- 明确写出“修改前检查”
- 普通任务不常驻读取 OpenSpec 细则

## 参考包

若需要把当前这套 OpenSpec 自动化参考结构带到其他项目，可优先携带以下内容作为样板：

- `openspec/README.md`
- `openspec/config.yaml`
- `openspec/guides/`
- `openspec/specs/collaboration/spec.md`
- `openspec/schemas/`

但迁移后必须立刻按新项目重写项目事实、运行时边界、禁改目录、执行器约束和 schema 名称。

## 输出限制

- 生成项目专用规则时，输出结果必须是最终规则正文，不要输出分析过程
- 不要把本模板原文照抄进项目文档
- 不要写“如果项目有前端则……”这类模板腔，要改写成项目实际情况
- 不适用的章节应删除或明确写“不适用”，不要为了凑结构硬写

## 自检要求

生成新项目规则后，至少自检：

1. 项目事实是否来自当前生效代码与配置
2. `AGENTS.md` 是否只保留最小 OpenSpec 触发提示
3. `openspec/README.md` 是否完整承载 OpenSpec 细则与自动化流程
4. 是否仍存在明显重复、模板腔或低价值清单
5. 是否明确了“用户只提需求即可”的自动协作方式
6. 是否区分了“可复用文件”和“必须项目化重写的文件”

## 附录：内嵌模板

以下内容可直接作为新项目生成 OpenSpec 自动化流程时的“单文件模板源”。  
使用方式：

1. 先让 `Codex` 阅读本文件上半部分规则
2. 再从本附录中抽取需要的文件内容
3. 最后按新项目事实改写占位符、目录、边界、运行时对象和禁改范围

### 文件：`AGENTS.md` 中的 OpenSpec 最小触发段

```md
## 任务路由与 OpenSpec

- 仅当用户明确以 `OpenSpec` 或 `openspec` 作为任务开头时，才进入 OpenSpec 处理流程；否则默认按普通任务处理，不因任务复杂度自动进入 OpenSpec。
- 进入 OpenSpec 流程后，必须先阅读 `openspec/README.md`，再继续需求澄清、工件生成、实现交接、执行器自检、审核、返工和归档。
```

### 文件：`openspec/README.md`

```md
# <project-name> OpenSpec 协作说明

本文件是当前仓库唯一的 OpenSpec / openspec 详细规则入口。  
当且仅当用户明确以 `OpenSpec` 或 `openspec` 作为任务开头时，才需要按本文件执行。

## 触发与输入

- 未出现 `OpenSpec` 或 `openspec` 前缀时，默认按普通任务处理，不自动进入 OpenSpec。
- 用户进入 OpenSpec 流程时，只需要描述需求、范围、限制和验收条件，不需要手动输入 `/opsx:*` 或 `openspec *` 命令。
- 若目标、禁改范围、验收标准、是否自动归档等信息不足，`Codex` 必须先沟通补齐，再进入工件阶段。
- 若用户明确要求“不要再确认、直接出工件”，且上下文已足够清楚，`Codex` 才可直接生成工件。

## 角色分工

- `Codex`：负责需求澄清、生成或补齐 `proposal / design / tasks / worker-brief / self-review / review / retrospective`、输出交接提示词、最终审核、返工意见和归档判断。
- `Antigravity`：负责读取工件、按 `tasks.md` 实施修改、更新任务状态、填写 `self-review.md`、先做执行器自检，再回传给 `Codex` 审核。
- `OpenSpec`：负责承载 `changes/`、模板、schema 和长期 spec，让每次需求都有稳定工件和可追踪输出。

默认分工是：`Codex` 决策与审核，用户手动调用 `Antigravity` 执行。  
除非用户明确要求，否则 `Codex` 不默认直接承担业务代码实现。

## 目录说明

```text
openspec/
├── changes/
│   └── <change-name>/
│       ├── proposal.md
│       ├── design.md
│       ├── tasks.md
│       ├── worker-brief.md
│       ├── self-review.md
│       ├── review.md
│       └── retrospective.md
├── README.md
├── config.yaml
├── guides/
│   ├── antigravity-apply.md
│   └── codex-review.md
├── schemas/
│   └── <project-schema-name>/
│       ├── schema.yaml
│       └── templates/
└── specs/
    └── collaboration/
        └── spec.md
```

- `Codex` 对应的主要工件在 `openspec/changes/<change-name>/`：
  - `proposal.md`：记录本次需求目标、范围、非目标和风险边界。
  - `design.md`：记录实现方案、涉及模块、状态流、接口流和禁改范围。
  - `tasks.md`：记录可顺序执行的任务拆解和验证动作，是执行与审核的主清单。
  - `worker-brief.md`：记录交给 `Antigravity` 的执行边界、输出要求和阻塞处理方式。
  - `self-review.md`：记录执行器自检结果，供 `Codex` 审核时参考，但不等于最终审核结论。
  - `review.md`：记录 `Codex` 的正式审核结论、问题和返工意见。
  - `retrospective.md`：记录多轮返工、失败原因和后续流程优化点。
- `Antigravity` 主要读取和回写 `openspec/changes/<change-name>/tasks.md`、`worker-brief.md`、`self-review.md`：
  - 读取 `tasks.md` 与 `worker-brief.md` 后按范围实施修改。
  - 完成后回写 `tasks.md` 的任务状态，并填写 `self-review.md` 后再交回 `Codex`。
  - `openspec/guides/antigravity-apply.md` 用于约束执行提示词格式和自检回传方式。
- `OpenSpec` 负责承载长期稳定的流程骨架和模板：
  - `openspec/README.md`：当前仓库的 OpenSpec 协作入口、角色分工和流程说明。
  - `openspec/config.yaml`：当前项目的 OpenSpec 上下文和工件生成规则。
  - `openspec/schemas/<project-schema-name>/schema.yaml`：定义本项目有哪些工件、依赖关系和生成顺序。
  - `openspec/schemas/<project-schema-name>/templates/`：存放各类工件模板，保证每次 change 的输出结构稳定。
  - `openspec/specs/collaboration/spec.md`：存放长期有效的协作 spec，沉淀跨需求复用的协作约束。
  - `openspec/changes/`：存放每次具体需求的工件目录，保证过程和结论可追踪。

## 常用命令

```powershell
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

- 用户不需要自己记这些命令；进入 OpenSpec 流程后，可直接让 `Codex` 代为决定该用哪一步。
- 若当前是手动协作模式，`Codex` 应直接给出可转发给 `Antigravity` 的提示词，而不是只给命令名。

## 推荐工件

- 基础工件至少包括：`proposal`、`design`、`tasks`
- 若任务交给执行器实现，或范围控制严格、返工风险较高，应额外补齐：`worker-brief`、`self-review`
- 按需要补充：`review`、`retrospective`

## 标准流程

`Proposal -> Design -> Tasks -> Implementation -> Validation`

推荐落地顺序：

1. `Codex` 与用户确认目标、范围、限制、验收条件。
2. `Codex` 生成或补齐本次 change 的工件。
3. `Codex` 输出可转发给 `Antigravity` 的执行提示词。
4. 用户手动调用 `Antigravity` 执行实现。
5. `Antigravity` 更新 `tasks.md`、填写 `self-review.md` 并回传审核提示词。
6. `Codex` 审核代码、工件和自检记录，决定通过、返工或补充复盘。
7. 若用户要求自动归档，审核通过后再归档；否则先汇报结果。

## Codex 规则

- 进入 OpenSpec 流程后，`Codex` 必须先确认需求理解，再生成工件。
- 若任务拆解不足以支持执行器稳定落地，必须先补工件，不得把模糊需求直接转交出去。
- 审核时必须以 `proposal.md`、`design.md`、`tasks.md`、用户验收条件、`self-review.md` 和实际 diff 为准，不得只看表面能否运行。
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
  - 是否满足 `proposal / design / tasks` 的目标要求
  - 已执行或已确认的验证动作
  - 剩余风险、假设和建议重点审核项
- 若自检未通过，允许先自行修复，但默认自修重试最多 `2` 次。
- 若达到 `2` 次自修后仍未通过，必须停止继续试错，并回传给 `Codex` 等待返工意见。

## 执行提示模板

- 执行提示模板见 `./guides/antigravity-apply.md`
- 审核提示与检查框架见 `./guides/codex-review.md`

## 与当前仓库约束的衔接

- 这里必须替换成新项目的禁改目录、高风险目录、运行时对象、路径前缀、构建产物和执行边界
- 结构可以复用，项目事实必须重写
```

### 文件：`openspec/config.yaml`

```yaml
schema: <project-schema-name>

context: |
  项目名称：<project-name>
  技术栈：<tech-stack>
  包管理器：<package-manager>
  Node 版本要求：<node-version>
  部署基础路径：<base-path>
  构建输出目录：<build-output>
  当前主业务模块：<main-module>
  当前主路由：<main-route>
  常用命令：
  - <dev-command>
  - <build-command>
  - <preview-command>
  关键约束：
  - 不修改 <build-output>/、<artifact-dir>/、node_modules/
  - 优先最小改动，不进行无关重构
  - 文档与说明默认使用中文
  - 新增或调整接口时，需同步检查 <api-entry>、<request-entry>、<proxy-entry>
  - 涉及运行时能力时，注意 <runtime-objects> 的初始化时机
  - 修改 OpenSpec 流程时，以 AGENTS.md 与 openspec/README.md 为协作约束事实源

rules:
  proposal: |
    - 写清本次变更的业务目标、非目标、影响范围和风险边界。
    - 如果需求描述含糊，先补齐可执行边界，再进入设计。
  spec: |
    - 使用 requirement + scenario 形式描述行为变更。
    - 只写本次变更影响的行为，不重写整个系统说明。
  design: |
    - 明确涉及文件、模块、状态流、接口流、UI 交互流。
    - 明确禁止修改范围，避免 worker 发散式重构。
  tasks: |
    - 任务必须可由 Antigravity 顺序执行。
    - 每个任务尽量落到具体文件或具体模块。
    - 任务中必须包含验证动作。
    - 任务中必须显式包含执行器自检步骤，要求回写 self-review.md。
  worker-brief: |
    - 明确 worker 只负责实现，不负责重构整体架构。
    - 要求 worker 完成后更新 tasks.md 勾选状态，并汇报测试与风险。
    - 明确禁止范围外改动。
  self-review: |
    - 该文档只记录 Antigravity 的实现后自检，不得写成最终审核结论。
    - 必须覆盖修改范围、行为一致性、已完成验证、剩余风险和交接说明。
  review: |
    - 审核优先关注行为回归、边界条件、测试缺口和与设计不一致之处。
    - 审核前应先参考 self-review.md，但必须保留 Codex 的独立结论。
  retrospective: |
    - 当 Antigravity 自检 2 次后仍未通过，或 Codex 退回导致明显返工时，建议补充 retrospective.md。
```

### 文件：`openspec/guides/antigravity-apply.md`

```md
# Antigravity 执行指令模板

以下内容用于在 Antigravity 中执行 OpenSpec 变更。

## 推荐提示词

```text
你是本仓库的实现 worker，只负责按既有设计执行，不负责改写架构。

请阅读以下文件：
- openspec/changes/<change-name>/proposal.md
- openspec/changes/<change-name>/design.md
- openspec/changes/<change-name>/tasks.md
- openspec/changes/<change-name>/worker-brief.md
- openspec/changes/<change-name>/self-review.md

执行要求：
1. 仅处理 tasks.md 中未勾选的任务
2. 严格遵守 worker-brief.md 的范围约束
3. 不修改无关文件，不进行额外重构
4. 如遇到设计缺失、任务冲突或上下文不足，先停止并指出阻塞点
5. 完成后更新 tasks.md 的勾选状态
6. 完成后填写 self-review.md，先完成实现者自检，再交由 Codex 最终审核
7. 如果自检结论仍是“存在问题待修正”，先分析失败原因并继续自行修复，但自修重试最多 2 次
8. 如果 2 次自修后仍未通过，停止继续试错，等待 Codex 给出返工意见后再继续修改

结束时请输出：
1. 修改了哪些文件
2. 每个任务对应的完成情况
3. 执行了哪些测试命令，结果如何
4. 剩余风险、假设与未完成项
5. 自检结论（是否建议提交 Codex 最终审核）
6. 如曾自检失败，说明失败原因和本轮修复动作
7. 当前自修重试次数，以及是否已达到 2 次上限
```

## 追加修正示例

```text
请基于以下审核意见继续修正当前 change：
1. ...
2. ...

要求：
1. 只处理上述审核问题
2. 不要重做已通过部分
3. 修复后重新汇报变更文件、测试结果与剩余风险
```
```

### 文件：`openspec/guides/codex-review.md`

```md
# Codex 审核指令模板

以下内容用于让 Codex 对 Antigravity 完成的实现进行复核。

## 推荐审核步骤

1. 阅读 `openspec/changes/<change-name>/proposal.md`
2. 阅读 `openspec/changes/<change-name>/design.md`
3. 阅读 `openspec/changes/<change-name>/tasks.md`
4. 阅读 `openspec/changes/<change-name>/self-review.md`
5. 检查 `git diff`
6. 核对 Antigravity 汇报的测试命令与结果
7. 判断实现是否满足 `spec`、`design` 和 `tasks`
8. 如果 Antigravity 已达到 2 次自修上限，给出明确返工意见并决定是否补充 `retrospective.md`

## 推荐提示词

```text
请对当前 change 做审查，重点看行为正确性、边界条件、设计一致性和测试覆盖是否充分。

检查范围：
- openspec/changes/<change-name>/proposal.md
- openspec/changes/<change-name>/design.md
- openspec/changes/<change-name>/tasks.md
- openspec/changes/<change-name>/self-review.md
- 当前 git diff
- worker 汇报的测试结果

输出要求：
1. 先给出 findings，按严重程度排序
2. 每条问题要指出文件路径和原因
3. 如无问题，明确说明未发现阻断问题
4. 最后给出结论：通过 / 退回
5. 如果退回，附上明确返工指令
6. 需要区分“Antigravity 自检结论”和“Codex 最终审核结论”，不能混写
7. 如果 Antigravity 已达到 2 次自修上限，必须给出下一步修改意见，而不是只写笼统结论
```
```

### 文件：`openspec/specs/collaboration/spec.md`

```md
# Collaboration Spec

## Goal

定义当前项目中 `Codex + Antigravity + OpenSpec` 的稳定协作方式。

## Requirements

### Requirement: OpenSpec Trigger

当用户明确以 `OpenSpec` 或 `openspec` 作为任务开头时，系统必须进入 OpenSpec 工件化流程；未出现此前缀时，默认按普通任务处理。

#### Scenario: Explicit OpenSpec Request

- Given 用户输入以 `OpenSpec` 或 `openspec` 开头
- When `Codex` 接手任务
- Then `Codex` 必须先读取 `openspec/README.md`
- And 后续需求澄清、工件生成、实现交接、执行器自检、审核、返工和归档都按 OpenSpec 流程执行

### Requirement: Codex Planning and Review

`Codex` 必须负责需求澄清、工件生成、执行提示词、结果审核和返工意见。

#### Scenario: Insufficient Context

- Given 用户只给出模糊需求
- When `Codex` 准备进入工件阶段
- Then `Codex` 必须先补齐目标、范围、限制和验收条件

### Requirement: Antigravity Implementation

`Antigravity` 必须只按 `tasks.md` 和交接提示词执行实现，不得擅自扩写需求或跳过自检。

#### Scenario: Worker Execution

- Given `Codex` 已生成 `proposal`、`design`、`tasks`
- When 用户手动调用 `Antigravity`
- Then `Antigravity` 必须按工件执行
- And 完成后更新 `tasks.md`
- And 填写 `self-review.md`
- And 回传给 `Codex` 审核提示词
```

### 文件：`openspec/schemas/<project-schema-name>/schema.yaml`

```yaml
name: <project-schema-name>
version: 1
description: 适用于 <project-name> 的 Codex 规划审核 + Antigravity 执行协作流程

artifacts:
  - id: proposal
    generates: proposal.md
    description: 变更目标、范围和风险边界
    template: proposal.md
    instruction: |
      生成本次变更的业务提案。重点说明为什么做、改什么、不改什么，以及影响的代码范围。
    requires: []

  - id: spec
    generates: specs/$DOMAIN/spec.md
    description: 行为变更说明
    template: spec.md
    instruction: |
      使用 requirement 和 scenario 描述本次变更的行为要求，覆盖正常、异常和边界场景。
    requires:
      - proposal

  - id: design
    generates: design.md
    description: 技术设计方案
    template: design.md
    instruction: |
      说明实现方案、涉及模块、关键状态流与接口流，并明确禁止修改范围。
    requires:
      - proposal
      - spec

  - id: tasks
    generates: tasks.md
    description: 可执行的实现清单
    template: tasks.md
    instruction: |
      将设计拆成 Antigravity 可顺序执行的任务，每项任务都要能落实到具体模块或验证动作。
    requires:
      - design

  - id: worker-brief
    generates: worker-brief.md
    description: 给 Antigravity 的执行说明
    template: worker-brief.md
    instruction: |
      生成给 worker 的执行说明，强调边界、禁止事项、输出格式和阻塞处理方式。
    requires:
      - tasks

  - id: self-review
    generates: self-review.md
    description: 给 Antigravity 的实现后自检记录
    template: self-review.md
    instruction: |
      生成给 worker 使用的自检记录模板。要求其在实现完成后，对照 proposal、spec、design 和 tasks 进行一次自检。
    requires:
      - worker-brief

  - id: review
    generates: review.md
    description: 审核记录
    template: review.md
    instruction: |
      生成用于 Codex 最终审核的记录模板，便于沉淀结论、问题与返工要求。
    requires:
      - self-review

  - id: retrospective
    generates: retrospective.md
    description: 失败经验与提示优化沉淀
    template: retrospective.md
    instruction: |
      生成用于 Codex 复盘和后续优化的记录模板。
    requires:
      - review

apply:
  requires:
    - tasks
    - worker-brief
  tracks: tasks.md
```

### 文件：`openspec/schemas/<project-schema-name>/templates/proposal.md`

```md
# Proposal

## Goal

## Why

## In Scope

## Out of Scope

## Risks
```

### 文件：`openspec/schemas/<project-schema-name>/templates/spec.md`

```md
# Spec

## Requirement: <name>

### Scenario: <name>

- Given ...
- When ...
- Then ...
```

### 文件：`openspec/schemas/<project-schema-name>/templates/design.md`

```md
# Design

## Summary

## Modules

## State Flow

## API / Data Flow

## UI / Interaction Flow

## Allowed Changes

## Forbidden Changes

## Validation Plan
```

### 文件：`openspec/schemas/<project-schema-name>/templates/tasks.md`

```md
# Tasks

- [ ] Task 1: ...
- [ ] Task 2: ...
- [ ] Validation: ...
- [ ] Self Review: update self-review.md
```

### 文件：`openspec/schemas/<project-schema-name>/templates/worker-brief.md`

```md
# Worker Brief

## Role

你是本仓库的实现 worker，只负责根据既有 proposal、spec、design 和 tasks 执行代码修改，不负责重构整体架构。

## Must Read

- `proposal.md`
- `design.md`
- `tasks.md`
- `specs/<domain>/spec.md`
- `self-review.md`

## Rules

- 只执行 `tasks.md` 中未勾选的任务
- 严格遵守设计中的范围约束
- 不修改无关文件，不做额外重构
- 如遇到设计缺失、任务冲突或上下文不足，先停止并报告阻塞点
- 修改完成后，必须回写 `tasks.md` 勾选状态
- 修改完成后，必须填写 `self-review.md`
```

### 文件：`openspec/schemas/<project-schema-name>/templates/self-review.md`

```md
# Self Review

## Changed Files

## Task Completion

## Validation

## Scope Check

## Remaining Risks

## Retry Count

## Conclusion
```

### 文件：`openspec/schemas/<project-schema-name>/templates/review.md`

```md
# Review

## Findings

## Pass / Return

## Rework Instructions
```

### 文件：`openspec/schemas/<project-schema-name>/templates/retrospective.md`

```md
# Retrospective

## Failure Summary

## Prompt Gaps

## Task Split Gaps

## Validation Gaps

## Next Improvements
```
