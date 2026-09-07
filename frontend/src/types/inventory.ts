/**
 * ERP/WMS 库存与主数据领域类型定义 (Inventory Domain Types)
 * 遵循 10-erp-wms 规格与业务规则：
 * 1. 数量与金额采用严格字符串表示，杜绝前端浮点精度损失
 * 2. 6 类标准库位：ReceivingStaging, Storage, Picking, ShippingStaging, QualityHold, Adjustment
 * 3. 统一库存公式：available_qty = on_hand_qty - reserved_qty >= 0
 * 4. 盘点状态机：NotStarted -> Counting -> ConfirmedAdjusted
 */

import type { BaseEntity, PageQuery, AllowedAction } from "./common";

/**
 * 字符串高精度加法，防止浮点数精度丢失
 * @param a 加数A（字符串或数字）
 * @param b 加数B（字符串或数字）
 * @returns 累加结果字符串
 */
export function stringAdd(a: string | number | undefined | null, b: string | number | undefined | null): string {
  const numA = parseFloat(String(a || "0")) || 0;
  const numB = parseFloat(String(b || "0")) || 0;
  const decA = (String(a || "").split(".")[1] || "").length;
  const decB = (String(b || "").split(".")[1] || "").length;
  const maxDec = Math.max(decA, decB);
  return (numA + numB).toFixed(maxDec);
}

/**
 * 字符串高精度减法，防止浮点数精度丢失
 * @param a 被减数（字符串或数字）
 * @param b 减数（字符串或数字）
 * @returns 相减结果字符串
 */
export function stringSub(a: string | number | undefined | null, b: string | number | undefined | null): string {
  const numA = parseFloat(String(a || "0")) || 0;
  const numB = parseFloat(String(b || "0")) || 0;
  const decA = (String(a || "").split(".")[1] || "").length;
  const decB = (String(b || "").split(".")[1] || "").length;
  const maxDec = Math.max(decA, decB);
  return (numA - numB).toFixed(maxDec);
}

/**
 * 比较两字符串数值大小
 * @param a 数值A
 * @param b 数值B
 * @returns 1: a > b; -1: a < b; 0: a === b
 */
export function stringCompare(a: string | number | undefined | null, b: string | number | undefined | null): number {
  const numA = parseFloat(String(a || "0")) || 0;
  const numB = parseFloat(String(b || "0")) || 0;
  if (numA > numB) return 1;
  if (numA < numB) return -1;
  return 0;
}

// ========================
// 主数据模型 (Master Data)
// ========================

/**
 * 物料分类枚举
 */
export type ProductCategory = "产成品" | "原材料" | "半成品" | "标准件" | "电子料" | "辅料包材";

/**
 * 商品物料主数据实体
 */
export interface Product extends BaseEntity {
  sku: string;
  name: string;
  spec?: string;
  uom: string;
  category: ProductCategory | string;
  batchMgmt: boolean;
  status: "ACTIVE" | "INACTIVE";
  minStock?: string;
  maxStock?: string;
  safetyStock?: string;
  unitPrice?: string;
}

/**
 * 物料查询入参
 */
export interface ProductQuery extends PageQuery {
  category?: string;
  batchMgmt?: boolean;
  status?: string;
}

/**
 * 6 类标准库位类型
 */
export type LocationType =
  | "ReceivingStaging" // 采购收货暂存位（放行后、上架前过渡）
  | "Storage"          // 常规存储位（正常保管）
  | "Picking"          // 拣货备料位（快速出库拣选）
  | "ShippingStaging"  // 发货暂存位（拣货完成等待发货）
  | "QualityHold"      // 质量隔离位（实际接收但未放行/不合格）
  | "Adjustment";      // 差异调整位（盘点溢缺调整专用）

/**
 * 仓库实体
 */
export interface Warehouse extends BaseEntity {
  code: string;
  name: string;
  type: string;
  status: "ACTIVE" | "INACTIVE";
  manager?: string;
  contact?: string;
  address?: string;
}

/**
 * 计量单位主数据实体
 */
export interface Uom extends BaseEntity {
  code: string;
  name: string;
  symbol?: string;
  status: "ACTIVE" | "INACTIVE";
}

/**
 * 库位实体
 */
export interface Location extends BaseEntity {
  warehouseId: string;
  warehouseName?: string;
  code: string;
  name: string;
  type: LocationType;
  status: "ACTIVE" | "INACTIVE";
  capacity?: string;
  description?: string;
}

/**
 * 库位查询参数
 */
export interface LocationQuery extends PageQuery {
  warehouseId?: string | number;
  type?: LocationType | string;
  status?: string;
}

/**
 * 往来客户实体
 */
export interface Customer extends BaseEntity {
  customerCode: string;
  customerName: string;
  contactPerson?: string;
  contactPhone?: string;
  shippingAddress?: string;
  status: "ACTIVE" | "INACTIVE";
}

/**
 * 往来供应商实体
 */
