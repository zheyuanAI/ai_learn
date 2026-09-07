<template>
  <div class="exception-center-container">
    <!-- 页面统一标题栏 -->
    <PageHeader
      title="跨域业务异常中心"
      tag="INSIGHTS / EXCEPTION CENTER"
      description="由仓储库存、生产制造与 IoT 设备告警实时事实派生的跨域异常集中监控视图，不建立第二份事实表。"
    >
      <template #actions>
        <button
          type="button"
          class="btn-refresh"
          :disabled="viewState === 'loading'"
          @click="loadExceptions"
        >
          <span>🔄 刷新异常</span>
        </button>
      </template>
    </PageHeader>

    <!-- 筛选控制栏 -->
    <FilterBar
      v-model="searchKeyword"
      placeholder="搜索异常描述、类型或来源..."
      @search="handleSearch"
      @reset="handleReset"
    >
      <!-- 统计时间范围筛选 -->
      <div class="filter-group">
        <label class="filter-label">时间范围</label>
        <select v-model="selectedTimeRange" class="filter-select" @change="handleSearch">
          <option value="">全部时间</option>
          <option value="today">今日</option>
          <option value="7d">近 7 天</option>
          <option value="30d">近 30 天</option>
        </select>
      </div>

      <!-- 异常来源筛选 -->
      <div class="filter-group">
        <label class="filter-label">事实来源域</label>
        <select v-model="selectedSource" class="filter-select" @change="handleSearch">
          <option value="">全部来源</option>
          <option value="inventory">仓储库存 (Inventory)</option>
          <option value="production">生产制造 (Production)</option>
          <option value="device_alarm">IoT 设备告警 (Device Alarm)</option>
        </select>
      </div>

      <!-- 严重级别筛选 -->
      <div class="filter-group">
        <label class="filter-label">严重级别</label>
        <select v-model="selectedSeverity" class="filter-select" @change="handleSearch">
          <option value="">全部级别</option>
          <option value="HIGH">高 (HIGH)</option>
          <option value="MEDIUM">中 (MEDIUM)</option>
        </select>
      </div>
    </FilterBar>

    <!-- 四态展示区域 -->
    <div class="exception-content-card">
      <!-- 1. 加载态 -->
      <div v-if="viewState === 'loading'" class="loading-state-box">
        <span class="spinner">⏳</span>
        <p>正在从跨域事实源派生聚合异常数据...</p>
      </div>

      <!-- 2. 异常错误态 -->
      <div v-else-if="viewState === 'error'" class="error-state-box">
        <ErrorState
          title="异常中心聚合失败"
          :message="errorMessage"
          code="EXCEPTION_QUERY_ERROR"
          detail="各域服务网关响应异常或网络中断，请稍后重试。"
          @retry="loadExceptions"
        />
      </div>

      <!-- 3. 空数据态 -->
      <div v-else-if="viewState === 'empty' || records.length === 0" class="empty-state-box">
        <EmptyState
          icon="🛡️"
          title="当前无任何跨域异常"
          description="该租户在所选筛选条件下未派生出任何库存差异、工序不良或设备告警异常。"
        />
      </div>

      <!-- 4. 就绪数据表格 -->
      <div v-else class="table-wrapper">
        <div class="sync-meta-header">
          <span class="meta-tag">
            生成时间: {{ pageMeta.generatedAt || "实时" }}
          </span>
          <span class="meta-tag">
            源数据更新: {{ pageMeta.sourceUpdatedAt || "实时" }}
          </span>
          <span class="total-tag">
            共派生 {{ total }} 条异常记录
          </span>
        </div>

        <table class="data-table">
          <thead>
            <tr>
              <th style="width: 130px;">事实来源域</th>
              <th style="width: 160px;">异常类型</th>
              <th style="width: 100px; text-align: center;">严重级别</th>
              <th style="width: 120px; text-align: right;">异常指标值</th>
              <th>异常描述与上下文</th>
              <th style="width: 180px; text-align: center;">发生时间</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="(rec, idx) in records" :key="idx">
              <td>
                <span class="source-tag" :class="`source-${rec.source?.toLowerCase()}`">
                  {{ formatSource(rec.source) }}
                </span>
              </td>
              <td class="type-cell font-mono">{{ rec.exception_type }}</td>
              <td style="text-align: center;">
                <StatusBadge
                  :type="getSeverityBadgeType(rec.severity)"
                  :text="rec.severity"
                />
              </td>
              <td style="text-align: right;" class="value-cell font-mono">
                {{ rec.value !== undefined ? rec.value : "-" }}
              </td>
              <td class="message-cell">{{ rec.message }}</td>
              <td style="text-align: center;" class="time-cell font-mono">
                {{ formatTime(rec.occurredAt) }}
              </td>
            </tr>
          </tbody>
        </table>

        <!-- 分页控制器 -->
        <div class="pagination-bar">
          <span class="page-info">第 {{ currentPage }} / {{ totalPages || 1 }} 页</span>
          <div class="page-buttons">
            <button
              type="button"
              class="btn-page"
              :disabled="currentPage <= 1"
              @click="handlePageChange(currentPage - 1)"
            >
              上一页
            </button>
            <button
              type="button"
              class="btn-page"
              :disabled="currentPage >= totalPages"
              @click="handlePageChange(currentPage + 1)"
            >
              下一页
            </button>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
