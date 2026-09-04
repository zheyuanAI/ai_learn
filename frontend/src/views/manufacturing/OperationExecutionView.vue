<template>
  <div class="manufacturing-view-container">
    <!-- 统一页面头部 -->
    <PageHeader
      title="工序实际执行 (Operation Execution)"
      tag="MES / SHOP FLOOR"
      description="工序执行记录现场真实的加工动作生命周期。只有在此处触发【开始】执行后，对应工单才正式推进为【生产中】。"
    >
      <template #actions>
        <button type="button" class="btn btn-primary" @click="openCreateModal">
          <span class="btn-icon">＋</span>
          <span>发起工序执行</span>
        </button>
      </template>
    </PageHeader>

    <!-- 统一筛选栏 -->
    <FilterBar
      v-model="queryParams.keyword"
      placeholder="搜索执行编号、工单号或工序..."
      @search="handleSearch"
      @reset="handleReset"
    >
      <div class="filter-select-group">
        <label class="filter-label">状态：</label>
        <select v-model="queryParams.status" class="filter-select" @change="handleSearch">
          <option value="">全部状态</option>
          <option value="NotStarted">未开始 (NotStarted)</option>
          <option value="Running">正在加工 (Running)</option>
          <option value="Paused">已暂停 (Paused)</option>
          <option value="Completed">已完成 (Completed)</option>
        </select>
      </div>
    </FilterBar>

    <!-- 错误异常提示 -->
    <ErrorState
      v-if="viewState === 'error'"
      title="工序执行数据加载失败"
      :message="errorMessage"
      @retry="fetchExecutionList"
    />

    <!-- 数据表格 -->
    <DataTable
      v-else
      :columns="columns"
      :data="executionList"
      :loading="viewState === 'loading'"
      :page="queryParams.page"
      :size="queryParams.size"
      :total="total"
      empty-text="暂无现场工序执行实例"
      @page-change="handlePageChange"
    >
      <!-- 执行编号 -->
      <template #executionNo="{ row }">
        <span class="font-mono highlight-code">{{ row.executionNo }}</span>
      </template>

      <!-- 关联单据 -->
      <template #workOrderId="{ row }">
        <div class="relation-cell">
          <span class="font-mono text-primary">{{ row.workOrderNo || row.workOrderId }}</span>
          <span class="font-mono text-muted sub-tag">派工: {{ row.dispatchNo }}</span>
        </div>
      </template>

      <!-- 工序步骤 -->
      <template #operationId="{ row }">
        <div class="op-cell">
          <span class="op-name">{{ row.operationName }}</span>
          <span class="text-muted sub-tag font-mono">操作员: {{ row.operatorName || row.operatorId }}</span>
        </div>
      </template>

      <!-- 机台设备 -->
      <template #deviceId="{ row }">
        <span v-if="row.deviceName || row.deviceCode" class="font-mono device-tag">
          {{ row.deviceName || row.deviceCode }}
        </span>
        <span v-else class="text-muted">纯手工工位</span>
      </template>

      <!-- 执行状态 -->
      <template #status="{ row }">
        <StatusBadge
          :type="row.status === 'Running' ? 'primary' : row.status === 'Completed' ? 'success' : row.status === 'Paused' ? 'warning' : 'default'"
          :text="row.status === 'NotStarted' ? '尚未开工' : row.status === 'Running' ? '运行中' : row.status === 'Paused' ? '已暂停' : '已完工'"
          :pulsing="row.status === 'Running'"
        />
      </template>

      <!-- 累计报工数 -->
      <template #reportedQty="{ row }">
        <QuantityText :value="row.reportedQty || '0.00'" unit="件" />
      </template>

      <!-- 时间记录 -->
      <template #startedAt="{ row }">
        <div class="time-cell font-mono text-muted">
          <div>起: {{ row.startedAt || "-" }}</div>
          <div>止: {{ row.completedAt || "-" }}</div>
        </div>
      </template>

      <!-- 实时动作操作入口 (受 allowedActions 约束) -->
      <template #actions="{ row }">
        <div class="action-btn-group">
          <!-- 开始 (NotStarted -> Running) -->
          <button
            v-if="row.status === 'NotStarted'"
            type="button"
            class="btn-text text-success"
            :disabled="!isActionAllowed(row, 'start')"
            :title="getActionDisabledReason(row, 'start') || '开始该工序执行'"
            @click="handleStart(row)"
          >
            开始
          </button>

          <!-- 暂停 (Running -> Paused) -->
          <button
            v-if="row.status === 'Running'"
            type="button"
            class="btn-text text-warning"
            :disabled="!isActionAllowed(row, 'pause')"
            :title="getActionDisabledReason(row, 'pause') || '暂停工序'"
            @click="openPauseModal(row)"
          >
            暂停
          </button>

          <!-- 恢复 (Paused -> Running) -->
          <button
            v-if="row.status === 'Paused'"
            type="button"
            class="btn-text text-primary"
            :disabled="!isActionAllowed(row, 'resume')"
            :title="getActionDisabledReason(row, 'resume') || '恢复工序执行'"
            @click="handleResume(row)"
          >
            恢复
          </button>

          <!-- 完成 (Running -> Completed) -->
          <button
            v-if="row.status === 'Running'"
            type="button"
            class="btn-text text-success"
            :disabled="!isActionAllowed(row, 'complete')"
            :title="getActionDisabledReason(row, 'complete') || '工序完工'"
            @click="handleComplete(row)"
          >
            完成
          </button>

          <!-- 报工 (Running / Paused) -->
          <button
            v-if="row.status === 'Running' || row.status === 'Paused'"
            type="button"
            class="btn-text text-cyan"
            :disabled="!isActionAllowed(row, 'report')"
            :title="getActionDisabledReason(row, 'report') || '现场报工录入'"
            @click="openReportModal(row)"
          >
            报工
          </button>
        </div>
      </template>
    </DataTable>

    <!-- 弹窗 1：创建工序执行实例 -->
    <div v-if="createModalVisible" class="modal-mask" @click.self="createModalVisible = false">
      <div class="modal-card">
        <div class="modal-header">
          <h3 class="modal-title">创建工序实际执行实例</h3>
          <button type="button" class="btn-close" @click="createModalVisible = false">✕</button>
        </div>
        <form class="modal-body" @submit.prevent="submitCreateExecution">
          <div class="form-item">
            <label>关联派工单 ID <span class="req">*</span></label>
            <input
              v-model="createForm.dispatchOrderId"
              type="text"
              class="form-input font-mono"
              placeholder="例如 disp-003 或 DSP-20260902-01"
              required
            />
          </div>
          <div class="form-item">
            <label>机台设备 ID (可选)</label>
            <input
              v-model="createForm.deviceId"
              type="text"
              class="form-input font-mono"
              placeholder="例如 dev-glue-01"
            />
          </div>
          <div class="modal-footer">
            <button type="button" class="btn btn-secondary" @click="createModalVisible = false">取消</button>
            <button type="submit" class="btn btn-primary" :disabled="isSubmitting">
              {{ isSubmitting ? "创建中..." : "保存执行实例" }}
            </button>
          </div>
        </form>
      </div>
    </div>

    <!-- 弹窗 2：暂停原因弹窗 -->
    <div v-if="pauseModalVisible" class="modal-mask" @click.self="pauseModalVisible = false">
      <div class="modal-card">
        <div class="modal-header">
          <h3 class="modal-title">工序暂停确认</h3>
          <button type="button" class="btn-close" @click="pauseModalVisible = false">✕</button>
        </div>
        <form class="modal-body" @submit.prevent="submitPause">
          <div class="form-item">
            <label>暂停原因 (如换料、设备维护、交接班) <span class="req">*</span></label>
            <input
              v-model="pauseReason"
              type="text"
              class="form-input"
              placeholder="请简要说明暂停原因"
              required
            />
          </div>
          <div class="modal-footer">
            <button type="button" class="btn btn-secondary" @click="pauseModalVisible = false">取消</button>
            <button type="submit" class="btn btn-warning" :disabled="isSubmitting">确认暂停</button>
          </div>
        </form>
      </div>
    </div>

    <!-- 弹窗 3：现场报工录入 -->
    <div v-if="reportModalVisible" class="modal-mask" @click.self="reportModalVisible = false">
      <div class="modal-card">
        <div class="modal-header">
          <h3 class="modal-title">工序报工录入 — {{ activeExec?.operationName }}</h3>
          <button type="button" class="btn-close" @click="reportModalVisible = false">✕</button>
        </div>
        <form class="modal-body" @submit.prevent="submitWorkReport">
          <div class="form-grid two-col">
            <div class="form-item">
              <label>申报合格数量 <span class="req">*</span></label>
              <input
                v-model="reportForm.qualifiedQty"
                type="text"
                class="form-input font-mono"
                placeholder="例如 20.00"
                required
              />
            </div>
            <div class="form-item">
              <label>申报不良数量 <span class="req">*</span></label>
              <input
                v-model="reportForm.defectQty"
                type="text"
                class="form-input font-mono"
                placeholder="例如 0.00"
                required
              />
            </div>
          </div>

          <div class="form-item">
            <label>报工时间</label>
            <input
              v-model="reportForm.reportTime"
              type="text"
              class="form-input font-mono"
              placeholder="默认当前时间"
            />
          </div>

          <div class="form-item">
            <label>备注说明</label>
            <input
              v-model="reportForm.remark"
              type="text"
              class="form-input"
              placeholder="例如 首批试切合格"
            />
          </div>

          <div class="modal-footer">
            <button type="button" class="btn btn-secondary" @click="reportModalVisible = false">取消</button>
            <button type="submit" class="btn btn-primary" :disabled="isSubmitting">提交报工</button>
          </div>
        </form>
      </div>
    </div>

    <!-- 二次确认对话框 -->
    <ConfirmDialog
      v-model:visible="confirmState.visible"
      :title="confirmState.title"
      :message="confirmState.message"
      :loading="confirmState.loading"
      @confirm="executeConfirmAction"
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
import type { ViewState } from "../../types/common";
import type {
  OperationExecutionItem,
  OperationExecutionStatus,
  OperationExecutionCreateRequest,
} from "../../types/manufacturing";
import {
  getOperationExecutions,
  createOperationExecution,
  startOperationExecution,
  pauseOperationExecution,
  resumeOperationExecution,
  completeOperationExecution,
  createWorkReport,
} from "../../api/manufacturing";

