<template>
  <div class="manufacturing-view-container">
    <!-- 统一页面头部 -->
    <PageHeader
      title="工艺路线 (Routing) 管理"
      tag="MES / MASTER DATA"
      description="定义产品加工工序流、作业工作中心与标准工时基准。工单审核通过时将锁定对应有效路线版本。"
    >
      <template #actions>
        <el-button v-if="hasPermission('mes:routing:manage')" type="primary" :icon="Plus" @click="openCreateModal">
          新建工艺路线
        </el-button>
      </template>
    </PageHeader>

    <!-- 统一筛选栏 -->
    <FilterBar
      v-model="queryParams.keyword"
      placeholder="搜索路线编码或产品名称..."
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
        <el-option label="生效中 (ACTIVE)" value="ACTIVE" />
        <el-option label="草稿 (DRAFT)" value="DRAFT" />
        <el-option label="已停用 (DISABLED)" value="DISABLED" />
      </el-select>
    </FilterBar>

    <!-- 错误异常提示 -->
    <ErrorState
      v-if="viewState === 'error'"
      title="工艺路线数据加载失败"
      :message="errorMessage"
      @retry="fetchRoutingList"
    />

    <!-- 数据表格 -->
    <DataTable
      v-else
      :columns="columns"
      :data="routingList"
      :loading="viewState === 'loading'"
      :page="queryParams.page"
      :size="queryParams.size"
      :total="total"
      empty-text="暂无匹配的工艺路线记录"
      @page-change="handlePageChange"
    >
      <!-- 路线编码 -->
      <template #routingCode="{ row }">
        <span class="font-mono highlight-code">{{ row.routingCode }}</span>
      </template>

      <!-- 产出产品 -->
      <template #productId="{ row }">
        <div class="product-cell">
          <span class="product-name">{{ row.productName || "未知产品" }}</span>
          <span class="product-code font-mono text-muted">{{ row.productCode || row.productId }}</span>
        </div>
      </template>

      <!-- 版本号 -->
      <template #version="{ row }">
        <span class="version-tag font-mono">{{ row.version }}</span>
      </template>

      <!-- 状态 -->
      <template #status="{ row }">
        <StatusBadge
          :type="row.status === 'ACTIVE' ? 'success' : row.status === 'DRAFT' ? 'warning' : 'default'"
          :text="row.status === 'ACTIVE' ? '生效中' : row.status === 'DRAFT' ? '草稿' : '已停用'"
        />
      </template>

      <!-- 工序数 -->
      <template #operationsCount="{ row }">
        <span class="badge-count">{{ row.operations?.length || 0 }} 道工序</span>
      </template>

      <!-- 操作列 -->
      <template #actions="{ row }">
        <div style="display: flex; gap: 8px; justify-content: center">
          <el-button type="primary" link size="small" @click="openDetailDrawer(row)">
            查看工序
          </el-button>
          <el-button
            type="danger"
            link
            size="small"
            :disabled="!isActionAllowed(row, 'delete')"
            :title="getActionDisabledReason(row, 'delete') || '删除此工艺路线'"
            @click="promptDelete(row)"
          >
            删除
          </el-button>
        </div>
      </template>
    </DataTable>

    <!-- 工艺路线工序流抽屉 -->
    <el-drawer
      v-model="drawerVisible"
      size="760px"
      destroy-on-close
    >
      <template #header>
        <div v-if="activeRouting" style="display: flex; align-items: center; gap: 12px">
          <span style="font-family: monospace; font-weight: bold; color: #67d2ff">{{ activeRouting.routingCode }}</span>
          <span style="font-size: 16px; font-weight: 600; color: #f1f5f9">{{ activeRouting.productName }} ({{ activeRouting.version }})</span>
        </div>
      </template>

      <div v-if="activeRouting" class="drawer-body">
        <div style="margin-bottom: 12px; font-size: 14px; font-weight: 600; color: #cbd5e1">
          标准工序流程 (共 {{ activeRouting.operations?.length || 0 }} 道工序)
        </div>

        <!-- 工序流卡片列表 -->
        <div style="display: flex; flex-direction: column; gap: 10px">
          <el-card
            v-for="op in activeRouting.operations"
            :key="op.id || op.operationNo"
            shadow="never"
            style="background: rgba(30, 41, 59, 0.4); border: 1px solid rgba(140, 162, 184, 0.2)"
          >
            <div style="display: flex; align-items: center; justify-content: space-between; margin-bottom: 8px">
              <div style="display: flex; align-items: center; gap: 10px">
                <el-tag type="primary" size="small" style="font-family: monospace">序号 {{ op.operationNo }}</el-tag>
                <span style="font-weight: 600; color: #f1f5f9; font-size: 15px">{{ op.operationName }}</span>
              </div>
              <el-tag type="info" size="small">{{ op.workCenterName || op.workCenterId }}</el-tag>
            </div>

            <div style="font-size: 13px; color: #8ca2b8; display: flex; align-items: center; gap: 8px">
              <span>标准工时：</span>
              <QuantityText :value="op.standardTimeMinutes || '0.00'" unit="分钟" />
              <span v-if="op.remark" style="color: #64748b">（{{ op.remark }}）</span>
            </div>
          </el-card>
        </div>
      </div>

      <template #footer>
        <el-button @click="drawerVisible = false">关闭</el-button>
      </template>
    </el-drawer>

    <!-- 新建工艺路线对话框 -->
    <el-dialog
      v-model="createModalVisible"
      title="新建工艺路线 (Routing)"
      width="800px"
      destroy-on-close
      append-to-body
    >
      <el-form label-width="110px" @submit.prevent="submitCreateRouting">
        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item label="路线编码" required>
              <el-input v-model="createForm.routingCode" placeholder="例如 ROUT-GW-STANDARD" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="版本号" required>
              <el-input v-model="createForm.version" placeholder="例如 V1.0" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="产出产品" required>
              <el-select v-model="createForm.productId" placeholder="请选择真实产品" filterable style="width: 100%">
                <el-option
                  v-for="product in products"
                  :key="product.id"
                  :value="String(product.id)"
                  :label="`${product.sku} (${product.name})`"
                />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="状态" required>
              <el-select v-model="createForm.status" style="width: 100%">
                <el-option label="生效中 (ACTIVE，可供工单选择)" value="ACTIVE" />
                <el-option label="草稿 (DRAFT，暂不可供工单选择)" value="DRAFT" />
              </el-select>
            </el-form-item>
          </el-col>
        </el-row>

        <!-- 动态工序行 -->
        <div style="margin-top: 16px; border-top: 1px dashed rgba(140, 162, 184, 0.2); padding-top: 16px">
          <div style="display: flex; justify-content: space-between; align-items: center; margin-bottom: 12px">
            <span style="font-weight: 600; color: #f1f5f9; font-size: 14px">工序步骤定义 <span style="color: #f43f5e">*</span></span>
            <el-button type="primary" size="small" :icon="Plus" @click="addOperationRow">添加工序</el-button>
          </div>

          <div style="display: flex; flex-direction: column; gap: 10px">
            <div
              v-for="(op, idx) in createForm.operations"
              :key="idx"
              style="display: flex; gap: 8px; align-items: center"
            >
              <el-input
                v-model.number="op.operationNo"
                type="number"
                step="10"
                placeholder="序号"
                style="width: 80px"
              />
              <el-input
                v-model="op.operationName"
                placeholder="工序名称 (如 主板贴片)"
                style="flex: 2"
              />
              <el-select
                v-model="op.workCenterId"
                placeholder="工作中心"
                style="flex: 2"
              >
                <el-option :value="MANUAL_WORK_CENTER_ID" label="人工工位（无需专用设备）" />
              </el-select>
              <el-input
                v-model="op.standardTimeMinutes"
                placeholder="标准工时(分)"
                style="width: 110px"
              />
              <el-button
                type="danger"
                :icon="Delete"
                circle
                size="small"
                :disabled="createForm.operations.length <= 1"
                @click="removeOperationRow(idx)"
              />
            </div>
          </div>
          <div style="margin-top: 6px; font-size: 12px; color: #8ca2b8">
            一期人工工序使用系统内置人工工位，不要求绑定专用设备。
          </div>
        </div>
      </el-form>

      <template #footer>
        <el-button @click="createModalVisible = false">取消</el-button>
        <el-button type="primary" :loading="isSubmitting" @click="submitCreateRouting">
          {{ isSubmitting ? "创建中..." : "保存工艺路线" }}
        </el-button>
      </template>
    </el-dialog>

    <!-- 删除确认对话框 -->
    <ConfirmDialog
      v-model:visible="deleteConfirm.visible"
      title="删除工艺路线确认"
      :message="`确定要删除工艺路线【${deleteConfirm.item?.routingCode}】吗？`"
      confirm-text="确认删除"
      danger
      :loading="deleteConfirm.loading"
      @confirm="handleConfirmDelete"
    />
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted } from "vue";
import { Plus, Delete } from "@element-plus/icons-vue";
import { ElMessage } from "element-plus";
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
import type { RoutingItem, RoutingCreateRequest } from "../../types/manufacturing";
import { getRoutings, createRouting, deleteRouting } from "../../api/manufacturing";
import { getProducts } from "../../api/masterData";
import type { Product } from "../../types/inventory";
import { usePermission } from "../../composables/usePermission";

