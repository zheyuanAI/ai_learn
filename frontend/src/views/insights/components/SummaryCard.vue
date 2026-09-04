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
        <!-- 陈旧数据标记 -->
        <span v-if="card.stale" class="stale-badge" :title="`源服务延迟，数据截至: ${card.staleSince}`">
          ⚠️ 已过期
        </span>
        <span v-else class="live-badge">● 实时</span>

        <!-- 单卡片独立刷新按钮 -->
        <button
          type="button"
          class="btn-card-refresh"
          :title="`刷新【${card.title}】单项数据`"
          :disabled="loading"
          @click="$emit('refresh', card.summaryType)"
        >
          <span :class="{ 'is-spinning': loading }">🔄</span>
        </button>
      </div>
    </div>

    <!-- 单卡片异常态隔离 -->
    <div v-if="card.error" class="card-error-body">
      <span class="err-icon">⚠️</span>
      <p class="err-msg">{{ card.error }}</p>
      <button
        type="button"
        class="btn-card-retry"
        @click="$emit('refresh', card.summaryType)"
      >
        重试该项
      </button>
    </div>

    <!-- 核心指标网格 (3~5 个核心指标) -->
    <div v-else class="metrics-grid">
      <div
        v-for="m in card.metrics"
        :key="m.key"
        class="metric-box"
        :class="`status-${m.status || 'normal'}`"
      >
        <span class="metric-label">{{ m.label }}</span>
        <div class="metric-value-row">
          <QuantityText
            v-if="m.isQuantity"
            :value="m.value"
            :unit="m.unit"
          />
          <template v-else>
            <span class="metric-num">{{ m.value }}</span>
            <span v-if="m.unit" class="metric-unit">{{ m.unit }}</span>
          </template>
        </div>
        <span v-if="m.subText" class="metric-sub">{{ m.subText }}</span>
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

      <button
        v-if="card.linkedRoute"
        type="button"
        class="btn-jump-domain"
        title="进入源业务领域控制台"
        @click="$emit('penetrate', card.linkedRoute)"
      >
        <span>穿透</span>
        <span class="jump-arrow">➔</span>
      </button>
    </div>
  </div>
</template>

<script setup lang="ts">
/**
 * 综合监控看板单个事实域汇总卡片 (SummaryCard)
 * 职责：
 * 1. 展示库存、履约、制造、质量、设备、告警、追溯等 7 大领域 3~5 个核心指标；
 * 2. 具备独立异常隔离与单卡刷新能力；
 * 3. 严格标识陈旧数据 (stale, staleSince)；
 * 4. 复用 QuantityText 防止数量失真。
 */

import type { DashboardCardData, DashboardCardType } from "../../../types/insights";
import QuantityText from "../../../components/common/QuantityText.vue";

defineProps<{
  card: DashboardCardData;
  loading?: boolean;
}>();

defineEmits<{
  (e: "refresh", type: DashboardCardType): void;
  (e: "penetrate", routePath: string): void;
}>();
</script>

<style scoped>
.summary-card-container {
  background: rgba(15, 23, 42, 0.85);
  border: 1px solid rgba(255, 255, 255, 0.1);
  border-radius: 10px;
  padding: 16px 18px;
  display: flex;
  flex-direction: column;
  transition: all 0.2s ease;
  box-shadow: 0 4px 16px rgba(0, 0, 0, 0.2);
  position: relative;
  overflow: hidden;
}

.summary-card-container:hover {
  border-color: rgba(56, 189, 248, 0.35);
  box-shadow: 0 6px 20px rgba(0, 0, 0, 0.3);
  transform: translateY(-2px);
}

/* 陈旧数据卡片高亮边缘 */
.summary-card-container.is-stale {
  border-color: rgba(245, 158, 11, 0.45);
  background: rgba(28, 22, 16, 0.85);
}

/* 告警卡片特殊呼吸边缘 */
.summary-card-container.is-alarm-card {
  border-color: rgba(239, 68, 68, 0.3);
}

.card-header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 10px;
  margin-bottom: 14px;
}

