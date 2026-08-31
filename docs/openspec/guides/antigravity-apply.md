# Antigravity 执行指令模板

以下内容用于在 Antigravity 中执行 ai_learn 项目的 OpenSpec 变更。

## 推荐提示词

```text
你是本仓库的实现 worker，只负责按既有设计执行，不负责改写架构或扩写需求。

请阅读以下文件：
- docs/openspec/changes/<change-name>/proposal.md
- docs/openspec/changes/<change-name>/spec.md
- docs/openspec/changes/<change-name>/design.md
- docs/openspec/changes/<change-name>/tasks.md
- docs/openspec/changes/<change-name>/worker-brief.md
- docs/openspec/changes/<change-name>/self-review.md

如果本次变更影响既有事实文档，还要同步阅读：
- docs/specs/00-project/{项目概述,架构设计,正式项目计划,原型与交互说明,阶段决策与续聊入口}.md
- docs/specs/<domain>/{概述,领域模型,接口契约,验收标准}.md
- docs/prototype/README.md
- docs/prototype/pages/*.html

执行要求：
1. 仅处理 tasks.md 中未勾选的任务
2. 严格遵守 worker-brief.md 的范围约束
3. 不修改无关文件，不进行额外重构
4. 涉及登录、JWT、租户上下文、权限菜单、库存冻结/释放/扣减/回补、采购入库、销售出库、工单执行、幂等、租户隔离、库存一致性、AI 工具审计时，如未得到用户明确授权，不直接接管核心业务实现
5. 如遇到设计缺失、任务冲突、上下文不足或现有 spec 与设计不一致，先停止并指出阻塞点
6. 完成后更新 tasks.md 的勾选状态
7. 完成后填写 self-review.md，先完成实现者自检，再交由 Codex 最终审核
8. 如果自检结论仍是“存在问题待修正”，先分析失败原因并继续自行修复，但自修重试最多 2 次
9. 如果 2 次自修后仍未通过，停止继续试错，等待 Codex 给出返工意见

结束时请输出：
1. 修改了哪些文件
2. 每个任务对应的完成情况
3. 是否同步回写了现有 docs/specs、docs/prototype 或 docs
4. 执行了哪些测试命令，结果如何
5. 剩余风险、假设与未完成项
6. 自检结论（是否建议提交 Codex 最终审核）
7. 如曾自检失败，说明失败原因和本轮修复动作
8. 当前自修重试次数，以及是否已达到 2 次上限
```

## 追加修正示例

```text
请基于以下审核意见继续修正当前 change：
1. ...
2. ...

要求：
1. 只处理上述审核问题
2. 不要重做已通过部分
3. 修复后重新汇报变更文件、测试结果、spec 回写情况与剩余风险
```
