<template>
  <div>
    <!-- 1. 二维 GIS 站点地图集成 -->
    <SiteMapListView
      v-if="activeModule === 'sitemap'"
      @navigate-dashboard="activeModule = 'dashboard'"
      @navigate-trace="activeModule = 'traceability'"
    />

    <!-- 2. 全闭环追溯中心集成 -->
    <TraceabilityView
      v-else-if="activeModule === 'traceability'"
    />

    <!-- 3. 首页综合监控看板 (默认) -->
    <div v-else class="dashboard-view-container">
      <!-- 顶部统一页面头与操作入口 -->
      <PageHeader
        title="首页综合监控看板"
        tag="DASHBOARD / CROSS-DOMAIN FACTS"
        description="黄金业务闭环 7 大事实域（库存、履约、制造、质量、设备、告警、追溯）只读聚合快照。默认每 30 秒自动轮询，单卡片异常互不影响，严禁建立第二套业务事实。"
      >
        <template #actions>
          <!-- 模块互通子导航 -->
          <div class="nav-sub-tabs">
            <button
              type="button"
              class="tab-btn is-active"
            >
              <span>📊 综合看板</span>
            </button>
            <button
              type="button"
              class="tab-btn"
              @click="activeModule = 'sitemap'"
            >
              <span>🗺️ 站点地图</span>
            </button>
            <button
              type="button"
              class="tab-btn"
              @click="activeModule = 'traceability'"
            >
              <span>🔍 闭环追溯</span>
            </button>
          </div>

          <!-- 30 秒自动轮询与即时刷新按钮 -->
          <button
            type="button"
            class="btn-refresh-now"
            :disabled="isRefreshing"
            title="点击立即触发跨域指标全量聚合"
            @click="handleManualRefresh"
          >
            <span :class="{ 'is-spinning': isRefreshing }">🔄</span>
            <span>立即刷新</span>
            <span class="countdown-badge">{{ countdownSeconds }}s</span>
          </button>
        </template>
      </PageHeader>

      <!-- 统计时间范围与数据同步状态控制栏 -->
      <section class="control-review-bar">
        <!-- 时间范围切换 -->
        <div class="range-block">
          <span class="review-label">统计时间范围</span>
          <div class="scenario-tabs">
            <button
              type="button"
              class="tab-btn-pill"
              :class="{ 'is-active': currentTimeRange === 'today' }"
              @click="setTimeRange('today')"
            >
              今日
            </button>
            <button
              type="button"
              class="tab-btn-pill"
              :class="{ 'is-active': currentTimeRange === '7d' }"
              @click="setTimeRange('7d')"
            >
              近 7 天
            </button>
            <button
              type="button"
              class="tab-btn-pill"
              :class="{ 'is-active': currentTimeRange === '30d' }"
              @click="setTimeRange('30d')"
            >
              近 30 天
            </button>
          </div>
          <strong class="effective-range-text">{{ overviewData?.timeRangeLabel || "今日 (00:00 - 23:59)" }}</strong>
        </div>

        <!-- 右侧同步状态模拟控制器 (供验收测试 StaleDataBanner 与故障降级) -->
        <div class="sync-status-block">
          <div class="sync-controller">
            <label class="review-label">源同步模式模拟:</label>
            <select v-model="simulatedSyncMode" class="sync-select" @change="handleSyncModeChange">
              <option value="healthy">正常 (Healthy - 全部实时)</option>
              <option value="delayed">更新延迟 (Delayed - 仓储延迟)</option>
              <option value="degraded">服务降级 (Degraded - IoT与履约不可用)</option>
            </select>
          </div>

          <div class="state-controller">
            <label class="review-label">四态切换:</label>
            <select v-model="simulateState" class="sync-select" @change="loadDashboardData(false)">
              <option value="normal">正常呈现 (Ready)</option>
              <option value="empty">空数据态 (Empty)</option>
              <option value="error">聚合异常 (Error)</option>
            </select>
          </div>

          <div class="sync-meta-text">
            <span>源事实更新:</span>
            <span class="sync-time">{{ overviewData?.sourceUpdatedAt || "实时" }}</span>
            <StatusBadge
              :type="overviewData?.staleCardsCount ? 'warning' : 'success'"
              :text="overviewData?.staleCardsCount ? `部分陈旧 (${overviewData.staleCardsCount})` : '全域正常'"
            />
          </div>
        </div>
      </section>

      <!-- 陈旧数据降级警告横幅 (StaleDataBanner) -->
      <StaleDataBanner
        :visible="(overviewData?.staleCardsCount || 0) > 0"
        :stale-count="overviewData?.staleCardsCount || 0"
        :stale-since="overviewData?.cards.device?.staleSince || '2026-08-26 15:45:00'"
        :loading="isRefreshing"
        @retry="handleManualRefresh"
      />

      <!-- 界面四态展示区域 -->
      <div class="dashboard-content-area">
        <!-- 1. 加载态 (Loading) -->
        <div v-if="viewState === 'loading'" class="state-loading-box">
          <span class="spinner-large">⏳</span>
          <p class="loading-msg">正在跨 7 大业务事实域并发聚合快照指标...</p>
        </div>

        <!-- 2. 全局错误态 (Error) -->
        <div v-else-if="viewState === 'error'" class="state-error-box">
          <ErrorState
            title="综合看板指标聚合失败"
            :message="errorMessage"
            code="GIS_QUERY_002"
            detail="各业务服务网关不可达或聚合超时，可点击下方重试按钮重新装载。"
            @retry="handleManualRefresh"
          />
        </div>

        <!-- 3. 空数据态 (Empty) -->
        <div v-else-if="viewState === 'empty' || !overviewData" class="state-empty-box">
          <EmptyState
            icon="📊"
            title="当前时间范围内暂无聚合指标"
            description="该租户在所选统计时间段内未产生业务事实或权限范围为空。"
          >
            <template #action>
              <button type="button" class="btn-refresh-pill" @click="setTimeRange('today')">
                切换至今日
              </button>
            </template>
          </EmptyState>
        </div>

        <!-- 4. 就绪态 (Ready)：七类卡片网格布局 -->
        <div v-else class="cards-dashboard-grid">
          <!-- 1. 仓储库存事实 -->
          <SummaryCard
            :card="overviewData.cards.inventory"
            :loading="cardLoadingMap.inventory"
            @refresh="refreshSingleCard"
            @penetrate="handlePenetrate"
          />

          <!-- 2. 采购销售履约 -->
          <SummaryCard
            :card="overviewData.cards.fulfillment"
            :loading="cardLoadingMap.fulfillment"
            @refresh="refreshSingleCard"
            @penetrate="handlePenetrate"
          />

          <!-- 3. 制造工序执行 -->
          <SummaryCard
            :card="overviewData.cards.manufacturing"
            :loading="cardLoadingMap.manufacturing"
            @refresh="refreshSingleCard"
            @penetrate="handlePenetrate"
          />

          <!-- 4. 质量管控事实 -->
          <SummaryCard
            :card="overviewData.cards.quality"
            :loading="cardLoadingMap.quality"
            @refresh="refreshSingleCard"
            @penetrate="handlePenetrate"
          />

          <!-- 5. IoT 设备状态 -->
          <SummaryCard
            :card="overviewData.cards.device"
            :loading="cardLoadingMap.device"
            @refresh="refreshSingleCard"
            @penetrate="handlePenetrate"
          />

          <!-- 6. 告警监控事实 -->
          <SummaryCard
            :card="overviewData.cards.alarm"
            :loading="cardLoadingMap.alarm"
            @refresh="refreshSingleCard"
            @penetrate="handlePenetrate"
          />

          <!-- 7. 黄金闭环追溯 -->
          <SummaryCard
            :card="overviewData.cards.traceability"
            :loading="cardLoadingMap.traceability"
            @refresh="refreshSingleCard"
            @penetrate="handlePenetrate"
          />
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
/**
 * 首页综合监控看板主视图与阶段7集成枢纽 (DashboardView)
 * 职责：
 * 1. 集中展示 7 大事实域（库存、履约、制造、质量、设备、告警、追溯）指标卡片；
 * 2. 30 秒自动轮询与手动即时刷新；
 * 3. 针对单卡片异常独立降级，与 StaleDataBanner 联动展示陈旧时间戳；
 * 4. 支持今日、近 7 天、近 30 天统计时间切换；
 * 5. 完整四态（Loading, Ready, Empty, Error）及本地高质量 Fixture 回退；
 * 6. 支持与二维 GIS 站点地图 (SiteMapListView) 及全闭环追溯中心 (TraceabilityView) 顶部子导航无缝切换。
 */

