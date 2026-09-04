<template>
  <div>
    <!-- 1. 二维底图监控画布模式 -->
    <SiteMapView
      v-if="currentMode === 'viewer'"
      :map-id="selectedMapId"
      @back-list="currentMode = 'list'"
      @edit-map="handleEditMap"
      @navigate-trace="handleNavigateTrace"
    />

    <!-- 2. 二维点位配置编辑器模式 -->
    <SiteMapEditorView
      v-else-if="currentMode === 'editor'"
      :map-id="selectedMapId"
      @back-map="currentMode = 'viewer'"
      @back-list="currentMode = 'list'"
    />

    <!-- 3. 地图列表模式 (默认) -->
    <div v-else class="sitemap-list-container">
      <!-- 顶部统一页面头 -->
      <PageHeader
        title="二维空间站点地图"
        tag="GIS / SITE MAP DIRECTORY"
        description="厂区、立体库与制造车间多底图配置管理。统一基于相对百分比坐标 (x%, y%) 渲染点位，确保页面与容器缩放时点位绝对不发生漂移。"
      >
        <template #actions>
          <div class="nav-sub-tabs">
            <button type="button" class="tab-btn" @click="$emit('navigate-dashboard')">
              <span>📊 综合看板</span>
            </button>
            <button type="button" class="tab-btn is-active">
              <span>🗺️ 站点地图</span>
            </button>
            <button type="button" class="tab-btn" @click="handleNavigateTrace">
              <span>🔍 闭环追溯</span>
            </button>
          </div>

          <button type="button" class="btn-create-map" @click="openCreateModal">
            <span>＋ 新建底图配置</span>
          </button>
        </template>
      </PageHeader>

      <!-- 检索过滤栏 -->
      <FilterBar
        v-model="searchKeyword"
        placeholder="搜索地图名称或编码 (如 MAP_PLANT_TOTAL)..."
        @search="handleFilter"
        @reset="handleReset"
      >
        <div class="filter-field">
          <label class="filter-label">底图渲染类型</label>
          <select v-model="bgFilter" class="filter-select" @change="handleFilter">
            <option value="">全部底图类型</option>
            <option value="SVG">矢量图形 (SVG)</option>
            <option value="IMAGE">光栅图片 (IMAGE)</option>
            <option value="GRID">网格坐标 (GRID)</option>
          </select>
        </div>

        <div class="filter-field">
          <label class="filter-label">状态模拟</label>
          <select v-model="simulateState" class="filter-select" @change="loadMapList">
            <option value="normal">正常 (Ready)</option>
            <option value="empty">空列表 (Empty)</option>
            <option value="error">网络异常 (Error)</option>
          </select>
        </div>
      </FilterBar>

      <!-- 主表格数据区 -->
      <div class="table-card">
        <div v-if="viewState === 'loading'" class="loading-box">
          <span class="spinner">⏳</span>
          <span>正在装载租户地图投影列表...</span>
        </div>

        <div v-else-if="viewState === 'error'" class="error-box">
          <ErrorState
            title="站点地图加载失败"
            :message="errorMessage"
            code="GIS_QUERY_002"
            @retry="loadMapList"
          />
        </div>

        <div v-else-if="viewState === 'empty' || filteredMaps.length === 0" class="empty-box">
          <EmptyState
            icon="🗺️"
            title="暂无配置的二维站点地图"
            description="当前租户尚未创建任何厂区或库房底图，点击上方按钮即可新增。"
          >
            <template #action>
              <button type="button" class="btn-create-map" @click="openCreateModal">
                立即新建地图
              </button>
            </template>
          </EmptyState>
        </div>

        <DataTable
          v-else
          :columns="tableColumns"
          :data="filteredMaps"
          row-key="id"
          :show-pagination="false"
        >
          <!-- 地图编码 -->
          <template #mapCode="{ value }">
            <span class="code-text">{{ value }}</span>
          </template>

          <!-- 地图名称 -->
          <template #mapName="{ row }">
            <div class="map-name-cell">
              <span class="map-icon">🗺️</span>
              <div class="map-name-meta">
                <strong class="name-text">{{ row.mapName }}</strong>
                <span class="desc-text">{{ row.description }}</span>
              </div>
            </div>
          </template>

          <!-- 底图类型 -->
          <template #backgroundType="{ value }">
            <StatusBadge
              :type="value === 'SVG' ? 'primary' : 'info'"
              :text="value"
            />
          </template>

          <!-- 点位数量 -->
          <template #pointCount="{ value }">
            <QuantityText :value="value" unit="个点位" />
          </template>

          <!-- 操作列 -->
          <template #actions="{ row }">
            <div class="action-buttons">
              <button
                type="button"
                class="btn-action primary"
                title="打开只读二维空间监控画布"
                @click="handleViewMap(row.id)"
              >
                <span>🖥️ 空间监控</span>
              </button>
              <button
                type="button"
                class="btn-action edit"
                title="配置与调整相对百分比点位"
                @click="handleEditMap(row.id)"
              >
                <span>✏️ 点位配置</span>
              </button>
            </div>
          </template>
        </DataTable>
      </div>

      <!-- 新建地图弹窗 (ConfirmDialog 封装) -->
      <ConfirmDialog
        v-model:visible="showCreateModal"
        title="新建二维站点底图配置"
        confirm-text="确认创建"
        cancel-text="取消"
        :loading="isSubmitting"
        @confirm="submitCreateMap"
      >
        <div class="create-form">
          <div class="form-item">
            <label class="form-label required">地图编码 (Map Code)</label>
            <input
              v-model="createFormData.mapCode"
              type="text"
              class="form-input"
              placeholder="例如: MAP_WORKSHOP_01"
            />
          </div>

          <div class="form-item">
            <label class="form-label required">地图名称 (Map Name)</label>
            <input
              v-model="createFormData.mapName"
              type="text"
              class="form-input"
              placeholder="例如: 智能制造二车间平面图"
            />
          </div>

          <div class="form-item">
            <label class="form-label required">底图类型</label>
            <select v-model="createFormData.backgroundType" class="form-select">
              <option value="SVG">矢量矢量底图 (SVG 预设)</option>
              <option value="IMAGE">静态图片资源 (IMAGE)</option>
              <option value="GRID">工业网格底图 (GRID)</option>
            </select>
          </div>

          <div class="form-item">
            <label class="form-label">用途描述说明</label>
            <textarea
              v-model="createFormData.description"
              class="form-textarea"
              rows="3"
              placeholder="说明底图适用的厂区、库区或生产线范围..."
            ></textarea>
          </div>

          <p v-if="formValidationError" class="form-error-tip">
            ⚠️ {{ formValidationError }}
          </p>
        </div>
      </ConfirmDialog>
    </div>
  </div>
