<template>
  <div class="trace-node-wrapper" :class="{ 'is-gap-node': node.isGap }">
    <!-- 节点主卡片 -->
    <div
      class="trace-node-card"
      :class="{
        'is-gap': node.isGap,
        'has-jump': node.hasPermission && node.linkedRoute,
      }"
      @click="$emit('select', node)"
    >
      <!-- 卡片头部：类型、编码、状态 -->
      <div class="card-header">
        <div class="type-and-code">
          <span class="type-icon">{{ getNodeIcon(node.nodeType) }}</span>
          <div class="title-block">
            <span class="node-title">{{ node.title }}</span>
            <span class="node-code">{{ node.nodeCode }}</span>
          </div>
        </div>
        <div class="header-status">
          <StatusBadge
            :type="node.statusType || (node.isGap ? 'danger' : 'default')"
            :text="node.status"
            :dot="true"
            :pulsing="node.isGap || node.statusType === 'danger'"
          />
        </div>
      </div>

      <!-- 断链缺口特别警示条 -->
      <div v-if="node.isGap" class="gap-alert-box">
        <div class="gap-badge">⚠️ 闭环断链诊断</div>
        <p class="gap-reason">{{ node.gapReason || "未检测到关联前序事实单据" }}</p>
      </div>

      <!-- 时间戳记录 -->
      <div class="node-time">
        <span class="time-label">发生时间：</span>
        <span class="time-val">{{ node.timestamp }}</span>
      </div>

      <!-- 业务明细指标网格 -->
      <div v-if="node.details && node.details.length > 0" class="details-grid">
        <div
          v-for="(item, idx) in node.details"
          :key="idx"
          class="detail-row"
          :class="{ 'is-warn': item.warn }"
        >
          <span class="detail-label">{{ item.label }}:</span>
          <div class="detail-val-box">
            <QuantityText
              v-if="item.isQuantity"
              :value="item.value"
              :unit="item.unit"
            />
            <span v-else class="detail-text">{{ item.value }}</span>
          </div>
        </div>
      </div>

      <!-- 底部操作与路由跳转入口 -->
      <div class="card-footer">
        <div class="permission-status">
          <span v-if="node.hasPermission" class="perm-ok">✓ 授权可查</span>
          <span v-else class="perm-lock">🔒 权限受限</span>
        </div>

        <button
          v-if="node.hasPermission && node.linkedRoute"
          type="button"
          class="btn-jump"
          title="点击穿透至业务控制台查看原始事实"
          @click.stop="handleNavigate"
        >
          <span>穿透详情</span>
          <span class="jump-arrow">➔</span>
        </button>
      </div>
    </div>

    <!-- 节点间连接器箭头 (正向/反向) -->
    <div v-if="!isLast" class="trace-connector">
      <div class="connector-line"></div>
      <div class="connector-arrow">
        <span class="arrow-symbol">▼</span>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
/**
 * 追溯链单个节点卡片 (TraceNodeCard)
 * 职责：
 * 1. 渲染销售、工单、工序、批次、质检、采购或告警事实节点；
 * 2. 对缺失来源清晰呈现缺口卡片 (isGap)，阐明断链原因与排查处置建议；
 * 3. 严格遵循权限与路由跳转约束；
 * 4. 采用 QuantityText 杜绝浮点失真，采用 StatusBadge 统一颜色语义。
 */

import { useRouter } from "vue-router";
import type { TraceNode, TraceNodeType, TraceDirection } from "../../../types/insights";
import StatusBadge from "../../../components/common/StatusBadge.vue";
import QuantityText from "../../../components/common/QuantityText.vue";

const props = withDefaults(
  defineProps<{
    node: TraceNode;
    isLast?: boolean;
    direction?: TraceDirection;
  }>(),
  {
    isLast: false,
    direction: "FORWARD",
  }
);

const emit = defineEmits<{
  (e: "select", node: TraceNode): void;
  (e: "jump", node: TraceNode): void;
}>();

const router = useRouter();

/**
 * 根据节点类型映射语义图标
 */
function getNodeIcon(type: TraceNodeType): string {
  switch (type) {
    case "SALES_ORDER":
      return "🛒";
    case "WORK_ORDER":
      return "📋";
    case "OPERATION_EXECUTION":
      return "⚙️";
    case "INVENTORY_BATCH":
      return "📦";
    case "QUALITY_INSPECT":
      return "🛡️";
    case "PURCHASE_ORDER":
      return "📑";
    case "DEVICE_ALARM":
      return "🚨";
    case "SHIPMENT":
      return "🚚";
    case "GAP_NODE":
      return "🛑";
    default:
      return "📄";
  }
}

/**
 * 穿透路由跳转
 */
function handleNavigate() {
  emit("jump", props.node);
  if (props.node.linkedRoute) {
    router.push(props.node.linkedRoute);
  }
}
</script>

<style scoped>
.trace-node-wrapper {
  display: flex;
  flex-direction: column;
  align-items: center;
  width: 100%;
  max-width: 620px;
}

