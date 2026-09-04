<template>
  <div class="sitemap-editor-container">
    <!-- 顶部统一操作头 -->
    <PageHeader
      title="二维站点地图点位配置编辑器"
      tag="GIS / MAP POINT CONFIGURATION"
      description="配置与标定厂区空间底图点位。在画布上点击可快速捕获相对百分比坐标 (x%, y%)；点位修改严格保存展示参数，不修改源业务事实。"
    >
      <template #actions>
        <button type="button" class="btn-return" @click="$emit('back-map')">
          <span>🖥️ 返回地图监控</span>
        </button>
        <button type="button" class="btn-return-list" @click="$emit('back-list')">
          <span>☰ 地图列表</span>
        </button>
        <button type="button" class="btn-save-all" :disabled="isSaving" @click="handleSaveCurrentPoint">
          <span v-if="isSaving" class="spinner">⏳</span>
          <span>{{ editingPointId ? "保存点位变更" : "＋ 新增当前点位" }}</span>
        </button>
      </template>
    </PageHeader>

    <!-- 操作提示条 -->
    <div class="editor-notice-bar">
      <span class="notice-icon">💡</span>
      <div class="notice-content">
        <strong>交互提示：</strong>
        <span>直接在下方底图任意位置点击，可自动将十字星光标定位并换算为防漂移百分比坐标 (0% - 100%)。</span>
      </div>
      <span class="curr-coord-tag">
        当前标定坐标: X: <strong>{{ activeForm.xPercent }}%</strong>, Y: <strong>{{ activeForm.yPercent }}%</strong>
      </span>
    </div>

    <!-- 主编辑工作区分块：左侧交互画布，右侧点位参数表单 -->
    <div class="editor-grid">
      <!-- 1. 交互点击拾取画布 -->
      <div class="canvas-editor-panel">
        <div class="canvas-header">
          <span class="canvas-title">点位拾取工作画布 (Plant Floor Canvas)</span>
          <span class="tip-text">点击底图任意区域重设点位位置</span>
        </div>

        <div
          ref="canvasRef"
          class="interactive-canvas"
          @click="handleCanvasClick"
        >
          <!-- SVG 底图 -->
          <svg
            class="map-svg-background"
            viewBox="0 0 1000 600"
            preserveAspectRatio="none"
            xmlns="http://www.w3.org/2000/svg"
          >
            <defs>
              <pattern id="editor-grid" width="40" height="40" patternUnits="userSpaceOnUse">
                <path d="M 40 0 L 0 0 0 40" fill="none" stroke="rgba(148, 185, 198, 0.08)" stroke-width="1" />
              </pattern>
            </defs>
            <rect width="100%" height="100%" fill="url(#editor-grid)" />
            <rect x="5%" y="10%" width="30%" height="38%" rx="8" fill="rgba(56, 189, 248, 0.05)" stroke="rgba(56, 189, 248, 0.25)" stroke-dasharray="4 4" />
            <text x="7%" y="16%" fill="#38bdf8" font-size="13" font-weight="700">成品立体库区 (WH-FG-01)</text>

            <rect x="5%" y="52%" width="30%" height="40%" rx="8" fill="rgba(56, 189, 248, 0.05)" stroke="rgba(56, 189, 248, 0.25)" stroke-dasharray="4 4" />
            <text x="7%" y="58%" fill="#38bdf8" font-size="13" font-weight="700">原料立体仓 (WH-RM-01)</text>

            <rect x="40%" y="10%" width="55%" height="82%" rx="8" fill="rgba(45, 212, 191, 0.04)" stroke="rgba(45, 212, 191, 0.22)" stroke-dasharray="4 4" />
            <text x="42%" y="16%" fill="#2dd4bf" font-size="13" font-weight="700">智能制造核心车间 (AREA-PROD)</text>
          </svg>

          <!-- 现有已配置点位标识 -->
          <div
            v-for="pt in existingPoints"
            :key="pt.id"
            class="existing-pin"
            :class="{ 'is-editing': pt.id === editingPointId }"
            :style="{ left: `${pt.xPercent}%`, top: `${pt.yPercent}%` }"
            :title="`已配置点位: ${pt.pointName}`"
            @click.stop="loadPointToEdit(pt)"
          >
            <span class="existing-dot"></span>
            <span class="existing-title">{{ pt.pointName }}</span>
          </div>

          <!-- 当前正在编辑/拾取的新十字星标记 -->
          <div
            class="active-cursor-marker"
            :style="{ left: `${activeForm.xPercent}%`, top: `${activeForm.yPercent}%` }"
          >
            <div class="marker-cross-h"></div>
            <div class="marker-cross-v"></div>
            <div class="marker-pulse-ring"></div>
            <span class="marker-tag">正在标定 ({{ activeForm.xPercent }}%, {{ activeForm.yPercent }}%)</span>
          </div>
        </div>
      </div>

      <!-- 2. 点位属性表单编辑面板 -->
      <div class="form-editor-panel">
        <div class="form-header">
          <h3 class="form-title">
            {{ editingPointId ? "编辑现有点位属性" : "配置新空间点位" }}
          </h3>
          <button v-if="editingPointId" type="button" class="btn-clear-edit" @click="resetToNew">
            重置为新建
          </button>
        </div>

        <div class="editor-form">
          <div class="form-row">
            <label class="form-label required">点位展示名称 (Point Name)</label>
            <input
              v-model="activeForm.pointName"
              type="text"
              class="form-input"
              placeholder="例如: 伺服冲压机 DEV-A01"
            />
          </div>

          <div class="form-grid-two">
            <div class="form-row">
              <label class="form-label required">实体类型</label>
              <select v-model="activeForm.entityType" class="form-select">
                <option value="DEVICE">生产设备 (DEVICE)</option>
                <option value="WAREHOUSE">仓库库区 (WAREHOUSE)</option>
                <option value="PRODUCTION_AREA">车间区域 (AREA)</option>
              </select>
            </div>

            <div class="form-row">
              <label class="form-label required">源实体编码 (Entity ID)</label>
              <input
                v-model="activeForm.entityId"
                type="text"
                class="form-input"
                placeholder="例如: DEV-A01 / WH-FG-01"
              />
            </div>
          </div>

          <div class="form-grid-two">
            <div class="form-row">
              <label class="form-label required">水平坐标 X (0-100%)</label>
              <input
                v-model.number="activeForm.xPercent"
                type="number"
                min="0"
                max="100"
                step="0.5"
                class="form-input font-mono"
              />
            </div>

            <div class="form-row">
              <label class="form-label required">垂直坐标 Y (0-100%)</label>
              <input
                v-model.number="activeForm.yPercent"
                type="number"
                min="0"
                max="100"
                step="0.5"
                class="form-input font-mono"
              />
            </div>
          </div>

          <div class="form-grid-two">
            <div class="form-row">
              <label class="form-label">旋转角度 (°)</label>
              <select v-model.number="activeForm.rotation" class="form-select font-mono">
                <option :value="0">0° (正向)</option>
                <option :value="90">90°</option>
                <option :value="180">180°</option>
                <option :value="270">270°</option>
              </select>
            </div>

            <div class="form-row">
              <label class="form-label">穿透业务路由</label>
              <input
                v-model="activeForm.linkedPage"
                type="text"
                class="form-input font-mono"
                placeholder="例如: /iot, /erp-wms, /mes"
              />
            </div>
          </div>

          <div class="form-row">
            <label class="form-label">业务实体描述说明</label>
            <textarea
              v-model="activeForm.detail"
              class="form-textarea"
              rows="3"
              placeholder="说明该点位所承载的工艺工序、物料品类或设备职能..."
            ></textarea>
          </div>

          <p v-if="validationError" class="validation-error">
            ⚠️ {{ validationError }}
          </p>

          <p v-if="feedbackSuccess" class="feedback-success">
            ✓ {{ feedbackSuccess }}
          </p>

          <div class="form-actions">
            <button
              type="button"
              class="btn-submit"
              :disabled="isSaving"
              @click="handleSaveCurrentPoint"
            >
              <span>{{ editingPointId ? "确认更新点位" : "保存并添加到地图" }}</span>
            </button>
          </div>
        </div>
      </div>
    </div>

    <!-- 底部已配置点位管理表格 (复用 DataTable) -->
    <div class="configured-points-card">
      <div class="points-card-header">
        <h3 class="points-card-title">当前底图已配置点位清单 ({{ existingPoints.length }} 个)</h3>
        <span class="points-card-tip">支持直接在此点击编辑或逻辑软删除</span>
      </div>

      <DataTable
        :columns="pointTableColumns"
        :data="existingPoints"
        row-key="id"
        :show-pagination="false"
      >
        <template #entityType="{ value }">
          <StatusBadge type="info" :text="value" />
        </template>

        <template #xPercent="{ value }">
          <span class="font-mono text-blue">{{ value }}%</span>
        </template>

        <template #yPercent="{ value }">
          <span class="font-mono text-blue">{{ value }}%</span>
        </template>

        <template #displayStatus="{ value }">
          <StatusBadge
            :type="value === 'Alarm' ? 'danger' : value === 'Warning' ? 'warning' : 'success'"
            :text="value"
          />
        </template>

        <template #actions="{ row }">
          <div class="table-actions">
            <button type="button" class="btn-tbl-edit" @click="loadPointToEdit(row)">
              编辑
            </button>
            <button type="button" class="btn-tbl-del" @click="askDeletePoint(row)">
              删除
            </button>
          </div>
        </template>
      </DataTable>
    </div>

    <!-- 删除确认弹窗 (ConfirmDialog) -->
    <ConfirmDialog
      v-model:visible="showDeleteDialog"
      title="删除空间点位确认"
      :message="`确定要删除点位【${pointToDelete?.pointName}】吗？删除仅移除该空间展示配置，不会影响设备或仓库源实体的事实数据。`"
      confirm-text="确认移除"
      cancel-text="取消"
      :danger="true"
      @confirm="confirmDeletePoint"
    />
  </div>
