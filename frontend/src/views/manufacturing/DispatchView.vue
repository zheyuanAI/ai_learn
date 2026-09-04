<template>
  <div class="manufacturing-view-container">
    <!-- 统一页面头部 -->
    <PageHeader
      title="生产派工 (Dispatch) 管理"
      tag="MES / DISPATCH"
      description="将已下达工单中的工序任务、派工数量分配给现场操作员及机台设备。下达派工仅表达安排生效，不代表现场已实际开工。"
    >
      <template #actions>
        <button type="button" class="btn btn-primary" @click="openCreateModal">
          <span class="btn-icon">＋</span>
          <span>新建派工单</span>
        </button>
      </template>
    </PageHeader>

    <!-- 统一筛选栏 -->
    <FilterBar
      v-model="queryParams.keyword"
      placeholder="搜索派工单号或工单编号..."
      @search="handleSearch"
      @reset="handleReset"
    >
      <div class="filter-select-group">
        <label class="filter-label">状态：</label>
        <select v-model="queryParams.status" class="filter-select" @change="handleSearch">
          <option value="">全部状态</option>
          <option value="Draft">草稿安排 (Draft)</option>
          <option value="Released">已下达 (Released)</option>
          <option value="Processing">加工中 (Processing)</option>
          <option value="Completed">已完成 (Completed)</option>
        </select>
      </div>
    </FilterBar>

    <!-- 错误异常提示 -->
    <ErrorState
      v-if="viewState === 'error'"
      title="派工单列表加载失败"
      :message="errorMessage"
      @retry="fetchDispatchList"
    />

    <!-- 数据表格 -->
    <DataTable
      v-else
      :columns="columns"
      :data="dispatchList"
      :loading="viewState === 'loading'"
      :page="queryParams.page"
      :size="queryParams.size"
      :total="total"
      empty-text="暂无匹配的派工单记录"
      @page-change="handlePageChange"
    >
      <!-- 派工单号 -->
      <template #dispatchNo="{ row }">
        <span class="font-mono highlight-code">{{ row.dispatchNo }}</span>
      </template>

      <!-- 关联工单与产品 -->
      <template #workOrderId="{ row }">
        <div class="wo-cell">
          <span class="font-mono highlight-wo">{{ row.workOrderNo || row.workOrderId }}</span>
          <span class="text-muted product-text">{{ row.productName || "定制产品" }}</span>
        </div>
      </template>

      <!-- 工序步骤 -->
      <template #operationId="{ row }">
        <div class="op-cell">
          <span class="op-title">{{ row.operationName }}</span>
          <span class="op-no font-mono text-muted">工序序号: #{{ row.operationNo || 10 }}</span>
        </div>
      </template>

      <!-- 责任操作工 -->
      <template #operatorId="{ row }">
        <span class="operator-name">{{ row.operatorName || row.operatorId }}</span>
      </template>

      <!-- 安排设备 -->
      <template #deviceId="{ row }">
        <span v-if="row.deviceName || row.deviceCode" class="font-mono device-text">
          {{ row.deviceName || row.deviceCode }}
        </span>
        <span v-else class="text-muted">人工通用工位</span>
      </template>

      <!-- 派工数量 -->
      <template #dispatchQty="{ row }">
        <QuantityText :value="row.dispatchQty" unit="件" />
      </template>

      <!-- 状态徽标 -->
      <template #status="{ row }">
        <StatusBadge
          :type="row.status === 'Completed' ? 'success' : row.status === 'Processing' ? 'primary' : row.status === 'Released' ? 'info' : 'default'"
          :text="row.status === 'Draft' ? '草稿' : row.status === 'Released' ? '已下达' : row.status === 'Processing' ? '加工中' : '已完成'"
          :pulsing="row.status === 'Processing'"
        />
      </template>

      <!-- 操作列 (受 allowedActions 约束) -->
      <template #actions="{ row }">
        <div class="action-btn-group">
          <!-- 下达派工 (Draft -> Released) -->
          <button
            v-if="row.status === 'Draft'"
            type="button"
            class="btn-text text-primary"
            :disabled="!isActionAllowed(row, 'release')"
            :title="getActionDisabledReason(row, 'release') || '下达派工单'"
            @click="promptRelease(row)"
          >
            下达
          </button>
          <span v-else class="text-muted font-xs">无需操作</span>
        </div>
      </template>
    </DataTable>

    <!-- 新建派工单对话框 -->
    <div v-if="createModalVisible" class="modal-mask" @click.self="createModalVisible = false">
      <div class="modal-card modal-large">
        <div class="modal-header">
          <h3 class="modal-title">新建工序派工安排</h3>
          <button type="button" class="btn-close" @click="createModalVisible = false">✕</button>
        </div>

        <form class="modal-body" @submit.prevent="submitCreateDispatch">
          <div class="form-grid two-col">
            <div class="form-item">
              <label>关联工单编号/ID <span class="req">*</span></label>
              <input
                v-model="createForm.workOrderId"
                type="text"
                class="form-input font-mono"
                placeholder="例如 wo-001 或 WO-20260901-001"
                required
              />
            </div>
            <div class="form-item">
              <label>关联工序 ID/名称 <span class="req">*</span></label>
              <input
                v-model="createForm.operationId"
                type="text"
                class="form-input font-mono"
                placeholder="例如 op-101"
                required
              />
            </div>
          </div>

          <div class="form-grid two-col">
            <div class="form-item">
              <label>派工指派操作工 <span class="req">*</span></label>
              <input
                v-model="createForm.operatorId"
                type="text"
                class="form-input"
                placeholder="例如 user-op-01 (王小华)"
                required
              />
            </div>
            <div class="form-item">
              <label>派工指派数量 <span class="req">*</span></label>
              <input
                v-model="createForm.dispatchQty"
                type="text"
                class="form-input font-mono"
                placeholder="例如 100.00"
                required
              />
            </div>
          </div>

          <div class="form-item">
            <label>指定加工设备 (可选，未指定为纯手工工序)</label>
            <input
              v-model="createForm.deviceId"
              type="text"
              class="form-input font-mono"
              placeholder="例如 dev-burn-01 (高速烧录机 1 号台)"
            />
          </div>

          <div class="modal-footer">
            <button type="button" class="btn btn-secondary" @click="createModalVisible = false">取消</button>
            <button type="submit" class="btn btn-primary" :disabled="isSubmitting">
              {{ isSubmitting ? "创建中..." : "保存派工单 (Draft)" }}
            </button>
          </div>
        </form>
      </div>
    </div>

    <!-- 下达确认对话框 -->
    <ConfirmDialog
      v-model:visible="releaseConfirm.visible"
      title="下达派工单确认"
      :message="`确定要正式下达派工单【${releaseConfirm.item?.dispatchNo}】吗？下达后允许操作工开始该工序执行。`"
      :loading="releaseConfirm.loading"
      @confirm="handleConfirmRelease"
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
  DispatchOrderItem,
  DispatchOrderCreateRequest,
  DispatchOrderStatus,
} from "../../types/manufacturing";
import { getDispatchOrders, createDispatchOrder, releaseDispatchOrder } from "../../api/manufacturing";

