<template>
  <div class="manufacturing-view-container">
    <!-- 统一页面头部 -->
    <PageHeader
      title="生产领料与退料协同 (Material Movement)"
      tag="MES / INVENTORY BRIDGE"
      description="连接制造执行与仓储库存的物料交接业务。领料确认触发实物库存扣减；退料确认增加退回库位库存并生成流水软引用。"
    >
      <template #actions>
        <button v-if="activeTab === 'issue'" type="button" class="btn btn-primary" @click="openCreateIssueModal">
          <span class="btn-icon">＋</span>
          <span>新建领料申请单</span>
        </button>
        <button v-else type="button" class="btn btn-primary" @click="openCreateReturnModal">
          <span class="btn-icon">＋</span>
          <span>新建生产退料单</span>
        </button>
      </template>
    </PageHeader>

    <!-- 顶部单据类型切换 Tab -->
    <div class="movement-nav-tabs">
      <button
        type="button"
        class="nav-tab-item"
        :class="{ 'is-active': activeTab === 'issue' }"
        @click="switchTab('issue')"
      >
        <span>生产领料单 (Material Issues)</span>
        <span class="tab-count-badge">{{ issueList.length }}</span>
      </button>
      <button
        type="button"
        class="nav-tab-item"
        :class="{ 'is-active': activeTab === 'return' }"
        @click="switchTab('return')"
      >
        <span>生产退料单 (Material Returns)</span>
        <span class="tab-count-badge">{{ returnList.length }}</span>
      </button>
    </div>

    <!-- 统一筛选栏 -->
    <FilterBar
      v-model="keyword"
      placeholder="搜索单据编号或关联工单号..."
      @search="handleSearch"
      @reset="handleReset"
    >
      <div class="filter-select-group">
        <label class="filter-label">状态：</label>
        <select v-model="statusFilter" class="filter-select" @change="handleSearch">
          <option value="">全部状态</option>
          <option value="Draft">草稿待确认 (Draft)</option>
          <option value="Confirmed">已出入库确认 (Confirmed)</option>
        </select>
      </div>
    </FilterBar>

    <!-- 错误异常提示 -->
    <ErrorState
      v-if="viewState === 'error'"
      title="领退料单据加载失败"
      :message="errorMessage"
      @retry="loadData"
    />

    <!-- 数据表格：领料单 -->
    <DataTable
      v-else-if="activeTab === 'issue'"
      :columns="issueColumns"
      :data="filteredIssueList"
      :loading="viewState === 'loading'"
      empty-text="暂无生产领料单记录"
    >
      <!-- 领料单号 -->
      <template #issueNo="{ row }">
        <span class="font-mono highlight-code">{{ row.issueNo }}</span>
      </template>

      <!-- 关联工单 -->
      <template #workOrderId="{ row }">
        <span class="font-mono text-primary">{{ row.workOrderNo || row.workOrderId }}</span>
      </template>

      <!-- 物料明细概览 -->
      <template #items="{ row }">
        <div class="items-summary">
          <div v-for="item in row.items" :key="item.id" class="item-line">
            <span class="item-name">{{ item.productName || item.productCode || item.productId }}</span>
            <span class="item-qty">
              领用: <QuantityText :value="item.issueQty" :unit="item.uom" />
            </span>
            <span class="item-loc font-mono text-muted">({{ item.warehouseName || item.warehouseId }} / {{ item.locationCode || item.locationId }})</span>
          </div>
        </div>
      </template>

      <!-- 状态 -->
      <template #status="{ row }">
        <StatusBadge
          :type="row.status === 'Confirmed' ? 'success' : 'warning'"
          :text="row.status === 'Confirmed' ? '已确认出库' : '待出库确认'"
        />
      </template>

      <!-- 库存流水引用 -->
      <template #inventoryTransactionId="{ row }">
        <span v-if="row.inventoryTransactionId" class="font-mono text-muted">
          {{ row.inventoryTransactionId }}
        </span>
        <span v-else class="text-muted font-xs">尚未产生扣减流水</span>
      </template>

      <!-- 操作 (受 allowedActions 约束) -->
      <template #actions="{ row }">
        <div class="action-btn-group">
          <button
            v-if="row.status === 'Draft'"
            type="button"
            class="btn-text text-primary"
            :disabled="!isActionAllowed(row, 'confirm')"
            :title="getActionDisabledReason(row, 'confirm') || '确认领料出库'"
            @click="promptConfirmIssue(row)"
          >
            出库确认
          </button>
          <span v-else class="text-muted font-xs">已完成扣减</span>
        </div>
      </template>
    </DataTable>

    <!-- 数据表格：退料单 -->
    <DataTable
      v-else
      :columns="returnColumns"
      :data="filteredReturnList"
      :loading="viewState === 'loading'"
      empty-text="暂无生产退料单记录"
    >
      <!-- 退料单号 -->
      <template #returnNo="{ row }">
        <span class="font-mono highlight-code">{{ row.returnNo }}</span>
      </template>

      <!-- 关联工单 -->
      <template #workOrderId="{ row }">
        <span class="font-mono text-primary">{{ row.workOrderNo || row.workOrderId }}</span>
      </template>

      <!-- 物料明细概览 -->
      <template #items="{ row }">
        <div class="items-summary">
          <div v-for="item in row.items" :key="item.id" class="item-line">
            <span class="item-name">{{ item.productName || item.productCode || item.productId }}</span>
            <span class="item-qty">
              退料: <QuantityText :value="item.returnQty" :unit="item.uom" />
            </span>
            <span class="item-loc font-mono text-muted">({{ item.warehouseName || item.warehouseId }} / {{ item.locationCode || item.locationId }})</span>
          </div>
        </div>
      </template>

      <!-- 状态 -->
      <template #status="{ row }">
        <StatusBadge
          :type="row.status === 'Confirmed' ? 'success' : 'warning'"
          :text="row.status === 'Confirmed' ? '已确认退库' : '待退库确认'"
        />
      </template>

      <!-- 库存流水引用 -->
      <template #inventoryTransactionId="{ row }">
        <span v-if="row.inventoryTransactionId" class="font-mono text-muted">
          {{ row.inventoryTransactionId }}
        </span>
        <span v-else class="text-muted font-xs">尚未产生退库流水</span>
      </template>

      <!-- 操作 (受 allowedActions 约束) -->
      <template #actions="{ row }">
        <div class="action-btn-group">
          <button
            v-if="row.status === 'Draft'"
            type="button"
            class="btn-text text-primary"
            :disabled="!isActionAllowed(row, 'confirm')"
            :title="getActionDisabledReason(row, 'confirm') || '确认退料入库'"
            @click="promptConfirmReturn(row)"
          >
            退库确认
          </button>
          <span v-else class="text-muted font-xs">已完成退入</span>
        </div>
      </template>
    </DataTable>

    <!-- 弹窗 1：新建领料单 -->
    <div v-if="createIssueModalVisible" class="modal-mask" @click.self="createIssueModalVisible = false">
      <div class="modal-card modal-large">
        <div class="modal-header">
          <h3 class="modal-title">新建生产领料申请单</h3>
          <button type="button" class="btn-close" @click="createIssueModalVisible = false">✕</button>
        </div>
        <form class="modal-body" @submit.prevent="submitCreateIssue">
          <div class="form-item">
            <label>关联工单编号/ID <span class="req">*</span></label>
            <input
              v-model="issueForm.workOrderId"
              type="text"
              class="form-input font-mono"
              placeholder="例如 wo-001 或 WO-20260901-001"
              required
            />
          </div>

          <div class="form-section">
            <label class="section-title">领料明细项</label>
            <div class="grid-form-row">
              <input
                v-model="issueForm.productId"
                type="text"
                class="form-input"
                placeholder="原料物料 ID (如 raw-01)"
                required
              />
              <input
                v-model="issueForm.issueQty"
                type="text"
                class="form-input font-mono"
                placeholder="领料数量 (如 100.00)"
                required
              />
              <input
                v-model="issueForm.warehouseId"
                type="text"
                class="form-input"
                placeholder="来源仓库 ID (wh-raw)"
                required
              />
              <input
                v-model="issueForm.locationId"
                type="text"
                class="form-input font-mono"
                placeholder="库位 ID (loc-a-01)"
                required
              />
            </div>
          </div>

          <div class="modal-footer">
            <button type="button" class="btn btn-secondary" @click="createIssueModalVisible = false">取消</button>
            <button type="submit" class="btn btn-primary" :disabled="isSubmitting">保存领料单 (草稿)</button>
          </div>
        </form>
      </div>
    </div>

    <!-- 弹窗 2：新建退料单 -->
    <div v-if="createReturnModalVisible" class="modal-mask" @click.self="createReturnModalVisible = false">
      <div class="modal-card modal-large">
        <div class="modal-header">
          <h3 class="modal-title">新建生产退料单</h3>
          <button type="button" class="btn-close" @click="createReturnModalVisible = false">✕</button>
        </div>
        <form class="modal-body" @submit.prevent="submitCreateReturn">
          <div class="form-item">
            <label>关联工单编号/ID <span class="req">*</span></label>
            <input
              v-model="returnForm.workOrderId"
              type="text"
              class="form-input font-mono"
              placeholder="例如 wo-001"
              required
            />
          </div>

          <div class="form-section">
            <label class="section-title">退料明细项</label>
            <div class="grid-form-row">
              <input
                v-model="returnForm.productId"
                type="text"
                class="form-input"
                placeholder="退回物料 ID (如 raw-01)"
                required
              />
              <input
                v-model="returnForm.returnQty"
                type="text"
                class="form-input font-mono"
                placeholder="退料数量 (如 2.00)"
                required
              />
              <input
                v-model="returnForm.warehouseId"
                type="text"
                class="form-input"
                placeholder="退回仓库 ID (wh-raw)"
                required
              />
              <input
                v-model="returnForm.locationId"
                type="text"
                class="form-input font-mono"
                placeholder="目标库位 ID (loc-a-01)"
                required
              />
            </div>
          </div>

          <div class="modal-footer">
            <button type="button" class="btn btn-secondary" @click="createReturnModalVisible = false">取消</button>
            <button type="submit" class="btn btn-primary" :disabled="isSubmitting">保存退料单 (草稿)</button>
          </div>
        </form>
      </div>
    </div>

    <!-- 二次确认对话框 -->
    <ConfirmDialog
      v-model:visible="confirmDialog.visible"
      :title="confirmDialog.title"
      :message="confirmDialog.message"
      :loading="confirmDialog.loading"
      @confirm="handleExecuteConfirm"
    />
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, computed, onMounted } from "vue";
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
  MaterialIssueItem,
  MaterialReturnItem,
} from "../../types/manufacturing";
import {
  getMaterialIssues,
  createMaterialIssue,
  confirmMaterialIssue,
  getMaterialReturns,
  createMaterialReturn,
  confirmMaterialReturn,
} from "../../api/manufacturing";

