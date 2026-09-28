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
          v-if="workOrder && hasPermission('trace:chain:view')"
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
              <!-- 修改用途：工单 DTO 只提供产品 ID，名称未补充时显示真实身份，避免空括号。 -->
              <strong>{{ workOrder.productName || workOrder.productId }}</strong>
              <span v-if="workOrder.productSpec || workOrder.productCode" class="spec font-mono text-muted">（{{ workOrder.productSpec || workOrder.productCode }}）</span>
            </p>
          </div>

          <!-- 顶部状态驱动操作按钮组 (受 allowedActions 约束) -->
          <div class="banner-actions" style="display: flex; gap: 8px; flex-wrap: wrap">
            <!-- 去排产派工 (Released 状态快捷跳转) -->
            <el-button
              v-if="workOrder.status === 'Released' && hasPermission('mes:dispatch:manage')"
              type="primary"
              title="前往生产派工页面创建工序派工"
              @click="router.push(`/mes/dispatch?workOrderId=${workOrder.id}`)"
            >
              去排产派工
            </el-button>

            <!-- 办理成品入库 (已开工或已下达状态) -->
            <el-button
              v-if="(workOrder.status === 'InProgress' || workOrder.status === 'Released') && hasAnyPermission('mes:finished:receipt', 'mes:finished:confirm')"
              type="primary"
              plain
              title="前往产成品入库页面办理入库"
              @click="router.push(`/mes/receipts?workOrderId=${workOrder.id}`)"
            >
              办理成品入库
            </el-button>

            <!-- 提交审核 -->
            <el-button
              v-if="(workOrder.status === 'Draft' || workOrder.status === 'Rejected') && hasPermission('mes:workorder:submit')"
              type="primary"
              :disabled="!isActionAllowed('submit')"
              :title="getActionDisabledReason('submit') || '提交审核'"
              @click="promptAction('submit', '提交审核确认', '确认将工单提交至质检/计划主管审核？')"
            >
              提交审核
            </el-button>

            <!-- 审批通过与驳回 -->
            <template v-if="workOrder.status === 'PendingApproval' && hasPermission('mes:workorder:approve')">
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
              v-if="workOrder.status === 'InProgress' && hasPermission('mes:workorder:complete')"
              type="success"
              :disabled="!isActionAllowed('complete')"
              :title="getActionDisabledReason('complete') || '正常完成工单'"
              @click="promptAction('complete', '正常完工确认', '确认全部工序报工合格并归档完成？')"
            >
              工单完工
            </el-button>

            <!-- 手工强制结案 -->
            <el-button
              v-if="(workOrder.status === 'Released' || workOrder.status === 'InProgress') && hasPermission('mes:workorder:complete')"
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
                  {{ getOperationLabel(row) }}
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
                  <span class="font-mono">{{ getDeviceLabel(row) }}</span>
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
              <el-table-column label="执行工序" min-width="160">
                <template #default="{ row }">{{ getOperationLabel(row) }}</template>
              </el-table-column>
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
          <!-- 修改用途：没有领退料查询权限时隐藏事实页签，不能把未查询显示为零记录。 -->
          <el-tab-pane v-if="canReadMaterials" :label="`生产领料 / 退料 (${materialIssues.length + materialReturns.length})`" name="movement">
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
                  <!-- 修改用途：直接显示查询 DTO 的各明细数量，不把单据项数当作领料数量。 -->
                  <el-table-column label="领料数量" min-width="120" align="right">
                    <template #default="{ row }">
                      <div v-for="item in row.items" :key="item.id || item.productId">
                        <QuantityText :value="item.issueQty" :unit="item.uom || ''" />
                      </div>
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
                  <!-- 修改用途：退料查询使用 returnQty，保持服务端原始数量，不自行推算。 -->
                  <el-table-column label="退料数量" min-width="120" align="right">
                    <template #default="{ row }">
                      <div v-for="item in row.items" :key="item.id || item.productId">
                        <QuantityText :value="item.returnQty" :unit="item.uom || ''" />
                      </div>
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
import { ref, reactive, computed, onMounted, watch } from "vue";
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
import { usePermission } from "../../composables/usePermission";
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
  getMaterialIssues,
  getMaterialReturns,
  getRoutingById,
} from "../../api/manufacturing";
import { getProductById } from "../../api/masterData";

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
const { hasPermission, hasAnyPermission } = usePermission();
// 修改用途：与 ProductionFactController 的两个 GET 权限保持一致，独立于工单查看权限。
const canReadMaterials = computed(() => hasAnyPermission("mes:material:requisition", "mes:material:confirm"));

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
// 修改用途：详情及名称补读共享请求世代，切换工单或重复刷新时旧响应不能覆盖当前事实。
let loadGeneration = 0;

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
 * 用途：读取工单及关联事实，并按已授权的主数据详情补充名称。
 * 入参：当前 props.id；出参：更新页面状态，无返回数据；流程：读取扁平工单 DTO，
 * 并行取得派工、执行、领退料与入库，仅当前请求世代可以提交页面状态；名称缺失时保留真实 ID。
 */