</template>

<script setup lang="ts">
/**
 * 二维站点地图列表与集成工作台视图 (SiteMapListView)
 * 职责：
 * 1. 展示当前租户配置的多张二维底图；
 * 2. 提供空间监控视图 (SiteMapView) 与点位编辑器 (SiteMapEditorView) 无缝集成切换；
 * 3. 支持创建新底图配置；
 * 4. 严格遵循租户隔离与四态规范。
 */

import { ref, reactive, computed, onMounted } from "vue";
import type { SiteMapItem } from "../../types/insights";
import type { ViewState } from "../../types/common";
import { fetchSiteMapList, createSiteMap } from "../../api/insights";
import PageHeader from "../../components/common/PageHeader.vue";
import FilterBar from "../../components/common/FilterBar.vue";
import DataTable, { type TableColumn } from "../../components/common/DataTable.vue";
import StatusBadge from "../../components/common/StatusBadge.vue";
import QuantityText from "../../components/common/QuantityText.vue";
import EmptyState from "../../components/common/EmptyState.vue";
import ErrorState from "../../components/common/ErrorState.vue";
import ConfirmDialog from "../../components/common/ConfirmDialog.vue";
import SiteMapView from "./SiteMapView.vue";
import SiteMapEditorView from "./SiteMapEditorView.vue";

const emit = defineEmits<{
  (e: "view-map", mapId: string | number): void;
  (e: "edit-map", mapId: string | number): void;
  (e: "navigate-trace"): void;
  (e: "navigate-dashboard"): void;
}>();

