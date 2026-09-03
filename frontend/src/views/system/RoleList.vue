<template>
  <div class="system-view-container">
    <!-- 头部说明 -->
    <header class="page-header">
      <div class="header-meta">
        <span class="meta-tag">AUTH / ROLE MANAGEMENT</span>
        <span class="meta-count-badge">当前租户定义 {{ roleList.length }} 个业务角色</span>
      </div>
      <div class="header-main-row">
        <div>
          <h1 class="page-title">业务角色与权限矩阵</h1>
          <p class="page-desc">
            维护系统角色画像，对角色进行功能权限点（Permission）和动态菜单树（Menu）的细粒度绑定与解绑。
          </p>
        </div>
        <button type="button" class="btn-create" @click="openCreateModal">
          <span class="btn-icon">＋</span>
          <span>新建角色</span>
        </button>
      </div>
    </header>

    <!-- 筛选工具栏 -->
    <section class="filter-panel">
      <div class="filter-row">
        <div class="filter-item">
          <label>关键字搜索</label>
          <input
            v-model="searchKeyword"
            type="text"
            class="filter-input"
            placeholder="搜索角色编码或名称..."
          />
        </div>

        <div class="filter-item">
          <label>状态筛选</label>
          <select v-model="filterStatus" class="filter-select">
            <option value="">全部状态</option>
            <option value="ACTIVE">正常 (ACTIVE)</option>
            <option value="DISABLED">已停用 (DISABLED)</option>
          </select>
        </div>

        <div class="filter-actions-inline">
          <button type="button" class="btn-search" @click="fetchRoleList">
            <span>🔄 刷新列表</span>
          </button>
        </div>
      </div>
    </section>

    <!-- 错误警告 -->
    <div v-if="errorMessage" class="error-banner">
      <span class="error-icon">⚠️</span>
      <div class="error-text">
        <strong>接口提示：</strong>
        <span>{{ errorMessage }}</span>
      </div>
      <button type="button" class="btn-retry" @click="fetchRoleList">重试</button>
    </div>

    <!-- 角色列表表格 -->
    <section class="table-wrapper">
      <div v-if="isLoading" class="table-loading-mask">
        <div class="spinner"></div>
        <span>正在加载角色列表...</span>
      </div>

      <table class="custom-table">
        <thead>
          <tr>
            <th style="width: 160px;">角色编码</th>
            <th style="width: 150px;">角色名称</th>
            <th>角色功能描述</th>
            <th style="width: 90px; text-align: center;">状态</th>
            <th style="width: 140px;">创建时间</th>
            <th style="width: 250px; text-align: center;">权限与菜单配置</th>
          </tr>
        </thead>
        <tbody>
          <tr v-if="!isLoading && filteredRoles.length === 0">
            <td colspan="6" class="empty-cell">
              <div class="empty-state">
                <span class="empty-icon">📂</span>
                <p>暂无符合条件的角色记录</p>
              </div>
            </td>
          </tr>

          <tr v-for="role in filteredRoles" :key="role.id">
            <!-- 角色编码 -->
            <td>
              <div class="role-code-wrap">
                <span class="font-mono highlight-cyan">{{ role.roleCode }}</span>
                <span v-if="isSystemRole(role)" class="sys-role-badge">系统基础</span>
              </div>
            </td>

            <!-- 角色名称 -->
            <td>
              <strong class="role-name-text">{{ role.roleName }}</strong>
            </td>

            <!-- 描述 -->
            <td>
              <span class="text-muted desc-text">{{ role.description || "-" }}</span>
            </td>

            <!-- 状态 Switch -->
            <td style="text-align: center;">
              <button
                type="button"
                class="switch-btn"
                :class="{ 'is-active': role.status === 'ACTIVE' }"
                :title="role.status === 'ACTIVE' ? '点击停用' : '点击启用'"
                @click="toggleRoleStatus(role)"
              >
                <span class="switch-handle"></span>
              </button>
            </td>

            <!-- 创建时间 -->
            <td>
              <span class="font-mono text-muted date-text">{{ role.createdAt || "-" }}</span>
            </td>

            <!-- 操作 -->
            <td>
              <div class="action-btn-group">
                <button
                  type="button"
                  class="action-btn btn-perm"
                  title="为角色分配功能权限点"
                  @click="openPermModal(role)"
                >
                  🔑 权限配置
                </button>
                <button
                  type="button"
                  class="action-btn btn-menu"
                  title="为角色分配动态菜单"
                  @click="openMenuModal(role)"
                >
                  📋 菜单授权
                </button>
                <button
                  type="button"
                  class="action-btn btn-edit"
                  title="编辑角色名称与描述"
                  @click="openEditModal(role)"
                >
                  编辑
                </button>
                <button
                  type="button"
                  class="action-btn btn-del"
                  :disabled="isSystemRole(role)"
                  :title="isSystemRole(role) ? '系统基础角色不可删除' : '删除角色'"
                  @click="handleDeleteRole(role)"
                >
                  删除
                </button>
              </div>
            </td>
          </tr>
        </tbody>
      </table>
    </section>

    <!-- 弹窗 1：新建角色 -->
    <div v-if="modals.create.visible" class="modal-overlay" @click.self="modals.create.visible = false">
      <div class="modal-card">
        <div class="modal-head">
          <h3 class="modal-title">新建业务角色</h3>
          <button type="button" class="modal-close" @click="modals.create.visible = false">✕</button>
        </div>

        <form class="modal-body" @submit.prevent="submitCreateRole">
          <div class="form-item">
            <label>角色编码 (Role Code) <b class="req-star">*</b></label>
            <input
              v-model="createForm.roleCode"
              type="text"
              class="form-input"
              placeholder="建议大写英文字母与下划线，例如 QUALITY_AUDITOR"
              required
            />
            <span class="field-hint">租户内唯一，一旦创建不可随意更改。</span>
          </div>

          <div class="form-item">
            <label>角色展示名称 (Role Name) <b class="req-star">*</b></label>
            <input
              v-model="createForm.roleName"
              type="text"
              class="form-input"
              placeholder="例如 质量审计专员"
              required
            />
          </div>

          <div class="form-item">
            <label>角色功能职责说明</label>
            <textarea
              v-model="createForm.description"
              class="form-textarea"
              rows="3"
              placeholder="描述该角色的业务定位与使用场景..."
            ></textarea>
          </div>

          <div class="modal-foot">
            <button type="button" class="btn-cancel" @click="modals.create.visible = false">取消</button>
            <button type="submit" class="btn-submit" :disabled="modals.create.submitting">
              {{ modals.create.submitting ? "创建中..." : "确认创建" }}
            </button>
          </div>
        </form>
      </div>
    </div>

    <!-- 弹窗 2：编辑角色 -->
    <div v-if="modals.edit.visible" class="modal-overlay" @click.self="modals.edit.visible = false">
      <div class="modal-card">
        <div class="modal-head">
          <h3 class="modal-title">编辑角色信息 ({{ editForm.roleCode }})</h3>
          <button type="button" class="modal-close" @click="modals.edit.visible = false">✕</button>
        </div>

        <form class="modal-body" @submit.prevent="submitEditRole">
          <div class="form-item">
            <label>角色编码 (只读)</label>
            <input
              type="text"
              class="form-input readonly-input"
              :value="editForm.roleCode"
              readonly
              disabled
            />
            <span class="field-hint">基础业务编码不支持直接修改。</span>
          </div>

          <div class="form-item">
            <label>角色展示名称 <b class="req-star">*</b></label>
            <input
              v-model="editForm.roleName"
              type="text"
              class="form-input"
              required
            />
          </div>

          <div class="form-item">
            <label>角色功能职责说明</label>
            <textarea
              v-model="editForm.description"
              class="form-textarea"
              rows="3"
            ></textarea>
          </div>

          <div class="modal-foot">
            <button type="button" class="btn-cancel" @click="modals.edit.visible = false">取消</button>
            <button type="submit" class="btn-submit" :disabled="modals.edit.submitting">
              {{ modals.edit.submitting ? "保存中..." : "保存修改" }}
            </button>
          </div>
        </form>
      </div>
    </div>

    <!-- 弹窗 3：权限配置 (按业务模块分组多选) -->
    <div v-if="modals.perm.visible" class="modal-overlay" @click.self="modals.perm.visible = false">
      <div class="modal-card modal-large">
        <div class="modal-head">
          <div>
            <h3 class="modal-title">配置功能权限点 — {{ activeRole?.roleName }} ({{ activeRole?.roleCode }})</h3>
            <span class="modal-sub">按系统业务模块分组授权，已选 {{ selectedPermIds.length }} 项</span>
          </div>
          <button type="button" class="modal-close" @click="modals.perm.visible = false">✕</button>
        </div>

        <div class="modal-body modal-scroll-body">
          <div class="module-group-container">
            <div
              v-for="(perms, moduleName) in groupedPermissions"
              :key="moduleName"
              class="module-group-card"
            >
              <div class="module-group-head">
                <div class="module-title-wrap">
                  <span class="module-tag">{{ formatModuleName(String(moduleName)) }}</span>
                  <span class="module-raw-code">({{ moduleName }})</span>
                </div>
                <div class="group-batch-actions">
                  <button
                    type="button"
                    class="batch-btn"
                    @click="selectAllModulePerms(perms)"
                  >
                    全选模块
                  </button>
                  <button
                    type="button"
                    class="batch-btn"
                    @click="deselectAllModulePerms(perms)"
                  >
                    清空
                  </button>
                </div>
              </div>

              <div class="perm-grid">
                <label
                  v-for="p in perms"
                  :key="p.id"
                  class="perm-checkbox-item"
                  :class="{ 'is-checked': selectedPermIds.includes(p.id) || selectedPermIds.includes(p.permissionCode) }"
                >
                  <input
                    v-model="selectedPermIds"
                    type="checkbox"
                    :value="p.id || p.permissionCode"
                  />
                  <div class="perm-text-info">
                    <span class="perm-name">{{ p.permissionName }}</span>
                    <span class="perm-code font-mono">{{ p.permissionCode }}</span>
                  </div>
                </label>
              </div>
            </div>
          </div>
        </div>

        <div class="modal-foot">
          <button type="button" class="btn-cancel" @click="modals.perm.visible = false">取消</button>
          <button type="submit" class="btn-submit" :disabled="modals.perm.submitting" @click="submitAssignPerms">
            {{ modals.perm.submitting ? "保存中..." : "保存权限授权" }}
          </button>
        </div>
      </div>
    </div>

    <!-- 弹窗 4：菜单配置 (树形勾选授权) -->
    <div v-if="modals.menu.visible" class="modal-overlay" @click.self="modals.menu.visible = false">
      <div class="modal-card modal-large">
        <div class="modal-head">
          <div>
            <h3 class="modal-title">配置动态菜单访问权限 — {{ activeRole?.roleName }} ({{ activeRole?.roleCode }})</h3>
            <span class="modal-sub">勾选该角色在侧边导航栏可见并可进入的菜单树</span>
          </div>
          <button type="button" class="modal-close" @click="modals.menu.visible = false">✕</button>
        </div>

        <div class="modal-body modal-scroll-body">
          <div class="menu-tree-container">
            <!-- 树形节点渲染 -->
            <div
              v-for="rootMenu in allMenus"
              :key="rootMenu.id"
              class="menu-tree-node root-node"
            >
              <div class="tree-row">
                <label class="tree-checkbox-wrap">
                  <input
                    v-model="selectedMenuIds"
                    type="checkbox"
                    :value="rootMenu.id"
                    @change="onParentMenuToggle(rootMenu)"
                  />
                  <span class="tree-item-title">{{ rootMenu.menuName }}</span>
                  <span class="tree-item-path font-mono">{{ rootMenu.routePath }}</span>
                </label>
              </div>

              <!-- 子菜单列表 -->
              <div v-if="rootMenu.children && rootMenu.children.length > 0" class="tree-children">
                <div
                  v-for="child in rootMenu.children"
                  :key="child.id"
                  class="menu-tree-node child-node"
                >
                  <label class="tree-checkbox-wrap">
                    <input
                      v-model="selectedMenuIds"
                      type="checkbox"
                      :value="child.id"
                    />
                    <span class="tree-item-title">{{ child.menuName }}</span>
                    <span class="tree-item-path font-mono">{{ child.routePath }}</span>
                    <span v-if="child.permissionCode" class="tree-perm-tag">{{ child.permissionCode }}</span>
                  </label>
                </div>
              </div>
            </div>
          </div>
        </div>

        <div class="modal-foot">
          <button type="button" class="btn-cancel" @click="modals.menu.visible = false">取消</button>
          <button type="submit" class="btn-submit" :disabled="modals.menu.submitting" @click="submitAssignMenus">
            {{ modals.menu.submitting ? "保存中..." : "保存菜单授权" }}
          </button>
        </div>
      </div>
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
  }, 3500);
}

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

