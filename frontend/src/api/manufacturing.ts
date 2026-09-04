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
export async function submitWorkOrder(id: string | number): Promise<ApiResponse<WorkOrder>> {
  return await request<WorkOrder>({
    url: `/api/work-orders/${id}/submit`,
    method: "POST",
  });
}

/**
 * 审核下达工单 (PendingApproval -> Released)
 * 接口路径：POST /api/work-orders/{id}/approve
 */
export async function approveWorkOrder(id: string | number): Promise<ApiResponse<WorkOrder>> {
  return await request<WorkOrder>({
    url: `/api/work-orders/${id}/approve`,
    method: "POST",
  });
}

/**
 * 驳回工单 (PendingApproval -> Rejected)
 * 接口路径：POST /api/work-orders/{id}/reject
 */
export async function rejectWorkOrder(id: string | number, reason?: string): Promise<ApiResponse<WorkOrder>> {
  return await request<WorkOrder>({
    url: `/api/work-orders/${id}/reject`,
    method: "POST",
    data: { reason: reason || "" },
  });
}

/**
 * 人工关闭/完成工单
 * 接口路径：POST /api/work-orders/{id}/complete
 */
export async function completeWorkOrder(
  id: string | number,
  reasonOrPayload?: string | { reason?: string; completionReason?: string }
): Promise<ApiResponse<WorkOrder>> {
  const data = typeof reasonOrPayload === "string" ? { reason: reasonOrPayload } : reasonOrPayload || {};
  return await request<WorkOrder>({
    url: `/api/work-orders/${id}/complete`,
    method: "POST",
    data,
  });
}
export const manualCompleteWorkOrder = completeWorkOrder;

/**
 * 分页查询派工记录
 * 接口路径：GET /api/dispatches
 */
export async function getDispatches(query: DispatchQuery = {}): Promise<ApiResponse<PageResult<DispatchRecord>>> {
  return await request<PageResult<DispatchRecord>>({
    url: "/api/dispatches",
    method: "GET",
    params: query,
  });
}
export const getDispatchOrders = getDispatches;

/**
 * 下达派工安排
 * 接口路径：POST /api/dispatches
 */
export async function createDispatch(payload: DispatchCreatePayload): Promise<ApiResponse<DispatchRecord>> {
  return await request<DispatchRecord>({
    url: "/api/dispatches",
    method: "POST",
    data: payload,
  });
}
export const createDispatchOrder = createDispatch;

/**
 * 发布派工单
 * 接口路径：POST /api/dispatches/{id}/release
 */
export async function releaseDispatchOrder(id: string | number): Promise<ApiResponse<DispatchRecord>> {
  return await request<DispatchRecord>({
    url: `/api/dispatches/${id}/release`,
    method: "POST",
  });
}

/**
 * 分页查询工序执行列表
 * 接口路径：GET /api/executions
 */
export async function getOperationExecutions(query: OperationExecutionQuery = {}): Promise<ApiResponse<PageResult<OperationExecution>>> {
  return await request<PageResult<OperationExecution>>({
    url: "/api/executions",
    method: "GET",
    params: query,
  });
}

/**
 * 创建工序执行记录
 * 接口路径：POST /api/executions
 */
export async function createOperationExecution(payload: any): Promise<ApiResponse<OperationExecution>> {
  return await request<OperationExecution>({
    url: "/api/executions",
    method: "POST",
    data: payload,
  });
}

/**
 * 开始工序执行
 * 接口路径：POST /api/executions/{id}/start
 */
export async function startOperationExecution(id: string | number): Promise<ApiResponse<OperationExecution>> {
  return await request<OperationExecution>({
    url: `/api/executions/${id}/start`,
    method: "POST",
  });
}

/**
 * 暂停工序执行
 * 接口路径：POST /api/executions/{id}/pause
 */
export async function pauseOperationExecution(id: string | number, reason?: string): Promise<ApiResponse<OperationExecution>> {
  return await request<OperationExecution>({
    url: `/api/executions/${id}/pause`,
    method: "POST",
    data: { reason: reason || "" },
  });
}

/**
 * 恢复工序执行
 * 接口路径：POST /api/executions/{id}/resume
 */
export async function resumeOperationExecution(id: string | number): Promise<ApiResponse<OperationExecution>> {
  return await request<OperationExecution>({
    url: `/api/executions/${id}/resume`,
    method: "POST",
  });
}

/**
 * 完工工序执行
 * 接口路径：POST /api/executions/{id}/complete
 */
