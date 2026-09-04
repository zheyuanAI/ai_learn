/**
 * 数据洞察、二维 GIS 站点图与综合监控看板领域类型定义 (Insights & GIS Types)
 * 严格遵循 docs/specs/40-gis-dashboard 规范与业务规则：
 * 1. 点位使用相对百分比坐标 (xPercent, yPercent)，杜绝页面缩放偏移；
 * 2. 状态展示优先级：Alarm (告警) > Offline (离线) > Warning (预警) > Normal (正常)；
 * 3. 黄金业务闭环 7 大事实域（库存、履约、制造、质量、设备、告警、追溯）指标汇总与陈旧数据 (stale) 降级；
 * 4. 追溯链节点支持正向/反向追溯及断链缺口卡片 (isGap)。
 */

import type { BadgeType } from "./common";

/**
 * 追溯节点类型枚举
 * 覆盖从销售需求、工单、工序、领料批次、质检、采购到设备告警的完整闭环
 */
export type TraceNodeType =
  | "SALES_ORDER"         // 销售订单
  | "WORK_ORDER"          // 制造工单
  | "OPERATION_EXECUTION" // 工序执行
  | "INVENTORY_BATCH"     // 库存批次 / 领退料
  | "QUALITY_INSPECT"     // 质检报告
  | "PURCHASE_ORDER"      // 采购订单 / 入库
  | "DEVICE_ALARM"        // 设备告警关联
  | "SHIPMENT"            // 发货出库
  | "GAP_NODE";           // 缺失来源标识缺口卡片

/**
 * 追溯查询方向
 * FORWARD: 正向追溯 (从需求/采购推导至成品/发货)
 * REVERSE: 反向追溯 (从告警/成品反查工单与原料批次)
 */
export type TraceDirection = "FORWARD" | "REVERSE";

/**
 * 追溯节点指标属性键值
 */
export interface TraceNodeDetailItem {
  label: string;
  value: string | number;
  isQuantity?: boolean;
  unit?: string;
  warn?: boolean;
}

/**
 * 追溯链节点模型 (TraceNode)
 * 支持正向/反向链条展示；对缺失来源标识缺口卡片；节点有权时支持路由跳转
 */
export interface TraceNode {
  id: string;
  nodeType: TraceNodeType;
  nodeCode: string;
  title: string;
  timestamp: string;
  status: string;
  statusType?: BadgeType;
  isGap?: boolean;
  gapReason?: string;
  hasPermission: boolean;
  linkedRoute?: string;
  details: TraceNodeDetailItem[];
  upstreamIds?: string[];
  downstreamIds?: string[];
}

/**
 * 追溯链查询响应结果包装
 */
export interface TraceabilityChainResult {
  queryTarget: {
    type: TraceNodeType | string;
    code: string;
    direction: TraceDirection;
  };
  nodes: TraceNode[];
  coverageRate: string;
  hasBrokenLinks: boolean;
  brokenCount: number;
  generatedAt: string;
  sourceSummary: string;
}

/**
 * 点位状态枚举
 * 优先级约束：Alarm > Offline > Warning > Normal
 */
export type MapPointStatus = "Normal" | "Warning" | "Alarm" | "Offline";

/**
 * 地图点位类型：仓库、生产区域、设备
 */
export type MapEntityType = "WAREHOUSE" | "PRODUCTION_AREA" | "DEVICE";

/**
 * 地图点位业务指标项
 */
export interface MapPointMetric {
  label: string;
  value: string;
  warn?: boolean;
}

/**
 * 告警标记投影
 */
export interface AlarmMarkerProjection {
  alarmId: string;
  pointId: string;
  alarmLevel: "CRITICAL" | "WARN" | "INFO";
  alarmStatus: string;
  occurredAt: string;
  sourceUpdatedAt: string;
}

/**
 * 二维地图点位投影 (MapPoint)
 * 严格基于受控底图与百分比坐标 (xPercent, yPercent) 渲染
 */
export interface MapPoint {
  id: string;
  siteMapId: string | number;
  entityType: MapEntityType;
  entityId: string;
  pointName: string;
  xPercent: number; // 0-100 百分比
  yPercent: number; // 0-100 百分比
  rotation?: number; // 旋转角度
  displayStatus: MapPointStatus;
  statusText: string;
  linkedPage?: string;
  sourceUpdatedAt: string;
  detail?: string;
  metrics?: MapPointMetric[];
  alarmMarker?: AlarmMarkerProjection;
}

/**
 * 二维站点地图配置实体
 */
export interface SiteMapItem {
  id: string | number;
  mapCode: string;
  mapName: string;
  backgroundType: "SVG" | "IMAGE" | "GRID";
  backgroundUrl?: string;
  width?: number;
  height?: number;
  pointCount?: number;
  description?: string;
  createdAt?: string;
  updatedAt?: string;
}

/**
 * 二维地图投影组合 (SiteMapProjection)
 */
export interface SiteMapProjection {
  siteMapId: string | number;
  mapCode: string;
  mapName: string;
  backgroundType: string;
  backgroundUrl?: string;
  points: MapPoint[];
  generatedAt: string;
}

/**
 * 看板时间范围支持类型
 */
export type DashboardTimeRange = "today" | "7d" | "30d";

/**
 * 七大事实域看板分类
 */
export type DashboardCardType =
  | "inventory"     // 库存
  | "fulfillment"   // 采购与销售履约
  | "manufacturing" // 制造执行
  | "quality"       // 质量检验
  | "device"        // IoT 设备
  | "alarm"         // 告警监控
  | "traceability";  // 业务追溯

/**
 * 看板单个核心指标项 (CardMetric)
 */
export interface CardMetric {
  key: string;
  label: string;
  value: string | number;
  unit?: string;
  status?: "normal" | "warning" | "danger" | "info";
  trend?: {
    direction: "up" | "down" | "flat";
    rate?: string;
  };
  subText?: string;
  isQuantity?: boolean;
}

/**
 * 单个看板卡片的数据投影 (DashboardCardData)
 * 包含陈旧数据标记 (stale) 与陈旧时间戳 (staleSince)
 */
export interface DashboardCardData {
  summaryType: DashboardCardType;
  title: string;
  icon: string;
  metrics: CardMetric[];
  timeRange: string;
  sourceSummary: string;
  generatedAt: string;
  sourceUpdatedAt: string;
  stale: boolean;
  staleSince?: string;
  error?: string;
  linkedRoute?: string;
}

/**
 * 综合监控看板全景汇总投影 (DashboardOverviewData)
 */
export interface DashboardOverviewData {
  timeRange: DashboardTimeRange;
  timeRangeLabel: string;
  generatedAt: string;
  sourceUpdatedAt: string;
  cards: Record<DashboardCardType, DashboardCardData>;
  staleCardsCount: number;
}

export type MapPointCreatePayload = Partial<MapPoint>;
export type DomainSummaryMetric = DashboardCardData;
export type DashboardQuery = { timeRange?: DashboardTimeRange; warehouseId?: string; areaId?: string; deviceId?: string };

