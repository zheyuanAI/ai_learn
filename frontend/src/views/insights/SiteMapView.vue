<template>
  <div class="site-map-view-container">
    <!-- 顶部统一导航与操作头 -->
    <PageHeader
      title="厂区二维空间拓扑地图"
      tag="GIS / 2D SPATIAL RUNTIME"
      description="基于受控矢量底图与百分比坐标 (x%, y%) 渲染空间点位，杜绝容器缩放偏移。状态展示优先级严格遵循“告警 (Alarm) > 离线 (Offline) > 预警 (Warning) > 正常 (Normal)”，支持穿透业务控制台。"
    >
      <template #actions>
        <div class="nav-sub-tabs">
          <RouterLink to="/gis" class="tab-btn">
            <span>📊 综合看板</span>
          </RouterLink>
          <button type="button" class="tab-btn is-active">
            <span>🗺️ 站点地图</span>
          </button>
          <button type="button" class="tab-btn" @click="$emit('navigate-trace')">
            <span>🔍 闭环追溯</span>
          </button>
        </div>

        <button type="button" class="btn-back-list" @click="$emit('back-list')">
          <span>☰ 地图列表</span>
        </button>

        <button type="button" class="btn-go-editor" @click="$emit('edit-map', currentMapId)">
          <span>✏️ 编辑点位</span>
        </button>
      </template>
    </PageHeader>

    <!-- 空间图层过滤与模拟控制器 -->
    <div class="map-control-bar">
      <div class="filter-group">
        <span class="control-label">图层过滤：</span>
        <div class="scenario-tabs">
          <button
            type="button"
            class="tab-pill"
            :class="{ 'is-active': activeFilter === 'all' }"
            @click="setFilter('all')"
          >
            全部点位 ({{ totalPointsCount }})
          </button>
          <button
            type="button"
            class="tab-pill alarm-pill"
            :class="{ 'is-active': activeFilter === 'alarm' }"
            @click="setFilter('alarm')"
          >
            🚨 仅看告警 ({{ alarmPointsCount }})
          </button>
          <button
            type="button"
            class="tab-pill"
            :class="{ 'is-active': activeFilter === 'device' }"
            @click="setFilter('device')"
          >
            🏭 生产设备
          </button>
          <button
            type="button"
            class="tab-pill"
            :class="{ 'is-active': activeFilter === 'warehouse' }"
            @click="setFilter('warehouse')"
          >
            📦 仓库库区
          </button>
        </div>
      </div>

      <!-- 状态指示图例与模拟查看角色 -->
      <div class="right-controls">
        <div class="status-legend">
          <span class="legend-item text-alarm">● 告警 (最高级)</span>
          <span class="legend-item text-offline">● 离线</span>
          <span class="legend-item text-warning">● 预警</span>
          <span class="legend-item text-normal">● 正常</span>
        </div>

        <div class="role-selector-box">
          <label class="role-label">模拟查看角色:</label>
          <select v-model="simulatedRole" class="control-select" @change="handleRoleChange">
            <option value="admin">租户管理员 (全域权限)</option>
            <option value="iot">IoT 工程师 (仅设备与告警)</option>
            <option value="warehouse">仓储操作员 (仅库区)</option>
          </select>
        </div>

        <div class="state-simulator-box">
          <select v-model="simulateState" class="control-select" @change="loadMapProjection">
            <option value="normal">正常事实 (Ready)</option>
            <option value="empty">无点位 (Empty)</option>
            <option value="error">加载异常 (Error)</option>
          </select>
        </div>
      </div>
    </div>

    <!-- 错误异常提示 -->
    <div v-if="viewState === 'error'" class="error-wrap">
      <ErrorState
        title="二维站点地图渲染失败"
        :message="errorMessage"
        code="GIS_POINT_001"
        @retry="loadMapProjection"
      />
    </div>

    <!-- 主空间画布与详情抽屉布局 -->
    <div v-else class="canvas-layout">
      <!-- 二维地图主画布面板 -->
      <div class="map-panel">
        <div class="panel-header">
          <div class="header-title-box">
            <span class="map-tag">CANVAS: {{ mapProjection?.mapCode || 'MAP_PLANT_TOTAL' }}</span>
            <strong class="map-title">{{ mapProjection?.mapName || '智能制造与立体仓储总平面图' }}</strong>
          </div>
          <span class="anti-drift-tag">📐 相对百分比坐标防偏移系统生效中</span>
        </div>

        <!-- 相对百分比核心画布容器 -->
        <div class="map-canvas-container">
          <!-- 1. 受控底图：工业 SVG 车间库房分区矢量网格 -->
          <svg
            class="map-svg-background"
            viewBox="0 0 1000 600"
            preserveAspectRatio="none"
            xmlns="http://www.w3.org/2000/svg"
          >
            <defs>
              <pattern id="grid-pattern" width="40" height="40" patternUnits="userSpaceOnUse">
                <path d="M 40 0 L 0 0 0 40" fill="none" stroke="rgba(148, 185, 198, 0.07)" stroke-width="1" />
              </pattern>
            </defs>

            <!-- 网格背景 -->
            <rect width="100%" height="100%" fill="url(#grid-pattern)" />

            <!-- 成品立库区 (WH-FG-01) -->
            <rect
              x="5%"
              y="10%"
              width="30%"
              height="38%"
              rx="8"
              fill="rgba(56, 189, 248, 0.05)"
              stroke="rgba(56, 189, 248, 0.25)"
              stroke-dasharray="4 4"
            />
            <text x="7%" y="16%" fill="#38bdf8" font-size="13" font-weight="700">
              成品立体库区 (WH-FG-01)
            </text>
            <text x="7%" y="22%" fill="#94a3b8" font-size="11">
              含 SHP-01 发货暂存位 · 高架自动化堆垛
            </text>

            <!-- 原料立体仓 (WH-RM-01) -->
            <rect
              x="5%"
              y="52%"
              width="30%"
              height="40%"
              rx="8"
              fill="rgba(56, 189, 248, 0.05)"
              stroke="rgba(56, 189, 248, 0.25)"
              stroke-dasharray="4 4"
            />
            <text x="7%" y="58%" fill="#38bdf8" font-size="13" font-weight="700">
              原料立体仓 (WH-RM-01)
            </text>
            <text x="7%" y="64%" fill="#94a3b8" font-size="11">
              含 RS-01 收货暂存位 · QH-01 质量隔离位
            </text>

            <!-- 智能制造核心车间 (AREA-PROD) -->
            <rect
              x="40%"
              y="10%"
              width="55%"
              height="82%"
              rx="8"
              fill="rgba(45, 212, 191, 0.04)"
              stroke="rgba(45, 212, 191, 0.22)"
              stroke-dasharray="4 4"
            />
            <text x="42%" y="16%" fill="#2dd4bf" font-size="13" font-weight="700">
              智能制造核心装配车间 (AREA-PROD)
            </text>
            <text x="42%" y="22%" fill="#94a3b8" font-size="11">
              冲压机床、数控加工、总装检测与包装线
            </text>
          </svg>

          <!-- 空数据提示 -->
          <div v-if="viewState === 'empty' || filteredPoints.length === 0" class="canvas-empty-overlay">
            <EmptyState
              icon="📍"
              title="当前图层暂无可见点位"
              description="该过滤条件或当前模拟角色权限下无对应空间投影点位。"
            />
          </div>

          <!-- 加载中遮罩 -->
          <div v-if="viewState === 'loading'" class="canvas-loading-overlay">
            <span class="spinner">⏳</span>
            <span>正在装载空间投影点位...</span>
          </div>

          <!-- 2. 空间点位集合 (严格相对百分比坐标防偏移) -->
          <div
            v-for="pt in filteredPoints"
            :key="pt.id"
            class="map-pin"
            :class="[
              `status-${pt.displayStatus.toLowerCase()}`,
              {
                'is-selected': selectedPoint?.id === pt.id,
                'is-alarm': pt.displayStatus === 'Alarm',
              },
            ]"
            :style="{
              left: `${pt.xPercent}%`,
              top: `${pt.yPercent}%`,
              transform: `translate(-50%, -50%) rotate(${pt.rotation || 0}deg)`,
            }"
            :title="`${pt.pointName} - ${pt.statusText}`"
            @click="selectPoint(pt)"
          >
            <!-- 状态圆点 (告警呼吸动画) -->
            <span class="pin-dot"></span>
            <!-- 点位标题 -->
            <span class="pin-label">{{ pt.pointName }}</span>
          </div>
        </div>

        <div class="panel-footer">
          <span class="footer-note">
            ℹ️ 提示：点击画布中的点位，可在右侧抽屉查看实时业务指标及直达关联控制台。
          </span>
          <span class="footer-time">投影生成时间: {{ mapProjection?.generatedAt || '-' }}</span>
        </div>
      </div>

      <!-- 右侧点位详情面板抽屉 (保持常驻交互) -->
      <div class="detail-panel">
        <template v-if="selectedPoint">
          <div class="detail-header">
            <div class="detail-tag-row">
              <span class="entity-type-badge">{{ selectedPoint.entityType }}</span>
              <StatusBadge
                :type="getStatusBadgeType(selectedPoint.displayStatus)"
                :text="selectedPoint.statusText"
                :pulsing="selectedPoint.displayStatus === 'Alarm'"
              />
            </div>
            <h3 class="detail-title">{{ selectedPoint.pointName }}</h3>
            <span class="entity-id-code">实体编码: {{ selectedPoint.entityId }}</span>
          </div>

          <!-- 严重告警警示框 -->
          <div v-if="selectedPoint.alarmMarker" class="alarm-detail-box">
            <div class="alarm-title-row">
              <span class="alarm-icon">🚨</span>
              <strong>严重告警事件 (IoT Alarm)</strong>
            </div>
            <div class="alarm-meta-grid">
              <div>告警编号: <code>{{ selectedPoint.alarmMarker.alarmId }}</code></div>
              <div>告警级别: <span class="text-alarm">{{ selectedPoint.alarmMarker.alarmLevel }}</span></div>
              <div>发生时间: {{ selectedPoint.alarmMarker.occurredAt }}</div>
            </div>
          </div>

          <div class="detail-body">
            <div class="section-label">空间坐标参数</div>
            <div class="coord-chips">
              <span class="coord-chip">X: {{ selectedPoint.xPercent }}%</span>
              <span class="coord-chip">Y: {{ selectedPoint.yPercent }}%</span>
              <span v-if="selectedPoint.rotation" class="coord-chip">旋转: {{ selectedPoint.rotation }}°</span>
            </div>

            <div class="section-label">业务实体描述</div>
            <p class="point-desc">{{ selectedPoint.detail || "暂无详细描述" }}</p>

            <div class="section-label">实时状态摘要</div>
            <div v-if="selectedPoint.metrics && selectedPoint.metrics.length > 0" class="metrics-list">
              <div
                v-for="(m, idx) in selectedPoint.metrics"
                :key="idx"
                class="metric-box"
                :class="{ 'is-warn': m.warn }"
              >
                <span class="m-label">{{ m.label }}</span>
                <strong class="m-val">{{ m.value }}</strong>
              </div>
            </div>

            <div class="source-time-row">
              <span class="st-label">源事实最后更新：</span>
              <span class="st-val">{{ selectedPoint.sourceUpdatedAt }}</span>
            </div>
          </div>

          <div class="detail-footer">
            <button
              v-if="selectedPoint.linkedPage"
              type="button"
              class="btn-penetrate"
              @click="handlePenetrate(selectedPoint.linkedPage)"
            >
              <span>直达业务控制台</span>
              <span>➔</span>
            </button>
            <span v-else class="text-muted-tip">该点位暂未配置穿透路由</span>
          </div>
        </template>

        <template v-else>
          <div class="empty-selection-box">
            <span class="empty-icon">👆</span>
            <p class="empty-text">请在左侧地图中点击任意设备或库区点位，查看受权限约束的业务事实摘要。</p>
          </div>
        </template>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
