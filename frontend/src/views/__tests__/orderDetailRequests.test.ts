import { readFileSync } from "node:fs";
import { runInNewContext } from "node:vm";
import { compileScript, parse } from "@vue/compiler-sfc";
import ts from "typescript";
import * as vue from "vue";
import { describe, expect, it, vi } from "vitest";
import { useCommand } from "../../composables/useCommand";
import { ApiError } from "../../utils/request";
import { stringCompare, stringSub } from "../../types/inventory";

type DetailKind = "purchasing" | "sales";

/** 用途：创建可手动完成的请求；无入参，返回 Promise 和成功/失败回调以控制响应顺序。 */
function deferred() {
  let resolve!: (value: any) => void;
  let reject!: (error: any) => void;
  const promise = new Promise<any>((success, failure) => { resolve = success; reject = failure; });
  return { promise, resolve, reject };
}

/**
 * 用途：执行真实详情组件的 script setup，验证异步请求和命令行为，无需浏览器 DOM。
 * 入参：领域、API 替身和初始可见性；出参：组件状态、props 和 props 监听回调。
 * 流程：由 Vue 编译实际 SFC，再由 TypeScript 转为 CommonJS，只替换网络与 UI 宿主依赖。
 */
function createDetail(kind: DetailKind, api: Record<string, any>, visible = true) {
  const filename = kind === "purchasing" ? "PurchaseOrderDetailView.vue" : "SalesOrderDetailView.vue";
  const path = new URL(`../${kind}/${filename}`, import.meta.url);
  const { descriptor } = parse(readFileSync(path, "utf8"));
  const script = compileScript(descriptor, { id: filename });
  // 修改用途：对齐项目 ES2020 编译目标，保留 Set 迭代语义，不能用旧 ES5 转换误判实际组件。
  const source = ts.transpileModule(script.content, { compilerOptions: { module: ts.ModuleKind.CommonJS, target: ts.ScriptTarget.ES2020 } }).outputText;
  const watchers: Array<(...args: any[]) => void> = [];
  const componentExports: any = {};
  runInNewContext(source, {
    exports: componentExports,
    console: { error: vi.fn() },
    require: (name: string) => {
      if (name === "vue") return { ...vue, watch: (_source: unknown, callback: (...args: any[]) => void) => watchers.push(callback) };
      if (name === "vue-router") return { useRouter: () => ({ push: vi.fn() }) };
      if (name.includes("useCommand")) return { useCommand };
      if (name.includes("usePermission")) return { usePermission: () => ({ hasPermission: () => true }) };
      if (name.includes("stores/auth")) return { useAuthStore: () => ({ permissions: ["sales:pick:confirm"] }) };
      if (name.includes("actionGuard")) return { isActionAllowed: () => api.allowPick === true };
      if (name.includes("utils/request")) return { ApiError };
      if (name.includes("types/inventory")) return { stringCompare, stringSub };
      if (name === "element-plus") return { ElMessage: { success: vi.fn(), error: vi.fn(), warning: vi.fn() }, ElMessageBox: { confirm: vi.fn().mockRejectedValue("cancel") } };
      if (name.includes("/api/")) return api;
      return {};
    },
  });
  const props = { orderId: "A", visible };
  const state = componentExports.default.setup(props, { expose: vi.fn(), emit: vi.fn() });
  return { state, props, watchers };
}

/** 用途：生成最小订单响应；入参为订单 ID，返回含明细及动作的服务端事实。 */
function orderResponse(id: string) {
  return { data: { id, warehouseId: id, lines: [], allowedActions: [] } };
}

/** 用途：推进异步请求微任务；无入参、无返回值，保证库位请求已开始以便控制完成顺序。 */
async function flushRequests() {
  await Promise.resolve();
  await Promise.resolve();
}

/** 用途：构造现行采购 DTO；入参为订单标识，返回仅含接口确实提供的 ID 与收货数量。 */
function purchaseDto(id: string) {
  return { data: {
    id, supplierId: `supplier-${id}`, status: "Completed", allowedActions: [],
    lines: [{ id: `line-${id}`, productId: `product-${id}`, orderedQty: "352.000000",
      receivedQty: "339.000000", pendingQty: "13.000000", uom: "PCS" }],
  } };
}

