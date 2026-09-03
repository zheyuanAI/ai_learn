<template>
  <div class="system-view-container">
    <!-- 头部说明 -->
    <header class="page-header">
      <div class="header-meta">
        <span class="meta-tag">AUTH / TENANT SETTING</span>
        <span class="meta-badge" :class="tenantData.status === 'ACTIVE' ? 'badge-green' : 'badge-red'">
          <i class="status-dot"></i> {{ tenantData.status === 'ACTIVE' ? '租户正常运行 (ACTIVE)' : '租户已停用 (DISABLED)' }}
        </span>
      </div>
      <h1 class="page-title">租户配置与空间上下文</h1>
      <p class="page-desc">
        管理当前租户的基础身份与展示信息。租户编码与空间 ID 为底层多租户数据隔离硬约束，不可变更。
      </p>
    </header>

    <!-- 加载状态 -->
    <div v-if="isLoading" class="loading-state">
      <div class="spinner"></div>
      <span>正在从 Auth 服务加载租户配置事实...</span>
    </div>

    <!-- 错误警告条 -->
    <div v-if="errorMessage" class="error-banner">
      <span class="error-icon">⚠️</span>
      <div class="error-text">
        <strong>租户接口调用异常：</strong>
        <span>{{ errorMessage }}</span>
      </div>
      <button type="button" class="btn-retry" @click="fetchTenant">重试拉取</button>
    </div>

    <!-- 核心卡片网格 -->
    <div v-if="!isLoading" class="content-grid">
      <!-- 左侧：基础配置与编辑表单 -->
      <section class="panel-card form-panel">
        <div class="panel-head">
          <h2 class="panel-title">租户基本信息维护</h2>
          <span class="panel-hint">PUT /api/auth/admin/tenants/current</span>
        </div>

        <form class="custom-form" @submit.prevent="handleSaveTenant">
          <!-- 租户编码 (只读不可改) -->
          <div class="form-item">
            <label class="form-label">
              <span>租户编码 (Tenant Code)</span>
              <span class="readonly-badge">🔒 系统硬约束·只读</span>
            </label>
            <div class="readonly-input-wrap">
              <input
                type="text"
                class="form-input readonly-input"
                :value="tenantData.tenantCode"
                readonly
                disabled
              />
            </div>
            <span class="field-hint">租户编码用于 JWT 签发、请求头隔离与数据库多租户行级路由。</span>
          </div>

          <!-- 租户名称 (可编辑) -->
          <div class="form-item">
            <label class="form-label" for="tenantName">
              <span>租户展示名称 (Tenant Name) <b class="req-star">*</b></span>
              <span class="char-count">{{ editForm.tenantName.length }}/50</span>
            </label>
            <input
              id="tenantName"
              v-model="editForm.tenantName"
              type="text"
              class="form-input"
              placeholder="请输入企业或工厂租户名称"
              maxlength="50"
              required
            />
            <span class="field-hint">用于控制台顶部、报表及通知中展示的直观租户名称。</span>
          </div>

          <!-- 租户状态 (只读/展示) -->
          <div class="form-item">
            <label class="form-label">
              <span>租户状态 (Status)</span>
            </label>
            <div class="status-display-row">
              <span class="status-pill" :class="tenantData.status === 'ACTIVE' ? 'pill-active' : 'pill-disabled'">
                {{ tenantData.status === 'ACTIVE' ? '已启用 (ACTIVE)' : '已停用 (DISABLED)' }}
              </span>
              <span class="status-note">租户启停属于平台级运维权限，租户内仅做只读事实呈现。</span>
            </div>
          </div>

          <!-- 提交操作按钮 -->
          <div class="form-actions">
            <button
              type="submit"
              class="btn-primary"
              :disabled="isSaving || !isFormModified || !editForm.tenantName.trim()"
            >
              <span v-if="isSaving" class="btn-spinner"></span>
              <span>{{ isSaving ? "正在保存..." : "保存租户修改" }}</span>
            </button>
            <button
              type="button"
              class="btn-secondary"
              :disabled="isSaving || !isFormModified"
              @click="handleResetForm"
            >
              重置输入
            </button>
          </div>
        </form>
      </section>

      <!-- 右侧：租户隔离元数据看板 -->
      <section class="panel-card info-panel">
        <div class="panel-head">
          <h2 class="panel-title">多租户隔离上下文事实</h2>
          <span class="panel-hint">Read-Only Metadata</span>
        </div>

        <div class="metadata-list">
          <div class="meta-row">
            <span class="meta-label">租户全局唯一 ID</span>
            <span class="meta-value font-mono">{{ tenantData.id || "未初始化" }}</span>
          </div>

          <div class="meta-row">
            <span class="meta-label">租户业务代号</span>
            <span class="meta-value highlight-cyan font-mono">{{ tenantData.tenantCode || "DEFAULT" }}</span>
          </div>

          <div class="meta-row">
            <span class="meta-label">创建时间</span>
            <span class="meta-value font-mono">{{ tenantData.createdAt || "2026-08-01 00:00:00" }}</span>
          </div>

          <div class="meta-row">
            <span class="meta-label">最后更新时间</span>
            <span class="meta-value font-mono">{{ tenantData.updatedAt || "刚刚" }}</span>
          </div>

          <div class="meta-row">
            <span class="meta-label">创建人员 / 系统</span>
            <span class="meta-value">{{ tenantData.createdBy || "system.initializer" }}</span>
          </div>
        </div>

        <div class="notice-box">
          <div class="notice-header">
            <span class="notice-icon">🛡️</span>
            <strong>租户安全与边界原则</strong>
          </div>
          <p class="notice-text">
            1. 所有 API 请求拦截器自动在 Header 中附带 <code>X-Tenant-Id</code>。<br/>
            2. 后端服务端在 ThreadLocal 中绑定租户上下文，严格执行行级隔离与外键约束。<br/>
            3. 禁止跨租户查询用户、角色或菜单配置。
          </p>
        </div>
      </section>
    </div>

    <!-- 统一 Toast 消息提示 -->
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

