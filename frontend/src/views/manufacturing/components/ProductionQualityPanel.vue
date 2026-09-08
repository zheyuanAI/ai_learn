<template>
  <div class="production-quality-panel">
    <CommandFeedback :error="lastError" :can-retry="canRetry" :executing="isExecuting" @retry="retry" />
    <!-- 顶部状态指示横幅 -->
    <div v-if="hasFailedInspections" class="quality-alert-banner alert-danger">
      <span class="banner-icon">⚠️</span>
      <div class="banner-content">
        <strong>存在不合格生产质检记录</strong>
        <span>当前工单存在未关闭的不合格批次，请执行不良品隔离、报废或特采关闭处置，防止不良品流入成品入库。</span>
      </div>
    </div>
    <div v-else-if="inspections.length > 0 && allPassed" class="quality-alert-banner alert-success">
      <span class="banner-icon">✓</span>
      <div class="banner-content">
        <strong>工序质检全部合格</strong>
        <span>报工批次均已通过生产质量检验，可正常前往办理【产成品完工入库】。</span>
      </div>
    </div>

    <!-- 区域 1：现场报工待检记录 -->
    <div class="panel-section">
      <div class="section-header">
        <h4 class="section-title">1. 关联现场工序报工记录 (Work Reports)</h4>
        <span class="text-muted font-xs">共 {{ workReports.length }} 条报工事实</span>
      </div>

      <div v-if="loadingReports" class="loading-text">⏳ 正在加载现场报工记录...</div>
      <div v-else-if="workReports.length === 0" class="empty-box">
        当前工单尚无工序报工记录。请先在【工序执行】完成加工并提交报工。
      </div>
      <div v-else class="report-table-wrapper">
        <table class="panel-table">
          <thead>
            <tr>
              <th>报工单号</th>
              <th>工序序号/名称</th>
              <th style="text-align: right;">申报合格</th>
              <th style="text-align: right;">申报不良</th>
              <th>报工时间</th>
              <th>备注说明</th>
              <th style="text-align: center;">操作</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="wr in workReports" :key="wr.id">
              <td class="font-mono text-cyan">{{ wr.reportNo }}</td>
              <td>{{ wr.operationName || `#${wr.operationId}` }}</td>
              <td style="text-align: right;" class="text-success font-bold font-mono">{{ wr.qualifiedQty }} 件</td>
              <td style="text-align: right;" class="text-danger font-bold font-mono">{{ wr.defectQty }} 件</td>
              <td class="font-mono text-muted text-xs">{{ wr.reportTime ? wr.reportTime.substring(0, 19).replace('T', ' ') : '-' }}</td>
              <td class="text-muted text-xs">{{ wr.remark || '-' }}</td>
              <td style="text-align: center;">
                <button
                  type="button"
                  class="btn-act-primary"
                  @click="openCreateInspection(wr)"
                >
                  发起质检
                </button>
              </td>
            </tr>
          </tbody>
        </table>
      </div>
    </div>

    <!-- 区域 2：生产质检单与处置决定 -->
    <div class="panel-section" style="margin-top: 20px;">
      <div class="section-header">
        <h4 class="section-title">2. 生产质检事实与检验结论 (Quality Inspections)</h4>
        <span class="text-muted font-xs">共 {{ inspections.length }} 条检验记录</span>
      </div>

      <div v-if="loadingInspections" class="loading-text">⏳ 正在加载质检记录...</div>
      <div v-else-if="inspections.length === 0" class="empty-box">
        暂无检验记录。请在上方报工行项点击【发起质检】创建首件/过程检验单。
      </div>
      <div v-else class="report-table-wrapper">
        <table class="panel-table">
          <thead>
            <tr>
              <th>质检单号</th>
              <th>检验类型</th>
              <th style="text-align: right;">抽样数量</th>
              <th>检验状态</th>
              <th>判定结果</th>
              <th style="text-align: right;">合格 / 不良</th>
              <th>处置决定</th>
              <th style="text-align: center;">操作</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="ins in inspections" :key="ins.id">
              <td class="font-mono text-primary">{{ ins.inspectionNo }}</td>
              <td>
                <span class="type-tag">{{ inspectionTypeText(ins.inspectionType) }}</span>
              </td>
              <td style="text-align: right;" class="font-mono">{{ ins.sampleQty }} 件</td>
              <td>
                <StatusBadge
                  :type="ins.status === 'Completed' || ins.status === 'Passed' ? 'success' : ins.status === 'Failed' ? 'danger' : 'warning'"
                  :text="ins.status === 'Draft' ? '待评定 (Draft)' : ins.status === 'Passed' ? '已合格' : ins.status === 'Failed' ? '不合格' : ins.status"
                />
              </td>
              <td>
                <span
                  v-if="ins.result"
                  :class="ins.result === 'Passed' ? 'text-success font-bold' : 'text-danger font-bold'"
                >
                  {{ ins.result === 'Passed' ? '合格 (Passed)' : '不合格 (Failed)' }}
                </span>
                <span v-else class="text-muted">-</span>
              </td>
              <td style="text-align: right;" class="font-mono text-xs">
                <span class="text-success">{{ ins.qualifiedQty || '0' }}</span> /
                <span class="text-danger">{{ ins.defectQty || '0' }}</span>
              </td>
              <td>
                <span v-if="ins.disposition" class="font-mono disp-badge">
                  {{ ins.disposition }}
                </span>
                <span v-else class="text-muted text-xs">-</span>
              </td>
              <td style="text-align: center;">
                <div class="action-cell">
                  <!-- 录入质检结果 (Draft -> Passed/Failed) -->
                  <button
                    v-if="ins.status === 'Draft' || !ins.result"
                    type="button"
                    class="btn-act-cyan"
                    @click="openSubmitModal(ins)"
                  >
                    录入结果
                  </button>

                  <!-- 关闭 Failed 质检 (disposition) -->
                  <button
                    v-if="ins.result === 'Failed' && !ins.disposition"
                    type="button"
                    class="btn-act-warning"
                    @click="openCloseModal(ins)"
                  >
                    不良处置
                  </button>
                  <span v-if="ins.result === 'Passed' || ins.disposition" class="text-muted font-xs">已完结</span>
                </div>
              </td>
            </tr>
          </tbody>
        </table>
      </div>
    </div>

    <!-- 模态框 1：新建质检单 Draft -->
    <div v-if="createModalVisible" class="modal-mask" @click.self="createModalVisible = false">
      <div class="modal-card">
        <div class="modal-header">
          <h3 class="modal-title">发起生产质检</h3>
          <button type="button" class="btn-close" @click="createModalVisible = false">✕</button>
        </div>
        <form class="modal-body" @submit.prevent="submitCreateInspection">
          <div class="form-item">
            <label>关联报工单</label>
            <input :value="selectedReport?.reportNo" class="form-input font-mono" disabled />
          </div>
          <div class="form-item">
            <label>质检编号</label>
            <input v-model="createForm.inspectionNo" class="form-input font-mono" required />
          </div>
          <div class="form-grid two-col">
            <div class="form-item">
              <label>检验类型 <span class="req">*</span></label>
              <select v-model="createForm.inspectionType" class="form-select" required>
                <option value="FIRST_ARTICLE">首件检验 (First Article)</option>
                <option value="ROUTING_INSPECTION">过程巡检 (In Process)</option>
                <option value="FINAL_INSPECTION">完工总检 (Final Inspection)</option>
              </select>
            </div>
            <div class="form-item">
              <label>抽样数量 <span class="req">*</span></label>
              <input v-model="createForm.sampleQty" type="number" min="1" class="form-input font-mono" required />
            </div>
          </div>
          <div class="modal-footer">
            <button type="button" class="btn btn-secondary" @click="createModalVisible = false">取消</button>
            <button type="submit" class="btn btn-primary" :disabled="submitting">保存质检单 (Draft)</button>
          </div>
        </form>
      </div>
    </div>

    <!-- 模态框 2：提交检验结果 -->
    <div v-if="submitModalVisible" class="modal-mask" @click.self="submitModalVisible = false">
      <div class="modal-card">
        <div class="modal-header">
          <h3 class="modal-title">评定质检结论 — {{ selectedInspection?.inspectionNo }}</h3>
          <button type="button" class="btn-close" @click="submitModalVisible = false">✕</button>
        </div>
        <form class="modal-body" @submit.prevent="submitResult">
          <div class="form-item">
            <label>检验结论判定 <span class="req">*</span></label>
            <select v-model="submitForm.result" class="form-select" required>
              <option value="Passed">合格放行 (Passed)</option>
              <option value="Failed">不合格超差 (Failed)</option>
            </select>
          </div>
          <div class="form-grid two-col">
            <div class="form-item">
              <label>合格数量 <span class="req">*</span></label>
              <input v-model="submitForm.qualifiedQty" type="number" step="0.01" class="form-input font-mono" required />
            </div>
            <div class="form-item">
              <label>不良品数量 <span class="req">*</span></label>
              <input v-model="submitForm.defectQty" type="number" step="0.01" class="form-input font-mono" required />
            </div>
          </div>
          <div class="modal-footer">
            <button type="button" class="btn btn-secondary" @click="submitModalVisible = false">取消</button>
            <button type="submit" class="btn btn-primary" :disabled="submitting">提交结论</button>
          </div>
        </form>
      </div>
    </div>

    <!-- 模态框 3：不良品处置关闭 -->
    <div v-if="closeModalVisible" class="modal-mask" @click.self="closeModalVisible = false">
      <div class="modal-card">
        <div class="modal-header">
          <h3 class="modal-title">不良品处置闭环 — {{ selectedInspection?.inspectionNo }}</h3>
          <button type="button" class="btn-close" @click="closeModalVisible = false">✕</button>
        </div>
        <form class="modal-body" @submit.prevent="submitClose">
          <div class="form-item">
            <label>处置方式 <span class="req">*</span></label>
            <select v-model="closeDisposition" class="form-select" required>
              <option value="ISOLATE">隔离审查 (ISOLATE) - 移至 QualityHold 隔离区</option>
              <option value="SCRAP">直接报废 (SCRAP) - 扣减制造在制品并计入损耗</option>
              <option value="CLOSE">特采放行 (CLOSE) - 经工程评审特采让步接收</option>
            </select>
          </div>
          <div class="modal-footer">
            <button type="button" class="btn btn-secondary" @click="closeModalVisible = false">取消</button>
            <button type="submit" class="btn btn-primary" :disabled="submitting">确认处置闭环</button>
          </div>
        </form>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