import { ref, reactive, onMounted, onUnmounted } from "vue";
import { useRouter } from "vue-router";
import type {
  DashboardOverviewData,
  DashboardTimeRange,
  DashboardCardType,
} from "../../types/insights";
import type { ViewState } from "../../types/common";
import { fetchDashboardOverview } from "../../api/insights";
import PageHeader from "../../components/common/PageHeader.vue";
import StatusBadge from "../../components/common/StatusBadge.vue";
import EmptyState from "../../components/common/EmptyState.vue";
import ErrorState from "../../components/common/ErrorState.vue";
import SummaryCard from "./components/SummaryCard.vue";
import StaleDataBanner from "./components/StaleDataBanner.vue";
import SiteMapListView from "./SiteMapListView.vue";
import TraceabilityView from "./TraceabilityView.vue";

const emit = defineEmits<{
  (e: "navigate-map"): void;
  (e: "navigate-trace"): void;
}>();

const router = useRouter();

// 活跃子模块：看板 (dashboard)、地图 (sitemap)、追溯 (traceability)
const activeModule = ref<"dashboard" | "sitemap" | "traceability">("dashboard");

// 界面状态
const viewState = ref<ViewState>("loading");
const errorMessage = ref<string>("");
const isRefreshing = ref(false);
const currentTimeRange = ref<DashboardTimeRange>("today");
const simulatedSyncMode = ref<"healthy" | "delayed" | "degraded">("healthy");
const simulateState = ref<"normal" | "empty" | "error">("normal");

