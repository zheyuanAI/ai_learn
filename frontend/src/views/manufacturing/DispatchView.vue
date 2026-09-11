<template>
  <div class="manufacturing-view-container">
    <CommandFeedback :error="lastError" :can-retry="canRetry" :executing="isExecuting" @retry="retry" />
    <!-- 统一页面头部（新建派工单入口严格受 mes:dispatch:manage 权限约束，修复 F03） -->
    <PageHeader
      title="生产派工 (Dispatch) 管理"
      tag="MES / DISPATCH"
      description="将已下达工单中的工序任务、派工数量分配给现场操作员及机台设备。下达派工仅表达安排生效，不代表现场已实际开工。"
    >
      <template #actions>
        <el-button
          v-if="hasPermission('mes:dispatch:manage')"
          type="primary"
          :icon="Plus"
          @click="openCreateModal"
        >
          新建派工单
        </el-button>
      </template>
    </PageHeader>

    <!-- 统一筛选栏 -->
    <FilterBar
      v-model="queryParams.keyword"
      placeholder="搜索派工单号或工单编号..."
      @search="handleSearch"
      @reset="handleReset"
    >
      <el-select
        v-model="queryParams.status"
        placeholder="全部状态"
        clearable
        style="width: 180px"
        @change="handleSearch"
      >
        <el-option label="草稿安排 (Draft)" value="Draft" />
        <el-option label="已下达 (Released)" value="Released" />
        <el-option label="加工中 (Processing)" value="Processing" />
        <el-option label="已完成 (Completed)" value="Completed" />
      </el-select>
    </FilterBar>

    <!-- 错误异常提示 -->
    <ErrorState
      v-if="viewState === 'error'"
      title="派工单列表加载失败"
      :message="errorMessage"
      @retry="fetchDispatchList"
    />

    <!-- 数据表格 -->
    <DataTable
      v-else
      :columns="columns"
      :data="dispatchList"
      :loading="viewState === 'loading'"
      :page="queryParams.page"
      :size="queryParams.size"
      :total="total"
      empty-text="暂无匹配的派工单记录"
      @page-change="handlePageChange"
    >
      <!-- 派工单号 -->
      <template #dispatchNo="{ row }">
        <span class="font-mono highlight-code">{{ row.dispatchNo }}</span>
      </template>

      <!-- 关联工单与产品 -->
      <template #workOrderId="{ row }">
        <div class="wo-cell">
          <span class="font-mono highlight-wo">{{ row.workOrderNo || row.workOrderId }}</span>
          <span class="text-muted product-text">{{ row.productName || "定制产品" }}</span>
        </div>
      </template>

      <!-- 工序步骤 -->
      <template #operationId="{ row }">
        <div class="op-cell">
          <span class="op-title">{{ row.operationName }}</span>
              <span class="op-no font-mono text-muted">工序序号: #{{ row.operationNo ?? "待返回" }}</span>
        </div>
      </template>

      <!-- 责任操作工 -->
      <template #operatorId="{ row }">
        <span class="operator-name">{{ row.operatorName || row.operatorId }}</span>
      </template>

      <!-- 安排设备 -->
      <template #deviceId="{ row }">
        <span v-if="row.deviceName || row.deviceCode" class="font-mono device-text">
          {{ row.deviceName || row.deviceCode }}
        </span>
        <span v-else class="text-muted">人工通用工位</span>
      </template>

      <!-- 派工数量 -->
      <template #dispatchQty="{ row }">
        <QuantityText :value="row.dispatchQty" unit="件" />
      </template>

      <!-- 状态徽标 -->
      <template #status="{ row }">
        <StatusBadge
          :type="row.status === 'Completed' ? 'success' : row.status === 'Processing' ? 'primary' : row.status === 'Released' ? 'info' : 'default'"
          :text="row.status === 'Draft' ? '草稿' : row.status === 'Released' ? '已下达' : row.status === 'Processing' ? '加工中' : '已完成'"
          :pulsing="row.status === 'Processing'"
        />
      </template>

      <!-- 操作列 (受 allowedActions 约束) -->
      <template #actions="{ row }">
        <div style="display: flex; gap: 8px; justify-content: center">
          <!-- 下达派工 (Draft -> Released) -->
          <el-button
            v-if="row.status === 'Draft'"
            type="primary"
            link
            size="small"
            :disabled="!isActionAllowed(row, 'release')"
            :title="getActionDisabledReason(row, 'release') || '下达派工单'"
            @click="promptRelease(row)"
          >
            下达
          </el-button>
          <!-- 前往执行 (Released -> Executions) -->
          <el-button
            v-else-if="row.status === 'Released'"
            type="primary"
            link
            size="small"
            style="font-weight: bold"
            title="前往工序执行页面并开始加工"
            @click="goToExecution(row)"
          >
            前往执行
          </el-button>
          <span v-else class="text-muted" style="font-size: 12px">已执行</span>
        </div>
      </template>
    </DataTable>

    <!-- 新建派工单对话框 -->
    <el-dialog
      v-model="createModalVisible"
      title="新建工序派工安排"
      width="760px"
      destroy-on-close
      append-to-body
    >
      <div v-if="isLoadingOptions" style="margin-bottom: 10px; font-size: 12px; color: #8ca2b8">
        ⏳ 正在拉取已下达工单与设备台账...
      </div>

      <el-form label-width="110px" @submit.prevent="submitCreateDispatch">
        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item label="已下达工单" required>
              <el-select
                v-model="createForm.workOrderId"
                placeholder="请选择已下达工单"
                filterable
                style="width: 100%"
                @change="onWorkOrderChange"
              >
                <el-option
                  v-for="wo in releasedWorkOrders"
                  :key="wo.id"
                  :value="wo.id"
                  :label="`${wo.workOrderNo || wo.woNo} - ${wo.productName || '工单'} (计划: ${wo.plannedQty}件)`"
                />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="指派工序步骤" required>
              <el-select
                v-model="createForm.operationId"
                placeholder="请选择执行工序"
                filterable
                style="width: 100%"
                :disabled="!createForm.workOrderId || isLoadingOperations"
              >
                <el-option
                  v-for="op in availableOperations"
                  :key="op.id"
                  :value="op.id"
                  :label="`#${op.operationNo} - ${op.operationName} (标准工时: ${op.standardTimeMinutes || 0}分)`"
                />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="责任操作工" required>
              <el-select
                v-model="createForm.operatorId"
                placeholder="请选择责任操作工"
                filterable
                style="width: 100%"
                :disabled="!operatorDirectoryAvailable"
              >
                <el-option
                  v-for="operator in operators"
                  :key="operator.id"
                  :value="operator.id"
                  :label="`${operator.userNo || operator.username} - ${operator.realName}`"
                />
              </el-select>
              <div v-if="!operatorDirectoryAvailable" style="font-size: 12px; color: #f59e0b; margin-top: 4px">
                未读取到可用的同租户操作员目录，派工创建暂不可提交。
              </div>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="派工数量" required>
              <el-input
                v-model="createForm.dispatchQty"
                type="number"
                min="0.01"
                step="0.01"
                placeholder="例如 100.00"
              />
            </el-form-item>
          </el-col>
          <el-col :span="24">
            <el-form-item label="指定加工设备">
              <el-select
                v-model="createForm.deviceId"
                placeholder="人工通用工位 (无需专用设备)"
                clearable
                style="width: 100%"
              >
                <el-option value="" label="人工通用工位 (无需专用设备)" />
                <el-option
                  v-for="dev in availableDevices"
                  :key="dev.id"
                  :value="dev.id"
                  :label="`${dev.deviceCode} - ${dev.deviceName} (${dev.status || 'ACTIVE'})`"
                />
              </el-select>
            </el-form-item>
          </el-col>
        </el-row>
      </el-form>

      <template #footer>
        <el-button @click="createModalVisible = false">取消</el-button>
        <el-button
          type="primary"
          :loading="isSubmitting"
          :disabled="!operatorDirectoryAvailable || !createForm.operatorId"
          @click="submitCreateDispatch"
        >
          {{ isSubmitting ? "创建中..." : "保存派工单 (Draft)" }}
        </el-button>
      </template>
    </el-dialog>

    <!-- 下达确认对话框 -->
    <ConfirmDialog
      v-model:visible="releaseConfirm.visible"
      title="下达派工单确认"
      :message="`确定要正式下达派工单【${releaseConfirm.item?.dispatchNo}】吗？下达后允许操作工开始该工序执行。`"
      :loading="releaseConfirm.loading"
      @confirm="handleConfirmRelease"
    />
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted } from "vue";
import { Plus } from "@element-plus/icons-vue";
import { ElMessage } from "element-plus";
import { useCommand } from "@/composables/useCommand";
import CommandFeedback from "@/components/common/CommandFeedback.vue";
import { useRoute, useRouter } from "vue-router";
import { usePermission } from "../../composables/usePermission";
import { isActionAllowed as checkAction, getActionDisabledReason as getDisabledReason } from "../../utils/actionGuard";
import type { AllowedAction } from "../../types/common";