/**
 * 跨域业务异常中心页面组件 (ExceptionCenterView)
 * 遵循后端 REST API GET /api/exception-center 契约
 * 纯粹直连后端派生事实，支持时间、来源、级别筛选与分页
 */

import { ref, reactive, onMounted } from "vue";
import PageHeader from "@/components/common/PageHeader.vue";
import FilterBar from "@/components/common/FilterBar.vue";
import StatusBadge from "@/components/common/StatusBadge.vue";
import EmptyState from "@/components/common/EmptyState.vue";
import ErrorState from "@/components/common/ErrorState.vue";
import { getExceptionCenter } from "@/api/insights";
import type { ExceptionCenterRecord } from "@/types/insights";
import type { ViewState, BadgeType } from "@/types/common";

const viewState = ref<ViewState>("loading");
const errorMessage = ref("");

const searchKeyword = ref("");
const selectedTimeRange = ref("today");
const selectedSource = ref("");
const selectedSeverity = ref("");

const currentPage = ref(1);
const pageSize = ref(20);
const total = ref(0);
const totalPages = ref(0);
const records = ref<ExceptionCenterRecord[]>([]);

const pageMeta = reactive({
  generatedAt: "",
  sourceUpdatedAt: "",
});

/**
 * 格式化来源域显示名称
 * @param source 来源代码
 */
function formatSource(source: string): string {
  if (!source) return "-";
  const s = source.toLowerCase();
  if (s.includes("inventory") || s.includes("stock")) return "仓储库存";
  if (s === "production") return "生产制造";
  if (s === "device_alarm") return "IoT 设备告警";
  return source;
}

/**
 * 转换严重级别为 BadgeType
 * @param severity 级别字符串
 */
function getSeverityBadgeType(severity: string): BadgeType {
  const s = (severity || "").toUpperCase();
  if (s === "HIGH") return "danger";
  if (s === "MEDIUM") return "warning";
  return "default";
}

/**
 * 格式化时间
 * @param timeStr 时间字符串
 */
function formatTime(timeStr: string): string {
  if (!timeStr) return "-";
  return timeStr.replace("T", " ").substring(0, 19);
}

/**
 * 从后端加载异常中心分页事实
 */
async function loadExceptions() {
  viewState.value = "loading";
  errorMessage.value = "";

  try {
    const res = await getExceptionCenter({
      time_range: selectedTimeRange.value || undefined,
      source: selectedSource.value || undefined,
      severity: selectedSeverity.value || undefined,
      page: currentPage.value,
      size: pageSize.value,
    });

    const pageData = res.data;
    records.value = pageData.records || [];
    total.value = pageData.total || 0;
    totalPages.value = pageData.totalPages || Math.ceil((pageData.total || 0) / pageSize.value) || 1;
    pageMeta.generatedAt = pageData.generated_at ? formatTime(pageData.generated_at) : "";
    pageMeta.sourceUpdatedAt = pageData.source_updated_at ? formatTime(pageData.source_updated_at) : "";

    if (records.value.length === 0) {
      viewState.value = "empty";
    } else {
      viewState.value = "ready";
    }
  } catch (err: any) {
    viewState.value = "error";
    errorMessage.value = err?.message || "无法连接异常中心服务";
  }
}

