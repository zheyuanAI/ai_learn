<template>
  <div class="manufacturing-view-container">
    <!-- 统一页面头部 -->
    <PageHeader
      title="生产成品入库 (Finished Goods Receipt)"
      tag="MES / RECEIPT"
      description="将车间已检验合格且尚未入库的产成品办理成品入库。入库确认后通过库存应用服务真实增加成品库位实物库存。"
    >
      <template #actions>
        <button type="button" class="btn btn-primary" @click="openCreateModal">
          <span class="btn-icon">＋</span>
          <span>新建成品入库单</span>
        </button>
      </template>
    </PageHeader>

    <!-- 统一筛选栏 -->
    <FilterBar
      v-model="queryParams.keyword"
      placeholder="搜索入库单号或工单编号..."
      @search="handleSearch"
      @reset="handleReset"
    >
      <div class="filter-select-group">
        <label class="filter-label">状态：</label>
        <select v-model="queryParams.status" class="filter-select" @change="handleSearch">
          <option value="">全部状态</option>
          <option value="Draft">草稿待入库 (Draft)</option>
          <option value="Confirmed">已确认入库 (Confirmed)</option>
        </select>
      </div>
    </FilterBar>

    <!-- 错误异常提示 -->
    <ErrorState
      v-if="viewState === 'error'"
      title="成品入库单加载失败"
      :message="errorMessage"
      @retry="fetchReceiptList"
    />

    <!-- 数据表格 -->
    <DataTable
      v-else
      :columns="columns"
      :data="receiptList"
      :loading="viewState === 'loading'"
      :page="queryParams.page"
      :size="queryParams.size"
      :total="total"
      empty-text="暂无匹配的成品入库记录"
      @page-change="handlePageChange"
    >
      <!-- 入库单号 -->
      <template #receiptNo="{ row }">
        <span class="font-mono highlight-code">{{ row.receiptNo }}</span>
      </template>

      <!-- 关联工单与产出物 -->
      <template #workOrderId="{ row }">
        <div class="wo-cell">
          <span class="font-mono text-primary">{{ row.workOrderNo || row.workOrderId }}</span>
          <span class="product-name text-muted">{{ row.productName || "产成品" }}</span>
        </div>
      </template>

      <!-- 入库数量 (全量 QuantityText) -->
      <template #receiptQty="{ row }">
        <QuantityText :value="row.receiptQty" unit="件" />
      </template>

      <!-- 目标仓库 / 库位 -->
      <template #warehouseId="{ row }">
        <div class="location-cell">
          <span class="wh-name">{{ row.warehouseName || row.warehouseId }}</span>
          <span class="loc-code font-mono text-muted">库位: {{ row.locationCode || row.locationId }}</span>
        </div>
      </template>

      <!-- 状态徽标 -->
      <template #status="{ row }">
        <StatusBadge
          :type="row.status === 'Confirmed' ? 'success' : 'warning'"
          :text="row.status === 'Confirmed' ? '已确认入库' : '待入库确认'"
        />
      </template>

      <!-- 库存流水软引用 -->
      <template #inventoryTransactionId="{ row }">
        <span v-if="row.inventoryTransactionId" class="font-mono text-muted">
          {{ row.inventoryTransactionId }}
        </span>
        <span v-else class="text-muted font-xs">待入库确认后生成</span>
      </template>

      <!-- 操作 (受 allowedActions 约束) -->
      <template #actions="{ row }">
        <div class="action-btn-group">
          <!-- 确认入库 (Draft -> Confirmed) -->
          <button
            v-if="row.status === 'Draft'"
            type="button"
            class="btn-text text-primary"
            :disabled="!isActionAllowed(row, 'confirm')"
            :title="getActionDisabledReason(row, 'confirm') || '确认成品入库'"
            @click="promptConfirm(row)"
          >
            确认入库
          </button>
          <span v-else class="text-muted font-xs">已完成增加</span>
        </div>
      </template>
    </DataTable>

    <!-- 新建成品入库单对话框 -->
    <div v-if="createModalVisible" class="modal-mask" @click.self="createModalVisible = false">
      <div class="modal-card modal-large">
        <div class="modal-header">
          <h3 class="modal-title">新建成品入库申请单</h3>
          <button type="button" class="btn-close" @click="createModalVisible = false">✕</button>
        </div>

        <form class="modal-body" @submit.prevent="submitCreateReceipt">
          <div class="form-grid two-col">
            <div class="form-item">
              <label>生产工单 <span class="req">*</span></label>
              <select v-model="createForm.workOrderId" class="form-input" required @change="fetchReceiptList">
                <option value="">请选择真实工单</option>
                <option v-for="workOrder in workOrders" :key="workOrder.id" :value="String(workOrder.id)">
                  {{ workOrder.workOrderNo }}
                </option>
              </select>
            </div>
            <div class="form-item">
              <label>成品入库数量 <span class="req">*</span></label>
              <input
                v-model="createForm.receiptQty"
                type="text"
                class="form-input font-mono"
                placeholder="例如 100.00"
                required
              />
            </div>
          </div>

          <div class="form-grid two-col">
            <div class="form-item">
              <label>目标入库仓库 <span class="req">*</span></label>
              <select v-model="createForm.warehouseId" class="form-input" required @change="loadLocations">
                <option value="">请选择真实仓库</option>
                <option v-for="warehouse in warehouses" :key="warehouse.id" :value="String(warehouse.id)">
                  {{ warehouse.name }} ({{ warehouse.code }})
                </option>
              </select>
            </div>
            <div class="form-item">
              <label>目标货架库位 <span class="req">*</span></label>
              <select v-model="createForm.locationId" class="form-input" required>
                <option value="">请选择真实库位</option>
                <option v-for="location in locations" :key="location.id" :value="String(location.id)">
                  {{ location.code }} ({{ location.name }})
                </option>
              </select>
            </div>
          </div>

          <p class="modal-hint">
            提示：入库数量受累计检验合格数量减累计已入库数量约束，不可超额申报入库。
          </p>

          <div class="modal-footer">
            <button type="button" class="btn btn-secondary" @click="createModalVisible = false">取消</button>
            <button type="submit" class="btn btn-primary" :disabled="isSubmitting">保存入库单 (草稿)</button>
          </div>
        </form>
      </div>
    </div>

    <!-- 确认入库二次确认 -->
    <ConfirmDialog
      v-model:visible="confirmDialog.visible"
      title="确认成品入库"
      :message="`确定确认入库单【${confirmDialog.item?.receiptNo}】？系统将向库存应用服务发出指令，增加成品实物库存并记录流水软引用。`"
      :loading="confirmDialog.loading"
      @confirm="handleExecuteConfirm"
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
  FinishedGoodsReceiptItem,
  FinishedGoodsReceiptCreateRequest,
} from "../../types/manufacturing";
import {
  getFinishedGoodsReceipts,
  createFinishedGoodsReceipt,
  confirmFinishedGoodsReceipt,
  getWorkOrders,
} from "../../api/manufacturing";
import { getLocations, getWarehouses } from "../../api/masterData";
import type { Location, Warehouse } from "../../types/inventory";
import type { WorkOrderItem } from "../../types/manufacturing";

