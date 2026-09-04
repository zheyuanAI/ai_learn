/**
 * IoT 设备接入与告警领域类型定义 (IoT & Digital Twin Types)
 * 遵循阶段 6 前端实施规范与契约规范 docs/specs/30-iot-digital-twin：
 * 1. 设备凭证明文只在创建响应中返回一次，前端严禁持久化缓存
 * 2. 设备状态快照、遥测时序、告警生命周期三类事实严格分离
 * 3. 操作入口依据 allowedActions 数组判定启用/禁用状态及原因
 * 4. MQTT QoS 1 消息去重键由 device_id + message_id (或 sequence) 构成
 */

import type { AllowedAction, BaseEntity, PageQuery } from "./common";

/**
 * 指标值类型枚举
 */
export type MetricValueType = "INTEGER" | "FLOAT" | "BOOLEAN" | "STRING";

/**
 * 设备模型中定义的指标字段
 */
export interface DeviceMetricDef {
  id?: string;
  metricCode: string;
  metricName: string;
  valueType: MetricValueType;
  /** 物理单位，例如 ℃, rpm, mm/s, bar */
  unit?: string;
  /** 是否必填上报 */
  required?: boolean;
  description?: string;
}

/**
 * 设备模型 (DeviceProfile) 实体
 */
export interface DeviceProfileItem extends BaseEntity {
  profileCode: string;
  profileName: string;
  description?: string;
  status: "ACTIVE" | "DISABLED";
  /** 离线超时判定秒数 (一期默认 60s) */
  offlineTimeoutSeconds: number;
  /** 允许上报的指标集 */
  metrics: DeviceMetricDef[];
  allowedActions?: AllowedAction[];
}

/**
 * 创建设备模型入参
 */
export interface DeviceProfileCreateRequest {
  profileCode: string;
  profileName: string;
  description?: string;
  offlineTimeoutSeconds?: number;
  metrics: Array<{
    metricCode: string;
    metricName: string;
    valueType: MetricValueType;
    unit?: string;
    required?: boolean;
    description?: string;
  }>;
}

/**
 * 告警规则运算符
 */
export type AlarmOperator = "GT" | "GTE" | "LT" | "LTE" | "EQ" | "NEQ";

/**
 * 告警严重级别
 */
export type AlarmLevel = "CRITICAL" | "MAJOR" | "MINOR" | "WARNING";

/**
 * 设备告警规则模型
 */
export interface DeviceAlarmRuleItem extends BaseEntity {
  ruleCode: string;
  ruleName: string;
  deviceProfileId: string;
  deviceProfileName?: string;
  /** 可选作用于具体设备，未提供时作用于模型全量设备 */
  deviceId?: string;
  deviceName?: string;
  metricCode: string;
  metricName?: string;
  operator: AlarmOperator;
  /** 触发阈值 (string 传输) */
  triggerThreshold: string;
  /** 恢复阈值 (string 传输) */
  recoveryThreshold: string;
  alarmLevel: AlarmLevel;
  status: "ACTIVE" | "DISABLED";
  allowedActions?: AllowedAction[];
}

/**
 * 创建告警规则入参
 */
export interface DeviceAlarmRuleCreateRequest {
  ruleCode: string;
  ruleName: string;
  deviceProfileId: string;
  deviceId?: string;
  metricCode: string;
  operator: AlarmOperator;
  triggerThreshold: string;
  recoveryThreshold: string;
  alarmLevel: AlarmLevel;
}

/**
 * 设备生命周期状态 (表达设备是否允许接入，与实时在线状态分离)
 */
export type DeviceLifecycleStatus = "ACTIVE" | "DISABLED";

/**
 * 接入协议类型 (一期仅支持 MQTT)
 */
export type DeviceProtocolType = "MQTT";

/**
 * 设备在线快照状态
 */
export type OnlineStatus = "ONLINE" | "OFFLINE";

/**
 * 设备运行快照状态
 */
export type RunningStatus = "RUNNING" | "IDLE" | "STOPPED";

/**
 * 设备告警快照状态
 */
export type DeviceAlarmStatus = "NORMAL" | "ALARMING";

/**
 * 设备实时状态快照模型 (DeviceStatus)
 */
export interface DeviceStatusSnapshot {
  deviceId: string;
  onlineStatus: OnlineStatus;
  runningStatus: RunningStatus;
  alarmStatus: DeviceAlarmStatus;
  lastSeenAt?: string;
  lastMessageKey?: string;
}

/**
 * 设备台账实体 (Device)
 */
export interface DeviceItem extends BaseEntity {
  deviceCode: string;
  deviceName: string;
  deviceProfileId: string;
  deviceProfileName?: string;
  protocolType: DeviceProtocolType;
  lifecycleStatus: DeviceLifecycleStatus;
  /** 稳定归属工作中心或区域，不将工单存为主关系 */
  workCenterId?: string;
  workCenterName?: string;
  areaId?: string;
  areaName?: string;
  mapPointId?: string;
  /** 聚合附带的最新状态快照 */
  statusSnapshot?: DeviceStatusSnapshot;
  allowedActions?: AllowedAction[];
}

/**
 * 创建设备入参
 */
export interface DeviceCreateRequest {
  deviceCode: string;
  deviceName: string;
  deviceProfileId: string;
  protocolType: DeviceProtocolType;
  workCenterId?: string;
  areaId?: string;
  mapPointId?: string;
}

/**
 * 更新设备入参
 */
export interface DeviceUpdateRequest {
  deviceName?: string;
  workCenterId?: string;
  areaId?: string;
  mapPointId?: string;
  lifecycleStatus?: DeviceLifecycleStatus;
}

/**
 * 设备凭证状态
 */
