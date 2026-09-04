<template>
  <div class="sales-order-list-view">
    <!-- 统一页面头部 -->
    <PageHeader
      title="销售履约控制台"
      tag="CORE / SALES / OUTBOUND"
      description="销售订单采用生命周期与履约进度双轴模型。正常路径只保留【直接拣货】与【发货确认】；直接拣货在同一事务内自动补足来源库位预留并移入发货暂存位（ShippingStaging），发货出库正式扣减实物库存并释放业务预留。"
    >
      <template #actions>
        <button type="button" class="btn-primary" @click="isCreateModalOpen = true">
          <span>＋ 新建销售订单</span>
        </button>
      </template>
    </PageHeader>

    <!-- 双轴过滤导航条 -->
    <div class="dual-filter-bar">
      <!-- 轴一：生命周期筛选 -->
      <div class="filter-group">
        <span class="group-title">生命周期 (Status)：</span>
        <div class="filter-buttons">
          <button
            v-for="opt in lifecycleFilters"
            :key="opt.value"
            type="button"
            class="filter-tab-btn"
            :class="{ 'is-active': queryParams.status === opt.value }"
            @click="setLifecycleFilter(opt.value)"
          >
            {{ opt.label }}
          </button>
        </div>
      </div>

      <!-- 轴二：派生履约进度筛选 -->
      <div class="filter-group">
        <span class="group-title">履约进度 (Fulfillment)：</span>
        <div class="filter-buttons">
          <button
            v-for="opt in fulfillmentFilters"
            :key="opt.value"
            type="button"
            class="filter-tab-btn"
            :class="{ 'is-active': queryParams.fulfillmentStatus === opt.value }"
            @click="setFulfillmentFilter(opt.value)"
          >
            {{ opt.label }}
          </button>
        </div>
      </div>
    </div>

    <!-- 检索输入栏 -->
    <FilterBar
      v-model="queryParams.keyword"
      placeholder="搜索销售单号、客户全称或物料..."
      @search="fetchOrders"
      @reset="resetFilter"
    />

    <!-- 四态展示 -->
    <ErrorState
      v-if="viewState === 'error'"
      title="获取销售订单失败"
      :message="errorMessage"
      @retry="fetchOrders"
    />

    <EmptyState
      v-else-if="viewState === 'empty'"
      title="暂无销售订单记录"
      description="当前筛选条件下未发现符合的销售订单，您可以调整筛选条件或新建订单。"
    >
      <template #action>
        <button type="button" class="btn-create-sm" @click="isCreateModalOpen = true">
          立即新建销售单
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
      <!-- 销售单号 -->
      <template #soNo="{ value }">
        <span class="mono-code">{{ value }}</span>
      </template>

      <!-- 客户名称 -->
      <template #customer="{ row }">
        <div class="cust-cell">
          <span class="cust-name">{{ row.customerName }}</span>
          <span class="cust-code">{{ row.customerCode }}</span>
        </div>
      </template>

      <!-- 双轴：生命周期状态 -->
      <template #status="{ value }">
        <StatusBadge
          :type="lifecycleBadgeType(value)"
          :text="lifecycleText(value)"
        />
      </template>

      <!-- 双轴：履约进度 -->
      <template #fulfillmentStatus="{ value }">
        <StatusBadge
          :type="fulfillmentBadgeType(value)"
          :text="fulfillmentText(value)"
        />
      </template>

      <!-- 完成方式 -->
      <template #completionType="{ row }">
        <span v-if="row.completionType === 'Manual'" class="tag-manual">人工完成</span>
        <span v-else-if="row.completionType === 'Normal'" class="tag-normal">全部发货</span>
        <span v-else class="text-muted">-</span>
      </template>

      <!-- 履约进度摘要 -->
      <template #summary="{ row }">
        <div v-if="row.lines && row.lines.length > 0" class="progress-cell">
          <span class="sku-hint">{{ row.lines[0].productName }}</span>
          <span class="ratio-hint">
            发货: {{ row.lines[0].shippedQty }} / 订购: {{ row.lines[0].orderedQty }} {{ row.lines[0].uom }}
          </span>
        </div>
      </template>

      <!-- 操作列 -->
      <template #actions="{ row }">
        <div class="table-actions">
          <button type="button" class="btn-link" @click="openOrderDetail(row)">
            详情 / 履约
          </button>
        </div>
      </template>
    </DataTable>

    <!-- 订单详情与履约抽屉 -->
    <SalesOrderDetailView
      v-model:visible="isDetailDrawerOpen"
      :order-id="selectedOrderId"
      @refresh="fetchOrders"
      @close="isDetailDrawerOpen = false"
    />

    <!-- 新建销售单弹窗 -->
    <div v-if="isCreateModalOpen" class="modal-mask" @click.self="isCreateModalOpen = false">
      <div class="modal-panel">
        <div class="modal-header">
          <h3 class="modal-title">新建销售订单</h3>
          <button type="button" class="btn-close" @click="isCreateModalOpen = false">✕</button>
        </div>
        <form class="modal-body" @submit.prevent="submitCreateOrder">
          <div class="form-item">
            <label>往来客户 <span class="req">*</span></label>
            <select v-model="createForm.customerId" class="form-select" required>
              <option value="1">华北智造系统有限公司 (CUS-NC-021)</option>
              <option value="2">苏州精密装备研究院 (CUS-EA-014)</option>
              <option value="3">宁波柔性制造中心 (CUS-EA-006)</option>
            </select>
          </div>
          <div class="form-row">
            <div class="form-item">
              <label>出库仓库 <span class="req">*</span></label>
              <select v-model="createForm.warehouseId" class="form-select" required>
                <option value="2">成品一仓 (WH-FG-01)</option>
              </select>
            </div>
            <div class="form-item">
              <label>计划发货日期 <span class="req">*</span></label>
              <input v-model="createForm.plannedShipDate" type="date" class="form-input" required />
            </div>
          </div>
          <div class="form-item">
            <label>订购物料 <span class="req">*</span></label>
            <select v-model="createForm.productId" class="form-select" required>
              <option value="1">FG-SERVO-01 (伺服电机总成 - 可用 400)</option>
              <option value="2">FG-CTRL-08 (边缘控制终端)</option>
            </select>
          </div>
          <div class="form-item">
            <label>订购数量 <span class="req">*</span></label>
            <input v-model="createForm.orderedQty" type="text" class="form-input" required placeholder="如: 40" />
          </div>
          <div class="form-item">
            <label>发运备注</label>
            <textarea v-model="createForm.remark" class="form-textarea" rows="2" placeholder="客户交付要求与注意事项..."></textarea>
          </div>
          <div class="modal-footer">
            <button type="button" class="btn-secondary" @click="isCreateModalOpen = false">取消</button>
            <button type="submit" class="btn-primary" :disabled="isCreating">
              {{ isCreating ? '创建中...' : '生成销售订单' }}
            </button>
          </div>
        </form>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
