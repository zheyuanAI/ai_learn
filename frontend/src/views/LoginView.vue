<template>
  <div class="login-page">
    <div class="login-container">
      <!-- 页面顶部 Header -->
      <header class="console-topbar">
        <div class="console-title-block">
          <div class="console-title-meta">
            <span class="console-module-code">AUTH / PLATFORM LOGIN &amp; SESSION</span>
            <span class="console-live-mark"><i aria-hidden="true"></i> 租户与单会话</span>
          </div>
          <h1 class="page-main-title">系统登录与认证上下文</h1>
          <p class="page-sub-title">
            承接租户识别、用户鉴权、角色权限与菜单加载。执行“后登录替换前登录”的单会话规则，旧 JWT 再次调用返回 401。
          </p>
        </div>
      </header>

      <!-- 登录核心工作区：左侧表单 + 右侧认证事实 -->
      <section class="console-layout" aria-label="登录与会话工作台">
        <!-- 左侧：登录表单卡片 -->
        <div class="console-panel-box login-form-panel">
          <div>
            <span class="console-section-index">用户登录</span>
            <h2 class="panel-heading">统一身份认证</h2>
          </div>

          <form class="login-form" @submit.prevent="handleLoginSubmit">
            <div class="form-field">
              <label for="loginTenant">企业租户编码 (Tenant Code) <b class="req-star">*</b></label>
              <select id="loginTenant" v-model="formData.tenantCode" class="form-input form-select" @change="onTenantChange">
                <option value="tenant_demo_a">tenant_demo_a (华北智能工厂示范租户)</option>
                <option value="tenant_demo_b">tenant_demo_b (华东高精装备制造租户)</option>
              </select>
            </div>

            <div class="form-field">
              <label for="loginUsername">账号用户名 (Username) <b class="req-star">*</b></label>
              <input
                id="loginUsername"
                v-model="formData.username"
                type="text"
                class="form-input"
                placeholder="请输入用户名"
                required
                @input="onUsernameInput"
              />
            </div>

            <div class="form-field">
              <label for="loginPassword">登录密码 (Password) <b class="req-star">*</b></label>
              <input
                id="loginPassword"
                v-model="formData.password"
                type="password"
                class="form-input"
                placeholder="请输入登录密码"
                required
              />
            </div>

            <button type="submit" class="login-submit-btn" :disabled="isLoading">
              <span class="btn-text">{{ isLoading ? "正在登录..." : "登录进入系统" }}</span>
              <span class="btn-perm-tag">POST /api/auth/login</span>
            </button>
          </form>

          <!-- 6 个正式业务角色快捷切换卡片 -->
          <div class="quick-role-section">
            <span class="quick-role-title">快速切换演示角色登录（6 类正式角色）：</span>
            <div class="quick-role-grid">
              <button
                v-for="(profile, uname) in ROLE_PRESETS"
                :key="uname"
                type="button"
                class="role-quick-btn"
                :class="{ 'is-active': formData.username === uname }"
                @click="quickSelectRole(uname)"
              >
                <span class="role-quick-name">{{ profile.roleName }}</span>
                <span class="role-quick-user">{{ uname }}</span>
              </button>
            </div>
          </div>
        </div>

        <!-- 右侧：会话机制与权限说明 -->
        <aside class="console-detail session-detail-panel">
          <div>
            <div class="detail-header-meta">
              <span class="console-module-code">SECURITY &amp; JWT SESSION</span>
              <span
                class="session-badge"
                :class="authStore.isSessionValid ? 'badge-green' : 'badge-red'"
              >
                {{ authStore.isSessionValid ? "会话正常 (Active)" : "已失效 (401 Displaced)" }}
              </span>
            </div>
            <h2 class="detail-heading">当前认证上下文与会话事实</h2>
            <p class="detail-desc">
              服务端在 Redis 维护最新 jti，当同一账号在另一设备登录时，新 jti 替换旧 jti。
            </p>
          </div>

          <!-- 事实网格 -->
          <div class="fact-grid">
            <div class="fact-item">
              <span class="fact-label">当前生效租户</span>
              <span class="fact-value highlight-cyan">{{ formData.tenantCode }}</span>
            </div>
            <div class="fact-item">
              <span class="fact-label">当前登录用户</span>
              <span class="fact-value">{{ currentProfile?.realName }} ({{ formData.username }})</span>
            </div>
            <div class="fact-item">
              <span class="fact-label">生效角色</span>
              <span class="fact-value highlight-warm">{{ currentProfile?.roleName || "未指定角色" }}</span>
            </div>
            <div class="fact-item">
              <span class="fact-label">当前 JWT 会话标识 (jti)</span>
              <span class="fact-value font-mono highlight-blue">
                {{ authStore.isSessionValid ? (currentProfile?.jti || "jti_pending") : "jti_INVALIDATED_401" }}
              </span>
            </div>
          </div>

          <!-- 单会话顶替机制模拟演示 -->
          <div class="kick-demo-card">
            <div class="kick-demo-header">
              <strong>单会话顶替机制模拟演示</strong>
              <button
                type="button"
                class="kick-action-btn"
                @click="simulateKickOut"
              >
                <span>⚠️ 模拟新设备登录（踢出当前会话）</span>
              </button>
            </div>
            <p class="kick-demo-desc">
              模拟另一个客户端使用相同账号成功登录并获取新 jti。当前页面下一次请求将被服务端 401 拦截并弹出失效警告。
            </p>
          </div>

          <!-- 已加载菜单与功能权限树 -->
          <section class="perm-tree-section">
            <div class="perm-tree-head">
              <h3>已加载菜单与功能权限树 (Menu Permissions)</h3>
              <small>根据 Auth 服务加载当前用户权限点</small>
            </div>
            <div class="perm-tag-list">
              <div
                v-for="perm in currentProfile?.permissions || []"
                :key="perm"
                class="perm-tag-item"
              >
                🔑 <code>{{ perm }}</code>
              </div>
            </div>
          </section>
        </aside>
      </section>

      <!-- 底部声明 -->
      <footer class="console-disclaimer">
        <span class="disclaimer-tag">PRODUCTION / AUTHENTICATED WORKSPACE</span>
        <p>基于 Vue 3 + Pinia + Axios 真实前后端链路，承载 6 类正式角色鉴权与动态菜单流转。</p>
      </footer>
    </div>

    <!-- 统一 Toast 提示 -->
    <div
      v-if="toast.visible"
      class="console-toast"
      :class="[`is-${toast.type}`, { 'is-visible': toast.visible }]"
      role="status"
    >
      {{ toast.message }}
    </div>
  </div>
