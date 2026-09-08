<template>
  <div class="pick-task-view">
    <!-- 页面头部 -->
    <PageHeader
      title="销售直接拣货履约队列"
      tag="CORE / SALES / PICKING"
      description="销售正常履约路径关键步骤。基于真实后端销售订单履约状态驱动：直接拣货在同一事务内自动补足预留并将实物及预留从来源位移至发货暂存位（ShippingStaging）；支持快捷直达订单履约详情与直接拣发操作。"
    />

    <!-- 筛选栏 -->
    <FilterBar
      v-model="queryParams.keyword"
      placeholder="搜索销售订单号、客户名称..."
      @search="fetchTasks"
      @reset="resetSearch"
    >
      <select v-model="queryParams.status" class="filter-select" @change="fetchTasks">
        <option value="">全部生命周期状态</option>
        <option value="Approved">已审核 (Approved)</option>
        <option value="Completed">已完成 (Completed)</option>
        <option value="Submitted">已提交 (Submitted)</option>
        <option value="Draft">草稿 (Draft)</option>
      </select>

      <select v-model="queryParams.fulfillmentStatus" class="filter-select" @change="fetchTasks">
        <option value="">全部履约进度</option>
        <option value="NotStarted">未开始 (NotStarted)</option>
        <option value="InProgress">履约中 (InProgress)</option>
        <option value="FullyShipped">全部发货 (FullyShipped)</option>
      </select>
    </FilterBar>

    <!-- 四态展示 -->
    <ErrorState
      v-if="viewState === 'error'"
      title="获取拣货履约队列异常"
      :message="errorMessage"
      @retry="fetchTasks"
    />

    <EmptyState
      v-else-if="viewState === 'empty'"
      title="暂无待履约订单记录"
      description="当前无符合条件的销售订单。在销售订单通过审核后将在此队列展示待拣货与待发货明细。"
    />

    <DataTable
      v-else
      :columns="columns"
      :data="taskList"
      :loading="viewState === 'loading'"
      :total="totalCount"
      :page="queryParams.page"
      :size="queryParams.size"
      @page-change="handlePageChange"
    >
      <!-- 销售单号与客户 -->
      <template #soNo="{ row }">
        <div class="so-cell">
          <span class="mono-code">{{ row.soNo }}</span>
          <span class="customer-sub">{{ row.customerName || row.customerCode || '-' }}</span>
        </div>
      </template>

      <!-- 生命周期状态 -->
      <template #status="{ value }">
        <StatusBadge
          :type="value === 'Approved' ? 'info' : value === 'Completed' ? 'success' : 'default'"
          :text="statusText(value)"
        />
      </template>

      <!-- 服务端派生履约进度 -->
      <template #fulfillmentStatus="{ value }">
        <StatusBadge
          :type="value === 'FullyShipped' ? 'success' : value === 'InProgress' ? 'warning' : 'default'"
          :text="fulfillmentText(value)"
        />
      </template>

      <!-- 派生数量汇总 -->
      <template #quantities="{ row }">
        <div class="line-qty-list">
          <div v-for="line in row.lines" :key="line.id" class="line-qty-row">
            <div class="line-product">
              <span class="line-no">行 {{ line.lineNo }}</span>
              <span class="line-sku">{{ line.sku }} / {{ line.productName }}</span>
            </div>
            <div class="qty-group">
              <div class="qty-item">
                <span class="qty-lbl">订购:</span>
                <span class="qty-val">{{ line.orderedQty }}</span>
              </div>
              <div class="qty-item">
                <span class="qty-lbl">已拣:</span>
                <span class="qty-val">{{ line.pickedQty }}</span>
              </div>
              <div class="qty-item" :class="{ 'highlight-staged': parseFloat(line.shippingStagedQty) > 0 }">
                <span class="qty-lbl">暂存:</span>
                <span class="qty-val">{{ line.shippingStagedQty }}</span>
              </div>
              <div class="qty-item">
                <span class="qty-lbl">已发:</span>
                <span class="qty-val">{{ line.shippedQty }}</span>
              </div>
              <div class="qty-item" :class="{ 'highlight-unpicked': parseFloat(line.unshippedQty) > 0 }">
                <span class="qty-lbl">未履约:</span>
                <span class="qty-val">{{ line.unshippedQty }}</span>
              </div>
            </div>
          </div>
        </div>
      </template>

      <!-- 计划发运日期 -->
      <template #plannedShipDate="{ value }">
        <span class="mono-text">{{ value ? value.substring(0, 10) : '-' }}</span>
      </template>

      <!-- 操作列 -->
      <template #actions="{ row }">
        <div class="action-buttons">
          <button
            type="button"
            class="btn-link"
            @click="openDetail(row)"
          >
            履约详情
          </button>
          <button
            v-if="row.status === 'Approved' && hasUnfulfilled(row)"
            type="button"
            class="btn-action-primary"
            @click="openDetail(row)"
          >
            直接拣货
          </button>
        </div>
      </template>
    </DataTable>

    <!-- 销售订单详情抽屉 (支持直接拣货/退回拣货/发货等操作) -->
    <SalesOrderDetailView
      v-model:visible="isDrawerOpen"
      :order-id="selectedOrderId"
      @close="isDrawerOpen = false"
      @refresh="fetchTasks"
      @pick-success="fetchTasks"
    />
  </div>
</template>

<script setup lang="ts">
/**
 * 销售直接拣货履约队列视图 (PickTaskView)
 * 职责：展示订单驱动的拣货履约队列，直达订单详情与直接拣发操作
 */