.header-left {
  display: flex;
  align-items: center;
  gap: 10px;
}

.card-icon {
  font-size: 24px;
  line-height: 1;
}

.title-meta {
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.card-title {
  font-size: 15px;
  font-weight: 700;
  color: #f1f5f9;
  margin: 0;
}

.card-time-range {
  font-size: 11px;
  color: #94a3b8;
}

.header-right {
  display: flex;
  align-items: center;
  gap: 8px;
}

.live-badge {
  font-size: 10px;
  color: #34d399;
  background: rgba(52, 211, 153, 0.1);
  padding: 2px 6px;
  border-radius: 4px;
  border: 1px solid rgba(52, 211, 153, 0.25);
}

.stale-badge {
  font-size: 10px;
  color: #fbbf24;
  background: rgba(245, 158, 11, 0.15);
  padding: 2px 6px;
  border-radius: 4px;
  border: 1px solid rgba(245, 158, 11, 0.35);
  font-weight: 600;
}

.btn-card-refresh {
  background: transparent;
  border: none;
  color: #94a3b8;
  font-size: 13px;
  cursor: pointer;
  padding: 2px 4px;
  border-radius: 4px;
  transition: all 0.2s;
}

.btn-card-refresh:hover {
  color: #38bdf8;
  background: rgba(255, 255, 255, 0.05);
}

.is-spinning {
  display: inline-block;
  animation: spin 1s linear infinite;
}

/* 局部异常 */
.card-error-body {
  padding: 24px 12px;
  text-align: center;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 8px;
}

.err-icon {
  font-size: 24px;
}

.err-msg {
  font-size: 12px;
  color: #fca5a5;
  margin: 0;
}

.btn-card-retry {
  padding: 4px 12px;
  background: rgba(239, 68, 68, 0.2);
  border: 1px solid rgba(239, 68, 68, 0.4);
  color: #fca5a5;
  border-radius: 4px;
  font-size: 11px;
  cursor: pointer;
}

/* 核心指标栅格 */
.metrics-grid {
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: 10px;
  margin-bottom: 14px;
  flex: 1;
}

.metric-box {
  background: rgba(30, 41, 59, 0.5);
  border: 1px solid rgba(255, 255, 255, 0.05);
  border-radius: 6px;
  padding: 10px 12px;
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.metric-box.status-warning {
  border-color: rgba(251, 191, 36, 0.25);
  background: rgba(251, 191, 36, 0.06);
}

.metric-box.status-danger {
  border-color: rgba(248, 113, 113, 0.3);
  background: rgba(248, 113, 113, 0.08);
}

.metric-label {
  font-size: 11px;
  color: #94a3b8;
  white-space: nowrap;
}

.metric-value-row {
  display: flex;
  align-items: baseline;
  gap: 4px;
}

.metric-num {
  font-family: var(--font-mono, monospace);
  font-size: 18px;
  font-weight: 700;
  color: #f1f5f9;
}

.status-warning .metric-num {
  color: #fbbf24;
}

.status-danger .metric-num {
  color: #f87171;
}

.metric-unit {
  font-size: 11px;
  color: #94a3b8;
}

.metric-sub {
  font-size: 10px;
  color: #64748b;
}

/* 卡片底部 */
.card-footer {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding-top: 10px;
  border-top: 1px solid rgba(255, 255, 255, 0.06);
  font-size: 11px;
}

.source-info {
  display: flex;
  flex-direction: column;
  gap: 1px;
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
  background: transparent;
  border: 1px solid rgba(56, 189, 248, 0.25);
  color: #38bdf8;
  padding: 3px 8px;
  border-radius: 4px;
  font-size: 11px;
  cursor: pointer;
  transition: all 0.2s;
}

.btn-jump-domain:hover {
  background: rgba(56, 189, 248, 0.15);
  border-color: #38bdf8;
  color: #fff;
}

.jump-arrow {
  font-size: 9px;
  transition: transform 0.2s;
}

.btn-jump-domain:hover .jump-arrow {
  transform: translateX(2px);
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
