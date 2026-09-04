<template>
  <div class="error-state-box" :class="{ 'is-card': card }">
    <div class="error-badge-icon">⚠️</div>
    <div class="error-content">
      <div class="error-header">
        <h4 class="error-title">{{ title || "请求处理异常" }}</h4>
        <span v-if="code" class="error-code">CODE: {{ code }}</span>
      </div>
      <p class="error-message">{{ message || "服务响应出现错误，请检查网络或联系管理员。" }}</p>
      <div v-if="detail" class="error-detail">{{ detail }}</div>
      <div v-if="showRetry || $slots.actions" class="error-actions">
        <button v-if="showRetry" type="button" class="btn-retry" @click="$emit('retry')">
          <span>重新加载</span>
        </button>
        <slot name="actions"></slot>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
/**
 * 统一错误状态展示组件 (ErrorState)
 * 支持错误码显示、详细原因折叠展开与重试操作
 */
withDefaults(
  defineProps<{
    title?: string;
    message?: string;
    code?: string | number;
    detail?: string;
    card?: boolean;
    showRetry?: boolean;
  }>(),
  {
    title: "请求处理异常",
    message: "",
    code: "",
    detail: "",
    card: true,
    showRetry: true,
  }
);

defineEmits<{
  (e: "retry"): void;
}>();
</script>

<style scoped>
.error-state-box {
  display: flex;
  align-items: flex-start;
  gap: 16px;
  padding: 20px;
  background: rgba(239, 68, 68, 0.08);
  border: 1px solid rgba(239, 68, 68, 0.25);
  border-radius: 8px;
  margin: 16px 0;
}

.is-card {
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.2);
}

.error-badge-icon {
  font-size: 24px;
  line-height: 1;
  color: #f87171;
}

.error-content {
  flex: 1;
}

.error-header {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-bottom: 6px;
}

.error-title {
  font-size: 15px;
  font-weight: 600;
  color: #fca5a5;
  margin: 0;
}

.error-code {
  font-size: 11px;
  font-family: var(--font-mono, monospace);
  background: rgba(239, 68, 68, 0.2);
  color: #fecaca;
  padding: 1px 6px;
  border-radius: 4px;
}

.error-message {
  font-size: 13px;
  color: #e2e8f0;
  margin: 0 0 10px 0;
  line-height: 1.5;
}

.error-detail {
  font-size: 12px;
  font-family: var(--font-mono, monospace);
  color: #94a3b8;
  background: rgba(0, 0, 0, 0.3);
  padding: 8px 12px;
  border-radius: 4px;
  margin-bottom: 12px;
  word-break: break-all;
}

.error-actions {
  display: flex;
  align-items: center;
  gap: 10px;
}

.btn-retry {
  padding: 6px 14px;
  background: rgba(239, 68, 68, 0.2);
  border: 1px solid rgba(239, 68, 68, 0.4);
  color: #fca5a5;
  border-radius: 6px;
  font-size: 13px;
  font-weight: 500;
  cursor: pointer;
  transition: all 0.2s;
}

.btn-retry:hover {
  background: rgba(239, 68, 68, 0.3);
  color: #ffffff;
}
</style>
