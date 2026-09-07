/**
 * 数据洞察、空间 GIS、综合看板与跨域异常中心 API 服务 (Insights API)
 * 遵循 docs/specs/40-gis-dashboard 规范契约与 luna-max 冻结接口
 * 纯粹直连真实后端 REST 接口，绝不捕获异常伪造假数据或模拟状态
 */

import request, { type ApiResponse } from "../utils/request";
import type {
  TraceabilityProjection,
  TraceabilityChainResult,
  TraceNode,
  SiteMapItem,
  SiteMapProjection,
  MapPoint,
  DashboardTimeRange,
  DashboardQuery,
  DashboardOverviewData,
  DashboardCardType,
  DashboardCardData,
  CardMetric,
  DashboardSummaryProjection,
  ExceptionCenterPage,
  CreateSiteMapCommand,
  SaveMapPointCommand,
} from "../types/insights";

const UUID_PATTERN = /^[0-9a-f]{8}-[0-9a-f]{4}-[1-5][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/i;

/** 校验冻结接口要求的 UUID，禁止将业务编码误作实体标识提交。 */
function requireUuid(value: string | number | undefined, field: string): string {
  const normalized = String(value || "").trim();
  if (!UUID_PATTERN.test(normalized)) {
    throw new Error(`${field} 必须是后端已分配的 UUID，不能使用业务编码`);
  }
  return normalized;
}

/** 将服务端 TraceNode 映射为页面节点，保留事实状态和权限裁剪结果。 */
function mapTraceNode(node: any): TraceNode {
  const entityType = String(node.entity_type || node.entityType || "").toUpperCase();
  const entityId = String(node.entity_id || node.entityId || "");
  const routeByType: Record<string, string> = {
    SALES_ORDER: `/sales/orders/${entityId}`,
    PURCHASE_ORDER: `/purchasing/orders/${entityId}`,
    WORK_ORDER: `/mes/work-orders/${entityId}`,
    OPERATION_EXECUTION: "/mes/executions",
    DEVICE_ALARM: `/iot/alarms/${entityId}`,
    INVENTORY_TRANSACTION: "/inventory/transactions",
    INVENTORY_BATCH: "/inventory/transactions",
    SHIPMENT: "/sales/shipments",
  };
  const complete = node.complete !== false;
  return {
    id: entityId,
    nodeType: entityType as TraceNode["nodeType"],
    nodeCode: node.label || entityId,
    title: node.label || entityType || entityId,
    timestamp: node.source_updated_at || node.sourceUpdatedAt || "",
    status: node.status || "",
    isGap: !complete,
    gapReason: complete ? undefined : "后端事实源标记该节点信息不完整",
    hasPermission: true,
    linkedRoute: routeByType[entityType],
    details: [
      { label: "实体类型", value: node.entity_type || node.entityType || "" },
      { label: "所需权限", value: node.required_permission || node.requiredPermission || "" },
    ].filter((item) => item.value !== ""),
  };
}

/** 统一映射 GIS 点位投影的 snake_case 字段。 */
function mapPointProjection(point: any, siteMapId: string): MapPoint {
  return {
    id: String(point.point_id || point.pointId),
    siteMapId,
    entityType: point.entity_type || point.entityType,
    entityId: String(point.entity_id || point.entityId),
    pointName: point.display_name || point.displayName || "",
    xPercent: Number(point.x_percent ?? point.xPercent),
    yPercent: Number(point.y_percent ?? point.yPercent),
    rotation: Number(point.rotation || 0),
    displayStatus: (point.display_status || point.displayStatus || "Normal") as MapPoint["displayStatus"],
    statusText: point.display_status || point.displayStatus || "",
    linkedPage: point.linked_page || point.linkedPage || undefined,
    sourceUpdatedAt: point.source_updated_at || point.sourceUpdatedAt || "",
    alarmMarker: point.alarm_id || point.alarmId ? {
      alarmId: String(point.alarm_id || point.alarmId),
      pointId: String(point.point_id || point.pointId),
      alarmLevel: (point.alarm_level || point.alarmLevel) as any,
      alarmStatus: point.alarm_status || point.alarmStatus || "",
      occurredAt: point.occurred_at || point.occurredAt || "",
      sourceUpdatedAt: point.source_updated_at || point.sourceUpdatedAt || "",
    } : undefined,
  };
}

/**
 * 将看板查询入参规范化为后端要求的 snake_case
 */
function normalizeDashboardParams(query: DashboardQuery = {}): Record<string, any> {
  const params: Record<string, any> = {};
  if (query.time_range || query.timeRange) {
    params.time_range = query.time_range || query.timeRange;
  }
  if (query.warehouse_id || query.warehouseId) {
    params.warehouse_id = query.warehouse_id || query.warehouseId;
  }
  if (query.production_area_id || query.areaId) {
    params.production_area_id = query.production_area_id || query.areaId;
  }
  if (query.device_id || query.deviceId) {
    params.device_id = query.device_id || query.deviceId;
  }
  return params;
}

/**
 * 查询全链路追溯拓扑
 * 接口路径：GET /api/traceability?entity_type=&entity_id=
 * 严格使用 snake_case 查询参数，出参为 TraceabilityProjection
 */
export async function getTraceability(params: {
  entity_type: string;
  entity_id: string;
}): Promise<ApiResponse<TraceabilityProjection>> {
  return await request<TraceabilityProjection>({
    url: "/api/traceability",
    method: "GET",
    params: {
      entity_type: params.entity_type,
      entity_id: params.entity_id,
    },
  });
}

/**
 * 适配视图层 TraceabilityView 的追溯链查询
 * 接口路径：GET /api/traceability
 */
export async function fetchTraceabilityChain(query: { entity_type: string; entity_id: string; direction?: "FORWARD" | "REVERSE" }): Promise<TraceabilityChainResult> {
  const entityType = query.entity_type.trim();
  const entityId = requireUuid(query.entity_id, "entity_id");

  const res = await request<TraceabilityProjection>({
    url: "/api/traceability",
    method: "GET",
    params: {
      entity_type: entityType,
      entity_id: entityId,
    },
  });

  const proj = res.data;
  const nodes = (proj?.nodes || []).map(mapTraceNode);
  const missingSources = proj?.missing_sources || [];
  const incompleteCount = nodes.filter((node) => node.isGap).length;
  const completedCount = nodes.filter((node) => !node.isGap).length;

  return {
    queryTarget: {
      type: entityType,
      code: entityId,
      direction: query.direction || "FORWARD",
    },
    nodes,
    coverageRate: (proj as any)?.coverage !== undefined
      ? String((proj as any).coverage)
      : `${completedCount}/${nodes.length}`,
    hasBrokenLinks: missingSources.length + incompleteCount > 0 || !!proj?.truncated,
    brokenCount: missingSources.length + incompleteCount,
    missingSources,
    hiddenNodeCount: proj?.hidden_node_count || 0,
    truncated: !!proj?.truncated,
    generatedAt: proj?.generated_at,
    sourceUpdatedAt: proj?.source_updated_at,
    sourceSummary: `已追溯 ${nodes.length} 个跨域节点`,
  };
}

/**
 * 查询当前租户二维 GIS 站点地图列表
 * 接口路径：GET /api/site-maps
 */
export async function fetchSiteMapList(query: any = {}): Promise<SiteMapItem[]> {
  const res = await request<any[]>({
    url: "/api/site-maps",
    method: "GET",
    params: query,
  });
  const records = Array.isArray(res.data) ? res.data : ((res.data as any)?.records || []);
  return records.map((item: any) => ({
    id: item.id,
    mapCode: item.mapCode || item.map_code,
    mapName: item.mapName || item.map_name,
    backgroundType: item.asset?.mimeType || item.asset?.mime_type || "",
    backgroundUrl: item.asset?.storageKey || item.asset?.storage_key,
    createdAt: item.createdAt || item.created_at,
    updatedAt: item.updatedAt || item.updated_at,
  }));
}

/**
 * 获取指定地图投影及已配置点位
 * 接口路径：GET /api/site-maps/{siteMapId}/projection
 */
export async function fetchSiteMapProjection(
  siteMapId: string | number | { siteMapId: string | number; [key: string]: any },
  params?: any
): Promise<SiteMapProjection> {
  const rawTargetId = typeof siteMapId === "object" ? ((siteMapId as any).siteMapId || (siteMapId as any).id || "") : siteMapId;
  const targetId = requireUuid(rawTargetId, "siteMapId");
  const res = await request<SiteMapProjection>({
    url: `/api/site-maps/${targetId}/projection`,
    method: "GET",
    params,
  });
  const projection = res.data as any;
  const normalizedMapId = String(projection.site_map_id || projection.siteMapId || targetId);
  return {
    siteMapId: normalizedMapId,
    mapCode: projection.map_code || projection.mapCode,
    mapName: projection.map_name || projection.mapName,
    backgroundType: projection.background_type || projection.backgroundType,
    storageKey: projection.storage_key || projection.storageKey,
    points: (projection.points || []).map((point: any) => mapPointProjection(point, normalizedMapId)),
    generatedAt: projection.generated_at || projection.generatedAt,
    requestId: projection.request_id || projection.requestId,
  };
}

/**
 * 创建新站点地图配置
 * 接口路径：POST /api/site-maps
 */
export async function createSiteMap(payload: CreateSiteMapCommand): Promise<ApiResponse<SiteMapItem>> {
  const res = await request<any>({
    url: "/api/site-maps",
    method: "POST",
    data: payload,
  });
  const item = res.data;
  return {
    ...res,
    data: {
      id: item.id,
      mapCode: item.mapCode || item.map_code,
      mapName: item.mapName || item.map_name,
      backgroundType: item.asset?.mimeType || item.asset?.mime_type || "",
      backgroundUrl: item.asset?.storageKey || item.asset?.storage_key,
      createdAt: item.createdAt || item.created_at,
      updatedAt: item.updatedAt || item.updated_at,
    },
  };
}

/**
 * 保存或更新地图点位
 * 接口路径：POST /api/site-map/points 或 PUT /api/site-map/points/{pointId}
 * 正式参数：siteMapId, entityType, entityId, xPercent, yPercent, rotation, linkedPage
 */
export async function saveMapPoint(point: any): Promise<MapPoint> {
  const command: SaveMapPointCommand = {
    siteMapId: requireUuid(point.siteMapId, "siteMapId"),
    entityType: point.entityType,
    entityId: requireUuid(point.entityId, "entityId"),
    xPercent: Number(point.xPercent),
    yPercent: Number(point.yPercent),
    rotation: point.rotation !== undefined ? Number(point.rotation) : undefined,
    linkedPage: point.linkedPage,
  };

  if (point.id && !String(point.id).startsWith("NEW-")) {
    const res = await request<MapPoint>({
      url: `/api/site-map/points/${point.id}`,
      method: "PUT",
      data: command,
    });
    return mapPointProjection(res.data, command.siteMapId);
  }

  const res = await request<MapPoint>({
    url: "/api/site-map/points",
    method: "POST",
    data: command,
  });
  return mapPointProjection(res.data, command.siteMapId);
}

/**
 * 逻辑软删除站点地图上的指定点位
 * 接口路径：DELETE /api/site-map/points/{pointId}
 */
export async function deleteMapPoint(pointId: string | number): Promise<void> {
  await request<void>({
    url: `/api/site-map/points/${pointId}`,
    method: "DELETE",
  });
}

/**
 * 1. 查询仓储库存域看板指标
 * 接口路径：GET /api/dashboard/inventory?time_range=&warehouse_id=
 */
export async function getInventoryDashboard(query: DashboardQuery = {}): Promise<ApiResponse<DashboardSummaryProjection>> {
  return await request<DashboardSummaryProjection>({
    url: "/api/dashboard/inventory",
    method: "GET",
    params: normalizeDashboardParams(query),
  });
}

/**
 * 2. 查询采购与销售履约域看板指标
 * 接口路径：GET /api/dashboard/fulfillment?time_range=
 */
export async function getFulfillmentDashboard(query: DashboardQuery = {}): Promise<ApiResponse<DashboardSummaryProjection>> {
  return await request<DashboardSummaryProjection>({
    url: "/api/dashboard/fulfillment",
    method: "GET",
    params: normalizeDashboardParams(query),
  });
}

/**
 * 3. 查询制造执行域看板指标
 * 接口路径：GET /api/dashboard/manufacturing?time_range=&production_area_id=
 */
export async function getManufacturingDashboard(query: DashboardQuery = {}): Promise<ApiResponse<DashboardSummaryProjection>> {
  return await request<DashboardSummaryProjection>({
    url: "/api/dashboard/manufacturing",
    method: "GET",
    params: normalizeDashboardParams(query),
  });
}

/**
 * 4. 查询质量管控域看板指标
 * 接口路径：GET /api/dashboard/quality?time_range=
 */
export async function getQualityDashboard(query: DashboardQuery = {}): Promise<ApiResponse<DashboardSummaryProjection>> {
  return await request<DashboardSummaryProjection>({
    url: "/api/dashboard/quality",
    method: "GET",
    params: normalizeDashboardParams(query),
  });
}

/**
 * 5. 查询 IoT 设备域看板指标
 * 接口路径：GET /api/dashboard/device?time_range=&device_id=
 */
export async function getDeviceDashboard(query: DashboardQuery = {}): Promise<ApiResponse<DashboardSummaryProjection>> {
  return await request<DashboardSummaryProjection>({
    url: "/api/dashboard/device",
    method: "GET",
    params: normalizeDashboardParams(query),
  });
}

/**
 * 6. 查询告警监控域看板指标
 * 接口路径：GET /api/dashboard/alarms?time_range=&device_id=
 */
export async function getAlarmsDashboard(query: DashboardQuery = {}): Promise<ApiResponse<DashboardSummaryProjection>> {
  return await request<DashboardSummaryProjection>({
    url: "/api/dashboard/alarms",
    method: "GET",
    params: normalizeDashboardParams(query),
  });
}

/**
 * 7. 查询全链路追溯域看板指标
 * 接口路径：GET /api/dashboard/traceability?time_range=
 */
export async function getTraceabilityDashboard(query: DashboardQuery = {}): Promise<ApiResponse<DashboardSummaryProjection>> {
  return await request<DashboardSummaryProjection>({
    url: "/api/dashboard/traceability",
    method: "GET",
    params: normalizeDashboardParams(query),
  });
}

/**
 * 查询跨域异常中心分页事实列表
 * 接口路径：GET /api/exception-center?time_range=&source=&severity=&page=1&size=20
 */
export async function getExceptionCenter(query: {
  time_range?: string;
  source?: string;
  severity?: string;
  page?: number;
  size?: number;
} = {}): Promise<ApiResponse<ExceptionCenterPage>> {
  return await request<ExceptionCenterPage>({
    url: "/api/exception-center",
    method: "GET",
    params: {
      time_range: query.time_range,
      source: query.source,
      severity: query.severity,
      page: query.page || 1,
      size: query.size || 20,
    },
  });
}

/**
 * 跨 7 大事实域并发聚合快照指标，用于综合监控看板
 * 遵循 docs/specs/40-gis-dashboard 规范契约，使用 Promise.allSettled 保证单卡片异常互不影响
 */
export async function fetchDashboardOverview(query: {
  timeRange?: DashboardTimeRange;
  time_range?: DashboardTimeRange;
  degradedDomains?: DashboardCardType[];
  simulateState?: string;
} = {}): Promise<DashboardOverviewData> {
  const timeRange: DashboardTimeRange = query.timeRange || (query.time_range as DashboardTimeRange) || "today";
  const timeRangeLabelMap: Record<DashboardTimeRange, string> = {
    today: "今日 (00:00 - 23:59)",
    "7d": "近 7 天累计",
    "30d": "近 30 天累计",
  };

  // 7 个正式端点并发调用
  const [
    invRes,
    fulRes,
    mfgRes,
    quaRes,
    devRes,
    almRes,
    traRes,
  ] = await Promise.allSettled([
    getInventoryDashboard({ time_range: timeRange }),
    getFulfillmentDashboard({ time_range: timeRange }),
    getManufacturingDashboard({ time_range: timeRange }),
    getQualityDashboard({ time_range: timeRange }),
    getDeviceDashboard({ time_range: timeRange }),
    getAlarmsDashboard({ time_range: timeRange }),
    getTraceabilityDashboard({ time_range: timeRange }),
  ]);

  /**
   * 将单个领域请求转换为看板卡片：请求失败只标记该卡片的错误，不阻断其他领域；
   * 成功时仅映射后端实际返回的指标，避免用前端计算结果冒充事实源。
   */
  function buildCardData(
    type: DashboardCardType,
    title: string,
    icon: string,
    res: PromiseSettledResult<ApiResponse<DashboardSummaryProjection>>,
    linkedRoute: string
  ): DashboardCardData {
    if (res.status === "rejected") {
      return {
        summaryType: type,
        title,
        icon,
        metrics: [],
        timeRange,
        sourceSummary: "数据源响应异常",
        generatedAt: undefined,
        sourceUpdatedAt: undefined,
        stale: undefined,
        staleSince: undefined,
        error: res.reason?.message || "请求失败",
        linkedRoute,
      };
    }

    const data = res.value.data;
    const metrics: CardMetric[] = Object.entries(data?.metrics || {}).map(([k, v]) => {
      const isQty = typeof v === "number" || (!isNaN(Number(v)) && String(v).trim() !== "");
      return {
        key: k,
        label: k,
        value: v !== null && v !== undefined ? String(v) : "0",
        isQuantity: isQty,
        status: "normal",
      };
    });

    return {
      summaryType: type,
      title,
      icon,
      metrics,
      timeRange: data?.time_range || timeRange,
      sourceSummary: data?.source_summary || `已聚合 ${metrics.length} 项指标`,
      generatedAt: data?.generated_at,
      sourceUpdatedAt: data?.source_updated_at,
      stale: data?.stale,
      staleSince: data?.stale_since,
      linkedRoute,
    };
  }

  const cards: Record<DashboardCardType, DashboardCardData> = {
    inventory: buildCardData("inventory", "库存资产监控", "📦", invRes, "/inventory/balances"),
    fulfillment: buildCardData("fulfillment", "履约时效监控", "🚚", fulRes, "/sales/orders"),
    manufacturing: buildCardData("manufacturing", "生产执行监控", "⚙️", mfgRes, "/mes/work-orders"),
    quality: buildCardData("quality", "质量合格监控", "🔍", quaRes, "/purchasing/quality"),
    device: buildCardData("device", "设备健康度监控", "📟", devRes, "/iot/devices"),
    alarm: buildCardData("alarm", "异常告警监控", "🚨", almRes, "/iot/alarms"),
    traceability: buildCardData("traceability", "追溯完整率监控", "🔗", traRes, "/traceability"),
  };

  const staleCardsCount = Object.values(cards).filter((c) => c.stale).length;

  return {
    timeRange,
    timeRangeLabel: timeRangeLabelMap[timeRange] || timeRange,
    generatedAt: Object.values(cards).map((card) => card.generatedAt).find(Boolean),
    sourceUpdatedAt: Object.values(cards).map((card) => card.sourceUpdatedAt).find(Boolean),
    cards,
    staleCardsCount,
  };
}
