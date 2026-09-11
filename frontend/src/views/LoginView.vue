<template>
  <div class="cloud-login-page">
    <!-- 动态高科技背景：Canvas 星空粒子 + 3D 空间坐标网格 + SVG 激光能量光流 -->
    <canvas ref="particleCanvas" class="bg-particle-canvas"></canvas>
    <div class="cyber-perspective-grid" aria-hidden="true"></div>
    <div class="laser-energy-ribbons" aria-hidden="true">
      <svg class="laser-svg" viewBox="0 0 1440 900" fill="none" preserveAspectRatio="none">
        <defs>
          <linearGradient id="laserGrad1" x1="0%" y1="0%" x2="100%" y2="100%">
            <stop offset="0%" stop-color="#0284c7" stop-opacity="0" />
            <stop offset="30%" stop-color="#38bdf8" stop-opacity="0.6" />
            <stop offset="70%" stop-color="#818cf8" stop-opacity="0.4" />
            <stop offset="100%" stop-color="#c084fc" stop-opacity="0" />
          </linearGradient>
          <linearGradient id="laserGrad2" x1="100%" y1="0%" x2="0%" y2="100%">
            <stop offset="0%" stop-color="#38bdf8" stop-opacity="0" />
            <stop offset="40%" stop-color="#06b6d4" stop-opacity="0.5" />
            <stop offset="80%" stop-color="#3b82f6" stop-opacity="0.3" />
            <stop offset="100%" stop-color="#1d4ed8" stop-opacity="0" />
          </linearGradient>
          <filter id="laserGlow" x="-20%" y="-20%" width="140%" height="140%">
            <feGaussianBlur stdDeviation="8" result="blur" />
            <feMerge>
              <feMergeNode in="blur" />
              <feMergeNode in="blur" />
              <feMergeNode in="SourceGraphic" />
            </feMerge>
          </filter>
        </defs>
        <path
          d="M-100,550 C300,320 700,750 1200,420 C1400,280 1550,310 1600,350"
          stroke="url(#laserGrad1)"
          stroke-width="3"
          filter="url(#laserGlow)"
          class="pulse-laser-1"
        />
        <path
          d="M-50,680 C350,480 650,820 1150,560 C1380,440 1520,490 1600,520"
          stroke="url(#laserGrad2)"
          stroke-width="1.8"
          stroke-dasharray="12 6"
          filter="url(#laserGlow)"
          class="pulse-laser-2"
        />
      </svg>
    </div>

    <!-- 顶部状态栏（仅保留极简纯净的品牌 Logo） -->
    <header class="cloud-topbar">
      <div class="brand-block">
        <div class="brand-logo-hex">
          <span class="brand-logo-core">AI</span>
        </div>
        <div class="brand-text">
          <span class="brand-name">AI Learn 智能协同智造中枢</span>
          <span class="brand-sub">ENTERPRISE CLOUD PLATFORM · WMS &amp; MES</span>
        </div>
      </div>
    </header>

    <!-- 401 动态会话过期感知警示条 -->
    <transition name="fade-slide">
      <div v-if="show401Alert" class="alert-banner-401" role="alert">
        <div class="alert-icon-wrap">⚠️</div>
        <div class="alert-content">
          <strong class="alert-title">会话状态已失效 (401 Displaced / Session Expired)</strong>
          <span class="alert-desc">
            检测到单会话已被新设备顶替或认证令牌已超时。请重新输入账号凭证接入系统。
          </span>
        </div>
        <button type="button" class="alert-close-btn" @click="dismiss401Alert" title="关闭提示">✕</button>
      </div>
    </transition>

    <!-- 核心工作区：居中悬浮黑曜石主控制舱 -->
    <main class="cloud-main-layout">
      <section class="login-center-console" aria-label="统一身份认证主控制舱">
        <div class="console-card-header">
          <div class="card-chip">AUTH // 统一身份认证</div>
          <h2 class="card-main-title">欢迎登录云端控制台</h2>
          <p class="card-sub-title">承接多租户识别、鉴权通行、动态权限与单会话追踪</p>
        </div>

        <form class="login-form" @submit.prevent="handleLoginSubmit">
          <!-- 租户选择 -->
          <div class="form-group">
            <label for="loginTenant" class="field-label">
              <span>企业租户编码 (Tenant Code)</span>
              <span class="required-star">*</span>
            </label>
            <el-select
              id="loginTenant"
              v-model="formData.tenantCode"
              class="tech-select"
              popper-class="tech-select-popper"
              @change="onTenantChange"
            >
              <el-option label="tenant_demo_a (华北智能工厂示范租户)" value="tenant_demo_a" />
              <el-option label="tenant_demo_b (华东高精装备制造租户)" value="tenant_demo_b" />
            </el-select>
          </div>

          <!-- 用户账号（合并快速体验角色选择 + 手动输入自由填写） -->
          <div class="form-group username-merged-group">
            <div class="field-label-row">
              <label for="loginUsername" class="field-label">
                <span>账号用户名 (Username)</span>
                <span class="required-star">*</span>
              </label>
              <span class="quick-tip-badge">可手动填写或点击下方角色快速填报</span>
            </div>

            <!-- 手动输入框 -->
            <el-input
              id="loginUsername"
              v-model="formData.username"
              class="tech-input"
              placeholder="请输入用户名或从下方快速选择"
              clearable
              @input="onUsernameInput"
            >
              <template #prefix>
                <span class="input-icon">👤</span>
              </template>
            </el-input>

            <!-- 与用户名直接合并的 6 类正式角色微型快捷胶囊 -->
            <div class="merged-role-selector" aria-label="正式角色快捷填报">
              <button
                v-for="(profile, uname) in ROLE_PRESETS"
                :key="uname"
                type="button"
                class="role-mini-chip"
                :class="{ 'is-active': formData.username.toLowerCase().trim() === uname.toLowerCase() }"
                @click="quickSelectRole(uname)"
                :title="`点击填入角色: ${profile.roleName} (${uname})`"
              >
                <span class="chip-dot"></span>
                <span class="chip-role-name">{{ profile.roleName }}</span>
              </button>
            </div>
          </div>

          <!-- 访问密码 -->
          <div class="form-group">
            <label for="loginPassword" class="field-label">
              <span>登录密码 (Password)</span>
              <span class="required-star">*</span>
            </label>
            <el-input
              id="loginPassword"
              v-model="formData.password"
              type="password"
              class="tech-input"
              show-password
              placeholder="请输入登录密码"
            >
              <template #prefix>
                <span class="input-icon">🔒</span>
              </template>
            </el-input>
          </div>

          <!-- 登录按钮 -->
          <el-button
            type="primary"
            size="large"
            native-type="submit"
            class="tech-submit-btn"
            :loading="isLoading"
          >
            <span class="submit-text">{{ isLoading ? "正在鉴权验证..." : "登录进入系统" }}</span>
            <span class="submit-api-tag">POST /api/auth/login</span>
          </el-button>
        </form>

        <!-- 底部微状态条 -->
        <div class="console-card-footer">
          <span class="sec-item">
            <i class="sec-dot green"></i> Redis 单一会话指纹监听
          </span>
          <span class="sec-divider">|</span>
          <span class="sec-item">
            <i class="sec-icon">🛡️</i> TLS 256-bit 加密
          </span>
        </div>
      </section>
    </main>

    <!-- 底部架构声明 -->
    <footer class="cloud-footer">
      <span class="footer-badge">PRODUCTION / AUTHENTICATED WORKSPACE</span>
      <span class="footer-text">
        基于 Vue 3 + Pinia + Axios 真实前后端链路，承载 6 类正式角色鉴权与动态菜单流转。
      </span>
    </footer>

    <!-- 统一 Toast 提示 -->
    <div
      v-if="toast.visible"
      class="cloud-toast"
      :class="[`toast-${toast.type}`, { 'is-visible': toast.visible }]"
      role="status"
    >
      {{ toast.message }}
    </div>
  </div>