const viewState = ref<ViewState>("loading");
const errorMessage = ref("");

const receiptList = ref<FinishedGoodsReceiptItem[]>([]);
const total = ref(0);
const workOrders = ref<WorkOrderItem[]>([]);
const warehouses = ref<Warehouse[]>([]);
const locations = ref<Location[]>([]);
const queryParams = reactive({
  page: 1,
  size: 10,
  keyword: "",
  status: "",
});

const columns: TableColumn[] = [
  { key: "receiptNo", label: "入库单号", width: "180px" },
  { key: "workOrderId", label: "来源工单 / 产品", minWidth: "200px" },
  { key: "receiptQty", label: "入库数量", width: "120px", align: "right" },
  { key: "warehouseId", label: "目标成品库位", minWidth: "180px" },
  { key: "status", label: "入库状态", width: "120px", align: "center" },
  { key: "inventoryTransactionId", label: "库存流水引用", width: "160px" },
  { key: "actions", label: "操作", width: "110px", align: "center" },
];

const createModalVisible = ref(false);
const isSubmitting = ref(false);
const createForm = reactive<FinishedGoodsReceiptCreateRequest>({
  workOrderId: "",
  receiptQty: "",
  warehouseId: "",
  locationId: "",
});

const confirmDialog = reactive({
  visible: false,
  loading: false,
  item: null as FinishedGoodsReceiptItem | null,
});

