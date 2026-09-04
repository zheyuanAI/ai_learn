/**
 * MES 制造执行领域类型定义 (Manufacturing Execution System Types)
 * 遵循阶段 5 前端实施规范与契约规范 docs/specs/20-mes：
 * 1. 数量必须使用字符串 (string) 传输与展示，杜绝 JavaScript 浮点精度损失
 * 2. 操作入口依据 allowedActions 数组判定启用/禁用状态及原因
 * 3. 严格匹配 WorkOrder、DispatchOrder、OperationExecution 等状态机流转
 */

import type { AllowedAction, BaseEntity, PageQuery } from "./common";

/**
 * BOM 状态枚举
 */
export type BomStatus = "DRAFT" | "ACTIVE" | "DISABLED";

/**
 * BOM 物料明细子项
 */
export interface BomComponent {
  id?: string;
  bomId?: string;
  componentProductId: string;
  componentProductName?: string;
  componentProductCode?: string;
  componentProductSpec?: string;
  /** 组件标准用量 (string 表达高精度数量) */
  componentQty: string;
  /** 计量单位 (UOM) */
  uom: string;
  /** 损耗率 (百分比，例如 '0.02' 表示 2%) */
  scrapRate?: string;
  remark?: string;
}

/**
 * BOM 物料清单主项模型
 */
export interface BomItem extends BaseEntity {
  bomCode: string;
  productId: string;
  productName?: string;
  productCode?: string;
  version: string;
  status: BomStatus;
  components: BomComponent[];
  allowedActions?: AllowedAction[];
}

/**
 * 创建 BOM 入参
 */
export interface BomCreateRequest {
  bomCode: string;
  productId: string;
  version: string;
  status?: BomStatus;
  components: Array<{
    componentProductId: string;
    componentQty: string;
    uom: string;
    scrapRate?: string;
    remark?: string;
  }>;
}

/**
 * 更新 BOM 入参
 */
export interface BomUpdateRequest {
  version?: string;
  status?: BomStatus;
  components?: Array<{
    componentProductId: string;
    componentQty: string;
    uom: string;
    scrapRate?: string;
    remark?: string;
  }>;
}

/**
 * 工艺路线状态枚举
 */
export type RoutingStatus = "DRAFT" | "ACTIVE" | "DISABLED";

/**
 * 工艺路线工序定义子项
 */
export interface OperationItem {
  id?: string;
  routingId?: string;
  /** 工序序号，例如 10, 20, 30 */
  operationNo: number;
  /** 工序名称，例如 切割、焊接、组装、检验 */
  operationName: string;
  /** 关联工作中心 ID */
  workCenterId: string;
  workCenterName?: string;
  /** 标准工时（分钟，字符串表示） */
  standardTimeMinutes?: string;
  remark?: string;
}

/**
 * 工艺路线主项模型
 */
export interface RoutingItem extends BaseEntity {
  routingCode: string;
  productId: string;
  productName?: string;
  productCode?: string;
  version: string;
  status: RoutingStatus;
  operations: OperationItem[];
  allowedActions?: AllowedAction[];
}

/**
 * 创建工艺路线入参
 */
export interface RoutingCreateRequest {
  routingCode: string;
  productId: string;
  version: string;
  status?: RoutingStatus;
  operations: Array<{
    operationNo: number;
    operationName: string;
    workCenterId: string;
    standardTimeMinutes?: string;
    remark?: string;
  }>;
}

/**
 * 更新工艺路线入参
 */
export interface RoutingUpdateRequest {
  version?: string;
  status?: RoutingStatus;
  operations?: Array<{
    operationNo: number;
    operationName: string;
    workCenterId: string;
    standardTimeMinutes?: string;
    remark?: string;
  }>;
}

/**
 * 工单状态机枚举：
 * Draft(未提交) -> PendingApproval(待审核) -> Released(已下达) -> InProgress(生产中) -> Completed(已完成)
 * 审核退回: PendingApproval -> Rejected(审核拒绝)
 */
