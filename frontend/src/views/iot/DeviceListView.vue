<template>
  <div class="iot-view-container">
    <!-- 统一页面头部 -->
    <PageHeader
      title="设备台账 (Device) 管理"
      tag="IOT / ASSET & GATEWAY"
      description="管理接入平台的机台物理设备。一期协议严格限定为 MQTT QoS 1，设备关联稳定的工作中心或区域，生命周期状态控制其是否允许连接接入。"
    >
      <template #actions>
        <el-button type="primary" :icon="Plus" @click="openCreateModal">
          新建设备
        </el-button>
      </template>
    </PageHeader>

    <!-- 统一筛选栏 -->
    <FilterBar
      v-model="queryParams.keyword"
      placeholder="搜索设备编码或名称..."
      @search="handleSearch"
      @reset="handleReset"
    >
      <div class="filter-select-group">
        <label class="filter-label">接入权限：</label>
        <el-select
          v-model="queryParams.lifecycleStatus"
          placeholder="全部"
          clearable
          style="width: 170px"
          @change="handleSearch"
        >
          <el-option label="全部" value="" />
          <el-option label="允许接入 (ACTIVE)" value="ACTIVE" />
          <el-option label="已禁用 (DISABLED)" value="DISABLED" />
        </el-select>
      </div>
    </FilterBar>

    <!-- 错误异常提示 -->
    <ErrorState
      v-if="viewState === 'error'"
      title="设备台账列表加载失败"
      :message="errorMessage"
      @retry="fetchDeviceList"
    />

    <!-- 数据表格 -->
    <DataTable
      v-else
      :columns="columns"
      :data="deviceList"
      :loading="viewState === 'loading'"
      :page="queryParams.page"
      :size="queryParams.size"
      :total="total"
      empty-text="暂无匹配的设备台账记录"
      @page-change="handlePageChange"
    >
      <!-- 设备编码 -->
      <template #deviceCode="{ row }">
        <span class="font-mono highlight-code">{{ row.deviceCode }}</span>
      </template>

      <!-- 设备名称与归属 -->
      <template #deviceName="{ row }">
        <div class="dev-info-cell">
          <span class="dev-title">{{ row.deviceName }}</span>
          <span class="dev-location text-muted">
            {{ row.workCenterName || "未返回" }} · {{ row.areaName || "未返回" }}
          </span>
        </div>
      </template>

      <!-- 所属 Profile -->
      <template #deviceProfileId="{ row }">
        <span class="profile-tag">{{ row.deviceProfileName || row.deviceProfileId }}</span>
      </template>

      <!-- 接入协议 -->
      <template #protocolType="{ row }">
        <span class="protocol-badge font-mono">{{ row.protocolType }}</span>
      </template>

      <!-- 实时在线状态 (带呼吸动画) -->
      <template #onlineStatus="{ row }">
        <StatusBadge
          :type="row.statusSnapshot?.onlineStatus === 'ONLINE' ? 'success' : 'default'"
          :text="row.statusSnapshot?.onlineStatus === 'ONLINE' ? '在线' : '离线'"
          :pulsing="row.statusSnapshot?.onlineStatus === 'ONLINE'"
        />
      </template>

      <!-- 运行快照状态 -->
      <template #runningStatus="{ row }">
        <StatusBadge
          :type="row.statusSnapshot?.runningStatus === 'RUNNING' ? 'primary' : row.statusSnapshot?.runningStatus === 'IDLE' ? 'info' : 'warning'"
          :text="row.statusSnapshot?.runningStatus === 'RUNNING' ? '运转中' : row.statusSnapshot?.runningStatus === 'IDLE' ? '空闲待机' : '停机'"
        />
      </template>

      <!-- 接入生命周期状态 -->
      <template #lifecycleStatus="{ row }">
        <span
          class="lifecycle-dot-tag"
          :class="row.lifecycleStatus === 'ACTIVE' ? 'is-active' : 'is-disabled'"
        >
          {{ row.lifecycleStatus === 'ACTIVE' ? '允许接入' : '已禁用' }}
        </span>
      </template>

      <!-- 操作 (受 allowedActions 约束) -->
      <template #actions="{ row }">
        <div class="action-btn-group">
          <!-- 查看详情 -->
          <el-button link type="primary" @click="$emit('select-detail', row)">
            详情
          </el-button>

          <!-- 生成接入凭证 (唤起安全对话框) -->
          <el-button
            link
            type="warning"
            :disabled="!isActionAllowed(row, 'credential')"
            :title="getActionDisabledReason(row, 'credential') || '签发 MQTT 接入凭证'"
            @click="openCredentialDialog(row)"
          >
            凭证
          </el-button>

          <!-- 启停生命周期 -->
          <el-button
            link
            :type="row.lifecycleStatus === 'ACTIVE' ? 'danger' : 'success'"
            :disabled="!isActionAllowed(row, 'toggle-status')"
            :title="getActionDisabledReason(row, 'toggle-status') || (row.lifecycleStatus === 'ACTIVE' ? '停用设备' : '启用设备')"
            @click="promptToggleStatus(row)"
          >
            {{ row.lifecycleStatus === 'ACTIVE' ? '停用' : '启用' }}
          </el-button>
        </div>
      </template>
    </DataTable>

    <!-- 新建设备模态框 -->
    <el-dialog
      v-model="createModalVisible"
      title="新建设备台账"
      width="640px"
      append-to-body
      destroy-on-close
    >
      <el-form label-position="top" class="custom-el-form">
        <el-alert
          v-if="profileOptions.length === 0"
          title="当前租户还没有设备模型，请先创建设备模型再登记设备。"
          type="warning"
          :closable="false"
          show-icon
          class="profile-empty-alert"
        >
          <template #default>
            <el-button link type="primary" @click="goToProfiles">前往设备模型维护</el-button>
          </template>
        </el-alert>
        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item label="设备业务编码 (Device Code)" required>
              <el-input
                v-model="createForm.deviceCode"
                placeholder="例如 DEV-CNC-A03"
                class="font-mono"
              />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="设备名称" required>
              <el-input
                v-model="createForm.deviceName"
                placeholder="例如 精密立式加工中心 3 号机"
              />
            </el-form-item>
          </el-col>
        </el-row>

        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item label="所属设备模型 (Profile)" required>
              <el-select
                v-model="createForm.deviceProfileId"
                placeholder="请选择设备模型"
                filterable
                style="width: 100%"
              >
                <el-option
                  v-for="p in profileOptions"
                  :key="p.id"
                  :label="`${p.profileName} (${p.profileCode})`"
                  :value="String(p.id)"
                />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="协议类型 (Protocol)" required>
              <el-input
                value="MQTT (一期标准接入)"
                disabled
                class="font-mono"
              />
            </el-form-item>
          </el-col>
        </el-row>

        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item label="归属工作中心 (WorkCenter)">
              <el-input
                v-model="createForm.workCenterId"
                placeholder="例如 wc-02 (机加一车间)"
              />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="归属厂区/车间区域 (Area)">
              <el-input
                v-model="createForm.areaId"
                placeholder="例如 area-m-01"
              />
            </el-form-item>
          </el-col>
        </el-row>
      </el-form>

      <template #footer>
        <span class="dialog-footer">
          <el-button @click="createModalVisible = false">取消</el-button>
          <el-button
            type="primary"
            :loading="isSubmitting"
            @click="submitCreateDevice"
          >
            保存设备台账
          </el-button>
        </span>
      </template>
    </el-dialog>

    <!-- 设备接入凭证专用弹窗 (明文只展示一次，不持久化) -->
    <DeviceCredentialDialog
      v-model:visible="credentialDialog.visible"
      :device-id="credentialDialog.deviceId"
      :device-code="credentialDialog.deviceCode"
      :device-name="credentialDialog.deviceName"
      @issued="handleCredentialIssued"
    />

    <!-- 启停设备二次确认对话框 -->
    <ConfirmDialog
      v-model:visible="toggleConfirm.visible"
      :title="toggleConfirm.title"
      :message="toggleConfirm.message"
      :danger="toggleConfirm.isDanger"
      :loading="toggleConfirm.loading"
      @confirm="handleConfirmToggle"
    />
  </div>
