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
        <!-- 修改：只有成功响应才允许显示实时；错误或缺少明确 stale 标识时显示不可用。 -->
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
 * 1. 展示库存、履约、制造、质量、设备、告警、追溯等 7 大领域 3~5 个核心指标；
 * 2. 具备独立异常隔离与单卡刷新能力；
 * 3. 严格标识陈旧数据 (stale, staleSince)；
 * 4. 复用 QuantityText 防止数量失真。
 */

import { Refresh, ArrowRight } from "@element-plus/icons-vue";
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
  margin-bottom: 16px;
  border-bottom: 1px solid rgba(56, 189, 248, 0.12);
  padding-bottom: 12px;
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

/* 核心指标栅格 */
.metrics-grid {
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: 10px;
  margin-bottom: 14px;
  flex: 1;
}

.metric-box {
  background: rgba(15, 23, 42, 0.7);
  border: 1px solid rgba(56, 189, 248, 0.16);
  border-radius: 8px;
  padding: 10px 12px;
  display: flex;
  flex-direction: column;
  gap: 4px;
  transition: all 0.2s ease;
}

.metric-box:hover {
  border-color: rgba(56, 189, 248, 0.35);
  background: rgba(14, 165, 233, 0.1);
}

.metric-box.status-warning {
  border-color: rgba(251, 191, 36, 0.35);
  background: rgba(251, 191, 36, 0.08);
}

.metric-box.status-danger {
  border-color: rgba(248, 113, 113, 0.4);
  background: rgba(248, 113, 113, 0.1);
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
  font-size: 20px;
  font-weight: 800;
  color: #f8fafc;
  text-shadow: 0 0 10px rgba(56, 189, 248, 0.25);
}

.status-warning .metric-num {
  color: #fbbf24;
  text-shadow: 0 0 10px rgba(245, 158, 11, 0.35);
}

.status-danger .metric-num {
  color: #f87171;
  text-shadow: 0 0 10px rgba(239, 68, 68, 0.35);
}

.metric-unit {
  font-size: 11px;
  color: #64748b;
  font-weight: 500;
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
  padding-top: 12px;
  border-top: 1px solid rgba(56, 189, 248, 0.12);
  font-size: 11px;
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

@keyframes spin {
  from {
    transform: rotate(0deg);
  }
  to {
    transform: rotate(360deg);
  }
}
</style>
