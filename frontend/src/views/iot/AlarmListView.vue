<template>
  <div class="iot-view-container">
    <!-- 统一页面头部 -->
    <PageHeader
      title="设备告警 (Device Alarm) 事实管理"
      tag="IOT / ALARM LIFECYCLE"
      description="管理设备端触发的告警事实。生命周期支持 Triggered -> Acked -> Recovered 与设备先自愈的 Triggered -> RecoveredUnacked -> Recovered 链路，业务上下文对生产工单与工序执行保持软引用。"
    />

    <!-- 统一筛选栏 -->
    <FilterBar
      v-model="queryParams.keyword"
      placeholder="搜索告警编号或设备编码..."
      @search="handleSearch"
      @reset="handleReset"
    >
      <!-- 严重级别 -->
      <div class="filter-select-group">
        <label class="filter-label">告警级别：</label>
        <select v-model="queryParams.alarmLevel" class="filter-select" @change="handleSearch">
          <option value="">全部级别</option>
          <option value="CRITICAL">紧急 (CRITICAL)</option>
          <option value="MAJOR">重要 (MAJOR)</option>
          <option value="MINOR">次要 (MINOR)</option>
          <option value="WARNING">预警 (WARNING)</option>
        </select>
      </div>

      <!-- 生命周期状态 -->
      <div class="filter-select-group">
        <label class="filter-label">生命周期：</label>
        <select v-model="queryParams.status" class="filter-select" @change="handleSearch">
          <option value="">全部状态</option>
          <option value="Triggered">新触发 (Triggered)</option>
          <option value="Acked">已确认未恢复 (Acked)</option>
          <option value="RecoveredUnacked">已自愈待确认 (RecoveredUnacked)</option>
          <option value="Recovered">已归档恢复 (Recovered)</option>
        </select>
      </div>

      <!-- 上下文状态 -->
      <div class="filter-select-group">
        <label class="filter-label">生产上下文：</label>
        <select v-model="queryParams.contextStatus" class="filter-select" @change="handleSearch">
          <option value="">全部</option>
          <option value="Linked">已绑定 (Linked)</option>
          <option value="Pending">待补充 (Pending)</option>
          <option value="Unlinked">未关联合同/工单 (Unlinked)</option>
        </select>
      </div>
    </FilterBar>

    <!-- 错误异常提示 -->
    <ErrorState
      v-if="viewState === 'error'"
      title="告警记录加载失败"
      :message="errorMessage"
      @retry="fetchAlarmList"
    />

    <!-- 数据表格 -->
    <DataTable
      v-else
      :columns="columns"
      :data="alarmList"
      :loading="viewState === 'loading'"
      :page="queryParams.page"
      :size="queryParams.size"
      :total="total"
      empty-text="暂无匹配的设备告警记录"
      @page-change="handlePageChange"
    >
      <!-- 告警编号 -->
      <template #alarmNo="{ row }">
        <span class="font-mono highlight-code">{{ row.alarmNo }}</span>
      </template>

      <!-- 关联设备 -->
      <template #deviceId="{ row }">
        <div class="dev-cell">
          <span class="dev-name">{{ row.deviceName || row.deviceId }}</span>
          <span class="dev-code font-mono text-muted">{{ row.deviceCode }}</span>
        </div>
      </template>

      <!-- 告警类型与级别 -->
      <template #alarmType="{ row }">
        <div class="type-cell">
          <span class="type-name">{{ row.alarmType }}</span>
          <span class="alarm-level-tag" :class="`level-${row.alarmLevel?.toLowerCase()}`">
            {{ row.alarmLevel }}
          </span>
        </div>
      </template>

      <!-- 生命周期状态 -->
      <template #status="{ row }">
        <StatusBadge
          :type="getAlarmStatusBadge(row.status)"
          :text="getAlarmStatusText(row.status)"
          :pulsing="row.status === 'Triggered'"
        />
      </template>

      <!-- 生产业务上下文 -->
      <template #contextStatus="{ row }">
        <div class="context-cell">
          <div v-if="row.contextStatus === 'Linked'" class="linked-info">
            <span class="font-mono text-primary">{{ row.workOrderNo || "工单" }}</span>
            <span class="font-mono text-muted font-xs">{{ row.executionNo || "工序执行" }}</span>
          </div>
          <span v-else-if="row.contextStatus === 'Pending'" class="text-warning font-xs">待补充上下文</span>
          <span v-else class="text-muted font-xs">无业务绑定</span>
        </div>
      </template>

      <!-- 触发时间 -->
      <template #triggeredAt="{ row }">
        <span class="font-mono text-muted date-text">{{ row.triggeredAt }}</span>
      </template>

      <!-- 操作 (受 allowedActions 约束) -->
      <template #actions="{ row }">
        <div class="action-btn-group">
          <!-- 详情 -->
          <button type="button" class="btn-text" @click="$emit('select-detail', row)">
            详情
          </button>

          <!-- 告警确认 (Triggered / RecoveredUnacked) -->
          <button
            v-if="row.status === 'Triggered' || row.status === 'RecoveredUnacked'"
            type="button"
            class="btn-text text-warning"
            :disabled="!isActionAllowed(row, 'ack')"
            :title="getActionDisabledReason(row, 'ack') || '确认告警'"
            @click="openAckModal(row)"
          >
            确认
          </button>

          <!-- 补充上下文 -->
          <button
            v-if="row.status !== 'Recovered'"
            type="button"
            class="btn-text text-primary"
            :disabled="!isActionAllowed(row, 'update-context')"
            :title="getActionDisabledReason(row, 'update-context') || '人工补充生产上下文'"
            @click="openContextModal(row)"
          >
            关联工单
          </button>
        </div>
      </template>
    </DataTable>

    <!-- 弹窗 1：告警确认 -->
    <div v-if="ackModalVisible" class="modal-mask" @click.self="ackModalVisible = false">
      <div class="modal-card">
        <div class="modal-header">
          <h3 class="modal-title">设备告警人工确认</h3>
          <button type="button" class="btn-close" @click="ackModalVisible = false">✕</button>
        </div>

        <form class="modal-body" @submit.prevent="submitAck">
          <p class="modal-hint">
            正在确认告警 <strong>{{ activeAlarm?.alarmNo }}</strong>（{{ activeAlarm?.alarmType }}）。确认后将记录当前操作员与确认时间：
          </p>

          <div class="form-item">
            <label>确认处理说明 / 现场排查情况 <span class="req">*</span></label>
            <textarea
              v-model="ackComment"
              class="form-input"
              rows="3"
              placeholder="例如：主轴温度已现场复查，机床主轴降速至 8000rpm 运行冷却..."
              required
            ></textarea>
          </div>

          <div class="modal-footer">
            <button type="button" class="btn btn-secondary" @click="ackModalVisible = false">取消</button>
            <button type="submit" class="btn btn-warning" :disabled="isSubmitting">提交确认</button>
          </div>
        </form>
      </div>
    </div>

    <!-- 弹窗 2：补充生产业务上下文 -->
    <div v-if="contextModalVisible" class="modal-mask" @click.self="contextModalVisible = false">
      <div class="modal-card">
        <div class="modal-header">
          <h3 class="modal-title">补充告警生产业务上下文</h3>
          <button type="button" class="btn-close" @click="contextModalVisible = false">✕</button>
        </div>

        <form class="modal-body" @submit.prevent="submitContext">
          <p class="modal-hint">
            为告警 <strong>{{ activeAlarm?.alarmNo }}</strong> 人工关联受影响的生产工单或现场工序执行实例：
          </p>

          <div class="form-item">
            <label>关联生产工单 ID</label>
            <input
              v-model="contextForm.workOrderId"
              type="text"
              class="form-input font-mono"
              placeholder="例如 wo-001 或 WO-20260901-001"
            />
          </div>

          <div class="form-item">
            <label>关联工序执行编号 / ID</label>
            <input
              v-model="contextForm.operationExecutionId"
              type="text"
              class="form-input font-mono"
              placeholder="例如 exec-002 或 EXE-20260901-02"
            />
          </div>

          <div class="modal-footer">
            <button type="button" class="btn btn-secondary" @click="contextModalVisible = false">取消</button>
            <button type="submit" class="btn btn-primary" :disabled="isSubmitting">保存关联上下文</button>
          </div>
        </form>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted } from "vue";
