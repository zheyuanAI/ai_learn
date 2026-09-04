<template>
  <div class="inventory-transaction-view">
    <!-- 统一页面头部 -->
    <PageHeader
      title="库存审计流水"
      tag="CORE / INVENTORY / TRANSACTIONS"
      description="不可篡改历史事实留痕。记录采购到货接收、质量放行移位/报废扣减、上架、直接拣货、发货出库、库位调拨与盘点调整事实。流水只追加不可随意删除，纠错必须生成反向或调整流水。"
    />

    <!-- 筛选搜索栏 -->
    <FilterBar
      v-model="queryParams.keyword"
      placeholder="搜索流水号、业务单号、物料 SKU..."
      @search="fetchTransactions"
      @reset="resetFilter"
    >
      <select v-model="queryParams.transactionType" class="filter-select" @change="fetchTransactions">
        <option value="">全部流水操作类型</option>
        <option value="PURCHASE_RECEIPT">PURCHASE_RECEIPT (采购收货入 QH)</option>
        <option value="QUALITY_RELEASE">QUALITY_RELEASE (质检放行移至 RS)</option>
        <option value="QUALITY_SCRAP">QUALITY_SCRAP (质检报废扣减)</option>
        <option value="QUALITY_RETURN">QUALITY_RETURN (采购退回供应方)</option>
        <option value="PUTAWAY">PUTAWAY (上架存储位)</option>
        <option value="DIRECT_PICK">DIRECT_PICK (直接拣货移至 SHP)</option>
        <option value="PICK_RETURN">PICK_RETURN (拣货退回合法库位)</option>
        <option value="SALES_SHIPMENT">SALES_SHIPMENT (发货扣减实物)</option>
        <option value="TRANSFER">TRANSFER (库位调拨)</option>
        <option value="STOCKTAKE_ADJUST">STOCKTAKE_ADJUST (差异盘点调整)</option>
      </select>
    </FilterBar>

    <!-- 四态展示 -->
    <ErrorState
      v-if="viewState === 'error'"
      title="获取库存流水异常"
      :message="errorMessage"
      @retry="fetchTransactions"
    />

    <EmptyState
      v-else-if="viewState === 'empty'"
      title="暂无库存流水记录"
      description="当前检索条件下未发现库存变动流水，执行收发存或盘点后将自动追加永久事实。"
    />

    <DataTable
      v-else
      :columns="columns"
      :data="transactionList"
      :loading="viewState === 'loading'"
      :total="totalCount"
      :page="queryParams.page"
      :size="queryParams.size"
      @page-change="handlePageChange"
    >
      <!-- 流水号插槽 -->
      <template #transactionNo="{ value }">
        <span class="mono-code">{{ value }}</span>
      </template>

      <!-- 操作类型插槽 -->
      <template #transactionType="{ value }">
        <StatusBadge
          :type="typeBadgeMap[String(value)] || 'default'"
          :text="typeTextMap[String(value)] || String(value)"
        />
      </template>

      <!-- 来源单据插槽 -->
      <template #source="{ row }">
        <div class="source-cell">
          <span class="source-no">{{ row.sourceNo || '-' }}</span>
          <span v-if="row.sourceLineId" class="source-line">行: {{ row.sourceLineId }}</span>
        </div>
      </template>

      <!-- 库位迁移路径插槽 -->
      <template #locationRoute="{ row }">
        <div class="route-cell">
          <span class="loc-tag">{{ row.fromLocationCode || '外部供应商' }}</span>
          <span class="route-arrow">➔</span>
          <span class="loc-tag">{{ row.toLocationCode || '外部出库/核销' }}</span>
        </div>
      </template>

      <!-- 变动数量插槽 -->
      <template #qty="{ row }">
        <span :class="parseFloat(row.qty) < 0 ? 'qty-negative' : 'qty-positive'">
          <QuantityText :value="row.qty" :unit="row.uom" />
        </span>
      </template>

      <!-- 审计人员插槽 -->
      <template #operator="{ row }">
        <div class="operator-cell">
          <span>{{ row.operatorName || 'wh.operator' }}</span>
          <span v-if="row.sessionId" class="session-tag">{{ row.sessionId }}</span>
        </div>
      </template>
    </DataTable>
  </div>
