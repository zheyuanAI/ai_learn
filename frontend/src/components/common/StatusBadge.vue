<template>
  <el-tag
    :type="elTagType"
    :effect="computedType === 'default' ? 'plain' : 'dark'"
    size="small"
    class="status-tag"
    :class="{ 'is-pulsing': pulsing }"
  >
    <span v-if="dot" class="tag-dot"></span>
    <slot>{{ text }}</slot>
  </el-tag>
</template>

<script setup lang="ts">
import { computed } from "vue";
import type { BadgeType } from "../../types/common";

/**
 * 统一状态徽标组件 (StatusBadge)
 * 基于 Element Plus el-tag 封装，兼容现有各种业务调用与呼吸灯属性
 */
const props = withDefaults(
  defineProps<{
    type?: BadgeType;
    text?: string;
    dot?: boolean;
    pulsing?: boolean;
  }>(),
  {
    type: "default",
    text: "",
    dot: true,
    pulsing: false,
  }
);

const computedType = computed(() => props.type || "default");

const elTagType = computed(() => {
  switch (computedType.value) {
    case "primary":
      return "primary";
    case "success":
      return "success";
    case "warning":
      return "warning";
    case "danger":
      return "danger";
    case "info":
      return "info";
    default:
      return "info";
  }
});
</script>

<style scoped>
.status-tag {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  font-weight: 500;
}
.tag-dot {
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background-color: currentColor;
}
.is-pulsing .tag-dot {
  animation: pulse-dot 1.8s infinite;
}
@keyframes pulse-dot {
  0% {
    box-shadow: 0 0 0 0 currentColor;
    opacity: 1;
  }
  70% {
    box-shadow: 0 0 0 4px transparent;
    opacity: 0.6;
  }
  100% {
    box-shadow: 0 0 0 0 transparent;
    opacity: 1;
  }
}
</style>
