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
        <el-button
          v-if="hasPermission('pur:quality:inspect')"
          type="primary"
          :icon="Plus"
          @click="openInspectModal"
        >
          录入到货质检结果
        </el-button>
      </template>
    </PageHeader>

    <!-- 顶部分类切换 -->
    <div class="tab-nav" style="margin-bottom: 16px">
      <el-radio-group
        v-model="activeTab"
        @change="(val) => switchQualityTab(val as 'inspections' | 'dispositions')"
      >
        <el-radio-button value="inspections">
          质检检验事实记录 ({{ inspections.length }})
        </el-radio-button>
        <el-radio-button value="dispositions">
          质量处置决定与执行 ({{ dispositions.length }})
        </el-radio-button>
      </el-radio-group>
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
          <el-button
            v-if="hasAnyPermission('pur:quality:release', 'pur:quality:return', 'pur:quality:scrap')"
            type="primary"
            link
            size="small"
            @click="openDecideModal(row)"
          >
            做出处置决定
          </el-button>
          <span v-else class="text-muted">无处置权限</span>
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
          <el-button
            v-if="row.status === 'PendingExecution' && hasPermission('pur:disposition:confirm')"
            type="success"
            link
            size="small"
            @click="openExecuteConfirm(row)"
          >
            确认执行实物处置
          </el-button>
          <span v-else-if="row.status === 'PendingExecution'" class="text-muted">无实物执行权限</span>
          <span v-else class="text-muted">已归档</span>
        </template>
      </DataTable>
    </div>

    <!-- 录入质检结果弹窗 -->
    <el-dialog
      v-model="isInspectOpen"
      title="录入采购到货质检结果"
      width="640px"
      destroy-on-close
    >
      <el-form label-width="130px" @submit.prevent="submitInspect">
        <el-alert
          v-if="!queryPoNo && receiptCandidates.length === 0"
          type="warning"
          show-icon
          style="margin-bottom: 14px"
          title="当前没有可质检的收货明细：请先由仓库人员完成“确认接收进质量隔离位”，再刷新本页面。"
        />
        <el-alert
          v-if="queryPoNo"
          type="info"
          show-icon
          style="margin-bottom: 14px"
          :title="`当前针对订单 ${queryPoNo} 录入质检（收货凭证: ${inspectForm.purchaseReceiptId}；收货行: ${inspectForm.purchaseReceiptLineId || '未返回'}）`"
        />
        <template v-else>
          <el-form-item label="关联采购收货单" required>
            <el-select v-model="selectedReceiptId" placeholder="请选择已确认收货单" style="width: 100%" @change="onReceiptChange(selectedReceiptId)">
              <el-option
                v-for="receipt in receiptOptions"
                :key="receipt.receiptId"
                :label="`${receipt.receiptNo} - 采购单 ${receipt.purchaseOrderNo}`"
                :value="receipt.receiptId"
              />
            </el-select>
          </el-form-item>

          <el-form-item v-if="selectedReceiptId" label="检验物料行" required>
            <el-select v-model="selectedReceiptLineId" placeholder="请选择检验物料行" style="width: 100%" @change="onReceiptLineChange(selectedReceiptLineId)">
              <el-option
                v-for="line in selectedReceiptLines"
                :key="line.receiptLineId"
                :label="`第 ${line.lineNo} 行 - ${productLabel(line)} - 实收待检: ${line.remainingQty} ${line.uom}`"
                :value="line.receiptLineId"
              />
            </el-select>
          </el-form-item>
        </template>

        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item label="检验总数量" required>
              <el-input v-model="inspectForm.inspectedQty" placeholder="如: 100" @input="calcUnqualified" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="质检合格数量" required>
              <el-input v-model="inspectForm.qualifiedQty" placeholder="如: 98" @input="calcUnqualified" />
            </el-form-item>
          </el-col>
        </el-row>

        <el-form-item label="不合格数量">
          <el-input :model-value="inspectForm.unqualifiedQty" disabled />
        </el-form-item>

        <el-form-item v-if="parseFloat(inspectForm.unqualifiedQty || '0') > 0" label="不合格原因" required>
          <el-input v-model="inspectForm.unqualifiedReason" placeholder="如: 轴向尺寸公差超差 0.15mm" />
        </el-form-item>

        <el-form-item label="检验说明与备注">
          <el-input v-model="inspectForm.inspectionRemark" type="textarea" :rows="2" placeholder="抽样标准与检验过程说明..." />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="isInspectOpen = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="submitInspect">
          确认提交检验事实
        </el-button>
      </template>
    </el-dialog>

    <!-- 做出处置决定弹窗 -->
    <el-dialog
      v-model="isDecideOpen"
      title="做出质量处置决定"
      width="560px"
      destroy-on-close
    >
      <el-form label-width="120px" @submit.prevent="submitDecide">
        <el-form-item label="处置动作类型" required>
          <el-select v-model="decideForm.dispositionType" placeholder="请选择处置动作类型" style="width: 100%">
            <el-option label="合格放行上架 (Release ➔ 移至收货暂存位)" value="Release" />
            <el-option label="不合格报废处理 (Scrap ➔ 扣减实物库存)" value="Scrap" />
            <el-option label="退回供应方 (Return ➔ 扣减实物库存)" value="Return" />
          </el-select>
        </el-form-item>
        <el-form-item label="处置数量" required>
          <el-input v-model="decideForm.dispositionQty" placeholder="请输入处置数量" />
        </el-form-item>
        <el-form-item label="处置依据与原因">
          <el-input v-model="decideForm.reason" type="textarea" :rows="2" placeholder="填写放行依据或报废/退货原因..." />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="isDecideOpen = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="submitDecide">
          确认下达处置决定
        </el-button>
      </template>
    </el-dialog>

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
import { Plus } from "@element-plus/icons-vue";
import { ElMessage, ElMessageBox } from "element-plus";
import { useCommand } from "@/composables/useCommand";
import CommandFeedback from "@/components/common/CommandFeedback.vue";
import { useRoute, useRouter } from "vue-router";
import PageHeader from "@/components/common/PageHeader.vue";
import DataTable, { type TableColumn } from "@/components/common/DataTable.vue";
import StatusBadge from "@/components/common/StatusBadge.vue";
import QuantityText from "@/components/common/QuantityText.vue";
import EmptyState from "@/components/common/EmptyState.vue";
import ConfirmDialog from "@/components/common/ConfirmDialog.vue";
import { usePermission } from "@/composables/usePermission";
import type {
  PurchaseQualityInspection,
  PurchaseQualityDisposition,
  PurchaseQualityReceiptCandidate,
} from "@/types/purchasing";
import { stringSub, type Location, type Product } from "@/types/inventory";
import { getLocations, getProducts } from "@/api/masterData";
import { getOperatorDirectory } from "@/api/auth";
import {
  getQualityInspections,
  inspectQuality,
  getQualityDispositions,
  decideQualityDisposition,
  confirmQualityDisposition,
  getQualityReceiptCandidates,
  getPurchaseOrderById,
} from "@/api/purchasing";

