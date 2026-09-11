<template>
  <div class="transfer-list-view">
    <!-- 统一页面头部 -->
    <PageHeader
      title="库位调拨管理"
      tag="CORE / INVENTORY / TRANSFER"
      description="在同一事务中扣减来源库位、增加目标库位，企业总实物库存保持不变。来源库位可用库存必须充足，调拨失败严禁产生单边库存事实。"
    >
      <template #actions>
        <el-button type="primary" :icon="Plus" @click="openCreateModal">
          发起库位调拨
        </el-button>
      </template>
    </PageHeader>

    <!-- 筛选搜索栏 -->
    <FilterBar
      v-model="queryParams.keyword"
      placeholder="搜索调拨单号、物料编码或名称..."
      @search="fetchTransfers"
      @reset="resetFilter"
    >
      <el-select
        v-model="queryParams.status"
        placeholder="全部调拨状态"
        clearable
        style="width: 220px"
        @change="fetchTransfers"
      >
        <el-option label="全部调拨状态" value="" />
        <el-option label="待确认执行 (Draft)" value="Draft" />
        <el-option label="已确认完成 (Confirmed)" value="Confirmed" />
      </el-select>
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
        <el-button type="primary" size="small" :icon="Plus" @click="openCreateModal">
          立即发起调拨
        </el-button>
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
        <el-button type="primary" link size="small" @click="openDetailModal(row)">
          详情
        </el-button>
        <el-button
          v-if="row.status === 'Draft'"
          type="success"
          link
          size="small"
          @click="openDetailModal(row)"
        >
          确认执行
        </el-button>
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
    <el-dialog
      v-model="isCreateVisible"
      title="发起库位调拨"
      width="550px"
      destroy-on-close
    >
      <el-form label-width="100px" @submit.prevent="handleCreateSubmit">
        <el-form-item label="物料" required>
          <el-select v-model="createForm.productId" placeholder="请选择物料" style="width: 100%">
            <el-option
              v-for="product in products"
              :key="product.id"
              :label="`${product.sku} - ${product.name}`"
              :value="product.id"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="来源库位" required>
          <el-select v-model="createForm.fromLocationId" placeholder="请选择来源库位" style="width: 100%">
            <el-option
              v-for="location in locations"
              :key="location.id"
              :label="`${location.code} - ${location.name}`"
              :value="location.id"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="目标库位" required>
          <el-select v-model="createForm.toLocationId" placeholder="请选择目标库位" style="width: 100%">
            <el-option
              v-for="location in locations"
              :key="location.id"
              :label="`${location.code} - ${location.name}`"
              :value="location.id"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="调拨数量" required>
          <el-input v-model="createForm.qty" placeholder="如: 30" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="isCreateVisible = false">取消</el-button>
        <el-button type="primary" :loading="isSubmitting" @click="handleCreateSubmit">
          {{ isSubmitting ? '创建中...' : '提交调拨单' }}
        </el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
/**
 * 库位调拨管理列表视图 (TransferListView)
 * 职责：展示调拨单据，支持发起新调拨与查看详情/确认执行
 */
import { ref, reactive, onMounted } from "vue";
import { Plus } from "@element-plus/icons-vue";
import { ElMessage } from "element-plus";
import PageHeader from "@/components/common/PageHeader.vue";
import FilterBar from "@/components/common/FilterBar.vue";
import DataTable, { type TableColumn } from "@/components/common/DataTable.vue";
import StatusBadge from "@/components/common/StatusBadge.vue";
import QuantityText from "@/components/common/QuantityText.vue";
import EmptyState from "@/components/common/EmptyState.vue";
import ErrorState from "@/components/common/ErrorState.vue";
import TransferDetailView from "./TransferDetailView.vue";
import type { ViewState } from "@/types/common";
import type { TransferOrder, Product, Location } from "@/types/inventory";
import { getLocations, getProducts } from "@/api/masterData";
import { getTransfers, createTransfer, confirmTransfer } from "@/api/inventory";

const viewState = ref<ViewState>("loading");
const errorMessage = ref("");
const transferList = ref<TransferOrder[]>([]);
const totalCount = ref(0);
const products = ref<Product[]>([]);
const locations = ref<Location[]>([]);

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
  productId: "",
  fromLocationId: "",
  toLocationId: "",
  qty: "",
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
    ElMessage.success("调拨确认成功！");
  } catch (err: any) {
    ElMessage.error(err?.message || "确认失败");
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
    const product = products.value.find((item) => item.id === createForm.productId);
    const fromLocation = locations.value.find((item) => item.id === createForm.fromLocationId);
    const toLocation = locations.value.find((item) => item.id === createForm.toLocationId);
    if (!product || !fromLocation || !toLocation) {
      throw new Error("请选择真实的物料和来源/目标库位");
    }
    await createTransfer({
      fromWarehouseId: String(fromLocation.warehouseId),
      fromLocationId: createForm.fromLocationId,
      toWarehouseId: String(toLocation.warehouseId),
      toLocationId: createForm.toLocationId,
      lines: [{
        productId: createForm.productId,
        uom: product.uom,
        quantity: createForm.qty,
      }],
    });
    isCreateVisible.value = false;
    ElMessage.success("调拨单创建成功！");
    await fetchTransfers();
  } catch (err: any) {
    ElMessage.error(err?.message || "创建调拨失败");
  } finally {
    isSubmitting.value = false;
  }
}

/**
 * 加载调拨表单的物料和库位，提交时仅传递后端返回的真实 UUID。
 */
async function loadMasterData() {
  try {
    const [productResponse, locationResponse] = await Promise.all([
      getProducts({ page: 1, size: 1000, status: "ACTIVE" }),
      getLocations({ page: 1, size: 1000, status: "ACTIVE" }),
    ]);
    products.value = productResponse.data.records;
    locations.value = locationResponse.data.records;
  } catch (error) {
    console.error("[TransferListView] 加载物料库位失败", error);
  }
}

onMounted(() => {
  fetchTransfers();
  loadMasterData();
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
