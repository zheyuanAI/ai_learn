<template>
  <div class="system-view-container">
    <!-- 头部说明 -->
    <header class="page-header">
      <div class="header-meta">
        <span class="meta-tag">AUTH / MENU MANAGEMENT</span>
        <span class="meta-count-badge">动态菜单树 (层级组织)</span>
      </div>
      <div class="header-main-row">
        <div>
          <h1 class="page-title">系统动态菜单管理</h1>
          <p class="page-desc">
            维护系统左侧导航栏的菜单节点树、路由路径、前端组件映射与权限点挂载。修改后即时联动侧边栏。
          </p>
        </div>
        <div class="header-action-group">
          <button type="button" class="btn-create" @click="openCreateModal(null)">
            <span class="btn-icon">＋</span>
            <span>新建顶级菜单</span>
          </button>
        </div>
      </div>
    </header>

    <!-- 工具栏 -->
    <section class="filter-panel">
      <div class="filter-row">
        <div class="filter-item">
          <label>关键字过滤</label>
          <input
            v-model="searchKeyword"
            type="text"
            class="filter-input"
            placeholder="搜索菜单名称、编码或路由..."
          />
        </div>

        <div class="filter-actions-inline">
          <button type="button" class="btn-expand-all" @click="expandAllNodes">
            <span>展开全部</span>
          </button>
          <button type="button" class="btn-collapse-all" @click="collapseAllNodes">
            <span>折叠全部</span>
          </button>
          <button type="button" class="btn-refresh" :disabled="isLoading" @click="fetchMenuList">
            <span>🔄 刷新</span>
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
      <button type="button" class="btn-retry" @click="fetchMenuList">重试</button>
    </div>

    <!-- 菜单树表格 (Tree Table) -->
    <section class="table-wrapper">
      <div v-if="isLoading" class="table-loading-mask">
        <div class="spinner"></div>
        <span>正在加载动态菜单树...</span>
      </div>

      <table class="custom-table tree-table">
        <thead>
          <tr>
            <th style="width: 260px;">菜单名称 (层级树)</th>
            <th style="width: 140px;">菜单编码</th>
            <th style="width: 170px;">前端路由路径</th>
            <th style="width: 190px;">视图组件路径</th>
            <th style="width: 80px; text-align: center;">排序</th>
            <th style="width: 170px;">关联权限点</th>
            <th style="width: 80px; text-align: center;">可见性</th>
            <th style="width: 100px; text-align: center;">启用状态</th>
            <th style="width: 220px; text-align: center;">操作</th>
          </tr>
        </thead>
        <tbody>
          <tr v-if="!isLoading && flattenedTreeRows.length === 0">
            <td colspan="9" class="empty-cell">
              <div class="empty-state">
                <span class="empty-icon">📂</span>
                <p>未找到符合条件的菜单节点</p>
              </div>
            </td>
          </tr>

          <tr
            v-for="row in flattenedTreeRows"
            :key="row.item.id"
            class="tree-row-item"
            :class="[`depth-level-${row.depth}`]"
          >
            <!-- 菜单名称与树形缩进 -->
            <td>
              <div class="tree-node-cell" :style="{ paddingLeft: `${row.depth * 22}px` }">
                <!-- 展开/折叠小图标 -->
                <button
                  v-if="row.hasChildren"
                  type="button"
                  class="tree-expand-btn"
                  @click="toggleNodeExpand(row.item.id)"
                >
                  <span :class="{ 'is-expanded': expandedNodeIds.has(row.item.id) }">▶</span>
                </button>
                <span v-else class="tree-leaf-spacer"></span>

                <!-- 菜单图标 -->
                <span class="menu-icon-symbol">{{ row.item.icon || (row.depth === 0 ? '📁' : '📄') }}</span>

                <!-- 菜单名称 -->
                <strong class="menu-name-text">{{ row.item.menuName }}</strong>
              </div>
            </td>

            <!-- 菜单编码 -->
            <td>
              <span class="font-mono highlight-cyan code-tag">{{ row.item.menuCode }}</span>
            </td>

            <!-- 路由路径 -->
            <td>
              <span class="font-mono route-text">{{ row.item.routePath }}</span>
            </td>

            <!-- 组件路径 -->
            <td>
              <span class="font-mono text-muted comp-text">{{ row.item.componentPath || "-" }}</span>
            </td>

            <!-- 排序 -->
            <td style="text-align: center;">
              <span class="font-mono sort-badge">{{ row.item.sortOrder ?? 0 }}</span>
            </td>

            <!-- 关联权限点 -->
            <td>
              <span v-if="row.item.permissionCode" class="font-mono perm-badge">
                🔑 {{ row.item.permissionCode }}
              </span>
              <span v-else class="text-muted no-perm">公共直达</span>
            </td>

            <!-- 可见性 Switch -->
            <td style="text-align: center;">
              <button
                type="button"
                class="switch-btn"
                :class="{ 'is-active': row.item.visible !== false }"
                :title="row.item.visible !== false ? '点击隐藏' : '点击展示'"
                @click="toggleMenuVisibility(row.item)"
              >
                <span class="switch-handle"></span>
              </button>
            </td>

            <!-- 启停状态：与导航显隐分别维护 -->
            <td style="text-align: center;">
              <button
                type="button"
                class="status-pill"
                :class="row.item.status === 'ACTIVE' ? 'is-active' : 'is-disabled'"
                :title="row.item.status === 'ACTIVE' ? '点击停用' : '点击启用'"
                @click="toggleMenuStatus(row.item)"
              >
                {{ row.item.status === 'ACTIVE' ? '启用' : '停用' }}
              </button>
            </td>

            <!-- 操作列 -->
            <td>
              <div class="action-btn-group">
                <button
                  type="button"
                  class="action-btn btn-add-child"
                  title="在此节点下添加子菜单"
                  @click="openCreateModal(row.item)"
                >
                  ＋ 子菜单
                </button>
                <button
                  type="button"
                  class="action-btn btn-edit"
                  title="编辑菜单属性"
                  @click="openEditModal(row.item)"
                >
                  编辑
                </button>
                <button
                  type="button"
                  class="action-btn btn-del"
                  title="删除菜单节点"
                  @click="handleDeleteMenu(row.item)"
                >
                  删除
                </button>
              </div>
            </td>
          </tr>
        </tbody>
      </table>
    </section>

    <!-- 弹窗 1：新建菜单 (顶级或子菜单) -->
    <div v-if="modals.create.visible" class="modal-overlay" @click.self="modals.create.visible = false">
      <div class="modal-card">
        <div class="modal-head">
          <h3 class="modal-title">{{ createForm.parentId ? "添加子菜单" : "新建顶级菜单" }}</h3>
          <button type="button" class="modal-close" @click="modals.create.visible = false">✕</button>
        </div>

        <form class="modal-body" @submit.prevent="submitCreateMenu">
          <div class="form-item">
            <label>上级父菜单 (Parent Menu)</label>
            <select v-model="createForm.parentId" class="form-select">
              <option :value="null">【无】顶级根菜单</option>
              <option v-for="m in flattenedSelectOptions" :key="m.id" :value="m.id">
                {{ m.prefix }}{{ m.menuName }} ({{ m.menuCode }})
              </option>
            </select>
          </div>

          <div class="form-row two-col">
            <div class="form-item">
              <label>菜单编码 (Menu Code) <b class="req-star">*</b></label>
              <input
                v-model="createForm.menuCode"
                type="text"
                class="form-input"
                placeholder="例如 SystemUsers"
                required
              />
            </div>
            <div class="form-item">
              <label>菜单名称 (Menu Name) <b class="req-star">*</b></label>
              <input
                v-model="createForm.menuName"
                type="text"
                class="form-input"
                placeholder="例如 用户管理"
                required
              />
            </div>
          </div>

          <div class="form-row two-col">
            <div class="form-item">
              <label>前端路由路径 (Route Path) <b class="req-star">*</b></label>
              <input
                v-model="createForm.routePath"
                type="text"
                class="form-input"
                placeholder="例如 /system/users"
                required
              />
            </div>
            <div class="form-item">
              <label>组件路径 (Component Path)</label>
              <input
                v-model="createForm.componentPath"
                type="text"
                class="form-input"
                placeholder="例如 views/system/UserList.vue"
              />
            </div>
          </div>

          <div class="form-row two-col">
            <div class="form-item">
              <label>图标标识 (Icon)</label>
              <input
                v-model="createForm.icon"
                type="text"
                class="form-input"
                placeholder="例如 👥 或 icon-user"
              />
            </div>
            <div class="form-item">
              <label>同级排序序号 (Sort Order)</label>
              <input
                v-model.number="createForm.sortOrder"
                type="number"
                class="form-input"
                placeholder="数值越小越靠前"
              />
            </div>
          </div>

          <div class="form-row two-col">
            <div class="form-item">
              <label>挂载功能权限点 (Permission Code)</label>
              <input
                v-model="createForm.permissionCode"
                type="text"
                class="form-input"
                placeholder="例如 system:user:view"
              />
            </div>
            <div class="form-item">
              <label>侧边栏是否可见</label>
              <select v-model="createForm.visible" class="form-select">
                <option :value="true">可见 (Visible)</option>
                <option :value="false">隐藏 (Hidden)</option>
              </select>
            </div>
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

    <!-- 弹窗 2：编辑菜单 (排除自身与后代子节点) -->
    <div v-if="modals.edit.visible" class="modal-overlay" @click.self="modals.edit.visible = false">
      <div class="modal-card">
        <div class="modal-head">
          <h3 class="modal-title">编辑菜单节点 ({{ editForm.menuName }})</h3>
          <button type="button" class="modal-close" @click="modals.edit.visible = false">✕</button>
        </div>

        <form class="modal-body" @submit.prevent="submitEditMenu">
          <div class="form-item">
            <label>上级父菜单 (自动排除自身及后代)</label>
            <select v-model="editForm.parentId" class="form-select">
              <option :value="null">【无】顶级根菜单</option>
              <option
                v-for="m in validParentOptionsForEdit"
                :key="m.id"
                :value="m.id"
              >
                {{ m.prefix }}{{ m.menuName }} ({{ m.menuCode }})
              </option>
            </select>
            <span class="field-hint">已自动过滤当前节点及其所有子菜单，防止形成循环嵌套死锁。</span>
          </div>

          <div class="form-row two-col">
            <div class="form-item">
              <label>菜单编码 (Menu Code) <b class="req-star">*</b></label>
              <input
                v-model="editForm.menuCode"
                type="text"
                class="form-input"
                required
              />
            </div>
            <div class="form-item">
              <label>菜单名称 (Menu Name) <b class="req-star">*</b></label>
              <input
                v-model="editForm.menuName"
                type="text"
                class="form-input"
                required
              />
            </div>
          </div>

          <div class="form-row two-col">
            <div class="form-item">
              <label>前端路由路径 (Route Path) <b class="req-star">*</b></label>
              <input
                v-model="editForm.routePath"
                type="text"
                class="form-input"
                required
              />
            </div>
            <div class="form-item">
              <label>组件路径 (Component Path)</label>
              <input
                v-model="editForm.componentPath"
                type="text"
                class="form-input"
              />
            </div>
          </div>

          <div class="form-row two-col">
            <div class="form-item">
              <label>图标标识 (Icon)</label>
              <input
                v-model="editForm.icon"
                type="text"
                class="form-input"
              />
            </div>
            <div class="form-item">
              <label>排序序号 (Sort Order)</label>
              <input
                v-model.number="editForm.sortOrder"
                type="number"
                class="form-input"
              />
            </div>
          </div>

          <div class="form-row two-col">
            <div class="form-item">
              <label>挂载权限点 (Permission Code)</label>
              <input
                v-model="editForm.permissionCode"
                type="text"
                class="form-input"
              />
            </div>
            <div class="form-item">
              <label>侧边栏是否可见</label>
              <select v-model="editForm.visible" class="form-select">
                <option :value="true">可见 (Visible)</option>
                <option :value="false">隐藏 (Hidden)</option>
              </select>
            </div>
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

