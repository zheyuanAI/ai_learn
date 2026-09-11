<template>
  <el-drawer
    :model-value="visible"
    size="840px"
    destroy-on-close
    @close="handleClose"
  >
    <template #header>
      <div style="display: flex; align-items: center; justify-content: space-between; width: 100%; padding-right: 12px">
        <div style="display: flex; align-items: center; gap: 12px; flex-wrap: wrap">
          <span class="meta-tag">PURCHASE ORDER</span>
          <h3 style="margin: 0; font-size: 18px; color: #f1f5f9">{{ order?.poNo || '采购订单详情' }}</h3>
          <StatusBadge
            v-if="order"
            :type="statusBadgeType(order.status)"
            :text="statusText(order.status)"
          />
          <el-tag v-if="order?.completionType === 'Manual'" type="warning" size="small">
            人工完成 (未收余量已终止)
          </el-tag>
        </div>
        <el-button
          v-if="order"
          type="primary"
          link
          :icon="Search"
          title="穿透前往全链路全闭环追溯中心"
          @click="handleGoTrace"
        >
          全链路追溯
        </el-button>
      </div>
    </template>

    <CommandFeedback :error="lastError" :can-retry="canRetry" :executing="isExecuting" @retry="retry" />

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
      <div class="action-bar" style="margin-bottom: 16px; padding: 12px; background: rgba(30, 41, 59, 0.4); border-radius: 8px;">
        <span class="bar-title" style="margin-right: 12px; font-weight: 500; font-size: 13px; color: #8ca2b8;">可用业务动作：</span>
        <div class="bar-buttons" style="display: inline-flex; gap: 8px; flex-wrap: wrap">
          <!-- 提交 -->
          <el-button
            v-if="isActionEnabled('submit')"
            type="primary"
            :loading="actionLoading"
            @click="handleSubmitOrder"
          >
            提交订单
          </el-button>

          <!-- 审核 -->
          <el-button
            v-if="isActionEnabled('approve')"
            type="primary"
            :loading="actionLoading"
            @click="handleApproveOrder"
          >
            审核通过
          </el-button>

          <!-- 到货验收与实际接收 -->
          <el-button
            v-if="isActionEnabled('confirmReceipt') && hasPermission('pur:receipt:confirm')"
            type="warning"
            :loading="actionLoading"
            @click="isReceiptConfirmOpen = true"
          >
            外观验收与接收 (进QH)
          </el-button>

          <!-- 上架 -->
          <el-button
            v-if="isActionEnabled('putaway')"
            type="success"
            :loading="actionLoading"
            @click="isPutawayOpen = true"
          >
            执行上架 (RS ➔ Storage)
          </el-button>

          <!-- 人工完成 -->
          <el-button
            v-if="isActionEnabled('complete')"
            type="danger"
            :loading="actionLoading"
            @click="isCompleteDialogOpen = true"
          >
            人工完成 (终止待收余量)
          </el-button>
        </div>
      </div>

      <!-- 基础资料卡片 -->
      <el-descriptions :column="2" border style="margin-bottom: 20px">
        <el-descriptions-item label="供应商">
          <strong>{{ order.supplierName }}</strong>
          <span style="color: #8ca2b8; margin-left: 6px">({{ order.supplierCode }})</span>
        </el-descriptions-item>
        <el-descriptions-item label="计划到货日期">
          {{ order.expectedArrivalDate }}
          <span style="color: #8ca2b8; margin-left: 6px">(采购员: {{ order.owner || order.createdBy }})</span>
        </el-descriptions-item>
        <el-descriptions-item label="质量隔离库位">
          <span v-if="order.qualityHoldLocationCode" class="loc-code">{{ order.qualityHoldLocationCode }}</span>
          <span v-else class="text-muted text-sm">待到货分配</span>
        </el-descriptions-item>
        <el-descriptions-item label="收货暂存过渡位">
          <span v-if="order.receivingStagingLocationCode" class="loc-code">{{ order.receivingStagingLocationCode }}</span>
          <span v-else class="text-muted text-sm">待质检放行后分配</span>
        </el-descriptions-item>
      </el-descriptions>

      <!-- 人工完成说明 -->
      <el-alert
        v-if="order.completionReason"
        type="warning"
        show-icon
        style="margin-bottom: 20px"
        :title="`人工完成原因说明：${order.completionReason}（完成于 ${order.completedAt} 由 ${order.completedBy}）`"
      />

      <!-- 订单明细行表格 -->
      <div class="lines-section" style="margin-bottom: 24px">
        <h4 style="margin-bottom: 12px; color: #f1f5f9">采购订单行项与履约事实</h4>
        <el-table :data="order.lines" stripe border style="width: 100%">
          <el-table-column label="物料 SKU / 名称" min-width="160">
            <template #default="{ row }">
              <div class="sku-cell">
                <span class="sku" style="font-family: monospace; color: #67d2ff">{{ row.sku }}</span>
                <span class="name" style="display: block; font-size: 12px; color: #cbd5e1">{{ row.productName }}</span>
              </div>
            </template>
          </el-table-column>
          <el-table-column prop="spec" label="规格型号" width="100" />
          <el-table-column label="采购要求" width="90" align="right">
            <template #default="{ row }">
              <QuantityText :value="row.orderedQty" :unit="row.uom" />
            </template>
          </el-table-column>
          <el-table-column label="累计到货" width="90" align="right">
            <template #default="{ row }">
              <QuantityText :value="row.arrivedQty" :unit="row.uom" />
            </template>
          </el-table-column>
          <el-table-column label="外观拒收" width="90" align="right">
            <template #default="{ row }">
              <span :class="parseFloat(row.rejectedQty) > 0 ? 'text-danger' : ''">
                <QuantityText :value="row.rejectedQty" :unit="row.uom" />
              </span>
            </template>
          </el-table-column>
          <el-table-column label="实际接收(QH)" width="110" align="right">
            <template #default="{ row }">
              <QuantityText :value="row.receivedQty" :unit="row.uom" />
            </template>
          </el-table-column>
          <el-table-column label="质检合格" width="90" align="right">
            <template #default="{ row }">
              <span class="text-success">
                <QuantityText :value="row.qualifiedQty" :unit="row.uom" />
              </span>
            </template>
          </el-table-column>
          <el-table-column label="放行移位(RS)" width="110" align="right">
            <template #default="{ row }">
              <QuantityText :value="row.releaseExecutedQty" :unit="row.uom" />
            </template>
          </el-table-column>
          <el-table-column label="已上架" width="80" align="right">
            <template #default="{ row }">
              <QuantityText :value="row.putawayQty" :unit="row.uom" />
            </template>
          </el-table-column>
          <el-table-column label="待收余量" width="90" align="right">
            <template #default="{ row }">
              <span :class="parseFloat(row.pendingQty) > 0 ? 'text-amber font-bold' : 'text-muted'">
                <QuantityText :value="row.pendingQty" :unit="row.uom" />
              </span>
            </template>
          </el-table-column>
        </el-table>
      </div>

      <!-- 历史事件时间线 -->
      <div v-if="order.events && order.events.length > 0" class="events-section">
        <h4 style="margin-bottom: 14px; color: #f1f5f9">采购履约与质量审计时间线</h4>
        <el-timeline>
          <el-timeline-item
            v-for="(ev, idx) in order.events"
            :key="idx"
            :timestamp="ev.time"
            placement="top"
            type="primary"
          >
            <el-card shadow="never" style="background: rgba(30, 41, 59, 0.4)">
              <div style="display: flex; justify-content: space-between; margin-bottom: 6px">
                <strong style="color: #67d2ff">{{ ev.action }}</strong>
                <span style="font-size: 12px; color: #8ca2b8">{{ ev.actor }}</span>
              </div>
              <p style="margin: 0; font-size: 13px; color: #cbd5e1">{{ ev.impact }}</p>
            </el-card>
          </el-timeline-item>
        </el-timeline>
      </div>
    </div>

    <template #footer>
      <el-button @click="handleClose">关闭抽屉</el-button>
    </template>
  </el-drawer>

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
</template>

