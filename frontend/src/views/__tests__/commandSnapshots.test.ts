import { readFileSync } from "node:fs";
import { runInNewContext } from "node:vm";
import { compileScript, parse } from "@vue/compiler-sfc";
import ts from "typescript";
import * as vue from "vue";
import * as inventory from "../../types/inventory";
import { currentLocalDateTimeValue } from "../../utils/dateTime";
import { useCommand } from "../../composables/useCommand";
import { ApiError } from "../../utils/request";
import { afterEach, expect, it, vi } from "vitest";

const compiledScripts = new Map<string, string>();

/**
 * 用途：编译实际页面并执行 setup；入参为页面路径及网络替身，返回页面内部状态与事件处理方法。
 * 流程：复用现有 SFC/VM 行为测试方式，保留原 useCommand，只替换网络、生命周期和 UI 宿主。
 */
function createView(file: string, commandApi: Record<string, any>, context: { props?: any; expose?: any; emit?: any; liveWatch?: boolean } = {}) {
  let source = compiledScripts.get(file);
  if (!source) {
    const path = new URL(`../${file}.vue`, import.meta.url);
    const script = compileScript(parse(readFileSync(path, "utf8")).descriptor, { id: file });
    source = ts.transpileModule(script.content, { compilerOptions: { module: ts.ModuleKind.CommonJS } }).outputText;
    compiledScripts.set(file, source);
  }
  const api = new Proxy(commandApi, {
    get: (target, name: string) => target[name] ?? (() => Promise.resolve({ data: { records: [] } })),
  });
  const componentExports: any = {};
  runInNewContext(source, {
    exports: componentExports, crypto: globalThis.crypto, Date,
    console: { error: vi.fn(), warn: vi.fn() },
    require: (name: string) => {
      if (name === "vue") return { ...vue, watch: context.liveWatch ? vue.watch : () => {}, onMounted: () => {} };
      if (name === "vue-router") return { useRoute: () => ({ query: {} }), useRouter: () => ({ push: vi.fn() }) };
      if (name.includes("useCommand")) return { useCommand };
      if (name.includes("usePermission")) return { usePermission: () => ({ hasPermission: () => true, hasAnyPermission: () => true }) };
      if (name.includes("stores/auth")) return { useAuthStore: () => ({ permissions: [] }) };
      if (name.includes("actionGuard")) return { isActionAllowed: () => true };
      if (name.includes("utils/request")) return { ApiError };
      if (name.includes("types/inventory")) return inventory;
      if (name.includes("utils/dateTime")) return { currentLocalDateTimeValue };
      if (name === "element-plus") return { ElMessage: { success: vi.fn(), error: vi.fn(), warning: vi.fn() }, ElMessageBox: { confirm: vi.fn().mockRejectedValue("cancel") } };
      if (name.includes("/api/")) return api;
      return {};
    },
  });
  return componentExports.default.setup(context.props ?? { id: "order-A", workOrderId: "order-A" }, { expose: context.expose ?? vi.fn(), emit: context.emit ?? vi.fn() });
}

type Scenario = {
  name: string;
  file: string;
  handler: string;
  api: string;
  prepare: (state: any) => void;
  change: (state: any) => void;
  payload?: any;
};

