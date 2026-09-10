/**
 * MES 制造执行业务 API 服务 (Manufacturing API)
 * 提供 BOM、工艺路线、工单全生命周期、派工排产、工序执行与报工、领退料与成品入库接口
 * 遵循 docs/specs/20-mes 规范契约，纯粹直连真实后端 REST 接口
 * 绝不在前端复制工单与生产状态机，工单状态、派工与执行进度由后端决定
 */

import request, { type ApiResponse } from "../utils/request";
import type { PageResult } from "../types/common";
import type {
  Bom,
  BomQuery,
  Routing,
  RoutingQuery,
  WorkOrder,
  WorkOrderQuery,
  WorkOrderCreatePayload,
  DispatchRecord,
  DispatchQuery,
  DispatchCreatePayload,
  OperationExecution,
  OperationExecutionQuery,
  WorkReportPayload,
  MaterialMovement,
  MaterialMovementQuery,
  MaterialMovementPayload,
  MaterialReturnItem,
  FinishedGoodsReceipt,
  FinishedGoodsReceiptQuery,
  FinishedGoodsReceiptPayload,
} from "../types/manufacturing";

/** 为同一业务命令显式复用幂等键；未传时保留旧调用方兼容行为。 */
function commandHeaders(idempotencyKey?: string) {
  return idempotencyKey ? { "Idempotency-Key": idempotencyKey } : undefined;
}

/** 为没有幂等键的旧调用方生成短唯一片段；页面命令调用通常会直接复用幂等键。 */
function generateRequestFallbackId(): string {
  if (typeof crypto !== "undefined" && typeof crypto.randomUUID === "function") {
    return crypto.randomUUID();
  }
  return `${Date.now().toString(36)}-${Math.random().toString(36).slice(2, 10)}`;
}

/**
 * 分页查询 BOM 清单列表
 * 接口路径：GET /api/boms
 */
export async function getBoms(query: BomQuery = {}): Promise<ApiResponse<PageResult<Bom>>> {
  return await request<PageResult<Bom>>({
    url: "/api/boms",
    method: "GET",
    params: query,
  });
}

/**
 * 查询单条 BOM 详情
 * 接口路径：GET /api/boms/{id}
 */
export async function getBomById(id: string | number): Promise<ApiResponse<Bom>> {
  return await request<Bom>({
    url: `/api/boms/${id}`,
    method: "GET",
  });
}

/**
 * 创建新 BOM
 * 接口路径：POST /api/boms
 */
export async function createBom(payload: Partial<Bom>): Promise<ApiResponse<Bom>> {
  return await request<Bom>({
    url: "/api/boms",
    method: "POST",
    data: payload,
  });
}

/**
 * 删除 BOM
 * 接口路径：DELETE /api/boms/{id}
 */
export async function deleteBom(id: string | number): Promise<ApiResponse<void>> {
  return await request<void>({
    url: `/api/boms/${id}`,
    method: "DELETE",
  });
}

/**
 * 分页查询工艺路线列表
 * 接口路径：GET /api/routings
 */
export async function getRoutings(query: RoutingQuery = {}): Promise<ApiResponse<PageResult<Routing>>> {
  return await request<PageResult<Routing>>({
    url: "/api/routings",
    method: "GET",
    params: query,
  });
}

/**
 * 查询单条工艺路线详情
 * 接口路径：GET /api/routings/{id}
 */
export async function getRoutingById(id: string | number): Promise<ApiResponse<Routing>> {
  return await request<Routing>({
    url: `/api/routings/${id}`,
    method: "GET",
  });
}

/**
 * 创建新工艺路线
 * 接口路径：POST /api/routings
 */
export async function createRouting(payload: Partial<Routing>): Promise<ApiResponse<Routing>> {
  return await request<Routing>({
    url: "/api/routings",
    method: "POST",
    data: payload,
  });
}

/**
 * 删除工艺路线
 * 接口路径：DELETE /api/routings/{id}
 */
export async function deleteRouting(id: string | number): Promise<ApiResponse<void>> {
  return await request<void>({
    url: `/api/routings/${id}`,
    method: "DELETE",
  });
}

/**
 * 分页查询生产工单列表
 * 接口路径：GET /api/work-orders
 */
export async function getWorkOrders(query: WorkOrderQuery = {}): Promise<ApiResponse<PageResult<WorkOrder>>> {
  return await request<PageResult<WorkOrder>>({
    url: "/api/work-orders",
    method: "GET",
    params: query,
  });
}