const viewState = ref<ViewState>("loading");
const errorMessage = ref("");
const activeTab = ref<"issue" | "return">("issue");

const keyword = ref("");
const statusFilter = ref("");

const issueList = ref<MaterialIssueItem[]>([]);
const returnList = ref<MaterialReturnItem[]>([]);

const issueColumns: TableColumn[] = [
  { key: "issueNo", label: "领料单号", width: "180px" },
  { key: "workOrderId", label: "关联工单", width: "180px" },
  { key: "items", label: "物料用量与库位明细", minWidth: "260px" },
  { key: "status", label: "状态", width: "120px", align: "center" },
  { key: "inventoryTransactionId", label: "库存流水", width: "160px" },
  { key: "actions", label: "操作", width: "110px", align: "center" },
];

const returnColumns: TableColumn[] = [
  { key: "returnNo", label: "退料单号", width: "180px" },
  { key: "workOrderId", label: "关联工单", width: "180px" },
  { key: "items", label: "退料明细与退库位", minWidth: "260px" },
  { key: "status", label: "状态", width: "120px", align: "center" },
  { key: "inventoryTransactionId", label: "库存流水", width: "160px" },
  { key: "actions", label: "操作", width: "110px", align: "center" },
];

const filteredIssueList = computed(() => {
  let list = issueList.value;
  if (keyword.value.trim()) {
    const kw = keyword.value.toLowerCase();
    list = list.filter(
      (m) =>
        m.issueNo.toLowerCase().includes(kw) ||
        m.workOrderNo?.toLowerCase().includes(kw)
    );
  }
  if (statusFilter.value) {
    list = list.filter((m) => m.status === statusFilter.value);
  }
  return list;
});

