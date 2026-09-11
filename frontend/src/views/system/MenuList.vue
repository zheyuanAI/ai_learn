<template>
  <div class="system-view-container">
    <!-- 头部说明 -->
    <header class="page-header">
      <div class="header-meta">
        <el-tag type="info" size="small">AUTH / MENU MANAGEMENT</el-tag>
        <el-tag type="primary" size="small" effect="plain">动态菜单树 (层级组织)</el-tag>
      </div>
      <div class="header-main-row">
        <div>
          <h1 class="page-title">系统动态菜单管理</h1>
          <p class="page-desc">
            维护系统左侧导航栏的菜单节点树、路由路径、前端组件映射与权限点挂载。修改后即时联动侧边栏。
          </p>
        </div>
        <div class="header-action-group">
          <el-button type="primary" @click="openCreateModal(null)">
            ＋ 新建顶级菜单
          </el-button>
        </div>
      </div>
    </header>

    <!-- 工具栏 -->
    <el-card shadow="never" class="filter-card">
      <el-form inline class="filter-form">
        <el-form-item label="关键字过滤">
          <el-input
            v-model="searchKeyword"
            placeholder="搜索菜单名称、编码或路由..."
            clearable
            style="width: 280px;"
          />
        </el-form-item>

        <el-form-item>
          <el-button-group>
            <el-button @click="expandAllNodes">展开全部</el-button>
            <el-button @click="collapseAllNodes">折叠全部</el-button>
            <el-button :loading="isLoading" @click="fetchMenuList">🔄 刷新</el-button>
          </el-button-group>
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
        <el-button type="danger" link size="small" @click="fetchMenuList">重试</el-button>
      </template>
    </el-alert>

    <!-- 菜单树表格 (Tree Table) -->
    <el-card shadow="never" class="table-card">
      <el-table
        v-loading="isLoading"
        :data="filteredTreeData"
        row-key="id"
        :tree-props="{ children: 'children' }"
        :default-expand-all="true"
        stripe
        style="width: 100%"
      >
        <template #empty>
          <el-empty description="未找到符合条件的菜单节点" />
        </template>

        <el-table-column prop="menuName" label="菜单名称 (层级树)" min-width="240">
          <template #default="{ row }">
            <span class="menu-icon-symbol mr-2">{{ row.icon || (row.children && row.children.length ? '📁' : '📄') }}</span>
            <strong class="menu-name-text">{{ row.menuName }}</strong>
          </template>
        </el-table-column>

        <el-table-column prop="menuCode" label="菜单编码" width="160" />

        <el-table-column prop="routePath" label="前端路由路径" width="190" show-overflow-tooltip>
          <template #default="{ row }">
            <code class="font-mono text-cyan">{{ row.routePath }}</code>
          </template>
        </el-table-column>

        <el-table-column prop="componentPath" label="视图组件路径" width="200" show-overflow-tooltip>
          <template #default="{ row }">
            <span class="font-mono text-muted">{{ row.componentPath || '—' }}</span>
          </template>
        </el-table-column>

        <el-table-column prop="sortOrder" label="排序" width="80" align="center" />

        <el-table-column prop="permissionCode" label="关联权限点" width="180" show-overflow-tooltip>
          <template #default="{ row }">
            <el-tag v-if="row.permissionCode" size="small" type="info" effect="plain">
              {{ row.permissionCode }}
            </el-tag>
            <span v-else class="text-muted">—</span>
          </template>
        </el-table-column>

        <el-table-column label="可见性" width="90" align="center">
          <template #default="{ row }">
            <el-button
              size="small"
              link
              :type="row.visible !== false ? 'success' : 'info'"
              @click="toggleMenuVisibility(row)"
            >
              {{ row.visible !== false ? '● 可见' : '○ 隐藏' }}
            </el-button>
          </template>
        </el-table-column>

        <el-table-column label="启用状态" width="100" align="center">
          <template #default="{ row }">
            <el-button
              size="small"
              link
              :type="row.status === 'ACTIVE' ? 'success' : 'danger'"
              @click="toggleMenuStatus(row)"
            >
              {{ row.status === 'ACTIVE' ? '启用' : '停用' }}
            </el-button>
          </template>
        </el-table-column>

        <el-table-column label="操作" width="220" align="center" fixed="right">
          <template #default="{ row }">
            <el-button type="primary" link size="small" @click="openCreateModal(row)">
              ＋ 子菜单
            </el-button>
            <el-button type="primary" link size="small" @click="openEditModal(row)">
              编辑
            </el-button>
            <el-button type="danger" link size="small" @click="handleDeleteMenu(row)">
              删除
            </el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <!-- 弹窗 1：新建菜单 (顶级或子菜单) -->
    <el-dialog
      v-model="modals.create.visible"
      :title="createForm.parentId ? '添加子菜单' : '新建顶级菜单'"
      width="640px"
      destroy-on-close
    >
      <el-form label-position="top" @submit.prevent="submitCreateMenu">
        <el-form-item label="上级父菜单 (Parent Menu)">
          <el-select v-model="createForm.parentId" clearable placeholder="【无】顶级根菜单" style="width: 100%;">
            <el-option value="" label="【无】顶级根菜单" />
            <el-option
              v-for="m in flattenedSelectOptions"
              :key="m.id"
              :value="m.id"
              :label="m.prefix + m.menuName + ' (' + m.menuCode + ')'"
            />
          </el-select>
        </el-form-item>

        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item label="菜单编码 (Menu Code)" required>
              <el-input v-model="createForm.menuCode" placeholder="例如 SystemUsers" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="菜单名称 (Menu Name)" required>
              <el-input v-model="createForm.menuName" placeholder="例如 用户管理" />
            </el-form-item>
          </el-col>
        </el-row>

        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item label="前端路由路径 (Route Path)" required>
              <el-input v-model="createForm.routePath" placeholder="例如 /system/users" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="组件路径 (Component Path)">
              <el-input v-model="createForm.componentPath" placeholder="例如 views/system/UserList.vue" />
            </el-form-item>
          </el-col>
        </el-row>

        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item label="图标标识 (Icon)">
              <el-input v-model="createForm.icon" placeholder="例如 👤 或 📦" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="排序序号 (Sort Order)">
              <el-input-number v-model="createForm.sortOrder" :min="0" :max="999" style="width: 100%;" />
            </el-form-item>
          </el-col>
        </el-row>

        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item label="挂载权限点 (Permission Code)">
              <el-input v-model="createForm.permissionCode" placeholder="如 auth:user:view" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="侧边栏可见性">
              <el-switch v-model="createForm.visible" active-text="可见" inactive-text="隐藏" />
            </el-form-item>
          </el-col>
        </el-row>
      </el-form>
      <template #footer>
        <el-button @click="modals.create.visible = false">取消</el-button>
        <el-button type="primary" :loading="modals.create.submitting" @click="submitCreateMenu">
          {{ modals.create.submitting ? "正在创建..." : "确认创建" }}
        </el-button>
      </template>
    </el-dialog>

    <!-- 弹窗 2：编辑菜单属性 -->
    <el-dialog
      v-model="modals.edit.visible"
      title="编辑菜单属性"
      width="640px"
      destroy-on-close
    >
      <el-form label-position="top" @submit.prevent="submitEditMenu">
        <el-form-item label="上级父菜单 (Parent Menu)">
          <el-select v-model="editForm.parentId" clearable placeholder="【无】顶级根菜单" style="width: 100%;">
            <el-option value="" label="【无】顶级根菜单" />
            <el-option
              v-for="m in validParentOptionsForEdit"
              :key="m.id"
              :value="m.id"
              :label="m.prefix + m.menuName + ' (' + m.menuCode + ')'"
            />
          </el-select>
        </el-form-item>

        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item label="菜单编码 (Menu Code)" required>
              <el-input v-model="editForm.menuCode" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="菜单名称 (Menu Name)" required>
              <el-input v-model="editForm.menuName" />
            </el-form-item>
          </el-col>
        </el-row>

        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item label="前端路由路径 (Route Path)" required>
              <el-input v-model="editForm.routePath" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="组件路径 (Component Path)">
              <el-input v-model="editForm.componentPath" />
            </el-form-item>
          </el-col>
        </el-row>

        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item label="图标标识 (Icon)">
              <el-input v-model="editForm.icon" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="排序序号 (Sort Order)">
              <el-input-number v-model="editForm.sortOrder" :min="0" :max="999" style="width: 100%;" />
            </el-form-item>
          </el-col>
        </el-row>

        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item label="挂载权限点 (Permission Code)">
              <el-input v-model="editForm.permissionCode" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="侧边栏可见性">
              <el-switch v-model="editForm.visible" active-text="可见" inactive-text="隐藏" />
            </el-form-item>
          </el-col>
        </el-row>
      </el-form>
      <template #footer>
        <el-button @click="modals.edit.visible = false">取消</el-button>
        <el-button type="primary" :loading="modals.edit.submitting" @click="submitEditMenu">
          {{ modals.edit.submitting ? "保存中..." : "保存修改" }}
        </el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, computed, onMounted } from "vue";
