<template>
  <el-dialog
    :model-value="visible"
    width="840px"
    destroy-on-close
    append-to-body
    @close="handleClose"
  >
    <template #header>
      <div style="display: flex; align-items: center; gap: 12px">
        <span style="font-weight: 600; font-size: 16px; color: #f1f5f9">销售发货确认与库存扣减</span>
        <el-tag v-if="order" type="info" size="small">{{ order.soNo }}</el-tag>
      </div>
    </template>

    <div v-if="order" class="dialog-body">
      <!-- 业务规则提示 -->
      <el-alert
        type="info"
        :closable="false"
        show-icon
        style="margin-bottom: 16px"
        title="发货出库与库存扣减规则"
        :description="`确认发货将从发货暂存位（${order.shippingLocationCode || '未返回真实库位'}）移出货物，正式扣减企业实物在库库存，并将对应有效预留转入已释放（released_qty）。当全部订单行均满足 shipped_qty = ordered_qty 时，系统自动进入【已完成 (Completed / FullyShipped / Normal)】。`"
      />

      <!-- 基础发运参数 -->
      <el-form label-width="100px" style="margin-bottom: 16px">
        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item label="客户全称">
              <el-input :model-value="order.customerName" disabled />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="发货时间" required>
              <el-input v-model="shipTime" type="datetime-local" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="承运物流商" required>
              <el-select v-model="carrierName" style="width: 100%">
                <el-option label="顺丰冷链物流" value="顺丰冷链物流" />
                <el-option label="跨越速运" value="跨越速运" />
                <el-option label="德邦精准汽运" value="德邦精准汽运" />
                <el-option label="客户自提 (专车直运)" value="客户自提" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="物流运单号">
              <el-input v-model="trackingNo" placeholder="如: SF20260826001" />
            </el-form-item>
          </el-col>
        </el-row>
      </el-form>

      <!-- 发货行项明细 -->
      <div class="lines-box">
        <h4 style="margin-bottom: 10px; color: #f1f5f9; font-size: 14px">可发货行项清单 (依据发货暂存位占用数量)</h4>
        <el-table :data="editableShipLines" border style="width: 100%">
          <el-table-column label="物料 SKU / 名称" min-width="180">
            <template #default="{ row }">
              <div>
                <div style="font-family: monospace; font-weight: bold; color: #67d2ff">{{ row.sku }}</div>
                <div style="font-size: 12px; color: #8ca2b8">{{ row.productName }}</div>
              </div>
            </template>
          </el-table-column>
          <el-table-column label="订单订购量" width="110" align="right">
            <template #default="{ row }">
              <QuantityText :value="row.orderedQty" :unit="row.uom" />
            </template>
          </el-table-column>
          <el-table-column label="累计已发货" width="110" align="right">
            <template #default="{ row }">
              <QuantityText :value="row.shippedQty" :unit="row.uom" />
            </template>
          </el-table-column>
          <el-table-column label="发货暂存可用" width="120" align="right">
            <template #default="{ row }">
              <span class="text-cyan font-bold">
                <QuantityText :value="row.shippingStagedQty" :unit="row.uom" />
              </span>
            </template>
          </el-table-column>
          <el-table-column label="本次发货数量" width="140" align="right">
            <template #default="{ row }">
              <el-input
                v-model="row.shipQty"
                size="small"
                :disabled="parseFloat(row.shippingStagedQty) <= 0"
              />
            </template>
          </el-table-column>
        </el-table>
      </div>
    </div>

    <template #footer>
      <el-button @click="handleClose">取消</el-button>
      <el-button type="primary" :loading="submitting" @click="submitShipment">
        确认发货并扣减实物库存
      </el-button>
    </template>
  </el-dialog>
</template>

<script setup lang="ts">
/**
 * 销售发货确认与实物库存扣减弹窗 (ShipmentConfirmView)
 * 职责：指定物流承运商与单号，录入各行发货数量并校验不超过发货暂存量
 */
import { ref, watch } from "vue";
import { ElMessage } from "element-plus";
import QuantityText from "@/components/common/QuantityText.vue";
import type { SalesOrder } from "@/types/sales";
import { stringCompare } from "@/types/inventory";
import { currentLocalDateTimeValue } from "@/utils/dateTime";

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

const shipTime = ref(currentLocalDateTimeValue());
const carrierName = ref("顺丰冷链物流");
const trackingNo = ref("");
const editableShipLines = ref<EditableShipLine[]>([]);

watch(
  () => props.order,
  (val) => {
    if (val && val.lines) {
      shipTime.value = currentLocalDateTimeValue();
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
    ElMessage.warning("至少需要输入一条发货数量大于0的行项！");
    return;
  }

  for (const l of linesToShip) {
    if (stringCompare(l.shipQty, l.shippingStagedQty) > 0) {
      ElMessage.warning(`物料 ${l.sku} 的发货数量 (${l.shipQty}) 不能超过发货暂存数量 (${l.shippingStagedQty})`);
      return;
    }
  }

  const payload = {
    salesOrderId: props.order.id,
    // 修改用途：后端 ShipmentConfirmRequest 使用 OffsetDateTime，必须提交 ISO-8601 时间而不是无时区文本。
    shipTime: new Date(shipTime.value).toISOString(),
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

.field-hint {
  color: #94a3b8;
  font-size: 11px;
  line-height: 1.4;
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