// 子模式：列表 (list)、空间监控 (viewer)、点位配置 (editor)
const currentMode = ref<"list" | "viewer" | "editor">("list");
const selectedMapId = ref<string | number>("MAP-001");

// 界面状态
const viewState = ref<ViewState>("loading");
const errorMessage = ref<string>("");
const mapList = ref<SiteMapItem[]>([]);
const searchKeyword = ref<string>("");
const bgFilter = ref<string>("");
const simulateState = ref<"normal" | "empty" | "error">("normal");

// 弹窗状态
const showCreateModal = ref(false);
const isSubmitting = ref(false);
const formValidationError = ref("");
const createFormData = reactive({
  mapCode: "",
  mapName: "",
  backgroundType: "SVG" as "SVG" | "IMAGE" | "GRID",
  description: "",
});

// 表格列定义
const tableColumns: TableColumn[] = [
  { key: "mapCode", label: "地图编码", width: "160px" },
  { key: "mapName", label: "地图名称与描述", minWidth: "240px" },
  { key: "backgroundType", label: "底图类型", width: "120px", align: "center" },
  { key: "pointCount", label: "已配置点位", width: "130px", align: "right" },
  { key: "updatedAt", label: "更新时间", width: "180px" },
  { key: "actions", label: "操作入口", width: "220px", align: "center" },
];

/**
 * 装载地图列表数据
 */
async function loadMapList() {
  viewState.value = "loading";
  errorMessage.value = "";

  if (simulateState.value === "error") {
    viewState.value = "error";
    errorMessage.value = "GIS_QUERY_002: 二维空间地图配置库响应超时";
    return;
  }
  if (simulateState.value === "empty") {
    mapList.value = [];
    viewState.value = "empty";
    return;
  }

  try {
    const list = await fetchSiteMapList();
    mapList.value = list;
    viewState.value = list.length === 0 ? "empty" : "ready";
  } catch (err: any) {
    viewState.value = "error";
    errorMessage.value = err?.message || "地图列表装载失败";
  }
}

/**
 * 筛选后的地图列表
 */
const filteredMaps = computed(() => {
  return mapList.value.filter((m) => {
    if (bgFilter.value && m.backgroundType !== bgFilter.value) return false;
    if (searchKeyword.value) {
      const kw = searchKeyword.value.toLowerCase().trim();
      return (
        m.mapCode.toLowerCase().includes(kw) ||
        m.mapName.toLowerCase().includes(kw) ||
        (m.description && m.description.toLowerCase().includes(kw))
      );
    }
    return true;
  });
});

function handleFilter() {
  // computed handles filtering
}

function handleReset() {
  searchKeyword.value = "";
  bgFilter.value = "";
  simulateState.value = "normal";
  loadMapList();
}

/**
 * 打开新建弹窗
 */
function openCreateModal() {
  formValidationError.value = "";
  createFormData.mapCode = `MAP_${Date.now().toString().slice(-5)}`;
  createFormData.mapName = "";
  createFormData.backgroundType = "SVG";
  createFormData.description = "";
  showCreateModal.value = true;
}

/**
 * 提交新建地图
 */
async function submitCreateMap() {
  if (!createFormData.mapCode.trim()) {
    formValidationError.value = "请输入合法的地图编码";
    return;
  }
  if (!createFormData.mapName.trim()) {
    formValidationError.value = "请输入地图名称";
    return;
  }

  isSubmitting.value = true;
  formValidationError.value = "";

  try {
    await createSiteMap({
      mapCode: createFormData.mapCode.trim(),
      mapName: createFormData.mapName.trim(),
      backgroundType: createFormData.backgroundType,
      description: createFormData.description.trim(),
    });
    showCreateModal.value = false;
    await loadMapList();
  } catch (err: any) {
    formValidationError.value = err?.message || "创建地图失败";
  } finally {
    isSubmitting.value = false;
  }
}

function handleViewMap(id: string | number) {
  selectedMapId.value = id;
  currentMode.value = "viewer";
  emit("view-map", id);
}

function handleEditMap(id: string | number) {
  selectedMapId.value = id;
  currentMode.value = "editor";
  emit("edit-map", id);
}

