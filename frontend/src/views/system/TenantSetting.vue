<template>
  <div class="system-view-container">
    <!-- 头部说明 -->
    <header class="page-header">
      <div class="header-meta">
        <el-tag type="info" size="small">AUTH / TENANT SETTING</el-tag>
        <el-tag :type="tenantData.status === 'ACTIVE' ? 'success' : 'danger'" size="small" effect="dark">
          ● {{ tenantData.status === 'ACTIVE' ? '租户正常运行 (ACTIVE)' : '租户已停用 (DISABLED)' }}
        </el-tag>
      </div>
      <h1 class="page-title">租户配置与空间上下文</h1>
      <p class="page-desc">
        管理当前租户的基础身份与展示信息。租户编码与空间 ID 为底层多租户数据隔离硬约束，不可变更。
      </p>
    </header>

    <!-- 错误警告条 -->
    <el-alert
      v-if="errorMessage"
      :title="'租户接口调用异常：' + errorMessage"
      type="warning"
      show-icon
      class="mb-4"
    >
      <template #default>
        <el-button type="warning" link size="small" @click="fetchTenant">重试拉取</el-button>
      </template>
    </el-alert>

    <!-- 加载状态 -->
    <div v-if="isLoading" class="loading-box" v-loading="true" element-loading-text="正在从 Auth 服务加载租户配置事实...">
      <div style="height: 260px;"></div>
    </div>

    <!-- 核心卡片网格 -->
    <el-row v-else :gutter="20" class="content-grid">
      <!-- 左侧：基础配置与编辑表单 -->
      <el-col :xs="24" :md="12">
        <el-card shadow="never" class="panel-card mb-4">
          <template #header>
            <div class="panel-head">
              <span class="panel-title font-bold">租户基本信息维护</span>
              <el-tag size="small" type="info" effect="plain">PUT /api/auth/admin/tenants/current</el-tag>
            </div>
          </template>

          <el-form label-position="top" @submit.prevent="handleSaveTenant">
            <!-- 租户编码 (只读不可改) -->
            <el-form-item label="租户编码 (Tenant Code)">
              <template #label>
                <div class="label-row">
                  <span>租户编码 (Tenant Code)</span>
                  <el-tag size="small" type="danger" effect="plain">🔒 系统硬约束·只读</el-tag>
                </div>
              </template>
              <el-input
                :model-value="tenantData.tenantCode"
                disabled
                class="font-mono"
              />
              <span class="field-hint">租户编码用于 JWT 签发、请求头隔离与数据库多租户行级路由。</span>
            </el-form-item>

            <!-- 租户名称 (可编辑) -->
            <el-form-item label="租户展示名称 (Tenant Name)" required>
              <el-input
                v-model="editForm.tenantName"
                placeholder="请输入企业或工厂租户名称"
                maxlength="50"
                show-word-limit
                clearable
              />
              <span class="field-hint">用于控制台顶部、报表及通知中展示的直观租户名称。</span>
            </el-form-item>

            <!-- 租户状态 (只读/展示) -->
            <el-form-item label="租户状态 (Status)">
              <div class="status-row">
                <el-tag :type="tenantData.status === 'ACTIVE' ? 'success' : 'danger'" effect="dark">
                  {{ tenantData.status === 'ACTIVE' ? '已启用 (ACTIVE)' : '已停用 (DISABLED)' }}
                </el-tag>
                <span class="status-note">租户启停属于平台级运维权限，租户内仅做只读事实呈现。</span>
              </div>
            </el-form-item>

            <!-- 提交操作按钮 -->
            <el-form-item class="form-actions">
              <el-button
                type="primary"
                :loading="isSaving"
                :disabled="!isFormModified || !editForm.tenantName.trim()"
                @click="handleSaveTenant"
              >
                {{ isSaving ? "正在保存..." : "保存租户修改" }}
              </el-button>
              <el-button
                :disabled="isSaving || !isFormModified"
                @click="handleResetForm"
              >
                重置输入
              </el-button>
            </el-form-item>
          </el-form>
        </el-card>
      </el-col>

      <!-- 右侧：租户隔离元数据看板 -->
      <el-col :xs="24" :md="12">
        <el-card shadow="never" class="panel-card mb-4">
          <template #header>
            <div class="panel-head">
              <span class="panel-title font-bold">多租户隔离上下文事实</span>
              <el-tag size="small" type="success" effect="plain">Read-Only Metadata</el-tag>
            </div>
          </template>

          <el-descriptions :column="1" border>
            <el-descriptions-item label="租户唯一 ID">
              <code class="font-mono">{{ tenantData.id || "未初始化" }}</code>
            </el-descriptions-item>
            <el-descriptions-item label="租户业务代号">
              <el-tag type="primary" effect="dark" class="font-mono">{{ tenantData.tenantCode || "DEFAULT" }}</el-tag>
            </el-descriptions-item>
            <el-descriptions-item label="创建时间">
              <span class="font-mono">{{ tenantData.createdAt || "2026-08-01 00:00:00" }}</span>
            </el-descriptions-item>
            <el-descriptions-item label="最后更新时间">
              <span class="font-mono">{{ tenantData.updatedAt || "刚刚" }}</span>
            </el-descriptions-item>
            <el-descriptions-item label="创建人员/系统">
              <span>{{ tenantData.createdBy || "system.initializer" }}</span>
            </el-descriptions-item>
          </el-descriptions>

          <el-alert
            title="租户安全与边界原则"
            type="info"
            :closable="false"
            show-icon
            class="security-notice mt-4"
          >
            <template #default>
              <p class="notice-item">1. 所有 API 请求拦截器自动在 Header 中附带 <code>X-Tenant-Id</code>。</p>
              <p class="notice-item">2. 后端服务端在 ThreadLocal 中绑定租户上下文，严格执行行级隔离与外键约束。</p>
              <p class="notice-item mb-0">3. 禁止跨租户查询用户、角色或菜单配置。</p>
            </template>
          </el-alert>
        </el-card>
      </el-col>
    </el-row>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, computed, onMounted } from "vue";
