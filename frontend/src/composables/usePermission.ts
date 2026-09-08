import { computed } from "vue";
import { useAuthStore } from "../stores/auth";

/**
 * 前端权限辅助组合式函数。
 *
 * 用途：提供基于 authStore.permissions 的权限检查能力，
 *       供页面组件控制按钮显示/禁用、区域渲染等。
 *
 * 核心流程：
 *   1. 读取 authStore.permissions（由登录时 /api/me 返回并缓存的权限码列表）
 *   2. 提供 hasPermission / hasAnyPermission / hasAllPermissions 检查方法
 *   3. 后端仍然是权限校验的最终防线，前端权限检查仅用于 UI 体验优化
 *
 * 出参：
 *   - permissions: 当前用户权限码列表的计算属性
 *   - hasPermission(code): 检查是否拥有指定权限
 *   - hasAnyPermission(...codes): 检查是否拥有任一权限
 *   - hasAllPermissions(...codes): 检查是否拥有全部权限
 */
export function usePermission() {
  const authStore = useAuthStore();

  /**
   * 当前用户的权限码列表（响应式计算属性）
   */
  const permissions = computed(() => authStore.permissions || []);

  /**
   * 检查当前用户是否拥有指定权限码。
   * 入参：permCode — 权限编码，格式为 module:resource:action（如 pur:order:view）
   * 出参：boolean
   */
  function hasPermission(permCode: string): boolean {
    if (!permCode) return true;
    return permissions.value.some(
      (p) => p === permCode || p.startsWith(permCode.split(":").slice(0, 2).join(":") + ":") && p.endsWith(":manage"),
    );
  }

  /**
   * 检查当前用户是否拥有任一权限码。
   * 入参：permCodes — 多个权限编码
   * 出参：boolean — 至少拥有一个时返回 true
   */
  function hasAnyPermission(...permCodes: string[]): boolean {
    return permCodes.some((code) => hasPermission(code));
  }

  /**
   * 检查当前用户是否拥有全部权限码。
   * 入参：permCodes — 多个权限编码
   * 出参：boolean — 全部拥有时返回 true
   */
  function hasAllPermissions(...permCodes: string[]): boolean {
    return permCodes.every((code) => hasPermission(code));
  }

  return {
    permissions,
    hasPermission,
    hasAnyPermission,
    hasAllPermissions,
  };
}
