<template>
  <div class="manufacturing-view-container">
    <CommandFeedback :error="lastError" :can-retry="canRetry" :executing="isExecuting" @retry="retry" />
    <!-- 统一页面头部 -->
    <PageHeader
      title="工序实际执行 (Operation Execution)"
      tag="MES / SHOP FLOOR"
      description="工序执行记录现场真实的加工动作生命周期。只有在此处触发【开始】执行后，对应工单才正式推进为【生产中】。"
    >
      <template #actions>
        <el-button type="primary" :icon="Plus" @click="openCreateModal">
          发起工序执行
        </el-button>
      </template>
    </PageHeader>

    <!-- 统一筛选栏 -->
    <FilterBar
      v-model="queryParams.keyword"
      placeholder="搜索执行编号、工单号或工序..."
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
        <el-option label="未开始 (NotStarted)" value="NotStarted" />
        <el-option label="正在加工 (Running)" value="Running" />
        <el-option label="已暂停 (Paused)" value="Paused" />
        <el-option label="已完成 (Completed)" value="Completed" />
      </el-select>
    </FilterBar>

    <!-- 错误异常提示 -->
    <ErrorState
      v-if="viewState === 'error'"
      title="工序执行数据加载失败"
      :message="errorMessage"
      @retry="fetchExecutionList"
    />

    <!-- 数据表格 -->
    <DataTable
      v-else
      :columns="columns"
      :data="executionList"
      :loading="viewState === 'loading'"
      :page="queryParams.page"
      :size="queryParams.size"
      :total="total"
      empty-text="暂无现场工序执行实例"
      @page-change="handlePageChange"
    >
      <!-- 执行编号 -->
      <template #executionNo="{ row }">
        <span class="font-mono highlight-code">{{ row.executionNo }}</span>
      </template>

      <!-- 关联单据 -->
      <template #workOrderId="{ row }">
        <div class="relation-cell">
          <span class="font-mono text-primary">{{ row.workOrderNo || row.workOrderId }}</span>
          <span class="font-mono text-muted sub-tag">派工: {{ row.dispatchNo }}</span>
        </div>
      </template>

      <!-- 工序步骤 -->
      <template #operationId="{ row }">
        <div class="op-cell">
          <span class="op-name">{{ row.operationName }}</span>
          <span class="text-muted sub-tag font-mono">操作员: {{ row.operatorName || row.operatorId }}</span>
        </div>
      </template>

      <!-- 机台设备 -->
      <template #deviceId="{ row }">
        <span v-if="row.deviceName || row.deviceCode" class="font-mono device-tag">
          {{ row.deviceName || row.deviceCode }}
        </span>
        <span v-else class="text-muted">纯手工工位</span>
      </template>

      <!-- 执行状态 -->
      <template #status="{ row }">
        <StatusBadge
          :type="row.status === 'Running' ? 'primary' : row.status === 'Completed' ? 'success' : row.status === 'Paused' ? 'warning' : 'default'"
          :text="row.status === 'NotStarted' ? '尚未开工' : row.status === 'Running' ? '运行中' : row.status === 'Paused' ? '已暂停' : '已完工'"
          :pulsing="row.status === 'Running'"
        />
      </template>

      <!-- 累计报工数 -->
      <template #reportedQty="{ row }">
        <QuantityText :value="row.reportedQty || '0.00'" unit="件" />
      </template>

      <!-- 时间记录 -->
      <template #startedAt="{ row }">
        <div class="time-cell font-mono text-muted">
          <div>起: {{ row.startedAt || "-" }}</div>
          <div>止: {{ row.completedAt || "-" }}</div>
        </div>
      </template>

      <!-- 实时动作操作入口 (受 allowedActions 约束) -->
      <template #actions="{ row }">
        <div style="display: flex; gap: 6px; justify-content: center; flex-wrap: wrap">
          <!-- 开始 (NotStarted -> Running) -->
          <el-button
            v-if="row.status === 'NotStarted'"
            type="success"
            link
            size="small"
            :disabled="!isActionAllowed(row, 'start')"
            :title="getActionDisabledReason(row, 'start') || '开始该工序执行'"
            @click="handleStart(row)"
          >
            开始
          </el-button>

          <!-- 暂停 (Running -> Paused) -->
          <el-button
            v-if="row.status === 'Running'"
            type="warning"
            link
            size="small"
            :disabled="!isActionAllowed(row, 'pause')"
            :title="getActionDisabledReason(row, 'pause') || '暂停工序'"
            @click="openPauseModal(row)"
          >
            暂停
          </el-button>

          <!-- 恢复 (Paused -> Running) -->
          <el-button
            v-if="row.status === 'Paused'"
            type="primary"
            link
            size="small"
            :disabled="!isActionAllowed(row, 'resume')"
            :title="getActionDisabledReason(row, 'resume') || '恢复工序执行'"
            @click="handleResume(row)"
          >
            恢复
          </el-button>

          <!-- 完成 (Running -> Completed) -->
          <el-button
            v-if="row.status === 'Running'"
            type="success"
            link
            size="small"
            :disabled="!isActionAllowed(row, 'complete')"
            :title="getActionDisabledReason(row, 'complete') || '工序完工'"
            @click="handleComplete(row)"
          >
            完成
          </el-button>

          <!-- 报工 (仅 Completed，和后端 MES_FACT_001 规则一致) -->
          <el-button
            v-if="row.status === 'Completed'"
            type="primary"
            link
            size="small"
            :disabled="!isActionAllowed(row, 'report')"
            :title="getActionDisabledReason(row, 'report') || '现场报工录入'"
            @click="openReportModal(row)"
          >
            报工
          </el-button>
        </div>
      </template>
    </DataTable>

    <!-- 弹窗 1：创建工序执行实例 -->
    <el-dialog
      v-model="createModalVisible"
      title="创建工序实际执行实例"
      width="560px"
      destroy-on-close
      append-to-body
    >
      <el-form label-width="120px" @submit.prevent="submitCreateExecution">
        <el-form-item label="关联派工单" required>
          <el-select
            v-model="createForm.dispatchOrderId"
            placeholder="请选择已下达派工单"
            filterable
            style="width: 100%"
          >
            <el-option
              v-for="d in releasedDispatches"
              :key="d.id"
              :value="d.id"
              :label="`${d.dispatchNo} - ${d.operationName} (工单: ${d.workOrderNo || d.workOrderId})`"
            />
            <el-option
              v-if="createForm.dispatchOrderId && !releasedDispatches.some(d => String(d.id) === String(createForm.dispatchOrderId))"
              :value="createForm.dispatchOrderId"
              :label="`当前派工单: ${createForm.dispatchOrderId}`"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="执行机台">
          <el-select
            v-model="createForm.deviceId"
            placeholder="请选择设备（可选，留空表示手工工位）"
            filterable
            clearable
            style="width: 100%"
          >
            <el-option
              v-for="device in deviceOptions"
              :key="device.id"
              :value="String(device.id)"
              :label="`${device.deviceName} (${device.deviceCode})`"
            />
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="createModalVisible = false">取消</el-button>
        <el-button type="primary" :loading="isSubmitting" @click="submitCreateExecution">
          {{ isSubmitting ? "创建中..." : "保存执行实例" }}
        </el-button>
      </template>
    </el-dialog>

    <!-- 弹窗 2：暂停原因弹窗 -->
    <el-dialog
      v-model="pauseModalVisible"
      title="工序暂停确认"
      width="520px"
      destroy-on-close
      append-to-body
    >
      <el-form label-width="110px" @submit.prevent="submitPause">
        <el-form-item label="暂停原因" required>
          <el-input
            v-model="pauseReason"
            placeholder="请简要说明暂停原因 (如换料、设备维护、交接班)"
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="pauseModalVisible = false">取消</el-button>
        <el-button type="warning" :loading="isSubmitting" @click="submitPause">确认暂停</el-button>
      </template>
    </el-dialog>

    <!-- 弹窗 3：现场报工录入 -->
    <el-dialog
      v-model="reportModalVisible"
      :title="`工序报工录入 — ${activeExec?.operationName || ''}`"
      width="600px"
      destroy-on-close
      append-to-body
    >
      <el-form label-width="110px" @submit.prevent="submitWorkReport">
        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item label="合格数量" required>
              <el-input v-model="reportForm.qualifiedQty" placeholder="例如 20.00" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="不良数量" required>
              <el-input v-model="reportForm.defectQty" placeholder="例如 0.00" />
            </el-form-item>
          </el-col>
          <el-col :span="24">
            <el-form-item label="报工时间">
              <el-input v-model="reportForm.reportTime" placeholder="默认当前时间" />
            </el-form-item>
          </el-col>
          <el-col :span="24">
            <el-form-item label="备注说明">
              <el-input v-model="reportForm.remark" placeholder="例如 首批试切合格" />
            </el-form-item>
          </el-col>
        </el-row>
      </el-form>
      <template #footer>
        <el-button @click="reportModalVisible = false">取消</el-button>
        <el-button type="primary" :loading="isSubmitting" @click="submitWorkReport">提交报工</el-button>
      </template>
    </el-dialog>

    <!-- 二次确认对话框 -->
    <ConfirmDialog
      v-model:visible="confirmState.visible"
      :title="confirmState.title"
      :message="confirmState.message"
      :loading="confirmState.loading"
      @confirm="executeConfirmAction"
    />
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted } from "vue";
import { Plus } from "@element-plus/icons-vue";
import { ElMessage, ElMessageBox } from "element-plus";
import { useCommand } from "@/composables/useCommand";
import CommandFeedback from "@/components/common/CommandFeedback.vue";
import { useRoute, useRouter } from "vue-router";
import { isActionAllowed as checkAction, getActionDisabledReason as getDisabledReason } from "../../utils/actionGuard";
import type { AllowedAction } from "../../types/common";

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
  OperationExecutionItem,
  OperationExecutionStatus,
  OperationExecutionCreateRequest,
} from "../../types/manufacturing";
import {
  getOperationExecutions,
  getDispatchOrders,
  createOperationExecution,
  startOperationExecution,
  pauseOperationExecution,
  resumeOperationExecution,
  completeOperationExecution,
  createWorkReport,
  getWorkOrders,
  getRoutings,
  getWorkReports,
} from "../../api/manufacturing";
import { getProducts } from "../../api/masterData";
import { getDevices } from "../../api/iot";
import { getOperatorDirectory } from "../../api/auth";
import { stringAdd } from "../../types/inventory";
import { currentLocalDateTimeValue } from "../../utils/dateTime";

