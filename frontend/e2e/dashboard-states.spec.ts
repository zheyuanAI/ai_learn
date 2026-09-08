import { expect, test, type Page, type Route } from "@playwright/test";

const baseURL = process.env.STAGE_UI_BASE_URL || "http://localhost:5173";
const username = process.env.STAGE_UI_USERNAME;
const password = process.env.STAGE_UI_PASSWORD;
const tenantCode = process.env.STAGE_UI_TENANT_CODE || "tenant_demo_a";

/**
 * 用途：使用环境变量中的正式账号进入真实看板路由。
 * 入参：Playwright 页面对象；出参：无。
 * 流程：缺少凭据时跳过，避免在测试源码中保存账号密码。
 */
async function login(page: Page): Promise<void> {
  test.skip(!username || !password, "需要 STAGE_UI_USERNAME 与 STAGE_UI_PASSWORD 才能验证看板三态");
  await page.goto(`${baseURL}/login`);
  await page.locator("#loginTenant, select[name='tenantCode']").first().selectOption(tenantCode);
  await page.locator("#loginUsername, input[name='username']").first().fill(username!);
  await page.locator("#loginPassword, input[name='password']").first().fill(password!);
  await page.locator("form.login-form button[type='submit'], button.login-submit-btn").first().click();
  await page.waitForURL((url) => !url.pathname.startsWith("/login"));
}

/** 返回符合统一响应包装的看板投影。 */
async function fulfillSummary(
  route: Route,
  type: string,
  options: { stale?: boolean; staleSince?: string; metrics?: Record<string, number | null> } = {},
): Promise<void> {
  const data: Record<string, unknown> = {
    summary_type: type,
    metrics: options.metrics ?? { total: 7 },
    time_range: "today",
    source_summary: `${type} facts`,
    generated_at: "2026-09-08T00:00:00Z",
    source_updated_at: "2026-09-07T23:59:00Z",
    request_id: `dashboard-${type}`,
  };
  if (options.stale !== undefined) {
    data.stale = options.stale;
  }
  if (options.staleSince) {
    data.stale_since = options.staleSince;
  }
  await route.fulfill({
    status: 200,
    contentType: "application/json",
    body: JSON.stringify({ code: 200, message: "操作成功", data, request_id: `dashboard-${type}` }),
  });
}

test("看板严格区分实时、陈旧和不可用，缺失事实不渲染为零", async ({ page }, testInfo) => {
  await login(page);

  let deviceCalls = 0;
  await page.route("**/api/dashboard/**", async (route) => {
    const type = new URL(route.request().url()).pathname.split("/").pop() || "unknown";
    if (type === "quality") {
      await route.fulfill({
        status: 503,
        contentType: "application/json",
        body: JSON.stringify({
          code: 503,
          message: "GIS_QUERY_002 质量事实暂不可用",
          request_id: "quality-unavailable",
        }),
      });
      return;
    }
    if (type === "alarms") {
      await fulfillSummary(route, "ALARM", {
        stale: true,
        staleSince: "2026-09-07T23:50:00Z",
        metrics: { active_alarm_count: 7 },
      });
      return;
    }
    if (type === "device") {
      deviceCalls += 1;
      if (deviceCalls === 2) {
        await route.fulfill({
          status: 200,
          contentType: "application/json",
          body: JSON.stringify({ code: 200, message: "操作成功", request_id: "device-missing-data" }),
        });
        return;
      }
      if (deviceCalls > 2) {
        await fulfillSummary(route, "DEVICE", { metrics: { device_count: null } });
        return;
      }
    }
    await fulfillSummary(route, type.toUpperCase(), { stale: false });
  });

  await page.goto(`${baseURL}/dashboard`);

  const inventory = page.locator(".summary-card-container").filter({ hasText: "库存资产监控" });
  await expect(inventory.getByText("● 实时")).toBeVisible();

  const alarm = page.locator(".summary-card-container").filter({ hasText: "异常告警监控" });
  await expect(alarm.getByText("⚠️ 已过期")).toBeVisible();
  await expect(alarm.getByText("7", { exact: true })).toBeVisible();
  await expect(alarm).toContainText("2026-09-07T23:50:00Z");

  const quality = page.locator(".summary-card-container").filter({ hasText: "质量合格监控" });
  await expect(quality.getByText("● 不可用")).toBeVisible();
  await expect(quality).toContainText("GIS_QUERY_002 质量事实暂不可用");
  await expect(quality.locator(".metric-box")).toHaveCount(0);

  const device = page.locator(".summary-card-container").filter({ hasText: "设备健康度监控" });
  await device.locator(".btn-card-refresh").click();
  await expect(device.getByText("● 不可用")).toBeVisible();
  await expect(device).toContainText("device-missing-data");
  await expect(device.locator(".metric-box")).toHaveCount(0);
  await expect(device.getByText("0", { exact: true })).toHaveCount(0);

  await device.locator(".btn-card-refresh").click();
  await expect(device.getByText("● 不可用")).toBeVisible();
  await expect(device).toContainText("dashboard-DEVICE");
  await expect(device.locator(".metric-box")).toHaveCount(0);
  await expect(device.getByText("0", { exact: true })).toHaveCount(0);

  await page.screenshot({ path: testInfo.outputPath("dashboard-three-states.png"), fullPage: true });
});
