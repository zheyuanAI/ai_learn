<template>
  <div v-if="visible && transfer" class="dialog-mask" @click.self="handleClose">
    <div class="dialog-panel">
      <div class="dialog-header">
        <div class="header-title-box">
          <h3 class="dialog-title">库位调拨单详情</h3>
          <span class="mono-no">{{ transfer.transferNo }}</span>
        </div>
        <button type="button" class="btn-close" @click="handleClose">✕</button>
      </div>

      <div class="dialog-body">
        <div class="status-summary-bar">
          <span class="status-label">当前单据状态：</span>
          <StatusBadge
            :type="transfer.status === 'Confirmed' ? 'success' : transfer.status === 'Draft' ? 'warning' : 'default'"
            :text="transfer.status === 'Confirmed' ? '已确认完成' : transfer.status === 'Draft' ? '待执行确认' : '已取消'"
          />
        </div>

        <div class="info-grid">
          <div class="info-card">
            <span class="card-label">调拨商品物料</span>
            <strong class="card-val">{{ transfer.productName }}</strong>
            <span class="card-sub">{{ transfer.sku }} ({{ transfer.uom }})</span>
            <span v-if="transfer.lotNo" class="card-tag">批次: {{ transfer.lotNo }}</span>
          </div>

          <div class="info-card">
            <span class="card-label">调拨数量</span>
            <div class="qty-highlight">
              <QuantityText :value="transfer.qty" :unit="transfer.uom" />
            </div>
            <span class="card-sub">企业总实物库存保持不变</span>
          </div>
        </div>

        <div class="route-box">
          <div class="route-point">
            <span class="point-badge from">来源 (FROM)</span>
            <strong class="point-name">{{ transfer.fromWarehouseName }}</strong>
            <span class="point-loc">{{ transfer.fromLocationCode }}</span>
          </div>
          <div class="route-arrow-icon">➔ 移位 ➔</div>
          <div class="route-point">
            <span class="point-badge to">目标 (TO)</span>
            <strong class="point-name">{{ transfer.toWarehouseName }}</strong>
            <span class="point-loc">{{ transfer.toLocationCode }}</span>
          </div>
        </div>

        <div class="meta-section">
          <div class="meta-item">
            <label>调拨原因：</label>
            <span>{{ transfer.reason || '无特殊原因说明' }}</span>
          </div>
          <div class="meta-item">
            <label>创建人员/时间：</label>
            <span>{{ transfer.createdBy || 'wh.operator' }} / {{ transfer.createdAt }}</span>
          </div>
          <div v-if="transfer.confirmedAt" class="meta-item">
            <label>确认人员/时间：</label>
            <span class="confirmed-text">{{ transfer.confirmedBy || 'wh.operator' }} / {{ transfer.confirmedAt }}</span>
          </div>
        </div>
      </div>

      <div class="dialog-footer">
        <button type="button" class="btn btn-secondary" @click="handleClose">关闭</button>
        <button
          v-if="canConfirm"
          type="button"
          class="btn btn-primary"
          :disabled="confirming"
          @click="isConfirmOpen = true"
        >
          <span>{{ confirming ? '⏳ 执行中...' : '确认执行调拨' }}</span>
        </button>
      </div>
    </div>

    <!-- 二次防误触确认对话框 -->
    <ConfirmDialog
      v-model:visible="isConfirmOpen"
      title="确认执行库位调拨"
      :message="`确定将 ${transfer.qty} ${transfer.uom} 的物料 ${transfer.productName} 从 ${transfer.fromLocationCode} 调拨至 ${transfer.toLocationCode} 吗？确认后将同步更新库存余额并追加不可篡改流水。`"
      :loading="confirming"
      @confirm="executeConfirm"
    />
  </div>
</template>

<script setup lang="ts">
/**
 * 库位调拨单详情与执行确认组件 (TransferDetailView)
 * 职责：展示调拨来源、目标库位、物料数量，并支持仓库人员执行确认
 * 权限控制：严格基于 allowedActions 判定当前操作是否可执行
 */
import { ref, computed } from "vue";
import StatusBadge from "@/components/common/StatusBadge.vue";
import QuantityText from "@/components/common/QuantityText.vue";
import ConfirmDialog from "@/components/common/ConfirmDialog.vue";
import type { TransferOrder } from "@/types/inventory";

const props = withDefaults(
  defineProps<{
    visible: boolean;
    transfer: TransferOrder | null;
    confirming?: boolean;
  }>(),
  {
    visible: false,
    transfer: null,
    confirming: false,
  }
);

const emit = defineEmits<{
  (e: "update:visible", val: boolean): void;
  (e: "confirm", id: string | number): void;
  (e: "close"): void;
}>();

const isConfirmOpen = ref(false);

