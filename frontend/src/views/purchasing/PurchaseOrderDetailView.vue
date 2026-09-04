<template>
  <div v-if="visible" class="detail-drawer-mask" @click.self="handleClose">
    <div class="detail-drawer">
      <!-- 头部 -->
      <div class="drawer-header">
        <div class="header-info">
          <span class="meta-tag">PURCHASE ORDER</span>
          <div class="title-row">
            <h2 class="po-title">{{ order?.poNo || '采购订单详情' }}</h2>
            <StatusBadge
              v-if="order"
              :type="statusBadgeType(order.status)"
              :text="statusText(order.status)"
            />
            <span v-if="order?.completionType === 'Manual'" class="badge-manual">
              人工完成 (未收余量已终止)
            </span>
          </div>
        </div>
        <button type="button" class="btn-close" @click="handleClose">✕</button>
      </div>

      <!-- 四态展示 -->
      <div v-if="viewState === 'loading'" class="loading-state">
        <span>⏳ 正在加载订单履约事实...</span>
      </div>

      <ErrorState
        v-else-if="viewState === 'error'"
        title="获取采购订单失败"
        :message="errorMessage"
        @retry="fetchDetail"
      />

      <!-- 核心内容 -->
      <div v-else-if="order" class="drawer-body">
        <!-- 操作按钮工具栏 (严格依据 allowedActions 控制) -->
        <div class="action-bar">
          <span class="bar-title">可用业务动作：</span>
          <div class="bar-buttons">
            <!-- 提交 -->
            <button
              v-if="isActionEnabled('submit')"
              type="button"
              class="btn-act btn-primary"
              :disabled="actionLoading"
              @click="handleSubmitOrder"
            >
              提交订单
            </button>

            <!-- 审核 -->
            <button
              v-if="isActionEnabled('approve')"
              type="button"
              class="btn-act btn-primary"
              :disabled="actionLoading"
              @click="handleApproveOrder"
            >
              审核通过
            </button>

            <!-- 到货验收与实际接收 -->
            <button
              v-if="isActionEnabled('receive')"
              type="button"
              class="btn-act btn-warning"
              :disabled="actionLoading"
              @click="isReceiptConfirmOpen = true"
            >
              外观验收与接收 (进QH)
            </button>

            <!-- 上架 -->
            <button
              v-if="isActionEnabled('putaway')"
              type="button"
              class="btn-act btn-success"
              :disabled="actionLoading"
              @click="isPutawayOpen = true"
            >
              执行上架 (RS ➔ Storage)
            </button>

            <!-- 人工完成 -->
            <button
              v-if="isActionEnabled('complete')"
              type="button"
              class="btn-act btn-danger"
              :disabled="actionLoading"
              @click="isCompleteDialogOpen = true"
            >
              人工完成 (终止待收余量)
            </button>
          </div>
        </div>

        <!-- 基础资料卡片 -->
        <div class="info-card-row">
          <div class="meta-card">
            <span class="lbl">供应商</span>
            <strong>{{ order.supplierName }}</strong>
            <span class="sub">{{ order.supplierCode }}</span>
          </div>
          <div class="meta-card">
            <span class="lbl">计划到货日期</span>
            <strong>{{ order.expectedArrivalDate }}</strong>
            <span class="sub">采购员: {{ order.owner || order.createdBy }}</span>
          </div>
          <div class="meta-card">
            <span class="lbl">质量隔离库位</span>
            <span class="loc-code">{{ order.qualityHoldLocationCode || 'QH-01' }}</span>
            <span class="sub">实际到货接管进入此库位</span>
          </div>
          <div class="meta-card">
            <span class="lbl">收货暂存过渡位</span>
            <span class="loc-code">{{ order.receivingStagingLocationCode || 'RS-01' }}</span>
            <span class="sub">质检放行后上架前库位</span>
          </div>
        </div>

        <!-- 人工完成说明 -->
        <div v-if="order.completionReason" class="reason-banner">
          <strong>人工完成原因说明：</strong>
          <span>{{ order.completionReason }}</span>
          <span class="reason-time">（完成于 {{ order.completedAt }} 由 {{ order.completedBy }}）</span>
        </div>

        <!-- 订单明细行表格 -->
        <div class="lines-section">
          <h3>采购订单行项与履约事实</h3>
          <div class="table-scroll">
            <table class="lines-table">
              <thead>
                <tr>
                  <th>物料 SKU / 名称</th>
                  <th>规格型号</th>
                  <th style="text-align: right;">采购要求</th>
                  <th style="text-align: right;">累计到货</th>
                  <th style="text-align: right;">外观拒收</th>
                  <th style="text-align: right;">实际接收(QH)</th>
                  <th style="text-align: right;">质检合格</th>
                  <th style="text-align: right;">放行移位(RS)</th>
                  <th style="text-align: right;">已上架</th>
                  <th style="text-align: right;">待收余量</th>
                </tr>
              </thead>
              <tbody>
                <tr v-for="line in order.lines" :key="line.id">
                  <td>
                    <div class="sku-cell">
                      <span class="sku">{{ line.sku }}</span>
                      <span class="name">{{ line.productName }}</span>
                    </div>
                  </td>
                  <td>{{ line.spec || '-' }}</td>
                  <td style="text-align: right;">
                    <QuantityText :value="line.orderedQty" :unit="line.uom" />
                  </td>
                  <td style="text-align: right;">
                    <QuantityText :value="line.arrivedQty" :unit="line.uom" />
                  </td>
                  <td style="text-align: right;">
                    <span :class="parseFloat(line.rejectedQty) > 0 ? 'text-danger' : ''">
                      <QuantityText :value="line.rejectedQty" :unit="line.uom" />
                    </span>
                  </td>
                  <td style="text-align: right;">
                    <QuantityText :value="line.receivedQty" :unit="line.uom" />
                  </td>
                  <td style="text-align: right;">
                    <span class="text-success">
                      <QuantityText :value="line.qualifiedQty" :unit="line.uom" />
                    </span>
                  </td>
                  <td style="text-align: right;">
                    <QuantityText :value="line.releaseExecutedQty" :unit="line.uom" />
                  </td>
                  <td style="text-align: right;">
                    <QuantityText :value="line.putawayQty" :unit="line.uom" />
                  </td>
                  <td style="text-align: right;">
                    <span :class="parseFloat(line.pendingQty) > 0 ? 'text-amber font-bold' : 'text-muted'">
                      <QuantityText :value="line.pendingQty" :unit="line.uom" />
                    </span>
                  </td>
                </tr>
              </tbody>
            </table>
          </div>
        </div>

        <!-- 历史事件时间线 -->
        <div v-if="order.events && order.events.length > 0" class="events-section">
          <h3>采购履约与质量审计时间线</h3>
          <div class="timeline">
            <div v-for="(ev, idx) in order.events" :key="idx" class="timeline-item">
              <div class="timeline-point"></div>
              <div class="timeline-content">
                <div class="ev-header">
                  <strong>{{ ev.action }}</strong>
                  <span class="ev-time">{{ ev.time }}</span>
                  <span class="ev-actor">{{ ev.actor }}</span>
                </div>
                <p class="ev-impact">{{ ev.impact }}</p>
              </div>
            </div>
          </div>
        </div>
      </div>

      <!-- 抽屉底部 -->
      <div class="drawer-footer">
        <button type="button" class="btn btn-secondary" @click="handleClose">关闭抽屉</button>
      </div>
    </div>

    <!-- 到货验收弹窗 -->
    <ReceiptConfirmView
      v-model:visible="isReceiptConfirmOpen"
      :order="order"
      :submitting="actionLoading"
      @confirm="handleConfirmReceipt"
      @close="isReceiptConfirmOpen = false"
    />

    <!-- 人工完成确认弹窗 -->
    <ConfirmDialog
      v-model:visible="isCompleteDialogOpen"
      title="人工完成采购订单"
      message="人工完成将终止剩余未收货数量，已收货物将继续流转质检和上架。请填写完成原因："
      danger
      :loading="actionLoading"
      @confirm="handleConfirmManualComplete"
    >
      <div class="reason-input-box">
        <textarea
          v-model="manualCompleteReason"
          class="reason-textarea"
          rows="3"
          placeholder="必填：如供应商产能受限终止供货..."
        ></textarea>
      </div>
    </ConfirmDialog>
  </div>