// 看板全景数据
const overviewData = ref<DashboardOverviewData | null>(null);

// 单卡片刷新中状态字典
const cardLoadingMap = reactive<Record<DashboardCardType, boolean>>({
  inventory: false,
  fulfillment: false,
  manufacturing: false,
  quality: false,
  device: false,
  alarm: false,
  traceability: false,
});

// 30 秒倒计时与定时器
const countdownSeconds = ref(30);
let timerId: number | null = null;

/**
 * 装载看板全域指标数据
 * @param silent 是否静默刷新（自动轮询时不全屏 loading）
 */
async function loadDashboardData(silent: boolean = false) {
  if (!silent) {
    viewState.value = "loading";
  }
  isRefreshing.value = true;
  errorMessage.value = "";

  // 根据模拟同步模式决定降级领域
  let degradedDomains: DashboardCardType[] = [];
  if (simulatedSyncMode.value === "delayed") {
    degradedDomains = ["inventory"];
  } else if (simulatedSyncMode.value === "degraded") {
    degradedDomains = ["device", "fulfillment"];
  }

  try {
    const data = await fetchDashboardOverview({
      timeRange: currentTimeRange.value,
      degradedDomains,
      simulateState: simulateState.value,
    });

    overviewData.value = data;
    viewState.value = "ready";
  } catch (err: any) {
    viewState.value = "error";
    errorMessage.value = err?.message || "综合看板装载超时";
  } finally {
    isRefreshing.value = false;
  }
}

/**
 * 设置时间范围
 */
function setTimeRange(range: DashboardTimeRange) {
  if (currentTimeRange.value === range) return;
  currentTimeRange.value = range;
  countdownSeconds.value = 30;
  loadDashboardData(false);
}

/**
 * 同步模式变更处理
 */
function handleSyncModeChange() {
  loadDashboardData(true);
}

/**
 * 手动刷新看板
 */
function handleManualRefresh() {
  countdownSeconds.value = 30;
  loadDashboardData(false);
}

/**
 * 单卡片独立刷新 (不影响其余 6 张卡片)
 */
async function refreshSingleCard(type: DashboardCardType) {
  cardLoadingMap[type] = true;
  try {
    // 单卡局部刷新：模拟重新拉取该单项
    await new Promise((resolve) => setTimeout(resolve, 600));
    if (overviewData.value && overviewData.value.cards[type]) {
      const card = overviewData.value.cards[type];
      card.stale = false;
      card.staleSince = undefined;
      card.error = undefined;
      card.generatedAt = new Date().toLocaleString();
      card.sourceUpdatedAt = new Date().toLocaleString();

      // 重新统计陈旧卡片数
      overviewData.value.staleCardsCount = Object.values(overviewData.value.cards).filter((c) => c.stale).length;
    }
  } catch (err: any) {
    if (overviewData.value && overviewData.value.cards[type]) {
      overviewData.value.cards[type].error = err?.message || "单项刷新失败";
    }
  } finally {
    cardLoadingMap[type] = false;
  }
}

