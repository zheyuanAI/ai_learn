<template>
  <div class="purchase-order-list-view">
    <!-- 统一页面头部 -->
    <PageHeader
      title="采购入库控制台"
      tag="CORE / PURCHASING / INBOUND"
      description="采购全链路状态流转：未提交 ➔ 已提交 ➔ 已审核 ➔ 部分收货 ➔ 已完成。仓库到货外观验收数量恒等（到货=拒收+实收），拒收数量不入库并保留为待收；实际接收货物全部进入质量隔离位（QualityHold），放行后入暂存位（ReceivingStaging）再上架存储位（Storage）。"
    >
      <template #actions>
        <button type="button" class="btn-primary" @click="isCreateModalOpen = true">
          <span>＋ 新建采购订单</span>
        </button>
      </template>
    </PageHeader>

    <!-- 顶层状态快速筛选标签条 -->
    <div class="status-tabs-row">
      <button
        v-for="st in statusFilters"
        :key="st.value"
        type="button"
        class="status-tab-btn"
        :class="{ 'is-active': queryParams.status === st.value }"
        @click="switchStatusFilter(st.value)"
      >
        <span>{{ st.label }}</span>
      </button>
    </div>

    <!-- 筛选搜索栏 -->
    <FilterBar
      v-model="queryParams.keyword"
      placeholder="搜索采购单号、供应商名称..."
      @search="fetchOrders"
      @reset="resetFilter"
    />

    <!-- 四态展示 -->
    <ErrorState
      v-if="viewState === 'error'"
      title="获取采购单列表失败"
      :message="errorMessage"
      @retry="fetchOrders"
    />

    <EmptyState
      v-else-if="viewState === 'empty'"
      title="暂无采购订单记录"
      description="当前筛选条件下未发现采购订单，您可以点击右上角新建采购订单。"
    >
      <template #action>
        <button type="button" class="btn-create-sm" @click="isCreateModalOpen = true">
          立即新建采购单
        </button>
      </template>
    </EmptyState>

    <DataTable
      v-else
      :columns="columns"
      :data="orderList"
      :loading="viewState === 'loading'"
      :total="totalCount"
      :page="queryParams.page"
      :size="queryParams.size"
      @page-change="handlePageChange"
    >
      <!-- 采购单号 -->
      <template #poNo="{ value }">
        <span class="mono-code">{{ value }}</span>
      </template>

      <!-- 供应商 -->
      <template #supplier="{ row }">
        <div class="supplier-cell">
          <span class="supp-name">{{ row.supplierName }}</span>
          <span class="supp-code">{{ row.supplierCode }}</span>
        </div>
      </template>

      <!-- 状态 -->
      <template #status="{ value, row }">
        <div class="status-cell">
          <StatusBadge
            :type="statusBadgeType(value)"
            :text="statusText(value)"
          />
          <span v-if="row.completionType === 'Manual'" class="tag-manual">人工完成</span>
        </div>
      </template>

      <!-- 明细统计与待收余量 -->
      <template #pendingSummary="{ row }">
        <div class="pending-cell">
          <span v-if="row.lines && row.lines[0]">
            待收: <QuantityText :value="row.lines[0].pendingQty" :unit="row.lines[0].uom" />
          </span>
          <span v-else class="text-muted">-</span>
        </div>
      </template>

      <!-- 操作列 -->
      <template #actions="{ row }">
        <div class="table-actions">
          <button type="button" class="btn-link" @click="openOrderDetail(row)">
            详情
          </button>
          <button
            v-if="row.status === 'Approved' || row.status === 'PartiallyReceived'"
            type="button"
            class="btn-link act-receive"
            @click="openReceiptConfirm(row)"
          >
            验收接收
          </button>
        </div>
      </template>
    </DataTable>

    <!-- 采购详情抽屉 -->
    <PurchaseOrderDetailView
      v-model:visible="isDetailDrawerOpen"
      :order-id="selectedOrderId"
      @refresh="fetchOrders"
      @close="isDetailDrawerOpen = false"
    />

    <!-- 外观验收与接收弹窗 -->
    <ReceiptConfirmView
      v-model:visible="isReceiptModalOpen"
      :order="selectedOrderForReceipt"
      :submitting="isReceiving"
      @confirm="handleConfirmReceipt"
      @close="isReceiptModalOpen = false"
    />

    <!-- 新建采购单弹窗 -->
    <div v-if="isCreateModalOpen" class="modal-mask" @click.self="isCreateModalOpen = false">
      <div class="modal-panel">
        <div class="modal-header">
          <h3 class="modal-title">新建采购订单</h3>
          <button type="button" class="btn-close" @click="isCreateModalOpen = false">✕</button>
        </div>
        <form class="modal-body" @submit.prevent="submitCreateOrder">
          <div class="form-item">
            <label>供应商 <span class="req">*</span></label>
            <select v-model="createForm.supplierId" class="form-select" required>
              <option value="1">华东精密机电制造有限公司 (SUP-HD-001)</option>
              <option value="2">精密轴承制造中心 (SUP-NB-008)</option>
            </select>
          </div>
          <div class="form-row">
            <div class="form-item">
              <label>目标仓库 <span class="req">*</span></label>
              <select v-model="createForm.targetWarehouseId" class="form-select" required>
                <option value="1">原料一仓 (WH-RM-01)</option>
              </select>
            </div>
            <div class="form-item">
              <label>计划到货日期 <span class="req">*</span></label>
              <input v-model="createForm.expectedArrivalDate" type="date" class="form-input" required />
            </div>
          </div>
          <div class="form-item">
            <label>采购商品物料 <span class="req">*</span></label>
            <select v-model="createForm.productId" class="form-select" required>
              <option value="3">RM-SERVO-ST (定子转子组件)</option>
              <option value="4">RM-BEARING-01 (高精轴承组件)</option>
            </select>
          </div>
          <div class="form-item">
            <label>采购数量 <span class="req">*</span></label>
            <input v-model="createForm.orderedQty" type="text" class="form-input" required placeholder="如: 80" />
          </div>
          <div class="form-item">
            <label>关联来源工单 (可选，用于追溯)</label>
            <input v-model="createForm.sourceWorkOrderId" type="text" class="form-input" placeholder="如: WO-20260826-018" />
          </div>
          <div class="modal-footer">
            <button type="button" class="btn-secondary" @click="isCreateModalOpen = false">取消</button>
            <button type="submit" class="btn-primary" :disabled="isCreating">
              {{ isCreating ? '创建中...' : '确认生成采购单' }}
            </button>
          </div>
        </form>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
