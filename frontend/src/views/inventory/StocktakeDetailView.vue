<template>
  <div v-if="visible && stocktake" class="dialog-mask" @click.self="handleClose">
    <div class="dialog-panel">
      <div class="dialog-header">
        <div class="header-left">
          <h3 class="dialog-title">差异盘点单详情</h3>
          <span class="mono-no">{{ stocktake.stocktakeNo }}</span>
          <StatusBadge
            :type="stocktake.status === 'ConfirmedAdjusted' ? 'success' : stocktake.status === 'Counting' ? 'warning' : 'default'"
            :text="stocktake.status === 'ConfirmedAdjusted' ? '已确认并调整' : stocktake.status === 'Counting' ? '盘点中' : '未盘点'"
          />
        </div>
        <button type="button" class="btn-close" @click="handleClose">✕</button>
      </div>

      <div class="dialog-body">
        <div class="meta-row">
          <div class="meta-card">
            <span class="lbl">盘点仓库</span>
            <strong>{{ stocktake.warehouseName }}</strong>
          </div>
          <div class="meta-card">
            <span class="lbl">盘点范围</span>
            <strong>{{ stocktake.scopeType === 'FULL' ? '全仓盘点' : '指定库位盘点' }}</strong>
          </div>
          <div class="meta-card">
            <span class="lbl">系统数量快照时点</span>
            <span class="mono-text">{{ stocktake.systemSnapshotAt || stocktake.createdAt }}</span>
          </div>
          <div v-if="stocktake.confirmedAt" class="meta-card">
            <span class="lbl">调整完成时点</span>
            <span class="mono-text text-success">{{ stocktake.confirmedAt }}</span>
          </div>
        </div>

        <div class="lines-container">
          <div class="lines-title-row">
            <h4>盘点物料明细与差异录入</h4>
            <span class="hint-text">实盘录入后自动计算差异；存在差异时必须填写原因说明</span>
          </div>

          <div class="table-scroll">
            <table class="detail-table">
              <thead>
                <tr>
                  <th>物料编码 (SKU)</th>
                  <th>物料名称</th>
                  <th>所在库位</th>
                  <th style="text-align: right;">快照系统量</th>
                  <th style="text-align: right; width: 130px;">实盘录入量</th>
                  <th style="text-align: right;">盘点差异</th>
                  <th>差异原因 (有差异必填)</th>
                </tr>
              </thead>
              <tbody>
                <tr v-for="line in editableLines" :key="line.id">
                  <td class="mono-sku">{{ line.sku }}</td>
                  <td>{{ line.productName }}</td>
                  <td class="mono-loc">{{ line.locationCode }}</td>
                  <td style="text-align: right;">
                    <QuantityText :value="line.systemQty" :unit="line.uom" />
                  </td>
                  <td style="text-align: right;">
                    <input
                      v-if="stocktake.status === 'Counting'"
                      v-model="line.countedQty"
                      type="text"
                      class="counted-input"
                      @input="onCountedChange(line)"
                    />
                    <QuantityText v-else :value="line.countedQty" :unit="line.uom" />
                  </td>
                  <td style="text-align: right;">
                    <span :class="getVarianceClass(line.varianceQty)">
                      <QuantityText :value="line.varianceQty" :unit="line.uom" />
                    </span>
                  </td>
                  <td>
                    <input
                      v-if="stocktake.status === 'Counting'"
                      v-model="line.varianceReason"
                      type="text"
                      class="reason-input"
                      :placeholder="parseFloat(line.varianceQty || '0') !== 0 ? '必须填写差异原因' : '无差异可留空'"
                    />
                    <span v-else class="reason-text">{{ line.varianceReason || '-' }}</span>
                  </td>
                </tr>
              </tbody>
            </table>
          </div>
        </div>
      </div>

      <div class="dialog-footer">
        <button type="button" class="btn btn-secondary" @click="handleClose">关闭</button>
        <template v-if="stocktake.status === 'Counting'">
          <button type="button" class="btn btn-secondary" :disabled="submitting" @click="saveDraftCount">
            暂存实盘数量
          </button>
          <button type="button" class="btn btn-primary" :disabled="submitting" @click="handleOpenConfirm">
            <span>{{ submitting ? '调整中...' : '确认并调整库存' }}</span>
          </button>
        </template>
      </div>
    </div>

    <!-- 确认并调整对话框 -->
    <ConfirmDialog
      v-model:visible="isConfirmOpen"
      title="确认盘点结果并更新库存"
      message="确认后将依据【实盘数量 - 系统数量】自动生成库存调整流水（STOCKTAKE_ADJUST），并正式调整对应库位实时余额。是否继续？"
      :loading="submitting"
      @confirm="executeConfirmAdjustment"
    />
  </div>
</template>

<script setup lang="ts">
/**
 * 差异盘点单详情与实盘调整组件 (StocktakeDetailView)
 * 职责：支持实盘录入、自动计算 variance = counted - system、必填差异原因及调整确认
 * 规则：无差异时不生成调整流水；有差异时生成不可篡改流水并更新库存
 */
import { ref, watch } from "vue";
import StatusBadge from "@/components/common/StatusBadge.vue";
import QuantityText from "@/components/common/QuantityText.vue";
import ConfirmDialog from "@/components/common/ConfirmDialog.vue";
import { type StocktakeOrder, type StocktakeLine, stringSub } from "@/types/inventory";