const viewState = ref<ViewState>("loading");
const errorMessage = ref("");

const executionList = ref<OperationExecutionItem[]>([]);
const total = ref(0);
const queryParams = reactive({
  page: 1,
  size: 10,
  keyword: "",
  status: "" as OperationExecutionStatus | "",
});

const columns: TableColumn[] = [
  { key: "executionNo", label: "执行编号", width: "160px" },
  { key: "workOrderId", label: "工单 / 派工单", minWidth: "180px" },
  { key: "operationId", label: "作业工序 / 操作人", minWidth: "180px" },
  { key: "deviceId", label: "执行机台", width: "160px" },
  { key: "status", label: "执行状态", width: "120px", align: "center" },
  { key: "reportedQty", label: "累计报工", width: "120px", align: "right" },
  { key: "startedAt", label: "开工 / 完工时间", width: "160px" },
  { key: "actions", label: "动作控制", width: "170px", align: "center" },
];

const createModalVisible = ref(false);
const isSubmitting = ref(false);
const createForm = reactive<OperationExecutionCreateRequest>({
  dispatchOrderId: "disp-003",
  deviceId: "dev-glue-01",
});

const activeExec = ref<OperationExecutionItem | null>(null);
const pauseModalVisible = ref(false);
const pauseReason = ref("");