/**
 * 启动 30 秒倒计时轮询器
 */
function startPollingTimer() {
  stopPollingTimer();
  timerId = window.setInterval(() => {
    countdownSeconds.value--;
    if (countdownSeconds.value <= 0) {
      countdownSeconds.value = 30;
      loadDashboardData(true);
    }
  }, 1000);
}

function stopPollingTimer() {
  if (timerId !== null) {
    clearInterval(timerId);
    timerId = null;
  }
}

/**
 * 穿透业务控制台
 */
function handlePenetrate(routePath: string) {
  if (routePath === "/gis") {
    activeModule.value = "sitemap";
  } else {
    router.push(routePath);
  }
}

onMounted(() => {
  loadDashboardData(false);
  startPollingTimer();
});

onUnmounted(() => {
  stopPollingTimer();
});
</script>

<style scoped>
.dashboard-view-container {
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

.btn-refresh-now {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 7px 14px;
  background: #0284c7;
  border: 1px solid #0369a1;
  color: #ffffff;
  border-radius: 6px;
  font-size: 13px;
  font-weight: 500;
  cursor: pointer;
  transition: all 0.2s;
}

.btn-refresh-now:hover:not(:disabled) {
  background: #0369a1;
}

.countdown-badge {
  font-size: 11px;
  font-family: var(--font-mono, monospace);
  background: rgba(0, 0, 0, 0.25);
  padding: 1px 6px;
  border-radius: 4px;
}

.is-spinning {
  display: inline-block;
  animation: spin 1s linear infinite;
}

/* 时间与同步状态控制条 */
.control-review-bar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  background: rgba(15, 23, 42, 0.7);
  border: 1px solid rgba(255, 255, 255, 0.08);
  border-radius: 8px;
  padding: 12px 18px;
  margin-bottom: 20px;
  flex-wrap: wrap;
}

.range-block {
  display: flex;
  align-items: center;
  gap: 12px;
  flex-wrap: wrap;
}

.review-label {
  font-size: 11px;
  color: #94a3b8;
}

.scenario-tabs {
  display: flex;
  gap: 6px;
}

.tab-btn-pill {
  padding: 4px 12px;
  border-radius: 20px;
  font-size: 12px;
  background: rgba(30, 41, 59, 0.7);
  border: 1px solid rgba(255, 255, 255, 0.1);
  color: #cbd5e1;
  cursor: pointer;
  transition: all 0.2s;
}

.tab-btn-pill.is-active {
  background: #0284c7;
  border-color: #0369a1;
  color: #ffffff;
  font-weight: 600;
}

.effective-range-text {
  font-size: 13px;
  color: #f1f5f9;
  font-family: var(--font-mono, monospace);
}

.sync-status-block {
  display: flex;
  align-items: center;
  gap: 16px;
  flex-wrap: wrap;
}

.sync-controller,
.state-controller {
  display: flex;
  align-items: center;
  gap: 6px;
}

.sync-select {
  background: rgba(30, 41, 59, 0.8);
  border: 1px solid rgba(255, 255, 255, 0.12);
  color: #f8fafc;
  padding: 4px 8px;
  border-radius: 6px;
  font-size: 11px;
  outline: none;
}

.sync-meta-text {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 11px;
  color: #94a3b8;
}

.sync-time {
  font-family: var(--font-mono, monospace);
  color: #cbd5e1;
}

/* 内容区 */
.dashboard-content-area {
  min-height: 480px;
}

.state-loading-box,
.state-empty-box,
.state-error-box {
  padding: 80px 20px;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  text-align: center;
}

.spinner-large {
  font-size: 40px;
}

.loading-msg {
  font-size: 14px;
  color: #94a3b8;
  margin-top: 12px;
}

.btn-refresh-pill {
  padding: 6px 16px;
  background: #0284c7;
  border: none;
  border-radius: 6px;
  color: #ffffff;
  font-size: 12px;
  cursor: pointer;
}

/* 七类卡片网格布局 */
.cards-dashboard-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(360px, 1fr));
  gap: 20px;
}

@keyframes spin {
  from {
    transform: rotate(0deg);
  }
  to {
    transform: rotate(360deg);
  }
}
</style>
