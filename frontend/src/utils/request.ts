import axios, { type AxiosInstance, type AxiosRequestConfig, type AxiosResponse, type InternalAxiosRequestConfig } from "axios";

/**
 * 统一后端返回数据信封结构 (ApiResponse)
 */
export interface ApiResponse<T = any> {
  code: number;
  message: string;
  data: T;
  success?: boolean;
  /** 后端 snake_case 请求号兼容字段。 */
  request_id?: string;
  requestId?: string;
  timestamp?: string;
}

/**
 * 用途：从后端响应体或响应头读取链路请求号。
 * 入参：body 为统一响应体，headers 为 Axios 响应头或普通 Header 对象。
 * 出参：按 request_id、requestId、大小写不敏感 X-Request-Id 优先级返回请求号，缺失时返回空字符串。
 */
export function readRequestId(body: unknown, headers?: unknown): string {
  if (body && typeof body === "object") {
    const responseBody = body as Record<string, unknown>;
    if (typeof responseBody.request_id === "string" && responseBody.request_id) return responseBody.request_id;
    if (typeof responseBody.requestId === "string" && responseBody.requestId) return responseBody.requestId;
  }
  if (headers && typeof headers === "object") {
    const headerEntries = typeof (headers as { toJSON?: () => Record<string, unknown> }).toJSON === "function"
      ? Object.entries((headers as { toJSON: () => Record<string, unknown> }).toJSON())
      : Object.entries(headers as Record<string, unknown>);
    const requestIdHeader = headerEntries.find(([key]) => key.toLowerCase() === "x-request-id")?.[1];
    return Array.isArray(requestIdHeader) ? String(requestIdHeader[0] || "") : String(requestIdHeader || "");
  }
  return "";
}

/** 用途：判定业务响应码是否代表可同键重试的网关暂不可用结果。 */
export function isRetryableApiCode(code: unknown): boolean {
  return code === 502 || code === 503;
}

/**
 * 增强型 API 错误类（修复 F09）。
 *
 * 用途：替代普通 Error，在错误传播链路中完整保留后端返回的诊断信息，
 *       供页面展示可理解的错误原因、可复制的请求号和重试引导。
 *
 * 携带字段：
 *   - httpStatus: HTTP 状态码（如 403/404/409/422/500/503），网络错误时为 0
 *   - code: 后端业务状态码（来自 ApiResponse.code）
 *   - requestId: 链路追踪请求号（来自 ApiResponse.requestId 或请求头 X-Request-Id）
 *   - retryable: 是否可用同一幂等键安全重试（503/超时/网络错误时为 true）
 */
export class ApiError extends Error {
  /** HTTP 状态码，网络错误或超时时为 0 */
  httpStatus: number;
  /** 后端业务状态码 */
  code: number;
  /** 链路追踪请求号，便于用户复制反馈 */
  requestId: string;
  /** 是否为可重试的不确定性错误（结果未知时保留幂等键） */
  retryable: boolean;

  constructor(options: {
    message: string;
    httpStatus?: number;
    code?: number;
    requestId?: string;
    retryable?: boolean;
  }) {
    super(options.message);
    this.name = "ApiError";
    this.httpStatus = options.httpStatus ?? 0;
    this.code = options.code ?? 0;
    this.requestId = options.requestId ?? "";
    this.retryable = options.retryable ?? false;
  }
}

/**
 * 本地存储 Token 与租户的常量 Key
 */
export const TOKEN_KEY = "ai_learn_token";
export const TENANT_KEY = "ai_learn_tenant";

/**
 * 生成唯一的请求 ID (X-Request-Id)
 * 用于链路追踪与后端日志审计
 */
export function generateRequestId(): string {
  if (typeof crypto !== "undefined" && typeof crypto.randomUUID === "function") {
    return `req-${crypto.randomUUID()}`;
  }
  const timestamp = Date.now().toString(36);
  const randomStr = Math.random().toString(36).substring(2, 9);
  return `req-${timestamp}-${randomStr}`;
}

/**
 * 创建 Axios 实例
 * 默认 baseURL 优先读取环境变量 VITE_API_BASE_URL，未配置时指向 Gateway 网关端口 20001
 */
