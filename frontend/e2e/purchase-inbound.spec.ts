import { expect, test, type Page } from "@playwright/test";

const baseURL = process.env.STAGE_UI_BASE_URL || "http://localhost:5173";
const username = process.env.STAGE_UI_USERNAME;
const password = process.env.STAGE_UI_PASSWORD;
const tenantCode = process.env.STAGE_UI_TENANT_CODE || "tenant_demo_a";

/**
 * 用途：使用环境变量中的正式演示账号建立浏览器会话。
 * 入参：Playwright 页面；出参：登录后的页面。
 * 流程：不猜测凭据，登录后由真实路由守卫加载租户权限。
 */
async function login(page: Page): Promise<void> {
  test.skip(!username || !password, "需要 STAGE_UI_USERNAME 与 STAGE_UI_PASSWORD 执行采购入库回归");
  await page.goto(`${baseURL}/login`);
  await page.evaluate(() => {
    localStorage.clear();
    sessionStorage.clear();
  });
  await page.goto(`${baseURL}/login`);
  await page.locator("#loginTenant, select[name='tenantCode']").first().selectOption(tenantCode);
  await page.locator("#loginUsername, input[name='username']").first().fill(username!);
  await page.locator("#loginPassword, input[name='password']").first().fill(password!);
  await page.locator("form.login-form button[type='submit'], button.login-submit-btn").first().click();
  await page.waitForURL((url) => !url.pathname.startsWith("/login"));
}

function apiResponse<T>(data: T) {
  return { code: 0, message: "OK", data };
}

function pageResponse<T>(records: T[], total = records.length) {
  return apiResponse({ records, total, page: 1, size: 10, totalPages: Math.max(1, Math.ceil(total / 10)) });
}

test.describe("采购到货、质量和上架事实边界", () => {
  test("收货确认由服务端分配独立 receiptId，不把订单 ID当作收货事实", async ({ page }) => {
    let confirmPayload: any = null;
    await page.route("**/api/purchase-orders*", async (route) => {
      await route.fulfill({
        json: pageResponse([{
          id: "po-from-api",
          poNo: "PO-REAL-CONTEXT",
          supplierName: "真实供应方",
          supplierCode: "SUP-REAL",
          expectedArrivalDate: "2026-09-08",
          warehouseName: "主仓",
          status: "Approved",
          lines: [{
            id: "po-line-from-api",
            productId: "product-from-api",
            sku: "RM-REAL",
            productName: "真实原料",
            uom: "PCS",
            orderedQty: "3.000000",
            arrivedQty: "0.000000",
            rejectedQty: "0.000000",
            receivedQty: "0.000000",
            inspectedQty: "0.000000",
            qualifiedQty: "0.000000",
            unqualifiedQty: "0.000000",
            releaseDecidedQty: "0.000000",
            scrapDecidedQty: "0.000000",
            returnDecidedQty: "0.000000",
            releaseExecutedQty: "0.000000",
            scrapExecutedQty: "0.000000",
            returnExecutedQty: "0.000000",
            putawayQty: "0.000000",
            pendingQty: "3.000000",
            targetWarehouseId: "warehouse-from-api",
          }],
        }]),
      });
    });
    await page.route("**/api/locations**", async (route) => {
      await route.fulfill({ json: pageResponse([{ id: "qh-from-api", warehouseId: "warehouse-from-api", code: "QH-REAL", name: "真实隔离位", type: "QualityHold", status: "ACTIVE" }]) });
    });
    await page.route("**/api/purchase-receipts/confirm", async (route) => {
      confirmPayload = JSON.parse(route.request().postData() || "{}");
      await route.fulfill({
        json: apiResponse({
          id: "receipt-from-server",
          receiptNo: "PR-REAL",
          purchaseOrderId: "po-from-api",
          lines: [{ id: "receipt-line-from-server", purchaseOrderLineId: "po-line-from-api", productId: "product-from-api" }],
        }),
      });
    });

    await login(page);
    await page.goto(`${baseURL}/purchasing/receipts`);
    await page.getByRole("button", { name: "验收接收" }).click();
    const submit = page.getByRole("button", { name: "确认接收进质量隔离位" });
    await expect(page.getByRole("status")).toContainText("收货事实 ID由服务端分配");
    await page.locator("select.form-input").selectOption("qh-from-api");
    await page.locator("input.qty-input").nth(0).fill("3");
    await page.locator("input.qty-input").nth(1).fill("1");
    await page.getByPlaceholder("必填拒收原因").fill("包装破损，收货前拒收");
    await expect(submit).toBeEnabled();
    await submit.click();
    await expect.poll(() => confirmPayload).toMatchObject({
      purchaseOrderId: "po-from-api",
      lines: [{ purchaseOrderLineId: "po-line-from-api", arrivedQty: "3", rejectedQty: "1", receivedQty: "2" }],
    });
    expect(confirmPayload.receiptId).toBeUndefined();
  });

  test("上架目标只查询来源暂存位同仓的启用 Storage 库位", async ({ page }) => {
    const locationRequests: string[] = [];
    await page.route("**/api/putaway-tasks**", async (route) => {
      await route.fulfill({
        json: pageResponse([{
          id: "putaway-from-api",
          taskNo: "PUT-REAL",
          purchaseReceiptId: "receipt-from-api",
          purchaseReceiptLineId: "receipt-line-from-api",
          productId: "product-from-api",
          fromLocationId: "receiving-from-api",
          toLocationId: null,
          putawayQty: "2.000000",
          status: "Pending",
        }]),
      });
    });
    await page.route("**/api/locations**", async (route) => {
      const url = new URL(route.request().url());
      if (url.pathname.endsWith("/receiving-from-api")) {
        await route.fulfill({ json: apiResponse({ id: "receiving-from-api", warehouseId: "warehouse-real", code: "RS-REAL", name: "真实收货暂存位", type: "ReceivingStaging", status: "ACTIVE" }) });
        return;
      }
      locationRequests.push(url.toString());
      await route.fulfill({
        json: pageResponse([
          { id: "storage-same-warehouse", warehouseId: "warehouse-real", code: "ST-REAL", name: "同仓存储位", type: "Storage", status: "ACTIVE" },
          { id: "storage-other-warehouse", warehouseId: "warehouse-other", code: "ST-OTHER", name: "跨仓存储位", type: "Storage", status: "ACTIVE" },
        ]),
      });
    });

    await login(page);
    await page.goto(`${baseURL}/purchasing/putaway`);
    await page.getByRole("button", { name: "确认上架" }).click();
    await expect(page.getByRole("heading", { name: "确认执行货物上架" })).toBeVisible();
    await expect(page.locator("select.form-select").last().locator("option")).toHaveCount(2);
    await expect(page.locator("select.form-select").last().getByRole("option", { name: /同仓存储位/ })).toHaveCount(1);
    await expect(page.locator("select.form-select").last().getByRole("option", { name: /跨仓存储位/ })).toHaveCount(0);
    expect(locationRequests.some((request) => request.includes("warehouseId=warehouse-real") && request.includes("type=Storage"))).toBeTruthy();
  });
});
