/**
 * ERP/WMS 销售履约领域类型定义 (Sales Domain Types)
 * 遵循 10-erp-wms 规格与业务规则：
 * 1. 双轴状态模型：
 *    - 生命周期 status: Draft(未提交) -> Submitted(已提交) -> Approved(已审核) -> Completed(已完成)
 *    - 履约进度 fulfillment_status (派生): NotStarted(未开始) / InProgress(履约中) / FullyShipped(全部发货)
 *    - 完成方式 completion_type: Normal(正常发货完成) / Manual(人工完成终止)
 * 2. 数量不变量：0 <= shipped_qty <= picked_qty <= reserved_qty <= ordered_qty
 * 3. 正常履约路径：审核通过 -> 直接拣货(自动补齐预留并移至 ShippingStaging) -> 发货(扣减实物并释放预留)
 * 4. 异常处理：退回拣货(从 ShippingStaging 移回)，释放未拣预留(释放 unpicked_qty)
 * 5. 人工完成：必须无发货暂存占用(shipping_staged_qty === 0)，必填完成原因
 */

import type { BaseEntity, PageQuery, AllowedAction } from "./common";

/**
 * 销售订单持久化生命周期状态
 */
export type SalesOrderStatus = "Draft" | "Submitted" | "Approved" | "Completed";

/**
 * 销售订单只读派生履约进度
 */
export type SalesOrderFulfillmentStatus = "NotStarted" | "InProgress" | "FullyShipped";

/**
 * 销售订单完成方式
 */
export type SalesOrderCompletionType = "Normal" | "Manual";

/**
 * 销售订单明细行实体
 */
export interface SalesOrderLine {
  id: string | number;
  soId: string | number;
  lineNo: number;
  productId: string | number;
  sku: string;
  productName: string;
  spec?: string;
  uom: string;
  sourceLocationId?: string | number;
  sourceLocationCode?: string;
  orderedQty: string;          // 客户要求订单数量
  reservedQty: string;         // 有效预留数量（包含已拣货与已发货）
  pickedQty: string;           // 累计拣货数量（包含发货暂存与已发货）
  shippedQty: string;          // 累计已发货出库数量
  // 5 个派生数量（按业务公式严格计算）
  unreservedQty: string;       // 尚未预留数量 = orderedQty - reservedQty
  unpickedQty: string;         // 已预留未拣货数量 = reservedQty - pickedQty
  shippingStagedQty: string;   // 发货暂存数量 = pickedQty - shippedQty
  activeReservedQty: string;   // 库内有效预留数量 = reservedQty - shippedQty
  unshippedQty: string;        // 尚未发货数量 = orderedQty - shippedQty
}

/**
 * 销售单事件记录
 */
export interface SalesOrderEvent {
  time: string;
  action: string;
  actor: string;
  session?: string;
  key?: string;
  impact?: string;
}

/**
 * 销售订单聚合根实体
 */
export interface SalesOrder extends BaseEntity {
  soNo: string;
  customerId: string | number;
  customerCode: string;
  customerName: string;
  owner?: string;
  plannedShipDate: string;
  status: SalesOrderStatus;
  fulfillmentStatus: SalesOrderFulfillmentStatus;
  completionType?: SalesOrderCompletionType | null;
  completionReason?: string | null;
  completedBy?: string;
  completedSessionId?: string;
  completedAt?: string;
  priority?: string;
  warehouseId?: string | number;
  warehouseName?: string;
  shippingLocationId?: string | number;
  shippingLocationCode?: string;
  lines: SalesOrderLine[];
  events?: SalesOrderEvent[];
  allowedActions?: AllowedAction[];
}

/**
 * 销售订单查询参数
 */
export interface SalesOrderQuery extends PageQuery {
  status?: SalesOrderStatus | string;
  fulfillmentStatus?: SalesOrderFulfillmentStatus | string;
  customerId?: string | number;
  dateFrom?: string;
  dateTo?: string;
}

/**
 * 创建销售订单明细入参
 */
export interface SalesOrderCreateLine {
  productId: string | number;
  orderedQty: string;
  uom: string;
  sourceLocationId?: string | number;
}

/**
 * 创建销售订单载荷
 */
export interface SalesOrderCreatePayload {
  customerId: string | number;
  plannedShipDate: string;
  warehouseId: string | number;
  priority?: string;
  remark?: string;
  lines: SalesOrderCreateLine[];
}

/**
 * 人工完成销售订单载荷
 */
export interface SalesOrderManualCompletePayload {
  completionReason: string;
}

/**
 * 拣货任务状态
 */
export type PickTaskStatus = "Pending" | "Completed" | "Returned";

/**
 * 拣货任务实体
 */
export interface PickTask extends BaseEntity {
  taskNo: string;
  salesOrderId: string | number;
  soNo: string;
  salesOrderLineId: string | number;
  productId: string | number;
  sku: string;
  productName: string;
  uom: string;
  lotNo?: string;
  sourceLocationId: string | number;
  sourceLocationCode: string;
  shippingLocationId: string | number;
  shippingLocationCode: string;
  pickQty: string;
  pickedQty: string;
  status: PickTaskStatus;
  confirmedAt?: string;
  allowedActions?: AllowedAction[];
}

/**
 * 直接拣货提交载荷（自动预留并移至发货暂存位）
 */
export interface DirectPickPayload {
  salesOrderId: string | number;
  salesOrderLineId: string | number;
  productId: string | number;
  pickedQty: string;
  sourceLocationId: string | number;
  shippingLocationId: string | number;
  lotNo?: string;
}

/**
 * 异常退回已拣货物载荷
 */
export interface PickReturnPayload {
  salesOrderId: string | number;
  salesOrderLineId: string | number;
  returnQty: string;
  toLocationId: string | number; // 移回原合法来源/拣选库位
  reason?: string;
}

/**
 * 释放未拣预留单行载荷
 */
export interface ReleaseReservationLine {
  salesOrderLineId: string | number;
  releaseQty: string;
  reason: string;
}

/**
 * 异常释放未拣预留提交载荷
 */
export interface ReleaseReservationPayload {
  salesOrderId: string | number;
  releaseLines: ReleaseReservationLine[];
}

/**
 * 发货单状态
 */
export type SalesShipmentStatus = "Draft" | "Confirmed";

/**
 * 销售发货确认单实体
 */
export interface SalesShipment extends BaseEntity {
  shipmentNo: string;
  salesOrderId: string | number;
  soNo: string;
  shipTime: string;
  carrierName?: string;
  trackingNo?: string;
  status: SalesShipmentStatus;
  lines: SalesShipmentLine[];
  allowedActions?: AllowedAction[];
}

/**
 * 发货明细行
 */
export interface SalesShipmentLine {
  id: string | number;
  shipmentId: string | number;
  salesOrderLineId: string | number;
  productId: string | number;
  sku: string;
  productName: string;
  uom: string;
  shipQty: string;
  lotNo?: string;
}

/**
 * 发货单提交明细
 */
export interface SalesShipmentConfirmLine {
  salesOrderLineId: string | number;
  productId: string | number;
  shipQty: string;
  lotNo?: string;
}

/**
 * 确认发货提交载荷
 */
export interface SalesShipmentConfirmPayload {
  salesOrderId: string | number;
  shipTime: string;
  carrierName?: string;
  trackingNo?: string;
  lines: SalesShipmentConfirmLine[];
}

export type PickConfirmPayload = DirectPickPayload;
export type ShipmentConfirmPayload = SalesShipmentConfirmPayload;
export type SalesReservationReleasePayload = ReleaseReservationPayload;

