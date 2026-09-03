import { request, type ApiResponse } from "../utils/request";

/**
 * 租户信息模型
 */
export interface TenantInfo {
  id: string;
  tenantCode: string;
  tenantName: string;
  status: "ACTIVE" | "DISABLED" | string;
  createdBy?: string;
  createdAt?: string;
  updatedBy?: string;
  updatedAt?: string;
}

/**
 * 更新租户信息入参
 */
export interface UpdateTenantRequest {
  tenantName: string;
  status?: string;
}

/**
 * 用户关联角色简要信息
 */
export interface UserRoleRelation {
  id?: string;
  roleId?: string;
  roleCode: string;
  roleName: string;
}

/**
 * 用户列表与详情模型
 */
export interface UserItem {
  id: string;
  tenantId?: string;
  userNo: string;
  username: string;
  realName: string;
  email?: string;
  phone?: string;
  status: "ACTIVE" | "DISABLED" | "LOCKED" | string;
  roles?: string[] | UserRoleRelation[];
  roleIds?: string[];
  createdBy?: string;
  createdAt?: string;
  updatedBy?: string;
  updatedAt?: string;
}

/**
 * 分页通用响应结构
 */
export interface PageResult<T> {
  list?: T[];
  records?: T[];
  total: number;
  page?: number;
  pageSize?: number;
  current?: number;
  size?: number;
}

/**
 * 用户列表查询参数
 */
export interface UserQueryParams {
  page?: number;
  size?: number;
  userNo?: string;
  username?: string;
  realName?: string;
  status?: string;
  roleId?: string;
}

/**
 * 创建用户入参
 */
export interface CreateUserRequest {
  userNo?: string;
  username: string;
  password?: string;
  realName: string;
  email?: string;
  phone?: string;
  status?: string;
  roleIds?: string[];
}

/**
 * 更新用户入参
 */
export interface UpdateUserRequest {
  userNo?: string;
  realName: string;
  email?: string;
  phone?: string;
  status?: string;
}

/**
 * 角色实体模型
 */
export interface RoleItem {
  id: string;
  tenantId?: string;
  roleCode: string;
  roleName: string;
  description?: string;
  status: "ACTIVE" | "DISABLED" | string;
  isSystem?: boolean;
  createdBy?: string;
  createdAt?: string;
  updatedBy?: string;
  updatedAt?: string;
}

/**
 * 角色列表查询参数
 */
export interface RoleQueryParams {
  status?: string;
  keyword?: string;
}

/**
 * 创建角色入参
 */
export interface CreateRoleRequest {
  roleCode: string;
  roleName: string;
  description?: string;
  status?: string;
}

/**
 * 更新角色入参
 */
export interface UpdateRoleRequest {
  roleName: string;
  description?: string;
  status?: string;
}

/**
 * 权限点模型
 */
export interface PermissionItem {
  id: string;
  permissionCode: string;
  permissionName: string;
  module: string;
  description?: string;
  createdAt?: string;
  updatedAt?: string;
}

/**
 * 菜单项模型
 */
export interface MenuItem {
  id: string;
  parentId?: string | null;
  menuCode: string;
  menuName: string;
  routePath: string;
  componentPath?: string;
  icon?: string;
  sortOrder?: number;
  permissionCode?: string;
  visible?: boolean;
  status: "ACTIVE" | "DISABLED" | string;
  children?: MenuItem[];
  createdAt?: string;
  updatedAt?: string;
}

/**
 * 创建菜单入参
 */
export interface CreateMenuRequest {
  parentId?: string | null;
  menuCode: string;
  menuName: string;
  routePath: string;
  componentPath?: string;
  icon?: string;
  sortOrder?: number;
  permissionCode?: string;
  visible?: boolean;
}

/**
 * 更新菜单入参
 */
export interface UpdateMenuRequest {
  parentId?: string | null;
  menuCode: string;
  menuName: string;
  routePath?: string;
  componentPath?: string;
  icon?: string;
  sortOrder?: number;
  permissionCode?: string;
  visible?: boolean;
}

/* =========================================================================
 * 1. 租户管理 (Tenant Admin APIs)
 * ========================================================================= */

/**
 * 获取当前登录租户的基础信息与配置
 * 请求路径：GET /api/auth/admin/tenants/current
 */
export function getCurrentTenant(): Promise<ApiResponse<TenantInfo>> {
  return request<TenantInfo>({
    url: "/api/auth/admin/tenants/current",
    method: "GET",
  });
}

/**
 * 更新当前租户名称与基本设置
 * 请求路径：PUT /api/auth/admin/tenants/current
 * @param data 租户更新参数
 */
export function updateCurrentTenant(data: UpdateTenantRequest): Promise<ApiResponse<TenantInfo>> {
  return request<TenantInfo>({
    url: "/api/auth/admin/tenants/current",
    method: "PUT",
    data,
  });
}

/* =========================================================================
 * 2. 用户管理 (User Admin APIs)
 * ========================================================================= */

/**
 * 分页多条件查询租户用户列表
 * 请求路径：GET /api/auth/admin/users
 * @param params 筛选与分页参数
 */