import {
  PageHeader,
  FilterBar,
  DataTable,
  StatusBadge,
  ErrorState,
} from "../../components/common";
import type { TableColumn } from "../../components/common/DataTable.vue";
import type { ViewState, BadgeType } from "../../types/common";
import type {
  DeviceAlarmItem,
  AlarmLifecycleStatus,
  AlarmLevel,
  AlarmContextStatus,
} from "../../types/iot";
import {
  getDeviceAlarms,
  ackDeviceAlarm,
  updateAlarmBusinessContext,
} from "../../api/iot";

defineEmits<{
  (e: "select-detail", item: DeviceAlarmItem): void;
}>();

const viewState = ref<ViewState>("loading");
const errorMessage = ref("");

const alarmList = ref<DeviceAlarmItem[]>([]);
const total = ref(0);
const queryParams = reactive({
  page: 1,
  size: 10,
  keyword: "",
  alarmLevel: "" as AlarmLevel | "",
  status: "" as AlarmLifecycleStatus | "",
  contextStatus: "" as AlarmContextStatus | "",
});

const columns: TableColumn[] = [
  { key: "alarmNo", label: "告警编号", width: "180px" },
  { key: "deviceId", label: "发生设备", minWidth: "180px" },
  { key: "alarmType", label: "告警类型 / 级别", minWidth: "180px" },
  { key: "status", label: "生命周期状态", width: "140px", align: "center" },
  { key: "contextStatus", label: "生产业务上下文", width: "170px" },
  { key: "triggeredAt", label: "触发时间", width: "160px" },
  { key: "actions", label: "操作", width: "160px", align: "center" },
];

