<template>
  <div class="manufacturing-view-container">
    <!-- 统一页面头部 -->
    <PageHeader
      title="生产工单 (Work Order) 管理"
      tag="MES / WORK ORDER"
      description="生产工单承接销售与制造意图，锁定 BOM 与 Routing 版本，记录下达派工、领退料、报工质检与成品入库全流程。"
    >
      <template #actions>
        <button type="button" class="btn btn-primary" @click="openCreateModal">
          <span class="btn-icon">＋</span>
          <span>新建生产工单</span>
        </button>
      </template>
    </PageHeader>

    <!-- 统一筛选栏 -->
    <FilterBar
      v-model="queryParams.keyword"
      placeholder="搜索工单号或产品名称/编码..."
      @search="handleSearch"
      @reset="handleReset"
    >
      <div class="filter-select-group">
        <label class="filter-label">状态：</label>
        <select v-model="queryParams.status" class="filter-select" @change="handleSearch">
          <option value="">全部状态</option>
          <option value="Draft">草稿 (Draft)</option>
          <option value="PendingApproval">待审核 (PendingApproval)</option>
          <option value="Released">已下达 (Released)</option>
          <option value="InProgress">生产中 (InProgress)</option>
          <option value="Completed">已完成 (Completed)</option>
          <option value="Rejected">已驳回 (Rejected)</option>
        </select>
      </div>
    </FilterBar>

    <!-- 错误异常提示 -->
    <ErrorState
      v-if="viewState === 'error'"
      title="工单数据加载失败"
      :message="errorMessage"
      @retry="fetchWorkOrders"
    />

    <!-- 数据表格 -->
    <DataTable
      v-else
      :columns="columns"
      :data="workOrderList"
      :loading="viewState === 'loading'"
      :page="queryParams.page"
      :size="queryParams.size"
      :total="total"
      empty-text="暂无匹配的生产工单记录"
      @page-change="handlePageChange"
    >
      <!-- 工单号 -->
      <template #workOrderNo="{ row }">
        <div class="wo-no-cell">
          <span class="font-mono highlight-code">{{ row.workOrderNo }}</span>
          <span v-if="row.sourceSalesOrderNo" class="source-tag font-mono">
            来源: {{ row.sourceSalesOrderNo }}
          </span>
        </div>
      </template>

      <!-- 产品 -->
      <template #productId="{ row }">
        <div class="product-cell">
          <span class="product-name">{{ row.productName || "未知产品" }}</span>
          <span class="product-spec text-muted">{{ row.productSpec || row.productCode }}</span>
        </div>
      </template>

      <!-- 计划数量 -->
      <template #plannedQty="{ row }">
        <QuantityText :value="row.plannedQty" unit="件" />
      </template>

      <!-- 状态 -->
      <template #status="{ row }">
        <StatusBadge
          :type="getStatusBadgeType(row.status)"
          :text="getStatusText(row.status)"
          :pulsing="row.status === 'InProgress'"
        />
      </template>

      <!-- 生产与入库进度 -->
      <template #progress="{ row }">
        <div class="progress-cell">
          <div class="progress-row">
            <span class="progress-label">报工:</span>
            <QuantityText :value="row.reportedQty" unit="件" />
            <span class="progress-sep">/</span>
            <span class="progress-label">入库:</span>
            <QuantityText :value="row.receivedQty" unit="件" />
          </div>
          <div v-if="row.defectQty && row.defectQty !== '0.00' && row.defectQty !== '0'" class="defect-row">
            <span class="defect-label">不良:</span>
            <QuantityText :value="row.defectQty" unit="件" class="text-danger" />
          </div>
        </div>
      </template>

      <!-- 计划时间 -->
      <template #plannedFinishTime="{ row }">
        <div class="time-cell font-mono text-muted">
          <span>{{ row.plannedFinishTime }}</span>
        </div>
      </template>

      <!-- 操作入口列 (受 allowedActions 控制) -->
      <template #actions="{ row }">
        <div class="action-btn-group">
          <!-- 详情 -->
          <button type="button" class="btn-text" @click="viewDetail(row)">
            详情
          </button>

          <!-- 提交审核 (Draft / Rejected) -->
          <button
            v-if="row.status === 'Draft' || row.status === 'Rejected'"
            type="button"
            class="btn-text text-primary"
            :disabled="!isActionAllowed(row, 'submit')"
            :title="getActionDisabledReason(row, 'submit') || '提交审批'"
            @click="promptSubmit(row)"
          >
            提交
          </button>

          <!-- 审核批准与驳回 (PendingApproval) -->
          <template v-if="row.status === 'PendingApproval'">
            <button
              type="button"
              class="btn-text text-success"
              :disabled="!isActionAllowed(row, 'approve')"
              :title="getActionDisabledReason(row, 'approve') || '审核通过并下达'"
              @click="promptApprove(row)"
            >
              批准
            </button>
            <button
              type="button"
              class="btn-text text-warning"
              :disabled="!isActionAllowed(row, 'reject')"
              :title="getActionDisabledReason(row, 'reject') || '驳回工单'"
              @click="openRejectModal(row)"
            >
              驳回
            </button>
          </template>

          <!-- 完工 (InProgress) -->
          <button
            v-if="row.status === 'InProgress'"
            type="button"
            class="btn-text text-success"
            :disabled="!isActionAllowed(row, 'complete')"
            :title="getActionDisabledReason(row, 'complete') || '正常完工'"
            @click="promptComplete(row)"
          >
            完工
          </button>

          <!-- 强制结案 (Released / InProgress) -->
          <button
            v-if="row.status === 'Released' || row.status === 'InProgress'"
            type="button"
            class="btn-text text-muted"
            :disabled="!isActionAllowed(row, 'manual-complete')"
            :title="getActionDisabledReason(row, 'manual-complete') || '强制人工结案'"
            @click="openManualCompleteModal(row)"
          >
            结案
          </button>
        </div>
      </template>
    </DataTable>

    <!-- 新建工单对话框 -->
    <div v-if="createModalVisible" class="modal-mask" @click.self="createModalVisible = false">
      <div class="modal-card modal-large">
        <div class="modal-header">
          <h3 class="modal-title">新建生产工单 (Work Order)</h3>
          <button type="button" class="btn-close" @click="createModalVisible = false">✕</button>
        </div>

        <form class="modal-body" @submit.prevent="submitCreateWorkOrder">
          <div class="form-grid two-col">
            <div class="form-item">
              <label>产出产品代码 <span class="req">*</span></label>
              <input
                v-model="createForm.productId"
                type="text"
                class="form-input"
                placeholder="例如 prod-101"
                required
              />
            </div>
            <div class="form-item">
              <label>计划生产数量 <span class="req">*</span></label>
              <input
                v-model="createForm.plannedQty"
                type="text"
                class="form-input font-mono"
                placeholder="例如 100.00"
                required
              />
            </div>
          </div>

          <div class="form-grid two-col">
            <div class="form-item">
              <label>计划开工时间 <span class="req">*</span></label>
              <input
                v-model="createForm.plannedStartTime"
                type="text"
                class="form-input font-mono"
                placeholder="2026-09-05 08:00:00"
                required
              />
            </div>
            <div class="form-item">
              <label>计划完工时间 <span class="req">*</span></label>
              <input
                v-model="createForm.plannedFinishTime"
                type="text"
                class="form-input font-mono"
                placeholder="2026-09-10 18:00:00"
                required
              />
            </div>
          </div>

          <div class="form-grid two-col">
            <div class="form-item">
              <label>关联 BOM 清单 ID <span class="req">*</span></label>
              <input
                v-model="createForm.bomId"
                type="text"
                class="form-input font-mono"
                placeholder="例如 bom-001"
                required
              />
            </div>
            <div class="form-item">
              <label>关联工艺路线 ID <span class="req">*</span></label>
              <input
                v-model="createForm.routingId"
                type="text"
                class="form-input font-mono"
                placeholder="例如 rout-001"
                required
              />
            </div>
          </div>

          <div class="form-item">
            <label>来源销售订单行 ID (可选，仅用于追溯)</label>
            <input
              v-model="createForm.sourceSalesOrderLineId"
              type="text"
              class="form-input font-mono"
              placeholder="例如 so-line-8891"
            />
          </div>

          <div class="modal-footer">
            <button type="button" class="btn btn-secondary" @click="createModalVisible = false">取消</button>
            <button type="submit" class="btn btn-primary" :disabled="isSubmitting">
              {{ isSubmitting ? "创建中..." : "确认创建工单 (Draft)" }}
            </button>
          </div>
        </form>
      </div>
    </div>

    <!-- 审核驳回原因弹窗 -->
    <div v-if="rejectModalVisible" class="modal-mask" @click.self="rejectModalVisible = false">
      <div class="modal-card">
        <div class="modal-header">
          <h3 class="modal-title">驳回工单审核确认</h3>
          <button type="button" class="btn-close" @click="rejectModalVisible = false">✕</button>
        </div>
        <form class="modal-body" @submit.prevent="handleConfirmReject">
          <p class="modal-hint">
            正在审核驳回工单 <strong>{{ activeWo?.workOrderNo }}</strong>。必须填写驳回原因：
          </p>
          <div class="form-item">
            <label>驳回原因说明 <span class="req">*</span></label>
            <textarea
              v-model="rejectionReason"
              class="form-input form-textarea"
              rows="3"
              placeholder="请详述退回原因，如物料缺料、工时计划冲突等..."
              required
            ></textarea>
          </div>
          <div class="modal-footer">
            <button type="button" class="btn btn-secondary" @click="rejectModalVisible = false">取消</button>
            <button type="submit" class="btn btn-warning" :disabled="isSubmitting">
              {{ isSubmitting ? "处理中..." : "确认退回驳回" }}
            </button>
          </div>
        </form>
      </div>
    </div>

    <!-- 人工强制结案原因弹窗 -->
    <div v-if="manualCompleteModalVisible" class="modal-mask" @click.self="manualCompleteModalVisible = false">
      <div class="modal-card">
        <div class="modal-header">
          <h3 class="modal-title">工单强制手动结案确认</h3>
          <button type="button" class="btn-close" @click="manualCompleteModalVisible = false">✕</button>
        </div>
        <form class="modal-body" @submit.prevent="handleConfirmManualComplete">
          <p class="modal-hint">
            注意：提前强制结案工单 <strong>{{ activeWo?.workOrderNo }}</strong> 将终止剩余生产，不补造任何报工或库存流水！
          </p>
          <div class="form-item">
            <label>强制完工原因 <span class="req">*</span></label>
            <textarea
              v-model="manualCompleteReason"
              class="form-input form-textarea"
              rows="3"
              placeholder="请填写提前截单/结案原因..."
              required
            ></textarea>
          </div>
          <div class="modal-footer">
            <button type="button" class="btn btn-secondary" @click="manualCompleteModalVisible = false">取消</button>
            <button type="submit" class="btn btn-danger" :disabled="isSubmitting">
              {{ isSubmitting ? "提交中..." : "确认强制结案" }}
            </button>
          </div>
        </form>
      </div>
    </div>

    <!-- 二次确认对话框 (提交/审核批准/正常完工) -->
    <ConfirmDialog
      v-model:visible="actionConfirm.visible"
      :title="actionConfirm.title"
      :message="actionConfirm.message"
      :danger="actionConfirm.danger"
      :loading="actionConfirm.loading"
      @confirm="handleExecuteActionConfirm"
    />
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted } from "vue";
import {
  PageHeader,
  FilterBar,
  DataTable,
  StatusBadge,
  QuantityText,
  ConfirmDialog,
  ErrorState,
} from "../../components/common";
import type { TableColumn } from "../../components/common/DataTable.vue";
import type { ViewState, BadgeType } from "../../types/common";
import type {
  WorkOrderItem,
  WorkOrderCreateRequest,
  WorkOrderStatus,
} from "../../types/manufacturing";
import {
  getWorkOrders,
  createWorkOrder,
  submitWorkOrder,
  approveWorkOrder,
  rejectWorkOrder,
  completeWorkOrder,
  manualCompleteWorkOrder,
} from "../../api/manufacturing";

