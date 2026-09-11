<template>
  <div class="system-view-container">
    <!-- 头部说明 -->
    <header class="page-header">
      <div class="header-meta">
        <el-tag type="info" size="small">AUTH / PERMISSION CATALOG</el-tag>
        <el-tag type="warning" size="small" effect="plain">🔒 只读能力目录事实源</el-tag>
        <el-tag type="primary" size="small" effect="dark">已注册 {{ permissionList.length }} 个系统权限点</el-tag>
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
    <el-card class="filter-card" shadow="never">
      <el-form inline class="filter-form">
        <el-form-item label="关键字搜索">
          <el-input
            v-model="searchKeyword"
            clearable
            placeholder="搜索权限编码 (如 sales.order.create) 或名称..."
            style="width: 320px;"
          />
        </el-form-item>

        <el-form-item label="所属业务模块">
          <el-select
            v-model="filterModule"
            clearable
            placeholder="全部模块 (All Modules)"
            style="width: 240px;"
          >
            <el-option label="全部模块" value="" />
            <el-option
              v-for="mod in availableModules"
              :key="mod"
              :label="formatModuleName(mod)"
              :value="mod"
            />
          </el-select>
        </el-form-item>

        <el-form-item>
          <el-button type="primary" :loading="isLoading" @click="fetchPermissionList">
            🔄 重新加载
          </el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <!-- 错误提示 -->
    <el-alert
      v-if="errorMessage"
      :title="'接口提示：' + errorMessage"
      type="error"
      show-icon
      class="mb-4"
    >
      <template #default>
        <el-button type="danger" link size="small" @click="fetchPermissionList">重试</el-button>
      </template>
    </el-alert>

    <!-- 加载骨架屏 -->
    <div v-if="isLoading" class="loading-box" v-loading="true" element-loading-text="正在从 Auth 权限服务加载系统能力清单...">
      <div style="height: 200px;"></div>
    </div>

    <!-- 模块分组展示 -->
    <div v-else class="modules-container">
      <el-empty
        v-if="Object.keys(filteredGroupedPerms).length === 0"
        description="未找到匹配条件的系统权限点"
      />

      <el-card
        v-for="(perms, moduleKey) in filteredGroupedPerms"
        :key="moduleKey"
        class="module-card mb-4"
        shadow="hover"
      >
        <template #header>
          <div class="module-card-head" @click="toggleModuleCollapse(String(moduleKey))">
            <div class="module-info-left">
              <span class="module-icon">📦</span>
              <div class="module-text">
                <span class="module-name">{{ formatModuleName(String(moduleKey)) }}</span>
                <span class="module-code font-mono">module: {{ moduleKey }}</span>
              </div>
            </div>

            <div class="module-info-right">
              <el-tag size="small" effect="plain">{{ perms.length }} 个权限点</el-tag>
              <span class="collapse-arrow" :class="{ 'is-collapsed': collapsedModules[String(moduleKey)] }">▼</span>
            </div>
          </div>
        </template>

        <!-- 模块下的权限点列表 -->
        <div v-show="!collapsedModules[String(moduleKey)]" class="module-perm-grid">
          <div
            v-for="perm in perms"
            :key="perm.id"
            class="perm-item-card"
          >
            <div class="perm-card-top">
              <div class="perm-name-row">
                <span class="key-icon">🔑</span>
                <strong class="perm-title">{{ perm.permissionName }}</strong>
              </div>
              <el-button
                size="small"
                type="primary"
                link
                @click="copyPermCode(perm.permissionCode)"
              >
                📋 复制
              </el-button>
            </div>

            <div class="perm-code-box">
              <code class="font-mono perm-code-val">{{ perm.permissionCode }}</code>
            </div>

            <p class="perm-desc-text">{{ perm.description || "无详细业务说明" }}</p>

            <footer class="perm-card-foot">
              <span class="meta-label">模块归属</span>
              <el-tag size="small" type="info">{{ perm.module }}</el-tag>
            </footer>
          </div>
        </div>
      </el-card>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, computed, onMounted } from "vue";
import { ElMessage } from "element-plus";
import { getPermissions, type PermissionItem } from "../../api/admin";

const isLoading = ref(true);
const errorMessage = ref("");
const permissionList = ref<PermissionItem[]>([]);

// 搜索与过滤
const searchKeyword = ref("");
const filterModule = ref("");

// 模块折叠状态
const collapsedModules = reactive<Record<string, boolean>>({});

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
      ElMessage.success(`已复制权限编码：${code}`);
    } else {
      ElMessage.info(`权限编码：${code}`);
    }
  } catch {
    ElMessage.info(`权限编码：${code}`);
  }
}

/**
 * 从后端加载权限点列表
 */
async function fetchPermissionList() {
  isLoading.value = true;
  errorMessage.value = "";
  try {
    const list = await getPermissions();
    permissionList.value = Array.isArray(list) ? list : [];
  } catch (err: any) {
    console.error("[PermissionList] 加载失败:", err);
    errorMessage.value = err.message || "获取系统权限点列表异常";
    ElMessage.error(errorMessage.value);
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
  gap: 16px;
}

.page-header {
  margin-bottom: 4px;
}

.header-meta {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 8px;
}

.page-title {
  font-size: 20px;
  font-weight: 700;
  color: #ebf3fb;
  margin: 0 0 6px 0;
}

.page-desc {
  font-size: 13px;
  color: #8ca2b8;
  margin: 0;
  line-height: 1.5;
}

.filter-card {
  margin-bottom: 12px;
}

.filter-form {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 12px;
}

.mb-4 {
  margin-bottom: 16px;
}

.module-card-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  cursor: pointer;
  user-select: none;
}

.module-info-left {
  display: flex;
  align-items: center;
  gap: 12px;
}

.module-icon {
  font-size: 20px;
}

.module-name {
  font-size: 15px;
  font-weight: 600;
  color: #ebf3fb;
  margin-right: 8px;
}

.module-code {
  font-size: 12px;
  color: #8ca2b8;
}

.module-info-right {
  display: flex;
  align-items: center;
  gap: 10px;
}

.collapse-arrow {
  font-size: 11px;
  color: #8ca2b8;
  transition: transform 0.2s ease;
}

.collapse-arrow.is-collapsed {
  transform: rotate(-90deg);
}

.module-perm-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(280px, 1fr));
  gap: 14px;
  padding-top: 6px;
}

.perm-item-card {
  background: rgba(15, 23, 42, 0.6);
  border: 1px solid rgba(124, 162, 194, 0.12);
  border-radius: 8px;
  padding: 14px;
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.perm-card-top {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.perm-name-row {
  display: flex;
  align-items: center;
  gap: 6px;
}

.perm-title {
  font-size: 13px;
  color: #f1f5f9;
}

.perm-code-box {
  background: rgba(0, 0, 0, 0.35);
  padding: 4px 8px;
  border-radius: 4px;
  border: 1px solid rgba(255, 255, 255, 0.05);
}

.perm-code-val {
  font-size: 12px;
  color: var(--el-color-primary);
}

.perm-desc-text {
  font-size: 12px;
  color: #94a3b8;
  line-height: 1.4;
  margin: 0;
  flex: 1;
}

.perm-card-foot {
  display: flex;
  align-items: center;
  justify-content: space-between;
  border-top: 1px solid rgba(255, 255, 255, 0.05);
  padding-top: 8px;
}

.meta-label {
  font-size: 11px;
  color: #64748b;
}

.font-mono {
  font-family: var(--font-mono, monospace);
}
</style>
