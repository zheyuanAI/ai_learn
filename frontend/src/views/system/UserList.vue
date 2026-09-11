<template>
  <div class="system-view-container">
    <!-- 头部说明 -->
    <header class="page-header">
      <div class="header-meta">
        <el-tag type="info" size="small">AUTH / USER MANAGEMENT</el-tag>
        <el-tag type="primary" size="small" effect="plain">总计 {{ totalCount }} 位操作用户</el-tag>
      </div>
      <div class="header-main-row">
        <div>
          <h1 class="page-title">租户操作用户管理</h1>
          <p class="page-desc">
            维护当前租户下的工号、登录账号、资料与角色授权。禁止停用或删除自身账号及最后管理员。
          </p>
        </div>
        <el-button type="primary" @click="openCreateModal">
          ＋ 新建用户
        </el-button>
      </div>
    </header>

    <!-- 筛选搜索栏 -->
    <el-card shadow="never" class="filter-card">
      <el-form inline class="filter-form">
        <el-form-item label="工号">
          <el-input
            v-model="queryParams.userNo"
            placeholder="搜索工号..."
            clearable
            style="width: 140px;"
            @keyup.enter="handleSearch"
          />
        </el-form-item>

        <el-form-item label="登录账号">
          <el-input
            v-model="queryParams.username"
            placeholder="搜索用户名..."
            clearable
            style="width: 140px;"
            @keyup.enter="handleSearch"
          />
        </el-form-item>

        <el-form-item label="真实姓名">
          <el-input
            v-model="queryParams.realName"
            placeholder="搜索真实姓名..."
            clearable
            style="width: 140px;"
            @keyup.enter="handleSearch"
          />
        </el-form-item>

        <el-form-item label="状态">
          <el-select
            v-model="queryParams.status"
            clearable
            placeholder="全部状态"
            style="width: 130px;"
            @change="handleSearch"
          >
            <el-option label="全部状态" value="" />
            <el-option label="正常" value="ACTIVE" />
            <el-option label="已禁用" value="DISABLED" />
            <el-option label="已锁定" value="LOCKED" />
          </el-select>
        </el-form-item>

        <el-form-item label="所属角色">
          <el-select
            v-model="queryParams.roleId"
            clearable
            placeholder="全部角色"
            style="width: 160px;"
            @change="handleSearch"
          >
            <el-option label="全部角色" value="" />
            <el-option
              v-for="role in allRoles"
              :key="role.id"
              :label="role.roleName"
              :value="role.id"
            />
          </el-select>
        </el-form-item>

        <el-form-item>
          <el-button type="primary" :loading="isLoading" @click="handleSearch">
            🔍 查询
          </el-button>
          <el-button :loading="isLoading" @click="handleResetQuery">
            重置
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
        <el-button type="danger" link size="small" @click="fetchUserList">重试</el-button>
      </template>
    </el-alert>

    <!-- 用户列表表格 -->
    <el-card shadow="never" class="table-card">
      <el-table
        v-loading="isLoading"
        :data="userList"
        row-key="id"
        stripe
        style="width: 100%"
      >
        <template #empty>
          <el-empty description="未找到符合条件的用户数据" />
        </template>

        <el-table-column prop="userNo" label="工号" width="110">
          <template #default="{ row }">
            <span class="font-mono text-cyan">{{ row.userNo || "-" }}</span>
          </template>
        </el-table-column>

        <el-table-column prop="username" label="登录用户名" width="150">
          <template #default="{ row }">
            <div class="user-row-title">
              <span class="font-mono">{{ row.username }}</span>
              <el-tag v-if="isCurrentUser(row)" size="small" type="warning" effect="dark">
                当前账号
              </el-tag>
            </div>
          </template>
        </el-table-column>

        <el-table-column prop="realName" label="真实姓名" width="120" />

        <el-table-column prop="phone" label="联系电话" width="130">
          <template #default="{ row }">
            <span class="font-mono text-muted">{{ row.phone || "-" }}</span>
          </template>
        </el-table-column>

        <el-table-column prop="email" label="电子邮箱" min-width="160" show-overflow-tooltip>
          <template #default="{ row }">
            <span class="text-muted">{{ row.email || "-" }}</span>
          </template>
        </el-table-column>

        <el-table-column label="已分配角色" min-width="180">
          <template #default="{ row }">
            <div class="role-tags-wrap" v-if="formatUserRoles(row).length > 0">
              <el-tag
                v-for="r in formatUserRoles(row)"
                :key="r"
                size="small"
                type="info"
                class="mr-1 mb-1"
              >
                {{ r }}
              </el-tag>
            </div>
            <span v-else class="text-muted">未分配角色</span>
          </template>
        </el-table-column>

        <el-table-column label="状态" width="100" align="center">
          <template #default="{ row }">
            <el-switch
              :model-value="row.status === 'ACTIVE'"
              :disabled="isCurrentUser(row)"
              active-text=""
              inactive-text=""
              @change="toggleUserStatus(row)"
            />
          </template>
        </el-table-column>

        <el-table-column prop="createdAt" label="创建时间" width="170">
          <template #default="{ row }">
            <span class="font-mono text-muted">{{ row.createdAt || "-" }}</span>
          </template>
        </el-table-column>

        <el-table-column label="操作" width="220" align="center" fixed="right">
          <template #default="{ row }">
            <el-button type="primary" link size="small" @click="openEditModal(row)">
              编辑
            </el-button>
            <el-button type="primary" link size="small" @click="openRoleAssignModal(row)">
              角色
            </el-button>
            <el-button type="warning" link size="small" @click="openResetPwdModal(row)">
              密码
            </el-button>
            <el-button
              type="danger"
              link
              size="small"
              :disabled="isCurrentUser(row)"
              @click="handleDeleteUser(row)"
            >
              删除
            </el-button>
          </template>
        </el-table-column>
      </el-table>

      <!-- 底部分页 -->
      <div v-if="totalCount > 0" class="pagination-box">
        <el-pagination
          v-model:current-page="queryParams.page"
          v-model:page-size="queryParams.size"
          :total="totalCount"
          :page-sizes="[10, 20, 50]"
          layout="total, sizes, prev, pager, next, jumper"
          background
          @current-change="handlePageChange"
          @size-change="handlePageSizeChange"
        />
      </div>
    </el-card>

    <!-- 弹窗 1：新建用户 -->
    <el-dialog
      v-model="modals.create.visible"
      title="新建租户操作用户"
      width="600px"
      destroy-on-close
    >
      <el-form label-position="top" @submit.prevent="submitCreateUser">
        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item label="工号 (User No)">
              <el-input v-model="createForm.userNo" placeholder="如 WORKER-001" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="登录用户名" required>
              <el-input v-model="createForm.username" placeholder="建议小写英数字" />
            </el-form-item>
          </el-col>
        </el-row>

        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item label="初始登录密码" required>
              <el-input v-model="createForm.password" type="password" show-password placeholder="至少 6 位" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="真实姓名" required>
              <el-input v-model="createForm.realName" placeholder="如 张三" />
            </el-form-item>
          </el-col>
        </el-row>

        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item label="电子邮箱">
              <el-input v-model="createForm.email" placeholder="user@factory.com" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="手机号码">
              <el-input v-model="createForm.phone" placeholder="13800000000" />
            </el-form-item>
          </el-col>
        </el-row>

        <el-form-item label="初始分配业务角色">
          <el-checkbox-group v-model="createForm.roleIds">
            <el-checkbox
              v-for="role in allRoles"
              :key="role.id"
              :label="role.id"
            >
              {{ role.roleName }}
            </el-checkbox>
          </el-checkbox-group>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="modals.create.visible = false">取消</el-button>
        <el-button type="primary" :loading="modals.create.submitting" @click="submitCreateUser">
          {{ modals.create.submitting ? "创建中..." : "确认创建" }}
        </el-button>
      </template>
    </el-dialog>

    <!-- 弹窗 2：编辑用户资料 -->
    <el-dialog
      v-model="modals.edit.visible"
      :title="'编辑用户资料 (' + editForm.username + ')'"
      width="600px"
      destroy-on-close
    >
      <el-form label-position="top" @submit.prevent="submitEditUser">
        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item label="工号 (User No)">
              <el-input v-model="editForm.userNo" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="真实姓名" required>
              <el-input v-model="editForm.realName" />
            </el-form-item>
          </el-col>
        </el-row>

        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item label="电子邮箱">
              <el-input v-model="editForm.email" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="联系手机号">
              <el-input v-model="editForm.phone" />
            </el-form-item>
          </el-col>
        </el-row>
      </el-form>
      <template #footer>
        <el-button @click="modals.edit.visible = false">取消</el-button>
        <el-button type="primary" :loading="modals.edit.submitting" @click="submitEditUser">
          {{ modals.edit.submitting ? "保存中..." : "保存修改" }}
        </el-button>
      </template>
    </el-dialog>

    <!-- 弹窗 3：分配角色 -->
    <el-dialog
      v-model="modals.assignRole.visible"
      :title="'分配角色 — ' + assignRoleForm.realName + ' (' + assignRoleForm.username + ')'"
      width="540px"
      destroy-on-close
    >
      <p class="modal-hint mb-4">选择该用户在当前租户下所担任的业务角色（支持多选）：</p>
      <el-checkbox-group v-model="assignRoleForm.selectedRoleIds" class="role-checkbox-vertical">
        <el-checkbox
          v-for="role in allRoles"
          :key="role.id"
          :label="role.id"
          class="role-cb-item"
        >
          <span class="font-bold">{{ role.roleName }}</span>
          <span class="text-muted ml-2">({{ role.roleCode }})</span>
        </el-checkbox>
      </el-checkbox-group>
      <template #footer>
        <el-button @click="modals.assignRole.visible = false">取消</el-button>
        <el-button type="primary" :loading="modals.assignRole.submitting" @click="submitAssignRoles">
          {{ modals.assignRole.submitting ? "提交中..." : "确认授权" }}
        </el-button>
      </template>
    </el-dialog>

    <!-- 弹窗 4：重置密码 -->
    <el-dialog
      v-model="modals.resetPwd.visible"
      :title="'重置密码 — ' + resetPwdForm.username"
      width="460px"
      destroy-on-close
    >
      <el-form label-position="top" @submit.prevent="submitResetPwd">
        <el-form-item label="新密码" required>
          <el-input
            v-model="resetPwdForm.password"
            type="password"
            show-password
            placeholder="请输入新密码（至少 6 位）"
          />
        </el-form-item>
        <el-form-item label="确认新密码" required>
          <el-input
            v-model="resetPwdForm.confirmPassword"
            type="password"
            show-password
            placeholder="请再次输入新密码"
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="modals.resetPwd.visible = false">取消</el-button>
        <el-button type="primary" :loading="modals.resetPwd.submitting" @click="submitResetPwd">
          {{ modals.resetPwd.submitting ? "提交中..." : "确认重置" }}
        </el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted } from "vue";