/**
 * 厂区二维空间拓扑地图主视图 (SiteMapView)
 * 职责：
 * 1. 渲染受控矢量底图与百分比相对坐标 (xPercent, yPercent)，杜绝窗口伸缩点位漂移；
 * 2. 遵循告警优先级：Alarm > Offline > Warning > Normal；
 * 3. 支持图层过滤与角色权限模拟切换；
 * 4. 点位点击呼出右侧业务摘要抽屉与跨域业务跳转。
 */

import { ref, computed, onMounted } from "vue";
import { useRouter } from "vue-router";
import type {
  SiteMapProjection,
  MapPoint,
  MapPointStatus,
} from "../../types/insights";
import type { ViewState, BadgeType } from "../../types/common";
import { fetchSiteMapProjection } from "../../api/insights";
import PageHeader from "../../components/common/PageHeader.vue";
import StatusBadge from "../../components/common/StatusBadge.vue";
import EmptyState from "../../components/common/EmptyState.vue";
import ErrorState from "../../components/common/ErrorState.vue";

const props = withDefaults(
  defineProps<{
    mapId?: string | number;
  }>(),
  {
    mapId: "MAP-001",
  }
);

const emit = defineEmits<{
  (e: "back-list"): void;
  (e: "edit-map", mapId: string | number): void;
  (e: "navigate-trace"): void;
}>();