function isActionAllowed(item: FinishedGoodsReceiptItem, action: string): boolean {
  if (!item.allowedActions || item.allowedActions.length === 0) return true;
  const match = item.allowedActions.find((a) => a.action === action);
  return match ? match.enabled : true;
}

function getActionDisabledReason(item: FinishedGoodsReceiptItem, action: string): string | undefined {
  const match = item.allowedActions?.find((a) => a.action === action);
  return match && !match.enabled ? match.reason : undefined;
}

async function fetchReceiptList() {
  if (!createForm.workOrderId) {
    receiptList.value = [];
    total.value = 0;
    viewState.value = "empty";
    return;
  }
  viewState.value = "loading";
  errorMessage.value = "";
  try {
    const res = await getFinishedGoodsReceipts(createForm.workOrderId);
    if (res.data) {
      let list = res.data || [];
      if (queryParams.keyword.trim()) {
        const kw = queryParams.keyword.toLowerCase();
        list = list.filter(
          (r) =>
            r.receiptNo.toLowerCase().includes(kw) ||
            r.workOrderNo?.toLowerCase().includes(kw) ||
            r.productName?.toLowerCase().includes(kw)
        );
      }
      receiptList.value = list;
      total.value = list.length;
      viewState.value = receiptList.value.length === 0 ? "empty" : "ready";
    }
  } catch (err: any) {
    errorMessage.value = err.message || "请求成品入库列表失败";
    viewState.value = "error";
  }
}

function handleSearch() {
  queryParams.page = 1;
  fetchReceiptList();
}

function handleReset() {
  queryParams.keyword = "";
  queryParams.status = "";
  queryParams.page = 1;
  fetchReceiptList();
}

function handlePageChange(page: number) {
  queryParams.page = page;
  fetchReceiptList();
}

function openCreateModal() {
  createForm.workOrderId = "";
  createForm.receiptQty = "";
  createForm.warehouseId = "";
  createForm.locationId = "";
  createModalVisible.value = true;
}

async function submitCreateReceipt() {
  if (!createForm.workOrderId || !createForm.receiptQty || !createForm.warehouseId || !createForm.locationId) return;
  isSubmitting.value = true;
  try {
    await createFinishedGoodsReceipt(createForm);
    createModalVisible.value = false;
    await fetchReceiptList();
  } catch (err: any) {
    alert(`创建入库单失败：${err.message}`);
  } finally {
    isSubmitting.value = false;
  }
}

function promptConfirm(item: FinishedGoodsReceiptItem) {
  confirmDialog.item = item;
  confirmDialog.visible = true;
}

async function handleExecuteConfirm() {
  if (!confirmDialog.item) return;
  confirmDialog.loading = true;
  try {
    await confirmFinishedGoodsReceipt(confirmDialog.item.id as string);
    confirmDialog.visible = false;
    await fetchReceiptList();
  } catch (err: any) {
    alert(`确认入库失败：${err.message}`);
  } finally {
    confirmDialog.loading = false;
  }
}

onMounted(() => {
  loadMasterData();
});

/** 加载 FGR 创建和查询所需真实 UUID，并按工单作为后端必填查询条件。 */
async function loadMasterData() {
  try {
    const [workOrderRes, warehouseRes] = await Promise.all([
      getWorkOrders({ page: 1, size: 200 }),
      getWarehouses({ page: 1, size: 200, status: "ACTIVE" }),
    ]);
    workOrders.value = workOrderRes.data.records || [];
    warehouses.value = warehouseRes.data.records || [];
  } catch (err: any) {
    errorMessage.value = err?.message || "加载成品入库主数据失败";
    viewState.value = "error";
  }
}

/** 按已选仓库读取真实库位 UUID，避免仓库与库位跨仓引用。 */
async function loadLocations() {
  createForm.locationId = "";
  if (!createForm.warehouseId) {
    locations.value = [];
    return;
  }
  const response = await getLocations({ warehouseId: createForm.warehouseId, page: 1, size: 200, status: "ACTIVE" });
  locations.value = response.data.records || [];
}
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

.wo-cell, .location-cell {
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.product-name, .loc-code {
  font-size: 11px;
}

.wh-name {
  color: #f1f5f9;
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
  max-width: 520px;
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

.modal-hint {
  margin: 0;
  font-size: 12px;
  color: #94a3b8;
  background: rgba(30, 41, 59, 0.5);
  padding: 8px 12px;
  border-radius: 6px;
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
