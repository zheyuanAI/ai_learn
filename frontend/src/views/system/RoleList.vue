<template>
  <div class="system-view-container">
    <!-- 头部说明 -->
    <header class="page-header">
      <div class="header-meta">
        <el-tag type="info" size="small">AUTH / ROLE MANAGEMENT</el-tag>
        <el-tag type="primary" size="small" effect="plain">当前租户定义 {{ roleList.length }} 个业务角色</el-tag>
      </div>
      <div class="header-main-row">
        <div>
          <h1 class="page-title">业务角色与权限矩阵</h1>
          <p class="page-desc">
            维护系统角色画像，对角色进行功能权限点（Permission）和动态菜单树（Menu）的细粒度绑定与解绑。
          </p>
        </div>
        <el-button type="primary" @click="openCreateModal">
          ＋ 新建角色
        </el-button>
      </div>
    </header>

    <!-- 筛选工具栏 -->
    <el-card shadow="never" class="filter-card">
      <el-form inline class="filter-form">
        <el-form-item label="关键字搜索">
          <el-input
            v-model="searchKeyword"
            placeholder="搜索角色编码或名称..."
            clearable
            style="width: 240px;"
          />
        </el-form-item>

        <el-form-item label="状态筛选">
          <el-select
            v-model="filterStatus"
            clearable
            placeholder="全部状态"
            style="width: 140px;"
          >
            <el-option label="全部状态" value="" />
            <el-option label="正常 (ACTIVE)" value="ACTIVE" />
            <el-option label="已停用 (DISABLED)" value="DISABLED" />
          </el-select>
        </el-form-item>

        <el-form-item>
          <el-button :loading="isLoading" @click="fetchRoleList">
            🔄 刷新列表
          </el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <!-- 错误警告 -->
    <el-alert
      v-if="errorMessage"
      :title="'接口提示：' + errorMessage"
      type="error"
      show-icon
      class="mb-4"
    >
      <template #default>
        <el-button type="danger" link size="small" @click="fetchRoleList">重试</el-button>
      </template>
    </el-alert>

    <!-- 角色列表表格 -->
    <el-card shadow="never" class="table-card">
      <el-table
        v-loading="isLoading"
        :data="filteredRoles"
        row-key="id"
        stripe
        style="width: 100%"
      >
        <template #empty>
          <el-empty description="暂无符合条件的角色记录" />
        </template>

        <el-table-column prop="roleCode" label="角色编码" width="180">
          <template #default="{ row }">
            <span class="font-mono text-cyan">{{ row.roleCode }}</span>
            <el-tag v-if="isSystemRole(row)" size="small" type="warning" effect="dark" class="ml-2">
              系统基础
            </el-tag>
          </template>
        </el-table-column>

        <el-table-column prop="roleName" label="角色名称" width="160">
          <template #default="{ row }">
            <strong class="role-name-text">{{ row.roleName }}</strong>
          </template>
        </el-table-column>

        <el-table-column prop="description" label="角色功能描述" min-width="220" show-overflow-tooltip>
          <template #default="{ row }">
            <span class="text-muted">{{ row.description || "-" }}</span>
          </template>
        </el-table-column>

        <el-table-column label="状态" width="100" align="center">
          <template #default="{ row }">
            <el-switch
              :model-value="row.status === 'ACTIVE'"
              active-text=""
              inactive-text=""
              @change="toggleRoleStatus(row)"
            />
          </template>
        </el-table-column>

        <el-table-column prop="createdAt" label="创建时间" width="170">
          <template #default="{ row }">
            <span class="font-mono text-muted">{{ row.createdAt || "-" }}</span>
          </template>
        </el-table-column>

        <el-table-column label="权限与菜单配置" width="280" align="center" fixed="right">
          <template #default="{ row }">
            <el-button type="primary" link size="small" @click="openPermModal(row)">
              🔑 权限配置
            </el-button>
            <el-button type="primary" link size="small" @click="openMenuModal(row)">
              📋 菜单授权
            </el-button>
            <el-button type="info" link size="small" @click="openEditModal(row)">
              编辑
            </el-button>
            <el-button
              type="danger"
              link
              size="small"
              :disabled="isSystemRole(row)"
              @click="handleDeleteRole(row)"
            >
              删除
            </el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <!-- 弹窗 1：新建角色 -->
    <el-dialog
      v-model="modals.create.visible"
      title="新建业务角色"
      width="540px"
      destroy-on-close
    >
      <el-form label-position="top" @submit.prevent="submitCreateRole">
        <el-form-item label="角色编码 (Role Code)" required>
          <el-input
            v-model="createForm.roleCode"
            placeholder="建议大写英文与下划线，例如 QUALITY_AUDITOR"
          />
          <span class="field-hint">租户内唯一，一旦创建不可随意更改。</span>
        </el-form-item>

        <el-form-item label="角色展示名称 (Role Name)" required>
          <el-input v-model="createForm.roleName" placeholder="例如 质量审计专员" />
        </el-form-item>

        <el-form-item label="角色功能职责说明">
          <el-input
            v-model="createForm.description"
            type="textarea"
            :rows="3"
            placeholder="描述该角色的业务定位与使用场景..."
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="modals.create.visible = false">取消</el-button>
        <el-button type="primary" :loading="modals.create.submitting" @click="submitCreateRole">
          {{ modals.create.submitting ? "创建中..." : "确认创建" }}
        </el-button>
      </template>
    </el-dialog>

    <!-- 弹窗 2：编辑角色 -->
    <el-dialog
      v-model="modals.edit.visible"
      :title="'编辑角色信息 (' + editForm.roleCode + ')'"
      width="540px"
      destroy-on-close
    >
      <el-form label-position="top" @submit.prevent="submitEditRole">
        <el-form-item label="角色编码 (只读)">
          <el-input :model-value="editForm.roleCode" disabled />
          <span class="field-hint">基础业务编码不支持直接修改。</span>
        </el-form-item>

        <el-form-item label="角色展示名称" required>
          <el-input v-model="editForm.roleName" />
        </el-form-item>

        <el-form-item label="角色功能职责说明">
          <el-input
            v-model="editForm.description"
            type="textarea"
            :rows="3"
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="modals.edit.visible = false">取消</el-button>
        <el-button type="primary" :loading="modals.edit.submitting" @click="submitEditRole">
          {{ modals.edit.submitting ? "保存中..." : "保存修改" }}
        </el-button>
      </template>
    </el-dialog>

    <!-- 弹窗 3：权限配置 (按业务模块分组多选) -->
    <el-dialog
      v-model="modals.perm.visible"
      :title="'配置功能权限点 — ' + (activeRole?.roleName || '') + ' (' + (activeRole?.roleCode || '') + ')'"
      width="780px"
      destroy-on-close
    >
      <div class="perm-modal-container">
        <el-alert
          :title="'按系统业务模块分组授权，已选 ' + selectedPermIds.length + ' 项'"
          type="info"
          :closable="false"
          class="mb-3"
        />

        <div class="module-group-container">
          <el-card
            v-for="(perms, moduleName) in groupedPermissions"
            :key="moduleName"
            shadow="never"
            class="module-group-card mb-3"
          >
            <template #header>
              <div class="module-group-head">
                <div>
                  <span class="font-bold">{{ formatModuleName(String(moduleName)) }}</span>
                  <span class="text-muted ml-2">({{ moduleName }})</span>
                </div>
                <div>
                  <el-button size="small" link type="primary" @click="selectAllModulePerms(perms)">
                    全选模块
                  </el-button>
                  <el-button size="small" link @click="deselectAllModulePerms(perms)">
                    清空
                  </el-button>
                </div>
              </div>
            </template>

            <el-checkbox-group v-model="selectedPermIds" class="perm-cb-grid">
              <el-checkbox
                v-for="p in perms"
                :key="p.id"
                :label="p.id || p.permissionCode"
                class="perm-cb-item"
              >
                <div class="perm-cb-label">
                  <span class="font-medium">{{ p.permissionName }}</span>
                  <code class="font-mono text-muted text-xs">{{ p.permissionCode }}</code>
                </div>
              </el-checkbox>
            </el-checkbox-group>
          </el-card>
        </div>
      </div>
      <template #footer>
        <el-button @click="modals.perm.visible = false">取消</el-button>
        <el-button type="primary" :loading="modals.perm.submitting" @click="submitAssignPerms">
          {{ modals.perm.submitting ? "保存中..." : "保存权限授权" }}
        </el-button>
      </template>
    </el-dialog>

    <!-- 弹窗 4：菜单配置 (树形勾选授权) -->
    <el-dialog
      v-model="modals.menu.visible"
      :title="'配置动态菜单访问权限 — ' + (activeRole?.roleName || '') + ' (' + (activeRole?.roleCode || '') + ')'"
      width="640px"
      destroy-on-close
    >
      <div class="menu-modal-container">
        <el-alert
          title="勾选该角色在侧边导航栏可见并可进入的菜单树"
          type="info"
          :closable="false"
          class="mb-3"
        />

        <el-checkbox-group v-model="selectedMenuIds" class="menu-tree-list">
          <div
            v-for="rootMenu in allMenus"
            :key="rootMenu.id"
            class="menu-tree-card"
          >
            <div class="root-menu-line">
              <el-checkbox
                :label="rootMenu.id"
                @change="onParentMenuToggle(rootMenu)"
              >
                <span class="font-bold">{{ rootMenu.menuName }}</span>
                <code class="font-mono text-cyan ml-2">{{ rootMenu.routePath }}</code>
              </el-checkbox>
            </div>

            <!-- 子菜单列表 -->
            <div v-if="rootMenu.children && rootMenu.children.length > 0" class="sub-menu-indent">
              <el-checkbox
                v-for="child in rootMenu.children"
                :key="child.id"
                :label="child.id"
                class="sub-menu-item"
              >
                <span>{{ child.menuName }}</span>
                <code class="font-mono text-muted text-xs ml-2">{{ child.routePath }}</code>
              </el-checkbox>
            </div>
          </div>
        </el-checkbox-group>
      </div>
      <template #footer>
        <el-button @click="modals.menu.visible = false">取消</el-button>
        <el-button type="primary" :loading="modals.menu.submitting" @click="submitAssignMenus">
          {{ modals.menu.submitting ? "保存中..." : "保存菜单授权" }}
        </el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, computed, onMounted } from "vue";
