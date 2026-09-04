/**
 * 库存内核业务 API 服务 (Inventory API)
 * 提供实时库存余额查询、预留占用与分配、不可篡改审计流水、库位调拨与差异盘点功能
 * 遵循 docs/specs/10-erp-wms 规格契约，纯粹直连真实后端 REST 接口
 * 绝不在前端复制库存、预留、调拨与盘点状态机，真实结果由后端事务决定
 */

import request, { type ApiResponse } from "../utils/request";
import type { PageResult } from "../types/common";
import type {
  InventoryBalance,
  InventoryBalanceQuery,
  InventoryReservation,
  InventoryReservationQuery,
  InventoryTransaction,
  InventoryTransactionQuery,
  TransferOrder,
  TransferCreatePayload,
  TransferQuery,
  StocktakeOrder,
  StocktakeCreatePayload,
  StocktakeRecordLinePayload,
  StocktakeConfirmPayload,
  StocktakeQuery,
} from "../types/inventory";

/**
 * 分页查询实时库存余额
 * 接口路径：GET /api/inventory/balances
 */
export async function getInventoryBalances(query: InventoryBalanceQuery = {}): Promise<ApiResponse<PageResult<InventoryBalance>>> {
  return await request<PageResult<InventoryBalance>>({
    url: "/api/inventory/balances",
    method: "GET",
    params: query,
  });
}

/**
 * 分页查询销售预留与分配明细
 * 接口路径：GET /api/inventory/reservations
 */
export async function getInventoryReservations(query: InventoryReservationQuery = {}): Promise<ApiResponse<PageResult<InventoryReservation>>> {
  return await request<PageResult<InventoryReservation>>({
    url: "/api/inventory/reservations",
    method: "GET",
    params: query,
  });
}

/**
 * 分页查询库存不可篡改审计流水
 * 接口路径：GET /api/inventory/transactions
 */
export async function getInventoryTransactions(query: InventoryTransactionQuery = {}): Promise<ApiResponse<PageResult<InventoryTransaction>>> {
  return await request<PageResult<InventoryTransaction>>({
    url: "/api/inventory/transactions",
    method: "GET",
    params: query,
  });
}

/**
 * 分页查询库位调拨单列表
 * 接口路径：GET /api/transfers
 */
export async function getTransfers(query: TransferQuery = {}): Promise<ApiResponse<PageResult<TransferOrder>>> {
  return await request<PageResult<TransferOrder>>({
    url: "/api/transfers",
    method: "GET",
    params: query,
  });
}

/**
 * 创建新调拨单
 * 接口路径：POST /api/transfers
 */
export async function createTransfer(payload: TransferCreatePayload): Promise<ApiResponse<TransferOrder>> {
  return await request<TransferOrder>({
    url: "/api/transfers",
    method: "POST",
    data: payload,
  });
}

/**
 * 确认执行调拨
 * 接口路径：POST /api/transfers/{id}/confirm
 * 要求携带 Idempotency-Key，在后端同一事务内校验可用库存、完成移动并生成流水
 */
export async function confirmTransfer(id: string | number): Promise<ApiResponse<TransferOrder>> {
  return await request<TransferOrder>({
    url: `/api/transfers/${id}/confirm`,
    method: "POST",
  });
}

/**
 * 分页查询差异盘点单列表
 * 接口路径：GET /api/stocktakes
 */
export async function getStocktakes(query: StocktakeQuery = {}): Promise<ApiResponse<PageResult<StocktakeOrder>>> {
  return await request<PageResult<StocktakeOrder>>({
    url: "/api/stocktakes",
    method: "GET",
    params: query,
  });
}

/**
 * 获取盘点单详情及快照明细行
 * 接口路径：GET /api/stocktakes/{id}
 */
export async function getStocktakeById(id: string | number): Promise<ApiResponse<StocktakeOrder>> {
  return await request<StocktakeOrder>({
    url: `/api/stocktakes/${id}`,
    method: "GET",
  });
}

/**
 * 创建新盘点单并冻结系统数量快照
 * 接口路径：POST /api/stocktakes
 */
export async function createStocktake(payload: StocktakeCreatePayload): Promise<ApiResponse<StocktakeOrder>> {
  return await request<StocktakeOrder>({
    url: "/api/stocktakes",
    method: "POST",
    data: payload,
  });
}

/**
 * 录入盘点实盘数量与差异原因
 * 接口路径：POST /api/stocktakes/{id}/record
 */
export async function recordStocktakeLines(id: string | number, lines: StocktakeRecordLinePayload[]): Promise<ApiResponse<StocktakeOrder>> {
  return await request<StocktakeOrder>({
    url: `/api/stocktakes/${id}/record`,
    method: "POST",
    data: { lines },
  });
}

/**
 * 确认盘点并调整库存余额
 * 接口路径：POST /api/stocktakes/{id}/confirm
 * 要求携带 Idempotency-Key，后端校验差异原因必填性，更新余额并追加差异流水
 */
export async function confirmStocktake(id: string | number, payload: StocktakeConfirmPayload): Promise<ApiResponse<StocktakeOrder>> {
  return await request<StocktakeOrder>({
    url: `/api/stocktakes/${id}/confirm`,
    method: "POST",
    data: payload,
  });
}