const activeAlarm = ref<DeviceAlarmItem | null>(null);
const ackModalVisible = ref(false);
const ackComment = ref("");

const contextModalVisible = ref(false);
const contextForm = reactive({
  workOrderId: "",
  operationExecutionId: "",
});

const isSubmitting = ref(false);

function getAlarmStatusBadge(status: AlarmLifecycleStatus): BadgeType {
  switch (status) {
    case "Triggered": return "danger";
    case "Acked": return "primary";
    case "RecoveredUnacked": return "warning";
    case "Recovered": return "success";
    default: return "default";
  }
}

function getAlarmStatusText(status: AlarmLifecycleStatus): string {
  switch (status) {
    case "Triggered": return "新触发待处理";
    case "Acked": return "已确认未恢复";
    case "RecoveredUnacked": return "已自愈待确认";
    case "Recovered": return "已完全恢复";
    default: return status;
  }
}

function isActionAllowed(item: DeviceAlarmItem, action: string): boolean {
  if (!item.allowedActions || item.allowedActions.length === 0) return true;
  const match = item.allowedActions.find((a) => a.action === action);
  return match ? match.enabled : true;
}

function getActionDisabledReason(item: DeviceAlarmItem, action: string): string | undefined {
  const match = item.allowedActions?.find((a) => a.action === action);
  return match && !match.enabled ? match.reason : undefined;
}

async function fetchAlarmList() {
  viewState.value = "loading";
  errorMessage.value = "";
  try {
    const res = await getDeviceAlarms({
      page: queryParams.page,
      size: queryParams.size,
      alarmLevel: queryParams.alarmLevel || undefined,
      status: queryParams.status || undefined,
      contextStatus: queryParams.contextStatus || undefined,
    });
    if (res.data) {
      let list = res.data.records || [];
      if (queryParams.keyword.trim()) {
        const kw = queryParams.keyword.toLowerCase();
        list = list.filter(
          (a) =>
            a.alarmNo.toLowerCase().includes(kw) ||
            a.deviceCode?.toLowerCase().includes(kw) ||
            a.alarmType.toLowerCase().includes(kw)
        );
      }
      alarmList.value = list;
      total.value = res.data.total || list.length;
      viewState.value = alarmList.value.length === 0 ? "empty" : "ready";
    }
  } catch (err: any) {
    errorMessage.value = err.message || "请求告警列表失败";
    viewState.value = "error";
  }
}

function handleSearch() {
  queryParams.page = 1;
  fetchAlarmList();
}

function handleReset() {
  queryParams.keyword = "";
  queryParams.alarmLevel = "";
  queryParams.status = "";
  queryParams.contextStatus = "";
  queryParams.page = 1;
  fetchAlarmList();
}

function handlePageChange(page: number) {
  queryParams.page = page;
  fetchAlarmList();
}

function openAckModal(item: DeviceAlarmItem) {
  activeAlarm.value = item;
  ackComment.value = "";
  ackModalVisible.value = true;
}