const router = useRouter();

// 界面状态
const viewState = ref<ViewState>("loading");
const errorMessage = ref<string>("");
const currentMapId = ref<string | number>(props.mapId);
const mapProjection = ref<SiteMapProjection | null>(null);
const selectedPoint = ref<MapPoint | null>(null);

// 交互过滤
const activeFilter = ref<"all" | "alarm" | "device" | "warehouse">("all");
const simulatedRole = ref<"admin" | "iot" | "warehouse">("admin");
const simulateState = ref<"normal" | "empty" | "error">("normal");

/**
 * 装载地图点位投影
 */
async function loadMapProjection() {
  viewState.value = "loading";
  errorMessage.value = "";

  try {
    const projection = await fetchSiteMapProjection({
      siteMapId: currentMapId.value,
      simulateState: simulateState.value,
    });
    mapProjection.value = projection;

    if (!projection.points || projection.points.length === 0) {
      viewState.value = "empty";
      selectedPoint.value = null;
    } else {
      viewState.value = "ready";
      // 默认选中告警点位或第一个点位
      const alarmPt = projection.points.find((p: any) => p.displayStatus === "Alarm");
      selectedPoint.value = alarmPt || projection.points[0];
    }
  } catch (err: any) {
    viewState.value = "error";
    errorMessage.value = err?.message || "地图点位投影装载失败";
  }
}