describe("采购详情现行 DTO 映射", () => {
  it("从真实主数据明细补名称，保留339接收和13待收，不把缺失履约事实补成零", async () => {
    const response = purchaseDto("A");
    response.data.lines.push({ ...response.data.lines[0], id: "second-line-A" });
    const supplier = vi.fn().mockResolvedValue({ data: { supplierCode: "SUP-HH007", supplierName: "恒华精密铝业有限公司" } });
    const product = vi.fn().mockResolvedValue({ data: { sku: "AL6061-824618", name: "6061铝合金支架坯件", spec: "82×46×18mm" } });
    const { state } = createDetail("purchasing", {
      getPurchaseOrderById: vi.fn().mockResolvedValue(response), getSupplierById: supplier, getProductById: product,
    });

    await state.fetchDetail();

    expect(supplier).toHaveBeenCalledWith("supplier-A");
    expect(product).toHaveBeenCalledOnce();
    expect(product).toHaveBeenCalledWith("product-A");
    expect(state.order.value.supplierName).toBe("恒华精密铝业有限公司");
    expect(state.order.value.supplierCode).toBe("SUP-HH007");
    for (const line of state.order.value.lines) {
      expect(line).toMatchObject({ sku: "AL6061-824618", productName: "6061铝合金支架坯件", spec: "82×46×18mm",
        receivedQty: "339.000000", pendingQty: "13.000000" });
      for (const field of ["arrivedQty", "rejectedQty", "qualifiedQty", "releaseExecutedQty", "putawayQty"]) {
        expect(line[field]).toBeUndefined();
      }
    }
  });

  it("主数据不可读取时保留已授权订单和真实ID，不伪造名称或收货数量", async () => {
    const { state } = createDetail("purchasing", {
      getPurchaseOrderById: vi.fn().mockResolvedValue(purchaseDto("A")),
      getSupplierById: vi.fn().mockRejectedValue(new ApiError({ message: "暂不可用", httpStatus: 503 })),
      getProductById: vi.fn().mockRejectedValue(new ApiError({ message: "无权限", httpStatus: 403 })),
    });

    await state.fetchDetail();

    expect(state.viewState.value).toBe("ready");
    expect(state.order.value.supplierName).toBe("supplier-A");
    expect(state.order.value.supplierCode).toBeUndefined();
    expect(state.order.value.lines[0]).toMatchObject({ sku: "product-A", receivedQty: "339.000000", pendingQty: "13.000000" });
  });

  it.each(["切单", "关闭"])("主数据慢响应在%s后不能回写旧采购详情", async (change) => {
    const slowSupplier = deferred();
    const slowProduct = deferred();
    const supplier = vi.fn().mockImplementation((id) => id === "supplier-A" ? slowSupplier.promise
      : Promise.resolve({ data: { supplierName: "供应商B" } }));
    const product = vi.fn().mockImplementation((id) => id === "product-A" ? slowProduct.promise
      : Promise.resolve({ data: { sku: "SKU-B", name: "物料B" } }));
    const { state, props, watchers } = createDetail("purchasing", {
      getPurchaseOrderById: vi.fn().mockImplementation((id) => Promise.resolve(purchaseDto(id))),
      getSupplierById: supplier, getProductById: product,
    });
    const oldLoad = state.fetchDetail();
    await flushRequests();
    expect(supplier).toHaveBeenCalledWith("supplier-A");
    if (change === "切单") {
      props.orderId = "B";
      await state.fetchDetail();
    } else {
      props.visible = false;
      watchers[0](["A", false]);
    }

    slowSupplier.resolve({ data: { supplierName: "供应商A" } });
    slowProduct.resolve({ data: { sku: "SKU-A", name: "物料A" } });
    await oldLoad;

    if (change === "切单") {
      expect(state.order.value.id).toBe("B");
      expect(state.order.value.supplierName).toBe("供应商B");
      expect(state.order.value.lines[0].sku).toBe("SKU-B");
      expect(state.viewState.value).toBe("ready");
    } else {
      expect(state.order.value).toBeNull();
    }
  });
});

