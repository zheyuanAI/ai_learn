<template>
  <div class="system-view-container">
    <!-- 头部说明 -->
    <header class="page-header">
      <div class="header-meta">
        <span class="meta-tag">AUTH / USER MANAGEMENT</span>
        <span class="meta-count-badge">总计 {{ totalCount }} 位操作用户</span>
      </div>
      <div class="header-main-row">
        <div>
          <h1 class="page-title">租户操作用户管理</h1>
          <p class="page-desc">
            维护当前租户下的工号、登录账号、资料与角色授权。禁止停用或删除自身账号及最后管理员。
          </p>
        </div>
        <button type="button" class="btn-create" @click="openCreateModal">
          <span class="btn-icon">＋</span>
          <span>新建用户</span>
        </button>
      </div>
    </header>

    <!-- 筛选搜索栏 -->
    <section class="filter-panel">
      <div class="filter-grid">
        <div class="filter-item">
          <label>工号 (User No)</label>
          <input
            v-model="queryParams.userNo"
            type="text"
            class="filter-input"
            placeholder="搜索工号..."
            @keyup.enter="handleSearch"
          />
        </div>

        <div class="filter-item">
          <label>登录用户名 (Username)</label>
          <input
            v-model="queryParams.username"
            type="text"
            class="filter-input"
            placeholder="搜索用户名..."
            @keyup.enter="handleSearch"
          />
        </div>

        <div class="filter-item">
          <label>真实姓名 (Real Name)</label>
          <input
            v-model="queryParams.realName"
            type="text"
            class="filter-input"
            placeholder="搜索真实姓名..."
            @keyup.enter="handleSearch"
          />
        </div>

        <div class="filter-item">
          <label>账号状态</label>
          <select v-model="queryParams.status" class="filter-select" @change="handleSearch">
            <option value="">全部状态</option>
            <option value="ACTIVE">正常 (ACTIVE)</option>
            <option value="DISABLED">已禁用 (DISABLED)</option>
            <option value="LOCKED">已锁定 (LOCKED)</option>
          </select>
        </div>

        <div class="filter-item">
          <label>所属角色</label>
          <select v-model="queryParams.roleId" class="filter-select" @change="handleSearch">
            <option value="">全部角色</option>
            <option v-for="role in allRoles" :key="role.id" :value="role.id">
              {{ role.roleName }} ({{ role.roleCode }})
            </option>
          </select>
        </div>
      </div>

      <div class="filter-actions">
        <button type="button" class="btn-search" :disabled="isLoading" @click="handleSearch">
          <span>🔍 查询</span>
        </button>
        <button type="button" class="btn-reset" :disabled="isLoading" @click="handleResetQuery">
          <span>重置</span>
        </button>
      </div>
    </section>

    <!-- 错误警告 -->
    <div v-if="errorMessage" class="error-banner">
      <span class="error-icon">⚠️</span>
      <div class="error-text">
        <strong>接口提示：</strong>
        <span>{{ errorMessage }}</span>
      </div>
      <button type="button" class="btn-retry" @click="fetchUserList">重试</button>
    </div>

    <!-- 用户列表表格 -->
    <section class="table-wrapper">
      <div v-if="isLoading" class="table-loading-mask">
        <div class="spinner"></div>
        <span>正在加载用户数据...</span>
      </div>

      <table class="custom-table">
        <thead>
          <tr>
            <th style="width: 100px;">工号</th>
            <th style="width: 130px;">登录用户名</th>
            <th style="width: 110px;">真实姓名</th>
            <th style="width: 130px;">联系电话</th>
            <th style="width: 170px;">电子邮箱</th>
            <th>已分配角色</th>
            <th style="width: 90px; text-align: center;">状态</th>
            <th style="width: 140px;">创建时间</th>
            <th style="width: 220px; text-align: center;">操作</th>
          </tr>
        </thead>
        <tbody>
          <tr v-if="!isLoading && userList.length === 0">
            <td colspan="9" class="empty-cell">
              <div class="empty-state">
                <span class="empty-icon">📂</span>
                <p>未找到符合条件的用户数据</p>
              </div>
            </td>
          </tr>

          <tr
            v-for="user in userList"
            :key="user.id"
            :class="{ 'highlight-self-row': isCurrentUser(user) }"
          >
            <!-- 工号 -->
            <td>
              <span class="font-mono highlight-cyan">{{ user.userNo || "-" }}</span>
            </td>

            <!-- 用户名 -->
            <td>
              <div class="username-cell">
                <span class="username-text font-mono">{{ user.username }}</span>
                <span v-if="isCurrentUser(user)" class="self-tag">当前账号</span>
              </div>
            </td>

            <!-- 姓名 -->
            <td>
              <span class="user-realname">{{ user.realName }}</span>
            </td>

            <!-- 电话 -->
            <td>
              <span class="font-mono text-muted">{{ user.phone || "-" }}</span>
            </td>

            <!-- 邮箱 -->
            <td>
              <span class="text-muted">{{ user.email || "-" }}</span>
            </td>

            <!-- 分配角色标签 -->
            <td>
              <div class="role-tags-wrap">
                <template v-if="formatUserRoles(user).length > 0">
                  <span
                    v-for="r in formatUserRoles(user)"
                    :key="r"
                    class="role-badge"
                  >
                    {{ r }}
                  </span>
                </template>
                <span v-else class="text-muted no-role">未分配角色</span>
              </div>
            </td>

            <!-- 状态 Switch / Badge -->
            <td style="text-align: center;">
              <button
                type="button"
                class="switch-btn"
                :class="{ 'is-active': user.status === 'ACTIVE', 'is-disabled-click': isCurrentUser(user) }"
                :title="isCurrentUser(user) ? '无法停用当前登录账号' : (user.status === 'ACTIVE' ? '点击禁用' : '点击启用')"
                @click="toggleUserStatus(user)"
              >
                <span class="switch-handle"></span>
              </button>
            </td>

            <!-- 创建时间 -->
            <td>
              <span class="font-mono text-muted date-text">{{ user.createdAt || "-" }}</span>
            </td>

            <!-- 操作 -->
            <td>
              <div class="action-btn-group">
                <button
                  type="button"
                  class="action-btn btn-edit"
                  title="编辑用户基本资料"
                  @click="openEditModal(user)"
                >
                  编辑
                </button>
                <button
                  type="button"
                  class="action-btn btn-role"
                  title="为用户分配业务角色"
                  @click="openRoleAssignModal(user)"
                >
                  角色
                </button>
                <button
                  type="button"
                  class="action-btn btn-pwd"
                  title="重置登录密码"
                  @click="openResetPwdModal(user)"
                >
                  密码
                </button>
                <button
                  type="button"
                  class="action-btn btn-del"
                  :disabled="isCurrentUser(user)"
                  :title="isCurrentUser(user) ? '无法删除当前登录账号' : '删除用户'"
                  @click="handleDeleteUser(user)"
                >
                  删除
                </button>
              </div>
            </td>
          </tr>
        </tbody>
      </table>

      <!-- 分页控制器 -->
      <footer class="pagination-footer">
        <div class="page-size-selector">
          <span>每页展示</span>
          <select v-model="queryParams.size" class="page-select" @change="handlePageSizeChange">
            <option :value="10">10 条</option>
            <option :value="20">20 条</option>
            <option :value="50">50 条</option>
          </select>
        </div>

        <div class="page-pager">
          <span class="page-info">第 {{ queryParams.page ?? 1 }} 页 / 共 {{ totalPages }} 页</span>
          <button
            type="button"
            class="pager-btn"
            :disabled="(queryParams.page ?? 1) <= 1 || isLoading"
            @click="goToPage((queryParams.page ?? 1) - 1)"
          >
            ‹ 上一页
          </button>
          <button
            type="button"
            class="pager-btn"
            :disabled="(queryParams.page ?? 1) >= totalPages || isLoading"
            @click="goToPage((queryParams.page ?? 1) + 1)"
          >
            下一页 ›
          </button>
        </div>
      </footer>
    </section>

    <!-- 弹窗 1：新建用户 -->
    <div v-if="modals.create.visible" class="modal-overlay" @click.self="modals.create.visible = false">
      <div class="modal-card">
        <div class="modal-head">
          <h3 class="modal-title">新建操作用户</h3>
          <button type="button" class="modal-close" @click="modals.create.visible = false">✕</button>
        </div>

        <form class="modal-body" @submit.prevent="submitCreateUser">
          <div class="form-row two-col">
            <div class="form-item">
              <label>工号 (User No) <b class="req-star">*</b></label>
              <input
                v-model="createForm.userNo"
                type="text"
                class="form-input"
                placeholder="例如 NO8001"
                required
              />
            </div>
            <div class="form-item">
              <label>用户名 (Username) <b class="req-star">*</b></label>
              <input
                v-model="createForm.username"
                type="text"
                class="form-input"
                placeholder="例如 operator.wang"
                required
              />
            </div>
          </div>

          <div class="form-row two-col">
            <div class="form-item">
              <label>初始登录密码 <b class="req-star">*</b></label>
              <input
                v-model="createForm.password"
                type="password"
                class="form-input"
                placeholder="至少 6 位字符"
                minlength="6"
                required
              />
            </div>
            <div class="form-item">
              <label>真实姓名 <b class="req-star">*</b></label>
              <input
                v-model="createForm.realName"
                type="text"
                class="form-input"
                placeholder="例如 王小华"
                required
              />
            </div>
          </div>

          <div class="form-row two-col">
            <div class="form-item">
              <label>电子邮箱</label>
              <input
                v-model="createForm.email"
                type="email"
                class="form-input"
                placeholder="user@factory.com"
              />
            </div>
            <div class="form-item">
              <label>联系手机号</label>
              <input
                v-model="createForm.phone"
                type="tel"
                class="form-input"
                placeholder="13800000000"
              />
            </div>
          </div>

          <div class="form-item">
            <label>初始分配角色</label>
            <div class="role-checkbox-group">
              <label
                v-for="role in allRoles"
                :key="role.id"
                class="checkbox-item"
              >
                <input
                  v-model="createForm.roleIds"
                  type="checkbox"
                  :value="role.id"
                />
                <span class="checkbox-label">{{ role.roleName }} ({{ role.roleCode }})</span>
              </label>
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

    <!-- 弹窗 2：编辑用户资料 -->
    <div v-if="modals.edit.visible" class="modal-overlay" @click.self="modals.edit.visible = false">
      <div class="modal-card">
        <div class="modal-head">
          <h3 class="modal-title">编辑用户资料 ({{ editForm.username }})</h3>
          <button type="button" class="modal-close" @click="modals.edit.visible = false">✕</button>
        </div>

        <form class="modal-body" @submit.prevent="submitEditUser">
          <div class="form-row two-col">
            <div class="form-item">
              <label>工号 (User No)</label>
              <input
                v-model="editForm.userNo"
                type="text"
                class="form-input"
                placeholder="工号"
              />
            </div>
            <div class="form-item">
              <label>真实姓名 <b class="req-star">*</b></label>
              <input
                v-model="editForm.realName"
                type="text"
                class="form-input"
                required
              />
            </div>
          </div>

          <div class="form-row two-col">
            <div class="form-item">
              <label>电子邮箱</label>
              <input
                v-model="editForm.email"
                type="email"
                class="form-input"
                placeholder="user@factory.com"
              />
            </div>
            <div class="form-item">
              <label>联系手机号</label>
              <input
                v-model="editForm.phone"
                type="tel"
                class="form-input"
                placeholder="13800000000"
              />
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

    <!-- 弹窗 3：分配角色 -->
    <div v-if="modals.assignRole.visible" class="modal-overlay" @click.self="modals.assignRole.visible = false">
      <div class="modal-card">
        <div class="modal-head">
          <h3 class="modal-title">分配业务角色 — {{ assignRoleForm.realName }} ({{ assignRoleForm.username }})</h3>
          <button type="button" class="modal-close" @click="modals.assignRole.visible = false">✕</button>
        </div>

        <form class="modal-body" @submit.prevent="submitAssignRoles">
          <p class="modal-hint">选择该用户在当前租户下所担任的业务角色（支持多选）：</p>

          <div class="role-checkbox-group vertical">
            <label
              v-for="role in allRoles"
              :key="role.id"
              class="checkbox-card"
              :class="{ 'is-checked': assignRoleForm.selectedRoleIds.includes(role.id) }"
            >
              <input
                v-model="assignRoleForm.selectedRoleIds"
                type="checkbox"
                :value="role.id"
              />
              <div class="checkbox-card-info">
                <div class="role-name-row">
                  <strong>{{ role.roleName }}</strong>
                  <span class="role-code-tag font-mono">{{ role.roleCode }}</span>
                </div>
                <p class="role-desc-text">{{ role.description || "暂无描述" }}</p>
              </div>
            </label>
          </div>

          <div class="modal-foot">
            <button type="button" class="btn-cancel" @click="modals.assignRole.visible = false">取消</button>
            <button type="submit" class="btn-submit" :disabled="modals.assignRole.submitting">
              {{ modals.assignRole.submitting ? "保存中..." : "保存角色分配" }}
            </button>
          </div>
        </form>
      </div>
    </div>

    <!-- 弹窗 4：重置密码 -->
    <div v-if="modals.resetPwd.visible" class="modal-overlay" @click.self="modals.resetPwd.visible = false">
      <div class="modal-card modal-small">
        <div class="modal-head">
          <h3 class="modal-title">重置用户密码</h3>
          <button type="button" class="modal-close" @click="modals.resetPwd.visible = false">✕</button>
        </div>

        <form class="modal-body" @submit.prevent="submitResetPwd">
          <p class="modal-hint">
            正在重置 <strong>{{ resetPwdForm.username }}</strong> 的登录密码。
          </p>

          <div class="form-item">
            <label>新密码 <b class="req-star">*</b></label>
            <input
              v-model="resetPwdForm.password"
              type="password"
              class="form-input"
              placeholder="请输入新密码（至少 6 位）"
              minlength="6"
              required
            />
          </div>

          <div class="form-item">
            <label>确认新密码 <b class="req-star">*</b></label>
            <input
              v-model="resetPwdForm.confirmPassword"
              type="password"
              class="form-input"
              placeholder="请再次输入新密码"
              minlength="6"
              required
            />
          </div>

          <div class="modal-foot">
            <button type="button" class="btn-cancel" @click="modals.resetPwd.visible = false">取消</button>
            <button type="submit" class="btn-submit" :disabled="modals.resetPwd.submitting">
              {{ modals.resetPwd.submitting ? "提交中..." : "确认重置" }}
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
  getUsers,
  createUser,
  updateUser,
  updateUserStatus,
  resetUserPassword,
  assignUserRoles,
  deleteUser,
  getRoles,
  type UserItem,
  type RoleItem,
  type PageResult,
  type UserQueryParams,
} from "../../api/admin";
import { useAuthStore } from "../../stores/auth";

