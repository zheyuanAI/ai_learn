/**
 * 销售履约与直接拣发 API 服务 (Sales API)
 * 提供销售订单全生命周期、双轴状态与派生数量、直接拣货自动预留、发货出库扣减接口
 * 遵循 docs/specs/10-erp-wms 规范契约，纯粹直连真实后端 REST 接口
 * 绝不在前端复制销售履约与库存扣减状态机，真实派生数量由后端动态计算
 */

import request, { type ApiResponse } from "../utils/request";
import type { PageResult } from "../types/common";
import type {
  SalesOrder,
  SalesOrderQuery,
  SalesOrderCreatePayload,
  PickTask,
  PickConfirmPayload,
  PickReturnPayload,
  ShipmentConfirmPayload,
  SalesReservationReleasePayload,
} from "../types/sales";

/**
 * 分页查询销售订单列表（返回持久化状态与派生履约状态）
 * 接口路径：GET /api/sales-orders
 */
export async function getSalesOrders(query: SalesOrderQuery = {}): Promise<ApiResponse<PageResult<SalesOrder>>> {
  return await request<PageResult<SalesOrder>>({
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
): Promise<ApiResponse<SalesOrder>> {
  const data = typeof reasonOrPayload === "string" ? { completionReason: reasonOrPayload } : reasonOrPayload || {};
  return await request<SalesOrder>({
    url: `/api/sales-orders/${id}/complete`,
    method: "POST",
    data,
  });
}

/**
 * 异常释放未拣预留
 * 接口路径：POST /api/sales-orders/{id}/reservations/release
 */
export async function releaseSalesReservation(orderId: string | number, payload: SalesReservationReleasePayload): Promise<ApiResponse<SalesOrder>> {
  return await request<SalesOrder>({
    url: `/api/sales-orders/${orderId}/reservations/release`,
    method: "POST",
    data: payload,
  });
}
export const releaseReservation = releaseSalesReservation;

/**
 * 分页查询拣货任务列表
 * 接口路径：GET /api/pick-tasks
 */
export async function getPickTasks(query: { page?: number; size?: number; status?: string } = {}): Promise<ApiResponse<PageResult<PickTask>>> {
  return await request<PageResult<PickTask>>({
    url: "/api/pick-tasks",
    method: "GET",
    params: query,
  });
}

/**
 * 确认直接拣货（先补齐预留，再将实物与预留分配同步移至 ShippingStaging 发货暂存位）
 * 接口路径：POST /api/pick-tasks/{id}/confirm 或 POST /api/pick-tasks/confirm
 */
export async function confirmPickTask(taskIdOrPayload: any, payload?: PickConfirmPayload): Promise<ApiResponse<PickTask>> {
  const actualPayload = payload || taskIdOrPayload;
  const targetId = payload ? taskIdOrPayload : (actualPayload.taskId || actualPayload.id || "");
  const url = targetId ? `/api/pick-tasks/${targetId}/confirm` : "/api/pick-tasks/confirm";
  return await request<PickTask>({
    url,
    method: "POST",
    data: actualPayload,
  });
}
export const confirmDirectPick = confirmPickTask;

/**
 * 退回未发货拣货（从 ShippingStaging 移回合法来源库位，减少已拣数量）
 * 接口路径：POST /api/pick-tasks/{id}/return
 */
export async function returnPickTask(taskIdOrPayload: any, payload?: PickReturnPayload): Promise<ApiResponse<PickTask>> {
  const actualPayload = payload || taskIdOrPayload;
  const targetId = payload ? taskIdOrPayload : (actualPayload.taskId || actualPayload.id || "");
  const url = targetId ? `/api/pick-tasks/${targetId}/return` : "/api/pick-tasks/return";
  return await request<PickTask>({
    url,
    method: "POST",
    data: actualPayload,
  });
}
export const returnPick = returnPickTask;

/**
 * 销售发货确认（扣减企业总实物库存，释放业务预留，更新履约数量）
 * 接口路径：POST /api/sales-shipments/{id}/confirm 或 POST /api/sales-shipments/confirm
 */
export async function confirmSalesShipment(shipmentIdOrPayload: any, payload?: ShipmentConfirmPayload): Promise<ApiResponse<SalesOrder>> {
  const actualPayload = payload || shipmentIdOrPayload;
  const targetId = payload ? shipmentIdOrPayload : (actualPayload.shipmentId || actualPayload.id || "");
  const url = targetId ? `/api/sales-shipments/${targetId}/confirm` : "/api/sales-shipments/confirm";
  return await request<SalesOrder>({
    url,
    method: "POST",
    data: actualPayload,
  });
}
export const confirmShipment = confirmSalesShipment;
