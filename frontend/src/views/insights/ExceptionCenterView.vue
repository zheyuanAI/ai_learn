<template>
  <div class="exception-center-container">
    <!-- 页面统一标题栏 -->
    <PageHeader
      title="跨域业务异常中心"
      tag="INSIGHTS / EXCEPTION CENTER"
      description="由仓储库存、生产制造与 IoT 设备告警实时事实派生的跨域异常集中监控视图，不建立第二份事实表。"
    >
      <template #actions>
        <el-button
          type="primary"
          :icon="Refresh"
          :loading="viewState === 'loading'"
          @click="loadExceptions"
        >
          刷新异常
        </el-button>
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
        <el-select
          v-model="selectedTimeRange"
          placeholder="全部时间"
          style="width: 140px"
          @change="handleSearch"
        >
          <el-option label="全部时间" value="" />
          <el-option label="今日" value="today" />
          <el-option label="近 7 天" value="7d" />
          <el-option label="近 30 天" value="30d" />
        </el-select>
      </div>

      <!-- 异常来源筛选 -->
      <div class="filter-group">
        <label class="filter-label">事实来源域</label>
        <el-select
          v-model="selectedSource"
          placeholder="全部来源"
          style="width: 190px"
          @change="handleSearch"
        >
          <el-option label="全部来源" value="" />
          <el-option label="仓储库存 (Inventory)" value="inventory" />
          <el-option label="生产制造 (Production)" value="production" />
          <el-option label="IoT 设备告警 (Device Alarm)" value="device_alarm" />
        </el-select>
      </div>

      <!-- 严重级别筛选 -->
      <div class="filter-group">
        <label class="filter-label">严重级别</label>
        <el-select
          v-model="selectedSeverity"
          placeholder="全部级别"
          style="width: 140px"
          @change="handleSearch"
        >
          <el-option label="全部级别" value="" />
          <el-option label="高 (HIGH)" value="HIGH" />
          <el-option label="中 (MEDIUM)" value="MEDIUM" />
        </el-select>
      </div>
    </FilterBar>

    <!-- 四态展示区域 -->
    <div class="exception-content-card">
      <!-- 1. 加载态 -->
      <div v-if="viewState === 'loading'" class="loading-state-box">
        <el-icon class="is-loading spinner"><Loading /></el-icon>
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

        <DataTable
          :columns="tableColumns"
          :data="records"
          :page="currentPage"
          :size="pageSize"
          :total="total"
          :show-pagination="true"
          :row-key="(row: any) => String(row.id || row.exception_type || Math.random())"
          @page-change="handlePageChange"
        >
          <!-- 事实来源域 -->
          <template #source="{ value }">
            <el-tag :type="getSourceTagType(value)" size="small">
              {{ formatSource(value) }}
            </el-tag>
          </template>

          <!-- 异常类型 -->
          <template #exception_type="{ value }">
            <span class="font-mono text-cyan">{{ value }}</span>
          </template>

          <!-- 严重级别 -->
          <template #severity="{ value }">
            <StatusBadge
              :type="getSeverityBadgeType(value)"
              :text="value"
            />
          </template>

          <!-- 异常指标值 -->
          <template #value="{ value }">
            <span class="font-mono value-num">{{ value !== undefined ? value : "-" }}</span>
          </template>

          <!-- 异常描述与上下文 -->
          <template #message="{ value }">
            <span class="message-cell">{{ value }}</span>
          </template>

          <!-- 发生时间 -->
          <template #occurredAt="{ value }">
            <span class="font-mono time-cell">{{ formatTime(value) }}</span>
          </template>
        </DataTable>
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
import { Refresh, Loading } from "@element-plus/icons-vue";
import PageHeader from "@/components/common/PageHeader.vue";
import FilterBar from "@/components/common/FilterBar.vue";
import DataTable, { type TableColumn } from "@/components/common/DataTable.vue";
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
 * 异常中心数据列配置
 */
const tableColumns: TableColumn[] = [
  { key: "source", label: "事实来源域", width: "150px" },
  { key: "exception_type", label: "异常类型", minWidth: "160px" },
  { key: "severity", label: "严重级别", width: "110px", align: "center" },
  { key: "value", label: "异常指标值", width: "120px", align: "right" },
  { key: "message", label: "异常描述与上下文", minWidth: "260px" },
  { key: "occurredAt", label: "发生时间", width: "180px", align: "center" },
];

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
 * 来源域 Tag 颜色映射
 */
function getSourceTagType(source: string): "primary" | "success" | "warning" | "info" {
  if (!source) return "info";
  const s = source.toLowerCase();
  if (s.includes("inventory")) return "primary";
  if (s === "production") return "success";
  if (s === "device_alarm") return "warning";
  return "info";
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

.filter-group {
  display: flex;
  align-items: center;
  gap: 8px;
}

.filter-label {
  font-size: 12px;
  color: #94a3b8;
  white-space: nowrap;
}

.exception-content-card {
  background: rgba(15, 23, 42, 0.7);
  border: 1px solid rgba(255, 255, 255, 0.08);
  border-radius: 8px;
  padding: 16px;
  min-height: 400px;
}

.sync-meta-header {
  display: flex;
  gap: 16px;
  align-items: center;
  padding-bottom: 12px;
  border-bottom: 1px solid rgba(255, 255, 255, 0.08);
  margin-bottom: 12px;
  font-size: 12px;
  color: #94a3b8;
}

.total-tag {
  margin-left: auto;
  font-weight: 600;
  color: #f1f5f9;
}

.text-cyan {
  color: #38bdf8;
}

.value-num {
  color: #f8fafc;
  font-weight: 600;
}

.message-cell {
  color: #cbd5e1;
  line-height: 1.5;
}

.time-cell {
  color: #94a3b8;
}

.font-mono {
  font-family: var(--font-mono, Consolas, Monaco, monospace);
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
  color: #94a3b8;
}

.spinner {
  font-size: 28px;
  color: #38bdf8;
}
</style>