const reportModalVisible = ref(false);
const reportForm = reactive({
  qualifiedQty: "10.00",
  defectQty: "0.00",
  reportTime: "",
  remark: "",
});

const confirmState = reactive({
  visible: false,
  loading: false,
  title: "",
  message: "",
  type: "" as "start" | "resume" | "complete",
  targetItem: null as OperationExecutionItem | null,
});

function isActionAllowed(item: OperationExecutionItem, action: string): boolean {
  if (!item.allowedActions || item.allowedActions.length === 0) return true;
  const match = item.allowedActions.find((a) => a.action === action);
  return match ? match.enabled : true;
}

function getActionDisabledReason(item: OperationExecutionItem, action: string): string | undefined {
  const match = item.allowedActions?.find((a) => a.action === action);
  return match && !match.enabled ? match.reason : undefined;
}

async function fetchExecutionList() {
  viewState.value = "loading";
  errorMessage.value = "";
  try {
    const res = await getOperationExecutions({
      page: queryParams.page,
      size: queryParams.size,
      status: queryParams.status || undefined,
    });
    if (res.data) {
      let list = res.data.records || [];
      if (queryParams.keyword.trim()) {
        const kw = queryParams.keyword.toLowerCase();
        list = list.filter(
          (e) =>
            e.executionNo.toLowerCase().includes(kw) ||
            e.workOrderNo?.toLowerCase().includes(kw) ||
            e.operationName?.toLowerCase().includes(kw)
        );
      }
      executionList.value = list;
      total.value = res.data.total || list.length;
      viewState.value = executionList.value.length === 0 ? "empty" : "ready";
    }
  } catch (err: any) {
    errorMessage.value = err.message || "请求工序执行列表失败";
    viewState.value = "error";
  }
}