/**
 * 判断是否为系统预置基础角色
 */
function isSystemRole(role: RoleItem): boolean {
  const baseCodes = ["TENANT_ADMIN", "SALES", "PURCHASING", "WAREHOUSE", "MES_INSPECTOR", "IOT_ENGINEER"];
  return baseCodes.includes(role.roleCode.toUpperCase()) || role.isSystem === true;
}

/**
 * 客户端计算过滤后的角色列表
 */
const filteredRoles = computed(() => {
  return roleList.value.filter((r) => {
    const kw = searchKeyword.value.trim().toLowerCase();
    const matchesKw = !kw || r.roleCode.toLowerCase().includes(kw) || r.roleName.toLowerCase().includes(kw);
    const matchesStatus = !filterStatus.value || r.status === filterStatus.value;
    return matchesKw && matchesStatus;
  });
});

/**
 * 权限按模块分组
 */
const groupedPermissions = computed(() => {
  const map: Record<string, PermissionItem[]> = {};
  for (const p of allPermissions.value) {
    const mod = p.module || "common";
    if (!map[mod]) map[mod] = [];
    map[mod].push(p);
  }
  return map;
});

/**
 * 模块编码友好翻译
 */
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

/**
 * 拉取角色列表
 */
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
    showToast(`角色列表拉取失败：${err.message}`, "danger");
  } finally {
    isLoading.value = false;
  }
}

