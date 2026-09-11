<template>
  <div class="wo-detail-container">
    <CommandFeedback :error="lastError" :can-retry="canRetry" :executing="isExecuting" @retry="retry" />
    <!-- 头部面包屑与状态导航 -->
    <div class="detail-header-nav" style="display: flex; align-items: center; justify-content: space-between; margin-bottom: 16px">
      <el-button :icon="ArrowLeft" link @click="handleBack">
        返回工单列表
      </el-button>
      <div class="header-tags" style="display: flex; align-items: center; gap: 12px">
        <span class="font-mono text-muted">工单 ID: {{ workOrder?.id || id }}</span>
        <el-button
          v-if="workOrder"
          type="primary"
          link
          :icon="Search"
          title="穿透前往全链路全闭环追溯中心"
          @click="handleGoTrace"
        >
          全链路追溯
        </el-button>
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
          <div class="banner-actions" style="display: flex; gap: 8px; flex-wrap: wrap">
            <!-- 去排产派工 (Released 状态快捷跳转) -->
            <el-button
              v-if="workOrder.status === 'Released'"
              type="primary"
              title="前往生产派工页面创建工序派工"
              @click="router.push(`/mes/dispatch?workOrderId=${workOrder.id}`)"
            >
              去排产派工
            </el-button>

            <!-- 办理成品入库 (已开工或已下达状态) -->
            <el-button
              v-if="workOrder.status === 'InProgress' || workOrder.status === 'Released'"
              type="primary"
              plain
              title="前往产成品入库页面办理入库"
              @click="router.push(`/mes/receipts?workOrderId=${workOrder.id}`)"
            >
              办理成品入库
            </el-button>

            <!-- 提交审核 -->
            <el-button
              v-if="workOrder.status === 'Draft' || workOrder.status === 'Rejected'"
              type="primary"
              :disabled="!isActionAllowed('submit')"
              :title="getActionDisabledReason('submit') || '提交审核'"
              @click="promptAction('submit', '提交审核确认', '确认将工单提交至质检/计划主管审核？')"
            >
              提交审核
            </el-button>

            <!-- 审批通过与驳回 -->
            <template v-if="workOrder.status === 'PendingApproval'">
              <el-button
                type="success"
                :disabled="!isActionAllowed('approve')"
                :title="getActionDisabledReason('approve') || '审核通过'"
                @click="promptAction('approve', '审核通过确认', '确认审核通过此工单并正式下达排产？有效 BOM 与路线版本将被锁定。')"
              >
                审核通过
              </el-button>
              <el-button
                type="warning"
                :disabled="!isActionAllowed('reject')"
                :title="getActionDisabledReason('reject') || '驳回审核'"
                @click="openRejectModal"
              >
                驳回
              </el-button>
            </template>

            <!-- 正常完工 -->
            <el-button
              v-if="workOrder.status === 'InProgress'"
              type="success"
              :disabled="!isActionAllowed('complete')"
              :title="getActionDisabledReason('complete') || '正常完成工单'"
              @click="promptAction('complete', '正常完工确认', '确认全部工序报工合格并归档完成？')"
            >
              工单完工
            </el-button>

            <!-- 手工强制结案 -->
            <el-button
              v-if="workOrder.status === 'Released' || workOrder.status === 'InProgress'"
              type="info"
              plain
              :disabled="!isActionAllowed('manualComplete')"
              :title="getActionDisabledReason('manualComplete') || '手工强制结案'"
              @click="openManualCompleteModal"
            >
              手工结案
            </el-button>
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
        <el-tabs v-model="activeTab" class="wo-tabs">
          <!-- Tab 1: 派工单 -->
          <el-tab-pane :label="`派工安排 (${dispatchOrders.length})`" name="dispatch">
            <el-table :data="dispatchOrders" border style="width: 100%">
              <el-table-column prop="dispatchNo" label="派工单号" width="180">
                <template #default="{ row }">
                  <span class="font-mono highlight-code">{{ row.dispatchNo }}</span>
                </template>
              </el-table-column>
              <el-table-column label="工序步骤" min-width="160">
                <template #default="{ row }">
                  {{ row.operationName }} (#{{ row.operationNo }})
                </template>
              </el-table-column>
              <el-table-column label="派工数量" width="120" align="right">
                <template #default="{ row }">
                  <QuantityText :value="row.dispatchQty" unit="件" />
                </template>
              </el-table-column>
              <el-table-column label="责任操作员" width="140">
                <template #default="{ row }">
                  {{ row.operatorName || row.operatorId }}
                </template>
              </el-table-column>
              <el-table-column label="绑定设备" min-width="160">
                <template #default="{ row }">
                  <span class="font-mono">{{ row.deviceName || row.deviceCode || "通用人工工位" }}</span>
                </template>
              </el-table-column>
              <el-table-column label="派工状态" width="120" align="center">
                <template #default="{ row }">
                  <StatusBadge
                    :type="row.status === 'Completed' ? 'success' : row.status === 'Processing' ? 'primary' : 'info'"
                    :text="row.status"
                  />
                </template>
              </el-table-column>
            </el-table>
          </el-tab-pane>

          <!-- Tab 2: 现场执行与报工 -->
          <el-tab-pane :label="`工序现场执行 (${executions.length})`" name="execution">
            <el-table :data="executions" border style="width: 100%">
              <el-table-column prop="executionNo" label="执行编号" width="180">
                <template #default="{ row }">
                  <span class="font-mono highlight-code">{{ row.executionNo }}</span>
                </template>
              </el-table-column>
              <el-table-column prop="operationName" label="执行工序" min-width="160" />
              <el-table-column label="执行人" width="130">
                <template #default="{ row }">
                  {{ row.operatorName || row.operatorId }}
                </template>
              </el-table-column>
              <el-table-column label="执行状态" width="120" align="center">
                <template #default="{ row }">
                  <StatusBadge
                    :type="row.status === 'Running' ? 'primary' : row.status === 'Completed' ? 'success' : 'default'"
                    :text="row.status"
                    :pulsing="row.status === 'Running'"
                  />
                </template>
              </el-table-column>
              <el-table-column prop="startedAt" label="实际开始时间" width="160">
                <template #default="{ row }">
                  <span class="font-mono text-muted">{{ row.startedAt || "-" }}</span>
                </template>
              </el-table-column>
              <el-table-column prop="completedAt" label="实际完工时间" width="160">
                <template #default="{ row }">
                  <span class="font-mono text-muted">{{ row.completedAt || "-" }}</span>
                </template>
              </el-table-column>
              <el-table-column label="累计报工数" width="130" align="right">
                <template #default="{ row }">
                  <QuantityText :value="row.reportedQty || '0.00'" unit="件" />
                </template>
              </el-table-column>
            </el-table>
          </el-tab-pane>

          <!-- Tab 3: 生产质检 -->
          <el-tab-pane label="生产质检" name="quality">
            <ProductionQualityPanel :work-order-id="workOrder.id" @refresh="loadAllData" />
          </el-tab-pane>

          <!-- Tab 4: 领退料 -->
          <el-tab-pane :label="`生产领料 / 退料 (${materialIssues.length + materialReturns.length})`" name="movement">
            <div class="movement-split" style="display: flex; gap: 16px; flex-wrap: wrap">
              <div class="split-card" style="flex: 1; min-width: 320px">
                <h4 class="sub-title" style="margin-bottom: 10px; color: #f1f5f9">领料单 (Material Issues)</h4>
                <el-table :data="materialIssues" border style="width: 100%" empty-text="暂无领料单">
                  <el-table-column prop="issueNo" label="单号" min-width="140">
                    <template #default="{ row }">
                      <span class="font-mono highlight-code">{{ row.issueNo }}</span>
                    </template>
                  </el-table-column>
                  <el-table-column label="状态" width="100" align="center">
                    <template #default="{ row }">
                      <StatusBadge :type="row.status === 'Confirmed' ? 'success' : 'warning'" :text="row.status" />
                    </template>
                  </el-table-column>
                  <el-table-column label="物料项数" width="110" align="right">
                    <template #default="{ row }">
                      {{ row.items?.length || 0 }} 项原料
                    </template>
                  </el-table-column>
                  <el-table-column prop="confirmedAt" label="出库确认时间" width="150">
                    <template #default="{ row }">
                      <span class="font-mono text-muted">{{ row.confirmedAt || "待出库" }}</span>
                    </template>
                  </el-table-column>
                </el-table>
              </div>

              <div class="split-card" style="flex: 1; min-width: 320px">
                <h4 class="sub-title" style="margin-bottom: 10px; color: #f1f5f9">退料单 (Material Returns)</h4>
                <el-table :data="materialReturns" border style="width: 100%" empty-text="暂无退料单">
                  <el-table-column prop="returnNo" label="单号" min-width="140">
                    <template #default="{ row }">
                      <span class="font-mono highlight-code">{{ row.returnNo }}</span>
                    </template>
                  </el-table-column>
                  <el-table-column label="状态" width="100" align="center">
                    <template #default="{ row }">
                      <StatusBadge :type="row.status === 'Confirmed' ? 'success' : 'warning'" :text="row.status" />
                    </template>
                  </el-table-column>
                  <el-table-column label="退料项数" width="110" align="right">
                    <template #default="{ row }">
                      {{ row.items?.length || 0 }} 项原料
                    </template>
                  </el-table-column>
                  <el-table-column prop="confirmedAt" label="退库确认时间" width="150">
                    <template #default="{ row }">
                      <span class="font-mono text-muted">{{ row.confirmedAt || "待入库" }}</span>
                    </template>
                  </el-table-column>
                </el-table>
              </div>
            </div>
          </el-tab-pane>

          <!-- Tab 5: 成品入库 -->
          <el-tab-pane :label="`成品入库 (${finishedReceipts.length})`" name="receipt">
            <el-table :data="finishedReceipts" border style="width: 100%" empty-text="暂无成品入库单记录">
              <el-table-column prop="receiptNo" label="入库单号" width="180">
                <template #default="{ row }">
                  <span class="font-mono highlight-code">{{ row.receiptNo }}</span>
                </template>
              </el-table-column>
              <el-table-column label="入库数量" width="120" align="right">
                <template #default="{ row }">
                  <QuantityText :value="row.receiptQty" unit="件" />
                </template>
              </el-table-column>
              <el-table-column label="目标仓库 / 库位" min-width="180">
                <template #default="{ row }">
                  {{ row.warehouseName || row.warehouseId }} / {{ row.locationCode || row.locationId }}
                </template>
              </el-table-column>
              <el-table-column label="状态" width="100" align="center">
                <template #default="{ row }">
                  <StatusBadge :type="row.status === 'Confirmed' ? 'success' : 'warning'" :text="row.status" />
                </template>
              </el-table-column>
              <el-table-column prop="inventoryTransactionId" label="库存流水关联" min-width="160">
                <template #default="{ row }">
                  <span class="font-mono text-muted">{{ row.inventoryTransactionId || "-" }}</span>
                </template>
              </el-table-column>
              <el-table-column prop="confirmedAt" label="确认时间" width="160">
                <template #default="{ row }">
                  <span class="font-mono text-muted">{{ row.confirmedAt || "待确认" }}</span>
                </template>
              </el-table-column>
            </el-table>
          </el-tab-pane>
        </el-tabs>
      </section>
    </div>

    <!-- 审核驳回弹窗 -->
    <el-dialog
      v-model="rejectModalVisible"
      title="退回驳回工单审核"
      width="520px"
      destroy-on-close
      append-to-body
    >
      <el-form label-width="110px" @submit.prevent="submitReject">
        <el-form-item label="驳回退回原因" required>
          <el-input
            v-model="rejectionReason"
            type="textarea"
            :rows="3"
            placeholder="请输入退回审批的具体原因..."
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="rejectModalVisible = false">取消</el-button>
        <el-button type="warning" :loading="isSubmitting" @click="submitReject">
          {{ isSubmitting ? "处理中..." : "确认退回" }}
        </el-button>
      </template>
    </el-dialog>

    <!-- 手工强制结案弹窗 -->
    <el-dialog
      v-model="manualModalVisible"
      title="工单提前结案确认"
      width="520px"
      destroy-on-close
      append-to-body
    >
      <el-form label-width="120px" @submit.prevent="submitManualComplete">
        <el-form-item label="强制结案原因" required>
          <el-input
            v-model="manualCompleteReason"
            type="textarea"
            :rows="3"
            placeholder="请填写手工结案原因..."
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="manualModalVisible = false">取消</el-button>
        <el-button type="danger" :loading="isSubmitting" @click="submitManualComplete">
          {{ isSubmitting ? "结案中..." : "确认强制完工" }}
        </el-button>
      </template>
    </el-dialog>

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
import { ArrowLeft, Search } from "@element-plus/icons-vue";
import { ElMessage } from "element-plus";
import { useCommand } from "@/composables/useCommand";
import CommandFeedback from "@/components/common/CommandFeedback.vue";
import { useRoute, useRouter } from "vue-router";
import ProductionQualityPanel from "./components/ProductionQualityPanel.vue";
import { isActionAllowed as checkAction, getActionDisabledReason as getDisabledReason } from "../../utils/actionGuard";
import type { AllowedAction } from "../../types/common";
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
  getFinishedGoodsReceipts,
} from "../../api/manufacturing";

