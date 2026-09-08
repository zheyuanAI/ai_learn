import { expect, test, type Page } from "@playwright/test";

const baseURL = process.env.STAGE_UI_BASE_URL || "http://localhost:5173";
const username = process.env.STAGE_UI_USERNAME;
const password = process.env.STAGE_UI_PASSWORD;
const tenantCode = process.env.STAGE_UI_TENANT_CODE || "tenant_demo_a";

/**
 * 用途：使用运行环境提供的演示账号建立真实浏览器会话。
 * 入参：Playwright 页面对象；出参：登录后页面；流程：填写租户、账号和密码并等待离开登录页。
 */
async function login(page: Page): Promise<void> {
  test.skip(!username || !password, "需要 STAGE_UI_USERNAME 与 STAGE_UI_PASSWORD 执行主数据浏览器回归");
  await page.goto(`${baseURL}/login`);
  await page.locator("#loginTenant, select[name='tenantCode']").first().selectOption(tenantCode);
  await page.locator("#loginUsername, input[name='username']").first().fill(username!);
  await page.locator("#loginPassword, input[name='password']").first().fill(password!);
  await page.locator("form.login-form button[type='submit'], button.login-submit-btn").first().click();
  await page.waitForURL((url) => !url.pathname.startsWith("/login"));
}

function pageResponse<T>(records: T[], total = records.length) {
  return {
    code: 0,
    message: "OK",
    data: { records, total, page: 1, size: 10, totalPages: Math.max(1, Math.ceil(total / 10)) },
  };
}

test.describe("主数据真实目录与 tab 状态", () => {
  test("从 query 初始化并回写 tab，目录请求携带服务端分页参数", async ({ page }) => {
    await page.route("**/api/warehouses*", async (route) => {
      const url = new URL(route.request().url());
      expect(url.searchParams.get("page")).toBe("1");
      expect(url.searchParams.get("size")).toBe("10");
      await route.fulfill({ json: pageResponse([{ id: "warehouse-1", code: "WH-REAL-01", name: "真实仓库", type: "STORAGE", status: "ACTIVE" }]) });
    });
    await page.route("**/api/uoms*", async (route) => {
      const url = new URL(route.request().url());
      expect(url.searchParams.get("page")).toBe("1");
      expect(url.searchParams.get("size")).toBe("10");
      await route.fulfill({ json: pageResponse([{ id: "uom-1", code: "PCS", name: "件", status: "ACTIVE" }]) });
    });

    await login(page);
    await page.goto(`${baseURL}/master-data?tab=warehouses`);
    await expect(page).toHaveURL(/\/master-data\?tab=warehouses/);
    await expect(page.getByText("WH-REAL-01", { exact: true })).toBeVisible();

    await page.getByRole("button", { name: /计量单位/ }).click();
    await expect(page).toHaveURL(/\/master-data\?tab=uoms/);
    await expect(page.getByText("PCS", { exact: true })).toBeVisible();
  });

  test("商品编辑器的 UOM 使用活动编码，不把 UUID 当作提交值", async ({ page }) => {
    await page.route("**/api/products*", async (route) => {
      await route.fulfill({ json: pageResponse([{ id: "product-1", sku: "SKU-REAL-01", name: "真实物料", uom: "PCS", category: "原材料", batchMgmt: false, status: "ACTIVE" }]) });
    });
    await page.route("**/api/uoms*", async (route) => {
      await route.fulfill({ json: pageResponse([{ id: "uom-1", code: "PCS", name: "件", status: "ACTIVE" }]) });
    });

    await login(page);
    await page.goto(`${baseURL}/master-data?tab=products`);
    await page.getByRole("button", { name: /新建商品物料|新建商品|新增/ }).first().click();
    const uomSelect = page.locator("select").filter({ has: page.locator("option[value='PCS']") }).first();
    await expect(uomSelect).toBeVisible();
    await expect(uomSelect.locator("option[value='PCS']")).toHaveCount(1);
    await expect(uomSelect.locator("option[value='uom-1']")).toHaveCount(0);
  });
});