const filteredReturnList = computed(() => {
  let list = returnList.value;
  if (keyword.value.trim()) {
    const kw = keyword.value.toLowerCase();
    list = list.filter(
      (r) =>
        r.returnNo.toLowerCase().includes(kw) ||
        r.workOrderNo?.toLowerCase().includes(kw)
    );
  }
  if (statusFilter.value) {
    list = list.filter((r) => r.status === statusFilter.value);
  }
  return list;
});

const createIssueModalVisible = ref(false);
const issueForm = reactive({
  workOrderId: "wo-001",
  productId: "raw-01",
  issueQty: "50.00",
  warehouseId: "wh-raw",
  locationId: "loc-a-01",
});

const createReturnModalVisible = ref(false);
const returnForm = reactive({
  workOrderId: "wo-001",
  productId: "raw-01",
  returnQty: "2.00",
  warehouseId: "wh-raw",
  locationId: "loc-a-01",
});

const isSubmitting = ref(false);

const confirmDialog = reactive({
  visible: false,
  loading: false,
  title: "",
  message: "",
  type: "" as "issue" | "return",
  targetId: "",
});

function isActionAllowed(item: any, action: string): boolean {
  if (!item.allowedActions || item.allowedActions.length === 0) return true;
  const match = item.allowedActions.find((a: any) => a.action === action);
  return match ? match.enabled : true;
}

