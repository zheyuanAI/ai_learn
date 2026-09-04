<template>
  <div class="pick-task-view">
    <!-- 页面头部 -->
    <PageHeader
      title="销售直接拣货控制台"
      tag="CORE / SALES / PICKING"
      description="销售正常履约路径关键步骤。直接拣货在同一事务内自动预留所需库存不足部分，将实物及有效预留从来源库位迁移至发货暂存位（ShippingStaging），企业总库存不变；异常退回则将未发货实物移回合法来源位。"
    />

    <!-- 筛选栏 -->
    <FilterBar
      v-model="searchKeyword"
      placeholder="搜索拣货任务号、销售单号、物料编码..."
      @search="fetchTasks"
      @reset="resetSearch"
    />

    <!-- 四态展示 -->
    <ErrorState
      v-if="viewState === 'error'"
      title="获取拣货任务异常"
      :message="errorMessage"
      @retry="fetchTasks"
    />

    <EmptyState
      v-else-if="viewState === 'empty'"
      title="暂无直接拣货任务记录"
      description="在销售订单详情中点击【直接拣货】后，将自动执行预留并在此生成拣货流水记录。"
    />

    <DataTable
      v-else
      :columns="columns"
      :data="filteredTaskList"
      :loading="viewState === 'loading'"
      :total="filteredTaskList.length"
      :page="1"
      :size="10"
    >
      <!-- 任务编号 -->
      <template #taskNo="{ value }">
        <span class="mono-code">{{ value }}</span>
      </template>

      <!-- 销售单号 -->
      <template #soNo="{ value }">
        <span class="mono-text">{{ value }}</span>
      </template>

      <!-- 拣货移位路径 -->
      <template #route="{ row }">
        <div class="route-cell">
          <span class="loc-tag">{{ row.sourceLocationCode }}</span>
          <span class="route-arrow">➔</span>
          <span class="loc-tag target">{{ row.shippingLocationCode }}</span>
        </div>
      </template>

      <!-- 拣货数量 -->
      <template #pickQty="{ row }">
        <QuantityText :value="row.pickQty" :unit="row.uom" />
      </template>

      <!-- 状态 -->
      <template #status="{ value }">
        <StatusBadge
          :type="value === 'Completed' ? 'success' : 'warning'"
          :text="value === 'Completed' ? '拣货入暂存位' : '已退回原库位'"
        />
      </template>
    </DataTable>

    <!-- 直接拣货执行弹窗 (供父组件调用或直接操作) -->
    <div v-if="isPickModalOpen" class="modal-mask" @click.self="isPickModalOpen = false">
      <div class="modal-panel">
        <div class="modal-header">
          <h3 class="modal-title">执行销售直接拣货</h3>
          <button type="button" class="btn-close" @click="isPickModalOpen = false">✕</button>
        </div>
        <form class="modal-body" @submit.prevent="submitDirectPick">
          <div class="rule-hint">
            <strong>底层事务规则：</strong>
            <span>优先消耗当前行未拣预留；不足部分自动在来源库位建立预留，随后实物与预留同步移入发货暂存位（{{ pickForm.shippingLocationCode || 'SHP-01' }}）。</span>
          </div>

          <div class="info-card">
            <span class="lbl">物料信息</span>
            <strong>{{ currentLine?.productName }} ({{ currentLine?.sku }})</strong>
            <span class="sub">订购量: {{ currentLine?.orderedQty }} | 已拣: {{ currentLine?.pickedQty }} | 可拣上限: {{ maxPickable }}</span>
          </div>

          <div class="form-item">
            <label>来源拣选库位 (Source Location) <span class="req">*</span></label>
            <select v-model="pickForm.sourceLocationId" class="form-select" required>
              <option value="6">FG-A-01 (成品常规存储位01 - 可用 400)</option>
              <option value="4">ST-B-02 (标准件存储位B02 - 可用 300)</option>
            </select>
          </div>

          <div class="form-item">
            <label>发货暂存库位 (ShippingStaging) <span class="req">*</span></label>
            <input :value="pickForm.shippingLocationCode || 'SHP-01'" type="text" class="form-input" disabled />
          </div>

          <div class="form-item">
            <label>本次拣货数量 <span class="req">*</span></label>
            <input v-model="pickForm.pickedQty" type="text" class="form-input text-cyan font-bold" required />
          </div>

          <div class="modal-footer">
            <button type="button" class="btn-secondary" @click="isPickModalOpen = false">取消</button>
            <button type="submit" class="btn-primary" :disabled="submitting">
              {{ submitting ? '拣货移位中...' : '确认直接拣货' }}
            </button>
          </div>
        </form>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
