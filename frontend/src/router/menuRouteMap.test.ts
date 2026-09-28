import { describe, expect, it } from "vitest";
import {
  filterMenuTreeByAccess,
  getMenuRoutePermissionOverride,
  hasAnyRoutePermission,
  isMenuRouteActive,
} from "./menuRouteMap";

describe("isMenuRouteActive", () => {
  it("同一路径的主数据菜单只高亮 query 匹配项", () => {
    const currentQuery = { tab: "products" };

    expect(isMenuRouteActive("/master-data", currentQuery, "/master-data/products")).toBe(true);
    expect(isMenuRouteActive("/master-data", currentQuery, "/master-data/warehouses")).toBe(false);
  });

  it("普通菜单仍按精确路径匹配", () => {
    expect(isMenuRouteActive("/dashboard", {}, "/dashboard")).toBe(true);
    expect(isMenuRouteActive("/inventory/balances", {}, "/dashboard")).toBe(false);
  });
});

describe("菜单权限过滤", () => {
  it("复用路由守卫的任一权限满足语义", () => {
    expect(hasAnyRoutePermission(["mes:dispatch:manage", "mes:workorder:view"], ["mes:workorder:view"]))
      .toBe(true);
    expect(hasAnyRoutePermission("mes:execution:manage", ["mes:workorder:view"]))
      .toBe(false);
  });

  it("兼容权限说明文本，并让同资源 manage 覆盖具体页面权限", () => {
    expect(hasAnyRoutePermission("mes:bom:view", ["mes:bom:manage (BOM 管理)"])).toBe(true);
    expect(hasAnyRoutePermission("pur:order:view", ["pur:order:create (创建采购订单)"])).toBe(false);
    expect(hasAnyRoutePermission("pur:order:view", ["pur:order:manage (采购订单管理)"])).toBe(true);
  });

  it("主数据同页签菜单使用各自精确权限", () => {
    expect(getMenuRoutePermissionOverride("/master-data/products")).toBe("inv:product:view");
    expect(getMenuRoutePermissionOverride("/master-data/warehouses")).toBe("inv:warehouse:view");
  });

  it("移除无权叶节点和过滤后为空的分组", () => {
    const menus = [
      {
        path: "/mes",
        children: [
          { path: "/mes/work-orders" },
          { path: "/mes/executions" },
        ],
      },
      { path: "/system/users" },
    ];

    expect(filterMenuTreeByAccess(menus, (path) => path === "/mes/work-orders")).toEqual([
      { path: "/mes", children: [{ path: "/mes/work-orders" }] },
    ]);
  });
});
