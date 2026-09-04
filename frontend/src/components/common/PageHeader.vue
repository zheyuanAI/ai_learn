<template>
  <div class="page-header-container">
    <div class="header-main">
      <div class="title-area">
        <div v-if="tag" class="header-tag">{{ tag }}</div>
        <h2 class="page-title">{{ title }}</h2>
        <p v-if="description" class="page-desc">{{ description }}</p>
      </div>
      <div v-if="$slots.actions" class="actions-area">
        <slot name="actions"></slot>
      </div>
    </div>
    <div v-if="$slots.extra" class="header-extra">
      <slot name="extra"></slot>
    </div>
  </div>
</template>

<script setup lang="ts">
/**
 * 统一页面头部组件 (PageHeader)
 * 提供标准标题、业务标签、描述说明与操作区插槽
 */
defineProps<{
  title: string;
  tag?: string;
  description?: string;
}>();
</script>

<style scoped>
.page-header-container {
  display: flex;
  flex-direction: column;
  gap: 12px;
  margin-bottom: 20px;
  padding-bottom: 16px;
  border-bottom: 1px solid rgba(255, 255, 255, 0.08);
}

.header-main {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
  flex-wrap: wrap;
}

.title-area {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.header-tag {
  align-self: flex-start;
  font-size: 11px;
  font-family: var(--font-mono, monospace);
  color: #38bdf8;
  background: rgba(56, 189, 248, 0.12);
  border: 1px solid rgba(56, 189, 248, 0.25);
  padding: 2px 8px;
  border-radius: 4px;
  letter-spacing: 0.5px;
  text-transform: uppercase;
}

.page-title {
  font-size: 22px;
  font-weight: 700;
  color: #f1f5f9;
  margin: 0;
  letter-spacing: -0.3px;
}

.page-desc {
  font-size: 13px;
  color: #94a3b8;
  margin: 0;
  max-width: 800px;
  line-height: 1.5;
}

.actions-area {
  display: flex;
  align-items: center;
  gap: 10px;
  flex-wrap: wrap;
}

.header-extra {
  padding-top: 4px;
}
</style>