const { hasPermission } = usePermission();
const route = useRoute();
const router = useRouter();
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
  DispatchOrderItem,
  DispatchOrderCreateRequest,
  DispatchOrderStatus,
} from "../../types/manufacturing";
import {
  getDispatchOrders,
  createDispatchOrder,
  releaseDispatchOrder,
  getWorkOrders,
  getRoutingById,
} from "../../api/manufacturing";
import { getDevices } from "../../api/iot";
import { getOperatorDirectory, type OperatorDirectoryItem } from "../../api/auth";
import { getProducts } from "../../api/masterData";

const viewState = ref<ViewState>("loading");
const errorMessage = ref("");

const dispatchList = ref<DispatchOrderItem[]>([]);
const total = ref(0);
const queryParams = reactive({
  page: 1,
  size: 10,
  keyword: "",
  status: "" as DispatchOrderStatus | "",
});

const columns: TableColumn[] = [
  { key: "dispatchNo", label: "派工单号", width: "180px" },
  { key: "workOrderId", label: "关联工单 / 产出品", minWidth: "200px" },
  { key: "operationId", label: "指派工序", minWidth: "180px" },
  { key: "dispatchQty", label: "派工数量", width: "120px", align: "right" },
  { key: "operatorId", label: "责任操作工", width: "120px" },
  { key: "deviceId", label: "分配设备", width: "160px" },
  { key: "status", label: "状态", width: "110px", align: "center" },
  { key: "actions", label: "操作", width: "130px", align: "center" },
];

