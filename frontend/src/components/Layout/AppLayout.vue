<template>
  <div class="shell">
    <!-- 侧边导航栏 (根据 authStore.menus 动态渲染) -->
    <aside class="rail">
      <div class="brand">
        <p class="eyebrow">AI Learn</p>
        <h1 class="brand-title">制造与仓储协同执行平台</h1>
        <p class="brand-desc">一期壳层按黄金业务闭环组织；动态挂载当前角色授权菜单。</p>
      </div>

      <nav class="nav-list">
        <template v-for="item in structuredMenus" :key="item.id || item.path">
          <!-- 拥有子菜单的折叠分组 -->
          <div v-if="item.children && item.children.length > 0" class="nav-group">
            <button
              type="button"
              class="nav-group-header"
              :class="{ 'is-active': isParentActive(item), 'is-open': openGroupIds.has(item.id) }"
              @click="toggleGroup(item.id)"
            >
              <div class="nav-group-title-box">
                <span class="nav-icon">{{ item.icon || '📁' }}</span>
                <div class="nav-link-content">
                  <strong class="nav-link-title">{{ item.label }}</strong>
                  <span v-if="item.detail" class="nav-link-detail">{{ item.detail }}</span>
                </div>
              </div>
              <span class="group-arrow" :class="{ 'is-expanded': openGroupIds.has(item.id) }">▼</span>
            </button>

            <!-- 二级子菜单列表 -->
            <div v-show="openGroupIds.has(item.id)" class="sub-nav-list">
              <RouterLink
                v-for="sub in item.children"
                :key="sub.path"
                class="sub-nav-link"
                :to="sub.path"
              >
                <span class="sub-dot"></span>
                <div class="sub-link-content">
                  <span class="sub-nav-title">{{ sub.label }}</span>
                  <span v-if="sub.detail" class="sub-nav-detail">{{ sub.detail }}</span>
                </div>
              </RouterLink>
            </div>
          </div>

          <!-- 普通一级直达菜单 -->
          <RouterLink
            v-else
            class="nav-link"
            :to="item.path"
          >
            <div class="nav-link-main">
              <span class="nav-icon">{{ item.icon || '📄' }}</span>
              <div class="nav-link-content">
                <strong class="nav-link-title">{{ item.label }}</strong>
                <span v-if="item.detail" class="nav-link-detail">{{ item.detail }}</span>
              </div>
            </div>
          </RouterLink>
        </template>
      </nav>

      <div class="rail-footer">
        <span class="rail-status-dot"></span>
        <span class="rail-status-text">系统认证状态：在线</span>
      </div>
    </aside>

    <!-- 工作台右侧主体区域 -->
    <div class="main-wrapper">
      <!-- 统一顶部栏 -->
      <header class="topbar">
        <div class="topbar-left">
          <span class="topbar-module-tag">AI-LEARN / WORKSPACE</span>
          <span class="topbar-tenant-tag">🏢 {{ authStore.activeTenant }}</span>
        </div>

        <div class="topbar-right">
          <div class="user-profile-badge">
            <span class="user-avatar-icon">👤</span>
            <div class="user-meta">
              <span class="user-name">{{ authStore.user?.realName || authStore.user?.username || "访客" }}</span>
              <span class="user-role-badge">{{ authStore.currentRoleName }}</span>
            </div>
          </div>

          <button
            type="button"
            class="logout-btn"
            title="退出当前登录会话"
            @click="handleLogout"
          >
            <span>退出登录</span>
            <span class="logout-icon" aria-hidden="true">➔</span>
          </button>
        </div>
      </header>

      <!-- 主视图路由插槽 -->
      <main class="workspace">
        <RouterView />
      </main>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, watch } from "vue";
import { useRouter, useRoute } from "vue-router";
import { useAuthStore } from "../../stores/auth";

interface DisplayMenuItem {
  id: string;
  path: string;
  label: string;
  detail?: string;
  icon?: string;
  children?: DisplayMenuItem[];
}

const router = useRouter();
const route = useRoute();
const authStore = useAuthStore();

// 维护展开的分组 ID 集合
const openGroupIds = ref<Set<string>>(new Set(["7", "System"]));

/**
 * 结构化侧边栏菜单（支持平铺与嵌套两种返回格式的无缝归一化）
 */
