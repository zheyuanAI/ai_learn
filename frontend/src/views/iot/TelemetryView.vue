<template>
  <div class="iot-view-container">
    <!-- 统一页面头部 -->
    <PageHeader
      title="设备遥测时序 (Device Telemetry)"
      tag="IOT / TIME SERIES"
      description="查询只追加的原始遥测时序事实。MQTT QoS 1 消息依据 device_id + message_id (或 sequence) 建立幂等去重键，重复投递不重复保存。"
    />

    <!-- 设备与指标快捷筛选器 -->
    <div class="telemetry-filter-bar">
      <div class="filter-item">
        <label>选择目标设备：</label>
        <select v-model="selectedDeviceId" class="select-control" @change="handleDeviceChange">
          <option v-for="d in deviceOptions" :key="d.id" :value="d.id">
            {{ d.deviceName }} ({{ d.deviceCode }})
          </option>
        </select>
      </div>

      <div class="filter-item">
        <label>指标过滤：</label>
        <input
          v-model="metricCodeFilter"
          type="text"
          class="input-control font-mono"
          placeholder="例如 spindle_temp"
          @keyup.enter="fetchTelemetry"
        />
      </div>

      <button type="button" class="btn btn-primary" @click="fetchTelemetry">
        <span>刷新遥测数据</span>
      </button>

      <button type="button" class="btn btn-secondary" @click="toggleSimulateCard">
        <span>{{ showSimulateCard ? "收起模拟器" : "打开 MQTT QoS 1 模拟测试器" }}</span>
      </button>
    </div>

    <!-- MQTT QoS 1 模拟上报与去重测试卡片 -->
    <div v-if="showSimulateCard" class="simulate-card">
      <div class="sim-header">
        <div class="sim-title-group">
          <span class="sim-tag font-mono">QoS 1 SIMULATOR</span>
          <h4 class="sim-title">MQTT 遥测消息模拟上报与幂等去重测试</h4>
        </div>
        <button type="button" class="btn-close" @click="showSimulateCard = false">✕</button>
      </div>

      <form class="sim-body" @submit.prevent="handleSendSimulate">
        <div class="sim-grid three-col">
          <div class="sim-item">
            <label>设备编码 (Device Code) <span class="req">*</span></label>
            <input
              v-model="simForm.deviceCode"
              type="text"
              class="input-control font-mono"
              required
            />
          </div>
          <div class="sim-item">
            <label>消息 ID (Message ID, 用于去重) <span class="req">*</span></label>
            <input
              v-model="simForm.messageId"
              type="text"
              class="input-control font-mono"
              required
            />
          </div>
          <div class="sim-item">
            <label>消息序号 (Sequence)</label>
            <input
              v-model.number="simForm.sequence"
              type="number"
              class="input-control font-mono"
            />
          </div>
        </div>

        <div class="sim-grid three-col">
          <div class="sim-item">
            <label>指标编码 (Metric Code) <span class="req">*</span></label>
            <input
              v-model="simForm.metricCode"
              type="text"
              class="input-control font-mono"
              required
            />
          </div>
          <div class="sim-item">
            <label>指标数值 (Metric Value) <span class="req">*</span></label>
            <input
              v-model="simForm.metricValue"
              type="text"
              class="input-control font-mono"
              required
            />
          </div>
          <div class="sim-item">
            <label>指标单位 (Unit)</label>
            <input
              v-model="simForm.metricUnit"
              type="text"
              class="input-control font-mono"
            />
          </div>
        </div>

        <div class="sim-footer">
          <div class="sim-hint text-muted">
            测试提示：连续以相同 Message ID 发送，将触发 QoS 1 幂等成功响应（不重复插入记录）。
          </div>
          <div class="sim-actions">
            <button
              type="button"
              class="btn btn-secondary"
              @click="generateNewMessageId"
            >
              生成新 MsgId
            </button>
            <button
              type="submit"
              class="btn btn-primary"
              :disabled="simulating"
            >
              {{ simulating ? "上报中..." : "模拟发送 MQTT 消息" }}
            </button>
          </div>
        </div>

        <!-- 模拟结果反馈框 -->
        <div v-if="simFeedback" class="sim-feedback-box" :class="simFeedback.duplicate ? 'is-duplicate' : 'is-success'">
          <span class="fb-icon">{{ simFeedback.duplicate ? '🔁' : '✓' }}</span>
          <div class="fb-text">
            <strong>{{ simFeedback.duplicate ? 'QoS 1 命中幂等去重 (Duplicate = true)' : '遥测事实接收并已保存' }}</strong>
            <span class="font-mono">{{ simFeedback.message }} (Key: {{ simFeedback.messageKey }})</span>
          </div>
        </div>
      </form>
    </div>

    <!-- 错误异常提示 -->
    <ErrorState
      v-if="viewState === 'error'"
      title="遥测时序加载失败"
      :message="errorMessage"
      @retry="fetchTelemetry"
    />

    <!-- 遥测时序表格 -->
    <DataTable
      v-else
      :columns="columns"
      :data="telemetryList"
      :loading="viewState === 'loading'"
      :page="queryParams.page"
      :size="queryParams.size"
      :total="total"
      empty-text="暂无该设备的遥测时序记录"
      @page-change="handlePageChange"
    >
      <!-- 去重标识 -->
      <template #dedupKey="{ row }">
        <div class="dedup-cell">
          <span v-if="row.messageId" class="font-mono text-primary">Msg: {{ row.messageId }}</span>
          <span v-if="row.sequence" class="font-mono text-muted">Seq: #{{ row.sequence }}</span>
        </div>
      </template>

      <!-- 采集与接收时间 -->
      <template #ts="{ row }">
        <div class="time-cell font-mono">
          <div>采: {{ row.ts }}</div>
          <div class="text-muted">收: {{ row.receivedAt }}</div>
        </div>
      </template>

      <!-- 指标编码与名称 -->
      <template #metricCode="{ row }">
        <div class="metric-cell">
          <span class="metric-title">{{ row.metricName || row.metricCode }}</span>
          <span class="metric-code font-mono text-muted">{{ row.metricCode }}</span>
        </div>
      </template>

      <!-- 指标值 (高精度 QuantityText) -->
      <template #metricValue="{ row }">
        <QuantityText :value="row.metricValue" :unit="row.metricUnit" />
      </template>
    </DataTable>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted } from "vue";