</template>

<script setup lang="ts">
import { reactive, ref, computed, onMounted, onUnmounted } from "vue";
import { useRouter, useRoute } from "vue-router";
import { ElMessage } from "element-plus";
import { useAuthStore, ROLE_PRESETS } from "../stores/auth";

const router = useRouter();
const route = useRoute();
const authStore = useAuthStore();

const isLoading = ref(false);
const show401Alert = ref(false);
const particleCanvas = ref<HTMLCanvasElement | null>(null);

// 表单响应式数据
const formData = reactive({
  tenantCode: authStore.activeTenant || "tenant_demo_a",
  username: "admin.zhang",
  password: "",
});

// Toast 状态管理
const toast = reactive({
  visible: false,
  message: "",
  type: "success",
});

let toastTimer: any = null;
let animationFrameId: number | null = null;

/**
 * 弹出提示消息
 * 中文注释：入参为消息文本与消息类型，同步调用 ElMessage 与本地微型 Toast 组件
 */
function showToast(message: string, type: "success" | "danger" | "warning" = "success") {
  ElMessage({
    message,
    type: type === "danger" ? "error" : type,
  });
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
 * 关闭 401 提示条
 */
function dismiss401Alert() {
  show401Alert.value = false;
}

/**
 * 租户下拉切换
 */
function onTenantChange() {
  authStore.activeTenant = formData.tenantCode;
  showToast(`已切换企业租户：${formData.tenantCode}`);
}

/**
 * 用户名输入响应
 */
function onUsernameInput() {
  authStore.isSessionValid = true;
}

/**
 * 快捷选择 6 类演示角色
 * 中文注释：点击预设角色芯片，秒级填入对应的系统用户名与演示密码 123456，同时点亮对应芯片
 */
function quickSelectRole(username: string) {
  formData.username = username;
  formData.password = "123456";
  authStore.isSessionValid = true;
  show401Alert.value = false;
  showToast(`已选择角色：${ROLE_PRESETS[username]?.roleName || username}，已自动填入用户名及演示密码 123456`);
}

/**
 * 提交登录表单
 */
async function handleLoginSubmit() {
  if (!formData.tenantCode) {
    showToast("登录失败：企业租户编码不能为空", "danger");
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
    }, 450);
  } catch (error: any) {
    showToast(`登录失败：${error.message || "服务异常"}`, "danger");
  } finally {
    isLoading.value = false;
  }
}