/**
 * 处理筛选搜索
 */
function handleSearch() {
  currentPage.value = 1;
  loadExceptions();
}

/**
 * 处理重置搜索
 */
function handleReset() {
  searchKeyword.value = "";
  selectedTimeRange.value = "today";
  selectedSource.value = "";
  selectedSeverity.value = "";
  currentPage.value = 1;
  loadExceptions();
}

/**
 * 处理分页切换
 * @param page 目标页码
 */
function handlePageChange(page: number) {
  currentPage.value = page;
  loadExceptions();
}

onMounted(() => {
  loadExceptions();
});
</script>

<style scoped>
.exception-center-container {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.btn-refresh {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 8px 16px;
  background: var(--color-surface, #ffffff);
  border: 1px solid var(--color-border, #d9d9d9);
  border-radius: 6px;
  cursor: pointer;
  font-size: 13px;
  font-weight: 500;
  transition: all 0.2s;
}

.btn-refresh:hover:not(:disabled) {
  border-color: var(--color-primary, #1890ff);
  color: var(--color-primary, #1890ff);
}

.filter-group {
  display: flex;
  align-items: center;
  gap: 6px;
}

.filter-label {
  font-size: 13px;
  color: var(--color-text-secondary, #666666);
}

.filter-select {
  padding: 6px 10px;
  border: 1px solid var(--color-border, #d9d9d9);
  border-radius: 4px;
  background: var(--color-surface, #ffffff);
  font-size: 13px;
  color: var(--color-text, #333333);
}

.exception-content-card {
  background: var(--color-surface, #ffffff);
  border: 1px solid var(--color-border, #e8e8e8);
  border-radius: 8px;
  padding: 16px;
  min-height: 400px;
}

.sync-meta-header {
  display: flex;
  gap: 16px;
  align-items: center;
  padding-bottom: 12px;
  border-bottom: 1px solid var(--color-border-light, #f0f0f0);
  margin-bottom: 12px;
  font-size: 12px;
  color: var(--color-text-secondary, #888888);
}

.total-tag {
  margin-left: auto;
  font-weight: 600;
  color: var(--color-text, #333333);
}

.data-table {
  width: 100%;
  border-collapse: collapse;
  font-size: 13px;
}

.data-table th,
.data-table td {
  padding: 10px 12px;
  border-bottom: 1px solid var(--color-border-light, #f0f0f0);
}

.data-table th {
  background: var(--color-bg-light, #fafafa);
  font-weight: 600;
  color: var(--color-text, #333333);
  text-align: left;
}

.source-tag {
  display: inline-block;
  padding: 2px 8px;
  border-radius: 4px;
  font-size: 12px;
  font-weight: 500;
}

.source-inventory {
  background: #e6f7ff;
  color: #096dd9;
}

.source-manufacturing {
  background: #f6ffed;
  color: #389e0d;
}

.source-iot {
  background: #fff7e6;
  color: #d46b08;
}

.message-cell {
  color: var(--color-text, #333333);
  line-height: 1.4;
}

.font-mono {
  font-family: Consolas, Monaco, "Courier New", monospace;
}

.loading-state-box,
.error-state-box,
.empty-state-box {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: 60px 20px;
  gap: 12px;
}

.spinner {
  font-size: 28px;
  animation: spin 1.5s infinite linear;
}

@keyframes spin {
  from { transform: rotate(0deg); }
  to { transform: rotate(360deg); }
}

.pagination-bar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-top: 16px;
  padding-top: 12px;
  border-top: 1px solid var(--color-border-light, #f0f0f0);
}

.page-info {
  font-size: 13px;
  color: var(--color-text-secondary, #666666);
}

.page-buttons {
  display: flex;
  gap: 8px;
}

.btn-page {
  padding: 6px 14px;
  border: 1px solid var(--color-border, #d9d9d9);
  background: #ffffff;
  border-radius: 4px;
  font-size: 13px;
  cursor: pointer;
}

.btn-page:disabled {
  opacity: 0.4;
  cursor: not-allowed;
}

.btn-page:hover:not(:disabled) {
  border-color: var(--color-primary, #1890ff);
  color: var(--color-primary, #1890ff);
}
</style>