.trace-node-card {
  width: 100%;
  background: rgba(15, 23, 42, 0.85);
  border: 1px solid rgba(255, 255, 255, 0.1);
  border-radius: 10px;
  padding: 16px 20px;
  box-shadow: 0 4px 16px rgba(0, 0, 0, 0.25);
  transition: all 0.2s ease;
  cursor: pointer;
  position: relative;
  overflow: hidden;
}

.trace-node-card:hover {
  border-color: rgba(56, 189, 248, 0.4);
  transform: translateY(-2px);
  box-shadow: 0 8px 24px rgba(0, 0, 0, 0.35);
}

/* 缺口卡片/断链特殊样式 */
.trace-node-card.is-gap {
  background: rgba(40, 15, 20, 0.85);
  border: 1.5px dashed rgba(248, 113, 113, 0.6);
  box-shadow: 0 0 16px rgba(239, 68, 68, 0.15);
}

.trace-node-card.is-gap:hover {
  border-color: rgba(248, 113, 113, 0.9);
  box-shadow: 0 0 20px rgba(239, 68, 68, 0.28);
}

.card-header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 12px;
}

.type-and-code {
  display: flex;
  align-items: center;
  gap: 10px;
}

.type-icon {
  font-size: 22px;
  line-height: 1;
  padding: 6px;
  background: rgba(255, 255, 255, 0.05);
  border-radius: 8px;
}

.title-block {
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.node-title {
  font-size: 14px;
  font-weight: 700;
  color: #f1f5f9;
}

.node-code {
  font-size: 12px;
  font-family: var(--font-mono, monospace);
  color: #38bdf8;
}

.gap-alert-box {
  background: rgba(239, 68, 68, 0.15);
  border: 1px solid rgba(239, 68, 68, 0.3);
  border-radius: 6px;
  padding: 10px 14px;
  margin-bottom: 12px;
}

.gap-badge {
  font-size: 11px;
  font-weight: 700;
  color: #fca5a5;
  margin-bottom: 4px;
  letter-spacing: 0.5px;
}

.gap-reason {
  margin: 0;
  font-size: 12px;
  color: #fecaca;
  line-height: 1.5;
}

.node-time {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 11px;
  color: #94a3b8;
  margin-bottom: 12px;
  padding-bottom: 8px;
  border-bottom: 1px dashed rgba(255, 255, 255, 0.07);
}

.time-val {
  font-family: var(--font-mono, monospace);
  color: #cbd5e1;
}

.details-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(220px, 1fr));
  gap: 8px 16px;
  margin-bottom: 14px;
  background: rgba(0, 0, 0, 0.2);
  padding: 10px 12px;
  border-radius: 6px;
}

.detail-row {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  font-size: 12px;
}

.detail-row.is-warn .detail-text {
  color: #f87171;
  font-weight: 600;
}

.detail-label {
  color: #94a3b8;
}

.detail-text {
  color: #e2e8f0;
  font-weight: 500;
}

.card-footer {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding-top: 8px;
  border-top: 1px solid rgba(255, 255, 255, 0.06);
}

.permission-status {
  font-size: 11px;
}

.perm-ok {
  color: #34d399;
}

.perm-lock {
  color: #94a3b8;
}

.btn-jump {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 4px 10px;
  background: rgba(56, 189, 248, 0.12);
  border: 1px solid rgba(56, 189, 248, 0.3);
  border-radius: 4px;
  color: #38bdf8;
  font-size: 11px;
  cursor: pointer;
  transition: all 0.2s ease;
}

.btn-jump:hover {
  background: rgba(56, 189, 248, 0.24);
  border-color: #38bdf8;
  color: #ffffff;
}

.jump-arrow {
  font-size: 10px;
  transition: transform 0.2s ease;
}

.btn-jump:hover .jump-arrow {
  transform: translateX(2px);
}

/* 连接线 */
.trace-connector {
  display: flex;
  flex-direction: column;
  align-items: center;
  height: 42px;
  margin: 4px 0;
}

.connector-line {
  width: 2px;
  flex: 1;
  background: linear-gradient(to bottom, rgba(56, 189, 248, 0.5), rgba(56, 189, 248, 0.8));
}

.connector-arrow {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 18px;
  height: 18px;
  border-radius: 50%;
  background: rgba(15, 23, 42, 0.9);
  border: 1px solid rgba(56, 189, 248, 0.4);
  margin-top: -2px;
}

.arrow-symbol {
  font-size: 9px;
  color: #38bdf8;
}

.is-gap-node + .trace-connector .connector-line {
  background: linear-gradient(to bottom, rgba(248, 113, 113, 0.5), rgba(248, 113, 113, 0.8));
}

.is-gap-node + .trace-connector .connector-arrow {
  border-color: rgba(248, 113, 113, 0.5);
}

.is-gap-node + .trace-connector .arrow-symbol {
  color: #f87171;
}
</style>