/**
 * 依据角色权限与图层过滤后的点位列表
 */
const filteredPoints = computed(() => {
  if (!mapProjection.value?.points) return [];
  let list = mapProjection.value.points;

  // 角色权限模拟过滤
  if (simulatedRole.value === "iot") {
    // IoT 工程师仅看设备
    list = list.filter((p) => p.entityType === "DEVICE");
  } else if (simulatedRole.value === "warehouse") {
    // 仓库操作员仅看库区
    list = list.filter((p) => p.entityType === "WAREHOUSE");
  }

  // 图层过滤
  if (activeFilter.value === "alarm") {
    list = list.filter((p) => p.displayStatus === "Alarm");
  } else if (activeFilter.value === "device") {
    list = list.filter((p) => p.entityType === "DEVICE");
  } else if (activeFilter.value === "warehouse") {
    list = list.filter((p) => p.entityType === "WAREHOUSE");
  }

  return list;
});

const totalPointsCount = computed(() => mapProjection.value?.points?.length || 0);
const alarmPointsCount = computed(() => {
  return mapProjection.value?.points?.filter((p) => p.displayStatus === "Alarm").length || 0;
});

function setFilter(filter: "all" | "alarm" | "device" | "warehouse") {
  activeFilter.value = filter;
}

function handleRoleChange() {
  // 切换角色时若当前选中点位已不可见，重新定位
  if (selectedPoint.value && !filteredPoints.value.some((p) => p.id === selectedPoint.value?.id)) {
    selectedPoint.value = filteredPoints.value[0] || null;
  }
}

