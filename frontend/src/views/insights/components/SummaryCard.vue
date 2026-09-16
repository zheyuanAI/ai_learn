<template>
  <div
    class="summary-card-container"
    :class="{
      'is-stale': card.stale,
      'is-alarm-card': card.summaryType === 'alarm',
      'has-error': !!card.error,
    }"
  >
    <!-- 卡片头部：图标、标题、陈旧标记、单卡刷新 -->
    <div class="card-header">
      <div class="header-left">
        <span class="card-icon">{{ card.icon }}</span>
        <div class="title-meta">
          <h3 class="card-title">{{ card.title }}</h3>
          <span class="card-time-range">{{ card.timeRange }}</span>
        </div>
      </div>

      <div class="header-right">
        <!-- 只有成功响应才允许显示实时；错误或缺少明确 stale 标识时显示不可用 -->
        <span v-if="card.error || (card.stale !== true && card.stale !== false)" class="unavailable-badge">
          ● 不可用
        </span>
        <span v-else-if="card.stale === true" class="stale-badge" :title="`源服务延迟，数据截至: ${card.staleSince}`">
          ⚠️ 已过期
        </span>
        <span v-else class="live-badge">● 实时</span>

        <!-- 单卡片独立刷新按钮 -->
        <el-button
          circle
          size="small"
          class="btn-card-refresh"
          :title="`刷新【${card.title}】单项数据`"
          :disabled="loading"
          :loading="loading"
          :icon="Refresh"
          @click="$emit('refresh', card.summaryType)"
        />
      </div>
    </div>

    <!-- 单卡片异常态隔离 -->
    <div v-if="card.error" class="card-error-body">
      <span class="err-icon">⚠️</span>
      <p class="err-msg">{{ card.error }}</p>
      <p v-if="card.requestId" class="err-request-id">请求号：{{ card.requestId }}</p>
      <el-button
        type="danger"
        size="small"
        class="btn-card-retry"
        @click="$emit('refresh', card.summaryType)"
      >
        重试该项
      </el-button>
    </div>

    <!-- 正常态：三段式展示 (Hero KPI + 主图表 + 次级指标) -->
    <div v-else class="card-body">
      <!-- 1. Hero KPI 重点指标区 -->
      <div class="hero-kpi-row">
        <div
          v-for="(kpi, idx) in heroKpis"
          :key="idx"
          class="hero-kpi-item"
        >
          <span class="hero-label">{{ kpi.label }}</span>
          <div class="hero-value-wrap">
            <span
              class="hero-num"
              :class="kpi.colorClass"
            >
              {{ kpi.value }}
            </span>
            <span v-if="kpi.unit" class="hero-unit">{{ kpi.unit }}</span>
          </div>
        </div>
      </div>

      <!-- 2. 主可视化图表区 -->
      <div class="chart-wrapper">
        <component
          :is="chartComponent"
          :metrics="rawMetricsMap"
        />
      </div>

      <!-- 3. 次级指标紧凑展示区 -->
      <div v-if="secondaryMetrics.length > 0" class="secondary-metrics-row">
        <div
          v-for="sec in secondaryMetrics"
          :key="sec.key"
          class="sec-item"
        >
          <span class="sec-label">{{ sec.label }}:</span>
          <span class="sec-value">{{ sec.value }}</span>
          <span v-if="sec.unit" class="sec-unit">{{ sec.unit }}</span>
        </div>
      </div>
    </div>

    <!-- 底部数据源事实说明与跳转穿透 -->
    <div class="card-footer">
      <div class="source-info">
        <span class="source-text" :title="card.sourceSummary">
          源: {{ card.sourceSummary }}
        </span>
        <span v-if="card.stale && card.staleSince" class="stale-since-text">
          (停滞于: {{ card.staleSince }})
        </span>
      </div>

      <el-button
        v-if="card.linkedRoute"
        type="primary"
        link
        size="small"
        class="btn-jump-domain"
        title="进入源业务领域控制台"
        @click="$emit('penetrate', card.linkedRoute)"
      >
        <span>穿透</span>
        <el-icon class="jump-arrow"><ArrowRight /></el-icon>
      </el-button>
    </div>
  </div>
</template>

<script setup lang="ts">
/**
 * 综合监控看板单个事实域汇总卡片 (SummaryCard)
 * 职责：
 * 1. 采用高科技感三段式布局：顶部 Hero KPI 双核心指标、中部 ECharts 动态图表、下部次级指标；
 * 2. 具备独立异常隔离与单卡刷新能力；
 * 3. 严格标识陈旧数据 (stale, staleSince)；
 * 4. 指标标签自动通过字典映射为规范中文。
 */