const viewState = ref<ViewState>("loading");
const errorMessage = ref("");
const { hasPermission } = usePermission();

// 一期允许人工工序不绑定专用设备；该稳定标识仅作为 Routing 的必填工作中心软引用。
const MANUAL_WORK_CENTER_ID = "00000000-0000-0000-0000-000000000001";

const routingList = ref<RoutingItem[]>([]);
const total = ref(0);
const products = ref<Product[]>([]);
const queryParams = reactive({
  page: 1,
  size: 10,
  keyword: "",
  status: "",
});

const columns: TableColumn[] = [
  { key: "routingCode", label: "路线编码", width: "180px" },
  { key: "productId", label: "目标产出品", minWidth: "220px" },
  { key: "version", label: "版本号", width: "100px", align: "center" },
  { key: "status", label: "状态", width: "110px", align: "center" },
  { key: "operationsCount", label: "工序流程", width: "120px", align: "center" },
  { key: "createdAt", label: "创建时间", width: "160px" },
  { key: "actions", label: "操作", width: "150px", align: "center" },
];

const drawerVisible = ref(false);
const activeRouting = ref<RoutingItem | null>(null);

const createModalVisible = ref(false);
const isSubmitting = ref(false);
const createForm = reactive<RoutingCreateRequest>({
  routingCode: "",
  productId: "",
  version: "V1.0",
  status: "ACTIVE",
  operations: [
    { operationNo: 10, operationName: "", workCenterId: MANUAL_WORK_CENTER_ID, standardTimeMinutes: "" },
  ],
});

