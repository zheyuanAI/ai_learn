<template>
  <div v-if="items.length" class="tool-progress">
    <div v-for="item in items" :key="item.callId" class="tool-row">
      <span class="tool-state" :class="`is-${item.status}`"></span>
      <div>
        <strong>{{ toolLabel(item.toolName) }}</strong>
        <small>{{ statusLabel(item.status) }}<template v-if="item.sourceSummary"> · {{ item.sourceSummary }}</template></small>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import type { AiToolProgressItem } from "../../../types/ai";

defineProps<{ items: AiToolProgressItem[] }>();

const TOOL_LABELS: Record<string, string> = {
  queryTrace: "查询黄金闭环追溯",
  queryLowStock: "查询低库存",
  queryInventoryByProductAndWarehouse: "查询库存余额",
  querySalesOrderStatus: "查询销售订单",
  queryPurchaseOrderStatus: "查询采购订单",
  queryWorkOrderProgress: "查询生产进度",
  queryQualityStatistics: "查询质量统计",
  queryDeviceAlarm: "查询设备告警",
  generateDailyOperationReport: "生成运营日报",
};

function toolLabel(name: string): string {
  return TOOL_LABELS[name] || name;
}

function statusLabel(status: AiToolProgressItem["status"]): string {
  return status === "running" ? "查询中" : status === "success" ? "已完成" : "查询失败";
}
</script>

<style scoped>
.tool-progress { display: grid; gap: 8px; margin: 12px 0; padding: 12px; border: 1px solid #dbe7f5; border-radius: 12px; background: #f8fbff; }
.tool-row { display: flex; align-items: flex-start; gap: 10px; color: #29415e; }
.tool-row strong { display: block; font-size: 13px; }
.tool-row small { display: block; margin-top: 2px; color: #71839a; line-height: 1.45; }
.tool-state { width: 9px; height: 9px; margin-top: 5px; border-radius: 50%; background: #91a2b7; }
.tool-state.is-running { background: #2d7ff9; box-shadow: 0 0 0 4px rgba(45, 127, 249, .12); animation: pulse 1.2s infinite; }
.tool-state.is-success { background: #27a66c; }
.tool-state.is-failed { background: #d94d5c; }
@keyframes pulse { 50% { opacity: .45; } }
</style>