const route = useRoute();
const { execute, retry, isExecuting, canRetry, lastError } = useCommand();
const router = useRouter();
const { hasPermission, hasAnyPermission } = usePermission();

const activeTab = ref<"inspections" | "dispositions">("inspections");
const loading = ref(false);
const submitting = ref(false);
const inspectionWarehouseId = ref("");

const inspections = ref<PurchaseQualityInspection[]>([]);
const dispositions = ref<PurchaseQualityDisposition[]>([]);

/**
 * 切换质检事实与处置执行页签。
 * 入参：目标页签；出参：无；流程：统一写入响应式状态，保证无障碍点击与鼠标点击走同一事件处理。
 */
function switchQualityTab(tab: "inspections" | "dispositions") {
  // 修改用途：将模板内直接赋值改为显式方法，修复仓库角色点击处置页签不切换的问题。
  activeTab.value = tab;
}

const inspectionColumns: TableColumn[] = [
  { key: "inspectionNo", label: "检验编号", width: "150px" },
  { key: "purchaseOrderNo", label: "采购单号", width: "140px" },
  { key: "productName", label: "物料", minWidth: "180px" },
  { key: "inspectedQty", label: "检验总量", width: "100px", align: "right" },
  { key: "qualifiedQty", label: "合格数量", width: "100px", align: "right" },
  { key: "unqualifiedQty", label: "不合格量", width: "100px", align: "right" },
  { key: "unqualifiedReason", label: "不合格原因", minWidth: "150px" },
  { key: "inspectedByName", label: "检验质检员", width: "130px" },
  { key: "actions", label: "操作", width: "120px", align: "center" },
];

