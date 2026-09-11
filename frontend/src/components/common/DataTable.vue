<template>
  <div class="data-table-container">
    <div class="table-scroll-wrapper">
      <el-table
        v-loading="loading"
        :data="data"
        :row-key="getRowKey"
        stripe
        style="width: 100%"
      >
        <template #empty>
          <EmptyState :title="emptyText || '暂无数据记录'" />
        </template>

        <el-table-column
          v-for="col in columns"
          :key="col.key"
          :prop="col.key"
          :label="col.label"
          :width="col.width"
          :min-width="col.minWidth"
          :align="col.align || 'left'"
        >
          <template #default="{ row, $index }">
            <slot :name="col.key" :row="row" :index="$index" :value="row[col.key]">
              {{ row[col.key] !== null && row[col.key] !== undefined ? row[col.key] : "-" }}
            </slot>
          </template>
        </el-table-column>
      </el-table>
    </div>

    <!-- 底部统一分页区 -->
    <div v-if="showPagination && total > 0" class="pagination-footer">
      <el-pagination
        :current-page="page"
        :page-size="size"
        :total="total"
        layout="total, prev, pager, next, jumper"
        background
        @current-change="$emit('page-change', $event)"
      />
    </div>
  </div>
</template>

<script setup lang="ts">
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

function getRowKey(row: any): string {
  if (typeof props.rowKey === "function") {
    return String(props.rowKey(row));
  }
  if (props.rowKey && row[props.rowKey] !== undefined) {
    return String(row[props.rowKey]);
  }
  return String(row.id || row.key || Math.random());
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

.pagination-footer {
  display: flex;
  align-items: center;
  justify-content: flex-end;
  padding: 12px 16px;
  border-top: 1px solid rgba(255, 255, 255, 0.06);
  background: rgba(15, 23, 42, 0.5);
}
</style>
