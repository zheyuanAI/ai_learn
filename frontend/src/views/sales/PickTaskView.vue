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
      <!-- 销售单号 -->
      <template #soNo="{ value }">
        <span class="mono-text">{{ value }}</span>
      </template>

      <template #status="{ value }">
        <StatusBadge :type="value === 'Approved' ? 'info' : 'default'" :text="value" />
      </template>
    </DataTable>

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
import type { SalesOrder } from "@/types/sales";
import { getPickTasks } from "@/api/sales";

const viewState = ref<ViewState>("loading");
const errorMessage = ref("");
const taskList = ref<SalesOrder[]>([]);
const searchKeyword = ref("");

const columns: TableColumn[] = [
  { key: "taskNo", label: "拣货任务号", width: "160px" },
  { key: "soNo", label: "销售订单号", width: "150px" },
  { key: "fulfillmentStatus", label: "服务端履约进度", width: "160px" },
  { key: "status", label: "状态", width: "130px", align: "center" },
  { key: "updatedAt", label: "订单更新时间", width: "160px" },
];

const filteredTaskList = computed(() => {
  if (!searchKeyword.value) return taskList.value;
  const kw = searchKeyword.value.toLowerCase();
  return taskList.value.filter(
    (t) =>
      t.soNo.toLowerCase().includes(kw)
  );
});

async function fetchTasks() {
  viewState.value = "loading";
  errorMessage.value = "";
  try {
    const res = await getPickTasks();
    taskList.value = res.data.records || [];
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
