import type { AllowedAction } from "../types/common";

/**
 * 判定指定动作是否被允许执行（统一版本，修复 F09）。
 *
 * 入参：
 *   - allowedActions: 后端返回的可执行动作列表，来源于各聚合 DTO 的 allowedActions 字段
 *   - action: 需要判定的动作名称，如 'submit'、'approve'、'confirm' 等
 *
 * 出参：
 *   - true: 允许执行该动作
 *   - false: 禁止执行该动作
 *
 * 核心规则（对齐后端 AllowedActionVo 契约）：
 *   1. allowedActions 为 undefined/null —— 后端未返回动作能力，按安全默认值返回 false
 *   2. allowedActions 为空数组 [] —— 后端明确声明无任何可执行动作（如 Completed 状态），返回 false
 *   3. 动作存在于列表中且 enabled=true —— 返回 true
 *   4. 动作存在于列表中且 enabled=false —— 返回 false
 *   5. 动作不在列表中 —— 后端未声明该动作能力，安全起见返回 false
 */
export function isActionAllowed(
  allowedActions: AllowedAction[] | undefined | null,
  action: string,
): boolean {
  // 修改用途：动作能力缺失时 fail-closed，禁止旧接口绕过后端状态机。
  if (allowedActions === undefined || allowedActions === null) {
    return false;
  }
  // 规则 2：空数组意味着当前状态无任何可执行动作
  if (allowedActions.length === 0) {
    return false;
  }
  // 规则 3/4/5：在列表中查找，未命中视为不允许
  const match = allowedActions.find((a) => a.action === action);
  return match?.enabled === true;
}

/**
 * 获取指定动作被禁用的原因文案。
 *
 * 入参：
 *   - allowedActions: 后端返回的可执行动作列表
 *   - action: 需要查询禁用原因的动作名称
 *
 * 出参：
 *   - 禁用原因字符串（当动作被禁用且后端提供了 reason 时）
 *   - undefined（当动作被允许且无需展示原因时）
 */
export function getActionDisabledReason(
  allowedActions: AllowedAction[] | undefined | null,
  action: string,
): string | undefined {
  // 修改用途：能力缺失与动作未命中均须给出明确的安全禁用说明。
  if (!allowedActions || allowedActions.length === 0) {
    return "后端未返回该动作能力，暂不可执行";
  }
  const match = allowedActions.find((a) => a.action === action);
  if (!match) {
    return "后端未返回该动作能力，暂不可执行";
  }
  return match.enabled ? undefined : (match.reason || "后端未返回该动作能力，暂不可执行");
}
