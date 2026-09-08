<template>
  <div v-if="visible" class="detail-drawer-mask" @click.self="handleClose">
    <div class="detail-drawer">
      <CommandFeedback :error="lastError" :can-retry="canRetry" :executing="isExecuting" @retry="retry" />
      <!-- 头部 -->
      <div class="drawer-header">
        <div class="header-info">
          <span class="meta-tag">SALES ORDER / DUAL-AXIS FULFILLMENT</span>
          <div class="title-row">
            <h2 class="so-title">{{ order?.soNo || '销售订单详情' }}</h2>
            <!-- 双轴状态并列展示 -->
            <div v-if="order" class="badges-group">
              <StatusBadge
                :type="lifecycleBadgeType(order.status)"
                :text="`生命周期: ${lifecycleText(order.status)}`"
              />
              <StatusBadge
                :type="fulfillmentBadgeType(order.fulfillmentStatus)"
                :text="`履约进度: ${fulfillmentText(order.fulfillmentStatus)}`"
              />
              <span v-if="order.completionType === 'Manual'" class="tag-manual">
                已完成 (人工终止)
              </span>
              <span v-else-if="order.completionType === 'Normal'" class="tag-normal">
                已完成 (全部发货)
              </span>
            </div>
          </div>
        </div>
        <div class="header-right-btns">
          <button
            v-if="order"
            type="button"
            class="btn-act-trace"
            title="穿透前往全链路全闭环追溯中心"
            @click="handleGoTrace"
          >
            <span>🔍 全链路追溯</span>
          </button>
          <button type="button" class="btn-close" @click="handleClose">✕</button>
        </div>
      </div>

      <!-- 四态展示 -->
      <div v-if="viewState === 'loading'" class="loading-state">
        <span>⏳ 正在加载销售订单详情与派生履约事实...</span>
      </div>

      <ErrorState
        v-else-if="viewState === 'error'"
        title="获取销售订单失败"
        :message="errorMessage"
        @retry="fetchDetail"
      />

      <!-- 核心内容 -->
      <div v-else-if="order" class="drawer-body">
        <!-- 正常与异常操作工具栏 (严格依据 allowedActions 控制) -->
        <div class="actions-panel">
          <div class="normal-actions">
            <span class="section-lbl">正常履约通道：</span>
            <!-- 提交 -->
            <button
              v-if="isActionEnabled('submit')"
              type="button"
              class="btn-act btn-primary"
              :disabled="actionLoading"
              @click="handleSubmit"
            >
              提交订单
            </button>

            <!-- 审核 -->
            <button
              v-if="isActionEnabled('approve')"
              type="button"
              class="btn-act btn-primary"
              :disabled="actionLoading"
              @click="handleApprove"
            >
              审核通过 (进入履约)
            </button>

            <!-- 直接拣货 -->
            <button
              v-if="isActionEnabled('directPick')"
              type="button"
              class="btn-act btn-primary highlight-btn"
              :disabled="actionLoading"
              @click="openDirectPick(order.lines[0])"
            >
              直接拣货 (自动补齐预留)
            </button>

            <!-- 确认发货 -->
            <button
              v-if="isActionEnabled('ship')"
              type="button"
              class="btn-act btn-success"
              :disabled="actionLoading"
              @click="isShipmentOpen = true"
            >
              确认发货 (扣减实物库存)
            </button>
          </div>

          <div class="exception-actions">
            <span class="section-lbl">异常与审计通道：</span>
            <!-- 查看预留详情 -->
            <button
              type="button"
              class="btn-act btn-secondary"
              @click="isReservationDetailOpen = true"
            >
              预留与库位分配
            </button>

            <!-- 退回未发货拣货 -->
            <button
              v-if="isActionEnabled('returnPick')"
              type="button"
              class="btn-act btn-warning"
              :disabled="actionLoading"
              @click="openReturnPickModal(order.lines[0])"
            >
              退回未发货拣货
            </button>

            <!-- 人工完成 -->
            <button
              v-if="isActionEnabled('manualComplete')"
              type="button"
              class="btn-act btn-danger"
              :disabled="actionLoading"
              @click="openManualComplete"
            >
              人工完成 (受控终止)
            </button>
          </div>
        </div>

        <!-- 基础元数据卡片 -->
        <div class="info-card-row">
          <div class="meta-card">
            <span class="lbl">客户信息</span>
            <strong>{{ order.customerName }}</strong>
            <span class="sub">{{ order.customerCode }} | 业务员: {{ order.owner || '未返回真实业务员' }}</span>
          </div>
          <div class="meta-card">
            <span class="lbl">计划发货日</span>
            <strong>{{ order.plannedShipDate }}</strong>
            <span class="sub">发运优先级: {{ order.priority || '标准' }}</span>
          </div>
          <div class="meta-card">
            <span class="lbl">出库仓库</span>
            <strong>{{ order.warehouseName || '未返回仓库事实' }}</strong>
            <span class="sub">发货暂存库位: {{ order.shippingLocationCode || '未返回真实库位' }}</span>
          </div>
        </div>

        <!-- 人工完成原因提示条 -->
        <div v-if="order.completionReason" class="reason-banner">
          <strong>人工完成原因：</strong>
          <span>{{ order.completionReason }}</span>
          <span class="reason-time">（操作人: {{ order.completedBy }} | 时间: {{ order.completedAt }}）</span>
        </div>

        <!-- 明细表格 (5 个派生数量精确列出) -->
        <div class="lines-section">
          <div class="lines-header">
            <h3>销售订单行项数量与进度明细</h3>
            <span class="formula-hint">满足不变量：0 ≤ 已发货 ≤ 累计拣货 ≤ 累计预留 ≤ 订购总量</span>
          </div>
          <div class="table-scroll">
            <table class="lines-table">
              <thead>
                <tr>
                  <th>物料 SKU / 名称</th>
                  <th style="text-align: right;">订购量</th>
                  <th style="text-align: right;">累计预留</th>
                  <th style="text-align: right;">累计已拣</th>
                  <th style="text-align: right;">累计已发</th>
                  <th style="text-align: right;">未预留量</th>
                  <th style="text-align: right;">已预留未拣</th>
                  <th style="text-align: right;">发货暂存占用</th>
                  <th style="text-align: right;">有效预留量</th>
                  <th style="text-align: right;">待发货总量</th>
                  <th style="text-align: center; width: 140px;">单行操作</th>
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
                  <td style="text-align: right;">
                    <QuantityText :value="line.orderedQty" :unit="line.uom" />
                  </td>
                  <td style="text-align: right;">
                    <QuantityText :value="line.reservedQty" :unit="line.uom" />
                  </td>
                  <td style="text-align: right;">
                    <QuantityText :value="line.pickedQty" :unit="line.uom" />
                  </td>
                  <td style="text-align: right;">
                    <QuantityText :value="line.shippedQty" :unit="line.uom" />
                  </td>
                  <td style="text-align: right;">
                    <QuantityText :value="line.unreservedQty" :unit="line.uom" />
                  </td>
                  <td style="text-align: right;">
                    <span :class="parseFloat(line.unpickedQty) > 0 ? 'text-amber' : ''">
                      <QuantityText :value="line.unpickedQty" :unit="line.uom" />
                    </span>
                  </td>
                  <td style="text-align: right;">
                    <span :class="parseFloat(line.shippingStagedQty) > 0 ? 'text-cyan font-bold' : ''">
                      <QuantityText :value="line.shippingStagedQty" :unit="line.uom" />
                    </span>
                  </td>
                  <td style="text-align: right;">
                    <span class="text-success">
                      <QuantityText :value="line.activeReservedQty" :unit="line.uom" />
                    </span>
                  </td>
                  <td style="text-align: right;">
                    <QuantityText :value="line.unshippedQty" :unit="line.uom" />
                  </td>
                  <td style="text-align: center;">
                    <div class="line-actions">
                      <button
                        v-if="isActionEnabled('directPick') && order.status === 'Approved' && parseFloat(line.unshippedQty) > 0 && parseFloat(line.orderedQty) > parseFloat(line.pickedQty)"
                        type="button"
                        class="btn-mini"
                        @click="openDirectPick(line)"
                      >
                        直接拣货
                      </button>
                      <button
                        v-if="isActionEnabled('returnPick') && order.status === 'Approved' && parseFloat(line.shippingStagedQty) > 0"
                        type="button"
                        class="btn-mini btn-warn-mini"
                        @click="openReturnPickModal(line)"
                      >
                        退回暂存
                      </button>
                    </div>
                  </td>
                </tr>
              </tbody>
            </table>
          </div>
        </div>

        <!-- 履约事件时间线 -->
        <div v-if="order.events && order.events.length > 0" class="events-section">
          <h3>销售履约与审计时间线</h3>
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

      <!-- 底部操作 -->
      <div class="drawer-footer">
        <button type="button" class="btn btn-secondary" @click="handleClose">关闭抽屉</button>
      </div>
    </div>

    <!-- 弹窗 1：直接拣货 -->
    <div v-if="isPickModalOpen && selectedLineForPick" class="modal-mask" @click.self="isPickModalOpen = false">
      <div class="modal-panel">
        <div class="modal-header">
          <h3 class="modal-title">执行销售直接拣货</h3>
          <button type="button" class="btn-close" @click="isPickModalOpen = false">✕</button>
        </div>
        <form class="modal-body" @submit.prevent="submitPick">
          <div class="rule-hint">
            优先消耗未拣预留，不足部分自动预留；实物与预留同步移动至后端返回的发货暂存位。
          </div>
          <div class="form-item">
            <label>物料信息</label>
            <input :value="`${selectedLineForPick.productName} (${selectedLineForPick.sku})`" type="text" class="form-input" disabled />
          </div>
          <div class="form-item">
            <label>来源库位 <span class="req">*</span></label>
            <select v-model="pickSourceLocationId" class="form-select" required>
              <option value="">请选择真实来源库位</option>
              <option v-for="location in sourceLocations" :key="location.id" :value="String(location.id)">
                {{ location.code }} ({{ location.name }})
              </option>
            </select>
          </div>
          <div class="form-item">
            <label>本次拣货数量 <span class="req">*</span></label>
            <input v-model="pickQtyInput" type="text" class="form-input text-cyan font-bold" required />
          </div>
          <div class="modal-footer">
            <button type="button" class="btn-secondary" @click="isPickModalOpen = false">取消</button>
            <button type="submit" class="btn-primary" :disabled="actionLoading">确认拣货</button>
          </div>
        </form>
      </div>
    </div>

    <!-- 弹窗 2：退回未发货拣货 -->
    <div v-if="isReturnModalOpen && selectedLineForReturn" class="modal-mask" @click.self="isReturnModalOpen = false">
      <div class="modal-panel">
        <div class="modal-header">
          <h3 class="modal-title">退回已拣货物至常规存储位</h3>
          <button type="button" class="btn-close" @click="isReturnModalOpen = false">✕</button>
        </div>
        <form class="modal-body" @submit.prevent="submitReturnPick">
          <div class="rule-hint">
            将后端返回的发货暂存位中未发货的实物及等量有效预留移回合法存储位，减少订单行 pickedQty。
          </div>
          <div class="form-item">
            <label>退回目标库位 <span class="req">*</span></label>
            <select v-model="returnToLocationId" class="form-select" required>
              <option value="">请选择真实退回库位</option>
              <option v-for="location in sourceLocations" :key="location.id" :value="String(location.id)">
                {{ location.code }} ({{ location.name }})
              </option>
            </select>
          </div>
          <div class="form-item">
            <label>退回数量 (最大: {{ selectedLineForReturn.shippingStagedQty }}) <span class="req">*</span></label>
            <input v-model="returnQtyInput" type="text" class="form-input text-danger font-bold" required />
          </div>
          <div class="form-item">
            <label>退回原因</label>
            <input v-model="returnReason" type="text" class="form-input" placeholder="如: 批次包装问题或客户延迟发运" />
          </div>
          <div class="modal-footer">
            <button type="button" class="btn-secondary" @click="isReturnModalOpen = false">取消</button>
            <button type="submit" class="btn-primary" :disabled="actionLoading">确认退回</button>
          </div>
        </form>
      </div>
    </div>

    <!-- 弹窗 3：确认发货 -->
    <ShipmentConfirmView
      v-model:visible="isShipmentOpen"
      :order="order"
      :submitting="actionLoading"
      @confirm="handleConfirmShipment"
      @close="isShipmentOpen = false"
    />

    <!-- 弹窗 4：预留明细与异常释放 -->
    <ReservationDetailView
      v-model:visible="isReservationDetailOpen"
      :order="order"
      :releasing="actionLoading"
      @release="handleReleaseReservation"
      @close="isReservationDetailOpen = false"
    />

    <!-- 弹窗 5：人工完成确认 -->
    <ConfirmDialog
      v-model:visible="isManualCompleteOpen"
      title="人工完成销售订单"
      message="人工完成将受控终止剩余未履约余量并释放对应预留。若各行存在未发货的发货暂存数量，必须先退回拣货。请填写完成原因："
      danger
      :loading="actionLoading"
      @confirm="executeManualComplete"
    >
      <div class="reason-box">
        <textarea
          v-model="manualCompleteReason"
          class="reason-area"
          rows="3"
          placeholder="必填完成原因说明..."
        ></textarea>
      </div>
    </ConfirmDialog>
  </div>