import { ElMessage, ElMessageBox } from "element-plus";
import {
  getRoles,
  createRole,
  updateRole,
  updateRoleStatus,
  deleteRole,
  getRolePermissions,
  assignRolePermissions,
  getRoleMenus,
  assignRoleMenus,
  getPermissions,
  getMenus,
  type RoleItem,
  type PermissionItem,
  type MenuItem,
} from "../../api/admin";
import { useAuthStore } from "../../stores/auth";

const authStore = useAuthStore();

const isLoading = ref(true);
const errorMessage = ref("");
const roleList = ref<RoleItem[]>([]);
const allPermissions = ref<PermissionItem[]>([]);
const allMenus = ref<MenuItem[]>([]);

// 筛选状态
const searchKeyword = ref("");
const filterStatus = ref("");

// 当前操作选中的角色
const activeRole = ref<RoleItem | null>(null);
const selectedPermIds = ref<string[]>([]);
const selectedMenuIds = ref<string[]>([]);

// 弹窗状态
const modals = reactive({
  create: { visible: false, submitting: false },
  edit: { visible: false, submitting: false },
  perm: { visible: false, submitting: false },
  menu: { visible: false, submitting: false },
});

const createForm = reactive({
  roleCode: "",
  roleName: "",
  description: "",
});