const props = withDefaults(
  defineProps<{
    id?: string;
  }>(),
  {
    id: "",
  }
);

const emit = defineEmits<{
  (e: "back"): void;
}>();

const route = useRoute();
const router = useRouter();

function handleBack() {
  emit("back");
  router.push("/mes/work-orders");
}

function handleGoTrace() {
  if (!workOrder.value) return;
  router.push({
    path: "/traceability",
    query: {
      entry_type: "WORK_ORDER",
      entity_id: String(workOrder.value.id),
    },
  });
}

const viewState = ref<ViewState>("loading");
const errorMessage = ref("");
const workOrder = ref<WorkOrderItem | null>(null);

const activeTab = ref<"dispatch" | "execution" | "quality" | "movement" | "receipt">("dispatch");

const dispatchOrders = ref<DispatchOrderItem[]>([]);
const executions = ref<OperationExecutionItem[]>([]);
const materialIssues = ref<any[]>([]);
const materialReturns = ref<any[]>([]);
const finishedReceipts = ref<FinishedGoodsReceiptItem[]>([]);

const rejectModalVisible = ref(false);
const rejectionReason = ref("");
const manualModalVisible = ref(false);
const manualCompleteReason = ref("");
const { execute, retry, isExecuting, canRetry, lastError } = useCommand();
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

