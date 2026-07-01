# Collaboration Spec

## Goal

定义当前项目中 `Codex + Antigravity + OpenSpec` 的稳定协作方式，并确保其与现有 `specs/`、原型和高风险业务边界一致。

## Requirements

### Requirement: Explicit OpenSpec Trigger

只有当用户明确以 `OpenSpec` 或 `openspec` 作为任务开头时，系统才进入 OpenSpec 工件化流程；未出现此前缀时，默认按普通任务处理。

#### Scenario: Explicit OpenSpec Request

- Given 用户输入以 `OpenSpec` 或 `openspec` 开头
- When `Codex` 接手任务
- Then `Codex` 必须先读取 `openspec/README.md`
- And 后续需求澄清、工件生成、实现交接、执行器自检、审核、返工和归档都按 OpenSpec 流程执行

#### Scenario: Ordinary Request

- Given 用户输入未带 `OpenSpec` 或 `openspec` 前缀
- When `Codex` 接手任务
- Then `Codex` 必须按普通任务流程处理
- And 不得仅因任务复杂就自动进入 OpenSpec 流程

### Requirement: Existing Spec Synchronization

OpenSpec 工件不能替代当前仓库的长期事实源；若变更影响既有业务规则、接口契约、验收条件或页面结构，必须同步回写现有文档。

#### Scenario: Domain Behavior Change

- Given 某次 change 修改了领域状态机、错误码、接口字段、权限点或验收条件
- When `Codex` 生成 tasks 或审核实现
- Then `Codex` 必须明确列出需要同步回写的 `specs/<domain>/{domain,api,acceptance}.md`
- And `Antigravity` 或执行者必须在完成实现时同步更新这些事实文档

#### Scenario: Prototype or UI Change

- Given 某次 change 修改了页面字段、状态标签、操作按钮或流程跳转
- When `Codex` 生成 design 或 tasks
- Then `Codex` 必须明确列出需要同步检查的 `prototype/README.md`、`prototype/pages/*.html` 和相关前端页面

### Requirement: Core Business Ownership Boundary

OpenSpec 流程不得绕过当前项目的核心业务边界；用户仍是核心业务主开发，AI 默认不接管高风险实现。

#### Scenario: Core Business Implementation Without Explicit Delegation

- Given change 涉及登录、JWT、租户上下文、权限菜单、库存冻结/释放/扣减/回补、采购入库、销售出库、工单执行、幂等、租户隔离、库存一致性或 AI 工具审计
- When 用户未明确授权 AI 直接实现
- Then `Codex` 必须优先输出澄清、工件、设计约束、测试样板或 review 意见
- And 不得默认直接接管整块核心业务实现

### Requirement: Worker Self Review and Rework Limit

执行器在提交 `Codex` 审核前，必须先完成自检；若连续自修 2 次仍未通过，必须停止试错并等待返工意见。

#### Scenario: Worker Completes Implementation

- Given `Antigravity` 已按 `tasks.md` 完成实现
- When 准备回传给 `Codex`
- Then `Antigravity` 必须更新 `tasks.md`
- And 必须填写 `self-review.md`
- And 必须汇报验证结果、剩余风险和自修轮次

#### Scenario: Retry Limit Reached

- Given `Antigravity` 的自修轮次已达到 2 次上限
- When 自检仍未通过或 `Codex` 仍判定退回
- Then `Antigravity` 必须停止继续试错
- And `Codex` 必须给出明确返工意见或补充 `retrospective.md`
