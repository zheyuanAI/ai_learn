import { expect, test, type Page } from "@playwright/test";

const baseURL = process.env.STAGE_UI_BASE_URL || "http://localhost:5173";
const username = process.env.STAGE_UI_USERNAME;
const password = process.env.STAGE_UI_PASSWORD;
const tenantCode = process.env.STAGE_UI_TENANT_CODE || "tenant_demo_a";

/**
 * 用途：使用仅来自环境变量的凭据建立真实浏览器会话。
 * 入参：Playwright 页面对象；出参：无，登录成功后停留在非登录页。
 * 流程：缺少账号或密码时跳过，避免将秘密写入源码或猜测认证信息。
 */
async function login(page: Page): Promise<void> {
  test.skip(!username || !password, "需要 STAGE_UI_USERNAME 与 STAGE_UI_PASSWORD 才能执行真实环境路由回归");
  await page.goto(`${baseURL}/login`);
  await page.locator("#loginTenant, select[name='tenantCode']").first().selectOption(tenantCode);
  await page.locator("#loginUsername, input[name='username']").first().fill(username!);
  await page.locator("#loginPassword, input[name='password']").first().fill(password!);
  await page.locator("form.login-form button[type='submit'], button.login-submit-btn").first().click();
  await page.waitForURL((url) => !url.pathname.startsWith("/login"));
}

/**
 * 用途：断言旧菜单地址不会静默回到首页。
 * 入参：页面对象、旧地址及其正式目标地址；出参：无。
 * 流程：导航后只接受正式页面或明确 /forbidden，未知地址必须显示独立 404 页面。
 */
async function expectLegacyRouteResolved(page: Page, legacyPath: string, targetPath: string): Promise<void> {
  await page.goto(`${baseURL}${legacyPath}`);
  await page.waitForLoadState("domcontentloaded");
  await page.waitForURL((url) => url.pathname === targetPath || url.pathname === "/forbidden");
  expect([targetPath, "/forbidden"]).toContain(new URL(page.url()).pathname);
  expect(new URL(page.url()).pathname).not.toBe("/");
  await expect(page.getByTestId("not-found-page")).toHaveCount(0);
}

test("真实旧菜单地址解析为正式页面或明确无权页，未知地址显示 404", async ({ page }) => {
  await login(page);

  const mappings: Array<[string, string]> = [
    ["/master-data/products", "/master-data"],
    ["/master-data/warehouses", "/master-data"],
    ["/master-data/inventory", "/inventory/balances"],
    ["/purchase/orders", "/purchasing/orders"],
    ["/purchase/inbound", "/purchasing/receipts"],
    ["/purchase/putaway", "/purchasing/putaway"],
    ["/sales/outbound", "/sales/picks"],
    ["/mes/execution", "/mes/dispatch"],
    ["/gis/map", "/gis/site-maps"],
  ];
  for (const [legacyPath, targetPath] of mappings) {
    await expectLegacyRouteResolved(page, legacyPath, targetPath);
  }

  await page.goto(`${baseURL}/route-that-does-not-exist`);
  await expect(page.getByTestId("not-found-page")).toBeVisible();
  await expect(page).toHaveURL(/\/route-that-does-not-exist$/);
});

test("无权路由明确显示 forbidden，不会静默回首页", async ({ page }) => {
  await login(page);
  const forbiddenPath = process.env.STAGE_UI_FORBIDDEN_PATH;
  test.skip(!forbiddenPath, "需要 STAGE_UI_FORBIDDEN_PATH 指定当前账号确认无权的真实路由");
  await page.goto(`${baseURL}${forbiddenPath}`);
  await expect(page).toHaveURL(/\/forbidden(?:\?|$)/);
  await expect(page.getByRole("heading", { name: "无权访问" })).toBeVisible();
});
