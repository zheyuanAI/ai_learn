<template>
  <div class="manufacturing-view-container">
    <!-- 统一页面头部 -->
    <PageHeader
      title="生产工单 (Work Order) 管理"
      tag="MES / WORK ORDER"
      description="生产工单承接销售与制造意图，锁定 BOM 与 Routing 版本，记录下达派工、领退料、报工质检与成品入库全流程。"
    >
      <template #actions>
        <button v-if="hasPermission('mes:workorder:create')" type="button" class="btn btn-primary" @click="openCreateModal">
          <span class="btn-icon">＋</span>
          <span>新建生产工单</span>
        </button>
      </template>
    </PageHeader>

    <!-- 统一筛选栏 -->
    <FilterBar
      v-model="queryParams.keyword"
      placeholder="搜索工单号或产品名称/编码..."
      @search="handleSearch"
      @reset="handleReset"
    >
      <div class="filter-select-group">
        <label class="filter-label">状态：</label>
        <select v-model="queryParams.status" class="filter-select" @change="handleSearch">
          <option value="">全部状态</option>
          <option value="Draft">草稿 (Draft)</option>
          <option value="PendingApproval">待审核 (PendingApproval)</option>
          <option value="Released">已下达 (Released)</option>
          <option value="InProgress">生产中 (InProgress)</option>
          <option value="Completed">已完成 (Completed)</option>
          <option value="Rejected">已驳回 (Rejected)</option>
        </select>
      </div>
    </FilterBar>

    <!-- 错误异常提示 -->
    <ErrorState
      v-if="viewState === 'error'"
      title="工单数据加载失败"
      :message="errorMessage"
      @retry="fetchWorkOrders"
    />

    <!-- 数据表格 -->
    <DataTable
      v-else
      :columns="columns"
      :data="workOrderList"
      :loading="viewState === 'loading'"
      :page="queryParams.page"
      :size="queryParams.size"
      :total="total"
      empty-text="暂无匹配的生产工单记录"
      @page-change="handlePageChange"
    >
      <!-- 工单号 -->
      <template #workOrderNo="{ row }">
        <div class="wo-no-cell">
          <span class="font-mono highlight-code">{{ row.workOrderNo }}</span>
          <span v-if="row.sourceSalesOrderNo" class="source-tag font-mono">
            来源: {{ row.sourceSalesOrderNo }}
          </span>
        </div>
      </template>

      <!-- 产品 -->
      <template #productId="{ row }">
        <div class="product-cell">
          <span class="product-name">{{ row.productName || "未知产品" }}</span>
          <span class="product-spec text-muted">{{ row.productSpec || row.productCode }}</span>
        </div>
      </template>

      <!-- 计划数量 -->
      <template #plannedQty="{ row }">
        <QuantityText :value="row.plannedQty" unit="件" />
      </template>

      <!-- 状态 -->
      <template #status="{ row }">
        <StatusBadge
          :type="getStatusBadgeType(row.status)"
          :text="getStatusText(row.status)"
          :pulsing="row.status === 'InProgress'"
        />
      </template>

      <!-- 生产与入库进度 -->
      <template #progress="{ row }">
        <div class="progress-cell">
          <div class="progress-row">
            <span class="progress-label">报工:</span>
            <QuantityText :value="row.reportedQty" unit="件" />
            <span class="progress-sep">/</span>
            <span class="progress-label">入库:</span>
            <QuantityText :value="row.receivedQty" unit="件" />
          </div>
          <div v-if="row.defectQty && row.defectQty !== '0.00' && row.defectQty !== '0'" class="defect-row">
            <span class="defect-label">不良:</span>
            <QuantityText :value="row.defectQty" unit="件" class="text-danger" />
          </div>
        </div>
      </template>

      <!-- 计划时间 -->
      <template #plannedFinishTime="{ row }">
        <div class="time-cell font-mono text-muted">
          <span>{{ row.plannedFinishTime }}</span>
        </div>
      </template>

      <!-- 操作入口列 (受 allowedActions 控制) -->
      <template #actions="{ row }">
        <div class="action-btn-group">
          <!-- 详情 -->
          <button type="button" class="btn-text" @click="viewDetail(row)">
            详情
          </button>

          <!-- 提交审核 (Draft / Rejected) -->
          <button
            v-if="row.status === 'Draft' || row.status === 'Rejected'"
            type="button"
            class="btn-text text-primary"
            :disabled="!isActionAllowed(row, 'submit')"
            :title="getActionDisabledReason(row, 'submit') || '提交审批'"
            @click="promptSubmit(row)"
          >
            提交
          </button>

          <!-- 审核批准与驳回 (PendingApproval) -->
          <template v-if="row.status === 'PendingApproval'">
            <button
              type="button"
              class="btn-text text-success"
              :disabled="!isActionAllowed(row, 'approve')"
              :title="getActionDisabledReason(row, 'approve') || '审核通过并下达'"
              @click="promptApprove(row)"
            >
              批准
            </button>
            <button
              type="button"
              class="btn-text text-warning"
              :disabled="!isActionAllowed(row, 'reject')"
              :title="getActionDisabledReason(row, 'reject') || '驳回工单'"
              @click="openRejectModal(row)"
            >
              驳回
            </button>
          </template>

          <!-- 完工 (InProgress) -->
          <button
            v-if="row.status === 'InProgress'"
            type="button"
            class="btn-text text-success"
            :disabled="!isActionAllowed(row, 'complete')"
            :title="getActionDisabledReason(row, 'complete') || '正常完工'"
            @click="promptComplete(row)"
          >
            完工
          </button>

          <!-- 强制结案 (Released / InProgress) -->
          <button
            v-if="row.status === 'Released' || row.status === 'InProgress'"
            type="button"
            class="btn-text text-muted"
            :disabled="!isActionAllowed(row, 'manualComplete')"
            :title="getActionDisabledReason(row, 'manualComplete') || '强制人工结案'"
            @click="openManualCompleteModal(row)"
          >
            结案
          </button>
        </div>
      </template>
    </DataTable>

    <!-- 新建工单对话框 -->
    <div v-if="createModalVisible" class="modal-mask" @click.self="createModalVisible = false">
      <div class="modal-card modal-large">
        <div class="modal-header">
          <h3 class="modal-title">新建生产工单 (Work Order)</h3>
          <button type="button" class="btn-close" @click="createModalVisible = false">✕</button>
        </div>

        <form class="modal-body" @submit.prevent="submitCreateWorkOrder">
          <div class="options-search-row">
            <label for="work-order-option-keyword">目录搜索</label>
            <input
              id="work-order-option-keyword"
              v-model="createOptionsKeyword"
              type="search"
              class="form-input"
              placeholder="输入产品、BOM 或工艺路线编码后回车搜索"
              @keyup.enter="loadCreateOptions"
            />
            <button type="button" class="btn-text text-primary" @click="loadCreateOptions">搜索</button>
          </div>
          <div v-if="createOptionsLoading" class="options-hint text-muted">⏳ 正在加载真实产品、BOM 和工艺路线目录...</div>
          <div v-else-if="createOptionsError" class="options-hint text-warning">⚠️ {{ createOptionsError }}</div>
          <div class="form-grid two-col">
            <div class="form-item">
              <label>产出产品 <span class="req">*</span></label>
              <select v-model="createForm.productId" class="form-input" required>
                <option value="">请选择真实产品</option>
                <option v-for="product in products" :key="product.id" :value="String(product.id)">{{ product.sku }} ({{ product.name }})</option>
              </select>
            </div>
            <div class="form-item">
              <label>计划生产数量 <span class="req">*</span></label>
              <input
                v-model="createForm.plannedQty"
                type="text"
                class="form-input font-mono"
                placeholder="例如 100.00"
                required
              />
            </div>
          </div>

          <div class="form-grid two-col">
            <div class="form-item">
              <label>计划开工时间 <span class="req">*</span></label>
              <input
                v-model="createForm.plannedStartTime"
                type="datetime-local"
                class="form-input"
                required
              />
              <small class="form-hint">页面选择日期和时间，提交时自动转换为服务端时间格式。</small>
            </div>
            <div class="form-item">
              <label>计划完工时间 <span class="req">*</span></label>
              <input
                v-model="createForm.plannedFinishTime"
                type="datetime-local"
                class="form-input"
                required
              />
              <small class="form-hint">页面选择日期和时间，提交时自动转换为服务端时间格式。</small>
            </div>
          </div>

          <div class="form-grid two-col">
            <div class="form-item">
              <label>关联 BOM <span class="req">*</span></label>
              <select v-model="createForm.bomId" class="form-input" required>
                <option value="">请选择真实 BOM</option>
                <option v-for="bom in boms" :key="bom.id" :value="String(bom.id)">{{ bom.bomCode }} / {{ bom.version }}</option>
              </select>
            </div>
            <div class="form-item">
              <label>关联工艺路线 <span class="req">*</span></label>
              <select v-model="createForm.routingId" class="form-input" required>
                <option value="">请选择真实工艺路线</option>
                <option v-for="routing in routings" :key="routing.id" :value="String(routing.id)">{{ routing.routingCode }} / {{ routing.version }}</option>
              </select>
            </div>
          </div>

          <div class="form-item">
            <label>来源销售订单行 ID (可选，仅用于追溯)</label>
            <input
              v-model="createForm.sourceSalesOrderLineId"
              type="text"
              class="form-input font-mono"
              placeholder="真实销售订单行 UUID"
            />
          </div>

          <div class="modal-footer">
            <button type="button" class="btn btn-secondary" @click="createModalVisible = false">取消</button>
            <button type="submit" class="btn btn-primary" :disabled="isSubmitting">
              {{ isSubmitting ? "创建中..." : "确认创建工单 (Draft)" }}
            </button>
          </div>
        </form>
      </div>
    </div>

    <!-- 审核驳回原因弹窗 -->
    <div v-if="rejectModalVisible" class="modal-mask" @click.self="rejectModalVisible = false">
      <div class="modal-card">
        <div class="modal-header">
          <h3 class="modal-title">驳回工单审核确认</h3>
          <button type="button" class="btn-close" @click="rejectModalVisible = false">✕</button>
        </div>
        <form class="modal-body" @submit.prevent="handleConfirmReject">
          <p class="modal-hint">
            正在审核驳回工单 <strong>{{ activeWo?.workOrderNo }}</strong>。必须填写驳回原因：
          </p>
          <div class="form-item">
            <label>驳回原因说明 <span class="req">*</span></label>
            <textarea
              v-model="rejectionReason"
              class="form-input form-textarea"
              rows="3"
              placeholder="请详述退回原因，如物料缺料、工时计划冲突等..."
              required
            ></textarea>
          </div>
          <div class="modal-footer">
            <button type="button" class="btn btn-secondary" @click="rejectModalVisible = false">取消</button>
            <button type="submit" class="btn btn-warning" :disabled="isSubmitting">
              {{ isSubmitting ? "处理中..." : "确认退回驳回" }}
            </button>
          </div>
        </form>
      </div>
    </div>

    <!-- 人工强制结案原因弹窗 -->
    <div v-if="manualCompleteModalVisible" class="modal-mask" @click.self="manualCompleteModalVisible = false">
      <div class="modal-card">
        <div class="modal-header">
          <h3 class="modal-title">工单强制手动结案确认</h3>
          <button type="button" class="btn-close" @click="manualCompleteModalVisible = false">✕</button>
        </div>
        <form class="modal-body" @submit.prevent="handleConfirmManualComplete">
          <p class="modal-hint">
            注意：提前强制结案工单 <strong>{{ activeWo?.workOrderNo }}</strong> 将终止剩余生产，不补造任何报工或库存流水！
          </p>
          <div class="form-item">
            <label>强制完工原因 <span class="req">*</span></label>
            <textarea
              v-model="manualCompleteReason"
              class="form-input form-textarea"
              rows="3"
              placeholder="请填写提前截单/结案原因..."
              required
            ></textarea>
          </div>
          <div class="modal-footer">
            <button type="button" class="btn btn-secondary" @click="manualCompleteModalVisible = false">取消</button>
            <button type="submit" class="btn btn-danger" :disabled="isSubmitting">
              {{ isSubmitting ? "提交中..." : "确认强制结案" }}
            </button>
          </div>
        </form>
      </div>
    </div>

    <!-- 二次确认对话框 (提交/审核批准/正常完工) -->
    <ConfirmDialog
      v-model:visible="actionConfirm.visible"
      :title="actionConfirm.title"
      :message="actionConfirm.message"
      :danger="actionConfirm.danger"
      :loading="actionConfirm.loading"
      @confirm="handleExecuteActionConfirm"
    />
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted } from "vue";
import { useRouter } from "vue-router";
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
import type { ViewState, BadgeType } from "../../types/common";
import type {
  WorkOrderItem,
  WorkOrderCreateRequest,
  WorkOrderStatus,
  BomItem,
  RoutingItem,
} from "../../types/manufacturing";
import type { Product } from "../../types/inventory";
import {
  getWorkOrders,
  createWorkOrder,
  submitWorkOrder,
  approveWorkOrder,
  rejectWorkOrder,
  completeWorkOrder,
  manualCompleteWorkOrder,
  getBoms,
  getRoutings,
} from "../../api/manufacturing";
import { getProducts } from "../../api/masterData";
import { usePermission } from "../../composables/usePermission";