import { ElMessage, ElMessageBox } from "element-plus";
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

function isCurrentUser(user: UserItem): boolean {
  if (!authStore.user) return false;
  return (
    user.username === authStore.user.username ||
    user.id === authStore.user.userId ||
    user.id === (authStore.user as any).id
  );
}

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
    ElMessage.error(`拉取用户列表失败：${err.message}`);
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

function handlePageSizeChange(val: number) {
  queryParams.size = val;
  queryParams.page = 1;
  fetchUserList();
}

function handlePageChange(val: number) {
  queryParams.page = val;
  fetchUserList();
}

async function toggleUserStatus(user: UserItem) {
  if (isCurrentUser(user)) {
    ElMessage.warning("操作受限：不可停用自身当前登录的账号");
    return;
  }

  const nextStatus = user.status === "ACTIVE" ? "DISABLED" : "ACTIVE";
  try {
    await updateUserStatus(user.id, nextStatus);
    user.status = nextStatus;
    ElMessage.success(`用户 ${user.username} 已成功${nextStatus === 'ACTIVE' ? '启用' : '禁用'}`);
  } catch (err: any) {
    ElMessage.error(`更新用户状态失败：${err.message}`);
  }
}

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

async function submitCreateUser() {
  if (!createForm.username.trim() || !createForm.password.trim() || !createForm.realName.trim()) {
    ElMessage.warning("请完整填写用户名、初始密码和真实姓名");
    return;
  }
  if (createForm.password.length < 6) {
    ElMessage.warning("密码长度至少为 6 位字符");
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
    ElMessage.success(`用户 ${createForm.username} 创建成功！`);
    modals.create.visible = false;
    await fetchUserList();
  } catch (err: any) {
    ElMessage.error(`创建用户失败：${err.message}`);
  } finally {
    modals.create.submitting = false;
  }
}

