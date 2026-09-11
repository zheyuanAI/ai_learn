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
        <el-button
          v-if="activeTab === 'issue'"
          type="primary"
          :icon="Plus"
          @click="openCreateIssueModal"
        >
          新建领料申请单
        </el-button>
        <el-button
          v-else
          type="primary"
          :icon="Plus"
          @click="openCreateReturnModal"
        >
          新建生产退料单
        </el-button>
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
        <el-select
          v-model="statusFilter"
          placeholder="全部状态"
          clearable
          style="width: 170px"
          @change="handleSearch"
        >
          <el-option label="全部状态" value="" />
          <el-option label="草稿待确认 (Draft)" value="Draft" />
          <el-option label="已出入库确认 (Confirmed)" value="Confirmed" />
        </el-select>
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
          <el-button
            v-if="row.status === 'Draft'"
            link
            type="primary"
            :disabled="!isActionAllowed(row, 'confirm')"
            :title="getActionDisabledReason(row, 'confirm') || '确认领料出库'"
            @click="promptConfirmIssue(row)"
          >
            出库确认
          </el-button>
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
          <el-button
            v-if="row.status === 'Draft'"
            link
            type="primary"
            :disabled="!isActionAllowed(row, 'confirm')"
            :title="getActionDisabledReason(row, 'confirm') || '确认退料入库'"
            @click="promptConfirmReturn(row)"
          >
            退库确认
          </el-button>
          <span v-else class="text-muted font-xs">已完成退入</span>
        </div>
      </template>
    </DataTable>

    <!-- 弹窗 1：新建领料单 -->
    <el-dialog
      v-model="createIssueModalVisible"
      title="新建生产领料申请单"
      width="680px"
      append-to-body
      destroy-on-close
    >
      <div class="dialog-content-wrapper">
        <div class="options-search-row">
          <label for="issue-option-keyword">目录搜索</label>
          <el-input
            id="issue-option-keyword"
            v-model="movementOptionsKeyword"
            clearable
            placeholder="输入工单、物料或仓库编码/名称后回车搜索"
            @keyup.enter="loadMovementOptions"
          >
            <template #append>
              <el-button :icon="Search" @click="loadMovementOptions">搜索</el-button>
            </template>
          </el-input>
        </div>
        <div v-if="isMovementOptionsLoading" class="options-hint text-muted">⏳ 正在加载真实主数据目录...</div>
        <div v-else-if="movementOptionsError" class="options-hint text-warning">⚠️ {{ movementOptionsError }}</div>

        <el-form label-position="top" class="custom-el-form">
          <el-form-item label="关联生产工单" required>
            <el-select
              v-model="issueForm.workOrderId"
              placeholder="请选择关联生产工单"
              filterable
              style="width: 100%"
            >
              <el-option
                v-for="wo in availableWorkOrders"
                :key="wo.id"
                :label="`${wo.workOrderNo || wo.woNo} - ${wo.productName || '工单'} (计划: ${wo.plannedQty}件, ${wo.status})`"
                :value="String(wo.id)"
              />
            </el-select>
          </el-form-item>

          <div class="form-section">
            <div class="section-title">领料明细项（消除手填 UUID，级联选择）</div>
            <el-row :gutter="16">
              <el-col :span="12">
                <el-form-item label="领用物料" required>
                  <el-select
                    v-model="issueForm.productId"
                    placeholder="请选择领用物料"
                    filterable
                    style="width: 100%"
                  >
                    <el-option
                      v-for="p in availableProducts"
                      :key="p.id"
                      :label="`${p.sku} - ${p.name} (${p.uom})`"
                      :value="String(p.id)"
                    />
                  </el-select>
                </el-form-item>
              </el-col>
              <el-col :span="12">
                <el-form-item label="领料数量" required>
                  <el-input
                    v-model="issueForm.issueQty"
                    type="number"
                    min="0.01"
                    step="0.01"
                    placeholder="领料数量 (如 100.00)"
                  />
                </el-form-item>
              </el-col>
            </el-row>

            <el-row :gutter="16">
              <el-col :span="12">
                <el-form-item label="出库仓库" required>
                  <el-select
                    v-model="issueForm.warehouseId"
                    placeholder="请选择出库仓库"
                    filterable
                    style="width: 100%"
                    @change="handleIssueWarehouseChange"
                  >
                    <el-option
                      v-for="w in availableWarehouses"
                      :key="w.id"
                      :label="`${w.code} - ${w.name}`"
                      :value="String(w.id)"
                    />
                  </el-select>
                </el-form-item>
              </el-col>
              <el-col :span="12">
                <el-form-item label="出库库位" required>
                  <el-select
                    v-model="issueForm.locationId"
                    :placeholder="!issueForm.warehouseId ? '请先选择出库仓库' : '请选择库位'"
                    :disabled="!issueForm.warehouseId"
                    filterable
                    style="width: 100%"
                  >
                    <el-option
                      v-for="l in filteredIssueLocations"
                      :key="l.id"
                      :label="`${l.code} - ${l.name} (${l.type})`"
                      :value="String(l.id)"
                    />
                  </el-select>
                </el-form-item>
              </el-col>
            </el-row>

            <el-form-item label="超领原因 (可选，超出定额时必填说明)">
              <el-input
                v-model="issueForm.overageReason"
                placeholder="如: 原料损耗补料、工艺调整追加领料..."
              />
            </el-form-item>
          </div>
        </el-form>
      </div>

      <template #footer>
        <span class="dialog-footer">
          <el-button @click="createIssueModalVisible = false">取消</el-button>
          <el-button
            type="primary"
            :loading="isSubmitting"
            @click="submitCreateIssue"
          >
            保存领料单 (草稿)
          </el-button>
        </span>
      </template>
    </el-dialog>

    <!-- 弹窗 2：新建退料单 -->
    <el-dialog
      v-model="createReturnModalVisible"
      title="新建生产退料单"
      width="680px"
      append-to-body
      destroy-on-close
    >
      <div class="dialog-content-wrapper">
        <div class="options-search-row">
          <label for="return-option-keyword">目录搜索</label>
          <el-input
            id="return-option-keyword"
            v-model="movementOptionsKeyword"
            clearable
            placeholder="输入工单、物料或仓库编码/名称后回车搜索"
            @keyup.enter="loadMovementOptions"
          >
            <template #append>
              <el-button :icon="Search" @click="loadMovementOptions">搜索</el-button>
            </template>
          </el-input>
        </div>
        <div v-if="isMovementOptionsLoading" class="options-hint text-muted">⏳ 正在加载真实主数据目录...</div>
        <div v-else-if="movementOptionsError" class="options-hint text-warning">⚠️ {{ movementOptionsError }}</div>

        <el-form label-position="top" class="custom-el-form">
          <el-form-item label="关联生产工单" required>
            <el-select
              v-model="returnForm.workOrderId"
              placeholder="请选择关联生产工单"
              filterable
              style="width: 100%"
            >
              <el-option
                v-for="wo in availableWorkOrders"
                :key="wo.id"
                :label="`${wo.workOrderNo || wo.woNo} - ${wo.productName || '工单'} (计划: ${wo.plannedQty}件, ${wo.status})`"
                :value="String(wo.id)"
              />
            </el-select>
          </el-form-item>

          <div class="form-section">
            <div class="section-title">退料明细项（消除手填 UUID，级联选择）</div>
            <el-row :gutter="16">
              <el-col :span="12">
                <el-form-item label="退回物料" required>
                  <el-select
                    v-model="returnForm.productId"
                    placeholder="请选择退回物料"
                    filterable
                    style="width: 100%"
                  >
                    <el-option
                      v-for="p in availableProducts"
                      :key="p.id"
                      :label="`${p.sku} - ${p.name} (${p.uom})`"
                      :value="String(p.id)"
                    />
                  </el-select>
                </el-form-item>
              </el-col>
              <el-col :span="12">
                <el-form-item label="退料数量" required>
                  <el-input
                    v-model="returnForm.returnQty"
                    type="number"
                    min="0.01"
                    step="0.01"
                    placeholder="退料数量 (如 2.00)"
                  />
                </el-form-item>
              </el-col>
            </el-row>

            <el-row :gutter="16">
              <el-col :span="12">
                <el-form-item label="退入仓库" required>
                  <el-select
                    v-model="returnForm.warehouseId"
                    placeholder="请选择退入仓库"
                    filterable
                    style="width: 100%"
                    @change="handleReturnWarehouseChange"
                  >
                    <el-option
                      v-for="w in availableWarehouses"
                      :key="w.id"
                      :label="`${w.code} - ${w.name}`"
                      :value="String(w.id)"
                    />
                  </el-select>
                </el-form-item>
              </el-col>
              <el-col :span="12">
                <el-form-item label="退入库位" required>
                  <el-select
                    v-model="returnForm.locationId"
                    :placeholder="!returnForm.warehouseId ? '请先选择退入仓库' : '请选择库位'"
                    :disabled="!returnForm.warehouseId"
                    filterable
                    style="width: 100%"
                  >
                    <el-option
                      v-for="l in filteredReturnLocations"
                      :key="l.id"
                      :label="`${l.code} - ${l.name} (${l.type})`"
                      :value="String(l.id)"
                    />
                  </el-select>
                </el-form-item>
              </el-col>
            </el-row>

            <el-form-item label="退料原因" required>
              <el-input
                v-model="returnForm.reason"
                placeholder="如: 工单完工余料退回、来料不良退库..."
              />
            </el-form-item>
          </div>
        </el-form>
      </div>

      <template #footer>
        <span class="dialog-footer">
          <el-button @click="createReturnModalVisible = false">取消</el-button>
          <el-button
            type="primary"
            :loading="isSubmitting"
            @click="submitCreateReturn"
          >
            保存退料单 (草稿)
          </el-button>
        </span>
      </template>
    </el-dialog>

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
import { Plus, Search } from "@element-plus/icons-vue";
import { ElMessage } from "element-plus";
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