const viewState = ref<ViewState>("loading");
const errorMessage = ref("");

const executionList = ref<OperationExecutionItem[]>([]);
const total = ref(0);
const releasedDispatches = ref<any[]>([]);
const deviceOptions = ref<any[]>([]);
const queryParams = reactive({
  page: 1,
  size: 10,
  keyword: "",
  status: "" as OperationExecutionStatus | "",
});

const columns: TableColumn[] = [
  { key: "executionNo", label: "执行编号", width: "160px" },
  { key: "workOrderId", label: "工单 / 派工单", minWidth: "180px" },
  { key: "operationId", label: "作业工序 / 操作人", minWidth: "180px" },
  { key: "deviceId", label: "执行机台", width: "160px" },
  { key: "status", label: "执行状态", width: "120px", align: "center" },
  { key: "reportedQty", label: "累计报工", width: "120px", align: "right" },
  { key: "startedAt", label: "开工 / 完工时间", width: "160px" },
  { key: "actions", label: "动作控制", width: "170px", align: "center" },
];

const createModalVisible = ref(false);
const { execute, retry, isExecuting, canRetry, lastError } = useCommand();
const isSubmitting = isExecuting;
const createForm = reactive<OperationExecutionCreateRequest>({
  dispatchOrderId: "",
  deviceId: "",
});

