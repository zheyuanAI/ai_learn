import { describe, expect, it } from "vitest";
import { getActionDisabledReason, isActionAllowed } from "../actionGuard";

describe("actionGuard", () => {
  it("缺少动作表时拒绝写动作", () => {
    expect(isActionAllowed(undefined, "confirm")).toBe(false);
    expect(isActionAllowed(null, "confirm")).toBe(false);
    expect(isActionAllowed([], "confirm")).toBe(false);
    expect(isActionAllowed([{ action: "confirm", enabled: false }], "confirm")).toBe(false);
  });

  it("只有后端明确启用的动作才允许执行", () => {
    expect(isActionAllowed([{ action: "confirm", enabled: true }], "confirm")).toBe(true);
    expect(isActionAllowed([{ action: "submit", enabled: true }], "confirm")).toBe(false);
  });

  it("缺少动作能力时返回统一禁用原因", () => {
    const fallback = "后端未返回该动作能力，暂不可执行";
    expect(getActionDisabledReason(undefined, "confirm")).toBe(fallback);
    expect(getActionDisabledReason([], "confirm")).toBe(fallback);
    expect(getActionDisabledReason([{ action: "confirm", enabled: false }], "confirm")).toBe(fallback);
    expect(getActionDisabledReason([{ action: "confirm", enabled: false, reason: "状态不允许" }], "confirm")).toBe("状态不允许");
    expect(getActionDisabledReason([{ action: "confirm", enabled: true }], "confirm")).toBeUndefined();
  });
});
