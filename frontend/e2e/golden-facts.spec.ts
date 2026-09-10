import { randomUUID } from "node:crypto";
import { test, expect, type APIRequestContext, type Page } from "@playwright/test";

/**
 * 真实黄金闭环事实验收。
 *
 * 本套件只使用服务端返回的业务 ID，首次写请求均带新的 Idempotency-Key；
 * 幂等专项会有意复用同一键验证重放与异载荷冲突。每次调用前重新登录目标角色，避免单会话顶替造成偶发 401。该文件不保存密码，
 * 运行时必须通过 STAGE_UI_PASSWORD 注入练习环境凭据。
 */

type Json = Record<string, any>;

const API_BASE = process.env.STAGE_API_BASE_URL || "http://127.0.0.1:20001";
const UI_BASE = process.env.STAGE_UI_BASE_URL || "http://localhost:5173";
const TENANT_CODE = process.env.STAGE_UI_TENANT_CODE || "tenant_demo_a";

function requiredEnv(name: string): string {
  const value = process.env[name]?.trim();
  if (!value) {
    throw new Error(`${name} 未设置；真实事实验收不会猜测或保存密码`);
  }
  return value;
}

const PASSWORD = requiredEnv("STAGE_UI_PASSWORD");
const ROLE = {
  admin: process.env.ROLE_ADMIN || "admin.zhang",
  sales: process.env.ROLE_SALES || "sales.liu",
  purchase: process.env.ROLE_PURCHASE || "buyer.chen",
  warehouse: process.env.ROLE_WAREHOUSE || "wh.operator",
  mes: process.env.ROLE_MES || "mes.inspector",
} as const;

/** 登录真实角色并返回本次会话令牌和用户对象。 */
async function login(request: APIRequestContext, username: string): Promise<Json> {
  const response = await request.post(`${API_BASE}/api/auth/login`, {
    data: { username, password: PASSWORD, tenantCode: TENANT_CODE },
  });
  const body = (await response.json()) as Json;
  expect(response.ok(), `登录 ${username} HTTP ${response.status()} ${body.message || ""}`).toBeTruthy();
  expect(body.code, `登录 ${username} 业务响应异常`).toBe(200);
  expect(body.data?.token, `登录 ${username} 未返回 token`).toBeTruthy();
  return body.data;
}

/** 执行成功 API 调用并校验统一响应；写操作由服务端生成事实 ID。 */
async function call(
  request: APIRequestContext,
  username: string,
  path: string,
  method: "GET" | "POST" = "GET",
  payload?: Json,
  idempotencyKey = randomUUID(),
): Promise<any> {
  const session = await login(request, username);
  const headers: Record<string, string> = {
    Authorization: `Bearer ${session.token}`,
  };
  if (method === "POST") {
    headers["Idempotency-Key"] = idempotencyKey;
  }
  const response = await request.fetch(`${API_BASE}${path}`, {
    method,
    headers,
    data: method === "POST" ? payload || {} : undefined,
  });
  const body = (await response.json()) as Json;
  expect(
    response.ok(),
    `${method} ${path} HTTP ${response.status()} ${body.message || ""} request_id=${body.request_id || ""}`,
  ).toBeTruthy();
  expect(body.code, `${method} ${path} 业务响应异常 request_id=${body.request_id || ""}`).toBe(200);
  return body.data;
}

/** 执行预期失败的 API 调用，确认失败不会被包装成绿色成功。 */
async function callFailure(
  request: APIRequestContext,
  username: string,
  path: string,
  payload: Json,
  idempotencyKey = randomUUID(),
): Promise<Json> {
  const session = await login(request, username);
  const response = await request.post(`${API_BASE}${path}`, {
    headers: { Authorization: `Bearer ${session.token}`, "Idempotency-Key": idempotencyKey },
    data: payload,
  });
  const body = (await response.json()) as Json;
  expect(response.ok()).toBeFalsy();
  expect(body.success).toBeFalsy();
  return body;
}

/** 将服务端数量字段转为数字；缺失字段返回 NaN，禁止把缺失当作零。 */
function quantity(value: unknown): number {
  return Number(value);
}