export function getUsers(params?: UserQueryParams): Promise<ApiResponse<PageResult<UserItem> | UserItem[]>> {
  return request<PageResult<UserItem> | UserItem[]>({
    url: "/api/auth/admin/users",
    method: "GET",
    params,
  });
}

/**
 * 获取单个用户详情（含关联角色 ID 清单）
 * 请求路径：GET /api/auth/admin/users/{id}
 * @param id 用户 UUID
 */
export function getUserDetail(id: string): Promise<ApiResponse<UserItem>> {
  return request<UserItem>({
    url: `/api/auth/admin/users/${id}`,
    method: "GET",
  });
}

/**
 * 新建租户操作用户
 * 请求路径：POST /api/auth/admin/users
 * @param data 新建用户参数
 */
export function createUser(data: CreateUserRequest): Promise<ApiResponse<UserItem>> {
  return request<UserItem>({
    url: "/api/auth/admin/users",
    method: "POST",
    data,
  });
}

/**
 * 修改用户基本信息（姓名、邮箱、手机号等）
 * 请求路径：PUT /api/auth/admin/users/{id}
 * @param id 用户 UUID
 * @param data 修改内容
 */
export function updateUser(id: string, data: UpdateUserRequest): Promise<ApiResponse<UserItem>> {
  return request<UserItem>({
    url: `/api/auth/admin/users/${id}`,
    method: "PUT",
    data,
  });
}

/**
 * 修改用户账号启用/禁用状态
 * 请求路径：PUT /api/auth/admin/users/{id}/status
 * @param id 用户 UUID
 * @param status ACTIVE | DISABLED
 */
export function updateUserStatus(id: string, status: "ACTIVE" | "DISABLED" | string): Promise<ApiResponse<void>> {
  return request<void>({
    url: `/api/auth/admin/users/${id}/status`,
    method: "PUT",
    data: { status },
  });
}

/**
 * 重置指定用户的登录密码
 * 请求路径：POST /api/auth/admin/users/{id}/reset-password
 * @param id 用户 UUID
 * @param newPassword 新密码
 */
export function resetUserPassword(id: string, newPassword: string): Promise<ApiResponse<void>> {
  return request<void>({
    url: `/api/auth/admin/users/${id}/reset-password`,
    method: "POST",
    data: { newPassword },
  });
}

/**
 * 为指定用户重新分配所属角色列表
 * 请求路径：PUT /api/auth/admin/users/{id}/roles
 * @param id 用户 UUID
 * @param roleIds 角色 ID 列表
 */
export function assignUserRoles(id: string, roleIds: string[]): Promise<ApiResponse<void>> {
  return request<void>({
    url: `/api/auth/admin/users/${id}/roles`,
    method: "PUT",
    data: { roleIds },
  });
}

/**
 * 逻辑删除指定用户
 * 请求路径：DELETE /api/auth/admin/users/{id}
 * @param id 用户 UUID
 */
export function deleteUser(id: string): Promise<ApiResponse<void>> {
  return request<void>({
    url: `/api/auth/admin/users/${id}`,
    method: "DELETE",
  });
}

/* =========================================================================
 * 3. 角色管理 (Role Admin APIs)
 * ========================================================================= */

/**
 * 查询当前租户的所有角色列表
 * 请求路径：GET /api/auth/admin/roles
 * @param params 过滤参数
 */
export function getRoles(params?: RoleQueryParams): Promise<ApiResponse<RoleItem[]>> {
  return request<RoleItem[]>({
    url: "/api/auth/admin/roles",
    method: "GET",
    params,
  });
}

/**
 * 获取单个角色详情
 * 请求路径：GET /api/auth/admin/roles/{id}
 * @param id 角色 UUID
 */
export function getRoleDetail(id: string): Promise<ApiResponse<RoleItem>> {
  return request<RoleItem>({
    url: `/api/auth/admin/roles/${id}`,
    method: "GET",
  });
}

/**
 * 新建业务角色
 * 请求路径：POST /api/auth/admin/roles
 * @param data 角色参数
 */
export function createRole(data: CreateRoleRequest): Promise<ApiResponse<RoleItem>> {
  return request<RoleItem>({
    url: "/api/auth/admin/roles",
    method: "POST",
    data,
  });
}

/**
 * 修改角色名称与描述信息
 * 请求路径：PUT /api/auth/admin/roles/{id}
 * @param id 角色 UUID
 * @param data 角色更新数据
 */
export function updateRole(id: string, data: UpdateRoleRequest): Promise<ApiResponse<RoleItem>> {
  return request<RoleItem>({
    url: `/api/auth/admin/roles/${id}`,
    method: "PUT",
    data,
  });
}

/**
 * 修改角色启用/停用状态
 * 请求路径：PUT /api/auth/admin/roles/{id}/status
 * @param id 角色 UUID
 * @param status ACTIVE | DISABLED
 */
export function updateRoleStatus(id: string, status: "ACTIVE" | "DISABLED" | string): Promise<ApiResponse<void>> {
  return request<void>({
    url: `/api/auth/admin/roles/${id}/status`,
    method: "PUT",
    data: { status },
  });
}