</template>

<script setup lang="ts">
/**
 * 二维站点地图点位配置编辑器 (SiteMapEditorView)
 * 职责：
 * 1. 允许管理员/IoT工程师在二维底图上标定点位；
 * 2. 画布交互点击精确计算相对百分比坐标 (x%, y%)，绝对防漂移；
 * 3. 校验百分比合法性 [0, 100] 与同租户实体引用约束；
 * 4. 支持点位创建、修改、逻辑移除；
 * 5. 复用 PageHeader, DataTable, StatusBadge, ConfirmDialog。
 */

import { ref, reactive, onMounted } from "vue";
import type { MapPoint, MapEntityType, MapPointStatus } from "../../types/insights";
import { fetchSiteMapProjection, saveMapPoint, deleteMapPoint } from "../../api/insights";
import PageHeader from "../../components/common/PageHeader.vue";
import DataTable, { type TableColumn } from "../../components/common/DataTable.vue";
import StatusBadge from "../../components/common/StatusBadge.vue";
import ConfirmDialog from "../../components/common/ConfirmDialog.vue";

const props = withDefaults(
  defineProps<{
    mapId?: string | number;
  }>(),
  {
    mapId: "MAP-001",
  }
);

defineEmits<{
  (e: "back-map"): void;
  (e: "back-list"): void;
}>();