/**
 * 生产工单专属质量检验与处置面板 (ProductionQualityPanel)
 * 职责：展示工单报工记录，录入生产质检事实，对不合格进行隔离/报废/特采处置闭环
 */
import { ref, reactive, computed, watch, onMounted } from "vue";
import { useCommand } from "@/composables/useCommand";
import CommandFeedback from "@/components/common/CommandFeedback.vue";
import StatusBadge from "@/components/common/StatusBadge.vue";
import {
  getWorkReports,
  getQualityInspections,
  createQualityInspection,
  submitQualityInspection,
  closeQualityInspection,
} from "@/api/manufacturing";

const props = defineProps<{
  workOrderId: string | number;
}>();

const emit = defineEmits<{
  (e: "refresh"): void;
}>();

const loadingReports = ref(false);
const loadingInspections = ref(false);
const submitting = ref(false);
const { execute, retry, isExecuting, canRetry, lastError } = useCommand();

const workReports = ref<any[]>([]);
const inspections = ref<any[]>([]);

const selectedReport = ref<any | null>(null);
const selectedInspection = ref<any | null>(null);

const createModalVisible = ref(false);
const submitModalVisible = ref(false);
const closeModalVisible = ref(false);

const createForm = reactive({
  inspectionNo: "",
  inspectionType: "FIRST_ARTICLE",
  sampleQty: "1",
});

