<template>
  <div class="transfer-list-view">
    <!-- 统一页面头部 -->
    <PageHeader
      title="库位调拨管理"
      tag="CORE / INVENTORY / TRANSFER"
      description="在同一事务中扣减来源库位、增加目标库位，企业总实物库存保持不变。来源库位可用库存必须充足，调拨失败严禁产生单边库存事实。"
    >
      <template #actions>
        <button type="button" class="btn-primary" @click="openCreateModal">
          <span>＋ 发起库位调拨</span>
        </button>
      </template>
    </PageHeader>

    <!-- 筛选搜索栏 -->
    <FilterBar
      v-model="queryParams.keyword"
      placeholder="搜索调拨单号、物料编码或名称..."
      @search="fetchTransfers"
      @reset="resetFilter"
    >
      <select v-model="queryParams.status" class="filter-select" @change="fetchTransfers">
        <option value="">全部调拨状态</option>
        <option value="Draft">待确认执行 (Draft)</option>
        <option value="Confirmed">已确认完成 (Confirmed)</option>
      </select>
    </FilterBar>

    <!-- 四态展示 -->
    <ErrorState
      v-if="viewState === 'error'"
      title="获取调拨列表异常"
      :message="errorMessage"
      @retry="fetchTransfers"
    />

    <EmptyState
      v-else-if="viewState === 'empty'"
      title="暂无调拨单记录"
      description="当前未查询到任何库位调拨单据，您可以点击右上角发起新的调拨任务。"
    >
      <template #action>
        <button type="button" class="btn-create-sm" @click="openCreateModal">
          立即发起调拨
        </button>
      </template>
    </EmptyState>

    <DataTable
      v-else
      :columns="columns"
      :data="transferList"
      :loading="viewState === 'loading'"
      :total="totalCount"
      :page="queryParams.page"
      :size="queryParams.size"
      @page-change="handlePageChange"
    >
      <!-- 调拨编号 -->
      <template #transferNo="{ value }">
        <span class="mono-code">{{ value }}</span>
      </template>

      <!-- 来源库位 -->
      <template #fromLocation="{ row }">
        <span class="loc-code">{{ row.fromLocationCode }}</span>
        <span class="loc-sub">({{ row.fromWarehouseName }})</span>
      </template>

      <!-- 目标库位 -->
      <template #toLocation="{ row }">
        <span class="loc-code">{{ row.toLocationCode }}</span>
        <span class="loc-sub">({{ row.toWarehouseName }})</span>
      </template>

      <!-- 调拨数量 -->
      <template #qty="{ row }">
        <QuantityText :value="row.qty" :unit="row.uom" />
      </template>

      <!-- 状态 -->
      <template #status="{ value }">
        <StatusBadge
          :type="value === 'Confirmed' ? 'success' : 'warning'"
          :text="value === 'Confirmed' ? '已确认完成' : '待执行确认'"
        />
      </template>

      <!-- 操作列 -->
      <template #actions="{ row }">
        <div class="table-actions">
          <button type="button" class="btn-link" @click="openDetailModal(row)">
            详情
          </button>
          <button
            v-if="row.status === 'Draft'"
            type="button"
            class="btn-link confirm-link"
            @click="openDetailModal(row)"
          >
            确认执行
          </button>
        </div>
      </template>
    </DataTable>

    <!-- 调拨单详情与执行确认弹窗 -->
    <TransferDetailView
      v-model:visible="isDetailVisible"
      :transfer="selectedTransfer"
      :confirming="isConfirming"
      @confirm="handleConfirmTransfer"
      @close="isDetailVisible = false"
    />

    <!-- 新建调拨单弹窗 -->
    <div v-if="isCreateVisible" class="modal-mask" @click.self="isCreateVisible = false">
      <div class="modal-panel">
        <div class="modal-header">
          <h3 class="modal-title">发起库位调拨</h3>
          <button type="button" class="btn-close" @click="isCreateVisible = false">✕</button>
        </div>
        <form class="modal-body" @submit.prevent="handleCreateSubmit">
          <div class="form-item">
            <label>物料 <span class="req">*</span></label>
            <select v-model="createForm.productId" class="form-select" required>
              <option value="3">RM-SERVO-ST (定子转子组件)</option>
              <option value="1">FG-SERVO-01 (伺服电机总成)</option>
              <option value="4">RM-BEARING-01 (高精轴承组件)</option>
            </select>
          </div>
          <div class="form-row">
            <div class="form-item">
              <label>来源库位 <span class="req">*</span></label>
              <select v-model="createForm.fromLocationId" class="form-select" required>
                <option value="3">ST-A-01 (原料存储位 - 可用 150)</option>
                <option value="2">RS-01 (收货暂存位 - 可用 70)</option>
                <option value="6">FG-A-01 (成品存储位 - 可用 400)</option>
              </select>
            </div>
            <div class="form-item">
              <label>目标库位 <span class="req">*</span></label>
              <select v-model="createForm.toLocationId" class="form-select" required>
                <option value="5">PK-01 (拣货备料位)</option>
                <option value="4">ST-B-02 (标准件存储位)</option>
                <option value="7">SHP-01 (发货暂存位)</option>
              </select>
            </div>
          </div>
          <div class="form-item">
            <label>调拨数量 <span class="req">*</span></label>
            <input v-model="createForm.qty" type="text" class="form-input" required placeholder="如: 30" />
          </div>
          <div class="form-item">
            <label>调拨原因</label>
            <textarea v-model="createForm.reason" class="form-textarea" rows="2" placeholder="填写车间备料或移位原因..."></textarea>
          </div>
          <div class="modal-footer">
            <button type="button" class="btn-secondary" @click="isCreateVisible = false">取消</button>
            <button type="submit" class="btn-primary" :disabled="isSubmitting">
              {{ isSubmitting ? '创建中...' : '提交调拨单' }}
            </button>
          </div>
        </form>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