const canvasRef = ref<HTMLDivElement | null>(null);
const existingPoints = ref<MapPoint[]>([]);
const editingPointId = ref<string | null>(null);
const isSaving = ref(false);
const validationError = ref("");
const feedbackSuccess = ref("");

// 待删除点位与弹窗
const showDeleteDialog = ref(false);
const pointToDelete = ref<MapPoint | null>(null);

// 编辑中表单
const activeForm = reactive<{
  pointName: string;
  entityType: MapEntityType;
  entityId: string;
  xPercent: number;
  yPercent: number;
  rotation: number;
  linkedPage: string;
  detail: string;
  displayStatus: MapPointStatus;
}>({
  pointName: "",
  entityType: "DEVICE",
  entityId: "",
  xPercent: 50,
  yPercent: 50,
  rotation: 0,
  linkedPage: "/iot",
  detail: "",
  displayStatus: "Normal",
});

// 表格列
const pointTableColumns: TableColumn[] = [
  { key: "pointName", label: "点位名称", minWidth: "180px" },
  { key: "entityType", label: "实体类型", width: "130px" },
  { key: "entityId", label: "实体编码", width: "130px" },
  { key: "xPercent", label: "水平 X", width: "100px", align: "right" },
  { key: "yPercent", label: "垂直 Y", width: "100px", align: "right" },
  { key: "displayStatus", label: "展示状态", width: "110px", align: "center" },
  { key: "linkedPage", label: "穿透页面", width: "120px" },
  { key: "actions", label: "操作", width: "140px", align: "center" },
];