const authStore = useAuthStore();

// 列表与状态
const isLoading = ref(true);
const errorMessage = ref("");
const userList = ref<UserItem[]>([]);
const allRoles = ref<RoleItem[]>([]);
const totalCount = ref(0);

// 查询过滤参数
const queryParams = reactive<UserQueryParams>({
  page: 1,
  size: 10,
  userNo: "",
  username: "",
  realName: "",
  status: "",
  roleId: "",
});

const totalPages = computed(() => {
  return Math.max(1, Math.ceil(totalCount.value / (queryParams.size || 10)));
});

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

// 弹窗状态汇总
const modals = reactive({
  create: { visible: false, submitting: false },
  edit: { visible: false, submitting: false },
  assignRole: { visible: false, submitting: false },
  resetPwd: { visible: false, submitting: false },
});

// 各弹窗表单数据
const createForm = reactive({
  userNo: "",
  username: "",
  password: "",
  realName: "",
  email: "",
  phone: "",
  roleIds: [] as string[],
});

const editForm = reactive({
  id: "",
  userNo: "",
  username: "",
  realName: "",
  email: "",
  phone: "",
});

const assignRoleForm = reactive({
  userId: "",
  username: "",
  realName: "",
  selectedRoleIds: [] as string[],
});

const resetPwdForm = reactive({
  userId: "",
  username: "",
  password: "",
  confirmPassword: "",
});