</template>

<script setup lang="ts">
import { isActionAllowed as checkAction, getActionDisabledReason as getDisabledReason } from "../../utils/actionGuard";
import type { AllowedAction } from "../../types/common";
import { ref, reactive, onMounted } from "vue";
import { useRouter } from "vue-router";
import { Plus } from "@element-plus/icons-vue";
import { ElMessage } from "element-plus";
import {
  PageHeader,
  FilterBar,
  DataTable,
  StatusBadge,
  ConfirmDialog,
  ErrorState,
} from "../../components/common";
import type { TableColumn } from "../../components/common/DataTable.vue";
import type { ViewState } from "../../types/common";
import type {
  DeviceItem,
  DeviceCreateRequest,
  DeviceProfileItem,
} from "../../types/iot";
import {
  getDevices,
  createDevice,
  toggleDeviceLifecycleStatus,
  getDeviceProfiles,
} from "../../api/iot";
import DeviceCredentialDialog from "./DeviceCredentialDialog.vue";

defineEmits<{
  (e: "select-detail", item: DeviceItem): void;
}>();

const router = useRouter();

const viewState = ref<ViewState>("loading");
const errorMessage = ref("");

const deviceList = ref<DeviceItem[]>([]);
const profileOptions = ref<DeviceProfileItem[]>([]);
const total = ref(0);
const queryParams = reactive({
  page: 1,
  size: 10,
  keyword: "",
  lifecycleStatus: "",
});

