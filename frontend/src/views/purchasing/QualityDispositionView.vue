<template>
  <div class="quality-disposition-view">
    <CommandFeedback :error="lastError" :can-retry="canRetry" :executing="isExecuting" @retry="retry" />
    <!-- 统一页面头部 -->
    <PageHeader
      title="采购到货质检与处置控制台"
      tag="CORE / QUALITY / PURCHASE QA"
      description="生产质检人员执行到货检验（inspected_qty = qualified_qty + unqualified_qty），质检只生成质量事实不改变库存；合格品决定放行，不合格品决定退回或报废；仓库人员确认处置执行后，放行货物从质量隔离位（QH）移至收货暂存位（RS），报废与退回扣减 QH 实物库存。"
    >
      <template #actions>
        <button type="button" class="btn-primary" @click="openInspectModal">
          <span>＋ 录入到货质检结果</span>
        </button>
      </template>
    </PageHeader>

    <!-- 顶部分类切换 -->
    <div class="tab-nav">
      <button
        type="button"
        class="tab-item"
        :class="{ 'is-active': activeTab === 'inspections' }"
        @click="activeTab = 'inspections'"
      >
        <span>质检检验事实记录</span>
        <span class="count-badge">{{ inspections.length }}</span>
      </button>
      <button
        type="button"
        class="tab-item"
        :class="{ 'is-active': activeTab === 'dispositions' }"
        @click="activeTab = 'dispositions'"
      >
        <span>质量处置决定与执行</span>
        <span class="count-badge">{{ dispositions.length }}</span>
      </button>
    </div>

    <!-- 1. 检验记录表格 -->
    <div v-if="activeTab === 'inspections'">
      <EmptyState
        v-if="inspections.length === 0"
        title="暂无质检检验记录"
        description="采购实际接收进入 QualityHold 质量隔离位后，质检人员可在此录入合格与不合格数量。"
      />
      <DataTable
        v-else
        :columns="inspectionColumns"
        :data="inspections"
        :loading="loading"
      >
        <template #inspectionNo="{ value }">
          <span class="mono-code">{{ value }}</span>
        </template>
        <template #purchaseOrderNo="{ value }">
          <span class="mono-text">{{ value }}</span>
        </template>
        <template #inspectedQty="{ row }">
          <QuantityText :value="row.inspectedQty" unit="件" />
        </template>
        <template #qualifiedQty="{ row }">
          <span class="text-success">
            <QuantityText :value="row.qualifiedQty" unit="件" />
          </span>
        </template>
        <template #unqualifiedQty="{ row }">
          <span :class="parseFloat(row.unqualifiedQty) > 0 ? 'text-danger' : 'text-muted'">
            <QuantityText :value="row.unqualifiedQty" unit="件" />
          </span>
        </template>
        <template #actions="{ row }">
          <button type="button" class="btn-link" @click="openDecideModal(row)">
            做出处置决定
          </button>
        </template>
      </DataTable>
    </div>

    <!-- 2. 处置决定与执行表格 -->
    <div v-else>
      <EmptyState
        v-if="dispositions.length === 0"
        title="暂无处置决定记录"
        description="完成到货质检后，生产质检或采购人员可生成放行、退回或报废处置决定。"
      />
      <DataTable
        v-else
        :columns="dispositionColumns"
        :data="dispositions"
        :loading="loading"
      >
        <template #dispositionNo="{ value }">
          <span class="mono-code">{{ value }}</span>
        </template>
        <template #dispositionType="{ value }">
          <StatusBadge
            :type="value === 'Release' ? 'success' : value === 'Scrap' ? 'danger' : 'warning'"
            :text="value === 'Release' ? '合格放行移位' : value === 'Scrap' ? '不合格报废' : '退回供应方'"
          />
        </template>
        <template #dispositionQty="{ row }">
          <QuantityText :value="row.dispositionQty" unit="件" />
        </template>
        <template #status="{ value }">
          <StatusBadge
            :type="value === 'Completed' ? 'success' : 'warning'"
            :text="value === 'Completed' ? '仓库已执行确认' : '待仓库执行确认'"
          />
        </template>
        <template #actions="{ row }">
          <button
            v-if="row.status === 'PendingExecution'"
            type="button"
            class="btn-link confirm-act"
            @click="openExecuteConfirm(row)"
          >
            确认执行实物处置
          </button>
          <span v-else class="text-muted">已归档</span>
        </template>
      </DataTable>
    </div>

    <!-- 录入质检结果弹窗 -->
    <div v-if="isInspectOpen" class="modal-mask" @click.self="isInspectOpen = false">
      <div class="modal-panel">
        <div class="modal-header">
          <h3 class="modal-title">录入采购到货质检结果</h3>
          <button type="button" class="btn-close" @click="isInspectOpen = false">✕</button>
        </div>
        <form class="modal-body" @submit.prevent="submitInspect">
          <div v-if="!inspectForm.purchaseReceiptId || !inspectForm.purchaseReceiptLineId" class="contract-alert" role="alert">
            <strong>质检提交已阻止：</strong>
            必须从真实收货确认响应取得独立 receiptId 和收货行 ID；采购订单行 ID、订单 ID 或随机 UUID 不能代用。
          </div>
          <!-- 订单与凭证上下文选择（消除手填 UUID，修复 F04） -->
          <div v-if="queryPoNo" class="info-alert" style="margin-bottom: 12px; padding: 8px 12px; background: rgba(56, 189, 248, 0.1); border: 1px solid rgba(56, 189, 248, 0.25); border-radius: 6px; font-size: 13px; color: #bae6fd;">
            <span>当前针对订单 <strong>{{ queryPoNo }}</strong> 录入质检（收货凭证: <span class="font-mono text-xs">{{ inspectForm.purchaseReceiptId }}</span>；收货行: <span class="font-mono text-xs">{{ inspectForm.purchaseReceiptLineId || '未返回' }}</span>）</span>
          </div>
          <div v-else class="form-item">
            <label>关联采购订单 <span class="req">*</span></label>
            <select v-model="inspectForm.purchaseOrderId" class="form-select" required @change="onOrderChange(inspectForm.purchaseOrderId)">
              <option value="">请选择待质检采购订单</option>
              <option v-for="order in availableOrders" :key="order.id" :value="order.id">
                {{ order.poNo }} - {{ order.supplierName }} ({{ order.status }})
              </option>
            </select>
          </div>

          <div v-if="selectedOrderLines.length > 0 && !queryPoNo" class="form-item">
            <label>检验物料行项 <span class="req">*</span></label>
            <select
              :value="selectedOrderLineId"
              class="form-select"
              required
              @change="(e: any) => {
                const target = selectedOrderLines.find(l => String(l.id) === e.target.value);
                if (target) onLineChange(target);
              }"
            >
              <option value="">请选择检验物料行</option>
              <option v-for="line in selectedOrderLines" :key="line.id" :value="line.id">
                {{ line.sku }} ({{ line.productName }}) - 实收待检: {{ line.receivedQty || line.orderedQty }} {{ line.uom }}
              </option>
            </select>
          </div>
          <div v-else-if="!queryPoNo" class="form-item">
            <label>物料 <span class="req">*</span></label>
            <select v-model="inspectForm.productId" class="form-select" required>
              <option value="">请选择物料</option>
              <option v-for="product in products" :key="product.id" :value="product.id">
                {{ product.sku }} - {{ product.name }}
              </option>
            </select>
          </div>
          <div class="form-grid">
            <div class="form-item">
              <label>检验总数量 <span class="req">*</span></label>
              <input v-model="inspectForm.inspectedQty" type="text" class="form-input" required @input="calcUnqualified" />
            </div>
            <div class="form-item">
              <label>质检合格数量 <span class="req">*</span></label>
              <input v-model="inspectForm.qualifiedQty" type="text" class="form-input text-success" required @input="calcUnqualified" />
            </div>
          </div>
          <div class="form-item">
            <label>质检不合格数量 (自动计算)</label>
            <input :value="inspectForm.unqualifiedQty" type="text" class="form-input text-danger" disabled />
          </div>
          <div v-if="parseFloat(inspectForm.unqualifiedQty || '0') > 0" class="form-item">
            <label>不合格原因 <span class="req">*</span></label>
            <input v-model="inspectForm.unqualifiedReason" type="text" class="form-input" required placeholder="如: 轴向尺寸公差超差 0.15mm" />
          </div>
          <div class="form-item">
            <label>检验说明与备注</label>
            <textarea v-model="inspectForm.inspectionRemark" class="form-textarea" rows="2" placeholder="抽样标准与检验过程说明..."></textarea>
          </div>
          <div class="modal-footer">
            <button type="button" class="btn-secondary" @click="isInspectOpen = false">取消</button>
            <button type="submit" class="btn-primary" :disabled="submitting">确认提交检验事实</button>
          </div>
        </form>
      </div>
    </div>

    <!-- 做出处置决定弹窗 -->
    <div v-if="isDecideOpen" class="modal-mask" @click.self="isDecideOpen = false">
      <div class="modal-panel">
        <div class="modal-header">
          <h3 class="modal-title">做出质量处置决定</h3>
          <button type="button" class="btn-close" @click="isDecideOpen = false">✕</button>
        </div>
        <form class="modal-body" @submit.prevent="submitDecide">
          <div class="form-item">
            <label>处置动作类型 <span class="req">*</span></label>
            <select v-model="decideForm.dispositionType" class="form-select" required>
              <option value="Release">合格放行上架 (Release ➔ 移至收货暂存位)</option>
              <option value="Scrap">不合格报废处理 (Scrap ➔ 扣减实物库存)</option>
              <option value="Return">退回供应方 (Return ➔ 扣减实物库存)</option>
            </select>
          </div>
          <div class="form-item">
            <label>处置数量 <span class="req">*</span></label>
            <input v-model="decideForm.dispositionQty" type="text" class="form-input" required />
          </div>
          <div class="form-item">
            <label>处置依据与原因</label>
            <textarea v-model="decideForm.reason" class="form-textarea" rows="2" placeholder="填写放行依据或报废/退货原因..."></textarea>
          </div>
          <div class="modal-footer">
            <button type="button" class="btn-secondary" @click="isDecideOpen = false">取消</button>
            <button type="submit" class="btn-primary" :disabled="submitting">确认下达处置决定</button>
          </div>
        </form>
      </div>
    </div>

    <!-- 仓库执行实物确认对话框 -->
    <ConfirmDialog
      v-model:visible="isExecuteOpen"
      title="确认执行质量处置实物处理"
      :message="executeDialogMessage"
      :loading="submitting"
      @confirm="executeDisposition"
    >
      <template v-if="selectedDisp?.dispositionType === 'Release'">
        <div class="form-item">
          <label>放行至收货暂存位 <span class="req">*</span></label>
          <select v-model="receivingStagingLocationId" class="form-select" required>
            <option value="">请选择 ReceivingStaging 库位</option>
              <option v-for="location in filteredReceivingStagingLocations" :key="location.id" :value="location.id">
                {{ location.code }} - {{ location.name }}
              </option>
          </select>
        </div>
        <div class="form-item">
          <label>后续上架目标库位（可选）</label>
          <select v-model="putawayTargetLocationId" class="form-select">
            <option value="">由后端选择默认 Storage 库位</option>
            <option v-for="location in filteredStorageLocations" :key="location.id" :value="location.id">
              {{ location.code }} - {{ location.name }}
            </option>
          </select>
        </div>
      </template>
    </ConfirmDialog>
  </div>
