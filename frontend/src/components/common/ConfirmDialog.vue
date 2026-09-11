<template>
  <el-dialog
    :model-value="visible"
    :title="title || '操作确认'"
    width="480px"
    :before-close="handleCancel"
    destroy-on-close
    align-center
  >
    <div class="dialog-content-body">
      <p v-if="message" class="dialog-msg-text">{{ message }}</p>
      <slot></slot>
    </div>
    <template #footer>
      <span class="dialog-footer">
        <el-button :disabled="loading" @click="handleCancel">
          {{ cancelText || "取消" }}
        </el-button>
        <el-button
          :type="danger ? 'danger' : 'primary'"
          :loading="loading"
          @click="handleConfirm"
        >
          {{ confirmText || "确认" }}
        </el-button>
      </span>
    </template>
  </el-dialog>
</template>

<script setup lang="ts">
/**
 * 统一确认对话框 (ConfirmDialog)
 * 封装 Element Plus el-dialog，兼容现有二次确认组件属性与事件
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

function handleConfirm() {
  emit("confirm");
}

function handleCancel() {
  emit("update:visible", false);
  emit("cancel");
}
</script>

<style scoped>
.dialog-content-body {
  font-size: 14px;
  color: #cbd5e1;
  line-height: 1.6;
}
.dialog-msg-text {
  margin: 0 0 12px 0;
}
</style>
