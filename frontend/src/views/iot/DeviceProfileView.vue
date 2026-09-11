<template>
  <div class="iot-view-container">
    <!-- 统一页面头部 -->
    <PageHeader
      title="设备模型 (Device Profile) 管理"
      tag="IOT / METRICS SCHEMA"
      description="定义设备类别及其允许上报的遥测指标规格、离线判定超时时长及单指标告警触发阈值规则。"
    >
      <template #actions>
        <el-button :icon="Plus" @click="openCreateRuleModal">
          添加告警规则
        </el-button>
        <el-button type="primary" :icon="Plus" @click="openCreateModal">
          新建设备模型
        </el-button>
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
        <el-select
          v-model="queryParams.status"
          placeholder="全部状态"
          clearable
          style="width: 170px"
          @change="handleSearch"
        >
          <el-option label="全部状态" value="" />
          <el-option label="启用中 (ACTIVE)" value="ACTIVE" />
          <el-option label="已停用 (DISABLED)" value="DISABLED" />
        </el-select>
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
          <el-button link type="primary" @click="openMetricsDrawer(row)">
            指标规格
          </el-button>
        </div>
      </template>
    </DataTable>

    <!-- 指标规格抽屉 -->
    <el-drawer
      v-model="drawerVisible"
      :title="activeProfile ? `${activeProfile.profileName} (${activeProfile.profileCode})` : '指标规格详情'"
      size="560px"
      append-to-body
    >
      <div v-if="activeProfile" class="drawer-body-wrapper">
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
      <template #footer>
        <div class="drawer-footer">
          <el-button @click="drawerVisible = false">关闭</el-button>
        </div>
      </template>
    </el-drawer>

    <!-- 弹窗 1：新建设备模型 -->
    <el-dialog
      v-model="createModalVisible"
      title="新建设备模型 (Device Profile)"
      width="680px"
      append-to-body
      destroy-on-close
    >
      <el-form label-position="top" class="custom-el-form">
        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item label="模型编码" required>
              <el-input
                v-model="createForm.profileCode"
                placeholder="例如 PROF-CNC-MILL"
                class="font-mono"
              />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="模型名称" required>
              <el-input
                v-model="createForm.profileName"
                placeholder="例如 数控加工中心 Profile"
              />
            </el-form-item>
          </el-col>
        </el-row>

        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item label="离线超时判定时间 (秒)">
              <el-input
                v-model.number="createForm.offlineTimeoutSeconds"
                type="number"
                placeholder="默认 60"
                class="font-mono"
              />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="模型简要描述说明">
              <el-input
                v-model="createForm.description"
                placeholder="应用场景或机型规格"
              />
            </el-form-item>
          </el-col>
        </el-row>

        <!-- 动态指标列表编辑 -->
        <div class="form-section">
          <div class="section-head">
            <label>指标规格定义 (Metrics) <span class="req">*</span></label>
            <el-button size="small" :icon="Plus" @click="addMetricRow">
              添加指标
            </el-button>
          </div>

          <div class="metrics-form-list">
            <div
              v-for="(m, idx) in createForm.metrics"
              :key="idx"
              class="metric-form-row"
            >
              <div class="m-code">
                <el-input
                  v-model="m.metricCode"
                  placeholder="指标编码 (如 temp)"
                  class="font-mono"
                />
              </div>
              <div class="m-name">
                <el-input
                  v-model="m.metricName"
                  placeholder="指标名称 (如 温度)"
                />
              </div>
              <div class="m-type">
                <el-select v-model="m.valueType" style="width: 100%">
                  <el-option label="FLOAT" value="FLOAT" />
                  <el-option label="INTEGER" value="INTEGER" />
                  <el-option label="BOOLEAN" value="BOOLEAN" />
                  <el-option label="STRING" value="STRING" />
                </el-select>
              </div>
              <div class="m-unit">
                <el-input
                  v-model="m.unit"
                  placeholder="单位 (℃)"
                  class="font-mono"
                />
              </div>
              <el-button
                type="danger"
                :icon="Delete"
                circle
                size="small"
                :disabled="createForm.metrics.length <= 1"
                @click="removeMetricRow(idx)"
              />
            </div>
          </div>
        </div>
      </el-form>

      <template #footer>
        <span class="dialog-footer">
          <el-button @click="createModalVisible = false">取消</el-button>
          <el-button
            type="primary"
            :loading="isSubmitting"
            @click="submitCreateProfile"
          >
            保存设备模型
          </el-button>
        </span>
      </template>
    </el-dialog>

    <!-- 弹窗 2：新建单指标告警规则 -->
    <el-dialog
      v-model="createRuleModalVisible"
      title="新建单指标阈值告警规则"
      width="680px"
      append-to-body
      destroy-on-close
    >
      <el-form label-position="top" class="custom-el-form">
        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item label="规则编码" required>
              <el-input
                v-model="ruleForm.ruleCode"
                placeholder="例如 RULE-SPINDLE-OVERHEAT"
                class="font-mono"
              />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="规则名称" required>
              <el-input
                v-model="ruleForm.ruleName"
                placeholder="例如 主轴超温严重告警"
              />
            </el-form-item>
          </el-col>
        </el-row>

        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item label="作用设备模型 ID" required>
              <el-input
                v-model="ruleForm.deviceProfileId"
                placeholder="请输入设备模型 UUID"
                class="font-mono"
              />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="监控指标编码 (Metric Code)" required>
              <el-input
                v-model="ruleForm.metricCode"
                placeholder="例如 spindle_temp"
                class="font-mono"
              />
            </el-form-item>
          </el-col>
        </el-row>

        <el-row :gutter="16">
          <el-col :span="8">
            <el-form-item label="比较运算符" required>
              <el-select v-model="ruleForm.operator" style="width: 100%">
                <el-option label="大于 (>)" value="GT" />
                <el-option label="大于等于 (>=)" value="GTE" />
                <el-option label="小于 (<)" value="LT" />
                <el-option label="小于等于 (<=)" value="LTE" />
                <el-option label="等于 (==)" value="EQ" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="触发阈值" required>
              <el-input
                v-model="ruleForm.triggerThreshold"
                placeholder="例如 65.00"
                class="font-mono"
              />
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="恢复阈值" required>
              <el-input
                v-model="ruleForm.recoveryThreshold"
                placeholder="例如 58.00"
                class="font-mono"
              />
            </el-form-item>
          </el-col>
        </el-row>

        <el-form-item label="告警严重级别" required>
          <el-select v-model="ruleForm.alarmLevel" style="width: 100%">
            <el-option label="紧急 (CRITICAL)" value="CRITICAL" />
            <el-option label="严重 (MAJOR)" value="MAJOR" />
            <el-option label="次要 (MINOR)" value="MINOR" />
            <el-option label="预警 (WARNING)" value="WARNING" />
          </el-select>
        </el-form-item>
      </el-form>

      <template #footer>
        <span class="dialog-footer">
          <el-button @click="createRuleModalVisible = false">取消</el-button>
          <el-button
            type="primary"
            :loading="isSubmitting"
            @click="submitCreateRule"
          >
            保存告警规则
          </el-button>
        </span>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted } from "vue";
