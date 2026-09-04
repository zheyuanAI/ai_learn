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

/**
 * 分页查询设备配置文件列表
 * 接口路径：GET /api/device-profiles
 */
export async function getDeviceProfiles(query: DeviceProfileQuery = {}): Promise<ApiResponse<PageResult<DeviceProfile>>> {
  return await request<PageResult<DeviceProfile>>({
    url: "/api/device-profiles",
    method: "GET",
    params: query,
  });
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
  return await request<DeviceProfile>({
    url: "/api/device-profiles",
    method: "POST",
    data: payload,
  });
}

/**
 * 分页查询设备告警规则
 * 接口路径：GET /api/device-alarm-rules
 */
export async function getDeviceAlarmRules(query: DeviceAlarmRuleQuery = {}): Promise<ApiResponse<PageResult<DeviceAlarmRule>>> {
  return await request<PageResult<DeviceAlarmRule>>({
    url: "/api/device-alarm-rules",
    method: "GET",
    params: query,
  });
}

/**
 * 创建单指标告警规则
 * 接口路径：POST /api/device-alarm-rules
 */
export async function createDeviceAlarmRule(payload: Partial<DeviceAlarmRule>): Promise<ApiResponse<DeviceAlarmRule>> {
  return await request<DeviceAlarmRule>({
    url: "/api/device-alarm-rules",
    method: "POST",
    data: payload,
  });
}
export const createAlarmRule = createDeviceAlarmRule;

/**
 * 分页查询设备台账列表
 * 接口路径：GET /api/devices
 */
export async function getDevices(query: DeviceQuery = {}): Promise<ApiResponse<PageResult<Device>>> {
  return await request<PageResult<Device>>({
    url: "/api/devices",
    method: "GET",
    params: query,
  });
}

/**
 * 查询单台设备详情与三态快照
 * 接口路径：GET /api/devices/{id}
 */
export async function getDeviceById(id: string | number): Promise<ApiResponse<Device>> {
  return await request<Device>({
    url: `/api/devices/${id}`,
    method: "GET",
  });
}
export const getDeviceDetail = getDeviceById;

/**
 * 创建设备台账
 * 接口路径：POST /api/devices
 */
export async function createDevice(payload: DeviceCreatePayload): Promise<ApiResponse<Device>> {
  return await request<Device>({
    url: "/api/devices",
    method: "POST",
    data: payload,
  });
}

/**
 * 切换设备生命周期状态 (ENABLE / DISABLE)
 * 接口路径：POST /api/devices/{id}/status
 */
export async function toggleDeviceLifecycleStatus(id: string | number, status: string): Promise<ApiResponse<Device>> {
  return await request<Device>({
    url: `/api/devices/${id}/status`,
    method: "POST",
    data: { status },
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
 * 接口路径：GET /api/telemetry
 */
export async function getTelemetry(query: any): Promise<ApiResponse<any>> {
  return await request<any>({
    url: "/api/telemetry",
    method: "GET",
    params: query,
  });
}
export const getDeviceTelemetry = getTelemetry;

/**
 * 模拟 MQTT 设备上报数据（测试与演练专用）
 * 接口路径：POST /api/iot/simulate
 */
export async function simulateMqttMessage(payload: any): Promise<ApiResponse<any>> {
  return await request<any>({
    url: "/api/iot/simulate",
    method: "POST",
    data: payload,
  });
}

/**
 * 查询设备实时状态快照
 * 接口路径：GET /api/devices/{id}/snapshot
 */
export async function getDeviceStateSnapshot(deviceId: string | number): Promise<ApiResponse<DeviceStateSnapshot>> {
  return await request<DeviceStateSnapshot>({
    url: `/api/devices/${deviceId}/snapshot`,
    method: "GET",
  });
}
export const getDeviceStatus = getDeviceStateSnapshot;

/**
 * 分页查询设备告警列表
 * 接口路径：GET /api/alarms
 */
export async function getAlarms(query: DeviceAlarmQuery = {}): Promise<ApiResponse<PageResult<DeviceAlarm>>> {
  return await request<PageResult<DeviceAlarm>>({
    url: "/api/alarms",
    method: "GET",
    params: query,
  });
}
export const getDeviceAlarms = getAlarms;

/**
 * 查询单条告警详情
 * 接口路径：GET /api/alarms/{id}
 */
export async function getAlarmById(id: string | number): Promise<ApiResponse<DeviceAlarm>> {
  return await request<DeviceAlarm>({
    url: `/api/alarms/${id}`,
    method: "GET",
  });
}
export const getDeviceAlarmDetail = getAlarmById;

/**
 * 确认设备告警（将告警状态迁移至 Acknowledged）
 * 接口路径：POST /api/alarms/{id}/ack
 */
export async function acknowledgeAlarm(id: string | number, payload: AlarmAckPayload): Promise<ApiResponse<DeviceAlarm>> {
  return await request<DeviceAlarm>({
    url: `/api/alarms/${id}/ack`,
    method: "POST",
    data: payload,
  });
}
export const ackDeviceAlarm = acknowledgeAlarm;

/**
 * 更新告警业务上下文
 * 接口路径：POST /api/alarms/{id}/context
 */
export async function updateAlarmBusinessContext(id: string | number, payload: any): Promise<ApiResponse<DeviceAlarm>> {
  return await request<DeviceAlarm>({
    url: `/api/alarms/${id}/context`,
    method: "POST",
    data: payload,
  });
}

/**
 * 关闭/清除已恢复告警
 * 接口路径：POST /api/alarms/{id}/close
 */
export async function closeAlarm(id: string | number, reason: string): Promise<ApiResponse<DeviceAlarm>> {
  return await request<DeviceAlarm>({
    url: `/api/alarms/${id}/close`,
    method: "POST",
    data: { reason },
  });
}