async function loadAllData() {
  const generation = ++loadGeneration;
  const targetId = props.id;
  const isCurrent = () => generation === loadGeneration && props.id === targetId;
  viewState.value = "loading";
  errorMessage.value = "";
  try {
    if (!targetId) {
      workOrder.value = null;
      viewState.value = "empty";
      return;
    }
    const res = await getWorkOrderDetail(targetId);
    if (!isCurrent()) return;
    const order = res.data;

    if (order) {
      const wid = order.id as string;
      const [dspRes, exeRes, fgRes, issueRes, returnRes, productRes, routingRes] = await Promise.all([
        getDispatchOrders({ workOrderId: wid }),
        getOperationExecutions({ workOrderId: wid }),
        getFinishedGoodsReceipts(wid),
        // 修改用途：没有关联事实权限时不请求接口，避免 403 使合法工单查询整体失败；对应页签同时隐藏。
        canReadMaterials.value ? getMaterialIssues(wid) : Promise.resolve({ data: [] }),
        canReadMaterials.value ? getMaterialReturns(wid) : Promise.resolve({ data: [] }),
        // 修改用途：仅补读拥有查看权限的现有详情端点，目录不可用不伪造名称或阻断工单事实。
        hasPermission("inv:product:view") ? getProductById(order.productId).catch(() => null) : Promise.resolve(null),
        hasPermission("mes:routing:view") ? getRoutingById(order.routingId).catch(() => null) : Promise.resolve(null),
      ]);
      if (!isCurrent()) return;
      const product = productRes?.data;
      const routing = routingRes?.data;
      const operations = new Map((routing?.operations || []).map(operation => [operation.id, operation] as const));
      // 修改用途：只按服务端工序 ID 关联路线明细，保留服务端已有名称和工序号，找不到时由模板显示 ID。
      const withOperation = <T extends DispatchOrderItem | OperationExecutionItem>(row: T): T => {
        const operation = operations.get(row.operationId);
        return { ...row, operationName: row.operationName || operation?.operationName,
          operationNo: row.operationNo ?? operation?.operationNo };
      };
      workOrder.value = { ...order, productName: product?.name || order.productName,
        // 修改用途：Product 正式编码字段为 sku，不推断不存在的 code。
        productSpec: product?.spec || order.productSpec, productCode: product?.sku || order.productCode,
        routingCode: routing?.routingCode || order.routingCode };
      dispatchOrders.value = (dspRes.data?.records || []).map(withOperation);
      executions.value = (exeRes.data?.records || []).map(withOperation);
      // 修改用途：复用已实现的指定工单领退料数组接口，查询失败进入错误态，不能伪装为零单据。
      materialIssues.value = issueRes.data || [];
      materialReturns.value = returnRes.data || [];
      finishedReceipts.value = fgRes.data || [];
    } else {
      workOrder.value = null;
      dispatchOrders.value = [];
      executions.value = [];
      materialIssues.value = [];
      materialReturns.value = [];
      finishedReceipts.value = [];
    }

    viewState.value = workOrder.value ? "ready" : "empty";
  } catch (err: any) {
    if (!isCurrent()) return;
    errorMessage.value = err.message || "加载工单全景数据失败";
    viewState.value = "error";
  }
}

/** 用途：显示工序名称或真实 ID；入参为派工/执行行，返回标签；只在后端提供工序号时追加编号。 */
function getOperationLabel(row: DispatchOrderItem | OperationExecutionItem): string {
  const name = row.operationName || row.operationId;
  return row.operationNo == null ? name : `${name} (#${row.operationNo})`;
}

/** 用途：显示真实设备绑定；入参为派工行，返回名称、编码或 ID；仅未提供设备 ID 时显示未绑定。 */
function getDeviceLabel(row: DispatchOrderItem): string {
  return row.deviceName || row.deviceCode || row.deviceId || "未绑定设备";
}

function promptAction(type: "submit" | "approve" | "complete", title: string, message: string) {
  confirmState.actionType = type;
  confirmState.title = title;
  confirmState.message = message;
  confirmState.visible = true;
}

async function executePromptAction() {
  if (!workOrder.value) return;
  // 修改用途：只执行已有三种确认动作，未知动作不能默认触发工单完工。
  if (!["submit", "approve", "complete"].includes(confirmState.actionType)) return;
  confirmState.loading = true;
  try {
    const wid = workOrder.value.id as string;
    const actionType = confirmState.actionType;
    const operation = (key: string) => actionType === "submit" ? submitWorkOrder(wid, key)
      : actionType === "approve" ? approveWorkOrder(wid, key) : completeWorkOrder(wid, undefined, key);
    await execute(async (key) => {
      await operation(key);
      // 修改用途：retry 成功恢复原工单，不关闭后来切换的工单确认弹窗。
      if (workOrder.value?.id === wid && confirmState.actionType === actionType) confirmState.visible = false;
      if (props.id === wid && workOrder.value?.id === wid) await loadAllData();
    }, { onConflict: loadAllData });
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
  // 修改用途：固定首次退回工单及原因，同键重试不读取后来选择的工单或表单。
  const workOrderId = workOrder.value.id as string;
  const reason = rejectionReason.value.trim();
  isSubmitting.value = true;
  try {
    await execute(async (key) => {
      await rejectWorkOrder(workOrderId, reason, key);
      // 修改用途：retry 成功只关闭原退回草稿，不覆盖另一个工单或新原因。
      if (workOrder.value?.id === workOrderId && rejectionReason.value.trim() === reason) rejectModalVisible.value = false;
      if (props.id === workOrderId && workOrder.value?.id === workOrderId) await loadAllData();
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
  // 修改用途：固定首次结案的工单 ID 和原因，保持同键重试语义一致。
  const workOrderId = workOrder.value.id as string;
  const reason = manualCompleteReason.value.trim();
  try {
    await execute(async (key) => {
      await manualCompleteWorkOrder(workOrderId, reason, key);
      // 修改用途：retry 成功恢复原结案；用户已经换工单或编辑新原因时保留当前表单。
      if (workOrder.value?.id === workOrderId && manualCompleteReason.value.trim() === reason) manualModalVisible.value = false;
      if (props.id === workOrderId && workOrder.value?.id === workOrderId) await loadAllData();
    }, { onConflict: loadAllData });
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
