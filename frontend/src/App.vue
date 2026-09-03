<template>
  <RouterView />
</template>

<script setup lang="ts">
import { onMounted, onUnmounted } from "vue";
import { useRouter } from "vue-router";
import { useAuthStore } from "./stores/auth";

const router = useRouter();
const authStore = useAuthStore();

/**
 * 监听全局 401 未授权 / 单会话顶替事件
 */
function handleUnauthorizedEvent(event: Event) {
  const customEvent = event as CustomEvent<{ message?: string }>;
  console.warn("[App Root] 收到 401 未授权事件：", customEvent.detail?.message);
  authStore.logoutAction();
  router.push({
    path: "/login",
    query: {
      redirect: router.currentRoute.value.fullPath,
      reason: "401",
    },
  });
}

onMounted(() => {
  window.addEventListener("ai-learn:unauthorized", handleUnauthorizedEvent);
});

onUnmounted(() => {
  window.removeEventListener("ai-learn:unauthorized", handleUnauthorizedEvent);
});
</script>
