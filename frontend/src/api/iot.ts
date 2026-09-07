/**
 * IoT 设备接入与告警业务 API 服务 (IoT API)
 * 提供设备 Profile、单指标告警规则、设备台账、一次性明文凭证、时序遥测与告警生命周期接口
 * 遵循 docs/specs/30-iot-digital-twin 规范契约，纯粹直连真实后端 REST 接口
 * 凭证明文仅在签发成功时在响应中返回一次，不回显、不存持久化缓存
 */

import request, { type ApiResponse } from "../utils/request";
import type { PageResult } from "../types/common";
import type {
  DeviceProfile,
  DeviceProfileQuery,
  DeviceAlarmRule,
  DeviceAlarmRuleQuery,
  Device,
  DeviceQuery,
  DeviceCreatePayload,
  DeviceCredentialResponse,
  TelemetryQuery,
  TelemetryMetricSeries,
  DeviceStateSnapshot,
  DeviceAlarm,
  DeviceAlarmQuery,
  AlarmAckPayload,
} from "../types/iot";

/** 后端 IoT 线协议转换为现有页面使用的驼峰模型，避免页面直接依赖 snake_case。 */
function toCamelRecord<T extends Record<string, any>>(value: T): T {
  if (!value || typeof value !== "object") return value;
  const mapped: Record<string, any> = {};
  for (const [key, item] of Object.entries(value)) {
    const camelKey = key.replace(/_([a-z])/g, (_, char: string) => char.toUpperCase());
    mapped[camelKey] = Array.isArray(item)
      ? item.map((entry) => (entry && typeof entry === "object" ? toCamelRecord(entry) : entry))
      : item && typeof item === "object" ? toCamelRecord(item) : item;
  }
  return mapped as T;
}

/** 将后端分页响应中的 records 同步映射为页面模型。 */
function toCamelPage<T>(response: ApiResponse<any>): ApiResponse<PageResult<T>> {
  const page = response.data || {};
  return {
    ...response,
    data: {
      ...toCamelRecord(page),
      records: Array.isArray(page.records) ? page.records.map((item: any) => toCamelRecord(item)) : [],
    },
  };
}

/**
 * 分页查询设备配置文件列表
 * 接口路径：GET /api/device-profiles
 */
export async function getDeviceProfiles(query: DeviceProfileQuery = {}): Promise<ApiResponse<PageResult<DeviceProfile>>> {
  const response = await request<any>({
    url: "/api/device-profiles",
    method: "GET",
    params: {
      profile_code: query.keyword,
      page: query.page,
      size: query.size,
    },
  });
  return toCamelPage<DeviceProfile>(response);
}

/**
 * 获取设备配置文件详情
 * 接口路径：GET /api/device-profiles/{id}
 */
export async function getDeviceProfileById(id: string | number): Promise<ApiResponse<DeviceProfile>> {
  return await request<DeviceProfile>({
    url: `/api/device-profiles/${id}`,
    method: "GET",
  });
}

/**
 * 创建设备配置文件
 * 接口路径：POST /api/device-profiles
 */
export async function createDeviceProfile(payload: Partial<DeviceProfile>): Promise<ApiResponse<DeviceProfile>> {
  const response = await request<any>({
    url: "/api/device-profiles",
    method: "POST",
    data: {
      profile_code: payload.profileCode,
      profile_name: payload.profileName,
      offline_timeout_seconds: payload.offlineTimeoutSeconds,
      metrics: payload.metrics?.map((metric: any) => ({
        metric_code: metric.metricCode,
        metric_name: metric.metricName,
        value_type: metric.valueType,
        unit: metric.unit,
        required: metric.required,
      })),
    },
  });
  return { ...response, data: toCamelRecord(response.data) };
}

/**
 * 分页查询设备告警规则
 * 接口路径：GET /api/device-alarm-rules
 */
export async function getDeviceAlarmRules(query: DeviceAlarmRuleQuery = {}): Promise<ApiResponse<DeviceAlarmRule[]>> {
  const response = await request<any>({
    url: "/api/device-alarm-rules",
    method: "GET",
    params: {
      device_profile_id: query.deviceProfileId,
      page: query.page,
      size: query.size,
    },
  });
  return { ...response, data: Array.isArray(response.data) ? response.data.map((item) => toCamelRecord(item)) : [] };
}

/**
 * 创建单指标告警规则
 * 接口路径：POST /api/device-alarm-rules
 */
export async function createDeviceAlarmRule(payload: Partial<DeviceAlarmRule>): Promise<ApiResponse<DeviceAlarmRule>> {
  const response = await request<any>({
    url: "/api/device-alarm-rules",
    method: "POST",
    data: {
      rule_code: payload.ruleCode,
      device_profile_id: payload.deviceProfileId,
      device_id: payload.deviceId,
      metric_code: payload.metricCode,
      operator: payload.operator,
      trigger_threshold: payload.triggerThreshold,
      recovery_threshold: payload.recoveryThreshold,
      alarm_level: payload.alarmLevel,
    },
  });
  return { ...response, data: toCamelRecord(response.data) };
}
export const createAlarmRule = createDeviceAlarmRule;