<script setup lang="ts">
import { isActionAllowed as checkAction, getActionDisabledReason as getDisabledReason } from "../../utils/actionGuard";
import type { AllowedAction } from "../../types/common";
/**
 * 采购订单详情抽屉组件 (PurchaseOrderDetailView)
 * 职责：展示采购订单生命周期、明细数量不变量、操作权限入口与审计时间线
 */
import { ref, watch } from "vue";
import { Search } from "@element-plus/icons-vue";
import { ElMessage, ElMessageBox } from "element-plus";
import { ApiError } from "@/utils/request";
import { useCommand } from "@/composables/useCommand";
import CommandFeedback from "@/components/common/CommandFeedback.vue";
import { useRouter } from "vue-router";
import { usePermission } from "@/composables/usePermission";
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
  confirmPurchaseReceiptWithServerId,
} from "@/api/purchasing";

const router = useRouter();
const { hasPermission } = usePermission();

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
const { execute, retry, isExecuting, canRetry, lastError } = useCommand();
const actionLoading = ref(false);

const isReceiptConfirmOpen = ref(false);
const isPutawayOpen = ref(false);
const isCompleteDialogOpen = ref(false);
const manualCompleteReason = ref("");

watch(
  () => [props.orderId, props.visible] as const,
  ([orderId, visible]) => {
    // 修改：合并 props 监听并立即执行，确保直达/刷新路由主动加载详情，列表抽屉仍可复用同一组件。
    if (orderId && visible) {
      void fetchDetail();
    }
  },
  { immediate: true }
);