function getActionDisabledReason(item: any, action: string): string | undefined {
  const match = item.allowedActions?.find((a: any) => a.action === action);
  return match && !match.enabled ? match.reason : undefined;
}

async function loadData() {
  viewState.value = "loading";
  errorMessage.value = "";
  try {
    const [iRes, rRes] = await Promise.all([
      getMaterialIssues(),
      getMaterialReturns(),
    ]);
    issueList.value = iRes.data?.records || [];
    returnList.value = rRes.data?.records || [];
    viewState.value = "ready";
  } catch (err: any) {
    errorMessage.value = err.message || "请求领退料单据失败";
    viewState.value = "error";
  }
}

function switchTab(tab: "issue" | "return") {
  activeTab.value = tab;
}

function handleSearch() {
  // filtered by computed
}

function handleReset() {
  keyword.value = "";
  statusFilter.value = "";
}

function openCreateIssueModal() {
  issueForm.workOrderId = "wo-001";
  issueForm.productId = "raw-01";
  issueForm.issueQty = "50.00";
  issueForm.warehouseId = "wh-raw";
  issueForm.locationId = "loc-a-01";
  createIssueModalVisible.value = true;
}

async function submitCreateIssue() {
  if (!issueForm.workOrderId || !issueForm.issueQty) return;
  isSubmitting.value = true;
  try {
    await createMaterialIssue({
      workOrderId: issueForm.workOrderId,
      items: [
        {
          productId: issueForm.productId,
          warehouseId: issueForm.warehouseId,
          locationId: issueForm.locationId,
          issueQty: issueForm.issueQty,
        },
      ],
    });
    createIssueModalVisible.value = false;
    await loadData();
  } catch (err: any) {
    alert(`创建领料单失败：${err.message}`);
  } finally {
    isSubmitting.value = false;
  }
}