export interface Supplier extends BaseEntity {
  supplierCode: string;
  supplierName: string;
  contactPerson?: string;
  contactPhone?: string;
  address?: string;
  status: "ACTIVE" | "INACTIVE";
}

// ========================
// 库存内核 (Inventory Core)
// ========================

/**
 * 库存唯一维度
 */
export interface InventoryDimension {
  productId: string;
  warehouseId: string;
  locationId: string;
  lotNo?: string;
}

/**
 * 实时库存余额实体
 */
export interface InventoryBalance extends BaseEntity {
  tenantId?: string;
  dimension?: InventoryDimension;
  // 视图解构与兼容字段
  productId?: string;
  sku?: string;
  productName?: string;
  spec?: string;
  uom?: string;
  warehouseId?: string;
  warehouseName?: string;
  locationId?: string;
  locationCode?: string;
  locationType?: LocationType;
  lotNo?: string;
  onHandQty: string;       // 实物在库量
  reservedQty: string;     // 业务预留量
  availableQty: string;    // 可用分配量 = onHandQty - reservedQty
  version: number;
  lastTransactionAt?: string;
}

/**
 * 库存余额查询条件
 */
export interface InventoryBalanceQuery extends PageQuery {
  product_id?: string;
  warehouse_id?: string;
  location_id?: string;
  lot_no?: string;
  productId?: string;
  warehouseId?: string;
  locationId?: string;
  locationType?: LocationType | string;
  lotNo?: string;
}

/**
 * 预留业务来源枚举
 */
export type ReservationSourceType = "SALES_ORDER" | "WORK_ORDER" | "TRANSFER" | "MANUAL";

/**
 * 预留状态枚举
 */
export type ReservationStatus = "Active" | "PartiallyReleased" | "Released";

/**
 * 库位级预留分配实体
 */
export interface InventoryReservationAllocation {
  id: string;
  reservationId: string;
  dimension?: InventoryDimension;
  locationId: string;
  locationCode?: string;
  allocatedQty: string;
  status: string;
  version: number;
  createdAt?: string;
  // 视图兼容
  warehouseId?: string;
  warehouseName?: string;
  lotNo?: string;
  releasedQty?: string;
}

/**
 * 库存预留记录实体
 */
export interface InventoryReservation extends BaseEntity {
  reservationNo?: string;
  sourceType: ReservationSourceType | string;
  sourceId: string;
  sourceNo?: string;
  sourceLineId?: string;
  dimension?: InventoryDimension;
  reservedQty: string;
  status: ReservationStatus | string;
  version: number;
  allocations?: InventoryReservationAllocation[];
  // 视图兼容
  productId?: string;
  sku?: string;
  productName?: string;
  uom?: string;
  releasedQty?: string;
  activeReservedQty?: string;
}

/**
 * 库存预留与分配视图 (匹配后端 InventoryReservationView)
 */
export interface InventoryReservationView {
  reservation: InventoryReservation;
  allocations: InventoryReservationAllocation[];
}

/**
 * 库存预留查询参数
 */
export interface InventoryReservationQuery extends PageQuery {
  reservation_id?: string;
  source_type?: string;
  source_id?: string;
  source_line_id?: string;
  status?: string;
  product_id?: string;
  warehouse_id?: string;
  location_id?: string;
  lot_no?: string;
  sourceType?: ReservationSourceType | string;
  sourceNo?: string;
  productId?: string;
}

/**
 * 库存流水操作类型
 */
export type InventoryTransactionType =
  | "PURCHASE_RECEIPT"     // 采购收货入库（进入 QualityHold）
  | "QUALITY_RELEASE"      // 质检放行移位（QH -> RS）
  | "QUALITY_SCRAP"        // 质检报废扣减（QH 扣减）
  | "QUALITY_RETURN"       // 采购退回供应方（QH 扣减）
  | "PUTAWAY"              // 上架入库（RS -> Storage）
  | "DIRECT_PICK"          // 直接拣货（来源库位 -> SHP）
  | "PICK_RETURN"          // 拣货退回（SHP -> 来源库位）
  | "SALES_SHIPMENT"       // 销售发货出库（SHP 扣减）
  | "MATERIAL_ISSUE"       // 生产领料出库
  | "MATERIAL_RETURN"      // 生产退料入库
  | "FG_INBOUND"           // 产成品完工入库
  | "TRANSFER"             // 库位调拨
  | "STOCKTAKE_ADJUST"     // 盘点差异调整
  | "ADJUSTMENT";

/**
 * 库存审计流水不可篡改事实
 */
export interface InventoryTransaction extends BaseEntity {
  transactionNo: string;
  transactionType: InventoryTransactionType | string;
  sourceType: string;
  sourceId: string;
  sourceLineId?: string;
  fromDimension?: InventoryDimension | null;
  toDimension?: InventoryDimension | null;
  quantity?: string;
  qty?: string;
  occurredAt: string;
  operatorId?: string;
  operatorName?: string;
  sessionId?: string;
  requestId?: string;
  idempotencyKey?: string;
  // 视图兼容
  productId?: string;
  sku?: string;
  productName?: string;
  uom?: string;
  lotNo?: string;
  fromWarehouseId?: string;
  fromWarehouseName?: string;
  fromLocationId?: string;
  fromLocationCode?: string;
  toWarehouseId?: string;
  toWarehouseName?: string;
  toLocationId?: string;
  toLocationCode?: string;
}

