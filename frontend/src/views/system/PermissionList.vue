<template>
  <div class="system-view-container">
    <!-- 头部说明 -->
    <header class="page-header">
      <div class="header-meta">
        <span class="meta-tag">AUTH / PERMISSION CATALOG</span>
        <span class="meta-badge badge-readonly">🔒 只读能力目录事实源</span>
        <span class="meta-count-badge">已注册 {{ permissionList.length }} 个系统权限点</span>
      </div>
      <div class="header-main-row">
        <div>
          <h1 class="page-title">系统功能权限目录</h1>
          <p class="page-desc">
            功能权限点为系统底层 API 与业务契约定义的不可变能力清单。此处仅供浏览、搜索与角色分配参照，不支持在租户端随意伪造与新增。
          </p>
        </div>
      </div>
    </header>

    <!-- 筛选与搜索工具条 -->
    <section class="filter-panel">
      <div class="filter-row">
        <div class="filter-item">
          <label>关键字搜索</label>
          <input
            v-model="searchKeyword"
            type="text"
            class="filter-input"
            placeholder="搜索权限编码 (如 sales.order.create) 或名称..."
          />
        </div>

        <div class="filter-item">
          <label>所属业务模块</label>
          <select v-model="filterModule" class="filter-select">
            <option value="">全部模块 (All Modules)</option>
            <option v-for="mod in availableModules" :key="mod" :value="mod">
              {{ formatModuleName(mod) }}
            </option>
          </select>
        </div>

        <div class="filter-actions-inline">
          <button type="button" class="btn-refresh" :disabled="isLoading" @click="fetchPermissionList">
            <span>🔄 重新加载</span>
          </button>
        </div>
      </div>
    </section>

    <!-- 加载中 -->
    <div v-if="isLoading" class="loading-state">
      <div class="spinner"></div>
      <span>正在从 Auth 权限服务加载系统能力清单...</span>
    </div>

    <!-- 错误提示 -->
    <div v-if="errorMessage" class="error-banner">
      <span class="error-icon">⚠️</span>
      <div class="error-text">
        <strong>接口提示：</strong>
        <span>{{ errorMessage }}</span>
      </div>
      <button type="button" class="btn-retry" @click="fetchPermissionList">重试</button>
    </div>

    <!-- 模块分组展示 -->
    <div v-if="!isLoading" class="modules-container">
      <div v-if="Object.keys(filteredGroupedPerms).length === 0" class="empty-state-box">
        <span class="empty-icon">📂</span>
        <p>未找到匹配条件的系统权限点</p>
      </div>

      <section
        v-for="(perms, moduleKey) in filteredGroupedPerms"
        :key="moduleKey"
        class="module-card"
      >
        <div class="module-card-head" @click="toggleModuleCollapse(String(moduleKey))">
          <div class="module-info-left">
            <span class="module-icon">📦</span>
            <div class="module-text">
              <h2 class="module-name">{{ formatModuleName(String(moduleKey)) }}</h2>
              <span class="module-code font-mono">module: {{ moduleKey }}</span>
            </div>
          </div>

          <div class="module-info-right">
            <span class="perm-count-badge">{{ perms.length }} 个权限点</span>
            <span class="collapse-arrow" :class="{ 'is-collapsed': collapsedModules[String(moduleKey)] }">▼</span>
          </div>
        </div>

        <!-- 模块下的权限点列表 -->
        <div v-show="!collapsedModules[String(moduleKey)]" class="module-perm-grid">
          <article
            v-for="perm in perms"
            :key="perm.id"
            class="perm-item-card"
          >
            <div class="perm-card-top">
              <div class="perm-name-row">
                <span class="key-icon">🔑</span>
                <strong class="perm-title">{{ perm.permissionName }}</strong>
              </div>
              <button
                type="button"
                class="btn-copy"
                title="复制权限点编码"
                @click="copyPermCode(perm.permissionCode)"
              >
                📋 复制编码
              </button>
            </div>

            <div class="perm-code-box">
              <code class="font-mono perm-code-val">{{ perm.permissionCode }}</code>
            </div>

            <p class="perm-desc-text">{{ perm.description || "无详细业务说明" }}</p>

            <footer class="perm-card-foot">
              <span class="meta-label">模块归属</span>
              <span class="meta-tag-pill font-mono">{{ perm.module }}</span>
            </footer>
          </article>
        </div>
      </section>
    </div>

    <!-- 统一 Toast 提示 -->
    <div
      v-if="toast.visible"
      class="sys-toast"
      :class="[`is-${toast.type}`, { 'is-visible': toast.visible }]"
      role="status"
    >
      <span class="toast-icon">{{ toast.type === 'success' ? '✓' : toast.type === 'danger' ? '✕' : 'ℹ' }}</span>
      <span>{{ toast.message }}</span>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, computed, onMounted } from "vue";