/**
 * 分页查询设备台账列表
 * 接口路径：GET /api/devices
 */
export async function getDevices(query: DeviceQuery = {}): Promise<ApiResponse<PageResult<Device>>> {
  const response = await request<any>({
    url: "/api/devices",
    method: "GET",
    params: {
      device_code: query.keyword,
      lifecycle_status: (query as any).lifecycleStatus || query.status,
      page: query.page,
      size: query.size,
    },
  });
  return toCamelPage<Device>(response);
}

/**
 * 查询单台设备详情与三态快照
 * 接口路径：GET /api/devices/{id}
 */
export async function getDeviceById(id: string | number): Promise<ApiResponse<Device>> {
  const response = await request<any>({
    url: `/api/devices/${id}`,
    method: "GET",
  });
  return { ...response, data: toCamelRecord(response.data) };
}
export const getDeviceDetail = getDeviceById;

/**
 * 创建设备台账
 * 接口路径：POST /api/devices
 */
export async function createDevice(payload: DeviceCreatePayload): Promise<ApiResponse<Device>> {
  const response = await request<any>({
    url: "/api/devices",
    method: "POST",
    data: {
      device_code: payload.deviceCode,
      device_name: payload.deviceName,
      device_profile_id: payload.deviceProfileId,
      protocol_type: payload.protocolType,
      work_center_id: payload.workCenterId || undefined,
      area_id: payload.areaId || undefined,
      map_point_id: payload.mapPointId || undefined,
    },
  });
  return { ...response, data: toCamelRecord(response.data) };
}

/**
 * 切换设备生命周期状态 (Active / Disabled)
 * 接口路径：PATCH /api/devices/{id}/lifecycle
 */
export async function toggleDeviceLifecycleStatus(id: string | number, status: "Active" | "Disabled" | string): Promise<ApiResponse<Device>> {
  const lifecycle_status = status.toLowerCase() === "disabled" ? "Disabled" : "Active";
  return await request<Device>({
    url: `/api/devices/${id}/lifecycle`,
    method: "PATCH",
    data: { lifecycle_status },
  });
}

/**
 * 查询设备凭证列表
 * 接口路径：GET /api/devices/{id}/credentials
 */
export async function getDeviceCredentials(deviceId: string | number): Promise<ApiResponse<any[]>> {
  return await request<any[]>({
    url: `/api/devices/${deviceId}/credentials`,
    method: "GET",
  });
}

/**
 * 为设备生成有效接入凭证
 * 接口路径：POST /api/devices/{id}/credentials
 * 安全规范：凭证明文仅在此次响应中返回一次；前端仅展示弹窗，不写入持久化存储
 */
export async function issueDeviceCredential(deviceId: string | number): Promise<ApiResponse<DeviceCredentialResponse>> {
  return await request<DeviceCredentialResponse>({
    url: `/api/devices/${deviceId}/credentials`,
    method: "POST",
  });
}
export const createDeviceCredential = issueDeviceCredential;

/**
 * 撤销设备接入凭证
 * 接口路径：POST /api/devices/{id}/credentials/{credentialId}/revoke
 */
export async function revokeDeviceCredential(deviceId: string | number, credentialId: string | number): Promise<ApiResponse<void>> {
  return await request<void>({
    url: `/api/devices/${deviceId}/credentials/${credentialId}/revoke`,
    method: "POST",
  });
}

/**
 * 查询设备时序遥测数据
 * 接口路径：GET /api/devices/{id}/telemetry?metric_code=&date_from=&date_to=&limit=100
 */
export async function getTelemetry(deviceIdOrQuery: any, queryParams?: any): Promise<ApiResponse<any>> {
  let targetId = "";
  let params: Record<string, any> = {};

  if (typeof deviceIdOrQuery === "object") {
    targetId = deviceIdOrQuery.deviceId || deviceIdOrQuery.id || "";
    params = {
      metric_code: deviceIdOrQuery.metric_code || deviceIdOrQuery.metricCode,
      date_from: deviceIdOrQuery.date_from || deviceIdOrQuery.dateFrom || deviceIdOrQuery.startTime,
      date_to: deviceIdOrQuery.date_to || deviceIdOrQuery.dateTo || deviceIdOrQuery.endTime,
      limit: deviceIdOrQuery.limit || deviceIdOrQuery.size || 100,
    };
  } else {
    targetId = String(deviceIdOrQuery);
    if (queryParams) {
      params = {
        metric_code: queryParams.metric_code || queryParams.metricCode,
        date_from: queryParams.date_from || queryParams.dateFrom || queryParams.startTime,
        date_to: queryParams.date_to || queryParams.dateTo || queryParams.endTime,
        limit: queryParams.limit || queryParams.size || 100,
      };
    }
  }

  const response = await request<any>({
    url: `/api/devices/${targetId}/telemetry`,
    method: "GET",
    params,
  });
  const records = Array.isArray(response.data) ? response.data.map((item) => toCamelRecord(item)) : [];
  return { ...response, data: { records, total: records.length, page: 1, size: records.length } };
}
export const getDeviceTelemetry = getTelemetry;

