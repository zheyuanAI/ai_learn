/**
 * 旧菜单路由与当前正式业务路由的兼容映射。
 * 用途：在数据库迁移完成前兼容缓存中的旧菜单，同时为路由守卫和侧边栏提供同一套规范化结果。
 */
export const LEGACY_MENU_ROUTE_ALIASES: Readonly<Record<string, string>> = Object.freeze({
  "/master-data/products": "/master-data?tab=products",
  "/master-data/warehouses": "/master-data?tab=warehouses",
  "/master-data/inventory": "/inventory/balances",
  "/purchase/orders": "/purchasing/orders",
  "/purchase/inbound": "/purchasing/receipts",
  "/purchase/putaway": "/purchasing/putaway",
  "/sales/outbound": "/sales/picks",
  "/mes/execution": "/mes/dispatch",
  "/gis/map": "/gis/site-maps",
  "/ai/trace": "/traceability",
});

export type RoutePermissionRequirement = string | string[] | undefined;

/**
 * 用途：从权限配置中提取稳定的权限编码，兼容演示数据附带的中文说明。
 * 入参：原始权限字符串；出参：module:resource:action 格式的权限编码。
 */
function normalizePermissionCode(permission: string): string {
  return String(permission).trim().split(/[\s（(]/, 1)[0];
}

/**
 * 用途：判断已授予权限是否覆盖页面或按钮所需权限。
 * 入参：requiredCode 所需权限、grantedCode 已授予权限；出参：是否覆盖。
 * 规则：同码直接通过；同一资源的 :manage 权限覆盖该资源下的具体动作。
 */
function permissionCovers(requiredCode: string, grantedCode: string): boolean {
  const required = normalizePermissionCode(requiredCode);
  const granted = normalizePermissionCode(grantedCode);
  const resourcePrefix = required.split(":").slice(0, 2).join(":");
  return granted === required
    || (granted.startsWith(`${resourcePrefix}:`) && granted.endsWith(":manage"));
}

const MENU_ROUTE_PERMISSION_OVERRIDES: Readonly<Record<string, RoutePermissionRequirement>> =
  Object.freeze({
    "/master-data?tab=products": "inv:product:view",
    "/master-data?tab=warehouses": "inv:warehouse:view",
  });

/**
 * 用途：把菜单返回的路由地址转换为当前前端正式地址。
 * 入参：后端菜单的 routePath/path，允许为空或缺少开头斜杠。
 * 出参：可直接交给 RouterLink 或 router.push 使用的绝对路由地址。
 * 流程：先清理空白和斜杠，再查找旧地址映射；未命中时保留原业务地址。
 */
export function normalizeMenuRoutePath(routePath: string | null | undefined): string {
  const trimmedPath = String(routePath || "").trim();
  if (!trimmedPath) return "/";

  const absolutePath = trimmedPath.startsWith("/") ? trimmedPath : `/${trimmedPath}`;
  return LEGACY_MENU_ROUTE_ALIASES[absolutePath] || absolutePath;
}

/**
 * 用途：取得菜单路由用于高亮匹配的 pathname，忽略 query/hash。
 * 入参：后端菜单的 routePath/path。
 * 出参：不包含 query/hash 的绝对 pathname。
 * 流程：先执行统一路由映射，再截取 query/hash 之前的路径部分。
 */
export function getMenuRoutePathname(routePath: string | null | undefined): string {
  return normalizeMenuRoutePath(routePath).split(/[?#]/, 1)[0] || "/";
}

/**
 * 用途：判断动态菜单是否与当前路由精确匹配，并把 query 作为菜单身份的一部分。
 * 入参：当前 pathname、当前 query、后端菜单路由。
 * 出参：路径一致且菜单声明的全部 query 参数一致时返回 true。
 * 流程：先统一旧菜单地址，再比较 pathname；带 query 的同页签菜单继续逐项比较参数，避免多个菜单同时高亮。
 */
export function isMenuRouteActive(
  currentPath: string,
  currentQuery: Record<string, unknown>,
  menuRoutePath: string | null | undefined,
): boolean {
  const normalizedPath = normalizeMenuRoutePath(menuRoutePath).split("#", 1)[0];
  const queryIndex = normalizedPath.indexOf("?");
  const targetPath = queryIndex >= 0 ? normalizedPath.slice(0, queryIndex) : normalizedPath;
  if (currentPath !== targetPath) {
    return false;
  }

  if (queryIndex < 0) {
    return true;
  }

  const targetQuery = new URLSearchParams(normalizedPath.slice(queryIndex + 1));
  for (const [key, expectedValue] of targetQuery.entries()) {
    const currentValue = currentQuery[key];
    if (Array.isArray(currentValue)) {
      if (!currentValue.map(String).includes(expectedValue)) {
        return false;
      }
    } else if (String(currentValue ?? "") !== expectedValue) {
      return false;
    }
  }
  return true;
}

/**
 * 用途：取得同一路由不同业务页签的菜单级权限要求。
 * 入参：后端菜单路由；出参：需要覆盖页面通用权限的菜单权限，未配置时返回 undefined。
 * 流程：先规范化历史地址，再按完整 path + query 查找，避免商品与仓库页签共用路由后互相越权展示。
 */
export function getMenuRoutePermissionOverride(
  menuRoutePath: string | null | undefined,
): RoutePermissionRequirement {
  return MENU_ROUTE_PERMISSION_OVERRIDES[normalizeMenuRoutePath(menuRoutePath)];
}

/**
 * 用途：按路由守卫的“任一权限满足”语义判断页面访问能力。
 * 入参：路由要求的一个或多个权限、当前用户权限集合；出参：是否允许展示对应菜单。
 * 流程：无权限要求直接放行；其余权限按规范化编码匹配，并兼容同资源 :manage 覆盖及中文说明。
 */
export function hasAnyRoutePermission(
  requiredPermission: RoutePermissionRequirement,
  userPermissions: readonly string[],
): boolean {
  if (!requiredPermission) {
    return true;
  }

  const required = Array.isArray(requiredPermission) ? requiredPermission : [requiredPermission];
  return required.some((permission) =>
    userPermissions.some((grantedPermission) => permissionCovers(permission, grantedPermission)),
  );
}

/**
 * 用途：递归过滤用户无权访问的菜单叶节点，并移除过滤后为空的分组。
 * 入参：菜单树和页面访问判断函数；出参：只含可访问页面的新菜单树。
 * 流程：叶节点按权限判断，分组递归保留可访问子项，不修改后端返回的原始菜单对象。
 */
export function filterMenuTreeByAccess<T extends { path: string; children?: T[] }>(
  menus: readonly T[],
  canAccess: (path: string) => boolean,
): T[] {
  return menus.flatMap((menu) => {
    if (menu.children && menu.children.length > 0) {
      const children = filterMenuTreeByAccess(menu.children, canAccess);
      return children.length > 0 ? [{ ...menu, children }] : [];
    }
    return canAccess(menu.path) ? [{ ...menu }] : [];
  });
}