const emit = defineEmits<{
  (e: "select-detail", item: WorkOrderItem): void;
}>();

const viewState = ref<ViewState>("loading");
const errorMessage = ref("");

const workOrderList = ref<WorkOrderItem[]>([]);
const total = ref(0);
const queryParams = reactive({
  page: 1,
  size: 10,
  keyword: "",
  status: "" as WorkOrderStatus | "",
});

const columns: TableColumn[] = [
  { key: "workOrderNo", label: "工单编号 / 来源", width: "220px" },
  { key: "productId", label: "目标产出品", minWidth: "200px" },
  { key: "plannedQty", label: "计划生产数", width: "120px", align: "right" },
  { key: "status", label: "工单状态", width: "120px", align: "center" },
  { key: "progress", label: "报工 / 入库进度", width: "160px", align: "center" },
  { key: "plannedFinishTime", label: "计划完工时间", width: "150px" },
  { key: "actions", label: "操作", width: "180px", align: "center" },
];

const createModalVisible = ref(false);
const isSubmitting = ref(false);
const createForm = reactive<WorkOrderCreateRequest>({
  productId: "prod-101",
  plannedQty: "100.00",
  plannedStartTime: "2026-09-05 08:00:00",
  plannedFinishTime: "2026-09-10 18:00:00",
  bomId: "bom-001",
  routingId: "rout-001",
  sourceSalesOrderLineId: "",
});