const activeExec = ref<OperationExecutionItem | null>(null);
const pauseModalVisible = ref(false);
const pauseReason = ref("");

const reportModalVisible = ref(false);
const reportForm = reactive({
  qualifiedQty: "10.00",
  defectQty: "0.00",
  reportTime: "",
  remark: "",
});

const confirmState = reactive({
  visible: false,
  loading: false,
  title: "",
  message: "",
  type: "" as "start" | "resume" | "complete",
  targetItem: null as OperationExecutionItem | null,
});

// 使用 actionGuard 统一权限判断逻辑
function isActionAllowed(item: { allowedActions?: AllowedAction[] | null }, action: string): boolean {
  return checkAction(item.allowedActions, action);
}

// 使用 actionGuard 统一获取禁用原因逻辑
function getActionDisabledReason(item: { allowedActions?: AllowedAction[] | null }, action: string): string | undefined {
  return getDisabledReason(item.allowedActions, action);
}

async function fetchExecutionList() {
  viewState.value = "loading";
  errorMessage.value = "";
  try {
    const res = await getOperationExecutions({
      page: queryParams.page,
      size: queryParams.size,
      status: queryParams.status || undefined,
    });
    if (res.data) {
      const rawList = res.data.records || [];
      const [dispatchResult, workOrderResult, routingResult, productResult, deviceResult, operatorResult]
        = await Promise.allSettled([
          getDispatchOrders({ page: 1, size: 1000 }),
          getWorkOrders({ page: 1, size: 1000 }),
          getRoutings({ page: 1, size: 1000 }),
          getProducts({ page: 1, size: 1000, status: "ACTIVE" }),
          getDevices({ page: 1, size: 1000 }),
          getOperatorDirectory({ page: 1, size: 1000 }),
        ]);
      const records = <T>(result: PromiseSettledResult<any>): T[] =>
        result.status === "fulfilled" ? result.value.data?.records || [] : [];
      const dispatches = records<any>(dispatchResult);
      const workOrders = records<any>(workOrderResult);
      const routings = records<any>(routingResult);
      const products = records<any>(productResult);
      const devices = records<any>(deviceResult);
      deviceOptions.value = devices;
      const operators = records<any>(operatorResult);
      const reportResults = await Promise.allSettled(
        [...new Set(rawList.map((item) => String(item.workOrderId)))].map((id) => getWorkReports(id)),
      );
      const reports = reportResults.flatMap((result) =>
        result.status === "fulfilled" ? result.value.data || [] : [],
      );
      let list = rawList.map((execution) => {
        const dispatch = dispatches.find((item) => String(item.id) === String(execution.dispatchOrderId));
        const workOrder = workOrders.find((item) => String(item.id) === String(execution.workOrderId));
        const routing = routings.find((item) => String(item.id) === String(workOrder?.routingId));
        const operation = routing?.operations?.find((item: any) => String(item.id) === String(execution.operationId));
        const product = products.find((item) => String(item.id) === String(workOrder?.productId));
        const operatorId = execution.operatorId || dispatch?.operatorId;
        const deviceId = execution.deviceId || dispatch?.deviceId;
        const operator = operators.find((item) => String(item.id) === String(operatorId));
        const device = devices.find((item) => String(item.id) === String(deviceId));
        const reportedQty = reports
          .filter((item: any) => String(item.operationExecutionId) === String(execution.id))
          .reduce((totalQty: string, item: any) => stringAdd(totalQty, item.reportQty || "0"), "0");
        return {
          ...execution,
          dispatchNo: execution.dispatchNo || dispatch?.dispatchNo || `DISP-${execution.dispatchOrderId}`,
          workOrderNo: execution.workOrderNo || workOrder?.workOrderNo || String(execution.workOrderId),
          productName: execution.productName || product?.name || String(workOrder?.productId || ""),
          operationNo: execution.operationNo || operation?.operationNo,
          operationName: execution.operationName || operation?.operationName || String(execution.operationId),
          operatorId,
          operatorName: execution.operatorName || operator?.realName || operator?.username || String(operatorId || ""),
          deviceId,
          deviceName: execution.deviceName || device?.deviceName,
          deviceCode: execution.deviceCode || device?.deviceCode,
          reportedQty,
        };
      });
      if (queryParams.keyword.trim()) {
        const kw = queryParams.keyword.toLowerCase();
        list = list.filter(
          (e) =>
            e.executionNo.toLowerCase().includes(kw) ||
            e.workOrderNo?.toLowerCase().includes(kw) ||
            e.operationName?.toLowerCase().includes(kw)
        );
      }
      executionList.value = list;
      total.value = res.data.total || list.length;
      viewState.value = executionList.value.length === 0 ? "empty" : "ready";
    }
  } catch (err: any) {
    errorMessage.value = err.message || "请求工序执行列表失败";
    viewState.value = "error";
  }
}