const createModalVisible = ref(false);
const { execute, retry, isExecuting, canRetry, lastError } = useCommand();
const isSubmitting = isExecuting;
const isLoadingOptions = ref(false);
const isLoadingOperations = ref(false);

const releasedWorkOrders = ref<any[]>([]);
const availableOperations = ref<any[]>([]);
const availableDevices = ref<any[]>([]);
const operators = ref<OperatorDirectoryItem[]>([]);

// 修改用途：派工只消费 Auth 返回的同租户最小操作员目录，不使用固定 UUID 或管理员详情接口伪造人员。
const operatorDirectoryAvailable = ref(false);

const createForm = reactive<DispatchOrderCreateRequest>({
  workOrderId: "",
  operationId: "",
  operatorId: "",
  dispatchQty: "",
  deviceId: "",
});

const releaseConfirm = reactive({
  visible: false,
  loading: false,
  item: null as DispatchOrderItem | null,
});

// 使用 actionGuard 统一权限判断逻辑
function isActionAllowed(item: { allowedActions?: AllowedAction[] | null }, action: string): boolean {
  return checkAction(item.allowedActions, action);
}

// 使用 actionGuard 统一获取禁用原因逻辑
function getActionDisabledReason(item: { allowedActions?: AllowedAction[] | null }, action: string): string | undefined {
  return getDisabledReason(item.allowedActions, action);
}