async function submitAck() {
  if (!activeAlarm.value || !ackComment.value.trim()) return;
  isSubmitting.value = true;
  try {
    await ackDeviceAlarm(activeAlarm.value.id as string, { ackComment: ackComment.value.trim() });
    ackModalVisible.value = false;
    await fetchAlarmList();
  } catch (err: any) {
    alert(`确认失败：${err.message}`);
  } finally {
    isSubmitting.value = false;
  }
}

function openContextModal(item: DeviceAlarmItem) {
  activeAlarm.value = item;
  contextForm.workOrderId = item.workOrderId || "wo-001";
  contextForm.operationExecutionId = item.operationExecutionId || "exec-002";
  contextModalVisible.value = true;
}

async function submitContext() {
  if (!activeAlarm.value) return;
  isSubmitting.value = true;
  try {
    await updateAlarmBusinessContext(activeAlarm.value.id as string, contextForm);
    contextModalVisible.value = false;
    await fetchAlarmList();
  } catch (err: any) {
    alert(`绑定上下文失败：${err.message}`);
  } finally {
    isSubmitting.value = false;
  }
}

onMounted(() => {
  fetchAlarmList();
});
</script>

<style scoped>
.iot-view-container {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.filter-select-group {
  display: flex;
  align-items: center;
  gap: 8px;
}

.filter-label {
  font-size: 13px;
  color: #94a3b8;
}

.filter-select {
  background: rgba(30, 41, 59, 0.8);
  border: 1px solid rgba(255, 255, 255, 0.12);
  color: #f8fafc;
  padding: 6px 12px;
  border-radius: 6px;
  font-size: 13px;
  outline: none;
}

.highlight-code {
  color: #38bdf8;
  font-weight: 600;
}

.dev-cell, .type-cell, .context-cell {
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.dev-name, .type-name {
  color: #f1f5f9;
}

.dev-code { font-size: 11px; }

.alarm-level-tag {
  align-self: flex-start;
  font-size: 10px;
  font-weight: 700;
  padding: 1px 6px;
  border-radius: 4px;
  text-transform: uppercase;
}

.level-critical { background: rgba(239, 68, 68, 0.2); color: #f87171; }
.level-major { background: rgba(251, 146, 60, 0.2); color: #fb923c; }
.level-minor { background: rgba(250, 204, 21, 0.2); color: #facc15; }
.level-warning { background: rgba(96, 165, 250, 0.2); color: #60a5fa; }

.linked-info {
  display: flex;
  flex-direction: column;
}

.date-text { font-size: 12px; }

.action-btn-group {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 6px;
}

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
  text-decoration: none;
}

.text-primary { color: #38bdf8 !important; }
.text-warning { color: #fbbf24 !important; }
.font-xs { font-size: 11px; }

/* 模态框 */
.modal-mask {
  position: fixed;
  inset: 0;
  background: rgba(0, 0, 0, 0.7);
  backdrop-filter: blur(4px);
  z-index: 1000;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 16px;
}

.modal-card {
  background: #0f172a;
  border: 1px solid rgba(255, 255, 255, 0.15);
  border-radius: 10px;
  width: 100%;
  max-width: 500px;
  box-shadow: 0 20px 30px rgba(0, 0, 0, 0.5);
  overflow: hidden;
}

.modal-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 16px 20px;
  border-bottom: 1px solid rgba(255, 255, 255, 0.08);
}

.modal-title {
  margin: 0;
  font-size: 16px;
  color: #f8fafc;
}

.modal-body {
  padding: 20px;
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.modal-hint {
  margin: 0;
  font-size: 13px;
  color: #cbd5e1;
  line-height: 1.5;
}

.form-item {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.form-item label {
  font-size: 12px;
  color: #94a3b8;
}

.req { color: #f87171; }

.form-input {
  background: rgba(30, 41, 59, 0.8);
  border: 1px solid rgba(255, 255, 255, 0.12);
  color: #f8fafc;
  padding: 8px 12px;
  border-radius: 6px;
  font-size: 13px;
  outline: none;
}

.form-input:focus { border-color: #38bdf8; }

.modal-footer {
  display: flex;
  align-items: center;
  justify-content: flex-end;
  gap: 12px;
  padding: 16px 20px;
  border-top: 1px solid rgba(255, 255, 255, 0.08);
  background: rgba(0, 0, 0, 0.2);
}

.btn-close {
  background: none;
  border: none;
  color: #94a3b8;
  font-size: 16px;
  cursor: pointer;
}
</style>
