<template>
  <div class="manufacturing-view-container">
    <CommandFeedback :error="lastError" :can-retry="canRetry" :executing="isExecuting" @retry="retry" />
    <!-- 统一页面头部 -->
    <PageHeader
      title="生产领料与退料协同 (Material Movement)"
      tag="MES / INVENTORY BRIDGE"
      description="连接制造执行与仓储库存的物料交接业务。领料确认触发实物库存扣减；退料确认增加退回库位库存并生成流水软引用。"
    >
      <template #actions>
        <button v-if="activeTab === 'issue'" type="button" class="btn btn-primary" @click="openCreateIssueModal">
          <span class="btn-icon">＋</span>
          <span>新建领料申请单</span>
        </button>
        <button v-else type="button" class="btn btn-primary" @click="openCreateReturnModal">
          <span class="btn-icon">＋</span>
          <span>新建生产退料单</span>
        </button>
      </template>
    </PageHeader>

    <!-- 顶部单据类型切换 Tab -->
    <div class="movement-nav-tabs">
      <button
        type="button"
        class="nav-tab-item"
        :class="{ 'is-active': activeTab === 'issue' }"
        @click="switchTab('issue')"
      >
        <span>生产领料单 (Material Issues)</span>
        <span class="tab-count-badge">{{ issueList.length }}</span>
      </button>
      <button
        type="button"
        class="nav-tab-item"
        :class="{ 'is-active': activeTab === 'return' }"
        @click="switchTab('return')"
      >
        <span>生产退料单 (Material Returns)</span>
        <span class="tab-count-badge">{{ returnList.length }}</span>
      </button>
    </div>

    <!-- 统一筛选栏 -->
    <FilterBar
      v-model="keyword"
      placeholder="搜索单据编号或关联工单号..."
      @search="handleSearch"
      @reset="handleReset"
    >
      <div class="filter-select-group">
        <label class="filter-label">状态：</label>
        <select v-model="statusFilter" class="filter-select" @change="handleSearch">
          <option value="">全部状态</option>
          <option value="Draft">草稿待确认 (Draft)</option>
          <option value="Confirmed">已出入库确认 (Confirmed)</option>
        </select>
      </div>
    </FilterBar>

    <!-- 错误异常提示 -->
    <ErrorState
      v-if="viewState === 'error'"
      title="领退料单据加载失败"
      :message="errorMessage"
      @retry="loadData"
    />

    <!-- 数据表格：领料单 -->
    <DataTable
      v-else-if="activeTab === 'issue'"
      :columns="issueColumns"
      :data="filteredIssueList"
      :loading="viewState === 'loading'"
      empty-text="暂无生产领料单记录"
    >
      <!-- 领料单号 -->
      <template #issueNo="{ row }">
        <span class="font-mono highlight-code">{{ row.issueNo }}</span>
      </template>

      <!-- 关联工单 -->
      <template #workOrderId="{ row }">
        <span class="font-mono text-primary">{{ row.workOrderNo || row.workOrderId }}</span>
      </template>

      <!-- 物料明细概览 -->
      <template #items="{ row }">
        <div class="items-summary">
          <div v-for="item in row.items" :key="item.id" class="item-line">
            <span class="item-name">{{ item.productName || item.productCode || item.productId }}</span>
            <span class="item-qty">
              领用: <QuantityText :value="item.issueQty" :unit="item.uom" />
            </span>
            <span class="item-loc font-mono text-muted">({{ item.warehouseName || item.warehouseId }} / {{ item.locationCode || item.locationId }})</span>
          </div>
        </div>
      </template>

      <!-- 状态 -->
      <template #status="{ row }">
        <StatusBadge
          :type="row.status === 'Confirmed' ? 'success' : 'warning'"
          :text="row.status === 'Confirmed' ? '已确认出库' : '待出库确认'"
        />
      </template>

      <!-- 库存流水引用 -->
      <template #inventoryTransactionId="{ row }">
        <span v-if="row.inventoryTransactionId" class="font-mono text-muted">
          {{ row.inventoryTransactionId }}
        </span>
        <span v-else class="text-muted font-xs">尚未产生扣减流水</span>
      </template>

      <!-- 操作 (受 allowedActions 约束) -->
      <template #actions="{ row }">
        <div class="action-btn-group">
          <button
            v-if="row.status === 'Draft'"
            type="button"
            class="btn-text text-primary"
            :disabled="!isActionAllowed(row, 'confirm')"
            :title="getActionDisabledReason(row, 'confirm') || '确认领料出库'"
            @click="promptConfirmIssue(row)"
          >
            出库确认
          </button>
          <span v-else class="text-muted font-xs">已完成扣减</span>
        </div>
      </template>
    </DataTable>

    <!-- 数据表格：退料单 -->
    <DataTable
      v-else
      :columns="returnColumns"
      :data="filteredReturnList"
      :loading="viewState === 'loading'"
      empty-text="暂无生产退料单记录"
    >
      <!-- 退料单号 -->
      <template #returnNo="{ row }">
        <span class="font-mono highlight-code">{{ row.returnNo }}</span>
      </template>

      <!-- 关联工单 -->
      <template #workOrderId="{ row }">
        <span class="font-mono text-primary">{{ row.workOrderNo || row.workOrderId }}</span>
      </template>

      <!-- 物料明细概览 -->
      <template #items="{ row }">
        <div class="items-summary">
          <div v-for="item in row.items" :key="item.id" class="item-line">
            <span class="item-name">{{ item.productName || item.productCode || item.productId }}</span>
            <span class="item-qty">
              退料: <QuantityText :value="item.returnQty" :unit="item.uom" />
            </span>
            <span class="item-loc font-mono text-muted">({{ item.warehouseName || item.warehouseId }} / {{ item.locationCode || item.locationId }})</span>
          </div>
        </div>
      </template>

      <!-- 状态 -->
      <template #status="{ row }">
        <StatusBadge
          :type="row.status === 'Confirmed' ? 'success' : 'warning'"
          :text="row.status === 'Confirmed' ? '已确认退库' : '待退库确认'"
        />
      </template>

      <!-- 库存流水引用 -->
      <template #inventoryTransactionId="{ row }">
        <span v-if="row.inventoryTransactionId" class="font-mono text-muted">
          {{ row.inventoryTransactionId }}
        </span>
        <span v-else class="text-muted font-xs">尚未产生退库流水</span>
      </template>

      <!-- 操作 (受 allowedActions 约束) -->
      <template #actions="{ row }">
        <div class="action-btn-group">
          <button
            v-if="row.status === 'Draft'"
            type="button"
            class="btn-text text-primary"
            :disabled="!isActionAllowed(row, 'confirm')"
            :title="getActionDisabledReason(row, 'confirm') || '确认退料入库'"
            @click="promptConfirmReturn(row)"
          >
            退库确认
          </button>
          <span v-else class="text-muted font-xs">已完成退入</span>
        </div>
      </template>
    </DataTable>

    <!-- 弹窗 1：新建领料单 -->
    <div v-if="createIssueModalVisible" class="modal-mask" @click.self="createIssueModalVisible = false">
      <div class="modal-card modal-large">
        <div class="modal-header">
          <h3 class="modal-title">新建生产领料申请单</h3>
          <button type="button" class="btn-close" @click="createIssueModalVisible = false">✕</button>
        </div>
        <form class="modal-body" @submit.prevent="submitCreateIssue">
          <div class="options-search-row">
            <label for="issue-option-keyword">目录搜索</label>
            <input
              id="issue-option-keyword"
              v-model="movementOptionsKeyword"
              type="search"
              class="form-input"
              placeholder="输入工单、物料或仓库编码/名称后回车搜索"
              @keyup.enter="loadMovementOptions"
            />
            <button type="button" class="btn-text text-primary" @click="loadMovementOptions">搜索</button>
          </div>
          <div v-if="isMovementOptionsLoading" class="options-hint text-muted">⏳ 正在加载真实主数据目录...</div>
          <div v-else-if="movementOptionsError" class="options-hint text-warning">⚠️ {{ movementOptionsError }}</div>
          <div class="form-item">
            <label>关联生产工单 <span class="req">*</span></label>
            <select v-model="issueForm.workOrderId" class="form-select" required>
              <option value="">请选择关联生产工单</option>
              <option v-for="wo in availableWorkOrders" :key="wo.id" :value="wo.id">
                {{ wo.workOrderNo || wo.woNo }} - {{ wo.productName || '工单' }} (计划: {{ wo.plannedQty }}件, {{ wo.status }})
              </option>
            </select>
          </div>

          <div class="form-section">
            <label class="section-title">领料明细项（消除手填 UUID，级联选择）</label>
            <div class="form-grid two-col" style="margin-bottom: 10px;">
              <div class="form-item">
                <label>领用物料 <span class="req">*</span></label>
                <select v-model="issueForm.productId" class="form-select" required>
                  <option value="">请选择领用物料</option>
                  <option v-for="p in availableProducts" :key="p.id" :value="p.id">
                    {{ p.sku }} - {{ p.name }} ({{ p.uom }})
                  </option>
                </select>
              </div>
              <div class="form-item">
                <label>领料数量 <span class="req">*</span></label>
                <input
                  v-model="issueForm.issueQty"
                  type="number"
                  min="0.01"
                  step="0.01"
                  class="form-input font-mono"
                  placeholder="领料数量 (如 100.00)"
                  required
                />
              </div>
            </div>

            <div class="form-grid two-col">
              <div class="form-item">
                <label>出库仓库 <span class="req">*</span></label>
                <select v-model="issueForm.warehouseId" class="form-select" required @change="handleIssueWarehouseChange">
                  <option value="">请选择出库仓库</option>
                  <option v-for="w in availableWarehouses" :key="w.id" :value="w.id">
                    {{ w.code }} - {{ w.name }}
                  </option>
                </select>
              </div>
              <div class="form-item">
                <label>出库库位 <span class="req">*</span></label>
                <select v-model="issueForm.locationId" class="form-select font-mono" required :disabled="!issueForm.warehouseId">
                  <option value="">{{ !issueForm.warehouseId ? '请先选择出库仓库' : '请选择库位' }}</option>
                  <option v-for="l in filteredIssueLocations" :key="l.id" :value="l.id">
                    {{ l.code }} - {{ l.name }} ({{ l.type }})
                  </option>
                </select>
              </div>
            </div>

            <div class="form-item" style="margin-top: 10px;">
              <label>超领原因 (可选，超出定额时必填说明)</label>
              <input
                v-model="issueForm.overageReason"
                type="text"
                class="form-input"
                placeholder="如: 原料损耗补料、工艺调整追加领料..."
              />
            </div>
          </div>

          <div class="modal-footer">
            <button type="button" class="btn btn-secondary" @click="createIssueModalVisible = false">取消</button>
            <button type="submit" class="btn btn-primary" :disabled="isSubmitting">保存领料单 (草稿)</button>
          </div>
        </form>
      </div>
    </div>

    <!-- 弹窗 2：新建退料单 -->
    <div v-if="createReturnModalVisible" class="modal-mask" @click.self="createReturnModalVisible = false">
      <div class="modal-card modal-large">
        <div class="modal-header">
          <h3 class="modal-title">新建生产退料单</h3>
          <button type="button" class="btn-close" @click="createReturnModalVisible = false">✕</button>
        </div>
        <form class="modal-body" @submit.prevent="submitCreateReturn">
          <div class="options-search-row">
            <label for="return-option-keyword">目录搜索</label>
            <input
              id="return-option-keyword"
              v-model="movementOptionsKeyword"
              type="search"
              class="form-input"
              placeholder="输入工单、物料或仓库编码/名称后回车搜索"
              @keyup.enter="loadMovementOptions"
            />
            <button type="button" class="btn-text text-primary" @click="loadMovementOptions">搜索</button>
          </div>
          <div v-if="isMovementOptionsLoading" class="options-hint text-muted">⏳ 正在加载真实主数据目录...</div>
          <div v-else-if="movementOptionsError" class="options-hint text-warning">⚠️ {{ movementOptionsError }}</div>
          <div class="form-item">
            <label>关联生产工单 <span class="req">*</span></label>
            <select v-model="returnForm.workOrderId" class="form-select" required>
              <option value="">请选择关联生产工单</option>
              <option v-for="wo in availableWorkOrders" :key="wo.id" :value="wo.id">
                {{ wo.workOrderNo || wo.woNo }} - {{ wo.productName || '工单' }} (计划: {{ wo.plannedQty }}件, {{ wo.status }})
              </option>
            </select>
          </div>

          <div class="form-section">
            <label class="section-title">退料明细项（消除手填 UUID，级联选择）</label>
            <div class="form-grid two-col" style="margin-bottom: 10px;">
              <div class="form-item">
                <label>退回物料 <span class="req">*</span></label>
                <select v-model="returnForm.productId" class="form-select" required>
                  <option value="">请选择退回物料</option>
                  <option v-for="p in availableProducts" :key="p.id" :value="p.id">
                    {{ p.sku }} - {{ p.name }} ({{ p.uom }})
                  </option>
                </select>
              </div>
              <div class="form-item">
                <label>退料数量 <span class="req">*</span></label>
                <input
                  v-model="returnForm.returnQty"
                  type="number"
                  min="0.01"
                  step="0.01"
                  class="form-input font-mono"
                  placeholder="退料数量 (如 2.00)"
                  required
                />
              </div>
            </div>

            <div class="form-grid two-col">
              <div class="form-item">
                <label>退入仓库 <span class="req">*</span></label>
                <select v-model="returnForm.warehouseId" class="form-select" required @change="handleReturnWarehouseChange">
                  <option value="">请选择退入仓库</option>
                  <option v-for="w in availableWarehouses" :key="w.id" :value="w.id">
                    {{ w.code }} - {{ w.name }}
                  </option>
                </select>
              </div>
              <div class="form-item">
                <label>退入库位 <span class="req">*</span></label>
                <select v-model="returnForm.locationId" class="form-select font-mono" required :disabled="!returnForm.warehouseId">
                  <option value="">{{ !returnForm.warehouseId ? '请先选择退入仓库' : '请选择库位' }}</option>
                  <option v-for="l in filteredReturnLocations" :key="l.id" :value="l.id">
                    {{ l.code }} - {{ l.name }} ({{ l.type }})
                  </option>
                </select>
              </div>
            </div>

            <div class="form-item" style="margin-top: 10px;">
              <label>退料原因 <span class="req">*</span></label>
              <input
                v-model="returnForm.reason"
                type="text"
                class="form-input"
                placeholder="如: 工单完工余料退回、来料不良退库..."
                required
              />
            </div>
          </div>

          <div class="modal-footer">
            <button type="button" class="btn btn-secondary" @click="createReturnModalVisible = false">取消</button>
            <button type="submit" class="btn btn-primary" :disabled="isSubmitting">保存退料单 (草稿)</button>
          </div>
        </form>
      </div>
    </div>

    <!-- 二次确认对话框 -->
    <ConfirmDialog
      v-model:visible="confirmDialog.visible"
      :title="confirmDialog.title"
      :message="confirmDialog.message"
      :loading="confirmDialog.loading"
      @confirm="handleExecuteConfirm"
    />
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, computed, onMounted } from "vue";
import { useCommand } from "@/composables/useCommand";
import CommandFeedback from "@/components/common/CommandFeedback.vue";
import { useRoute } from "vue-router";
import { isActionAllowed as checkAction, getActionDisabledReason as getDisabledReason } from "../../utils/actionGuard";
import type { AllowedAction } from "../../types/common";
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
  MaterialIssueItem,
  MaterialReturnItem,
} from "../../types/manufacturing";
import {
  createMaterialIssue,
  confirmMaterialIssue,
  getMaterialIssues,
  createMaterialReturn,
  confirmMaterialReturn,
  getMaterialReturns,
  getWorkOrders,
} from "../../api/manufacturing";
import { getProducts, getWarehouses, getLocations } from "../../api/masterData";
import type { Product, Warehouse, Location } from "../../types/inventory";