</template>

<script setup lang="ts">
import { isActionAllowed as checkAction, getActionDisabledReason as getDisabledReason } from "../../utils/actionGuard";
import type { AllowedAction } from "../../types/common";
/**
 * 销售订单详情抽屉组件 (SalesOrderDetailView)
 * 职责：并列展示生命周期、履约进度与完成方式；按行展示 5 个派生数量；严格依据 allowedActions 控制按钮
 */
import { ref, watch } from "vue";
import { ApiError } from "@/utils/request";
import { useCommand } from "@/composables/useCommand";
import CommandFeedback from "@/components/common/CommandFeedback.vue";
import { useRouter } from "vue-router";
import StatusBadge from "@/components/common/StatusBadge.vue";
import QuantityText from "@/components/common/QuantityText.vue";
import ErrorState from "@/components/common/ErrorState.vue";
import ConfirmDialog from "@/components/common/ConfirmDialog.vue";
import ReservationDetailView from "./ReservationDetailView.vue";
import ShipmentConfirmView from "./ShipmentConfirmView.vue";
import type { ViewState } from "@/types/common";
import type { SalesOrder, SalesOrderLine } from "@/types/sales";
import { stringSub, type Location } from "@/types/inventory";
import { getLocations } from "@/api/masterData";

const router = useRouter();