/**
 * 销售直接拣货视图组件 (PickTaskView)
 * 职责：展示拣货任务历史，支持执行直接拣货（自动补足预留并迁移至发货暂存位）
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
import type { PickTask, SalesOrderLine } from "@/types/sales";
import { stringSub } from "@/types/inventory";
import { getPickTasks, confirmDirectPick } from "@/api/sales";

const viewState = ref<ViewState>("loading");
const errorMessage = ref("");
const taskList = ref<PickTask[]>([]);
const searchKeyword = ref("");

const columns: TableColumn[] = [
  { key: "taskNo", label: "拣货任务号", width: "160px" },
  { key: "soNo", label: "销售订单号", width: "150px" },
  { key: "sku", label: "物料编码", width: "130px" },
  { key: "productName", label: "物料名称", minWidth: "140px" },
  { key: "route", label: "移位路径 (来源 ➔ 发货暂存)", width: "230px" },
  { key: "pickQty", label: "拣货数量", width: "110px", align: "right" },
  { key: "status", label: "状态", width: "130px", align: "center" },
  { key: "confirmedAt", label: "拣货确认时间", width: "160px" },
];

const filteredTaskList = computed(() => {
  if (!searchKeyword.value) return taskList.value;
  const kw = searchKeyword.value.toLowerCase();
  return taskList.value.filter(
    (t) =>
      t.taskNo.toLowerCase().includes(kw) ||
      t.soNo.toLowerCase().includes(kw) ||
      t.sku.toLowerCase().includes(kw)
  );
});

// 弹窗表单状态
const isPickModalOpen = ref(false);
const submitting = ref(false);
const currentOrderId = ref<string | number>("");
const currentLine = ref<SalesOrderLine | null>(null);

const pickForm = reactive({
  sourceLocationId: "6",
  sourceLocationCode: "FG-A-01",
  shippingLocationId: "7",
  shippingLocationCode: "SHP-01",
  pickedQty: "20",
});

const maxPickable = computed(() => {
  if (!currentLine.value) return "0";
  return stringSub(currentLine.value.orderedQty, currentLine.value.pickedQty);
});

async function fetchTasks() {
  viewState.value = "loading";
  errorMessage.value = "";
  try {
    const res = await getPickTasks();
    taskList.value = res.data.records;
    viewState.value = taskList.value.length === 0 ? "empty" : "ready";
  } catch (err: any) {
    console.error("[PickTaskView] 获取失败:", err);
    errorMessage.value = err?.message || "网络请求异常";
    viewState.value = "error";
  }
}

function resetSearch() {
  searchKeyword.value = "";
  fetchTasks();
}

/**
 * 供外部调用的直接拣货弹窗打开方法
 */
function triggerDirectPickModal(orderId: string | number, line: SalesOrderLine) {
  currentOrderId.value = orderId;
  currentLine.value = line;
  pickForm.pickedQty = stringSub(line.orderedQty, line.pickedQty);
  isPickModalOpen.value = true;
}

defineExpose({
  triggerDirectPickModal,
  fetchTasks,
});

async function submitDirectPick() {
  if (!currentLine.value || !currentOrderId.value) return;
  submitting.value = true;
  try {
    await confirmDirectPick({
      salesOrderId: currentOrderId.value,
      salesOrderLineId: currentLine.value.id,
      productId: currentLine.value.productId,
      pickedQty: pickForm.pickedQty,
      sourceLocationId: pickForm.sourceLocationId,
      shippingLocationId: pickForm.shippingLocationId,
    });
    isPickModalOpen.value = false;
    await fetchTasks();
  } catch (err: any) {
    alert(err?.message || "拣货失败");
  } finally {
    submitting.value = false;
  }
}

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

.mono-code {
  font-family: var(--font-mono, monospace);
  font-size: 12px;
  color: #38bdf8;
}

.mono-text {
  font-family: var(--font-mono, monospace);
  font-size: 12px;
  color: #cbd5e1;
}

.route-cell {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 12px;
}

.loc-tag {
  font-family: var(--font-mono, monospace);
  background: rgba(30, 41, 59, 0.6);
  padding: 2px 6px;
  border-radius: 4px;
  color: #cbd5e1;
}

.loc-tag.target {
  color: #38bdf8;
  border: 1px solid rgba(56, 189, 248, 0.3);
}

.route-arrow {
  color: #94a3b8;
  font-size: 10px;
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

.rule-hint {
  font-size: 12px;
  line-height: 1.5;
  color: #cbd5e1;
  background: rgba(56, 189, 248, 0.08);
  border: 1px solid rgba(56, 189, 248, 0.25);
  border-radius: 6px;
  padding: 10px;
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

.text-cyan {
  color: #38bdf8;
}

.font-bold {
  font-weight: 700;
  font-size: 16px;
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