const service: AxiosInstance = axios.create({
  baseURL: import.meta.env.VITE_API_BASE_URL || "http://localhost:20001",
  timeout: 15000,
  headers: {
    "Content-Type": "application/json;charset=utf-8",
  },
});

/**
 * 生成唯一的幂等键 (Idempotency-Key)
 * 防止网络抖动或用户重复点击导致同一业务命令重复执行
 */
export function generateIdempotencyKey(): string {
  if (typeof crypto !== "undefined" && typeof crypto.randomUUID === "function") {
    return `idemp-${crypto.randomUUID()}`;
  }
  const timestamp = Date.now().toString(36);
  const randomStr = Math.random().toString(36).substring(2, 10);
  return `idemp-${timestamp}-${randomStr}`;
}

/**
 * 请求拦截器：
 * 1. 自动注入 Authorization: Bearer <token>
 * 2. 自动生成并携带 X-Request-Id (若未显式指定)
 * 3. 携带当前生效租户标识 X-Tenant-Id
 * 4. 为 POST / PUT / PATCH / DELETE 写操作注入 Idempotency-Key (若未显式指定)
 */
service.interceptors.request.use(
  (config: InternalAxiosRequestConfig) => {
    // 注入链路追踪 Request ID
    if (!config.headers.has("X-Request-Id")) {
      config.headers.set("X-Request-Id", generateRequestId());
    }

    // 注入持久化的 Token
    const token = localStorage.getItem(TOKEN_KEY);
    if (token) {
      config.headers.set("Authorization", `Bearer ${token}`);
    }

    // 注入当前选择的租户标识
    const tenant = localStorage.getItem(TENANT_KEY);
    if (tenant) {
      // 该 Header 只是当前前端上下文的提示，不能作为越权切换租户的依据；后端必须以 JWT 会话绑定的可信租户为准。
      config.headers.set("X-Tenant-Id", tenant);
    }

    // 为修改类请求自动携带幂等键，防止重复提交。
    // 注意：推荐业务视图使用 useCommand 组合式函数管理幂等键生命周期，
    // 通过 headers 显式传入 Idempotency-Key 以支持重试复用。
    // 此处仅为兼容旧视图的兜底行为——每次请求自动生成新键。
    const method = (config.method || "get").toUpperCase();
    if (["POST", "PUT", "PATCH", "DELETE"].includes(method) && !config.headers.has("Idempotency-Key")) {
      config.headers.set("Idempotency-Key", generateIdempotencyKey());
    }

    return config;
  },
  (error) => {
    console.error("[Request Interceptor Error]:", error);
    return Promise.reject(error);
  }
);

/**
 * 响应拦截器（修复 F09 错误封装缺失上下文）：
 * 1. 统一解析后端 ApiResponse 结构，成功时透传
 * 2. 业务异常和 HTTP 异常均封装为 ApiError，保留 httpStatus/code/requestId/retryable
 * 3. 针对 401 自动清除 Token 并重定向，403/404/409/422/503/超时分别处理
 * 4. 页面可通过 catch(e) { if (e instanceof ApiError) ... } 展示结构化错误信息
 */