const emit = defineEmits<{
  (e: "select-detail", item: WorkOrderItem): void;
}>();

const router = useRouter();
const { hasPermission } = usePermission();

const viewState = ref<ViewState>("loading");
const errorMessage = ref("");

const workOrderList = ref<WorkOrderItem[]>([]);
const total = ref(0);
const products = ref<Product[]>([]);
const boms = ref<BomItem[]>([]);
const routings = ref<RoutingItem[]>([]);
const createOptionsKeyword = ref("");
const createOptionsLoading = ref(false);
const createOptionsError = ref("");
const queryParams = reactive({
  page: 1,
  size: 10,
  keyword: "",
  status: "" as WorkOrderStatus | "",
});

const columns: TableColumn[] = [
  { key: "workOrderNo", label: "工单编号 / 来源", width: "220px" },
  { key: "productId", label: "目标产出品", minWidth: "200px" },
  { key: "plannedQty", label: "计划生产数", width: "120px", align: "right" },
  { key: "status", label: "工单状态", width: "120px", align: "center" },
  { key: "progress", label: "报工 / 入库进度", width: "160px", align: "center" },
  { key: "plannedFinishTime", label: "计划完工时间", width: "150px" },
  { key: "actions", label: "操作", width: "180px", align: "center" },
];

const createModalVisible = ref(false);
const isSubmitting = ref(false);
const createForm = reactive<WorkOrderCreateRequest>({
  productId: "",
  plannedQty: "",
  plannedStartTime: "",
  plannedFinishTime: "",
  bomId: "",
  routingId: "",
  sourceSalesOrderLineId: "",
});