const dispositionColumns: TableColumn[] = [
  { key: "dispositionNo", label: "处置编号", width: "150px" },
  { key: "purchaseOrderNo", label: "采购单号", width: "140px" },
  { key: "dispositionType", label: "处置决定", width: "130px", align: "center" },
  { key: "dispositionQty", label: "处置数量", width: "100px", align: "right" },
  { key: "status", label: "执行状态", width: "140px", align: "center" },
  { key: "decidedByName", label: "决定人", width: "120px" },
  { key: "executedByName", label: "仓库执行人", width: "120px" },
  { key: "actions", label: "操作", width: "130px", align: "center" },
];

// 质检弹窗与选择联动
const isInspectOpen = ref(false);
const queryPoNo = ref("");
const receiptCandidates = ref<PurchaseQualityReceiptCandidate[]>([]);
const selectedReceiptId = ref("");
const selectedReceiptLineId = ref("");

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

const receiptOptions = computed(() => {
  const seen = new Set<string>();
  return receiptCandidates.value.filter((candidate) => {
    if (seen.has(candidate.receiptId)) return false;
    seen.add(candidate.receiptId);
    return true;
  });
});

const selectedReceiptLines = computed(() => receiptCandidates.value.filter(
  (candidate) => candidate.receiptId === selectedReceiptId.value,
));

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

/**
 * 用途：根据质检人员选择的真实收货单切换可检验明细。
 * 入参：后端返回的收货事实 ID；出参：无；流程：从候选项回填采购单和收货单标识，清空旧明细。
 */
function onReceiptChange(receiptId: string) {
  const receipt = receiptCandidates.value.find((candidate) => candidate.receiptId === receiptId);
  selectedReceiptId.value = receiptId;
  selectedReceiptLineId.value = "";
  inspectForm.purchaseOrderId = receipt?.purchaseOrderId || "";
  inspectForm.purchaseReceiptId = receiptId;
  inspectForm.purchaseReceiptLineId = "";
  inspectForm.productId = "";
  inspectForm.inspectedQty = "";
  inspectForm.qualifiedQty = "";
  inspectForm.unqualifiedQty = "";
}

/**
 * 用途：根据质检人员选择的真实收货行回填物料和剩余待检数量。
 * 入参：后端返回的收货行 ID；出参：无；流程：只使用候选项中的真实标识和剩余数量。
 */
function onReceiptLineChange(receiptLineId: string) {
  const line = selectedReceiptLines.value.find((candidate) => candidate.receiptLineId === receiptLineId);
  if (!line) return;
  selectedReceiptLineId.value = receiptLineId;
  inspectForm.purchaseReceiptId = line.receiptId;
  inspectForm.purchaseOrderId = line.purchaseOrderId;
  inspectForm.purchaseReceiptLineId = line.receiptLineId;
  inspectForm.productId = line.productId;
  inspectForm.inspectedQty = line.remainingQty;
  inspectForm.qualifiedQty = line.remainingQty;
  inspectForm.unqualifiedQty = "0";
}

