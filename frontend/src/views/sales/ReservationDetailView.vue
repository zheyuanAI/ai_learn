<template>
  <el-dialog
    :model-value="visible"
    width="760px"
    destroy-on-close
    append-to-body
    @close="handleClose"
  >
    <template #header>
      <div style="display: flex; align-items: center; gap: 12px">
        <span style="font-weight: 600; font-size: 16px; color: #f1f5f9">销售订单预留与库位分配详情</span>
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
        title="底层预留逻辑事实"
        description="在当前业务规范下，“直接拣货”在同一事务内优先使用未拣预留，不足部分在来源库位自动预留，再随实物同步迁移至发货暂存位（ShippingStaging）；发货出库时正式释放预留。本视图展示底层预留事实支撑审计追溯。"
      />

      <!-- 预留概览卡片 -->
      <div class="overview-grid" style="display: flex; flex-direction: column; gap: 12px">
        <el-card
          v-for="line in order.lines"
          :key="line.id"
          shadow="never"
          style="background: rgba(30, 41, 59, 0.4); border: 1px solid rgba(140, 162, 184, 0.2)"
        >
          <div style="display: flex; justify-content: space-between; align-items: center; margin-bottom: 12px">
            <span style="font-weight: 600; color: #f1f5f9">{{ line.productName }} ({{ line.sku }})</span>
            <el-tag size="small" type="primary">行项: {{ line.lineNo }}</el-tag>
          </div>
          <div class="card-nums">
            <div class="num-item">
              <span class="num-lbl">要求订购量</span>
              <QuantityText :value="line.orderedQty" :unit="line.uom" />
            </div>
            <div class="num-item">
              <span class="num-lbl">累计预留量</span>
              <QuantityText :value="line.reservedQty" :unit="line.uom" />
            </div>
            <div class="num-item">
              <span class="num-lbl">已预留未拣</span>
              <span :class="parseFloat(line.unpickedQty) > 0 ? 'text-amber' : ''">
                <QuantityText :value="line.unpickedQty" :unit="line.uom" />
              </span>
            </div>
            <div class="num-item">
              <span class="num-lbl">发货暂存占用</span>
              <span :class="parseFloat(line.shippingStagedQty) > 0 ? 'text-cyan' : ''">
                <QuantityText :value="line.shippingStagedQty" :unit="line.uom" />
              </span>
            </div>
            <div class="num-item">
              <span class="num-lbl">库内有效预留</span>
              <span class="text-success font-bold">
                <QuantityText :value="line.activeReservedQty" :unit="line.uom" />
              </span>
            </div>
          </div>

          <!-- 异常释放按钮 -->
          <div v-if="canRelease && parseFloat(line.unpickedQty) > 0" class="release-action-bar" style="margin-top: 12px; display: flex; align-items: center; justify-content: space-between; padding-top: 10px; border-top: 1px dashed rgba(140, 162, 184, 0.2)">
            <span class="hint" style="font-size: 12px; color: #8ca2b8">存在已预留但未拣货数量，若终止履约可在此异常释放：</span>
            <el-button
              type="danger"
              size="small"
              :disabled="releasing"
              @click="openReleaseLine(line)"
            >
              释放未拣预留 ({{ line.unpickedQty }})
            </el-button>
          </div>
        </el-card>
      </div>
    </div>

    <template #footer>
      <el-button @click="handleClose">关闭</el-button>
    </template>
  </el-dialog>

  <!-- 释放预留对话框 -->
  <ConfirmDialog
    v-model:visible="isReleaseDialogOpen"
    title="异常释放未拣预留"
    :message="releaseDialogMessage"
    danger
    :loading="releasing"
    @confirm="executeRelease"
  >
    <div class="release-reason-box" style="margin-top: 12px">
      <div style="font-size: 13px; margin-bottom: 6px; color: #cbd5e1">释放原因说明 <span style="color: #f43f5e">*</span></div>
      <el-input v-model="releaseReason" placeholder="如: 客户调减需求或人工完成前清理..." />
    </div>
  </ConfirmDialog>
</template>

<script setup lang="ts">
/**
 * 销售订单预留明细与异常释放组件 (ReservationDetailView)
 * 职责：展示订单各行项有效预留、发货暂存占用量，并支持异常释放未拣预留
 */