/**
 * 删除指定业务角色（若有用户绑定通常返回 409 冲突）
 * 请求路径：DELETE /api/auth/admin/roles/{id}
 * @param id 角色 UUID
 */
export function deleteRole(id: string): Promise<ApiResponse<void>> {
  return request<void>({
    url: `/api/auth/admin/roles/${id}`,
    method: "DELETE",
  });
}

/**
 * 获取指定角色已授权的权限点 ID 或编码列表
 * 请求路径：GET /api/auth/admin/roles/{roleId}/permissions
 * @param roleId 角色 UUID
 */
export function getRolePermissions(roleId: string): Promise<ApiResponse<string[] | PermissionItem[]>> {
  return request<string[] | PermissionItem[]>({
    url: `/api/auth/admin/roles/${roleId}/permissions`,
    method: "GET",
  });
}

/**
 * 为角色分配功能权限点集合
 * 请求路径：PUT /api/auth/admin/roles/{roleId}/permissions
 * @param roleId 角色 UUID
 * @param permissionIds 权限点 ID/Code 集合
 */
export function assignRolePermissions(roleId: string, permissionIds: string[]): Promise<ApiResponse<void>> {
  return request<void>({
    url: `/api/auth/admin/roles/${roleId}/permissions`,
    method: "PUT",
    data: { permissionIds },
  });
}

/**
 * 获取指定角色已分配的菜单 ID 列表
 * 请求路径：GET /api/auth/admin/roles/{roleId}/menus
 * @param roleId 角色 UUID
 */
export function getRoleMenus(roleId: string): Promise<ApiResponse<string[] | MenuItem[]>> {
  return request<string[] | MenuItem[]>({
    url: `/api/auth/admin/roles/${roleId}/menus`,
    method: "GET",
  });
}

/**
 * 为角色分配动态菜单树节点
 * 请求路径：PUT /api/auth/admin/roles/{roleId}/menus
 * @param roleId 角色 UUID
 * @param menuIds 选中的菜单 ID 列表
 */
export function assignRoleMenus(roleId: string, menuIds: string[]): Promise<ApiResponse<void>> {
  return request<void>({
    url: `/api/auth/admin/roles/${roleId}/menus`,
    method: "PUT",
    data: { menuIds },
  });
}

/* =========================================================================
 * 4. 权限目录 (Permission Admin APIs)
 * ========================================================================= */

/**
 * 获取全量系统功能权限点目录（只读能力目录）
 * 请求路径：GET /api/auth/admin/permissions
 */
export function getPermissions(): Promise<ApiResponse<PermissionItem[]>> {
  return request<PermissionItem[]>({
    url: "/api/auth/admin/permissions",
    method: "GET",
  });
}

/* =========================================================================
 * 5. 菜单管理 (Menu Admin APIs)
 * ========================================================================= */

/**
 * 获取全量动态菜单树或平铺菜单列表
 * 请求路径：GET /api/auth/admin/menus
 */
export function getMenus(): Promise<ApiResponse<MenuItem[]>> {
  return request<MenuItem[]>({
    url: "/api/auth/admin/menus",
    method: "GET",
  });
}

/**
 * 获取单个菜单节点详情
 * 请求路径：GET /api/auth/admin/menus/{id}
 * @param id 菜单 UUID
 */
export function getMenuDetail(id: string): Promise<ApiResponse<MenuItem>> {
  return request<MenuItem>({
    url: `/api/auth/admin/menus/${id}`,
    method: "GET",
  });
}

/**
 * 新建菜单节点（根菜单或子菜单）
 * 请求路径：POST /api/auth/admin/menus
 * @param data 新建参数
 */
export function createMenu(data: CreateMenuRequest): Promise<ApiResponse<MenuItem>> {
  return request<MenuItem>({
    url: "/api/auth/admin/menus",
    method: "POST",
    data,
  });
}

/**
 * 更新菜单节点信息
 * 请求路径：PUT /api/auth/admin/menus/{id}
 * @param id 菜单 UUID
 * @param data 更新参数
 */
export function updateMenu(id: string, data: UpdateMenuRequest): Promise<ApiResponse<MenuItem>> {
  return request<MenuItem>({
    url: `/api/auth/admin/menus/${id}`,
    method: "PUT",
    data,
  });
}

/**
 * 删除指定菜单节点
 * 请求路径：DELETE /api/auth/admin/menus/{id}
 * @param id 菜单 UUID
 */
export function deleteMenu(id: string): Promise<ApiResponse<void>> {
  return request<void>({
    url: `/api/auth/admin/menus/${id}`,
    method: "DELETE",
  });
}

/**
 * 快速变更菜单节点启用状态
 * 请求路径：PUT /api/auth/admin/menus/{id}/status
 * @param id 菜单 UUID
 * @param status ACTIVE | DISABLED
 */
export function updateMenuStatus(id: string, status: "ACTIVE" | "DISABLED" | string): Promise<ApiResponse<MenuItem>> {
  return request<MenuItem>({
    url: `/api/auth/admin/menus/${id}/status`,
    method: "PUT",
    data: { status },
  });
}