const deleteConfirm = reactive({
  visible: false,
  loading: false,
  item: null as RoutingItem | null,
});

// 使用 actionGuard 统一权限判断逻辑
function isActionAllowed(item: { allowedActions?: AllowedAction[] | null }, action: string): boolean {
  return checkAction(item.allowedActions, action);
}

// 使用 actionGuard 统一获取禁用原因逻辑
function getActionDisabledReason(item: { allowedActions?: AllowedAction[] | null }, action: string): string | undefined {
  return getDisabledReason(item.allowedActions, action);
}

async function fetchRoutingList() {
  viewState.value = "loading";
  errorMessage.value = "";
  try {
    const res = await getRoutings({
      page: queryParams.page,
      size: queryParams.size,
      keyword: queryParams.keyword.trim() || undefined,
      status: queryParams.status || undefined,
    });
    if (res.data) {
      routingList.value = (res.data.records || []).map(normalizeRouting);
      total.value = res.data.total || 0;
      viewState.value = routingList.value.length === 0 ? "empty" : "ready";
    }
  } catch (err: any) {
    errorMessage.value = err.message || "请求工艺路线接口失败";
    viewState.value = "error";
  }
}

function handleSearch() {
  queryParams.page = 1;
  fetchRoutingList();
}

function handleReset() {
  queryParams.keyword = "";
  queryParams.status = "";
  queryParams.page = 1;
  fetchRoutingList();
}