</template>

<script setup lang="ts">
/**
 * 采购到货质检与处置视图 (QualityDispositionView)
 * 职责：质检录入、处置决定（放行/报废/退回）与仓库实物执行确认
 */
import { ref, reactive, computed, onMounted } from "vue";
import { useCommand } from "@/composables/useCommand";
import CommandFeedback from "@/components/common/CommandFeedback.vue";
import { useRoute, useRouter } from "vue-router";
import PageHeader from "@/components/common/PageHeader.vue";
import DataTable, { type TableColumn } from "@/components/common/DataTable.vue";
import StatusBadge from "@/components/common/StatusBadge.vue";
import QuantityText from "@/components/common/QuantityText.vue";
import EmptyState from "@/components/common/EmptyState.vue";
import ConfirmDialog from "@/components/common/ConfirmDialog.vue";
import type {
  PurchaseQualityInspection,
  PurchaseQualityDisposition,
} from "@/types/purchasing";
import { stringSub, type Location, type Product } from "@/types/inventory";
import { getLocations, getProducts } from "@/api/masterData";
import {
  getQualityInspections,
  inspectQuality,
  getQualityDispositions,
  decideQualityDisposition,
  confirmQualityDisposition,
  getPurchaseOrders,
  getPurchaseOrderById,
} from "@/api/purchasing";

