<template>
  <div class="reservation-view-container">
    <!-- 统一页面头部 -->
    <PageHeader
      title="库存预留与库位分配"
      tag="CORE / INVENTORY / RESERVATION"
      description="表达销售等业务对库存的占用承诺。直接拣货在同一事务内自动建立来源库位预留分配，并随实物同步迁移至发货暂存位（ShippingStaging）；发货出库或主动释放时才减少有效分配并增加 released_qty。"
    />

    <!-- 筛选搜索栏 -->
    <FilterBar
      v-model="queryParams.keyword"
      placeholder="搜索预留单号、业务来源单号..."
      @search="fetchReservations"
      @reset="resetFilter"
    >
      <select v-model="queryParams.sourceType" class="filter-select" @change="fetchReservations">
        <option value="">全部来源业务</option>
        <option value="SALES_ORDER">SALES_ORDER (销售订单)</option>
        <option value="WORK_ORDER">WORK_ORDER (生产工单)</option>
        <option value="TRANSFER">TRANSFER (库位调拨)</option>
      </select>

      <select v-model="queryParams.status" class="filter-select" @change="fetchReservations">
        <option value="">全部预留状态</option>
        <option value="Active">Active (生效中)</option>
        <option value="PartiallyReleased">PartiallyReleased (部分释放)</option>
        <option value="Released">Released (已全部释放)</option>
      </select>
    </FilterBar>

    <!-- 四态渲染 -->
    <ErrorState
      v-if="viewState === 'error'"
      title="获取库存预留数据异常"
      :message="errorMessage"
      @retry="fetchReservations"
    />

    <EmptyState
      v-else-if="viewState === 'empty'"
      title="暂无库存预留记录"
      description="当前未发现业务对库存的有效预留，销售订单执行直接拣货时将自动补足预留。"
    />

    <DataTable
      v-else
      :columns="columns"
      :data="reservationList"
      :loading="viewState === 'loading'"
      :total="totalCount"
      :page="queryParams.page"
      :size="queryParams.size"
      @page-change="handlePageChange"
    >
      <!-- 预留编号 -->
      <template #reservationNo="{ value }">
        <span class="mono-code">{{ value }}</span>
      </template>

      <!-- 来源单据 -->
      <template #source="{ row }">
        <div class="source-cell">
          <span class="source-tag">{{ row.sourceType }}</span>
          <span class="source-no">{{ row.sourceNo || '-' }}</span>
        </div>
      </template>

      <!-- 预留总量 -->
      <template #reservedQty="{ row }">
        <QuantityText :value="row.reservedQty" :unit="row.uom" />
      </template>

      <!-- 已释放量 -->
      <template #releasedQty="{ row }">
        <QuantityText :value="row.releasedQty" :unit="row.uom" />
      </template>

      <!-- 当前有效预留占用 -->
      <template #activeReservedQty="{ row }">
        <QuantityText :value="row.activeReservedQty" :unit="row.uom" />
      </template>

      <!-- 状态徽标 -->
      <template #status="{ value }">
        <StatusBadge
          :type="value === 'Active' ? 'primary' : value === 'PartiallyReleased' ? 'warning' : 'default'"
          :text="value === 'Active' ? '生效中' : value === 'PartiallyReleased' ? '部分释放' : '已全部释放'"
        />
      </template>

      <!-- 库位分配明细 -->
      <template #allocations="{ row }">
        <div v-if="row.allocations && row.allocations.length > 0" class="alloc-list">
          <div v-for="al in row.allocations" :key="al.id" class="alloc-item">
            <span class="alloc-loc">{{ al.locationCode }}</span>
            <span class="alloc-qty">分配: {{ al.allocatedQty }}</span>
            <span class="alloc-rel">释放: {{ al.releasedQty }}</span>
          </div>
        </div>
        <span v-else class="text-muted">-</span>
      </template>
    </DataTable>
  </div>
</template>

<script setup lang="ts">
/**
 * 库存预留与分配视图 (ReservationView)
 * 职责：展示业务预留记录及其在各个库位的分配明细
 */
import { ref, reactive, onMounted } from "vue";
import PageHeader from "@/components/common/PageHeader.vue";
import FilterBar from "@/components/common/FilterBar.vue";
import DataTable, { type TableColumn } from "@/components/common/DataTable.vue";
import StatusBadge from "@/components/common/StatusBadge.vue";
import QuantityText from "@/components/common/QuantityText.vue";
import EmptyState from "@/components/common/EmptyState.vue";
import ErrorState from "@/components/common/ErrorState.vue";
import type { ViewState } from "@/types/common";
import type { InventoryReservation } from "@/types/inventory";
import { getInventoryReservations } from "@/api/inventory";

const viewState = ref<ViewState>("loading");
const errorMessage = ref("");
const reservationList = ref<InventoryReservation[]>([]);
const totalCount = ref(0);

const queryParams = reactive({
  page: 1,
  size: 10,
  keyword: "",
  sourceType: "",
  status: "",
});

const columns: TableColumn[] = [
  { key: "reservationNo", label: "预留编号", width: "160px" },
  { key: "source", label: "业务来源单据", width: "170px" },
  { key: "sku", label: "物料编码", width: "130px" },
  { key: "productName", label: "物料名称", minWidth: "140px" },
  { key: "reservedQty", label: "初始预留量", width: "110px", align: "right" },
  { key: "releasedQty", label: "已释放量", width: "110px", align: "right" },
  { key: "activeReservedQty", label: "有效占用量", width: "110px", align: "right" },
  { key: "status", label: "预留状态", width: "110px", align: "center" },
  { key: "allocations", label: "库位有效分配明细", minWidth: "220px" },
];

async function fetchReservations() {
  viewState.value = "loading";
  errorMessage.value = "";
  try {
    const res = await getInventoryReservations({
      page: queryParams.page,
      size: queryParams.size,
      keyword: queryParams.keyword,
      sourceType: queryParams.sourceType,
      status: queryParams.status,
    });
    reservationList.value = res.data.records;
    totalCount.value = res.data.total;
    viewState.value = reservationList.value.length === 0 ? "empty" : "ready";
  } catch (err: any) {
    console.error("[ReservationView] 获取失败:", err);
    errorMessage.value = err?.message || "网络请求异常";
    viewState.value = "error";
  }
}

function handlePageChange(page: number) {
  queryParams.page = page;
  fetchReservations();
}

function resetFilter() {
  queryParams.keyword = "";
  queryParams.sourceType = "";
  queryParams.status = "";
  queryParams.page = 1;
  fetchReservations();
}

onMounted(() => {
  fetchReservations();
});
</script>

<style scoped>
.reservation-view-container {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.mono-code {
  font-family: var(--font-mono, monospace);
  font-size: 12px;
  color: #38bdf8;
}

.source-cell {
  display: flex;
  align-items: center;
  gap: 6px;
}

.source-tag {
  font-size: 10px;
  padding: 1px 5px;
  background: rgba(56, 189, 248, 0.12);
  color: #38bdf8;
  border-radius: 4px;
}

.source-no {
  font-family: var(--font-mono, monospace);
  font-size: 12px;
  color: #e2e8f0;
}

.alloc-list {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.alloc-item {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 12px;
  background: rgba(30, 41, 59, 0.5);
  padding: 3px 8px;
  border-radius: 4px;
}

.alloc-loc {
  font-family: var(--font-mono, monospace);
  color: #38bdf8;
  font-weight: 600;
}

.alloc-qty {
  color: #fbbf24;
}

.alloc-rel {
  color: #64748b;
}

.text-muted {
  color: #64748b;
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
</style>
