<template>
  <div class="putaway-task-view">
    <CommandFeedback :error="lastError" :can-retry="canRetry" :executing="isExecuting" @retry="retry" />
    <!-- 统一页面头部 -->
    <PageHeader
      title="采购上架任务控制台"
      tag="CORE / INVENTORY / PUTAWAY"
      description="货物经生产质检合格并放行移入收货暂存位（ReceivingStaging）后生成待上架任务。确认上架只将实物从暂存位移至目标常规存储位（Storage），不重复增加企业总实物库存。"
    />

    <!-- 筛选搜索栏 -->
    <FilterBar
      v-model="queryParams.keyword"
      placeholder="搜索上架任务号、采购单号、物料编码..."
      @search="fetchPutawayTasks"
      @reset="resetFilter"
    >
      <select v-model="queryParams.status" class="filter-select" @change="fetchPutawayTasks">
        <option value="">全部上架状态</option>
        <option value="Pending">待上架 (Pending)</option>
        <option value="Confirmed">已确认完成 (Confirmed)</option>
      </select>
    </FilterBar>

    <!-- 四态展示 -->
    <ErrorState
      v-if="viewState === 'error'"
      title="获取上架任务异常"
      :message="errorMessage"
      @retry="fetchPutawayTasks"
    />

    <EmptyState
      v-else-if="viewState === 'empty'"
      title="暂无待处理上架任务"
      description="当前收货暂存位无待上架合格货物，完成采购质检放行后将自动生成上架指令。"
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
      <!-- 任务编号 -->
      <template #taskNo="{ value }">
        <span class="mono-code">{{ value }}</span>
      </template>

      <!-- 采购单号 -->
      <template #poNo="{ value }">
        <span class="mono-text">{{ value }}</span>
      </template>

      <!-- 上架数量 -->
      <template #putawayQty="{ row }">
        <QuantityText :value="row.putawayQty" :unit="row.uom" />
      </template>

      <!-- 移位路径 -->
      <template #route="{ row }">
        <div class="route-cell">
          <span class="loc-code">{{ sourceLocations[String(row.fromLocationId)]?.code || row.fromLocationId }} (收货暂存)</span>
          <span class="route-arrow">➔</span>
          <span class="loc-code target">{{ row.toLocationCode || row.toLocationId || '待选存储位' }}</span>
        </div>
      </template>

      <!-- 状态 -->
      <template #status="{ value }">
        <StatusBadge
          :type="value === 'Confirmed' ? 'success' : 'warning'"
          :text="value === 'Confirmed' ? '已上架存储' : '待执行上架'"
        />
      </template>

      <!-- 操作列 -->
      <template #actions="{ row }">
        <button
          v-if="row.status === 'Pending'"
          type="button"
          class="btn-action-primary"
          @click="openConfirmModal(row)"
        >
          确认上架
        </button>
        <span v-else class="text-muted">已入库</span>
      </template>
    </DataTable>

    <!-- 确认上架弹窗 -->
    <div v-if="isConfirmModalOpen && selectedTask" class="modal-mask" @click.self="isConfirmModalOpen = false">
      <div class="modal-panel">
        <div class="modal-header">
          <h3 class="modal-title">确认执行货物上架</h3>
          <button type="button" class="btn-close" @click="isConfirmModalOpen = false">✕</button>
        </div>
        <form class="modal-body" @submit.prevent="submitPutaway">
          <div class="info-card">
            <span class="lbl">物料信息</span>
            <strong>{{ selectedTask.productName }} ({{ selectedTask.sku }})</strong>
            <span class="sub">来源暂存位: {{ selectedTask.fromLocationCode }} | 批次: {{ selectedTask.lotNo || '-' }}</span>
          </div>
          <div class="form-item">
            <label>目标常规存储库位 (Storage) <span class="req">*</span></label>
            <select v-model="targetLocationId" class="form-select" required>
              <option value="">请选择 Storage 库位</option>
              <option v-for="location in filteredStorageLocations" :key="location.id" :value="location.id">
                {{ location.code }} - {{ location.name }} {{ location.warehouseName ? `(${location.warehouseName})` : '' }}
              </option>
            </select>
          </div>
          <div class="form-item">
            <label>本次上架数量 <span class="req">*</span></label>
            <input v-model="putawayQtyInput" type="text" class="form-input" required />
          </div>
          <div class="modal-footer">
            <button type="button" class="btn-secondary" @click="isConfirmModalOpen = false">取消</button>
            <button type="submit" class="btn-primary" :disabled="isSubmitting">
              {{ isSubmitting ? '上架执行中...' : '确认移入目标库位' }}
            </button>
          </div>
        </form>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