const route = useRoute();
const viewState = ref<ViewState>("loading");
const errorMessage = ref("");
const activeTab = ref<"issue" | "return">("issue");

const keyword = ref("");
const statusFilter = ref("");

const issueList = ref<MaterialIssueItem[]>([]);
const returnList = ref<MaterialReturnItem[]>([]);

const availableWorkOrders = ref<any[]>([]);
const availableProducts = ref<Product[]>([]);
const availableWarehouses = ref<Warehouse[]>([]);
const availableLocations = ref<Location[]>([]);
const movementOptionsKeyword = ref("");
const movementOptionsError = ref("");
const isMovementOptionsLoading = ref(false);

const issueColumns: TableColumn[] = [
  { key: "issueNo", label: "领料单号", width: "180px" },
  { key: "workOrderId", label: "关联工单", width: "180px" },
  { key: "items", label: "物料用量与库位明细", minWidth: "260px" },
  { key: "status", label: "状态", width: "120px", align: "center" },
  { key: "inventoryTransactionId", label: "库存流水", width: "160px" },
  { key: "actions", label: "操作", width: "110px", align: "center" },
];

const returnColumns: TableColumn[] = [
  { key: "returnNo", label: "退料单号", width: "180px" },
  { key: "workOrderId", label: "关联工单", width: "180px" },
  { key: "items", label: "退料明细与退库位", minWidth: "260px" },
  { key: "status", label: "状态", width: "120px", align: "center" },
  { key: "inventoryTransactionId", label: "库存流水", width: "160px" },
  { key: "actions", label: "操作", width: "110px", align: "center" },
];