function handlePageChange(page: number) {
  queryParams.page = page;
  fetchRoutingList();
}

function openDetailDrawer(item: RoutingItem) {
  activeRouting.value = item;
  drawerVisible.value = true;
}

function openCreateModal() {
  createForm.routingCode = "";
  createForm.productId = "";
  createForm.version = "V1.0";
  createForm.status = "ACTIVE";
  createForm.operations = [
    { operationNo: 10, operationName: "", workCenterId: MANUAL_WORK_CENTER_ID, standardTimeMinutes: "" },
  ];
  createModalVisible.value = true;
}

function addOperationRow() {
  const nextNo = (createForm.operations.length + 1) * 10;
  createForm.operations.push({
    operationNo: nextNo,
    operationName: "",
    workCenterId: MANUAL_WORK_CENTER_ID,
    standardTimeMinutes: "15.00",
  });
}

function removeOperationRow(idx: number) {
  if (createForm.operations.length > 1) {
    createForm.operations.splice(idx, 1);
  }
}

async function submitCreateRouting() {
  if (!createForm.routingCode.trim() || !createForm.productId.trim()) return;
  isSubmitting.value = true;
  try {
    await createRouting(createForm);
    createModalVisible.value = false;
    await fetchRoutingList();
  } catch (err: any) {
    ElMessage.error(`创建失败：${err.message}`);
  } finally {
    isSubmitting.value = false;
  }
}

function promptDelete(item: RoutingItem) {
  deleteConfirm.item = item;
  deleteConfirm.visible = true;
}

async function handleConfirmDelete() {
  if (!deleteConfirm.item) return;
  deleteConfirm.loading = true;
  try {
    await deleteRouting(deleteConfirm.item.id as string);
    deleteConfirm.visible = false;
    await fetchRoutingList();
  } catch (err: any) {
    ElMessage.error(`删除失败：${err.message}`);
  } finally {
    deleteConfirm.loading = false;
  }
}

onMounted(async () => {
  await loadProducts();
  await fetchRoutingList();
});

/** 加载工艺路线所关联的真实产品 UUID。 */
async function loadProducts() {
  const response = await getProducts({ page: 1, size: 1000, status: "ACTIVE" });
  products.value = response.data.records || [];
}

/**
 * 将 foundation 路线事实补齐为页面展示模型。
 * 入参：后端 Routing 事实；出参：带产品名称和编码的页面记录。
 */