const editForm = reactive({
  id: "",
  roleCode: "",
  roleName: "",
  description: "",
});

function isSystemRole(role: RoleItem): boolean {
  const baseCodes = ["TENANT_ADMIN", "SALES", "PURCHASING", "WAREHOUSE", "MES_INSPECTOR", "IOT_ENGINEER"];
  return baseCodes.includes(role.roleCode.toUpperCase()) || role.isSystem === true;
}

const filteredRoles = computed(() => {
  return roleList.value.filter((r) => {
    const kw = searchKeyword.value.trim().toLowerCase();
    const matchesKw = !kw || r.roleCode.toLowerCase().includes(kw) || r.roleName.toLowerCase().includes(kw);
    const matchesStatus = !filterStatus.value || r.status === filterStatus.value;
    return matchesKw && matchesStatus;
  });
});

const groupedPermissions = computed(() => {
  const map: Record<string, PermissionItem[]> = {};
  for (const p of allPermissions.value) {
    const mod = p.module || "common";
    if (!map[mod]) map[mod] = [];
    map[mod].push(p);
  }
  return map;
});

function formatModuleName(mod: string): string {
  const modMap: Record<string, string> = {
    auth: "认证与系统管理 (Auth)",
    purchasing: "采购供需管理 (Purchasing)",
    sales: "销售与履约管理 (Sales)",
    inventory: "仓储与库存收发 (Inventory)",
    mes: "生产制造与工序 (MES)",
    iot: "IoT设备与遥测告警 (IoT)",
    gis: "空间地图与看板 (GIS)",
    ai: "AI助手只读能力 (AI)",
  };
  return modMap[mod] || `模块 [${mod}]`;
}