const route = useRoute();
const { execute, retry, isExecuting, canRetry, lastError } = useCommand();
const router = useRouter();

const activeTab = ref<"inspections" | "dispositions">("inspections");
const loading = ref(false);
const submitting = ref(false);
const inspectionWarehouseId = ref("");

const inspections = ref<PurchaseQualityInspection[]>([]);
const dispositions = ref<PurchaseQualityDisposition[]>([]);

const inspectionColumns: TableColumn[] = [
  { key: "inspectionNo", label: "检验编号", width: "150px" },
  { key: "purchaseOrderNo", label: "采购单号", width: "140px" },
  { key: "productId", label: "物料 UUID", width: "220px" },
  { key: "inspectedQty", label: "检验总量", width: "100px", align: "right" },
  { key: "qualifiedQty", label: "合格数量", width: "100px", align: "right" },
  { key: "unqualifiedQty", label: "不合格量", width: "100px", align: "right" },
  { key: "unqualifiedReason", label: "不合格原因", minWidth: "150px" },
  { key: "inspectedBy", label: "检验质检员", width: "110px" },
  { key: "actions", label: "操作", width: "120px", align: "center" },
];

const dispositionColumns: TableColumn[] = [
  { key: "dispositionNo", label: "处置编号", width: "150px" },
  { key: "purchaseOrderNo", label: "采购单号", width: "140px" },
  { key: "dispositionType", label: "处置决定", width: "130px", align: "center" },
  { key: "dispositionQty", label: "处置数量", width: "100px", align: "right" },
  { key: "status", label: "执行状态", width: "140px", align: "center" },
  { key: "decidedBy", label: "决定人", width: "100px" },
  { key: "executedBy", label: "仓库执行人", width: "100px" },
  { key: "actions", label: "操作", width: "130px", align: "center" },
];