/**
 * 初始化轻量 Canvas 星空与微光粒子（耗能极低，自适应屏幕）
 */
function initParticles() {
  const canvas = particleCanvas.value;
  if (!canvas) return;
  const ctx = canvas.getContext("2d");
  if (!ctx) return;

  let width = (canvas.width = window.innerWidth);
  let height = (canvas.height = window.innerHeight);

  const handleResize = () => {
    if (!canvas) return;
    width = canvas.width = window.innerWidth;
    height = canvas.height = window.innerHeight;
  };
  window.addEventListener("resize", handleResize);

  // 粒子集
  const count = Math.min(Math.floor((width * height) / 18000), 60);
  const particles: Array<{
    x: number;
    y: number;
    vx: number;
    vy: number;
    size: number;
    alpha: number;
  }> = [];

  for (let i = 0; i < count; i++) {
    particles.push({
      x: Math.random() * width,
      y: Math.random() * height,
      vx: (Math.random() - 0.5) * 0.35,
      vy: (Math.random() - 0.5) * 0.35,
      size: Math.random() * 1.8 + 0.6,
      alpha: Math.random() * 0.6 + 0.2,
    });
  }

  function render() {
    if (!ctx) return;
    ctx.clearRect(0, 0, width, height);

    // 绘制粒子与微妙连线
    for (let i = 0; i < particles.length; i++) {
      const p = particles[i];
      p.x += p.vx;
      p.y += p.vy;
      if (p.x < 0) p.x = width;
      if (p.x > width) p.x = 0;
      if (p.y < 0) p.y = height;
      if (p.y > height) p.y = 0;

      ctx.beginPath();
      ctx.arc(p.x, p.y, p.size, 0, Math.PI * 2);
      ctx.fillStyle = `rgba(56, 189, 248, ${p.alpha})`;
      ctx.fill();

      for (let j = i + 1; j < particles.length; j++) {
        const p2 = particles[j];
        const dx = p.x - p2.x;
        const dy = p.y - p2.y;
        const dist = Math.sqrt(dx * dx + dy * dy);
        if (dist < 100) {
          ctx.beginPath();
          ctx.moveTo(p.x, p.y);
          ctx.lineTo(p2.x, p2.y);
          ctx.strokeStyle = `rgba(56, 189, 248, ${0.12 * (1 - dist / 100)})`;
          ctx.lineWidth = 0.6;
          ctx.stroke();
        }
      }
    }

    animationFrameId = requestAnimationFrame(render);
  }

  render();
}

onMounted(() => {
  // 检查 URL 是否带 401 会话失效标记
  if (route.query.reason === "401" || !authStore.isSessionValid) {
    show401Alert.value = true;
  }

  initParticles();
});

onUnmounted(() => {
  if (animationFrameId !== null) {
    cancelAnimationFrame(animationFrameId);
  }
  if (toastTimer) {
    clearTimeout(toastTimer);
  }
});
</script>