const activeWo = ref<WorkOrderItem | null>(null);
const rejectModalVisible = ref(false);
const rejectionReason = ref("");

const manualCompleteModalVisible = ref(false);
const manualCompleteReason = ref("");

const actionConfirm = reactive({
  visible: false,
  loading: false,
  title: "",
  message: "",
  danger: false,
  actionType: "" as "submit" | "approve" | "complete",
  targetWo: null as WorkOrderItem | null,
});

function getStatusBadgeType(status: WorkOrderStatus): BadgeType {
  switch (status) {
    case "Draft": return "default";
    case "PendingApproval": return "warning";
    case "Released": return "info";
    case "InProgress": return "primary";
    case "Completed": return "success";
    case "Rejected": return "danger";
    default: return "default";
  }
}

function getStatusText(status: WorkOrderStatus): string {
  switch (status) {
    case "Draft": return "未提交草稿";
    case "PendingApproval": return "待审批";
    case "Released": return "已下达排产";
    case "InProgress": return "生产执行中";
    case "Completed": return "已完工结案";
    case "Rejected": return "审批被退回";
    default: return status;
  }
}

// 使用 actionGuard 统一权限判断逻辑
function isActionAllowed(item: { allowedActions?: AllowedAction[] | null }, action: string): boolean {
  return checkAction(item.allowedActions, action);
}

