<template>
  <div class="alarm-detail-container">
    <!-- 头部返回与导航 -->
    <div class="detail-top-nav">
      <el-button :icon="ArrowLeft" @click="handleBack">
        返回告警列表
      </el-button>
      <div class="top-nav-actions">
        <!-- 穿透全链路追溯中心 -->
        <el-button
          v-if="alarm"
          :icon="Search"
          title="穿透前往全链路全闭环追溯中心"
          @click="goToTraceability"
        >
          全链路追溯
        </el-button>

        <!-- 确认告警 (受 allowedActions 约束) -->
        <el-button
          v-if="alarm && (alarm.status === 'Triggered' || alarm.status === 'RecoveredUnacked')"
          type="warning"
          :disabled="!isActionAllowed('ack')"
          @click="ackModalVisible = true"
        >
          人工确认此告警
        </el-button>
      </div>
    </div>

    <!-- 加载中态 -->
    <div v-if="viewState === 'loading'" class="loading-box">
      <span class="spinner">⏳</span>
      <span>正在加载告警生命周期全景事实...</span>
    </div>

    <!-- 错误异常提示 -->
    <ErrorState
      v-else-if="viewState === 'error'"
      title="告警详情加载失败"
      :message="errorMessage"
      @retry="loadAlarmData"
    />

    <div v-else-if="alarm" class="alarm-main-layout">
      <!-- 顶部基础卡片 -->
      <section class="alarm-header-card">
        <div class="header-main-info">
          <div class="title-row">
            <h2 class="alarm-title font-mono">{{ alarm.alarmNo }}</h2>
            <span class="alarm-level-tag" :class="`level-${alarm.alarmLevel.toLowerCase()}`">
              {{ alarm.alarmLevel }}
            </span>
            <StatusBadge
              :type="getAlarmStatusBadge(alarm.status)"
              :text="getAlarmStatusText(alarm.status)"
              :pulsing="alarm.status === 'Triggered'"
            />
          </div>
          <p class="alarm-sub-title">
            <strong>{{ alarm.alarmType }}</strong>
            · 发生设备: <span class="font-mono text-primary">{{ alarm.deviceName || alarm.deviceId }} ({{ alarm.deviceCode }})</span>
          </p>
        </div>

        <!-- 触发时指标对比小卡片 -->
        <div class="trigger-metric-strip">
          <div class="metric-block">
            <span class="lbl">监控指标：</span>
            <span class="val font-mono">{{ alarm.metricCode || "未返回" }}</span>
          </div>
          <div class="metric-block">
            <span class="lbl">触发异常读数：</span>
            <span class="val text-danger font-bold">{{ alarm.triggerMetricValue || "未返回" }}</span>
          </div>
          <div class="metric-block">
            <span class="lbl">规则阈值判定：</span>
            <span class="val font-mono text-warning">{{ alarm.triggerThreshold || "未返回" }}</span>
          </div>
        </div>
      </section>

      <!-- 中部两栏：生命周期时间轴 & 生产业务上下文软引用 -->
      <div class="alarm-content-grid">
        <!-- 生命周期事件时间轴 -->
        <section class="lifecycle-timeline-card">
          <h3 class="card-section-title">告警生命周期推进事实 (Lifecycle Facts)</h3>

          <div class="timeline-wrapper">
            <!-- 节点 1：触发 -->
            <div class="timeline-step is-done">
              <div class="step-dot danger-dot">1</div>
              <div class="step-detail">
                <div class="step-head">
                  <h4 class="step-name">异常触发 (Triggered)</h4>
                  <span class="step-time font-mono">{{ alarm.triggeredAt }}</span>
                </div>
                <p class="step-desc text-muted">
                  设备端遥测事实超出单指标监控阈值，由 IoT 核心引擎自动产生该告警事实。
                </p>
              </div>
            </div>

            <!-- 节点 2：确认 -->
            <div
              class="timeline-step"
              :class="alarm.ackedAt ? 'is-done' : 'is-pending'"
            >
              <div class="step-dot" :class="alarm.ackedAt ? 'primary-dot' : 'pending-dot'">2</div>
              <div class="step-detail">
                <div class="step-head">
                  <h4 class="step-name">运维确认 (Acked)</h4>
                  <span v-if="alarm.ackedAt" class="step-time font-mono">{{ alarm.ackedAt }}</span>
                  <span v-else class="step-badge-wait">待人工确认</span>
                </div>
                <p v-if="alarm.ackedAt" class="step-desc">
                  <strong>确认人：</strong>{{ alarm.ackUserName || alarm.ackUserId }}<br />
                  <strong>排查日志：</strong>{{ alarm.ackComment || "无详细记录" }}
                </p>
                <p v-else class="step-desc text-muted">
                  尚未有人工确认排查介入记录。
                </p>
              </div>
            </div>

            <!-- 节点 3：恢复 -->
            <div
              class="timeline-step"
              :class="alarm.recoveredAt ? 'is-done' : 'is-pending'"
            >
              <div class="step-dot" :class="alarm.recoveredAt ? 'success-dot' : 'pending-dot'">3</div>
              <div class="step-detail">
                <div class="step-head">
                  <h4 class="step-name">恢复自愈 (Recovered)</h4>
                  <span v-if="alarm.recoveredAt" class="step-time font-mono">{{ alarm.recoveredAt }}</span>
                  <span v-else class="step-badge-wait">尚未自愈恢复</span>
                </div>
                <p v-if="alarm.recoveredAt" class="step-desc">
                  设备后续遥测指标恢复至安全回差范围内，IoT 引擎判定告警自愈结束。
                </p>
                <p v-else class="step-desc text-muted">
                  需等待设备后续遥测读数落入安全阈值方可驱动自愈，禁止由前端直接虚构恢复事实。
                </p>
              </div>
            </div>
          </div>
        </section>

        <!-- 生产业务上下文软引用卡片 -->
        <section class="context-binding-card">
          <div class="binding-header">
            <h3 class="card-section-title">生产业务上下文软引用 (Business Context)</h3>
            <el-button
              v-if="alarm.status !== 'Recovered'"
              link
              type="primary"
              @click="openContextModal"
            >
              更正/补充上下文
            </el-button>
          </div>

          <p class="context-desc text-muted">
            业务上下文仅用于向制造排产和工单追溯保持软引用关联，IoT 告警与设备事实自身不直接物理依赖 Core 表。
          </p>

          <div class="context-properties-list">
            <div class="prop-item">
              <span class="prop-label">上下文关联状态:</span>
              <span
                class="status-tag"
                :class="alarm.contextStatus === 'Linked' ? 'is-linked' : alarm.contextStatus === 'Pending' ? 'is-pending' : 'is-unlinked'"
              >
                {{ alarm.contextStatus === 'Linked' ? '已软引用绑定' : alarm.contextStatus === 'Pending' ? '待补充 (Core未就绪/延后)' : '未关联合同' }}
              </span>
            </div>

            <div class="prop-item">
              <span class="prop-label">关联来源方式:</span>
              <span class="prop-val">{{ alarm.contextSource === 'Auto' ? '自动推导 (Auto)' : alarm.contextSource === 'Manual' ? '人工补充 (Manual)' : '未指定' }}</span>
            </div>

            <div class="prop-item">
              <span class="prop-label">关联生产工单:</span>
              <span class="prop-val font-mono text-primary">{{ alarm.workOrderNo || alarm.workOrderId || "无关联工单" }}</span>
            </div>

            <div class="prop-item">
              <span class="prop-label">关职工序执行:</span>
              <span class="prop-val font-mono">{{ alarm.executionNo || alarm.operationExecutionId || "无关联工序" }}</span>
            </div>
          </div>
        </section>
      </div>
    </div>

    <!-- 弹窗 1：告警确认 -->
    <el-dialog
      v-model="ackModalVisible"
      title="人工确认告警事实"
      width="520px"
      append-to-body
      destroy-on-close
    >
      <el-form label-position="top" class="custom-el-form" @submit.prevent="submitAck">
        <el-form-item label="现场排查记录说明" required>
          <el-input
            v-model="ackComment"
            type="textarea"
            :rows="3"
            placeholder="请输入处理现场情况..."
          />
        </el-form-item>
      </el-form>

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

    <!-- 弹窗 2：更正上下文 -->
    <el-dialog
      v-model="contextModalVisible"
      title="更正/补充生产业务上下文"
      width="520px"
      append-to-body
      destroy-on-close
    >
      <el-form label-position="top" class="custom-el-form" @submit.prevent="submitContext">
        <el-form-item label="受影响生产工单编号/ID">
          <el-input
            v-model="contextForm.workOrderId"
            placeholder="请输入工单 UUID"
            class="font-mono"
          />
        </el-form-item>
        <el-form-item label="受影响工序执行编号/ID">
          <el-input
            v-model="contextForm.operationExecutionId"
            placeholder="请输入工序执行 UUID"
            class="font-mono"
          />
        </el-form-item>
      </el-form>

      <template #footer>
        <span class="dialog-footer">
          <el-button @click="contextModalVisible = false">取消</el-button>
          <el-button
            type="primary"
            :loading="isSubmitting"
            @click="submitContext"
          >
            保存软引用
          </el-button>
        </span>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { isActionAllowed as checkAction, getActionDisabledReason as getDisabledReason } from "../../utils/actionGuard";
