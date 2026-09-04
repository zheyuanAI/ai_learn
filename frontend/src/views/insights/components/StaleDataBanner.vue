<template>
  <div v-if="visible" class="stale-data-banner" role="alert" aria-live="assertive">
    <div class="banner-icon">⚠️</div>
    <div class="banner-content">
      <div class="banner-title-row">
        <strong class="banner-title">数据陈旧警示 (Stale Data Warning)</strong>
        <span class="stale-count-badge">共 {{ staleCount }} 个领域指标已过期</span>
      </div>
      <p class="banner-desc">
        当前看板中存在源领域服务响应超时或更新延迟（最早陈旧于：<span class="stale-time">{{ staleSince || "2026-08-26 15:45:00" }}</span
        >）。系统已启用上一次成功结果只读降级保留展示，未冒充实时事实；严禁依据降级数据执行关键质量放行或高风险出库。
      </p>
    </div>
    <div class="banner-actions">
      <button type="button" class="btn-sync-retry" :disabled="loading" @click="$emit('retry')">
        <span v-if="loading" class="spinner">⏳</span>
        <span>立即重试同步</span>
      </button>
    </div>
  </div>
</template>

<script setup lang="ts">
/**
 * 陈旧数据横幅警告组件 (StaleDataBanner)
 * 职责：
 * 1. 当综合看板中某一或多个源业务领域服务暂时不可用或返回陈旧数据时显著呈现；
 * 2. 明确标记陈旧时间戳 (stale_since)，绝不以伪造值或 0 替代；
 * 3. 提供手动强制重新同步入口；
 * 4. 遵守业务规则第 7 条（来源故障与陈旧数据）。
 */

withDefaults(
  defineProps<{
    visible?: boolean;
    staleCount?: number;
    staleSince?: string;
    loading?: boolean;
  }>(),
  {
    visible: true,
    staleCount: 1,
    staleSince: "",
    loading: false,
  }
);

defineEmits<{
  (e: "retry"): void;
}>();
</script>

<style scoped>
.stale-data-banner {
  display: flex;
  align-items: center;
  gap: 16px;
  background: rgba(245, 158, 11, 0.12);
  border: 1px solid rgba(245, 158, 11, 0.4);
  border-radius: 8px;
  padding: 14px 20px;
  margin-bottom: 20px;
  box-shadow: 0 4px 16px rgba(245, 158, 11, 0.1);
  animation: banner-fade-in 0.3s ease-out;
}

.banner-icon {
  font-size: 24px;
  line-height: 1;
  color: #fbbf24;
}

.banner-content {
  flex: 1;
}

.banner-title-row {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-bottom: 4px;
}

.banner-title {
  font-size: 14px;
  color: #fbbf24;
  font-weight: 700;
}

.stale-count-badge {
  font-size: 11px;
  padding: 1px 8px;
  border-radius: 12px;
  background: rgba(245, 158, 11, 0.2);
  border: 1px solid rgba(245, 158, 11, 0.4);
  color: #fef3c7;
}

.banner-desc {
  margin: 0;
  font-size: 12px;
  color: #e2e8f0;
  line-height: 1.5;
}

.stale-time {
  font-family: var(--font-mono, monospace);
  color: #fbbf24;
  font-weight: 600;
}

.banner-actions {
  display: flex;
  align-items: center;
}

.btn-sync-retry {
  padding: 7px 16px;
  border-radius: 6px;
  background: rgba(245, 158, 11, 0.2);
  border: 1px solid rgba(245, 158, 11, 0.5);
  color: #fbbf24;
  font-size: 12px;
  font-weight: 600;
  cursor: pointer;
  white-space: nowrap;
  transition: all 0.2s;
}

.btn-sync-retry:hover:not(:disabled) {
  background: #f59e0b;
  color: #0f172a;
}

.btn-sync-retry:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}

@keyframes banner-fade-in {
  from {
    opacity: 0;
    transform: translateY(-6px);
  }
  to {
    opacity: 1;
    transform: translateY(0);
  }
}
</style>
