<template>
  <div class="manufacturing-view-container">
    <!-- 统一页面头部 -->
    <PageHeader
      title="物料清单 (BOM) 管理"
      tag="MES / MASTER DATA"
      description="维护产成品及其标准物料清单构成与损耗率。已生效工单绑定的 BOM 版本不允许物理删除。"
    >
      <template #actions>
        <button type="button" class="btn btn-primary" @click="openCreateModal">
          <span class="btn-icon">＋</span>
          <span>新建物料清单</span>
        </button>
      </template>
    </PageHeader>

    <!-- 统一筛选栏 -->
    <FilterBar
      v-model="queryParams.keyword"
      placeholder="搜索 BOM 编码或产品名称/代号..."
      @search="handleSearch"
      @reset="handleReset"
    >
      <div class="filter-select-group">
        <label class="filter-label">状态：</label>
        <select v-model="queryParams.status" class="filter-select" @change="handleSearch">
          <option value="">全部状态</option>
          <option value="ACTIVE">生效中 (ACTIVE)</option>
          <option value="DRAFT">草稿 (DRAFT)</option>
          <option value="DISABLED">已废弃 (DISABLED)</option>
        </select>
      </div>
    </FilterBar>

    <!-- 错误异常提示 -->
    <ErrorState
      v-if="viewState === 'error'"
      title="BOM 列表数据加载失败"
      :message="errorMessage"
      @retry="fetchBomList"
    />

    <!-- 数据表格 -->
    <DataTable
      v-else
      :columns="columns"
      :data="bomList"
      :loading="viewState === 'loading'"
      :page="queryParams.page"
      :size="queryParams.size"
      :total="total"
      empty-text="暂无匹配的物料清单记录"
      @page-change="handlePageChange"
    >
      <!-- BOM 编码 -->
      <template #bomCode="{ row }">
        <span class="font-mono highlight-code">{{ row.bomCode }}</span>
      </template>

      <!-- 所属产品 -->
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

      <!-- 状态徽标 -->
      <template #status="{ row }">
        <StatusBadge
          :type="row.status === 'ACTIVE' ? 'success' : row.status === 'DRAFT' ? 'warning' : 'default'"
          :text="row.status === 'ACTIVE' ? '生效中' : row.status === 'DRAFT' ? '草稿' : '已停用'"
        />
      </template>

      <!-- 物料组件数 -->
      <template #componentsCount="{ row }">
        <span class="badge-count">{{ row.components?.length || 0 }} 种原料</span>
      </template>

      <!-- 操作列 -->
      <template #actions="{ row }">
        <div class="action-btn-group">
          <button
            type="button"
            class="btn-text"
            @click="openDetailDrawer(row)"
          >
            查看明细
          </button>
          <button
            type="button"
            class="btn-text text-danger"
            :disabled="!isActionAllowed(row, 'delete')"
            :title="getActionDisabledReason(row, 'delete') || '删除此 BOM'"
            @click="promptDelete(row)"
          >
            删除
          </button>
        </div>
      </template>
    </DataTable>

    <!-- BOM 组件明细抽屉面板 -->
    <div v-if="drawerVisible && activeBom" class="drawer-overlay" @click.self="drawerVisible = false">
      <div class="drawer-panel">
        <div class="drawer-header">
          <div>
            <span class="drawer-tag font-mono">{{ activeBom.bomCode }}</span>
            <h3 class="drawer-title">{{ activeBom.productName }} ({{ activeBom.version }})</h3>
          </div>
          <button type="button" class="btn-close" @click="drawerVisible = false">✕</button>
        </div>

        <div class="drawer-body">
          <div class="drawer-section-title">
            <span>构成物料项列表 (共 {{ activeBom.components?.length || 0 }} 项)</span>
          </div>

          <table class="nested-table">
            <thead>
              <tr>
                <th style="width: 50px;">#</th>
                <th>原料组件名称 / 编码</th>
                <th style="width: 140px; text-align: right;">标准用量</th>
                <th style="width: 100px; text-align: right;">损耗率</th>
                <th>备注说明</th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="(comp, idx) in activeBom.components" :key="comp.id || idx">
                <td class="font-mono text-muted">{{ idx + 1 }}</td>
                <td>
                  <div class="component-meta">
                    <span class="comp-name">{{ comp.componentProductName || comp.componentProductId }}</span>
                    <span class="comp-code font-mono text-muted">{{ comp.componentProductCode || "-" }}</span>
                  </div>
                </td>
                <td style="text-align: right;">
                  <QuantityText :value="comp.componentQty" :unit="comp.uom" />
                </td>
                <td style="text-align: right;">
                  <QuantityText :value="comp.scrapRate || '0.00'" unit="%" />
                </td>
                <td class="text-muted">{{ comp.remark || "-" }}</td>
              </tr>
            </tbody>
          </table>
        </div>

        <div class="drawer-footer">
          <button type="button" class="btn btn-secondary" @click="drawerVisible = false">关闭</button>
        </div>
      </div>
    </div>

    <!-- 新建 BOM 模态对话框 -->
    <div v-if="createModalVisible" class="modal-mask" @click.self="createModalVisible = false">
      <div class="modal-card modal-large">
        <div class="modal-header">
          <h3 class="modal-title">新建物料清单 (BOM)</h3>
          <button type="button" class="btn-close" @click="createModalVisible = false">✕</button>
        </div>

        <form class="modal-body" @submit.prevent="submitCreateBom">
          <div class="form-grid two-col">
            <div class="form-item">
              <label>BOM 编码 <span class="req">*</span></label>
              <input
                v-model="createForm.bomCode"
                type="text"
                class="form-input font-mono"
                placeholder="例如 BOM-CTL-002"
                required
              />
            </div>
            <div class="form-item">
              <label>版本号 <span class="req">*</span></label>
              <input
                v-model="createForm.version"
                type="text"
                class="form-input font-mono"
                placeholder="例如 V1.0"
                required
              />
            </div>
          </div>

          <div class="form-item">
            <label>产出产品代码/标识 <span class="req">*</span></label>
            <input
              v-model="createForm.productId"
              type="text"
              class="form-input"
              placeholder="例如 prod-101 (工业网关主板)"
              required
            />
          </div>

          <!-- 动态组件清单编辑 -->
          <div class="form-section">
            <div class="section-head">
              <label>BOM 原料明细项 <span class="req">*</span></label>
              <button type="button" class="btn-sm btn-secondary" @click="addComponentRow">
                ＋ 添加物料
              </button>
            </div>

            <div class="component-form-table">
              <div
                v-for="(comp, idx) in createForm.components"
                :key="idx"
                class="component-form-row"
              >
                <div class="comp-col-id">
                  <input
                    v-model="comp.componentProductId"
                    type="text"
                    class="form-input"
                    placeholder="组件产品 ID"
                    required
                  />
                </div>
                <div class="comp-col-qty">
                  <input
                    v-model="comp.componentQty"
                    type="text"
                    class="form-input font-mono"
                    placeholder="数量 (如 1.00)"
                    required
                  />
                </div>
                <div class="comp-col-uom">
                  <input
                    v-model="comp.uom"
                    type="text"
                    class="form-input"
                    placeholder="单位 (PCS)"
                    required
                  />
                </div>
                <div class="comp-col-scrap">
                  <input
                    v-model="comp.scrapRate"
                    type="text"
                    class="form-input font-mono"
                    placeholder="损耗 (如 0.01)"
                  />
                </div>
                <button
                  type="button"
                  class="btn-del-row"
                  :disabled="createForm.components.length <= 1"
                  @click="removeComponentRow(idx)"
                >
                  ✕
                </button>
              </div>
            </div>
          </div>

          <div class="modal-footer">
            <button type="button" class="btn btn-secondary" @click="createModalVisible = false">取消</button>
            <button type="submit" class="btn btn-primary" :disabled="isSubmitting">
              {{ isSubmitting ? "创建中..." : "保存 BOM" }}
            </button>
          </div>
        </form>
      </div>
    </div>

    <!-- 删除确认对话框 -->
    <ConfirmDialog
      v-model:visible="deleteConfirm.visible"
      title="删除物料清单确认"
      :message="`确定要永久删除 BOM 清单【${deleteConfirm.item?.bomCode}】吗？若已有工单引用可能导致关联异常。`"
      confirm-text="确认删除"
      danger
      :loading="deleteConfirm.loading"
      @confirm="handleConfirmDelete"
    />
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted } from "vue";
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
import type { BomItem, BomCreateRequest } from "../../types/manufacturing";
import { getBoms, createBom, deleteBom } from "../../api/manufacturing";