/**
 * 装载已有地图点位
 */
async function loadPoints() {
  try {
    const proj = await fetchSiteMapProjection({ siteMapId: props.mapId });
    existingPoints.value = proj.points || [];
  } catch (err) {
    console.error("加载点位配置失败", err);
  }
}

/**
 * 画布点击换算百分比坐标
 */
function handleCanvasClick(event: MouseEvent) {
  if (!canvasRef.value) return;
  const rect = canvasRef.value.getBoundingClientRect();
  const clickX = event.clientX - rect.left;
  const clickY = event.clientY - rect.top;

  const pctX = Math.round((clickX / rect.width) * 1000) / 10;
  const pctY = Math.round((clickY / rect.height) * 1000) / 10;

  activeForm.xPercent = Math.max(0, Math.min(100, pctX));
  activeForm.yPercent = Math.max(0, Math.min(100, pctY));
}

/**
 * 载入某点位至表单进行编辑
 */
function loadPointToEdit(pt: MapPoint) {
  editingPointId.value = pt.id;
  activeForm.pointName = pt.pointName;
  activeForm.entityType = pt.entityType;
  activeForm.entityId = pt.entityId;
  activeForm.xPercent = pt.xPercent;
  activeForm.yPercent = pt.yPercent;
  activeForm.rotation = pt.rotation || 0;
  activeForm.linkedPage = pt.linkedPage || "";
  activeForm.detail = pt.detail || "";
  activeForm.displayStatus = pt.displayStatus;
  validationError.value = "";
  feedbackSuccess.value = "";
}

/**
 * 重置为新建模式
 */
function resetToNew() {
  editingPointId.value = null;
  activeForm.pointName = "";
  activeForm.entityId = "";
  activeForm.xPercent = 50;
  activeForm.yPercent = 50;
  activeForm.rotation = 0;
  activeForm.detail = "";
  validationError.value = "";
  feedbackSuccess.value = "";
}

/**
 * 保存点位变更
 */
async function handleSaveCurrentPoint() {
  validationError.value = "";
  feedbackSuccess.value = "";

  if (!activeForm.pointName.trim()) {
    validationError.value = "请输入点位展示名称";
    return;
  }
  if (!activeForm.entityId.trim()) {
    validationError.value = "请输入所关联的业务实体编码";
    return;
  }
  if (
    activeForm.xPercent < 0 ||
    activeForm.xPercent > 100 ||
    activeForm.yPercent < 0 ||
    activeForm.yPercent > 100
  ) {
    validationError.value = "坐标百分比必须处于 0% 至 100% 范围内";
    return;
  }

  isSaving.value = true;
  try {
    const saved = await saveMapPoint({
      id: editingPointId.value || undefined,
      siteMapId: props.mapId,
      pointName: activeForm.pointName.trim(),
      entityType: activeForm.entityType,
      entityId: activeForm.entityId.trim(),
      xPercent: activeForm.xPercent,
      yPercent: activeForm.yPercent,
      rotation: activeForm.rotation,
      linkedPage: activeForm.linkedPage.trim(),
      detail: activeForm.detail.trim(),
      displayStatus: activeForm.displayStatus,
    });

    feedbackSuccess.value = `点位【${saved.pointName}】配置已成功保存！`;
    await loadPoints();
    if (!editingPointId.value) {
      resetToNew();
    }
  } catch (err: any) {
    validationError.value = err?.message || "保存点位配置失败";
  } finally {
    isSaving.value = false;
  }
}