function openEditModal(user: UserItem) {
  editForm.id = user.id;
  editForm.userNo = user.userNo || "";
  editForm.username = user.username;
  editForm.realName = user.realName;
  editForm.email = user.email || "";
  editForm.phone = user.phone || "";
  modals.edit.visible = true;
}

async function submitEditUser() {
  if (!editForm.realName.trim()) {
    ElMessage.warning("真实姓名不能为空");
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
    ElMessage.success(`用户 ${editForm.username} 资料更新成功`);
    modals.edit.visible = false;
    await fetchUserList();
  } catch (err: any) {
    ElMessage.error(`更新用户资料失败：${err.message}`);
  } finally {
    modals.edit.submitting = false;
  }
}

function openRoleAssignModal(user: UserItem) {
  assignRoleForm.userId = user.id;
  assignRoleForm.username = user.username;
  assignRoleForm.realName = user.realName;

  const currentIds: string[] = [];
  if (user.roles && Array.isArray(user.roles)) {
    for (const r of user.roles) {
      if (typeof r === "string") {
        const found = allRoles.value.find((ar) => ar.roleCode === r || ar.id === r);
        if (found) currentIds.push(found.id);
      } else if (r && r.id) {
        currentIds.push(r.id);
      }
    }
  }
  assignRoleForm.selectedRoleIds = currentIds;
  modals.assignRole.visible = true;
}