const scenarios: Scenario[] = [
  {
    name: "采购建单", file: "purchasing/PurchaseOrderListView", handler: "submitCreateOrder", api: "createPurchaseOrder",
    prepare: (s) => {
      s.products.value = [{ id: "product-A", uom: "EA" }]; s.suppliers.value = [{ id: "supplier-A" }]; s.warehouses.value = [{ id: "warehouse-A" }];
      Object.assign(s.createForm, { supplierId: "supplier-A", productId: "product-A", targetWarehouseId: "warehouse-A", orderedQty: "1", expectedArrivalDate: "2026-10-01" });
    },
    change: (s) => Object.assign(s.createForm, { supplierId: "supplier-B", productId: "product-B", orderedQty: "2", targetWarehouseId: "warehouse-B" }),
  },
  {
    name: "采购收货嵌套明细", file: "purchasing/PurchaseOrderListView", handler: "handleConfirmReceipt", api: "confirmPurchaseReceiptWithServerId",
    prepare: () => {}, change: () => {}, payload: { purchaseOrderId: "order-A", lines: [{ receivedQty: "1" }] },
  },
  {
    name: "采购质量检验", file: "purchasing/QualityDispositionView", handler: "submitInspect", api: "inspectQuality",
    prepare: (s) => Object.assign(s.inspectForm, { purchaseReceiptId: "receipt-A", purchaseReceiptLineId: "line-A", productId: "product-A", inspectedQty: "1", qualifiedQty: "1" }),
    change: (s) => Object.assign(s.inspectForm, { purchaseReceiptId: "receipt-B", productId: "product-B", inspectedQty: "2" }),
  },
  {
    name: "采购质量决定", file: "purchasing/QualityDispositionView", handler: "submitDecide", api: "decideQualityDisposition",
    prepare: (s) => { s.selectedInspect.value = { id: "inspection-A", purchaseReceiptId: "receipt-A" }; Object.assign(s.decideForm, { dispositionType: "Release", dispositionQty: "1", reason: "首次原因" }); },
    change: (s) => { s.selectedInspect.value.id = "inspection-B"; s.selectedInspect.value.purchaseReceiptId = "receipt-B"; Object.assign(s.decideForm, { dispositionType: "Scrap", dispositionQty: "2", reason: "新原因" }); },
  },
  {
    name: "采购处置执行", file: "purchasing/QualityDispositionView", handler: "executeDisposition", api: "confirmQualityDisposition",
    prepare: (s) => { s.selectedDisp.value = { id: "disposition-A", dispositionType: "Scrap" }; s.putawayTargetLocationId.value = "location-A"; },
    change: (s) => { s.selectedDisp.value.id = "disposition-B"; s.putawayTargetLocationId.value = "location-B"; },
  },
  {
    name: "采购上架", file: "purchasing/PutawayTaskView", handler: "submitPutaway", api: "confirmPutawayTask",
    prepare: (s) => { s.selectedTask.value = { id: "task-A", fromLocationId: "source-A" }; s.storageLocations.value = [{ id: "location-A", warehouseId: "warehouse-A", type: "Storage", status: "ACTIVE" }]; s.targetLocationId.value = "location-A"; s.putawayQtyInput.value = "1"; },
    change: (s) => { s.selectedTask.value.id = "task-B"; s.targetLocationId.value = "location-B"; s.putawayQtyInput.value = "2"; },
  },
  {
    name: "派工创建", file: "manufacturing/DispatchView", handler: "submitCreateDispatch", api: "createDispatchOrder",
    prepare: (s) => { s.operatorDirectoryAvailable.value = true; Object.assign(s.createForm, { workOrderId: "order-A", operationId: "operation-A", operatorId: "operator-A", dispatchQty: "1", deviceId: "device-A" }); },
    change: (s) => Object.assign(s.createForm, { workOrderId: "order-B", dispatchQty: "2", deviceId: "device-B" }),
  },
  {
    name: "派工下达", file: "manufacturing/DispatchView", handler: "handleConfirmRelease", api: "releaseDispatchOrder",
    prepare: (s) => { s.releaseConfirm.item = { id: "dispatch-A" }; }, change: (s) => { s.releaseConfirm.item.id = "dispatch-B"; },
  },
  {
    name: "工序执行创建", file: "manufacturing/OperationExecutionView", handler: "submitCreateExecution", api: "createOperationExecution",
    prepare: (s) => Object.assign(s.createForm, { dispatchOrderId: "dispatch-A", deviceId: "device-A" }),
    change: (s) => Object.assign(s.createForm, { dispatchOrderId: "dispatch-B", deviceId: "device-B" }),
  },
  {
    name: "工序暂停", file: "manufacturing/OperationExecutionView", handler: "submitPause", api: "pauseOperationExecution",
    prepare: (s) => { s.activeExec.value = { id: "execution-A" }; s.pauseReason.value = "首次原因"; },
    change: (s) => { s.activeExec.value.id = "execution-B"; s.pauseReason.value = "新原因"; },
  },
  ...(["start", "resume", "complete"] as const).map((action) => ({
    name: `工序${action}`, file: "manufacturing/OperationExecutionView", handler: "executeConfirmAction", api: `${action}OperationExecution`,
    prepare: (s: any) => { s.confirmState.targetItem = { id: "execution-A" }; s.confirmState.type = action; },
    change: (s: any) => { s.confirmState.targetItem.id = "execution-B"; s.confirmState.type = "pause"; },
  })),
  {
    name: "工序报工", file: "manufacturing/OperationExecutionView", handler: "submitWorkReport", api: "createWorkReport",
    prepare: (s) => { s.activeExec.value = { id: "execution-A", workOrderId: "order-A", operationId: "operation-A" }; Object.assign(s.reportForm, { qualifiedQty: "1", defectQty: "0", reportTime: "2026-10-01T10:00", remark: "首次备注" }); },
    change: (s) => { s.activeExec.value.id = "execution-B"; Object.assign(s.reportForm, { qualifiedQty: "2", reportTime: "2026-10-02T10:00", remark: "新备注" }); },
  },
  ...(["Issue", "Return"] as const).map((kind) => ({
    name: `生产${kind}创建`, file: "manufacturing/MaterialMovementView", handler: `submitCreate${kind}`, api: `createMaterial${kind}`,
    prepare: (s: any) => Object.assign(s[kind === "Issue" ? "issueForm" : "returnForm"], { workOrderId: "order-A", productId: "product-A", warehouseId: "warehouse-A", locationId: "location-A", issueQty: "1", returnQty: "1", overageReason: "首次原因", reason: "首次原因" }),
    change: (s: any) => Object.assign(s[kind === "Issue" ? "issueForm" : "returnForm"], { workOrderId: "order-B", productId: "product-B", issueQty: "2", returnQty: "2", locationId: "location-B", reason: "新原因", overageReason: "新原因" }),
  })),
  ...(["Issue", "Return"] as const).map((kind) => ({
    name: `生产${kind}确认`, file: "manufacturing/MaterialMovementView", handler: "handleExecuteConfirm", api: `confirmMaterial${kind}`,
    prepare: (s: any) => { s.confirmDialog.type = kind.toLowerCase(); s.confirmDialog.targetId = "movement-A"; },
    change: (s: any) => { s.confirmDialog.type = "other"; s.confirmDialog.targetId = "movement-B"; },
  })),
  {
    name: "成品入库创建", file: "manufacturing/FinishedGoodsReceiptView", handler: "submitCreateReceipt", api: "createFinishedGoodsReceipt",
    prepare: (s) => { s.workOrders.value = [{ id: "order-A", qualifiedQty: "5", receivedQty: "0" }]; Object.assign(s.createForm, { workOrderId: "order-A", receiptQty: "1", warehouseId: "warehouse-A", locationId: "location-A" }); },
    change: (s) => Object.assign(s.createForm, { workOrderId: "order-B", receiptQty: "2", locationId: "location-B" }),
  },
  {
    name: "成品入库确认", file: "manufacturing/FinishedGoodsReceiptView", handler: "handleExecuteConfirm", api: "confirmFinishedGoodsReceipt",
    prepare: (s) => { s.confirmDialog.item = { id: "receipt-A" }; }, change: (s) => { s.confirmDialog.item.id = "receipt-B"; },
  },
  ...(["submit", "approve", "complete"] as const).map((action) => ({
    name: `工单${action}`, file: "manufacturing/WorkOrderDetailView", handler: "executePromptAction", api: `${action}WorkOrder`,
    prepare: (s: any) => { s.workOrder.value = { id: "order-A" }; s.confirmState.actionType = action; },
    change: (s: any) => { s.workOrder.value.id = "order-B"; s.confirmState.actionType = "other"; },
  })),
  ...(["reject", "manual"] as const).map((action) => ({
    name: `工单${action}原因`, file: "manufacturing/WorkOrderDetailView", handler: action === "reject" ? "submitReject" : "submitManualComplete", api: action === "reject" ? "rejectWorkOrder" : "manualCompleteWorkOrder",
    prepare: (s: any) => { s.workOrder.value = { id: "order-A" }; s.rejectionReason.value = "首次原因"; s.manualCompleteReason.value = "首次原因"; },
    change: (s: any) => { s.workOrder.value.id = "order-B"; s.rejectionReason.value = "新原因"; s.manualCompleteReason.value = "新原因"; },
  })),
  {
    name: "生产质检创建", file: "manufacturing/components/ProductionQualityPanel", handler: "submitCreateInspection", api: "createQualityInspection",
    prepare: (s) => { s.selectedReport.value = { id: "report-A" }; Object.assign(s.createForm, { inspectionNo: "inspection-A", inspectionType: "FIRST_ARTICLE", sampleQty: "1" }); },
    change: (s) => { s.selectedReport.value.id = "report-B"; Object.assign(s.createForm, { inspectionNo: "inspection-B", sampleQty: "2" }); },
  },
  {
    name: "生产质检评定", file: "manufacturing/components/ProductionQualityPanel", handler: "submitResult", api: "submitQualityInspection",
    prepare: (s) => { s.selectedInspection.value = { id: "inspection-A" }; Object.assign(s.submitForm, { result: "Passed", qualifiedQty: "1", defectQty: "0" }); },
    change: (s) => { s.selectedInspection.value.id = "inspection-B"; Object.assign(s.submitForm, { result: "Failed", qualifiedQty: "0", defectQty: "2" }); },
  },
  {
    name: "生产质检关闭", file: "manufacturing/components/ProductionQualityPanel", handler: "submitClose", api: "closeQualityInspection",
    prepare: (s) => { s.selectedInspection.value = { id: "inspection-A" }; s.closeDisposition.value = "ISOLATE"; },
    change: (s) => { s.selectedInspection.value.id = "inspection-B"; s.closeDisposition.value = "SCRAP"; },
  },
];