service.interceptors.response.use(
  (response: AxiosResponse) => {
    const res = response.data;

    // 若返回数据非标准 ApiResponse 对象（如 Blob、纯文本等），直接透传
    if (!res || typeof res !== "object" || !("code" in res)) {
      return response;
    }

    const apiRes = res as ApiResponse;
    // 提取请求头中的 X-Request-Id 作为链路追踪标识
    // 修改用途：业务错误优先复用统一读取逻辑，兼容两种响应体字段与响应头。
    const requestId = readRequestId(apiRes, response.headers) || readRequestId(undefined, response.config.headers);

    // 业务状态码 200 或 0 表示成功
    if (apiRes.code === 200 || apiRes.code === 0) {
      return response;
    }

    // 业务状态码 401：会话已过期或已被新登录顶替
    if (apiRes.code === 401) {
      handleUnauthorized(apiRes.message || "登录会话已失效，请重新登录");
      return Promise.reject(new ApiError({
        message: apiRes.message || "Unauthorized",
        httpStatus: 401,
        code: apiRes.code,
        requestId,
        retryable: false,
      }));
    }

    // 其他业务异常：封装为 ApiError 保留完整上下文
    const errorMsg = apiRes.message || `业务处理失败 (Code: ${apiRes.code})`;
    console.warn(`[API Business Error] [${apiRes.code}]:`, errorMsg, `requestId=${requestId}`);
    return Promise.reject(new ApiError({
      message: errorMsg,
      httpStatus: response.status,
      code: apiRes.code,
      requestId,
      retryable: isRetryableApiCode(apiRes.code),
    }));
  },
  (error) => {
    // 处理 HTTP 网络错误响应
    const status = error.response?.status || 0;
    const responseData = error.response?.data;
    // 修改用途：HTTP 错误统一从响应体和响应头提取请求号。
    const requestId = readRequestId(responseData, error.response?.headers) || readRequestId(undefined, error.config?.headers);
    const businessCode = responseData && typeof responseData === "object" ? (responseData.code || 0) : 0;

    let message = "网络请求失败，请检查网络或后端服务状态";
    let retryable = false;

    if (responseData && typeof responseData === "object" && responseData.message) {
      message = responseData.message;
    }

    switch (status) {
      case 401:
        message = message || "未授权或当前会话已在其他终端登录 (401)";
        handleUnauthorized(message);
        break;
      case 403:
        message = message || "抱歉，您没有权限执行此操作 (403)";
        // 403 不可重试，权限不足需要联系管理员
        break;
      case 404:
        message = message || "请求的接口资源不存在 (404)";
        break;
      case 409:
        // 409 冲突：操作可能已执行，提示用户刷新查看结果
        message = message || "操作冲突：该命令可能已执行成功，请刷新页面查看最新状态 (409)";
        break;
      case 422:
        message = message || "请求参数校验失败，请检查输入 (422)";
        break;
      case 500:
        message = message || "后端服务异常，请稍后重试 (500)";
        break;
      case 502:
      case 503:
        // 502/503 可重试：服务暂不可用，结果可能不确定
        message = message || "后端服务暂不可用，请稍后重试 (503)";
        retryable = true;
        break;
      default:
        if (error.code === "ECONNABORTED" || error.message?.includes("timeout")) {
          // 超时：结果不确定，保留幂等键允许重试
          message = "请求连接超时，操作结果不确定，请勿重复提交，可点击重试 (Timeout)";
          retryable = true;
        } else if (error.message?.includes("Network Error")) {
          // 网络错误：结果不确定
          message = "无法连接至后端网关服务，操作结果不确定 (Network Error)";
          retryable = true;
        }
        break;
    }

    console.error(`[HTTP Error ${status || "UNKNOWN"}]:`, message, `requestId=${requestId}`, error);
    return Promise.reject(new ApiError({
      message,
      httpStatus: status,
      code: businessCode,
      requestId,
      retryable,
    }));
  }
);

/**
 * 401 未授权与会话顶替清理处理
 * 入参为提示信息，核心流程清除本地凭据并派发全局登出事件或跳转
 */
function handleUnauthorized(message: string) {
  localStorage.removeItem(TOKEN_KEY);
  window.dispatchEvent(new CustomEvent("ai-learn:unauthorized", { detail: { message } }));

  // 如果当前不在登录页，保存重定向地址并跳转
  const currentPath = window.location.pathname;
  if (currentPath !== "/login") {
    const redirect = encodeURIComponent(currentPath + window.location.search);
    window.location.href = `/login?redirect=${redirect}&reason=401`;
  }
}

/**
 * 通用请求包装函数。
 * 入参为 Axios 请求配置，出参为后端统一 ApiResponse；认证失败、业务错误和网络错误已由响应拦截器统一转为 rejected Promise，调用方不应重复解析 HTTP 外壳。
 */
export async function request<T = any>(config: AxiosRequestConfig): Promise<ApiResponse<T>> {
  const response = await service.request<ApiResponse<T>>(config);
  return response.data;
}

export { service as axiosInstance };
export default request;