/**
 * 判断是否为当前登录用户
 */
function isCurrentUser(user: UserItem): boolean {
  if (!authStore.user) return false;
  return (
    user.username === authStore.user.username ||
    user.id === authStore.user.userId ||
    user.id === (authStore.user as any).id
  );
}

/**
 * 格式化展示用户角色列表
 */
function formatUserRoles(user: UserItem): string[] {
  if (!user.roles) return [];
  if (Array.isArray(user.roles)) {
    return user.roles.map((r: any) => {
      if (typeof r === "string") return r;
      return r.roleName || r.roleCode || String(r);
    });
  }
  return [];
}

/**
 * 从后端拉取角色字典列表供筛选和分配使用
 */
async function fetchAllRoles() {
  try {
    const res = await getRoles();
    if (res.data && Array.isArray(res.data)) {
      allRoles.value = res.data;
    }
  } catch (err: any) {
    console.warn("拉取角色字典失败:", err.message);
  }
}

/**
 * 拉取用户列表
 */
async function fetchUserList() {
  isLoading.value = true;
  errorMessage.value = "";
  try {
    const res = await getUsers({
      page: queryParams.page,
      size: queryParams.size,
      userNo: queryParams.userNo?.trim() || undefined,
      username: queryParams.username?.trim() || undefined,
      realName: queryParams.realName?.trim() || undefined,
      status: queryParams.status || undefined,
      roleId: queryParams.roleId || undefined,
    });

    if (res.data) {
      if (Array.isArray(res.data)) {
        userList.value = res.data;
        totalCount.value = res.data.length;
      } else {
        const pageRes = res.data as PageResult<UserItem>;
        userList.value = pageRes.records || pageRes.list || [];
        totalCount.value = pageRes.total || userList.value.length;
      }
    }
  } catch (err: any) {
    errorMessage.value = err.message || "无法拉取用户列表";
    showToast(`拉取用户列表失败：${err.message}`, "danger");
  } finally {
    isLoading.value = false;
  }
}