function selectPoint(pt: MapPoint) {
  selectedPoint.value = pt;
}

function getStatusBadgeType(status: MapPointStatus): BadgeType {
  switch (status) {
    case "Alarm":
      return "danger";
    case "Offline":
      return "default";
    case "Warning":
      return "warning";
    case "Normal":
      return "success";
  }
}

function handlePenetrate(routePath: string) {
  router.push(routePath);
}

onMounted(() => {
  loadMapProjection();
});
</script>

<style scoped>
.site-map-view-container {
  padding: 20px 28px 40px;
  max-width: 1560px;
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

.btn-back-list,
.btn-go-editor {
  padding: 7px 14px;
  border-radius: 6px;
  font-size: 12px;
  font-weight: 500;
  cursor: pointer;
  transition: all 0.2s;
}

.btn-back-list {
  background: rgba(51, 65, 85, 0.6);
  color: #cbd5e1;
  border: 1px solid rgba(255, 255, 255, 0.1);
}

.btn-back-list:hover {
  background: rgba(71, 85, 105, 0.8);
  color: #fff;
}

.btn-go-editor {
  background: rgba(56, 189, 248, 0.15);
  color: #38bdf8;
  border: 1px solid rgba(56, 189, 248, 0.3);
}

.btn-go-editor:hover {
  background: rgba(56, 189, 248, 0.25);
  color: #fff;
}

/* 控制栏 */
.map-control-bar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  background: rgba(15, 23, 42, 0.7);
  border: 1px solid rgba(255, 255, 255, 0.08);
  border-radius: 8px;
  padding: 12px 18px;
  margin-bottom: 16px;
  flex-wrap: wrap;
}

.filter-group {
  display: flex;
  align-items: center;
  gap: 10px;
}

.control-label {
  font-size: 12px;
  color: #94a3b8;
}

.scenario-tabs {
  display: flex;
  gap: 6px;
}

.tab-pill {
  padding: 5px 12px;
  border-radius: 20px;
  font-size: 12px;
  background: rgba(30, 41, 59, 0.7);
  border: 1px solid rgba(255, 255, 255, 0.1);
  color: #cbd5e1;
  cursor: pointer;
  transition: all 0.2s;
}

.tab-pill:hover {
  border-color: #38bdf8;
  color: #fff;
}

.tab-pill.is-active {
  background: #0284c7;
  border-color: #0369a1;
  color: #fff;
  font-weight: 600;
}

.tab-pill.alarm-pill.is-active {
  background: #dc2626;
  border-color: #b91c1c;
}

.right-controls {
  display: flex;
  align-items: center;
  gap: 16px;
  flex-wrap: wrap;
}

.status-legend {
  display: flex;
  align-items: center;
  gap: 10px;
  font-size: 11px;
}

.text-alarm {
  color: #f87171;
}

.text-offline {
  color: #94a3b8;
}

.text-warning {
  color: #fbbf24;
}

.text-normal {
  color: #34d399;
}

.role-selector-box,
.state-simulator-box {
  display: flex;
  align-items: center;
  gap: 6px;
}

.role-label {
  font-size: 11px;
  color: #94a3b8;
}

.control-select {
  background: rgba(30, 41, 59, 0.8);
  border: 1px solid rgba(255, 255, 255, 0.15);
  color: #f8fafc;
  padding: 4px 8px;
  border-radius: 6px;
  font-size: 11px;
  outline: none;
}