/** 检查指定操作是否被后端或状态机允许 */
function isActionAllowed(item: { allowedActions?: AllowedAction[] | null }, action: string): boolean {
  return checkAction(item.allowedActions, action);
}

/** 获取指定操作被禁用的原因提示 */
function getActionDisabledReason(item: { allowedActions?: AllowedAction[] | null }, action: string): string | undefined {
  return getDisabledReason(item.allowedActions, action);
}

/** 加载领料与退料单据列表 */
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

/** 搜索主数据基础选项（工单、物料、仓库、库位） */
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

/** 切换领料/退料视图 Tab */
function switchTab(tab: "issue" | "return") {
  activeTab.value = tab;
}

/** 触发列表搜索 */
function handleSearch() {
  // filtered by computed
}

/** 重置搜索条件 */
function handleReset() {
  keyword.value = "";
  statusFilter.value = "";
}

/** 打开新建领料申请单对话框并初始化选项 */
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

/** 提交创建领料申请单 */
async function submitCreateIssue() {
  if (!issueForm.workOrderId || !issueForm.productId || !issueForm.issueQty || !issueForm.warehouseId || !issueForm.locationId) {
    ElMessage.warning("请填写完整的领料信息");
    return;
  }
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
    ElMessage.success("领料单已成功创建！可在领料单列表中执行出库确认。");
    await loadData();
  } catch (err: any) {
    ElMessage.error(`创建领料单失败：${err.message}`);
  }
}