import { ElMessage, ElMessageBox } from "element-plus";
import {
  getMenus,
  createMenu,
  updateMenu,
  updateMenuStatus,
  deleteMenu,
  type MenuItem,
  type CreateMenuRequest,
  type UpdateMenuRequest,
} from "../../api/admin";
import { useAuthStore } from "../../stores/auth";

const authStore = useAuthStore();

const isLoading = ref(true);
const errorMessage = ref("");
const rawMenus = ref<MenuItem[]>([]);
const searchKeyword = ref("");

// 弹窗状态
const modals = reactive({
  create: { visible: false, submitting: false },
  edit: { visible: false, submitting: false },
});

const createForm = reactive<CreateMenuRequest>({
  parentId: null,
  menuCode: "",
  menuName: "",
  routePath: "",
  componentPath: "",
  icon: "",
  sortOrder: 0,
  permissionCode: "",
  visible: true,
});

const editForm = reactive<UpdateMenuRequest & { id: string }>({
  id: "",
  parentId: null,
  menuCode: "",
  menuName: "",
  routePath: "",
  componentPath: "",
  icon: "",
  sortOrder: 0,
  permissionCode: "",
  visible: true,
});

/**
 * 将平铺或已有嵌套数据组织为树结构
 */
const menuTree = computed<MenuItem[]>(() => {
  const items = rawMenus.value;
  if (!items || items.length === 0) return [];

  const hasExistingChildren = items.some((i) => i.children && i.children.length > 0);
  if (hasExistingChildren) {
    return items;
  }

  const map = new Map<string, MenuItem>();
  for (const item of items) {
    map.set(item.id, { ...item, children: [] });
  }

  const roots: MenuItem[] = [];
  for (const item of items) {
    const node = map.get(item.id)!;
    if (item.parentId && map.has(item.parentId)) {
      const parent = map.get(item.parentId)!;
      if (!parent.children) parent.children = [];
      parent.children.push(node);
    } else {
      roots.push(node);
    }
  }

  const sortFn = (a: MenuItem, b: MenuItem) => (a.sortOrder ?? 0) - (b.sortOrder ?? 0);
  roots.sort(sortFn);
  for (const root of roots) {
    if (root.children) root.children.sort(sortFn);
  }

  return roots;
});

