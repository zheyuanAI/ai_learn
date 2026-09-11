<template>
  <section class="not-found-page" data-testid="not-found-page">
    <div class="not-found-card">
      <span class="not-found-code">404</span>
      <p class="not-found-eyebrow">ROUTE NOT FOUND</p>
      <h1>页面不存在</h1>
      <p class="not-found-message">
        当前地址没有对应的业务页面。你可以返回上一页，或回到系统首页继续操作。
      </p>
      <p class="not-found-path">{{ route.fullPath }}</p>
      <div class="not-found-actions">
        <el-button @click="handleBack">返回上一页</el-button>
        <el-button type="primary" @click="goHome">返回系统首页</el-button>
      </div>
    </div>
  </section>
</template>

<script setup lang="ts">
import { useRoute, useRouter } from "vue-router";

const route = useRoute();
const router = useRouter();

/**
 * 用途：返回用户进入 404 页面前的可用历史位置。
 * 入参：无，读取浏览器当前历史长度。
 * 出参：无；有历史记录时返回上一页，否则进入系统首页。
 * 流程：优先调用浏览器后退，避免未知地址静默落到首页；没有可后退记录时使用首页兜底。
 */
function handleBack() {
  if (window.history.length > 1) {
    router.back();
    return;
  }
  goHome();
}

/**
 * 用途：把用户带回系统首页。
 * 入参：无。
 * 出参：无；通过路由导航进入首页。
 * 流程：使用 replace 清理无效地址，避免用户再次后退回同一个 404 页面。
 */
function goHome() {
  router.replace({ name: "Overview" });
}
</script>

<style scoped>
.not-found-page {
  min-height: 100vh;
  display: grid;
  place-items: center;
  padding: 32px;
  background:
    radial-gradient(circle at 20% 20%, rgba(103, 210, 255, 0.12), transparent 34%),
    #08111a;
  color: #e9f2f9;
}

.not-found-card {
  width: min(560px, 100%);
  padding: 40px;
  border: 1px solid rgba(124, 162, 194, 0.28);
  border-radius: 18px;
  background: rgba(15, 28, 40, 0.9);
  box-shadow: 0 24px 70px rgba(0, 0, 0, 0.35);
}

.not-found-code {
  display: block;
  color: #67d2ff;
  font: 700 56px/1 var(--font-mono, monospace);
  letter-spacing: 0.08em;
}

.not-found-eyebrow {
  margin: 14px 0 8px;
  color: #8ca2b8;
  font-size: 11px;
  letter-spacing: 0.16em;
}

h1 {
  margin: 0;
  font-size: 28px;
}

.not-found-message {
  margin: 14px 0 0;
  color: #b8c8d6;
  line-height: 1.7;
}

.not-found-path {
  margin: 18px 0 0;
  padding: 10px 12px;
  overflow-wrap: anywhere;
  border-radius: 8px;
  background: rgba(0, 0, 0, 0.24);
  color: #91a7b8;
  font: 12px/1.5 var(--font-mono, monospace);
}

.not-found-actions {
  display: flex;
  gap: 10px;
  margin-top: 24px;
}

.btn {
  padding: 9px 16px;
  border-radius: 8px;
  font: inherit;
  cursor: pointer;
}

.btn-secondary {
  border: 1px solid rgba(124, 162, 194, 0.3);
  background: rgba(124, 162, 194, 0.1);
  color: #d2e1ed;
}

.btn-primary {
  border: 1px solid rgba(103, 210, 255, 0.5);
  background: rgba(103, 210, 255, 0.18);
  color: #dff7ff;
}
</style>