</template>

<script setup lang="ts">
/**
 * 采购订单详情抽屉组件 (PurchaseOrderDetailView)
 * 职责：展示采购订单生命周期、明细数量不变量、操作权限入口与审计时间线
 */
import { ref, watch } from "vue";
import StatusBadge from "@/components/common/StatusBadge.vue";
import QuantityText from "@/components/common/QuantityText.vue";
import ErrorState from "@/components/common/ErrorState.vue";
import ConfirmDialog from "@/components/common/ConfirmDialog.vue";
import ReceiptConfirmView from "./ReceiptConfirmView.vue";
import type { ViewState } from "@/types/common";
import type { PurchaseOrder } from "@/types/purchasing";
import {
  getPurchaseOrderById,
  submitPurchaseOrder,
  approvePurchaseOrder,
  completePurchaseOrder,
  confirmPurchaseReceipt,
} from "@/api/purchasing";

const props = withDefaults(
  defineProps<{
    visible: boolean;
    orderId: string | number | null;
  }>(),
  {
    visible: false,
    orderId: null,
  }
);

const emit = defineEmits<{
  (e: "update:visible", val: boolean): void;
  (e: "close"): void;
  (e: "refresh"): void;
}>();

const viewState = ref<ViewState>("loading");
const errorMessage = ref("");
const order = ref<PurchaseOrder | null>(null);
const actionLoading = ref(false);

