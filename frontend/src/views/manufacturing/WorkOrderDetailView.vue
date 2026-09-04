<template>
  <div class="wo-detail-container">
    <!-- 头部面包屑与状态导航 -->
    <div class="detail-header-nav">
      <button type="button" class="btn-back" @click="$emit('back')">
        ‹ 返回工单列表
      </button>
      <div class="header-tags">
        <span class="font-mono text-muted">工单 ID: {{ workOrder?.id || id }}</span>
      </div>
    </div>

    <!-- 加载中或错误态 -->
    <div v-if="viewState === 'loading'" class="loading-box">
      <span class="spinner">⏳</span>
      <span>正在加载工单全景事实与关联单据...</span>
    </div>

    <ErrorState
      v-else-if="viewState === 'error'"
      title="无法读取工单详情"
      :message="errorMessage"
      @retry="loadAllData"
    />

    <div v-else-if="workOrder" class="detail-main-layout">
      <!-- 概览看板 -->
      <section class="overview-banner-card">
        <div class="banner-top-row">
          <div class="banner-left">
            <div class="wo-title-group">
              <h2 class="wo-title font-mono">{{ workOrder.workOrderNo }}</h2>
              <StatusBadge
                :type="getStatusBadgeType(workOrder.status)"
                :text="getStatusText(workOrder.status)"
                :pulsing="workOrder.status === 'InProgress'"
              />
            </div>
            <p class="product-line">
              <strong>{{ workOrder.productName }}</strong>
              <span class="spec font-mono text-muted">（{{ workOrder.productSpec || workOrder.productCode }}）</span>
            </p>
          </div>

          <!-- 顶部状态驱动操作按钮组 (受 allowedActions 约束) -->
          <div class="banner-actions">
            <!-- 提交审核 -->
            <button
              v-if="workOrder.status === 'Draft' || workOrder.status === 'Rejected'"
              type="button"
              class="btn btn-primary"
              :disabled="!isActionAllowed('submit')"
              :title="getActionDisabledReason('submit') || '提交审核'"
              @click="promptAction('submit', '提交审核确认', '确认将工单提交至质检/计划主管审核？')"
            >
              提交审核
            </button>

            <!-- 审批通过与驳回 -->
            <template v-if="workOrder.status === 'PendingApproval'">
              <button
                type="button"
                class="btn btn-success"
                :disabled="!isActionAllowed('approve')"
                :title="getActionDisabledReason('approve') || '审核通过'"
                @click="promptAction('approve', '审核通过确认', '确认审核通过此工单并正式下达排产？有效 BOM 与路线版本将被锁定。')"
              >
                审核通过
              </button>
              <button
                type="button"
                class="btn btn-warning"
                :disabled="!isActionAllowed('reject')"
                :title="getActionDisabledReason('reject') || '驳回审核'"
                @click="openRejectModal"
              >
                驳回
              </button>
            </template>

            <!-- 正常完工 -->
            <button
              v-if="workOrder.status === 'InProgress'"
              type="button"
              class="btn btn-success"
              :disabled="!isActionAllowed('complete')"
              :title="getActionDisabledReason('complete') || '正常完成工单'"
              @click="promptAction('complete', '正常完工确认', '确认全部工序报工合格并归档完成？')"
            >
              工单完工
            </button>

            <!-- 手工强制结案 -->
            <button
              v-if="workOrder.status === 'Released' || workOrder.status === 'InProgress'"
              type="button"
              class="btn btn-secondary"
              :disabled="!isActionAllowed('manual-complete')"
              :title="getActionDisabledReason('manual-complete') || '手工强制结案'"
              @click="openManualCompleteModal"
            >
              手工结案
            </button>
          </div>
        </div>

        <!-- 关键进度四指标卡片 (全 QuantityText 渲染) -->
        <div class="metrics-grid">
          <div class="metric-card">
            <span class="metric-title">计划生产数</span>
            <div class="metric-num">
              <QuantityText :value="workOrder.plannedQty" unit="件" />
            </div>
            <span class="metric-sub text-muted">起止: {{ workOrder.plannedStartTime }} ~ {{ workOrder.plannedFinishTime }}</span>
          </div>

          <div class="metric-card">
            <span class="metric-title">累计申报产出</span>
            <div class="metric-num highlight-blue">
              <QuantityText :value="workOrder.reportedQty" unit="件" />
            </div>
            <span class="metric-sub text-muted">包含合格与不良品</span>
          </div>

          <div class="metric-card">
            <span class="metric-title">质检合格数</span>
            <div class="metric-num highlight-green">
              <QuantityText :value="workOrder.qualifiedQty" unit="件" />
            </div>
            <span class="metric-sub text-muted">不良退损: <QuantityText :value="workOrder.defectQty" unit="件" class="text-danger" /></span>
          </div>

          <div class="metric-card">
            <span class="metric-title">成品入库完成数</span>
            <div class="metric-num highlight-cyan">
              <QuantityText :value="workOrder.receivedQty" unit="件" />
            </div>
            <span class="metric-sub text-muted">实物库存已增加</span>
          </div>
        </div>

        <!-- 锁定基础属性信息栏 -->
        <div class="meta-strip">
          <div class="meta-item">
            <span class="lbl">锁定 BOM：</span>
            <span class="val font-mono">{{ workOrder.bomCode || workOrder.bomId }} ({{ workOrder.bomVersion || 'V1.0' }})</span>
          </div>
          <div class="meta-item">
            <span class="lbl">锁定工艺路线：</span>
            <span class="val font-mono">{{ workOrder.routingCode || workOrder.routingId }} ({{ workOrder.routingVersion || 'V1.0' }})</span>
          </div>
          <div v-if="workOrder.sourceSalesOrderNo" class="meta-item">
            <span class="lbl">销售来源订单：</span>
            <span class="val font-mono text-primary">{{ workOrder.sourceSalesOrderNo }}</span>
          </div>
          <div v-if="workOrder.rejectionReason" class="meta-item full-width text-danger">
            <span class="lbl">审核退回原因：</span>
            <span class="val">{{ workOrder.rejectionReason }}</span>
          </div>
          <div v-if="workOrder.completionReason" class="meta-item full-width text-warning">
            <span class="lbl">手工结案原因：</span>
            <span class="val">{{ workOrder.completionReason }}</span>
          </div>
        </div>
      </section>

      <!-- 关联事实子单据标签页 -->
      <section class="tabs-container">
        <div class="tabs-header">
          <button
            type="button"
            class="tab-btn"
            :class="{ 'is-active': activeTab === 'dispatch' }"
            @click="activeTab = 'dispatch'"
          >
            派工安排 ({{ dispatchOrders.length }})
          </button>
          <button
            type="button"
            class="tab-btn"
            :class="{ 'is-active': activeTab === 'execution' }"
            @click="activeTab = 'execution'"
          >
            工序现场执行 ({{ executions.length }})
          </button>
          <button
            type="button"
            class="tab-btn"
            :class="{ 'is-active': activeTab === 'movement' }"
            @click="activeTab = 'movement'"
          >
            生产领料 / 退料 ({{ materialIssues.length + materialReturns.length }})
          </button>
          <button
            type="button"
            class="tab-btn"
            :class="{ 'is-active': activeTab === 'receipt' }"
            @click="activeTab = 'receipt'"
          >
            成品入库 ({{ finishedReceipts.length }})
          </button>
        </div>

        <!-- Tab 1: 派工单 -->
        <div v-if="activeTab === 'dispatch'" class="tab-pane">
          <table class="sub-table">
            <thead>
              <tr>
                <th>派工单号</th>
                <th>工序步骤</th>
                <th>派工数量</th>
                <th>责任操作员</th>
                <th>绑定设备</th>
                <th>派工状态</th>
              </tr>
            </thead>
            <tbody>
              <tr v-if="dispatchOrders.length === 0">
                <td colspan="6" class="text-center text-muted">暂无派工安排</td>
              </tr>
              <tr v-for="d in dispatchOrders" :key="d.id">
                <td class="font-mono highlight-code">{{ d.dispatchNo }}</td>
                <td>{{ d.operationName }} (#{{ d.operationNo }})</td>
                <td><QuantityText :value="d.dispatchQty" unit="件" /></td>
                <td>{{ d.operatorName || d.operatorId }}</td>
                <td class="font-mono">{{ d.deviceName || d.deviceCode || "通用人工工位" }}</td>
                <td>
                  <StatusBadge
                    :type="d.status === 'Completed' ? 'success' : d.status === 'Processing' ? 'primary' : 'info'"
                    :text="d.status"
                  />
                </td>
              </tr>
            </tbody>
          </table>
        </div>

        <!-- Tab 2: 现场执行与报工 -->
        <div v-if="activeTab === 'execution'" class="tab-pane">
          <table class="sub-table">
            <thead>
              <tr>
                <th>执行编号</th>
                <th>执行工序</th>
                <th>执行人</th>
                <th>执行状态</th>
                <th>实际开始时间</th>
                <th>实际完工时间</th>
                <th>累计报工数</th>
              </tr>
            </thead>
            <tbody>
              <tr v-if="executions.length === 0">
                <td colspan="7" class="text-center text-muted">尚未生成工序执行事实</td>
              </tr>
              <tr v-for="e in executions" :key="e.id">
                <td class="font-mono highlight-code">{{ e.executionNo }}</td>
                <td>{{ e.operationName }}</td>
                <td>{{ e.operatorName || e.operatorId }}</td>
                <td>
                  <StatusBadge
                    :type="e.status === 'Running' ? 'primary' : e.status === 'Completed' ? 'success' : 'default'"
                    :text="e.status"
                    :pulsing="e.status === 'Running'"
                  />
                </td>
                <td class="font-mono text-muted">{{ e.startedAt || "-" }}</td>
                <td class="font-mono text-muted">{{ e.completedAt || "-" }}</td>
                <td><QuantityText :value="e.reportedQty || '0.00'" unit="件" /></td>
              </tr>
            </tbody>
          </table>
        </div>

        <!-- Tab 3: 领退料 -->
        <div v-if="activeTab === 'movement'" class="tab-pane">
          <div class="movement-split">
            <div class="split-card">
              <h4 class="sub-title">领料单 (Material Issues)</h4>
              <table class="sub-table">
                <thead>
                  <tr>
                    <th>单号</th>
                    <th>状态</th>
                    <th>物料项数</th>
                    <th>出库确认时间</th>
                  </tr>
                </thead>
                <tbody>
                  <tr v-if="materialIssues.length === 0">
                    <td colspan="4" class="text-center text-muted">暂无领料单</td>
                  </tr>
                  <tr v-for="m in materialIssues" :key="m.id">
                    <td class="font-mono highlight-code">{{ m.issueNo }}</td>
                    <td>
                      <StatusBadge :type="m.status === 'Confirmed' ? 'success' : 'warning'" :text="m.status" />
                    </td>
                    <td>{{ m.items?.length || 0 }} 项原料</td>
                    <td class="font-mono text-muted">{{ m.confirmedAt || "待出库" }}</td>
                  </tr>
                </tbody>
              </table>
            </div>

            <div class="split-card">
              <h4 class="sub-title">退料单 (Material Returns)</h4>
              <table class="sub-table">
                <thead>
                  <tr>
                    <th>单号</th>
                    <th>状态</th>
                    <th>退料项数</th>
                    <th>退库确认时间</th>
                  </tr>
                </thead>
                <tbody>
                  <tr v-if="materialReturns.length === 0">
                    <td colspan="4" class="text-center text-muted">暂无退料单</td>
                  </tr>
                  <tr v-for="r in materialReturns" :key="r.id">
                    <td class="font-mono highlight-code">{{ r.returnNo }}</td>
                    <td>
                      <StatusBadge :type="r.status === 'Confirmed' ? 'success' : 'warning'" :text="r.status" />
                    </td>
                    <td>{{ r.items?.length || 0 }} 项原料</td>
                    <td class="font-mono text-muted">{{ r.confirmedAt || "待入库" }}</td>
                  </tr>
                </tbody>
              </table>
            </div>
          </div>
        </div>

        <!-- Tab 4: 成品入库 -->
        <div v-if="activeTab === 'receipt'" class="tab-pane">
          <table class="sub-table">
            <thead>
              <tr>
                <th>入库单号</th>
                <th>入库数量</th>
                <th>目标仓库 / 库位</th>
                <th>状态</th>
                <th>库存流水关联</th>
                <th>确认时间</th>
              </tr>
            </thead>
            <tbody>
              <tr v-if="finishedReceipts.length === 0">
                <td colspan="6" class="text-center text-muted">暂无成品入库单记录</td>
              </tr>
              <tr v-for="fg in finishedReceipts" :key="fg.id">
                <td class="font-mono highlight-code">{{ fg.receiptNo }}</td>
                <td><QuantityText :value="fg.receiptQty" unit="件" /></td>
                <td>{{ fg.warehouseName || fg.warehouseId }} / {{ fg.locationCode || fg.locationId }}</td>
                <td>
                  <StatusBadge :type="fg.status === 'Confirmed' ? 'success' : 'warning'" :text="fg.status" />
                </td>
                <td class="font-mono text-muted">{{ fg.inventoryTransactionId || "-" }}</td>
                <td class="font-mono text-muted">{{ fg.confirmedAt || "待确认" }}</td>
              </tr>
            </tbody>
          </table>
        </div>
      </section>
    </div>

    <!-- 审核驳回弹窗 -->
    <div v-if="rejectModalVisible" class="modal-mask" @click.self="rejectModalVisible = false">
      <div class="modal-card">
        <div class="modal-header">
          <h3 class="modal-title">退回驳回工单审核</h3>
          <button type="button" class="btn-close" @click="rejectModalVisible = false">✕</button>
        </div>
        <form class="modal-body" @submit.prevent="submitReject">
          <div class="form-item">
            <label>驳回退回原因 <span class="req">*</span></label>
            <textarea
              v-model="rejectionReason"
              class="form-input"
              rows="3"
              placeholder="请输入退回审批的具体原因..."
              required
            ></textarea>
          </div>
          <div class="modal-footer">
            <button type="button" class="btn btn-secondary" @click="rejectModalVisible = false">取消</button>
            <button type="submit" class="btn btn-warning" :disabled="isSubmitting">
              {{ isSubmitting ? "处理中..." : "确认退回" }}
            </button>
          </div>
        </form>
      </div>
    </div>

    <!-- 手工强制结案弹窗 -->
    <div v-if="manualModalVisible" class="modal-mask" @click.self="manualModalVisible = false">
      <div class="modal-card">
        <div class="modal-header">
          <h3 class="modal-title">工单提前结案确认</h3>
          <button type="button" class="btn-close" @click="manualModalVisible = false">✕</button>
        </div>
        <form class="modal-body" @submit.prevent="submitManualComplete">
          <div class="form-item">
            <label>强制截单/完工原因说明 <span class="req">*</span></label>
            <textarea
              v-model="manualCompleteReason"
              class="form-input"
              rows="3"
              placeholder="请填写手工结案原因..."
              required
            ></textarea>
          </div>
          <div class="modal-footer">
            <button type="button" class="btn btn-secondary" @click="manualModalVisible = false">取消</button>
            <button type="submit" class="btn btn-danger" :disabled="isSubmitting">
              {{ isSubmitting ? "结案中..." : "确认强制完工" }}
            </button>
          </div>
        </form>
      </div>
    </div>

    <!-- 统一二次确认弹窗 -->
    <ConfirmDialog
      v-model:visible="confirmState.visible"
      :title="confirmState.title"
      :message="confirmState.message"
      :loading="confirmState.loading"
      @confirm="executePromptAction"
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
import type { ViewState, BadgeType } from "../../types/common";
import type {
  WorkOrderItem,
  WorkOrderStatus,
  DispatchOrderItem,
  OperationExecutionItem,
  MaterialIssueItem,
  MaterialReturnItem,
  FinishedGoodsReceiptItem,
} from "../../types/manufacturing";
import {
  getWorkOrderDetail,
  getWorkOrders,
  submitWorkOrder,
  approveWorkOrder,
  rejectWorkOrder,
  completeWorkOrder,
  manualCompleteWorkOrder,
  getDispatchOrders,
  getOperationExecutions,
  getMaterialIssues,
  getMaterialReturns,
  getFinishedGoodsReceipts,
} from "../../api/manufacturing";

const props = withDefaults(
  defineProps<{
    id?: string;
  }>(),
  {
    id: "wo-001",
  }
);

defineEmits<{
  (e: "back"): void;
}>();

const viewState = ref<ViewState>("loading");
const errorMessage = ref("");
const workOrder = ref<WorkOrderItem | null>(null);

const activeTab = ref<"dispatch" | "execution" | "movement" | "receipt">("dispatch");

const dispatchOrders = ref<DispatchOrderItem[]>([]);
const executions = ref<OperationExecutionItem[]>([]);
const materialIssues = ref<MaterialIssueItem[]>([]);
const materialReturns = ref<MaterialReturnItem[]>([]);
const finishedReceipts = ref<FinishedGoodsReceiptItem[]>([]);

const rejectModalVisible = ref(false);
const rejectionReason = ref("");
const manualModalVisible = ref(false);
const manualCompleteReason = ref("");
const isSubmitting = ref(false);

const confirmState = reactive({
  visible: false,
  loading: false,
  title: "",
  message: "",
  actionType: "" as "submit" | "approve" | "complete",
});

function getStatusBadgeType(status?: WorkOrderStatus): BadgeType {
  switch (status) {
    case "Draft": return "default";
    case "PendingApproval": return "warning";
    case "Released": return "info";
    case "InProgress": return "primary";
    case "Completed": return "success";
    case "Rejected": return "danger";
    default: return "default";
  }
}

function getStatusText(status?: WorkOrderStatus): string {
  switch (status) {
    case "Draft": return "未提交草稿";
    case "PendingApproval": return "待审核";
    case "Released": return "已下达排产";
    case "InProgress": return "生产中";
    case "Completed": return "已完工结案";
    case "Rejected": return "审批退回";
    default: return status || "";
  }
}

function isActionAllowed(action: string): boolean {
  if (!workOrder.value?.allowedActions || workOrder.value.allowedActions.length === 0) return true;
  const match = workOrder.value.allowedActions.find((a) => a.action === action);
  return match ? match.enabled : true;
}

function getActionDisabledReason(action: string): string | undefined {
  const match = workOrder.value?.allowedActions?.find((a) => a.action === action);
  return match && !match.enabled ? match.reason : undefined;
}

/**
 * 加载工单详情以及全部关联单据
 */
async function loadAllData() {
  viewState.value = "loading";
  errorMessage.value = "";
  try {
    const targetId = props.id || "wo-001";
    const res = await getWorkOrderDetail(targetId);
    if (res.data) {
      workOrder.value = res.data;
    } else {
      const fallbackList = await getWorkOrders({ page: 1, size: 1 });
      workOrder.value = fallbackList.data?.records?.[0] || null;
    }

    if (workOrder.value) {
      const wid = workOrder.value.id as string;
      const [dspRes, exeRes, issRes, retRes, fgRes] = await Promise.all([
        getDispatchOrders({ workOrderId: wid }),
        getOperationExecutions({ workOrderId: wid }),
        getMaterialIssues({ workOrderId: wid }),
        getMaterialReturns({ workOrderId: wid }),
        getFinishedGoodsReceipts({ workOrderId: wid }),
      ]);
      dispatchOrders.value = dspRes.data?.records || [];
      executions.value = exeRes.data?.records || [];
      materialIssues.value = issRes.data?.records || [];
      materialReturns.value = retRes.data?.records || [];
      finishedReceipts.value = fgRes.data?.records || [];
    }

    viewState.value = workOrder.value ? "ready" : "empty";
  } catch (err: any) {
    errorMessage.value = err.message || "加载工单全景数据失败";
    viewState.value = "error";
  }
}

function promptAction(type: "submit" | "approve" | "complete", title: string, message: string) {
  confirmState.actionType = type;
  confirmState.title = title;
  confirmState.message = message;
  confirmState.visible = true;
}

async function executePromptAction() {
  if (!workOrder.value) return;
  confirmState.loading = true;
  try {
    const wid = workOrder.value.id as string;
    if (confirmState.actionType === "submit") {
      await submitWorkOrder(wid);
    } else if (confirmState.actionType === "approve") {
      await approveWorkOrder(wid);
    } else if (confirmState.actionType === "complete") {
      await completeWorkOrder(wid);
    }
    confirmState.visible = false;
    await loadAllData();
  } catch (err: any) {
    alert(`操作失败：${err.message}`);
  } finally {
    confirmState.loading = false;
  }
}

function openRejectModal() {
  rejectionReason.value = "";
  rejectModalVisible.value = true;
}

async function submitReject() {
  if (!workOrder.value || !rejectionReason.value.trim()) return;
  isSubmitting.value = true;
  try {
    await rejectWorkOrder(workOrder.value.id as string, rejectionReason.value.trim());
    rejectModalVisible.value = false;
    await loadAllData();
  } catch (err: any) {
    alert(`退回失败：${err.message}`);
  } finally {
    isSubmitting.value = false;
  }
}

function openManualCompleteModal() {
  manualCompleteReason.value = "";
  manualModalVisible.value = true;
}

async function submitManualComplete() {
  if (!workOrder.value || !manualCompleteReason.value.trim()) return;
  isSubmitting.value = true;
  try {
    await manualCompleteWorkOrder(workOrder.value.id as string, manualCompleteReason.value.trim());
    manualModalVisible.value = false;
    await loadAllData();
  } catch (err: any) {
    alert(`结案失败：${err.message}`);
  } finally {
    isSubmitting.value = false;
  }
}

watch(
  () => props.id,
  () => {
    loadAllData();
  }
);

onMounted(() => {
  loadAllData();
});
</script>

<style scoped>
.wo-detail-container {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.detail-header-nav {
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
  font-size: 14px;
}

.overview-banner-card {
  background: rgba(15, 23, 42, 0.8);
  border: 1px solid rgba(255, 255, 255, 0.1);
  border-radius: 10px;
  padding: 20px;
  display: flex;
  flex-direction: column;
  gap: 20px;
}

.banner-top-row {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
  flex-wrap: wrap;
}

.wo-title-group {
  display: flex;
  align-items: center;
  gap: 12px;
}

.wo-title {
  margin: 0;
  font-size: 20px;
  color: #38bdf8;
}

.product-line {
  margin: 6px 0 0;
  font-size: 15px;
  color: #f1f5f9;
}

.banner-actions {
  display: flex;
  align-items: center;
  gap: 10px;
}

.metrics-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(200px, 1fr));
  gap: 14px;
}