</template>

<script setup lang="ts">
import { reactive, ref, computed } from "vue";
import { useRouter, useRoute } from "vue-router";
import { useAuthStore, ROLE_PRESETS } from "../stores/auth";

const router = useRouter();
const route = useRoute();
const authStore = useAuthStore();

const isLoading = ref(false);

const formData = reactive({
  tenantCode: authStore.activeTenant || "tenant_demo_a",
  username: "admin.zhang",
  password: "",
});

const toast = reactive({
  visible: false,
  message: "",
  type: "success",
});

let toastTimer: any = null;

/**
 * 弹出提示消息
 * 入参为消息文本与类型，出参无
 */
function showToast(message: string, type: "success" | "danger" | "warning" = "success") {
  if (toastTimer) clearTimeout(toastTimer);
  toast.message = message;
  toast.type = type;
  toast.visible = true;
  toastTimer = setTimeout(() => {
    toast.visible = false;
  }, 3600);
}

/**
 * 计算当前选中账号的角色预设信息
 */
const currentProfile = computed(() => {
  const uname = formData.username.toLowerCase().trim();
  return ROLE_PRESETS[uname] || ROLE_PRESETS["admin.zhang"];
});

/**
 * 租户下拉切换
 */
function onTenantChange() {
  authStore.activeTenant = formData.tenantCode;
  showToast(`已切换租户：${formData.tenantCode}`);
}

/**
 * 用户名输入响应
 */
function onUsernameInput() {
  authStore.isSessionValid = true;
}

/**
 * 快捷选择 6 类演示角色
 */
function quickSelectRole(username: string) {
  formData.username = username;
  formData.password = "123456";
  authStore.isSessionValid = true;
  showToast(`已选择角色：${username}，已自动填入演示密码 123456`);
}

/**
 * 提交登录表单
 */
async function handleLoginSubmit() {
  if (!formData.tenantCode) {
    showToast("登录失败：租户编码不能为空", "danger");
    return;
  }
  if (!formData.username) {
    showToast("登录失败：请输入登录账号用户名", "danger");
    return;
  }
  if (!formData.password) {
    showToast("登录失败：请输入登录密码", "danger");
    return;
  }

  isLoading.value = true;
  try {
    const res = await authStore.loginAction({
      tenantCode: formData.tenantCode,
      username: formData.username,
      password: formData.password,
    });

    const realName = res.user?.realName || formData.username;
    const role = res.user?.roles?.[0] || "";
    showToast(`登录成功！当前身份：${realName}${role ? '（' + role + '）' : ''}，正在进入系统...`, "success");

    // 获取目标跳转地址
    const redirectUrl = (route.query.redirect as string) || currentProfile.value.redirect || "/";
    setTimeout(() => {
      router.push({ path: redirectUrl });
    }, 500);
  } catch (error: any) {
    showToast(`登录失败：${error.message || "服务异常"}`, "danger");
  } finally {
    isLoading.value = false;
  }
}

