import { expect, test, type Page } from "@playwright/test";

const baseURL = process.env.STAGE_UI_BASE_URL || "http://localhost:5173";
const username = process.env.STAGE_UI_USERNAME;
const password = process.env.STAGE_UI_PASSWORD;
const tenantCode = process.env.STAGE_UI_TENANT_CODE || "tenant_demo_a";

/**
 * 用途：使用环境变量中的演示账号建立浏览器会话。
 * 入参：Playwright 页面；出参：已通过真实路由守卫的页面会话。
 * 流程：不猜测密码；缺少凭据时跳过本组需要登录的回归。
 */
async function login(page: Page): Promise<void> {
  test.skip(!username || !password, "需要 STAGE_UI_USERNAME 与 STAGE_UI_PASSWORD 执行销售履约回归");
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

const order = {
  id: "sales-order-from-api",
  soNo: "SO-REAL-001",
  customerId: "customer-1",
  customerCode: "CUS-REAL",
  customerName: "真实客户",
  warehouseId: "warehouse-1",
  warehouseName: "成品仓",
  shippingLocationId: "shipping-1",
  shippingLocationCode: "SHP-REAL",
  plannedShipDate: "2026-09-10",
  status: "Approved",
  fulfillmentStatus: "InProgress",
  allowedActions: [
    { action: "directPick", enabled: true },
    { action: "ship", enabled: true },
    { action: "returnPick", enabled: true },
    { action: "manualComplete", enabled: false },
  ],
  lines: [{
    id: "sales-line-from-api",
    soId: "sales-order-from-api",
    lineNo: 1,
    productId: "product-from-api",
    sku: "FG-REAL",
    productName: "真实成品",
    uom: "PCS",
    sourceLocationId: "storage-1",
    sourceLocationCode: "ST-REAL",
    orderedQty: "2.000000",
    reservedQty: "0.000000",
    pickedQty: "0.000000",
    shippedQty: "0.000000",
    unreservedQty: "2.000000",
    unpickedQty: "0.000000",
    shippingStagedQty: "0.000000",
    activeReservedQty: "0.000000",
    unshippedQty: "2.000000",
  }],
};

test.describe("销售订单驱动拣货、分批发货和退回", () => {
  test("拣货队列传递分页关键字并展示订单行事实，不伪造 taskNo", async ({ page }) => {
    const pickQueries: URL[] = [];
    await page.route("**/api/pick-tasks*", async (route) => {
      const url = new URL(route.request().url());
      pickQueries.push(url);
      await route.fulfill({ json: pageResponse([order]) });
    });

    await login(page);
    await page.goto(`${baseURL}/sales/picks`);
    await page.getByPlaceholder("搜索销售订单号、客户名称...").fill("SO-REAL");
    await page.getByRole("button", { name: "查询" }).click();

    await expect(page.getByText("SO-REAL-001")).toBeVisible();
    await expect(page.getByText("行 1")).toBeVisible();
    await expect(page.getByText(/FG-REAL \/ 真实成品/)).toBeVisible();
    await expect(page.getByText("未履约:")).toBeVisible();
    await expect(page.getByText("taskNo")).toHaveCount(0);
    expect(pickQueries.some((url) => url.searchParams.get("keyword") === "SO-REAL"
      && url.searchParams.get("page") === "1"
      && url.searchParams.get("size") === "10")).toBeTruthy();
  });

  test("直接拣货使用真实操作 ID、同仓来源和发货暂存位", async ({ page }) => {
    let pickPayload: any = null;
    const partialOrder = {
      ...order,
      lines: [{ ...order.lines[0], orderedQty: "3.000000", unreservedQty: "3.000000", unshippedQty: "3.000000" }],
    };
    await page.route("**/api/pick-tasks*", async (route) => {
      await route.fulfill({ json: pageResponse([partialOrder]) });
    });
    await page.route("**/api/sales-orders/sales-order-from-api", async (route) => {
      await route.fulfill({ json: apiResponse(partialOrder) });
    });
    await page.route("**/api/locations*", async (route) => {
      const url = new URL(route.request().url());
      const type = url.searchParams.get("type");
      const records = type === "ShippingStaging"
        ? [{ id: "shipping-1", warehouseId: "warehouse-1", code: "SHP-REAL", name: "真实发货暂存位", type: "ShippingStaging", status: "ACTIVE" }]
        : type === "Picking"
          ? [{ id: "picking-1", warehouseId: "warehouse-1", code: "PK-REAL", name: "真实拣货位", type: "Picking", status: "ACTIVE" }]
          : [{ id: "storage-1", warehouseId: "warehouse-1", code: "ST-REAL", name: "真实存储位", type: "Storage", status: "ACTIVE" }];
      await route.fulfill({ json: pageResponse(records) });
    });
    await page.route("**/api/inventory/balances*", async (route) => {
      await route.fulfill({ json: pageResponse([{
        dimension: {
          productId: "product-from-api",
          warehouseId: "warehouse-1",
          locationId: "storage-1",
          lotNo: "",
        },
        onHandQty: "2.000000",
        reservedQty: "0.000000",
        availableQty: "2.000000",
        version: 1,
      }]) });
    });
    await page.route("**/api/pick-tasks/confirm", async (route) => {
      pickPayload = JSON.parse(route.request().postData() || "{}");
      await route.fulfill({ json: apiResponse({ action: "PICK", operationId: "11111111-1111-1111-1111-111111111111", order, inventoryTransactionIds: [], reservationIds: [] }) });
    });

    await login(page);
    await page.goto(`${baseURL}/sales/picks`);
    await page.getByRole("button", { name: "履约详情" }).click();
    await expect(page.getByRole("heading", { name: /销售订单行项数量与进度明细/ })).toBeVisible();
    await page.getByRole("button", { name: "直接拣货 (自动补齐预留)" }).click();

    const sourceSelect = page.locator("select.form-select").filter({ has: page.locator("option[value='storage-1']") }).first();
    await expect(sourceSelect.locator("option[value='picking-1']")).toBeDisabled();
    await expect(page.locator("input.text-cyan")).toHaveValue("3.000000");
    await sourceSelect.selectOption("storage-1");
    await expect(page.locator("input.text-cyan")).toHaveValue("2.000000");
    await page.getByRole("button", { name: "确认拣货" }).click();

    await expect.poll(() => pickPayload).toEqual({
      salesOrderId: "sales-order-from-api",
      lines: [{
        salesOrderLineId: "sales-line-from-api",
        pickedQty: "2.000000",
        sourceLocationId: "storage-1",
        shippingLocationId: "shipping-1",
      }],
    });
  });

  test("发货可按暂存数量分批提交，且不从页面补造操作 ID", async ({ page }) => {
    let shipmentPayload: any = null;
    const stagedOrder = {
      ...order,
      lines: [{ ...order.lines[0], reservedQty: "2.000000", pickedQty: "2.000000", shippingStagedQty: "2.000000", activeReservedQty: "2.000000" }],
    };
    await page.route("**/api/sales-orders/sales-order-from-api", async (route) => {
      await route.fulfill({ json: apiResponse(stagedOrder) });
    });
    await page.route("**/api/locations*", async (route) => {
      await route.fulfill({ json: pageResponse([{ id: "storage-1", warehouseId: "warehouse-1", code: "ST-REAL", name: "真实存储位", type: "Storage", status: "ACTIVE" }, { id: "shipping-1", warehouseId: "warehouse-1", code: "SHP-REAL", name: "真实发货暂存位", type: "ShippingStaging", status: "ACTIVE" }]) });
    });
    await page.route("**/api/sales-shipments/confirm", async (route) => {
      shipmentPayload = JSON.parse(route.request().postData() || "{}");
      await route.fulfill({ json: apiResponse({ action: "SHIP", operationId: "22222222-2222-2222-2222-222222222222", order: stagedOrder, inventoryTransactionIds: [], reservationIds: [] }) });
    });

    await login(page);
    await page.goto(`${baseURL}/sales/orders/sales-order-from-api`);
    await expect(page.getByRole("button", { name: "确认发货 (扣减实物库存)" })).toBeVisible();
    await page.getByRole("button", { name: "确认发货 (扣减实物库存)" }).click();
    await page.locator("input.qty-input").fill("1");
    await page.getByRole("button", { name: "确认发货并扣减实物库存" }).click();

    await expect.poll(() => shipmentPayload).toMatchObject({
      salesOrderId: "sales-order-from-api",
      shipmentLines: [{ salesOrderLineId: "sales-line-from-api", productId: "product-from-api", shipQty: "1" }],
    });
  });

  test("退回一部分暂存后仍可完成两次分批发货", async ({ page }) => {
    let currentOrder: any = {
      ...order,
      lines: [{ ...order.lines[0], orderedQty: "3.000000", reservedQty: "3.000000", pickedQty: "3.000000", shippingStagedQty: "3.000000", activeReservedQty: "3.000000", unshippedQty: "3.000000" }],
    };
    const returns: any[] = [];
    const shipments: any[] = [];
    await page.route("**/api/sales-orders/sales-order-from-api", async (route) => {
      await route.fulfill({ json: apiResponse(currentOrder) });
    });
    await page.route("**/api/locations*", async (route) => {
      await route.fulfill({ json: pageResponse([{ id: "storage-1", warehouseId: "warehouse-1", code: "ST-REAL", name: "真实存储位", type: "Storage", status: "ACTIVE" }, { id: "shipping-1", warehouseId: "warehouse-1", code: "SHP-REAL", name: "真实发货暂存位", type: "ShippingStaging", status: "ACTIVE" }]) });
    });
    await page.route("**/api/pick-tasks/return", async (route) => {
      const payload = JSON.parse(route.request().postData() || "{}");
      returns.push(payload);
      currentOrder = { ...currentOrder, lines: [{ ...currentOrder.lines[0], pickedQty: "2.000000", shippingStagedQty: "2.000000", activeReservedQty: "2.000000", unshippedQty: "3.000000" }] };
      await route.fulfill({ json: apiResponse({ action: "PICK_RETURN", operationId: "33333333-3333-3333-3333-333333333333", order: currentOrder, inventoryTransactionIds: [], reservationIds: [] }) });
    });
    await page.route("**/api/sales-shipments/confirm", async (route) => {
      const payload = JSON.parse(route.request().postData() || "{}");
      shipments.push(payload);
      currentOrder = { ...currentOrder, lines: [{ ...currentOrder.lines[0], shippedQty: "1.000000", shippingStagedQty: "1.000000", activeReservedQty: "2.000000", unshippedQty: "2.000000" }] };
      await route.fulfill({ json: apiResponse({ action: "SHIP", operationId: "44444444-4444-4444-4444-444444444444", order: currentOrder, inventoryTransactionIds: [], reservationIds: [] }) });
    });
    // 同一确认入口按幂等键执行第二批；测试路由按调用次数返回第二次事实。

    await login(page);
    await page.goto(`${baseURL}/sales/orders/sales-order-from-api`);
    await page.getByRole("button", { name: "退回未发货拣货" }).click();
    await page.locator("input.text-danger").fill("1");
    await page.getByRole("button", { name: "确认退回" }).click();
    await expect.poll(() => returns.length).toBe(1);

    await page.getByRole("button", { name: "确认发货 (扣减实物库存)" }).click();
    await page.locator("input.qty-input").fill("1");
    await page.getByRole("button", { name: "确认发货并扣减实物库存" }).click();
    await expect.poll(() => shipments.length).toBe(1);

    await page.getByRole("button", { name: "确认发货 (扣减实物库存)" }).click();
    await page.locator("input.qty-input").fill("1");
    await page.getByRole("button", { name: "确认发货并扣减实物库存" }).click();
    await expect.poll(() => shipments.length).toBe(2);
    expect(returns[0].lines[0]).toMatchObject({ salesOrderLineId: "sales-line-from-api", returnQty: "1", toLocationId: "storage-1" });
    expect(shipments.map((payload) => payload.shipmentLines[0].shipQty)).toEqual(["1", "1"]);
  });
});
