<template>
  <div class="device-detail-container">
    <!-- 头部返回与导航 -->
    <div class="detail-top-nav">
      <button type="button" class="btn-back" @click="$emit('back')">
        ‹ 返回设备列表
      </button>
      <div class="top-nav-actions">
        <button type="button" class="btn btn-warning" @click="credentialDialogVisible = true">
          <span>签发新凭证</span>
        </button>
      </div>
    </div>

    <!-- 加载中态 -->
    <div v-if="viewState === 'loading'" class="loading-box">
      <span class="spinner">⏳</span>
      <span>正在读取设备全景状态与时序数据...</span>
    </div>

    <!-- 错误异常提示 -->
    <ErrorState
      v-else-if="viewState === 'error'"
      title="设备详情加载失败"
      :message="errorMessage"
      @retry="loadDeviceData"
    />

    <div v-else-if="device" class="device-main-layout">
      <!-- 顶部基础属性与状态快照大卡片 -->
      <section class="device-header-card">
        <div class="header-main-info">
          <div class="title-row">
            <h2 class="dev-name">{{ device.deviceName }}</h2>
            <span class="dev-code font-mono text-primary">{{ device.deviceCode }}</span>
            <span class="proto-tag font-mono">{{ device.protocolType }}</span>
          </div>
          <p class="dev-meta-desc text-muted">
            所属模型: <strong>{{ device.deviceProfileName || device.deviceProfileId }}</strong>
            · 归属车间: <strong>{{ device.workCenterName || "默认车间" }}</strong>
            · 区域: <strong>{{ device.areaName || "加工区" }}</strong>
          </p>
        </div>

        <!-- 实时三态快照卡片组 -->
        <div class="status-cards-grid">
          <!-- 在线状态 -->
          <div class="status-indicator-card">
            <span class="card-label">通信在线快照</span>
            <div class="card-value-row">
              <StatusBadge
                :type="statusSnapshot.onlineStatus === 'ONLINE' ? 'success' : 'default'"
                :text="statusSnapshot.onlineStatus === 'ONLINE' ? '通信正常 (ONLINE)' : '通信离线 (OFFLINE)'"
                :pulsing="statusSnapshot.onlineStatus === 'ONLINE'"
              />
            </div>
            <span class="card-hint text-muted">最后心跳: {{ statusSnapshot.lastSeenAt || "未知" }}</span>
          </div>

          <!-- 运行状态 -->
          <div class="status-indicator-card">
            <span class="card-label">机台运行状态</span>
            <div class="card-value-row">
              <StatusBadge
                :type="statusSnapshot.runningStatus === 'RUNNING' ? 'primary' : statusSnapshot.runningStatus === 'IDLE' ? 'info' : 'warning'"
                :text="statusSnapshot.runningStatus === 'RUNNING' ? '生产运转中' : statusSnapshot.runningStatus === 'IDLE' ? '就绪待机' : '停机关停'"
              />
            </div>
            <span class="card-hint text-muted font-mono">去重键: {{ statusSnapshot.lastMessageKey || "none" }}</span>
          </div>

          <!-- 告警状态 -->
          <div class="status-indicator-card">
            <span class="card-label">告警健康状态</span>
            <div class="card-value-row">
              <StatusBadge
                :type="statusSnapshot.alarmStatus === 'ALARMING' ? 'danger' : 'success'"
                :text="statusSnapshot.alarmStatus === 'ALARMING' ? '存在活跃告警' : '运行正常无告警'"
                :pulsing="statusSnapshot.alarmStatus === 'ALARMING'"
              />
            </div>
            <span class="card-hint text-muted">活跃告警数: {{ activeAlarms.length }}</span>
          </div>
        </div>
      </section>

      <!-- 实时遥测指标速览面板 -->
      <section class="telemetry-preview-section">
        <div class="section-header">
          <h3 class="section-title">最新遥测指标快照 (Latest Telemetry)</h3>
          <button type="button" class="btn-link" @click="$emit('go-telemetry', device)">
            查看完整遥测时序 ›
          </button>
        </div>

        <div class="telemetry-cards-grid">
          <div
            v-for="rec in latestTelemetry"
            :key="rec.id"
            class="telemetry-metric-card"
          >
            <span class="t-metric-name">{{ rec.metricName || rec.metricCode }}</span>
            <div class="t-metric-value">
              <QuantityText :value="rec.metricValue" :unit="rec.metricUnit" />
            </div>
            <span class="t-metric-time font-mono text-muted">上报: {{ rec.ts }}</span>
          </div>
        </div>
      </section>

      <!-- 下部两栏布局：凭证管理与告警历史 -->
      <div class="bottom-two-col">
        <!-- 凭证管理 -->
        <section class="sub-panel">
          <div class="panel-header">
            <h4 class="panel-title">接入凭证台账 (Credentials)</h4>
            <span class="panel-sub text-muted">（服务端仅存引用与状态，明文不回显）</span>
          </div>

          <table class="sub-table">
            <thead>
              <tr>
                <th>凭证标识引用</th>
                <th>状态</th>
                <th>签发时间</th>
                <th>操作</th>
              </tr>
            </thead>
            <tbody>
              <tr v-if="credentials.length === 0">
                <td colspan="4" class="text-center text-muted">尚未签发任何凭证</td>
              </tr>
              <tr v-for="c in credentials" :key="c.id">
                <td class="font-mono highlight-code">{{ c.credentialReference }}</td>
                <td>
                  <StatusBadge
                    :type="c.credentialStatus === 'ACTIVE' ? 'success' : 'default'"
                    :text="c.credentialStatus === 'ACTIVE' ? '有效' : '已撤销'"
                  />
                </td>
                <td class="font-mono text-muted">{{ c.createdAt }}</td>
                <td>
                  <button
                    v-if="c.credentialStatus === 'ACTIVE'"
                    type="button"
                    class="btn-text text-danger"
                    :disabled="!isActionAllowed(c, 'revoke')"
                    @click="promptRevokeCredential(c)"
                  >
                    撤销
                  </button>
                  <span v-else class="text-muted font-xs">已失效</span>
                </td>
              </tr>
            </tbody>
          </table>
        </section>

        <!-- 活动告警 -->
        <section class="sub-panel">
          <div class="panel-header">
            <h4 class="panel-title">关联活动告警记录 (Active Alarms)</h4>
            <span class="panel-sub text-muted">共 {{ activeAlarms.length }} 项</span>
          </div>

          <table class="sub-table">
            <thead>
              <tr>
                <th>告警编号</th>
                <th>告警类型</th>
                <th>严重级</th>
                <th>状态</th>
                <th>触发时间</th>
              </tr>
            </thead>
            <tbody>
              <tr v-if="activeAlarms.length === 0">
                <td colspan="5" class="text-center text-muted">当前无未归档告警</td>
              </tr>
              <tr v-for="a in activeAlarms" :key="a.id">
                <td class="font-mono highlight-code">{{ a.alarmNo }}</td>
                <td>{{ a.alarmType }}</td>
                <td>
                  <span class="alarm-level-tag" :class="`level-${a.alarmLevel.toLowerCase()}`">
                    {{ a.alarmLevel }}
                  </span>
                </td>
                <td>
                  <StatusBadge
                    :type="a.status === 'Triggered' ? 'danger' : a.status === 'RecoveredUnacked' ? 'warning' : 'primary'"
                    :text="a.status"
                    :pulsing="a.status === 'Triggered'"
                  />
                </td>
                <td class="font-mono text-muted">{{ a.triggeredAt }}</td>
              </tr>
            </tbody>
          </table>
        </section>
      </div>
    </div>

    <!-- 凭证生成一次性安全对话框 -->
    <DeviceCredentialDialog
      v-if="device"
      v-model:visible="credentialDialogVisible"
      :device-id="device.id as string"
      :device-code="device.deviceCode"
      :device-name="device.deviceName"
      @issued="loadDeviceData"
    />

    <!-- 撤销确认对话框 -->
    <ConfirmDialog
      v-model:visible="revokeConfirm.visible"
      title="撤销设备接入凭证确认"
      :message="`确定要撤销凭证【${revokeConfirm.item?.credentialReference}】吗？撤销后设备将无法继续使用该凭证接入 MQTT。`"
      danger
      :loading="revokeConfirm.loading"
      @confirm="handleConfirmRevoke"
    />
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted, watch } from "vue";
import {
  StatusBadge,
  QuantityText,
  ConfirmDialog,
  ErrorState,
} from "../../components/common";
import type { ViewState } from "../../types/common";
import type {
  DeviceItem,
  DeviceStatusSnapshot,
  DeviceCredentialItem,
  DeviceTelemetryRecord,
  DeviceAlarmItem,
} from "../../types/iot";
import {
  getDeviceDetail,
  getDeviceStatus,
  getDeviceCredentials,
  revokeDeviceCredential,
  getDeviceTelemetry,
  getDeviceAlarms,
} from "../../api/iot";
import DeviceCredentialDialog from "./DeviceCredentialDialog.vue";