const submitForm = reactive({
  result: "Passed",
  qualifiedQty: "10.00",
  defectQty: "0.00",
});

const closeDisposition = ref<"ISOLATE" | "SCRAP" | "CLOSE">("ISOLATE");

const hasFailedInspections = computed(() =>
  inspections.value.some((ins) => ins.result === "Failed" && !ins.disposition)
);

const allPassed = computed(() =>
  inspections.value.length > 0 && inspections.value.every((ins) => ins.result === "Passed" || ins.disposition)
);

function inspectionTypeText(type: string): string {
  const map: Record<string, string> = {
    FIRST_ARTICLE: "首件检验",
    ROUTING_INSPECTION: "过程巡检",
    FINAL_INSPECTION: "完工总检",
  };
  return map[type] || type;
}

async function loadData() {
  if (!props.workOrderId) return;
  loadingReports.value = true;
  loadingInspections.value = true;
  try {
    const [repRes, insRes] = await Promise.allSettled([
      getWorkReports(props.workOrderId),
      getQualityInspections(props.workOrderId),
    ]);
    if (repRes.status === "fulfilled") {
      workReports.value = repRes.value.data || [];
    }
    if (insRes.status === "fulfilled") {
      inspections.value = insRes.value.data || [];
    }
  } catch (err) {
    console.error("[ProductionQualityPanel] 加载质检数据失败:", err);
  } finally {
    loadingReports.value = false;
    loadingInspections.value = false;
  }
}

