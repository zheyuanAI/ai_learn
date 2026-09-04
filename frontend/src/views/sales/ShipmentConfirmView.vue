<template>
  <div v-if="visible && order" class="dialog-mask" @click.self="handleClose">
    <div class="dialog-panel">
      <div class="dialog-header">
        <div class="header-left">
          <h3 class="dialog-title">销售发货确认与库存扣减</h3>
          <span class="mono-no">{{ order.soNo }}</span>
        </div>
        <button type="button" class="btn-close" @click="handleClose">✕</button>
      </div>

      <div class="dialog-body">
        <!-- 业务规则提示 -->
        <div class="rule-box">
          <div class="rule-icon">🚚</div>
          <div class="rule-text">
            <strong>发货出库与库存扣减规则：</strong>
            <span>确认发货将从发货暂存位（{{ order.shippingLocationCode || 'SHP-01' }}）移出货物，正式扣减企业实物在库库存，并将对应有效预留转入已释放（released_qty）。当全部订单行均满足 shipped_qty = ordered_qty 时，系统自动进入【已完成 (Completed / FullyShipped / Normal)】。</span>
          </div>
        </div>

        <!-- 基础发运参数 -->
        <div class="form-grid">
          <div class="form-item">
            <label>客户全称</label>
            <input :value="order.customerName" type="text" class="form-input" disabled />
          </div>
          <div class="form-item">
            <label>发货时间 <span class="req">*</span></label>
            <input v-model="shipTime" type="datetime-local" class="form-input" required />
          </div>
          <div class="form-item">
            <label>承运物流商 <span class="req">*</span></label>
            <select v-model="carrierName" class="form-select" required>
              <option value="顺丰冷链物流">顺丰冷链物流</option>
              <option value="跨越速运">跨越速运</option>
              <option value="德邦精准汽运">德邦精准汽运</option>
              <option value="客户自提">客户自提 (专车直运)</option>
            </select>
          </div>
          <div class="form-item">
            <label>物流运单号</label>
            <input v-model="trackingNo" type="text" class="form-input mono-text" placeholder="如: SF20260826001" />
          </div>
        </div>

        <!-- 发货行项明细 -->
        <div class="lines-box">
          <h4>可发货行项清单 (依据发货暂存位占用数量)</h4>
          <div class="table-scroll">
            <table class="shipment-table">
              <thead>
                <tr>
                  <th>物料 SKU / 名称</th>
                  <th style="text-align: right;">订单订购量</th>
                  <th style="text-align: right;">累计已发货</th>
                  <th style="text-align: right;">发货暂存可用</th>
                  <th style="text-align: right; width: 120px;">本次发货数量 <span class="req">*</span></th>
                </tr>
              </thead>
              <tbody>
                <tr v-for="line in editableShipLines" :key="line.salesOrderLineId">
                  <td>
                    <div class="sku-cell">
                      <span class="sku">{{ line.sku }}</span>
                      <span class="name">{{ line.productName }}</span>
                    </div>
                  </td>
                  <td style="text-align: right;">
                    <QuantityText :value="line.orderedQty" :unit="line.uom" />
                  </td>
                  <td style="text-align: right;">
                    <QuantityText :value="line.shippedQty" :unit="line.uom" />
                  </td>
                  <td style="text-align: right;">
                    <span class="text-cyan font-bold">
                      <QuantityText :value="line.shippingStagedQty" :unit="line.uom" />
                    </span>
                  </td>
                  <td style="text-align: right;">
                    <input
                      v-model="line.shipQty"
                      type="text"
                      class="qty-input"
                      :disabled="parseFloat(line.shippingStagedQty) <= 0"
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
        <button type="button" class="btn btn-primary" :disabled="submitting" @click="submitShipment">
          <span v-if="submitting">⏳ 发货出库中...</span>
          <span v-else>确认发货并扣减实物库存</span>
        </button>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
