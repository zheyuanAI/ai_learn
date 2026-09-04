<template>
  <div class="iot-view-container">
    <!-- 统一页面头部 -->
    <PageHeader
      title="设备模型 (Device Profile) 管理"
      tag="IOT / METRICS SCHEMA"
      description="定义设备类别及其允许上报的遥测指标规格、离线判定超时时长及单指标告警触发阈值规则。"
    >
      <template #actions>
        <button type="button" class="btn btn-secondary" @click="openCreateRuleModal">
          <span class="btn-icon">＋</span>
          <span>添加告警规则</span>
        </button>
        <button type="button" class="btn btn-primary" @click="openCreateModal">
          <span class="btn-icon">＋</span>
          <span>新建设备模型</span>
        </button>
      </template>
    </PageHeader>

    <!-- 统一筛选栏 -->
    <FilterBar
      v-model="queryParams.keyword"
      placeholder="搜索模型编码或名称..."
      @search="handleSearch"
      @reset="handleReset"
    >
      <div class="filter-select-group">
        <label class="filter-label">状态：</label>
        <select v-model="queryParams.status" class="filter-select" @change="handleSearch">
          <option value="">全部状态</option>
          <option value="ACTIVE">启用中 (ACTIVE)</option>
          <option value="DISABLED">已停用 (DISABLED)</option>
        </select>
      </div>
    </FilterBar>

    <!-- 错误异常提示 -->
    <ErrorState
      v-if="viewState === 'error'"
      title="设备模型列表加载失败"
      :message="errorMessage"
      @retry="fetchProfileList"
    />

    <!-- 数据表格 -->
    <DataTable
      v-else
      :columns="columns"
      :data="profileList"
      :loading="viewState === 'loading'"
      :page="queryParams.page"
      :size="queryParams.size"
      :total="total"
      empty-text="暂无匹配的设备模型记录"
      @page-change="handlePageChange"
    >
      <!-- 模型编码 -->
      <template #profileCode="{ row }">
        <span class="font-mono highlight-code">{{ row.profileCode }}</span>
      </template>

      <!-- 模型名称 -->
      <template #profileName="{ row }">
        <div class="profile-name-cell">
          <span class="p-title">{{ row.profileName }}</span>
          <span class="p-desc text-muted">{{ row.description || "暂无描述" }}</span>
        </div>
      </template>

      <!-- 离线超时时长 -->
      <template #offlineTimeoutSeconds="{ row }">
        <QuantityText :value="row.offlineTimeoutSeconds || 60" unit="秒" />
      </template>

      <!-- 指标集定义 -->
      <template #metricsCount="{ row }">
        <span class="badge-count">{{ row.metrics?.length || 0 }} 项指标</span>
      </template>

      <!-- 状态徽标 -->
      <template #status="{ row }">
        <StatusBadge
          :type="row.status === 'ACTIVE' ? 'success' : 'default'"
          :text="row.status === 'ACTIVE' ? '启用中' : '已停用'"
        />
      </template>

      <!-- 操作列 -->
      <template #actions="{ row }">
        <div class="action-btn-group">
          <button type="button" class="btn-text" @click="openMetricsDrawer(row)">
            指标规格
          </button>
        </div>
      </template>
    </DataTable>

    <!-- 指标规格抽屉 -->
    <div v-if="drawerVisible && activeProfile" class="drawer-overlay" @click.self="drawerVisible = false">
      <div class="drawer-panel">
        <div class="drawer-header">
          <div>
            <span class="drawer-tag font-mono">{{ activeProfile.profileCode }}</span>
            <h3 class="drawer-title">{{ activeProfile.profileName }}</h3>
          </div>
          <button type="button" class="btn-close" @click="drawerVisible = false">✕</button>
        </div>

        <div class="drawer-body">
          <div class="drawer-section-title">
            <span>允许上报指标集 (共 {{ activeProfile.metrics?.length || 0 }} 项)</span>
          </div>

          <table class="nested-table">
            <thead>
              <tr>
                <th>指标编码</th>
                <th>指标名称</th>
                <th>数据类型</th>
                <th>物理单位</th>
                <th>必填约束</th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="m in activeProfile.metrics" :key="m.id || m.metricCode">
                <td class="font-mono text-primary">{{ m.metricCode }}</td>
                <td>{{ m.metricName }}</td>
                <td class="font-mono">{{ m.valueType }}</td>
                <td class="font-mono">{{ m.unit || "-" }}</td>
                <td>
                  <span :class="m.required ? 'text-warning' : 'text-muted'">
                    {{ m.required ? "必须上报" : "可选" }}
                  </span>
                </td>
              </tr>
            </tbody>
          </table>
        </div>

        <div class="drawer-footer">
          <button type="button" class="btn btn-secondary" @click="drawerVisible = false">关闭</button>
        </div>
      </div>
    </div>

    <!-- 弹窗 1：新建设备模型 -->
    <div v-if="createModalVisible" class="modal-mask" @click.self="createModalVisible = false">
      <div class="modal-card modal-large">
        <div class="modal-header">
          <h3 class="modal-title">新建设备模型 (Device Profile)</h3>
          <button type="button" class="btn-close" @click="createModalVisible = false">✕</button>
        </div>

        <form class="modal-body" @submit.prevent="submitCreateProfile">
          <div class="form-grid two-col">
            <div class="form-item">
              <label>模型编码 <span class="req">*</span></label>
              <input
                v-model="createForm.profileCode"
                type="text"
                class="form-input font-mono"
                placeholder="例如 PROF-CNC-MILL"
                required
              />
            </div>
            <div class="form-item">
              <label>模型名称 <span class="req">*</span></label>
              <input
                v-model="createForm.profileName"
                type="text"
                class="form-input"
                placeholder="例如 数控加工中心 Profile"
                required
              />
            </div>
          </div>

          <div class="form-grid two-col">
            <div class="form-item">
              <label>离线超时判定时间 (秒)</label>
              <input
                v-model.number="createForm.offlineTimeoutSeconds"
                type="number"
                class="form-input font-mono"
                placeholder="默认 60"
              />
            </div>
            <div class="form-item">
              <label>模型简要描述说明</label>
              <input
                v-model="createForm.description"
                type="text"
                class="form-input"
                placeholder="应用场景或机型规格"
              />
            </div>
          </div>

          <!-- 动态指标列表编辑 -->
          <div class="form-section">
            <div class="section-head">
              <label>指标规格定义 (Metrics) <span class="req">*</span></label>
              <button type="button" class="btn-sm btn-secondary" @click="addMetricRow">
                ＋ 添加指标
              </button>
            </div>

            <div class="metrics-form-list">
              <div
                v-for="(m, idx) in createForm.metrics"
                :key="idx"
                class="metric-form-row"
              >
                <div class="m-code">
                  <input
                    v-model="m.metricCode"
                    type="text"
                    class="form-input font-mono"
                    placeholder="指标编码 (如 temp)"
                    required
                  />
                </div>
                <div class="m-name">
                  <input
                    v-model="m.metricName"
                    type="text"
                    class="form-input"
                    placeholder="指标名称 (如 温度)"
                    required
                  />
                </div>
                <div class="m-type">
                  <select v-model="m.valueType" class="form-input font-mono">
                    <option value="FLOAT">FLOAT</option>
                    <option value="INTEGER">INTEGER</option>
                    <option value="BOOLEAN">BOOLEAN</option>
                    <option value="STRING">STRING</option>
                  </select>
                </div>
                <div class="m-unit">
                  <input
                    v-model="m.unit"
                    type="text"
                    class="form-input font-mono"
                    placeholder="单位 (℃)"
                  />
                </div>
                <button
                  type="button"
                  class="btn-del-row"
                  :disabled="createForm.metrics.length <= 1"
                  @click="removeMetricRow(idx)"
                >
                  ✕
                </button>
              </div>
            </div>
          </div>

          <div class="modal-footer">
            <button type="button" class="btn btn-secondary" @click="createModalVisible = false">取消</button>
            <button type="submit" class="btn btn-primary" :disabled="isSubmitting">保存设备模型</button>
          </div>
        </form>
      </div>
    </div>

    <!-- 弹窗 2：新建单指标告警规则 -->
    <div v-if="createRuleModalVisible" class="modal-mask" @click.self="createRuleModalVisible = false">
      <div class="modal-card modal-large">
        <div class="modal-header">
          <h3 class="modal-title">新建单指标阈值告警规则</h3>
          <button type="button" class="btn-close" @click="createRuleModalVisible = false">✕</button>
        </div>

        <form class="modal-body" @submit.prevent="submitCreateRule">
          <div class="form-grid two-col">
            <div class="form-item">
              <label>规则编码 <span class="req">*</span></label>
              <input
                v-model="ruleForm.ruleCode"
                type="text"
                class="form-input font-mono"
                placeholder="例如 RULE-SPINDLE-OVERHEAT"
                required
              />
            </div>
            <div class="form-item">
              <label>规则名称 <span class="req">*</span></label>
              <input
                v-model="ruleForm.ruleName"
                type="text"
                class="form-input"
                placeholder="例如 主轴超温严重告警"
                required
              />
            </div>
          </div>

          <div class="form-grid two-col">
            <div class="form-item">
              <label>作用设备模型 ID <span class="req">*</span></label>
              <input
                v-model="ruleForm.deviceProfileId"
                type="text"
                class="form-input font-mono"
                placeholder="例如 prof-001"
                required
              />
            </div>
            <div class="form-item">
              <label>监控指标编码 (Metric Code) <span class="req">*</span></label>
              <input
                v-model="ruleForm.metricCode"
                type="text"
                class="form-input font-mono"
                placeholder="例如 spindle_temp"
                required
              />
            </div>
          </div>

          <div class="form-grid three-col">
            <div class="form-item">
              <label>比较运算符 <span class="req">*</span></label>
              <select v-model="ruleForm.operator" class="form-input font-mono">
                <option value="GT">大于 (&gt;)</option>
                <option value="GTE">大于等于 (&gt;=)</option>
                <option value="LT">小于 (&lt;)</option>
                <option value="LTE">小于等于 (&lt;=)</option>
                <option value="EQ">等于 (==)</option>
              </select>
            </div>
            <div class="form-item">
              <label>触发阈值 <span class="req">*</span></label>
              <input
                v-model="ruleForm.triggerThreshold"
                type="text"
                class="form-input font-mono"
                placeholder="例如 65.00"
                required
              />
            </div>
            <div class="form-item">
              <label>恢复阈值 <span class="req">*</span></label>
              <input
                v-model="ruleForm.recoveryThreshold"
                type="text"
                class="form-input font-mono"
                placeholder="例如 58.00"
                required
              />
            </div>
          </div>

          <div class="form-item">
            <label>告警严重级别 <span class="req">*</span></label>
            <select v-model="ruleForm.alarmLevel" class="form-input">
              <option value="CRITICAL">紧急 (CRITICAL)</option>
              <option value="MAJOR">严重 (MAJOR)</option>
              <option value="MINOR">次要 (MINOR)</option>
              <option value="WARNING">预警 (WARNING)</option>
            </select>
          </div>

          <div class="modal-footer">
            <button type="button" class="btn btn-secondary" @click="createRuleModalVisible = false">取消</button>
            <button type="submit" class="btn btn-primary" :disabled="isSubmitting">保存告警规则</button>
          </div>
        </form>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted } from "vue";
