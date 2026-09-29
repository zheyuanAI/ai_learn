import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { axiosInstance, isRetryableApiCode, readRequestId } from "../request";

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

describe("HTTP 错误提示", () => {
  beforeEach(() => {
    vi.stubGlobal("localStorage", { getItem: () => null });
    vi.spyOn(console, "error").mockImplementation(() => {});
  });
  afterEach(() => {
    vi.restoreAllMocks();
    vi.unstubAllGlobals();
  });

  it.each([
    [400, "请求失败 (HTTP 400)"],
    [403, "抱歉，您没有权限执行此操作 (403)"],
    [409, "操作冲突：该命令可能已执行成功，请刷新页面查看最新状态 (409)"],
    [502, "后端服务暂不可用，请稍后重试 (502)"],
  ])("响应体缺少 message 时按 HTTP %i 返回准确提示", async (status, expectedMessage) => {
    // 用途：通过真实响应拦截器验证状态码兜底文案；入参为模拟 HTTP 状态，出参为 ApiError 断言。
    await expect(axiosInstance.get("/test-error", {
      adapter: async (config) => Promise.reject({
        config,
        response: { status, data: { code: status }, headers: {}, config },
      }),
    })).rejects.toMatchObject({ message: expectedMessage, httpStatus: status });
  });

  it("保留后端明确返回的错误说明", async () => {
    // 用途：确认后端文案优先于本地兜底；入参为模拟 403 响应，出参为 ApiError 断言。
    await expect(axiosInstance.get("/test-server-message", {
      adapter: async (config) => Promise.reject({
        config,
        response: { status: 403, data: { code: 403, message: "租户无权访问" }, headers: {}, config },
      }),
    })).rejects.toMatchObject({ message: "租户无权访问", httpStatus: 403 });
  });

  it("错误日志不输出请求中的认证令牌", async () => {
    // 用途：确认 HTTP 异常的诊断日志不泄露请求头；入参为带令牌的模拟请求，出参为日志断言。
    vi.stubGlobal("localStorage", { getItem: () => "private-token" });
    await expect(axiosInstance.get("/test-private-header", {
      adapter: async (config) => Promise.reject({
        config,
        response: { status: 403, data: { code: 403 }, headers: {}, config },
      }),
    })).rejects.toMatchObject({ httpStatus: 403 });
    expect(JSON.stringify(vi.mocked(console.error).mock.calls)).not.toContain("private-token");
  });
});