// 使用 actionGuard 统一获取禁用原因逻辑
function getActionDisabledReason(item: { allowedActions?: AllowedAction[] | null }, action: string): string | undefined {
  return getDisabledReason(item.allowedActions, action);
}

async function fetchWorkOrders() {
  viewState.value = "loading";
  errorMessage.value = "";
  try {
    const res = await getWorkOrders({
      page: queryParams.page,
      size: queryParams.size,
      keyword: queryParams.keyword.trim() || undefined,
      status: queryParams.status || undefined,
    });
    if (res.data) {
      // 修改用途：后端列表返回扁平生命周期视图；产品名称仍从已加载的真实产品目录补齐，避免页面显示 UUID。
      workOrderList.value = (res.data.records || []).map(normalizeWorkOrder);
      total.value = res.data.total || 0;
      viewState.value = workOrderList.value.length === 0 ? "empty" : "ready";
    }
  } catch (err: any) {
    errorMessage.value = err.message || "请求工单列表失败";
    viewState.value = "error";
  }
}

function handleSearch() {
  queryParams.page = 1;
  fetchWorkOrders();
}

function handleReset() {
  queryParams.keyword = "";
  queryParams.status = "";
  queryParams.page = 1;
  fetchWorkOrders();
}

function handlePageChange(page: number) {
  queryParams.page = page;
  fetchWorkOrders();
}