const activeWo = ref<WorkOrderItem | null>(null);
const rejectModalVisible = ref(false);
const rejectionReason = ref("");

const manualCompleteModalVisible = ref(false);
const manualCompleteReason = ref("");

const actionConfirm = reactive({
  visible: false,
  loading: false,
  title: "",
  message: "",
  danger: false,
  actionType: "" as "submit" | "approve" | "complete",
  targetWo: null as WorkOrderItem | null,
});

function getStatusBadgeType(status: WorkOrderStatus): BadgeType {
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

function getStatusText(status: WorkOrderStatus): string {
  switch (status) {
    case "Draft": return "未提交草稿";
    case "PendingApproval": return "待审批";
    case "Released": return "已下达排产";
    case "InProgress": return "生产执行中";
    case "Completed": return "已完工结案";
    case "Rejected": return "审批被退回";
    default: return status;
  }
}

function isActionAllowed(item: WorkOrderItem, action: string): boolean {
  if (!item.allowedActions || item.allowedActions.length === 0) return true;
  const match = item.allowedActions.find((a) => a.action === action);
  return match ? match.enabled : true;
}

function getActionDisabledReason(item: WorkOrderItem, action: string): string | undefined {
  const match = item.allowedActions?.find((a) => a.action === action);
  return match && !match.enabled ? match.reason : undefined;
}

async function fetchWorkOrders() {
  viewState.value = "loading";
  errorMessage.value = "";
  try {
    const res = await getWorkOrders({
      page: queryParams.page,
      size: queryParams.size,
      keyword: queryParams.keyword.trim() || undefined,
      status: queryParams.status || undefined,
    });
    if (res.data) {
      workOrderList.value = res.data.records || [];
      total.value = res.data.total || 0;
      viewState.value = workOrderList.value.length === 0 ? "empty" : "ready";
    }
  } catch (err: any) {
    errorMessage.value = err.message || "请求工单列表失败";
    viewState.value = "error";
  }
}

function handleSearch() {
  queryParams.page = 1;
  fetchWorkOrders();
}

function handleReset() {
  queryParams.keyword = "";
  queryParams.status = "";
  queryParams.page = 1;
  fetchWorkOrders();
}

function handlePageChange(page: number) {
  queryParams.page = page;
  fetchWorkOrders();
}

function viewDetail(item: WorkOrderItem) {
  emit("select-detail", item);
}

function openCreateModal() {
  createForm.productId = "prod-101";
  createForm.plannedQty = "100.00";
  createForm.plannedStartTime = "2026-09-05 08:00:00";
  createForm.plannedFinishTime = "2026-09-10 18:00:00";
  createForm.bomId = "bom-001";
  createForm.routingId = "rout-001";
  createForm.sourceSalesOrderLineId = "";
  createModalVisible.value = true;
}

async function submitCreateWorkOrder() {
  if (!createForm.plannedQty || !createForm.bomId || !createForm.routingId) return;
  isSubmitting.value = true;
  try {
    await createWorkOrder(createForm);
    createModalVisible.value = false;
    await fetchWorkOrders();
  } catch (err: any) {
    alert(`创建工单失败：${err.message}`);
  } finally {
    isSubmitting.value = false;
  }
}

function promptSubmit(item: WorkOrderItem) {
  actionConfirm.title = "提交工单审批确认";
  actionConfirm.message = `确认提交工单【${item.workOrderNo}】进入待审核状态吗？`;
  actionConfirm.danger = false;
  actionConfirm.actionType = "submit";
  actionConfirm.targetWo = item;
  actionConfirm.visible = true;
}

function promptApprove(item: WorkOrderItem) {
  actionConfirm.title = "工单审核通过确认";
  actionConfirm.message = `审核通过工单【${item.workOrderNo}】并将状态推进至【已下达】？生效 BOM 与 Routing 版本将被正式锁定。`;
  actionConfirm.danger = false;
  actionConfirm.actionType = "approve";
  actionConfirm.targetWo = item;
  actionConfirm.visible = true;
}

function promptComplete(item: WorkOrderItem) {
  actionConfirm.title = "工单正常完工确认";
  actionConfirm.message = `确认工单【${item.workOrderNo}】已完成全部工序报工与质检并标记为正常完工？`;
  actionConfirm.danger = false;
  actionConfirm.actionType = "complete";
  actionConfirm.targetWo = item;
  actionConfirm.visible = true;
}

async function handleExecuteActionConfirm() {
  if (!actionConfirm.targetWo) return;
  actionConfirm.loading = true;
  try {
    if (actionConfirm.actionType === "submit") {
      await submitWorkOrder(actionConfirm.targetWo.id as string);
    } else if (actionConfirm.actionType === "approve") {
      await approveWorkOrder(actionConfirm.targetWo.id as string);
    } else if (actionConfirm.actionType === "complete") {
      await completeWorkOrder(actionConfirm.targetWo.id as string);
    }
    actionConfirm.visible = false;
    await fetchWorkOrders();
  } catch (err: any) {
    alert(`操作失败：${err.message}`);
  } finally {
    actionConfirm.loading = false;
  }
}

function openRejectModal(item: WorkOrderItem) {
  activeWo.value = item;
  rejectionReason.value = "";
  rejectModalVisible.value = true;
}

async function handleConfirmReject() {
  if (!activeWo.value || !rejectionReason.value.trim()) return;
  isSubmitting.value = true;
  try {
    await rejectWorkOrder(activeWo.value.id as string, rejectionReason.value.trim());
    rejectModalVisible.value = false;
    await fetchWorkOrders();
  } catch (err: any) {
    alert(`退回失败：${err.message}`);
  } finally {
    isSubmitting.value = false;
  }
}

function openManualCompleteModal(item: WorkOrderItem) {
  activeWo.value = item;
  manualCompleteReason.value = "";
  manualCompleteModalVisible.value = true;
}

async function handleConfirmManualComplete() {
  if (!activeWo.value || !manualCompleteReason.value.trim()) return;
  isSubmitting.value = true;
  try {
    await manualCompleteWorkOrder(activeWo.value.id as string, manualCompleteReason.value.trim());
    manualCompleteModalVisible.value = false;
    await fetchWorkOrders();
  } catch (err: any) {
    alert(`结案失败：${err.message}`);
  } finally {
    isSubmitting.value = false;
  }
}

onMounted(() => {
  fetchWorkOrders();
});
</script>

<style scoped>
.manufacturing-view-container {
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

.wo-no-cell {
  display: flex;
  flex-direction: column;
  gap: 3px;
}

.source-tag {
  font-size: 11px;
  color: #94a3b8;
  background: rgba(148, 163, 184, 0.12);
  padding: 1px 6px;
  border-radius: 4px;
  align-self: flex-start;
}

.product-cell {
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.product-name {
  font-weight: 500;
  color: #f1f5f9;
}

.product-spec {
  font-size: 11px;
}

.progress-cell {
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.progress-row {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 4px;
  font-size: 12px;
}

.progress-label {
  color: #94a3b8;
}

.progress-sep {
  color: #64748b;
  margin: 0 2px;
}

.defect-row {
  font-size: 11px;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 4px;
}

.defect-label {
  color: #f87171;
}

.time-cell {
  font-size: 12px;
}

.action-btn-group {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  flex-wrap: wrap;
}

.btn-text {
  background: none;
  border: none;
  color: #38bdf8;
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
.text-success { color: #34d399 !important; }
.text-warning { color: #fbbf24 !important; }
.text-danger { color: #f87171 !important; }

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
  max-width: 520px;
  box-shadow: 0 20px 30px rgba(0, 0, 0, 0.5);
  overflow: hidden;
}

.modal-large {
  max-width: 680px;
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
  max-height: 75vh;
  overflow-y: auto;
}

.modal-hint {
  margin: 0;
  font-size: 13px;
  color: #cbd5e1;
  line-height: 1.5;
}

.form-grid.two-col {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 12px;
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

.req {
  color: #f87171;
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

.form-input:focus {
  border-color: #38bdf8;
}

.form-textarea {
  resize: vertical;
}

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
