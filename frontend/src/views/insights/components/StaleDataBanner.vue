<template>
  <div v-if="visible" class="stale-data-banner" role="alert" aria-live="assertive">
    <div class="banner-icon">⚠️</div>
    <div class="banner-content">
      <div class="banner-title-row">
        <strong class="banner-title">数据陈旧警示 (Stale Data Warning)</strong>
        <span class="stale-count-badge">共 {{ staleCount }} 个领域指标已过期</span>
      </div>
      <p class="banner-desc">
        当前看板中存在源领域服务响应超时或更新延迟（最早陈旧于：<span class="stale-time">{{ staleSince || "服务端未提供陈旧起始时间" }}</span
        >）。系统已启用上一次成功结果只读降级保留展示，未冒充实时事实；严禁依据降级数据执行关键质量放行或高风险出库。
      </p>
    </div>
    <div class="banner-actions">
      <el-button
        type="warning"
        size="small"
        :loading="loading"
        @click="$emit('retry')"
      >
        立即重试同步
      </el-button>
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
  background: linear-gradient(90deg, rgba(245, 158, 11, 0.16) 0%, rgba(217, 119, 6, 0.08) 100%);
  border: 1px solid rgba(245, 158, 11, 0.45);
  border-radius: 12px;
  padding: 14px 22px;
  margin-bottom: 24px;
  box-shadow: 0 0 24px rgba(245, 158, 11, 0.15), 0 8px 24px rgba(0, 0, 0, 0.5);
  backdrop-filter: blur(14px);
  animation: banner-fade-in 0.3s ease-out;
}

.banner-icon {
  font-size: 24px;
  line-height: 1;
  color: #fbbf24;
  filter: drop-shadow(0 0 8px rgba(245, 158, 11, 0.5));
}

.banner-content {
  flex: 1;
}

.banner-title-row {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 4px;
}

.banner-title {
  font-size: 14px;
  color: #fbbf24;
  font-weight: 700;
  letter-spacing: 0.3px;
}

.stale-count-badge {
  font-size: 11px;
  padding: 2px 10px;
  border-radius: 9999px;
  background: rgba(245, 158, 11, 0.22);
  border: 1px solid rgba(245, 158, 11, 0.5);
  color: #fef3c7;
  font-weight: 600;
}

.banner-desc {
  margin: 0;
  font-size: 12px;
  color: #fde68a;
  line-height: 1.6;
  opacity: 0.92;
}

.stale-time {
  font-family: var(--font-mono, monospace);
  color: #fbbf24;
  font-weight: 700;
  text-shadow: 0 0 8px rgba(245, 158, 11, 0.4);
}

.banner-actions {
  display: flex;
  align-items: center;
}

:deep(.banner-actions .el-button--warning) {
  background: rgba(245, 158, 11, 0.25) !important;
  border-color: rgba(245, 158, 11, 0.6) !important;
  color: #fef3c7 !important;
  font-weight: 600;
  border-radius: 8px;
  box-shadow: 0 0 12px rgba(245, 158, 11, 0.25);
}

:deep(.banner-actions .el-button--warning:hover) {
  background: rgba(245, 158, 11, 0.45) !important;
  color: #ffffff !important;
  box-shadow: 0 0 16px rgba(245, 158, 11, 0.45);
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