/**
 * 库位调拨管理列表视图 (TransferListView)
 * 职责：展示调拨单据，支持发起新调拨与查看详情/确认执行
 */
import { ref, reactive, onMounted } from "vue";
import PageHeader from "@/components/common/PageHeader.vue";
import FilterBar from "@/components/common/FilterBar.vue";
import DataTable, { type TableColumn } from "@/components/common/DataTable.vue";
import StatusBadge from "@/components/common/StatusBadge.vue";
import QuantityText from "@/components/common/QuantityText.vue";
import EmptyState from "@/components/common/EmptyState.vue";
import ErrorState from "@/components/common/ErrorState.vue";
import TransferDetailView from "./TransferDetailView.vue";
import type { ViewState } from "@/types/common";
import type { TransferOrder } from "@/types/inventory";
import { getTransfers, createTransfer, confirmTransfer } from "@/api/inventory";

const viewState = ref<ViewState>("loading");
const errorMessage = ref("");
const transferList = ref<TransferOrder[]>([]);
const totalCount = ref(0);

const queryParams = reactive({
  page: 1,
  size: 10,
  keyword: "",
  status: "",
});

const columns: TableColumn[] = [
  { key: "transferNo", label: "调拨编号", width: "150px" },
  { key: "sku", label: "物料编码", width: "130px" },
  { key: "productName", label: "物料名称", minWidth: "140px" },
  { key: "fromLocation", label: "来源库位", width: "160px" },
  { key: "toLocation", label: "目标库位", width: "160px" },
  { key: "qty", label: "调拨数量", width: "110px", align: "right" },
  { key: "status", label: "状态", width: "110px", align: "center" },
  { key: "createdAt", label: "创建时间", width: "160px" },
  { key: "actions", label: "操作", width: "130px", align: "center" },
];

const isDetailVisible = ref(false);
const selectedTransfer = ref<TransferOrder | null>(null);
const isConfirming = ref(false);

const isCreateVisible = ref(false);
const isSubmitting = ref(false);
const createForm = reactive({
  productId: "3",
  fromWarehouseId: "1",
  fromLocationId: "3",
  toWarehouseId: "1",
  toLocationId: "5",
  qty: "30",
  reason: "车间生产急需备料至拣选位",
});

async function fetchTransfers() {
  viewState.value = "loading";
  errorMessage.value = "";
  try {
    const res = await getTransfers({
      page: queryParams.page,
      size: queryParams.size,
      status: queryParams.status,
    });
    transferList.value = res.data.records;
    totalCount.value = res.data.total;
    viewState.value = transferList.value.length === 0 ? "empty" : "ready";
  } catch (err: any) {
    console.error("[TransferListView] 获取调拨失败:", err);
    errorMessage.value = err?.message || "网络请求异常";
    viewState.value = "error";
  }
}

function handlePageChange(page: number) {
  queryParams.page = page;
  fetchTransfers();
}

function resetFilter() {
  queryParams.keyword = "";
  queryParams.status = "";
  queryParams.page = 1;
  fetchTransfers();
}

