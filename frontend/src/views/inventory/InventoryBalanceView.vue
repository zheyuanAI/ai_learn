<template>
  <div class="inventory-balance-view">
    <!-- 统一页面头部 -->
    <PageHeader
      title="实时库存余额"
      tag="CORE / INVENTORY / BALANCE"
      description="按租户、产品、仓库、库位与批次维度精确维护。遵循统一公式：available_qty = on_hand_qty - reserved_qty >= 0。质量隔离位（QualityHold）货物禁止正常销售预留与生产领料。"
    >
      <template #actions>
        <button type="button" class="btn-refresh" :disabled="viewState === 'loading'" @click="fetchBalances">
          <span>🔄 刷新余额</span>
        </button>
      </template>
    </PageHeader>

    <!-- 顶层汇总 KPI 指标条 -->
    <div class="kpi-strip">
      <div class="kpi-card highlight-cyan">
        <span class="kpi-label">企业总实物库存 (On Hand)</span>
        <div class="kpi-value-row">
          <QuantityText :value="kpiSummary.totalOnHand" />
          <span class="kpi-unit">件</span>
        </div>
        <span class="kpi-hint">企业实际接管拥有的全部物理实物</span>
      </div>

      <div class="kpi-card highlight-amber">
        <span class="kpi-label">业务预留占用 (Reserved)</span>
        <div class="kpi-value-row">
          <QuantityText :value="kpiSummary.totalReserved" />
          <span class="kpi-unit">件</span>
        </div>
        <span class="kpi-hint">销售直接拣货与业务锁定的库位有效分配</span>
      </div>

      <div class="kpi-card highlight-emerald">
        <span class="kpi-label">可用实物库存 (Available)</span>
        <div class="kpi-value-row">
          <QuantityText :value="kpiSummary.totalAvailable" />
          <span class="kpi-unit">件</span>
        </div>
        <span class="kpi-hint">仍可支持调拨或新增销售拣货的数量</span>
      </div>

      <div class="kpi-card highlight-rose">
        <span class="kpi-label">质量隔离位实物 (QualityHold)</span>
        <div class="kpi-value-row">
          <QuantityText :value="kpiSummary.qualityHoldOnHand" />
          <span class="kpi-unit">件</span>
        </div>
        <span class="kpi-hint">到货实际接收尚未放行，严禁领料/预留</span>
      </div>
    </div>

    <!-- 筛选搜索栏 -->
    <FilterBar
      v-model="queryParams.keyword"
      placeholder="搜索物料 SKU、名称或库位编码..."
      @search="fetchBalances"
      @reset="resetFilter"
    >
      <select v-model="queryParams.locationType" class="filter-select" @change="fetchBalances">
        <option value="">全部标准库位类型</option>
        <option value="ReceivingStaging">ReceivingStaging (收货暂存位)</option>
        <option value="Storage">Storage (常规存储位)</option>
        <option value="Picking">Picking (拣货备料位)</option>
        <option value="ShippingStaging">ShippingStaging (发货暂存位)</option>
        <option value="QualityHold">QualityHold (质量隔离位)</option>
        <option value="Adjustment">Adjustment (差异调整位)</option>
      </select>

      <select v-model="queryParams.warehouseId" class="filter-select" @change="fetchBalances">
        <option value="">全部仓库</option>
        <option value="1">原料一仓</option>
        <option value="2">成品一仓</option>
        <option value="3">虚拟仓</option>
      </select>
    </FilterBar>

    <!-- 四态渲染 -->
    <ErrorState
      v-if="viewState === 'error'"
      title="获取库存余额失败"
      :message="errorMessage"
      @retry="fetchBalances"
    />

    <EmptyState
      v-else-if="viewState === 'empty'"
      title="未检索到库存余额记录"
      description="当前库位或筛选条件下暂无在库物料，可通过采购收货或调拨入库增加库存。"
    />

    <DataTable
      v-else
      :columns="columns"
      :data="balanceList"
      :loading="viewState === 'loading'"
      :total="totalCount"
      :page="queryParams.page"
      :size="queryParams.size"
      @page-change="handlePageChange"
    >
      <!-- 标准库位类型插槽 -->
      <template #locationType="{ value }">
        <StatusBadge
          :type="locationTypeBadgeMap[String(value)] || 'default'"
          :text="locationTypeTextMap[String(value)] || String(value)"
        />
      </template>

      <!-- 实物在库数量插槽 -->
      <template #onHandQty="{ row }">
        <QuantityText :value="row.onHandQty" :unit="row.uom" />
      </template>

      <!-- 业务预留数量插槽 -->
      <template #reservedQty="{ row }">
        <QuantityText :value="row.reservedQty" :unit="row.uom" />
      </template>

      <!-- 可用分配数量插槽 -->
      <template #availableQty="{ row }">
        <QuantityText :value="row.availableQty" :unit="row.uom" />
      </template>

      <!-- 批次号插槽 -->
      <template #lotNo="{ value }">
        <span class="mono-text">{{ value || '-' }}</span>
      </template>
    </DataTable>
  </div>
</template>

<script setup lang="ts">
/**
 * 实时库存余额视图 (InventoryBalanceView)
 * 职责：展示按仓库、库位、物料与批次聚合的在库实物、预留与可用量
 * 核心规则：available_qty = on_hand_qty - reserved_qty >= 0
 * 数量精度：全部通过 QuantityText 严格字符串展示
 */