/**
 * 销售发货确认与实物库存扣减弹窗 (ShipmentConfirmView)
 * 职责：指定物流承运商与单号，录入各行发货数量并校验不超过发货暂存量
 */
import { ref, watch } from "vue";
import QuantityText from "@/components/common/QuantityText.vue";
import type { SalesOrder } from "@/types/sales";
import { stringCompare } from "@/types/inventory";

interface EditableShipLine {
  salesOrderLineId: string | number;
  productId: string | number;
  sku: string;
  productName: string;
  uom: string;
  orderedQty: string;
  shippedQty: string;
  shippingStagedQty: string;
  shipQty: string;
}

const props = withDefaults(
  defineProps<{
    visible: boolean;
    order: SalesOrder | null;
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

const shipTime = ref(new Date().toISOString().slice(0, 16));
const carrierName = ref("顺丰冷链物流");
const trackingNo = ref(`SF${Date.now().toString().slice(-8)}`);
const editableShipLines = ref<EditableShipLine[]>([]);

watch(
  () => props.order,
  (val) => {
    if (val && val.lines) {
      editableShipLines.value = val.lines.map((l) => ({
        salesOrderLineId: l.id,
        productId: l.productId,
        sku: l.sku,
        productName: l.productName,
        uom: l.uom,
        orderedQty: l.orderedQty,
        shippedQty: l.shippedQty,
        shippingStagedQty: l.shippingStagedQty,
        shipQty: parseFloat(l.shippingStagedQty) > 0 ? l.shippingStagedQty : "0",
      }));
    }
  },
  { immediate: true }
);

function handleClose() {
  emit("update:visible", false);
  emit("close");
}

function submitShipment() {
  if (!props.order) return;

  const linesToShip = editableShipLines.value.filter((l) => parseFloat(l.shipQty) > 0);
  if (linesToShip.length === 0) {
    alert("至少需要输入一条发货数量大于0的行项！");
    return;
  }

  for (const l of linesToShip) {
    if (stringCompare(l.shipQty, l.shippingStagedQty) > 0) {
      alert(`物料 ${l.sku} 的发货数量 (${l.shipQty}) 不能超过发货暂存数量 (${l.shippingStagedQty})`);
      return;
    }
  }

  const payload = {
    salesOrderId: props.order.id,
    shipTime: shipTime.value.replace("T", " ") + ":00",
    carrierName: carrierName.value,
    trackingNo: trackingNo.value,
    lines: linesToShip.map((l) => ({
      salesOrderLineId: l.salesOrderLineId,
      productId: l.productId,
      shipQty: l.shipQty,
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

.rule-box {
  display: flex;
  align-items: flex-start;
  gap: 10px;
  background: rgba(56, 189, 248, 0.08);
  border: 1px solid rgba(56, 189, 248, 0.25);
  border-radius: 6px;
  padding: 12px;
}

.rule-icon {
  font-size: 18px;
}

.rule-text {
  font-size: 12px;
  line-height: 1.5;
  color: #cbd5e1;
}

.form-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(180px, 1fr));
  gap: 12px;
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

.shipment-table {
  width: 100%;
  border-collapse: collapse;
  font-size: 13px;
  color: #e2e8f0;
}

.shipment-table th {
  background: rgba(30, 41, 59, 0.8);
  color: #94a3b8;
  padding: 10px 12px;
  border-bottom: 1px solid rgba(255, 255, 255, 0.08);
  white-space: nowrap;
}

.shipment-table td {
  padding: 10px 12px;
  border-bottom: 1px solid rgba(255, 255, 255, 0.04);
}

.sku-cell {
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.sku {
  font-family: var(--font-mono, monospace);
  color: #38bdf8;
  font-size: 12px;
}

.name {
  font-size: 12px;
}

.text-cyan {
  color: #38bdf8;
}

.font-bold {
  font-weight: 700;
}

.qty-input {
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