const columns: TableColumn[] = [
  { key: "deviceCode", label: "设备编码", width: "160px" },
  { key: "deviceName", label: "设备名称 / 车间归属", minWidth: "220px" },
  { key: "deviceProfileId", label: "所属模型", minWidth: "180px" },
  { key: "protocolType", label: "接入协议", width: "100px", align: "center" },
  { key: "onlineStatus", label: "通信在线快照", width: "120px", align: "center" },
  { key: "runningStatus", label: "运行状态快照", width: "120px", align: "center" },
  { key: "lifecycleStatus", label: "接入控制", width: "110px", align: "center" },
  { key: "actions", label: "操作", width: "160px", align: "center" },
];

const createModalVisible = ref(false);
const isSubmitting = ref(false);
const createForm = reactive<DeviceCreateRequest>({
  deviceCode: "",
  deviceName: "",
  deviceProfileId: "",
  protocolType: "MQTT",
  workCenterId: "",
  areaId: "",
});

// 凭证弹窗状态
const credentialDialog = reactive({
  visible: false,
  deviceId: "",
  deviceCode: "",
  deviceName: "",
});

// 启停确认状态
const toggleConfirm = reactive({
  visible: false,
  loading: false,
  title: "",
  message: "",
  isDanger: false,
  targetDevice: null as DeviceItem | null,
});

/** 检查指定操作是否被后端或状态机允许 */
function isActionAllowed(item: { allowedActions?: AllowedAction[] | null }, action: string): boolean {
  return checkAction(item.allowedActions, action);
}

/** 获取指定操作被禁用的原因提示 */
function getActionDisabledReason(item: { allowedActions?: AllowedAction[] | null }, action: string): string | undefined {
  return getDisabledReason(item.allowedActions, action);
}

/** 分页获取设备列表 */
async function fetchDeviceList() {
  viewState.value = "loading";
  errorMessage.value = "";
  try {
    const res = await getDevices({
      page: queryParams.page,
      size: queryParams.size,
      keyword: queryParams.keyword.trim() || undefined,
      lifecycleStatus: queryParams.lifecycleStatus || undefined,
    });
    if (res.data) {
      deviceList.value = res.data.records || [];
      total.value = res.data.total || 0;
      viewState.value = deviceList.value.length === 0 ? "empty" : "ready";
    }
  } catch (err: any) {
    errorMessage.value = err.message || "请求设备列表失败";
    viewState.value = "error";
  }
}

/** 加载设备模型下拉候选列表 */
async function fetchProfileOptions() {
  try {
    // 修改：设备档案下拉框一次读取当前租户完整候选目录，避免默认分页只返回前 20 条。
    const res = await getDeviceProfiles({ page: 1, size: 1000 });
    if (res.data) {
      profileOptions.value = res.data.records || [];
      if (profileOptions.value.length > 0 && !createForm.deviceProfileId) {
        createForm.deviceProfileId = profileOptions.value[0].id as string;
      }
    }
  } catch (e) {
    console.warn("加载模型选项失败:", e);
  }
}

/** 触发搜索 */
function handleSearch() {
  queryParams.page = 1;
  fetchDeviceList();
}