// 使用 actionGuard 统一权限判断逻辑
function isActionAllowed(action: string): boolean {
  return checkAction(workOrder.value?.allowedActions, action);
}

// 使用 actionGuard 统一获取禁用原因逻辑
function getActionDisabledReason(action: string): string | undefined {
  return getDisabledReason(workOrder.value?.allowedActions, action);
}

/**
 * 加载工单详情以及全部关联单据
 */
async function loadAllData() {
  viewState.value = "loading";
  errorMessage.value = "";
  try {
    const targetId = props.id;
    if (!targetId) {
      viewState.value = "empty";
      return;
    }
    const res = await getWorkOrderDetail(targetId);
    workOrder.value = res.data || null;

    if (workOrder.value) {
      const wid = workOrder.value.id as string;
      const [dspRes, exeRes, fgRes] = await Promise.all([
        getDispatchOrders({ workOrderId: wid }),
        getOperationExecutions({ workOrderId: wid }),
        getFinishedGoodsReceipts(wid),
      ]);
      dispatchOrders.value = dspRes.data?.records || [];
      executions.value = exeRes.data?.records || [];
      // 后端未提供领退料列表读取接口，页面不伪造历史事实。
      materialIssues.value = [];
      materialReturns.value = [];
      finishedReceipts.value = fgRes.data || [];
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
      await execute((key) => submitWorkOrder(wid, key), { onConflict: loadAllData });
    } else if (confirmState.actionType === "approve") {
      await execute((key) => approveWorkOrder(wid, key), { onConflict: loadAllData });
    } else if (confirmState.actionType === "complete") {
      await execute((key) => completeWorkOrder(wid, undefined, key), { onConflict: loadAllData });
    }
    confirmState.visible = false;
    await loadAllData();
  } catch (err: any) {
    ElMessage.error(`操作失败：${err.message}`);
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
  const currentWorkOrder = workOrder.value;
  isSubmitting.value = true;
  try {
    await execute(async (key) => {
      await rejectWorkOrder(currentWorkOrder.id as string, rejectionReason.value.trim(), key);
      rejectModalVisible.value = false;
      await loadAllData();
    }, { onConflict: loadAllData });
  } catch (err: any) {
    ElMessage.error(`退回失败：${err.message}`);
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
  try {
    await execute((key) => manualCompleteWorkOrder(workOrder.value!.id as string, manualCompleteReason.value.trim(), key), { onConflict: loadAllData });
    manualModalVisible.value = false;
    await loadAllData();
  } catch (err: any) {
    ElMessage.error(`结案失败：${err.message}`);
  } finally { /* useCommand 在 finally 中恢复 isExecuting。 */ }
}

watch(
  () => props.id,
  () => {
    loadAllData();
  }
);

onMounted(() => {
  loadAllData();
  if (route.query.openQuality === "true" || route.query.tab === "quality") {
    activeTab.value = "quality";
  }
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

.header-tags {
  display: flex;
  align-items: center;
  gap: 12px;
}

.btn-nav-trace {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  background: rgba(56, 189, 248, 0.12);
  border: 1px solid rgba(56, 189, 248, 0.35);
  color: #38bdf8;
  padding: 6px 14px;
  border-radius: 6px;
  cursor: pointer;
  font-size: 13px;
  font-weight: 500;
  transition: all 0.2s;
}

.btn-nav-trace:hover {
  background: rgba(56, 189, 248, 0.22);
  border-color: #38bdf8;
  box-shadow: 0 0 10px rgba(56, 189, 248, 0.2);
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
