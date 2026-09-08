/**
 * 采购入库与质检处置 API 服务 (Purchasing API)
 * 提供采购订单全生命周期、到货验收拒收、质量隔离与判定、处置执行及上架确认接口
 * 遵循 docs/specs/10-erp-wms 规范契约，纯粹直连真实后端 REST 接口
 * 绝不在前端复制采购与质检状态机，订单状态、累计数量与 allowedActions 完全由后端决定
 */

import request, { type ApiResponse } from "../utils/request";
import type { PageResult } from "../types/common";
import type {
  PurchaseOrder,
  PurchaseOrderQuery,
  PurchaseOrderCreatePayload,
  PurchaseReceipt,
  PurchaseReceiptConfirmPayload,
  PurchaseQualityInspection,
  QualityInspectPayload,
  PurchaseQualityDisposition,
  QualityDispositionDecidePayload,
  QualityDispositionConfirmPayload,
  PutawayTask,
  PutawayConfirmPayload,
} from "../types/purchasing";

/** 为同一业务命令显式复用幂等键；未传时保留旧调用方兼容行为。 */
function commandHeaders(idempotencyKey?: string) {
  return idempotencyKey ? { "Idempotency-Key": idempotencyKey } : undefined;
}

/**
 * 分页查询采购订单列表
 * 接口路径：GET /api/purchase-orders
 */
export async function getPurchaseOrders(query: PurchaseOrderQuery = {}): Promise<ApiResponse<PageResult<PurchaseOrder>>> {
  return await request<PageResult<PurchaseOrder>>({
    url: "/api/purchase-orders",
    method: "GET",
    params: query,
  });
}

/**
 * 查询单张采购订单详情（包含明细四类数量与 allowedActions）
 * 接口路径：GET /api/purchase-orders/{id}
 */
export async function getPurchaseOrderById(id: string | number): Promise<ApiResponse<PurchaseOrder>> {
  return await request<PurchaseOrder>({
    url: `/api/purchase-orders/${id}`,
    method: "GET",
  });
}

/**
 * 创建新采购订单（初始为 Draft 未提交状态）
 * 接口路径：POST /api/purchase-orders
 */
export async function createPurchaseOrder(payload: PurchaseOrderCreatePayload, idempotencyKey?: string): Promise<ApiResponse<PurchaseOrder>> {
  return await request<PurchaseOrder>({
    url: "/api/purchase-orders",
    method: "POST",
    data: payload,
    headers: commandHeaders(idempotencyKey),
  });
}

/**
 * 提交采购订单进入审核流程 (Draft -> Submitted)
 * 接口路径：POST /api/purchase-orders/{id}/submit
 */
export async function submitPurchaseOrder(id: string | number, idempotencyKey?: string): Promise<ApiResponse<PurchaseOrder>> {
  return await request<PurchaseOrder>({
    url: `/api/purchase-orders/${id}/submit`,
    method: "POST",
    headers: commandHeaders(idempotencyKey),
  });
}

/**
 * 审核通过采购订单 (Submitted -> Approved)
 * 接口路径：POST /api/purchase-orders/{id}/approve
 */
export async function approvePurchaseOrder(id: string | number, idempotencyKey?: string): Promise<ApiResponse<PurchaseOrder>> {
  return await request<PurchaseOrder>({
    url: `/api/purchase-orders/${id}/approve`,
    method: "POST",
    headers: commandHeaders(idempotencyKey),
  });
}

/**
 * 人工完成采购订单（剩余未收货数量不再履约，不补造流水）
 * 接口路径：POST /api/purchase-orders/{id}/complete
 */
export async function completePurchaseOrder(
  id: string | number,
  reasonOrPayload?: string | { completionReason: string },
  idempotencyKey?: string,
): Promise<ApiResponse<PurchaseOrder>> {
  const data = typeof reasonOrPayload === "string" ? { completionReason: reasonOrPayload } : reasonOrPayload || {};
  return await request<PurchaseOrder>({
    url: `/api/purchase-orders/${id}/complete`,
    method: "POST",
    data,
    headers: commandHeaders(idempotencyKey),
  });
}

/**
 * 仓库到货外观验收确认
 * 严格执行 arrived_qty = rejected_qty + received_qty，实收数量全部进入 QualityHold 质量隔离位
 * 路径 receiptId 为独立收货事实 UUID，订单 ID 必须取请求体 purchaseOrderId
 * 接口路径：POST /api/purchase-receipts/{receiptId}/confirm
 */
export async function confirmPurchaseReceipt(
  receiptId: string,
  payload: PurchaseReceiptConfirmPayload,
  idempotencyKey?: string,
): Promise<ApiResponse<PurchaseReceipt>> {
  return await request<PurchaseReceipt>({
    url: `/api/purchase-receipts/${receiptId}/confirm`,
    method: "POST",
    data: payload,
    headers: commandHeaders(idempotencyKey),
  });
}

/**
 * 仓库到货确认（服务端分配独立 receiptId）。
 * 接口路径：POST /api/purchase-receipts/confirm
 * 客户端只提交采购事实和幂等键，响应中的 id 才是后续质检使用的真实收货事实 ID。
 */
export async function confirmPurchaseReceiptWithServerId(
  payload: PurchaseReceiptConfirmPayload,
  idempotencyKey?: string,
): Promise<ApiResponse<PurchaseReceipt>> {
  return await request<PurchaseReceipt>({
    url: "/api/purchase-receipts/confirm",
    method: "POST",
    data: payload,
    headers: commandHeaders(idempotencyKey),
  });
}

