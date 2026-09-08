import { describe, expect, it } from "vitest";
import { isRetryableApiCode, readRequestId } from "../request";

describe("readRequestId", () => {
  it("优先读取 snake_case 响应字段", () => {
    expect(readRequestId({ request_id: "snake", requestId: "camel" }, { "X-Request-Id": "header" })).toBe("snake");
  });

  it("兼容 camelCase 与大小写不敏感响应头", () => {
    expect(readRequestId({ requestId: "camel" }, { "x-request-id": "header" })).toBe("camel");
    expect(readRequestId({}, { "X-ReQuEsT-Id": "header" })).toBe("header");
  });

  it("无请求号时返回空字符串", () => {
    expect(readRequestId(undefined, undefined)).toBe("");
  });

  it("业务信封 502/503 标记为可同键重试", () => {
    expect(isRetryableApiCode(502)).toBe(true);
    expect(isRetryableApiCode(503)).toBe(true);
    expect(isRetryableApiCode(500)).toBe(false);
  });
});
