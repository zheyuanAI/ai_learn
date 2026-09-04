<template>
  <div v-if="visible" class="modal-mask" @click.self="handleClose">
    <div class="modal-card">
      <div class="modal-header">
        <h3 class="modal-title">设备 MQTT 接入凭证</h3>
        <button type="button" class="btn-close" @click="handleClose">✕</button>
      </div>

      <div class="modal-body">
        <!-- 步骤 1：尚未签发，确认生成 -->
        <div v-if="!credentialResult" class="issue-confirm-pane">
          <p class="desc-text">
            正在为设备 <strong>{{ deviceName || deviceCode }}</strong> ({{ deviceCode }}) 申请签发新的 MQTT 接入身份凭证。
          </p>

          <div class="security-alert-box warning-theme">
            <span class="alert-icon">⚠️</span>
            <div class="alert-text">
              <strong>安全接入规范：</strong>
              <span>
                接入凭证明文密匙将在创建成功后<strong>仅展示一次</strong>。关闭本对话框后，服务端仅保留哈希/引用标识，明文将不可再次回显或恢复。
              </span>
            </div>
          </div>

          <div class="issue-action-row">
            <button
              type="button"
              class="btn btn-primary"
              :disabled="loading"
              @click="handleIssueCredential"
            >
              <span v-if="loading" class="spinner">⏳ </span>
              <span>{{ loading ? "正在签发接入凭证..." : "立即签发设备接入凭证" }}</span>
            </button>
          </div>
        </div>

        <!-- 步骤 2：签发成功，一次性明文展示 -->
        <div v-else class="credential-display-pane">
          <div class="security-alert-box danger-theme">
            <span class="alert-icon">🔒</span>
            <div class="alert-text">
              <strong>一次性安全凭证（仅此展示一次）：</strong>
              <span>
                请立即复制并妥善保管以下设备接入参数。关闭此窗口后将永久无法再次查看明文！
              </span>
            </div>
          </div>

          <div class="credential-info-list">
            <!-- 凭证引用标识 -->
            <div class="info-row">
              <span class="info-label">凭证引用标识:</span>
              <span class="info-value font-mono">{{ credentialResult.credentialReference }}</span>
            </div>

            <!-- MQTT ClientId -->
            <div class="info-row">
              <span class="info-label">MQTT Client ID:</span>
              <div class="copyable-box">
                <span class="info-value font-mono">{{ credentialResult.mqttClientId }}</span>
                <button type="button" class="btn-copy" @click="copyText(credentialResult.mqttClientId, 'clientId')">
                  {{ copiedField === 'clientId' ? '已复制✓' : '复制' }}
                </button>
              </div>
            </div>

            <!-- MQTT Username -->
            <div class="info-row">
              <span class="info-label">MQTT 用户名:</span>
              <div class="copyable-box">
                <span class="info-value font-mono">{{ credentialResult.mqttUsername }}</span>
                <button type="button" class="btn-copy" @click="copyText(credentialResult.mqttUsername, 'username')">
                  {{ copiedField === 'username' ? '已复制✓' : '复制' }}
                </button>
              </div>
            </div>

            <!-- 明文 Secret Token -->
            <div class="info-row highlight-secret-row">
              <span class="info-label">明文接入密匙 (Secret):</span>
              <div class="copyable-box">
                <span class="info-value font-mono secret-text">{{ credentialResult.credentialSecret }}</span>
                <button type="button" class="btn-copy btn-copy-primary" @click="copyText(credentialResult.credentialSecret, 'secret')">
                  {{ copiedField === 'secret' ? '已复制✓' : '复制密匙' }}
                </button>
              </div>
            </div>

            <!-- 签发时间 -->
            <div class="info-row">
              <span class="info-label">签发有效时间:</span>
              <span class="info-value font-mono text-muted">{{ credentialResult.createdAt }}</span>
            </div>
          </div>
        </div>
      </div>

      <div class="modal-footer">
        <button
          type="button"
          class="btn btn-secondary"
          @click="handleClose"
        >
          {{ credentialResult ? "我已保存完毕，安全关闭" : "取消" }}
        </button>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref } from "vue";
import type { DeviceCredentialCreateResult } from "../../types/iot";
import { createDeviceCredential } from "../../api/iot";

/**
 * 组件入参与事件
 */
const props = defineProps<{
  visible: boolean;
  deviceId: string;
  deviceCode?: string;
  deviceName?: string;
}>();

const emit = defineEmits<{
  (e: "update:visible", val: boolean): void;
  (e: "issued", res: DeviceCredentialCreateResult): void;
}>();

const loading = ref(false);
/** 一次性明文凭证返回结果，关闭对话框时必须强制清空置 null，不存本地持久化缓存 */
const credentialResult = ref<DeviceCredentialCreateResult | null>(null);
const copiedField = ref("");