// 质检弹窗与选择联动
const isInspectOpen = ref(false);
const queryPoNo = ref("");
const availableOrders = ref<any[]>([]);
const selectedOrderLines = ref<any[]>([]);
const selectedOrderLineId = ref("");

const inspectForm = reactive({
  purchaseOrderId: "",
  purchaseReceiptId: "",
  purchaseReceiptLineId: "",
  productId: "",
  inspectedQty: "",
  qualifiedQty: "",
  unqualifiedQty: "",
  unqualifiedReason: "",
  inspectionRemark: "",
});
const products = ref<Product[]>([]);
const receivingStagingLocations = ref<Location[]>([]);
const storageLocations = ref<Location[]>([]);

/**
 * 用途：只暴露与收货事实同仓、启用且类型正确的处置库位。
 * 入参：当前收货上下文的真实目标仓库 ID；出参：可供仓库执行选择的库位。
 * 流程：缺少同仓事实时返回空集合，防止把跨仓或未确认库位提交给后端。
 */
const filteredReceivingStagingLocations = computed(() => {
  if (!inspectionWarehouseId.value) return [];
  return receivingStagingLocations.value.filter(
    (location) => String(location.warehouseId) === String(inspectionWarehouseId.value),
  );
});

const filteredStorageLocations = computed(() => {
  if (!inspectionWarehouseId.value) return [];
  return storageLocations.value.filter(
    (location) => String(location.warehouseId) === String(inspectionWarehouseId.value),
  );
});