function handleSearch() {
  queryParams.page = 1;
  fetchExecutionList();
}

function handleReset() {
  queryParams.keyword = "";
  queryParams.status = "";
  queryParams.page = 1;
  fetchExecutionList();
}

function handlePageChange(page: number) {
  queryParams.page = page;
  fetchExecutionList();
}

async function loadReleasedDispatches() {
  try {
    // 执行建单只读取服务端小页；不可把第一页当成完整派工目录。
    const res = await getDispatchOrders({ page: 1, size: 1000, status: "Released" });
    if (res.data) {
      releasedDispatches.value = res.data.records || [];
    }
  } catch (err) {
    console.warn("[OperationExecutionView] 加载已下达派工单失败", err);
  }
}

/** 加载当前租户设备目录，供执行实例通过业务名称选择真实设备。 */
async function loadDeviceOptions() {
  try {
    const res = await getDevices({ page: 1, size: 1000 });
    deviceOptions.value = res.data?.records || [];
  } catch (err) {
    console.warn("[OperationExecutionView] 加载设备目录失败", err);
    deviceOptions.value = [];
  }
}

async function openCreateModal() {
  createForm.dispatchOrderId = "";
  createForm.deviceId = "";
  await Promise.all([loadReleasedDispatches(), loadDeviceOptions()]);
  createModalVisible.value = true;
}