import {
  getSalesOrderById,
  submitSalesOrder,
  approveSalesOrder,
  completeSalesOrder,
  confirmDirectPick,
  returnPick,
  releaseReservation,
  confirmShipment,
} from "@/api/sales";

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
  (e: "pick-success"): void;
}>();

const viewState = ref<ViewState>("loading");
const errorMessage = ref("");
const order = ref<SalesOrder | null>(null);
const { execute, retry, isExecuting, canRetry, lastError } = useCommand();
const actionLoading = ref(false);
const sourceLocations = ref<Location[]>([]);
const shippingLocationId = ref("");

function handleGoTrace() {
  if (!order.value) return;
  router.push({
    path: "/traceability",
    query: {
      entry_type: "SALES_ORDER",
      entity_id: String(order.value.id),
    },
  });
}

// 弹窗状态
const isPickModalOpen = ref(false);
const selectedLineForPick = ref<SalesOrderLine | null>(null);
const pickSourceLocationId = ref("");
const pickQtyInput = ref("");

const isReturnModalOpen = ref(false);
const selectedLineForReturn = ref<SalesOrderLine | null>(null);
const returnToLocationId = ref("");
const returnQtyInput = ref("");
const returnReason = ref("");

const isShipmentOpen = ref(false);
const isReservationDetailOpen = ref(false);

