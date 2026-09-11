<template>
  <div class="manufacturing-view-container">
    <CommandFeedback :error="lastError" :can-retry="canRetry" :executing="isExecuting" @retry="retry" />
    <!-- 统一页面头部 -->
    <PageHeader
      title="生产成品入库 (Finished Goods Receipt)"
      tag="MES / RECEIPT"
      description="将车间已检验合格且尚未入库的产成品办理成品入库。入库确认后通过库存应用服务真实增加成品库位实物库存。"
    >
      <template #actions>
        <el-button
          v-if="hasPermission('mes:finished:receipt')"
          type="primary"
          :icon="Plus"
          @click="openCreateModal"
        >
          新建成品入库单
        </el-button>
      </template>
    </PageHeader>

    <!-- 统一筛选栏 -->
    <FilterBar
      v-model="queryParams.keyword"
      placeholder="搜索入库单号或工单编号..."
      @search="handleSearch"
      @reset="handleReset"
    >
      <div class="filter-select-group">
        <label class="filter-label">所属工单：</label>
        <el-select
          v-model="selectedWorkOrderId"
          placeholder="请选择生产工单"
          filterable
          clearable
          style="width: 260px"
          @change="handleWorkOrderFilterChange"
        >
          <el-option label="全部工单" value="" />
          <el-option
            v-for="wo in workOrders"
            :key="wo.id"
            :label="`${wo.workOrderNo} - ${wo.productName || '工单'} (${wo.status})`"
            :value="String(wo.id)"
          />
        </el-select>
      </div>
      <div class="filter-select-group">
        <label class="filter-label">状态：</label>
        <el-select
          v-model="queryParams.status"
          placeholder="全部状态"
          clearable
          style="width: 170px"
          @change="handleSearch"
        >
          <el-option label="全部状态" value="" />
          <el-option label="草稿待入库 (Draft)" value="Draft" />
          <el-option label="已确认入库 (Confirmed)" value="Confirmed" />
        </el-select>
      </div>
    </FilterBar>

    <!-- 错误异常提示 -->
    <ErrorState
      v-if="viewState === 'error'"
      title="成品入库单加载失败"
      :message="errorMessage"
      @retry="fetchReceiptList"
    />

    <!-- 数据表格 -->
    <DataTable
      v-else
      :columns="columns"
      :data="receiptList"
      :loading="viewState === 'loading'"
      :page="queryParams.page"
      :size="queryParams.size"
      :total="total"
      empty-text="暂无匹配的成品入库记录（请确认已选择有效工单）"
      @page-change="handlePageChange"
    >
      <!-- 入库单号 -->
      <template #receiptNo="{ row }">
        <span class="font-mono highlight-code">{{ row.receiptNo }}</span>
      </template>

      <!-- 关联工单与产出物 -->
      <template #workOrderId="{ row }">
        <div class="wo-cell">
          <span class="font-mono text-primary">{{ row.workOrderNo || row.workOrderId }}</span>
          <span class="product-name text-muted">{{ row.productName || "产成品" }}</span>
        </div>
      </template>

      <!-- 入库数量 (全量 QuantityText) -->
      <template #receiptQty="{ row }">
        <QuantityText :value="row.receiptQty" unit="件" />
      </template>

      <!-- 目标仓库 / 库位 -->
      <template #warehouseId="{ row }">
        <div class="location-cell">
          <span class="wh-name">{{ row.warehouseName || row.warehouseId }}</span>
          <span class="loc-code font-mono text-muted">库位: {{ row.locationCode || row.locationId }}</span>
        </div>
      </template>

      <!-- 状态徽标 -->
      <template #status="{ row }">
        <StatusBadge
          :type="row.status === 'Confirmed' ? 'success' : 'warning'"
          :text="row.status === 'Confirmed' ? '已确认入库' : '待入库确认'"
        />
      </template>

      <!-- 库存流水软引用 -->
      <template #inventoryTransactionId="{ row }">
        <span v-if="row.inventoryTransactionId" class="font-mono text-muted">
          {{ row.inventoryTransactionId }}
        </span>
        <span v-else class="text-muted font-xs">待入库确认后生成</span>
      </template>

      <!-- 操作 (受 allowedActions 约束) -->
      <template #actions="{ row }">
        <div class="action-btn-group">
          <!-- 确认入库 (Draft -> Confirmed) -->
          <el-button
            v-if="row.status === 'Draft'"
            link
            type="primary"
            :disabled="!isActionAllowed(row, 'confirm')"
            :title="getActionDisabledReason(row, 'confirm') || '确认成品入库'"
            @click="promptConfirm(row)"
          >
            确认入库
          </el-button>
          <span v-else class="text-muted font-xs">已完成增加</span>
        </div>
      </template>
    </DataTable>

    <!-- 新建成品入库单对话框 -->
    <el-dialog
      v-model="createModalVisible"
      title="新建成品入库申请单"
      width="640px"
      append-to-body
      destroy-on-close
    >
      <div class="dialog-content-wrapper">
        <div class="options-search-row">
          <label for="receipt-option-keyword">目录搜索</label>
          <el-input
            id="receipt-option-keyword"
            v-model="masterDataKeyword"
            clearable
            placeholder="输入工单、仓库或库位编码/名称后回车搜索"
            @keyup.enter="loadMasterData"
          >
            <template #append>
              <el-button :icon="Search" @click="loadMasterData">搜索</el-button>
            </template>
          </el-input>
        </div>
        <div v-if="masterDataLoading" class="options-hint text-muted">⏳ 正在加载真实主数据目录...</div>
        <div v-else-if="masterDataError" class="options-hint text-warning">⚠️ {{ masterDataError }}</div>

        <el-form label-position="top" class="custom-el-form">
          <el-row :gutter="16">
            <el-col :span="12">
              <el-form-item label="生产工单" required>
                <el-select
                  v-model="createForm.workOrderId"
                  placeholder="请选择真实工单"
                  filterable
                  style="width: 100%"
                  @change="onModalWorkOrderChange"
                >
                  <el-option
                    v-for="workOrder in workOrders"
                    :key="workOrder.id"
                    :label="`${workOrder.workOrderNo} - ${workOrder.productName || '工单'}`"
                    :value="String(workOrder.id)"
                  />
                </el-select>
              </el-form-item>
            </el-col>
            <el-col :span="12">
              <el-form-item label="成品入库数量" required>
                <el-input
                  v-model="createForm.receiptQty"
                  type="number"
                  min="0.01"
                  step="0.01"
                  :max="maxEligibleQuantity > 0 ? maxEligibleQuantity : undefined"
                  placeholder="例如 100.00"
                />
              </el-form-item>
            </el-col>
          </el-row>

          <el-row :gutter="16">
            <el-col :span="12">
              <el-form-item label="目标入库仓库" required>
                <el-select
                  v-model="createForm.warehouseId"
                  placeholder="请选择真实仓库"
                  filterable
                  style="width: 100%"
                  @change="loadLocations"
                >
                  <el-option
                    v-for="warehouse in warehouses"
                    :key="warehouse.id"
                    :label="`${warehouse.name} (${warehouse.code})`"
                    :value="String(warehouse.id)"
                  />
                </el-select>
              </el-form-item>
            </el-col>
            <el-col :span="12">
              <el-form-item label="目标货架库位" required>
                <el-select
                  v-model="createForm.locationId"
                  :placeholder="!createForm.warehouseId ? '请先选择仓库' : '请选择库位'"
                  :disabled="!createForm.warehouseId"
                  filterable
                  style="width: 100%"
                >
                  <el-option
                    v-for="location in locations"
                    :key="location.id"
                    :label="`${location.code} (${location.name}) - ${location.type}`"
                    :value="String(location.id)"
                  />
                </el-select>
              </el-form-item>
            </el-col>
          </el-row>

          <div v-if="createForm.warehouseId && !masterDataLoading && !masterDataError && locations.length === 0" class="options-hint text-warning">
            当前仓库没有启用的 Storage 类型库位，请先在库位管理中创建或启用 Storage 库位。
          </div>

          <div class="modal-hint">
            {{ maxEligibleHint }}
          </div>
        </el-form>
      </div>

      <template #footer>
        <span class="dialog-footer">
          <el-button @click="createModalVisible = false">取消</el-button>
          <el-button
            type="primary"
            :loading="isSubmitting"
            :disabled="!receiptQuantityValid || !createForm.locationId || locations.length === 0"
            :title="receiptQuantityValid ? '保存入库单 (草稿)' : '入库数量必须在已检验合格且未入库余额内'"
            @click="submitCreateReceipt"
          >
            保存入库单 (草稿)
          </el-button>
        </span>
      </template>
    </el-dialog>

    <!-- 确认入库二次确认 -->
    <ConfirmDialog
      v-model:visible="confirmDialog.visible"
      title="确认成品入库"
      :message="`确定确认入库单【${confirmDialog.item?.receiptNo}】？系统将向库存应用服务发出指令，增加成品实物库存并记录流水软引用。`"
      :loading="confirmDialog.loading"
      @confirm="handleExecuteConfirm"
    />
  </div>
