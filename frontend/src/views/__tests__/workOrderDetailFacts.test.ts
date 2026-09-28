import { readFileSync } from "node:fs";
import { runInNewContext } from "node:vm";
import { compileScript, parse } from "@vue/compiler-sfc";
import ts from "typescript";
import * as vue from "vue";
import { describe, expect, it, vi } from "vitest";
import { useCommand } from "../../composables/useCommand";

/** 用途：创建可控制完成顺序的请求；无入参，返回 Promise 与 resolve，以固定慢目录响应交错。 */
function deferred() {
  let resolve!: (value: any) => void;
  const promise = new Promise<any>(done => { resolve = done; });
  return { promise, resolve };
}

/**
 * 用途：执行真实工单详情 SFC 的 setup；入参为网络替身与目录查看权限，返回响应式页面状态及 props。
 * 流程：沿用现有 SFC/TypeScript/VM 行为测试，不安装 DOM 框架，只替换网络、权限和页面宿主。
 */
function createDetail(overrides: Record<string, any> = {}, canReadCatalog = true, canReadMaterials = true) {
  const filename = new URL("../manufacturing/WorkOrderDetailView.vue", import.meta.url);
  const script = compileScript(parse(readFileSync(filename, "utf8")).descriptor, { id: "work-order-detail" });
  const source = ts.transpileModule(script.content, { compilerOptions: { module: ts.ModuleKind.CommonJS } }).outputText;
  const api = {
    getWorkOrderDetail: vi.fn(async (id: string) => ({ data: { id, productId: `product-${id}`, routingId: `routing-${id}`,
      status: "Completed", plannedQty: 143, reportedQty: 143, qualifiedQty: 137, defectQty: 6, receivedQty: 137 } })),
    getDispatchOrders: vi.fn(async () => ({ data: { records: [{ id: "dispatch", operationId: "operation", deviceId: "device-id",
      operationName: null, operationNo: null, deviceName: null, deviceCode: null, dispatchQty: 143, status: "Completed" }] } })),
    getOperationExecutions: vi.fn(async () => ({ data: { records: [{ id: "execution", operationId: "operation", operationName: null, operationNo: null }] } })),
    getFinishedGoodsReceipts: vi.fn(async () => ({ data: [] })),
    getMaterialIssues: vi.fn(async () => ({ data: [{ id: "issue", items: [{ id: "issue-line", issueQty: 293 }] }] })),
    getMaterialReturns: vi.fn(async () => ({ data: [{ id: "return", items: [{ id: "return-line", returnQty: 7 }] }] })),
    getProductById: vi.fn(async () => ({ data: { name: "HV-08液压阀安装支架组件", sku: "ZJ-HV08-R2", spec: "左右支架配套" } })),
    getRoutingById: vi.fn(async () => ({ data: { routingCode: "RT-HV08", operations: [{ id: "operation", operationNo: 20, operationName: "配对装配与扭矩复核" }] } })),
    ...overrides,
  };
  const exports: any = {};
  runInNewContext(source, {
    exports,
    require: (name: string) => {
      if (name === "vue") return { ...vue, onMounted: () => {}, watch: () => {} };
      if (name === "vue-router") return { useRoute: () => ({ query: {} }), useRouter: () => ({ push: vi.fn() }) };
      if (name.includes("useCommand")) return { useCommand };
      if (name.includes("usePermission")) return { usePermission: () => ({ hasPermission: () => canReadCatalog, hasAnyPermission: () => canReadMaterials }) };
      if (name.includes("actionGuard")) return { isActionAllowed: () => true, getActionDisabledReason: () => undefined };
      if (name === "element-plus") return { ElMessage: { error: vi.fn() } };
      if (name.includes("/api/")) return api;
      return {};
    },
  });
  const props = { id: "A" };
  const state = exports.default.setup(props, { expose: vi.fn(), emit: vi.fn() });
  return { state, props, api };
}

