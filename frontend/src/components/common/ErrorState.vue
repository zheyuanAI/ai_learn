<template>
  <el-result
    icon="error"
    :title="title || '请求处理异常'"
    :sub-title="message || '服务响应出现错误，请检查网络或联系管理员。'"
    class="error-state-el"
    :class="{ 'is-card': card }"
  >
    <template #extra>
      <div v-if="code" class="error-code-badge">CODE: {{ code }}</div>
      <div v-if="detail" class="error-detail-box">{{ detail }}</div>
      <div class="error-action-btns">
        <el-button v-if="showRetry" type="danger" plain @click="$emit('retry')">
          重新加载
        </el-button>
        <slot name="actions"></slot>
      </div>
    </template>
  </el-result>
</template>

<script setup lang="ts">
/**
 * 统一错误状态展示组件 (ErrorState)
 * 封装 Element Plus el-result，兼容原入参与插槽
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
.error-state-el {
  padding: 24px 20px;
  background: rgba(239, 68, 68, 0.06);
  border: 1px solid rgba(239, 68, 68, 0.2);
  border-radius: 8px;
  margin: 16px 0;
}
.is-card {
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.25);
}
.error-code-badge {
  font-size: 11px;
  font-family: var(--font-mono, monospace);
  background: rgba(239, 68, 68, 0.25);
  color: #fecaca;
  padding: 2px 8px;
  border-radius: 4px;
  display: inline-block;
  margin-bottom: 8px;
}
.error-detail-box {
  font-size: 12px;
  font-family: var(--font-mono, monospace);
  color: #94a3b8;
  background: rgba(0, 0, 0, 0.35);
  padding: 8px 12px;
  border-radius: 4px;
  margin-bottom: 12px;
  word-break: break-all;
  max-width: 600px;
}
.error-action-btns {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 12px;
}
</style>