import {
  PageHeader,
  DataTable,
  QuantityText,
  ErrorState,
} from "../../components/common";
import type { TableColumn } from "../../components/common/DataTable.vue";
import type { ViewState } from "../../types/common";
import type {
  DeviceItem,
  DeviceTelemetryRecord,
  MqttSimulateResponse,
} from "../../types/iot";
import {
  getDevices,
  getDeviceTelemetry,
  simulateMqttMessage,
} from "../../api/iot";

const viewState = ref<ViewState>("loading");
const errorMessage = ref("");

const deviceOptions = ref<DeviceItem[]>([]);
const selectedDeviceId = ref("");
const metricCodeFilter = ref("");

const telemetryList = ref<DeviceTelemetryRecord[]>([]);
const total = ref(0);
const queryParams = reactive({
  page: 1,
  size: 15,
});

const columns: TableColumn[] = [
  { key: "dedupKey", label: "去重标识 (MsgId / Seq)", width: "220px" },
  { key: "ts", label: "采集 / 接收时间 (UTC)", width: "220px" },
  { key: "metricCode", label: "监测指标名称 / 编码", minWidth: "200px" },
  { key: "metricValue", label: "指标上报读数", width: "160px", align: "right" },
];

const showSimulateCard = ref(false);
const simulating = ref(false);
const simFeedback = ref<MqttSimulateResponse | null>(null);

const simForm = reactive({
  deviceCode: "DEV-CNC-A01",
  messageId: `msg-${Date.now().toString().slice(-6)}`,
  sequence: 1285,
  metricCode: "spindle_temp",
  metricValue: "62.80",
  metricUnit: "℃",
});

function toggleSimulateCard() {
  showSimulateCard.value = !showSimulateCard.value;
}

function generateNewMessageId() {
  simForm.messageId = `msg-${Date.now().toString().slice(-6)}`;
  simForm.sequence = Math.floor(Math.random() * 9000 + 1000);
}

async function loadDevices() {
  try {
    const res = await getDevices();
    if (res.data) {
      deviceOptions.value = res.data.records || [];
      if (deviceOptions.value.length > 0 && !selectedDeviceId.value) {
        selectedDeviceId.value = deviceOptions.value[0].id as string;
        simForm.deviceCode = deviceOptions.value[0].deviceCode;
      }
    }
  } catch (err: any) {
    console.warn("加载设备下拉项失败:", err);
  }
}

