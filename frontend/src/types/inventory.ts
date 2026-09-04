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
  status: "ENABLE" | "DISABLE";
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
 * 库位实体
 */
export interface Location extends BaseEntity {
  warehouseId: string | number;
  warehouseName?: string;
  code: string;
  name: string;
  type: LocationType;
  status: "AVAILABLE" | "OCCUPIED" | "LOCKED";
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
 * 实时库存余额实体
 */
export interface InventoryBalance extends BaseEntity {
  tenantId?: string | number;
  productId: string | number;
  sku: string;
  productName: string;
  spec?: string;
  uom: string;
  warehouseId: string | number;
  warehouseName: string;
  locationId: string | number;
  locationCode: string;
  locationType: LocationType;
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
  productId?: string | number;
  warehouseId?: string | number;
  locationId?: string | number;
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
 * 库存预留记录实体
 */
export interface InventoryReservation extends BaseEntity {
  reservationNo: string;
  sourceType: ReservationSourceType;
  sourceId: string | number;
  sourceNo?: string;
  sourceLineId?: string | number;
  productId: string | number;
  sku: string;
  productName: string;
  uom: string;
  reservedQty: string;
  releasedQty: string;
  activeReservedQty: string;
  status: ReservationStatus;
  allocations?: InventoryReservationAllocation[];
}

/**
 * 库位级预留分配实体
 */
export interface InventoryReservationAllocation {
  id: string | number;
  reservationId: string | number;
  warehouseId: string | number;
  warehouseName?: string;
  locationId: string | number;
  locationCode: string;
  lotNo?: string;
  allocatedQty: string;
  releasedQty: string;
  version: number;
}

/**
 * 库存预留查询参数
 */
export interface InventoryReservationQuery extends PageQuery {
  sourceType?: ReservationSourceType | string;
  sourceNo?: string;
  productId?: string | number;
  status?: ReservationStatus | string;
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
  | "STOCKTAKE_ADJUST";    // 盘点差异调整

/**
 * 库存审计流水不可篡改事实
 */
export interface InventoryTransaction extends BaseEntity {
  transactionNo: string;
  transactionType: InventoryTransactionType;
  sourceType: string;
  sourceId: string | number;
  sourceNo?: string;
  sourceLineId?: string | number;
  productId: string | number;
  sku: string;
  productName: string;
  uom: string;
  lotNo?: string;
  fromWarehouseId?: string | number;
  fromWarehouseName?: string;
  fromLocationId?: string | number;
  fromLocationCode?: string;
  toWarehouseId?: string | number;
  toWarehouseName?: string;
  toLocationId?: string | number;
  toLocationCode?: string;
  qty: string; // 正数增加，负数减少，或移位绝对数量
  occurredAt: string;
  operatorId?: string | number;
  operatorName?: string;
  sessionId?: string;
  idempotencyKey?: string;
}

/**
 * 库存流水查询入参
 */
export interface InventoryTransactionQuery extends PageQuery {
  transactionType?: InventoryTransactionType | string;
  productId?: string | number;
  warehouseId?: string | number;
  locationId?: string | number;
  lotNo?: string;
  dateFrom?: string;
  dateTo?: string;
}

// ========================
// 库位调拨 (Transfer)
// ========================

/**
 * 调拨单状态
 */
export type TransferStatus = "Draft" | "Confirmed" | "Cancelled";

/**
 * 调拨单实体
 */
export interface TransferOrder extends BaseEntity {
  transferNo: string;
  fromWarehouseId: string | number;
  fromWarehouseName: string;
  fromLocationId: string | number;
  fromLocationCode: string;
  toWarehouseId: string | number;
  toWarehouseName: string;
  toLocationId: string | number;
  toLocationCode: string;
  productId: string | number;
  sku: string;
  productName: string;
  uom: string;
  lotNo?: string;
  qty: string;
  status: TransferStatus;
  reason?: string;
  confirmedBy?: string;
  confirmedAt?: string;
  allowedActions?: AllowedAction[];
}

/**
 * 调拨创建载荷
 */
export interface TransferCreatePayload {
  fromWarehouseId: string | number;
  fromLocationId: string | number;
  toWarehouseId: string | number;
  toLocationId: string | number;
  productId: string | number;
  qty: string;
  lotNo?: string;
  reason?: string;
}

/**
 * 调拨查询入参
 */
export interface TransferQuery extends PageQuery {
  status?: TransferStatus | string;
  fromWarehouseId?: string | number;
  toWarehouseId?: string | number;
  productId?: string | number;
}

// ========================
// 差异盘点 (Stocktake)
// ========================

/**
 * 盘点单状态
 */
export type StocktakeStatus = "NotStarted" | "Counting" | "ConfirmedAdjusted";

/**
 * 盘点明细行
 */
export interface StocktakeLine {
  id: string | number;
  stocktakeOrderId: string | number;
  productId: string | number;
  sku: string;
  productName: string;
  spec?: string;
  uom: string;
  warehouseId: string | number;
  locationId: string | number;
  locationCode: string;
  lotNo?: string;
  systemQty: string;       // 冻结快照系统数量
  countedQty?: string;     // 实盘录入数量
  varianceQty?: string;    // 差异数量 = countedQty - systemQty
  varianceReason?: string; // 差异原因（有差异时必填）
}

/**
 * 盘点单实体
 */
export interface StocktakeOrder extends BaseEntity {
  stocktakeNo: string;
  warehouseId: string | number;
  warehouseName: string;
  locationId?: string | number;
  locationCode?: string;
  scopeType: "FULL" | "LOCATION" | "CATEGORY";
  status: StocktakeStatus;
  systemSnapshotAt?: string;
  confirmedAt?: string;
  confirmedBy?: string;
  lines: StocktakeLine[];
  allowedActions?: AllowedAction[];
}

/**
 * 盘点单创建载荷
 */
export interface StocktakeCreatePayload {
  warehouseId: string | number;
  locationId?: string | number;
  scopeType: "FULL" | "LOCATION" | "CATEGORY";
  remark?: string;
}

/**
 * 盘点录入行项载荷
 */
export interface StocktakeRecordLinePayload {
  lineId: string | number;
  countedQty: string;
  varianceReason?: string;
}

/**
 * 盘点确认调整载荷
 */
export interface StocktakeConfirmPayload {
  lines: StocktakeRecordLinePayload[];
  overallReason?: string;
}

/**
 * 盘点查询参数
 */
export interface StocktakeQuery extends PageQuery {
  warehouseId?: string | number;
  status?: StocktakeStatus | string;
}
