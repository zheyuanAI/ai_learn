<template>
  <div class="stocktake-list-view">
    <!-- 统一页面头部 -->
    <PageHeader
      title="差异盘点控制台"
      tag="CORE / INVENTORY / STOCKTAKE"
      description="页面状态流转：未盘点 -> 盘点中 -> 已确认并调整。未盘点生成盘点范围并冻结系统数量快照；盘点中录入实盘数量与差异原因；确认并调整后按【实盘数量 - 系统数量】生成调整流水并更新余额。"
    >
      <template #actions>
        <el-button type="primary" :icon="Plus" @click="isCreateVisible = true">
          新建盘点任务
        </el-button>
      </template>
    </PageHeader>

    <!-- 筛选搜索栏 -->
    <FilterBar
      v-model="queryParams.keyword"
      placeholder="搜索盘点单号..."
      @search="fetchStocktakes"
      @reset="resetFilter"
    >
      <el-select
        v-model="queryParams.status"
        placeholder="全部盘点状态"
        clearable
        style="width: 220px"
        @change="fetchStocktakes"
      >
        <el-option label="全部盘点状态" value="" />
        <el-option label="未盘点 (NotStarted)" value="NotStarted" />
        <el-option label="盘点中 (Counting)" value="Counting" />
        <el-option label="已确认并调整 (ConfirmedAdjusted)" value="ConfirmedAdjusted" />
      </el-select>
    </FilterBar>

    <!-- 四态展示 -->
    <ErrorState
      v-if="viewState === 'error'"
      title="获取盘点列表异常"
      :message="errorMessage"
      @retry="fetchStocktakes"
    />

    <EmptyState
      v-else-if="viewState === 'empty'"
      title="暂无差异盘点记录"
      description="当前未发现盘点任务，您可以点击右上角发起新的盘点计划并冻结快照。"
    >
      <template #action>
        <el-button type="primary" size="small" :icon="Plus" @click="isCreateVisible = true">
          立即新建盘点
        </el-button>
      </template>
    </EmptyState>

    <DataTable
      v-else
      :columns="columns"
      :data="stocktakeList"
      :loading="viewState === 'loading'"
      :total="totalCount"
      :page="queryParams.page"
      :size="queryParams.size"
      @page-change="handlePageChange"
    >
      <!-- 盘点编号 -->
      <template #stocktakeNo="{ value }">
        <span class="mono-code">{{ value }}</span>
      </template>

      <!-- 盘点范围 -->
      <template #scope="{ row }">
        <span>{{ row.scopeType === 'FULL' ? '全仓盘点' : '指定库位盘点' }}</span>
        <span v-if="row.locationCode" class="loc-code">({{ row.locationCode }})</span>
      </template>

      <!-- 盘点状态 -->
      <template #status="{ value }">
        <StatusBadge
          :type="value === 'ConfirmedAdjusted' ? 'success' : value === 'Counting' ? 'warning' : 'default'"
          :text="value === 'ConfirmedAdjusted' ? '已确认并调整' : value === 'Counting' ? '盘点中' : '未盘点'"
        />
      </template>

      <!-- 操作列 -->
      <template #actions="{ row }">
        <el-button
          type="primary"
          link
          size="small"
          @click="openDetail(row)"
        >
          {{ row.status === 'Counting' ? '录入/调整' : '查看详情' }}
        </el-button>
      </template>
    </DataTable>

    <!-- 盘点详情与录入调整弹窗 -->
    <StocktakeDetailView
      v-model:visible="isDetailVisible"
      :stocktake="selectedStocktake"
      :submitting="isSubmitting"
      @record="handleRecordLines"
      @confirm="handleConfirmAdjustment"
      @close="isDetailVisible = false"
    />

    <!-- 新建盘点计划弹窗 -->
    <el-dialog
      v-model="isCreateVisible"
      title="新建差异盘点单"
      width="500px"
      destroy-on-close
    >
      <el-form label-width="110px" @submit.prevent="handleCreateSubmit">
        <el-form-item label="目标仓库" required>
          <el-select v-model="createForm.warehouseId" placeholder="请选择仓库" style="width: 100%">
            <el-option
              v-for="warehouse in warehouses"
              :key="warehouse.id"
              :label="`${warehouse.code} - ${warehouse.name}`"
              :value="warehouse.id"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="指定盘点库位" required>
          <el-select v-model="createForm.locationId" placeholder="请选择库位" style="width: 100%">
            <el-option
              v-for="location in availableLocations"
              :key="location.id"
              :label="`${location.code} - ${location.name}`"
              :value="location.id"
            />
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="isCreateVisible = false">取消</el-button>
        <el-button type="primary" :loading="isCreating" @click="handleCreateSubmit">
          {{ isCreating ? '创建并冻结快照中...' : '确认生成盘点单' }}
        </el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
/**
 * 差异盘点控制台列表视图 (StocktakeListView)
 * 职责：展示盘点任务，支持冻结系统快照、进入实盘录入与调整流水生成
 */