// 记录所有展开的节点 ID
const expandedNodeIds = ref<Set<string>>(new Set());

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

  // 判断是否已经是嵌套树（第一项已有 children）
  const hasExistingChildren = items.some((i) => i.children && i.children.length > 0);
  if (hasExistingChildren) {
    return items;
  }

  // 组装平铺列表为树
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

  // 排序
  const sortFn = (a: MenuItem, b: MenuItem) => (a.sortOrder ?? 0) - (b.sortOrder ?? 0);
  roots.sort(sortFn);
  for (const root of roots) {
    if (root.children) root.children.sort(sortFn);
  }

  return roots;
});

/**
 * 将菜单树递归平铺为表格渲染所需的行对象（带深度与展开状态控制）
 */
interface FlattenedRow {
  item: MenuItem;
  depth: number;
  hasChildren: boolean;
}

const flattenedTreeRows = computed<FlattenedRow[]>(() => {
  const result: FlattenedRow[] = [];
  const kw = searchKeyword.value.trim().toLowerCase();

  function traverse(nodes: MenuItem[], depth: number) {
    for (const node of nodes) {
      const hasChildren = !!(node.children && node.children.length > 0);
      const isExpanded = expandedNodeIds.value.has(node.id) || !!kw; // 搜索时自动全展开

      // 如果有关键字，过滤判断
      let matches = true;
      if (kw) {
        const matchName = node.menuName.toLowerCase().includes(kw);
        const matchCode = node.menuCode.toLowerCase().includes(kw);
        const matchRoute = node.routePath.toLowerCase().includes(kw);
        const matchChild = hasChildren && node.children!.some((c) =>
          c.menuName.toLowerCase().includes(kw) ||
          c.menuCode.toLowerCase().includes(kw) ||
          c.routePath.toLowerCase().includes(kw)
        );
        matches = matchName || matchCode || matchRoute || matchChild;
      }

      if (matches) {
        result.push({ item: node, depth, hasChildren });
        if (hasChildren && (isExpanded || kw)) {
          traverse(node.children!, depth + 1);
        }
      }
    }
  }

  traverse(menuTree.value, 0);
  return result;
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
    // 寻找后代节点
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

/**
 * 展开/折叠单节点
 */
function toggleNodeExpand(id: string) {
  if (expandedNodeIds.value.has(id)) {
    expandedNodeIds.value.delete(id);
  } else {
    expandedNodeIds.value.add(id);
  }
}

function expandAllNodes() {
  function collect(nodes: MenuItem[]) {
    for (const node of nodes) {
      expandedNodeIds.value.add(node.id);
      if (node.children) collect(node.children);
    }
  }
  collect(menuTree.value);
}

function collapseAllNodes() {
  expandedNodeIds.value.clear();
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
      // 默认展开顶层节点
      expandAllNodes();
    }
  } catch (err: any) {
    errorMessage.value = err.message || "无法拉取动态菜单树";
    showToast(`菜单列表拉取失败：${err.message}`, "danger");
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
    showToast(`菜单 ${item.menuName} 已设为 ${nextVisible ? '可见' : '隐藏'}`, "success");

    // 联动刷新全局侧边栏
    await authStore.fetchUserMenus();
  } catch (err: any) {
    showToast(`变更可见性失败：${err.message}`, "danger");
  }
}