/**
 * 界面四态与错误信息
 */
const viewState = ref<ViewState>("loading");
const errorMessage = ref("");

/**
 * 列表与分页状态
 */
const bomList = ref<BomItem[]>([]);
const total = ref(0);
const queryParams = reactive({
  page: 1,
  size: 10,
  keyword: "",
  status: "",
});

/**
 * 表格列配置
 */
const columns: TableColumn[] = [
  { key: "bomCode", label: "BOM 编码", width: "160px" },
  { key: "productId", label: "产出目标产品", minWidth: "220px" },
  { key: "version", label: "版本号", width: "100px", align: "center" },
  { key: "status", label: "状态", width: "110px", align: "center" },
  { key: "componentsCount", label: "物料构成", width: "120px", align: "center" },
  { key: "createdAt", label: "创建时间", width: "160px" },
  { key: "actions", label: "操作", width: "150px", align: "center" },
];

/**
 * 抽屉明查看状态
 */
const drawerVisible = ref(false);
const activeBom = ref<BomItem | null>(null);

/**
 * 创建对话框状态
 */
const createModalVisible = ref(false);
const isSubmitting = ref(false);
const createForm = reactive<BomCreateRequest>({
  bomCode: "",
  productId: "",
  version: "V1.0",
  components: [
    { componentProductId: "", componentQty: "1.00", uom: "PCS", scrapRate: "0.00" },
  ],
});