/**
 * 销售履约控制台列表视图 (SalesOrderListView)
 * 职责：并列双轴过滤（生命周期状态 + 履约进度），展示各订单履约事实与抽屉交互
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
import { getSalesOrders, createSalesOrder } from "@/api/sales";

const viewState = ref<ViewState>("loading");
const errorMessage = ref("");
const orderList = ref<SalesOrder[]>([]);
const totalCount = ref(0);

const queryParams = reactive({
  page: 1,
  size: 10,
  keyword: "",
  status: "",
  fulfillmentStatus: "",
});

const lifecycleFilters = [
  { label: "全部生命周期", value: "" },
  { label: "履约中 (Approved)", value: "Approved" },
  { label: "已完成 (Completed)", value: "Completed" },
];

const fulfillmentFilters = [
  { label: "全部进度", value: "" },
  { label: "尚未开始 (NotStarted)", value: "NotStarted" },
  { label: "履约处理中 (InProgress)", value: "InProgress" },
  { label: "全部发货 (FullyShipped)", value: "FullyShipped" },
];

const columns: TableColumn[] = [
  { key: "soNo", label: "销售订单号", width: "160px" },
  { key: "customer", label: "客户信息", minWidth: "180px" },
  { key: "plannedShipDate", label: "计划发货日", width: "120px" },
  { key: "status", label: "生命周期状态", width: "130px", align: "center" },
  { key: "fulfillmentStatus", label: "履约进度", width: "130px", align: "center" },
  { key: "completionType", label: "完成方式", width: "110px", align: "center" },
  { key: "summary", label: "首行履约概览", width: "200px" },
  { key: "actions", label: "操作", width: "120px", align: "center" },
];

const isDetailDrawerOpen = ref(false);
const selectedOrderId = ref<string | number | null>(null);

const isCreateModalOpen = ref(false);
const isCreating = ref(false);
const createForm = reactive({
  customerId: "1",
  warehouseId: "2",
  plannedShipDate: new Date().toISOString().slice(0, 10),
  productId: "1",
  orderedQty: "40",
  remark: "加急专车发运",
});

function lifecycleBadgeType(status: string): any {
  const map: Record<string, string> = {
    Draft: "default",
    Submitted: "primary",
    Approved: "info",
    Completed: "success",
  };
  return map[status] || "default";
}

function lifecycleText(status: string): string {
  const map: Record<string, string> = {
    Draft: "未提交",
    Submitted: "已提交",
    Approved: "履约中",
    Completed: "已完成",
  };
  return map[status] || status;
}

function fulfillmentBadgeType(st: string): any {
  const map: Record<string, string> = {
    NotStarted: "default",
    InProgress: "warning",
    FullyShipped: "success",
  };
  return map[st] || "default";
}

function fulfillmentText(st: string): string {
  const map: Record<string, string> = {
    NotStarted: "尚未开始",
    InProgress: "处理中",
    FullyShipped: "全部发货",
  };
  return map[st] || st;
}

async function fetchOrders() {
  viewState.value = "loading";
  errorMessage.value = "";
  try {
    const res = await getSalesOrders({
      page: queryParams.page,
      size: queryParams.size,
      keyword: queryParams.keyword,
      status: queryParams.status,
      fulfillmentStatus: queryParams.fulfillmentStatus,
    });
    orderList.value = res.data.records;
    totalCount.value = res.data.total;
    viewState.value = orderList.value.length === 0 ? "empty" : "ready";
  } catch (err: any) {
    console.error("[SalesOrderListView] 查询失败:", err);
    errorMessage.value = err?.message || "网络请求异常";
    viewState.value = "error";
  }
}

function setLifecycleFilter(val: string) {
  queryParams.status = val;
  queryParams.page = 1;
  fetchOrders();
}

function setFulfillmentFilter(val: string) {
  queryParams.fulfillmentStatus = val;
  queryParams.page = 1;
  fetchOrders();
}

function handlePageChange(page: number) {
  queryParams.page = page;
  fetchOrders();
}

function resetFilter() {
  queryParams.keyword = "";
  queryParams.status = "";
  queryParams.fulfillmentStatus = "";
  queryParams.page = 1;
  fetchOrders();
}

function openOrderDetail(row: SalesOrder) {
  selectedOrderId.value = row.id;
  isDetailDrawerOpen.value = true;
}

async function submitCreateOrder() {
  isCreating.value = true;
  try {
    await createSalesOrder({
      customerId: createForm.customerId,
      plannedShipDate: createForm.plannedShipDate,
      warehouseId: createForm.warehouseId,
      remark: createForm.remark,
      lines: [
        {
          productId: createForm.productId,
          orderedQty: createForm.orderedQty,
          uom: "台",
          sourceLocationId: "6",
        },
      ],
    });
    isCreateModalOpen.value = false;
    await fetchOrders();
  } catch (err: any) {
    alert(err?.message || "创建销售单失败");
  } finally {
    isCreating.value = false;
  }
}

onMounted(() => {
  fetchOrders();
});
</script>

<style scoped>
.sales-order-list-view {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.dual-filter-bar {
  display: flex;
  flex-direction: column;
  gap: 10px;
  background: rgba(15, 23, 42, 0.5);
  border: 1px solid rgba(255, 255, 255, 0.06);
  border-radius: 8px;
  padding: 12px 16px;
}

.filter-group {
  display: flex;
  align-items: center;
  gap: 10px;
  flex-wrap: wrap;
}

.group-title {
  font-size: 12px;
  color: #94a3b8;
  min-width: 140px;
}

.filter-buttons {
  display: flex;
  align-items: center;
  gap: 6px;
  flex-wrap: wrap;
}

.filter-tab-btn {
  padding: 5px 12px;
  background: rgba(30, 41, 59, 0.5);
  border: 1px solid rgba(255, 255, 255, 0.08);
  border-radius: 6px;
  color: #cbd5e1;
  font-size: 12px;
  cursor: pointer;
  transition: all 0.2s;
}

.filter-tab-btn:hover {
  background: rgba(51, 65, 85, 0.7);
  color: #ffffff;
}

.filter-tab-btn.is-active {
  background: rgba(56, 189, 248, 0.15);
  border-color: rgba(56, 189, 248, 0.35);
  color: #38bdf8;
  font-weight: 500;
}

.mono-code {
  font-family: var(--font-mono, monospace);
  font-size: 12px;
  color: #38bdf8;
}

.cust-cell {
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.cust-name {
  font-size: 13px;
  color: #f1f5f9;
}

.cust-code {
  font-size: 11px;
  color: #64748b;
  font-family: var(--font-mono, monospace);
}

.tag-manual {
  font-size: 11px;
  color: #f87171;
  background: rgba(239, 68, 68, 0.12);
  padding: 2px 6px;
  border-radius: 4px;
}

.tag-normal {
  font-size: 11px;
  color: #34d399;
  background: rgba(52, 211, 153, 0.12);
  padding: 2px 6px;
  border-radius: 4px;
}

.text-muted {
  color: #64748b;
}

.progress-cell {
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.sku-hint {
  font-size: 12px;
  color: #e2e8f0;
}

.ratio-hint {
  font-size: 11px;
  color: #38bdf8;
  font-family: var(--font-mono, monospace);
}

.table-actions {
  display: flex;
  align-items: center;
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