/** 重置搜索条件 */
function handleReset() {
  queryParams.keyword = "";
  queryParams.lifecycleStatus = "";
  queryParams.page = 1;
  fetchDeviceList();
}

/** 分页变更处理 */
function handlePageChange(page: number) {
  queryParams.page = page;
  fetchDeviceList();
}

/** 打开新建设备台账对话框 */
function openCreateModal() {
  createForm.deviceCode = "";
  createForm.deviceName = "";
  createForm.workCenterId = "";
  createForm.areaId = "";
  if (profileOptions.value.length > 0) {
    createForm.deviceProfileId = profileOptions.value[0].id as string;
  }
  createModalVisible.value = true;
}

/** 关闭设备弹窗并进入设备模型维护页，解除首个设备创建的前置阻断。 */
async function goToProfiles() {
  createModalVisible.value = false;
  await router.push({ name: "IotProfileList" });
}

/** 提交新建设备台账 */
async function submitCreateDevice() {
  if (!createForm.deviceCode || !createForm.deviceName || !createForm.deviceProfileId) {
    ElMessage.warning("请填写完整的设备信息");
    return;
  }
  isSubmitting.value = true;
  try {
    await createDevice(createForm);
    createModalVisible.value = false;
    ElMessage.success("设备台账创建成功！");
    await fetchDeviceList();
  } catch (err: any) {
    ElMessage.error(`创建设备失败：${err.message}`);
  } finally {
    isSubmitting.value = false;
  }
}

/** 打开凭证生成弹窗 */
function openCredentialDialog(item: DeviceItem) {
  credentialDialog.deviceId = item.id as string;
  credentialDialog.deviceCode = item.deviceCode;
  credentialDialog.deviceName = item.deviceName;
  credentialDialog.visible = true;
}

/** 凭证成功签发后刷新设备列表 */
function handleCredentialIssued() {
  fetchDeviceList();
}

/** 提示启停生命周期状态确认对话框 */
function promptToggleStatus(item: DeviceItem) {
  toggleConfirm.targetDevice = item;
  const isCurrentlyActive = item.lifecycleStatus.toLowerCase() === "active";
  toggleConfirm.title = isCurrentlyActive ? "停用设备接入确认" : "启用设备接入确认";
  toggleConfirm.message = isCurrentlyActive
    ? `确认停用设备【${item.deviceCode}】？停用后该设备持有的 MQTT 接入凭证将无法继续建立连接。`
    : `确认启用设备【${item.deviceCode}】接入？`;
  toggleConfirm.isDanger = isCurrentlyActive;
  toggleConfirm.visible = true;
}

/** 执行启停生命周期状态切换 */
async function handleConfirmToggle() {
  if (!toggleConfirm.targetDevice) return;
  toggleConfirm.loading = true;
  try {
    const nextStatus = toggleConfirm.targetDevice.lifecycleStatus.toLowerCase() === "active" ? "Disabled" : "Active";
    await toggleDeviceLifecycleStatus(toggleConfirm.targetDevice.id as string, nextStatus);
    toggleConfirm.visible = false;
    ElMessage.success("设备生命周期状态更新成功！");
    await fetchDeviceList();
  } catch (err: any) {
    ElMessage.error(`状态切换失败：${err.message}`);
  } finally {
    toggleConfirm.loading = false;
  }
}

onMounted(() => {
  fetchDeviceList();
  fetchProfileOptions();
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

.dev-info-cell {
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.dev-title {
  color: #f1f5f9;
  font-weight: 500;
}

.dev-location {
  font-size: 11px;
}

.profile-tag {
  color: #cbd5e1;
  font-size: 13px;
}

.protocol-badge {
  background: rgba(56, 189, 248, 0.12);
  border: 1px solid rgba(56, 189, 248, 0.25);
  color: #38bdf8;
  padding: 2px 8px;
  border-radius: 4px;
  font-size: 11px;
}

.lifecycle-dot-tag {
  font-size: 12px;
  font-weight: 500;
}

.lifecycle-dot-tag.is-active {
  color: #34d399;
}

.lifecycle-dot-tag.is-disabled {
  color: #64748b;
}

.action-btn-group {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
}

.custom-el-form :deep(.el-form-item__label) {
  color: #94a3b8;
  font-size: 13px;
  padding-bottom: 4px;
}
</style>