const filteredIssueList = computed(() => {
  let list = issueList.value;
  if (keyword.value.trim()) {
    const kw = keyword.value.toLowerCase();
    list = list.filter(
      (m) =>
        m.issueNo.toLowerCase().includes(kw) ||
        m.workOrderNo?.toLowerCase().includes(kw)
    );
  }
  if (statusFilter.value) {
    list = list.filter((m) => m.status === statusFilter.value);
  }
  return list;
});

const filteredReturnList = computed(() => {
  let list = returnList.value;
  if (keyword.value.trim()) {
    const kw = keyword.value.toLowerCase();
    list = list.filter(
      (r) =>
        r.returnNo.toLowerCase().includes(kw) ||
        r.workOrderNo?.toLowerCase().includes(kw)
    );
  }
  if (statusFilter.value) {
    list = list.filter((r) => r.status === statusFilter.value);
  }
  return list;
});

const createIssueModalVisible = ref(false);
const issueForm = reactive({
  workOrderId: "",
  productId: "",
  issueQty: "",
  warehouseId: "",
  locationId: "",
  overageReason: "",
});

const createReturnModalVisible = ref(false);
const returnForm = reactive({
  workOrderId: "",
  productId: "",
  returnQty: "",
  warehouseId: "",
  locationId: "",
  reason: "工单完工余料退回",
});