/**
 * 模拟单会话被新客户端顶替
 */
function simulateKickOut() {
  authStore.simulateSessionKicked();
  showToast("⚠️ 401 告警：该账号已在另一客户端登录，旧会话已失效！", "danger");
}
</script>

<style scoped>
.login-page {
  min-height: 100vh;
  padding: 32px;
  display: flex;
  justify-content: center;
  align-items: flex-start;
  box-sizing: border-box;
}

.login-container {
  width: 100%;
  max-width: 1280px;
}

.console-topbar {
  margin-bottom: 24px;
  padding-bottom: 18px;
  border-bottom: 1px solid var(--line, rgba(124, 162, 194, 0.18));
}

.console-title-meta {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 8px;
}

.console-module-code {
  font-size: 11px;
  letter-spacing: 0.16em;
  color: var(--accent, #67d2ff);
  font-weight: 600;
  text-transform: uppercase;
}

.console-live-mark {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  font-size: 11px;
  color: #6fe1a6;
  padding: 2px 8px;
  background: rgba(111, 225, 166, 0.1);
  border-radius: 4px;
  border: 1px solid rgba(111, 225, 166, 0.2);
}

.console-live-mark i {
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: #6fe1a6;
  box-shadow: 0 0 6px #6fe1a6;
}

.page-main-title {
  font-size: 24px;
  font-weight: 700;
  margin: 0 0 8px;
  color: #fff;
}

.page-sub-title {
  font-size: 13px;
  color: var(--muted, #8ca2b8);
  margin: 0;
  line-height: 1.6;
}

.console-layout {
  display: grid;
  grid-template-columns: minmax(360px, 440px) minmax(0, 1fr);
  gap: 24px;
  margin-top: 18px;
}

.console-panel-box,
.console-detail {
  border: 1px solid var(--line, rgba(124, 162, 194, 0.18));
  border-radius: 20px;
  background: var(--panel, rgba(10, 18, 28, 0.84));
  box-shadow: 0 18px 60px rgba(0, 0, 0, 0.35);
  backdrop-filter: blur(16px);
}

.login-form-panel {
  padding: 26px;
  display: flex;
  flex-direction: column;
  gap: 20px;
}

.console-section-index {
  font-size: 11px;
  letter-spacing: 0.12em;
  color: var(--accent, #67d2ff);
  text-transform: uppercase;
}

.panel-heading {
  margin: 6px 0 0;
  font-size: 20px;
  color: #fff;
}

.login-form {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.form-field {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.form-field label {
  font-size: 12px;
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
  transition: border-color 0.2s ease, box-shadow 0.2s ease;
  box-sizing: border-box;
}

.form-input:focus {
  border-color: var(--accent, #67d2ff);
  box-shadow: 0 0 0 2px rgba(103, 210, 255, 0.2);
}

.form-select {
  cursor: pointer;
}

.form-select option {
  background: #0d1723;
  color: #fff;
}

.login-submit-btn {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: 12px;
  min-height: 48px;
  margin-top: 8px;
  border-radius: 10px;
  background: linear-gradient(135deg, #1c7ba6, #0f5478);
  border: 1px solid rgba(103, 210, 255, 0.4);
  color: #fff;
  cursor: pointer;
  transition: all 0.2s ease;
}

.login-submit-btn:hover:not(:disabled) {
  background: linear-gradient(135deg, #2492c4, #146691);
  box-shadow: 0 0 16px rgba(103, 210, 255, 0.35);
}

.login-submit-btn:disabled {
  opacity: 0.6;
  cursor: not-allowed;
}

.btn-text {
  font-size: 14px;
  font-weight: 700;
  letter-spacing: 0.04em;
}

.btn-perm-tag {
  font-size: 10px;
  color: #92d4f8;
  font-family: monospace;
  margin-top: 2px;
}

.quick-role-section {
  border-top: 1px solid var(--line, rgba(124, 162, 194, 0.18));
  padding-top: 16px;
}

.quick-role-title {
  font-size: 11px;
  color: var(--muted, #8ca2b8);
  display: block;
  margin-bottom: 10px;
}

.quick-role-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 8px;
}

.role-quick-btn {
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  padding: 8px 10px;
  border-radius: 8px;
  background: rgba(255, 255, 255, 0.03);
  border: 1px solid rgba(124, 162, 194, 0.18);
  color: var(--text, #ebf3fb);
  cursor: pointer;
  transition: all 0.15s ease;
  text-align: left;
}

.role-quick-btn:hover {
  background: rgba(103, 210, 255, 0.1);
  border-color: rgba(103, 210, 255, 0.4);
}

.role-quick-btn.is-active {
  background: rgba(103, 210, 255, 0.14);
  border-color: var(--accent, #67d2ff);
}

.role-quick-name {
  font-size: 12px;
  font-weight: 600;
}

.role-quick-user {
  font-size: 10px;
  color: var(--muted, #8ca2b8);
  margin-top: 2px;
}

.session-detail-panel {
  padding: 26px;
  display: flex;
  flex-direction: column;
  gap: 20px;
}

.detail-header-meta {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 6px;
}

.session-badge {
  font-size: 11px;
  padding: 3px 10px;
  border-radius: 12px;
  font-weight: 600;
}

.badge-green {
  background: rgba(111, 225, 166, 0.15);
  border: 1px solid rgba(111, 225, 166, 0.35);
  color: #6fe1a6;
}

.badge-red {
  background: rgba(255, 125, 125, 0.15);
  border: 1px solid rgba(255, 125, 125, 0.35);
  color: #ff7d7d;
}

.detail-heading {
  margin: 4px 0 0;
  font-size: 20px;
  color: #fff;
}

.detail-desc {
  margin: 6px 0 0;
  font-size: 12px;
  color: var(--muted, #8ca2b8);
  line-height: 1.6;
}

.fact-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 12px;
}

.fact-item {
  padding: 12px 14px;
  border-radius: 10px;
  background: rgba(255, 255, 255, 0.02);
  border: 1px solid rgba(124, 162, 194, 0.12);
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.fact-label {
  font-size: 11px;
  color: var(--muted, #8ca2b8);
}

.fact-value {
  font-size: 13px;
  font-weight: 600;
  color: #fff;
  word-break: break-all;
}

.highlight-cyan {
  color: var(--accent, #67d2ff);
}

.highlight-warm {
  color: var(--warm, #ffb25e);
}

.highlight-blue {
  color: #71e1dc;
}

.font-mono {
  font-family: monospace;
}

.kick-demo-card {
  padding: 16px;
  border-radius: 10px;
  background: rgba(255, 178, 94, 0.05);
  border: 1px solid rgba(255, 178, 94, 0.25);
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.kick-demo-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 12px;
}

.kick-demo-header strong {
  font-size: 13px;
  color: #fff;
}

.kick-action-btn {
  padding: 6px 12px;
  border-radius: 6px;
  background: rgba(255, 178, 94, 0.15);
  border: 1px solid rgba(255, 178, 94, 0.4);
  color: var(--warm, #ffb25e);
  font-size: 12px;
  cursor: pointer;
  transition: all 0.2s ease;
  white-space: nowrap;
}

.kick-action-btn:hover {
  background: rgba(255, 178, 94, 0.28);
  border-color: rgba(255, 178, 94, 0.7);
}

.kick-demo-desc {
  margin: 0;
  font-size: 11px;
  color: var(--muted, #8ca2b8);
  line-height: 1.6;
}

.perm-tree-section {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.perm-tree-head h3 {
  margin: 0;
  font-size: 13px;
  color: #fff;
}

.perm-tree-head small {
  font-size: 11px;
  color: var(--muted, #8ca2b8);
}

.perm-tag-list {
  display: flex;
  flex-direction: column;
  gap: 6px;
  max-height: 220px;
  overflow-y: auto;
}

.perm-tag-item {
  padding: 6px 10px;
  border-radius: 6px;
  background: rgba(113, 225, 220, 0.05);
  border: 1px solid rgba(113, 225, 220, 0.18);
  font-family: monospace;
  font-size: 11px;
  color: #71e1dc;
}

.console-disclaimer {
  margin-top: 24px;
  padding: 16px 20px;
  border-radius: 12px;
  background: rgba(255, 255, 255, 0.02);
  border: 1px solid var(--line, rgba(124, 162, 194, 0.14));
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.disclaimer-tag {
  font-size: 10px;
  letter-spacing: 0.12em;
  color: var(--accent, #67d2ff);
  font-weight: 700;
}

.console-disclaimer p {
  margin: 0;
  font-size: 12px;
  color: var(--muted, #8ca2b8);
}

.console-toast {
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
}

.console-toast.is-visible {
  opacity: 1;
  transform: translateY(0);
  pointer-events: auto;
}

.console-toast.is-success {
  background: #0d281e;
  border: 1px solid #6fe1a6;
  color: #6fe1a6;
}

.console-toast.is-danger {
  background: #2b1114;
  border: 1px solid #ff7d7d;
  color: #ff9b9b;
}

.console-toast.is-warning {
  background: #2c2010;
  border: 1px solid #ffb25e;
  color: #ffb25e;
}

@media (max-width: 960px) {
  .console-layout {
    grid-template-columns: 1fr;
  }
  .fact-grid {
    grid-template-columns: 1fr;
  }
}
</style>
