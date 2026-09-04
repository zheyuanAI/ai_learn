<template>
  <div v-if="visible && order" class="dialog-mask" @click.self="handleClose">
    <div class="dialog-panel">
      <div class="dialog-header">
        <div class="header-left">
          <h3 class="dialog-title">仓库到货外观验收与接收</h3>
          <span class="mono-no">{{ order.poNo }}</span>
        </div>
        <button type="button" class="btn-close" @click="handleClose">✕</button>
      </div>

      <div class="dialog-body">
        <!-- 业务规则提示框 -->
        <div class="rule-alert">
          <div class="alert-icon">ℹ️</div>
          <div class="alert-text">
            <strong>业务规则约束：</strong>
            <span>到货数量 = 拒收数量 + 实际接收数量 (arrived_qty = rejected_qty + received_qty)。外观破损拒收数量不入库并保留为采购待收余量；实际接收货物全部进入 QualityHold（质量隔离位），质检放行前严禁上架或领料。</span>
          </div>
        </div>

        <div class="meta-inputs">
          <div class="input-item">
            <label>到货验收时间 <span class="req">*</span></label>
            <input v-model="receiptTime" type="datetime-local" class="form-input" required />
          </div>
          <div class="input-item">
            <label>入库隔离库位 (QualityHold) <span class="req">*</span></label>
            <input :value="order.qualityHoldLocationCode || 'QH-01'" type="text" class="form-input" disabled />
          </div>
        </div>

        <div class="lines-box">
          <h4>到货明细数量录入</h4>
          <div class="table-scroll">
            <table class="receipt-table">
              <thead>
                <tr>
                  <th>物料 SKU / 名称</th>
                  <th style="text-align: right;">订单总需求</th>
                  <th style="text-align: right;">当前待收余量</th>
                  <th style="text-align: right; width: 100px;">本次到货量 <span class="req">*</span></th>
                  <th style="text-align: right; width: 100px;">外观拒收量</th>
                  <th style="text-align: right; width: 100px;">实际接收量</th>
                  <th style="width: 130px;">批次编号</th>
                  <th style="min-width: 140px;">拒收原因 (拒收>0必填)</th>
                </tr>
              </thead>
              <tbody>
                <tr v-for="line in receiptLines" :key="line.poLineId">
                  <td>
                    <div class="sku-cell">
                      <span class="sku-text">{{ line.sku }}</span>
                      <span class="prod-text">{{ line.productName }}</span>
                    </div>
                  </td>
                  <td style="text-align: right;">
                    <QuantityText :value="line.orderedQty" :unit="line.uom" />
                  </td>
                  <td style="text-align: right;">
                    <QuantityText :value="line.pendingQty" :unit="line.uom" />
                  </td>
                  <td>
                    <input
                      v-model="line.arrivedQty"
                      type="text"
                      class="qty-input"
                      @input="onArrivedOrRejectedChange(line)"
                    />
                  </td>
                  <td>
                    <input
                      v-model="line.rejectedQty"
                      type="text"
                      class="qty-input text-danger"
                      @input="onArrivedOrRejectedChange(line)"
                    />
                  </td>
                  <td>
                    <input
                      v-model="line.receivedQty"
                      type="text"
                      class="qty-input text-success"
                      disabled
                    />
                  </td>
                  <td>
                    <input v-model="line.lotNo" type="text" class="text-input mono-text" placeholder="LOT-..." />
                  </td>
                  <td>
                    <input
                      v-model="line.rejectionReason"
                      type="text"
                      class="text-input"
                      :placeholder="parseFloat(line.rejectedQty || '0') > 0 ? '必填拒收原因' : '无拒收可留空'"
                    />
                  </td>
                </tr>
              </tbody>
            </table>
          </div>
        </div>
      </div>

      <div class="dialog-footer">
        <button type="button" class="btn btn-secondary" @click="handleClose">取消</button>
        <button type="button" class="btn btn-primary" :disabled="submitting" @click="handleSubmit">
          <span v-if="submitting">⏳ 提交中...</span>
          <span v-else>确认接收进质量隔离位</span>
        </button>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
/**
 * 采购收货外观验收与接收对话框组件 (ReceiptConfirmView)
 * 职责：按行录入到货量、拒收量、实际接收量，校验数量恒等式与拒收原因
 * 数量恒等式：arrived_qty = rejected_qty + received_qty
 */
import { ref, watch } from "vue";
import QuantityText from "@/components/common/QuantityText.vue";
import { type PurchaseOrder } from "@/types/purchasing";
import { stringSub, stringCompare } from "@/types/inventory";

interface EditableReceiptLine {
  poLineId: string | number;
  productId: string | number;
  sku: string;
  productName: string;
  uom: string;
  orderedQty: string;
  pendingQty: string;
  arrivedQty: string;
  rejectedQty: string;
  receivedQty: string;
  lotNo?: string;
  rejectionReason?: string;
}

const props = withDefaults(
  defineProps<{
    visible: boolean;
    order: PurchaseOrder | null;
    submitting?: boolean;
  }>(),
  {
    visible: false,
    order: null,
    submitting: false,
  }
);

const emit = defineEmits<{
  (e: "update:visible", val: boolean): void;
  (e: "confirm", payload: any): void;
  (e: "close"): void;
}>();

const receiptTime = ref(new Date().toISOString().slice(0, 16));
const receiptLines = ref<EditableReceiptLine[]>([]);