export async function completeOperationExecution(id: string | number): Promise<ApiResponse<OperationExecution>> {
  return await request<OperationExecution>({
    url: `/api/executions/${id}/complete`,
    method: "POST",
  });
}

/**
 * 提交工序报工与质检判定
 * 接口路径：POST /api/executions/{id}/report
 */
export async function submitWorkReport(idOrPayload: any, payload?: WorkReportPayload): Promise<ApiResponse<OperationExecution>> {
  const actualPayload = payload || idOrPayload;
  const targetId = payload ? idOrPayload : (actualPayload.operationExecutionId || actualPayload.executionId || "");
  const url = targetId ? `/api/executions/${targetId}/report` : "/api/executions/report";
  return await request<OperationExecution>({
    url,
    method: "POST",
    data: actualPayload,
  });
}
export const createWorkReport = submitWorkReport;

/**
 * 分页查询生产领退料记录
 * 接口路径：GET /api/material-movements
 */
export async function getMaterialMovements(query: MaterialMovementQuery = {}): Promise<ApiResponse<PageResult<MaterialMovement>>> {
  return await request<PageResult<MaterialMovement>>({
    url: "/api/material-movements",
    method: "GET",
    params: query,
  });
}

/**
 * 查询生产领料记录
 * 接口路径：GET /api/material-movements?type=ISSUE
 */
export async function getMaterialIssues(query: any = {}): Promise<ApiResponse<PageResult<MaterialMovement>>> {
  return await request<PageResult<MaterialMovement>>({
    url: "/api/material-movements",
    method: "GET",
    params: { ...query, movementType: "ISSUE" },
  });
}

/**
 * 创建生产领料单
 * 接口路径：POST /api/material-movements/issue
 */
export async function createMaterialIssue(payload: MaterialMovementPayload): Promise<ApiResponse<MaterialMovement>> {
  return await request<MaterialMovement>({
    url: "/api/material-movements/issue",
    method: "POST",
    data: payload,
  });
}

/**
 * 确认生产领料出库
 * 接口路径：POST /api/material-movements/{id}/confirm
 */
export async function confirmMaterialIssue(id: string | number, payload?: any): Promise<ApiResponse<MaterialMovement>> {
  return await request<MaterialMovement>({
    url: `/api/material-movements/${id}/confirm`,
    method: "POST",
    data: payload,
  });
}
export const issueMaterials = createMaterialIssue;

/**
 * 查询生产退料记录
 * 接口路径：GET /api/material-movements?type=RETURN
 */
export async function getMaterialReturns(query: any = {}): Promise<ApiResponse<PageResult<MaterialReturnItem>>> {
  return await request<PageResult<MaterialReturnItem>>({
    url: "/api/material-movements",
    method: "GET",
    params: { ...query, movementType: "RETURN" },
  });
}

/**
 * 创建生产退料单
 * 接口路径：POST /api/material-movements/return
 */
export async function createMaterialReturn(payload: MaterialMovementPayload): Promise<ApiResponse<MaterialMovement>> {
  return await request<MaterialMovement>({
    url: "/api/material-movements/return",
    method: "POST",
    data: payload,
  });
}

/**
 * 确认生产退料入库
 * 接口路径：POST /api/material-movements/{id}/confirm
 */
export async function confirmMaterialReturn(id: string | number, payload?: any): Promise<ApiResponse<MaterialMovement>> {
  return await request<MaterialMovement>({
    url: `/api/material-movements/${id}/confirm`,
    method: "POST",
    data: payload,
  });
}
export const returnMaterials = createMaterialReturn;

/**
 * 分页查询成品入库记录
 * 接口路径：GET /api/finished-goods-receipts
 */
export async function getFinishedGoodsReceipts(query: FinishedGoodsReceiptQuery = {}): Promise<ApiResponse<PageResult<FinishedGoodsReceipt>>> {
  return await request<PageResult<FinishedGoodsReceipt>>({
    url: "/api/finished-goods-receipts",
    method: "GET",
    params: query,
  });
}

/**
 * 确认成品完工入库
 * 接口路径：POST /api/finished-goods-receipts
 */
export async function confirmFinishedGoodsReceipt(payload: FinishedGoodsReceiptPayload): Promise<ApiResponse<FinishedGoodsReceipt>> {
  return await request<FinishedGoodsReceipt>({
    url: "/api/finished-goods-receipts",
    method: "POST",
    data: payload,
  });
}
export const createFinishedGoodsReceipt = confirmFinishedGoodsReceipt;