const props = withDefaults(
  defineProps<{
    deviceId?: string;
  }>(),
  {
    deviceId: "dev-cnc-01",
  }
);

defineEmits<{
  (e: "back"): void;
  (e: "go-telemetry", dev: DeviceItem): void;
}>();

const viewState = ref<ViewState>("loading");
const errorMessage = ref("");

const device = ref<DeviceItem | null>(null);
const statusSnapshot = ref<DeviceStatusSnapshot>({
  deviceId: "",
  onlineStatus: "OFFLINE",
  runningStatus: "STOPPED",
  alarmStatus: "NORMAL",
});
const credentials = ref<DeviceCredentialItem[]>([]);
const latestTelemetry = ref<DeviceTelemetryRecord[]>([]);
const activeAlarms = ref<DeviceAlarmItem[]>([]);

const credentialDialogVisible = ref(false);

const revokeConfirm = reactive({
  visible: false,
  loading: false,
  item: null as DeviceCredentialItem | null,
});

function isActionAllowed(item: any, action: string): boolean {
  if (!item.allowedActions || item.allowedActions.length === 0) return true;
  const match = item.allowedActions.find((a: any) => a.action === action);
  return match ? match.enabled : true;
}

async function loadDeviceData() {
  viewState.value = "loading";
  errorMessage.value = "";
  try {
    const id = props.deviceId || "dev-cnc-01";
    const [devRes, statRes, credRes, tlmRes, almRes] = await Promise.all([
      getDeviceDetail(id),
      getDeviceStatus(id),
      getDeviceCredentials(id),
      getDeviceTelemetry({ deviceId: id, limit: 4 }),
      getDeviceAlarms({ deviceId: id }),
    ]);

    if (devRes.data) {
      device.value = devRes.data;
    }
    if (statRes.data) {
      statusSnapshot.value = statRes.data;
    }
    credentials.value = credRes.data || [];
    latestTelemetry.value = tlmRes.data?.records || [];
    activeAlarms.value = (almRes.data?.records || []).filter((a: any) => a.status !== "Recovered");

    viewState.value = device.value ? "ready" : "empty";
  } catch (err: any) {
    errorMessage.value = err.message || "请求设备全景数据失败";
    viewState.value = "error";
  }
}

