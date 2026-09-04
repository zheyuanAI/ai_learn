<template>
  <div class="traceability-view-container">
    <!-- 顶部统一导航与页面说明 -->
    <PageHeader
      title="黄金业务闭环追溯中心"
      tag="INSIGHTS / TRACEABILITY ENGINE"
      description="跨 ERP 订单、WMS 库存、MES 工序、质检与 IoT 告警的端到端全息追溯。支持正向推导与反向溯源，对缺失业务事实的环节显式标注断链缺口卡片。"
    >
      <template #actions>
        <!-- 模块快捷互通导航 -->
        <div class="nav-sub-tabs">
          <RouterLink to="/gis" class="tab-btn">
            <span>📊 综合看板</span>
          </RouterLink>
          <button type="button" class="tab-btn is-active">
            <span>🔍 闭环追溯</span>
          </button>
        </div>
      </template>
    </PageHeader>

    <!-- 追溯检索控制栏 -->
    <FilterBar
      v-model="queryParams.entryCode"
      placeholder="输入单据/批次/设备编码 (如 SO-20260826-018)..."
      @search="loadTraceChain"
      @reset="handleReset"
    >
      <!-- 切入类型选择 -->
      <div class="filter-field">
        <label class="filter-label">切入实体类型</label>
        <select v-model="queryParams.entryType" class="filter-select" @change="handleTypeChange">
          <option value="SALES_ORDER">🛒 销售订单 (SO)</option>
          <option value="WORK_ORDER">📋 制造工单 (WO)</option>
          <option value="DEVICE_ALARM">🚨 设备告警 (ALARM)</option>
          <option value="INVENTORY_BATCH">📦 库存批次 (LOT)</option>
        </select>
      </div>

      <!-- 追溯方向选择 -->
      <div class="filter-field">
        <label class="filter-label">追溯方向</label>
        <div class="direction-switch">
          <button
            type="button"
            class="dir-btn"
            :class="{ 'is-active': queryParams.direction === 'FORWARD' }"
            @click="setDirection('FORWARD')"
          >
            ➔ 正向推导
          </button>
          <button
            type="button"
            class="dir-btn"
            :class="{ 'is-active': queryParams.direction === 'REVERSE' }"
            @click="setDirection('REVERSE')"
          >
            ← 反向溯源
          </button>
        </div>
      </div>

      <!-- 四态模拟控制 (供验收验证) -->
      <div class="filter-field">
        <label class="filter-label">四态模式模拟</label>
        <select v-model="queryParams.simulateState" class="filter-select" @change="loadTraceChain">
          <option value="normal">正常事实 (Ready)</option>
          <option value="empty">空链状态 (Empty)</option>
          <option value="error">源服务故障 (Error)</option>
        </select>
      </div>

      <template #right>
        <!-- 快速示范填充预设 -->
        <div class="preset-pills">
          <span class="preset-label">快速切入示例:</span>
          <button type="button" class="pill-btn" @click="applyPreset('SO-20260826-018', 'SALES_ORDER', 'FORWARD')">
            销售单018
          </button>
          <button type="button" class="pill-btn" @click="applyPreset('ALM-20260826-033', 'DEVICE_ALARM', 'REVERSE')">
            告警033
          </button>
          <button type="button" class="pill-btn" @click="applyPreset('WO-20260826-018', 'WORK_ORDER', 'FORWARD')">
            工单018
          </button>
        </div>
      </template>
    </FilterBar>

    <!-- 追溯诊断与指标汇总看板条 -->
    <div v-if="viewState === 'ready' && chainResult" class="chain-meta-bar">
      <div class="meta-item">
        <span class="meta-title">当前追溯锚点</span>
        <strong class="meta-val highlight-blue">
          {{ chainResult.queryTarget.code }}
          <span class="dir-tag">({{ chainResult.queryTarget.direction === 'FORWARD' ? '正向' : '反向' }})</span>
        </strong>
      </div>

      <div class="meta-divider"></div>

      <div class="meta-item">
        <span class="meta-title">黄金闭环完整率</span>
        <strong class="meta-val" :class="chainResult.hasBrokenLinks ? 'text-warn' : 'text-success'">
          {{ chainResult.coverageRate }}
        </strong>
      </div>

      <div class="meta-divider"></div>

      <div class="meta-item">
        <span class="meta-title">发现断链缺口</span>
        <div class="meta-badge-row">
          <StatusBadge
            v-if="chainResult.hasBrokenLinks"
            type="danger"
            :text="`存在 ${chainResult.brokenCount} 处断链缺口`"
            :pulsing="true"
          />
          <StatusBadge
            v-else
            type="success"
            text="事实链条完整闭环"
          />
        </div>
      </div>

      <div class="meta-divider"></div>

      <div class="meta-item info-side">
        <span class="meta-title">投影数据源</span>
        <span class="meta-source">{{ chainResult.sourceSummary }}</span>
      </div>
    </div>

    <!-- 界面四态展示容器 -->
    <div class="content-area">
      <!-- 1. 加载态 (Loading) -->
      <div v-if="viewState === 'loading'" class="state-loading">
        <div class="loading-spinner">⏳</div>
        <p class="loading-text">正在跨 ERP、WMS、MES、IoT 多域深度装载追溯拓扑关系链...</p>
      </div>

      <!-- 2. 错误态 (Error) -->
      <div v-else-if="viewState === 'error'" class="state-error-wrap">
        <ErrorState
          title="追溯链拓扑装载失败"
          :message="errorMessage"
          code="GIS_QUERY_002"
          detail="跨服务图数据库或领域只读投影不可达，已阻断脏数据生成。"
          @retry="loadTraceChain"
        />
      </div>

      <!-- 3. 空数据态 (Empty) -->
      <div v-else-if="viewState === 'empty' || (!chainResult?.nodes || chainResult.nodes.length === 0)" class="state-empty-wrap">
        <EmptyState
          icon="⛓️"
          title="未检索到对应业务实体的关联事实链路"
          description="输入的单据或批次编码暂无跨域关联事实，请检查单据号是否准确，或更换切入类型后重试。"
        >
          <template #action>
            <button type="button" class="btn-retry-primary" @click="applyPreset('SO-20260826-018', 'SALES_ORDER', 'FORWARD')">
              装载典型销售闭环示例
            </button>
          </template>
        </EmptyState>
      </div>

      <!-- 4. 就绪态 (Ready) 追溯链条拓扑流 -->
      <div v-else class="chain-timeline-flow">
        <div class="timeline-start-badge">
          <span>{{ queryParams.direction === 'FORWARD' ? '🏁 需求发起源头' : '🚨 异常反查端点' }}</span>
        </div>

        <div class="nodes-list">
          <TraceNodeCard
            v-for="(node, index) in chainResult.nodes"
            :key="node.id"
            :node="node"
            :is-last="index === chainResult.nodes.length - 1"
            :direction="queryParams.direction"
            @select="handleSelectNode"
          />
        </div>

        <div class="timeline-end-badge">
          <span>{{ queryParams.direction === 'FORWARD' ? '🎯 履约交运终点' : '📦 原料采购源头' }}</span>
        </div>
      </div>
    </div>

    <!-- 侧边节点详情与审计抽屉 -->
    <div v-if="selectedNode" class="node-drawer-mask" @click.self="selectedNode = null">
      <div class="node-drawer-panel">
        <div class="drawer-header">
          <div class="drawer-title-box">
            <span class="drawer-tag">NODE FACT DETAILS</span>
            <h3 class="drawer-title">{{ selectedNode.title }} ({{ selectedNode.nodeCode }})</h3>
          </div>
          <button type="button" class="drawer-close" @click="selectedNode = null">✕</button>
        </div>

        <div class="drawer-body">
          <div v-if="selectedNode.isGap" class="drawer-alert-gap">
            <strong>⚠️ 断链缺口警示：</strong>
            <p>{{ selectedNode.gapReason }}</p>
          </div>

          <div class="drawer-info-grid">
            <div class="info-row">
              <span class="row-k">发生时间：</span>
              <span class="row-v">{{ selectedNode.timestamp }}</span>
            </div>
            <div class="info-row">
              <span class="row-k">当前状态：</span>
              <StatusBadge :type="selectedNode.statusType || 'default'" :text="selectedNode.status" />
            </div>
            <div class="info-row">
              <span class="row-k">查看权限：</span>
              <span :class="selectedNode.hasPermission ? 'text-success' : 'text-warn'">
                {{ selectedNode.hasPermission ? '✓ 具备查看权限' : '🔒 无该实体细粒度权限' }}
              </span>
            </div>
          </div>

          <h4 class="drawer-sub-title">业务明细事实</h4>
          <div class="drawer-detail-table">
            <div v-for="(item, idx) in selectedNode.details" :key="idx" class="table-row">
              <span class="table-col-label">{{ item.label }}</span>
              <span class="table-col-val" :class="{ 'is-warn': item.warn }">
                <QuantityText v-if="item.isQuantity" :value="item.value" :unit="item.unit" />
                <template v-else>{{ item.value }}</template>
              </span>
            </div>
          </div>
        </div>

        <div class="drawer-footer">
          <button
            v-if="selectedNode.hasPermission && selectedNode.linkedRoute"
            type="button"
            class="btn-drawer-jump"
            @click="navigateToRoute(selectedNode.linkedRoute)"
          >
            <span>前往业务页面查看原始凭据</span>
            <span>➔</span>
          </button>
          <button type="button" class="btn-drawer-close" @click="selectedNode = null">
            关闭
          </button>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
