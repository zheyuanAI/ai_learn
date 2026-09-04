<template>
  <div class="master-data-table-wrapper">
    <DataTable
      :columns="columns"
      :data="data"
      :loading="loading"
      :total="total"
      :page="page"
      :size="size"
      @page-change="$emit('page-change', $event)"
    >
      <!-- 状态列插槽 -->
      <template #status="{ value }">
        <StatusBadge
          :type="value === 'ENABLE' || value === 'ACTIVE' || value === 'AVAILABLE' ? 'success' : 'default'"
          :text="statusTextMap[String(value)] || String(value)"
        />
      </template>

      <!-- 库位类型列插槽 -->
      <template #type="{ value }">
        <StatusBadge
          :type="locationTypeBadgeMap[String(value)] || 'info'"
          :text="locationTypeTextMap[String(value)] || String(value)"
        />
      </template>

      <!-- 数量展示插槽 -->
      <template #minStock="{ row }">
        <QuantityText :value="row.minStock" :unit="row.uom" />
      </template>

      <template #maxStock="{ row }">
        <QuantityText :value="row.maxStock" :unit="row.uom" />
      </template>

      <template #safetyStock="{ row }">
        <QuantityText :value="row.safetyStock" :unit="row.uom" />
      </template>

      <template #unitPrice="{ row }">
        <span class="price-cell">¥ {{ row.unitPrice || '0.00' }}</span>
      </template>

      <!-- 批次管理布尔值插槽 -->
      <template #batchMgmt="{ value }">
        <span :class="value ? 'tag-batch' : 'tag-nobatch'">
          {{ value ? "启用批次" : "常规管理" }}
        </span>
      </template>

      <!-- 操作列插槽 -->
      <template #actions="{ row }">
        <div class="action-buttons">
          <button
            type="button"
            class="btn-action"
            @click="$emit('edit', row)"
          >
            编辑
          </button>
        </div>
      </template>
    </DataTable>
  </div>
</template>

<script setup lang="ts">
/**
 * 主数据表格渲染子组件 (MasterDataTable)
 * 职责：复用 DataTable 与 StatusBadge、QuantityText，根据传入的 columns 渲染商品、库位、客商等
 * 入参：columns 表头定义，data 数据数组，loading 加载态，分页字段
 * 事件：page-change 翻页，edit 点击编辑
 */
import DataTable, { type TableColumn } from "@/components/common/DataTable.vue";
import StatusBadge from "@/components/common/StatusBadge.vue";
import QuantityText from "@/components/common/QuantityText.vue";

defineProps<{
  columns: TableColumn[];
  data: any[];
  loading?: boolean;
  total?: number;
  page?: number;
  size?: number;
}>();

defineEmits<{
  (e: "page-change", page: number): void;
  (e: "edit", row: any): void;
}>();

const statusTextMap: Record<string, string> = {
  ENABLE: "启用",
  DISABLE: "停用",
  ACTIVE: "正常",
  INACTIVE: "已冻结",
  AVAILABLE: "空闲可用",
  OCCUPIED: "已占用",
  LOCKED: "已锁定",
};

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
</script>

<style scoped>
.master-data-table-wrapper {
  width: 100%;
}

.price-cell {
  font-family: var(--font-mono, monospace);
  font-weight: 600;
  color: #38bdf8;
}

.tag-batch {
  display: inline-block;
  padding: 2px 6px;
  background: rgba(167, 139, 250, 0.15);
  color: #c084fc;
  border-radius: 4px;
  font-size: 11px;
}

.tag-nobatch {
  display: inline-block;
  padding: 2px 6px;
  background: rgba(148, 163, 184, 0.1);
  color: #94a3b8;
  border-radius: 4px;
  font-size: 11px;
}

.action-buttons {
  display: flex;
  align-items: center;
  gap: 8px;
}

.btn-action {
  background: transparent;
  border: none;
  color: #38bdf8;
  cursor: pointer;
  font-size: 12px;
  padding: 2px 6px;
  border-radius: 4px;
  transition: background 0.2s;
}

.btn-action:hover {
  background: rgba(56, 189, 248, 0.15);
}
</style>