/**
 * 采购入库控制台列表视图 (PurchaseOrderListView)
 * 职责：展示采购订单队列，支持生命周期状态筛选、新建采购单与快速验收入库
 */
import { ref, reactive, onMounted } from "vue";
import PageHeader from "@/components/common/PageHeader.vue";
import FilterBar from "@/components/common/FilterBar.vue";
import DataTable, { type TableColumn } from "@/components/common/DataTable.vue";
import StatusBadge from "@/components/common/StatusBadge.vue";
import QuantityText from "@/components/common/QuantityText.vue";
import EmptyState from "@/components/common/EmptyState.vue";
import ErrorState from "@/components/common/ErrorState.vue";
import PurchaseOrderDetailView from "./PurchaseOrderDetailView.vue";
import ReceiptConfirmView from "./ReceiptConfirmView.vue";
import type { ViewState } from "@/types/common";
import type { PurchaseOrder } from "@/types/purchasing";
import {
  getPurchaseOrders,
  createPurchaseOrder,
  confirmPurchaseReceipt,
} from "@/api/purchasing";

const viewState = ref<ViewState>("loading");
const errorMessage = ref("");
const orderList = ref<PurchaseOrder[]>([]);
const totalCount = ref(0);

const queryParams = reactive({
  page: 1,
  size: 10,
  keyword: "",
  status: "",
});

const statusFilters = [
  { label: "全部生命周期", value: "" },
  { label: "未提交 (Draft)", value: "Draft" },
  { label: "已提交 (Submitted)", value: "Submitted" },
  { label: "已审核 (Approved)", value: "Approved" },
  { label: "部分收货 (PartiallyReceived)", value: "PartiallyReceived" },
  { label: "已完成 (Completed)", value: "Completed" },
];

const columns: TableColumn[] = [
  { key: "poNo", label: "采购订单号", width: "160px" },
  { key: "supplier", label: "供应商", minWidth: "180px" },
  { key: "expectedArrivalDate", label: "计划到货日", width: "120px" },
  { key: "warehouseName", label: "目标仓库", width: "120px" },
  { key: "status", label: "当前状态", width: "130px", align: "center" },
  { key: "pendingSummary", label: "当前待收余量", width: "130px", align: "right" },
  { key: "actions", label: "操作", width: "140px", align: "center" },
];

const isDetailDrawerOpen = ref(false);
const selectedOrderId = ref<string | number | null>(null);

const isReceiptModalOpen = ref(false);
const selectedOrderForReceipt = ref<PurchaseOrder | null>(null);
const isReceiving = ref(false);

const isCreateModalOpen = ref(false);
const isCreating = ref(false);
const createForm = reactive({
  supplierId: "1",
  targetWarehouseId: "1",
  expectedArrivalDate: new Date().toISOString().slice(0, 10),
  productId: "3",
  orderedQty: "80",
  sourceWorkOrderId: "WO-20260826-018",
});