async function submitAssignRoles() {
  modals.assignRole.submitting = true;
  try {
    await assignUserRoles(assignRoleForm.userId, assignRoleForm.selectedRoleIds);
    ElMessage.success(`用户 ${assignRoleForm.username} 角色授权已更新`);
    modals.assignRole.visible = false;
    await fetchUserList();
  } catch (err: any) {
    ElMessage.error(`分配角色失败：${err.message}`);
  } finally {
    modals.assignRole.submitting = false;
  }
}

function openResetPwdModal(user: UserItem) {
  resetPwdForm.userId = user.id;
  resetPwdForm.username = user.username;
  resetPwdForm.password = "";
  resetPwdForm.confirmPassword = "";
  modals.resetPwd.visible = true;
}

async function submitResetPwd() {
  if (!resetPwdForm.password.trim() || resetPwdForm.password.length < 6) {
    ElMessage.warning("新密码长度不能少于 6 位字符");
    return;
  }
  if (resetPwdForm.password !== resetPwdForm.confirmPassword) {
    ElMessage.warning("两次输入的密码不一致，请核对后重试");
    return;
  }

  modals.resetPwd.submitting = true;
  try {
    await resetUserPassword(resetPwdForm.userId, resetPwdForm.password);
    ElMessage.success(`用户 ${resetPwdForm.username} 密码重置成功！`);
    modals.resetPwd.visible = false;
  } catch (err: any) {
    ElMessage.error(`密码重置失败：${err.message}`);
  } finally {
    modals.resetPwd.submitting = false;
  }
}

async function handleDeleteUser(user: UserItem) {
  if (isCurrentUser(user)) {
    ElMessage.warning("不可删除当前登录的自身账号");
    return;
  }

  try {
    await ElMessageBox.confirm(
      `确定要删除用户 [${user.realName} (${user.username})] 吗？删除后此账号将无法登录系统。`,
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
    await deleteUser(user.id);
    ElMessage.success(`用户 ${user.username} 已成功删除`);
    await fetchUserList();
  } catch (err: any) {
    ElMessage.error(`删除用户失败：${err.message}`);
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

.user-row-title {
  display: flex;
  align-items: center;
  gap: 6px;
}

.pagination-box {
  display: flex;
  justify-content: flex-end;
  padding: 14px 0 2px 0;
}

.role-checkbox-vertical {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.role-cb-item {
  margin-right: 0 !important;
}

.modal-hint {
  font-size: 13px;
  color: #8ca2b8;
}

.text-cyan {
  color: var(--el-color-primary);
}

.text-muted {
  color: #8ca2b8;
}

.mr-1 {
  margin-right: 4px;
}

.mb-1 {
  margin-bottom: 4px;
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

.font-mono {
  font-family: var(--font-mono, monospace);
}
</style>