function handleSearch() {
  queryParams.page = 1;
  fetchUserList();
}

function handleResetQuery() {
  queryParams.userNo = "";
  queryParams.username = "";
  queryParams.realName = "";
  queryParams.status = "";
  queryParams.roleId = "";
  queryParams.page = 1;
  fetchUserList();
}

function handlePageSizeChange() {
  queryParams.page = 1;
  fetchUserList();
}

function goToPage(page: number) {
  if (page < 1 || page > totalPages.value) return;
  queryParams.page = page;
  fetchUserList();
}

/**
 * 启停用户状态
 */
async function toggleUserStatus(user: UserItem) {
  if (isCurrentUser(user)) {
    showToast("操作受限：不可停用自身当前登录的账号", "warning");
    return;
  }

  const nextStatus = user.status === "ACTIVE" ? "DISABLED" : "ACTIVE";
  try {
    await updateUserStatus(user.id, nextStatus);
    user.status = nextStatus;
    showToast(`用户 ${user.username} 状态已变更为 ${nextStatus === 'ACTIVE' ? '启用' : '禁用'}`, "success");
  } catch (err: any) {
    showToast(`变更状态失败：${err.message}`, "danger");
  }
}

/**
 * 打开新建用户弹窗
 */
function openCreateModal() {
  createForm.userNo = "";
  createForm.username = "";
  createForm.password = "";
  createForm.realName = "";
  createForm.email = "";
  createForm.phone = "";
  createForm.roleIds = [];
  modals.create.visible = true;
}