function handleSearch() {
  queryParams.page = 1;
  fetchExecutionList();
}

function handleReset() {
  queryParams.keyword = "";
  queryParams.status = "";
  queryParams.page = 1;
  fetchExecutionList();
}

function handlePageChange(page: number) {
  queryParams.page = page;
  fetchExecutionList();
}

function openCreateModal() {
  createForm.dispatchOrderId = "disp-003";
  createForm.deviceId = "dev-glue-01";
  createModalVisible.value = true;
}

async function submitCreateExecution() {
  if (!createForm.dispatchOrderId) return;
  isSubmitting.value = true;
  try {
    await createOperationExecution(createForm);
    createModalVisible.value = false;
    await fetchExecutionList();
  } catch (err: any) {
    alert(`创建执行失败：${err.message}`);
  } finally {
    isSubmitting.value = false;
  }
}

function handleStart(item: OperationExecutionItem) {
  confirmState.title = "开始工序执行确认";
  confirmState.message = `确认开工执行【${item.executionNo}】？关联工单将正式推进为【生产中】。`;
  confirmState.type = "start";
  confirmState.targetItem = item;
  confirmState.visible = true;
}

function openPauseModal(item: OperationExecutionItem) {
  activeExec.value = item;
  pauseReason.value = "设备刀具磨损检查更换";
  pauseModalVisible.value = true;
}

async function submitPause() {
  if (!activeExec.value) return;
  isSubmitting.value = true;
  try {
    await pauseOperationExecution(activeExec.value.id as string, pauseReason.value);
    pauseModalVisible.value = false;
    await fetchExecutionList();
  } catch (err: any) {
    alert(`暂停失败：${err.message}`);
  } finally {
    isSubmitting.value = false;
  }
}

function handleResume(item: OperationExecutionItem) {
  confirmState.title = "恢复运行确认";
  confirmState.message = `确认恢复执行【${item.executionNo}】？`;
  confirmState.type = "resume";
  confirmState.targetItem = item;
  confirmState.visible = true;
}

function handleComplete(item: OperationExecutionItem) {
  confirmState.title = "工序完工确认";
  confirmState.message = `确认完成该道工序执行【${item.executionNo}】？`;
  confirmState.type = "complete";
  confirmState.targetItem = item;
  confirmState.visible = true;
}

async function executeConfirmAction() {
  if (!confirmState.targetItem) return;
  confirmState.loading = true;
  try {
    const id = confirmState.targetItem.id as string;
    if (confirmState.type === "start") {
      await startOperationExecution(id);
    } else if (confirmState.type === "resume") {
      await resumeOperationExecution(id);
    } else if (confirmState.type === "complete") {
      await completeOperationExecution(id);
    }
    confirmState.visible = false;
    await fetchExecutionList();
  } catch (err: any) {
    alert(`操作失败：${err.message}`);
  } finally {
    confirmState.loading = false;
  }
}

function openReportModal(item: OperationExecutionItem) {
  activeExec.value = item;
  reportForm.qualifiedQty = "10.00";
  reportForm.defectQty = "0.00";
  reportForm.reportTime = new Date().toLocaleString();
  reportForm.remark = "";
  reportModalVisible.value = true;
}

async function submitWorkReport() {
  if (!activeExec.value) return;
  isSubmitting.value = true;
  try {
    await createWorkReport({
      operationExecutionId: activeExec.value.id as string,
      qualifiedQty: reportForm.qualifiedQty,
      defectQty: reportForm.defectQty,
      reportTime: reportForm.reportTime,
      remark: reportForm.remark,
    });
    reportModalVisible.value = false;
    await fetchExecutionList();
  } catch (err: any) {
    alert(`报工提交失败：${err.message}`);
  } finally {
    isSubmitting.value = false;
  }
}

onMounted(() => {
  fetchExecutionList();
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

.relation-cell, .op-cell {
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.sub-tag {
  font-size: 11px;
}

.op-name {
  color: #f1f5f9;
}

.device-tag {
  color: #38bdf8;
}

.time-cell {
  font-size: 11px;
}

.action-btn-group {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 6px;
  flex-wrap: wrap;
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
.text-success { color: #34d399 !important; }
.text-warning { color: #fbbf24 !important; }
.text-cyan { color: #22d3ee !important; }

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
