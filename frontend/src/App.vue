<template>
  <!-- Element Plus 全局配置提供者：中文国际化 + 统一组件尺寸 -->
  <el-config-provider :locale="zhCn" size="default">
    <RouterView />
  </el-config-provider>
</template>

<script setup lang="ts">
import { onMounted, onUnmounted } from "vue";
import { useRouter } from "vue-router";
import { useAuthStore } from "./stores/auth";
import zhCn from "element-plus/es/locale/lang/zh-cn";

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
  // 默认以暗色模式启动
  document.documentElement.classList.add("dark");
  window.addEventListener("ai-learn:unauthorized", handleUnauthorizedEvent);
});

onUnmounted(() => {
  window.removeEventListener("ai-learn:unauthorized", handleUnauthorizedEvent);
});
</script>