const isReceiptConfirmOpen = ref(false);
const isPutawayOpen = ref(false);
const isCompleteDialogOpen = ref(false);
const manualCompleteReason = ref("");

watch(
  () => props.orderId,
  (val) => {
    if (val && props.visible) {
      fetchDetail();
    }
  }
);

watch(
  () => props.visible,
  (val) => {
    if (val && props.orderId) {
      fetchDetail();
    }
  }
);

async function fetchDetail() {
  if (!props.orderId) return;
  viewState.value = "loading";
  errorMessage.value = "";
  try {
    const res = await getPurchaseOrderById(props.orderId);
    order.value = res.data;
    viewState.value = "ready";
  } catch (err: any) {
    console.error("[PurchaseOrderDetailView] 获取失败:", err);
    errorMessage.value = err?.message || "网络请求异常";
    viewState.value = "error";
  }
}

function isActionEnabled(actionKey: string): boolean {
  if (!order.value) return false;
  if (!order.value.allowedActions) return true;
  const act = order.value.allowedActions.find((a) => a.action === actionKey);
  return act ? act.enabled : false;
}

function statusBadgeType(status: string): any {
  const map: Record<string, string> = {
    Draft: "default",
    Submitted: "primary",
    Approved: "info",
    PartiallyReceived: "warning",
    Completed: "success",
  };
  return map[status] || "default";
}

function statusText(status: string): string {
  const map: Record<string, string> = {
    Draft: "未提交",
    Submitted: "已提交",
    Approved: "已审核",
    PartiallyReceived: "部分收货",
    Completed: "已完成",
  };
  return map[status] || status;
}

function handleClose() {
  emit("update:visible", false);
  emit("close");
}

async function handleSubmitOrder() {
  if (!order.value) return;
  actionLoading.value = true;
  try {
    await submitPurchaseOrder(order.value.id);
    await fetchDetail();
    emit("refresh");
  } catch (err: any) {
    alert(err?.message || "提交失败");
  } finally {
    actionLoading.value = false;
  }
}