function viewDetail(item: WorkOrderItem) {
  // 该页面是正式路由宿主，不依赖未被监听的组件事件来打开详情。
  router.push(`/mes/work-orders/${encodeURIComponent(String(item.id))}`);
  emit("select-detail", item);
}

function openCreateModal() {
  createForm.productId = "";
  createForm.plannedQty = "";
  createForm.plannedStartTime = "";
  createForm.plannedFinishTime = "";
  createForm.bomId = "";
  createForm.routingId = "";
  createForm.sourceSalesOrderLineId = "";
  createModalVisible.value = true;
}

/**
 * 将 datetime-local 的本地日期时间转换为后端 OffsetDateTime 可解析的 ISO 字符串。
 * 入参：页面控件返回的本地日期时间字符串；出参：带时区偏移的 ISO-8601 字符串。
 * 流程：浏览器按当前本地时区解析，再统一序列化为 UTC，避免把普通文本直接交给 Jackson 解析。
 */
function toIsoDateTime(value: string): string {
  const parsed = new Date(value);
  if (Number.isNaN(parsed.getTime())) {
    throw new Error("计划时间格式无效，请使用日期时间选择器重新选择");
  }
  return parsed.toISOString();
}

async function submitCreateWorkOrder() {
  if (!createForm.productId || !products.value.some((item) => String(item.id) === String(createForm.productId))
    || !createForm.plannedQty || !createForm.bomId
    || !boms.value.some((item) => String(item.id) === String(createForm.bomId))
    || !createForm.routingId
    || !routings.value.some((item) => String(item.id) === String(createForm.routingId))) return;
  isSubmitting.value = true;
  try {
    // 修改用途：页面展示使用 datetime-local，接口提交使用 OffsetDateTime 能解析的 ISO-8601。
    const payload: WorkOrderCreateRequest = {
      ...createForm,
      plannedStartTime: toIsoDateTime(createForm.plannedStartTime),
      plannedFinishTime: toIsoDateTime(createForm.plannedFinishTime),
    };
    if (new Date(payload.plannedFinishTime).getTime() <= new Date(payload.plannedStartTime).getTime()) {
      throw new Error("计划完工时间必须晚于计划开工时间");
    }
    await createWorkOrder(payload);
    createModalVisible.value = false;
    await fetchWorkOrders();
  } catch (err: any) {
    alert(`创建工单失败：${err.message}`);
  } finally {
    isSubmitting.value = false;
  }
}