describe.each<DetailKind>(["purchasing", "sales"])("%s 详情并发请求", (kind) => {
  it("新订单先完成时，旧订单成功响应不能覆盖详情", async () => {
    const first = deferred();
    const second = deferred();
    const query = vi.fn().mockReturnValueOnce(first.promise).mockReturnValueOnce(second.promise);
    const { state, props } = createDetail(kind, { getPurchaseOrderById: query, getSalesOrderById: query });
    const firstLoad = state.fetchDetail();
    props.orderId = "B";
    const secondLoad = state.fetchDetail();
    second.resolve(orderResponse("B"));
    await secondLoad;
    first.resolve(orderResponse("A"));
    await firstLoad;
    expect(state.order.value.id).toBe("B");
    expect(state.viewState.value).toBe("ready");
  });

  it("旧请求失败不会提前结束新请求的加载状态", async () => {
    const first = deferred();
    const second = deferred();
    const query = vi.fn().mockReturnValueOnce(first.promise).mockReturnValueOnce(second.promise);
    const { state, props } = createDetail(kind, { getPurchaseOrderById: query, getSalesOrderById: query });
    const firstLoad = state.fetchDetail();
    props.orderId = "B";
    const secondLoad = state.fetchDetail();
    first.reject(new ApiError({ message: "旧请求失败", httpStatus: 404 }));
    await firstLoad;
    expect(state.viewState.value).toBe("loading");
    expect(state.errorMessage.value).toBe("");
    second.resolve(orderResponse("B"));
    await secondLoad;
    expect(state.order.value.id).toBe("B");
  });

  it("同一订单重复刷新也只接受最新响应", async () => {
    const first = deferred();
    const second = deferred();
    const query = vi.fn().mockReturnValueOnce(first.promise).mockReturnValueOnce(second.promise);
    const { state } = createDetail(kind, { getPurchaseOrderById: query, getSalesOrderById: query });
    const firstLoad = state.fetchDetail();
    const secondLoad = state.fetchDetail();
    second.resolve({ data: { ...orderResponse("A").data, revision: 2 } });
    await secondLoad;
    first.resolve({ data: { ...orderResponse("A").data, revision: 1 } });
    await firstLoad;
    expect(state.order.value.revision).toBe(2);
  });

  it("抽屉关闭后在途成功响应不再更新页面", async () => {
    const request = deferred();
    const query = vi.fn().mockReturnValue(request.promise);
    const { state, props, watchers } = createDetail(kind, { getPurchaseOrderById: query, getSalesOrderById: query });
    const load = state.fetchDetail();
    props.visible = false;
    watchers[0](["A", false]);
    request.resolve(orderResponse("A"));
    await load;
    expect(state.order.value).toBeNull();
  });

  it.each(["submit", "approve"])("%s 同键重试仍作用于首次选中的订单", async (action) => {
    const command = vi.fn().mockRejectedValueOnce(new ApiError({ message: "暂不可用", httpStatus: 503 })).mockResolvedValue({});
    const { state } = createDetail(kind, {
      submitPurchaseOrder: command, submitSalesOrder: command,
      approvePurchaseOrder: command, approveSalesOrder: command,
    }, false);
    state.order.value = orderResponse("A").data;
    const handler = action === "submit"
      ? (kind === "purchasing" ? "handleSubmitOrder" : "handleSubmit")
      : (kind === "purchasing" ? "handleApproveOrder" : "handleApprove");
    await state[handler]();
    state.order.value = orderResponse("B").data;
    await state.retry();
    expect(command.mock.calls[0][0]).toBe("A");
    expect(command.mock.calls[1][0]).toBe("A");
    expect(command.mock.calls[1][1]).toBe(command.mock.calls[0][1]);
  });

  it("同键重试保留首次人工完成原因", async () => {
    const command = vi.fn().mockRejectedValueOnce(new ApiError({ message: "暂不可用", httpStatus: 503 })).mockResolvedValue({});
    const { state } = createDetail(kind, { completePurchaseOrder: command, completeSalesOrder: command }, false);
    state.order.value = orderResponse("A").data;
    state.manualCompleteReason.value = "首次原因";
    await state[kind === "purchasing" ? "handleConfirmManualComplete" : "executeManualComplete"]();
    state.manualCompleteReason.value = "修改后的原因";
    state.order.value = orderResponse("B").data;
    await state.retry();
    expect(command.mock.calls[1][0]).toBe("A");
    expect(command.mock.calls[1][1]).toEqual({ completionReason: "首次原因" });
    expect(command.mock.calls[1][2]).toBe(command.mock.calls[0][2]);
  });
});