import { ref, computed } from "vue";
import { ElMessage } from "element-plus";
import QuantityText from "@/components/common/QuantityText.vue";
import ConfirmDialog from "@/components/common/ConfirmDialog.vue";
import type { SalesOrder, SalesOrderLine } from "@/types/sales";

const props = withDefaults(
  defineProps<{
    visible: boolean;
    order: SalesOrder | null;
    canRelease?: boolean;
    releasing?: boolean;
  }>(),
  {
    visible: false,
    order: null,
    canRelease: false,
    releasing: false,
  }
);

const emit = defineEmits<{
  (e: "update:visible", val: boolean): void;
  (e: "release", payload: any): void;
  (e: "close"): void;
}>();

const isReleaseDialogOpen = ref(false);
const activeLineToRelease = ref<SalesOrderLine | null>(null);
const releaseReason = ref("人工调整释放未拣预留恢复可用库存");

const releaseDialogMessage = computed(() => {
  if (!activeLineToRelease.value) return "";
  return `确认释放物料 ${activeLineToRelease.value.sku} 的未拣预留数量 ${activeLineToRelease.value.unpickedQty} ${activeLineToRelease.value.uom} 吗？释放后对应数量恢复为仓库可用库存，实物数量不变。`;
});

function handleClose() {
  emit("update:visible", false);
  emit("close");
}

function openReleaseLine(line: SalesOrderLine) {
  activeLineToRelease.value = line;
  releaseReason.value = "异常调减释放未拣预留";
  isReleaseDialogOpen.value = true;
}

function executeRelease() {
  if (!props.order || !activeLineToRelease.value) return;
  if (!releaseReason.value.trim()) {
    ElMessage.warning("必须填写释放原因！");
    return;
  }
  isReleaseDialogOpen.value = false;
  emit("release", {
    salesOrderId: props.order.id,
    releaseLines: [
      {
        salesOrderLineId: activeLineToRelease.value.id,
        releaseQty: activeLineToRelease.value.unpickedQty,
        reason: releaseReason.value,
      },
    ],
  });
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
  max-width: 760px;
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

.overview-grid {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.line-res-card {
  background: rgba(30, 41, 59, 0.4);
  border: 1px solid rgba(255, 255, 255, 0.08);
  border-radius: 8px;
  padding: 14px;
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.card-top {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.sku-name {
  font-size: 14px;
  font-weight: 600;
  color: #f8fafc;
}

.line-tag {
  font-size: 11px;
  color: #94a3b8;
  background: rgba(0, 0, 0, 0.25);
  padding: 2px 6px;
  border-radius: 4px;
}

.card-nums {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(110px, 1fr));
  gap: 10px;
  background: rgba(15, 23, 42, 0.5);
  padding: 10px;
  border-radius: 6px;
}

.num-item {
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.num-lbl {
  font-size: 11px;
  color: #64748b;
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

.release-action-bar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  border-top: 1px dashed rgba(255, 255, 255, 0.08);
  padding-top: 8px;
}

.hint {
  font-size: 11px;
  color: #94a3b8;
}

.btn-release-sm {
  padding: 4px 10px;
  background: rgba(239, 68, 68, 0.15);
  border: 1px solid rgba(239, 68, 68, 0.3);
  color: #f87171;
  border-radius: 4px;
  font-size: 12px;
  cursor: pointer;
}

.btn-release-sm:hover:not(:disabled) {
  background: #dc2626;
  color: #ffffff;
}

.dialog-footer {
  display: flex;
  align-items: center;
  justify-content: flex-end;
  padding: 14px 20px;
  background: rgba(0, 0, 0, 0.2);
  border-top: 1px solid rgba(255, 255, 255, 0.08);
}

.btn {
  padding: 8px 16px;
  border-radius: 6px;
  font-size: 13px;
  cursor: pointer;
  border: 1px solid transparent;
}

.btn-secondary {
  background: rgba(51, 65, 85, 0.6);
  color: #cbd5e1;
  border-color: rgba(255, 255, 255, 0.1);
}

.release-reason-box {
  margin-top: 12px;
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