const filteredIssueLocations = computed(() => {
  if (!issueForm.warehouseId) return [];
  return availableLocations.value.filter((l) => String(l.warehouseId) === String(issueForm.warehouseId));
});

const filteredReturnLocations = computed(() => {
  if (!returnForm.warehouseId) return [];
  return availableLocations.value.filter((l) => String(l.warehouseId) === String(returnForm.warehouseId));
});

const { execute, retry, isExecuting, canRetry, lastError } = useCommand();
const isSubmitting = isExecuting;

const confirmDialog = reactive({
  visible: false,
  loading: false,
  title: "",
  message: "",
  type: "" as "issue" | "return",
  targetId: "",
});

// 替换为调用 actionGuard 的版本
function isActionAllowed(item: { allowedActions?: AllowedAction[] | null }, action: string): boolean {
  return checkAction(item.allowedActions, action);
}

// 替换为调用 actionGuard 的版本
function getActionDisabledReason(item: { allowedActions?: AllowedAction[] | null }, action: string): string | undefined {
  return getDisabledReason(item.allowedActions, action);
}

async function loadData() {
  viewState.value = "loading";
  errorMessage.value = "";
  if (availableWorkOrders.value.length === 0) {
    issueList.value = [];
    returnList.value = [];
    viewState.value = "ready";
    return;
  }
  try {
    // 修改用途：按真实工单集合读取领退料事实，避免保存成功后页面仍固定显示空列表。
    const results = await Promise.all(availableWorkOrders.value.map(async (workOrder) => {
      const workOrderId = String(workOrder.id);
      const [issues, returns] = await Promise.all([
        getMaterialIssues(workOrderId),
        getMaterialReturns(workOrderId),
      ]);
      return {
        issues: issues.data || [],
        returns: returns.data || [],
      };
    }));
    issueList.value = results.flatMap((result) => result.issues);
    returnList.value = results.flatMap((result) => result.returns);
    viewState.value = "ready";
  } catch (err: any) {
    errorMessage.value = err?.message || "请求领退料单列表失败";
    issueList.value = [];
    returnList.value = [];
    viewState.value = "error";
  }
}

