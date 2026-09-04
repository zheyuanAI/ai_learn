<template>
  <span class="quantity-wrapper" :class="{ 'is-zero': isZero, 'is-negative': isNegative }">
    <span class="quantity-value">{{ formattedValue }}</span>
    <span v-if="unit" class="quantity-unit">{{ unit }}</span>
  </span>
</template>

<script setup lang="ts">
import { computed } from "vue";

/**
 * 统一高精度数量展示组件 (QuantityText)
 * 严格使用字符串保持原始精度，杜绝 JavaScript 浮点误差
 */
const props = withDefaults(
  defineProps<{
    value?: string | number;
    unit?: string;
    precision?: number;
  }>(),
  {
    value: "0",
    unit: "",
    precision: undefined,
  }
);

const stringValue = computed(() => {
  if (props.value === null || props.value === undefined) return "0";
  return String(props.value).trim();
});

const isZero = computed(() => {
  const v = stringValue.value;
  return v === "0" || v === "0.0" || v === "0.00" || /^0+(\.0+)?$/.test(v);
});

const isNegative = computed(() => {
  return stringValue.value.startsWith("-");
});

const formattedValue = computed(() => {
  const raw = stringValue.value;
  if (props.precision === undefined) return raw;

  // 严格字符串保留小数位数，避免浮点运算
  const parts = raw.split(".");
  const intPart = parts[0];
  let decPart = parts[1] || "";

  if (props.precision === 0) return intPart;

  if (decPart.length < props.precision) {
    decPart = decPart.padEnd(props.precision, "0");
  } else if (decPart.length > props.precision) {
    decPart = decPart.substring(0, props.precision);
  }

  return `${intPart}.${decPart}`;
});
</script>

<style scoped>
.quantity-wrapper {
  display: inline-flex;
  align-items: baseline;
  gap: 4px;
  font-family: var(--font-mono, monospace);
  font-weight: 600;
  color: #f1f5f9;
}

.quantity-value {
  letter-spacing: -0.2px;
}

.quantity-unit {
  font-size: 11px;
  font-weight: 400;
  color: #94a3b8;
}

.is-zero {
  color: #64748b;
}

.is-negative {
  color: #f87171;
}
</style>