afterEach(() => vi.useRealTimers());

/** 用途：定位实际页面弹窗状态；入参为既有行为场景，返回响应式引用或对象属性路径。 */
function modalPath(scenario: Scenario): string {
  const paths: Record<string, string> = {
    submitCreateOrder: "isCreateModalOpen.value", handleConfirmReceipt: "isReceiptModalOpen.value",
    submitInspect: "isInspectOpen.value", submitDecide: "isDecideOpen.value", executeDisposition: "isExecuteOpen.value",
    submitPutaway: "isConfirmModalOpen.value", submitCreateDispatch: "createModalVisible.value", handleConfirmRelease: "releaseConfirm.visible",
    submitCreateExecution: "createModalVisible.value", submitPause: "pauseModalVisible.value", executeConfirmAction: "confirmState.visible", submitWorkReport: "reportModalVisible.value",
    submitCreateIssue: "createIssueModalVisible.value", submitCreateReturn: "createReturnModalVisible.value",
    submitCreateReceipt: "createModalVisible.value", handleExecuteConfirm: "confirmDialog.visible",
    executePromptAction: "confirmState.visible", submitReject: "rejectModalVisible.value", submitManualComplete: "manualModalVisible.value",
    submitCreateInspection: "createModalVisible.value", submitResult: "submitModalVisible.value", submitClose: "closeModalVisible.value",
  };
  return paths[scenario.handler];
}