/**
 * 采购上架任务管理视图 (PutawayTaskView)
 * 职责：展示待上架任务，指定目标常规存储库位并确认实物上架
 */
import { ref, reactive, computed, onMounted } from "vue";
import { useCommand } from "@/composables/useCommand";
import CommandFeedback from "@/components/common/CommandFeedback.vue";
import PageHeader from "@/components/common/PageHeader.vue";
import FilterBar from "@/components/common/FilterBar.vue";
import DataTable, { type TableColumn } from "@/components/common/DataTable.vue";
import StatusBadge from "@/components/common/StatusBadge.vue";
import QuantityText from "@/components/common/QuantityText.vue";
import EmptyState from "@/components/common/EmptyState.vue";
import ErrorState from "@/components/common/ErrorState.vue";
import type { ViewState } from "@/types/common";
import type { PutawayTask } from "@/types/purchasing";
import type { Location } from "@/types/inventory";
import { getLocationById, getLocations } from "@/api/masterData";
import { getPutawayTasks, confirmPutawayTask } from "@/api/purchasing";

const viewState = ref<ViewState>("loading");
const errorMessage = ref("");
const taskList = ref<PutawayTask[]>([]);
const totalCount = ref(0);
const storageLocations = ref<Location[]>([]);
const sourceLocations = ref<Record<string, Location>>({});

/** 根据当前上架任务所属仓库，过滤出同仓 Storage 库位 */
const filteredStorageLocations = computed(() => {
  return selectedTask.value ? storageLocations.value : [];
});

const queryParams = reactive({
  page: 1,
  size: 10,
  keyword: "",
  status: "",
});

const columns: TableColumn[] = [
  { key: "taskNo", label: "上架任务号", width: "150px" },
  { key: "poNo", label: "采购订单号", width: "140px" },
  { key: "sku", label: "物料编码", width: "130px" },
  { key: "productName", label: "物料名称", minWidth: "140px" },
  { key: "putawayQty", label: "上架数量", width: "110px", align: "right" },
  { key: "route", label: "移位路径 (From ➔ To)", width: "220px" },
  { key: "status", label: "任务状态", width: "120px", align: "center" },
  { key: "createdAt", label: "放行时间", width: "160px" },
  { key: "actions", label: "操作", width: "110px", align: "center" },
];

const isConfirmModalOpen = ref(false);
const selectedTask = ref<PutawayTask | null>(null);
const targetLocationId = ref("");
const putawayQtyInput = ref("");
const { execute, retry, isExecuting, canRetry, lastError } = useCommand();
const isSubmitting = isExecuting;

async function fetchPutawayTasks() {
  viewState.value = "loading";
  errorMessage.value = "";
  try {
    const res = await getPutawayTasks({
      page: queryParams.page,
      size: queryParams.size,
      status: queryParams.status || undefined,
    });
    taskList.value = res.data.records;
    totalCount.value = res.data.total;
    await loadSourceLocations(taskList.value);
    viewState.value = taskList.value.length === 0 ? "empty" : "ready";
  } catch (err: any) {
    console.error("[PutawayTaskView] 获取失败:", err);
    errorMessage.value = err?.message || "网络请求异常";
    viewState.value = "error";
  }
}

function handlePageChange(page: number) {
  queryParams.page = page;
  fetchPutawayTasks();
}

function resetFilter() {
  queryParams.keyword = "";
  queryParams.status = "";
  queryParams.page = 1;
  fetchPutawayTasks();
}

/**
 * 用途：加载当前上架任务来源暂存位及同仓 Storage 库位。
 * 入参：后端返回的真实上架任务；出参：无，更新当前弹窗可选库位。
 * 流程：先按任务真实 fromLocationId 读取库位，再按返回的 warehouseId 查询启用 Storage；缺事实时保持空集合。
 */
async function loadSourceLocations(tasks: PutawayTask[]) {
  const entries = await Promise.all(tasks
    .filter((task) => task.fromLocationId)
    .map(async (task) => {
      try {
        const response = await getLocationById(String(task.fromLocationId));
        return [String(task.fromLocationId), response.data] as const;
      } catch (error) {
        console.error("[PutawayTaskView] 加载来源库位失败", error);
        return null;
      }
    }));
  sourceLocations.value = Object.fromEntries(entries.filter((entry): entry is readonly [string, Location] => Boolean(entry)));
}