/**
 * 提交新建用户
 */
async function submitCreateUser() {
  if (!createForm.username.trim() || !createForm.password || !createForm.realName.trim()) {
    showToast("请完整填写用户名、初始密码和真实姓名", "warning");
    return;
  }

  modals.create.submitting = true;
  try {
    await createUser({
      userNo: createForm.userNo.trim() || undefined,
      username: createForm.username.trim(),
      password: createForm.password,
      realName: createForm.realName.trim(),
      email: createForm.email.trim() || undefined,
      phone: createForm.phone.trim() || undefined,
      roleIds: createForm.roleIds,
    });
    showToast(`用户 ${createForm.username} 创建成功！`, "success");
    modals.create.visible = false;
    await fetchUserList();
  } catch (err: any) {
    showToast(`创建失败：${err.message}`, "danger");
  } finally {
    modals.create.submitting = false;
  }
}

/**
 * 打开编辑弹窗
 */
function openEditModal(user: UserItem) {
  editForm.id = user.id;
  editForm.userNo = user.userNo || "";
  editForm.username = user.username;
  editForm.realName = user.realName;
  editForm.email = user.email || "";
  editForm.phone = user.phone || "";
  modals.edit.visible = true;
}

/**
 * 提交编辑修改
 */
async function submitEditUser() {
  if (!editForm.realName.trim()) {
    showToast("真实姓名不能为空", "warning");
    return;
  }

  modals.edit.submitting = true;
  try {
    await updateUser(editForm.id, {
      userNo: editForm.userNo.trim() || undefined,
      realName: editForm.realName.trim(),
      email: editForm.email.trim() || undefined,
      phone: editForm.phone.trim() || undefined,
    });
    showToast(`用户 ${editForm.username} 资料更新成功`, "success");
    modals.edit.visible = false;
    await fetchUserList();

    // 如果修改的是当前登录用户，联动刷新个人资料
    if (editForm.username === authStore.user?.username) {
      await authStore.fetchUserInfo();
    }
  } catch (err: any) {
    showToast(`更新失败：${err.message}`, "danger");
  } finally {
    modals.edit.submitting = false;
  }
}

