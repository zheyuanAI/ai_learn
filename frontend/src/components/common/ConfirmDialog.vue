<template>
  <div v-if="visible" class="dialog-mask" @click.self="handleCancel">
    <div class="dialog-panel" :class="{ 'is-danger': danger }">
      <div class="dialog-header">
        <h3 class="dialog-title">{{ title || "操作确认" }}</h3>
        <button type="button" class="btn-close" @click="handleCancel">✕</button>
      </div>
      <div class="dialog-body">
        <p class="dialog-message">{{ message }}</p>
        <slot></slot>
      </div>
      <div class="dialog-footer">
        <button type="button" class="btn btn-secondary" :disabled="loading" @click="handleCancel">
          {{ cancelText || "取消" }}
        </button>
        <button
          type="button"
          class="btn"
          :class="danger ? 'btn-danger' : 'btn-primary'"
          :disabled="loading"
          @click="handleConfirm"
        >
          <span v-if="loading" class="spinner">⏳ </span>
          <span>{{ confirmText || "确认" }}</span>
        </button>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
/**
 * 统一确认对话框 (ConfirmDialog)
 * 用于关键写操作二次确认防误触
 */
const props = withDefaults(
  defineProps<{
    visible: boolean;
    title?: string;
    message?: string;
    confirmText?: string;
    cancelText?: string;
    danger?: boolean;
    loading?: boolean;
  }>(),
  {
    title: "操作确认",
    message: "",
    confirmText: "确认",
    cancelText: "取消",
    danger: false,
    loading: false,
  }
);

const emit = defineEmits<{
  (e: "update:visible", val: boolean): void;
  (e: "confirm"): void;
  (e: "cancel"): void;
}>();

function handleCancel() {
  if (props.loading) return;
  emit("update:visible", false);
  emit("cancel");
}

function handleConfirm() {
  emit("confirm");
}
</script>

<style scoped>
.dialog-mask {
  position: fixed;
  inset: 0;
  background: rgba(0, 0, 0, 0.7);
  backdrop-filter: blur(4px);
  display: flex;
  align-items: center;
  justify-content: center;
  z-index: 1000;
  padding: 16px;
}

.dialog-panel {
  background: #0f172a;
  border: 1px solid rgba(255, 255, 255, 0.15);
  border-radius: 10px;
  width: 100%;
  max-width: 460px;
  box-shadow: 0 20px 25px -5px rgba(0, 0, 0, 0.5);
  animation: dialog-enter 0.2s ease-out;
  overflow: hidden;
}

.dialog-panel.is-danger {
  border-color: rgba(239, 68, 68, 0.4);
}

.dialog-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 16px 20px;
  border-bottom: 1px solid rgba(255, 255, 255, 0.08);
}

.dialog-title {
  margin: 0;
  font-size: 16px;
  font-weight: 600;
  color: #f8fafc;
}

.btn-close {
  background: transparent;
  border: none;
  color: #94a3b8;
  font-size: 16px;
  cursor: pointer;
  padding: 4px;
  border-radius: 4px;
}

.btn-close:hover {
  color: #f8fafc;
}

.dialog-body {
  padding: 20px;
  font-size: 14px;
  color: #cbd5e1;
  line-height: 1.6;
}

.dialog-message {
  margin: 0;
}

.dialog-footer {
  display: flex;
  align-items: center;
  justify-content: flex-end;
  gap: 12px;
  padding: 14px 20px;
  background: rgba(0, 0, 0, 0.2);
  border-top: 1px solid rgba(255, 255, 255, 0.08);
}

.btn {
  padding: 8px 16px;
  border-radius: 6px;
  font-size: 13px;
  font-weight: 500;
  cursor: pointer;
  transition: all 0.2s;
  border: 1px solid transparent;
}

.btn:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}

.btn-secondary {
  background: rgba(51, 65, 85, 0.6);
  color: #cbd5e1;
  border-color: rgba(255, 255, 255, 0.1);
}

.btn-secondary:hover:not(:disabled) {
  background: rgba(71, 85, 105, 0.8);
  color: #ffffff;
}

.btn-primary {
  background: #0284c7;
  color: #ffffff;
  border-color: #0369a1;
}

.btn-primary:hover:not(:disabled) {
  background: #0369a1;
}

.btn-danger {
  background: #dc2626;
  color: #ffffff;
  border-color: #b91c1c;
}

.btn-danger:hover:not(:disabled) {
  background: #b91c1c;
}

@keyframes dialog-enter {
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