/**
 * 用途：把详情查询错误转换为可区分的页面文案。
 * 入参：详情 API 抛出的错误对象；出参：面向用户的错误说明。
 * 流程：优先按 HTTP 403/404 显示权限或资源错误，其余错误沿用后端消息。
 */
function detailLoadErrorMessage(error: unknown): string {
  if (error instanceof ApiError && error.httpStatus === 404) return "采购订单资源不存在或已被删除（404）。";
  if (error instanceof ApiError && error.httpStatus === 403) return "您没有查看该采购订单的权限（403）。";
  return error instanceof Error ? error.message : "网络请求异常";
}

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
    errorMessage.value = detailLoadErrorMessage(err);
    viewState.value = "error";
  }
}

/**
 * 用途：一键穿透直达全链路追溯中心
 * 入参：当前采购订单 ID (order.id)
 * 出参：无；通过路由导航跳转 /traceability
 */
function handleGoTrace() {
  if (!order.value?.id) return;
  router.push({
    path: "/traceability",
    query: {
      entry_type: "PURCHASE_ORDER",
      entity_id: String(order.value.id),
      direction: "FORWARD",
    },
  });
}

// 替换为调用 actionGuard 的版本
function isActionEnabled(actionKey: string): boolean {
  if (!order.value) return false;
  return checkAction(order.value.allowedActions, actionKey);
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
  try {
    await execute((key) => submitPurchaseOrder(order.value!.id, key), { onConflict: fetchDetail });
    await fetchDetail();
    ElMessage.success("采购订单提交成功！");
    emit("refresh");
  } catch (err: any) {
    ElMessage.error(err?.message || "提交失败");
  } finally { /* useCommand 在 finally 中恢复 isExecuting。 */ }
}