/**
 * 切换菜单启用状态；请求只提交 status，避免将 visible 冒充为启停状态。
 */
async function toggleMenuStatus(item: MenuItem) {
  const nextStatus = item.status === "ACTIVE" ? "DISABLED" : "ACTIVE";
  try {
    const response = await updateMenuStatus(item.id, nextStatus);
    item.status = response.data?.status ?? nextStatus;
    showToast(`菜单 ${item.menuName} 已${nextStatus === 'ACTIVE' ? '启用' : '停用'}`, "success");
    await authStore.fetchUserMenus();
  } catch (err: any) {
    showToast(`变更启用状态失败：${err.message}`, "danger");
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
    showToast("请完整填写菜单编码、菜单名称与路由路径", "warning");
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
    showToast(`菜单 ${createForm.menuName} 创建成功！`, "success");
    modals.create.visible = false;
    await fetchMenuList();

    // 联动刷新用户侧边栏
    await authStore.fetchUserMenus();
  } catch (err: any) {
    showToast(`创建菜单失败：${err.message}`, "danger");
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
    showToast("请填写菜单编码、名称和路由路径", "warning");
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
    showToast(`菜单 ${editForm.menuName} 保存成功`, "success");
    modals.edit.visible = false;
    await fetchMenuList();

    // 联动刷新用户侧边栏
    await authStore.fetchUserMenus();
  } catch (err: any) {
    showToast(`更新菜单失败：${err.message}`, "danger");
  } finally {
    modals.edit.submitting = false;
  }
}