import { ref, reactive, onMounted } from "vue";
import PageHeader from "@/components/common/PageHeader.vue";
import FilterBar from "@/components/common/FilterBar.vue";
import DataTable, { type TableColumn } from "@/components/common/DataTable.vue";
import StatusBadge from "@/components/common/StatusBadge.vue";
import EmptyState from "@/components/common/EmptyState.vue";
import ErrorState from "@/components/common/ErrorState.vue";
import SalesOrderDetailView from "./SalesOrderDetailView.vue";
import type { ViewState } from "@/types/common";
import type { SalesOrder } from "@/types/sales";
import { getPickTasks } from "@/api/sales";

const viewState = ref<ViewState>("loading");
const errorMessage = ref("");
const taskList = ref<SalesOrder[]>([]);
const totalCount = ref(0);

const queryParams = reactive({
  page: 1,
  size: 10,
  keyword: "",
  status: "",
  fulfillmentStatus: "",
});

const isDrawerOpen = ref(false);
const selectedOrderId = ref<string | number | null>(null);

const columns: TableColumn[] = [
  { key: "soNo", label: "销售订单 / 客户", minWidth: "180px" },
  { key: "status", label: "生命周期状态", width: "130px", align: "center" },
  { key: "fulfillmentStatus", label: "履约进度", width: "130px", align: "center" },
  { key: "quantities", label: "派生数量 (订购/待拣/暂存/已发)", minWidth: "240px" },
  { key: "plannedShipDate", label: "计划发运", width: "120px" },
  { key: "updatedAt", label: "更新时间", width: "160px" },
  { key: "actions", label: "操作", width: "170px", align: "center" },
];

function statusText(st: string): string {
  const map: Record<string, string> = {
    Draft: "草稿",
    Submitted: "已提交",
    Approved: "已审核",
    Completed: "已完成",
  };
  return map[st] || st;
}

function fulfillmentText(st: string): string {
  const map: Record<string, string> = {
    NotStarted: "未开始",
    InProgress: "履约中",
    FullyShipped: "全部发货",
  };
  return map[st] || st;
}

/**
 * 用途：判断订单是否存在尚未履约数量，作为拣货入口的后端事实前置筛选。
 * 入参：销售订单；出参：至少一行未发货时返回 true。
 * 流程：仅读取服务端派生数量，不在前端修改订单状态。
 */
function hasUnfulfilled(order: SalesOrder): boolean {
  return order.lines?.some((line) => parseFloat(line.unshippedQty || "0") > 0) === true;
}

async function fetchTasks() {
  viewState.value = "loading";
  errorMessage.value = "";
  try {
    const res = await getPickTasks({
      page: queryParams.page,
      size: queryParams.size,
      keyword: queryParams.keyword || undefined,
      status: queryParams.status || undefined,
      fulfillmentStatus: queryParams.fulfillmentStatus || undefined,
    });
    taskList.value = res.data.records || [];
    totalCount.value = res.data.total || 0;
    viewState.value = taskList.value.length === 0 ? "empty" : "ready";
  } catch (err: any) {
    console.error("[PickTaskView] 获取失败:", err);
    errorMessage.value = err?.message || "网络请求异常";
    viewState.value = "error";
  }
}

function handlePageChange(page: number) {
  queryParams.page = page;
  fetchTasks();
}

function resetSearch() {
  queryParams.keyword = "";
  queryParams.status = "";
  queryParams.fulfillmentStatus = "";
  queryParams.page = 1;
  fetchTasks();
}

function openDetail(row: SalesOrder) {
  selectedOrderId.value = row.id;
  isDrawerOpen.value = true;
}

defineExpose({
  fetchTasks,
});

onMounted(() => {
  fetchTasks();
});
</script>

<style scoped>
.pick-task-view {
  display: flex;
  flex-direction: column;
  gap: 16px;
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

.so-cell {
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.mono-code {
  font-family: var(--font-mono, monospace);
  font-size: 13px;
  color: #38bdf8;
  font-weight: 500;
}

.customer-sub {
  font-size: 11px;
  color: #94a3b8;
}

.mono-text {
  font-family: var(--font-mono, monospace);
  font-size: 12px;
  color: #cbd5e1;
}

.line-qty-list {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.line-qty-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
}

.line-product {
  display: flex;
  flex-direction: column;
  min-width: 150px;
  gap: 2px;
}

.line-no {
  color: #94a3b8;
  font-size: 11px;
}

.line-sku {
  color: #cbd5e1;
  font-size: 11px;
}

.qty-group {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 10px;
  font-size: 12px;
}

.qty-item {
  display: flex;
  align-items: center;
  gap: 4px;
  background: rgba(30, 41, 59, 0.5);
  padding: 2px 6px;
  border-radius: 4px;
}

.qty-lbl {
  color: #64748b;
  font-size: 11px;
}

.qty-val {
  font-family: var(--font-mono, monospace);
  color: #cbd5e1;
  font-weight: 600;
}

.highlight-unpicked .qty-val {
  color: #f59e0b;
}

.highlight-staged .qty-val {
  color: #38bdf8;
}

.action-buttons {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
}

.btn-link {
  background: transparent;
  border: none;
  color: #38bdf8;
  font-size: 12px;
  cursor: pointer;
  padding: 2px 6px;
}

.btn-link:hover {
  text-decoration: underline;
}

.btn-action-primary {
  padding: 4px 10px;
  background: rgba(56, 189, 248, 0.15);
  border: 1px solid rgba(56, 189, 248, 0.3);
  color: #38bdf8;
  border-radius: 4px;
  font-size: 12px;
  cursor: pointer;
  white-space: nowrap;
}

.btn-action-primary:hover {
  background: #0284c7;
  color: #ffffff;
}
</style>