describe("工单详情真实查询字段", () => {
  it("补读产品和工序名称，展示真实领293退7且保持已完成工单数量", async () => {
    const { state, api } = createDetail();
    await state.loadAllData();
    expect(state.workOrder.value).toMatchObject({ status: "Completed", plannedQty: 143, reportedQty: 143,
      qualifiedQty: 137, defectQty: 6, receivedQty: 137, productName: "HV-08液压阀安装支架组件", productCode: "ZJ-HV08-R2" });
    expect(api.getProductById).toHaveBeenCalledWith("product-A");
    expect(api.getRoutingById).toHaveBeenCalledWith("routing-A");
    expect(api.getMaterialIssues).toHaveBeenCalledWith("A");
    expect(api.getMaterialReturns).toHaveBeenCalledWith("A");
    expect(state.materialIssues.value[0].items[0].issueQty).toBe(293);
    expect(state.materialReturns.value[0].items[0].returnQty).toBe(7);
    expect(state.getOperationLabel(state.dispatchOrders.value[0])).toBe("配对装配与扭矩复核 (#20)");
    expect(state.getOperationLabel(state.executions.value[0])).toBe("配对装配与扭矩复核 (#20)");
    expect(state.getDeviceLabel(state.dispatchOrders.value[0])).toBe("device-id");
    expect(state.getDeviceLabel({ deviceId: null })).toBe("未绑定设备");
  });

  it("缺少目录权限时不发额外查询，仍保留真实产品、工序和设备 ID", async () => {
    const { state, api } = createDetail({}, false);
    await state.loadAllData();
    expect(api.getProductById).not.toHaveBeenCalled();
    expect(api.getRoutingById).not.toHaveBeenCalled();
    expect(state.workOrder.value.productId).toBe("product-A");
    expect(state.getOperationLabel(state.dispatchOrders.value[0])).toBe("operation");
    expect(state.getDeviceLabel(state.dispatchOrders.value[0])).toBe("device-id");
    expect(state.viewState.value).toBe("ready");
  });

  it("目录不可用时保留业务事实，领退料查询失败明确呈现错误", async () => {
    const { state } = createDetail({ getProductById: vi.fn().mockRejectedValue(new Error("目录不可用")) });
    await state.loadAllData();
    expect(state.workOrder.value.productId).toBe("product-A");
    expect(state.materialIssues.value).toHaveLength(1);
    expect(state.viewState.value).toBe("ready");
    const failed = createDetail({ getMaterialIssues: vi.fn().mockRejectedValue(new Error("领料查询失败")) });
    await failed.state.loadAllData();
    expect(failed.state.viewState.value).toBe("error");
    expect(failed.state.errorMessage.value).toBe("领料查询失败");
  });

  it("无领退料权限时不调用两个 GET 且隐藏页签，合法工单仍可查看", async () => {
    const { state, api } = createDetail({}, true, false);
    await state.loadAllData();
    expect(api.getMaterialIssues).not.toHaveBeenCalled();
    expect(api.getMaterialReturns).not.toHaveBeenCalled();
    expect(state.canReadMaterials.value).toBe(false);
    expect(state.workOrder.value).toMatchObject({ id: "A", status: "Completed", plannedQty: 143, receivedQty: 137 });
    expect(state.viewState.value).toBe("ready");
  });

  it.each(["切换工单", "同单刷新"])("%s后旧目录响应不能覆盖最新工单及关联事实", async mode => {
    const firstCatalog = deferred();
    const oldReadStarted = deferred();
    const productQuery = vi.fn().mockImplementationOnce(() => { oldReadStarted.resolve(undefined); return firstCatalog.promise; })
      .mockResolvedValue({ data: { name: "最新产品" } });
    const { state, props } = createDetail({ getProductById: productQuery });
    const oldLoad = state.loadAllData();
    await oldReadStarted.promise;
    if (mode === "切换工单") props.id = "B";
    await state.loadAllData();
    firstCatalog.resolve({ data: { name: "旧产品" } });
    await oldLoad;
    expect(state.workOrder.value.id).toBe(props.id);
    expect(state.workOrder.value.productName).toBe("最新产品");
    expect(state.materialIssues.value[0].items[0].issueQty).toBe(293);
    expect(state.viewState.value).toBe("ready");
  });

  it("旧工单主响应迟到时不开始补读或覆盖新工单", async () => {
    const firstOrder = deferred();
    const query = vi.fn().mockReturnValueOnce(firstOrder.promise).mockResolvedValue({ data: { id: "B", productId: "product-B", routingId: "routing-B" } });
    const { state, props, api } = createDetail({ getWorkOrderDetail: query });
    const oldLoad = state.loadAllData();
    props.id = "B";
    await state.loadAllData();
    firstOrder.resolve({ data: { id: "A", productId: "product-A", routingId: "routing-A" } });
    await oldLoad;
    expect(state.workOrder.value.id).toBe("B");
    expect(api.getProductById).toHaveBeenCalledTimes(1);
    expect(api.getMaterialIssues).toHaveBeenCalledWith("B");
  });
});