const isManualCompleteOpen = ref(false);
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
  if (error instanceof ApiError && error.httpStatus === 404) return "销售订单资源不存在或已被删除（404）。";
  if (error instanceof ApiError && error.httpStatus === 403) return "您没有查看该销售订单的权限（403）。";
  return error instanceof Error ? error.message : "网络请求异常";
}

async function fetchDetail() {
  if (!props.orderId) return;
  viewState.value = "loading";
  errorMessage.value = "";
  try {
    const res = await getSalesOrderById(props.orderId);
    order.value = res.data;
    await loadLocations();
    viewState.value = "ready";
  } catch (err: any) {
    console.error("[SalesOrderDetailView] 获取失败:", err);
    errorMessage.value = detailLoadErrorMessage(err);
    viewState.value = "error";
  }
}

// 替换为调用 actionGuard 的版本
function isActionEnabled(actionKey: string): boolean {
  if (!order.value) return false;
  return checkAction(order.value.allowedActions, actionKey);
}

function lifecycleBadgeType(status: string): any {
  const map: Record<string, string> = {
    Draft: "default",
    Submitted: "primary",
    Approved: "info",
    Completed: "success",
  };
  return map[status] || "default";
}

function lifecycleText(status: string): string {
  const map: Record<string, string> = {
    Draft: "未提交",
    Submitted: "已提交",
    Approved: "履约中",
    Completed: "已完成",
  };
  return map[status] || status;
}