it("销售旧库位响应不能覆盖新订单库位或提前结束加载", async () => {
  const oldLocations = deferred();
  const newLocations = deferred();
  const query = vi.fn().mockImplementation((id) => Promise.resolve(orderResponse(id)));
  const locations = vi.fn().mockImplementation(({ warehouseId }) => warehouseId === "A" ? oldLocations.promise : newLocations.promise);
  const { state, props } = createDetail("sales", { allowPick: true, getSalesOrderById: query, getLocations: locations });
  const firstLoad = state.fetchDetail();
  await flushRequests();
  props.orderId = "B";
  const secondLoad = state.fetchDetail();
  await flushRequests();
  oldLocations.resolve({ data: { records: [{ id: "A-location", warehouseId: "A", type: "Storage", status: "ACTIVE" }] } });
  await firstLoad;
  expect(state.viewState.value).toBe("loading");
  expect(state.sourceLocations.value).toEqual([]);
  newLocations.resolve({ data: { records: [{ id: "B-location", warehouseId: "B", type: "Storage", status: "ACTIVE" }] } });
  await secondLoad;
  expect(state.sourceLocations.value.map((location: any) => location.id)).toEqual(["B-location"]);
  expect(state.viewState.value).toBe("ready");
});

it.each([
  ["purchasing", "handleConfirmReceipt", "confirmPurchaseReceiptWithServerId"],
  ["sales", "handleConfirmShipment", "confirmShipment"],
] as const)("%s 嵌套载荷在同键重试时保持首次值", async (kind, handler, apiName) => {
  const command = vi.fn().mockRejectedValueOnce(new ApiError({ message: "暂不可用", httpStatus: 503 })).mockResolvedValue({ data: { id: "receipt", operationId: "operation", lines: [{ id: "receipt-line" }] } });
  const { state } = createDetail(kind, { [apiName]: command }, false);
  const payload = { salesOrderId: "A", purchaseOrderId: "A", lines: [{ qty: "1" }] };
  await state[handler](payload);
  payload.lines[0].qty = "2";
  await state.retry();
  expect(command.mock.calls[1][0].lines[0].qty).toBe("1");
  expect(command.mock.calls[1][1]).toBe(command.mock.calls[0][1]);
});

it.each(["pick", "return"])("销售 %s 同键重试保持首次数量与库位", async (action) => {
  const command = vi.fn().mockRejectedValueOnce(new ApiError({ message: "暂不可用", httpStatus: 503 })).mockResolvedValue({ data: { operationId: "operation" } });
  const { state } = createDetail("sales", { confirmDirectPick: command, returnPick: command }, false);
  state.order.value = orderResponse("A").data;
  const line = { id: "line-A", productId: "product-A", uom: "EA" };
  if (action === "pick") {
    state.selectedLineForPick.value = line;
    state.pickQtyInput.value = "1";
    state.pickSourceLocationId.value = "source-A";
    state.sourceLocations.value = [{ id: "source-A", warehouseId: "A" }];
    state.shippingLocations.value = [{ id: "shipping-A", warehouseId: "A", type: "ShippingStaging", status: "ACTIVE" }];
    state.pickBalances.value = [{ locationId: "source-A", availableQty: "5" }];
    await state.submitPick();
    state.pickQtyInput.value = "2";
    state.shippingLocationId.value = "shipping-B";
  } else {
    state.selectedLineForReturn.value = line;
    state.returnQtyInput.value = "1";
    state.returnToLocationId.value = "source-A";
    await state.submitReturnPick();
    state.returnQtyInput.value = "2";
    state.returnToLocationId.value = "source-B";
  }
  expect(command).toHaveBeenCalledOnce();
  await state.retry();
  expect(command.mock.calls[1][0]).toEqual(command.mock.calls[0][0]);
  expect(command.mock.calls[1][0].lines[0][action === "pick" ? "pickedQty" : "returnQty"]).toBe("1");
  expect(command.mock.calls[1][1]).toBe(command.mock.calls[0][1]);
});

it("销售预留释放同键重试深复制首次载荷", async () => {
  const command = vi.fn().mockRejectedValueOnce(new ApiError({ message: "暂不可用", httpStatus: 503 })).mockResolvedValue({});
  const { state } = createDetail("sales", { releaseReservation: command }, false);
  const payload = { salesOrderId: "A", lines: [{ qty: "1" }] };
  await state.handleReleaseReservation(payload);
  payload.salesOrderId = "B";
  payload.lines[0].qty = "2";
  await state.retry();
  expect(command.mock.calls[1][0]).toBe("A");
  expect(command.mock.calls[1][1].lines[0].qty).toBe("1");
  expect(command.mock.calls[1][2]).toBe(command.mock.calls[0][2]);
});

