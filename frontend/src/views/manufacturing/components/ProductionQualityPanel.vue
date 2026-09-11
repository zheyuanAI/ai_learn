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
                <el-button
                  size="small"
                  type="primary"
                  plain
                  @click="openCreateInspection(wr)"
                >
                  发起质检
                </el-button>
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
                  <el-button
                    v-if="ins.status === 'Draft' || !ins.result"
                    size="small"
                    type="primary"
                    plain
                    @click="openSubmitModal(ins)"
                  >
                    录入结果
                  </el-button>

                  <!-- 关闭 Failed 质检 (disposition) -->
                  <el-button
                    v-if="ins.result === 'Failed' && !ins.disposition"
                    size="small"
                    type="warning"
                    plain
                    @click="openCloseModal(ins)"
                  >
                    不良处置
                  </el-button>
                  <span v-if="ins.result === 'Passed' || ins.disposition" class="text-muted font-xs">已完结</span>
                </div>
              </td>
            </tr>
          </tbody>
        </table>
      </div>
    </div>

    <!-- 模态框 1：新建质检单 Draft -->
    <el-dialog
      v-model="createModalVisible"
      title="发起生产质检"
      width="520px"
      append-to-body
      destroy-on-close
    >
      <el-form label-position="top" class="custom-el-form">
        <el-form-item label="关联报工单">
          <el-input :value="selectedReport?.reportNo" disabled class="font-mono" />
        </el-form-item>
        <el-form-item label="质检编号" required>
          <el-input v-model="createForm.inspectionNo" class="font-mono" />
        </el-form-item>
        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item label="检验类型" required>
              <el-select v-model="createForm.inspectionType" style="width: 100%">
                <el-option label="首件检验 (First Article)" value="FIRST_ARTICLE" />
                <el-option label="过程巡检 (In Process)" value="ROUTING_INSPECTION" />
                <el-option label="完工总检 (Final Inspection)" value="FINAL_INSPECTION" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="抽样数量" required>
              <el-input v-model="createForm.sampleQty" type="number" min="1" class="font-mono" />
            </el-form-item>
          </el-col>
        </el-row>
      </el-form>

      <template #footer>
        <span class="dialog-footer">
          <el-button @click="createModalVisible = false">取消</el-button>
          <el-button
            type="primary"
            :loading="submitting"
            @click="submitCreateInspection"
          >
            保存质检单 (Draft)
          </el-button>
        </span>
      </template>
    </el-dialog>

    <!-- 模态框 2：提交检验结果 -->
    <el-dialog
      v-model="submitModalVisible"
      :title="`评定质检结论 — ${selectedInspection?.inspectionNo || ''}`"
      width="520px"
      append-to-body
      destroy-on-close
    >
      <el-form label-position="top" class="custom-el-form">
        <el-form-item label="检验结论判定" required>
          <el-select v-model="submitForm.result" style="width: 100%">
            <el-option label="合格放行 (Passed)" value="Passed" />
            <el-option label="不合格超差 (Failed)" value="Failed" />
          </el-select>
        </el-form-item>
        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item label="合格数量" required>
              <el-input v-model="submitForm.qualifiedQty" type="number" step="0.01" class="font-mono" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="不良品数量" required>
              <el-input v-model="submitForm.defectQty" type="number" step="0.01" class="font-mono" />
            </el-form-item>
          </el-col>
        </el-row>
      </el-form>

      <template #footer>
        <span class="dialog-footer">
          <el-button @click="submitModalVisible = false">取消</el-button>
          <el-button
            type="primary"
            :loading="submitting"
            @click="submitResult"
          >
            提交结论
          </el-button>
        </span>
      </template>
    </el-dialog>

    <!-- 模态框 3：不良品处置关闭 -->
    <el-dialog
      v-model="closeModalVisible"
      :title="`不良品处置闭环 — ${selectedInspection?.inspectionNo || ''}`"
      width="520px"
      append-to-body
      destroy-on-close
    >
      <el-form label-position="top" class="custom-el-form">
        <el-form-item label="处置方式" required>
          <el-select v-model="closeDisposition" style="width: 100%">
            <el-option label="隔离审查 (ISOLATE) - 移至 QualityHold 隔离区" value="ISOLATE" />
            <el-option label="直接报废 (SCRAP) - 扣减制造在制品并计入损耗" value="SCRAP" />
            <el-option label="特采放行 (CLOSE) - 经工程评审特采让步接收" value="CLOSE" />
          </el-select>
        </el-form-item>
      </el-form>

      <template #footer>
        <span class="dialog-footer">
          <el-button @click="closeModalVisible = false">取消</el-button>
          <el-button
            type="primary"
            :loading="submitting"
            @click="submitClose"
          >
            确认处置闭环
          </el-button>
        </span>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
/**
 * 生产工单专属质量检验与处置面板 (ProductionQualityPanel)
 * 职责：展示工单报工记录，录入生产质检事实，对不合格进行隔离/报废/特采处置闭环
 */
import { ref, reactive, computed, watch, onMounted } from "vue";
import { ElMessage } from "element-plus";
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

/** 转换检验类型文本 */
function inspectionTypeText(type: string): string {
  const map: Record<string, string> = {
    FIRST_ARTICLE: "首件检验",
    ROUTING_INSPECTION: "过程巡检",
    FINAL_INSPECTION: "完工总检",
  };
  return map[type] || type;
}

/** 加载当前工单的报工记录与质检单列表 */
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

/** 打开新建质检单对话框 */
function openCreateInspection(report: any) {
  selectedReport.value = report;
  createForm.inspectionNo = `INS-${Date.now().toString().slice(-6)}`;
  createForm.inspectionType = "FIRST_ARTICLE";
  createForm.sampleQty = String(report.qualifiedQty || "1");
  createModalVisible.value = true;
}

/** 提交创建质检单 */
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
    ElMessage.success("质检单创建成功！");
    await loadData();
    emit("refresh");
  } catch (err: any) {
    ElMessage.error(`创建质检单失败：${err?.message || err}`);
  } finally {
    submitting.value = false;
  }
}

/** 打开评定质检结论对话框 */
function openSubmitModal(ins: any) {
  selectedInspection.value = ins;
  submitForm.result = "Passed";
  submitForm.qualifiedQty = String(ins.sampleQty || "10.00");
  submitForm.defectQty = "0.00";
  submitModalVisible.value = true;
}

/** 提交质检评定结论 */
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
    ElMessage.success("质检结论评定成功！");
    await loadData();
    emit("refresh");
  } catch (err: any) {
    ElMessage.error(`提交质检结论失败：${err?.message || err}`);
  } finally {
    submitting.value = false;
  }
}

/** 打开不良品处置对话框 */
function openCloseModal(ins: any) {
  selectedInspection.value = ins;
  closeDisposition.value = "ISOLATE";
  closeModalVisible.value = true;
}

/** 提交不良品处置闭环 */
async function submitClose() {
  if (!selectedInspection.value) return;
  const inspection = selectedInspection.value;
  submitting.value = true;
  try {
    await execute((key) => closeQualityInspection(inspection.id, closeDisposition.value, key), { onConflict: loadData });
    closeModalVisible.value = false;
    ElMessage.success("不良品处置闭环已完成！");
    await loadData();
    emit("refresh");
  } catch (err: any) {
    ElMessage.error(`关闭处置失败：${err?.message || err}`);
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

.custom-el-form :deep(.el-form-item__label) {
  color: #94a3b8;
  font-size: 13px;
  padding-bottom: 4px;
}
</style>