import type { AllowedAction } from "../../types/common";
import { ref, reactive, onMounted, watch } from "vue";
import { ArrowLeft, Search } from "@element-plus/icons-vue";
import { ElMessage } from "element-plus";
import { useRouter } from "vue-router";
import {
  StatusBadge,
  ErrorState,
} from "../../components/common";
import type { ViewState, BadgeType } from "../../types/common";
import type {
  DeviceAlarmItem,
  AlarmLifecycleStatus,
} from "../../types/iot";
import {
  getDeviceAlarmDetail,
  ackDeviceAlarm,
  updateAlarmBusinessContext,
} from "../../api/iot";

const props = defineProps<{
  alarmId?: string;
}>();

const emit = defineEmits<{
  (e: "back"): void;
}>();

const router = useRouter();

/** 返回告警列表 */
function handleBack() {
  emit("back");
  router.push("/iot/alarms");
}

/** 穿透跳转至全链路追溯中心 */
function goToTraceability() {
  if (!alarm.value) return;
  router.push({
    path: "/traceability",
    query: {
      entry_type: "DEVICE_ALARM",
      entity_id: alarm.value.id as string,
    },
  });
}

const viewState = ref<ViewState>("loading");
const errorMessage = ref("");
const alarm = ref<DeviceAlarmItem | null>(null);

