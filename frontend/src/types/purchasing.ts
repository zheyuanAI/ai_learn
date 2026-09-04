/**
 * ERP/WMS 采购入库领域类型定义 (Purchasing Domain Types)
 * 遵循 10-erp-wms 规格与业务规则：
 * 1. 采购订单状态机：未提交(Draft) -> 已提交(Submitted) -> 已审核(Approved) -> 部分收货(PartiallyReceived) -> 已完成(Completed)
 * 2. 数量约束：arrived_qty = rejected_qty + received_qty
 * 3. 拒收数量不入库、不计入已收货并保留为待收；实际接收(received_qty)全部进入 QualityHold
 * 4. 质检约束：inspected_qty = qualified_qty + unqualified_qty
 * 5. 处置时点：放行移入 ReceivingStaging，上架移至 Storage，退回/报废执行扣减实物库存
 */

import type { BaseEntity, PageQuery, AllowedAction } from "./common";

/**
 * 采购订单生命周期状态
 */
export type PurchaseOrderStatus = "Draft" | "Submitted" | "Approved" | "PartiallyReceived" | "Completed";

/**
 * 采购订单完成方式
 */
export type PurchaseOrderCompletionType = "Normal" | "Manual";

/**
 * 采购订单明细行
 */
export interface PurchaseOrderLine {
  id: string | number;
  poId: string | number;
  lineNo: number;
  productId: string | number;
  sku: string;
  productName: string;
  spec?: string;
  uom: string;
  targetWarehouseId: string | number;
  targetWarehouseName?: string;
  orderedQty: string;          // 采购要求总数量
  arrivedQty: string;          // 累计到货验收量
  rejectedQty: string;         // 累计收货前外观拒收量
  rejectionReason?: string;    // 最近一次拒收原因
  receivedQty: string;         // 累计实际接收进入 QualityHold 量
  inspectedQty: string;        // 累计完成到货质检量
  qualifiedQty: string;        // 累计质检合格量
  unqualifiedQty: string;      // 累计质检不合格量
  unqualifiedReason?: string;  // 不合格原因
  releaseDecidedQty: string;   // 决定放行量
  scrapDecidedQty: string;     // 决定报废量
  returnDecidedQty: string;    // 决定退回供应方量
  releaseExecutedQty: string;  // 已执行放行移至 RS 量
  scrapExecutedQty: string;    // 已执行报废扣减量
  returnExecutedQty: string;   // 已执行退回扣减量
  putawayQty: string;          // 累计上架到 Storage 量
  pendingQty: string;          // 剩余待收货数量 = orderedQty - receivedQty
  sourceWorkOrderId?: string;  // 关联来源生产工单（仅表达追溯，不触发MRP）
  lotNo?: string;
}

/**
 * 采购单变更审计事件
 */
export interface PurchaseOrderEvent {
  time: string;
  action: string;
  actor: string;
  session?: string;
  key?: string;
  impact?: string;
}

/**
 * 采购订单聚合根实体
 */
export interface PurchaseOrder extends BaseEntity {
  poNo: string;
  supplierId: string | number;
  supplierCode: string;
  supplierName: string;
  owner?: string;
  expectedArrivalDate: string;
  status: PurchaseOrderStatus;
  completionType?: PurchaseOrderCompletionType | null;
  completionReason?: string | null;
  completedBy?: string;
  completedSessionId?: string;
  completedAt?: string;
  priority?: string;
  targetWarehouseId?: string | number;
  warehouseName?: string;
  qualityHoldLocationId?: string | number;
  qualityHoldLocationCode?: string;
  receivingStagingLocationId?: string | number;
  receivingStagingLocationCode?: string;
  storageLocationId?: string | number;
  storageLocationCode?: string;
  lines: PurchaseOrderLine[];
  events?: PurchaseOrderEvent[];
  allowedActions?: AllowedAction[];
}

/**
 * 采购订单查询条件
 */
export interface PurchaseOrderQuery extends PageQuery {
  status?: PurchaseOrderStatus | string;
  supplierId?: string | number;
  dateFrom?: string;
  dateTo?: string;
}

/**
 * 采购订单创建行项
 */
export interface PurchaseOrderCreateLine {
  productId: string | number;
  orderedQty: string;
  uom: string;
  targetWarehouseId: string | number;
  sourceWorkOrderId?: string;
}

/**
 * 采购订单创建载荷
 */
export interface PurchaseOrderCreatePayload {
  supplierId: string | number;
  expectedArrivalDate: string;
  targetWarehouseId: string | number;
  remark?: string;
  lines: PurchaseOrderCreateLine[];
}

/**
 * 采购收货单状态
 */
export type PurchaseReceiptStatus = "Draft" | "Confirmed";

/**
 * 采购收货单实体
 */