/**
 * 打开分配角色弹窗
 */
function openRoleAssignModal(user: UserItem) {
  assignRoleForm.userId = user.id;
  assignRoleForm.username = user.username;
  assignRoleForm.realName = user.realName;

  // 提取当前用户已有的 roleIds
  const currentRoleIds: string[] = [];
  if (user.roleIds && Array.isArray(user.roleIds)) {
    currentRoleIds.push(...user.roleIds);
  } else if (user.roles && Array.isArray(user.roles)) {
    for (const r of user.roles) {
      if (typeof r === "object") {
        const roleId = (r as any).roleId || (r as any).id;
        if (roleId) currentRoleIds.push(String(roleId));
      } else if (typeof r === "string") {
        const found = allRoles.value.find((ar) => ar.roleCode === r || ar.roleName === r);
        if (found) currentRoleIds.push(found.id);
      }
    }
  }

  assignRoleForm.selectedRoleIds = [...new Set(currentRoleIds)];
  modals.assignRole.visible = true;
}

/**
 * 提交分配角色
 */
async function submitAssignRoles() {
  modals.assignRole.submitting = true;
  try {
    await assignUserRoles(assignRoleForm.userId, assignRoleForm.selectedRoleIds);
    showToast(`用户 ${assignRoleForm.username} 角色已重新分配`, "success");
    modals.assignRole.visible = false;
    await fetchUserList();

    // 若给自身修改了角色，联动刷新 authStore 用户与菜单
    if (assignRoleForm.username === authStore.user?.username) {
      await authStore.fetchUserInfo();
      await authStore.fetchUserMenus();
    }
  } catch (err: any) {
    showToast(`角色分配失败：${err.message}`, "danger");
  } finally {
    modals.assignRole.submitting = false;
  }
}

/**
 * 打开重置密码弹窗
 */
function openResetPwdModal(user: UserItem) {
  resetPwdForm.userId = user.id;
  resetPwdForm.username = user.username;
  resetPwdForm.password = "";
  resetPwdForm.confirmPassword = "";
  modals.resetPwd.visible = true;
}

/**
 * 提交重置密码
 */
async function submitResetPwd() {
  if (!resetPwdForm.password || resetPwdForm.password.length < 6) {
    showToast("新密码长度不能少于 6 位", "warning");
    return;
  }
  if (resetPwdForm.password !== resetPwdForm.confirmPassword) {
    showToast("两次输入的密码不一致", "warning");
    return;
  }

  modals.resetPwd.submitting = true;
  try {
    await resetUserPassword(resetPwdForm.userId, resetPwdForm.password);
    showToast(`用户 ${resetPwdForm.username} 密码重置成功`, "success");
    modals.resetPwd.visible = false;
  } catch (err: any) {
    showToast(`密码重置失败：${err.message}`, "danger");
  } finally {
    modals.resetPwd.submitting = false;
  }
}

/**
 * 删除用户（含保护）
 */