it("销售切换拣货物料后，旧余额响应不能覆盖新物料余额", async () => {
  const first = deferred();
  const second = deferred();
  const query = vi.fn().mockReturnValueOnce(first.promise).mockReturnValueOnce(second.promise);
  const { state } = createDetail("sales", { getInventoryBalances: query });
  state.isPickModalOpen.value = true;
  state.selectedLineForPick.value = { id: "line-A", productId: "product-A" };
  const firstLoad = state.loadPickBalances(state.selectedLineForPick.value);
  state.selectedLineForPick.value = { id: "line-B", productId: "product-B" };
  const secondLoad = state.loadPickBalances(state.selectedLineForPick.value);
  second.resolve({ data: { records: [{ productId: "product-B", availableQty: "2" }] } });
  await secondLoad;
  first.resolve({ data: { records: [{ productId: "product-A", availableQty: "1" }] } });
  await firstLoad;
  expect(state.pickBalances.value.map((balance: any) => balance.productId)).toEqual(["product-B"]);
});

it("销售旧余额请求失败不得清除新余额、错误状态或加载标记", async () => {
  const first = deferred();
  const second = deferred();
  const query = vi.fn().mockReturnValueOnce(first.promise).mockReturnValueOnce(second.promise);
  const { state } = createDetail("sales", { getInventoryBalances: query });
  state.isPickModalOpen.value = true;
  state.selectedLineForPick.value = { id: "line-A", productId: "product-A" };
  const firstLoad = state.loadPickBalances(state.selectedLineForPick.value);
  state.selectedLineForPick.value = { id: "line-B", productId: "product-B" };
  const secondLoad = state.loadPickBalances(state.selectedLineForPick.value);
  first.reject(new ApiError({ message: "旧余额请求失败", httpStatus: 503 }));
  await firstLoad;
  expect(state.pickSourcesLoading.value).toBe(true);
  expect(state.pickSourceError.value).toBe("");
  second.resolve({ data: { records: [{ productId: "product-B", availableQty: "2" }] } });
  await secondLoad;
  expect(state.pickSourcesLoading.value).toBe(false);
});

it.each(["close", "switch-order"])("销售 %s 后在途余额响应不再更新拣货表单", async (action) => {
  const pending = deferred();
  const { state, props } = createDetail("sales", { getInventoryBalances: vi.fn().mockReturnValue(pending.promise) });
  state.isPickModalOpen.value = true;
  state.selectedLineForPick.value = { id: "line-A", productId: "product-A" };
  const load = state.loadPickBalances(state.selectedLineForPick.value);
  if (action === "close") state.isPickModalOpen.value = false;
  else props.orderId = "B";
  pending.resolve({ data: { records: [{ productId: "product-A", availableQty: "1" }] } });
  await load;
  expect(state.pickBalances.value).toEqual([]);
});

const detailCommands = [
  { kind: "purchasing", handler: "handleSubmitOrder", api: "submitPurchaseOrder" },
  { kind: "purchasing", handler: "handleApproveOrder", api: "approvePurchaseOrder" },
  { kind: "purchasing", handler: "handleConfirmManualComplete", api: "completePurchaseOrder", modal: "isCompleteDialogOpen" },
  { kind: "purchasing", handler: "handleConfirmReceipt", api: "confirmPurchaseReceiptWithServerId", modal: "isReceiptConfirmOpen" },
  { kind: "sales", handler: "handleSubmit", api: "submitSalesOrder" },
  { kind: "sales", handler: "handleApprove", api: "approveSalesOrder" },
  { kind: "sales", handler: "executeManualComplete", api: "completeSalesOrder", modal: "isManualCompleteOpen" },
  { kind: "sales", handler: "submitPick", api: "confirmDirectPick", modal: "isPickModalOpen" },
  { kind: "sales", handler: "submitReturnPick", api: "returnPick", modal: "isReturnModalOpen" },
  { kind: "sales", handler: "handleConfirmShipment", api: "confirmShipment", modal: "isShipmentOpen" },
  { kind: "sales", handler: "handleReleaseReservation", api: "releaseReservation", modal: "isReservationDetailOpen" },
] as const;