/**
 * 查询工单聚合详情
 * 接口路径：GET /api/work-orders/{id}
 */
export async function getWorkOrderById(id: string | number): Promise<ApiResponse<WorkOrder>> {
  return await request<WorkOrder>({
    url: `/api/work-orders/${id}`,
    method: "GET",
  });
}
export const getWorkOrderDetail = getWorkOrderById;

/**
 * 创建生产工单（初始为 Draft 未提交状态）
 * 接口路径：POST /api/work-orders
 */
export async function createWorkOrder(payload: WorkOrderCreatePayload): Promise<ApiResponse<WorkOrder>> {
  return await request<WorkOrder>({
    url: "/api/work-orders",
    method: "POST",
    data: payload,
  });
}

/**
 * 提交工单审核 (Draft/Rejected -> PendingApproval)
 * 接口路径：POST /api/work-orders/{id}/submit
 */
export async function submitWorkOrder(id: string | number, idempotencyKey?: string): Promise<ApiResponse<WorkOrder>> {
  return await request<WorkOrder>({
    url: `/api/work-orders/${id}/submit`,
    method: "POST",
    headers: commandHeaders(idempotencyKey),
  });
}

/**
 * 审核下达工单 (PendingApproval -> Released)
 * 接口路径：POST /api/work-orders/{id}/approve
 */
export async function approveWorkOrder(id: string | number, idempotencyKey?: string): Promise<ApiResponse<WorkOrder>> {
  return await request<WorkOrder>({
    url: `/api/work-orders/${id}/approve`,
    method: "POST",
    headers: commandHeaders(idempotencyKey),
  });
}

/**
 * 驳回工单 (PendingApproval -> Rejected)
 * 接口路径：POST /api/work-orders/{id}/reject
 */
export async function rejectWorkOrder(id: string | number, reason?: string, idempotencyKey?: string): Promise<ApiResponse<WorkOrder>> {
  return await request<WorkOrder>({
    url: `/api/work-orders/${id}/reject`,
    method: "POST",
    data: { reason: reason || "" },
    headers: commandHeaders(idempotencyKey),
  });
}

/**
 * 正常完成工单
 * 接口路径：POST /api/work-orders/{id}/complete
 */
export async function completeWorkOrder(
  id: string | number,
  reasonOrPayload?: string | { reason?: string; completionReason?: string }, idempotencyKey?: string
): Promise<ApiResponse<WorkOrder>> {
  const data = typeof reasonOrPayload === "string" ? { reason: reasonOrPayload } : reasonOrPayload || {};
  return await request<WorkOrder>({
    url: `/api/work-orders/${id}/complete`,
    method: "POST",
    data,
    headers: commandHeaders(idempotencyKey),
  });
}

/**
 * 手工结案工单；只记录人工结案原因，不补造工序、报工、质检或库存事实。
 * 接口路径：POST /api/work-orders/{id}/manual-complete
 */
export async function manualCompleteWorkOrder(
  id: string | number,
  completionReason: string,
  idempotencyKey?: string,
): Promise<ApiResponse<WorkOrder>> {
  return await request<WorkOrder>({
    url: `/api/work-orders/${id}/manual-complete`,
    method: "POST",
    data: { completionReason },
    headers: commandHeaders(idempotencyKey),
  });
}

/**
 * 分页查询派工单列表
 * 接口路径：GET /api/dispatch-orders
 */
export async function getDispatchOrders(query: DispatchQuery = {}): Promise<ApiResponse<PageResult<DispatchRecord>>> {
  return await request<PageResult<DispatchRecord>>({
    url: "/api/dispatch-orders",
    method: "GET",
    params: query,
  });
}
export const getDispatches = getDispatchOrders;

/**
 * 下达派工安排
 * 接口路径：POST /api/dispatch-orders
 * 正式字段：work_order_id, operation_id, operator_id, dispatch_qty, device_id
 */
export async function createDispatchOrder(payload: any, idempotencyKey?: string): Promise<ApiResponse<DispatchRecord>> {
  const requestBody = {
    work_order_id: payload.work_order_id || payload.workOrderId,
    operation_id: payload.operation_id || payload.operationId,
    operator_id: payload.operator_id || payload.operatorId,
    dispatch_qty: String(payload.dispatch_qty || payload.dispatchQty || "0"),
    device_id: payload.device_id || payload.deviceId,
  };
  return await request<DispatchRecord>({
    url: "/api/dispatch-orders",
    method: "POST",
    data: requestBody,
    headers: commandHeaders(idempotencyKey),
  });
}
export const createDispatch = createDispatchOrder;