async function submitCreateExecution() {
  if (!createForm.dispatchOrderId) return;
  try {
    await execute(async (key) => {
      const created = await createOperationExecution(createForm, key);
      if (!created?.data?.id) {
        throw new Error("服务端未返回 operationExecutionId，已阻止继续报工");
      }
      createModalVisible.value = false;
      ElMessage.success("工序执行创建成功！");
      await fetchExecutionList();
    }, { onConflict: fetchExecutionList });
  } catch (err: any) {
    ElMessage.error(`创建执行失败：${err.message}`);
  } finally { /* useCommand 在 finally 中恢复 isExecuting。 */ }
}

function handleStart(item: OperationExecutionItem) {
  confirmState.title = "开始工序执行确认";
  confirmState.message = `确认开工执行【${item.executionNo}】？关联工单将正式推进为【生产中】。`;
  confirmState.type = "start";
  confirmState.targetItem = item;
  confirmState.visible = true;
}

function openPauseModal(item: OperationExecutionItem) {
  activeExec.value = item;
  pauseReason.value = "设备刀具磨损检查更换";
  pauseModalVisible.value = true;
}

async function submitPause() {
  if (!activeExec.value) return;
  try {
    await execute(async (key) => {
      await pauseOperationExecution(activeExec.value!.id as string, pauseReason.value, key);
      pauseModalVisible.value = false;
      ElMessage.success("工序已暂停！");
      await fetchExecutionList();
    }, { onConflict: fetchExecutionList });
  } catch (err: any) {
    ElMessage.error(`暂停失败：${err.message}`);
  } finally { /* useCommand 在 finally 中恢复 isExecuting。 */ }
}