// Toast 状态管理
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
    // 兜底赋值方便页面正常显示当前租户信息
    tenantData.tenantCode = authStore.activeTenant;
    tenantData.tenantName = tenantData.tenantName || "华北智能工厂示范租户";
    editForm.tenantName = tenantData.tenantName;
  } finally {
    isLoading.value = false;
  }
}

/**
 * 保存租户修改
 */
async function handleSaveTenant() {
  const newName = editForm.tenantName.trim();
  if (!newName) {
    showToast("租户名称不能为空", "warning");
    return;
  }
  if (newName.length < 2) {
    showToast("租户名称至少需要 2 个字符", "warning");
    return;
  }

  isSaving.value = true;
  try {
    const res = await updateCurrentTenant({
      tenantName: newName,
      status: tenantData.status,
    });
    showToast(res.message || "租户配置已成功更新", "success");
    // 重新拉取最新数据刷新界面
    await fetchTenant();
  } catch (err: any) {
    showToast(`保存失败：${err.message || "后端服务异常"}`, "danger");
  } finally {
    isSaving.value = false;
  }
}

/**
 * 重置输入
 */
function handleResetForm() {
  editForm.tenantName = tenantData.tenantName;
}

onMounted(() => {
  fetchTenant();
});
</script>

<style scoped>
.system-view-container {
  display: flex;
  flex-direction: column;
  gap: 24px;
  max-width: 1200px;
}