/** 用途：访问页面实际嵌套状态；入参为 setup 返回值及路径，返回宿主对象和属性名供读取或设置。 */
function property(state: any, path: string) {
  const keys = path.split(".");
  const key = keys.pop()!;
  return { owner: keys.reduce((owner, name) => owner[name], state), key };
}

/** 用途：提供符合实际各 API 返回形态的复读事实；无入参，返回分页、数组与详情网络替身。 */
function refreshApis() {
  const paged = () => Promise.resolve({ data: { records: [{ id: "fresh-A" }], total: 1 } });
  const array = () => Promise.resolve({ data: [{ id: "fresh-A" }] });
  return {
    getPurchaseOrders: paged, getPutawayTasks: paged, getDispatchOrders: paged, getOperationExecutions: paged,
    getQualityInspections: array, getQualityDispositions: array, getWorkReports: () => Promise.resolve({ data: [] }),
    getMaterialIssues: array, getMaterialReturns: array, getFinishedGoodsReceipts: array,
    getWorkOrderDetail: () => Promise.resolve({ data: { id: "order-A", revision: 2 } }),
  };
}

/** 用途：获取页面实际显示事实；入参为测试场景和 setup 状态，返回列表或工单详情供断言刷新结果。 */
function displayedFacts(scenario: Scenario, state: any) {
  if (scenario.file.includes("WorkOrderDetail")) return state.workOrder.value;
  const list = scenario.file.includes("PurchaseOrderList") ? "orderList"
    : scenario.file.includes("QualityDisposition") || scenario.file.includes("ProductionQualityPanel") ? "inspections"
    : scenario.file.includes("PutawayTask") ? "taskList"
    : scenario.file.includes("DispatchView") ? "dispatchList"
    : scenario.file.includes("OperationExecution") ? "executionList"
    : scenario.file.includes("FinishedGoodsReceipt") ? "receiptList"
    : scenario.api.includes("Return") ? "returnList" : "issueList";
  return state[list].value;
}

