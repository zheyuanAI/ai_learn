<template>
  <div class="iot-view-container">
    <!-- 统一页面头部 -->
    <PageHeader
      title="设备台账 (Device) 管理"
      tag="IOT / ASSET & GATEWAY"
      description="管理接入平台的机台物理设备。一期协议严格限定为 MQTT QoS 1，设备关联稳定的工作中心或区域，生命周期状态控制其是否允许连接接入。"
    >
      <template #actions>
        <button type="button" class="btn btn-primary" @click="openCreateModal">
          <span class="btn-icon">＋</span>
          <span>新建设备</span>
        </button>
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
        <select v-model="queryParams.lifecycleStatus" class="filter-select" @change="handleSearch">
          <option value="">全部</option>
          <option value="ACTIVE">允许接入 (ACTIVE)</option>
          <option value="DISABLED">已禁用 (DISABLED)</option>
        </select>
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
            {{ row.workCenterName || "通用车间" }} · {{ row.areaName || "加工区" }}
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
          <button type="button" class="btn-text text-primary" @click="$emit('select-detail', row)">
            详情
          </button>

          <!-- 生成接入凭证 (唤起安全对话框) -->
          <button
            type="button"
            class="btn-text text-warning"
            :disabled="!isActionAllowed(row, 'credential')"
            :title="getActionDisabledReason(row, 'credential') || '签发 MQTT 接入凭证'"
            @click="openCredentialDialog(row)"
          >
            凭证
          </button>

          <!-- 启停生命周期 -->
          <button
            type="button"
            class="btn-text"
            :class="row.lifecycleStatus === 'ACTIVE' ? 'text-danger' : 'text-success'"
            :disabled="!isActionAllowed(row, 'toggle-status')"
            :title="getActionDisabledReason(row, 'toggle-status') || (row.lifecycleStatus === 'ACTIVE' ? '停用设备' : '启用设备')"
            @click="promptToggleStatus(row)"
          >
            {{ row.lifecycleStatus === 'ACTIVE' ? '停用' : '启用' }}
          </button>
        </div>
      </template>
    </DataTable>

    <!-- 新建设备模态框 -->
    <div v-if="createModalVisible" class="modal-mask" @click.self="createModalVisible = false">
      <div class="modal-card modal-large">
        <div class="modal-header">
          <h3 class="modal-title">新建设备台账</h3>
          <button type="button" class="btn-close" @click="createModalVisible = false">✕</button>
        </div>

        <form class="modal-body" @submit.prevent="submitCreateDevice">
          <div class="form-grid two-col">
            <div class="form-item">
              <label>设备业务编码 (Device Code) <span class="req">*</span></label>
              <input
                v-model="createForm.deviceCode"
                type="text"
                class="form-input font-mono"
                placeholder="例如 DEV-CNC-A03"
                required
              />
            </div>
            <div class="form-item">
              <label>设备名称 <span class="req">*</span></label>
              <input
                v-model="createForm.deviceName"
                type="text"
                class="form-input"
                placeholder="例如 精密立式加工中心 3 号机"
                required
              />
            </div>
          </div>

          <div class="form-grid two-col">
            <div class="form-item">
              <label>所属设备模型 (Profile) <span class="req">*</span></label>
              <select v-model="createForm.deviceProfileId" class="form-input" required>
                <option v-for="p in profileOptions" :key="p.id" :value="p.id">
                  {{ p.profileName }} ({{ p.profileCode }})
                </option>
              </select>
            </div>
            <div class="form-item">
              <label>协议类型 (Protocol) <span class="req">*</span></label>
              <input
                type="text"
                class="form-input font-mono"
                value="MQTT (一期标准接入)"
                disabled
              />
            </div>
          </div>

          <div class="form-grid two-col">
            <div class="form-item">
              <label>归属工作中心 (WorkCenter)</label>
              <input
                v-model="createForm.workCenterId"
                type="text"
                class="form-input"
                placeholder="例如 wc-02 (机加一车间)"
              />
            </div>
            <div class="form-item">
              <label>归属厂区/车间区域 (Area)</label>
              <input
                v-model="createForm.areaId"
                type="text"
                class="form-input"
                placeholder="例如 area-m-01"
              />
            </div>
          </div>

          <div class="modal-footer">
            <button type="button" class="btn btn-secondary" @click="createModalVisible = false">取消</button>
            <button type="submit" class="btn btn-primary" :disabled="isSubmitting">保存设备台账</button>
          </div>
        </form>
      </div>
    </div>

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
import { ref, reactive, onMounted } from "vue";
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