async function handleApproveOrder() {
  if (!order.value) return;
  try {
    await execute((key) => approvePurchaseOrder(order.value!.id, key), { onConflict: fetchDetail });
    await fetchDetail();
    ElMessage.success("采购订单审核通过！");
    emit("refresh");
  } catch (err: any) {
    ElMessage.error(err?.message || "审核失败");
  } finally { /* useCommand 在 finally 中恢复 isExecuting。 */ }
}

async function handleConfirmReceipt(payload: any) {
  try {
    const { receiptId: _ignoredClientId, ...requestPayload } = payload;
    // 修改：收货事实 ID由服务端按幂等键分配，客户端不能用订单号、订单行 ID或随机 UUID代替。
    const receiptResponse = await execute((key) => confirmPurchaseReceiptWithServerId(requestPayload, key), { onConflict: fetchDetail });
    isReceiptConfirmOpen.value = false;
    await fetchDetail();
    emit("refresh");

    // 修改用途：质检必须沿收货接口返回的独立 ID 继续，禁止把提交载荷或订单行 ID 当作收货事实。
    const persistedReceipt = receiptResponse?.data;
    const returnedReceiptId = String(persistedReceipt?.id || "");
    const returnedReceiptLineId = String(persistedReceipt?.lines?.[0]?.id || "");
    if (!returnedReceiptId || !returnedReceiptLineId) {
      throw new Error("收货接口未返回 receiptId 或收货行 ID，已停止进入质检流程。");
    }

    // 成功提示并引导进入质检（使用 ElMessageBox 替代原生 confirm）
    const poNo = order.value?.poNo || "";
    const orderId = String(order.value?.id || "");
    try {
      await ElMessageBox.confirm(
        `采购到货验收成功！实收货物已送入 QualityHold 质量隔离位。\n\n订单号: ${poNo}\n收货凭证号: ${payload.receiptNo || returnedReceiptId}\n\n是否立即前往【采购到货质检】录入检验事实？`,
        "到货验收成功",
        {
          confirmButtonText: "前往质检",
          cancelButtonText: "留在此页",
          type: "success",
        }
      );
      router.push({
        path: "/purchasing/quality",
        query: {
          receiptId: returnedReceiptId,
          receiptLineId: returnedReceiptLineId,
          orderId,
          poNo,
          warehouseId: String(order.value?.lines?.find(
            (line) => String(line.id) === String(persistedReceipt.lines[0].purchaseOrderLineId),
          )?.targetWarehouseId || ""),
          productId: String(persistedReceipt.lines[0].productId || ""),
        },
      });
    } catch {
      // 用户留在当前抽屉
    }
  } catch (err: any) {
    ElMessage.error(err?.message || "收货确认失败");
  } finally { /* useCommand 在 finally 中恢复 isExecuting。 */ }
}

async function handleConfirmManualComplete() {
  if (!order.value) return;
  if (!manualCompleteReason.value.trim()) {
    ElMessage.warning("必须填写人工完成原因！");
    return;
  }
  try {
    await execute((key) => completePurchaseOrder(order.value!.id, {
      completionReason: manualCompleteReason.value,
    }, key), { onConflict: fetchDetail });
    isCompleteDialogOpen.value = false;
    manualCompleteReason.value = "";
    ElMessage.success("采购订单已人工完成！");
    await fetchDetail();
    emit("refresh");
  } catch (err: any) {
    ElMessage.error(err?.message || "人工完成失败");
  } finally { /* useCommand 在 finally 中恢复 isExecuting。 */ }
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

.header-right-btns {
  display: flex;
  align-items: center;
  gap: 10px;
}

.btn-act-trace {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  padding: 6px 12px;
  background: rgba(14, 165, 233, 0.15);
  border: 1px solid rgba(14, 165, 233, 0.4);
  color: #38bdf8;
  border-radius: 6px;
  font-size: 12px;
  font-weight: 500;
  cursor: pointer;
  transition: all 0.2s;
}

.btn-act-trace:hover {
  background: rgba(14, 165, 233, 0.3);
  color: #ffffff;
}
</style>