function fulfillmentBadgeType(st: string): any {
  const map: Record<string, string> = {
    NotStarted: "default",
    InProgress: "warning",
    FullyShipped: "success",
  };
  return map[st] || "default";
}

function fulfillmentText(st: string): string {
  const map: Record<string, string> = {
    NotStarted: "尚未开始",
    InProgress: "履约处理中",
    FullyShipped: "全部发货",
  };
  return map[st] || st;
}

function handleClose() {
  emit("update:visible", false);
  emit("close");
}

async function handleSubmit() {
  if (!order.value) return;
  actionLoading.value = true;
  try {
    await execute((key) => submitSalesOrder(order.value!.id, key), { onConflict: fetchDetail });
    await fetchDetail();
    emit("refresh");
  } catch (err: any) {
    alert(err?.message || "提交失败");
  } finally {
    actionLoading.value = false;
  }
}

async function handleApprove() {
  if (!order.value) return;
  actionLoading.value = true;
  try {
    await execute((key) => approveSalesOrder(order.value!.id, key), { onConflict: fetchDetail });
    await fetchDetail();
    emit("refresh");
  } catch (err: any) {
    alert(err?.message || "审核失败");
  } finally {
    actionLoading.value = false;
  }
}

function openDirectPick(line?: SalesOrderLine) {
  if (!line && order.value && order.value.lines.length > 0) {
    line = order.value.lines[0];
  }
  if (!line) return;
  selectedLineForPick.value = line;
  pickQtyInput.value = stringSub(line.orderedQty, line.pickedQty);
  pickSourceLocationId.value = line.sourceLocationId ? String(line.sourceLocationId) : "";
  isPickModalOpen.value = true;
}

async function submitPick() {
  if (!order.value || !selectedLineForPick.value || !pickSourceLocationId.value || !shippingLocationId.value) return;
  const currentOrder = order.value;
  const line = selectedLineForPick.value;
  actionLoading.value = true;
  try {
    await execute(async (key) => {
      const response = await confirmDirectPick({
      salesOrderId: String(currentOrder.id),
      lines: [{
        salesOrderLineId: String(line.id),
        pickedQty: pickQtyInput.value,
        sourceLocationId: pickSourceLocationId.value,
        shippingLocationId: shippingLocationId.value,
      }],
      }, key);
      if (!response?.data?.operationId) {
        throw new Error("直接拣货响应未返回服务端 operationId，已停止后续页面跳转。");
      }
      isPickModalOpen.value = false;
      await fetchDetail();
      emit("refresh");
      emit("pick-success");
    }, { onConflict: fetchDetail });
  } catch (err: any) {
    alert(err?.message || "直接拣货失败");
  } finally {
    actionLoading.value = false;
  }
}

function openReturnPickModal(line?: SalesOrderLine) {
  if (!line && order.value && order.value.lines.length > 0) {
    line = order.value.lines[0];
  }
  if (!line) return;
  selectedLineForReturn.value = line;
  returnQtyInput.value = line.shippingStagedQty;
  returnToLocationId.value = line.sourceLocationId ? String(line.sourceLocationId) : "";
  isReturnModalOpen.value = true;
}