function handleResume(item: OperationExecutionItem) {
  confirmState.title = "恢复运行确认";
  confirmState.message = `确认恢复执行【${item.executionNo}】？`;
  confirmState.type = "resume";
  confirmState.targetItem = item;
  confirmState.visible = true;
}

function handleComplete(item: OperationExecutionItem) {
  confirmState.title = "工序完工确认";
  confirmState.message = `确认完成该道工序执行【${item.executionNo}】？`;
  confirmState.type = "complete";
  confirmState.targetItem = item;
  confirmState.visible = true;
}

async function executeConfirmAction() {
  if (!confirmState.targetItem) return;
  confirmState.loading = true;
  try {
    const id = confirmState.targetItem.id as string;
    if (confirmState.type === "start") {
      await execute((key) => startOperationExecution(id, key), { onConflict: fetchExecutionList });
    } else if (confirmState.type === "resume") {
      await execute((key) => resumeOperationExecution(id, key), { onConflict: fetchExecutionList });
    } else if (confirmState.type === "complete") {
      await execute((key) => completeOperationExecution(id, key), { onConflict: fetchExecutionList });
    }
    confirmState.visible = false;
    await fetchExecutionList();
  } catch (err: any) {
    ElMessage.error(`操作失败：${err.message}`);
  } finally {
    confirmState.loading = false;
  }
}

function openReportModal(item: OperationExecutionItem) {
  activeExec.value = item;
  reportForm.qualifiedQty = "10.00";
  reportForm.defectQty = "0.00";
  reportForm.reportTime = currentLocalDateTimeValue();
  reportForm.remark = "";
  reportModalVisible.value = true;
}

async function submitWorkReport() {
  if (!activeExec.value) return;
  const execution = activeExec.value;
  const woId = execution.workOrderId;
  // 修改用途：报工单号是服务端事实的必填标识，由页面一次生成并在幂等执行期间复用。
  const reportNo = `RPT-${crypto.randomUUID()}`;
  try {
    const created = await execute((key) => createWorkReport({
      reportNo,
      operationExecutionId: execution.id as string,
      workOrderId: woId,
      operationId: execution.operationId,
      qualifiedQty: reportForm.qualifiedQty,
      defectQty: reportForm.defectQty,
      reportTime: new Date(reportForm.reportTime).toISOString(),
      remark: reportForm.remark,
    }, key), { onConflict: fetchExecutionList });
    if (!created?.data?.id) {
      throw new Error("服务端未返回 workReportId，已阻止继续发起质检");
    }
    reportModalVisible.value = false;
    await fetchExecutionList();
    try {
      await ElMessageBox.confirm("工序报工已提交成功！是否前往生产工单详情办理【生产质检】与成品入库？", "报工成功", {
        confirmButtonText: "前往办理",
        cancelButtonText: "留在此页",
        type: "success",
      });
      await router.push({
        // 报工完成后直接进入真实工单详情，避免列表页丢失 openQuality 导航意图。
        path: `/mes/work-orders/${encodeURIComponent(String(woId))}`,
        query: { openQuality: "true" },
      });
    } catch {
      // 用户选择留在此页
    }
  } catch (err: any) {
    ElMessage.error(`报工提交失败：${err.message}`);
  } finally { /* useCommand 在 finally 中恢复 isExecuting。 */ }
}

onMounted(async () => {
  fetchExecutionList();
  await loadReleasedDispatches();
  if (route.query.dispatchOrderId) {
    createForm.dispatchOrderId = String(route.query.dispatchOrderId);
    createModalVisible.value = true;
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

.relation-cell, .op-cell {
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.sub-tag {
  font-size: 11px;
}

.op-name {
  color: #f1f5f9;
}

.device-tag {
  color: #38bdf8;
}

.time-cell {
  font-size: 11px;
}

.action-btn-group {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 6px;
  flex-wrap: wrap;
}

.btn-text {
  background: none;
  border: none;
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
.text-cyan { color: #22d3ee !important; }

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
  max-width: 500px;
  box-shadow: 0 20px 30px rgba(0, 0, 0, 0.5);
  overflow: hidden;
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