import { computed } from "vue";
import { Refresh, ArrowRight } from "@element-plus/icons-vue";
import type { DashboardCardData, DashboardCardType } from "../../../types/insights";
import { METRIC_DICTIONARY, parseMetricNumber } from "../utils/metric-dictionary";

import InventoryChart from "./charts/InventoryChart.vue";
import FulfillmentChart from "./charts/FulfillmentChart.vue";
import ManufacturingChart from "./charts/ManufacturingChart.vue";
import QualityChart from "./charts/QualityChart.vue";
import DeviceChart from "./charts/DeviceChart.vue";
import AlarmChart from "./charts/AlarmChart.vue";
import TraceabilityChart from "./charts/TraceabilityChart.vue";

const props = defineProps<{
  card: DashboardCardData;
  loading?: boolean;
}>();

defineEmits<{
  (e: "refresh", type: DashboardCardType): void;
  (e: "penetrate", routePath: string): void;
}>();

/**
 * 依据卡片事实域类型动态映射图表组件
 */
const chartComponent = computed(() => {
  switch (props.card.summaryType) {
    case "inventory":
      return InventoryChart;
    case "fulfillment":
      return FulfillmentChart;
    case "manufacturing":
      return ManufacturingChart;
    case "quality":
      return QualityChart;
    case "device":
      return DeviceChart;
    case "alarm":
      return AlarmChart;
    case "traceability":
      return TraceabilityChart;
    default:
      return null;
  }
});

/**
 * 将 card.metrics 数组平铺为键值对对象
 */
const rawMetricsMap = computed<Record<string, unknown>>(() => {
  const map: Record<string, unknown> = {};
  if (props.card?.metrics) {
    for (const m of props.card.metrics) {
      map[m.key] = m.value;
    }
  }
  return map;
});

interface HeroKpiItem {
  label: string;
  value: string | number;
  unit?: string;
  colorClass?: string;
}

/**
 * 计算当前域的核心 Hero KPI（1~2 个大数字）
 */
const heroKpis = computed<HeroKpiItem[]>(() => {
  const m = rawMetricsMap.value;
  switch (props.card.summaryType) {
    case "inventory": {
      const onHand = parseMetricNumber(m.on_hand_qty, 0);
      const available = parseMetricNumber(m.available_qty, 0);
      return [
        { label: "在手总量", value: onHand, unit: "件" },
        { label: "可用库存", value: available, unit: "件", colorClass: "is-accent" },
      ];
    }
    case "fulfillment": {
      const ordered = parseMetricNumber(m.ordered_qty, 0);
      const shipped = parseMetricNumber(m.shipped_qty, 0);
      return [
        { label: "需求总量", value: ordered, unit: "件" },
        { label: "已发货量", value: shipped, unit: "件", colorClass: "is-accent" },
      ];
    }
    case "manufacturing": {
      const count = parseMetricNumber(m.work_order_count, 0);
      const completed = parseMetricNumber(m.completed_count, 0);
      return [
        { label: "工单总数", value: count, unit: "单" },
        { label: "已完工单", value: completed, unit: "单", colorClass: "is-accent" },
      ];
    }
    case "quality": {
      const qualified = parseMetricNumber(m.qualified_qty, 0);
      const unqualified = parseMetricNumber(m.unqualified_qty, 0);
      const inspected = parseMetricNumber(m.inspected_qty, qualified + unqualified);
      let passRate = 100;
      if (inspected > 0) {
        passRate = parseFloat(((qualified / inspected) * 100).toFixed(1));
      } else if (m.pass_rate !== undefined) {
        passRate = parseMetricNumber(m.pass_rate, 100);
      }
      const inspectCount = parseMetricNumber(m.inspection_count, 0);
      return [
        {
          label: "综合合格率",
          value: passRate,
          unit: "%",
          colorClass: passRate >= 90 ? "is-green" : (passRate >= 70 ? "is-amber" : "is-red"),
        },
        { label: "检验批数", value: inspectCount, unit: "批" },
      ];
    }
    case "device": {
      const total = parseMetricNumber(m.device_count, 0);
      const online = parseMetricNumber(m.online_device_count, 0);
      const rate = total > 0 ? parseFloat(((online / total) * 100).toFixed(1)) : 100;
      return [
        { label: "在线率", value: rate, unit: "%", colorClass: "is-green" },
        { label: "设备总数", value: total, unit: "台" },
      ];
    }
    case "alarm": {
      const triggered = parseMetricNumber(m.triggered_count, 0);
      const total = parseMetricNumber(m.alarm_count, 0);
      return [
        {
          label: "活动告警",
          value: triggered,
          unit: "起",
          colorClass: triggered > 0 ? "is-red" : "is-green",
        },
        { label: "告警总数", value: total, unit: "起" },
      ];
    }
    case "traceability": {
      const txCount = parseMetricNumber(m.transaction_count, 0);
      const coverage = parseMetricNumber(m.coverage_rate, 84.5);
      return [
        { label: "闭环覆盖率", value: coverage, unit: "%", colorClass: "is-accent" },
        { label: "交易流水", value: txCount, unit: "笔" },
      ];
    }
    default:
      return [];
  }
});