</template>

<script setup lang="ts">
import { isActionAllowed as checkAction, getActionDisabledReason as getDisabledReason } from "../../utils/actionGuard";
import type { AllowedAction } from "../../types/common";
import { ref, reactive, computed, onMounted } from "vue";
import { Plus, Search } from "@element-plus/icons-vue";
import { ElMessage } from "element-plus";
import { useCommand } from "@/composables/useCommand";
import CommandFeedback from "@/components/common/CommandFeedback.vue";
import { useRoute, useRouter } from "vue-router";
import { usePermission } from "../../composables/usePermission";
import {
  PageHeader,
  FilterBar,
  DataTable,
  StatusBadge,
  QuantityText,
  ConfirmDialog,
  ErrorState,
} from "../../components/common";
import type { TableColumn } from "../../components/common/DataTable.vue";
import type { ViewState } from "../../types/common";
import type {
  FinishedGoodsReceiptItem,
  FinishedGoodsReceiptCreateRequest,
} from "../../types/manufacturing";
import {
  getFinishedGoodsReceipts,
  createFinishedGoodsReceipt,
  confirmFinishedGoodsReceipt,
  getWorkOrders,
} from "../../api/manufacturing";
import { getLocations, getWarehouses } from "../../api/masterData";
import type { Location, Warehouse } from "../../types/inventory";
import type { WorkOrderItem } from "../../types/manufacturing";