/**
 * 发布派工单
 * 接口路径：POST /api/dispatch-orders/{id}/release
 */
export async function releaseDispatchOrder(id: string | number, idempotencyKey?: string): Promise<ApiResponse<DispatchRecord>> {
  return await request<DispatchRecord>({
    url: `/api/dispatch-orders/${id}/release`,
    method: "POST",
    headers: commandHeaders(idempotencyKey),
  });
}

/**
 * 分页查询工序执行列表
 * 接口路径：GET /api/operation-executions
 */
export async function getOperationExecutions(query: OperationExecutionQuery = {}): Promise<ApiResponse<PageResult<OperationExecution>>> {
  return await request<PageResult<OperationExecution>>({
    url: "/api/operation-executions",
    method: "GET",
    params: query,
  });
}

/**
 * 创建工序执行记录
 * 接口路径：POST /api/operation-executions
 * 正式字段：dispatch_order_id, work_order_id, operation_id, device_id
 */
export async function createOperationExecution(payload: any, idempotencyKey?: string): Promise<ApiResponse<OperationExecution>> {
  const requestBody = {
    dispatch_order_id: payload.dispatch_order_id || payload.dispatchOrderId,
    work_order_id: payload.work_order_id || payload.workOrderId,
    operation_id: payload.operation_id || payload.operationId,
    device_id: payload.device_id || payload.deviceId,
  };
  return await request<OperationExecution>({
    url: "/api/operation-executions",
    method: "POST",
    data: requestBody,
    headers: commandHeaders(idempotencyKey),
  });
}

/**
 * 开始工序执行
 * 接口路径：POST /api/operation-executions/{id}/start
 */
export async function startOperationExecution(id: string | number, idempotencyKey?: string): Promise<ApiResponse<OperationExecution>> {
  return await request<OperationExecution>({
    url: `/api/operation-executions/${id}/start`,
    method: "POST",
    // 修改用途：工序事件接口要求发生时间，页面动作统一提交当前客户端 ISO 时间。
    data: { occurred_at: new Date().toISOString() },
    headers: commandHeaders(idempotencyKey),
  });
}

/**
 * 暂停工序执行
 * 接口路径：POST /api/operation-executions/{id}/pause
 */
export async function pauseOperationExecution(id: string | number, reason?: string, idempotencyKey?: string): Promise<ApiResponse<OperationExecution>> {
  return await request<OperationExecution>({
    url: `/api/operation-executions/${id}/pause`,
    method: "POST",
    // 修改用途：暂停与其他执行事件使用同一时间字段，避免后端按缺失时间拒绝。
    data: { reason: reason || "", occurred_at: new Date().toISOString() },
    headers: commandHeaders(idempotencyKey),
  });
}

/**
 * 恢复工序执行
 * 接口路径：POST /api/operation-executions/{id}/resume
 */
export async function resumeOperationExecution(id: string | number, idempotencyKey?: string): Promise<ApiResponse<OperationExecution>> {
  return await request<OperationExecution>({
    url: `/api/operation-executions/${id}/resume`,
    method: "POST",
    // 修改用途：恢复事件补齐后端必需的发生时间。
    data: { occurred_at: new Date().toISOString() },
    headers: commandHeaders(idempotencyKey),
  });
}

/**
 * 完工工序执行
 * 接口路径：POST /api/operation-executions/{id}/complete
 */
export async function completeOperationExecution(id: string | number, idempotencyKey?: string): Promise<ApiResponse<OperationExecution>> {
  return await request<OperationExecution>({
    url: `/api/operation-executions/${id}/complete`,
    method: "POST",
    // 修改用途：完成事件补齐后端必需的发生时间。
    data: { occurred_at: new Date().toISOString() },
    headers: commandHeaders(idempotencyKey),
  });
}

/**
 * 提交工序报工记录
 * 接口路径：POST /api/work-reports
 * 正式字段：reportNo, operationExecutionId, workOrderId, operationId, reportTime, qualifiedQty, defectQty, remark
 */
