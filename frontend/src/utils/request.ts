import axios, { type AxiosInstance, type AxiosRequestConfig, type AxiosResponse, type InternalAxiosRequestConfig } from "axios";

/**
 * 统一后端返回数据信封结构 (ApiResponse)
 */
export interface ApiResponse<T = any> {
  code: number;
  message: string;
  data: T;
  success?: boolean;
  requestId?: string;
  timestamp?: string;
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
 * 请求拦截器：
 * 1. 自动注入 Authorization: Bearer <token>
 * 2. 自动生成并携带 X-Request-Id
 * 3. 携带当前生效租户标识 X-Tenant-Id
 */
service.interceptors.request.use(
  (config: InternalAxiosRequestConfig) => {
    // 注入链路追踪 Request ID
    config.headers.set("X-Request-Id", generateRequestId());

    // 注入持久化的 Token
    const token = localStorage.getItem(TOKEN_KEY);
    if (token) {
      config.headers.set("Authorization", `Bearer ${token}`);
    }

    // 注入当前选择的租户标识
    const tenant = localStorage.getItem(TENANT_KEY);
    if (tenant) {
      config.headers.set("X-Tenant-Id", tenant);
    }

    return config;
  },
  (error) => {
    console.error("[Request Interceptor Error]:", error);
    return Promise.reject(error);
  }
);

/**
 * 响应拦截器：
 * 1. 统一解析后端 ApiResponse 结构
 * 2. 针对 401（未授权/单会话顶替）、403（无权访问）、409（并发冲突）、500（服务异常）统一处理与提示
 * 3. 401 时自动清除 Token 并重定向至登录页
 */
service.interceptors.response.use(
  (response: AxiosResponse) => {
    const res = response.data;

    // 若返回数据非标准 ApiResponse 对象（如 Blob、纯文本等），直接透传
    if (!res || typeof res !== "object" || !("code" in res)) {
      return response;
    }

    const apiRes = res as ApiResponse;

    // 业务状态码 200 或 0 表示成功
    if (apiRes.code === 200 || apiRes.code === 0) {
      return response;
    }

    // 业务状态码 401：会话已过期或已被新登录顶替
    if (apiRes.code === 401) {
      handleUnauthorized(apiRes.message || "登录会话已失效，请重新登录");
      return Promise.reject(new Error(apiRes.message || "Unauthorized"));
    }

    // 其他业务异常
    const errorMsg = apiRes.message || `业务处理失败 (Code: ${apiRes.code})`;
    console.warn(`[API Business Error] [${apiRes.code}]:`, errorMsg);
    return Promise.reject(new Error(errorMsg));
  },
  (error) => {
    // 处理 HTTP 网络错误响应
    let message = "网络请求失败，请检查网络或后端服务状态";
    const status = error.response?.status;
    const responseData = error.response?.data;

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
        break;
      case 404:
        message = message || "请求的接口资源不存在 (404)";
        break;
      case 409:
        message = message || "操作冲突，数据状态已变更，请刷新重试 (409)";
        break;
      case 500:
      case 502:
      case 503:
        message = message || "后端服务异常，请稍后重试 (500)";
        break;
      default:
        if (error.code === "ECONNABORTED" || error.message?.includes("timeout")) {
          message = "请求连接超时，请检查后端网关是否已启动";
        } else if (error.message?.includes("Network Error")) {
          message = "无法连接至后端网关服务 (http://localhost:20001)";
        }
        break;
    }

    console.error(`[HTTP Error ${status || "UNKNOWN"}]:`, message, error);
    return Promise.reject(new Error(message));
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
 * 通用请求包装函数
 */
export async function request<T = any>(config: AxiosRequestConfig): Promise<ApiResponse<T>> {
  const response = await service.request<ApiResponse<T>>(config);
  return response.data;
}

export { service as axiosInstance };
export default request;