import {
  PageHeader,
  FilterBar,
  DataTable,
  StatusBadge,
  QuantityText,
  ErrorState,
} from "../../components/common";
import type { TableColumn } from "../../components/common/DataTable.vue";
import type { ViewState } from "../../types/common";
import type {
  DeviceProfileItem,
  DeviceProfileCreateRequest,
  DeviceAlarmRuleCreateRequest,
  AlarmOperator,
  AlarmLevel,
} from "../../types/iot";
import {
  getDeviceProfiles,
  createDeviceProfile,
  createAlarmRule,
} from "../../api/iot";

const viewState = ref<ViewState>("loading");
const errorMessage = ref("");

const profileList = ref<DeviceProfileItem[]>([]);
const total = ref(0);
const queryParams = reactive({
  page: 1,
  size: 10,
  keyword: "",
  status: "",
});

const columns: TableColumn[] = [
  { key: "profileCode", label: "模型编码", width: "180px" },
  { key: "profileName", label: "模型名称 / 场景说明", minWidth: "220px" },
  { key: "offlineTimeoutSeconds", label: "离线阈值", width: "110px", align: "right" },
  { key: "metricsCount", label: "指标集规格", width: "120px", align: "center" },
  { key: "status", label: "状态", width: "110px", align: "center" },
  { key: "createdAt", label: "创建时间", width: "160px" },
  { key: "actions", label: "操作", width: "110px", align: "center" },
];