/**
 * 删除确认状态
 */
const deleteConfirm = reactive({
  visible: false,
  loading: false,
  item: null as BomItem | null,
});

/**
 * 判断指定操作是否在 allowedActions 中启用
 * @param item 当前 BOM 数据
 * @param action 操作指令标识
 */
function isActionAllowed(item: BomItem, action: string): boolean {
  if (!item.allowedActions || item.allowedActions.length === 0) return true;
  const match = item.allowedActions.find((a) => a.action === action);
  return match ? match.enabled : true;
}

/**
 * 获取操作禁用原因
 */
function getActionDisabledReason(item: BomItem, action: string): string | undefined {
  const match = item.allowedActions?.find((a) => a.action === action);
  return match && !match.enabled ? match.reason : undefined;
}

/**
 * 加载 BOM 列表数据
 */
async function fetchBomList() {
  viewState.value = "loading";
  errorMessage.value = "";
  try {
    const res = await getBoms({
      page: queryParams.page,
      size: queryParams.size,
      keyword: queryParams.keyword.trim() || undefined,
      status: queryParams.status || undefined,
    });
    if (res.data) {
      bomList.value = res.data.records || [];
      total.value = res.data.total || 0;
      viewState.value = bomList.value.length === 0 ? "empty" : "ready";
    }
  } catch (err: any) {
    errorMessage.value = err.message || "请求 BOM 列表接口失败";
    viewState.value = "error";
  }
}

function handleSearch() {
  queryParams.page = 1;
  fetchBomList();
}

function handleReset() {
  queryParams.keyword = "";
  queryParams.status = "";
  queryParams.page = 1;
  fetchBomList();
}

function handlePageChange(page: number) {
  queryParams.page = page;
  fetchBomList();
}

function openDetailDrawer(item: BomItem) {
  activeBom.value = item;
  drawerVisible.value = true;
}

function openCreateModal() {
  createForm.bomCode = "";
  createForm.productId = "";
  createForm.version = "V1.0";
  createForm.components = [
    { componentProductId: "", componentQty: "1.00", uom: "PCS", scrapRate: "0.00" },
  ];
  createModalVisible.value = true;
}

function addComponentRow() {
  createForm.components.push({
    componentProductId: "",
    componentQty: "1.00",
    uom: "PCS",
    scrapRate: "0.00",
  });
}

function removeComponentRow(idx: number) {
  if (createForm.components.length > 1) {
    createForm.components.splice(idx, 1);
  }
}

/**
 * 提交创建 BOM
 */
async function submitCreateBom() {
  if (!createForm.bomCode.trim() || !createForm.productId.trim()) return;
  isSubmitting.value = true;
  try {
    await createBom(createForm);
    createModalVisible.value = false;
    await fetchBomList();
  } catch (err: any) {
    alert(`创建失败：${err.message}`);
  } finally {
    isSubmitting.value = false;
  }
}

function promptDelete(item: BomItem) {
  deleteConfirm.item = item;
  deleteConfirm.visible = true;
}

/**
 * 确认删除 BOM
 */
async function handleConfirmDelete() {
  if (!deleteConfirm.item) return;
  deleteConfirm.loading = true;
  try {
    await deleteBom(deleteConfirm.item.id as string);
    deleteConfirm.visible = false;
    await fetchBomList();
  } catch (err: any) {
    alert(`删除失败：${err.message}`);
  } finally {
    deleteConfirm.loading = false;
  }
}

onMounted(() => {
  fetchBomList();
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
  width: 600px;
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
  letter-spacing: 0.5px;
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
  margin-bottom: 12px;
}

.nested-table {
  width: 100%;
  border-collapse: collapse;
  font-size: 13px;
}

.nested-table th {
  background: rgba(30, 41, 59, 0.5);
  padding: 8px 10px;
  color: #94a3b8;
  font-weight: 500;
  border-bottom: 1px solid rgba(255, 255, 255, 0.08);
}

.nested-table td {
  padding: 10px;
  border-bottom: 1px solid rgba(255, 255, 255, 0.05);
}

.component-meta {
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.comp-name {
  color: #f1f5f9;
}

.comp-code {
  font-size: 11px;
}

.drawer-footer {
  padding: 16px 20px;
  border-top: 1px solid rgba(255, 255, 255, 0.08);
  display: flex;
  justify-content: flex-end;
}

/* 模态框通用 */
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

.component-form-table {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.component-form-row {
  display: flex;
  gap: 8px;
  align-items: center;
}

.comp-col-id { flex: 3; }
.comp-col-qty { flex: 2; }
.comp-col-uom { flex: 1.5; }
.comp-col-scrap { flex: 1.5; }

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