/**
 * 黄金业务闭环追溯中心视图 (TraceabilityView)
 * 核心功能：
 * 1. 支持从销售订单、工单、批次、设备告警等多入口切入；
 * 2. 支持正向/反向链条展示；
 * 3. 对缺失来源识别并高亮断链缺口卡片；
 * 4. 节点具备权限时支持路由跳转业务控制台；
 * 5. 内置四态（Loading, Ready, Empty, Error）及模拟演练开关。
 */

import { ref, reactive, onMounted } from "vue";
import { useRouter } from "vue-router";
import type {
  TraceabilityChainResult,
  TraceNodeType,
  TraceDirection,
  TraceNode,
} from "../../types/insights";
import type { ViewState } from "../../types/common";
import { fetchTraceabilityChain } from "../../api/insights";
import PageHeader from "../../components/common/PageHeader.vue";
import FilterBar from "../../components/common/FilterBar.vue";
import StatusBadge from "../../components/common/StatusBadge.vue";
import QuantityText from "../../components/common/QuantityText.vue";
import EmptyState from "../../components/common/EmptyState.vue";
import ErrorState from "../../components/common/ErrorState.vue";
import TraceNodeCard from "./components/TraceNodeCard.vue";

const router = useRouter();

// 界面状态
const viewState = ref<ViewState>("loading");
const errorMessage = ref<string>("");
const selectedNode = ref<TraceNode | null>(null);