export type WorkOrderStatus =
  | "Draft"
  | "PendingApproval"
  | "Released"
  | "InProgress"
  | "Completed"
  | "Rejected";

/**
 * 工单完工类型枚举
 */
export type WorkOrderCompletionType = "Normal" | "Manual";

/**
 * 生产工单模型
 */
export interface WorkOrderItem extends BaseEntity {
  workOrderNo: string;
  productId: string;
  productName?: string;
  productCode?: string;
  productSpec?: string;
  /** 计划生产数量 (string 保持精度) */
  plannedQty: string;
  /** 计划开始时间 */
  plannedStartTime: string;
  /** 计划完成时间 */
  plannedFinishTime: string;
  /** 关联 BOM ID 与锁定版本 */
  bomId: string;
  bomCode?: string;
  bomVersion?: string;
  /** 关联工艺路线 ID 与锁定版本 */
  routingId: string;
  routingCode?: string;
  routingVersion?: string;
  /** 来源销售订单行 ID（可选，由用户显式选择，仅用于追溯不形成库存预留） */
  sourceSalesOrderLineId?: string;
  sourceSalesOrderNo?: string;
  /** 工单状态 */
  status: WorkOrderStatus;
  /** 提交与审核信息 */
  submittedBy?: string;
  submittedAt?: string;
  reviewedBy?: string;
  reviewedAt?: string;
  rejectionReason?: string;
  /** 完工信息 */
  completionType?: WorkOrderCompletionType;
  completionReason?: string;
  completedBy?: string;
  completedAt?: string;
  /** 数量统计汇总 (全字符串传输) */
  reportedQty: string;
  qualifiedQty: string;
  defectQty: string;
  receivedQty: string;
  allowedActions?: AllowedAction[];
}

/**
 * 创建工单入参
 */
export interface WorkOrderCreateRequest {
  productId: string;
  plannedQty: string;
  plannedStartTime: string;
  plannedFinishTime: string;
  bomId: string;
  routingId: string;
  sourceSalesOrderLineId?: string;
}

/**
 * 修改工单入参 (仅在 Draft 或 Rejected 状态下允许)
 */
export interface WorkOrderUpdateRequest {
  productId?: string;
  plannedQty?: string;
  plannedStartTime?: string;
  plannedFinishTime?: string;
  bomId?: string;
  routingId?: string;
  sourceSalesOrderLineId?: string;
}

/**
 * 派工单状态枚举:
 * Draft(草稿) -> Released(已下达) -> Processing(执行中) -> Completed(已完成)
 */
export type DispatchOrderStatus = "Draft" | "Released" | "Processing" | "Completed";

/**
 * 派工单模型
 */
export interface DispatchOrderItem extends BaseEntity {
  dispatchNo: string;
  workOrderId: string;
  workOrderNo?: string;
  productName?: string;
  operationId: string;
  operationNo?: number;
  operationName?: string;
  operatorId: string;
  operatorName?: string;
  /** 派工数量 (string 保持精度) */
  dispatchQty: string;
  /** 可选设备 ID */
  deviceId?: string;
  deviceName?: string;
  deviceCode?: string;
  status: DispatchOrderStatus;
  allowedActions?: AllowedAction[];
}

/**
 * 创建派工单入参
 */
export interface DispatchOrderCreateRequest {
  workOrderId: string;
  operationId: string;
  operatorId: string;
  dispatchQty: string;
  deviceId?: string;
}

/**
 * 工序实际执行实例状态机枚举:
 * NotStarted -> Running -> Paused -> Running -> Completed
 */
export type OperationExecutionStatus = "NotStarted" | "Running" | "Paused" | "Completed";

/**
 * 工序执行模型
 */