import { getPermissions, type PermissionItem } from "../../api/admin";

const isLoading = ref(true);
const errorMessage = ref("");
const permissionList = ref<PermissionItem[]>([]);

// 搜索与过滤
const searchKeyword = ref("");
const filterModule = ref("");

// 模块折叠状态
const collapsedModules = reactive<Record<string, boolean>>({});

// Toast 状态
const toast = reactive({
  visible: false,
  message: "",
  type: "success" as "success" | "danger" | "warning",
});
let toastTimer: any = null;

function showToast(message: string, type: "success" | "danger" | "warning" = "success") {
  if (toastTimer) clearTimeout(toastTimer);
  toast.message = message;
  toast.type = type;
  toast.visible = true;
  toastTimer = setTimeout(() => {
    toast.visible = false;
  }, 3000);
}

/**
 * 模块名称映射字典
 */
const MODULE_NAMES: Record<string, string> = {
  auth: "认证与租户安全 (Auth)",
  purchasing: "采购供需与供应商 (Purchasing)",
  sales: "销售订单与履约 (Sales)",
  inventory: "仓储收发存与库位 (Inventory)",
  mes: "生产执行与质检 (MES)",
  iot: "IoT设备与遥测告警 (IoT)",
  gis: "空间地图与综合看板 (GIS)",
  ai: "AI受控问答与审计 (AI)",
  common: "公共基础能力 (Common)",
};

function formatModuleName(mod: string): string {
  return MODULE_NAMES[mod] || `业务模块 (${mod})`;
}

/**
 * 获取所有可用模块列表
 */
const availableModules = computed(() => {
  const set = new Set<string>();
  for (const p of permissionList.value) {
    if (p.module) set.add(p.module);
  }
  return Array.from(set).sort();
});

/**
 * 过滤与模块分组
 */
const filteredGroupedPerms = computed(() => {
  const map: Record<string, PermissionItem[]> = {};
  const kw = searchKeyword.value.trim().toLowerCase();

  for (const p of permissionList.value) {
    if (filterModule.value && p.module !== filterModule.value) {
      continue;
    }
    if (kw) {
      const matchCode = p.permissionCode.toLowerCase().includes(kw);
      const matchName = p.permissionName.toLowerCase().includes(kw);
      const matchDesc = (p.description || "").toLowerCase().includes(kw);
      if (!matchCode && !matchName && !matchDesc) {
        continue;
      }
    }

    const mod = p.module || "common";
    if (!map[mod]) map[mod] = [];
    map[mod].push(p);
  }

  return map;
});

/**
 * 折叠/展开某个模块
 */
function toggleModuleCollapse(mod: string) {
  collapsedModules[mod] = !collapsedModules[mod];
}

/**
 * 复制权限编码
 */
async function copyPermCode(code: string) {
  try {
    if (navigator.clipboard && navigator.clipboard.writeText) {
      await navigator.clipboard.writeText(code);
      showToast(`已复制权限编码：${code}`, "success");
    } else {
      showToast(`权限编码：${code}`, "info" as any);
    }
  } catch {
    showToast(`权限编码：${code}`, "info" as any);
  }
}

/**
 * 从后端加载权限点列表
 */
async function fetchPermissionList() {
  isLoading.value = true;
  errorMessage.value = "";
  try {
    const res = await getPermissions();
    if (res.data && Array.isArray(res.data)) {
      permissionList.value = res.data;
    }
  } catch (err: any) {
    errorMessage.value = err.message || "无法拉取系统权限目录";
    showToast(`权限目录拉取失败：${err.message}`, "danger");
  } finally {
    isLoading.value = false;
  }
}

onMounted(() => {
  fetchPermissionList();
});
</script>

<style scoped>
.system-view-container {
  display: flex;
  flex-direction: column;
  gap: 20px;
}