async function submitReturnPick() {
  if (!order.value || !selectedLineForReturn.value || !returnToLocationId.value) return;
  const currentOrder = order.value;
  const line = selectedLineForReturn.value;
  actionLoading.value = true;
  try {
    await execute(async (key) => {
      const response = await returnPick({
      salesOrderId: String(currentOrder.id),
      lines: [{
        salesOrderLineId: String(line.id),
        returnQty: returnQtyInput.value,
        toLocationId: returnToLocationId.value,
      }],
      }, key);
      if (!response?.data?.operationId) {
        throw new Error("拣货退回响应未返回服务端 operationId，已停止后续页面跳转。");
      }
      isReturnModalOpen.value = false;
      await fetchDetail();
      emit("refresh");
    }, { onConflict: fetchDetail });
  } catch (err: any) {
    alert(err?.message || "退回失败");
  } finally {
    actionLoading.value = false;
  }
}

async function handleConfirmShipment(payload: any) {
  actionLoading.value = true;
  try {
    await execute(async (key) => {
      const response = await confirmShipment(payload, key);
      if (!response?.data?.operationId) {
        throw new Error("发货响应未返回服务端 operationId，已停止后续页面刷新。");
      }
      isShipmentOpen.value = false;
      await fetchDetail();
      emit("refresh");
    }, { onConflict: fetchDetail });
  } catch (err: any) {
    alert(err?.message || "发货失败");
  } finally {
    actionLoading.value = false;
  }
}

/** 加载当前订单仓库下的真实库位 UUID，供拣货与退回命令引用。 */
async function loadLocations() {
  if (!order.value?.warehouseId) {
    sourceLocations.value = [];
    return;
  }
  const [sourceResponse, shippingResponse] = await Promise.all([
    getLocations({ warehouseId: order.value.warehouseId, type: "Storage", status: "ACTIVE", page: 1, size: 20 }),
    getLocations({ warehouseId: order.value.warehouseId, type: "ShippingStaging", status: "ACTIVE", page: 1, size: 20 }),
  ]);
  sourceLocations.value = (sourceResponse.data.records || []).filter((location) =>
    String(location.warehouseId) === String(order.value?.warehouseId)
    && location.type === "Storage"
    && location.status === "ACTIVE",
  );
  shippingLocationId.value = String((shippingResponse.data.records || []).find((location) =>
    String(location.warehouseId) === String(order.value?.warehouseId)
    && location.type === "ShippingStaging"
    && location.status === "ACTIVE",
  )?.id || "");
}

async function handleReleaseReservation(payload: any) {
  actionLoading.value = true;
  try {
    await execute(async (key) => {
      await releaseReservation(payload.salesOrderId, payload, key);
      isReservationDetailOpen.value = false;
      await fetchDetail();
      emit("refresh");
    }, { onConflict: fetchDetail });
  } catch (err: any) {
    alert(err?.message || "释放预留失败");
  } finally {
    actionLoading.value = false;
  }
}

function openManualComplete() {
  if (!order.value) return;
  const hasStaged = order.value.lines.some((l) => parseFloat(l.shippingStagedQty) > 0);
  if (hasStaged) {
    alert("当前订单存在尚未发货的发货暂存数量，不能直接人工完成！请先执行【退回未发货拣货】。");
    return;
  }
  manualCompleteReason.value = "客户调整生产计划终止剩余交货";
  isManualCompleteOpen.value = true;
}