/**
 * 带有过滤关键字的菜单树
 */
const filteredTreeData = computed(() => {
  const kw = searchKeyword.value.trim().toLowerCase();
  if (!kw) return menuTree.value;

  function filterNodes(nodes: MenuItem[]): MenuItem[] {
    const res: MenuItem[] = [];
    for (const node of nodes) {
      const matchName = (node.menuName || "").toLowerCase().includes(kw);
      const matchCode = (node.menuCode || "").toLowerCase().includes(kw);
      const matchRoute = (node.routePath || "").toLowerCase().includes(kw);

      let filteredChildren: MenuItem[] = [];
      if (node.children && node.children.length > 0) {
        filteredChildren = filterNodes(node.children);
      }

      if (matchName || matchCode || matchRoute || filteredChildren.length > 0) {
        res.push({
          ...node,
          children: filteredChildren.length > 0 ? filteredChildren : node.children,
        });
      }
    }
    return res;
  }

  return filterNodes(menuTree.value);
});

/**
 * 平铺所有菜单选项（用于新建时选择父菜单）
 */
const flattenedSelectOptions = computed(() => {
  const result: Array<{ id: string; menuName: string; menuCode: string; prefix: string }> = [];
  function walk(nodes: MenuItem[], depth: number) {
    for (const node of nodes) {
      const prefix = depth > 0 ? "　".repeat(depth) + "└─ " : "";
      result.push({
        id: node.id,
        menuName: node.menuName,
        menuCode: node.menuCode,
        prefix,
      });
      if (node.children && node.children.length > 0) {
        walk(node.children, depth + 1);
      }
    }
  }
  walk(menuTree.value, 0);
  return result;
});