async function handleDeleteUser(user: UserItem) {
  if (isCurrentUser(user)) {
    showToast("保护告警：禁止删除当前登录的账号", "danger");
    return;
  }

  const confirmed = window.confirm(`确定要逻辑删除用户 [${user.username} - ${user.realName}] 吗？该操作不可逆。`);
  if (!confirmed) return;

  try {
    await deleteUser(user.id);
    showToast(`用户 ${user.username} 已成功删除`, "success");
    await fetchUserList();
  } catch (err: any) {
    showToast(`删除用户失败：${err.message}`, "danger");
  }
}

onMounted(() => {
  fetchAllRoles();
  fetchUserList();
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

/* 筛选栏 */
.filter-panel {
  padding: 16px 20px;
  border-radius: 14px;
  background: var(--panel, rgba(10, 18, 28, 0.84));
  border: 1px solid var(--line, rgba(124, 162, 194, 0.18));
  display: flex;
  flex-direction: column;
  gap: 14px;
}

.filter-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(180px, 1fr));
  gap: 12px;
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
  font-family: inherit;
  outline: none;
  transition: border-color 0.2s ease;
}

.filter-input:focus,
.filter-select:focus {
  border-color: var(--accent, #67d2ff);
}

.filter-select option {
  background: #0d1723;
  color: #fff;
}

.filter-actions {
  display: flex;
  justify-content: flex-end;
  gap: 10px;
}

.btn-search {
  padding: 6px 16px;
  border-radius: 6px;
  background: rgba(103, 210, 255, 0.15);
  border: 1px solid rgba(103, 210, 255, 0.35);
  color: var(--accent, #67d2ff);
  font-size: 12px;
  font-weight: 600;
  cursor: pointer;
  transition: all 0.2s ease;
}

.btn-search:hover:not(:disabled) {
  background: rgba(103, 210, 255, 0.28);
}

.btn-reset {
  padding: 6px 14px;
  border-radius: 6px;
  background: rgba(255, 255, 255, 0.04);
  border: 1px solid rgba(124, 162, 194, 0.2);
  color: #8ca2b8;
  font-size: 12px;
  cursor: pointer;
}

.btn-reset:hover:not(:disabled) {
  background: rgba(255, 255, 255, 0.08);
  color: #fff;
}

/* 表格 */
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

.highlight-self-row {
  background: rgba(103, 210, 255, 0.06);
}

.username-cell {
  display: flex;
  align-items: center;
  gap: 6px;
}

.username-text {
  font-weight: 600;
  color: #fff;
}

.self-tag {
  font-size: 10px;
  padding: 1px 6px;
  border-radius: 4px;
  background: rgba(255, 178, 94, 0.15);
  border: 1px solid rgba(255, 178, 94, 0.35);
  color: #ffb25e;
}

.user-realname {
  font-weight: 500;
}

.text-muted {
  color: var(--muted, #8ca2b8);
}

.font-mono {
  font-family: monospace;
}

.highlight-cyan {
  color: var(--accent, #67d2ff);
  font-weight: 600;
}

.date-text {
  font-size: 11px;
}

.role-tags-wrap {
  display: flex;
  flex-wrap: wrap;
  gap: 4px;
}

.role-badge {
  font-size: 11px;
  padding: 2px 7px;
  border-radius: 4px;
  background: rgba(103, 210, 255, 0.08);
  border: 1px solid rgba(103, 210, 255, 0.2);
  color: #b5e4fc;
}

.no-role {
  font-size: 11px;
  font-style: italic;
}

/* Switch 开关组件 */
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

.switch-btn.is-disabled-click {
  opacity: 0.5;
  cursor: not-allowed;
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
  font-family: inherit;
  cursor: pointer;
  transition: all 0.15s ease;
  border: 1px solid transparent;
}

.btn-edit {
  background: rgba(103, 210, 255, 0.1);
  border-color: rgba(103, 210, 255, 0.25);
  color: var(--accent, #67d2ff);
}
.btn-edit:hover {
  background: rgba(103, 210, 255, 0.22);
}

.btn-role {
  background: rgba(255, 178, 94, 0.1);
  border-color: rgba(255, 178, 94, 0.25);
  color: #ffb25e;
}
.btn-role:hover {
  background: rgba(255, 178, 94, 0.22);
}

.btn-pwd {
  background: rgba(111, 225, 166, 0.1);
  border-color: rgba(111, 225, 166, 0.25);
  color: #6fe1a6;
}
.btn-pwd:hover {
  background: rgba(111, 225, 166, 0.22);
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

/* 空状态 */
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

/* 分页 */
.pagination-footer {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 12px 18px;
  background: rgba(5, 11, 17, 0.85);
  border-top: 1px solid rgba(124, 162, 194, 0.14);
  font-size: 12px;
}

.page-size-selector {
  display: flex;
  align-items: center;
  gap: 8px;
  color: var(--muted, #8ca2b8);
}

.page-select {
  padding: 4px 8px;
  border-radius: 4px;
  background: rgba(255, 255, 255, 0.04);
  border: 1px solid rgba(124, 162, 194, 0.2);
  color: #fff;
  font-size: 12px;
  outline: none;
}

.page-select option {
  background: #0d1723;
}

.page-pager {
  display: flex;
  align-items: center;
  gap: 12px;
}

.page-info {
  color: var(--muted, #8ca2b8);
}

.pager-btn {
  padding: 4px 12px;
  border-radius: 6px;
  background: rgba(255, 255, 255, 0.04);
  border: 1px solid rgba(124, 162, 194, 0.2);
  color: #d5e3ef;
  font-size: 12px;
  cursor: pointer;
  transition: all 0.2s ease;
}

.pager-btn:hover:not(:disabled) {
  background: rgba(103, 210, 255, 0.15);
  border-color: rgba(103, 210, 255, 0.4);
  color: #fff;
}

.pager-btn:disabled {
  opacity: 0.35;
  cursor: not-allowed;
}

/* Modal 通用样式 */
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

.modal-small {
  max-width: 440px;
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
  padding: 4px;
}

.modal-close:hover {
  color: #fff;
}

.modal-body {
  padding: 20px;
  display: flex;
  flex-direction: column;
  gap: 16px;
  max-height: calc(85vh - 120px);
  overflow-y: auto;
}

.modal-hint {
  font-size: 12px;
  color: var(--muted, #8ca2b8);
  margin: 0;
  line-height: 1.5;
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

.form-input {
  width: 100%;
  padding: 8px 12px;
  border-radius: 6px;
  background: rgba(255, 255, 255, 0.04);
  border: 1px solid rgba(124, 162, 194, 0.25);
  color: #fff;
  font-size: 12px;
  font-family: inherit;
  outline: none;
  transition: all 0.2s ease;
  box-sizing: border-box;
}

.form-input:focus {
  border-color: var(--accent, #67d2ff);
  box-shadow: 0 0 0 2px rgba(103, 210, 255, 0.2);
}

.role-checkbox-group {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 8px;
  max-height: 180px;
  overflow-y: auto;
  padding: 6px;
  background: rgba(0, 0, 0, 0.2);
  border-radius: 8px;
}

.role-checkbox-group.vertical {
  grid-template-columns: 1fr;
  max-height: 280px;
}

.checkbox-item {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 11px;
  color: #d5e3ef;
  cursor: pointer;
}

.checkbox-card {
  display: flex;
  align-items: flex-start;
  gap: 10px;
  padding: 10px 12px;
  border-radius: 8px;
  background: rgba(255, 255, 255, 0.02);
  border: 1px solid rgba(124, 162, 194, 0.14);
  cursor: pointer;
  transition: all 0.15s ease;
}

.checkbox-card:hover {
  background: rgba(103, 210, 255, 0.04);
  border-color: rgba(103, 210, 255, 0.3);
}

.checkbox-card.is-checked {
  background: rgba(103, 210, 255, 0.08);
  border-color: var(--accent, #67d2ff);
}

.checkbox-card-info {
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.role-name-row {
  display: flex;
  align-items: center;
  gap: 8px;
}

.role-name-row strong {
  font-size: 13px;
  color: #fff;
}

.role-code-tag {
  font-size: 10px;
  color: var(--accent, #67d2ff);
}

.role-desc-text {
  font-size: 11px;
  color: var(--muted, #8ca2b8);
  margin: 0;
}

.modal-foot {
  display: flex;
  justify-content: flex-end;
  gap: 10px;
  padding-top: 12px;
  border-top: 1px solid rgba(124, 162, 194, 0.12);
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
