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
async function login(page: Page, loginUsername = username, loginPassword = password): Promise<void> {
  test.skip(!loginUsername || !loginPassword, "需要 STAGE_UI_USERNAME 与 STAGE_UI_PASSWORD 才能执行真实环境详情回归");
  // 清理上一角色的浏览器会话，确保同一用例切换角色时重新走后端登录与权限加载。
  await page.goto(`${baseURL}/login`);
  await page.evaluate(() => {
    localStorage.clear();
    sessionStorage.clear();
  });
  await page.goto(`${baseURL}/login`);
  await page.locator("#loginTenant, select[name='tenantCode']").first().selectOption(tenantCode);
  await page.locator("#loginUsername, input[name='username']").first().fill(loginUsername);
  await page.locator("#loginPassword, input[name='password']").first().fill(loginPassword);
  await page.locator("form.login-form button[type='submit'], button.login-submit-btn").first().click();
  await page.waitForURL((url) => !url.pathname.startsWith("/login"));
}

/**
 * 用途：验证一个真实详情直达地址可在刷新、后退和前进后保持详情宿主。
 * 入参：页面对象、列表地址、详情地址和详情宿主 test id；出参：无。
 * 流程：从列表进入详情，刷新后检查宿主，再后退回列表并前进回详情，覆盖深链与浏览器历史恢复。
 */
async function expectDetailSurvivesReload(page: Page, listPath: string, path: string, testId: string): Promise<void> {
  // 先建立列表→详情历史链，确保后退断言验证真实浏览器导航而不是仅验证同页刷新。
  await page.goto(`${baseURL}${listPath}`);
  await page.goto(`${baseURL}${path}`);
  await expect(page.getByTestId(testId)).toBeVisible();
  await page.reload();
  await expect(page.getByTestId(testId)).toBeVisible();
  await expect(page).toHaveURL(new RegExp(`${path.replace(/[.*+?^${}()|[\]\\]/g, "\\$&")}(?:\\?.*)?$`));
  await page.goBack();
  await expect(page).toHaveURL(new RegExp(`${listPath.replace(/[.*+?^${}()|[\]\\]/g, "\\$&")}(?:\\?.*)?$`));
  await page.goForward();
  await expect(page.getByTestId(testId)).toBeVisible();
}

test("采购、销售和调拨详情可直达并在刷新后保留真实详情宿主", async ({ page }) => {
  const purchaseId = process.env.STAGE_UI_PURCHASE_ORDER_ID;
  const salesId = process.env.STAGE_UI_SALES_ORDER_ID;
  const transferId = process.env.STAGE_UI_TRANSFER_ID;
  const purchaseUsername = process.env.STAGE_UI_PURCHASE_USERNAME || username;
  const purchasePassword = process.env.STAGE_UI_PURCHASE_PASSWORD || password;
  const salesUsername = process.env.STAGE_UI_SALES_USERNAME || username;
  const salesPassword = process.env.STAGE_UI_SALES_PASSWORD || password;
  const transferUsername = process.env.STAGE_UI_TRANSFER_USERNAME || username;
  const transferPassword = process.env.STAGE_UI_TRANSFER_PASSWORD || password;
  test.skip(
    !purchaseId || !salesId || !transferId || !purchaseUsername || !purchasePassword || !salesUsername || !salesPassword || !transferUsername || !transferPassword,
    "需要详情 ID 及各详情对应的真实登录账号；密码仅从环境变量读取",
  );

  await login(page, purchaseUsername, purchasePassword);
  await expectDetailSurvivesReload(page, "/purchasing/orders", `/purchasing/orders/${purchaseId}`, "purchase-order-detail-page");
  await login(page, salesUsername, salesPassword);
  await expectDetailSurvivesReload(page, "/sales/orders", `/sales/orders/${salesId}`, "sales-order-detail-page");
  await login(page, transferUsername, transferPassword);
  await expectDetailSurvivesReload(page, "/inventory/transfers", `/inventory/transfers/${transferId}`, "transfer-detail-page");
});

test("详情 API 的 404 与 403 显示明确错误态", async ({ page }) => {
  await login(page);
  await page.route("**/api/purchase-orders/not-found-route-test", async (route) => {
    await route.fulfill({ status: 404, contentType: "application/json", body: JSON.stringify({ code: 404, message: "资源不存在" }) });
  });
  await page.goto(`${baseURL}/purchasing/orders/not-found-route-test`);
  await expect(page.getByText("采购订单资源不存在或已被删除（404）。")).toBeVisible();

  await page.route("**/api/sales-orders/forbidden-route-test", async (route) => {
    await route.fulfill({ status: 403, contentType: "application/json", body: JSON.stringify({ code: 403, message: "无权限" }) });
  });
  await page.goto(`${baseURL}/sales/orders/forbidden-route-test`);
  await expect(page.getByText("您没有查看该销售订单的权限（403）。")).toBeVisible();
});