async function loadMovementOptions() {
  isMovementOptionsLoading.value = true;
  movementOptionsError.value = "";
  try {
    const keyword = movementOptionsKeyword.value.trim() || undefined;
    const selectedWarehouseId = activeTab.value === "issue" ? issueForm.warehouseId : returnForm.warehouseId;
    const [woRes, prodRes, whRes, locRes] = await Promise.allSettled([
      getWorkOrders({ page: 1, size: 1000, workOrderNo: keyword }),
      getProducts({ page: 1, size: 1000, keyword, status: "ACTIVE" }),
      getWarehouses({ page: 1, size: 1000, keyword, status: "ACTIVE" }),
      selectedWarehouseId
        ? getLocations({ page: 1, size: 1000, keyword, warehouseId: selectedWarehouseId, status: "ACTIVE" })
        : Promise.resolve(null),
    ]);
    if (woRes.status === "fulfilled") {
      availableWorkOrders.value = woRes.value.data?.records || [];
    }
    if (prodRes.status === "fulfilled") {
      availableProducts.value = prodRes.value.data?.records || [];
    }
    if (whRes.status === "fulfilled") {
      availableWarehouses.value = whRes.value.data?.records || [];
    }
    if (locRes.status === "fulfilled") {
      availableLocations.value = locRes.value?.data?.records || [];
    } else {
      availableLocations.value = [];
    }
    if (woRes.status === "rejected" || prodRes.status === "rejected" || whRes.status === "rejected" || locRes.status === "rejected") {
      movementOptionsError.value = "部分主数据目录加载失败，请重试或缩小搜索条件";
    }
    // 搜索/切换页面后清理已经不在当前真实目录中的下游值，禁止把旧 UUID 提交给后端。
    if (issueForm.productId && !availableProducts.value.some((item) => String(item.id) === String(issueForm.productId))) issueForm.productId = "";
    if (returnForm.productId && !availableProducts.value.some((item) => String(item.id) === String(returnForm.productId))) returnForm.productId = "";
    if (issueForm.warehouseId && !availableWarehouses.value.some((item) => String(item.id) === String(issueForm.warehouseId))) {
      issueForm.warehouseId = "";
      issueForm.locationId = "";
    }
    if (returnForm.warehouseId && !availableWarehouses.value.some((item) => String(item.id) === String(returnForm.warehouseId))) {
      returnForm.warehouseId = "";
      returnForm.locationId = "";
    }
  } catch (err: any) {
    movementOptionsError.value = err?.message || "加载基础选项失败";
    availableLocations.value = [];
  } finally {
    isMovementOptionsLoading.value = false;
  }
}