export interface PurchaseReceipt extends BaseEntity {
  receiptNo: string;
  purchaseOrderId: string | number;
  poNo: string;
  receiptTime: string;
  qualityHoldLocationId: string | number;
  qualityHoldLocationCode: string;
  status: PurchaseReceiptStatus;
  lines: PurchaseReceiptLine[];
  allowedActions?: AllowedAction[];
}

/**
 * 采购收货明细行
 */
export interface PurchaseReceiptLine {
  id: string | number;
  purchaseReceiptId: string | number;
  poLineId: string | number;
  productId: string | number;
  sku: string;
  productName: string;
  uom: string;
  arrivedQty: string;       // 到货数量
  rejectedQty: string;      // 收货前外观拒收数量
  rejectionReason?: string; // 拒收原因 (rejectedQty > 0 必填)
  receivedQty: string;      // 实际接收进入质量隔离位数量
  lotNo?: string;
}

/**
 * 采购到货验收提交载荷行
 */
export interface PurchaseReceiptConfirmLine {
  poLineId: string | number;
  productId: string | number;
  arrivedQty: string;
  rejectedQty: string;
  receivedQty: string;
  rejectionReason?: string;
  lotNo?: string;
}

/**
 * 采购到货验收确认载荷
 */
export interface PurchaseReceiptConfirmPayload {
  purchaseOrderId: string | number;
  receiptTime: string;
  qualityHoldLocationId: string | number;
  lines: PurchaseReceiptConfirmLine[];
}

/**
 * 到货检验事实实体
 */
export interface PurchaseQualityInspection extends BaseEntity {
  inspectionNo: string;
  purchaseOrderId: string | number;
  poNo: string;
  purchaseReceiptId: string | number;
  purchaseReceiptLineId: string | number;
  productId: string | number;
  sku: string;
  productName: string;
  inspectedQty: string;      // 本次检验数量
  qualifiedQty: string;      // 质检合格数量
  unqualifiedQty: string;    // 质检不合格数量
  unqualifiedReason?: string;
  inspectedBy: string;
  inspectedAt: string;
  inspectionRemark?: string;
}

/**
 * 质量处置类型
 */
export type QualityDispositionType = "Release" | "Return" | "Scrap";

/**
 * 质量处置执行状态
 */
export type QualityDispositionStatus = "PendingDecision" | "PendingExecution" | "Completed";

/**
 * 采购质量处置决定与执行实体
 */
export interface PurchaseQualityDisposition extends BaseEntity {
  dispositionNo: string;
  inspectionId: string | number;
  purchaseOrderId: string | number;
  poNo: string;
  purchaseReceiptId: string | number;
  productId: string | number;
  sku: string;
  productName: string;
  dispositionType: QualityDispositionType;
  dispositionQty: string;
  reason?: string;
  status: QualityDispositionStatus;
  decidedBy?: string;
  decidedAt?: string;
  executedBy?: string;
  executedAt?: string;
  fromLocationId?: string | number;
  fromLocationCode?: string;
  toLocationId?: string | number;
  toLocationCode?: string;
  allowedActions?: AllowedAction[];
}

/**
 * 采购质检录入载荷
 */
export interface QualityInspectPayload {
  purchaseOrderId: string | number;
  purchaseReceiptId: string | number;
  purchaseReceiptLineId: string | number;
  productId: string | number;
  inspectedQty: string;
  qualifiedQty: string;
  unqualifiedQty: string;
  unqualifiedReason?: string;
  inspectionRemark?: string;
}

/**
 * 采购质量处置决定载荷
 */
export interface QualityDispositionDecidePayload {
  inspectionId: string | number;
  dispositionType: QualityDispositionType;
  dispositionQty: string;
  reason?: string;
}

/**
 * 质量处置仓库确认执行载荷
 */
export interface QualityDispositionConfirmPayload {
  dispositionId: string | number;
  toLocationId?: string | number; // 放行时移至 ReceivingStaging 库位
}

/**
 * 上架任务状态
 */
export type PutawayTaskStatus = "Pending" | "Processing" | "Confirmed";

/**
 * 上架任务实体
 */
export interface PutawayTask extends BaseEntity {
  taskNo: string;
  purchaseOrderId: string | number;
  poNo: string;
  purchaseReceiptId: string | number;
  productId: string | number;
  sku: string;
  productName: string;
  uom: string;
  lotNo?: string;
  fromWarehouseId: string | number;
  fromWarehouseName?: string;
  fromLocationId: string | number;
  fromLocationCode: string;
  toWarehouseId: string | number;
  toWarehouseName?: string;
  toLocationId: string | number;
  toLocationCode: string;
  putawayQty: string;
  status: PutawayTaskStatus;
  confirmedBy?: string;
  confirmedAt?: string;
  allowedActions?: AllowedAction[];
}

/**
 * 上架任务确认载荷
 */
export interface PutawayConfirmPayload {
  taskId: string | number;
  toLocationId: string | number;
  putawayQty: string;
}