/**
 * 编辑时的父菜单选项：递归排除自身及后代节点
 */
const validParentOptionsForEdit = computed(() => {
  const excludedIds = new Set<string>();
  if (editForm.id) {
    excludedIds.add(editForm.id);
    function collectDescendants(nodes: MenuItem[]) {
      for (const node of nodes) {
        if (node.id === editForm.id) {
          addChildrenToSet(node);
        } else if (node.children && node.children.length > 0) {
          collectDescendants(node.children);
        }
      }
    }
    function addChildrenToSet(parent: MenuItem) {
      if (parent.children) {
        for (const c of parent.children) {
          excludedIds.add(c.id);
          addChildrenToSet(c);
        }
      }
    }
    collectDescendants(menuTree.value);
  }

  return flattenedSelectOptions.value.filter((opt) => !excludedIds.has(opt.id));
});

function expandAllNodes() {
  // el-table 默认根据 row-key 结合内部机制，此保留方法契约
}

function collapseAllNodes() {
  // el-table 默认契约
}

/**
 * 拉取后端菜单数据
 */
async function fetchMenuList() {
  isLoading.value = true;
  errorMessage.value = "";
  try {
    const res = await getMenus();
    if (res.data && Array.isArray(res.data)) {
      rawMenus.value = res.data;
    }
  } catch (err: any) {
    errorMessage.value = err.message || "无法拉取动态菜单树";
    ElMessage.error(`菜单列表拉取失败：${err.message}`);
  } finally {
    isLoading.value = false;
  }
}

/**
 * 切换可见性
 */
async function toggleMenuVisibility(item: MenuItem) {
  const nextVisible = item.visible === false;
  try {
    await updateMenu(item.id, {
      parentId: item.parentId || null,
      menuCode: item.menuCode,
      menuName: item.menuName,
      routePath: item.routePath,
      componentPath: item.componentPath || undefined,
      icon: item.icon || undefined,
      sortOrder: item.sortOrder ?? 0,
      permissionCode: item.permissionCode || undefined,
      visible: nextVisible,
    });
    item.visible = nextVisible;
    ElMessage.success(`菜单 ${item.menuName} 已设为 ${nextVisible ? '可见' : '隐藏'}`);
    await authStore.fetchUserMenus();
  } catch (err: any) {
    ElMessage.error(`变更可见性失败：${err.message}`);
  }
}

/**
 * 切换菜单启用状态
 */
async function toggleMenuStatus(item: MenuItem) {
  const nextStatus = item.status === "ACTIVE" ? "DISABLED" : "ACTIVE";
  try {
    const response = await updateMenuStatus(item.id, nextStatus);
    item.status = response.data?.status ?? nextStatus;
    ElMessage.success(`菜单 ${item.menuName} 已${nextStatus === 'ACTIVE' ? '启用' : '停用'}`);
    await authStore.fetchUserMenus();
  } catch (err: any) {
    ElMessage.error(`变更启用状态失败：${err.message}`);
  }
}

/**
 * 打开新建弹窗
 */
function openCreateModal(parent: MenuItem | null) {
  createForm.parentId = parent ? parent.id : null;
  createForm.menuCode = "";
  createForm.menuName = "";
  createForm.routePath = parent ? `${parent.routePath}/` : "/";
  createForm.componentPath = "";
  createForm.icon = parent ? "📄" : "📁";
  createForm.sortOrder = 1;
  createForm.permissionCode = "";
  createForm.visible = true;
  modals.create.visible = true;
}

