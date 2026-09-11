<template>
  <div class="filter-bar-card">
    <div class="filter-inputs">
      <div v-if="showSearch" class="search-box">
        <el-input
          :model-value="modelValue"
          :placeholder="placeholder || '输入关键词搜索...'"
          clearable
          style="width: 260px;"
          @update:model-value="$emit('update:modelValue', String($event || ''))"
          @keyup.enter="$emit('search')"
        >
          <template #prefix>
            <el-icon><Search /></el-icon>
          </template>
        </el-input>
      </div>

      <slot></slot>

      <div class="filter-actions">
        <el-button type="primary" @click="$emit('search')">
          <el-icon><Search /></el-icon>
          <span>查询</span>
        </el-button>
        <el-button @click="$emit('reset')">
          <el-icon><Refresh /></el-icon>
          <span>重置</span>
        </el-button>
      </div>
    </div>

    <div v-if="$slots.right" class="filter-right">
      <slot name="right"></slot>
    </div>
  </div>
</template>

<script setup lang="ts">
/**
 * 统一筛选栏组件 (FilterBar)
 * 集成 Element Plus el-input 与 el-button
 * 支持搜索输入双向绑定、自定义筛选项插槽、查询/重置触发与右侧快捷操作
 */
import { Search, Refresh } from "@element-plus/icons-vue";

withDefaults(
  defineProps<{
    modelValue?: string;
    showSearch?: boolean;
    placeholder?: string;
  }>(),
  {
    modelValue: "",
    showSearch: true,
    placeholder: "输入关键词搜索...",
  }
);

defineEmits<{
  (e: "update:modelValue", val: string): void;
  (e: "search"): void;
  (e: "reset"): void;
}>();
</script>

<style scoped>
.filter-bar-card {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  background: rgba(15, 23, 42, 0.7);
  border: 1px solid rgba(255, 255, 255, 0.08);
  border-radius: 8px;
  padding: 14px 18px;
  margin-bottom: 16px;
  flex-wrap: wrap;
}

.filter-inputs {
  display: flex;
  align-items: center;
  gap: 12px;
  flex-wrap: wrap;
  flex: 1;
}

.search-box {
  position: relative;
  display: flex;
  align-items: center;
  min-width: 220px;
}

.search-icon {
  position: absolute;
  left: 10px;
  font-size: 13px;
  opacity: 0.6;
  pointer-events: none;
}

.search-input {
  width: 100%;
  background: rgba(30, 41, 59, 0.8);
  border: 1px solid rgba(255, 255, 255, 0.12);
  color: #f8fafc;
  padding: 7px 12px 7px 32px;
  border-radius: 6px;
  font-size: 13px;
  outline: none;
  transition: border-color 0.2s, box-shadow 0.2s;
}

.search-input:focus {
  border-color: #38bdf8;
  box-shadow: 0 0 0 2px rgba(56, 189, 248, 0.2);
}

.filter-actions {
  display: flex;
  align-items: center;
  gap: 8px;
}

.btn {
  padding: 7px 14px;
  border-radius: 6px;
  font-size: 13px;
  font-weight: 500;
  cursor: pointer;
  transition: all 0.2s;
  border: 1px solid transparent;
}

.btn-primary {
  background: #0284c7;
  color: #ffffff;
  border-color: #0369a1;
}

.btn-primary:hover {
  background: #0369a1;
}

.btn-secondary {
  background: rgba(51, 65, 85, 0.6);
  color: #cbd5e1;
  border-color: rgba(255, 255, 255, 0.1);
}

.btn-secondary:hover {
  background: rgba(71, 85, 105, 0.8);
  color: #ffffff;
}

.filter-right {
  display: flex;
  align-items: center;
  gap: 10px;
}
</style>