async function fetchTelemetry() {
  if (!selectedDeviceId.value) return;
  viewState.value = "loading";
  errorMessage.value = "";
  try {
    const res = await getDeviceTelemetry({
      deviceId: selectedDeviceId.value,
      metricCode: metricCodeFilter.value.trim() || undefined,
      page: queryParams.page,
      size: queryParams.size,
    });
    if (res.data) {
      telemetryList.value = res.data.records || [];
      total.value = res.data.total || 0;
      viewState.value = telemetryList.value.length === 0 ? "empty" : "ready";
    }
  } catch (err: any) {
    errorMessage.value = err.message || "加载遥测数据失败";
    viewState.value = "error";
  }
}

function handleDeviceChange() {
  const currentDev = deviceOptions.value.find((d) => d.id === selectedDeviceId.value);
  if (currentDev) {
    simForm.deviceCode = currentDev.deviceCode;
  }
  queryParams.page = 1;
  fetchTelemetry();
}

function handlePageChange(page: number) {
  queryParams.page = page;
  fetchTelemetry();
}

async function handleSendSimulate() {
  simulating.value = true;
  simFeedback.value = null;
  try {
    const res = await simulateMqttMessage({
      deviceCode: simForm.deviceCode,
      messageId: simForm.messageId,
      sequence: simForm.sequence,
      ts: new Date().toISOString(),
      metrics: [
        {
          metricCode: simForm.metricCode,
          metricValue: simForm.metricValue,
          metricUnit: simForm.metricUnit,
        },
      ],
    });
    if (res.data) {
      simFeedback.value = res.data;
      await fetchTelemetry();
    }
  } catch (err: any) {
    alert(`模拟上报失败：${err.message}`);
  } finally {
    simulating.value = false;
  }
}

onMounted(async () => {
  await loadDevices();
  await fetchTelemetry();
});
</script>

<style scoped>
.iot-view-container {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.telemetry-filter-bar {
  display: flex;
  align-items: center;
  gap: 16px;
  background: rgba(15, 23, 42, 0.7);
  border: 1px solid rgba(255, 255, 255, 0.08);
  border-radius: 8px;
  padding: 12px 18px;
  flex-wrap: wrap;
}

.filter-item {
  display: flex;
  align-items: center;
  gap: 8px;
}

.filter-item label {
  font-size: 13px;
  color: #94a3b8;
  white-space: nowrap;
}

.select-control, .input-control {
  background: rgba(30, 41, 59, 0.8);
  border: 1px solid rgba(255, 255, 255, 0.12);
  color: #f8fafc;
  padding: 6px 12px;
  border-radius: 6px;
  font-size: 13px;
  outline: none;
}

.select-control:focus, .input-control:focus {
  border-color: #38bdf8;
}

.simulate-card {
  background: rgba(15, 23, 42, 0.85);
  border: 1px solid rgba(56, 189, 248, 0.3);
  border-radius: 10px;
  padding: 16px 20px;
  display: flex;
  flex-direction: column;
  gap: 14px;
  animation: fade-in 0.2s ease-out;
}

.sim-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.sim-tag {
  font-size: 11px;
  color: #38bdf8;
}

.sim-title {
  margin: 2px 0 0;
  font-size: 15px;
  color: #f8fafc;
}

.sim-body {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.sim-grid.three-col {
  display: grid;
  grid-template-columns: 1fr 1fr 1fr;
  gap: 12px;
}

.sim-item {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.sim-item label {
  font-size: 12px;
  color: #94a3b8;
}

.req { color: #f87171; }

.sim-footer {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding-top: 8px;
  border-top: 1px dashed rgba(255, 255, 255, 0.1);
  flex-wrap: wrap;
}

.sim-hint {
  font-size: 12px;
}

.sim-actions {
  display: flex;
  align-items: center;
  gap: 10px;
}

.sim-feedback-box {
  display: flex;
  align-items: flex-start;
  gap: 10px;
  padding: 10px 14px;
  border-radius: 6px;
  font-size: 12px;
}

.is-success {
  background: rgba(52, 211, 153, 0.12);
  border: 1px solid rgba(52, 211, 153, 0.3);
  color: #34d399;
}

.is-duplicate {
  background: rgba(251, 191, 36, 0.12);
  border: 1px solid rgba(251, 191, 36, 0.3);
  color: #fbbf24;
}

.fb-icon { font-size: 15px; }

.fb-text {
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.dedup-cell, .metric-cell, .time-cell {
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.metric-title { color: #f1f5f9; }
.metric-code { font-size: 11px; }

.btn-close {
  background: none;
  border: none;
  color: #94a3b8;
  font-size: 16px;
  cursor: pointer;
}

@keyframes fade-in {
  from { opacity: 0; transform: translateY(-4px); }
  to { opacity: 1; transform: translateY(0); }
}
</style>