// 查询参数
const queryParams = reactive<{
  entryType: TraceNodeType;
  entryCode: string;
  direction: TraceDirection;
  simulateState: "normal" | "empty" | "error";
}>({
  entryType: "SALES_ORDER",
  entryCode: "SO-20260826-018",
  direction: "FORWARD",
  simulateState: "normal",
});

// 追溯结果数据
const chainResult = ref<TraceabilityChainResult | null>(null);

/**
 * 装载追溯拓扑关系链
 */
async function loadTraceChain() {
  viewState.value = "loading";
  errorMessage.value = "";

  try {
    const result = await fetchTraceabilityChain({
      entryType: queryParams.entryType,
      entryCode: queryParams.entryCode.trim(),
      direction: queryParams.direction,
      simulateState: queryParams.simulateState,
    });

    chainResult.value = result;
    if (!result.nodes || result.nodes.length === 0) {
      viewState.value = "empty";
    } else {
      viewState.value = "ready";
    }
  } catch (err: any) {
    viewState.value = "error";
    errorMessage.value = err?.message || "跨域追溯链装载失败";
  }
}

/**
 * 切换追溯方向
 */
function setDirection(dir: TraceDirection) {
  if (queryParams.direction === dir) return;
  queryParams.direction = dir;
  loadTraceChain();
}

/**
 * 切换切入实体类型时自动填入适配默认编码
 */