/**
 * 预加载全量权限和菜单
 */
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

/**
 * 启停角色
 */
async function toggleRoleStatus(role: RoleItem) {
  const nextStatus = role.status === "ACTIVE" ? "DISABLED" : "ACTIVE";
  try {
    await updateRoleStatus(role.id, nextStatus);
    role.status = nextStatus;
    showToast(`角色 ${role.roleName} 状态已更新为 ${nextStatus === 'ACTIVE' ? '启用' : '停用'}`, "success");
  } catch (err: any) {
    showToast(`角色状态变更失败：${err.message}`, "danger");
  }
}

/**
 * 打开新建角色弹窗
 */
function openCreateModal() {
  createForm.roleCode = "";
  createForm.roleName = "";
  createForm.description = "";
  modals.create.visible = true;
}

/**
 * 提交新建角色
 */
async function submitCreateRole() {
  if (!createForm.roleCode.trim() || !createForm.roleName.trim()) {
    showToast("角色编码和角色名称不能为空", "warning");
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
    showToast(`角色 ${createForm.roleName} 创建成功！`, "success");
    modals.create.visible = false;
    await fetchRoleList();
  } catch (err: any) {
    showToast(`创建角色失败：${err.message}`, "danger");
  } finally {
    modals.create.submitting = false;
  }
}