function openCreateReturnModal() {
  returnForm.workOrderId = "wo-001";
  returnForm.productId = "raw-01";
  returnForm.returnQty = "2.00";
  returnForm.warehouseId = "wh-raw";
  returnForm.locationId = "loc-a-01";
  createReturnModalVisible.value = true;
}

async function submitCreateReturn() {
  if (!returnForm.workOrderId || !returnForm.returnQty) return;
  isSubmitting.value = true;
  try {
    await createMaterialReturn({
      workOrderId: returnForm.workOrderId,
      items: [
        {
          productId: returnForm.productId,
          warehouseId: returnForm.warehouseId,
          locationId: returnForm.locationId,
          returnQty: returnForm.returnQty,
        },
      ],
    });
    createReturnModalVisible.value = false;
    await loadData();
  } catch (err: any) {
    alert(`创建退料单失败：${err.message}`);
  } finally {
    isSubmitting.value = false;
  }
}

function promptConfirmIssue(item: MaterialIssueItem) {
  confirmDialog.title = "确认生产领料出库";
  confirmDialog.message = `确认领料单【${item.issueNo}】出库？库存服务将真实扣减原料库位库存并生成流水软引用。`;
  confirmDialog.type = "issue";
  confirmDialog.targetId = item.id as string;
  confirmDialog.visible = true;
}

function promptConfirmReturn(item: MaterialReturnItem) {
  confirmDialog.title = "确认生产退料入库";
  confirmDialog.message = `确认退料单【${item.returnNo}】入库？将增加目标库位实物库存并生成流水软引用。`;
  confirmDialog.type = "return";
  confirmDialog.targetId = item.id as string;
  confirmDialog.visible = true;
}

async function handleExecuteConfirm() {
  confirmDialog.loading = true;
  try {
    if (confirmDialog.type === "issue") {
      await confirmMaterialIssue(confirmDialog.targetId);
    } else {
      await confirmMaterialReturn(confirmDialog.targetId);
    }
    confirmDialog.visible = false;
    await loadData();
  } catch (err: any) {
    alert(`确认失败：${err.message}`);
  } finally {
    confirmDialog.loading = false;
  }
}

onMounted(() => {
  loadData();
});
</script>

<style scoped>
.manufacturing-view-container {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.movement-nav-tabs {
  display: flex;
  align-items: center;
  gap: 12px;
  background: rgba(15, 23, 42, 0.7);
  border: 1px solid rgba(255, 255, 255, 0.08);
  border-radius: 8px;
  padding: 6px;
}

.nav-tab-item {
  display: flex;
  align-items: center;
  gap: 8px;
  background: transparent;
  border: none;
  color: #94a3b8;
  padding: 8px 16px;
  border-radius: 6px;
  font-size: 13px;
  font-weight: 500;
  cursor: pointer;
  transition: all 0.2s;
}

.nav-tab-item:hover {
  color: #f8fafc;
  background: rgba(255, 255, 255, 0.05);
}

.nav-tab-item.is-active {
  background: #0284c7;
  color: #ffffff;
}

.tab-count-badge {
  background: rgba(0, 0, 0, 0.25);
  font-size: 11px;
  font-family: var(--font-mono, monospace);
  padding: 1px 6px;
  border-radius: 10px;
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

.items-summary {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.item-line {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 12px;
}

.item-name {
  color: #f1f5f9;
}

.item-qty {
  color: #cbd5e1;
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

.modal-large { max-width: 680px; }

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

.form-item {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.form-item label, .section-title {
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

.form-section {
  display: flex;
  flex-direction: column;
  gap: 8px;
  border-top: 1px dashed rgba(255, 255, 255, 0.1);
  padding-top: 12px;
}

.grid-form-row {
  display: grid;
  grid-template-columns: 2fr 1.5fr 1.5fr 1.5fr;
  gap: 8px;
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