/**
 * 提交到货质检记录
 * 严格执行 inspected_qty = qualified_qty + unqualified_qty，检验只生成质量事实不改变库存
 * 接口路径：POST /api/purchase-receipts/{receiptId}/quality/inspect
 */
export async function submitQualityInspection(
  receiptId: string,
  payload: QualityInspectPayload,
  idempotencyKey?: string,
): Promise<ApiResponse<PurchaseQualityInspection>> {
  return await request<PurchaseQualityInspection>({
    url: `/api/purchase-receipts/${receiptId}/quality/inspect`,
    method: "POST",
    data: payload,
    headers: commandHeaders(idempotencyKey),
  });
}
export const inspectQuality = submitQualityInspection;

/**
 * 查询当前租户质量处置列表
 * 接口路径：GET /api/purchase-quality-dispositions
 */
export async function getQualityDispositions(): Promise<ApiResponse<PurchaseQualityDisposition[]>> {
  return await request<PurchaseQualityDisposition[]>({
    url: "/api/purchase-quality-dispositions",
    method: "GET",
  });
}

/**
 * 查询当前租户采购到货质检记录列表
 * 接口路径：GET /api/purchase-receipts/quality-inspections
 */
export async function getQualityInspections(): Promise<ApiResponse<PurchaseQualityInspection[]>> {
  return await request<PurchaseQualityInspection[]>({
    url: "/api/purchase-receipts/quality-inspections",
    method: "GET",
  });
}

/**
 * 质量处置决定 (Release / Return / Scrap)
 * 接口路径：POST /api/purchase-receipts/{receiptId}/quality/{action}
 */
export async function decideQualityDisposition(
  receiptId: string,
  payload: QualityDispositionDecidePayload,
  idempotencyKey?: string,
): Promise<ApiResponse<PurchaseQualityDisposition>> {
  const action = payload.dispositionType.toLowerCase();

  return await request<PurchaseQualityDisposition>({
    url: `/api/purchase-receipts/${receiptId}/quality/${action}`,
    method: "POST",
    data: payload,
    headers: commandHeaders(idempotencyKey),
  });
}

/**
 * 质量处置决定：放行（入库收货暂存位）
 * 接口路径：POST /api/purchase-receipts/{id}/quality/release
 */
export async function decideQualityRelease(receiptId: string | number, payload: QualityDispositionDecidePayload, idempotencyKey?: string): Promise<ApiResponse<PurchaseQualityDisposition>> {
  return await request<PurchaseQualityDisposition>({
    url: `/api/purchase-receipts/${receiptId}/quality/release`,
    method: "POST",
    data: payload,
    headers: commandHeaders(idempotencyKey),
  });
}

/**
 * 质量处置决定：退回供应方
 * 接口路径：POST /api/purchase-receipts/{id}/quality/return
 */
export async function decideQualityReturn(receiptId: string | number, payload: QualityDispositionDecidePayload, idempotencyKey?: string): Promise<ApiResponse<PurchaseQualityDisposition>> {
  return await request<PurchaseQualityDisposition>({
    url: `/api/purchase-receipts/${receiptId}/quality/return`,
    method: "POST",
    data: payload,
    headers: commandHeaders(idempotencyKey),
  });
}

/**
 * 质量处置决定：报废
 * 接口路径：POST /api/purchase-receipts/{id}/quality/scrap
 */
export async function decideQualityScrap(receiptId: string | number, payload: QualityDispositionDecidePayload, idempotencyKey?: string): Promise<ApiResponse<PurchaseQualityDisposition>> {
  return await request<PurchaseQualityDisposition>({
    url: `/api/purchase-receipts/${receiptId}/quality/scrap`,
    method: "POST",
    data: payload,
    headers: commandHeaders(idempotencyKey),
  });
}

/**
 * 仓库人员执行并确认处置结果
 * 放行执行移位至 ReceivingStaging，退回与报废执行扣减 QualityHold 库存并生成流水
 * 接口路径：POST /api/purchase-quality-dispositions/{id}/confirm
 */
export async function confirmQualityDisposition(dispositionId: string | number, payload: QualityDispositionConfirmPayload, idempotencyKey?: string): Promise<ApiResponse<PurchaseQualityDisposition>> {
  return await request<PurchaseQualityDisposition>({
    url: `/api/purchase-quality-dispositions/${dispositionId}/confirm`,
    method: "POST",
    data: payload,
    headers: commandHeaders(idempotencyKey),
  });
}

/**
 * 分页查询待上架与已上架任务
 * 接口路径：GET /api/putaway-tasks
 */
export async function getPutawayTasks(query: { page?: number; size?: number; status?: string } = {}): Promise<ApiResponse<PageResult<PutawayTask>>> {
  return await request<PageResult<PutawayTask>>({
    url: "/api/putaway-tasks",
    method: "GET",
    params: query,
  });
}

/**
 * 确认上架任务（从 ReceivingStaging 移动至目标 Storage 库位，不重复增加库存）
 * 接口路径：POST /api/putaway-tasks/{id}/confirm
 */
export async function confirmPutawayTask(taskId: string | number, payload: PutawayConfirmPayload, idempotencyKey?: string): Promise<ApiResponse<PutawayTask>> {
  return await request<PutawayTask>({
    url: `/api/putaway-tasks/${taskId}/confirm`,
    method: "POST",
    data: payload,
    headers: commandHeaders(idempotencyKey),
  });
}
