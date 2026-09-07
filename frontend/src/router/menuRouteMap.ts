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