function openDetailModal(row: TransferOrder) {
  selectedTransfer.value = row;
  isDetailVisible.value = true;
}

async function handleConfirmTransfer(id: string | number) {
  isConfirming.value = true;
  try {
    const res = await confirmTransfer(id);
    selectedTransfer.value = res.data;
    await fetchTransfers();
    isDetailVisible.value = false;
  } catch (err: any) {
    alert(err?.message || "确认失败");
  } finally {
    isConfirming.value = false;
  }
}

function openCreateModal() {
  isCreateVisible.value = true;
}

async function handleCreateSubmit() {
  isSubmitting.value = true;
  try {
    await createTransfer({
      productId: createForm.productId,
      fromWarehouseId: createForm.fromWarehouseId,
      fromLocationId: createForm.fromLocationId,
      toWarehouseId: createForm.toWarehouseId,
      toLocationId: createForm.toLocationId,
      qty: createForm.qty,
      reason: createForm.reason,
    });
    isCreateVisible.value = false;
    await fetchTransfers();
  } catch (err: any) {
    alert(err?.message || "创建调拨失败");
  } finally {
    isSubmitting.value = false;
  }
}

onMounted(() => {
  fetchTransfers();
});
</script>

<style scoped>
.transfer-list-view {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.mono-code {
  font-family: var(--font-mono, monospace);
  font-size: 12px;
  color: #38bdf8;
}

.loc-code {
  font-family: var(--font-mono, monospace);
  font-size: 12px;
  color: #f1f5f9;
}

.loc-sub {
  font-size: 11px;
  color: #64748b;
  margin-left: 4px;
}

.table-actions {
  display: flex;
  align-items: center;
  gap: 8px;
  justify-content: center;
}

.btn-link {
  background: transparent;
  border: none;
  color: #38bdf8;
  font-size: 12px;
  cursor: pointer;
  padding: 2px 6px;
}

.confirm-link {
  color: #34d399;
}

.btn-primary {
  padding: 8px 16px;
  background: #0284c7;
  color: #ffffff;
  border: 1px solid #0369a1;
  border-radius: 6px;
  font-size: 13px;
  font-weight: 500;
  cursor: pointer;
}

.btn-secondary {
  padding: 8px 16px;
  background: rgba(51, 65, 85, 0.6);
  color: #cbd5e1;
  border: 1px solid rgba(255, 255, 255, 0.1);
  border-radius: 6px;
  font-size: 13px;
  cursor: pointer;
}

.btn-create-sm {
  padding: 6px 14px;
  background: #0284c7;
  border: none;
  border-radius: 4px;
  color: #fff;
  font-size: 12px;
  cursor: pointer;
}

.filter-select {
  background: rgba(30, 41, 59, 0.8);
  border: 1px solid rgba(255, 255, 255, 0.12);
  color: #f8fafc;
  padding: 7px 12px;
  border-radius: 6px;
  font-size: 13px;
  outline: none;
}

/* 模态弹窗 */
.modal-mask {
  position: fixed;
  inset: 0;
  background: rgba(0, 0, 0, 0.7);
  backdrop-filter: blur(4px);
  display: flex;
  align-items: center;
  justify-content: center;
  z-index: 1000;
  padding: 16px;
}

.modal-panel {
  background: #0f172a;
  border: 1px solid rgba(255, 255, 255, 0.15);
  border-radius: 10px;
  width: 100%;
  max-width: 520px;
  overflow: hidden;
  box-shadow: 0 20px 25px -5px rgba(0, 0, 0, 0.5);
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

.btn-close {
  background: transparent;
  border: none;
  color: #94a3b8;
  font-size: 16px;
  cursor: pointer;
}

.modal-body {
  padding: 20px;
  display: flex;
  flex-direction: column;
  gap: 14px;
}

.form-row {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 12px;
}

.form-item {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

label {
  font-size: 12px;
  color: #94a3b8;
}

.req {
  color: #f87171;
}

.form-input,
.form-select,
.form-textarea {
  background: rgba(30, 41, 59, 0.8);
  border: 1px solid rgba(255, 255, 255, 0.12);
  color: #f8fafc;
  padding: 7px 10px;
  border-radius: 6px;
  font-size: 13px;
  outline: none;
}

.modal-footer {
  display: flex;
  align-items: center;
  justify-content: flex-end;
  gap: 12px;
  padding-top: 10px;
}
</style>