.page-header {
  padding-bottom: 16px;
  border-bottom: 1px solid var(--line, rgba(124, 162, 194, 0.18));
}

.header-meta {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 8px;
}

.meta-tag {
  font-size: 11px;
  letter-spacing: 0.14em;
  color: var(--accent, #67d2ff);
  font-weight: 700;
  text-transform: uppercase;
}

.badge-readonly {
  font-size: 11px;
  padding: 2px 8px;
  border-radius: 6px;
  background: rgba(255, 178, 94, 0.12);
  border: 1px solid rgba(255, 178, 94, 0.35);
  color: #ffb25e;
  font-weight: 600;
}

.meta-count-badge {
  font-size: 11px;
  padding: 2px 8px;
  border-radius: 10px;
  background: rgba(103, 210, 255, 0.1);
  border: 1px solid rgba(103, 210, 255, 0.25);
  color: #c9e8fa;
}

.page-title {
  font-size: 22px;
  font-weight: 700;
  color: #fff;
  margin: 0 0 6px;
}

.page-desc {
  font-size: 13px;
  color: var(--muted, #8ca2b8);
  margin: 0;
  line-height: 1.5;
}

.filter-panel {
  padding: 14px 18px;
  border-radius: 14px;
  background: var(--panel, rgba(10, 18, 28, 0.84));
  border: 1px solid var(--line, rgba(124, 162, 194, 0.18));
}

.filter-row {
  display: flex;
  align-items: flex-end;
  gap: 14px;
  flex-wrap: wrap;
}

.filter-item {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.filter-item label {
  font-size: 11px;
  color: var(--muted, #8ca2b8);
}

.filter-input,
.filter-select {
  padding: 7px 10px;
  border-radius: 6px;
  background: rgba(255, 255, 255, 0.04);
  border: 1px solid rgba(124, 162, 194, 0.22);
  color: #fff;
  font-size: 12px;
  outline: none;
}

.filter-input {
  min-width: 280px;
}

.filter-select option {
  background: #0d1723;
}

.filter-actions-inline {
  margin-left: auto;
}

.btn-refresh {
  padding: 7px 16px;
  border-radius: 6px;
  background: rgba(103, 210, 255, 0.12);
  border: 1px solid rgba(103, 210, 255, 0.3);
  color: var(--accent, #67d2ff);
  font-size: 12px;
  cursor: pointer;
}

.btn-refresh:hover:not(:disabled) {
  background: rgba(103, 210, 255, 0.25);
}

.modules-container {
  display: flex;
  flex-direction: column;
  gap: 20px;
}

.module-card {
  border-radius: 14px;
  background: var(--panel, rgba(10, 18, 28, 0.84));
  border: 1px solid var(--line, rgba(124, 162, 194, 0.18));
  overflow: hidden;
  box-shadow: 0 16px 40px rgba(0, 0, 0, 0.2);
}

.module-card-head {
  padding: 14px 20px;
  background: rgba(5, 11, 17, 0.7);
  border-bottom: 1px solid rgba(124, 162, 194, 0.12);
  display: flex;
  justify-content: space-between;
  align-items: center;
  cursor: pointer;
  user-select: none;
  transition: background 0.2s ease;
}

.module-card-head:hover {
  background: rgba(103, 210, 255, 0.04);
}

.module-info-left {
  display: flex;
  align-items: center;
  gap: 12px;
}

.module-icon {
  font-size: 18px;
}

.module-name {
  font-size: 15px;
  font-weight: 700;
  color: #fff;
  margin: 0;
}

.module-code {
  font-size: 11px;
  color: var(--accent, #67d2ff);
}

.module-info-right {
  display: flex;
  align-items: center;
  gap: 12px;
}

.perm-count-badge {
  font-size: 11px;
  padding: 3px 9px;
  border-radius: 10px;
  background: rgba(111, 225, 166, 0.1);
  border: 1px solid rgba(111, 225, 166, 0.25);
  color: #6fe1a6;
  font-weight: 600;
}

.collapse-arrow {
  font-size: 10px;
  color: var(--muted, #8ca2b8);
  transition: transform 0.2s ease;
}

.collapse-arrow.is-collapsed {
  transform: rotate(-90deg);
}

.module-perm-grid {
  padding: 18px;
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(280px, 1fr));
  gap: 14px;
}

.perm-item-card {
  padding: 14px;
  border-radius: 10px;
  background: rgba(255, 255, 255, 0.02);
  border: 1px solid rgba(124, 162, 194, 0.12);
  display: flex;
  flex-direction: column;
  gap: 8px;
  transition: all 0.2s ease;
}

.perm-item-card:hover {
  background: rgba(103, 210, 255, 0.03);
  border-color: rgba(103, 210, 255, 0.28);
}

.perm-card-top {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 8px;
}

.perm-name-row {
  display: flex;
  align-items: center;
  gap: 6px;
}

.key-icon {
  font-size: 13px;
}

.perm-title {
  font-size: 13px;
  color: #fff;
}

.btn-copy {
  padding: 2px 6px;
  border-radius: 4px;
  background: rgba(255, 255, 255, 0.05);
  border: 1px solid rgba(124, 162, 194, 0.18);
  color: var(--muted, #8ca2b8);
  font-size: 10px;
  cursor: pointer;
  transition: all 0.15s ease;
}

.btn-copy:hover {
  background: rgba(103, 210, 255, 0.15);
  border-color: rgba(103, 210, 255, 0.4);
  color: #fff;
}

.perm-code-box {
  padding: 5px 8px;
  border-radius: 6px;
  background: rgba(0, 0, 0, 0.3);
  border: 1px solid rgba(124, 162, 194, 0.08);
}

.perm-code-val {
  font-size: 11px;
  color: var(--accent, #67d2ff);
  word-break: break-all;
}

.perm-desc-text {
  font-size: 11px;
  color: var(--muted, #8ca2b8);
  margin: 0;
  line-height: 1.4;
  flex: 1;
}

.perm-card-foot {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding-top: 6px;
  border-top: 1px solid rgba(124, 162, 194, 0.08);
  font-size: 10px;
}

.meta-label {
  color: var(--muted, #8ca2b8);
}

.meta-tag-pill {
  color: #ffb25e;
  background: rgba(255, 178, 94, 0.1);
  padding: 1px 6px;
  border-radius: 4px;
}

.font-mono {
  font-family: monospace;
}

.loading-state {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 32px;
  border-radius: 14px;
  background: var(--panel, rgba(10, 18, 28, 0.84));
  border: 1px solid var(--line, rgba(124, 162, 194, 0.18));
  color: var(--muted, #8ca2b8);
  font-size: 13px;
}

.spinner {
  width: 16px;
  height: 16px;
  border: 2px solid rgba(103, 210, 255, 0.2);
  border-top-color: var(--accent, #67d2ff);
  border-radius: 50%;
  animation: spin 0.8s linear infinite;
}

.empty-state-box {
  padding: 48px;
  text-align: center;
  color: var(--muted, #8ca2b8);
}

.empty-icon {
  font-size: 28px;
}

.error-banner {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 12px 16px;
  border-radius: 10px;
  background: rgba(255, 125, 125, 0.1);
  border: 1px solid rgba(255, 125, 125, 0.3);
  color: #ff9b9b;
  font-size: 12px;
}

.error-icon {
  font-size: 16px;
}

.error-text {
  flex: 1;
}

.btn-retry {
  padding: 4px 10px;
  border-radius: 4px;
  background: rgba(255, 125, 125, 0.2);
  border: 1px solid rgba(255, 125, 125, 0.4);
  color: #fff;
  font-size: 11px;
  cursor: pointer;
}

.sys-toast {
  position: fixed;
  bottom: 24px;
  right: 24px;
  padding: 12px 20px;
  border-radius: 8px;
  font-size: 13px;
  font-weight: 600;
  z-index: 9999;
  box-shadow: 0 10px 30px rgba(0, 0, 0, 0.5);
  transition: all 0.3s ease;
  opacity: 0;
  transform: translateY(10px);
  pointer-events: none;
  display: flex;
  align-items: center;
  gap: 8px;
}

.sys-toast.is-visible {
  opacity: 1;
  transform: translateY(0);
  pointer-events: auto;
}

.sys-toast.is-success {
  background: #0d281e;
  border: 1px solid #6fe1a6;
  color: #6fe1a6;
}

.sys-toast.is-danger {
  background: #2b1114;
  border: 1px solid #ff7d7d;
  color: #ff9b9b;
}

@keyframes spin {
  to {
    transform: rotate(360deg);
  }
}
</style>