const route = useRoute();
const router = useRouter();
const { hasPermission } = usePermission();

const viewState = ref<ViewState>("loading");
const errorMessage = ref("");

const receiptList = ref<FinishedGoodsReceiptItem[]>([]);
const total = ref(0);
const workOrders = ref<WorkOrderItem[]>([]);
const warehouses = ref<Warehouse[]>([]);
const locations = ref<Location[]>([]);
const selectedWorkOrderId = ref("");

const queryParams = reactive({
  page: 1,
  size: 10,
  keyword: "",
  status: "",
});

const columns: TableColumn[] = [
  { key: "receiptNo", label: "入库单号", width: "180px" },
  { key: "workOrderId", label: "来源工单 / 产品", minWidth: "200px" },
  { key: "receiptQty", label: "入库数量", width: "120px", align: "right" },
  { key: "warehouseId", label: "目标成品库位", minWidth: "180px" },
  { key: "status", label: "入库状态", width: "120px", align: "center" },
  { key: "inventoryTransactionId", label: "库存流水引用", width: "160px" },
  { key: "actions", label: "操作", width: "110px", align: "center" },
];

const createModalVisible = ref(false);
const { execute, retry, isExecuting, canRetry, lastError } = useCommand();
const isSubmitting = isExecuting;
const createForm = reactive<FinishedGoodsReceiptCreateRequest>({
  workOrderId: "",
  receiptQty: "",
  warehouseId: "",
  locationId: "",
});
const masterDataKeyword = ref("");
const masterDataLoading = ref(false);
const masterDataError = ref("");

const confirmDialog = reactive({
  visible: false,
  loading: false,
  item: null as FinishedGoodsReceiptItem | null,
});

const currentModalWorkOrder = computed(() =>
  workOrders.value.find((w) => String(w.id) === String(createForm.workOrderId))
);

/** 由服务端工单汇总事实计算当前可申报的合格未入库余额。 */
const maxEligibleQuantity = computed(() => {
  if (!currentModalWorkOrder.value) return 0;
  const qual = parseFloat(currentModalWorkOrder.value.qualifiedQty || "0");
  const recv = parseFloat(currentModalWorkOrder.value.receivedQty || "0");
  return Math.max(0, qual - recv);
});

/** 前端只做显式数量闸门，最终仍由服务端按质检事实再次校验。 */
const receiptQuantityValid = computed(() => {
  const requested = parseFloat(createForm.receiptQty || "0");
  return Number.isFinite(requested) && requested > 0 && requested <= maxEligibleQuantity.value;
});

const maxEligibleHint = computed(() => {
  if (!currentModalWorkOrder.value) {
    return "提示：请选择工单，系统将自动核算最大可入库合格量。";
  }
  const wo = currentModalWorkOrder.value;
  return `工单 ${wo.workOrderNo}：累计质检合格 ${wo.qualifiedQty || "0"} 件，已完工入库 ${wo.receivedQty || "0"} 件；当前最大可申报入库量为 ${maxEligibleQuantity.value} 件。`;
});