import { ref, reactive, computed, onMounted } from "vue";
import PageHeader from "@/components/common/PageHeader.vue";
import FilterBar from "@/components/common/FilterBar.vue";
import DataTable, { type TableColumn } from "@/components/common/DataTable.vue";
import StatusBadge from "@/components/common/StatusBadge.vue";
import QuantityText from "@/components/common/QuantityText.vue";
import EmptyState from "@/components/common/EmptyState.vue";
import ErrorState from "@/components/common/ErrorState.vue";
import type { ViewState } from "@/types/common";
import { type InventoryBalance, stringAdd, stringSub } from "@/types/inventory";
import { getInventoryBalances } from "@/api/inventory";

const viewState = ref<ViewState>("loading");
const errorMessage = ref("");
const balanceList = ref<InventoryBalance[]>([]);
const totalCount = ref(0);

const queryParams = reactive({
  page: 1,
  size: 10,
  keyword: "",
  locationType: "",
  warehouseId: "",
});

const columns: TableColumn[] = [
  { key: "sku", label: "物料编码 (SKU)", width: "130px" },
  { key: "productName", label: "物料名称", minWidth: "150px" },
  { key: "warehouseName", label: "所在仓库", width: "110px" },
  { key: "locationCode", label: "库位编码", width: "100px" },
  { key: "locationType", label: "标准库位类型", width: "140px", align: "center" },
  { key: "lotNo", label: "批次号", width: "140px" },
  { key: "onHandQty", label: "实物在库", width: "110px", align: "right" },
  { key: "reservedQty", label: "业务预留", width: "110px", align: "right" },
  { key: "availableQty", label: "可用分配", width: "110px", align: "right" },
  { key: "lastTransactionAt", label: "最后流水时点", width: "160px" },
];

const locationTypeTextMap: Record<string, string> = {
  ReceivingStaging: "收货暂存位",
  Storage: "常规存储位",
  Picking: "拣货备料位",
  ShippingStaging: "发货暂存位",
  QualityHold: "质量隔离位",
  Adjustment: "差异调整位",
};

const locationTypeBadgeMap: Record<string, any> = {
  ReceivingStaging: "info",
  Storage: "primary",
  Picking: "warning",
  ShippingStaging: "success",
  QualityHold: "danger",
  Adjustment: "default",
};

// 动态汇总 KPI
const kpiSummary = computed(() => {
  let onHand = "0";
  let reserved = "0";
  let qhOnHand = "0";

  balanceList.value.forEach((b) => {
    onHand = stringAdd(onHand, b.onHandQty);
    reserved = stringAdd(reserved, b.reservedQty);
    if (b.locationType === "QualityHold") {
      qhOnHand = stringAdd(qhOnHand, b.onHandQty);
    }
  });

  return {
    totalOnHand: onHand,
    totalReserved: reserved,
    totalAvailable: stringSub(onHand, reserved),
    qualityHoldOnHand: qhOnHand,
  };
});

/**
 * 获取库存余额列表
 */
async function fetchBalances() {
  viewState.value = "loading";
  errorMessage.value = "";
  try {
    const res = await getInventoryBalances({
      page: queryParams.page,
      size: queryParams.size,
      keyword: queryParams.keyword,
      locationType: queryParams.locationType,
      warehouseId: queryParams.warehouseId,
    });
    balanceList.value = res.data.records;
    totalCount.value = res.data.total;
    viewState.value = balanceList.value.length === 0 ? "empty" : "ready";
  } catch (err: any) {
    console.error("[InventoryBalanceView] 获取失败:", err);
    errorMessage.value = err?.message || "网络请求异常";
    viewState.value = "error";
  }
}

function handlePageChange(page: number) {
  queryParams.page = page;
  fetchBalances();
}

function resetFilter() {
  queryParams.keyword = "";
  queryParams.locationType = "";
  queryParams.warehouseId = "";
  queryParams.page = 1;
  fetchBalances();
}

onMounted(() => {
  fetchBalances();
});
</script>

<style scoped>
.inventory-balance-view {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.kpi-strip {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(220px, 1fr));
  gap: 14px;
}

.kpi-card {
  background: rgba(15, 23, 42, 0.7);
  border: 1px solid rgba(255, 255, 255, 0.08);
  border-radius: 8px;
  padding: 16px 18px;
  display: flex;
  flex-direction: column;
  gap: 6px;
  position: relative;
  overflow: hidden;
}

.kpi-card::before {
  content: "";
  position: absolute;
  top: 0;
  left: 0;
  right: 0;
  height: 2px;
}

.highlight-cyan::before {
  background: #38bdf8;
}

.highlight-amber::before {
  background: #fbbf24;
}

.highlight-emerald::before {
  background: #34d399;
}

.highlight-rose::before {
  background: #f87171;
}

.kpi-label {
  font-size: 12px;
  color: #94a3b8;
}

.kpi-value-row {
  display: flex;
  align-items: baseline;
  gap: 6px;
  font-size: 24px;
  font-weight: 700;
  color: #f8fafc;
}

.kpi-unit {
  font-size: 12px;
  color: #64748b;
  font-weight: 400;
}

.kpi-hint {
  font-size: 11px;
  color: #64748b;
  line-height: 1.4;
}

.btn-refresh {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 7px 14px;
  background: rgba(51, 65, 85, 0.6);
  border: 1px solid rgba(255, 255, 255, 0.1);
  border-radius: 6px;
  color: #cbd5e1;
  font-size: 13px;
  cursor: pointer;
  transition: all 0.2s;
}

.btn-refresh:hover:not(:disabled) {
  background: rgba(71, 85, 105, 0.8);
  color: #ffffff;
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

.mono-text {
  font-family: var(--font-mono, monospace);
  color: #cbd5e1;
  font-size: 12px;
}
</style>