const canConfirm = computed(() => {
  if (!props.transfer) return false;
  if (props.transfer.status !== "Draft") return false;
  // 依据 allowedActions 判定
  if (props.transfer.allowedActions) {
    const act = props.transfer.allowedActions.find((a) => a.action === "confirm");
    return act ? act.enabled : true;
  }
  return true;
});

function handleClose() {
  emit("update:visible", false);
  emit("close");
}

function executeConfirm() {
  if (!props.transfer) return;
  isConfirmOpen.value = false;
  emit("confirm", props.transfer.id);
}
</script>

<style scoped>
.dialog-mask {
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

.dialog-panel {
  background: #0f172a;
  border: 1px solid rgba(255, 255, 255, 0.15);
  border-radius: 10px;
  width: 100%;
  max-width: 580px;
  overflow: hidden;
  box-shadow: 0 20px 25px -5px rgba(0, 0, 0, 0.5);
  animation: enter 0.2s ease-out;
}

.dialog-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 16px 20px;
  border-bottom: 1px solid rgba(255, 255, 255, 0.08);
}

.header-title-box {
  display: flex;
  align-items: baseline;
  gap: 10px;
}

.dialog-title {
  margin: 0;
  font-size: 16px;
  font-weight: 600;
  color: #f8fafc;
}

.mono-no {
  font-family: var(--font-mono, monospace);
  font-size: 12px;
  color: #38bdf8;
}

.btn-close {
  background: transparent;
  border: none;
  color: #94a3b8;
  font-size: 16px;
  cursor: pointer;
}

.dialog-body {
  padding: 20px;
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.status-summary-bar {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 13px;
  color: #94a3b8;
}

.info-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 12px;
}

.info-card {
  background: rgba(30, 41, 59, 0.5);
  border: 1px solid rgba(255, 255, 255, 0.06);
  border-radius: 6px;
  padding: 12px;
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.card-label {
  font-size: 11px;
  color: #64748b;
}

.card-val {
  font-size: 14px;
  color: #f1f5f9;
}

.card-sub {
  font-size: 11px;
  color: #94a3b8;
}

.card-tag {
  align-self: flex-start;
  font-size: 10px;
  font-family: var(--font-mono, monospace);
  background: rgba(56, 189, 248, 0.12);
  color: #38bdf8;
  padding: 1px 6px;
  border-radius: 4px;
  margin-top: 4px;
}

.qty-highlight {
  font-size: 20px;
}

.route-box {
  display: flex;
  align-items: center;
  justify-content: space-between;
  background: rgba(15, 23, 42, 0.6);
  border: 1px dashed rgba(255, 255, 255, 0.12);
  border-radius: 8px;
  padding: 14px 18px;
}

.route-point {
  display: flex;
  flex-direction: column;
  gap: 3px;
}

.point-badge {
  font-size: 10px;
  font-weight: 700;
  padding: 1px 6px;
  border-radius: 4px;
  align-self: flex-start;
}

.point-badge.from {
  background: rgba(251, 191, 36, 0.15);
  color: #fbbf24;
}

.point-badge.to {
  background: rgba(52, 211, 153, 0.15);
  color: #34d399;
}

.point-name {
  font-size: 13px;
  color: #f8fafc;
}

.point-loc {
  font-family: var(--font-mono, monospace);
  font-size: 12px;
  color: #38bdf8;
}

.route-arrow-icon {
  font-size: 13px;
  font-weight: 600;
  color: #94a3b8;
}

.meta-section {
  display: flex;
  flex-direction: column;
  gap: 6px;
  font-size: 12px;
  color: #94a3b8;
  background: rgba(0, 0, 0, 0.2);
  padding: 10px 14px;
  border-radius: 6px;
}

.meta-item {
  display: flex;
  align-items: center;
  gap: 8px;
}

.confirmed-text {
  color: #34d399;
}

.dialog-footer {
  display: flex;
  align-items: center;
  justify-content: flex-end;
  gap: 12px;
  padding: 14px 20px;
  background: rgba(0, 0, 0, 0.2);
  border-top: 1px solid rgba(255, 255, 255, 0.08);
}

.btn {
  padding: 8px 16px;
  border-radius: 6px;
  font-size: 13px;
  font-weight: 500;
  cursor: pointer;
  transition: all 0.2s;
  border: 1px solid transparent;
}

.btn-secondary {
  background: rgba(51, 65, 85, 0.6);
  color: #cbd5e1;
  border-color: rgba(255, 255, 255, 0.1);
}

.btn-primary {
  background: #0284c7;
  color: #ffffff;
}

@keyframes enter {
  from {
    opacity: 0;
    transform: scale(0.95);
  }
  to {
    opacity: 1;
    transform: scale(1);
  }
}
</style>