function openCreateInspection(report: any) {
  selectedReport.value = report;
  createForm.inspectionNo = `INS-${Date.now().toString().slice(-6)}`;
  createForm.inspectionType = "FIRST_ARTICLE";
  createForm.sampleQty = String(report.qualifiedQty || "1");
  createModalVisible.value = true;
}

async function submitCreateInspection() {
  if (!selectedReport.value) return;
  const report = selectedReport.value;
  submitting.value = true;
  try {
    const created = await execute((key) => createQualityInspection({
      workReportId: report.id,
      inspectionNo: createForm.inspectionNo,
      inspectionType: createForm.inspectionType,
      sampleQty: createForm.sampleQty,
    }, key), { onConflict: loadData });
    if (!created?.data?.id) {
      throw new Error("服务端未返回 inspection_id，已阻止继续办理质检结果");
    }
    createModalVisible.value = false;
    await loadData();
    emit("refresh");
  } catch (err: any) {
    alert(`创建质检单失败：${err?.message || err}`);
  } finally {
    submitting.value = false;
  }
}

function openSubmitModal(ins: any) {
  selectedInspection.value = ins;
  submitForm.result = "Passed";
  submitForm.qualifiedQty = String(ins.sampleQty || "10.00");
  submitForm.defectQty = "0.00";
  submitModalVisible.value = true;
}

async function submitResult() {
  if (!selectedInspection.value) return;
  const inspection = selectedInspection.value;
  submitting.value = true;
  try {
    await execute((key) => submitQualityInspection(inspection.id, {
      result: submitForm.result,
      qualifiedQty: submitForm.qualifiedQty,
      defectQty: submitForm.defectQty,
    }, key), { onConflict: loadData });
    submitModalVisible.value = false;
    await loadData();
    emit("refresh");
  } catch (err: any) {
    alert(`提交质检结论失败：${err?.message || err}`);
  } finally {
    submitting.value = false;
  }
}

function openCloseModal(ins: any) {
  selectedInspection.value = ins;
  closeDisposition.value = "ISOLATE";
  closeModalVisible.value = true;
}

async function submitClose() {
  if (!selectedInspection.value) return;
  const inspection = selectedInspection.value;
  submitting.value = true;
  try {
    await execute((key) => closeQualityInspection(inspection.id, closeDisposition.value, key), { onConflict: loadData });
    closeModalVisible.value = false;
    await loadData();
    emit("refresh");
  } catch (err: any) {
    alert(`关闭处置失败：${err?.message || err}`);
  } finally {
    submitting.value = false;
  }
}

watch(() => props.workOrderId, () => {
  loadData();
});

onMounted(() => {
  loadData();
});
</script>