export async function createWorkReport(payload: any, idempotencyKey?: string): Promise<ApiResponse<any>> {
  const requestBody = {
    reportNo: payload.reportNo,
    operationExecutionId: payload.operationExecutionId,
    workOrderId: payload.workOrderId,
    operationId: payload.operationId,
    reportTime: payload.reportTime,
    qualifiedQty: String(payload.qualifiedQty),
    defectQty: String(payload.defectQty),
    remark: payload.remark,
  };
  return await request<any>({
    url: "/api/work-reports",
    method: "POST",
    data: requestBody,
    headers: commandHeaders(idempotencyKey),
  });
}
export const submitWorkReport = createWorkReport;

/**
 * 查询指定工单的报工集合
 * 接口路径：GET /api/work-reports?work_order_id={workOrderId}
 */
export async function getWorkReports(workOrderId: string | number): Promise<ApiResponse<any[]>> {
  return await request<any[]>({
    url: "/api/work-reports",
    method: "GET",
    params: { work_order_id: workOrderId },
  });
}

/**
 * 创建生产领料单 Draft
 * 接口路径：POST /api/material-issues
 * 正式字段：issueNo, workOrderId, items[{productId, warehouseId, locationId, quantity}], overageReason
 */
export async function createMaterialIssue(payload: any, idempotencyKey?: string): Promise<ApiResponse<MaterialMovement>> {
  const items = (payload.items || []).map((item: any) => ({
    productId: item.productId,
    warehouseId: item.warehouseId,
    locationId: item.locationId,
    quantity: String(item.quantity || item.issueQty || "0"),
  }));
  // 修改用途：页面不要求用户手填内部单据号；优先用幂等键生成稳定编号，确保网络重试仍复用同一业务请求摘要。
  const issueNo = payload.issueNo || `MI-${idempotencyKey || generateRequestFallbackId()}`;
  const requestBody = {
    issueNo,
    workOrderId: payload.workOrderId,
    items,
    overageReason: payload.overageReason,
  };
  return await request<MaterialMovement>({
    url: "/api/material-issues",
    method: "POST",
    data: requestBody,
    headers: commandHeaders(idempotencyKey),
  });
}
export const issueMaterials = createMaterialIssue;

/**
 * 查询当前租户指定工单的生产领料单集合。
 * 接口路径：GET /api/material-issues?work_order_id={workOrderId}
 */
export async function getMaterialIssues(workOrderId: string | number): Promise<ApiResponse<MaterialMovement[]>> {
  return await request<MaterialMovement[]>({
    url: "/api/material-issues",
    method: "GET",
    params: { work_order_id: workOrderId },
  });
}

/**
 * 确认生产领料出库
 * 接口路径：POST /api/material-issues/{id}/confirm
 */
export async function confirmMaterialIssue(id: string | number, idempotencyKey?: string): Promise<ApiResponse<any>> {
  return await request<any>({
    url: `/api/material-issues/${id}/confirm`,
    method: "POST",
    headers: commandHeaders(idempotencyKey),
  });
}

/**
 * 创建生产退料单 Draft
 * 接口路径：POST /api/material-returns
 * 正式字段：returnNo, workOrderId, items[{productId, warehouseId, locationId, quantity}], reason
 */
export async function createMaterialReturn(payload: any, idempotencyKey?: string): Promise<ApiResponse<MaterialMovement>> {
  const items = (payload.items || []).map((item: any) => ({
    productId: item.productId,
    warehouseId: item.warehouseId,
    locationId: item.locationId,
    quantity: String(item.quantity || item.returnQty || "0"),
  }));
  // 修改用途：退料单号与领料单号保持同样的自动生成和幂等重试语义。
  const returnNo = payload.returnNo || `MR-${idempotencyKey || generateRequestFallbackId()}`;
  const requestBody = {
    returnNo,
    workOrderId: payload.workOrderId,
    items,
    reason: payload.reason,
  };
  return await request<MaterialMovement>({
    url: "/api/material-returns",
    method: "POST",
    data: requestBody,
    headers: commandHeaders(idempotencyKey),
  });
}
export const returnMaterials = createMaterialReturn;

/**
 * 查询当前租户指定工单的生产退料单集合。
 * 接口路径：GET /api/material-returns?work_order_id={workOrderId}
 */
export async function getMaterialReturns(workOrderId: string | number): Promise<ApiResponse<MaterialReturnItem[]>> {
  return await request<MaterialReturnItem[]>({
    url: "/api/material-returns",
    method: "GET",
    params: { work_order_id: workOrderId },
  });
}

/**
 * 确认生产退料入库
 * 接口路径：POST /api/material-returns/{id}/confirm
 */
