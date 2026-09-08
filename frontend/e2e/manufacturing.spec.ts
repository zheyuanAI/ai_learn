import { expect, test, type Page } from "@playwright/test";

const baseURL = process.env.STAGE_UI_BASE_URL || "http://localhost:5173";
const username = process.env.STAGE_UI_USERNAME;
const password = process.env.STAGE_UI_PASSWORD;
const tenantCode = process.env.STAGE_UI_TENANT_CODE || "tenant_demo_a";

/** MES 派工、执行和质检入口需要生产角色或管理员权限。 */
function requireMesRole(): void {
  test.skip(
    !username || !password || username !== "mes.inspector",
    "该场景需要 STAGE_UI_USERNAME=mes.inspector 执行；tenant.admin 没有 MES 写权限",
  );
}

/**
 * 用途：使用环境变量中的演示账号建立真实登录会话。
 * 入参：Playwright 页面；出参：登录后的业务页面。
 * 流程：清理旧会话、提交登录表单，再等待路由守卫离开登录页。
 */
async function login(page: Page): Promise<void> {
  test.skip(!username || !password, "需要 STAGE_UI_USERNAME 与 STAGE_UI_PASSWORD 执行 MES 回归");
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

const workOrder = {
  id: "work-order-real",
  workOrderNo: "WO-REAL-001",
  productId: "product-real",
  productName: "真实成品",
  plannedQty: "10.000000",
  reportedQty: "4.000000",
  qualifiedQty: "4.000000",
  defectQty: "0.000000",
  receivedQty: "0.000000",
  status: "InProgress",
  bomId: "bom-real",
  routingId: "routing-real",
  allowedActions: [],
};

test.describe("MES 制造事实链路", () => {
  test("没有受控操作员目录时，派工创建明确阻塞且不提交固定 UUID", async ({ page }) => {
    requireMesRole();
    await page.route("**/api/dispatch-orders*", async (route) => {
      await route.fulfill({ json: pageResponse([]) });
    });
    await page.route("**/api/work-orders*", async (route) => {
      await route.fulfill({ json: pageResponse([{ ...workOrder, status: "Released", routingId: "routing-real" }]) });
    });
    await page.route("**/api/devices*", async (route) => {
      await route.fulfill({ json: pageResponse([]) });
    });

    await login(page);
    await page.goto(`${baseURL}/mes/dispatch`);
    await page.getByRole("button", { name: "新建派工单" }).click();

    await expect(page.getByRole("status")).toContainText("受控同租户操作员目录未提供");
    const submit = page.getByRole("button", { name: "保存派工单 (Draft)" });
    await expect(submit).toBeDisabled();
    await expect(page.getByText("固定 UUID")).toHaveCount(0);
  });

  test("工序报工使用真实 operationExecutionId、workOrderId 和服务端 workReportId", async ({ page }) => {
    requireMesRole();
    let reportPayload: any = null;
    await page.route("**/api/operation-executions*", async (route) => {
      await route.fulfill({
        json: pageResponse([{
          id: "operation-execution-real",
          executionNo: "OE-REAL-001",
          dispatchOrderId: "dispatch-real",
          workOrderId: workOrder.id,
          workOrderNo: workOrder.workOrderNo,
          operationId: "operation-real",
          operationName: "装配",
          operatorId: "operator-real",
          status: "Running",
          reportedQty: "0.000000",
          allowedActions: [{ action: "report", enabled: true }],
        }]),
      });
    });
    await page.route("**/api/dispatch-orders*", async (route) => {
      await route.fulfill({ json: pageResponse([{ id: "dispatch-real", dispatchNo: "DO-REAL-001", status: "Released" }]) });
    });
    await page.route("**/api/work-reports", async (route) => {
      if (route.request().method() === "POST") {
        reportPayload = JSON.parse(route.request().postData() || "{}");
        await route.fulfill({ json: apiResponse({ id: "work-report-real", reportNo: "WR-REAL-001" }) });
        return;
      }
      await route.fulfill({ json: apiResponse([]) });
    });
    page.on("dialog", (dialog) => dialog.dismiss());

    await login(page);
    await page.goto(`${baseURL}/mes/executions`);
    await page.getByRole("button", { name: "报工" }).click();
    const reportModal = page.locator(".modal-card").last();
    await reportModal.locator("input").nth(0).fill("3");
    await reportModal.locator("input").nth(1).fill("1");
    await reportModal.getByRole("button", { name: "提交报工" }).click();

    await expect.poll(() => reportPayload).toMatchObject({
      operationExecutionId: "operation-execution-real",
      workOrderId: "work-order-real",
      operationId: "operation-real",
      qualifiedQty: "3",
      defectQty: "1",
    });
    expect(reportPayload.id).toBeUndefined();
  });

  test("工单详情质量面板串联报工 ID、服务端 inspectionId 和 Failed 处置", async ({ page }) => {
    requireMesRole();
    let createPayload: any = null;
    let submitPayload: any = null;
    let closePayload: any = null;
    let inspections: any[] = [];

    await page.route("**/api/work-orders/work-order-real", async (route) => {
      const url = new URL(route.request().url());
      if (url.pathname.endsWith(`/work-orders/${workOrder.id}`)) {
        await route.fulfill({ json: apiResponse(workOrder) });
        return;
      }
      await route.fulfill({ json: pageResponse([workOrder]) });
    });
    await page.route("**/api/dispatch-orders*", async (route) => {
      await route.fulfill({ json: pageResponse([]) });
    });
    await page.route("**/api/operation-executions*", async (route) => {
      await route.fulfill({ json: pageResponse([]) });
    });
    await page.route("**/api/finished-goods-receipts*", async (route) => {
      await route.fulfill({ json: apiResponse([]) });
    });
    await page.route("**/api/work-reports*", async (route) => {
      await route.fulfill({ json: apiResponse([{
        id: "work-report-real",
        reportNo: "WR-REAL-001",
        workOrderId: workOrder.id,
        operationId: "operation-real",
        operationName: "装配",
        qualifiedQty: "3.000000",
        defectQty: "1.000000",
        reportTime: "2026-09-08T10:00:00Z",
      }]) });
    });
    await page.route("**/api/quality-inspections**", async (route) => {
      const url = new URL(route.request().url());
      const method = route.request().method();
      if (method === "POST" && url.pathname.endsWith("/quality-inspections")) {
        createPayload = JSON.parse(route.request().postData() || "{}");
        inspections = [{
          id: "inspection-real",
          inspectionNo: "QC-REAL-001",
          workReportId: "work-report-real",
          workOrderId: workOrder.id,
          inspectionType: "FINAL_INSPECTION",
          sampleQty: "4.000000",
          status: "Draft",
          result: null,
        }];
        await route.fulfill({ json: apiResponse(inspections[0]) });
        return;
      }
      if (method === "POST" && url.pathname.endsWith("/submit")) {
        submitPayload = JSON.parse(route.request().postData() || "{}");
        inspections = [{ ...inspections[0], status: "Failed", result: "Failed", qualifiedQty: "3", defectQty: "1" }];
        await route.fulfill({ json: apiResponse(inspections[0]) });
        return;
      }
      if (method === "POST" && url.pathname.endsWith("/close")) {
        closePayload = JSON.parse(route.request().postData() || "{}");
        inspections = [{ ...inspections[0], status: "Closed", result: "CLOSED:ISOLATE", disposition: "ISOLATE" }];
        await route.fulfill({ json: apiResponse(inspections[0]) });
        return;
      }
      await route.fulfill({ json: apiResponse(inspections) });
    });

    await login(page);
    await page.goto(`${baseURL}/mes/work-orders/${workOrder.id}?tab=quality`);
    await expect(page.getByRole("heading", { name: /生产质检事实与检验结论/ })).toBeVisible();
    await page.getByRole("button", { name: "发起质检" }).click();
    await page.locator(".modal-card").last().getByRole("button", { name: "保存质检单 (Draft)" }).click();
    await expect.poll(() => createPayload).toMatchObject({ workReportId: "work-report-real" });
    expect(createPayload.id).toBeUndefined();

    await page.getByRole("button", { name: "录入结果" }).click();
    const submitModal = page.locator(".modal-card").last();
    await submitModal.locator("select.form-select").selectOption("Failed");
    await submitModal.locator("input[type='number']").nth(0).fill("3");
    await submitModal.locator("input[type='number']").nth(1).fill("1");
    await submitModal.getByRole("button", { name: "提交结论" }).click();
    await expect.poll(() => submitPayload).toEqual({ qualifiedQty: "3", defectQty: "1", result: "Failed" });

    await page.getByRole("button", { name: "不良处置" }).click();
    await page.locator(".modal-card").last().getByRole("button", { name: "确认处置闭环" }).click();
    await expect.poll(() => closePayload).toEqual({ disposition: "ISOLATE" });
    await expect(page.getByText("ISOLATE")).toBeVisible();
  });

  test("没有合格未入库余额时，成品入库草稿按钮保持禁用", async ({ page }) => {
    // 成品入库属于 MES 写权限场景；管理员没有该权限时应按角色门禁跳过，而不是等待不存在的按钮。
    requireMesRole();
    const fullyReceived = { ...workOrder, qualifiedQty: "5.000000", receivedQty: "5.000000" };
    await page.route("**/api/work-orders*", async (route) => {
      await route.fulfill({ json: pageResponse([fullyReceived]) });
    });
    await page.route("**/api/warehouses*", async (route) => {
      await route.fulfill({ json: pageResponse([{ id: "warehouse-real", code: "FG", name: "成品仓", status: "ACTIVE" }]) });
    });
    await page.route("**/api/locations*", async (route) => {
      await route.fulfill({ json: pageResponse([{ id: "location-real", warehouseId: "warehouse-real", code: "FG-01", name: "成品位", type: "Storage", status: "ACTIVE" }]) });
    });
    await page.route("**/api/finished-goods-receipts*", async (route) => {
      await route.fulfill({ json: apiResponse([]) });
    });

    await login(page);
    await page.goto(`${baseURL}/mes/receipts?workOrderId=${fullyReceived.id}`);
    await page.getByRole("button", { name: "新建成品入库单" }).click();
    await expect(page.getByText(/当前最大可申报入库量为 0/)).toBeVisible();
    await expect(page.getByRole("button", { name: "保存入库单 (草稿)" })).toBeDisabled();
  });
});