const viewState = ref<ViewState>("loading");
const errorMessage = ref("");

const dispatchList = ref<DispatchOrderItem[]>([]);
const total = ref(0);
const queryParams = reactive({
  page: 1,
  size: 10,
  keyword: "",
  status: "" as DispatchOrderStatus | "",
});

const columns: TableColumn[] = [
  { key: "dispatchNo", label: "派工单号", width: "180px" },
  { key: "workOrderId", label: "关联工单 / 产出品", minWidth: "200px" },
  { key: "operationId", label: "指派工序", minWidth: "180px" },
  { key: "dispatchQty", label: "派工数量", width: "120px", align: "right" },
  { key: "operatorId", label: "责任操作工", width: "120px" },
  { key: "deviceId", label: "分配设备", width: "160px" },
  { key: "status", label: "状态", width: "110px", align: "center" },
  { key: "actions", label: "操作", width: "100px", align: "center" },
];

const createModalVisible = ref(false);
const isSubmitting = ref(false);
const createForm = reactive<DispatchOrderCreateRequest>({
  workOrderId: "wo-001",
  operationId: "op-101",
  operatorId: "user-op-01",
  dispatchQty: "50.00",
  deviceId: "dev-burn-01",
});

const releaseConfirm = reactive({
  visible: false,
  loading: false,
  item: null as DispatchOrderItem | null,
});