export interface OperationExecutionItem extends BaseEntity {
  executionNo: string;
  dispatchOrderId: string;
  dispatchNo?: string;
  workOrderId: string;
  workOrderNo?: string;
  productName?: string;
  operationId: string;
  operationNo?: number;
  operationName?: string;
  operatorId: string;
  operatorName?: string;
  /** 可选关联设备 ID */
  deviceId?: string;
  deviceName?: string;
  deviceCode?: string;
  status: OperationExecutionStatus;
  /** 实际时间记录 */
  startedAt?: string;
  pausedAt?: string;
  resumedAt?: string;
  completedAt?: string;
  /** 累计报工数量 (string) */
  reportedQty?: string;
  allowedActions?: AllowedAction[];
}

/**
 * 创建工序执行实例入参
 */
export interface OperationExecutionCreateRequest {
  dispatchOrderId: string;
  deviceId?: string;
}

/**
 * 报工记录模型
 */
export interface WorkReportItem extends BaseEntity {
  reportNo: string;
  operationExecutionId: string;
  executionNo?: string;
  workOrderId: string;
  workOrderNo?: string;
  operationId: string;
  operationName?: string;
  reportTime: string;
  /** 申报合格数量 (string) */
  qualifiedQty: string;
  /** 申报不良数量 (string) */
  defectQty: string;
  /** 派生总报工数量 = qualifiedQty + defectQty (string) */
  reportQty: string;
  remark?: string;
  allowedActions?: AllowedAction[];
}

/**
 * 创建报工入参
 */
export interface WorkReportCreateRequest {
  operationExecutionId: string;
  reportTime: string;
  qualifiedQty: string;
  defectQty: string;
  remark?: string;
}

/**
 * 质检检验结果
 */
export type QualityResult = "Draft" | "Passed" | "Failed";

/**
 * 质量检验模型
 */
export interface QualityInspectionItem extends BaseEntity {
  inspectionNo: string;
  workReportId: string;
  reportNo?: string;
  workOrderId: string;
  workOrderNo?: string;
  operationId: string;
  operationName?: string;
  inspectionType: "FIRST_ARTICLE" | "PATROL" | "FINAL" | string;
  /** 抽样数量 (string) */
  sampleQty: string;
  /** 检验合格数量 (string) */
  qualifiedQty: string;
  /** 检验不良数量 (string) */
  defectQty: string;
  result: QualityResult;
  inspectorId?: string;
  inspectorName?: string;
  inspectedAt?: string;
  remark?: string;
  allowedActions?: AllowedAction[];
}

/**
 * 创建质检单入参
 */
export interface QualityInspectionCreateRequest {
  workReportId: string;
  inspectionType: string;
  sampleQty: string;
  remark?: string;
}

/**
 * 提交质检结果入参
 */
export interface QualityInspectionSubmitRequest {
  qualifiedQty: string;
  defectQty: string;
  result: "Passed" | "Failed";
  remark?: string;
}

/**
 * 领料/退料单据状态
 */
export type MaterialMovementStatus = "Draft" | "Confirmed";

/**
 * 生产领料单明细
 */
export interface MaterialIssueDetail {
  id?: string;
  productId: string;
  productCode?: string;
  productName?: string;
  productSpec?: string;
  warehouseId: string;
  warehouseName?: string;
  locationId: string;
  locationCode?: string;
  /** 领料数量 (string) */
  issueQty: string;
  uom: string;
}

/**
 * 生产领料单模型
 */
export interface MaterialIssueItem extends BaseEntity {
  issueNo: string;
  workOrderId: string;
  workOrderNo?: string;
  status: MaterialMovementStatus;
  confirmedAt?: string;
  confirmedBy?: string;
  /** 库存流水关联标识（由库存模块返回） */
  inventoryTransactionId?: string;
  items: MaterialIssueDetail[];
  allowedActions?: AllowedAction[];
}

/**
 * 创建领料单入参
 */
export interface MaterialIssueCreateRequest {
  workOrderId: string;
  items: Array<{
    productId: string;
    warehouseId: string;
    locationId: string;
    issueQty: string;
  }>;
}

/**
 * 生产退料单明细
 */