const ackModalVisible = ref(false);
const ackComment = ref("");

const contextModalVisible = ref(false);
const contextForm = reactive({
  workOrderId: "",
  operationExecutionId: "",
});

const isSubmitting = ref(false);

/** 获取告警状态对应徽标样式 */
function getAlarmStatusBadge(status?: AlarmLifecycleStatus): BadgeType {
  switch (status) {
    case "Triggered": return "danger";
    case "Acked": return "primary";
    case "RecoveredUnacked": return "warning";
    case "Recovered": return "success";
    default: return "default";
  }
}

/** 获取告警状态中文显示文本 */
function getAlarmStatusText(status?: AlarmLifecycleStatus): string {
  switch (status) {
    case "Triggered": return "新触发待处理";
    case "Acked": return "已确认未恢复";
    case "RecoveredUnacked": return "已自愈待确认";
    case "Recovered": return "已完全恢复";
    default: return status || "";
  }
}

/** 检查指定操作是否被后端或状态机允许 */
function isActionAllowed(action: string): boolean {
  return checkAction(alarm.value?.allowedActions, action);
}

/** 加载告警全景详情 */
async function loadAlarmData() {
  viewState.value = "loading";
  errorMessage.value = "";
  try {
    const id = props.alarmId;
    if (!id) {
      throw new Error("缺少告警 UUID，无法查询告警详情");
    }
    const res = await getDeviceAlarmDetail(id);
    alarm.value = res.data || null;
    viewState.value = alarm.value ? "ready" : "empty";
  } catch (err: any) {
    errorMessage.value = err.message || "请求告警详情失败";
    viewState.value = "error";
  }
}

/** 提交人工确认告警 */
async function submitAck() {
  if (!alarm.value || !ackComment.value.trim()) {
    ElMessage.warning("请填写现场排查说明");
    return;
  }
  isSubmitting.value = true;
  try {
    await ackDeviceAlarm(alarm.value.id as string, { ackComment: ackComment.value.trim() });
    ackModalVisible.value = false;
    ElMessage.success("告警确认已完成！");
    await loadAlarmData();
  } catch (err: any) {
    ElMessage.error(`确认失败：${err.message}`);
  } finally {
    isSubmitting.value = false;
  }
}

/** 打开更正业务上下文对话框 */
function openContextModal() {
  if (!alarm.value) return;
  contextForm.workOrderId = alarm.value.workOrderId || "";
  contextForm.operationExecutionId = alarm.value.operationExecutionId || "";
  contextModalVisible.value = true;
}

/** 提交更正业务上下文软引用 */
async function submitContext() {
  if (!alarm.value) return;
  if (!contextForm.workOrderId.trim() && !contextForm.operationExecutionId.trim()) {
    ElMessage.warning("请填写至少一个后端已分配的工单或工序执行 UUID");
    return;
  }
  isSubmitting.value = true;
  try {
    await updateAlarmBusinessContext(alarm.value.id as string, contextForm);
    contextModalVisible.value = false;
    ElMessage.success("生产业务上下文关联已保存！");
    await loadAlarmData();
  } catch (err: any) {
    ElMessage.error(`保存失败：${err.message}`);
  } finally {
    isSubmitting.value = false;
  }
}

watch(() => props.alarmId, () => {
  loadAlarmData();
});

onMounted(() => {
  loadAlarmData();
});
</script>