it.each(scenarios)("$name：503后编辑表单和对象仍同键重试首次载荷", async (scenario) => {
  const requests: any[][] = [];
  const operation = vi.fn(async (...args: any[]) => {
    // 修改用途：记录实际调用时的 JSON 快照，不能让 mock.calls 持有实时对象掩盖旧实现缺陷。
    requests.push(JSON.parse(JSON.stringify(args)));
    if (requests.length === 1) throw new ApiError({ message: "暂不可用", httpStatus: 503 });
    return { data: { id: "created-A", lines: [{ id: "line-A" }] } };
  });
  const state = createView(scenario.file, { [scenario.api]: operation });
  scenario.prepare(state);
  const payload = scenario.payload ? JSON.parse(JSON.stringify(scenario.payload)) : undefined;
  await state[scenario.handler](payload);
  expect(operation).toHaveBeenCalledOnce();
  expect(state.canRetry.value).toBe(true);
  scenario.change(state);
  if (payload) { payload.purchaseOrderId = "order-B"; payload.lines[0].receivedQty = "2"; }
  await state.retry();
  expect(requests).toHaveLength(2);
  expect(requests[1]).toEqual(requests[0]);
});

it.each(scenarios)("$name：retry成功关闭原弹窗并更新页面事实", async (scenario) => {
  const operation = vi.fn().mockRejectedValueOnce(new ApiError({ message: "暂不可用", httpStatus: 503 }))
    .mockResolvedValue({ data: { id: "created-A", lines: [{ id: "line-A" }] } });
  const state = createView(scenario.file, { ...refreshApis(), [scenario.api]: operation });
  scenario.prepare(state);
  if (state.availableWorkOrders) state.availableWorkOrders.value = [{ id: "order-A" }];
  if (state.selectedWorkOrderId) state.selectedWorkOrderId.value = "order-A";
  if (state.selectedOrderForReceipt) state.selectedOrderForReceipt.value = { id: "order-A" };
  const modal = property(state, modalPath(scenario));
  modal.owner[modal.key] = true;
  await state[scenario.handler](scenario.payload);
  expect(operation).toHaveBeenCalledOnce();
  expect(modal.owner[modal.key]).toBe(true);
  expect(state.canRetry.value).toBe(true);
  await state.retry();
  expect(operation).toHaveBeenCalledTimes(2);
  expect(modal.owner[modal.key]).toBe(false);
  expect(state.canRetry.value).toBe(false);
  expect(state.isExecuting.value).toBe(false);
  expect(displayedFacts(scenario, state)).toEqual(scenario.file.includes("WorkOrderDetail")
    ? { id: "order-A", revision: 2 } : [expect.objectContaining({ id: "fresh-A" })]);
});