async function handleApproveOrder() {
  if (!order.value) return;
  actionLoading.value = true;
  try {
    await approvePurchaseOrder(order.value.id);
    await fetchDetail();
    emit("refresh");
  } catch (err: any) {
    alert(err?.message || "审核失败");
  } finally {
    actionLoading.value = false;
  }
}

async function handleConfirmReceipt(payload: any) {
  actionLoading.value = true;
  try {
    await confirmPurchaseReceipt(payload);
    isReceiptConfirmOpen.value = false;
    await fetchDetail();
    emit("refresh");
  } catch (err: any) {
    alert(err?.message || "收货确认失败");
  } finally {
    actionLoading.value = false;
  }
}

async function handleConfirmManualComplete() {
  if (!order.value) return;
  if (!manualCompleteReason.value.trim()) {
    alert("必须填写人工完成原因！");
    return;
  }
  actionLoading.value = true;
  try {
    await completePurchaseOrder(order.value.id, {
      completionReason: manualCompleteReason.value,
    });
    isCompleteDialogOpen.value = false;
    manualCompleteReason.value = "";
    await fetchDetail();
    emit("refresh");
  } catch (err: any) {
    alert(err?.message || "人工完成失败");
  } finally {
    actionLoading.value = false;
  }
}
</script>

<style scoped>
.detail-drawer-mask {
  position: fixed;
  inset: 0;
  background: rgba(0, 0, 0, 0.7);
  backdrop-filter: blur(4px);
  display: flex;
  justify-content: flex-end;
  z-index: 1000;
}

.detail-drawer {
  background: #0f172a;
  border-left: 1px solid rgba(255, 255, 255, 0.15);
  width: 100%;
  max-width: 960px;
  height: 100vh;
  display: flex;
  flex-direction: column;
  box-shadow: -10px 0 25px rgba(0, 0, 0, 0.5);
  animation: slide-in 0.25s ease-out;
}

.drawer-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 18px 24px;
  border-bottom: 1px solid rgba(255, 255, 255, 0.08);
}

.header-info {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.meta-tag {
  font-size: 11px;
  font-family: var(--font-mono, monospace);
  color: #38bdf8;
  letter-spacing: 0.5px;
}

.title-row {
  display: flex;
  align-items: center;
  gap: 12px;
}

.po-title {
  margin: 0;
  font-size: 18px;
  color: #f8fafc;
}

.badge-manual {
  font-size: 11px;
  background: rgba(239, 68, 68, 0.15);
  color: #f87171;
  border: 1px solid rgba(239, 68, 68, 0.3);
  padding: 2px 8px;
  border-radius: 4px;
}

.btn-close {
  background: transparent;
  border: none;
  color: #94a3b8;
  font-size: 18px;
  cursor: pointer;
}

.drawer-body {
  flex: 1;
  overflow-y: auto;
  padding: 20px 24px;
  display: flex;
  flex-direction: column;
  gap: 20px;
}

.action-bar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  background: rgba(30, 41, 59, 0.5);
  border: 1px solid rgba(255, 255, 255, 0.08);
  border-radius: 8px;
  padding: 12px 18px;
  flex-wrap: wrap;
  gap: 10px;
}

.bar-title {
  font-size: 13px;
  color: #94a3b8;
  font-weight: 500;
}

.bar-buttons {
  display: flex;
  align-items: center;
  gap: 10px;
}

.btn-act {
  padding: 6px 14px;
  border-radius: 6px;
  font-size: 13px;
  font-weight: 500;
  cursor: pointer;
  border: 1px solid transparent;
  transition: all 0.2s;
}

.btn-primary {
  background: #0284c7;
  color: #ffffff;
}

.btn-warning {
  background: rgba(251, 191, 36, 0.15);
  color: #fbbf24;
  border-color: rgba(251, 191, 36, 0.3);
}