watch(
  () => props.order,
  (val) => {
    if (val && val.lines) {
      receiptLines.value = val.lines.map((l) => {
        const pending = parseFloat(l.pendingQty || "0") > 0 ? l.pendingQty : l.orderedQty;
        return {
          poLineId: l.id,
          productId: l.productId,
          sku: l.sku,
          productName: l.productName,
          uom: l.uom,
          orderedQty: l.orderedQty,
          pendingQty: l.pendingQty,
          arrivedQty: pending,
          rejectedQty: "0",
          receivedQty: pending,
          lotNo: `LOT-${new Date().toISOString().slice(0, 10).replace(/-/g, "")}-01`,
          rejectionReason: "",
        };
      });
    }
  },
  { immediate: true }
);

function onArrivedOrRejectedChange(line: EditableReceiptLine) {
  const arrived = parseFloat(line.arrivedQty || "0");
  const rejected = parseFloat(line.rejectedQty || "0");
  if (rejected > arrived) {
    line.rejectedQty = String(arrived);
  }
  line.receivedQty = stringSub(line.arrivedQty || "0", line.rejectedQty || "0");
}

function handleClose() {
  emit("update:visible", false);
  emit("close");
}

function handleSubmit() {
  if (!props.order) return;

  for (const l of receiptLines.value) {
    const arrived = parseFloat(l.arrivedQty || "0");
    const rejected = parseFloat(l.rejectedQty || "0");
    const received = parseFloat(l.receivedQty || "0");

    if (arrived <= 0) {
      alert(`物料 ${l.sku} 的到货数量必须大于0`);
      return;
    }

    if (stringCompare(l.arrivedQty, stringSub(l.arrivedQty, "0")) !== 0 && arrived !== rejected + received) {
      alert(`行项数量不守恒：到货数量必须等于拒收数量 + 实际接收数量`);
      return;
    }

    if (rejected > 0 && (!l.rejectionReason || !l.rejectionReason.trim())) {
      alert(`物料 ${l.sku} 存在拒收数量 (${l.rejectedQty})，必须填写拒收原因！`);
      return;
    }
  }

  const payload = {
    purchaseOrderId: props.order.id,
    receiptTime: receiptTime.value.replace("T", " ") + ":00",
    qualityHoldLocationId: props.order.qualityHoldLocationId || "1",
    lines: receiptLines.value.map((l) => ({
      poLineId: l.poLineId,
      productId: l.productId,
      arrivedQty: l.arrivedQty,
      rejectedQty: l.rejectedQty,
      receivedQty: l.receivedQty,
      rejectionReason: l.rejectionReason,
      lotNo: l.lotNo,
    })),
  };

  emit("confirm", payload);
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
  max-width: 900px;
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
  align-items: baseline;
  gap: 12px;
}

.dialog-title {
  margin: 0;
  font-size: 16px;
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
  overflow-y: auto;
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.rule-alert {
  display: flex;
  align-items: flex-start;
  gap: 10px;
  background: rgba(56, 189, 248, 0.08);
  border: 1px solid rgba(56, 189, 248, 0.25);
  border-radius: 6px;
  padding: 12px;
}

.alert-icon {
  font-size: 16px;
}

.alert-text {
  font-size: 12px;
  line-height: 1.5;
  color: #cbd5e1;
}

.meta-inputs {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 16px;
}

.input-item {
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

.form-input {
  background: rgba(30, 41, 59, 0.8);
  border: 1px solid rgba(255, 255, 255, 0.12);
  color: #f8fafc;
  padding: 7px 10px;
  border-radius: 6px;
  font-size: 13px;
  outline: none;
}

.lines-box h4 {
  margin: 0 0 10px;
  font-size: 14px;
  color: #f1f5f9;
}

.table-scroll {
  overflow-x: auto;
  border: 1px solid rgba(255, 255, 255, 0.08);
  border-radius: 6px;
}

.receipt-table {
  width: 100%;
  border-collapse: collapse;
  font-size: 13px;
  color: #e2e8f0;
}

.receipt-table th {
  background: rgba(30, 41, 59, 0.8);
  color: #94a3b8;
  padding: 10px 12px;
  border-bottom: 1px solid rgba(255, 255, 255, 0.08);
  white-space: nowrap;
}

.receipt-table td {
  padding: 10px 12px;
  border-bottom: 1px solid rgba(255, 255, 255, 0.04);
}

.sku-cell {
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.sku-text {
  font-family: var(--font-mono, monospace);
  color: #38bdf8;
  font-size: 12px;
}

.prod-text {
  font-size: 12px;
}

.qty-input {
  width: 80px;
  background: rgba(15, 23, 42, 0.8);
  border: 1px solid rgba(56, 189, 248, 0.3);
  color: #f8fafc;
  padding: 4px 6px;
  border-radius: 4px;
  text-align: right;
  font-family: var(--font-mono, monospace);
  font-weight: 600;
  outline: none;
}

.text-danger {
  color: #f87171;
  border-color: rgba(239, 68, 68, 0.3);
}

.text-success {
  color: #34d399;
  border-color: rgba(52, 211, 153, 0.3);
  background: rgba(0, 0, 0, 0.2);
}

.text-input {
  width: 100%;
  background: rgba(15, 23, 42, 0.8);
  border: 1px solid rgba(255, 255, 255, 0.12);
  color: #f8fafc;
  padding: 4px 6px;
  border-radius: 4px;
  font-size: 12px;
  outline: none;
}

.mono-text {
  font-family: var(--font-mono, monospace);
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