/**
 * 提交新建菜单
 */
async function submitCreateMenu() {
  if (!createForm.menuCode.trim() || !createForm.menuName.trim() || !createForm.routePath.trim()) {
    ElMessage.warning("请完整填写菜单编码、菜单名称与路由路径");
    return;
  }

  modals.create.submitting = true;
  try {
    await createMenu({
      parentId: createForm.parentId || undefined,
      menuCode: createForm.menuCode.trim(),
      menuName: createForm.menuName.trim(),
      routePath: createForm.routePath.trim(),
      componentPath: createForm.componentPath?.trim() || undefined,
      icon: createForm.icon?.trim() || undefined,
      sortOrder: createForm.sortOrder || 0,
      permissionCode: createForm.permissionCode?.trim() || undefined,
      visible: createForm.visible !== false,
    });
    ElMessage.success(`菜单 ${createForm.menuName} 创建成功！`);
    modals.create.visible = false;
    await fetchMenuList();
    await authStore.fetchUserMenus();
  } catch (err: any) {
    ElMessage.error(`创建菜单失败：${err.message}`);
  } finally {
    modals.create.submitting = false;
  }
}

/**
 * 打开编辑弹窗
 */
function openEditModal(item: MenuItem) {
  editForm.id = item.id;
  editForm.parentId = item.parentId || null;
  editForm.menuCode = item.menuCode;
  editForm.menuName = item.menuName;
  editForm.routePath = item.routePath;
  editForm.componentPath = item.componentPath || "";
  editForm.icon = item.icon || "";
  editForm.sortOrder = item.sortOrder ?? 0;
  editForm.permissionCode = item.permissionCode || "";
  editForm.visible = item.visible !== false;
  modals.edit.visible = true;
}

/**
 * 提交编辑菜单
 */
async function submitEditMenu() {
  if (!editForm.menuCode?.trim() || !editForm.menuName?.trim() || !editForm.routePath?.trim()) {
    ElMessage.warning("请填写菜单编码、名称和路由路径");
    return;
  }

  modals.edit.submitting = true;
  try {
    await updateMenu(editForm.id, {
      parentId: editForm.parentId || null,
      menuCode: editForm.menuCode.trim(),
      menuName: editForm.menuName.trim(),
      routePath: editForm.routePath.trim(),
      componentPath: editForm.componentPath?.trim() || undefined,
      icon: editForm.icon?.trim() || undefined,
      sortOrder: editForm.sortOrder ?? 0,
      permissionCode: editForm.permissionCode?.trim() || undefined,
      visible: editForm.visible !== false,
    });
    ElMessage.success(`菜单 ${editForm.menuName} 保存成功`);
    modals.edit.visible = false;
    await fetchMenuList();
    await authStore.fetchUserMenus();
  } catch (err: any) {
    ElMessage.error(`更新菜单失败：${err.message}`);
  } finally {
    modals.edit.submitting = false;
  }
}

/**
 * 删除菜单
 */
async function handleDeleteMenu(item: MenuItem) {
  try {
    await ElMessageBox.confirm(
      `确定要删除菜单 [${item.menuName} (${item.menuCode})] 吗？若存在子菜单将无法删除。`,
      "删除确认",
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
    await deleteMenu(item.id);
    ElMessage.success(`菜单 ${item.menuName} 已成功删除`);
    await fetchMenuList();
    await authStore.fetchUserMenus();
  } catch (err: any) {
    ElMessage.error(`删除失败：${err.message || "存在子菜单或角色引用关联冲突 (409)"}`);
  }
}

onMounted(() => {
  fetchMenuList();
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

.mr-2 {
  margin-right: 8px;
}

.mb-4 {
  margin-bottom: 16px;
}

.text-cyan {
  color: var(--el-color-primary);
}

.text-muted {
  color: #8ca2b8;
}

.font-mono {
  font-family: var(--font-mono, monospace);
}
</style>