function calcUnqualified() {
  inspectForm.unqualifiedQty = stringSub(inspectForm.inspectedQty, inspectForm.qualifiedQty);
}

/** 订单选择联动：自动拉取明细行并选中第一行，消除手填 UUID (修复 F04) */
async function onOrderChange(orderId: string) {
  inspectForm.purchaseOrderId = orderId;
  inspectForm.purchaseReceiptLineId = "";
  selectedOrderLineId.value = "";
  try {
    const res = await getPurchaseOrderById(orderId);
    if (res.data && res.data.lines) {
      selectedOrderLines.value = res.data.lines;
    }
  } catch (err) {
    console.warn("[QualityDispositionView] 加载订单行项失败", err);
  }
}

/** 明细行选择联动：自动回显物料与默认实收数量 */
function onLineChange(line: any) {
  // 修改用途：订单行只用于预览产品和数量，绝不能冒充收货行 ID。
  selectedOrderLineId.value = String(line.id);
  inspectForm.productId = line.productId;
  const targetQty = String(line.receivedQty && parseFloat(line.receivedQty) > 0 ? line.receivedQty : "");
  inspectForm.inspectedQty = targetQty;
  inspectForm.qualifiedQty = targetQty;
  inspectForm.unqualifiedQty = "0";
}

// 处置决定弹窗
const isDecideOpen = ref(false);
const selectedInspect = ref<PurchaseQualityInspection | null>(null);
const decideForm = reactive({
  dispositionType: "Release" as "Release" | "Return" | "Scrap",
  dispositionQty: "",
  reason: "",
});

// 仓库执行弹窗
const isExecuteOpen = ref(false);
const selectedDisp = ref<PurchaseQualityDisposition | null>(null);
const receivingStagingLocationId = ref("");
const putawayTargetLocationId = ref("");

const executeDialogMessage = computed(() => {
  if (!selectedDisp.value) return "";
  if (selectedDisp.value.dispositionType === "Release") {
    return `确认将 ${selectedDisp.value.dispositionQty} 件合格品从 QualityHold 质量隔离位移动至所选 ReceivingStaging 收货暂存位吗？确认后将生成后续上架任务。`;
  }
  return `确认执行 ${selectedDisp.value.dispositionType === 'Scrap' ? '报废扣减' : '退回供应方'} 吗？将从 QualityHold 质量隔离位扣减 ${selectedDisp.value.dispositionQty} 件实物库存并生成不可篡改流水。`;
});

async function loadData() {
  loading.value = true;
  try {
    const [resI, resD] = await Promise.all([
      getQualityInspections(),
      getQualityDispositions(),
    ]);
    inspections.value = resI.data;
    dispositions.value = resD.data;
  } catch (err: any) {
    console.error("[QualityDispositionView] 加载失败:", err);
    alert(err?.message || "加载质检与处置记录失败");
  } finally {
    loading.value = false;
  }
}

/**
 * 加载质检与处置所需主数据，所有选择值使用后端返回的真实 UUID。
 */
async function loadReferenceOptions() {
  const [productResponse, receivingResponse, storageResponse, poResponse] = await Promise.all([
    getProducts({ page: 1, size: 20, status: "ACTIVE" }),
    getLocations({ page: 1, size: 20, warehouseId: inspectionWarehouseId.value || undefined, type: "ReceivingStaging", status: "ACTIVE" }),
    getLocations({ page: 1, size: 20, warehouseId: inspectionWarehouseId.value || undefined, type: "Storage", status: "ACTIVE" }),
    getPurchaseOrders({ page: 1, size: 20 }),
  ]);
  products.value = productResponse.data.records;
  receivingStagingLocations.value = receivingResponse.data.records;
  storageLocations.value = storageResponse.data.records;
  availableOrders.value = poResponse.data.records || [];
}