/** 聚合指定产品和仓库的所有服务端余额行。 */
async function inventoryTotal(
  request: APIRequestContext,
  username: string,
  productId: string,
  warehouseId: string,
): Promise<{ onHand: number; reserved: number; available: number }> {
  const page = await call(
    request,
    username,
    `/api/inventory/balances?product_id=${productId}&warehouse_id=${warehouseId}&page=1&size=100`,
  );
  const rows = (page.records || []) as Json[];
  expect(rows.length, "库存查询未返回任何余额行；不能把空结果当作 0").toBeGreaterThan(0);
  return rows.reduce(
    (sum, row) => ({
      onHand: sum.onHand + quantity(row.onHandQty),
      reserved: sum.reserved + quantity(row.reservedQty),
      available: sum.available + quantity(row.availableQty),
    }),
    { onHand: 0, reserved: 0, available: 0 },
  );
}

/** 页面侧复读真实订单详情，确认 UI 使用的是刚刚写入的服务端事实。 */
async function assertOrderVisible(page: Page, orderId: string, orderNo: string): Promise<void> {
  await page.goto(`${UI_BASE}/sales/orders/${orderId}`);
  await page.waitForLoadState("domcontentloaded");
  await expect(page).not.toHaveURL(/\/login(?:\?|$)/);
  await expect(page.locator("body")).toContainText(orderNo);
}