function promptRevokeCredential(item: DeviceCredentialItem) {
  revokeConfirm.item = item;
  revokeConfirm.visible = true;
}

async function handleConfirmRevoke() {
  if (!revokeConfirm.item || !device.value) return;
  revokeConfirm.loading = true;
  try {
    await revokeDeviceCredential(device.value.id as string, revokeConfirm.item.id as string);
    revokeConfirm.visible = false;
    await loadDeviceData();
  } catch (err: any) {
    alert(`撤销失败：${err.message}`);
  } finally {
    revokeConfirm.loading = false;
  }
}

watch(() => props.deviceId, () => {
  loadDeviceData();
});

onMounted(() => {
  loadDeviceData();
});
</script>

<style scoped>
.device-detail-container {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.detail-top-nav {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.btn-back {
  background: none;
  border: 1px solid rgba(255, 255, 255, 0.15);
  color: #cbd5e1;
  padding: 6px 14px;
  border-radius: 6px;
  cursor: pointer;
  font-size: 13px;
  transition: all 0.2s;
}

.btn-back:hover {
  background: rgba(255, 255, 255, 0.08);
  color: #f8fafc;
}

.loading-box {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 12px;
  padding: 60px;
  color: #94a3b8;
}

.device-main-layout {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.device-header-card {
  background: rgba(15, 23, 42, 0.8);
  border: 1px solid rgba(255, 255, 255, 0.1);
  border-radius: 10px;
  padding: 20px;
  display: flex;
  flex-direction: column;
  gap: 18px;
}

.title-row {
  display: flex;
  align-items: center;
  gap: 12px;
  flex-wrap: wrap;
}

.dev-name {
  margin: 0;
  font-size: 20px;
  color: #f8fafc;
}

.dev-code {
  font-size: 16px;
  font-weight: 600;
}

.proto-tag {
  background: rgba(56, 189, 248, 0.12);
  border: 1px solid rgba(56, 189, 248, 0.25);
  color: #38bdf8;
  padding: 2px 8px;
  border-radius: 4px;
  font-size: 11px;
}

.dev-meta-desc {
  margin: 6px 0 0;
  font-size: 13px;
}

.status-cards-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(220px, 1fr));
  gap: 14px;
}

.status-indicator-card {
  background: rgba(30, 41, 59, 0.5);
  border: 1px solid rgba(255, 255, 255, 0.08);
  border-radius: 8px;
  padding: 14px 16px;
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.card-label {
  font-size: 12px;
  color: #94a3b8;
}

.card-hint {
  font-size: 11px;
}

.telemetry-preview-section {
  background: rgba(15, 23, 42, 0.7);
  border: 1px solid rgba(255, 255, 255, 0.08);
  border-radius: 8px;
  padding: 16px 20px;
  display: flex;
  flex-direction: column;
  gap: 14px;
}

.section-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.section-title {
  margin: 0;
  font-size: 15px;
  color: #f8fafc;
}

.btn-link {
  background: none;
  border: none;
  color: #38bdf8;
  font-size: 13px;
  cursor: pointer;
}

.btn-link:hover {
  text-decoration: underline;
}

.telemetry-cards-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(180px, 1fr));
  gap: 12px;
}