function productLabel(candidate: PurchaseQualityReceiptCandidate) {
  const product = products.value.find((item) => String(item.id) === String(candidate.productId));
  return product ? `${product.sku} (${product.name})` : String(candidate.productId);
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
    const [resI, resD, operatorResponse] = await Promise.all([
      getQualityInspections(),
      getQualityDispositions(),
      getOperatorDirectory({ page: 1, size: 1000 }).catch(() => null),
    ]);
    const productMap = new Map(products.value.map((item) => [String(item.id), item]));
    const operators = operatorResponse?.data?.records || [];
    const operatorName = (id?: string) => {
      const operator = operators.find((item) => String(item.id) === String(id));
      return operator?.realName || operator?.username || id || "-";
    };
    inspections.value = (resI.data || []).map((inspection) => {
      const product = productMap.get(String(inspection.productId));
      return {
        ...inspection,
        sku: inspection.sku || product?.sku || String(inspection.productId),
        productName: inspection.productName || product?.name || String(inspection.productId),
        inspectedByName: operatorName(inspection.inspectedBy),
      };
    });
    dispositions.value = (resD.data || []).map((disposition) => ({
      ...disposition,
      decidedByName: operatorName(disposition.decidedBy),
      executedByName: operatorName(disposition.executedBy),
    }));
  } catch (err: any) {
    console.error("[QualityDispositionView] 加载失败:", err);
    ElMessage.error(err?.message || "加载质检与处置记录失败");
  } finally {
    loading.value = false;
  }
}

/**
 * 加载质检与处置所需主数据，所有选择值使用后端返回的真实 UUID。
 */
async function loadReferenceOptions(loadReceiptCandidates = false) {
  const [productResponse, receivingResponse, storageResponse] = await Promise.all([
    getProducts({ page: 1, size: 1000, status: "ACTIVE" }),
    getLocations({ page: 1, size: 1000, warehouseId: inspectionWarehouseId.value || undefined, type: "ReceivingStaging", status: "ACTIVE" }),
    getLocations({ page: 1, size: 1000, warehouseId: inspectionWarehouseId.value || undefined, type: "Storage", status: "ACTIVE" }),
  ]);
  products.value = productResponse.data.records;
  receivingStagingLocations.value = receivingResponse.data.records;
  storageLocations.value = storageResponse.data.records;
  if (loadReceiptCandidates) {
    const candidateResponse = await getQualityReceiptCandidates();
    receiptCandidates.value = candidateResponse.data || [];
  }
}

/**
 * 打开质检录入前刷新物料与合法处置库位。
 */
async function openInspectModal() {
  try {
    await loadReferenceOptions(true);
    if (!queryPoNo.value) {
      selectedReceiptId.value = "";
      selectedReceiptLineId.value = "";
      inspectForm.purchaseOrderId = "";
      inspectForm.purchaseReceiptId = "";
      inspectForm.purchaseReceiptLineId = "";
      inspectForm.productId = "";
      inspectForm.inspectedQty = "";
      inspectForm.qualifiedQty = "";
      inspectForm.unqualifiedQty = "";
      inspectForm.unqualifiedReason = "";
      inspectForm.inspectionRemark = "";
    }
    // 修改用途：收货跳转可被仓库角色访问，但质检录入只能由具备检验权限的角色打开。
    if (hasPermission('pur:quality:inspect')) {
      isInspectOpen.value = true;
    }
  } catch (error: any) {
    ElMessage.error(error?.message || "加载质检选项失败");
  }
}

async function submitInspect() {
  if (!inspectForm.purchaseReceiptId || !inspectForm.purchaseReceiptLineId || !inspectForm.productId) {
    ElMessage.warning("请选择真实的“关联采购收货单”和“检验物料行”，不能使用采购订单行 ID 或随机 UUID。");
    return;
  }
  if (!inspectForm.inspectedQty || parseFloat(inspectForm.inspectedQty) <= 0) {
    ElMessage.warning("检验数量必须来自真实收货明细且大于 0。");
    return;
  }
  submitting.value = true;
  try {
    await execute(async (key) => {
      await inspectQuality(inspectForm.purchaseReceiptId, { ...inspectForm }, key);
      isInspectOpen.value = false;
      ElMessage.success("质检事实录入成功！");
      await loadData();
    }, { onConflict: loadData });
  } catch (err: any) {
    ElMessage.error(err?.message || "提交质检失败");
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
    ElMessage.success("处置决策下达成功！");
    await loadData();
  } catch (err: any) {
    ElMessage.error(err?.message || "下达处置失败");
  } finally {
    submitting.value = false;
  }
}

