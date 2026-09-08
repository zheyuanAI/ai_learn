<template>
  <div v-if="error" class="command-feedback" role="alert">
    <span>操作失败：{{ error.message || "未知错误" }}</span>
    <span v-if="error.requestId" class="request-id">请求号：{{ error.requestId }}</span>
    <button v-if="canRetry" type="button" :disabled="executing" @click="$emit('retry')">
      {{ executing ? "重试中..." : "使用原请求键重试" }}
    </button>
  </div>
</template>

<script setup lang="ts">
/** 用途：在真实写页面统一展示结构化 ApiError 与同键重试入口。 */
defineProps<{ error: any; canRetry: boolean; executing: boolean }>();
defineEmits<{ (e: "retry"): void }>();
</script>

<style scoped>
.command-feedback { display:flex; gap:8px; align-items:center; flex-wrap:wrap; padding:8px 12px; margin:0 0 12px; border:1px solid rgba(248,113,113,.45); border-radius:6px; color:#fecaca; background:rgba(127,29,29,.2); font-size:12px; }
.request-id { color:#cbd5e1; font-family:monospace; }
button { border:1px solid #38bdf8; border-radius:4px; background:transparent; color:#7dd3fc; cursor:pointer; padding:3px 8px; }
</style>
