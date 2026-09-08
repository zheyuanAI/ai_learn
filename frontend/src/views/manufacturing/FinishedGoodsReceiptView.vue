<template>
  <div class="manufacturing-view-container">
    <CommandFeedback :error="lastError" :can-retry="canRetry" :executing="isExecuting" @retry="retry" />
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
        <label class="filter-label">所属工单：</label>
        <select v-model="selectedWorkOrderId" class="filter-select" @change="handleWorkOrderFilterChange">
          <option value="">请选择生产工单</option>
          <option v-for="wo in workOrders" :key="wo.id" :value="String(wo.id)">
            {{ wo.workOrderNo }} - {{ wo.productName || '工单' }} ({{ wo.status }})
          </option>
        </select>
      </div>
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
      empty-text="暂无匹配的成品入库记录（请确认已选择有效工单）"
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
          <div class="options-search-row">
            <label for="receipt-option-keyword">目录搜索</label>
            <input
              id="receipt-option-keyword"
              v-model="masterDataKeyword"
              type="search"
              class="form-input"
              placeholder="输入工单、仓库或库位编码/名称后回车搜索"
              @keyup.enter="loadMasterData"
            />
            <button type="button" class="btn-text text-primary" @click="loadMasterData">搜索</button>
          </div>
          <div v-if="masterDataLoading" class="options-hint text-muted">⏳ 正在加载真实主数据目录...</div>
          <div v-else-if="masterDataError" class="options-hint text-warning">⚠️ {{ masterDataError }}</div>
          <div class="form-grid two-col">
            <div class="form-item">
              <label>生产工单 <span class="req">*</span></label>
              <select v-model="createForm.workOrderId" class="form-input" required @change="onModalWorkOrderChange">
                <option value="">请选择真实工单</option>
                <option v-for="workOrder in workOrders" :key="workOrder.id" :value="String(workOrder.id)">
                  {{ workOrder.workOrderNo }} - {{ workOrder.productName || '工单' }}
                </option>
              </select>
            </div>
            <div class="form-item">
              <label>成品入库数量 <span class="req">*</span></label>
              <input
                v-model="createForm.receiptQty"
                type="number"
                min="0.01"
                step="0.01"
                :max="maxEligibleQuantity > 0 ? maxEligibleQuantity : undefined"
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
              <select v-model="createForm.locationId" class="form-input" required :disabled="!createForm.warehouseId">
                <option value="">{{ !createForm.warehouseId ? '请先选择仓库' : '请选择库位' }}</option>
                <option v-for="location in locations" :key="location.id" :value="String(location.id)">
                  {{ location.code }} ({{ location.name }})
                </option>
              </select>
            </div>
          </div>

          <p class="modal-hint">
            {{ maxEligibleHint }}
          </p>

          <div class="modal-footer">
            <button type="button" class="btn btn-secondary" @click="createModalVisible = false">取消</button>
            <button
              type="submit"
              class="btn btn-primary"
              :disabled="isSubmitting || !receiptQuantityValid"
              :title="receiptQuantityValid ? '保存入库单 (草稿)' : '入库数量必须在已检验合格且未入库余额内'"
            >
              保存入库单 (草稿)
            </button>
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
import { isActionAllowed as checkAction, getActionDisabledReason as getDisabledReason } from "../../utils/actionGuard";
import type { AllowedAction } from "../../types/common";
import { ref, reactive, computed, onMounted } from "vue";
import { useCommand } from "@/composables/useCommand";
import CommandFeedback from "@/components/common/CommandFeedback.vue";
import { useRoute, useRouter } from "vue-router";
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

const route = useRoute();
const router = useRouter();

const viewState = ref<ViewState>("loading");
const errorMessage = ref("");