async function fetchRoleList() {
  isLoading.value = true;
  errorMessage.value = "";
  try {
    const res = await getRoles();
    if (res.data && Array.isArray(res.data)) {
      roleList.value = res.data;
    }
  } catch (err: any) {
    errorMessage.value = err.message || "无法拉取角色列表";
    ElMessage.error(`角色列表拉取失败：${err.message}`);
  } finally {
    isLoading.value = false;
  }
}

async function preloadDicts() {
  try {
    const [permRes, menuRes] = await Promise.all([getPermissions(), getMenus()]);
    if (permRes.data && Array.isArray(permRes.data)) {
      allPermissions.value = permRes.data;
    }
    if (menuRes.data && Array.isArray(menuRes.data)) {
      allMenus.value = menuRes.data;
    }
  } catch (err: any) {
    console.warn("预加载字典异常:", err.message);
  }
}

async function toggleRoleStatus(role: RoleItem) {
  const nextStatus = role.status === "ACTIVE" ? "DISABLED" : "ACTIVE";
  try {
    await updateRoleStatus(role.id, nextStatus);
    role.status = nextStatus;
    ElMessage.success(`角色 ${role.roleName} 状态已更新为 ${nextStatus === 'ACTIVE' ? '启用' : '停用'}`);
  } catch (err: any) {
    ElMessage.error(`角色状态变更失败：${err.message}`);
  }
}

function openCreateModal() {
  createForm.roleCode = "";
  createForm.roleName = "";
  createForm.description = "";
  modals.create.visible = true;
}

async function submitCreateRole() {
  if (!createForm.roleCode.trim() || !createForm.roleName.trim()) {
    ElMessage.warning("角色编码和角色名称不能为空");
    return;
  }

  modals.create.submitting = true;
  try {
    await createRole({
      roleCode: createForm.roleCode.trim().toUpperCase(),
      roleName: createForm.roleName.trim(),
      description: createForm.description.trim() || undefined,
      status: "ACTIVE",
    });
    ElMessage.success(`角色 ${createForm.roleName} 创建成功！`);
    modals.create.visible = false;
    await fetchRoleList();
  } catch (err: any) {
    ElMessage.error(`创建角色失败：${err.message}`);
  } finally {
    modals.create.submitting = false;
  }
}

function openEditModal(role: RoleItem) {
  editForm.id = role.id;
  editForm.roleCode = role.roleCode;
  editForm.roleName = role.roleName;
  editForm.description = role.description || "";
  modals.edit.visible = true;
}

async function submitEditRole() {
  if (!editForm.roleName.trim()) {
    ElMessage.warning("角色名称不能为空");
    return;
  }

  modals.edit.submitting = true;
  try {
    await updateRole(editForm.id, {
      roleName: editForm.roleName.trim(),
      description: editForm.description.trim() || undefined,
    });
    ElMessage.success(`角色 ${editForm.roleName} 修改成功`);
    modals.edit.visible = false;
    await fetchRoleList();
    await authStore.fetchUserInfo();
  } catch (err: any) {
    ElMessage.error(`更新角色失败：${err.message}`);
  } finally {
    modals.edit.submitting = false;
  }
}

async function handleDeleteRole(role: RoleItem) {
  if (isSystemRole(role)) {
    ElMessage.warning("操作受限：系统预置角色不可删除");
    return;
  }

  try {
    await ElMessageBox.confirm(
      `确定要删除角色 [${role.roleName} (${role.roleCode})] 吗？若有用户关联将无法删除。`,
      "操作确认",
      {
        confirmButtonText: "确认删除",
        cancelButtonText: "取消",
        type: "warning",
      }
    );
  } catch {
    return;
  }

  try {
    await deleteRole(role.id);
    ElMessage.success(`角色 ${role.roleName} 已成功删除`);
    await fetchRoleList();
  } catch (err: any) {
    ElMessage.error(`删除失败：${err.message || "存在关联用户或权限冲突 (409)"}`);
  }
}