function normalizeRouting(item: RoutingItem): RoutingItem {
  const product = products.value.find((candidate) => String(candidate.id) === String(item.productId));
  return {
    ...item,
    productName: item.productName || product?.name,
    productCode: item.productCode || product?.sku,
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

.product-cell {
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.product-name {
  font-weight: 500;
  color: #f1f5f9;
}

.product-code {
  font-size: 11px;
}

.version-tag {
  background: rgba(148, 163, 184, 0.15);
  border: 1px solid rgba(148, 163, 184, 0.3);
  padding: 2px 8px;
  border-radius: 4px;
  font-size: 11px;
  color: #cbd5e1;
}

.badge-count {
  font-size: 12px;
  color: #94a3b8;
}

.action-btn-group {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 12px;
}

.btn-text {
  background: none;
  border: none;
  color: #38bdf8;
  font-size: 13px;
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

.text-danger {
  color: #f87171 !important;
}

/* 抽屉样式 */
.drawer-overlay {
  position: fixed;
  inset: 0;
  background: rgba(0, 0, 0, 0.6);
  backdrop-filter: blur(3px);
  z-index: 1000;
  display: flex;
  justify-content: flex-end;
}

.drawer-panel {
  width: 560px;
  max-width: 90vw;
  background: #0f172a;
  border-left: 1px solid rgba(255, 255, 255, 0.12);
  height: 100%;
  display: flex;
  flex-direction: column;
  box-shadow: -10px 0 25px rgba(0, 0, 0, 0.5);
  animation: slide-left 0.25s ease-out;
}

.drawer-header {
  padding: 20px;
  border-bottom: 1px solid rgba(255, 255, 255, 0.08);
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
}

.drawer-tag {
  font-size: 11px;
  color: #38bdf8;
}

.drawer-title {
  margin: 4px 0 0;
  font-size: 16px;
  color: #f8fafc;
}

.drawer-body {
  flex: 1;
  padding: 20px;
  overflow-y: auto;
}

.drawer-section-title {
  font-size: 13px;
  font-weight: 600;
  color: #94a3b8;
  margin-bottom: 16px;
}

.operation-timeline {
  display: flex;
  flex-direction: column;
  gap: 14px;
}

.operation-step-card {
  display: flex;
  align-items: flex-start;
  gap: 14px;
  background: rgba(30, 41, 59, 0.5);
  border: 1px solid rgba(255, 255, 255, 0.08);
  border-radius: 8px;
  padding: 14px 16px;
}

.step-badge {
  background: rgba(56, 189, 248, 0.15);
  border: 1px solid rgba(56, 189, 248, 0.3);
  color: #38bdf8;
  font-weight: 700;
  padding: 4px 8px;
  border-radius: 6px;
  font-size: 13px;
}

.step-content {
  flex: 1;
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.step-top-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.step-name {
  margin: 0;
  font-size: 14px;
  color: #f1f5f9;
}

.wc-tag {
  font-size: 11px;
  background: rgba(148, 163, 184, 0.12);
  color: #94a3b8;
  padding: 2px 8px;
  border-radius: 4px;
}

.step-meta-row {
  font-size: 13px;
  color: #cbd5e1;
  display: flex;
  align-items: center;
  gap: 6px;
}

.meta-label {
  font-size: 12px;
  color: #94a3b8;
}

.step-remark {
  font-size: 12px;
}

.drawer-footer {
  padding: 16px 20px;
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
  max-width: 680px;
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
  max-height: 75vh;
  overflow-y: auto;
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

.form-section {
  display: flex;
  flex-direction: column;
  gap: 10px;
  border-top: 1px dashed rgba(255, 255, 255, 0.1);
  padding-top: 14px;
}

.section-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.section-head label {
  font-size: 13px;
  font-weight: 600;
  color: #cbd5e1;
}

.op-form-list {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.op-form-row {
  display: flex;
  gap: 8px;
  align-items: center;
}

.op-col-no { flex: 1.2; }
.op-col-name { flex: 3; }
.op-col-wc { flex: 2; }
.op-col-time { flex: 2; }

.btn-del-row {
  background: rgba(239, 68, 68, 0.15);
  border: 1px solid rgba(239, 68, 68, 0.3);
  color: #f87171;
  padding: 6px 10px;
  border-radius: 6px;
  cursor: pointer;
}

.btn-del-row:disabled {
  opacity: 0.3;
  cursor: not-allowed;
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

.btn-sm {
  padding: 4px 10px;
  font-size: 12px;
  border-radius: 4px;
  cursor: pointer;
}

@keyframes slide-left {
  from { transform: translateX(100%); }
  to { transform: translateX(0); }
}
</style>
