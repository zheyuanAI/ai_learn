<template>
  <div class="forbidden-container">
    <div class="forbidden-card">
      <div class="forbidden-icon">🚫</div>
      <h1 class="forbidden-title">无权访问</h1>
      <p class="forbidden-message">
        抱歉，您当前的角色没有访问此页面的权限。
      </p>

      <div v-if="missingPermission" class="forbidden-detail">
        <span class="detail-label">所需权限：</span>
        <code class="detail-code">{{ missingPermission }}</code>
      </div>

      <div v-if="currentRole" class="forbidden-detail">
        <span class="detail-label">当前角色：</span>
        <span class="detail-value">{{ currentRole }}</span>
      </div>

      <div class="forbidden-actions">
        <button type="button" class="btn btn-primary" @click="goHome">
          返回首页
        </button>
        <button type="button" class="btn btn-secondary" @click="goBack">
          返回上一页
        </button>
      </div>

      <p class="forbidden-hint">
        如需访问此功能，请联系租户管理员为您的角色分配相应权限。
      </p>
    </div>
  </div>
</template>

<script setup lang="ts">
/**
 * 无权限页面（403 Forbidden）。
 *
 * 用途：当用户访问其角色权限不允许的页面时，展示友好的无权限提示，
 *       替代空白页或静默重定向到首页。
 *
 * 入参：通过路由 query 获取 missingPermission 和 from 参数
 * 核心流程：展示当前角色、缺少的权限码，提供返回首页和上一页操作
 */
import { computed } from "vue";
import { useRouter, useRoute } from "vue-router";
import { useAuthStore } from "../stores/auth";

const router = useRouter();
const route = useRoute();
const authStore = useAuthStore();

/** 缺少的权限码（由路由守卫通过 query 传入） */
const missingPermission = computed(() => route.query.permission as string || "");

/** 当前用户角色名称 */
const currentRole = computed(() => authStore.currentRoleName);

/** 返回首页 */
function goHome() {
  router.push("/");
}

/** 返回上一页 */
function goBack() {
  const from = route.query.from as string;
  if (from) {
    router.push(from);
  } else {
    router.back();
  }
}
</script>

<style scoped>
.forbidden-container {
  display: flex;
  align-items: center;
  justify-content: center;
  min-height: 70vh;
  padding: 24px;
}

.forbidden-card {
  max-width: 480px;
  width: 100%;
  text-align: center;
  padding: 48px 32px;
  border-radius: 16px;
  background: rgba(255, 255, 255, 0.03);
  border: 1px solid rgba(255, 125, 125, 0.2);
}

.forbidden-icon {
  font-size: 64px;
  margin-bottom: 16px;
}

.forbidden-title {
  font-size: 24px;
  font-weight: 700;
  color: #ff9b9b;
  margin: 0 0 12px;
}

.forbidden-message {
  font-size: 14px;
  color: #c9d8e8;
  margin: 0 0 24px;
  line-height: 1.6;
}

.forbidden-detail {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  margin-bottom: 12px;
  font-size: 13px;
}

.detail-label {
  color: #8ca2b8;
}

.detail-code {
  padding: 2px 8px;
  border-radius: 4px;
  background: rgba(103, 210, 255, 0.1);
  border: 1px solid rgba(103, 210, 255, 0.25);
  color: #67d2ff;
  font-size: 12px;
}

.detail-value {
  color: #ffb25e;
  font-weight: 600;
}

.forbidden-actions {
  display: flex;
  gap: 12px;
  justify-content: center;
  margin: 28px 0 20px;
}

.btn {
  padding: 8px 20px;
  border-radius: 8px;
  font-size: 13px;
  font-family: inherit;
  cursor: pointer;
  border: 1px solid transparent;
  transition: all 0.2s ease;
}

.btn-primary {
  background: rgba(103, 210, 255, 0.15);
  border-color: rgba(103, 210, 255, 0.35);
  color: #67d2ff;
}

.btn-primary:hover {
  background: rgba(103, 210, 255, 0.25);
}

.btn-secondary {
  background: rgba(255, 255, 255, 0.05);
  border-color: rgba(255, 255, 255, 0.15);
  color: #c9d8e8;
}

.btn-secondary:hover {
  background: rgba(255, 255, 255, 0.1);
}

.forbidden-hint {
  font-size: 12px;
  color: #6b7f92;
  margin: 0;
  line-height: 1.5;
}
</style>