const props = withDefaults(
  defineProps<{
    visible: boolean;
    stocktake: StocktakeOrder | null;
    submitting?: boolean;
  }>(),
  {
    visible: false,
    stocktake: null,
    submitting: false,
  }
);

const emit = defineEmits<{
  (e: "update:visible", val: boolean): void;
  (e: "record", lines: any[]): void;
  (e: "confirm", payload: any): void;
  (e: "close"): void;
}>();

const editableLines = ref<StocktakeLine[]>([]);
const isConfirmOpen = ref(false);

watch(
  () => props.stocktake,
  (val) => {
    if (val && val.lines) {
      editableLines.value = JSON.parse(JSON.stringify(val.lines));
    } else {
      editableLines.value = [];
    }
  },
  { immediate: true }
);

function onCountedChange(line: StocktakeLine) {
  line.varianceQty = stringSub(line.countedQty || "0", line.systemQty || "0");
}

function getVarianceClass(v?: string) {
  const num = parseFloat(v || "0");
  if (num > 0) return "var-positive";
  if (num < 0) return "var-negative";
  return "var-zero";
}

function handleClose() {
  emit("update:visible", false);
  emit("close");
}

function saveDraftCount() {
  const payload = editableLines.value.map((l) => ({
    lineId: l.id,
    countedQty: l.countedQty || l.systemQty,
    varianceReason: l.varianceReason,
  }));
  emit("record", payload);
}

function handleOpenConfirm() {
  // 校验差异原因
  for (const l of editableLines.value) {
    const v = parseFloat(l.varianceQty || "0");
    if (v !== 0 && (!l.varianceReason || !l.varianceReason.trim())) {
      alert(`物料 ${l.sku} 存在差异量 (${l.varianceQty})，必须填写差异原因！`);
      return;
    }
  }
  isConfirmOpen.value = true;
}

function executeConfirmAdjustment() {
  isConfirmOpen.value = false;
  const linesPayload = editableLines.value.map((l) => ({
    lineId: l.id,
    countedQty: l.countedQty || l.systemQty,
    varianceReason: l.varianceReason,
  }));
  emit("confirm", { lines: linesPayload });
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
  max-width: 820px;
  max-height: 90vh;
  display: flex;
  flex-direction: column;
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

.header-left {
  display: flex;
  align-items: center;
  gap: 12px;
}

.dialog-title {
  margin: 0;
  font-size: 16px;
  font-weight: 600;
  color: #f8fafc;
}

.mono-no {
  font-family: var(--font-mono, monospace);
  font-size: 13px;
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
  overflow-y: auto;
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.meta-row {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(160px, 1fr));
  gap: 10px;
}

.meta-card {
  background: rgba(30, 41, 59, 0.5);
  border: 1px solid rgba(255, 255, 255, 0.06);
  border-radius: 6px;
  padding: 10px 12px;
  display: flex;
  flex-direction: column;
  gap: 3px;
}

.lbl {
  font-size: 11px;
  color: #64748b;
}

.mono-text {
  font-family: var(--font-mono, monospace);
  font-size: 12px;
  color: #cbd5e1;
}

.text-success {
  color: #34d399;
}

.lines-container {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.lines-title-row {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
}

.lines-title-row h4 {
  margin: 0;
  font-size: 14px;
  color: #f1f5f9;
}

.hint-text {
  font-size: 12px;
  color: #94a3b8;
}

.table-scroll {
  overflow-x: auto;
  border: 1px solid rgba(255, 255, 255, 0.08);
  border-radius: 6px;
}

.detail-table {
  width: 100%;
  border-collapse: collapse;
  font-size: 13px;
  color: #e2e8f0;
}

.detail-table th {
  background: rgba(30, 41, 59, 0.8);
  color: #94a3b8;
  padding: 10px 12px;
  border-bottom: 1px solid rgba(255, 255, 255, 0.08);
  white-space: nowrap;
}

.detail-table td {
  padding: 10px 12px;
  border-bottom: 1px solid rgba(255, 255, 255, 0.04);
}

.mono-sku {
  font-family: var(--font-mono, monospace);
  color: #38bdf8;
}

.mono-loc {
  font-family: var(--font-mono, monospace);
  color: #cbd5e1;
}

.counted-input {
  width: 90px;
  background: rgba(15, 23, 42, 0.8);
  border: 1px solid rgba(56, 189, 248, 0.3);
  color: #f8fafc;
  padding: 4px 8px;
  border-radius: 4px;
  text-align: right;
  font-family: var(--font-mono, monospace);
  font-weight: 600;
  outline: none;
}

.reason-input {
  width: 100%;
  min-width: 140px;
  background: rgba(15, 23, 42, 0.8);
  border: 1px solid rgba(255, 255, 255, 0.12);
  color: #f8fafc;
  padding: 4px 8px;
  border-radius: 4px;
  font-size: 12px;
  outline: none;
}

.reason-input:focus {
  border-color: #38bdf8;
}

.var-positive {
  color: #34d399;
}

.var-negative {
  color: #f87171;
}

.var-zero {
  color: #64748b;
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