async function fetchDispatchList() {
  viewState.value = "loading";
  errorMessage.value = "";
  try {
    const res = await getDispatchOrders({
      page: queryParams.page,
      size: queryParams.size,
      status: queryParams.status || undefined,
    });
    if (res.data) {
      const [workOrderResult, deviceResult, operatorResult, productResult] = await Promise.allSettled([
        getWorkOrders({ page: 1, size: 1000 }),
        getDevices({ page: 1, size: 1000 }),
        getOperatorDirectory({ page: 1, size: 1000 }),
        getProducts({ page: 1, size: 1000, status: "ACTIVE" }),
      ]);
      const records = <T>(result: PromiseSettledResult<any>): T[] =>
        result.status === "fulfilled" ? result.value.data?.records || [] : [];
      const workOrders = records<any>(workOrderResult);
      const devices = records<any>(deviceResult);
      const operatorItems = records<any>(operatorResult);
      const products = records<any>(productResult);
      const routingIds = [...new Set(workOrders.map((item) => String(item.routingId || "")).filter(Boolean))];
      const routingResults = await Promise.allSettled(routingIds.map((id) => getRoutingById(id)));
      const routings = routingResults.flatMap((result) =>
        result.status === "fulfilled" && result.value.data ? [result.value.data] : [],
      );
      let list = (res.data.records || []).map((dispatch) => {
        const workOrder = workOrders.find((item) => String(item.id) === String(dispatch.workOrderId));
        const routing = routings.find((item) => String(item.id) === String(workOrder?.routingId));
        const operation = routing?.operations?.find((item: any) => String(item.id) === String(dispatch.operationId));
        const product = products.find((item) => String(item.id) === String(workOrder?.productId));
        const operator = operatorItems.find((item) => String(item.id) === String(dispatch.operatorId));
        const device = devices.find((item) => String(item.id) === String(dispatch.deviceId));
        return {
          ...dispatch,
          workOrderNo: dispatch.workOrderNo || workOrder?.workOrderNo || String(dispatch.workOrderId),
          productName: dispatch.productName || product?.name || String(workOrder?.productId || ""),
          operationNo: dispatch.operationNo || operation?.operationNo,
          operationName: dispatch.operationName || operation?.operationName || String(dispatch.operationId),
          operatorName: dispatch.operatorName || operator?.realName || operator?.username || String(dispatch.operatorId),
          deviceName: dispatch.deviceName || device?.deviceName,
          deviceCode: dispatch.deviceCode || device?.deviceCode,
        };
      });
      if (queryParams.keyword.trim()) {
        const kw = queryParams.keyword.toLowerCase();
        list = list.filter(
          (d: any) =>
            d.dispatchNo.toLowerCase().includes(kw) ||
            d.workOrderNo?.toLowerCase().includes(kw) ||
            d.operationName?.toLowerCase().includes(kw)
        );
      }
      dispatchList.value = list;
      total.value = res.data.total || list.length;
      viewState.value = dispatchList.value.length === 0 ? "empty" : "ready";
    }
  } catch (err: any) {
    errorMessage.value = err.message || "请求派工列表失败";
    viewState.value = "error";
  }
}

function handleSearch() {
  queryParams.page = 1;
  fetchDispatchList();
}

function handleReset() {
  queryParams.keyword = "";
  queryParams.status = "";
  queryParams.page = 1;
  fetchDispatchList();
}

function handlePageChange(page: number) {
  queryParams.page = page;
  fetchDispatchList();
}

async function openCreateModal() {
  createForm.workOrderId = "";
  createForm.operationId = "";
  // 修改用途：重新打开表单时清理旧的人员选择，避免跨工单误带责任操作工。
  createForm.operatorId = "";
  createForm.dispatchQty = "";
  createForm.deviceId = "";
  availableOperations.value = [];
  createModalVisible.value = true;
  await loadDispatchModalOptions();
}

/**
 * 拉取已下达工单与设备台账选项
 */
async function loadDispatchModalOptions() {
  isLoadingOptions.value = true;
  try {
    const [woRes, devRes, operatorRes] = await Promise.allSettled([
      getWorkOrders({ page: 1, size: 1000, status: "Released" }),
      getDevices({ page: 1, size: 1000 }),
      getOperatorDirectory({ page: 1, size: 1000 }),
    ]);

    if (woRes.status === "fulfilled") {
      const records = woRes.value.data?.records || [];
      // 派工只允许选择后端明确返回的 Released 工单，不用其他状态冒充可派工来源。
      releasedWorkOrders.value = records.filter((w: any) => w.status === "Released");
    }

    if (devRes.status === "fulfilled") {
      availableDevices.value = devRes.value.data?.records || [];
    }

    if (operatorRes.status === "fulfilled") {
      operators.value = operatorRes.value.data?.records || [];
      operatorDirectoryAvailable.value = operators.value.length > 0;
    } else {
      operators.value = [];
      operatorDirectoryAvailable.value = false;
    }
  } catch (err) {
    console.error("[DispatchView] 加载派工建单选项失败:", err);
  } finally {
    isLoadingOptions.value = false;
  }
}