const drawerVisible = ref(false);
const activeProfile = ref<DeviceProfileItem | null>(null);

const createModalVisible = ref(false);
const isSubmitting = ref(false);
const createForm = reactive<DeviceProfileCreateRequest>({
  profileCode: "",
  profileName: "",
  description: "",
  offlineTimeoutSeconds: 60,
  metrics: [
    { metricCode: "temperature", metricName: "工作温度", valueType: "FLOAT", unit: "℃", required: true },
  ],
});

const createRuleModalVisible = ref(false);
const ruleForm = reactive<DeviceAlarmRuleCreateRequest>({
  ruleCode: "",
  ruleName: "",
  deviceProfileId: "prof-001",
  metricCode: "spindle_temp",
  operator: "GT" as AlarmOperator,
  triggerThreshold: "65.00",
  recoveryThreshold: "58.00",
  alarmLevel: "CRITICAL" as AlarmLevel,
});

async function fetchProfileList() {
  viewState.value = "loading";
  errorMessage.value = "";
  try {
    const res = await getDeviceProfiles({
      page: queryParams.page,
      size: queryParams.size,
      keyword: queryParams.keyword.trim() || undefined,
      status: queryParams.status || undefined,
    });
    if (res.data) {
      profileList.value = res.data.records || [];
      total.value = res.data.total || 0;
      viewState.value = profileList.value.length === 0 ? "empty" : "ready";
    }
  } catch (err: any) {
    errorMessage.value = err.message || "加载设备模型失败";
    viewState.value = "error";
  }
}

