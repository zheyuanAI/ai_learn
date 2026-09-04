/**
 * 数据洞察、空间 GIS 与综合看板业务 API 服务 (Insights API)
 * 提供全链路正反向追溯、二维 GIS 站点地图与七类综合监控看板接口
 * 遵循 docs/specs/40-gis-dashboard 规范契约，纯粹直连真实后端 REST 接口
 * 错误与陈旧状态完全由后端响应决定，前端各领域看板卡片独立请求互不掩盖异常
 */

import request, { type ApiResponse } from "../utils/request";
import type { PageResult } from "../types/common";
import type {
  TraceabilityChainResult,
  TraceNode,
  SiteMapItem,
  SiteMapProjection,
  MapPoint,
  MapPointCreatePayload,
  DashboardTimeRange,
  DashboardCardType,
  DashboardOverviewData,
  DomainSummaryMetric,
  DashboardQuery,
} from "../types/insights";

/**
 * 查询全链路追溯拓扑链（支持 SO / WO / ALARM / LOT 多入口切入）
 * 接口路径：GET /api/insights/traceability
 */
export async function fetchTraceabilityChain(query: any): Promise<TraceabilityChainResult> {
  const res = await request<TraceabilityChainResult>({
    url: "/api/insights/traceability",
    method: "GET",
    params: query,
  });
  return res.data;
}

/**
 * 分页查询二维 GIS 站点地图列表
 * 接口路径：GET /api/site-maps
 */
export async function fetchSiteMapList(query: any = {}): Promise<SiteMapItem[]> {
  const res = await request<any>({
    url: "/api/site-maps",
    method: "GET",
    params: query,
  });
  return Array.isArray(res.data) ? res.data : (res.data?.records || []);
}

/**
 * 获取站点地图投影及已配置点位
 * 接口路径：GET /api/site-maps/{id}/projection
 */
export async function fetchSiteMapProjection(idOrParams: any): Promise<SiteMapProjection> {
  const targetId = typeof idOrParams === "object" ? (idOrParams.siteMapId || idOrParams.id || "") : idOrParams;
  const res = await request<SiteMapProjection>({
    url: `/api/site-maps/${targetId}/projection`,
    method: "GET",
    params: typeof idOrParams === "object" ? idOrParams : undefined,
  });
  return res.data;
}

/**
 * 创建新站点地图配置
 * 接口路径：POST /api/site-maps
 */
export async function createSiteMap(payload: Partial<SiteMapItem>): Promise<ApiResponse<SiteMapItem>> {
  return await request<SiteMapItem>({
    url: "/api/site-maps",
    method: "POST",
    data: payload,
  });
}

/**
 * 保存或更新地图点位
 * 接口路径：POST /api/site-map/points 或 PUT /api/site-map/points/{id}
 */
export async function saveMapPoint(point: any): Promise<MapPoint> {
  if (point.id && !String(point.id).startsWith("NEW-")) {
    const res = await request<MapPoint>({
      url: `/api/site-map/points/${point.id}`,
      method: "PUT",
      data: point,
    });
    return res.data;
  }
  const res = await request<MapPoint>({
    url: "/api/site-map/points",
    method: "POST",
    data: point,
  });
  return res.data;
}

/**
 * 删除站点地图上的指定点位
 * 接口路径：DELETE /api/site-map/points/{pointId}
 */
export async function deleteMapPoint(pointId: string | number): Promise<void> {
  await request<void>({
    url: `/api/site-map/points/${pointId}`,
    method: "DELETE",
  });
}

/**
 * 查询综合监控看板全景概览
 * 接口路径：GET /api/dashboard/overview
 */
export async function fetchDashboardOverview(params: {
  timeRange: DashboardTimeRange;
  degradedDomains?: DashboardCardType[];
  simulateState?: any;
}): Promise<DashboardOverviewData> {
  const res = await request<DashboardOverviewData>({
    url: "/api/dashboard/overview",
    method: "GET",
    params,
  });
  return res.data;
}

/**
 * 查询仓储库存域看板指标
 * 接口路径：GET /api/dashboard/inventory
 */
export async function getInventoryDashboard(query: DashboardQuery = {}): Promise<ApiResponse<DomainSummaryMetric>> {
  return await request<DomainSummaryMetric>({
    url: "/api/dashboard/inventory",
    method: "GET",
    params: query,
  });
}

/**
 * 查询履约交运域看板指标
 * 接口路径：GET /api/dashboard/fulfillment
 */
export async function getFulfillmentDashboard(query: DashboardQuery = {}): Promise<ApiResponse<DomainSummaryMetric>> {
  return await request<DomainSummaryMetric>({
    url: "/api/dashboard/fulfillment",
    method: "GET",
    params: query,
  });
}

/**
 * 查询制造工序域看板指标
 * 接口路径：GET /api/dashboard/manufacturing
 */
export async function getManufacturingDashboard(query: DashboardQuery = {}): Promise<ApiResponse<DomainSummaryMetric>> {
  return await request<DomainSummaryMetric>({
    url: "/api/dashboard/manufacturing",
    method: "GET",
    params: query,
  });
}

/**
 * 查询质量管控域看板指标
 * 接口路径：GET /api/dashboard/quality
 */
export async function getQualityDashboard(query: DashboardQuery = {}): Promise<ApiResponse<DomainSummaryMetric>> {
  return await request<DomainSummaryMetric>({
    url: "/api/dashboard/quality",
    method: "GET",
    params: query,
  });
}

/**
 * 查询 IoT 设备域看板指标
 * 接口路径：GET /api/dashboard/device
 */
export async function getDeviceDashboard(query: DashboardQuery = {}): Promise<ApiResponse<DomainSummaryMetric>> {
  return await request<DomainSummaryMetric>({
    url: "/api/dashboard/device",
    method: "GET",
    params: query,
  });
}

/**
 * 查询告警监控域看板指标
 * 接口路径：GET /api/dashboard/alarms
 */
export async function getAlarmsDashboard(query: DashboardQuery = {}): Promise<ApiResponse<DomainSummaryMetric>> {
  return await request<DomainSummaryMetric>({
    url: "/api/dashboard/alarms",
    method: "GET",
    params: query,
  });
}

/**
 * 查询全链路追溯域看板指标
 * 接口路径：GET /api/dashboard/traceability
 */
export async function getTraceabilityDashboard(query: DashboardQuery = {}): Promise<ApiResponse<DomainSummaryMetric>> {
  return await request<DomainSummaryMetric>({
    url: "/api/dashboard/traceability",
    method: "GET",
    params: query,
  });
}