/**
 * 打开质检录入前刷新物料与合法处置库位。
 */
async function openInspectModal() {
  try {
    await loadReferenceOptions();
    isInspectOpen.value = true;
  } catch (error: any) {
    alert(error?.message || "加载质检选项失败");
  }
}

async function submitInspect() {
  if (!inspectForm.purchaseReceiptId || !inspectForm.purchaseReceiptLineId || !inspectForm.productId) {
    alert("缺少真实收货事实 ID：请从收货确认响应进入，不能使用采购订单行 ID 或随机 UUID。");
    return;
  }
  if (!inspectForm.inspectedQty || parseFloat(inspectForm.inspectedQty) <= 0) {
    alert("检验数量必须来自真实收货明细且大于 0。");
    return;
  }
  submitting.value = true;
  try {
    await execute(async (key) => {
      await inspectQuality(inspectForm.purchaseReceiptId, { ...inspectForm }, key);
      isInspectOpen.value = false;
      await loadData();
    }, { onConflict: loadData });
  } catch (err: any) {
    alert(err?.message || "提交质检失败");
  } finally {
    submitting.value = false;
  }
}

function openDecideModal(row: PurchaseQualityInspection) {
  selectedInspect.value = row;
  decideForm.dispositionQty = row.qualifiedQty;
  decideForm.dispositionType = "Release";
  isDecideOpen.value = true;
}

async function submitDecide() {
  if (!selectedInspect.value) return;
  const inspection = selectedInspect.value;
  submitting.value = true;
  try {
    await execute((key) => decideQualityDisposition(inspection.purchaseReceiptId, {
      inspectionId: String(inspection.id),
      dispositionType: decideForm.dispositionType,
      dispositionQty: decideForm.dispositionQty,
      reason: decideForm.reason,
    }, key), { onConflict: loadData });
    isDecideOpen.value = false;
    await loadData();
  } catch (err: any) {
    alert(err?.message || "下达处置失败");
  } finally {
    submitting.value = false;
  }
}

function openExecuteConfirm(row: PurchaseQualityDisposition) {
  selectedDisp.value = row;
  receivingStagingLocationId.value = "";
  putawayTargetLocationId.value = "";
  isExecuteOpen.value = true;
}

async function executeDisposition() {
  if (!selectedDisp.value) return;
  const disposition = selectedDisp.value;
  if (disposition.dispositionType === "Release") {
    if (!inspectionWarehouseId.value || filteredReceivingStagingLocations.value.length === 0) {
      alert("未取得处置收货的目标仓库或同仓 ReceivingStaging 库位，已阻止执行。");
      return;
    }
    if (!receivingStagingLocationId.value || !filteredReceivingStagingLocations.value.some(
      (location) => String(location.id) === String(receivingStagingLocationId.value),
    )) {
      alert("请选择当前收货目标仓库下真实、启用的 ReceivingStaging 库位。");
      return;
    }
    if (putawayTargetLocationId.value && !filteredStorageLocations.value.some(
      (location) => String(location.id) === String(putawayTargetLocationId.value),
    )) {
      alert("上架目标必须是当前收货目标仓库下真实、启用的 Storage 库位。");
      return;
    }
  }
  submitting.value = true;
  try {
    await execute((key) => confirmQualityDisposition(String(disposition.id), {
      dispositionId: String(disposition.id),
      toLocationId: disposition.dispositionType === "Release" ? receivingStagingLocationId.value : undefined,
      putawayTargetLocationId: putawayTargetLocationId.value || undefined,
    }, key), { onConflict: loadData });
    const isRelease = disposition.dispositionType === "Release";
    isExecuteOpen.value = false;
    await loadData();
    if (isRelease) {
      if (confirm("合格品已成功转移至收货暂存位，并已生成上架任务。是否立即前往【上架任务】页面执行入库？")) {
        router.push("/purchasing/putaway");
      }
    }
  } catch (err: any) {
    alert(err?.message || "执行失败");
  } finally {
    submitting.value = false;
  }
}