/** 打开新建生产退料单对话框并初始化选项 */
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

/** 提交创建生产退料单 */
async function submitCreateReturn() {
  if (!returnForm.workOrderId || !returnForm.productId || !returnForm.returnQty || !returnForm.warehouseId || !returnForm.locationId) {
    ElMessage.warning("请填写完整的退料信息");
    return;
  }
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
    ElMessage.success("退料单已成功创建！可在退料单列表中执行退库确认。");
    await loadData();
  } catch (err: any) {
    ElMessage.error(`创建退料单失败：${err.message}`);
  }
}

/** 提示领料出库确认对话框 */
function promptConfirmIssue(item: MaterialIssueItem) {
  confirmDialog.title = "确认生产领料出库";
  confirmDialog.message = `确认领料单【${item.issueNo}】出库？库存服务将真实扣减原料库位库存并生成流水软引用。`;
  confirmDialog.type = "issue";
  confirmDialog.targetId = item.id as string;
  confirmDialog.visible = true;
}

/** 提示退料入库确认对话框 */
function promptConfirmReturn(item: MaterialReturnItem) {
  confirmDialog.title = "确认生产退料入库";
  confirmDialog.message = `确认退料单【${item.returnNo}】入库？将增加目标库位实物库存并生成流水软引用。`;
  confirmDialog.type = "return";
  confirmDialog.targetId = item.id as string;
  confirmDialog.visible = true;
}

/** 执行出库/退库确认指令 */
async function handleExecuteConfirm() {
  confirmDialog.loading = true;
  try {
    if (confirmDialog.type === "issue") {
      await execute((key) => confirmMaterialIssue(confirmDialog.targetId, key), { onConflict: loadData });
    } else {
      await execute((key) => confirmMaterialReturn(confirmDialog.targetId, key), { onConflict: loadData });
    }
    confirmDialog.visible = false;
    ElMessage.success("确认操作已成功完成！");
    await loadData();
  } catch (err: any) {
    ElMessage.error(`确认失败：${err.message}`);
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

.form-section {
  display: flex;
  flex-direction: column;
  gap: 12px;
  border-top: 1px dashed rgba(255, 255, 255, 0.1);
  padding-top: 14px;
  margin-top: 8px;
}

.section-title {
  font-size: 13px;
  color: #38bdf8;
  font-weight: 500;
}

.custom-el-form :deep(.el-form-item__label) {
  color: #94a3b8;
  font-size: 13px;
  padding-bottom: 4px;
}
</style>
