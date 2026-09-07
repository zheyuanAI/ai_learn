import { request, type ApiResponse } from "../utils/request";

/**
 * 用户登录入参模型
 */
export interface LoginRequest {
  /** 企业租户编码，例如 tenant_demo_a */
  tenantCode: string;
  /** 登录用户名，例如 admin.zhang */
  username: string;
  /** 登录密码 */
  password?: string;
}

/**
 * 用户信息模型（对齐后端 UserProfileVo 与 UserInfoVo）
 */
export interface UserInfo {
  userId?: string;
  id?: string | number;
  tenantId?: string;
  tenantCode?: string;
  username: string;
  realName: string;
  email?: string;
  phone?: string;
  roles?: string[];
  /** permissions 是规范字段；perms 仅用于兼容旧 DTO，不代表存在两套权限来源。 */
  permissions?: string[];
  perms?: string[];
  avatar?: string;
  /** 当前 JWT 会话标识 */
  jti?: string;
}

/**
 * 登录成功返回模型
 */
export interface LoginResponse {
  token: string;
  tokenType?: string;
  expiresIn?: number;
  jti?: string;
  user: UserInfo;
}

/**
 * 动态菜单树节点模型（对齐后端 MenuNodeVo）
 */
export interface MenuItem {
  id?: string | number;
  parentId?: string | number | null;
  menuCode?: string;
  menuName?: string;
  routePath?: string;
  componentPath?: string;
  icon?: string;
  sortOrder?: number;
  visible?: boolean;
  status?: "ACTIVE" | "DISABLED" | string;
  children?: MenuItem[];
  // 兼容前端旧原型字段
  name?: string;
  path?: string;
  label?: string;
  detail?: string;
  permissions?: string[];
}

/**
 * 用户登录接口
 * 入参为租户编码、账号与密码，出参为 Token 与用户信息。
 * 租户编码只用于定位登录空间，凭据、会话和权限均由后端校验与签发，前端不在此处自行判权。
 * 请求路径：POST /api/auth/login
 */
export function login(data: LoginRequest): Promise<ApiResponse<LoginResponse>> {
  return request<LoginResponse>({
    url: "/api/auth/login",
    method: "POST",
    data,
  });
}

/**
 * 用户登出接口
 * 清除服务端当前会话 jti。
 * 请求路径：POST /api/auth/logout
 */
export function logout(): Promise<ApiResponse<void>> {
  return request<void>({
    url: "/api/auth/logout",
    method: "POST",
  });
}

/**
 * 获取当前登录用户画像与权限上下文
 * 请求路径：GET /api/me
 */
export function getMe(): Promise<ApiResponse<UserInfo>> {
  return request<UserInfo>({
    url: "/api/me",
    method: "GET",
  });
}

/**
 * 获取当前登录用户可访问的动态菜单树
 * 菜单由后端按当前会话的角色、权限及菜单状态过滤，前端仅负责保存和展示返回结果。
 * 请求路径：GET /api/me/menus
 */
export function getMyMenus(): Promise<ApiResponse<MenuItem[]>> {
  return request<MenuItem[]>({
    url: "/api/me/menus",
    method: "GET",
  });
}