import { ElMessage } from "element-plus";
import { getCurrentTenant, updateCurrentTenant, type TenantInfo } from "../../api/admin";
import { useAuthStore } from "../../stores/auth";

const authStore = useAuthStore();

// 核心数据状态
const isLoading = ref(true);
const isSaving = ref(false);
const errorMessage = ref("");

const tenantData = reactive<TenantInfo>({
  id: "",
  tenantCode: authStore.activeTenant || "tenant_demo_a",
  tenantName: "",
  status: "ACTIVE",
  createdAt: "",
  updatedAt: "",
  createdBy: "",
});

const editForm = reactive({
  tenantName: "",
});

// 检查表单是否被修改
const isFormModified = computed(() => {
  return editForm.tenantName.trim() !== tenantData.tenantName;
});

/**
 * 从后端拉取当前租户数据
 */
async function fetchTenant() {
  isLoading.value = true;
  errorMessage.value = "";
  try {
    const res = await getCurrentTenant();
    if (res.data) {
      tenantData.id = res.data.id;
      tenantData.tenantCode = res.data.tenantCode || authStore.activeTenant;
      tenantData.tenantName = res.data.tenantName || "";
      tenantData.status = res.data.status || "ACTIVE";
      tenantData.createdAt = res.data.createdAt;
      tenantData.updatedAt = res.data.updatedAt;
      tenantData.createdBy = res.data.createdBy;
      editForm.tenantName = tenantData.tenantName;
    }
  } catch (err: any) {
    errorMessage.value = err.message || "无法拉取租户配置";
    tenantData.tenantCode = authStore.activeTenant;
    tenantData.tenantName = tenantData.tenantName || "华北智能工厂示范租户";
    editForm.tenantName = tenantData.tenantName;
    ElMessage.warning(errorMessage.value);
  } finally {
    isLoading.value = false;
  }
}

/**
 * 保存租户名称修改
 */
async function handleSaveTenant() {
  const newName = editForm.tenantName.trim();
  if (!newName) {
    ElMessage.warning("租户展示名称不能为空");
    return;
  }
  if (!isFormModified.value) {
    ElMessage.info("未检测到修改内容");
    return;
  }

  isSaving.value = true;
  try {
    const res = await updateCurrentTenant({ tenantName: newName });
    if (res.data) {
      tenantData.tenantName = res.data.tenantName;
      tenantData.updatedAt = res.data.updatedAt || new Date().toLocaleString();
      editForm.tenantName = tenantData.tenantName;
      // 同步更新 Pinia store
      authStore.activeTenant = tenantData.tenantName;
    } else {
      tenantData.tenantName = newName;
    }
    ElMessage.success("租户展示名称已成功更新！");
  } catch (err: any) {
    console.error("[TenantSetting] 保存失败:", err);
    ElMessage.error(err.message || "保存租户配置失败");
  } finally {
    isSaving.value = false;
  }
}

/**
 * 重置表单输入
 */
function handleResetForm() {
  editForm.tenantName = tenantData.tenantName;
  ElMessage.info("已重置为当前保存值");
}

onMounted(() => {
  fetchTenant();
});
</script>

<style scoped>
.system-view-container {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.page-header {
  margin-bottom: 8px;
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

.panel-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.panel-title {
  font-size: 14px;
  color: #f1f5f9;
}

.label-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  width: 100%;
}

.field-hint {
  font-size: 11px;
  color: #8ca2b8;
  margin-top: 4px;
  display: block;
}

.status-row {
  display: flex;
  align-items: center;
  gap: 10px;
}

.status-note {
  font-size: 12px;
  color: #64748b;
}

.form-actions {
  margin-top: 24px;
}

.security-notice {
  margin-top: 16px;
}

.notice-item {
  margin: 4px 0;
  font-size: 12px;
  line-height: 1.5;
}

.mb-4 {
  margin-bottom: 16px;
}

.mt-4 {
  margin-top: 16px;
}

.mb-0 {
  margin-bottom: 0;
}

.font-bold {
  font-weight: 600;
}

.font-mono {
  font-family: var(--font-mono, monospace);
}
</style>