function handleSearch() {
  queryParams.page = 1;
  fetchProfileList();
}

function handleReset() {
  queryParams.keyword = "";
  queryParams.status = "";
  queryParams.page = 1;
  fetchProfileList();
}

function handlePageChange(page: number) {
  queryParams.page = page;
  fetchProfileList();
}

function openMetricsDrawer(item: DeviceProfileItem) {
  activeProfile.value = item;
  drawerVisible.value = true;
}

function openCreateModal() {
  createForm.profileCode = "";
  createForm.profileName = "";
  createForm.description = "";
  createForm.offlineTimeoutSeconds = 60;
  createForm.metrics = [
    { metricCode: "temperature", metricName: "工作温度", valueType: "FLOAT", unit: "℃", required: true },
  ];
  createModalVisible.value = true;
}

function addMetricRow() {
  createForm.metrics.push({
    metricCode: "",
    metricName: "",
    valueType: "FLOAT",
    unit: "",
    required: true,
  });
}

function removeMetricRow(idx: number) {
  if (createForm.metrics.length > 1) {
    createForm.metrics.splice(idx, 1);
  }
}

async function submitCreateProfile() {
  if (!createForm.profileCode || !createForm.profileName) return;
  isSubmitting.value = true;
  try {
    await createDeviceProfile(createForm);
    createModalVisible.value = false;
    await fetchProfileList();
  } catch (err: any) {
    alert(`创建设备模型失败：${err.message}`);
  } finally {
    isSubmitting.value = false;
  }
}

function openCreateRuleModal() {
  ruleForm.ruleCode = "";
  ruleForm.ruleName = "";
  ruleForm.deviceProfileId = profileList.value[0]?.id as string || "prof-001";
  ruleForm.metricCode = "spindle_temp";
  ruleForm.operator = "GT";
  ruleForm.triggerThreshold = "65.00";
  ruleForm.recoveryThreshold = "58.00";
  ruleForm.alarmLevel = "CRITICAL";
  createRuleModalVisible.value = true;
}

async function submitCreateRule() {
  if (!ruleForm.ruleCode || !ruleForm.metricCode) return;
  isSubmitting.value = true;
  try {
    await createAlarmRule(ruleForm);
    createRuleModalVisible.value = false;
    alert("单指标告警规则已成功创建！");
  } catch (err: any) {
    alert(`创建告警规则失败：${err.message}`);
  } finally {
    isSubmitting.value = false;
  }
}

onMounted(() => {
  fetchProfileList();
});
</script>