function statusBadgeType(status: string): any {
  const map: Record<string, string> = {
    Draft: "default",
    Submitted: "primary",
    Approved: "info",
    PartiallyReceived: "warning",
    Completed: "success",
  };
  return map[status] || "default";
}

function statusText(status: string): string {
  const map: Record<string, string> = {
    Draft: "未提交",
    Submitted: "已提交",
    Approved: "已审核",
    PartiallyReceived: "部分收货",
    Completed: "已完成",
  };
  return map[status] || status;
}

async function fetchOrders() {
  viewState.value = "loading";
  errorMessage.value = "";
  try {
    const res = await getPurchaseOrders({
      page: queryParams.page,
      size: queryParams.size,
      keyword: queryParams.keyword,
      status: queryParams.status,
    });
    orderList.value = res.data.records;
    totalCount.value = res.data.total;
    viewState.value = orderList.value.length === 0 ? "empty" : "ready";
  } catch (err: any) {
    console.error("[PurchaseOrderListView] 查询失败:", err);
    errorMessage.value = err?.message || "网络请求异常";
    viewState.value = "error";
  }
}

function handlePageChange(page: number) {
  queryParams.page = page;
  fetchOrders();
}

function switchStatusFilter(st: string) {
  queryParams.status = st;
  queryParams.page = 1;
  fetchOrders();
}

function resetFilter() {
  queryParams.keyword = "";
  queryParams.status = "";
  queryParams.page = 1;
  fetchOrders();
}

function openOrderDetail(row: PurchaseOrder) {
  selectedOrderId.value = row.id;
  isDetailDrawerOpen.value = true;
}

function openReceiptConfirm(row: PurchaseOrder) {
  selectedOrderForReceipt.value = row;
  isReceiptModalOpen.value = true;
}

async function handleConfirmReceipt(payload: any) {
  isReceiving.value = true;
  try {
    await confirmPurchaseReceipt(payload);
    isReceiptModalOpen.value = false;
    await fetchOrders();
  } catch (err: any) {
    alert(err?.message || "收货失败");
  } finally {
    isReceiving.value = false;
  }
}

async function submitCreateOrder() {
  isCreating.value = true;
  try {
    await createPurchaseOrder({
      supplierId: createForm.supplierId,
      expectedArrivalDate: createForm.expectedArrivalDate,
      targetWarehouseId: createForm.targetWarehouseId,
      lines: [
        {
          productId: createForm.productId,
          orderedQty: createForm.orderedQty,
          uom: "件",
          targetWarehouseId: createForm.targetWarehouseId,
          sourceWorkOrderId: createForm.sourceWorkOrderId,
        },
      ],
    });
    isCreateModalOpen.value = false;
    await fetchOrders();
  } catch (err: any) {
    alert(err?.message || "创建采购单失败");
  } finally {
    isCreating.value = false;
  }
}

onMounted(() => {
  fetchOrders();
});
</script>

<style scoped>
.purchase-order-list-view {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.status-tabs-row {
  display: flex;
  gap: 8px;
  overflow-x: auto;
  border-bottom: 1px solid rgba(255, 255, 255, 0.08);
  padding-bottom: 8px;
}

.status-tab-btn {
  padding: 6px 14px;
  background: rgba(30, 41, 59, 0.4);
  border: 1px solid rgba(255, 255, 255, 0.08);
  border-radius: 6px;
  color: #94a3b8;
  font-size: 12px;
  cursor: pointer;
  transition: all 0.2s;
  white-space: nowrap;
}

.status-tab-btn:hover {
  background: rgba(51, 65, 85, 0.6);
  color: #f1f5f9;
}

.status-tab-btn.is-active {
  background: rgba(56, 189, 248, 0.12);
  border-color: rgba(56, 189, 248, 0.3);
  color: #38bdf8;
}

.mono-code {
  font-family: var(--font-mono, monospace);
  font-size: 12px;
  color: #38bdf8;
}

.supplier-cell {
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.supp-name {
  font-size: 13px;
  color: #f1f5f9;
}

.supp-code {
  font-size: 11px;
  color: #64748b;
  font-family: var(--font-mono, monospace);
}

.status-cell {
  display: flex;
  align-items: center;
  gap: 6px;
  justify-content: center;
}

.tag-manual {
  font-size: 10px;
  color: #f87171;
  background: rgba(239, 68, 68, 0.12);
  padding: 1px 4px;
  border-radius: 3px;
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

.act-receive {
  color: #fbbf24;
  font-weight: 600;
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
.form-select {
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