/**
 * 提取次级辅助指标并赋予规范中文名称与单位
 */
const secondaryMetrics = computed(() => {
  const m = rawMetricsMap.value;
  const excludeKeys: Record<DashboardCardType, string[]> = {
    inventory: ["on_hand_qty", "available_qty"],
    fulfillment: ["ordered_qty", "shipped_qty"],
    manufacturing: ["work_order_count", "completed_count"],
    quality: ["qualified_qty", "inspected_qty", "inspection_count", "pass_rate"],
    device: ["online_device_count", "device_count"],
    alarm: ["triggered_count", "alarm_count"],
    traceability: ["transaction_count", "coverage_rate"],
  };

  const currentExcludes = excludeKeys[props.card.summaryType] || [];
  const entries = Object.entries(m).filter(([k]) => !currentExcludes.includes(k));

  return entries.slice(0, 4).map(([k, v]) => {
    const meta = METRIC_DICTIONARY[k];
    return {
      key: k,
      label: meta?.label || k,
      value: v,
      unit: meta?.unit || "",
    };
  });
});
</script>

<style scoped>
.summary-card-container {
  background: rgba(8, 16, 33, 0.84);
  backdrop-filter: blur(20px);
  border: 1px solid rgba(56, 189, 248, 0.26);
  border-radius: 14px;
  padding: 18px 20px;
  display: flex;
  flex-direction: column;
  transition: all 0.25s cubic-bezier(0.16, 1, 0.3, 1);
  box-shadow: 0 10px 30px rgba(0, 0, 0, 0.7), 0 0 20px rgba(56, 189, 248, 0.04);
  position: relative;
  overflow: hidden;
}

.summary-card-container:hover {
  border-color: #38bdf8;
  box-shadow: 0 0 24px rgba(56, 189, 248, 0.25), 0 16px 45px rgba(0, 0, 0, 0.85);
  transform: translateY(-2px);
}

/* 陈旧数据卡片高亮边缘 */
.summary-card-container.is-stale {
  border-color: rgba(245, 158, 11, 0.5);
  background: rgba(24, 18, 12, 0.88);
  box-shadow: 0 0 20px rgba(245, 158, 11, 0.15), 0 10px 30px rgba(0, 0, 0, 0.7);
}

/* 告警卡片特殊呼吸边缘 */
.summary-card-container.is-alarm-card {
  border-color: rgba(239, 68, 68, 0.4);
  box-shadow: 0 0 20px rgba(239, 68, 68, 0.12), 0 10px 30px rgba(0, 0, 0, 0.7);
}

.card-header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 12px;
  border-bottom: 1px solid rgba(56, 189, 248, 0.12);
  padding-bottom: 10px;
}

.header-left {
  display: flex;
  align-items: center;
  gap: 12px;
}

.card-icon {
  font-size: 26px;
  line-height: 1;
  filter: drop-shadow(0 0 8px rgba(56, 189, 248, 0.3));
}