test.describe.serial("阶段 0–9 真实黄金闭环事实验收", () => {
  test("动态清洁样本完成采购—制造—质量—库存—销售闭环", async ({ page, request }) => {
    test.setTimeout(180000);
    const run = Date.now().toString(36).toUpperCase();
    const prefix = `GOLDEN-${run}`;

    // 1. admin.zhang 创建本次运行独立的主数据；所有 ID 均取响应。
    const uomPayload = {
      code: `${prefix}-PC`, name: `件-${run}`, symbol: "PCS", decimalScale: 0, status: "ACTIVE",
    };
    const uomKey = randomUUID();
    const uom = await call(request, ROLE.admin, "/api/uoms", "POST", uomPayload, uomKey);
    const uomReplay = await call(request, ROLE.admin, "/api/uoms", "POST", uomPayload, uomKey);
    expect(uomReplay.id, "同载荷重放未返回首次 UOM ID").toBe(uom.id);
    const uomConflict = await callFailure(request, ROLE.admin, "/api/uoms", {
      ...uomPayload,
      name: `件-异载荷-${run}`,
    }, uomKey);
    expect([400, 409]).toContain(uomConflict.code);
    const warehouse = await call(request, ROLE.admin, "/api/warehouses", "POST", {
      code: `${prefix}-WH`, name: `黄金验收仓-${run}`, type: "RAW_MATERIAL", manager: "验收人", contact: "", address: "", status: "ACTIVE",
    });
    const customer = await call(request, ROLE.admin, "/api/customers", "POST", {
      customerCode: `${prefix}-CUS`, customerName: `黄金客户-${run}`, contactPerson: "验收人", contactPhone: "", shippingAddress: "练习地址", status: "ACTIVE",
    });
    const supplier = await call(request, ROLE.admin, "/api/suppliers", "POST", {
      supplierCode: `${prefix}-SUP`, supplierName: `黄金供应商-${run}`, contactPerson: "验收人", contactPhone: "", address: "练习地址", status: "ACTIVE",
    });
    const locations: Record<string, Json> = {};
    for (const [key, type, name] of [
      ["receiving", "ReceivingStaging", "收货暂存"],
      ["quality", "QualityHold", "质量隔离"],
      ["storage", "Storage", "原料成品库"],
      ["shipping", "ShippingStaging", "发货暂存"],
    ] as const) {
      locations[key] = await call(request, ROLE.admin, "/api/locations", "POST", {
        warehouseId: warehouse.id, code: `${prefix}-${key}`, name: `${name}-${run}`, type, capacity: "1000", status: "ACTIVE",
      });
    }
    const raw = await call(request, ROLE.admin, "/api/products", "POST", {
      sku: `${prefix}-RM`, name: `原料-${run}`, spec: "黄金闭环原料", uom: uom.code, category: "RAW_MATERIAL", batchManaged: true, unitPrice: "1", status: "ACTIVE",
    });
    const finished = await call(request, ROLE.admin, "/api/products", "POST", {
      sku: `${prefix}-FG`, name: `成品-${run}`, spec: "黄金闭环成品", uom: uom.code, category: "FINISHED_GOODS", batchManaged: true, unitPrice: "1", status: "ACTIVE",
    });

    // 2. mes.inspector 创建 BOM/Routing；工作中心从真实目录读取，不猜 UUID。
    const routingPage = await call(request, ROLE.mes, "/api/routings?page=1&size=100");
    const routingRecords = (routingPage.records || []) as Json[];
    const discoveredWorkCenter = routingRecords
      .flatMap((item) => (item.operations || []) as Json[])
      .map((operation) => operation.workCenterId)
      .find(Boolean) || process.env.STAGE_WORK_CENTER_ID;
    if (!discoveredWorkCenter) {
      throw new Error("GOLDEN_FLOW_BLOCKED_WORK_CENTER_FACT: 真实 Routing 目录未返回 workCenterId，请注入 STAGE_WORK_CENTER_ID");
    }
    const bom = await call(request, ROLE.mes, "/api/boms", "POST", {
      bomCode: `${prefix}-BOM`, productId: finished.id, version: "V1", status: "ACTIVE",
      components: [{ componentProductId: raw.id, componentQty: 1, uom: uom.code, scrapRate: 0 }],
    });
    const routing = await call(request, ROLE.mes, "/api/routings", "POST", {
      routingCode: `${prefix}-RTG`, productId: finished.id, version: "V1", status: "ACTIVE",
      operations: [{ operationNo: 10, operationName: "装配", workCenterId: discoveredWorkCenter, standardTimeMinutes: 10 }],
    });
    const operationId = routing.operations?.[0]?.id;
    expect(operationId, "Routing 响应未返回 operationId").toBeTruthy();

    // 3. sales.liu 创建并审核 F=2 的销售需求。
    const order = await call(request, ROLE.sales, "/api/sales-orders", "POST", {
      soNo: `${prefix}-SO`, customerId: customer.id, plannedShipDate: "2026-09-30", remark: "真实黄金闭环",
      lines: [{ lineNo: 1, productId: finished.id, uom: uom.code, orderedQty: "2" }],
    });
    const salesLineId = order.lines?.[0]?.id;
    expect(salesLineId, "销售订单响应未返回 salesOrderLineId").toBeTruthy();
    await call(request, ROLE.sales, `/api/sales-orders/${order.id}/submit`, "POST");
    await call(request, ROLE.sales, `/api/sales-orders/${order.id}/approve`, "POST");

    // 4. mes.inspector 创建并审核来源销售行的 2 件工单。
    const workOrder = await call(request, ROLE.mes, "/api/work-orders", "POST", {
      workOrderNo: `${prefix}-WO`, productId: finished.id, plannedQty: 2,
      plannedStartTime: "2026-09-08T16:00:00+08:00", plannedFinishTime: "2026-09-08T18:00:00+08:00",
      bomId: bom.id, routingId: routing.id, sourceSalesOrderLineId: salesLineId,
    });
    const workOrderId = workOrder.workOrder?.id || workOrder.id;
    expect(workOrderId, "工单响应未返回 workOrder.id").toBeTruthy();
    await call(request, ROLE.mes, `/api/work-orders/${workOrderId}/submit`, "POST");
    const releasedWorkOrder = await call(request, ROLE.mes, `/api/work-orders/${workOrderId}/approve`, "POST");
    expect(releasedWorkOrder.status).toBe("Released");

    // 5. buyer.chen 创建并审核 R=3；仓库实际到货 3、拒收 1、实收 2。
    const purchaseOrder = await call(request, ROLE.purchase, "/api/purchase-orders", "POST", {
      poNo: `${prefix}-PO`, supplierId: supplier.id, expectedArrivalDate: "2026-09-09", remark: "黄金闭环采购",
      lines: [{ lineNo: 1, productId: raw.id, uom: uom.code, orderedQty: "3", targetWarehouseId: warehouse.id }],
    });
    const purchaseLineId = purchaseOrder.lines?.[0]?.id;
    expect(purchaseLineId, "采购订单响应未返回 purchaseOrderLineId").toBeTruthy();
    await call(request, ROLE.purchase, `/api/purchase-orders/${purchaseOrder.id}/submit`, "POST");
    await call(request, ROLE.purchase, `/api/purchase-orders/${purchaseOrder.id}/approve`, "POST");
    const receipt = await call(request, ROLE.warehouse, "/api/purchase-receipts/confirm", "POST", {
      purchaseOrderId: purchaseOrder.id, receiptNo: `${prefix}-RCV`, receiptTime: "2026-09-09T09:00:00+08:00",
      qualityHoldLocationId: locations.quality.id,
      lines: [{ purchaseOrderLineId: purchaseLineId, productId: raw.id, uom: uom.code, arrivedQty: "3", rejectedQty: "1", receivedQty: "2", lotNo: `${prefix}-LOT`, rejectionReason: "外观不合格 1 件" }],
    });
    expect(receipt.id, "服务端未分配独立 receiptId").toBeTruthy();
    const receiptLine = receipt.lines?.[0];
    expect(quantity(receiptLine?.arrivedQty)).toBe(3);
    expect(quantity(receiptLine?.rejectedQty)).toBe(1);
    expect(quantity(receiptLine?.receivedQty)).toBe(2);
    const rawAtHold = await inventoryTotal(request, ROLE.admin, raw.id, warehouse.id);
    expect(rawAtHold.onHand).toBe(2);
    expect(rawAtHold.reserved).toBe(0);
    await call(request, ROLE.purchase, `/api/purchase-orders/${purchaseOrder.id}/complete`, "POST", { completionReason: "验收样本结束，终止剩余待收 1 件" });

    // 6. 采购质检只写质量事实；放行执行移动到暂存，再确认上架到 Storage。
    const purchaseInspection = await call(request, ROLE.mes, `/api/purchase-receipts/${receipt.id}/quality/inspect`, "POST", {
      purchaseOrderId: purchaseOrder.id, purchaseReceiptId: receipt.id, purchaseReceiptLineId: receiptLine.id,
      productId: raw.id, inspectedQty: "2", qualifiedQty: "2", unqualifiedQty: "0", inspectionRemark: "全数合格",
    });
    const disposition = await call(request, ROLE.mes, `/api/purchase-receipts/${receipt.id}/quality/release`, "POST", {
      inspectionId: purchaseInspection.id, dispositionType: "Release", dispositionQty: "2", reason: "质检放行",
    });
    expect(disposition.id, "服务端未返回质量处置 ID").toBeTruthy();
    await call(request, ROLE.warehouse, `/api/purchase-quality-dispositions/${disposition.id}/confirm`, "POST", {
      dispositionId: disposition.id, toLocationId: locations.receiving.id, putawayTargetLocationId: locations.storage.id,
    });
    const putawayPage = await call(request, ROLE.warehouse, "/api/putaway-tasks?page=1&size=1000");
    const putaway = (putawayPage.records || []).find((item: Json) => item.purchaseReceiptId === receipt.id);
    expect(putaway?.id, "质量放行后未生成对应上架任务").toBeTruthy();
    await call(request, ROLE.warehouse, `/api/putaway-tasks/${putaway.id}/confirm`, "POST", {
      taskId: putaway.id, toLocationId: locations.storage.id, putawayQty: "2",
    });
    const rawAtStorage = await inventoryTotal(request, ROLE.admin, raw.id, warehouse.id);
    expect(rawAtStorage.onHand).toBe(2);
    expect(rawAtStorage.available).toBe(2);

    // 7. 先下达派工并进入 Processing，再补建 NotStarted 执行实例（回归 Task15 修复）。
    const mesSession = await login(request, ROLE.mes);
    const dispatch = await call(request, ROLE.mes, "/api/dispatch-orders", "POST", {
      work_order_id: workOrderId, operation_id: operationId, operator_id: mesSession.user.userId, dispatch_qty: 2,
    });
    await call(request, ROLE.mes, `/api/dispatch-orders/${dispatch.id}/release`, "POST");
    const processingDispatch = await call(request, ROLE.mes, `/api/dispatch-orders/${dispatch.id}/start-processing`, "POST");
    expect(processingDispatch.status).toBe("Processing");
    const execution = await call(request, ROLE.mes, "/api/operation-executions", "POST", {
      dispatch_order_id: dispatch.id, work_order_id: workOrderId, operation_id: operationId,
    });
    expect(execution.status).toBe("NotStarted");

    // 8. wh.operator 确认领料 R=2；实际库存从 Storage 扣减。
    const issue = await call(request, ROLE.mes, "/api/material-issues", "POST", {
      issueNo: `${prefix}-ISSUE`, workOrderId,
      items: [{ productId: raw.id, warehouseId: warehouse.id, locationId: locations.storage.id, quantity: 2 }],
    });
    await call(request, ROLE.warehouse, `/api/material-issues/${issue.id}/confirm`, "POST");
    const rawAfterIssue = await inventoryTotal(request, ROLE.admin, raw.id, warehouse.id);
    expect(rawAfterIssue.onHand).toBe(0);

    // 9. 工序执行、报工、生产质检、成品入库和工单完工。
    await call(request, ROLE.mes, `/api/operation-executions/${execution.id}/start`, "POST", { occurredAt: "2026-09-09T16:10:00+08:00" });
    await call(request, ROLE.mes, `/api/operation-executions/${execution.id}/complete`, "POST", { occurredAt: "2026-09-09T16:20:00+08:00" });
    const completedDispatch = await call(request, ROLE.mes, `/api/dispatch-orders/${dispatch.id}/complete`, "POST");
    expect(completedDispatch.status).toBe("Completed");
    const report = await call(request, ROLE.mes, "/api/work-reports", "POST", {
      reportNo: `${prefix}-REPORT`, operationExecutionId: execution.id, workOrderId, operationId,
      reportTime: "2026-09-09T16:00:00+08:00", qualifiedQty: 2, defectQty: 0, remark: "全数合格报工",
    });
    expect(quantity(report.qualifiedQty)).toBe(2);
    // 负向：未关闭 Failed 质检时，成品入库必须被服务端阻断；关闭后才允许继续正式 Passed 质检。
    const failedInspection = await call(request, ROLE.mes, "/api/quality-inspections", "POST", {
      inspectionNo: `${prefix}-QI-FAILED`, workReportId: report.id, inspectionType: "FINAL", sampleQty: 1,
    });
    await call(request, ROLE.mes, `/api/quality-inspections/${failedInspection.id}/submit`, "POST", {
      qualifiedQty: 0, defectQty: 1, result: "Failed",
    });
    const blockedFinishedReceipt = await callFailure(request, ROLE.mes, "/api/finished-goods-receipts", {
      receiptNo: `${prefix}-FGR-BLOCKED`, workOrderId, receiptQty: 1,
      warehouseId: warehouse.id, locationId: locations.storage.id,
    });
    expect([400, 409, 422]).toContain(Number(blockedFinishedReceipt.code));
    await call(request, ROLE.mes, `/api/quality-inspections/${failedInspection.id}/close`, "POST", {
      disposition: "SCRAP",
    });
    const finalInspection = await call(request, ROLE.mes, "/api/quality-inspections", "POST", {
      inspectionNo: `${prefix}-QI`, workReportId: report.id, inspectionType: "FINAL", sampleQty: 2,
    });
    const passedInspection = await call(request, ROLE.mes, `/api/quality-inspections/${finalInspection.id}/submit`, "POST", {
      qualifiedQty: 2, defectQty: 0, result: "Passed",
    });
    expect(passedInspection.status).toBe("Passed");
    const finishedReceipt = await call(request, ROLE.mes, "/api/finished-goods-receipts", "POST", {
      receiptNo: `${prefix}-FGR`, workOrderId, receiptQty: 2, warehouseId: warehouse.id, locationId: locations.storage.id,
    });
    await call(request, ROLE.warehouse, `/api/finished-goods-receipts/${finishedReceipt.id}/confirm`, "POST");
    const completedWorkOrder = await call(request, ROLE.mes, `/api/work-orders/${workOrderId}/complete`, "POST");
    expect(completedWorkOrder.status).toBe("Completed");
    const finishedBeforeShip = await inventoryTotal(request, ROLE.admin, finished.id, warehouse.id);
    expect(finishedBeforeShip.onHand).toBe(2);
    expect(finishedBeforeShip.reserved).toBe(0);
    expect(finishedBeforeShip.available).toBe(2);

    // 10. wh.operator 两次直接拣货和发货；每次均复读服务端累计量。
    const pickPayload = {
      salesOrderId: order.id,
      lines: [{ salesOrderLineId: salesLineId, pickedQty: "1", sourceLocationId: locations.storage.id, shippingLocationId: locations.shipping.id }],
    };
    const insufficientPick = await callFailure(request, ROLE.warehouse, "/api/pick-tasks/confirm", {
      salesOrderId: order.id,
      lines: [{ salesOrderLineId: salesLineId, pickedQty: "3", sourceLocationId: locations.storage.id, shippingLocationId: locations.shipping.id }],
    });
    expect([400, 409, 422]).toContain(Number(insufficientPick.code));
    const firstPick = await call(request, ROLE.warehouse, "/api/pick-tasks/confirm", "POST", pickPayload);
    expect(quantity(firstPick.order.lines[0].pickedQty)).toBe(1);
    const firstShip = await call(request, ROLE.warehouse, "/api/sales-shipments/confirm", "POST", {
      salesOrderId: order.id, shipTime: "2026-09-09T17:00:00+08:00", shipmentLines: [{ salesOrderLineId: salesLineId, productId: finished.id, shipQty: "1" }],
    });
    expect(quantity(firstShip.order.lines[0].shippedQty)).toBe(1);
    const secondPick = await call(request, ROLE.warehouse, "/api/pick-tasks/confirm", "POST", pickPayload);
    expect(quantity(secondPick.order.lines[0].pickedQty)).toBe(2);
    const secondShip = await call(request, ROLE.warehouse, "/api/sales-shipments/confirm", "POST", {
      salesOrderId: order.id, shipTime: "2026-09-09T17:01:00+08:00", shipmentLines: [{ salesOrderLineId: salesLineId, productId: finished.id, shipQty: "1" }],
    });
    expect(secondShip.order.status).toBe("Completed");
    expect(quantity(secondShip.order.lines[0].orderedQty)).toBe(2);
    expect(quantity(secondShip.order.lines[0].pickedQty)).toBe(2);
    expect(quantity(secondShip.order.lines[0].shippedQty)).toBe(2);
    const finishedAfterShip = await inventoryTotal(request, ROLE.admin, finished.id, warehouse.id);
    expect(finishedAfterShip.onHand).toBe(0);
    expect(finishedAfterShip.reserved).toBe(0);
    expect(finishedAfterShip.available).toBe(0);

    // 11. 负向：无 MES 写权限的 tenant.admin 不能创建派工事实。
    const forbidden = await callFailure(request, ROLE.admin, "/api/dispatch-orders", {
      work_order_id: workOrderId, operation_id: operationId, operator_id: mesSession.user.userId, dispatch_qty: 1,
    });
    expect(forbidden.code).toBe(403);

    // 12. 浏览器页面复读最终订单，证明 API 事实能由正式页面读取。
    await loginAsPage(page, ROLE.sales);
    await assertOrderVisible(page, order.id, order.soNo);
  });
});

/** 使用真实登录表单为页面复读建立会话。 */
async function loginAsPage(page: Page, username: string): Promise<void> {
  await page.goto(`${UI_BASE}/login`);
  await page.waitForLoadState("domcontentloaded");
  await page.locator("#loginTenant, select[name='tenantCode']").first().selectOption(TENANT_CODE);
  await page.fill("#loginUsername, input[name='username']", username);
  await page.fill("#loginPassword, input[name='password']", PASSWORD);
  await page.click("button[type='submit']");
  await page.waitForURL((url) => !url.pathname.includes("/login"), { timeout: 15000 });
}