async function openExecuteConfirm(row: PurchaseQualityDisposition) {
  selectedDisp.value = row;
  receivingStagingLocationId.value = "";
  putawayTargetLocationId.value = "";

  try {
    // 流程：按处置记录关联的真实采购单读取目标仓库，再刷新同仓 ReceivingStaging/Storage 库位。
    const orderResponse = await getPurchaseOrderById(String(row.purchaseOrderId));
    const order = orderResponse.data;
    const targetWarehouseId = order.targetWarehouseId
      || order.lines?.find((line) => String(line.id) === String(row.purchaseReceiptLineId))?.targetWarehouseId
      || order.lines?.find((line) => String(line.productId) === String(row.productId))?.targetWarehouseId;
    inspectionWarehouseId.value = String(targetWarehouseId || "");
    if (!inspectionWarehouseId.value) {
      ElMessage.warning("未能从真实采购单取得处置目标仓库，已阻止执行。");
      selectedDisp.value = null;
      return;
    }
    await loadReferenceOptions();
  } catch (error: any) {
    console.error("[QualityDispositionView] 加载处置目标仓库与库位失败", error);
    ElMessage.error(error?.message || "加载处置目标仓库与库位失败");
    selectedDisp.value = null;
    return;
  }
  isExecuteOpen.value = true;
}

async function executeDisposition() {
  if (!selectedDisp.value) return;
  const disposition = selectedDisp.value;
  if (disposition.dispositionType === "Release") {
    if (!inspectionWarehouseId.value || filteredReceivingStagingLocations.value.length === 0) {
      ElMessage.warning("未取得处置收货的目标仓库或同仓 ReceivingStaging 库位，已阻止执行。");
      return;
    }
    if (!receivingStagingLocationId.value || !filteredReceivingStagingLocations.value.some(
      (location) => String(location.id) === String(receivingStagingLocationId.value),
    )) {
      ElMessage.warning("请选择当前收货目标仓库下真实、启用的 ReceivingStaging 库位。");
      return;
    }
    if (putawayTargetLocationId.value && !filteredStorageLocations.value.some(
      (location) => String(location.id) === String(putawayTargetLocationId.value),
    )) {
      ElMessage.warning("上架目标必须是当前收货目标仓库下真实、启用的 Storage 库位。");
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
      try {
        await ElMessageBox.confirm(
          "合格品已成功转移至收货暂存位，并已生成上架任务。是否立即前往【上架任务】页面执行入库？",
          "处置执行成功",
          {
            confirmButtonText: "前往上架任务",
            cancelButtonText: "留在此页",
            type: "success",
          }
        );
        await router.push({ name: "PurchasePutawayTask" });
      } catch {
        // 用户留在此页
      }
    } else {
      ElMessage.success("处置执行成功！");
    }
  } catch (err: any) {
    ElMessage.error(err?.message || "执行失败");
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
  await loadReferenceOptions().catch((error) => console.error("[QualityDispositionView] 加载质检选项失败", error));
  await loadData();
  // 处理从采购收货一键跳转过来的 query 参数 (修复 F04)
  const qOrderId = route.query.orderId as string;
  const qPoNo = route.query.poNo as string;
  if (qReceiptId && qReceiptLineId && qProductId) {
    inspectForm.purchaseReceiptId = qReceiptId;
    inspectForm.purchaseReceiptLineId = qReceiptLineId;
    inspectForm.productId = qProductId;
    if (qOrderId) {
      inspectForm.purchaseOrderId = qOrderId;
      queryPoNo.value = qPoNo || qOrderId;
      inspectForm.purchaseReceiptId = qReceiptId;
      inspectForm.purchaseReceiptLineId = qReceiptLineId;
      inspectForm.productId = qProductId;
    }
    // 修改用途：收货跳转可被仓库角色访问，但质检录入只能由具备检验权限的角色打开。
    if (hasPermission('pur:quality:inspect')) {
      isInspectOpen.value = true;
    }
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