function isActionAllowed(item: DeviceItem, action: string): boolean {
  if (!item.allowedActions || item.allowedActions.length === 0) return true;
  const match = item.allowedActions.find((a) => a.action === action);
  return match ? match.enabled : true;
}

function getActionDisabledReason(item: DeviceItem, action: string): string | undefined {
  const match = item.allowedActions?.find((a) => a.action === action);
  return match && !match.enabled ? match.reason : undefined;
}

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

async function fetchProfileOptions() {
  try {
    const res = await getDeviceProfiles();
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

function handleSearch() {
  queryParams.page = 1;
  fetchDeviceList();
}

function handleReset() {
  queryParams.keyword = "";
  queryParams.lifecycleStatus = "";
  queryParams.page = 1;
  fetchDeviceList();
}

function handlePageChange(page: number) {
  queryParams.page = page;
  fetchDeviceList();
}

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

async function submitCreateDevice() {
  if (!createForm.deviceCode || !createForm.deviceName || !createForm.deviceProfileId) return;
  isSubmitting.value = true;
  try {
    await createDevice(createForm);
    createModalVisible.value = false;
    await fetchDeviceList();
  } catch (err: any) {
    alert(`创建设备失败：${err.message}`);
  } finally {
    isSubmitting.value = false;
  }
}

function openCredentialDialog(item: DeviceItem) {
  credentialDialog.deviceId = item.id as string;
  credentialDialog.deviceCode = item.deviceCode;
  credentialDialog.deviceName = item.deviceName;
  credentialDialog.visible = true;
}

function handleCredentialIssued() {
  fetchDeviceList();
}

function promptToggleStatus(item: DeviceItem) {
  toggleConfirm.targetDevice = item;
  const isCurrentlyActive = item.lifecycleStatus === "ACTIVE";
  toggleConfirm.title = isCurrentlyActive ? "停用设备接入确认" : "启用设备接入确认";
  toggleConfirm.message = isCurrentlyActive
    ? `确认停用设备【${item.deviceCode}】？停用后该设备持有的 MQTT 接入凭证将无法继续建立连接。`
    : `确认启用设备【${item.deviceCode}】接入？`;
  toggleConfirm.isDanger = isCurrentlyActive;
  toggleConfirm.visible = true;
}

async function handleConfirmToggle() {
  if (!toggleConfirm.targetDevice) return;
  toggleConfirm.loading = true;
  try {
    const nextStatus = toggleConfirm.targetDevice.lifecycleStatus === "ACTIVE" ? "DISABLED" : "ACTIVE";
    await toggleDeviceLifecycleStatus(toggleConfirm.targetDevice.id as string, nextStatus);
    toggleConfirm.visible = false;
    await fetchDeviceList();
  } catch (err: any) {
    alert(`状态切换失败：${err.message}`);
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

.btn-text {
  background: none;
  border: none;
  font-size: 12px;
  cursor: pointer;
  padding: 2px 4px;
}

.btn-text:hover:not(:disabled) {
  text-decoration: underline;
}

.btn-text:disabled {
  color: #64748b;
  cursor: not-allowed;
  text-decoration: none;
}

.text-primary { color: #38bdf8 !important; }
.text-warning { color: #fbbf24 !important; }
.text-danger { color: #f87171 !important; }
.text-success { color: #34d399 !important; }

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

.modal-large { max-width: 640px; }

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
}

.form-grid.two-col {
  display: grid;
  grid-template-columns: 1fr 1fr;
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
</style>
