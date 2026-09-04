<template>
  <div class="data-table-container">
    <div class="table-scroll-wrapper">
      <table class="data-table">
        <thead>
          <tr>
            <th
              v-for="col in columns"
              :key="col.key"
              :style="{ width: col.width, minWidth: col.minWidth, textAlign: col.align || 'left' }"
            >
              {{ col.label }}
            </th>
          </tr>
        </thead>
        <tbody>
          <!-- 加载中遮罩/提示 -->
          <tr v-if="loading">
            <td :colspan="columns.length" class="loading-cell">
              <div class="loading-box">
                <span class="spinner">⏳</span>
                <span>正在加载数据...</span>
              </div>
            </td>
          </tr>

          <!-- 空数据状态 -->
          <tr v-else-if="!data || data.length === 0">
            <td :colspan="columns.length" class="empty-cell">
              <EmptyState :title="emptyText || '暂无数据记录'" />
            </td>
          </tr>

          <!-- 真实数据行 -->
          <tr
            v-else
            v-for="(row, rowIndex) in data"
            :key="getRowKey(row, rowIndex)"
            class="table-row"
            :class="{ 'is-selected': selectedRowKeys?.includes(getRowKey(row, rowIndex)) }"
          >
            <td
              v-for="col in columns"
              :key="col.key"
              :style="{ textAlign: col.align || 'left' }"
            >
              <slot :name="col.key" :row="row" :index="rowIndex" :value="row[col.key]">
                {{ row[col.key] !== null && row[col.key] !== undefined ? row[col.key] : "-" }}
              </slot>
            </td>
          </tr>
        </tbody>
      </table>
    </div>

    <!-- 底部简易分页控制区 -->
    <div v-if="showPagination && total > 0" class="pagination-footer">
      <div class="page-summary">
        共 <strong class="highlight-num">{{ total }}</strong> 条记录，当前第 {{ page }} / {{ totalPages }} 页
      </div>
      <div class="page-controls">
        <button
          type="button"
          class="btn-page"
          :disabled="page <= 1 || loading"
          @click="$emit('page-change', page - 1)"
        >
          上一页
        </button>
        <button
          type="button"
          class="btn-page"
          :disabled="page >= totalPages || loading"
          @click="$emit('page-change', page + 1)"
        >
          下一页
        </button>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed } from "vue";
import EmptyState from "./EmptyState.vue";

export interface TableColumn {
  key: string;
  label: string;
  width?: string;
  minWidth?: string;
  align?: "left" | "center" | "right";
}

const props = withDefaults(
  defineProps<{
    columns: TableColumn[];
    data: any[];
    rowKey?: string | ((row: any) => string | number);
    loading?: boolean;
    emptyText?: string;
    showPagination?: boolean;
    page?: number;
    size?: number;
    total?: number;
    selectedRowKeys?: (string | number)[];
  }>(),
  {
    loading: false,
    emptyText: "暂无数据记录",
    showPagination: true,
    page: 1,
    size: 10,
    total: 0,
    selectedRowKeys: () => [],
  }
);

defineEmits<{
  (e: "page-change", page: number): void;
}>();

const totalPages = computed(() => {
  if (!props.total || !props.size) return 1;
  return Math.ceil(props.total / props.size) || 1;
});

function getRowKey(row: any, index: number): string | number {
  if (typeof props.rowKey === "function") {
    return props.rowKey(row);
  }
  if (props.rowKey && row[props.rowKey] !== undefined) {
    return row[props.rowKey];
  }
  return row.id || index;
}
</script>

<style scoped>
.data-table-container {
  background: rgba(15, 23, 42, 0.7);
  border: 1px solid rgba(255, 255, 255, 0.08);
  border-radius: 8px;
  overflow: hidden;
  display: flex;
  flex-direction: column;
}

.table-scroll-wrapper {
  overflow-x: auto;
  max-width: 100%;
}

.data-table {
  width: 100%;
  border-collapse: collapse;
  text-align: left;
  font-size: 13px;
  color: #e2e8f0;
}

.data-table th {
  background: rgba(30, 41, 59, 0.6);
  color: #94a3b8;
  font-weight: 600;
  padding: 12px 16px;
  border-bottom: 1px solid rgba(255, 255, 255, 0.08);
  white-space: nowrap;
}

.data-table td {
  padding: 12px 16px;
  border-bottom: 1px solid rgba(255, 255, 255, 0.05);
  line-height: 1.4;
}

.table-row:hover td {
  background: rgba(56, 189, 248, 0.04);
}

.table-row.is-selected td {
  background: rgba(56, 189, 248, 0.08);
}

.loading-cell,
.empty-cell {
  padding: 40px 16px;
  text-align: center;
}

.loading-box {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 10px;
  color: #94a3b8;
  font-size: 14px;
}

.pagination-footer {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 12px 18px;
  background: rgba(30, 41, 59, 0.4);
  border-top: 1px solid rgba(255, 255, 255, 0.06);
  font-size: 13px;
  color: #94a3b8;
  flex-wrap: wrap;
  gap: 12px;
}

.highlight-num {
  color: #f1f5f9;
  font-family: var(--font-mono, monospace);
}

.page-controls {
  display: flex;
  align-items: center;
  gap: 8px;
}

.btn-page {
  padding: 5px 12px;
  background: rgba(51, 65, 85, 0.6);
  border: 1px solid rgba(255, 255, 255, 0.1);
  color: #cbd5e1;
  border-radius: 4px;
  font-size: 12px;
  cursor: pointer;
  transition: all 0.2s;
}

.btn-page:hover:not(:disabled) {
  background: rgba(71, 85, 105, 0.8);
  color: #ffffff;
}

.btn-page:disabled {
  opacity: 0.4;
  cursor: not-allowed;
}
</style>