/**
 * 库存流水查询入参
 */
export interface InventoryTransactionQuery extends PageQuery {
  transaction_type?: string;
  source_type?: string;
  source_id?: string;
  source_line_id?: string;
  product_id?: string;
  warehouse_id?: string;
  location_id?: string;
  lot_no?: string;
  occurred_from?: string;
  occurred_to?: string;
  transactionType?: InventoryTransactionType | string;
  productId?: string;
  warehouseId?: string;
  locationId?: string;
  lotNo?: string;
  dateFrom?: string;
  dateTo?: string;
}

// ========================
// 库位调拨 (Transfer)
// ========================

export type TransferStatus = "Draft" | "Confirmed";

/**
 * 调拨单明细
 */
export interface TransferLineItem {
  id?: string;
  transferId?: string;
  lineNo?: number;
  productId: string;
  lotNo?: string;
  uom: string;
  quantity: string;
  // 视图展示
  productName?: string;
  sku?: string;
}

/**
 * 调拨单实体 (TransferView)
 */
export interface TransferOrder extends BaseEntity {
  transferNo: string;
  fromWarehouseId: string;
  fromLocationId: string;
  toWarehouseId: string;
  toLocationId: string;
  status: TransferStatus;
  version: number;
  confirmedBy?: string;
  confirmedAt?: string;
  lines: TransferLineItem[];
  transactionIds?: string[];
  allowedActions?: AllowedAction[];
  // 视图展示兼容
  fromWarehouseName?: string;
  fromLocationCode?: string;
  toWarehouseName?: string;
  toLocationCode?: string;
  productId?: string;
  sku?: string;
  productName?: string;
  uom?: string;
  qty?: string;
  lotNo?: string;
  reason?: string;
}

/**
 * 调拨创建载荷 (对齐后端 TransferCreateRequest)
 */
export interface TransferCreatePayload {
  transferNo?: string;
  fromWarehouseId: string;
  fromLocationId: string;
  toWarehouseId: string;
  toLocationId: string;
  lines: Array<{
    productId: string;
    lotNo?: string;
    uom: string;
    quantity: string;
  }>;
}

/**
 * 调拨查询入参
 */
export interface TransferQuery extends PageQuery {
  status?: TransferStatus | string;
  keyword?: string;
  fromWarehouseId?: string;
  toWarehouseId?: string;
  productId?: string;
}

// ========================
// 差异盘点 (Stocktake)
// ========================

export type StocktakeStatus = "NotStarted" | "Counting" | "ConfirmedAdjusted";

/**
 * 盘点明细行 (对齐后端 StocktakeLine)
 */
export interface StocktakeLine {
  id?: string;
  lineId?: string;
  stocktakeId?: string;
  productId: string;
  warehouseId?: string;
  locationId?: string;
  lotNo?: string;
  systemQty: string;
  systemBalanceVersion?: number;
  countedQty?: string;
  varianceQty?: string;
  varianceReason?: string;
  adjustmentTransactionId?: string;
  // 视图展示
  sku?: string;
  productName?: string;
  uom?: string;
  locationCode?: string;
}

/**
 * 盘点单实体 (对齐后端 StocktakeView)
 */
export interface StocktakeOrder extends BaseEntity {
  stocktakeNo: string;
  warehouseId: string;
  locationId?: string;
  status: StocktakeStatus;
  version: number;
  startedBy?: string;
  startedAt?: string;
  confirmedBy?: string;
  confirmedAt?: string;
  lines: StocktakeLine[];
  transactionIds?: string[];
  allowedActions?: AllowedAction[];
  // 视图展示兼容
  warehouseName?: string;
  locationCode?: string;
  scopeType?: string;
  systemSnapshotAt?: string;
}

/**
 * 盘点创建载荷 (对齐后端 StocktakeCreateRequest)
 */
export interface StocktakeCreatePayload {
  stocktakeNo?: string;
  warehouseId: string;
  locationId?: string;
}

/**
 * 盘点确认明细 (对齐后端 StocktakeCountLineRequest)
 */
export interface StocktakeCountLinePayload {
  lineId: string;
  countedQty: string;
  varianceReason?: string;
}

/**
 * 盘点确认载荷 (对齐后端 StocktakeConfirmRequest)
 */
export interface StocktakeConfirmPayload {
  lines: StocktakeCountLinePayload[];
}

/**
 * 盘点查询参数
 */
export interface StocktakeQuery extends PageQuery {
  warehouseId?: string;
  status?: StocktakeStatus | string;
  keyword?: string;
}