/**
 * 触发删除点位询问
 */
function askDeletePoint(pt: MapPoint) {
  pointToDelete.value = pt;
  showDeleteDialog.value = true;
}

/**
 * 确认删除点位
 */
async function confirmDeletePoint() {
  if (!pointToDelete.value) return;
  try {
    await deleteMapPoint(pointToDelete.value.id);
    showDeleteDialog.value = false;
    pointToDelete.value = null;
    await loadPoints();
  } catch (err) {
    console.error("删除点位失败", err);
  }
}

onMounted(() => {
  loadPoints();
});
</script>

<style scoped>
.sitemap-editor-container {
  padding: 20px 28px 40px;
  max-width: 1560px;
  margin: 0 auto;
}

.btn-return,
.btn-return-list,
.btn-save-all {
  padding: 7px 14px;
  border-radius: 6px;
  font-size: 12px;
  font-weight: 500;
  cursor: pointer;
  transition: all 0.2s;
}

.btn-return,
.btn-return-list {
  background: rgba(51, 65, 85, 0.6);
  color: #cbd5e1;
  border: 1px solid rgba(255, 255, 255, 0.1);
}

.btn-return:hover,
.btn-return-list:hover {
  background: rgba(71, 85, 105, 0.8);
  color: #fff;
}

.btn-save-all {
  background: #0284c7;
  color: #ffffff;
  border: 1px solid #0369a1;
}

.btn-save-all:hover:not(:disabled) {
  background: #0369a1;
}

.editor-notice-bar {
  display: flex;
  align-items: center;
  gap: 12px;
  background: rgba(56, 189, 248, 0.08);
  border: 1px solid rgba(56, 189, 248, 0.25);
  border-radius: 8px;
  padding: 10px 16px;
  margin-bottom: 20px;
  font-size: 13px;
  color: #e2e8f0;
}

.curr-coord-tag {
  margin-left: auto;
  font-family: var(--font-mono, monospace);
  font-size: 12px;
  color: #38bdf8;
  background: rgba(0, 0, 0, 0.3);
  padding: 4px 10px;
  border-radius: 4px;
}

.editor-grid {
  display: grid;
  grid-template-columns: 1fr 440px;
  gap: 20px;
  margin-bottom: 24px;
}

@media (max-width: 1100px) {
  .editor-grid {
    grid-template-columns: 1fr;
  }
}

.canvas-editor-panel {
  background: rgba(15, 23, 42, 0.85);
  border: 1px solid rgba(255, 255, 255, 0.1);
  border-radius: 10px;
  display: flex;
  flex-direction: column;
  overflow: hidden;
}

.canvas-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 12px 18px;
  background: rgba(30, 41, 59, 0.5);
  border-bottom: 1px solid rgba(255, 255, 255, 0.08);
}

.canvas-title {
  font-size: 13px;
  font-weight: 600;
  color: #f1f5f9;
}

.tip-text {
  font-size: 11px;
  color: #94a3b8;
}