export async function confirmMaterialReturn(id: string | number, idempotencyKey?: string): Promise<ApiResponse<any>> {
  return await request<any>({
    url: `/api/material-returns/${id}/confirm`,
    method: "POST",
    headers: commandHeaders(idempotencyKey),
  });
}

/**
 * 创建生产报工质检 Draft
 * 接口路径：POST /api/quality-inspections
 * 正式字段：inspectionNo, workReportId, inspectionType, sampleQty
 */
export async function createQualityInspection(payload: any, idempotencyKey?: string): Promise<ApiResponse<any>> {
  const requestBody = {
    inspectionNo: payload.inspectionNo || `INS-${Date.now().toString().slice(-6)}`,
    workReportId: payload.workReportId,
    inspectionType: payload.inspectionType || "FIRST_ARTICLE",
    sampleQty: String(payload.sampleQty || "1"),
  };
  return await request<any>({
    url: "/api/quality-inspections",
    method: "POST",
    data: requestBody,
    headers: commandHeaders(idempotencyKey),
  });
}

/**
 * 提交质检结果 (Draft -> Passed / Failed)
 * 接口路径：POST /api/quality-inspections/{id}/submit
 * 正式字段：qualifiedQty, defectQty, result
 */
export async function submitQualityInspection(id: string | number, payload: any, idempotencyKey?: string): Promise<ApiResponse<any>> {
  const requestBody = {
    qualifiedQty: String(payload.qualifiedQty || "0"),
    defectQty: String(payload.defectQty || "0"),
    result: payload.result || "Passed",
  };
  return await request<any>({
    url: `/api/quality-inspections/${id}/submit`,
    method: "POST",
    data: requestBody,
    headers: commandHeaders(idempotencyKey),
  });
}

/**
 * 关闭 Failed 质检处置结果
 * 接口路径：POST /api/quality-inspections/{id}/close
 * 正式字段：disposition ("ISOLATE" | "SCRAP" | "CLOSE")
 */
export async function closeQualityInspection(id: string | number, disposition: "ISOLATE" | "SCRAP" | "CLOSE", idempotencyKey?: string): Promise<ApiResponse<any>> {
  return await request<any>({
    url: `/api/quality-inspections/${id}/close`,
    method: "POST",
    data: { disposition },
    headers: commandHeaders(idempotencyKey),
  });
}

/**
 * 查询指定工单的质检记录集合
 * 接口路径：GET /api/quality-inspections?work_order_id={workOrderId}
 */
export async function getQualityInspections(workOrderId: string | number): Promise<ApiResponse<any[]>> {
  return await request<any[]>({
    url: "/api/quality-inspections",
    method: "GET",
    params: { work_order_id: workOrderId },
  });
}

/**
 * 分页或按工单查询成品入库记录
 * 接口路径：GET /api/finished-goods-receipts
 */
export async function getFinishedGoodsReceipts(workOrderId: string): Promise<ApiResponse<FinishedGoodsReceipt[]>> {
  return await request<FinishedGoodsReceipt[]>({
    url: "/api/finished-goods-receipts",
    method: "GET",
    params: { work_order_id: workOrderId },
  });
}

/**
 * 创建成品入库单 Draft
 * 接口路径：POST /api/finished-goods-receipts
 * 正式字段：receiptNo, workOrderId, receiptQty, warehouseId, locationId
 */
export async function createFinishedGoodsReceipt(payload: FinishedGoodsReceiptPayload, idempotencyKey?: string): Promise<ApiResponse<FinishedGoodsReceipt>> {
  const requestBody = {
    receiptNo: payload.receiptNo,
    workOrderId: payload.workOrderId,
    receiptQty: String(payload.receiptQty || "0"),
    warehouseId: payload.warehouseId,
    locationId: payload.locationId,
  };
  return await request<FinishedGoodsReceipt>({
    url: "/api/finished-goods-receipts",
    method: "POST",
    data: requestBody,
    headers: commandHeaders(idempotencyKey),
  });
}

/**
 * 确认成品完工入库
 * 接口路径：POST /api/finished-goods-receipts/{id}/confirm
 */
export async function confirmFinishedGoodsReceipt(id: string | number, idempotencyKey?: string): Promise<ApiResponse<any>> {
  return await request<any>({
    url: `/api/finished-goods-receipts/${id}/confirm`,
    method: "POST",
    headers: commandHeaders(idempotencyKey),
  });
}