function isActionAllowed(item: DispatchOrderItem, action: string): boolean {
  if (!item.allowedActions || item.allowedActions.length === 0) return true;
  const match = item.allowedActions.find((a) => a.action === action);
  return match ? match.enabled : true;
}

function getActionDisabledReason(item: DispatchOrderItem, action: string): string | undefined {
  const match = item.allowedActions?.find((a) => a.action === action);
  return match && !match.enabled ? match.reason : undefined;
}

async function fetchDispatchList() {
  viewState.value = "loading";
  errorMessage.value = "";
  try {
    const res = await getDispatchOrders({
      page: queryParams.page,
      size: queryParams.size,
      status: queryParams.status || undefined,
    });
    if (res.data) {
      let list = res.data.records || [];
      if (queryParams.keyword.trim()) {
        const kw = queryParams.keyword.toLowerCase();
        list = list.filter(
          (d: any) =>
            d.dispatchNo.toLowerCase().includes(kw) ||
            d.workOrderNo?.toLowerCase().includes(kw) ||
            d.operationName?.toLowerCase().includes(kw)
        );
      }
      dispatchList.value = list;
      total.value = res.data.total || list.length;
      viewState.value = dispatchList.value.length === 0 ? "empty" : "ready";
    }
  } catch (err: any) {
    errorMessage.value = err.message || "请求派工列表失败";
    viewState.value = "error";
  }
}

function handleSearch() {
  queryParams.page = 1;
  fetchDispatchList();
}

function handleReset() {
  queryParams.keyword = "";
  queryParams.status = "";
  queryParams.page = 1;
  fetchDispatchList();
}

function handlePageChange(page: number) {
  queryParams.page = page;
  fetchDispatchList();
}

function openCreateModal() {
  createForm.workOrderId = "wo-001";
  createForm.operationId = "op-101";
  createForm.operatorId = "user-op-01";
  createForm.dispatchQty = "50.00";
  createForm.deviceId = "";
  createModalVisible.value = true;
}

async function submitCreateDispatch() {
  if (!createForm.workOrderId || !createForm.dispatchQty) return;
  isSubmitting.value = true;
  try {
    await createDispatchOrder(createForm);
    createModalVisible.value = false;
    await fetchDispatchList();
  } catch (err: any) {
    alert(`创建派工单失败：${err.message}`);
  } finally {
    isSubmitting.value = false;
  }
}

function promptRelease(item: DispatchOrderItem) {
  releaseConfirm.item = item;
  releaseConfirm.visible = true;
}

async function handleConfirmRelease() {
  if (!releaseConfirm.item) return;
  releaseConfirm.loading = true;
  try {
    await releaseDispatchOrder(releaseConfirm.item.id as string);
    releaseConfirm.visible = false;
    await fetchDispatchList();
  } catch (err: any) {
    alert(`下达失败：${err.message}`);
  } finally {
    releaseConfirm.loading = false;
  }
}

onMounted(() => {
  fetchDispatchList();
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

.wo-cell, .op-cell {
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.highlight-wo {
  color: #e2e8f0;
  font-weight: 500;
}

.product-text {
  font-size: 11px;
}

.op-title {
  color: #f1f5f9;
}

.op-no {
  font-size: 11px;
}

.operator-name {
  color: #e2e8f0;
}

.device-text {
  color: #38bdf8;
}

.action-btn-group {
  display: flex;
  align-items: center;
  justify-content: center;
}

.btn-text {
  background: none;
  border: none;
  font-size: 12px;
  cursor: pointer;
  padding: 2px 6px;
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
  max-width: 540px;
  box-shadow: 0 20px 30px rgba(0, 0, 0, 0.5);
  overflow: hidden;
}

.modal-large { max-width: 620px; }

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