function promptSubmit(item: WorkOrderItem) {
  actionConfirm.title = "提交工单审批确认";
  actionConfirm.message = `确认提交工单【${item.workOrderNo}】进入待审核状态吗？`;
  actionConfirm.danger = false;
  actionConfirm.actionType = "submit";
  actionConfirm.targetWo = item;
  actionConfirm.visible = true;
}

function promptApprove(item: WorkOrderItem) {
  actionConfirm.title = "工单审核通过确认";
  actionConfirm.message = `审核通过工单【${item.workOrderNo}】并将状态推进至【已下达】？生效 BOM 与 Routing 版本将被正式锁定。`;
  actionConfirm.danger = false;
  actionConfirm.actionType = "approve";
  actionConfirm.targetWo = item;
  actionConfirm.visible = true;
}

function promptComplete(item: WorkOrderItem) {
  actionConfirm.title = "工单正常完工确认";
  actionConfirm.message = `确认工单【${item.workOrderNo}】已完成全部工序报工与质检并标记为正常完工？`;
  actionConfirm.danger = false;
  actionConfirm.actionType = "complete";
  actionConfirm.targetWo = item;
  actionConfirm.visible = true;
}

async function handleExecuteActionConfirm() {
  if (!actionConfirm.targetWo) return;
  actionConfirm.loading = true;
  try {
    if (actionConfirm.actionType === "submit") {
      await submitWorkOrder(actionConfirm.targetWo.id as string);
    } else if (actionConfirm.actionType === "approve") {
      await approveWorkOrder(actionConfirm.targetWo.id as string);
    } else if (actionConfirm.actionType === "complete") {
      await completeWorkOrder(actionConfirm.targetWo.id as string);
    }
    actionConfirm.visible = false;
    await fetchWorkOrders();
  } catch (err: any) {
    alert(`操作失败：${err.message}`);
  } finally {
    actionConfirm.loading = false;
  }
}

function openRejectModal(item: WorkOrderItem) {
  activeWo.value = item;
  rejectionReason.value = "";
  rejectModalVisible.value = true;
}

async function handleConfirmReject() {
  if (!activeWo.value || !rejectionReason.value.trim()) return;
  isSubmitting.value = true;
  try {
    await rejectWorkOrder(activeWo.value.id as string, rejectionReason.value.trim());
    rejectModalVisible.value = false;
    await fetchWorkOrders();
  } catch (err: any) {
    alert(`退回失败：${err.message}`);
  } finally {
    isSubmitting.value = false;
  }
}

function openManualCompleteModal(item: WorkOrderItem) {
  activeWo.value = item;
  manualCompleteReason.value = "";
  manualCompleteModalVisible.value = true;
}

async function handleConfirmManualComplete() {
  if (!activeWo.value || !manualCompleteReason.value.trim()) return;
  isSubmitting.value = true;
  try {
    await manualCompleteWorkOrder(activeWo.value.id as string, manualCompleteReason.value.trim());
    manualCompleteModalVisible.value = false;
    await fetchWorkOrders();
  } catch (err: any) {
    alert(`结案失败：${err.message}`);
  } finally {
    isSubmitting.value = false;
  }
}

onMounted(async () => {
  // 修改用途：先加载真实产品目录，再渲染工单列表，保证产品列和新建工单下拉框使用同一份选项。
  await loadCreateOptions();
  await fetchWorkOrders();
});