<style scoped>
.alarm-detail-container {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.detail-top-nav {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.top-nav-actions {
  display: flex;
  align-items: center;
  gap: 10px;
}

.loading-box {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 12px;
  padding: 60px;
  color: #94a3b8;
}

.alarm-main-layout {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.alarm-header-card {
  background: rgba(15, 23, 42, 0.8);
  border: 1px solid rgba(255, 255, 255, 0.1);
  border-radius: 10px;
  padding: 20px;
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.title-row {
  display: flex;
  align-items: center;
  gap: 12px;
  flex-wrap: wrap;
}

.alarm-title {
  margin: 0;
  font-size: 20px;
  color: #f8fafc;
}

.alarm-sub-title {
  margin: 6px 0 0;
  font-size: 14px;
  color: #cbd5e1;
}

.alarm-level-tag {
  font-size: 11px;
  font-weight: 700;
  padding: 2px 8px;
  border-radius: 4px;
}

.level-critical { background: rgba(239, 68, 68, 0.2); color: #f87171; }
.level-major { background: rgba(251, 146, 60, 0.2); color: #fb923c; }
.level-minor { background: rgba(250, 204, 21, 0.2); color: #facc15; }
.level-warning { background: rgba(96, 165, 250, 0.2); color: #60a5fa; }

.trigger-metric-strip {
  display: flex;
  align-items: center;
  gap: 24px;
  flex-wrap: wrap;
  padding-top: 14px;
  border-top: 1px dashed rgba(255, 255, 255, 0.1);
  font-size: 13px;
}

.metric-block .lbl { color: #94a3b8; }
.metric-block .val { color: #f1f5f9; }

.alarm-content-grid {
  display: grid;
  grid-template-columns: 1.2fr 1fr;
  gap: 16px;
}

.lifecycle-timeline-card, .context-binding-card {
  background: rgba(15, 23, 42, 0.7);
  border: 1px solid rgba(255, 255, 255, 0.08);
  border-radius: 8px;
  padding: 20px;
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.card-section-title {
  margin: 0;
  font-size: 15px;
  color: #f8fafc;
}

.binding-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.timeline-wrapper {
  display: flex;
  flex-direction: column;
  gap: 20px;
  position: relative;
  padding-left: 10px;
}

.timeline-step {
  display: flex;
  gap: 14px;
  position: relative;
}

.timeline-step:not(:last-child)::before {
  content: "";
  position: absolute;
  left: 14px;
  top: 30px;
  bottom: -20px;
  width: 2px;
  background: rgba(255, 255, 255, 0.1);
}

.step-dot {
  width: 28px;
  height: 28px;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 12px;
  font-weight: 700;
  z-index: 1;
}

.danger-dot { background: rgba(239, 68, 68, 0.2); color: #f87171; border: 2px solid #ef4444; }
.primary-dot { background: rgba(56, 189, 248, 0.2); color: #38bdf8; border: 2px solid #38bdf8; }
.success-dot { background: rgba(52, 211, 153, 0.2); color: #34d399; border: 2px solid #34d399; }
.pending-dot { background: rgba(100, 116, 139, 0.2); color: #64748b; border: 2px dashed #64748b; }

.step-detail {
  flex: 1;
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.step-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.step-name {
  margin: 0;
  font-size: 14px;
  color: #f1f5f9;
}

.step-time {
  font-size: 12px;
  color: #94a3b8;
}

.step-badge-wait {
  font-size: 11px;
  color: #94a3b8;
  background: rgba(255, 255, 255, 0.05);
  padding: 1px 6px;
  border-radius: 4px;
}

.step-desc {
  margin: 0;
  font-size: 12px;
  line-height: 1.5;
  color: #cbd5e1;
}

.context-desc {
  margin: 0;
  font-size: 12px;
  line-height: 1.5;
}

.context-properties-list {
  display: flex;
  flex-direction: column;
  gap: 12px;
  background: rgba(30, 41, 59, 0.4);
  border: 1px solid rgba(255, 255, 255, 0.06);
  border-radius: 6px;
  padding: 14px;
}

.prop-item {
  display: flex;
  align-items: center;
  justify-content: space-between;
  font-size: 13px;
}

.prop-label { color: #94a3b8; }
.prop-val { color: #f1f5f9; }

.status-tag {
  font-size: 11px;
  padding: 2px 8px;
  border-radius: 4px;
}

.is-linked { background: rgba(52, 211, 153, 0.15); color: #34d399; }
.is-pending { background: rgba(251, 191, 36, 0.15); color: #fbbf24; }
.is-unlinked { background: rgba(148, 163, 184, 0.15); color: #94a3b8; }

.text-danger { color: #f87171 !important; }
.text-warning { color: #fbbf24 !important; }
.text-primary { color: #38bdf8 !important; }

.custom-el-form :deep(.el-form-item__label) {
  color: #94a3b8;
  font-size: 13px;
  padding-bottom: 4px;
}
</style>