<style scoped>
.iot-view-container {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.filter-select-group {
  display: flex;
  align-items: center;
  gap: 8px;
}

.filter-label {
  font-size: 13px;
  color: #94a3b8;
}

.filter-select {
  background: rgba(30, 41, 59, 0.8);
  border: 1px solid rgba(255, 255, 255, 0.12);
  color: #f8fafc;
  padding: 6px 12px;
  border-radius: 6px;
  font-size: 13px;
  outline: none;
}

.highlight-code {
  color: #38bdf8;
  font-weight: 600;
}

.profile-name-cell {
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.p-title {
  color: #f1f5f9;
  font-weight: 500;
}

.p-desc {
  font-size: 11px;
}

.badge-count {
  font-size: 12px;
  color: #94a3b8;
}

.action-btn-group {
  display: flex;
  align-items: center;
  justify-content: center;
}

.btn-text {
  background: none;
  border: none;
  color: #38bdf8;
  font-size: 12px;
  cursor: pointer;
  padding: 2px 6px;
}

.btn-text:hover {
  text-decoration: underline;
}

/* 抽屉 */
.drawer-overlay {
  position: fixed;
  inset: 0;
  background: rgba(0, 0, 0, 0.6);
  backdrop-filter: blur(3px);
  z-index: 1000;
  display: flex;
  justify-content: flex-end;
}

.drawer-panel {
  width: 600px;
  max-width: 90vw;
  background: #0f172a;
  border-left: 1px solid rgba(255, 255, 255, 0.12);
  height: 100%;
  display: flex;
  flex-direction: column;
  box-shadow: -10px 0 25px rgba(0, 0, 0, 0.5);
}

.drawer-header {
  padding: 20px;
  border-bottom: 1px solid rgba(255, 255, 255, 0.08);
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
}

.drawer-tag {
  font-size: 11px;
  color: #38bdf8;
}

.drawer-title {
  margin: 4px 0 0;
  font-size: 16px;
  color: #f8fafc;
}

.drawer-body {
  flex: 1;
  padding: 20px;
  overflow-y: auto;
}

.drawer-section-title {
  font-size: 13px;
  font-weight: 600;
  color: #94a3b8;
  margin-bottom: 12px;
}

.nested-table {
  width: 100%;
  border-collapse: collapse;
  font-size: 13px;
}

.nested-table th {
  background: rgba(30, 41, 59, 0.5);
  padding: 8px 10px;
  color: #94a3b8;
  font-weight: 500;
  border-bottom: 1px solid rgba(255, 255, 255, 0.08);
}

.nested-table td {
  padding: 10px;
  border-bottom: 1px solid rgba(255, 255, 255, 0.05);
  color: #cbd5e1;
}

.drawer-footer {
  padding: 16px 20px;
  border-top: 1px solid rgba(255, 255, 255, 0.08);
  display: flex;
  justify-content: flex-end;
}

/* 模态框 */
.modal-mask {
  position: fixed;
  inset: 0;
  background: rgba(0, 0, 0, 0.7);
  backdrop-filter: blur(4px);
  z-index: 1000;
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
  max-width: 520px;
  box-shadow: 0 20px 30px rgba(0, 0, 0, 0.5);
  overflow: hidden;
}

.modal-large { max-width: 680px; }

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

.modal-body {
  padding: 20px;
  display: flex;
  flex-direction: column;
  gap: 16px;
  max-height: 75vh;
  overflow-y: auto;
}

.form-grid.two-col {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 12px;
}

.form-grid.three-col {
  display: grid;
  grid-template-columns: 1fr 1fr 1fr;
  gap: 12px;
}

.form-item {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.form-item label {
  font-size: 12px;
  color: #94a3b8;
}

.req { color: #f87171; }

.form-input {
  background: rgba(30, 41, 59, 0.8);
  border: 1px solid rgba(255, 255, 255, 0.12);
  color: #f8fafc;
  padding: 8px 12px;
  border-radius: 6px;
  font-size: 13px;
  outline: none;
}

.form-input:focus { border-color: #38bdf8; }

.form-section {
  display: flex;
  flex-direction: column;
  gap: 10px;
  border-top: 1px dashed rgba(255, 255, 255, 0.1);
  padding-top: 14px;
}

.section-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.section-head label {
  font-size: 13px;
  font-weight: 600;
  color: #cbd5e1;
}

.metrics-form-list {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.metric-form-row {
  display: flex;
  gap: 8px;
  align-items: center;
}

.m-code { flex: 2.5; }
.m-name { flex: 2.5; }
.m-type { flex: 2; }
.m-unit { flex: 1.5; }

.btn-del-row {
  background: rgba(239, 68, 68, 0.15);
  border: 1px solid rgba(239, 68, 68, 0.3);
  color: #f87171;
  padding: 6px 10px;
  border-radius: 6px;
  cursor: pointer;
}

.btn-del-row:disabled {
  opacity: 0.3;
  cursor: not-allowed;
}

.modal-footer {
  display: flex;
  align-items: center;
  justify-content: flex-end;
  gap: 12px;
  padding: 16px 20px;
  border-top: 1px solid rgba(255, 255, 255, 0.08);
  background: rgba(0, 0, 0, 0.2);
}

.btn-close {
  background: none;
  border: none;
  color: #94a3b8;
  font-size: 16px;
  cursor: pointer;
}

.btn-sm {
  padding: 4px 10px;
  font-size: 12px;
  border-radius: 4px;
  cursor: pointer;
}

.text-primary { color: #38bdf8 !important; }
.text-warning { color: #fbbf24 !important; }
</style>
