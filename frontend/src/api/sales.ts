/**
 * 销售履约与直接拣发 API 服务 (Sales API)
 * 提供销售订单全生命周期、双轴状态与派生数量、直接拣货自动预留、发货出库扣减接口
 * 遵循 docs/specs/10-erp-wms 规范契约，纯粹直连真实后端 REST 接口
 * 绝不在前端复制销售履约与库存扣减状态机，真实派生数量由后端动态计算
 */

import request, { type ApiResponse } from "../utils/request";
import type {
  SalesOrder,
  SalesOrderPageResult,
  SalesOrderQuery,
  SalesOrderCreatePayload,
  PickTaskConfirmRequest,
  PickTaskReturnRequest,
  DirectPickPayload,
  PickReturnPayload,
  SalesShipmentConfirmPayload,
  SalesReservationReleasePayload,
  SalesFulfillmentResult,
} from "../types/sales";

/**
 * 分页查询销售订单列表（返回持久化状态与派生履约状态）
 * 接口路径：GET /api/sales-orders
 */
export async function getSalesOrders(query: SalesOrderQuery = {}): Promise<ApiResponse<SalesOrderPageResult>> {
  return await request<SalesOrderPageResult>({
    url: "/api/sales-orders",
    method: "GET",
    params: query,
  });
}

/**
 * 查询销售订单聚合详情（返回 5 个派生数量与 allowedActions）
 * 接口路径：GET /api/sales-orders/{id}
 */
export async function getSalesOrderById(id: string | number): Promise<ApiResponse<SalesOrder>> {
  return await request<SalesOrder>({
    url: `/api/sales-orders/${id}`,
    method: "GET",
  });
}

/**
 * 创建新销售订单（初始为 Draft 未提交状态）
 * 接口路径：POST /api/sales-orders
 */
export async function createSalesOrder(payload: SalesOrderCreatePayload): Promise<ApiResponse<SalesOrder>> {
  return await request<SalesOrder>({
    url: "/api/sales-orders",
    method: "POST",
    data: payload,
  });
}

/**
 * 提交销售订单审核 (Draft -> Submitted)
 * 接口路径：POST /api/sales-orders/{id}/submit
 */
export async function submitSalesOrder(id: string | number): Promise<ApiResponse<SalesOrder>> {
  return await request<SalesOrder>({
    url: `/api/sales-orders/${id}/submit`,
    method: "POST",
  });
}

/**
 * 审核通过销售订单 (Submitted -> Approved)
 * 接口路径：POST /api/sales-orders/{id}/approve
 */
export async function approveSalesOrder(id: string | number): Promise<ApiResponse<SalesOrder>> {
  return await request<SalesOrder>({
    url: `/api/sales-orders/${id}/approve`,
    method: "POST",
  });
}

/**
 * 人工完成销售订单
 * 接口路径：POST /api/sales-orders/{id}/complete
 */
export async function completeSalesOrder(
  id: string | number,
  reasonOrPayload?: string | { completionReason: string }
): Promise<ApiResponse<SalesFulfillmentResult>> {
  const data = typeof reasonOrPayload === "string" ? { completionReason: reasonOrPayload } : reasonOrPayload || { completionReason: "" };
  return await request<SalesFulfillmentResult>({
    url: `/api/sales-orders/${id}/complete`,
    method: "POST",
    data,
  });
}

/**
 * 异常释放未拣预留
 * 接口路径：POST /api/sales-orders/{id}/reservations/release
 */
export async function releaseSalesReservation(
  orderId: string | number,
  payload: SalesReservationReleasePayload
): Promise<ApiResponse<SalesFulfillmentResult>> {
  return await request<SalesFulfillmentResult>({
    url: `/api/sales-orders/${orderId}/reservations/release`,
    method: "POST",
    data: {
      releaseLines: payload.releaseLines,
    },
  });
}
export const releaseReservation = releaseSalesReservation;

/**
 * 分页查询订单驱动的拣货任务队列（基于销售订单分页）
 * 接口路径：GET /api/pick-tasks
 */
export async function getPickTasks(query: SalesOrderQuery = {}): Promise<ApiResponse<SalesOrderPageResult>> {
  return await request<SalesOrderPageResult>({
    url: "/api/pick-tasks",
    method: "GET",
    params: query,
  });
}

/**
 * 确认直接拣货
 * 路径 {id} 为履约操作事实标识，请求体内 salesOrderId 为销售订单标识
 * 接口路径：POST /api/pick-tasks/{id}/confirm
 */
export async function confirmPickTask(
  operationId: string,
  payload: PickTaskConfirmRequest
): Promise<ApiResponse<SalesFulfillmentResult>> {
  return await request<SalesFulfillmentResult>({
    url: `/api/pick-tasks/${operationId}/confirm`,
    method: "POST",
    data: payload,
  });
}
export const confirmDirectPick = confirmPickTask;

/**
 * 退回未发货拣货
 * 路径 {id} 为履约操作事实标识，请求体内 salesOrderId 为销售订单标识
 * 接口路径：POST /api/pick-tasks/{id}/return
 */
export async function returnPickTask(
  operationId: string,
  payload: PickTaskReturnRequest
): Promise<ApiResponse<SalesFulfillmentResult>> {
  return await request<SalesFulfillmentResult>({
    url: `/api/pick-tasks/${operationId}/return`,
    method: "POST",
    data: payload,
  });
}
export const returnPick = returnPickTask;

/**
 * 销售发货确认（扣减企业总实物库存，释放业务预留，更新履约数量）
 * 路径 {id} 为发货履约事实标识，请求体内 salesOrderId 为销售订单标识
 * 接口路径：POST /api/sales-shipments/{id}/confirm
 */
export async function confirmSalesShipment(
  operationId: string,
  payload: SalesShipmentConfirmPayload
): Promise<ApiResponse<SalesFulfillmentResult>> {
  const requestBody = {
    salesOrderId: String(payload.salesOrderId),
    shipTime: payload.shipTime,
    shipmentLines: (payload.shipmentLines || payload.lines || []).map((line) => ({
      salesOrderLineId: String(line.salesOrderLineId),
      productId: String(line.productId),
      shipQty: String(line.shipQty),
    })),
  };

  return await request<SalesFulfillmentResult>({
    url: `/api/sales-shipments/${operationId}/confirm`,
    method: "POST",
    data: requestBody,
  });
}
export const confirmShipment = confirmSalesShipment;