import { ref, reactive, onMounted, computed, watch } from "vue";
import { Plus } from "@element-plus/icons-vue";
import { ElMessage } from "element-plus";
import PageHeader from "@/components/common/PageHeader.vue";
import FilterBar from "@/components/common/FilterBar.vue";
import DataTable, { type TableColumn } from "@/components/common/DataTable.vue";
import StatusBadge from "@/components/common/StatusBadge.vue";
import EmptyState from "@/components/common/EmptyState.vue";
import ErrorState from "@/components/common/ErrorState.vue";
import StocktakeDetailView from "./StocktakeDetailView.vue";
import type { ViewState } from "@/types/common";
import type { StocktakeOrder, Warehouse, Location } from "@/types/inventory";
import { getLocations, getWarehouses } from "@/api/masterData";
import {
  getStocktakes,
  createStocktake,
  getStocktakeById,
  startStocktake,
  confirmStocktake,
} from "@/api/inventory";

const viewState = ref<ViewState>("loading");
const errorMessage = ref("");
const stocktakeList = ref<StocktakeOrder[]>([]);
const totalCount = ref(0);
const warehouses = ref<Warehouse[]>([]);
const locations = ref<Location[]>([]);

const queryParams = reactive({
  page: 1,
  size: 10,
  keyword: "",
  status: "",
});

const columns: TableColumn[] = [
  { key: "stocktakeNo", label: "盘点单号", width: "160px" },
  { key: "warehouseName", label: "盘点仓库", width: "120px" },
  { key: "scope", label: "盘点范围", width: "160px" },
  { key: "status", label: "状态", width: "130px", align: "center" },
  { key: "systemSnapshotAt", label: "快照冻结时点", width: "160px" },
  { key: "confirmedAt", label: "调整完成时点", width: "160px" },
  { key: "actions", label: "操作", width: "110px", align: "center" },
];

const isDetailVisible = ref(false);
const selectedStocktake = ref<StocktakeOrder | null>(null);
const isSubmitting = ref(false);

const isCreateVisible = ref(false);
const isCreating = ref(false);
const createForm = reactive({
  stocktakeNo: "",
  warehouseId: "",
  locationId: "",
});
const availableLocations = computed(() => locations.value.filter(
  (location) => location.warehouseId === createForm.warehouseId,
));

watch(
  () => createForm.warehouseId,
  () => {
    createForm.locationId = "";
  },
);

async function fetchStocktakes() {
  viewState.value = "loading";
  errorMessage.value = "";
  try {
    const res = await getStocktakes({
      page: queryParams.page,
      size: queryParams.size,
      status: queryParams.status,
    });
    stocktakeList.value = res.data.records;
    totalCount.value = res.data.total;
    viewState.value = stocktakeList.value.length === 0 ? "empty" : "ready";
  } catch (err: any) {
    console.error("[StocktakeListView] 查询失败:", err);
    errorMessage.value = err?.message || "网络请求异常";
    viewState.value = "error";
  }
}

function handlePageChange(page: number) {
  queryParams.page = page;
  fetchStocktakes();
}

function resetFilter() {
  queryParams.keyword = "";
  queryParams.status = "";
  queryParams.page = 1;
  fetchStocktakes();
}

/**
 * 打开盘点详情前重新读取后端聚合，确保展示真实明细、状态、版本和允许动作。
 */
async function openDetail(row: StocktakeOrder) {
  try {
    const response = await getStocktakeById(String(row.id));
    selectedStocktake.value = response.data;
    isDetailVisible.value = true;
  } catch (error: any) {
    ElMessage.error(error?.message || "读取盘点详情失败");
  }
}

async function handleRecordLines(_lines: any[]) {
  // 实盘数据在 confirm 时整体提交
}

async function handleConfirmAdjustment(payload: any) {
  if (!selectedStocktake.value) return;
  isSubmitting.value = true;
  try {
    const res = await confirmStocktake(selectedStocktake.value.id, payload);
    selectedStocktake.value = res.data;
    await fetchStocktakes();
    isDetailVisible.value = false;
    ElMessage.success("盘点调整确认成功！");
  } catch (err: any) {
    ElMessage.error(err?.message || "调整失败");
  } finally {
    isSubmitting.value = false;
  }
}

async function handleCreateSubmit() {
  isCreating.value = true;
  try {
    const created = await createStocktake({
      stocktakeNo: createForm.stocktakeNo.trim() || undefined,
      warehouseId: createForm.warehouseId,
      locationId: createForm.locationId,
    });
    const started = await startStocktake(String(created.data.id));
    selectedStocktake.value = (await getStocktakeById(String(started.data.id))).data;
    isCreateVisible.value = false;
    await fetchStocktakes();
    isDetailVisible.value = true;
    ElMessage.success("盘点单创建成功并已开始盘点！");
  } catch (err: any) {
    ElMessage.error(err?.message || "创建盘点单失败");
  } finally {
    isCreating.value = false;
  }
}

/**
 * 加载盘点表单所需的仓库和库位，所有选项值均为后端真实 UUID。
 */
async function loadMasterData() {
  try {
    const [warehouseResponse, locationResponse] = await Promise.all([
      getWarehouses({ page: 1, size: 1000, status: "ACTIVE" }),
      getLocations({ page: 1, size: 1000, status: "ACTIVE" }),
    ]);
    warehouses.value = warehouseResponse.data.records;
    locations.value = locationResponse.data.records;
  } catch (error) {
    console.error("[StocktakeListView] 加载仓库库位失败", error);
  }
}

onMounted(() => {
  fetchStocktakes();
  loadMasterData();
});
</script>

<style scoped>
.stocktake-list-view {
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
  color: #94a3b8;
  margin-left: 4px;
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

.highlight-act {
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