import { Plus, Delete } from "@element-plus/icons-vue";
import { ElMessage } from "element-plus";
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
  deviceProfileId: "",
  metricCode: "spindle_temp",
  operator: "GT" as AlarmOperator,
  triggerThreshold: "65.00",
  recoveryThreshold: "58.00",
  alarmLevel: "CRITICAL" as AlarmLevel,
});

/** 分页加载设备模型列表 */
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

/** 搜索模型 */
function handleSearch() {
  queryParams.page = 1;
  fetchProfileList();
}

/** 重置搜索条件 */
function handleReset() {
  queryParams.keyword = "";
  queryParams.status = "";
  queryParams.page = 1;
  fetchProfileList();
}

/** 分页变化处理 */
function handlePageChange(page: number) {
  queryParams.page = page;
  fetchProfileList();
}

/** 打开指标规格抽屉 */
function openMetricsDrawer(item: DeviceProfileItem) {
  activeProfile.value = item;
  drawerVisible.value = true;
}

/** 打开新建设备模型对话框 */
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

/** 新增一行指标规格 */
function addMetricRow() {
  createForm.metrics.push({
    metricCode: "",
    metricName: "",
    valueType: "FLOAT",
    unit: "",
    required: true,
  });
}

/** 移除一行指标规格 */
function removeMetricRow(idx: number) {
  if (createForm.metrics.length > 1) {
    createForm.metrics.splice(idx, 1);
  }
}

/** 提交创建设备模型 */
async function submitCreateProfile() {
  if (!createForm.profileCode || !createForm.profileName) {
    ElMessage.warning("请填写模型编码和名称");
    return;
  }
  isSubmitting.value = true;
  try {
    await createDeviceProfile(createForm);
    createModalVisible.value = false;
    ElMessage.success("设备模型创建成功！");
    await fetchProfileList();
  } catch (err: any) {
    ElMessage.error(`创建设备模型失败：${err.message}`);
  } finally {
    isSubmitting.value = false;
  }
}

/** 打开新建告警规则对话框 */
function openCreateRuleModal() {
  ruleForm.ruleCode = "";
  ruleForm.ruleName = "";
  ruleForm.deviceProfileId = (profileList.value[0]?.id as string) || "";
  ruleForm.metricCode = "spindle_temp";
  ruleForm.operator = "GT";
  ruleForm.triggerThreshold = "65.00";
  ruleForm.recoveryThreshold = "58.00";
  ruleForm.alarmLevel = "CRITICAL";
  createRuleModalVisible.value = true;
}

/** 提交新建告警规则 */
async function submitCreateRule() {
  if (!ruleForm.ruleCode || !ruleForm.metricCode || !ruleForm.deviceProfileId) {
    ElMessage.warning("请填写完整的告警规则信息");
    return;
  }
  isSubmitting.value = true;
  try {
    await createAlarmRule(ruleForm);
    createRuleModalVisible.value = false;
    ElMessage.success("单指标告警规则已成功创建！");
  } catch (err: any) {
    ElMessage.error(`创建告警规则失败：${err.message}`);
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

/* 抽屉样式 */
.drawer-body-wrapper {
  display: flex;
  flex-direction: column;
  gap: 14px;
}

.drawer-section-title {
  font-size: 13px;
  font-weight: 600;
  color: #38bdf8;
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
  text-align: left;
}

.nested-table td {
  padding: 10px;
  border-bottom: 1px solid rgba(255, 255, 255, 0.05);
  color: #cbd5e1;
}

.drawer-footer {
  display: flex;
  justify-content: flex-end;
}

/* 表单内部 */
.form-section {
  display: flex;
  flex-direction: column;
  gap: 10px;
  border-top: 1px dashed rgba(255, 255, 255, 0.1);
  padding-top: 14px;
  margin-top: 6px;
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

.req { color: #f87171; }

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

.custom-el-form :deep(.el-form-item__label) {
  color: #94a3b8;
  font-size: 13px;
  padding-bottom: 4px;
}

.text-primary { color: #38bdf8 !important; }
.text-warning { color: #fbbf24 !important; }
</style>