<style scoped>
/* ================= 全局页面与高科技背景 ================= */
.cloud-login-page {
  position: relative;
  min-height: 100vh;
  width: 100%;
  background: radial-gradient(circle at 50% 25%, #0a1630 0%, #030712 75%, #01040a 100%);
  color: #f1f5f9;
  font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, "Helvetica Neue", sans-serif;
  overflow-x: hidden;
  box-sizing: border-box;
  display: flex;
  flex-direction: column;
  justify-content: space-between;
}

/* Canvas 粒子层 */
.bg-particle-canvas {
  position: absolute;
  top: 0;
  left: 0;
  width: 100%;
  height: 100%;
  pointer-events: none;
  z-index: 1;
}

/* 3D 空间坐标网格地平线 */
.cyber-perspective-grid {
  position: absolute;
  bottom: 0;
  left: 0;
  width: 100%;
  height: 50%;
  background-image:
    linear-gradient(to right, rgba(56, 189, 248, 0.12) 1px, transparent 1px),
    linear-gradient(to bottom, rgba(56, 189, 248, 0.12) 1px, transparent 1px);
  background-size: 60px 60px;
  transform: perspective(480px) rotateX(68deg);
  transform-origin: bottom center;
  mask-image: linear-gradient(to top, rgba(0, 0, 0, 1) 15%, rgba(0, 0, 0, 0) 90%);
  -webkit-mask-image: linear-gradient(to top, rgba(0, 0, 0, 1) 15%, rgba(0, 0, 0, 0) 90%);
  pointer-events: none;
  z-index: 2;
}

/* 激光流束 SVG */
.laser-energy-ribbons {
  position: absolute;
  top: 0;
  left: 0;
  width: 100%;
  height: 100%;
  pointer-events: none;
  z-index: 2;
  overflow: hidden;
}

.laser-svg {
  width: 100%;
  height: 100%;
  opacity: 0.85;
}

.pulse-laser-1 {
  animation: laserPulse1 8s ease-in-out infinite alternate;
}

.pulse-laser-2 {
  animation: laserPulse2 10s ease-in-out infinite alternate;
}

@keyframes laserPulse1 {
  0% { opacity: 0.55; transform: translateY(0px) scaleY(1); }
  100% { opacity: 0.95; transform: translateY(-15px) scaleY(1.05); }
}

@keyframes laserPulse2 {
  0% { opacity: 0.4; stroke-dashoffset: 0; }
  100% { opacity: 0.8; stroke-dashoffset: 120; }
}

/* ================= 顶部状态栏 ================= */
.cloud-topbar {
  position: relative;
  z-index: 10;
  display: flex;
  justify-content: flex-start;
  align-items: center;
  padding: 20px 36px;
  border-bottom: 1px solid rgba(56, 189, 248, 0.15);
  background: rgba(3, 7, 18, 0.55);
  backdrop-filter: blur(14px);
}

.brand-block {
  display: flex;
  align-items: center;
  gap: 14px;
}

.brand-logo-hex {
  width: 38px;
  height: 38px;
  background: linear-gradient(135deg, #0284c7, #0369a1);
  border: 1px solid #38bdf8;
  border-radius: 10px;
  display: flex;
  align-items: center;
  justify-content: center;
  box-shadow: 0 0 16px rgba(56, 189, 248, 0.45);
}

.brand-logo-core {
  font-weight: 900;
  font-size: 15px;
  color: #ffffff;
  letter-spacing: 0.5px;
}

.brand-text {
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.brand-name {
  font-size: 16px;
  font-weight: 700;
  color: #ffffff;
  letter-spacing: 0.5px;
}

.brand-sub {
  font-size: 10px;
  color: #38bdf8;
  letter-spacing: 0.14em;
  font-weight: 600;
}

/* ================= 401 动态会话感知警示条 ================= */
.alert-banner-401 {
  position: relative;
  z-index: 15;
  margin: 16px auto 0;
  max-width: 520px;
  width: calc(100% - 48px);
  background: linear-gradient(90deg, rgba(245, 158, 11, 0.16) 0%, rgba(217, 119, 6, 0.1) 100%);
  border: 1px solid rgba(245, 158, 11, 0.5);
  border-radius: 10px;
  padding: 10px 18px;
  display: flex;
  align-items: center;
  gap: 14px;
  box-shadow: 0 0 24px rgba(245, 158, 11, 0.25);
  backdrop-filter: blur(8px);
}

.alert-icon-wrap {
  font-size: 18px;
  flex-shrink: 0;
}

.alert-content {
  flex: 1;
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.alert-title {
  color: #fbbf24;
  font-size: 13px;
  font-weight: 700;
}

.alert-desc {
  color: #fde68a;
  font-size: 12px;
  opacity: 0.9;
}

.alert-close-btn {
  background: transparent;
  border: none;
  color: #fbbf24;
  font-size: 14px;
  cursor: pointer;
  padding: 4px;
  opacity: 0.8;
  transition: opacity 0.15s;
}

.alert-close-btn:hover {
  opacity: 1;
}

/* ================= 核心工作区：完美居中主控制舱 ================= */
.cloud-main-layout {
  position: relative;
  z-index: 10;
  display: flex;
  justify-content: center;
  align-items: center;
  padding: 36px 24px 24px;
  max-width: 1280px;
  margin: 0 auto;
  width: 100%;
  flex: 1;
  box-sizing: border-box;
}

/* 居中黑曜石主控制舱 */
.login-center-console {
  width: 100%;
  max-width: 490px;
  background: rgba(8, 16, 33, 0.84);
  border: 1px solid rgba(56, 189, 248, 0.35);
  border-radius: 20px;
  padding: 32px;
  box-shadow: 0 20px 60px rgba(0, 0, 0, 0.85), 0 0 40px rgba(56, 189, 248, 0.12);
  backdrop-filter: blur(22px);
  display: flex;
  flex-direction: column;
  gap: 22px;
}

.console-card-header {
  border-bottom: 1px solid rgba(56, 189, 248, 0.15);
  padding-bottom: 16px;
}

.card-chip {
  display: inline-block;
  font-size: 11px;
  color: #38bdf8;
  font-weight: 600;
  letter-spacing: 0.12em;
  margin-bottom: 6px;
}

.card-main-title {
  font-size: 21px;
  font-weight: 700;
  color: #ffffff;
  margin: 0 0 4px;
}

.card-sub-title {
  font-size: 12px;
  color: #94a3b8;
  margin: 0;
}

/* 表单组件 */
.login-form {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.form-group {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.field-label {
  font-size: 12px;
  color: #cbd5e1;
  font-weight: 500;
  display: flex;
  align-items: center;
  gap: 4px;
}

.field-label-row {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.quick-tip-badge {
  font-size: 11px;
  color: #64748b;
}

.required-star {
  color: #f87171;
}

.input-icon {
  font-size: 13px;
  opacity: 0.7;
  margin-right: 4px;
}

/* 用户名与角色合并选择器 */
.username-merged-group {
  gap: 8px;
}

.merged-role-selector {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  margin-top: 2px;
}

.role-mini-chip {
  background: rgba(15, 23, 42, 0.75);
  border: 1px solid rgba(56, 189, 248, 0.22);
  border-radius: 6px;
  padding: 4px 10px;
  cursor: pointer;
  display: inline-flex;
  align-items: center;
  gap: 6px;
  transition: all 0.18s ease;
  user-select: none;
}

.role-mini-chip:hover {
  background: rgba(14, 165, 233, 0.18);
  border-color: rgba(56, 189, 248, 0.5);
  transform: translateY(-1px);
}

.role-mini-chip.is-active {
  background: rgba(14, 165, 233, 0.28);
  border-color: #38bdf8;
  box-shadow: 0 0 10px rgba(56, 189, 248, 0.35);
}

.chip-dot {
  width: 5px;
  height: 5px;
  border-radius: 50%;
  background: #38bdf8;
  transition: all 0.2s ease;
}

.role-mini-chip.is-active .chip-dot {
  background: #34d399;
  box-shadow: 0 0 6px #34d399;
}

.chip-role-name {
  font-size: 11px;
  font-weight: 500;
  color: #cbd5e1;
}

.role-mini-chip.is-active .chip-role-name {
  color: #ffffff;
  font-weight: 600;
}

/* Element Plus 科技风深色穿透覆盖 */
:deep(.tech-select .el-select__wrapper) {
  background: rgba(15, 23, 42, 0.8) !important;
  box-shadow: 0 0 0 1px rgba(56, 189, 248, 0.25) inset !important;
  border-radius: 8px;
  color: #f1f5f9;
  min-height: 42px;
  transition: all 0.2s ease;
}

:deep(.tech-select .el-select__wrapper.is-focused) {
  box-shadow: 0 0 0 1px #38bdf8 inset, 0 0 12px rgba(56, 189, 248, 0.3) !important;
}

:deep(.tech-select .el-select__placeholder),
:deep(.tech-select .el-select__selected-item) {
  color: #e2e8f0 !important;
  font-size: 13px;
}

:deep(.tech-input .el-input__wrapper) {
  background: rgba(15, 23, 42, 0.8) !important;
  box-shadow: 0 0 0 1px rgba(56, 189, 248, 0.25) inset !important;
  border-radius: 8px;
  min-height: 42px;
  transition: all 0.2s ease;
}

:deep(.tech-input .el-input__wrapper.is-focus) {
  box-shadow: 0 0 0 1px #38bdf8 inset, 0 0 12px rgba(56, 189, 248, 0.3) !important;
}

:deep(.tech-input .el-input__inner) {
  color: #ffffff !important;
  font-size: 13px;
}

:deep(.tech-input .el-input__inner::placeholder) {
  color: #64748b !important;
}

/* 提交按钮 */
.tech-submit-btn {
  width: 100%;
  min-height: 46px;
  margin-top: 8px;
  border-radius: 10px;
  background: linear-gradient(135deg, #0284c7 0%, #0369a1 100%) !important;
  border: 1px solid rgba(56, 189, 248, 0.5) !important;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  box-shadow: 0 4px 20px rgba(2, 132, 199, 0.4);
  transition: all 0.2s ease;
}

.tech-submit-btn:hover:not(:disabled) {
  background: linear-gradient(135deg, #0369a1 0%, #0284c7 100%) !important;
  box-shadow: 0 0 24px rgba(56, 189, 248, 0.6);
  transform: translateY(-1px);
}

.submit-text {
  font-size: 14px;
  font-weight: 700;
  color: #ffffff;
  letter-spacing: 0.04em;
}

.submit-api-tag {
  font-size: 10px;
  color: #bae6fd;
  font-family: monospace;
  margin-top: 1px;
}

/* 主舱安全页脚 */
.console-card-footer {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 12px;
  font-size: 11px;
  color: #64748b;
  border-top: 1px solid rgba(56, 189, 248, 0.1);
  padding-top: 14px;
}

.sec-item {
  display: flex;
  align-items: center;
  gap: 5px;
}

.sec-dot.green {
  width: 5px;
  height: 5px;
  border-radius: 50%;
  background: #10b981;
}

.sec-divider {
  opacity: 0.3;
}

/* ================= 底部页脚 ================= */
.cloud-footer {
  position: relative;
  z-index: 10;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 4px;
  padding: 16px 24px;
  border-top: 1px solid rgba(56, 189, 248, 0.12);
  background: rgba(3, 7, 18, 0.5);
  backdrop-filter: blur(10px);
}

.footer-badge {
  font-size: 10px;
  color: #38bdf8;
  letter-spacing: 0.14em;
  font-weight: 700;
}

.footer-text {
  font-size: 11px;
  color: #64748b;
  text-align: center;
}

/* ================= 统一 Toast ================= */
.cloud-toast {
  position: fixed;
  bottom: 24px;
  right: 24px;
  padding: 12px 20px;
  border-radius: 10px;
  font-size: 13px;
  font-weight: 600;
  z-index: 9999;
  box-shadow: 0 10px 30px rgba(0, 0, 0, 0.7);
  transition: all 0.3s cubic-bezier(0.16, 1, 0.3, 1);
  opacity: 0;
  transform: translateY(16px);
  pointer-events: none;
  backdrop-filter: blur(12px);
}

.cloud-toast.is-visible {
  opacity: 1;
  transform: translateY(0);
  pointer-events: auto;
}

.cloud-toast.toast-success {
  background: rgba(6, 78, 59, 0.9);
  border: 1px solid #10b981;
  color: #a7f3d0;
}

.cloud-toast.toast-danger {
  background: rgba(127, 29, 29, 0.9);
  border: 1px solid #ef4444;
  color: #fecaca;
}

.cloud-toast.toast-warning {
  background: rgba(120, 53, 15, 0.9);
  border: 1px solid #f59e0b;
  color: #fde68a;
}

/* ================= 动画与响应式断点 ================= */
.fade-slide-enter-active, .fade-slide-leave-active {
  transition: all 0.3s ease;
}
.fade-slide-enter-from, .fade-slide-leave-to {
  opacity: 0;
  transform: translateY(-10px);
}

@media (max-width: 640px) {
  .cloud-topbar {
    padding: 14px 18px;
  }
  .brand-sub {
    display: none;
  }
  .login-center-console {
    padding: 22px 16px;
  }
  .merged-role-selector {
    gap: 4px;
  }
  .role-mini-chip {
    padding: 3px 6px;
  }
}
</style>