/** 检查指定操作是否被后端或状态机允许 */
function isActionAllowed(item: { allowedActions?: AllowedAction[] | null }, action: string): boolean {
  return checkAction(item.allowedActions, action);
}

/** 获取指定操作被禁用的原因提示 */
function getActionDisabledReason(item: { allowedActions?: AllowedAction[] | null }, action: string): string | undefined {
  return getDisabledReason(item.allowedActions, action);
}

/** 分页获取指定工单的成品入库记录 */
async function fetchReceiptList() {
  if (!selectedWorkOrderId.value) {
    receiptList.value = [];
    total.value = 0;
    viewState.value = "empty";
    return;
  }
  viewState.value = "loading";
  errorMessage.value = "";
  try {
    const res = await getFinishedGoodsReceipts(selectedWorkOrderId.value);
    if (res.data) {
      let list = res.data || [];
      if (queryParams.keyword.trim()) {
        const kw = queryParams.keyword.toLowerCase();
        list = list.filter(
          (r) =>
            r.receiptNo.toLowerCase().includes(kw) ||
            r.workOrderNo?.toLowerCase().includes(kw) ||
            r.productName?.toLowerCase().includes(kw)
        );
      }
      if (queryParams.status) {
        list = list.filter((r) => r.status === queryParams.status);
      }
      receiptList.value = list;
      total.value = list.length;
      viewState.value = receiptList.value.length === 0 ? "empty" : "ready";
    }
  } catch (err: any) {
    errorMessage.value = err.message || "请求成品入库列表失败";
    viewState.value = "error";
  }
}

/** 切换所属工单筛选 */
function handleWorkOrderFilterChange() {
  fetchReceiptList();
}

/** 筛选查询 */
function handleSearch() {
  queryParams.page = 1;
  fetchReceiptList();
}

/** 重置筛选条件 */
function handleReset() {
  queryParams.keyword = "";
  queryParams.status = "";
  queryParams.page = 1;
  fetchReceiptList();
}

/** 分页改变处理 */
function handlePageChange(page: number) {
  queryParams.page = page;
  fetchReceiptList();
}

/** 打开新建成品入库申请单对话框 */
async function openCreateModal() {
  createForm.workOrderId = selectedWorkOrderId.value || (workOrders.value[0] ? String(workOrders.value[0].id) : "");
  if (warehouses.value.length > 0) {
    createForm.warehouseId = String(warehouses.value[0].id);
    await loadLocations();
  }
  onModalWorkOrderChange();
  createModalVisible.value = true;
}

/** 弹窗内工单变更处理，自动带入合格未入库数量 */
function onModalWorkOrderChange() {
  if (!createForm.workOrderId) return;
  const wo = workOrders.value.find((w) => String(w.id) === String(createForm.workOrderId));
  if (wo) {
    const qual = parseFloat(wo.qualifiedQty || "0");
    const recv = parseFloat(wo.receivedQty || "0");
    const diff = qual - recv;
    // 没有后端确认的可入库数量时保持空值，禁止以计划量或固定数量冒充合格事实。
    createForm.receiptQty = diff > 0 ? String(diff) : "";
  }
}

/** 提交新建成品入库申请单 */
async function submitCreateReceipt() {
  if (!createForm.workOrderId || !receiptQuantityValid.value || !createForm.warehouseId || !createForm.locationId) {
    ElMessage.warning("请完整填写合格的入库信息");
    return;
  }
  // 修改用途：成品入库单号是服务端事实的必填标识，由页面一次生成并在幂等执行期间复用。
  const receiptNo = `FGR-${crypto.randomUUID()}`;
  try {
    const created = await execute((key) => createFinishedGoodsReceipt({ ...createForm, receiptNo }, key), { onConflict: fetchReceiptList });
    if (!created?.data?.id) {
      throw new Error("服务端未返回成品入库单 ID，已阻止继续确认");
    }
    createModalVisible.value = false;
    ElMessage.success("成品入库申请单创建成功！请在列表点击【确认入库】将货物正式移入库位。");
    selectedWorkOrderId.value = createForm.workOrderId;
    await fetchReceiptList();
  } catch (err: any) {
    ElMessage.error(`创建入库单失败：${err.message}`);
  }
}

/** 提示确认成品入库对话框 */
function promptConfirm(item: FinishedGoodsReceiptItem) {
  confirmDialog.item = item;
  confirmDialog.visible = true;
}