/** 仓库变更后仅查询该仓真实活动库位，并清除失效下游值。 */
async function handleIssueWarehouseChange() {
  issueForm.locationId = "";
  activeTab.value = "issue";
  await loadMovementOptions();
}

/** 仓库变更后仅查询该仓真实活动库位，并清除失效下游值。 */
async function handleReturnWarehouseChange() {
  returnForm.locationId = "";
  activeTab.value = "return";
  await loadMovementOptions();
}

function switchTab(tab: "issue" | "return") {
  activeTab.value = tab;
}

function handleSearch() {
  // filtered by computed
}

function handleReset() {
  keyword.value = "";
  statusFilter.value = "";
}

function openCreateIssueModal() {
  if (!issueForm.workOrderId && availableWorkOrders.value.length > 0) {
    issueForm.workOrderId = String(availableWorkOrders.value[0].id);
  }
  if (!issueForm.productId && availableProducts.value.length > 0) {
    issueForm.productId = String(availableProducts.value[0].id);
  }
  if (!issueForm.warehouseId && availableWarehouses.value.length > 0) {
    issueForm.warehouseId = String(availableWarehouses.value[0].id);
  }
  void loadMovementOptions();
  createIssueModalVisible.value = true;
}

async function submitCreateIssue() {
  if (!issueForm.workOrderId || !issueForm.productId || !issueForm.issueQty || !issueForm.warehouseId || !issueForm.locationId) return;
  try {
    const created = await execute((key) => createMaterialIssue({
      workOrderId: issueForm.workOrderId,
      items: [
        {
          productId: issueForm.productId,
          warehouseId: issueForm.warehouseId,
          locationId: issueForm.locationId,
          issueQty: issueForm.issueQty,
        },
      ],
      overageReason: issueForm.overageReason || undefined,
    }, key), { onConflict: loadData });
    if (!created?.data?.id) {
      throw new Error("服务端未返回 materialIssueId，已阻止继续确认领料");
    }
    createIssueModalVisible.value = false;
    alert("领料单已成功创建！可在领料单列表中执行出库确认。");
    await loadData();
  } catch (err: any) {
    alert(`创建领料单失败：${err.message}`);
  } finally { /* useCommand 在 finally 中恢复 isExecuting。 */ }
}