it.each(scenarios.filter((scenario) => scenario.handler !== "handleConfirmReceipt"))("$name：retry成功保留后来编辑或切换的新弹窗", async (scenario) => {
  const operation = vi.fn().mockRejectedValueOnce(new ApiError({ message: "暂不可用", httpStatus: 503 }))
    .mockResolvedValue({ data: { id: "created-A", lines: [{ id: "line-A" }] } });
  const state = createView(scenario.file, { ...refreshApis(), [scenario.api]: operation });
  scenario.prepare(state);
  if (state.availableWorkOrders) state.availableWorkOrders.value = [{ id: "order-A" }];
  if (state.selectedWorkOrderId) state.selectedWorkOrderId.value = "order-A";
  const modal = property(state, modalPath(scenario));
  modal.owner[modal.key] = true;
  await state[scenario.handler](scenario.payload);
  expect(state.canRetry.value).toBe(true);
  scenario.change(state);
  const draft = JSON.stringify(state.createForm ?? state.issueForm ?? state.submitForm ?? state.decideForm ?? state.inspectForm);
  await state.retry();
  expect(modal.owner[modal.key]).toBe(true);
  expect(JSON.stringify(state.createForm ?? state.issueForm ?? state.submitForm ?? state.decideForm ?? state.inspectForm)).toBe(draft);
  if (scenario.file.includes("WorkOrderDetail")) expect(state.workOrder.value.id).toBe("order-B");
});

it("采购列表原收货retry成功不关闭另一订单的收货弹窗", async () => {
  const operation = vi.fn().mockRejectedValueOnce(new ApiError({ message: "暂不可用", httpStatus: 503 }))
    .mockResolvedValue({ data: { id: "created-A", lines: [{ id: "line-A" }] } });
  const state = createView("purchasing/PurchaseOrderListView", { ...refreshApis(), confirmPurchaseReceiptWithServerId: operation });
  state.selectedOrderForReceipt.value = { id: "order-A" };
  state.isReceiptModalOpen.value = true;
  await state.handleConfirmReceipt({ purchaseOrderId: "order-A", lines: [{ receivedQty: "1" }] });
  state.selectedOrderForReceipt.value = { id: "order-B" };
  await state.retry();
  expect(state.isReceiptModalOpen.value).toBe(true);
  expect(state.selectedOrderForReceipt.value.id).toBe("order-B");
});

const childDraftCases = [
  { parent: "purchasing/PurchaseOrderListView", child: "purchasing/ReceiptConfirmView", handler: "handleConfirmReceipt", childHandler: "handleSubmit", api: "confirmPurchaseReceiptWithServerId", modal: "isReceiptModalOpen", childRef: "receiptConfirmView" },
  { parent: "purchasing/PurchaseOrderDetailView", child: "purchasing/ReceiptConfirmView", handler: "handleConfirmReceipt", childHandler: "handleSubmit", api: "confirmPurchaseReceiptWithServerId", modal: "isReceiptConfirmOpen", childRef: "receiptConfirmView" },
  { parent: "sales/SalesOrderDetailView", child: "sales/ShipmentConfirmView", handler: "handleConfirmShipment", childHandler: "submitShipment", api: "confirmShipment", modal: "isShipmentOpen", childRef: "shipmentConfirmView" },
  { parent: "sales/SalesOrderDetailView", child: "sales/ReservationDetailView", handler: "handleReleaseReservation", childHandler: "executeRelease", api: "releaseReservation", modal: "isReservationDetailOpen", childRef: "reservationDetailView" },
];