/** 加载工单创建所需的真实产品、BOM 与工艺路线 UUID；每次只取服务端小页并支持关键词搜索。 */
async function loadCreateOptions() {
  createOptionsLoading.value = true;
  createOptionsError.value = "";
  try {
    const keyword = createOptionsKeyword.value.trim() || undefined;
    const [productRes, bomRes, routingRes] = await Promise.all([
      getProducts({ page: 1, size: 1000, keyword, status: "ACTIVE" }),
      getBoms({ page: 1, size: 1000, keyword, status: "ACTIVE" }),
      getRoutings({ page: 1, size: 1000, keyword, status: "ACTIVE" }),
    ]);
    products.value = productRes.data.records || [];
    boms.value = bomRes.data.records || [];
    routings.value = routingRes.data.records || [];
    // 搜索结果变化后清理不再属于当前真实目录的选择值，禁止提交失效 UUID。
    if (createForm.productId && !products.value.some((item) => String(item.id) === String(createForm.productId))) createForm.productId = "";
    if (createForm.bomId && !boms.value.some((item) => String(item.id) === String(createForm.bomId))) createForm.bomId = "";
    if (createForm.routingId && !routings.value.some((item) => String(item.id) === String(createForm.routingId))) createForm.routingId = "";
  } catch (err: any) {
    createOptionsError.value = err?.message || "加载工单创建主数据失败";
  } finally {
    createOptionsLoading.value = false;
  }
}

/**
 * 将工单视图中的产品 UUID 补齐为当前租户产品目录中的名称、编码和规格。
 * 入参：后端工单查询视图；出参：可直接展示的工单行；流程：只读匹配已授权加载的产品目录，不对后端状态和动作做前端推断。
 */
function normalizeWorkOrder(item: WorkOrderItem): WorkOrderItem {
  const product = products.value.find((candidate) => String(candidate.id) === String(item.productId));
  return {
    ...item,
    productName: item.productName || product?.name,
    productCode: item.productCode || product?.sku,
    productSpec: item.productSpec || product?.spec,
  };
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

.wo-no-cell {
  display: flex;
  flex-direction: column;
  gap: 3px;
}

.source-tag {
  font-size: 11px;
  color: #94a3b8;
  background: rgba(148, 163, 184, 0.12);
  padding: 1px 6px;
  border-radius: 4px;
  align-self: flex-start;
}

.product-cell {
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.product-name {
  font-weight: 500;
  color: #f1f5f9;
}

.product-spec {
  font-size: 11px;
}

.progress-cell {
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.progress-row {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 4px;
  font-size: 12px;
}

.progress-label {
  color: #94a3b8;
}

.progress-sep {
  color: #64748b;
  margin: 0 2px;
}

.defect-row {
  font-size: 11px;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 4px;
}

.defect-label {
  color: #f87171;
}

.time-cell {
  font-size: 12px;
}

.action-btn-group {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  flex-wrap: wrap;
}

.btn-text {
  background: none;
  border: none;
  color: #38bdf8;
  font-size: 12px;
  cursor: pointer;
  padding: 2px 4px;
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
.text-success { color: #34d399 !important; }
.text-warning { color: #fbbf24 !important; }
.text-danger { color: #f87171 !important; }

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

.modal-large {
  max-width: 680px;
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
  gap: 16px;
  max-height: 75vh;
  overflow-y: auto;
}

.modal-hint {
  margin: 0;
  font-size: 13px;
  color: #cbd5e1;
  line-height: 1.5;
}

.options-search-row {
  display: flex;
  align-items: center;
  gap: 8px;
}

.options-search-row label {
  flex: 0 0 auto;
  font-size: 12px;
  color: #94a3b8;
}

.options-search-row .form-input {
  flex: 1;
}

.options-hint {
  font-size: 12px;
}

.form-grid.two-col {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 12px;
}

.form-item {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.form-item label {
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
  padding: 8px 12px;
  border-radius: 6px;
  font-size: 13px;
  outline: none;
}

.form-input:focus {
  border-color: #38bdf8;
}

.form-textarea {
  resize: vertical;
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