/** 执行成品入库确认 */
async function handleExecuteConfirm() {
  if (!confirmDialog.item) return;
  confirmDialog.loading = true;
  try {
    await execute((key) => confirmFinishedGoodsReceipt(confirmDialog.item!.id as string, key), { onConflict: fetchReceiptList });
    confirmDialog.visible = false;
    ElMessage.success("成品入库确认已成功完成！");
    await fetchReceiptList();
  } catch (err: any) {
    ElMessage.error(`确认入库失败：${err.message}`);
  } finally {
    confirmDialog.loading = false;
  }
}

onMounted(async () => {
  await loadMasterData();
  if (route.query.workOrderId) {
    selectedWorkOrderId.value = String(route.query.workOrderId);
  } else if (workOrders.value.length > 0) {
    selectedWorkOrderId.value = String(workOrders.value[0].id);
  }
  if (selectedWorkOrderId.value) {
    await fetchReceiptList();
  }
});

/** 加载 FGR 创建和查询所需真实 UUID，并按工单作为后端必填查询条件。 */
async function loadMasterData() {
  masterDataLoading.value = true;
  masterDataError.value = "";
  try {
    const keyword = masterDataKeyword.value.trim() || undefined;
    const [workOrderRes, warehouseRes] = await Promise.all([
      getWorkOrders({ page: 1, size: 1000, workOrderNo: keyword }),
      getWarehouses({ page: 1, size: 1000, keyword, status: "ACTIVE" }),
    ]);
    workOrders.value = workOrderRes.data.records || [];
    warehouses.value = warehouseRes.data.records || [];
    if (selectedWorkOrderId.value && !workOrders.value.some((item) => String(item.id) === String(selectedWorkOrderId.value))) {
      selectedWorkOrderId.value = "";
    }
    if (createForm.warehouseId && !warehouses.value.some((item) => String(item.id) === String(createForm.warehouseId))) {
      createForm.warehouseId = "";
      createForm.locationId = "";
      locations.value = [];
    }
  } catch (err: any) {
    errorMessage.value = err?.message || "加载成品入库主数据失败";
    masterDataError.value = errorMessage.value;
    viewState.value = "error";
  } finally {
    masterDataLoading.value = false;
  }
}

/** 按已选仓库读取真实库位 UUID，避免仓库与库位跨仓引用。 */
async function loadLocations() {
  createForm.locationId = "";
  if (!createForm.warehouseId) {
    locations.value = [];
    return;
  }
  const response = await getLocations({
    warehouseId: createForm.warehouseId,
    page: 1,
    size: 1000,
    keyword: masterDataKeyword.value.trim() || undefined,
    type: "Storage",
    status: "ACTIVE",
  });
  // 修改用途：后端按类型查询，前端再次收敛结果，避免非 Storage 库位进入成品入库选择框。
  locations.value = (response.data.records || []).filter(
    (location) => location.status === "ACTIVE" && location.type === "Storage"
  );
}
</script>

<style scoped>
.manufacturing-view-container {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.filter-select-group {
  display: flex;
  align-items: center;
  gap: 8px;
}

.filter-label {
  font-size: 13px;
  color: #94a3b8;
}

.highlight-code {
  color: #38bdf8;
  font-weight: 600;
}

.wo-cell, .location-cell {
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.product-name, .loc-code {
  font-size: 11px;
}

.wh-name {
  color: #f1f5f9;
}

.action-btn-group {
  display: flex;
  align-items: center;
  justify-content: center;
}

.text-primary { color: #38bdf8 !important; }
.font-xs { font-size: 11px; }

/* 模态框内部样式 */
.dialog-content-wrapper {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.options-search-row {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 8px 12px;
  border: 1px solid rgba(148, 163, 184, 0.18);
  border-radius: 6px;
  background: rgba(15, 23, 42, 0.4);
}

.options-search-row label {
  flex: 0 0 auto;
  font-size: 13px;
  color: #94a3b8;
}

.options-hint {
  padding: 8px 12px;
  border-radius: 6px;
  background: rgba(30, 41, 59, 0.6);
  font-size: 12px;
}

.modal-hint {
  margin: 0;
  font-size: 12px;
  color: #94a3b8;
  background: rgba(30, 41, 59, 0.5);
  padding: 10px 14px;
  border-radius: 6px;
  border: 1px solid rgba(255, 255, 255, 0.05);
}

.custom-el-form :deep(.el-form-item__label) {
  color: #94a3b8;
  font-size: 13px;
  padding-bottom: 4px;
}
</style>