/**
 * 用途：准备实际订单命令与可复读详情；入参为命令场景，返回页面、props、查询替身和原始载荷。
 * 流程：首次命令抛出 503，retry 返回真实标识形态，页面查询返回更高版本以确认成功刷新。
 */
function retryDetail(scenario: typeof detailCommands[number]) {
  const operation = vi.fn().mockRejectedValueOnce(new ApiError({ message: "暂不可用", httpStatus: 503 }))
    .mockResolvedValue({ data: { id: "receipt-A", operationId: "operation-A", lines: [{ id: "receipt-line-A" }] } });
  const query = vi.fn().mockResolvedValue({ data: { ...orderResponse("A").data, revision: 2 } });
  const view = createDetail(scenario.kind, { [scenario.api]: operation, getPurchaseOrderById: query, getSalesOrderById: query });
  const { state } = view;
  state.order.value = { ...orderResponse("A").data, revision: 1 };
  state.manualCompleteReason.value = "首次原因";
  if ("modal" in scenario) state[scenario.modal].value = true;
  if (scenario.handler === "submitPick") {
    state.selectedLineForPick.value = { id: "line-A", productId: "product-A", uom: "EA" };
    state.pickQtyInput.value = "1"; state.pickSourceLocationId.value = "source-A";
    state.sourceLocations.value = [{ id: "source-A", warehouseId: "A" }];
    state.shippingLocations.value = [{ id: "shipping-A", warehouseId: "A", type: "ShippingStaging", status: "ACTIVE" }];
    state.pickBalances.value = [{ locationId: "source-A", availableQty: "5" }];
  }
  if (scenario.handler === "submitReturnPick") {
    state.selectedLineForReturn.value = { id: "line-A", productId: "product-A", uom: "EA" };
    state.returnQtyInput.value = "1"; state.returnToLocationId.value = "source-A";
  }
  return { ...view, operation, query, payload: { purchaseOrderId: "A", salesOrderId: "A", lines: [{ qty: "1" }] } };
}

it.each(detailCommands)("$kind $handler：retry成功复读详情并关闭原弹窗", async (scenario) => {
  const { state, operation, query, payload } = retryDetail(scenario);
  await state[scenario.handler](payload);
  expect(state.canRetry.value).toBe(true);
  expect(query).not.toHaveBeenCalled();
  if ("modal" in scenario) expect(state[scenario.modal].value).toBe(true);
  await state.retry();
  expect(operation).toHaveBeenCalledTimes(2);
  expect(query).toHaveBeenCalledOnce();
  expect(query).toHaveBeenCalledWith("A");
  expect(state.order.value.revision).toBe(2);
  expect(state.canRetry.value).toBe(false);
  if ("modal" in scenario) expect(state[scenario.modal].value).toBe(false);
});

it.each(detailCommands)("$kind $handler：原订单retry成功不能刷新或关闭后来选择的订单", async (scenario) => {
  const { state, props, query, payload } = retryDetail(scenario);
  await state[scenario.handler](payload);
  props.orderId = "B";
  state.order.value = { ...orderResponse("B").data, revision: 7 };
  await state.retry();
  expect(query).not.toHaveBeenCalled();
  expect(state.order.value.id).toBe("B");
  expect(state.order.value.revision).toBe(7);
  if ("modal" in scenario) expect(state[scenario.modal].value).toBe(true);
});

it.each(detailCommands.filter((scenario) => "modal" in scenario))("$kind $handler：同订单retry成功保留编辑后的新稿", async (scenario) => {
  const { state, payload } = retryDetail(scenario);
  await state[scenario.handler](payload);
  state.manualCompleteReason.value = "新原因";
  if (scenario.handler === "submitPick") state.pickQtyInput.value = "2";
  if (scenario.handler === "submitReturnPick") state.returnQtyInput.value = "2";
  payload.lines[0].qty = "2";
  await state.retry();
  if ("modal" in scenario) expect(state[scenario.modal].value).toBe(true);
  expect(state.manualCompleteReason.value).toBe("新原因");
  if (scenario.handler === "submitPick") expect(state.pickQtyInput.value).toBe("2");
  if (scenario.handler === "submitReturnPick") expect(state.returnQtyInput.value).toBe("2");
});