onMounted(async () => {
  // 修改用途：先读取收货成功后携带的真实仓库上下文，再加载同仓处置库位。
  inspectionWarehouseId.value = String(route.query.warehouseId || "");
  const qReceiptId = String(route.query.receiptId || "");
  const qReceiptLineId = String(route.query.receiptLineId || "");
  const qProductId = String(route.query.productId || "");
  loadData();
  await loadReferenceOptions().catch((error) => console.error("[QualityDispositionView] 加载质检选项失败", error));
  // 处理从采购收货一键跳转过来的 query 参数 (修复 F04)
  const qOrderId = route.query.orderId as string;
  const qPoNo = route.query.poNo as string;
  if (qReceiptId) {
    inspectForm.purchaseReceiptId = qReceiptId;
    inspectForm.purchaseReceiptLineId = qReceiptLineId;
    inspectForm.productId = qProductId;
    if (qOrderId) {
      inspectForm.purchaseOrderId = qOrderId;
      queryPoNo.value = qPoNo || qOrderId;
      await onOrderChange(qOrderId);
      // onOrderChange 只加载订单行，不覆盖真实 receipt line ID。
      inspectForm.purchaseReceiptId = qReceiptId;
      inspectForm.purchaseReceiptLineId = qReceiptLineId;
      inspectForm.productId = qProductId;
    }
    isInspectOpen.value = true;
  }
});
</script>

<style scoped>
.quality-disposition-view {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.contract-alert {
  padding: 12px;
  border: 1px solid rgba(248, 113, 113, 0.45);
  border-radius: 6px;
  background: rgba(127, 29, 29, 0.2);
  color: #fecaca;
  font-size: 12px;
  line-height: 1.5;
}

.tab-nav {
  display: flex;
  gap: 10px;
  border-bottom: 1px solid rgba(255, 255, 255, 0.08);
  padding-bottom: 10px;
}

.tab-item {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 8px 16px;
  background: rgba(30, 41, 59, 0.4);
  border: 1px solid rgba(255, 255, 255, 0.08);
  border-radius: 6px;
  color: #94a3b8;
  font-size: 13px;
  cursor: pointer;
  transition: all 0.2s;
}

.tab-item.is-active {
  background: rgba(56, 189, 248, 0.12);
  border-color: rgba(56, 189, 248, 0.3);
  color: #38bdf8;
}

.count-badge {
  font-size: 11px;
  font-family: var(--font-mono, monospace);
  background: rgba(0, 0, 0, 0.3);
  padding: 1px 6px;
  border-radius: 10px;
}

.mono-code {
  font-family: var(--font-mono, monospace);
  color: #38bdf8;
  font-size: 12px;
}

.mono-text {
  font-family: var(--font-mono, monospace);
  color: #cbd5e1;
  font-size: 12px;
}

.text-success {
  color: #34d399;
}

.text-danger {
  color: #f87171;
}

.text-muted {
  color: #64748b;
}

.btn-link {
  background: transparent;
  border: none;
  color: #38bdf8;
  font-size: 12px;
  cursor: pointer;
  padding: 2px 6px;
}

.confirm-act {
  color: #34d399;
  font-weight: 600;
}

.btn-primary {
  padding: 8px 16px;
  background: #0284c7;
  color: #ffffff;
  border: 1px solid #0369a1;
  border-radius: 6px;
  font-size: 13px;
  font-weight: 500;
  cursor: pointer;
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

/* 模态框 */
.modal-mask {
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

.modal-panel {
  background: #0f172a;
  border: 1px solid rgba(255, 255, 255, 0.15);
  border-radius: 10px;
  width: 100%;
  max-width: 520px;
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

.btn-close {
  background: transparent;
  border: none;
  color: #94a3b8;
  font-size: 16px;
  cursor: pointer;
}

.modal-body {
  padding: 20px;
  display: flex;
  flex-direction: column;
  gap: 14px;
}

.form-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
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
.form-select,
.form-textarea {
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
</style>