function handleTypeChange() {
  switch (queryParams.entryType) {
    case "SALES_ORDER":
      queryParams.entryCode = "SO-20260826-018";
      queryParams.direction = "FORWARD";
      break;
    case "WORK_ORDER":
      queryParams.entryCode = "WO-20260826-018";
      queryParams.direction = "FORWARD";
      break;
    case "DEVICE_ALARM":
      queryParams.entryCode = "ALM-20260826-033";
      queryParams.direction = "REVERSE";
      break;
    case "INVENTORY_BATCH":
      queryParams.entryCode = "LOT-20260820-003";
      queryParams.direction = "REVERSE";
      break;
  }
  loadTraceChain();
}

/**
 * 应用快捷预设
 */
function applyPreset(code: string, type: TraceNodeType, dir: TraceDirection) {
  queryParams.entryCode = code;
  queryParams.entryType = type;
  queryParams.direction = dir;
  queryParams.simulateState = "normal";
  loadTraceChain();
}

/**
 * 重置检索条件
 */
function handleReset() {
  queryParams.entryType = "SALES_ORDER";
  queryParams.entryCode = "SO-20260826-018";
  queryParams.direction = "FORWARD";
  queryParams.simulateState = "normal";
  loadTraceChain();
}

/**
 * 选中节点查看抽屉详情
 */
function handleSelectNode(node: TraceNode) {
  selectedNode.value = node;
}

/**
 * 路由跳转
 */
function navigateToRoute(routePath: string) {
  selectedNode.value = null;
  router.push(routePath);
}

onMounted(() => {
  loadTraceChain();
});
</script>

<style scoped>
.traceability-view-container {
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

.direction-switch {
  display: flex;
  background: rgba(0, 0, 0, 0.3);
  border-radius: 6px;
  padding: 2px;
  border: 1px solid rgba(255, 255, 255, 0.1);
}

.dir-btn {
  padding: 5px 10px;
  border-radius: 4px;
  border: none;
  background: transparent;
  color: #94a3b8;
  font-size: 11px;
  cursor: pointer;
  transition: all 0.15s;
}

.dir-btn.is-active {
  background: #0284c7;
  color: #ffffff;
  font-weight: 600;
}

.preset-pills {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 12px;
}

.preset-label {
  color: #64748b;
  font-size: 11px;
}

.pill-btn {
  background: rgba(56, 189, 248, 0.08);
  border: 1px solid rgba(56, 189, 248, 0.2);
  color: #38bdf8;
  padding: 3px 8px;
  border-radius: 12px;
  font-size: 11px;
  cursor: pointer;
  transition: all 0.15s;
}

.pill-btn:hover {
  background: rgba(56, 189, 248, 0.2);
  color: #ffffff;
}

/* 指标汇总看板条 */
.chain-meta-bar {
  display: flex;
  align-items: center;
  background: rgba(15, 23, 42, 0.7);
  border: 1px solid rgba(56, 189, 248, 0.2);
  border-radius: 8px;
  padding: 12px 20px;
  margin-bottom: 24px;
  gap: 20px;
  flex-wrap: wrap;
}

.meta-item {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.meta-title {
  font-size: 11px;
  color: #94a3b8;
}

.meta-val {
  font-size: 15px;
  font-family: var(--font-mono, monospace);
  color: #f1f5f9;
}

.meta-val.highlight-blue {
  color: #38bdf8;
}

.dir-tag {
  font-size: 11px;
  font-weight: 400;
  color: #94a3b8;
  margin-left: 4px;
}

.text-warn {
  color: #fbbf24;
}

.text-success {
  color: #34d399;
}

.meta-divider {
  width: 1px;
  height: 28px;
  background: rgba(255, 255, 255, 0.1);
}

.info-side {
  margin-left: auto;
}

.meta-source {
  font-size: 11px;
  font-family: var(--font-mono, monospace);
  color: #64748b;
}

/* 内容区域四态 */
.content-area {
  min-height: 480px;
  display: flex;
  flex-direction: column;
}

.state-loading {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: 80px 20px;
  gap: 16px;
}

.loading-spinner {
  font-size: 36px;
  animation: pulse 1.5s infinite;
}

.loading-text {
  font-size: 14px;
  color: #94a3b8;
}

.state-error-wrap,
.state-empty-wrap {
  margin-top: 20px;
}

.btn-retry-primary {
  padding: 7px 16px;
  background: #0284c7;
  color: #ffffff;
  border: none;
  border-radius: 6px;
  font-size: 13px;
  cursor: pointer;
}

/* 时间线拓扑流 */
.chain-timeline-flow {
  display: flex;
  flex-direction: column;
  align-items: center;
  padding: 10px 0 40px;
}

.timeline-start-badge,
.timeline-end-badge {
  padding: 6px 16px;
  border-radius: 20px;
  background: rgba(15, 23, 42, 0.8);
  border: 1px solid rgba(56, 189, 248, 0.3);
  font-size: 12px;
  font-weight: 600;
  color: #38bdf8;
  box-shadow: 0 0 12px rgba(56, 189, 248, 0.2);
}

.nodes-list {
  display: flex;
  flex-direction: column;
  align-items: center;
  width: 100%;
  margin: 12px 0;
}

/* 详情抽屉 */
.node-drawer-mask {
  position: fixed;
  inset: 0;
  background: rgba(0, 0, 0, 0.65);
  backdrop-filter: blur(4px);
  z-index: 1000;
  display: flex;
  justify-content: flex-end;
}

.node-drawer-panel {
  width: 100%;
  max-width: 480px;
  height: 100%;
  background: #0f172a;
  border-left: 1px solid rgba(255, 255, 255, 0.12);
  display: flex;
  flex-direction: column;
  box-shadow: -10px 0 30px rgba(0, 0, 0, 0.5);
  animation: slide-in 0.25s ease-out;
}

.drawer-header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  padding: 20px;
  border-bottom: 1px solid rgba(255, 255, 255, 0.08);
}

.drawer-tag {
  font-size: 10px;
  color: #38bdf8;
  font-family: var(--font-mono, monospace);
  letter-spacing: 0.5px;
}

.drawer-title {
  font-size: 16px;
  color: #f1f5f9;
  margin: 4px 0 0;
}

.drawer-close {
  background: transparent;
  border: none;
  color: #94a3b8;
  font-size: 18px;
  cursor: pointer;
}

.drawer-body {
  flex: 1;
  overflow-y: auto;
  padding: 20px;
}

.drawer-alert-gap {
  background: rgba(239, 68, 68, 0.15);
  border: 1px solid rgba(239, 68, 68, 0.35);
  padding: 12px;
  border-radius: 6px;
  color: #fca5a5;
  font-size: 13px;
  margin-bottom: 16px;
}

.drawer-info-grid {
  display: flex;
  flex-direction: column;
  gap: 10px;
  padding: 12px;
  background: rgba(30, 41, 59, 0.5);
  border-radius: 6px;
  margin-bottom: 20px;
}

.info-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  font-size: 13px;
}