/**
 * 当所选工单变动时，自动清空旧工序并拉取该工单锁定 Routing 的有效工序列表
 */
async function onWorkOrderChange() {
  createForm.operationId = "";
  availableOperations.value = [];
  if (!createForm.workOrderId) return;

  const selectedWo = releasedWorkOrders.value.find((w: any) => String(w.id) === String(createForm.workOrderId));
  if (!selectedWo) return;

  // 默认联动填入计划数量
  if (selectedWo.plannedQty) {
    createForm.dispatchQty = String(selectedWo.plannedQty);
  }

  if (!selectedWo.routingId) {
    console.warn("[DispatchView] 该工单未关联有效的工艺路线 (routingId 为空)");
    return;
  }

  isLoadingOperations.value = true;
  try {
    const routingRes = await getRoutingById(selectedWo.routingId);
    if (routingRes.data && routingRes.data.operations) {
      availableOperations.value = routingRes.data.operations;
      if (availableOperations.value.length > 0) {
        createForm.operationId = (availableOperations.value[0].id || "") as string;
      }
    }
  } catch (err) {
    console.error("[DispatchView] 加载工艺路线工序失败:", err);
  } finally {
    isLoadingOperations.value = false;
  }
}

async function submitCreateDispatch() {
  if (!operatorDirectoryAvailable.value) {
    ElMessage.warning("当前没有可用的同租户操作员目录，派工创建已阻止。");
    return;
  }
  if (!createForm.workOrderId || !createForm.operationId || !createForm.operatorId || !createForm.dispatchQty) return;
  try {
    await execute(async (key) => {
      const created = await createDispatchOrder(createForm, key);
      if (!created?.data?.id) {
        throw new Error("服务端未返回 dispatchOrderId，已阻止继续派工");
      }
      createModalVisible.value = false;
      await fetchDispatchList();
    }, { onConflict: fetchDispatchList });
  } catch (err: any) {
    ElMessage.error(`创建派工单失败：${err.message}`);
  } finally { /* useCommand 在 finally 中恢复 isExecuting。 */ }
}

function promptRelease(item: DispatchOrderItem) {
  releaseConfirm.item = item;
  releaseConfirm.visible = true;
}

async function handleConfirmRelease() {
  if (!releaseConfirm.item) return;
  releaseConfirm.loading = true;
  try {
    await execute(async (key) => {
      await releaseDispatchOrder(releaseConfirm.item!.id as string, key);
      releaseConfirm.visible = false;
      await fetchDispatchList();
    }, { onConflict: fetchDispatchList });
  } catch (err: any) {
    ElMessage.error(`下达失败：${err.message}`);
  } finally {
    releaseConfirm.loading = false;
  }
}

function goToExecution(row: DispatchOrderItem) {
  router.push({
    path: "/mes/executions",
    query: {
      dispatchOrderId: String(row.id),
      workOrderId: String(row.workOrderId),
    },
  });
}

onMounted(async () => {
  fetchDispatchList();
  if (route.query.workOrderId) {
    await openCreateModal();
    createForm.workOrderId = String(route.query.workOrderId);
    await onWorkOrderChange();
  }
});
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

.wo-cell, .op-cell {
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.highlight-wo {
  color: #e2e8f0;
  font-weight: 500;
}

.product-text {
  font-size: 11px;
}

.op-title {
  color: #f1f5f9;
}

.op-no {
  font-size: 11px;
}

.operator-name {
  color: #e2e8f0;
}

.device-text {
  color: #38bdf8;
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
  max-width: 540px;
  box-shadow: 0 20px 30px rgba(0, 0, 0, 0.5);
  overflow: hidden;
}

.modal-large { max-width: 620px; }

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

.selector-unavailable {
  border: 1px dashed rgba(248, 113, 113, 0.55);
  border-radius: 6px;
  padding: 8px 10px;
  color: #fca5a5;
  background: rgba(127, 29, 29, 0.18);
  font-size: 12px;
  line-height: 1.5;
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