/**
 * 打开编辑角色弹窗
 */
function openEditModal(role: RoleItem) {
  editForm.id = role.id;
  editForm.roleCode = role.roleCode;
  editForm.roleName = role.roleName;
  editForm.description = role.description || "";
  modals.edit.visible = true;
}

/**
 * 提交编辑角色
 */
async function submitEditRole() {
  if (!editForm.roleName.trim()) {
    showToast("角色名称不能为空", "warning");
    return;
  }

  modals.edit.submitting = true;
  try {
    await updateRole(editForm.id, {
      roleName: editForm.roleName.trim(),
      description: editForm.description.trim() || undefined,
    });
    showToast(`角色 ${editForm.roleName} 修改成功`, "success");
    modals.edit.visible = false;
    await fetchRoleList();

    // 联动刷新用户信息
    await authStore.fetchUserInfo();
  } catch (err: any) {
    showToast(`更新角色失败：${err.message}`, "danger");
  } finally {
    modals.edit.submitting = false;
  }
}

/**
 * 删除角色（捕捉 409 冲突友好提示）
 */
async function handleDeleteRole(role: RoleItem) {
  if (isSystemRole(role)) {
    showToast("操作受限：系统预置角色不可删除", "warning");
    return;
  }

  const confirmed = window.confirm(`确定要删除角色 [${role.roleName} (${role.roleCode})] 吗？若有用户关联将无法删除。`);
  if (!confirmed) return;

  try {
    await deleteRole(role.id);
    showToast(`角色 ${role.roleName} 已成功删除`, "success");
    await fetchRoleList();
  } catch (err: any) {
    // 捕获 409 错误或其他错误
    showToast(`删除失败：${err.message || "存在关联用户或权限冲突 (409)"}`, "danger");
  }
}