/**
 * 删除菜单（处理 409 冲突友好提示）
 */
async function handleDeleteMenu(item: MenuItem) {
  const confirmed = window.confirm(`确定要删除菜单 [${item.menuName} (${item.menuCode})] 吗？若存在子菜单将无法删除。`);
  if (!confirmed) return;

  try {
    await deleteMenu(item.id);
    showToast(`菜单 ${item.menuName} 已成功删除`, "success");
    await fetchMenuList();

    // 联动刷新侧边栏
    await authStore.fetchUserMenus();
  } catch (err: any) {
    showToast(`删除失败：${err.message || "存在子菜单或角色引用关联冲突 (409)"}`, "danger");
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

.filter-input {
  min-width: 260px;
  padding: 7px 10px;
  border-radius: 6px;
  background: rgba(255, 255, 255, 0.04);
  border: 1px solid rgba(124, 162, 194, 0.22);
  color: #fff;
  font-size: 12px;
  outline: none;
}

.filter-actions-inline {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-left: auto;
}

.btn-expand-all,
.btn-collapse-all,
.btn-refresh {
  padding: 6px 12px;
  border-radius: 6px;
  background: rgba(255, 255, 255, 0.04);
  border: 1px solid rgba(124, 162, 194, 0.2);
  color: #c9e8fa;
  font-size: 11px;
  cursor: pointer;
  transition: all 0.15s ease;
}

.btn-expand-all:hover,
.btn-collapse-all:hover {
  background: rgba(255, 255, 255, 0.08);
  color: #fff;
}

.btn-refresh {
  background: rgba(103, 210, 255, 0.12);
  border-color: rgba(103, 210, 255, 0.3);
  color: var(--accent, #67d2ff);
}
.btn-refresh:hover:not(:disabled) {
  background: rgba(103, 210, 255, 0.25);
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
  padding: 10px 14px;
  border-bottom: 1px solid rgba(124, 162, 194, 0.08);
  color: var(--text, #ebf3fb);
  vertical-align: middle;
}

.custom-table tbody tr:hover {
  background: rgba(103, 210, 255, 0.04);
}

.tree-node-cell {
  display: flex;
  align-items: center;
  gap: 8px;
}

.tree-expand-btn {
  width: 18px;
  height: 18px;
  display: flex;
  align-items: center;
  justify-content: center;
  border-radius: 4px;
  background: rgba(255, 255, 255, 0.06);
  border: 1px solid rgba(124, 162, 194, 0.2);
  color: #a0c4e2;
  font-size: 9px;
  cursor: pointer;
  padding: 0;
  transition: all 0.15s ease;
}

.tree-expand-btn:hover {
  background: rgba(103, 210, 255, 0.2);
  color: #fff;
}

.tree-expand-btn span {
  display: inline-block;
  transition: transform 0.2s ease;
}

.tree-expand-btn span.is-expanded {
  transform: rotate(90deg);
}

.tree-leaf-spacer {
  width: 18px;
  height: 18px;
}

.menu-icon-symbol {
  font-size: 14px;
}

.menu-name-text {
  color: #fff;
  font-size: 13px;
}

.depth-level-1 {
  background: rgba(255, 255, 255, 0.01);
}

.depth-level-2 {
  background: rgba(255, 255, 255, 0.02);
}

.code-tag {
  font-size: 11px;
}

.route-text {
  font-size: 12px;
  color: #a7d9f7;
}

.comp-text {
  font-size: 11px;
}

.sort-badge {
  font-size: 11px;
  background: rgba(255, 255, 255, 0.04);
  padding: 2px 6px;
  border-radius: 4px;
  color: #d2e4f5;
}

.perm-badge {
  font-size: 11px;
  color: #ffb25e;
  background: rgba(255, 178, 94, 0.08);
  padding: 2px 6px;
  border-radius: 4px;
  border: 1px solid rgba(255, 178, 94, 0.2);
}

.no-perm {
  font-size: 11px;
  font-style: italic;
}

.font-mono {
  font-family: monospace;
}

.highlight-cyan {
  color: var(--accent, #67d2ff);
}

.text-muted {
  color: var(--muted, #8ca2b8);
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

.status-pill {
  min-width: 48px;
  padding: 3px 8px;
  border-radius: 999px;
  border: 1px solid transparent;
  font-size: 11px;
  cursor: pointer;
}

.status-pill.is-active {
  color: #b9f7d0;
  background: rgba(53, 190, 111, 0.16);
  border-color: rgba(53, 190, 111, 0.42);
}

.status-pill.is-disabled {
  color: #ffd0d0;
  background: rgba(217, 84, 84, 0.16);
  border-color: rgba(217, 84, 84, 0.42);
}

/* 操作按钮 */
.action-btn-group {
  display: flex;
  justify-content: center;
  align-items: center;
  gap: 6px;
}

.action-btn {
  padding: 3px 8px;
  border-radius: 4px;
  font-size: 11px;
  cursor: pointer;
  transition: all 0.15s ease;
  border: 1px solid transparent;
}

.btn-add-child {
  background: rgba(103, 210, 255, 0.12);
  border-color: rgba(103, 210, 255, 0.3);
  color: var(--accent, #67d2ff);
  font-weight: 600;
}
.btn-add-child:hover {
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
.btn-del:hover {
  background: rgba(255, 125, 125, 0.25);
}

/* 弹窗样式 */
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
  max-width: 620px;
  background: #0e1723;
  border: 1px solid rgba(103, 210, 255, 0.3);
  border-radius: 16px;
  box-shadow: 0 24px 60px rgba(0, 0, 0, 0.6);
  display: flex;
  flex-direction: column;
  overflow: hidden;
  animation: modalIn 0.2s ease;
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
  gap: 14px;
  max-height: calc(85vh - 120px);
  overflow-y: auto;
}

.form-row.two-col {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 12px;
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
.form-select {
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
.form-select:focus {
  border-color: var(--accent, #67d2ff);
}

.form-select option {
  background: #0d1723;
}

.field-hint {
  font-size: 11px;
  color: var(--muted, #8ca2b8);
  line-height: 1.4;
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

/* 错误条与 Toast */
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