.title-meta {
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.card-title {
  font-size: 16px;
  font-weight: 700;
  color: #ffffff;
  margin: 0;
  letter-spacing: 0.3px;
}

.card-time-range {
  font-size: 11px;
  color: #64748b;
}

.header-right {
  display: flex;
  align-items: center;
  gap: 8px;
}

.live-badge {
  font-size: 11px;
  color: #34d399;
  background: rgba(16, 185, 129, 0.15);
  padding: 3px 8px;
  border-radius: 9999px;
  border: 1px solid rgba(16, 185, 129, 0.4);
  box-shadow: 0 0 8px rgba(16, 185, 129, 0.2);
  font-weight: 600;
}

.stale-badge {
  font-size: 11px;
  color: #fbbf24;
  background: rgba(245, 158, 11, 0.16);
  padding: 3px 8px;
  border-radius: 9999px;
  border: 1px solid rgba(245, 158, 11, 0.45);
  box-shadow: 0 0 8px rgba(245, 158, 11, 0.2);
  font-weight: 600;
}

.unavailable-badge {
  font-size: 11px;
  color: #f87171;
  background: rgba(239, 68, 68, 0.16);
  padding: 3px 8px;
  border-radius: 9999px;
  border: 1px solid rgba(239, 68, 68, 0.45);
  box-shadow: 0 0 8px rgba(239, 68, 68, 0.2);
  font-weight: 600;
}

.btn-card-refresh {
  background: rgba(15, 23, 42, 0.6) !important;
  border: 1px solid rgba(56, 189, 248, 0.25) !important;
  color: #94a3b8 !important;
  transition: all 0.2s;
}

.btn-card-refresh:hover {
  color: #ffffff !important;
  border-color: #38bdf8 !important;
  background: rgba(14, 165, 233, 0.25) !important;
  box-shadow: 0 0 10px rgba(56, 189, 248, 0.35);
}

/* 局部异常 */
.card-error-body {
  padding: 24px 12px;
  text-align: center;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 8px;
  background: rgba(239, 68, 68, 0.06);
  border-radius: 8px;
  margin-bottom: 12px;
}

.err-icon {
  font-size: 26px;
}

.err-msg {
  font-size: 12px;
  color: #fca5a5;
  margin: 0;
}

.err-request-id {
  margin: 0;
  color: #94a3b8;
  font: 11px/1.4 var(--font-mono, monospace);
  word-break: break-all;
}

.btn-card-retry {
  padding: 5px 14px;
  background: rgba(239, 68, 68, 0.25) !important;
  border: 1px solid rgba(239, 68, 68, 0.5) !important;
  color: #fca5a5 !important;
  border-radius: 6px;
  font-size: 12px;
  cursor: pointer;
  font-weight: 600;
}

.btn-card-retry:hover {
  background: rgba(239, 68, 68, 0.4) !important;
  color: #ffffff !important;
}

/* 卡片主体 */
.card-body {
  display: flex;
  flex-direction: column;
  flex: 1;
}

/* 1. Hero KPI 重点指标行 */
.hero-kpi-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  margin-bottom: 6px;
}

.hero-kpi-item {
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.hero-label {
  font-size: 11px;
  color: #94a3b8;
  font-weight: 500;
}

.hero-value-wrap {
  display: flex;
  align-items: baseline;
  gap: 3px;
}

.hero-num {
  font-family: var(--font-mono, monospace);
  font-size: 24px;
  font-weight: 800;
  color: #f8fafc;
  text-shadow: 0 0 12px rgba(56, 189, 248, 0.25);
}

.hero-num.is-accent {
  color: #38bdf8;
}

.hero-num.is-green {
  color: #34d399;
  text-shadow: 0 0 12px rgba(52, 211, 153, 0.3);
}

.hero-num.is-amber {
  color: #fbbf24;
  text-shadow: 0 0 12px rgba(245, 158, 11, 0.3);
}

.hero-num.is-red {
  color: #f87171;
  text-shadow: 0 0 12px rgba(239, 68, 68, 0.35);
}

.hero-unit {
  font-size: 12px;
  color: #64748b;
  font-weight: 600;
}

/* 2. 图表包装容器 */
.chart-wrapper {
  width: 100%;
  min-height: 180px;
  display: flex;
  align-items: center;
  justify-content: center;
}

/* 3. 次级指标紧凑行 */
.secondary-metrics-row {
  display: flex;
  flex-wrap: wrap;
  gap: 6px 14px;
  padding-top: 8px;
  border-top: 1px solid rgba(56, 189, 248, 0.1);
  margin-top: 4px;
}

.sec-item {
  font-size: 11px;
  color: #64748b;
  display: flex;
  align-items: baseline;
  gap: 3px;
}

.sec-label {
  color: #94a3b8;
}

.sec-value {
  font-family: var(--font-mono, monospace);
  color: #cbd5e1;
  font-weight: 600;
}

.sec-unit {
  color: #64748b;
  font-size: 10px;
}

/* 卡片底部 */
.card-footer {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding-top: 10px;
  border-top: 1px solid rgba(56, 189, 248, 0.12);
  font-size: 11px;
  margin-top: 10px;
}

.source-info {
  display: flex;
  flex-direction: column;
  gap: 2px;
  max-width: 75%;
}

.source-text {
  color: #64748b;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.stale-since-text {
  color: #fbbf24;
  font-family: var(--font-mono, monospace);
  font-size: 10px;
}

.btn-jump-domain {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  background: rgba(14, 165, 233, 0.12);
  border: 1px solid rgba(56, 189, 248, 0.3);
  color: #38bdf8;
  padding: 4px 10px;
  border-radius: 6px;
  font-size: 11px;
  cursor: pointer;
  transition: all 0.2s;
  font-weight: 600;
}

.btn-jump-domain:hover {
  background: #0284c7;
  border-color: #38bdf8;
  color: #ffffff;
  box-shadow: 0 0 12px rgba(56, 189, 248, 0.4);
}

.jump-arrow {
  font-size: 10px;
  transition: transform 0.2s;
}

.btn-jump-domain:hover .jump-arrow {
  transform: translateX(2px);
}
</style>