/* 画布与详情栅格布局 */
.canvas-layout {
  display: grid;
  grid-template-columns: 1fr 380px;
  gap: 20px;
  min-height: 640px;
}

@media (max-width: 1100px) {
  .canvas-layout {
    grid-template-columns: 1fr;
  }
}

.map-panel {
  background: rgba(15, 23, 42, 0.85);
  border: 1px solid rgba(255, 255, 255, 0.1);
  border-radius: 10px;
  display: flex;
  flex-direction: column;
  overflow: hidden;
  box-shadow: 0 8px 32px rgba(0, 0, 0, 0.3);
}

.panel-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 14px 20px;
  background: rgba(30, 41, 59, 0.5);
  border-bottom: 1px solid rgba(255, 255, 255, 0.08);
}

.header-title-box {
  display: flex;
  align-items: center;
  gap: 10px;
}

.map-tag {
  font-size: 10px;
  font-family: var(--font-mono, monospace);
  color: #38bdf8;
  background: rgba(56, 189, 248, 0.1);
  padding: 2px 6px;
  border-radius: 4px;
}

.map-title {
  font-size: 14px;
  color: #f1f5f9;
}

.anti-drift-tag {
  font-size: 11px;
  color: #34d399;
}

/* 核心百分比画布 */
.map-canvas-container {
  flex: 1;
  position: relative;
  min-height: 520px;
  background: radial-gradient(circle at 50% 50%, #0d1b24, #060e15);
  overflow: hidden;
}

.map-svg-background {
  position: absolute;
  inset: 0;
  width: 100%;
  height: 100%;
  pointer-events: none;
}

.canvas-loading-overlay,
.canvas-empty-overlay {
  position: absolute;
  inset: 0;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 12px;
  color: #94a3b8;
  background: rgba(6, 14, 21, 0.7);
  backdrop-filter: blur(2px);
  z-index: 20;
}

/* 地图点位标记 (Pins) */
.map-pin {
  position: absolute;
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 6px 12px;
  border-radius: 20px;
  background: rgba(7, 16, 23, 0.94);
  border: 1px solid rgba(255, 255, 255, 0.2);
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.5);
  cursor: pointer;
  transition: all 0.2s ease;
  user-select: none;
  z-index: 5;
  white-space: nowrap;
}

.map-pin:hover {
  border-color: #38bdf8;
  transform: translate(-50%, -50%) scale(1.06) !important;
  z-index: 15;
}

.map-pin.is-selected {
  border-color: #38bdf8;
  box-shadow: 0 0 16px rgba(56, 189, 248, 0.5);
  z-index: 16;
}

.pin-dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  background: currentColor;
}

.pin-label {
  font-size: 11px;
  font-weight: 700;
  color: #e2e8f0;
}

/* 优先级颜色与动画 */
.map-pin.status-alarm {
  color: #f87171;
  border-color: rgba(248, 113, 113, 0.6);
  box-shadow: 0 0 16px rgba(239, 68, 68, 0.45);
}

.map-pin.status-alarm .pin-dot {
  animation: pulse-alarm 1.2s infinite;
}

.map-pin.status-warning {
  color: #fbbf24;
  border-color: rgba(251, 191, 36, 0.5);
}

.map-pin.status-offline {
  color: #94a3b8;
  opacity: 0.7;
}

.map-pin.status-normal {
  color: #34d399;
}

.panel-footer {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 10px 18px;
  background: rgba(15, 23, 42, 0.8);
  border-top: 1px solid rgba(255, 255, 255, 0.06);
  font-size: 11px;
}

.footer-note {
  color: #94a3b8;
}

.footer-time {
  color: #64748b;
  font-family: var(--font-mono, monospace);
}

/* 右侧详情抽屉 */
.detail-panel {
  background: rgba(15, 23, 42, 0.85);
  border: 1px solid rgba(255, 255, 255, 0.1);
  border-radius: 10px;
  display: flex;
  flex-direction: column;
  padding: 20px;
  box-shadow: 0 8px 32px rgba(0, 0, 0, 0.3);
}