/**
 * 模拟 MQTT 设备上报数据（测试与演练专用）
 * 接口路径：POST /api/protocol-adapters/mqtt/simulate
 * 正式字段：device_code, ts, message_id, sequence, metrics[{metric_code, metric_value, metric_unit}]
 */
export async function simulateMqttMessage(payload: any): Promise<ApiResponse<any>> {
  const requestBody = {
    device_code: payload.device_code || payload.deviceCode,
    ts: payload.ts || new Date().toISOString(),
    message_id: payload.message_id || payload.messageId || `msg-${Date.now()}`,
    sequence: payload.sequence ?? 1,
    metrics: (payload.metrics || []).map((m: any) => ({
      metric_code: m.metric_code || m.metricCode,
      metric_value: m.metric_value !== undefined ? m.metric_value : m.metricValue,
      metric_unit: m.metric_unit || m.metricUnit,
    })),
  };

  return await request<any>({
    url: "/api/protocol-adapters/mqtt/simulate",
    method: "POST",
    data: requestBody,
  });
}

/**
 * 查询设备实时状态快照
 * 接口路径：GET /api/devices/{id}/status
 */
export async function getDeviceStateSnapshot(deviceId: string | number): Promise<ApiResponse<DeviceStateSnapshot>> {
  const response = await request<any>({
    url: `/api/devices/${deviceId}/status`,
    method: "GET",
  });
  return { ...response, data: toCamelRecord(response.data) };
}
export const getDeviceStatus = getDeviceStateSnapshot;

/**
 * 分页查询设备告警列表
 * 接口路径：GET /api/device-alarms?device_id=&status=&alarm_level=&date_from=&date_to=&context_status=&page=1&size=20
 */
export async function getAlarms(query: any = {}): Promise<ApiResponse<PageResult<DeviceAlarm>>> {
  const params: Record<string, any> = {
    page: query.page || 1,
    size: query.size || 20,
  };
  if (query.device_id || query.deviceId) params.device_id = query.device_id || query.deviceId;
  if (query.status) params.status = query.status;
  if (query.alarm_level || query.alarmLevel || query.level) params.alarm_level = query.alarm_level || query.alarmLevel || query.level;
  if (query.date_from || query.dateFrom) params.date_from = query.date_from || query.dateFrom;
  if (query.date_to || query.dateTo) params.date_to = query.date_to || query.dateTo;
  if (query.context_status || query.contextStatus) params.context_status = query.context_status || query.contextStatus;

  const response = await request<any>({
    url: "/api/device-alarms",
    method: "GET",
    params,
  });
  return toCamelPage<DeviceAlarm>(response);
}
export const getDeviceAlarms = getAlarms;

/**
 * 查询单条告警详情
 * 接口路径：GET /api/device-alarms/{id}
 */
export async function getAlarmById(id: string | number): Promise<ApiResponse<DeviceAlarm>> {
  const response = await request<any>({
    url: `/api/device-alarms/${id}`,
    method: "GET",
  });
  return { ...response, data: toCamelRecord(response.data) };
}
export const getDeviceAlarmDetail = getAlarmById;

/**
 * 确认设备告警（将告警状态迁移至 Acked）
 * 接口路径：POST /api/device-alarms/{id}/ack
 * 正式请求载荷：{ ack_comment }
 */
export async function acknowledgeAlarm(id: string | number, payload: any = {}): Promise<ApiResponse<DeviceAlarm>> {
  const ack_comment = payload.ack_comment || payload.ackComment || "已确认告警";
  return await request<DeviceAlarm>({
    url: `/api/device-alarms/${id}/ack`,
    method: "POST",
    data: { ack_comment },
  });
}
export const ackDeviceAlarm = acknowledgeAlarm;

/**
 * 更新告警业务上下文（人工补链）
 * 接口路径：PUT /api/device-alarms/{id}/business-context
 * 正式请求载荷：{ operation_execution_id, work_order_id }（至少一项）
 */
export async function updateAlarmBusinessContext(id: string | number, payload: any): Promise<ApiResponse<{ alarmId: string; status: string; detail: string }>> {
  const requestBody: Record<string, any> = {};
  if (payload.operation_execution_id || payload.operationExecutionId) {
    requestBody.operation_execution_id = payload.operation_execution_id || payload.operationExecutionId;
  }
  if (payload.work_order_id || payload.workOrderId) {
    requestBody.work_order_id = payload.work_order_id || payload.workOrderId;
  }
  const response = await request<any>({
    url: `/api/device-alarms/${id}/business-context`,
    method: "PUT",
    data: requestBody,
  });
  return { ...response, data: toCamelRecord(response.data) };
}