async function openPermModal(role: RoleItem) {
  activeRole.value = role;
  selectedPermIds.value = [];
  modals.perm.visible = true;

  try {
    const res = await getRolePermissions(role.id);
    if (res.data && Array.isArray(res.data)) {
      selectedPermIds.value = res.data.map((item: any) => {
        if (typeof item === "string") return item;
        return item.id || item.permissionCode || String(item);
      });
    }
  } catch (err: any) {
    console.warn("拉取角色权限失败:", err.message);
  }
}

function selectAllModulePerms(perms: PermissionItem[]) {
  const ids = perms.map((p) => p.id || p.permissionCode);
  selectedPermIds.value = [...new Set([...selectedPermIds.value, ...ids])];
}

function deselectAllModulePerms(perms: PermissionItem[]) {
  const idsToRemove = new Set(perms.map((p) => p.id || p.permissionCode));
  selectedPermIds.value = selectedPermIds.value.filter((id) => !idsToRemove.has(id));
}

async function submitAssignPerms() {
  if (!activeRole.value) return;
  modals.perm.submitting = true;
  try {
    await assignRolePermissions(activeRole.value.id, selectedPermIds.value);
    ElMessage.success(`角色 ${activeRole.value.roleName} 权限点已更新`);
    modals.perm.visible = false;
    await authStore.fetchUserInfo();
  } catch (err: any) {
    ElMessage.error(`权限分配失败：${err.message}`);
  } finally {
    modals.perm.submitting = false;
  }
}

async function openMenuModal(role: RoleItem) {
  activeRole.value = role;
  selectedMenuIds.value = [];
  modals.menu.visible = true;

  try {
    const res = await getRoleMenus(role.id);
    if (res.data && Array.isArray(res.data)) {
      selectedMenuIds.value = res.data.map((item: any) => {
        if (typeof item === "string") return item;
        return item.id || item.menuCode || String(item);
      });
    }
  } catch (err: any) {
    console.warn("拉取角色菜单失败:", err.message);
  }
}

function onParentMenuToggle(parent: MenuItem) {
  const isParentChecked = selectedMenuIds.value.includes(parent.id);
  if (parent.children && parent.children.length > 0) {
    for (const c of parent.children) {
      if (isParentChecked) {
        if (!selectedMenuIds.value.includes(c.id)) {
          selectedMenuIds.value.push(c.id);
        }
      } else {
        selectedMenuIds.value = selectedMenuIds.value.filter((id) => id !== c.id);
      }
    }
  }
}

async function submitAssignMenus() {
  if (!activeRole.value) return;
  modals.menu.submitting = true;
  try {
    await assignRoleMenus(activeRole.value.id, selectedMenuIds.value);
    ElMessage.success(`角色 ${activeRole.value.roleName} 动态菜单已更新`);
    modals.menu.visible = false;
    await authStore.fetchUserMenus();
  } catch (err: any) {
    ElMessage.error(`菜单分配失败：${err.message}`);
  } finally {
    modals.menu.submitting = false;
  }
}

onMounted(() => {
  fetchRoleList();
  preloadDicts();
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

.header-main-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
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
  margin-bottom: 4px;
}

.filter-form {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 12px;
}

.table-card {
  margin-top: 4px;
}

.field-hint {
  font-size: 11px;
  color: #8ca2b8;
  margin-top: 4px;
  display: block;
}

.perm-cb-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(220px, 1fr));
  gap: 10px;
}

.perm-cb-item {
  margin-right: 0 !important;
}

.perm-cb-label {
  display: flex;
  flex-direction: column;
}

.module-group-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.menu-tree-card {
  background: rgba(15, 23, 42, 0.4);
  border: 1px solid rgba(255, 255, 255, 0.05);
  border-radius: 6px;
  padding: 10px 14px;
  margin-bottom: 10px;
}

.sub-menu-indent {
  padding-left: 26px;
  display: flex;
  flex-wrap: wrap;
  gap: 12px;
  margin-top: 6px;
}

.sub-menu-item {
  margin-right: 0 !important;
}

.text-cyan {
  color: var(--el-color-primary);
}

.text-muted {
  color: #8ca2b8;
}

.text-xs {
  font-size: 11px;
}

.mb-3 {
  margin-bottom: 12px;
}

.mb-4 {
  margin-bottom: 16px;
}

.ml-2 {
  margin-left: 8px;
}

.font-bold {
  font-weight: 600;
}

.font-medium {
  font-weight: 500;
}

.font-mono {
  font-family: var(--font-mono, monospace);
}
</style>