.page-header {
  padding-bottom: 18px;
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

.meta-badge {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  font-size: 11px;
  padding: 2px 10px;
  border-radius: 12px;
  font-weight: 600;
}

.badge-green {
  background: rgba(111, 225, 166, 0.12);
  border: 1px solid rgba(111, 225, 166, 0.3);
  color: #6fe1a6;
}

.badge-red {
  background: rgba(255, 125, 125, 0.12);
  border: 1px solid rgba(255, 125, 125, 0.3);
  color: #ff7d7d;
}

.status-dot {
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: currentColor;
  box-shadow: 0 0 6px currentColor;
}

.page-title {
  font-size: 22px;
  font-weight: 700;
  color: #fff;
  margin: 0 0 8px;
}

.page-desc {
  font-size: 13px;
  color: var(--muted, #8ca2b8);
  margin: 0;
  line-height: 1.6;
}

.loading-state {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 32px;
  border-radius: 16px;
  background: var(--panel, rgba(10, 18, 28, 0.84));
  border: 1px solid var(--line, rgba(124, 162, 194, 0.18));
  color: var(--muted, #8ca2b8);
  font-size: 13px;
}

.spinner {
  width: 18px;
  height: 18px;
  border: 2px solid rgba(103, 210, 255, 0.2);
  border-top-color: var(--accent, #67d2ff);
  border-radius: 50%;
  animation: spin 0.8s linear infinite;
}

.error-banner {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 14px 18px;
  border-radius: 12px;
  background: rgba(255, 125, 125, 0.1);
  border: 1px solid rgba(255, 125, 125, 0.35);
  color: #ff9b9b;
  font-size: 13px;
}

.error-icon {
  font-size: 16px;
}

.error-text {
  flex: 1;
}

.btn-retry {
  padding: 4px 12px;
  border-radius: 6px;
  background: rgba(255, 125, 125, 0.2);
  border: 1px solid rgba(255, 125, 125, 0.4);
  color: #fff;
  font-size: 12px;
  cursor: pointer;
}

.content-grid {
  display: grid;
  grid-template-columns: minmax(360px, 1.2fr) minmax(300px, 1fr);
  gap: 20px;
}

.panel-card {
  border: 1px solid var(--line, rgba(124, 162, 194, 0.18));
  border-radius: 18px;
  background: var(--panel, rgba(10, 18, 28, 0.84));
  box-shadow: 0 16px 40px rgba(0, 0, 0, 0.3);
  backdrop-filter: blur(14px);
  padding: 24px;
  display: flex;
  flex-direction: column;
  gap: 20px;
}

.panel-head {
  display: flex;
  justify-content: space-between;
  align-items: center;
  border-bottom: 1px solid rgba(124, 162, 194, 0.12);
  padding-bottom: 12px;
}

.panel-title {
  font-size: 16px;
  font-weight: 700;
  color: #fff;
  margin: 0;
}

.panel-hint {
  font-size: 11px;
  font-family: monospace;
  color: var(--accent, #67d2ff);
  background: rgba(103, 210, 255, 0.08);
  padding: 2px 8px;
  border-radius: 4px;
  border: 1px solid rgba(103, 210, 255, 0.2);
}

.custom-form {
  display: flex;
  flex-direction: column;
  gap: 18px;
}

.form-item {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.form-label {
  display: flex;
  justify-content: space-between;
  align-items: center;
  font-size: 12px;
  color: #d2e4f3;
  font-weight: 600;
}

.readonly-badge {
  font-size: 10px;
  color: #ffb25e;
  background: rgba(255, 178, 94, 0.1);
  padding: 1px 6px;
  border-radius: 4px;
  border: 1px solid rgba(255, 178, 94, 0.25);
}

.char-count {
  font-size: 10px;
  color: var(--muted, #8ca2b8);
}

.req-star {
  color: #ff7d7d;
}

.form-input {
  width: 100%;
  padding: 10px 14px;
  border-radius: 8px;
  background: rgba(255, 255, 255, 0.04);
  border: 1px solid rgba(124, 162, 194, 0.25);
  color: #fff;
  font-size: 13px;
  font-family: inherit;
  outline: none;
  transition: all 0.2s ease;
}

.form-input:focus {
  border-color: var(--accent, #67d2ff);
  box-shadow: 0 0 0 2px rgba(103, 210, 255, 0.2);
}

.readonly-input {
  background: rgba(0, 0, 0, 0.35);
  border-color: rgba(124, 162, 194, 0.12);
  color: #8ca2b8;
  cursor: not-allowed;
  font-family: monospace;
}

.field-hint {
  font-size: 11px;
  color: var(--muted, #8ca2b8);
  line-height: 1.4;
}

.status-display-row {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-top: 4px;
}

.status-pill {
  padding: 4px 12px;
  border-radius: 6px;
  font-size: 12px;
  font-weight: 600;
}

.pill-active {
  background: rgba(111, 225, 166, 0.12);
  border: 1px solid rgba(111, 225, 166, 0.3);
  color: #6fe1a6;
}

.pill-disabled {
  background: rgba(255, 125, 125, 0.12);
  border: 1px solid rgba(255, 125, 125, 0.3);
  color: #ff7d7d;
}

.status-note {
  font-size: 11px;
  color: var(--muted, #8ca2b8);
}

.form-actions {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-top: 10px;
  padding-top: 16px;
  border-top: 1px solid rgba(124, 162, 194, 0.12);
}

.btn-primary {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  padding: 10px 20px;
  border-radius: 8px;
  background: linear-gradient(135deg, #1c7ba6, #0f5478);
  border: 1px solid rgba(103, 210, 255, 0.4);
  color: #fff;
  font-size: 13px;
  font-weight: 600;
  cursor: pointer;
  transition: all 0.2s ease;
}

.btn-primary:hover:not(:disabled) {
  background: linear-gradient(135deg, #2492c4, #146691);
  box-shadow: 0 0 14px rgba(103, 210, 255, 0.3);
}

.btn-primary:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}

.btn-secondary {
  padding: 10px 18px;
  border-radius: 8px;
  background: rgba(255, 255, 255, 0.04);
  border: 1px solid rgba(124, 162, 194, 0.2);
  color: #d5e3ef;
  font-size: 13px;
  cursor: pointer;
  transition: all 0.2s ease;
}

.btn-secondary:hover:not(:disabled) {
  background: rgba(255, 255, 255, 0.08);
  border-color: rgba(124, 162, 194, 0.35);
}

.btn-spinner {
  width: 14px;
  height: 14px;
  border: 2px solid rgba(255, 255, 255, 0.3);
  border-top-color: #fff;
  border-radius: 50%;
  animation: spin 0.8s linear infinite;
}

.metadata-list {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.meta-row {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 10px 14px;
  border-radius: 8px;
  background: rgba(255, 255, 255, 0.02);
  border: 1px solid rgba(124, 162, 194, 0.1);
}

.meta-label {
  font-size: 12px;
  color: var(--muted, #8ca2b8);
}

.meta-value {
  font-size: 12px;
  color: #fff;
  font-weight: 500;
}

.font-mono {
  font-family: monospace;
}

.highlight-cyan {
  color: var(--accent, #67d2ff);
  font-weight: 700;
}

.notice-box {
  padding: 16px;
  border-radius: 10px;
  background: rgba(103, 210, 255, 0.04);
  border: 1px solid rgba(103, 210, 255, 0.18);
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.notice-header {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 13px;
  color: var(--accent, #67d2ff);
}

.notice-text {
  font-size: 12px;
  color: #b0c9df;
  margin: 0;
  line-height: 1.6;
}

.notice-text code {
  font-family: monospace;
  background: rgba(0, 0, 0, 0.3);
  padding: 1px 4px;
  border-radius: 3px;
  color: #ffb25e;
}

/* Toast */
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

@keyframes spin {
  to {
    transform: rotate(360deg);
  }
}

@media (max-width: 900px) {
  .content-grid {
    grid-template-columns: 1fr;
  }
}
</style>
