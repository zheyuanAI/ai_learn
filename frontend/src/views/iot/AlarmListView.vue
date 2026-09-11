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
        <el-select
          v-model="queryParams.alarmLevel"
          placeholder="全部级别"
          clearable
          style="width: 160px"
          @change="handleSearch"
        >
          <el-option label="全部级别" value="" />
          <el-option label="紧急 (CRITICAL)" value="CRITICAL" />
          <el-option label="重要 (MAJOR)" value="MAJOR" />
          <el-option label="次要 (MINOR)" value="MINOR" />
          <el-option label="预警 (WARNING)" value="WARNING" />
        </el-select>
      </div>

      <!-- 生命周期状态 -->
      <div class="filter-select-group">
        <label class="filter-label">生命周期：</label>
        <el-select
          v-model="queryParams.status"
          placeholder="全部状态"
          clearable
          style="width: 170px"
          @change="handleSearch"
        >
          <el-option label="全部状态" value="" />
          <el-option label="新触发 (Triggered)" value="Triggered" />
          <el-option label="已确认未恢复 (Acked)" value="Acked" />
          <el-option label="已自愈待确认 (RecoveredUnacked)" value="RecoveredUnacked" />
          <el-option label="已归档恢复 (Recovered)" value="Recovered" />
        </el-select>
      </div>

      <!-- 上下文状态 -->
      <div class="filter-select-group">
        <label class="filter-label">生产上下文：</label>
        <el-select
          v-model="queryParams.contextStatus"
          placeholder="全部"
          clearable
          style="width: 170px"
          @change="handleSearch"
        >
          <el-option label="全部" value="" />
          <el-option label="已绑定 (Linked)" value="Linked" />
          <el-option label="待补充 (Pending)" value="Pending" />
          <el-option label="未关联合同/工单 (Unlinked)" value="Unlinked" />
        </el-select>
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
          <el-button link type="primary" @click="$emit('select-detail', row)">
            详情
          </el-button>

          <!-- 告警确认 (Triggered / RecoveredUnacked) -->
          <el-button
            v-if="row.status === 'Triggered' || row.status === 'RecoveredUnacked'"
            link
            type="warning"
            :disabled="!isActionAllowed(row, 'ack')"
            :title="getActionDisabledReason(row, 'ack') || '确认告警'"
            @click="openAckModal(row)"
          >
            确认
          </el-button>

          <!-- 补充上下文 -->
          <el-button
            v-if="row.status !== 'Recovered'"
            link
            type="primary"
            :disabled="!isActionAllowed(row, 'update-context')"
            :title="getActionDisabledReason(row, 'update-context') || '人工补充生产上下文'"
            @click="openContextModal(row)"
          >
            关联工单
          </el-button>
        </div>
      </template>
    </DataTable>

    <!-- 弹窗 1：告警确认 -->
    <el-dialog
      v-model="ackModalVisible"
      title="设备告警人工确认"
      width="520px"
      append-to-body
      destroy-on-close
    >
      <div class="modal-body-content">
        <div class="modal-hint">
          正在确认告警 <strong>{{ activeAlarm?.alarmNo }}</strong>（{{ activeAlarm?.alarmType }}）。确认后将记录当前操作员与确认时间：
        </div>

        <el-form label-position="top" class="custom-el-form" @submit.prevent="submitAck">
          <el-form-item label="确认处理说明 / 现场排查情况" required>
            <el-input
              v-model="ackComment"
              type="textarea"
              :rows="3"
              placeholder="例如：主轴温度已现场复查，机床主轴降速至 8000rpm 运行冷却..."
            />
          </el-form-item>
        </el-form>
      </div>

      <template #footer>
        <span class="dialog-footer">
          <el-button @click="ackModalVisible = false">取消</el-button>
          <el-button
            type="warning"
            :loading="isSubmitting"
            @click="submitAck"
          >
            提交确认
          </el-button>
        </span>
      </template>
    </el-dialog>

    <!-- 弹窗 2：补充生产业务上下文 -->
    <el-dialog
      v-model="contextModalVisible"
      title="补充告警生产业务上下文"
      width="520px"
      append-to-body
      destroy-on-close
    >
      <div class="modal-body-content">
        <div class="modal-hint">
          为告警 <strong>{{ activeAlarm?.alarmNo }}</strong> 人工关联受影响的生产工单或现场工序执行实例：
        </div>

        <el-form label-position="top" class="custom-el-form" @submit.prevent="submitContext">
          <el-form-item label="关联生产工单 ID">
            <el-input
              v-model="contextForm.workOrderId"
              placeholder="请输入工单 UUID"
              class="font-mono"
            />
          </el-form-item>

          <el-form-item label="关联工序执行编号 / ID">
            <el-input
              v-model="contextForm.operationExecutionId"
              placeholder="请输入工序执行 UUID"
              class="font-mono"
            />
          </el-form-item>
        </el-form>
      </div>

      <template #footer>
        <span class="dialog-footer">
          <el-button @click="contextModalVisible = false">取消</el-button>
          <el-button
            type="primary"
            :loading="isSubmitting"
            @click="submitContext"
          >
            保存关联上下文
          </el-button>
        </span>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { isActionAllowed as checkAction, getActionDisabledReason as getDisabledReason } from "../../utils/actionGuard";
