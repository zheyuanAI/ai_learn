<template>
  <div class="empty-state-box">
    <div class="empty-icon">{{ icon || "📭" }}</div>
    <h4 class="empty-title">{{ title || "暂无数据" }}</h4>
    <p v-if="description" class="empty-desc">{{ description }}</p>
    <div v-if="$slots.action" class="empty-action">
      <slot name="action"></slot>
    </div>
  </div>
</template>

<script setup lang="ts">
/**
 * 统一空状态组件 (EmptyState)
 */
defineProps<{
  icon?: string;
  title?: string;
  description?: string;
}>();
</script>

<style scoped>
.empty-state-box {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: 48px 24px;
  text-align: center;
  background: rgba(15, 23, 42, 0.4);
  border: 1px dashed rgba(255, 255, 255, 0.1);
  border-radius: 8px;
}

.empty-icon {
  font-size: 36px;
  margin-bottom: 12px;
  opacity: 0.8;
}

.empty-title {
  font-size: 16px;
  font-weight: 600;
  color: #cbd5e1;
  margin: 0 0 6px 0;
}

.empty-desc {
  font-size: 13px;
  color: #64748b;
  max-width: 420px;
  margin: 0 0 16px 0;
  line-height: 1.5;
}

.empty-action {
  margin-top: 8px;
}
</style>