function openCreateReturnModal() {
  if (!returnForm.workOrderId && availableWorkOrders.value.length > 0) {
    returnForm.workOrderId = String(availableWorkOrders.value[0].id);
  }
  if (!returnForm.productId && availableProducts.value.length > 0) {
    returnForm.productId = String(availableProducts.value[0].id);
  }
  if (!returnForm.warehouseId && availableWarehouses.value.length > 0) {
    returnForm.warehouseId = String(availableWarehouses.value[0].id);
  }
  void loadMovementOptions();
  createReturnModalVisible.value = true;
}

async function submitCreateReturn() {
  if (!returnForm.workOrderId || !returnForm.productId || !returnForm.returnQty || !returnForm.warehouseId || !returnForm.locationId) return;
  try {
    const created = await execute((key) => createMaterialReturn({
      workOrderId: returnForm.workOrderId,
      items: [
        {
          productId: returnForm.productId,
          warehouseId: returnForm.warehouseId,
          locationId: returnForm.locationId,
          returnQty: returnForm.returnQty,
        },
      ],
      reason: returnForm.reason,
    }, key), { onConflict: loadData });
    if (!created?.data?.id) {
      throw new Error("服务端未返回 materialReturnId，已阻止继续确认退料");
    }
    createReturnModalVisible.value = false;
    alert("退料单已成功创建！可在退料单列表中执行退库确认。");
    await loadData();
  } catch (err: any) {
    alert(`创建退料单失败：${err.message}`);
  } finally { /* useCommand 在 finally 中恢复 isExecuting。 */ }
}

function promptConfirmIssue(item: MaterialIssueItem) {
  confirmDialog.title = "确认生产领料出库";
  confirmDialog.message = `确认领料单【${item.issueNo}】出库？库存服务将真实扣减原料库位库存并生成流水软引用。`;
  confirmDialog.type = "issue";
  confirmDialog.targetId = item.id as string;
  confirmDialog.visible = true;
}

function promptConfirmReturn(item: MaterialReturnItem) {
  confirmDialog.title = "确认生产退料入库";
  confirmDialog.message = `确认退料单【${item.returnNo}】入库？将增加目标库位实物库存并生成流水软引用。`;
  confirmDialog.type = "return";
  confirmDialog.targetId = item.id as string;
  confirmDialog.visible = true;
}

async function handleExecuteConfirm() {
  confirmDialog.loading = true;
  try {
    if (confirmDialog.type === "issue") {
      await execute((key) => confirmMaterialIssue(confirmDialog.targetId, key), { onConflict: loadData });
    } else {
      await execute((key) => confirmMaterialReturn(confirmDialog.targetId, key), { onConflict: loadData });
    }
    confirmDialog.visible = false;
    await loadData();
  } catch (err: any) {
    alert(`确认失败：${err.message}`);
  } finally {
    confirmDialog.loading = false;
  }
}

onMounted(async () => {
  await loadMovementOptions();
  await loadData();
  if (route.query.workOrderId) {
    const qWoId = String(route.query.workOrderId);
    issueForm.workOrderId = qWoId;
    returnForm.workOrderId = qWoId;
    if (route.query.tab === "return") {
      activeTab.value = "return";
      openCreateReturnModal();
    } else {
      openCreateIssueModal();
    }
  }
});
</script>