/**
 * 签发设备凭证
 */
async function handleIssueCredential() {
  if (!props.deviceId) return;
  loading.value = true;
  try {
    const res = await createDeviceCredential(props.deviceId);
    if (res.data) {
      credentialResult.value = res.data;
      emit("issued", res.data);
    }
  } catch (err: any) {
    alert(`签发凭证失败：${err.message}`);
  } finally {
    loading.value = false;
  }
}

/**
 * 复制文本至剪贴板
 */
async function copyText(text: string, field: string) {
  try {
    if (navigator.clipboard) {
      await navigator.clipboard.writeText(text);
    } else {
      const input = document.createElement("input");
      input.value = text;
      document.body.appendChild(input);
      input.select();
      document.execCommand("copy");
      document.body.removeChild(input);
    }
    copiedField.value = field;
    setTimeout(() => {
      if (copiedField.value === field) copiedField.value = "";
    }, 2000);
  } catch (err) {
    console.warn("复制文本失败:", err);
  }
}

/**
 * 关闭对话框：严格遵循规范，清空一切内存明文！
 */
function handleClose() {
  credentialResult.value = null;
  copiedField.value = "";
  emit("update:visible", false);
}
</script>

<style scoped>
.modal-mask {
  position: fixed;
  inset: 0;
  background: rgba(0, 0, 0, 0.75);
  backdrop-filter: blur(4px);
  z-index: 1100;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 16px;
}

.modal-card {
  background: #0f172a;
  border: 1px solid rgba(255, 255, 255, 0.15);
  border-radius: 10px;
  width: 100%;
  max-width: 580px;
  box-shadow: 0 25px 35px rgba(0, 0, 0, 0.5);
  overflow: hidden;
}

.modal-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 16px 20px;
  border-bottom: 1px solid rgba(255, 255, 255, 0.08);
}

.modal-title {
  margin: 0;
  font-size: 16px;
  color: #f8fafc;
}

.btn-close {
  background: none;
  border: none;
  color: #94a3b8;
  font-size: 16px;
  cursor: pointer;
}

.modal-body {
  padding: 20px;
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.desc-text {
  margin: 0 0 12px;
  font-size: 13px;
  color: #cbd5e1;
  line-height: 1.5;
}

.security-alert-box {
  display: flex;
  align-items: flex-start;
  gap: 12px;
  padding: 12px 14px;
  border-radius: 8px;
  font-size: 12px;
  line-height: 1.5;
}

.warning-theme {
  background: rgba(251, 191, 36, 0.12);
  border: 1px solid rgba(251, 191, 36, 0.3);
  color: #fbbf24;
}

.danger-theme {
  background: rgba(239, 68, 68, 0.12);
  border: 1px solid rgba(239, 68, 68, 0.35);
  color: #f87171;
}

.alert-icon {
  font-size: 16px;
}

.alert-text {
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.issue-action-row {
  margin-top: 16px;
  display: flex;
  justify-content: center;
}

.credential-info-list {
  display: flex;
  flex-direction: column;
  gap: 10px;
  background: rgba(30, 41, 59, 0.5);
  border: 1px solid rgba(255, 255, 255, 0.08);
  border-radius: 8px;
  padding: 14px;
}

.info-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  font-size: 13px;
}

.info-label {
  color: #94a3b8;
  white-space: nowrap;
}

.info-value {
  color: #f1f5f9;
  word-break: break-all;
}

.copyable-box {
  display: flex;
  align-items: center;
  gap: 8px;
  flex: 1;
  justify-content: flex-end;
}

.highlight-secret-row {
  background: rgba(56, 189, 248, 0.08);
  border: 1px solid rgba(56, 189, 248, 0.25);
  padding: 8px 10px;
  border-radius: 6px;
}

.secret-text {
  color: #38bdf8;
  font-weight: 700;
  letter-spacing: 0.5px;
}

.btn-copy {
  background: rgba(51, 65, 85, 0.8);
  border: 1px solid rgba(255, 255, 255, 0.15);
  color: #cbd5e1;
  padding: 3px 8px;
  border-radius: 4px;
  font-size: 11px;
  cursor: pointer;
  white-space: nowrap;
  transition: all 0.2s;
}

.btn-copy:hover {
  background: rgba(71, 85, 105, 1);
  color: #ffffff;
}

.btn-copy-primary {
  background: #0284c7;
  border-color: #0369a1;
  color: #ffffff;
}

.btn-copy-primary:hover {
  background: #0369a1;
}

.modal-footer {
  display: flex;
  align-items: center;
  justify-content: flex-end;
  padding: 14px 20px;
  border-top: 1px solid rgba(255, 255, 255, 0.08);
  background: rgba(0, 0, 0, 0.2);
}
</style>