.btn-success {
  background: rgba(52, 211, 153, 0.15);
  color: #34d399;
  border-color: rgba(52, 211, 153, 0.3);
}

.btn-danger {
  background: rgba(239, 68, 68, 0.15);
  color: #f87171;
  border-color: rgba(239, 68, 68, 0.3);
}

.info-card-row {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(180px, 1fr));
  gap: 12px;
}

.meta-card {
  background: rgba(15, 23, 42, 0.6);
  border: 1px solid rgba(255, 255, 255, 0.06);
  border-radius: 6px;
  padding: 12px;
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.lbl {
  font-size: 11px;
  color: #64748b;
}

.sub {
  font-size: 11px;
  color: #94a3b8;
}

.loc-code {
  font-family: var(--font-mono, monospace);
  font-size: 13px;
  color: #38bdf8;
  font-weight: 600;
}

.reason-banner {
  background: rgba(239, 68, 68, 0.08);
  border: 1px solid rgba(239, 68, 68, 0.25);
  border-radius: 6px;
  padding: 10px 14px;
  font-size: 13px;
  color: #fca5a5;
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
}

.reason-time {
  font-size: 11px;
  color: #94a3b8;
}

.lines-section h3,
.events-section h3 {
  margin: 0 0 12px;
  font-size: 15px;
  color: #f1f5f9;
}

.table-scroll {
  overflow-x: auto;
  border: 1px solid rgba(255, 255, 255, 0.08);
  border-radius: 6px;
}

.lines-table {
  width: 100%;
  border-collapse: collapse;
  font-size: 13px;
  color: #e2e8f0;
}

.lines-table th {
  background: rgba(30, 41, 59, 0.8);
  color: #94a3b8;
  padding: 10px 12px;
  border-bottom: 1px solid rgba(255, 255, 255, 0.08);
  white-space: nowrap;
}

.lines-table td {
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

.text-danger {
  color: #f87171;
}

.text-success {
  color: #34d399;
}

.text-amber {
  color: #fbbf24;
}

.text-muted {
  color: #64748b;
}

.font-bold {
  font-weight: 700;
}

/* 时间线 */
.timeline {
  display: flex;
  flex-direction: column;
  gap: 12px;
  border-left: 2px solid rgba(56, 189, 248, 0.3);
  margin-left: 8px;
  padding-left: 16px;
}

.timeline-item {
  position: relative;
}

.timeline-point {
  position: absolute;
  left: -21px;
  top: 4px;
  width: 8px;
  height: 8px;
  border-radius: 50%;
  background: #38bdf8;
}

.timeline-content {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.ev-header {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 13px;
  color: #f1f5f9;
}

.ev-time {
  font-size: 11px;
  font-family: var(--font-mono, monospace);
  color: #94a3b8;
}

.ev-actor {
  font-size: 11px;
  color: #64748b;
}

.ev-impact {
  margin: 0;
  font-size: 12px;
  color: #cbd5e1;
}

.drawer-footer {
  padding: 14px 24px;
  background: rgba(0, 0, 0, 0.2);
  border-top: 1px solid rgba(255, 255, 255, 0.08);
  display: flex;
  justify-content: flex-end;
}

.btn-secondary {
  padding: 8px 16px;
  background: rgba(51, 65, 85, 0.6);
  color: #cbd5e1;
  border: 1px solid rgba(255, 255, 255, 0.1);
  border-radius: 6px;
  font-size: 13px;
  cursor: pointer;
}

.reason-input-box {
  margin-top: 10px;
}

.reason-textarea {
  width: 100%;
  background: rgba(30, 41, 59, 0.8);
  border: 1px solid rgba(255, 255, 255, 0.12);
  color: #f8fafc;
  padding: 8px 10px;
  border-radius: 6px;
  font-size: 13px;
  outline: none;
}

@keyframes slide-in {
  from {
    transform: translateX(100%);
  }
  to {
    transform: translateX(0);
  }
}
</style>