.interactive-canvas {
  position: relative;
  min-height: 520px;
  background: radial-gradient(circle at 50% 50%, #0d1b24, #060e15);
  cursor: crosshair;
  overflow: hidden;
  user-select: none;
}

.map-svg-background {
  position: absolute;
  inset: 0;
  width: 100%;
  height: 100%;
  pointer-events: none;
}

/* 已配置点位图标 */
.existing-pin {
  position: absolute;
  transform: translate(-50%, -50%);
  display: flex;
  align-items: center;
  gap: 4px;
  padding: 4px 8px;
  background: rgba(15, 23, 42, 0.9);
  border: 1px solid rgba(255, 255, 255, 0.2);
  border-radius: 12px;
  font-size: 10px;
  color: #cbd5e1;
  cursor: pointer;
  transition: all 0.15s;
  z-index: 5;
}

.existing-pin:hover,
.existing-pin.is-editing {
  border-color: #38bdf8;
  box-shadow: 0 0 10px rgba(56, 189, 248, 0.5);
  z-index: 10;
  background: rgba(7, 16, 23, 0.95);
}

.existing-dot {
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: #38bdf8;
}

/* 正在拾取的十字星光标 */
.active-cursor-marker {
  position: absolute;
  transform: translate(-50%, -50%);
  pointer-events: none;
  z-index: 20;
}

.marker-cross-h {
  position: absolute;
  left: -14px;
  top: 0;
  width: 28px;
  height: 2px;
  background: #38bdf8;
}

.marker-cross-v {
  position: absolute;
  left: 0;
  top: -14px;
  width: 2px;
  height: 28px;
  background: #38bdf8;
}

.marker-pulse-ring {
  position: absolute;
  left: -8px;
  top: -8px;
  width: 16px;
  height: 16px;
  border: 2px solid #38bdf8;
  border-radius: 50%;
  animation: pulse-ring 1.5s infinite;
}

.marker-tag {
  position: absolute;
  top: 14px;
  left: 50%;
  transform: translateX(-50%);
  background: rgba(15, 23, 42, 0.9);
  border: 1px solid #38bdf8;
  color: #38bdf8;
  font-size: 10px;
  font-family: var(--font-mono, monospace);
  padding: 1px 6px;
  border-radius: 4px;
  white-space: nowrap;
}

/* 表单面板 */
.form-editor-panel {
  background: rgba(15, 23, 42, 0.85);
  border: 1px solid rgba(255, 255, 255, 0.1);
  border-radius: 10px;
  padding: 20px;
  display: flex;
  flex-direction: column;
}

.form-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 16px;
  padding-bottom: 12px;
  border-bottom: 1px solid rgba(255, 255, 255, 0.08);
}

.form-title {
  font-size: 15px;
  color: #f1f5f9;
  margin: 0;
}

.btn-clear-edit {
  font-size: 11px;
  color: #38bdf8;
  background: transparent;
  border: none;
  cursor: pointer;
}

.editor-form {
  display: flex;
  flex-direction: column;
  gap: 14px;
}

.form-row {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.form-grid-two {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 12px;
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
  border: 1px solid rgba(255, 255, 255, 0.12);
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

.font-mono {
  font-family: var(--font-mono, monospace);
}

.validation-error {
  font-size: 12px;
  color: #f87171;
  margin: 0;
}

.feedback-success {
  font-size: 12px;
  color: #34d399;
  margin: 0;
}

.form-actions {
  margin-top: 10px;
}

.btn-submit {
  width: 100%;
  padding: 10px;
  background: #0284c7;
  color: #ffffff;
  border: none;
  border-radius: 6px;
  font-size: 13px;
  font-weight: 600;
  cursor: pointer;
  transition: all 0.2s;
}

.btn-submit:hover:not(:disabled) {
  background: #0369a1;
}

/* 底部已配置点位管理 */
.configured-points-card {
  background: rgba(15, 23, 42, 0.85);
  border: 1px solid rgba(255, 255, 255, 0.1);
  border-radius: 10px;
  padding: 20px;
}

.points-card-header {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  margin-bottom: 14px;
}

.points-card-title {
  font-size: 15px;
  color: #f1f5f9;
  margin: 0;
}

.points-card-tip {
  font-size: 12px;
  color: #94a3b8;
}

.text-blue {
  color: #38bdf8;
}

.table-actions {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
}

.btn-tbl-edit,
.btn-tbl-del {
  padding: 4px 10px;
  border-radius: 4px;
  font-size: 11px;
  cursor: pointer;
  border: 1px solid transparent;
}

.btn-tbl-edit {
  background: rgba(56, 189, 248, 0.12);
  color: #38bdf8;
  border-color: rgba(56, 189, 248, 0.3);
}

.btn-tbl-del {
  background: rgba(239, 68, 68, 0.12);
  color: #f87171;
  border-color: rgba(239, 68, 68, 0.3);
}

@keyframes pulse-ring {
  0% {
    transform: scale(0.6);
    opacity: 1;
  }
  100% {
    transform: scale(1.6);
    opacity: 0;
  }
}
</style>
