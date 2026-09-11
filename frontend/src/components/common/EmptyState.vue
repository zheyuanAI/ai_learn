<template>
  <el-empty
    :description="description || title || '暂无数据'"
    :image-size="70"
    class="empty-state-el"
  >
    <template v-if="icon" #image>
      <span class="custom-empty-icon">{{ icon }}</span>
    </template>
    <template v-if="$slots.action" #default>
      <div class="empty-action">
        <slot name="action"></slot>
      </div>
    </template>
  </el-empty>
</template>

<script setup lang="ts">
/**
 * 统一空状态组件 (EmptyState)
 * 封装 Element Plus el-empty，适配工业深色风
 */
defineProps<{
  icon?: string;
  title?: string;
  description?: string;
}>();
</script>

<style scoped>
.empty-state-el {
  padding: 36px 20px;
}
.custom-empty-icon {
  font-size: 40px;
  line-height: 1;
}
.empty-action {
  margin-top: 8px;
}
</style>