const receiptList = ref<FinishedGoodsReceiptItem[]>([]);
const total = ref(0);
const workOrders = ref<WorkOrderItem[]>([]);
const warehouses = ref<Warehouse[]>([]);
const locations = ref<Location[]>([]);
const selectedWorkOrderId = ref("");

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
const { execute, retry, isExecuting, canRetry, lastError } = useCommand();
const isSubmitting = isExecuting;
const createForm = reactive<FinishedGoodsReceiptCreateRequest>({
  workOrderId: "",
  receiptQty: "",
  warehouseId: "",
  locationId: "",
});
const masterDataKeyword = ref("");
const masterDataLoading = ref(false);
const masterDataError = ref("");

const confirmDialog = reactive({
  visible: false,
  loading: false,
  item: null as FinishedGoodsReceiptItem | null,
});

const currentModalWorkOrder = computed(() =>
  workOrders.value.find((w) => String(w.id) === String(createForm.workOrderId))
);

/** 由服务端工单汇总事实计算当前可申报的合格未入库余额。 */
const maxEligibleQuantity = computed(() => {
  if (!currentModalWorkOrder.value) return 0;
  const qual = parseFloat(currentModalWorkOrder.value.qualifiedQty || "0");
  const recv = parseFloat(currentModalWorkOrder.value.receivedQty || "0");
  return Math.max(0, qual - recv);
});

/** 前端只做显式数量闸门，最终仍由服务端按质检事实再次校验。 */
const receiptQuantityValid = computed(() => {
  const requested = parseFloat(createForm.receiptQty || "0");
  return Number.isFinite(requested) && requested > 0 && requested <= maxEligibleQuantity.value;
});

const maxEligibleHint = computed(() => {
  if (!currentModalWorkOrder.value) {
    return "提示：请选择工单，系统将自动核算最大可入库合格量。";
  }
  const wo = currentModalWorkOrder.value;
  return `工单 ${wo.workOrderNo}：累计质检合格 ${wo.qualifiedQty || "0"} 件，已完工入库 ${wo.receivedQty || "0"} 件；当前最大可申报入库量为 ${maxEligibleQuantity.value} 件。`;
});

// 替换为调用 actionGuard 的版本
function isActionAllowed(item: { allowedActions?: AllowedAction[] | null }, action: string): boolean {
  return checkAction(item.allowedActions, action);
}

// 替换为调用 actionGuard 的版本
function getActionDisabledReason(item: { allowedActions?: AllowedAction[] | null }, action: string): string | undefined {
  return getDisabledReason(item.allowedActions, action);
}