import type { AllowedAction } from "../../types/common";
import { ref, reactive, onMounted } from "vue";
import { ElMessage } from "element-plus";
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

/** 获取告警状态对应的徽标类型 */
function getAlarmStatusBadge(status: AlarmLifecycleStatus): BadgeType {
  switch (status) {
    case "Triggered": return "danger";
    case "Acked": return "primary";
    case "RecoveredUnacked": return "warning";
    case "Recovered": return "success";
    default: return "default";
  }
}

/** 获取告警状态中文显示文本 */
function getAlarmStatusText(status: AlarmLifecycleStatus): string {
  switch (status) {
    case "Triggered": return "新触发待处理";
    case "Acked": return "已确认未恢复";
    case "RecoveredUnacked": return "已自愈待确认";
    case "Recovered": return "已完全恢复";
    default: return status;
  }
}

/** 检查指定操作是否被后端或状态机允许 */
function isActionAllowed(item: { allowedActions?: AllowedAction[] | null }, action: string): boolean {
  return checkAction(item.allowedActions, action);
}

/** 获取指定操作被禁用的原因提示 */
function getActionDisabledReason(item: { allowedActions?: AllowedAction[] | null }, action: string): string | undefined {
  return getDisabledReason(item.allowedActions, action);
}

/** 分页获取告警列表 */
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

/** 触发搜索 */
function handleSearch() {
  queryParams.page = 1;
  fetchAlarmList();
}

/** 重置筛选条件 */
function handleReset() {
  queryParams.keyword = "";
  queryParams.alarmLevel = "";
  queryParams.status = "";
  queryParams.contextStatus = "";
  queryParams.page = 1;
  fetchAlarmList();
}

/** 分页变更处理 */
function handlePageChange(page: number) {
  queryParams.page = page;
  fetchAlarmList();
}

/** 打开人工确认告警对话框 */
function openAckModal(item: DeviceAlarmItem) {
  activeAlarm.value = item;
  ackComment.value = "";
  ackModalVisible.value = true;
}

/** 提交人工确认告警 */
async function submitAck() {
  if (!activeAlarm.value || !ackComment.value.trim()) {
    ElMessage.warning("请填写现场排查说明");
    return;
  }
  isSubmitting.value = true;
  try {
    await ackDeviceAlarm(activeAlarm.value.id as string, { ackComment: ackComment.value.trim() });
    ackModalVisible.value = false;
    ElMessage.success("告警确认已提交！");
    await fetchAlarmList();
  } catch (err: any) {
    ElMessage.error(`确认失败：${err.message}`);
  } finally {
    isSubmitting.value = false;
  }
}

/** 打开补充业务上下文对话框 */
function openContextModal(item: DeviceAlarmItem) {
  activeAlarm.value = item;
  contextForm.workOrderId = item.workOrderId || "";
  contextForm.operationExecutionId = item.operationExecutionId || "";
  contextModalVisible.value = true;
}

/** 提交补充业务上下文 */
async function submitContext() {
  if (!activeAlarm.value) return;
  if (!contextForm.workOrderId.trim() && !contextForm.operationExecutionId.trim()) {
    ElMessage.warning("请填写至少一个后端已分配的工单或工序执行 UUID");
    return;
  }
  isSubmitting.value = true;
  try {
    await updateAlarmBusinessContext(activeAlarm.value.id as string, contextForm);
    contextModalVisible.value = false;
    ElMessage.success("生产业务上下文关联已保存！");
    await fetchAlarmList();
  } catch (err: any) {
    ElMessage.error(`绑定上下文失败：${err.message}`);
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

.text-primary { color: #38bdf8 !important; }
.text-warning { color: #fbbf24 !important; }
.font-xs { font-size: 11px; }

.modal-body-content {
  display: flex;
  flex-direction: column;
  gap: 14px;
}

.modal-hint {
  font-size: 13px;
  color: #cbd5e1;
  line-height: 1.5;
}

.custom-el-form :deep(.el-form-item__label) {
  color: #94a3b8;
  font-size: 13px;
  padding-bottom: 4px;
}
</style>