</template>

<script setup lang="ts">
/**
 * 库存不可篡改审计流水视图 (InventoryTransactionView)
 * 职责：展示全链路不可篡改事实，支持操作类型筛选与精确检索
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
import type { InventoryTransaction } from "@/types/inventory";
import { getInventoryTransactions } from "@/api/inventory";

const viewState = ref<ViewState>("loading");
const errorMessage = ref("");
const transactionList = ref<InventoryTransaction[]>([]);
const totalCount = ref(0);

const queryParams = reactive({
  page: 1,
  size: 10,
  keyword: "",
  transactionType: "",
});

const columns: TableColumn[] = [
  { key: "transactionNo", label: "流水编号", width: "150px" },
  { key: "transactionType", label: "业务类型", width: "140px", align: "center" },
  { key: "source", label: "关联业务来源", width: "160px" },
  { key: "sku", label: "物料编码", width: "120px" },
  { key: "productName", label: "物料名称", minWidth: "140px" },
  { key: "locationRoute", label: "流转路径 (From ➔ To)", width: "200px" },
  { key: "qty", label: "变动数量", width: "110px", align: "right" },
  { key: "occurredAt", label: "发生时点", width: "160px" },
  { key: "operator", label: "操作人员 / 会话", width: "150px" },
];

const typeTextMap: Record<string, string> = {
  PURCHASE_RECEIPT: "采购收货入库",
  QUALITY_RELEASE: "质检放行移位",
  QUALITY_SCRAP: "质检报废扣减",
  QUALITY_RETURN: "退回供应方",
  PUTAWAY: "上架存储位",
  DIRECT_PICK: "直接拣货移位",
  PICK_RETURN: "拣货退回",
  SALES_SHIPMENT: "销售发货出库",
  TRANSFER: "库位调拨",
  STOCKTAKE_ADJUST: "差异盘点调整",
};

const typeBadgeMap: Record<string, any> = {
  PURCHASE_RECEIPT: "primary",
  QUALITY_RELEASE: "info",
  QUALITY_SCRAP: "danger",
  QUALITY_RETURN: "warning",
  PUTAWAY: "success",
  DIRECT_PICK: "primary",
  PICK_RETURN: "warning",
  SALES_SHIPMENT: "danger",
  TRANSFER: "info",
  STOCKTAKE_ADJUST: "default",
};

async function fetchTransactions() {
  viewState.value = "loading";
  errorMessage.value = "";
  try {
    const res = await getInventoryTransactions({
      page: queryParams.page,
      size: queryParams.size,
      keyword: queryParams.keyword,
      transactionType: queryParams.transactionType,
    });
    transactionList.value = res.data.records;
    totalCount.value = res.data.total;
    viewState.value = transactionList.value.length === 0 ? "empty" : "ready";
  } catch (err: any) {
    console.error("[InventoryTransactionView] 查询失败:", err);
    errorMessage.value = err?.message || "网络请求异常";
    viewState.value = "error";
  }
}

function handlePageChange(page: number) {
  queryParams.page = page;
  fetchTransactions();
}

function resetFilter() {
  queryParams.keyword = "";
  queryParams.transactionType = "";
  queryParams.page = 1;
  fetchTransactions();
}

onMounted(() => {
  fetchTransactions();
});
</script>

<style scoped>
.inventory-transaction-view {
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
  flex-direction: column;
  gap: 2px;
}

.source-no {
  font-family: var(--font-mono, monospace);
  font-size: 12px;
  color: #e2e8f0;
}

.source-line {
  font-size: 11px;
  color: #64748b;
}

.route-cell {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 12px;
}

.loc-tag {
  background: rgba(30, 41, 59, 0.6);
  padding: 2px 6px;
  border-radius: 4px;
  color: #cbd5e1;
  font-family: var(--font-mono, monospace);
}

.route-arrow {
  color: #38bdf8;
  font-size: 10px;
}

.qty-positive {
  color: #34d399;
}

.qty-negative {
  color: #f87171;
}

.operator-cell {
  display: flex;
  flex-direction: column;
  gap: 2px;
  font-size: 12px;
}

.session-tag {
  font-size: 10px;
  font-family: var(--font-mono, monospace);
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