async function fetchReceiptList() {
  if (!selectedWorkOrderId.value) {
    receiptList.value = [];
    total.value = 0;
    viewState.value = "empty";
    return;
  }
  viewState.value = "loading";
  errorMessage.value = "";
  try {
    const res = await getFinishedGoodsReceipts(selectedWorkOrderId.value);
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
      if (queryParams.status) {
        list = list.filter((r) => r.status === queryParams.status);
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

function handleWorkOrderFilterChange() {
  fetchReceiptList();
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

async function openCreateModal() {
  createForm.workOrderId = selectedWorkOrderId.value || (workOrders.value[0] ? String(workOrders.value[0].id) : "");
  if (warehouses.value.length > 0) {
    createForm.warehouseId = String(warehouses.value[0].id);
    await loadLocations();
  }
  onModalWorkOrderChange();
  createModalVisible.value = true;
}

function onModalWorkOrderChange() {
  if (!createForm.workOrderId) return;
  const wo = workOrders.value.find((w) => String(w.id) === String(createForm.workOrderId));
  if (wo) {
    const qual = parseFloat(wo.qualifiedQty || "0");
    const recv = parseFloat(wo.receivedQty || "0");
    const diff = qual - recv;
    // 没有后端确认的可入库数量时保持空值，禁止以计划量或固定数量冒充合格事实。
    createForm.receiptQty = diff > 0 ? String(diff) : "";
  }
}

async function submitCreateReceipt() {
  if (!createForm.workOrderId || !receiptQuantityValid.value || !createForm.warehouseId || !createForm.locationId) return;
  try {
    const created = await execute((key) => createFinishedGoodsReceipt(createForm, key), { onConflict: fetchReceiptList });
    if (!created?.data?.id) {
      throw new Error("服务端未返回成品入库单 ID，已阻止继续确认");
    }
    createModalVisible.value = false;
    alert("成品入库申请单创建成功！请在列表点击【确认入库】将货物正式移入库位。");
    selectedWorkOrderId.value = createForm.workOrderId;
    await fetchReceiptList();
  } catch (err: any) {
    alert(`创建入库单失败：${err.message}`);
  } finally { /* useCommand 在 finally 中恢复 isExecuting。 */ }
}

function promptConfirm(item: FinishedGoodsReceiptItem) {
  confirmDialog.item = item;
  confirmDialog.visible = true;
}

async function handleExecuteConfirm() {
  if (!confirmDialog.item) return;
  confirmDialog.loading = true;
  try {
    await execute((key) => confirmFinishedGoodsReceipt(confirmDialog.item!.id as string, key), { onConflict: fetchReceiptList });
    confirmDialog.visible = false;
    await fetchReceiptList();
  } catch (err: any) {
    alert(`确认入库失败：${err.message}`);
  } finally {
    confirmDialog.loading = false;
  }
}

onMounted(async () => {
  await loadMasterData();
  if (route.query.workOrderId) {
    selectedWorkOrderId.value = String(route.query.workOrderId);
  } else if (workOrders.value.length > 0) {
    selectedWorkOrderId.value = String(workOrders.value[0].id);
  }
  if (selectedWorkOrderId.value) {
    await fetchReceiptList();
  }
});

/** 加载 FGR 创建和查询所需真实 UUID，并按工单作为后端必填查询条件。 */
async function loadMasterData() {
  masterDataLoading.value = true;
  masterDataError.value = "";
  try {
    const keyword = masterDataKeyword.value.trim() || undefined;
    const [workOrderRes, warehouseRes] = await Promise.all([
      getWorkOrders({ page: 1, size: 20, workOrderNo: keyword }),
      getWarehouses({ page: 1, size: 20, keyword, status: "ACTIVE" }),
    ]);
    workOrders.value = workOrderRes.data.records || [];
    warehouses.value = warehouseRes.data.records || [];
    if (selectedWorkOrderId.value && !workOrders.value.some((item) => String(item.id) === String(selectedWorkOrderId.value))) {
      selectedWorkOrderId.value = "";
    }
    if (createForm.warehouseId && !warehouses.value.some((item) => String(item.id) === String(createForm.warehouseId))) {
      createForm.warehouseId = "";
      createForm.locationId = "";
      locations.value = [];
    }
  } catch (err: any) {
    errorMessage.value = err?.message || "加载成品入库主数据失败";
    masterDataError.value = errorMessage.value;
    viewState.value = "error";
  } finally {
    masterDataLoading.value = false;
  }
}

/** 按已选仓库读取真实库位 UUID，避免仓库与库位跨仓引用。 */
async function loadLocations() {
  createForm.locationId = "";
  if (!createForm.warehouseId) {
    locations.value = [];
    return;
  }
  const response = await getLocations({
    warehouseId: createForm.warehouseId,
    page: 1,
    size: 20,
    keyword: masterDataKeyword.value.trim() || undefined,
    status: "ACTIVE",
  });
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

.options-search-row {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 8px 10px;
  border: 1px solid rgba(148, 163, 184, 0.18);
  border-radius: 6px;
}

.options-search-row label {
  flex: 0 0 auto;
}

.options-search-row .form-input {
  flex: 1;
}

.options-hint {
  padding: 8px 10px;
  border-radius: 6px;
  background: rgba(30, 41, 59, 0.6);
  font-size: 12px;
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