.row-k {
  color: #94a3b8;
}

.row-v {
  color: #cbd5e1;
  font-family: var(--font-mono, monospace);
}

.drawer-sub-title {
  font-size: 13px;
  color: #94a3b8;
  margin: 0 0 10px;
  text-transform: uppercase;
}

.drawer-detail-table {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.table-row {
  display: flex;
  justify-content: space-between;
  align-items: baseline;
  padding: 8px 12px;
  background: rgba(0, 0, 0, 0.25);
  border-radius: 4px;
  font-size: 13px;
}

.table-col-label {
  color: #94a3b8;
}

.table-col-val {
  color: #f1f5f9;
  font-weight: 500;
}

.table-col-val.is-warn {
  color: #f87171;
}

.drawer-footer {
  padding: 16px 20px;
  border-top: 1px solid rgba(255, 255, 255, 0.08);
  display: flex;
  gap: 12px;
  justify-content: flex-end;
  background: rgba(0, 0, 0, 0.2);
}

.btn-drawer-jump {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 8px 16px;
  background: #0284c7;
  color: #ffffff;
  border: none;
  border-radius: 6px;
  font-size: 13px;
  font-weight: 500;
  cursor: pointer;
}

.btn-drawer-close {
  padding: 8px 16px;
  background: rgba(51, 65, 85, 0.6);
  color: #cbd5e1;
  border: 1px solid rgba(255, 255, 255, 0.1);
  border-radius: 6px;
  font-size: 13px;
  cursor: pointer;
}

@keyframes slide-in {
  from {
    transform: translateX(100%);
  }
  to {
    transform: translateX(0);
  }
}
</style>