.detail-header {
  margin-bottom: 16px;
  padding-bottom: 12px;
  border-bottom: 1px solid rgba(255, 255, 255, 0.08);
}

.detail-tag-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 8px;
}

.entity-type-badge {
  font-size: 10px;
  font-family: var(--font-mono, monospace);
  padding: 2px 6px;
  background: rgba(56, 189, 248, 0.15);
  color: #38bdf8;
  border-radius: 4px;
}

.detail-title {
  margin: 0 0 4px;
  font-size: 16px;
  color: #f1f5f9;
}

.entity-id-code {
  font-size: 11px;
  font-family: var(--font-mono, monospace);
  color: #94a3b8;
}

.alarm-detail-box {
  background: rgba(239, 68, 68, 0.15);
  border: 1px solid rgba(239, 68, 68, 0.35);
  border-radius: 6px;
  padding: 12px;
  margin-bottom: 16px;
}

.alarm-title-row {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 12px;
  color: #fca5a5;
  margin-bottom: 6px;
}

.alarm-meta-grid {
  font-size: 11px;
  color: #cbd5e1;
  line-height: 1.6;
}

.detail-body {
  flex: 1;
}

.section-label {
  font-size: 11px;
  color: #94a3b8;
  margin: 14px 0 6px;
  text-transform: uppercase;
}

.coord-chips {
  display: flex;
  gap: 8px;
}

.coord-chip {
  padding: 2px 8px;
  border-radius: 4px;
  background: rgba(0, 0, 0, 0.3);
  border: 1px solid rgba(255, 255, 255, 0.1);
  font-family: var(--font-mono, monospace);
  font-size: 11px;
  color: #cbd5e1;
}

.point-desc {
  font-size: 13px;
  color: #cbd5e1;
  line-height: 1.5;
  margin: 0;
}

.metrics-list {
  display: grid;
  grid-template-columns: 1fr;
  gap: 8px;
}

.metric-box {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 8px 12px;
  background: rgba(30, 41, 59, 0.5);
  border-radius: 6px;
  font-size: 12px;
}

.metric-box.is-warn .m-val {
  color: #f87171;
}

.m-label {
  color: #94a3b8;
}

.m-val {
  color: #f1f5f9;
  font-family: var(--font-mono, monospace);
}

.source-time-row {
  margin-top: 16px;
  padding-top: 10px;
  border-top: 1px dashed rgba(255, 255, 255, 0.08);
  font-size: 11px;
  display: flex;
  align-items: baseline;
  justify-content: space-between;
}

.st-label {
  color: #64748b;
}

.st-val {
  color: #94a3b8;
  font-family: var(--font-mono, monospace);
}

.detail-footer {
  padding-top: 16px;
  border-top: 1px solid rgba(255, 255, 255, 0.08);
}

.btn-penetrate {
  width: 100%;
  padding: 10px;
  background: #0284c7;
  color: #ffffff;
  border: none;
  border-radius: 6px;
  font-size: 13px;
  font-weight: 600;
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  transition: all 0.2s;
}

.btn-penetrate:hover {
  background: #0369a1;
}

.text-muted-tip {
  font-size: 12px;
  color: #64748b;
  text-align: center;
  display: block;
}

.empty-selection-box {
  height: 100%;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  text-align: center;
  color: #64748b;
  gap: 12px;
  padding: 40px 20px;
}

.empty-icon {
  font-size: 32px;
}

.empty-text {
  font-size: 13px;
  line-height: 1.5;
  max-width: 240px;
  margin: 0;
}

@keyframes pulse-alarm {
  0% {
    box-shadow: 0 0 0 0 rgba(239, 68, 68, 0.8);
  }
  70% {
    box-shadow: 0 0 0 10px rgba(239, 68, 68, 0);
  }
  100% {
    box-shadow: 0 0 0 0 rgba(239, 68, 68, 0);
  }
}
</style>