<style scoped>
.manufacturing-view-container {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.movement-nav-tabs {
  display: flex;
  align-items: center;
  gap: 12px;
  background: rgba(15, 23, 42, 0.7);
  border: 1px solid rgba(255, 255, 255, 0.08);
  border-radius: 8px;
  padding: 6px;
}

.nav-tab-item {
  display: flex;
  align-items: center;
  gap: 8px;
  background: transparent;
  border: none;
  color: #94a3b8;
  padding: 8px 16px;
  border-radius: 6px;
  font-size: 13px;
  font-weight: 500;
  cursor: pointer;
  transition: all 0.2s;
}

.nav-tab-item:hover {
  color: #f8fafc;
  background: rgba(255, 255, 255, 0.05);
}

.nav-tab-item.is-active {
  background: #0284c7;
  color: #ffffff;
}

.tab-count-badge {
  background: rgba(0, 0, 0, 0.25);
  font-size: 11px;
  font-family: var(--font-mono, monospace);
  padding: 1px 6px;
  border-radius: 10px;
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

.filter-select {
  background: rgba(30, 41, 59, 0.8);
  border: 1px solid rgba(255, 255, 255, 0.12);
  color: #f8fafc;
  padding: 6px 12px;
  border-radius: 6px;
  font-size: 13px;
  outline: none;
}

.highlight-code {
  color: #38bdf8;
  font-weight: 600;
}

.items-summary {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.item-line {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 12px;
}

.item-name {
  color: #f1f5f9;
}

.item-qty {
  color: #cbd5e1;
}

.action-btn-group {
  display: flex;
  align-items: center;
  justify-content: center;
}

.btn-text {
  background: none;
  border: none;
  font-size: 12px;
  cursor: pointer;
  padding: 2px 6px;
}

.btn-text:hover:not(:disabled) {
  text-decoration: underline;
}

.btn-text:disabled {
  color: #64748b;
  cursor: not-allowed;
  text-decoration: none;
}

.text-primary { color: #38bdf8 !important; }
.font-xs { font-size: 11px; }

/* 模态框 */
.modal-mask {
  position: fixed;
  inset: 0;
  background: rgba(0, 0, 0, 0.7);
  backdrop-filter: blur(4px);
  z-index: 1000;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 16px;
}

.modal-card {
  background: #0f172a;
  border: 1px solid rgba(255, 255, 255, 0.15);
  border-radius: 10px;
  width: 100%;
  max-width: 520px;
  box-shadow: 0 20px 30px rgba(0, 0, 0, 0.5);
  overflow: hidden;
}

.modal-large { max-width: 680px; }

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
  gap: 16px;
}

.form-item {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.form-item label, .section-title {
  font-size: 12px;
  color: #94a3b8;
}

.options-search-row {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 8px 10px;
  border: 1px solid rgba(148, 163, 184, 0.18);
  border-radius: 6px;
}

.options-search-row label {
  flex: 0 0 auto;
}

.options-search-row .form-input {
  flex: 1;
}

.options-hint {
  padding: 8px 10px;
  border-radius: 6px;
  background: rgba(30, 41, 59, 0.6);
  font-size: 12px;
}

.req { color: #f87171; }

.form-input {
  background: rgba(30, 41, 59, 0.8);
  border: 1px solid rgba(255, 255, 255, 0.12);
  color: #f8fafc;
  padding: 8px 12px;
  border-radius: 6px;
  font-size: 13px;
  outline: none;
}

.form-input:focus { border-color: #38bdf8; }

.form-section {
  display: flex;
  flex-direction: column;
  gap: 8px;
  border-top: 1px dashed rgba(255, 255, 255, 0.1);
  padding-top: 12px;
}

.grid-form-row {
  display: grid;
  grid-template-columns: 2fr 1.5fr 1.5fr 1.5fr;
  gap: 8px;
}

.modal-footer {
  display: flex;
  align-items: center;
  justify-content: flex-end;
  gap: 12px;
  padding: 16px 20px;
  border-top: 1px solid rgba(255, 255, 255, 0.08);
  background: rgba(0, 0, 0, 0.2);
}

.btn-close {
  background: none;
  border: none;
  color: #94a3b8;
  font-size: 16px;
  cursor: pointer;
}
</style>
