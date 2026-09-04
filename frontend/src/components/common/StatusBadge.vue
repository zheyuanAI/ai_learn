<template>
  <span class="status-badge" :class="[`badge-${computedType}`, { 'is-pulsing': pulsing }]">
    <span v-if="dot" class="badge-dot"></span>
    <span class="badge-text"><slot>{{ text }}</slot></span>
  </span>
</template>

<script setup lang="ts">
import { computed } from "vue";
import type { BadgeType } from "../../types/common";

/**
 * 统一状态徽标组件 (StatusBadge)
 * 支持语义化颜色映射、呼吸点动画与自定义文本
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
</script>

<style scoped>
.status-badge {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 3px 8px;
  border-radius: 4px;
  font-size: 12px;
  font-weight: 500;
  line-height: 1;
  white-space: nowrap;
}

.badge-dot {
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: currentColor;
}

/* 默认灰蓝 */
.badge-default {
  background: rgba(148, 163, 184, 0.12);
  color: #94a3b8;
  border: 1px solid rgba(148, 163, 184, 0.25);
}

/* 主题青蓝 */
.badge-primary {
  background: rgba(56, 189, 248, 0.12);
  color: #38bdf8;
  border: 1px solid rgba(56, 189, 248, 0.25);
}

/* 成功翠绿 */
.badge-success {
  background: rgba(52, 211, 153, 0.12);
  color: #34d399;
  border: 1px solid rgba(52, 211, 153, 0.25);
}

/* 警示金橙 */
.badge-warning {
  background: rgba(251, 191, 36, 0.12);
  color: #fbbf24;
  border: 1px solid rgba(251, 191, 36, 0.25);
}

/* 危险朱红 */
.badge-danger {
  background: rgba(248, 113, 113, 0.12);
  color: #f87171;
  border: 1px solid rgba(248, 113, 113, 0.25);
}

/* 信息淡紫 */
.badge-info {
  background: rgba(167, 139, 250, 0.12);
  color: #a78bfa;
  border: 1px solid rgba(167, 139, 250, 0.25);
}

.is-pulsing .badge-dot {
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