/**
 * 打开权限配置弹窗
 */
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

/**
 * 提交保存角色权限配置
 */
async function submitAssignPerms() {
  if (!activeRole.value) return;
  modals.perm.submitting = true;
  try {
    await assignRolePermissions(activeRole.value.id, selectedPermIds.value);
    showToast(`角色 ${activeRole.value.roleName} 权限点已更新`, "success");
    modals.perm.visible = false;

    // 联动刷新用户画像
    await authStore.fetchUserInfo();
  } catch (err: any) {
    showToast(`权限分配失败：${err.message}`, "danger");
  } finally {
    modals.perm.submitting = false;
  }
}

/**
 * 打开菜单配置弹窗
 */
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

/**
 * 提交保存角色菜单配置
 */
async function submitAssignMenus() {
  if (!activeRole.value) return;
  modals.menu.submitting = true;
  try {
    await assignRoleMenus(activeRole.value.id, selectedMenuIds.value);
    showToast(`角色 ${activeRole.value.roleName} 动态菜单已更新`, "success");
    modals.menu.visible = false;

    // 联动刷新用户侧边栏菜单
    await authStore.fetchUserMenus();
  } catch (err: any) {
    showToast(`菜单分配失败：${err.message}`, "danger");
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

.meta-count-badge {
  font-size: 11px;
  padding: 2px 8px;
  border-radius: 10px;
  background: rgba(103, 210, 255, 0.1);
  border: 1px solid rgba(103, 210, 255, 0.25);
  color: #c9e8fa;
}

.header-main-row {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  gap: 16px;
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

.btn-create {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 9px 18px;
  border-radius: 8px;
  background: linear-gradient(135deg, #1c7ba6, #0f5478);
  border: 1px solid rgba(103, 210, 255, 0.4);
  color: #fff;
  font-size: 13px;
  font-weight: 600;
  cursor: pointer;
  white-space: nowrap;
  transition: all 0.2s ease;
}

.btn-create:hover {
  background: linear-gradient(135deg, #2492c4, #146691);
  box-shadow: 0 0 14px rgba(103, 210, 255, 0.35);
}

.btn-icon {
  font-size: 15px;
  font-weight: bold;
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
  min-width: 220px;
}

.filter-select option {
  background: #0d1723;
}

.filter-actions-inline {
  margin-left: auto;
}

.btn-search {
  padding: 7px 16px;
  border-radius: 6px;
  background: rgba(103, 210, 255, 0.12);
  border: 1px solid rgba(103, 210, 255, 0.3);
  color: var(--accent, #67d2ff);
  font-size: 12px;
  cursor: pointer;
}

.table-wrapper {
  position: relative;
  border-radius: 14px;
  border: 1px solid var(--line, rgba(124, 162, 194, 0.18));
  background: var(--panel, rgba(10, 18, 28, 0.84));
  overflow-x: auto;
  box-shadow: 0 16px 40px rgba(0, 0, 0, 0.25);
}

.table-loading-mask {
  position: absolute;
  inset: 0;
  background: rgba(8, 17, 26, 0.7);
  backdrop-filter: blur(4px);
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 10px;
  z-index: 10;
  color: var(--accent, #67d2ff);
  font-size: 13px;
}

.custom-table {
  width: 100%;
  border-collapse: collapse;
  text-align: left;
  font-size: 13px;
}

.custom-table th {
  padding: 12px 14px;
  background: rgba(5, 11, 17, 0.9);
  color: #a2bed8;
  font-weight: 600;
  font-size: 12px;
  border-bottom: 1px solid rgba(124, 162, 194, 0.18);
  white-space: nowrap;
}

.custom-table td {
  padding: 12px 14px;
  border-bottom: 1px solid rgba(124, 162, 194, 0.1);
  color: var(--text, #ebf3fb);
  vertical-align: middle;
}

.custom-table tbody tr:hover {
  background: rgba(103, 210, 255, 0.04);
}

.role-code-wrap {
  display: flex;
  align-items: center;
  gap: 6px;
}

.sys-role-badge {
  font-size: 10px;
  padding: 1px 5px;
  border-radius: 4px;
  background: rgba(255, 178, 94, 0.12);
  border: 1px solid rgba(255, 178, 94, 0.3);
  color: #ffb25e;
}

.role-name-text {
  color: #fff;
}

.desc-text {
  font-size: 12px;
  line-height: 1.4;
}

.font-mono {
  font-family: monospace;
}

.highlight-cyan {
  color: var(--accent, #67d2ff);
  font-weight: 600;
}

.text-muted {
  color: var(--muted, #8ca2b8);
}

.date-text {
  font-size: 11px;
}

/* Switch 开关 */
.switch-btn {
  width: 36px;
  height: 20px;
  border-radius: 10px;
  background: rgba(255, 125, 125, 0.3);
  border: 1px solid rgba(255, 125, 125, 0.5);
  position: relative;
  cursor: pointer;
  transition: all 0.2s ease;
  padding: 0;
  outline: none;
}

.switch-btn.is-active {
  background: rgba(111, 225, 166, 0.3);
  border-color: rgba(111, 225, 166, 0.6);
}

.switch-handle {
  width: 14px;
  height: 14px;
  border-radius: 50%;
  background: #ff7d7d;
  position: absolute;
  top: 2px;
  left: 2px;
  transition: transform 0.2s ease, background 0.2s ease;
}

.switch-btn.is-active .switch-handle {
  transform: translateX(16px);
  background: #6fe1a6;
}

/* 操作按钮 */
.action-btn-group {
  display: flex;
  justify-content: center;
  align-items: center;
  gap: 6px;
}

.action-btn {
  padding: 4px 8px;
  border-radius: 4px;
  font-size: 11px;
  cursor: pointer;
  transition: all 0.15s ease;
  border: 1px solid transparent;
}

.btn-perm {
  background: rgba(111, 225, 166, 0.12);
  border-color: rgba(111, 225, 166, 0.3);
  color: #6fe1a6;
  font-weight: 600;
}
.btn-perm:hover {
  background: rgba(111, 225, 166, 0.25);
}

.btn-menu {
  background: rgba(103, 210, 255, 0.12);
  border-color: rgba(103, 210, 255, 0.3);
  color: var(--accent, #67d2ff);
  font-weight: 600;
}
.btn-menu:hover {
  background: rgba(103, 210, 255, 0.25);
}

.btn-edit {
  background: rgba(255, 255, 255, 0.05);
  border-color: rgba(124, 162, 194, 0.2);
  color: #d5e3ef;
}
.btn-edit:hover {
  background: rgba(255, 255, 255, 0.1);
}

.btn-del {
  background: rgba(255, 125, 125, 0.1);
  border-color: rgba(255, 125, 125, 0.25);
  color: #ff7d7d;
}
.btn-del:hover:not(:disabled) {
  background: rgba(255, 125, 125, 0.25);
}
.btn-del:disabled {
  opacity: 0.35;
  cursor: not-allowed;
}

/* 模态框 */
.modal-overlay {
  position: fixed;
  inset: 0;
  background: rgba(3, 7, 12, 0.78);
  backdrop-filter: blur(10px);
  display: flex;
  align-items: center;
  justify-content: center;
  z-index: 999;
  padding: 20px;
}

.modal-card {
  width: 100%;
  max-width: 560px;
  background: #0e1723;
  border: 1px solid rgba(103, 210, 255, 0.3);
  border-radius: 16px;
  box-shadow: 0 24px 60px rgba(0, 0, 0, 0.6);
  display: flex;
  flex-direction: column;
  overflow: hidden;
  animation: modalIn 0.2s ease;
}

.modal-large {
  max-width: 780px;
}

.modal-head {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 16px 20px;
  border-bottom: 1px solid rgba(124, 162, 194, 0.14);
  background: rgba(5, 11, 17, 0.6);
}

.modal-title {
  font-size: 16px;
  font-weight: 700;
  color: #fff;
  margin: 0;
}

.modal-sub {
  font-size: 11px;
  color: var(--muted, #8ca2b8);
  margin-top: 2px;
  display: block;
}

.modal-close {
  background: transparent;
  border: none;
  color: var(--muted, #8ca2b8);
  font-size: 16px;
  cursor: pointer;
}

.modal-body {
  padding: 20px;
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.modal-scroll-body {
  max-height: calc(85vh - 140px);
  overflow-y: auto;
}

.form-item {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.form-item label {
  font-size: 11px;
  color: #c4d9eb;
  font-weight: 600;
}

.req-star {
  color: #ff7d7d;
}

.form-input,
.form-textarea {
  width: 100%;
  padding: 8px 12px;
  border-radius: 6px;
  background: rgba(255, 255, 255, 0.04);
  border: 1px solid rgba(124, 162, 194, 0.25);
  color: #fff;
  font-size: 12px;
  font-family: inherit;
  outline: none;
  box-sizing: border-box;
}

.form-input:focus,
.form-textarea:focus {
  border-color: var(--accent, #67d2ff);
}

.readonly-input {
  background: rgba(0, 0, 0, 0.35);
  color: #8ca2b8;
  cursor: not-allowed;
}

.field-hint {
  font-size: 11px;
  color: var(--muted, #8ca2b8);
}

/* 权限按模块分组 UI */
.module-group-container {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.module-group-card {
  padding: 14px 16px;
  border-radius: 10px;
  background: rgba(255, 255, 255, 0.02);
  border: 1px solid rgba(124, 162, 194, 0.14);
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.module-group-head {
  display: flex;
  justify-content: space-between;
  align-items: center;
  border-bottom: 1px solid rgba(124, 162, 194, 0.1);
  padding-bottom: 8px;
}

.module-title-wrap {
  display: flex;
  align-items: center;
  gap: 8px;
}

.module-tag {
  font-size: 12px;
  font-weight: 700;
  color: var(--accent, #67d2ff);
}

.module-raw-code {
  font-size: 10px;
  color: var(--muted, #8ca2b8);
  font-family: monospace;
}

.group-batch-actions {
  display: flex;
  gap: 8px;
}

.batch-btn {
  padding: 2px 8px;
  border-radius: 4px;
  background: rgba(255, 255, 255, 0.05);
  border: 1px solid rgba(124, 162, 194, 0.2);
  color: #c9e8fa;
  font-size: 11px;
  cursor: pointer;
}

.batch-btn:hover {
  background: rgba(103, 210, 255, 0.15);
  border-color: rgba(103, 210, 255, 0.4);
}

.perm-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(220px, 1fr));
  gap: 8px;
}

.perm-checkbox-item {
  display: flex;
  align-items: flex-start;
  gap: 8px;
  padding: 8px 10px;
  border-radius: 6px;
  background: rgba(0, 0, 0, 0.2);
  border: 1px solid rgba(124, 162, 194, 0.1);
  cursor: pointer;
  transition: all 0.15s ease;
}

.perm-checkbox-item:hover {
  border-color: rgba(103, 210, 255, 0.3);
}

.perm-checkbox-item.is-checked {
  background: rgba(111, 225, 166, 0.08);
  border-color: #6fe1a6;
}

.perm-text-info {
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.perm-name {
  font-size: 12px;
  font-weight: 500;
  color: #fff;
}

.perm-code {
  font-size: 10px;
  color: #8ca2b8;
}

/* 树形菜单勾选 UI */
.menu-tree-container {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.menu-tree-node.root-node {
  padding: 12px 14px;
  border-radius: 10px;
  background: rgba(255, 255, 255, 0.02);
  border: 1px solid rgba(124, 162, 194, 0.12);
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.tree-row {
  display: flex;
  align-items: center;
}

.tree-checkbox-wrap {
  display: flex;
  align-items: center;
  gap: 8px;
  cursor: pointer;
  font-size: 13px;
  color: #fff;
}

.tree-item-title {
  font-weight: 600;
}

.tree-item-path {
  font-size: 11px;
  color: var(--accent, #67d2ff);
  background: rgba(103, 210, 255, 0.08);
  padding: 1px 6px;
  border-radius: 4px;
}

.tree-perm-tag {
  font-size: 10px;
  color: #ffb25e;
  font-family: monospace;
}

.tree-children {
  display: flex;
  flex-direction: column;
  gap: 8px;
  padding-left: 28px;
  border-left: 1px dashed rgba(124, 162, 194, 0.2);
  margin-left: 8px;
}

.child-node .tree-checkbox-wrap {
  font-size: 12px;
  color: #d5e3ef;
}

.modal-foot {
  display: flex;
  justify-content: flex-end;
  gap: 10px;
  padding: 14px 20px;
  border-top: 1px solid rgba(124, 162, 194, 0.12);
  background: rgba(5, 11, 17, 0.6);
}

.btn-cancel {
  padding: 8px 16px;
  border-radius: 6px;
  background: rgba(255, 255, 255, 0.04);
  border: 1px solid rgba(124, 162, 194, 0.2);
  color: #8ca2b8;
  font-size: 12px;
  cursor: pointer;
}

.btn-submit {
  padding: 8px 18px;
  border-radius: 6px;
  background: linear-gradient(135deg, #1c7ba6, #0f5478);
  border: 1px solid rgba(103, 210, 255, 0.4);
  color: #fff;
  font-size: 12px;
  font-weight: 600;
  cursor: pointer;
  transition: all 0.2s ease;
}

.btn-submit:hover:not(:disabled) {
  background: linear-gradient(135deg, #2492c4, #146691);
}

.btn-submit:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}

/* 错误与 Toast */
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

.sys-toast.is-warning {
  background: #2c2010;
  border: 1px solid #ffb25e;
  color: #ffb25e;
}

.spinner {
  width: 16px;
  height: 16px;
  border: 2px solid rgba(103, 210, 255, 0.2);
  border-top-color: var(--accent, #67d2ff);
  border-radius: 50%;
  animation: spin 0.8s linear infinite;
}

.empty-cell {
  text-align: center;
  padding: 48px !important;
}

.empty-state {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 8px;
  color: var(--muted, #8ca2b8);
}

.empty-icon {
  font-size: 28px;
}

@keyframes spin {
  to {
    transform: rotate(360deg);
  }
}

@keyframes modalIn {
  from {
    opacity: 0;
    transform: scale(0.95);
  }
  to {
    opacity: 1;
    transform: scale(1);
  }
}
</style>