<style scoped>
.production-quality-panel {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.quality-alert-banner {
  display: flex;
  align-items: flex-start;
  gap: 12px;
  padding: 12px 16px;
  border-radius: 8px;
  font-size: 13px;
}

.alert-danger {
  background: rgba(239, 68, 68, 0.12);
  border: 1px solid rgba(239, 68, 68, 0.3);
  color: #fca5a5;
}

.alert-success {
  background: rgba(16, 185, 129, 0.12);
  border: 1px solid rgba(16, 185, 129, 0.3);
  color: #6ee7b7;
}

.banner-icon {
  font-size: 18px;
  line-height: 1;
}

.banner-content {
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.panel-section {
  background: rgba(15, 23, 42, 0.6);
  border: 1px solid rgba(255, 255, 255, 0.08);
  border-radius: 8px;
  padding: 16px;
}

.section-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 12px;
}

.section-title {
  margin: 0;
  font-size: 14px;
  color: #f8fafc;
  font-weight: 600;
}

.loading-text, .empty-box {
  padding: 24px;
  text-align: center;
  color: #94a3b8;
  font-size: 13px;
  background: rgba(30, 41, 59, 0.3);
  border-radius: 6px;
}

.report-table-wrapper {
  overflow-x: auto;
}

.panel-table {
  width: 100%;
  border-collapse: collapse;
  font-size: 12px;
}

.panel-table th, .panel-table td {
  padding: 8px 12px;
  border-bottom: 1px solid rgba(255, 255, 255, 0.06);
  text-align: left;
}

.panel-table th {
  color: #94a3b8;
  background: rgba(30, 41, 59, 0.5);
  font-weight: 500;
}

.font-mono {
  font-family: var(--font-mono, monospace);
}

.text-cyan { color: #38bdf8; }
.text-primary { color: #0284c7; }
.text-success { color: #34d399; }
.text-danger { color: #f87171; }
.text-muted { color: #64748b; }
.font-bold { font-weight: 600; }
.font-xs { font-size: 11px; }

.type-tag {
  background: rgba(56, 189, 248, 0.12);
  color: #38bdf8;
  padding: 2px 6px;
  border-radius: 4px;
  font-size: 11px;
}

.disp-badge {
  background: rgba(245, 158, 11, 0.15);
  color: #fbbf24;
  padding: 2px 6px;
  border-radius: 4px;
  font-size: 11px;
}

.action-cell {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 6px;
}

.btn-act-primary, .btn-act-cyan, .btn-act-warning {
  padding: 3px 8px;
  border-radius: 4px;
  font-size: 11px;
  cursor: pointer;
  border: 1px solid transparent;
}

.btn-act-primary {
  background: rgba(56, 189, 248, 0.15);
  border-color: rgba(56, 189, 248, 0.3);
  color: #38bdf8;
}

.btn-act-primary:hover {
  background: #0284c7;
  color: #ffffff;
}

.btn-act-cyan {
  background: rgba(34, 211, 238, 0.15);
  border-color: rgba(34, 211, 238, 0.3);
  color: #22d3ee;
}

.btn-act-cyan:hover {
  background: #0891b2;
  color: #ffffff;
}

.btn-act-warning {
  background: rgba(245, 158, 11, 0.15);
  border-color: rgba(245, 158, 11, 0.3);
  color: #fbbf24;
}

.btn-act-warning:hover {
  background: #d97706;
  color: #ffffff;
}

/* 模态弹窗 */
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
  max-width: 480px;
  box-shadow: 0 20px 30px rgba(0, 0, 0, 0.5);
  overflow: hidden;
}

.modal-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 16px 20px;
  border-bottom: 1px solid rgba(255, 255, 255, 0.08);
}

.modal-title {
  margin: 0;
  font-size: 15px;
  color: #f8fafc;
}

.btn-close {
  background: none;
  border: none;
  color: #94a3b8;
  font-size: 16px;
  cursor: pointer;
}

.modal-body {
  padding: 20px;
  display: flex;
  flex-direction: column;
  gap: 14px;
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

.form-input, .form-select {
  background: rgba(30, 41, 59, 0.8);
  border: 1px solid rgba(255, 255, 255, 0.12);
  color: #f8fafc;
  padding: 7px 10px;
  border-radius: 6px;
  font-size: 13px;
  outline: none;
}

.modal-footer {
  display: flex;
  align-items: center;
  justify-content: flex-end;
  gap: 12px;
  padding: 14px 20px;
  border-top: 1px solid rgba(255, 255, 255, 0.08);
  background: rgba(0, 0, 0, 0.2);
}
</style>