const structuredMenus = computed<DisplayMenuItem[]>(() => {
  const storeMenus = authStore.menus;
  if (!storeMenus || storeMenus.length === 0) {
    // 默认基础菜单
    return [
      { id: "1", path: "/", label: "一期总览", detail: "一条黄金业务闭环", icon: "📊" },
      { id: "2", path: "/erp-wms", label: "供需与仓储", detail: "人工关联、预留、收发存", icon: "📦" },
      { id: "3", path: "/mes", label: "制造执行", detail: "领退料、工序执行、质检", icon: "🏭" },
      { id: "4", path: "/iot", label: "设备事实", detail: "遥测、状态、告警分离", icon: "📡" },
      { id: "5", path: "/gis", label: "地图与看板", detail: "业务事实只读展示", icon: "🗺️" },
      { id: "6", path: "/ai", label: "AI 只读助手", detail: "受控查询、来源与审计", icon: "🤖" },
    ];
  }

  // 检查是否已有嵌套 children
  const hasNestedChildren = storeMenus.some((m) => m.children && m.children.length > 0);
  if (hasNestedChildren) {
    return storeMenus.map((m) => formatMenuNode(m));
  }

  // 若为平铺结构，组织为树
  const nodeMap = new Map<string, DisplayMenuItem>();
  for (const m of storeMenus) {
    const idStr = String(m.id || m.menuCode || m.routePath);
    nodeMap.set(idStr, {
      id: idStr,
      path: m.routePath || m.path || "/",
      label: m.menuName || m.label || m.name || "未命名菜单",
      detail: m.detail || "",
      icon: m.icon || "",
      children: [],
    });
  }

  const roots: DisplayMenuItem[] = [];
  for (const m of storeMenus) {
    const idStr = String(m.id || m.menuCode || m.routePath);
    const node = nodeMap.get(idStr)!;
    const parentIdStr = m.parentId ? String(m.parentId) : null;
    if (parentIdStr && nodeMap.has(parentIdStr)) {
      nodeMap.get(parentIdStr)!.children!.push(node);
    } else {
      roots.push(node);
    }
  }

  return roots;
});

function formatMenuNode(m: any): DisplayMenuItem {
  return {
    id: String(m.id || m.menuCode || m.routePath),
    path: m.routePath || m.path || "/",
    label: m.menuName || m.label || m.name || "未命名菜单",
    detail: m.detail || "",
    icon: m.icon || "",
    children: m.children && m.children.length > 0 ? m.children.map(formatMenuNode) : undefined,
  };
}

/**
 * 判断父级菜单是否处于高亮激活状态
 */
function isParentActive(item: DisplayMenuItem): boolean {
  if (!item.children || item.children.length === 0) {
    return route.path === item.path;
  }
  return item.children.some((sub) => route.path.startsWith(sub.path));
}

/**
 * 切换子菜单展开/折叠
 */
function toggleGroup(id: string) {
  if (openGroupIds.value.has(id)) {
    openGroupIds.value.delete(id);
  } else {
    openGroupIds.value.add(id);
  }
}

/**
 * 监听当前路由，自动展开对应父菜单
 */
watch(
  () => route.path,
  (currentPath) => {
    for (const group of structuredMenus.value) {
      if (group.children && group.children.some((sub) => currentPath.startsWith(sub.path))) {
        openGroupIds.value.add(group.id);
      }
    }
  },
  { immediate: true }
);

/**
 * 处理用户登出
 */
async function handleLogout() {
  await authStore.logoutAction();
  router.push({ path: "/login" });
}
</script>

<style scoped>
.main-wrapper {
  display: flex;
  flex-direction: column;
  min-height: 100vh;
}

.brand-title {
  font-size: 16px;
  font-weight: 700;
  margin: 0 0 8px;
  color: #fff;
}