function handleNavigateTrace() {
  emit("navigate-trace");
}

onMounted(() => {
  loadMapList();
});
</script>

<style scoped>
.sitemap-list-container {
  padding: 20px 28px 40px;
  max-width: 1400px;
  margin: 0 auto;
}

.nav-sub-tabs {
  display: flex;
  align-items: center;
  gap: 8px;
  background: rgba(30, 41, 59, 0.6);
  padding: 4px;
  border-radius: 8px;
  border: 1px solid rgba(255, 255, 255, 0.08);
}

.tab-btn {
  padding: 6px 14px;
  border-radius: 6px;
  font-size: 12px;
  font-weight: 500;
  color: #94a3b8;
  border: none;
  background: transparent;
  cursor: pointer;
  text-decoration: none;
  transition: all 0.2s;
}

.tab-btn:hover {
  color: #f1f5f9;
}

.tab-btn.is-active {
  background: #0284c7;
  color: #ffffff;
}

.btn-create-map {
  padding: 7px 16px;
  background: #0284c7;
  border: 1px solid #0369a1;
  color: #ffffff;
  border-radius: 6px;
  font-size: 13px;
  font-weight: 500;
  cursor: pointer;
  transition: all 0.2s;
}

.btn-create-map:hover {
  background: #0369a1;
}

.filter-field {
  display: flex;
  align-items: center;
  gap: 8px;
}

.filter-label {
  font-size: 12px;
  color: #94a3b8;
  white-space: nowrap;
}

.filter-select {
  background: rgba(30, 41, 59, 0.8);
  border: 1px solid rgba(255, 255, 255, 0.12);
  color: #f8fafc;
  padding: 6px 10px;
  border-radius: 6px;
  font-size: 12px;
  outline: none;
}

.table-card {
  margin-top: 16px;
}

.loading-box,
.empty-box,
.error-box {
  padding: 60px 20px;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 12px;
  color: #94a3b8;
  background: rgba(15, 23, 42, 0.6);
  border: 1px solid rgba(255, 255, 255, 0.08);
  border-radius: 8px;
}

.code-text {
  font-family: var(--font-mono, monospace);
  color: #38bdf8;
  font-weight: 600;
}

.map-name-cell {
  display: flex;
  align-items: center;
  gap: 12px;
}

.map-icon {
  font-size: 24px;
}

.map-name-meta {
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.name-text {
  color: #f1f5f9;
  font-size: 14px;
}

.desc-text {
  color: #94a3b8;
  font-size: 11px;
}

.action-buttons {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
}

.btn-action {
  padding: 5px 10px;
  border-radius: 4px;
  font-size: 11px;
  font-weight: 500;
  cursor: pointer;
  transition: all 0.2s;
  border: 1px solid transparent;
}

.btn-action.primary {
  background: rgba(56, 189, 248, 0.12);
  border-color: rgba(56, 189, 248, 0.3);
  color: #38bdf8;
}

.btn-action.primary:hover {
  background: rgba(56, 189, 248, 0.25);
  color: #ffffff;
}

.btn-action.edit {
  background: rgba(148, 163, 184, 0.12);
  border-color: rgba(148, 163, 184, 0.25);
  color: #cbd5e1;
}

.btn-action.edit:hover {
  background: rgba(148, 163, 184, 0.25);
  color: #ffffff;
}

/* 创建弹窗表单 */
.create-form {
  display: flex;
  flex-direction: column;
  gap: 14px;
}

.form-item {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.form-label {
  font-size: 12px;
  color: #94a3b8;
}

.form-label.required::after {
  content: " *";
  color: #f87171;
}

.form-input,
.form-select,
.form-textarea {
  background: rgba(30, 41, 59, 0.8);
  border: 1px solid rgba(255, 255, 255, 0.15);
  border-radius: 6px;
  color: #f8fafc;
  padding: 8px 12px;
  font-size: 13px;
  outline: none;
  font-family: inherit;
}

.form-input:focus,
.form-select:focus,
.form-textarea:focus {
  border-color: #38bdf8;
}

.form-error-tip {
  margin: 0;
  font-size: 12px;
  color: #f87171;
}
</style>