export interface MaterialReturnDetail {
  id?: string;
  productId: string;
  productCode?: string;
  productName?: string;
  productSpec?: string;
  warehouseId: string;
  warehouseName?: string;
  locationId: string;
  locationCode?: string;
  /** 退料数量 (string) */
  returnQty: string;
  uom: string;
}

/**
 * 生产退料单模型
 */
export interface MaterialReturnItem extends BaseEntity {
  returnNo: string;
  workOrderId: string;
  workOrderNo?: string;
  status: MaterialMovementStatus;
  confirmedAt?: string;
  confirmedBy?: string;
  /** 库存流水关联标识 */
  inventoryTransactionId?: string;
  items: MaterialReturnDetail[];
  allowedActions?: AllowedAction[];
}

/**
 * 创建退料单入参
 */
export interface MaterialReturnCreateRequest {
  workOrderId: string;
  items: Array<{
    productId: string;
    warehouseId: string;
    locationId: string;
    returnQty: string;
  }>;
}

/**
 * 成品入库单模型
 */
export interface FinishedGoodsReceiptItem extends BaseEntity {
  receiptNo: string;
  workOrderId: string;
  workOrderNo?: string;
  productId?: string;
  productName?: string;
  productCode?: string;
  /** 入库数量 (string) */
  receiptQty: string;
  warehouseId: string;
  warehouseName?: string;
  locationId: string;
  locationCode?: string;
  status: "Draft" | "Confirmed";
  confirmedAt?: string;
  confirmedBy?: string;
  /** 库存流水关联标识 */
  inventoryTransactionId?: string;
  allowedActions?: AllowedAction[];
}

/**
 * 创建成品入库单入参
 */
export interface FinishedGoodsReceiptCreateRequest {
  workOrderId: string;
  receiptQty: string;
  warehouseId: string;
  locationId: string;
}

/**
 * 工单查询过滤条件
 */
export interface WorkOrderQueryParams extends PageQuery {
  workOrderNo?: string;
  productId?: string;
  status?: WorkOrderStatus | "";
  sourceSalesOrderLineId?: string;
  plannedDateFrom?: string;
  plannedDateTo?: string;
}

/**
 * 派工单查询过滤条件
 */
export interface DispatchOrderQueryParams extends PageQuery {
  workOrderId?: string;
  operationId?: string;
  operatorId?: string;
  status?: DispatchOrderStatus | "";
}

/**
 * 工序执行查询过滤条件
 */
export interface OperationExecutionQueryParams extends PageQuery {
  workOrderId?: string;
  operationId?: string;
  deviceId?: string;
  status?: OperationExecutionStatus | "";
  dateFrom?: string;
  dateTo?: string;
}

export type Bom = BomItem;
export type BomQuery = PageQuery & { keyword?: string; status?: string };
export type Routing = RoutingItem;
export type RoutingQuery = PageQuery & { keyword?: string; status?: string };
export type WorkOrder = WorkOrderItem;
export type WorkOrderQuery = WorkOrderQueryParams;
export type WorkOrderCreatePayload = WorkOrderCreateRequest;
export type DispatchRecord = DispatchOrderItem;
export type DispatchQuery = DispatchOrderQueryParams & { keyword?: string };
export type DispatchCreatePayload = DispatchOrderCreateRequest;
export type OperationExecution = OperationExecutionItem;
export type OperationExecutionQuery = OperationExecutionQueryParams & { keyword?: string };
export type WorkReportPayload = WorkReportCreateRequest;
export type MaterialMovement = MaterialIssueItem;
export type MaterialMovementQuery = PageQuery & { keyword?: string; movementType?: string; workOrderId?: string };
export type MaterialMovementPayload = any;
export type FinishedGoodsReceipt = FinishedGoodsReceiptItem;
export type FinishedGoodsReceiptQuery = PageQuery & { keyword?: string; workOrderId?: string };
export type FinishedGoodsReceiptPayload = any;