.metric-card {
  background: rgba(30, 41, 59, 0.5);
  border: 1px solid rgba(255, 255, 255, 0.08);
  border-radius: 8px;
  padding: 14px 16px;
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.metric-title {
  font-size: 12px;
  color: #94a3b8;
}

.metric-num {
  font-size: 18px;
  font-weight: 700;
  color: #f8fafc;
}

.highlight-blue { color: #60a5fa; }
.highlight-green { color: #34d399; }
.highlight-cyan { color: #38bdf8; }

.metric-sub {
  font-size: 11px;
}

.meta-strip {
  display: flex;
  align-items: center;
  gap: 24px;
  flex-wrap: wrap;
  padding-top: 14px;
  border-top: 1px dashed rgba(255, 255, 255, 0.1);
  font-size: 13px;
}

.meta-item .lbl {
  color: #94a3b8;
}

.meta-item .val {
  color: #e2e8f0;
}

.meta-item.full-width {
  width: 100%;
}

.tabs-container {
  background: rgba(15, 23, 42, 0.7);
  border: 1px solid rgba(255, 255, 255, 0.08);
  border-radius: 8px;
  overflow: hidden;
}

.tabs-header {
  display: flex;
  align-items: center;
  background: rgba(30, 41, 59, 0.6);
  border-bottom: 1px solid rgba(255, 255, 255, 0.08);
}

.tab-btn {
  background: transparent;
  border: none;
  color: #94a3b8;
  padding: 14px 20px;
  font-size: 13px;
  font-weight: 500;
  cursor: pointer;
  border-bottom: 2px solid transparent;
  transition: all 0.2s;
}

.tab-btn:hover {
  color: #f8fafc;
}

.tab-btn.is-active {
  color: #38bdf8;
  border-bottom-color: #38bdf8;
  background: rgba(56, 189, 248, 0.05);
}

.tab-pane {
  padding: 16px;
  overflow-x: auto;
}

.sub-table {
  width: 100%;
  border-collapse: collapse;
  font-size: 13px;
}

.sub-table th {
  background: rgba(30, 41, 59, 0.4);
  padding: 10px 12px;
  color: #94a3b8;
  text-align: left;
  border-bottom: 1px solid rgba(255, 255, 255, 0.08);
}

.sub-table td {
  padding: 12px;
  border-bottom: 1px solid rgba(255, 255, 255, 0.05);
  color: #cbd5e1;
}

.highlight-code {
  color: #38bdf8;
}

.movement-split {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 16px;
}

.split-card {
  background: rgba(30, 41, 59, 0.3);
  border: 1px solid rgba(255, 255, 255, 0.06);
  border-radius: 8px;
  padding: 12px;
}

.sub-title {
  margin: 0 0 10px;
  font-size: 14px;
  color: #e2e8f0;
}

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
  max-width: 480px;
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
  gap: 14px;
}

.modal-footer {
  display: flex;
  align-items: center;
  justify-content: flex-end;
  gap: 12px;
  padding: 14px 20px;
  border-top: 1px solid rgba(255, 255, 255, 0.08);
  background: rgba(0, 0, 0, 0.2);
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

.form-input {
  background: rgba(30, 41, 59, 0.8);
  border: 1px solid rgba(255, 255, 255, 0.12);
  color: #f8fafc;
  padding: 8px 12px;
  border-radius: 6px;
  font-size: 13px;
  outline: none;
}

.btn-close {
  background: none;
  border: none;
  color: #94a3b8;
  font-size: 16px;
  cursor: pointer;
}

.text-danger { color: #f87171 !important; }
.text-warning { color: #fbbf24 !important; }
.text-success { color: #34d399 !important; }
.text-primary { color: #38bdf8 !important; }
</style>
