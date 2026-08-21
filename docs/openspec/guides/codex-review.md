# Codex 审核指令模板

以下内容用于让 Codex 对 Antigravity 完成的实现进行复核。

## 推荐审核步骤

1. 阅读 `docs/openspec/changes/<change-name>/proposal.md`
2. 阅读 `docs/openspec/changes/<change-name>/spec.md`
3. 阅读 `docs/openspec/changes/<change-name>/design.md`
4. 阅读 `docs/openspec/changes/<change-name>/tasks.md`
5. 阅读 `docs/openspec/changes/<change-name>/self-review.md`
6. 检查 `git diff`
7. 核对 Antigravity 汇报的测试命令与结果
8. 若本次变更影响现有事实文档，核对对应 `docs/specs/`、`prototype/`、`docs/` 是否已同步回写
9. 判断实现是否满足 `proposal`、`spec`、`design` 和 `tasks`
10. 如果 Antigravity 已达到 2 次自修上限，给出明确返工意见并决定是否补充 `retrospective.md`

## 推荐提示词

```text
请对当前 change 做审查，重点看行为正确性、边界条件、设计一致性、事实文档回写和测试覆盖是否充分。

检查范围：
- docs/openspec/changes/<change-name>/proposal.md
- docs/openspec/changes/<change-name>/spec.md
- docs/openspec/changes/<change-name>/design.md
- docs/openspec/changes/<change-name>/tasks.md
- docs/openspec/changes/<change-name>/self-review.md
- 当前 git diff
- worker 汇报的测试结果
- 如有涉及，还要检查对应 docs/specs/、prototype/、docs/ 的同步更新

输出要求：
1. 先给出 findings，按严重程度排序
2. 每条问题要指出文件路径和原因
3. 如无问题，明确说明未发现阻断问题
4. 最后给出结论：通过 / 退回
5. 如果退回，附上明确返工指令
6. 需要区分“Antigravity 自检结论”和“Codex 最终审核结论”，不能混写
7. 如果 Antigravity 已达到 2 次自修上限，必须给出下一步修改意见，而不是只写笼统结论
```