.telemetry-metric-card {
  background: rgba(30, 41, 59, 0.4);
  border: 1px solid rgba(255, 255, 255, 0.06);
  border-radius: 6px;
  padding: 12px 14px;
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.t-metric-name {
  font-size: 12px;
  color: #94a3b8;
}

.t-metric-value {
  font-size: 18px;
}

.t-metric-time {
  font-size: 11px;
}

.bottom-two-col {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 16px;
}

.sub-panel {
  background: rgba(15, 23, 42, 0.7);
  border: 1px solid rgba(255, 255, 255, 0.08);
  border-radius: 8px;
  padding: 16px;
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.panel-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.panel-title {
  margin: 0;
  font-size: 14px;
  color: #f1f5f9;
}

.panel-sub {
  font-size: 11px;
}

.sub-table {
  width: 100%;
  border-collapse: collapse;
  font-size: 13px;
}

.sub-table th {
  background: rgba(30, 41, 59, 0.4);
  padding: 8px 10px;
  color: #94a3b8;
  text-align: left;
  border-bottom: 1px solid rgba(255, 255, 255, 0.08);
}

.sub-table td {
  padding: 10px;
  border-bottom: 1px solid rgba(255, 255, 255, 0.05);
  color: #cbd5e1;
}

.highlight-code {
  color: #38bdf8;
}

.alarm-level-tag {
  font-size: 11px;
  font-weight: 600;
  padding: 2px 6px;
  border-radius: 4px;
}

.level-critical { background: rgba(239, 68, 68, 0.2); color: #f87171; }
.level-major { background: rgba(251, 146, 60, 0.2); color: #fb923c; }
.level-minor { background: rgba(250, 204, 21, 0.2); color: #facc15; }
.level-warning { background: rgba(96, 165, 250, 0.2); color: #60a5fa; }

.btn-text {
  background: none;
  border: none;
  font-size: 12px;
  cursor: pointer;
  padding: 2px 4px;
}

.btn-text:hover:not(:disabled) {
  text-decoration: underline;
}

.btn-text:disabled {
  color: #64748b;
  cursor: not-allowed;
}

.text-danger { color: #f87171 !important; }
.text-primary { color: #38bdf8 !important; }
.font-xs { font-size: 11px; }
</style>