export type DeviceCredentialStatus = "ACTIVE" | "REVOKED";

/**
 * 设备接入凭证台账模型 (查询不返回明文 Secret)
 */
export interface DeviceCredentialItem extends BaseEntity {
  deviceId: string;
  deviceCode?: string;
  credentialReference: string;
  credentialStatus: DeviceCredentialStatus;
  lastUsedAt?: string;
  revokedAt?: string;
  allowedActions?: AllowedAction[];
}

/**
 * 创建设备凭证的一次性返回结果
 * 严格遵循规范：明文只展示一次，关闭后不回显
 */
export interface DeviceCredentialCreateResult {
  credentialId: string;
  credentialReference: string;
  deviceId: string;
  deviceCode: string;
  /** 一次性返回的明文接入密匙/Token，关闭对话框后不再提供 */
  credentialSecret: string;
  createdAt: string;
  mqttClientId: string;
  mqttUsername: string;
}

/**
 * 遥测单个指标项载荷
 */
export interface TelemetryMetricItem {
  metricCode: string;
  metricName?: string;
  /** 指标值 (string 传输) */
  metricValue: string | number;
  metricUnit?: string;
}

/**
 * 原始遥测时序记录 (DeviceTelemetry 只追加事实)
 */
export interface DeviceTelemetryRecord extends BaseEntity {
  deviceId: string;
  deviceCode?: string;
  messageId?: string;
  sequence?: number;
  /** 设备采集时间 (ISO8601) */
  ts: string;
  /** 平台接收时间 (ISO8601) */
  receivedAt: string;
  metricCode: string;
  metricName?: string;
  metricValue: string;
  metricUnit?: string;
}

/**
 * 告警生命周期状态:
 * Triggered -> Acked -> Recovered
 * Triggered -> RecoveredUnacked -> Recovered
 */
export type AlarmLifecycleStatus =
  | "Triggered"
  | "Acked"
  | "RecoveredUnacked"
  | "Recovered";

/**
 * 告警上下文状态
 */
export type AlarmContextStatus = "Unlinked" | "Pending" | "Linked";

/**
 * 告警上下文来源
 */
export type AlarmContextSource = "Auto" | "Manual";

/**
 * 设备告警记录实体 (DeviceAlarm)
 */
export interface DeviceAlarmItem extends BaseEntity {
  alarmNo: string;
  deviceId: string;
  deviceCode?: string;
  deviceName?: string;
  alarmType: string;
  alarmLevel: AlarmLevel;
  status: AlarmLifecycleStatus;
  /** 触发时间 */
  triggeredAt: string;
  /** 确认时间与确认人 */
  ackedAt?: string;
  ackUserId?: string;
  ackUserName?: string;
  ackComment?: string;
  /** 恢复时间 */
  recoveredAt?: string;
  /** 可选业务上下文（对 Core 的软引用） */
  operationExecutionId?: string;
  executionNo?: string;
  workOrderId?: string;
  workOrderNo?: string;
  contextSource?: AlarmContextSource;
  contextStatus: AlarmContextStatus;
  /** 触发时异常遥测指标值 */
  metricCode?: string;
  triggerMetricValue?: string;
  triggerThreshold?: string;
  allowedActions?: AllowedAction[];
}

/**
 * 确认告警入参
 */
export interface DeviceAlarmAckRequest {
  ackComment: string;
}

/**
 * 人工补充或更正告警业务上下文入参
 */
export interface AlarmContextUpdateRequest {
  workOrderId?: string;
  operationExecutionId?: string;
}

/**
 * 遥测时序查询入参
 */
export interface TelemetryQueryParams extends PageQuery {
  deviceId: string;
  metricCode?: string;
  dateFrom?: string;
  dateTo?: string;
  limit?: number;
}

/**
 * 告警查询入参
 */
export interface DeviceAlarmQueryParams extends PageQuery {
  deviceId?: string;
  status?: AlarmLifecycleStatus | "";
  alarmLevel?: AlarmLevel | "";
  contextStatus?: AlarmContextStatus | "";
  dateFrom?: string;
  dateTo?: string;
}

/**
 * MQTT 模拟上报请求 (测试专用)
 */
export interface MqttSimulateRequest {
  deviceCode: string;
  ts: string;
  messageId?: string;
  sequence?: number;
  metrics: Array<{
    metricCode: string;
    metricValue: string | number;
    metricUnit?: string;
  }>;
}

/**
 * MQTT 模拟上报响应
 */
export interface MqttSimulateResponse {
  accepted: boolean;
  duplicate: boolean;
  messageKey: string;
  telemetryIds?: string[];
  message?: string;
}

export type DeviceProfile = DeviceProfileItem;
export type DeviceProfileQuery = PageQuery & { keyword?: string; status?: string };
export type DeviceAlarmRule = DeviceAlarmRuleItem;
export type DeviceAlarmRuleQuery = PageQuery & { keyword?: string; deviceProfileId?: string };
export type Device = DeviceItem;
export type DeviceQuery = PageQuery & { keyword?: string; deviceProfileId?: string; status?: string };
export type DeviceCreatePayload = DeviceCreateRequest;
export type DeviceCredentialResponse = DeviceCredentialCreateResult;
export type TelemetryQuery = { deviceId: string; metricCode?: string; startTime?: string; endTime?: string; limit?: number };
export type TelemetryMetricSeries = any;
export type DeviceStateSnapshot = DeviceStatusSnapshot;
export type DeviceAlarm = DeviceAlarmItem;
export type DeviceAlarmQuery = PageQuery & { keyword?: string; deviceId?: string; status?: string; level?: string };
export type AlarmAckPayload = { ackComment?: string };