async function loadStorageLocationsForTask(task: PutawayTask): Promise<boolean> {
  if (!task.fromLocationId) {
    storageLocations.value = [];
    return false;
  }
  const source = sourceLocations.value[String(task.fromLocationId)];
  if (!source || source.type !== "ReceivingStaging" || !source.warehouseId) {
    storageLocations.value = [];
    return false;
  }
  try {
    const response = await getLocations({
      page: 1,
      size: 20,
      warehouseId: source.warehouseId,
      type: "Storage",
      status: "ACTIVE",
    });
    storageLocations.value = response.data.records.filter(
      (location) => String(location.warehouseId) === String(source.warehouseId)
        && location.type === "Storage"
        && location.status === "ACTIVE",
    );
    return storageLocations.value.length > 0;
  } catch (error) {
    storageLocations.value = [];
    console.error("[PutawayTaskView] 加载同仓 Storage 库位失败", error);
    return false;
  }
}

async function openConfirmModal(row: PutawayTask) {
  selectedTask.value = row;
  if (!(await loadStorageLocationsForTask(row))) {
    targetLocationId.value = "";
    alert("未找到该上架任务来源仓库下启用的 Storage 库位，请先维护真实库位。");
    selectedTask.value = null;
    return;
  }
  targetLocationId.value = row.toLocationId && filteredStorageLocations.value.some(
    (location) => String(location.id) === String(row.toLocationId),
  ) ? String(row.toLocationId) : "";
  putawayQtyInput.value = row.putawayQty;
  isConfirmModalOpen.value = true;
}

async function submitPutaway() {
  if (!selectedTask.value) return;
  const task = selectedTask.value;
  if (!targetLocationId.value || !filteredStorageLocations.value.some(
    (location) => String(location.id) === String(targetLocationId.value),
  )) {
    alert("请选择当前来源仓库下真实、启用的 Storage 库位。");
    return;
  }
  if (!putawayQtyInput.value || Number.parseFloat(putawayQtyInput.value) <= 0) {
    alert("上架数量必须来自真实任务且大于 0。");
    return;
  }
  try {
    await execute((key) => confirmPutawayTask(String(task.id), {
      taskId: String(task.id),
      toLocationId: targetLocationId.value,
      putawayQty: putawayQtyInput.value,
    }, key), { onConflict: fetchPutawayTasks });
    isConfirmModalOpen.value = false;
    await fetchPutawayTasks();
  } catch (err: any) {
    alert(err?.message || "上架确认失败");
  } finally { /* useCommand 在 finally 中恢复 isExecuting。 */ }
}

onMounted(() => {
  fetchPutawayTasks();
});
</script>

<style scoped>
.putaway-task-view {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.mono-code {
  font-family: var(--font-mono, monospace);
  color: #38bdf8;
  font-size: 12px;
}

.mono-text {
  font-family: var(--font-mono, monospace);
  color: #cbd5e1;
  font-size: 12px;
}

.route-cell {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 12px;
}

.loc-code {
  font-family: var(--font-mono, monospace);
  background: rgba(30, 41, 59, 0.6);
  padding: 2px 6px;
  border-radius: 4px;
}

.loc-code.target {
  color: #34d399;
}

.route-arrow {
  color: #38bdf8;
  font-size: 10px;
}

.text-muted {
  color: #64748b;
  font-size: 12px;
}

.btn-action-primary {
  padding: 4px 10px;
  background: rgba(56, 189, 248, 0.15);
  border: 1px solid rgba(56, 189, 248, 0.3);
  color: #38bdf8;
  border-radius: 4px;
  font-size: 12px;
  cursor: pointer;
}

.btn-action-primary:hover {
  background: #0284c7;
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

/* 模态框 */
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
  max-width: 500px;
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

.info-card {
  background: rgba(30, 41, 59, 0.5);
  border: 1px solid rgba(255, 255, 255, 0.06);
  border-radius: 6px;
  padding: 12px;
  display: flex;
  flex-direction: column;
  gap: 3px;
}

.lbl {
  font-size: 11px;
  color: #64748b;
}

.sub {
  font-size: 11px;
  color: #94a3b8;
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

.btn-primary {
  padding: 8px 16px;
  background: #0284c7;
  color: #ffffff;
  border: 1px solid #0369a1;
  border-radius: 6px;
  font-size: 13px;
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
</style>