async function executeManualComplete() {
  if (!order.value) return;
  if (!manualCompleteReason.value.trim()) {
    alert("必须填写人工完成原因！");
    return;
  }
  actionLoading.value = true;
  try {
    const currentOrder = order.value;
    await execute(async (key) => {
      await completeSalesOrder(currentOrder.id, {
        completionReason: manualCompleteReason.value,
      }, key);
      isManualCompleteOpen.value = false;
      await fetchDetail();
      emit("refresh");
    }, { onConflict: fetchDetail });
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
  max-width: 1080px;
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
  flex-wrap: wrap;
}

.so-title {
  margin: 0;
  font-size: 18px;
  color: #f8fafc;
}

.badges-group {
  display: flex;
  align-items: center;
  gap: 8px;
}

.tag-manual {
  font-size: 11px;
  background: rgba(239, 68, 68, 0.15);
  color: #f87171;
  border: 1px solid rgba(239, 68, 68, 0.3);
  padding: 2px 8px;
  border-radius: 4px;
}

.tag-normal {
  font-size: 11px;
  background: rgba(52, 211, 153, 0.15);
  color: #34d399;
  border: 1px solid rgba(52, 211, 153, 0.3);
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
  gap: 18px;
}

.actions-panel {
  background: rgba(30, 41, 59, 0.5);
  border: 1px solid rgba(255, 255, 255, 0.08);
  border-radius: 8px;
  padding: 14px 18px;
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.normal-actions,
.exception-actions {
  display: flex;
  align-items: center;
  gap: 10px;
  flex-wrap: wrap;
}

.section-lbl {
  font-size: 12px;
  color: #94a3b8;
  min-width: 90px;
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

.highlight-btn {
  background: #0ea5e9;
  box-shadow: 0 0 10px rgba(14, 165, 233, 0.4);
}

.btn-success {
  background: rgba(52, 211, 153, 0.15);
  color: #34d399;
  border-color: rgba(52, 211, 153, 0.3);
}

.btn-warning {
  background: rgba(251, 191, 36, 0.15);
  color: #fbbf24;
  border-color: rgba(251, 191, 36, 0.3);
}

.btn-danger {
  background: rgba(239, 68, 68, 0.15);
  color: #f87171;
  border-color: rgba(239, 68, 68, 0.3);
}

.btn-secondary {
  background: rgba(51, 65, 85, 0.6);
  color: #cbd5e1;
  border: 1px solid rgba(255, 255, 255, 0.1);
}

.info-card-row {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(200px, 1fr));
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
  margin: 0;
  font-size: 15px;
  color: #f1f5f9;
}

.lines-header {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  margin-bottom: 12px;
}

.formula-hint {
  font-size: 12px;
  color: #38bdf8;
  font-family: var(--font-mono, monospace);
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

.text-amber {
  color: #fbbf24;
}

.text-cyan {
  color: #38bdf8;
}

.text-success {
  color: #34d399;
}

.font-bold {
  font-weight: 700;
}

.line-actions {
  display: flex;
  align-items: center;
  gap: 6px;
  justify-content: center;
}

.btn-mini {
  padding: 2px 8px;
  background: #0284c7;
  border: none;
  border-radius: 4px;
  color: #ffffff;
  font-size: 11px;
  cursor: pointer;
}

.btn-warn-mini {
  background: rgba(251, 191, 36, 0.15);
  color: #fbbf24;
  border: 1px solid rgba(251, 191, 36, 0.3);
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

/* 模态框 */
.modal-mask {
  position: fixed;
  inset: 0;
  background: rgba(0, 0, 0, 0.7);
  backdrop-filter: blur(4px);
  display: flex;
  align-items: center;
  justify-content: center;
  z-index: 1100;
  padding: 16px;
}

.modal-panel {
  background: #0f172a;
  border: 1px solid rgba(255, 255, 255, 0.15);
  border-radius: 10px;
  width: 100%;
  max-width: 500px;
  box-shadow: 0 20px 25px -5px rgba(0, 0, 0, 0.5);
}

.modal-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 16px 20px;
  border-bottom: 1px solid rgba(255, 255, 255, 0.08);
}

.modal-title {
  margin: 0;
  font-size: 16px;
  color: #f8fafc;
}

.modal-body {
  padding: 20px;
  display: flex;
  flex-direction: column;
  gap: 14px;
}

.rule-hint {
  font-size: 12px;
  color: #cbd5e1;
  background: rgba(56, 189, 248, 0.08);
  padding: 10px;
  border-radius: 6px;
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

.modal-footer {
  display: flex;
  align-items: center;
  justify-content: flex-end;
  gap: 12px;
  padding-top: 10px;
}

.reason-box {
  margin-top: 10px;
}

.reason-area {
  width: 100%;
  background: rgba(30, 41, 59, 0.8);
  border: 1px solid rgba(255, 255, 255, 0.12);
  color: #f8fafc;
  padding: 8px 10px;
  border-radius: 6px;
  font-size: 13px;
  outline: none;
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