.brand-desc {
  font-size: 11px;
  color: var(--muted, #8ca2b8);
  margin: 0;
  line-height: 1.5;
}

.rail-footer {
  margin-top: 24px;
  padding: 12px;
  border-radius: 10px;
  background: rgba(255, 255, 255, 0.03);
  border: 1px solid rgba(255, 255, 255, 0.06);
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 11px;
  color: #8ca2b8;
}

.rail-status-dot {
  width: 7px;
  height: 7px;
  border-radius: 50%;
  background: #6fe1a6;
  box-shadow: 0 0 8px #6fe1a6;
}

.nav-link-main {
  display: flex;
  align-items: flex-start;
  gap: 10px;
}

.nav-icon {
  font-size: 14px;
  margin-top: 2px;
}

.nav-link-content {
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.nav-link-title {
  font-size: 14px;
  color: var(--text, #ebf3fb);
}

.nav-link-detail {
  font-size: 11px;
  color: var(--muted, #8ca2b8);
  line-height: 1.3;
}

/* 分组父级按钮 */
.nav-group {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.nav-group-header {
  width: 100%;
  padding: 10px 12px;
  border-radius: 10px;
  background: rgba(255, 255, 255, 0.02);
  border: 1px solid transparent;
  display: flex;
  justify-content: space-between;
  align-items: center;
  cursor: pointer;
  text-align: left;
  transition: all 0.2s ease;
  font-family: inherit;
}

.nav-group-header:hover {
  background: rgba(103, 210, 255, 0.05);
  border-color: rgba(103, 210, 255, 0.15);
}

.nav-group-header.is-active {
  border-color: rgba(103, 210, 255, 0.35);
  background: rgba(103, 210, 255, 0.08);
}

.nav-group-title-box {
  display: flex;
  align-items: flex-start;
  gap: 10px;
}

.group-arrow {
  font-size: 9px;
  color: var(--muted, #8ca2b8);
  transition: transform 0.2s ease;
  margin-left: 6px;
}

.group-arrow.is-expanded {
  transform: rotate(0deg);
}

.group-arrow:not(.is-expanded) {
  transform: rotate(-90deg);
}

/* 子菜单列表 */
.sub-nav-list {
  display: flex;
  flex-direction: column;
  gap: 3px;
  padding-left: 20px;
  margin-top: 2px;
  border-left: 1px dashed rgba(124, 162, 194, 0.2);
  margin-left: 14px;
}

.sub-nav-link {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 8px 10px;
  border-radius: 6px;
  color: var(--muted, #8ca2b8);
  font-size: 13px;
  transition: all 0.15s ease;
}

.sub-nav-link:hover {
  color: #fff;
  background: rgba(103, 210, 255, 0.06);
}

.sub-nav-link.router-link-active {
  color: var(--accent, #67d2ff);
  background: rgba(103, 210, 255, 0.12);
  font-weight: 600;
}

.sub-dot {
  width: 4px;
  height: 4px;
  border-radius: 50%;
  background: currentColor;
}

.sub-link-content {
  display: flex;
  flex-direction: column;
  gap: 1px;
}

.sub-nav-title {
  font-size: 12px;
}

.sub-nav-detail {
  font-size: 10px;
  opacity: 0.7;
}

/* 顶部栏 */
.topbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 14px 28px;
  background: rgba(5, 11, 17, 0.72);
  backdrop-filter: blur(14px);
  border-bottom: 1px solid var(--line, rgba(124, 162, 194, 0.18));
}

.topbar-left {
  display: flex;
  align-items: center;
  gap: 12px;
}

.topbar-module-tag {
  font-size: 11px;
  letter-spacing: 0.12em;
  color: var(--accent, #67d2ff);
  font-weight: 600;
}

.topbar-tenant-tag {
  font-size: 11px;
  padding: 3px 8px;
  border-radius: 6px;
  background: rgba(103, 210, 255, 0.1);
  border: 1px solid rgba(103, 210, 255, 0.25);
  color: #c9e8fa;
}

.topbar-right {
  display: flex;
  align-items: center;
  gap: 16px;
}

.user-profile-badge {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 4px 12px 4px 6px;
  border-radius: 20px;
  background: rgba(255, 255, 255, 0.04);
  border: 1px solid var(--line, rgba(124, 162, 194, 0.18));
}

.user-avatar-icon {
  font-size: 16px;
  width: 28px;
  height: 28px;
  border-radius: 50%;
  background: rgba(103, 210, 255, 0.15);
  display: flex;
  align-items: center;
  justify-content: center;
}

.user-meta {
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.user-name {
  font-size: 12px;
  font-weight: 600;
  color: #fff;
}

.user-role-badge {
  font-size: 10px;
  color: var(--accent-warm, #ffb25e);
}

.logout-btn {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 6px 14px;
  border-radius: 8px;
  background: rgba(255, 125, 125, 0.1);
  border: 1px solid rgba(255, 125, 125, 0.28);
  color: #ff9b9b;
  font-size: 12px;
  font-family: inherit;
  cursor: pointer;
  transition: all 0.2s ease;
}

.logout-btn:hover {
  background: rgba(255, 125, 125, 0.22);
  border-color: rgba(255, 125, 125, 0.5);
  color: #fff;
}

.logout-icon {
  font-size: 12px;
  transition: transform 0.2s ease;
}

.logout-btn:hover .logout-icon {
  transform: translateX(2px);
}
</style>