it.each(childDraftCases.flatMap((scenario) => [false, true].map((edited) => ({ ...scenario, edited }))))("$parent $child 编辑=$edited：使用真实子表单验证retry关闭边界及复读", async (scenario) => {
  const operation = vi.fn().mockRejectedValueOnce(new ApiError({ message: "暂不可用", httpStatus: 503 }))
    .mockResolvedValue({ data: { id: "receipt-A", operationId: "operation-A", lines: [{ id: "receipt-line-A" }] } });
  const order = { id: "order-A", warehouseId: "warehouse-A", lines: [{ id: "line-A", productId: "product-A", uom: "EA", orderedQty: "5", shippedQty: "0", shippingStagedQty: "3", unpickedQty: "3" }], allowedActions: [] };
  const query = vi.fn().mockResolvedValue({ data: { ...order, revision: 2 } });
  const parent = createView(scenario.parent, { ...refreshApis(), [scenario.api]: operation, getPurchaseOrderById: query, getSalesOrderById: query }, { props: { orderId: "order-A", visible: true } });
  parent[scenario.modal].value = true;
  if (parent.order) parent.order.value = order;
  if (parent.selectedOrderForReceipt) parent.selectedOrderForReceipt.value = order;
  const props = vue.reactive({ order, visible: true, submitting: false, canRelease: true });
  const expose = vi.fn();
  const emit = vi.fn();
  const child = createView(scenario.child, {}, { props, expose, emit, liveWatch: true });
  parent[scenario.childRef].value = expose.mock.calls[0][0];
  if (child.receiptLines) {
    child.qualityHoldLocationId.value = "hold-A";
    Object.assign(child.receiptLines.value[0], { arrivedQty: "1", receivedQty: "1", rejectedQty: "0" });
  }
  if (child.activeLineToRelease) child.openReleaseLine(order.lines[0]);
  child[scenario.childHandler]();
  const payload = emit.mock.calls.find(([event]: any[]) => event === "confirm" || event === "release")?.[1];
  expect(payload).toBeDefined();
  await parent[scenario.handler](payload);
  expect(parent.canRetry.value).toBe(true);
  if (scenario.edited) {
    if (child.receiptLines) Object.assign(child.receiptLines.value[0], { arrivedQty: "2", receivedQty: "2" });
    if (child.trackingNo) child.trackingNo.value = "NEW-TRACKING";
    if (child.releaseReason) { child.openReleaseLine(order.lines[0]); child.releaseReason.value = "新释放原因"; }
  }
  const draft = expose.mock.calls[0][0].getDraftSnapshot();
  await parent.retry();
  expect(parent[scenario.modal].value).toBe(scenario.edited);
  expect(parent.canRetry.value).toBe(false);
  // 修改用途：模拟实际模板把复读后的同订单 props 传给子组件，验证 watch 不清除在编草稿。
  props.order = parent.order?.value ?? { ...order, revision: 2 };
  await vue.nextTick();
  expect(expose.mock.calls[0][0].getDraftSnapshot()).toBe(draft);
  if (scenario.child.includes("ReceiptConfirm") || scenario.child.includes("ShipmentConfirm")) {
    props.visible = false;
    await vue.nextTick();
    props.order = { ...order, lines: [{ ...order.lines[0], shippingStagedQty: "4" }] };
    props.visible = true;
    await vue.nextTick();
    if (child.receiptLines) expect(child.receiptLines.value[0].receivedQty).toBe("");
    if (child.editableShipLines) expect(child.editableShipLines.value[0].shipQty).toBe("4");
  }
});

it.each(["start", "pause", "resume", "complete"] as const)("工序%s API 的实际请求体在延时重试时保持首次事件时间", async (action) => {
  vi.useFakeTimers();
  vi.setSystemTime(new Date("2026-10-01T10:00:00Z"));
  const requests: any[] = [];
  const request = vi.fn(async (config: any) => {
    requests.push(JSON.parse(JSON.stringify(config)));
    if (requests.length === 1) throw new ApiError({ message: "暂不可用", httpStatus: 503 });
    return { data: { id: "execution-A" } };
  });
  const apiExports: any = {};
  const source = ts.transpileModule(readFileSync(new URL("../../api/manufacturing.ts", import.meta.url), "utf8"), { compilerOptions: { module: ts.ModuleKind.CommonJS } }).outputText;
  runInNewContext(source, { exports: apiExports, Date, crypto: globalThis.crypto, require: () => ({ default: request }) });
  const apiName = `${action}OperationExecution`;
  const state = createView("manufacturing/OperationExecutionView", { [apiName]: apiExports[apiName] });
  if (action === "pause") {
    state.activeExec.value = { id: "execution-A" }; state.pauseReason.value = "首次原因";
    await state.submitPause();
  } else {
    state.confirmState.targetItem = { id: "execution-A" }; state.confirmState.type = action;
    await state.executeConfirmAction();
  }
  expect(state.canRetry.value).toBe(true);
  vi.setSystemTime(new Date("2026-10-01T11:00:00Z"));
  await state.retry();
  expect(requests).toHaveLength(2);
  expect(requests[1]).toEqual(requests[0]);
  expect(requests[1].data.occurred_at).toBe("2026-10-01T10:00:00.000Z");
});
