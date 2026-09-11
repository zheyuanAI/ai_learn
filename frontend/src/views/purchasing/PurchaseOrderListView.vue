<template>
  <div class="purchase-order-list-view">
    <CommandFeedback :error="lastError" :can-retry="canRetry" :executing="isExecuting" @retry="retry" />
    <!-- 统一页面头部（支持到货验收工作台模式切换，修复 F04） -->
    <PageHeader
      :title="isReceiptMode ? '采购到货验收工作台' : '采购入库控制台'"
      :tag="isReceiptMode ? 'WAREHOUSE / INBOUND RECEIPT' : 'CORE / PURCHASING / INBOUND'"
      :description="isReceiptMode ? '仓库人员执行采购到货外观验收：核对实到数量与外观拒收数量（到货=拒收+实收），实际接收货物统一送入 QualityHold 隔离位，放行前严禁上架。' : '采购全链路状态流转：未提交 ➔ 已提交 ➔ 已审核 ➔ 部分收货 ➔ 已完成。仓库到货外观验收数量恒等（到货=拒收+实收），拒收数量不入库并保留为待收；实际接收货物全部进入质量隔离位（QualityHold），放行后入暂存位（ReceivingStaging）再上架存储位（Storage）。'"
    >
      <template #actions>
        <!-- 修改用途：只有具备采购订单创建权限的角色才显示新建入口。 -->
        <el-button
          v-if="!isReceiptMode && hasPermission('pur:order:create')"
          type="primary"
          :icon="Plus"
          @click="isCreateModalOpen = true"
        >
          新建采购订单
        </el-button>
      </template>
    </PageHeader>

    <!-- 到货验收模式专属指引横幅 -->
    <div v-if="isReceiptMode" class="receipt-guide-banner">
      <div class="guide-icon">📦</div>
      <div class="guide-content">
        <strong>仓库到货验收工作台指引：</strong>
        <span>请选择处于【已审核】或【部分收货】状态的采购订单，点击操作列的【外观验收接收】录入实收数量。实收后货物将自动转入 QualityHold 隔离位并可一键前往质检。</span>
      </div>
    </div>

    <!-- 顶层状态快速筛选标签条 -->
    <div class="status-tabs-row" style="margin-bottom: 14px">
      <el-radio-group
        v-model="queryParams.status"
        @change="(val) => switchStatusFilter(String(val || ''))"
      >
        <el-radio-button
          v-for="st in statusFilters"
          :key="st.value"
          :value="st.value"
          :label="st.label"
        />
      </el-radio-group>
    </div>

    <!-- 筛选搜索栏 -->
    <FilterBar
      v-model="queryParams.keyword"
      placeholder="搜索采购单号、供应商名称..."
      @search="fetchOrders"
      @reset="resetFilter"
    />

    <!-- 四态展示 -->
    <ErrorState
      v-if="viewState === 'error'"
      title="获取采购单列表失败"
      :message="errorMessage"
      @retry="fetchOrders"
    />

    <EmptyState
      v-else-if="viewState === 'empty'"
      title="暂无采购订单记录"
      description="当前筛选条件下未发现采购订单，您可以点击右上角新建采购订单。"
    >
      <template #action>
        <!-- 修改用途：避免仓库/质检角色看到无权执行的采购订单创建入口。 -->
        <el-button
          v-if="hasPermission('pur:order:create')"
          type="primary"
          size="small"
          :icon="Plus"
          @click="isCreateModalOpen = true"
        >
          立即新建采购单
        </el-button>
      </template>
    </EmptyState>

    <DataTable
      v-else
      :columns="columns"
      :data="orderList"
      :loading="viewState === 'loading'"
      :total="totalCount"
      :page="queryParams.page"
      :size="queryParams.size"
      @page-change="handlePageChange"
    >
      <!-- 采购单号 -->
      <template #poNo="{ value }">
        <span class="mono-code">{{ value }}</span>
      </template>

      <!-- 供应商 -->
      <template #supplier="{ row }">
        <div class="supplier-cell">
          <span class="supp-name">{{ row.supplierName }}</span>
          <span class="supp-code">{{ row.supplierCode }}</span>
        </div>
      </template>

      <!-- 状态 -->
      <template #status="{ value, row }">
        <div class="status-cell">
          <StatusBadge
            :type="statusBadgeType(value)"
            :text="statusText(value)"
          />
          <span v-if="row.completionType === 'Manual'" class="tag-manual">人工完成</span>
        </div>
      </template>

      <!-- 明细统计与待收余量 -->
      <template #pendingSummary="{ row }">
        <div class="pending-cell">
          <span v-if="row.lines && row.lines[0]">
            待收: <QuantityText :value="row.lines[0].pendingQty" :unit="row.lines[0].uom" />
          </span>
          <span v-else class="text-muted">-</span>
        </div>
      </template>

      <!-- 操作列 -->
      <template #actions="{ row }">
        <el-button type="primary" link size="small" @click="openOrderDetail(row)">
          详情
        </el-button>
        <el-button
          v-if="hasPermission('pur:receipt:confirm') && (row.status === 'Approved' || row.status === 'PartiallyReceived')"
          type="success"
          link
          size="small"
          @click="openReceiptConfirm(row)"
        >
          验收接收
        </el-button>
      </template>
    </DataTable>

    <!-- 采购详情抽屉 -->
    <PurchaseOrderDetailView
      v-model:visible="isDetailDrawerOpen"
      :order-id="selectedOrderId"
      @refresh="fetchOrders"
      @close="isDetailDrawerOpen = false"
    />

    <!-- 外观验收与接收弹窗 -->
    <ReceiptConfirmView
      v-model:visible="isReceiptModalOpen"
      :order="selectedOrderForReceipt"
      :submitting="isReceiving"
      @confirm="handleConfirmReceipt"
      @close="isReceiptModalOpen = false"
    />

    <!-- 新建采购单弹窗 -->
    <el-dialog
      v-model="isCreateModalOpen"
      title="新建采购订单"
      width="620px"
      destroy-on-close
    >
      <el-form label-width="120px" @submit.prevent="submitCreateOrder">
        <el-alert
          v-if="optionsErrorMessage"
          type="warning"
          :title="optionsErrorMessage"
          show-icon
          style="margin-bottom: 14px"
        >
          <template #default>
            <el-button type="primary" link size="small" @click="loadCreateOptions">重试拉取</el-button>
          </template>
        </el-alert>

        <el-form-item label="主数据搜索">
          <el-input
            v-model="createOptionKeyword"
            placeholder="输入供应商、仓库或物料编码/名称后回车搜索"
            clearable
            @keyup.enter="loadCreateOptions"
          >
            <template #append>
              <el-button :icon="Search" @click="loadCreateOptions" />
            </template>
          </el-input>
        </el-form-item>

        <el-form-item label="供应商" required>
          <el-select v-model="createForm.supplierId" placeholder="请选择供应商" filterable style="width: 100%">
            <el-option
              v-for="supplier in suppliers"
              :key="supplier.id"
              :label="`${supplier.supplierCode} - ${supplier.supplierName}`"
              :value="supplier.id"
            />
          </el-select>
        </el-form-item>

        <el-form-item label="目标仓库" required>
          <el-select v-model="createForm.targetWarehouseId" placeholder="请选择目标仓库" style="width: 100%">
            <el-option
              v-for="warehouse in warehouses"
              :key="warehouse.id"
              :label="`${warehouse.code} - ${warehouse.name}`"
              :value="warehouse.id"
            />
          </el-select>
        </el-form-item>

        <el-form-item label="计划到货日期" required>
          <el-date-picker
            v-model="createForm.expectedArrivalDate"
            type="date"
            placeholder="选择日期"
            value-format="YYYY-MM-DD"
            style="width: 100%"
          />
        </el-form-item>

        <el-form-item label="采购商品物料" required>
          <el-select v-model="createForm.productId" placeholder="请选择物料" filterable style="width: 100%">
            <el-option
              v-for="product in products"
              :key="product.id"
              :label="`${product.sku} - ${product.name}`"
              :value="product.id"
            />
          </el-select>
        </el-form-item>

        <el-form-item label="采购数量" required>
          <el-input v-model="createForm.orderedQty" placeholder="如: 80" />
        </el-form-item>

        <el-form-item label="关联来源工单">
          <el-select v-model="createForm.sourceWorkOrderId" placeholder="不关联来源工单 (可选，用于追溯)" clearable style="width: 100%">
            <el-option label="不关联来源工单" value="" />
            <el-option
              v-for="workOrder in workOrders"
              :key="workOrder.id"
              :label="workOrder.workOrderNo"
              :value="workOrder.id"
            />
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="isCreateModalOpen = false">取消</el-button>
        <el-button type="primary" :loading="isCreating" @click="submitCreateOrder">
          {{ isCreating ? '创建中...' : '确认生成采购单' }}
        </el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
/**
 * 采购入库控制台列表视图 (PurchaseOrderListView)
 * 职责：展示采购订单队列，支持生命周期状态筛选、新建采购单与快速验收入库
 */
import { ref, reactive, computed, onMounted } from "vue";
import { Plus, Search } from "@element-plus/icons-vue";
import { ElMessage, ElMessageBox } from "element-plus";
import { useCommand } from "@/composables/useCommand";
import CommandFeedback from "@/components/common/CommandFeedback.vue";
import { useRoute, useRouter } from "vue-router";
import PageHeader from "@/components/common/PageHeader.vue";
import FilterBar from "@/components/common/FilterBar.vue";
import DataTable, { type TableColumn } from "@/components/common/DataTable.vue";
import StatusBadge from "@/components/common/StatusBadge.vue";
import QuantityText from "@/components/common/QuantityText.vue";
import EmptyState from "@/components/common/EmptyState.vue";
import ErrorState from "@/components/common/ErrorState.vue";
import PurchaseOrderDetailView from "./PurchaseOrderDetailView.vue";
import ReceiptConfirmView from "./ReceiptConfirmView.vue";
import type { ViewState } from "@/types/common";
import type { PurchaseOrder } from "@/types/purchasing";
import type { Product, Supplier, Warehouse } from "@/types/inventory";
import { getProducts, getSuppliers, getWarehouses } from "@/api/masterData";
import { getWorkOrders } from "@/api/manufacturing";
import { usePermission } from "@/composables/usePermission";
import {
  getPurchaseOrders,
  createPurchaseOrder,
  confirmPurchaseReceiptWithServerId,
} from "@/api/purchasing";

const route = useRoute();
const router = useRouter();
const { hasPermission } = usePermission();

/** 是否处于仓库到货验收工作台模式 (/purchasing/receipts) */
const isReceiptMode = computed(() => {
  return route.path.includes("/purchasing/receipts") || route.name === "PurchaseReceiptConfirm";
});

const viewState = ref<ViewState>("loading");
const errorMessage = ref("");
const orderList = ref<PurchaseOrder[]>([]);
const totalCount = ref(0);
const products = ref<Product[]>([]);
const suppliers = ref<Supplier[]>([]);
const warehouses = ref<Warehouse[]>([]);
const workOrders = ref<Array<{ id: string | number; workOrderNo?: string; woNo?: string }>>([]);

const queryParams = reactive({
  page: 1,
  size: 10,
  keyword: "",
  status: "",
});

const statusFilters = [
  { label: "全部生命周期", value: "" },
  { label: "未提交 (Draft)", value: "Draft" },
  { label: "已提交 (Submitted)", value: "Submitted" },
  { label: "已审核 (Approved)", value: "Approved" },
  { label: "部分收货 (PartiallyReceived)", value: "PartiallyReceived" },
  { label: "已完成 (Completed)", value: "Completed" },
];

const columns: TableColumn[] = [
  { key: "poNo", label: "采购订单号", width: "160px" },
  { key: "supplier", label: "供应商", minWidth: "180px" },
  { key: "expectedArrivalDate", label: "计划到货日", width: "120px" },
  { key: "warehouseName", label: "目标仓库", width: "120px" },
  { key: "status", label: "当前状态", width: "130px", align: "center" },
  { key: "pendingSummary", label: "当前待收余量", width: "130px", align: "right" },
  { key: "actions", label: "操作", width: "140px", align: "center" },
];

const isDetailDrawerOpen = ref(false);
const selectedOrderId = ref<string | number | null>(null);

const isReceiptModalOpen = ref(false);
const selectedOrderForReceipt = ref<PurchaseOrder | null>(null);
const { execute, retry, isExecuting, canRetry, lastError } = useCommand();
const isReceiving = isExecuting;

const isCreateModalOpen = ref(false);
const isCreating = isExecuting;
const createForm = reactive({
  supplierId: "",
  targetWarehouseId: "",
  expectedArrivalDate: new Date().toISOString().slice(0, 10),
  productId: "",
  orderedQty: "",
  sourceWorkOrderId: "",
});
const createOptionKeyword = ref("");

function statusBadgeType(status: string): any {
  const map: Record<string, string> = {
    Draft: "default",
    Submitted: "primary",
    Approved: "info",
    PartiallyReceived: "warning",
    Completed: "success",
  };
  return map[status] || "default";
}

function statusText(status: string): string {
  const map: Record<string, string> = {
    Draft: "未提交",
    Submitted: "已提交",
    Approved: "已审核",
    PartiallyReceived: "部分收货",
    Completed: "已完成",
  };
  return map[status] || status;
}

async function fetchOrders() {
  viewState.value = "loading";
  errorMessage.value = "";
  try {
    const res = await getPurchaseOrders({
      page: queryParams.page,
      size: queryParams.size,
      keyword: queryParams.keyword,
      status: queryParams.status,
    });
    const supplierMap = new Map(suppliers.value.map((item) => [String(item.id), item]));
    const productMap = new Map(products.value.map((item) => [String(item.id), item]));
    const warehouseMap = new Map(warehouses.value.map((item) => [String(item.id), item]));
    orderList.value = (res.data.records || []).map((order) => {
      const supplier = supplierMap.get(String(order.supplierId));
      const lines = (order.lines || []).map((line) => {
        const product = productMap.get(String(line.productId));
        const warehouse = warehouseMap.get(String(line.targetWarehouseId));
        return {
          ...line,
          sku: line.sku || product?.sku || String(line.productId),
          productName: line.productName || product?.name || String(line.productId),
          spec: line.spec || product?.spec,
          targetWarehouseName: line.targetWarehouseName || warehouse?.name || String(line.targetWarehouseId),
        };
      });
      return {
        ...order,
        supplierCode: order.supplierCode || supplier?.supplierCode || String(order.supplierId),
        supplierName: order.supplierName || supplier?.supplierName || String(order.supplierId),
        warehouseName: order.warehouseName || lines[0]?.targetWarehouseName,
        lines,
      };
    });
    totalCount.value = res.data.total;
    viewState.value = orderList.value.length === 0 ? "empty" : "ready";
  } catch (err: any) {
    console.error("[PurchaseOrderListView] 查询失败:", err);
    errorMessage.value = err?.message || "网络请求异常";
    viewState.value = "error";
  }
}

function handlePageChange(page: number) {
  queryParams.page = page;
  fetchOrders();
}

function switchStatusFilter(st: string) {
  queryParams.status = st;
  queryParams.page = 1;
  fetchOrders();
}

function resetFilter() {
  queryParams.keyword = "";
  queryParams.status = "";
  queryParams.page = 1;
  fetchOrders();
}

function openOrderDetail(row: PurchaseOrder) {
  selectedOrderId.value = row.id;
  isDetailDrawerOpen.value = true;
}

function openReceiptConfirm(row: PurchaseOrder) {
  selectedOrderForReceipt.value = row;
  isReceiptModalOpen.value = true;
}

async function handleConfirmReceipt(payload: any) {
  try {
    const { receiptId: _ignoredClientId, ...requestPayload } = payload;
    const receiptResponse = await execute(async (key) => {
      // 修改：收货事实 ID由服务端按幂等键分配，客户端不能用订单号、订单行 ID或随机 UUID代替。
      const response = await confirmPurchaseReceiptWithServerId(requestPayload, key);
      isReceiptModalOpen.value = false;
      await fetchOrders();
      return response;
    }, { onConflict: fetchOrders });

    // 修改用途：后续质检上下文只能接收收货接口返回的独立 ID，不能沿用提交载荷或订单行 ID。
    const persistedReceipt = receiptResponse?.data;
    const returnedReceiptId = String(persistedReceipt?.id || "");
    const returnedReceiptLineId = String(persistedReceipt?.lines?.[0]?.id || "");
    if (!returnedReceiptId || !returnedReceiptLineId) {
      throw new Error("收货接口未返回 receiptId 或收货行 ID，已停止进入质检流程。");
    }

    // 成功提示并引导进入质检（使用 ElMessageBox 替代原生 confirm）
    const poNo = selectedOrderForReceipt.value?.poNo || "";
    // 修改用途：质检上下文的采购订单 ID 必须以服务端返回的收货事实为准，避免列表行缓存或旧数据把错误订单 ID 带入后续流程。
    const orderId = String(persistedReceipt?.purchaseOrderId || selectedOrderForReceipt.value?.id || "");
    try {
      await ElMessageBox.confirm(
        `采购到货验收成功！实收货物已送入 QualityHold 质量隔离位。\n\n订单号: ${poNo}\n收货凭证号: ${payload.receiptNo || returnedReceiptId}\n\n是否立即前往【采购到货质检】录入检验事实？`,
        "到货验收成功",
        {
          confirmButtonText: "前往质检",
          cancelButtonText: "留在列表",
          type: "success",
        }
      );
      router.push({
        path: "/purchasing/quality",
        query: {
          receiptId: returnedReceiptId,
          receiptLineId: returnedReceiptLineId,
          orderId,
          poNo,
          warehouseId: String(selectedOrderForReceipt.value?.lines?.find(
            (line) => String(line.id) === String(persistedReceipt.lines[0].purchaseOrderLineId),
          )?.targetWarehouseId || ""),
          productId: String(persistedReceipt.lines[0].productId || ""),
        },
      });
    } catch {
      // 用户留在当前列表
    }
  } catch (err: any) {
    ElMessage.error(err?.message || "收货失败");
  } finally { /* useCommand 在 finally 中恢复 isExecuting。 */ }
}

async function submitCreateOrder() {
  try {
    const product = products.value.find((item) => String(item.id) === String(createForm.productId));
    if (!product) {
      throw new Error("请选择真实物料");
    }
    if (!suppliers.value.some((item) => String(item.id) === String(createForm.supplierId))) {
      throw new Error("供应商选项已失效，请重新搜索并选择真实供应商");
    }
    if (!warehouses.value.some((item) => String(item.id) === String(createForm.targetWarehouseId))) {
      throw new Error("仓库选项已失效，请重新搜索并选择真实仓库");
    }
    await execute((key) => createPurchaseOrder({
      supplierId: createForm.supplierId,
      expectedArrivalDate: createForm.expectedArrivalDate,
      lines: [
        {
          productId: createForm.productId,
          orderedQty: createForm.orderedQty,
          uom: product.uom,
          targetWarehouseId: createForm.targetWarehouseId,
          sourceWorkOrderId: createForm.sourceWorkOrderId || undefined,
        },
      ],
    }, key), { onConflict: fetchOrders });
    isCreateModalOpen.value = false;
    ElMessage.success("采购订单创建成功！");
    await fetchOrders();
  } catch (err: any) {
    ElMessage.error(err?.message || "创建采购单失败");
  } finally { /* useCommand 在 finally 中恢复 isExecuting。 */ }
}

/**
 * 加载采购建单所需主数据和来源工单（修复 F10：采用 Promise.allSettled 避免可选工单失败拖垮必填选项）
 */
const isOptionsLoading = ref(false);
const optionsErrorMessage = ref("");

async function loadCreateOptions() {
  isOptionsLoading.value = true;
  optionsErrorMessage.value = "";
  try {
    const keyword = createOptionKeyword.value.trim() || undefined;
    const [supplierSettled, warehouseSettled, productSettled, workOrderSettled] = await Promise.allSettled([
      getSuppliers({ page: 1, size: 1000, keyword, status: "ACTIVE" }),
      getWarehouses({ page: 1, size: 1000, keyword, status: "ACTIVE" }),
      getProducts({ page: 1, size: 1000, keyword, status: "ACTIVE" }),
      getWorkOrders({ page: 1, size: 1000, workOrderNo: keyword }),
    ]);

    if (supplierSettled.status === "fulfilled") {
      suppliers.value = supplierSettled.value.data.records || [];
      if (createForm.supplierId && !suppliers.value.some((item) => String(item.id) === String(createForm.supplierId))) {
        createForm.supplierId = "";
      }
    } else {
      console.warn("[PurchaseOrderListView] 供应商选项加载失败:", supplierSettled.reason);
    }

    if (warehouseSettled.status === "fulfilled") {
      warehouses.value = warehouseSettled.value.data.records || [];
      if (createForm.targetWarehouseId && !warehouses.value.some((item) => String(item.id) === String(createForm.targetWarehouseId))) {
        createForm.targetWarehouseId = "";
      }
    } else {
      console.warn("[PurchaseOrderListView] 仓库选项加载失败:", warehouseSettled.reason);
    }

    if (productSettled.status === "fulfilled") {
      products.value = productSettled.value.data.records || [];
      if (createForm.productId && !products.value.some((item) => String(item.id) === String(createForm.productId))) {
        createForm.productId = "";
      }
    } else {
      console.warn("[PurchaseOrderListView] 物料选项加载失败:", productSettled.reason);
    }

    if (workOrderSettled.status === "fulfilled") {
      workOrders.value = (workOrderSettled.value.data.records || []).map((item: any) => ({
        id: item.id,
        workOrderNo: item.workOrderNo || item.woNo,
      }));
    } else {
      // 可选来源工单失败（如买方角色无 MES 查看权限）静默降级，不阻塞采购建单
      console.info("[PurchaseOrderListView] 可选来源工单不可用或无权限，已自动降级跳过");
      workOrders.value = [];
    }

    // 若必填核心数据全部为空，记录提示
    if (suppliers.value.length === 0 && warehouses.value.length === 0 && products.value.length === 0) {
      optionsErrorMessage.value = "基础数据（供应商/仓库/物料）加载受限，请确认主数据是否已录入";
    }
  } catch (error: any) {
    console.error("[PurchaseOrderListView] 加载建单选项未知异常", error);
    optionsErrorMessage.value = error?.message || "加载建单基础选项失败";
  } finally {
    isOptionsLoading.value = false;
  }
}

onMounted(async () => {
  // 若从 /purchasing/receipts 路由进入，默认筛选已审核可收货状态
  if (isReceiptMode.value && !queryParams.status) {
    queryParams.status = "Approved";
  }
  await loadCreateOptions();
  await fetchOrders();
  loadCreateOptions();
});
</script>

<style scoped>
.purchase-order-list-view {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.receipt-guide-banner {
  display: flex;
  align-items: center;
  gap: 12px;
  background: rgba(14, 165, 233, 0.12);
  border: 1px solid rgba(14, 165, 233, 0.3);
  border-radius: 8px;
  padding: 12px 16px;
  color: #bae6fd;
  font-size: 13px;
  line-height: 1.5;
}

.receipt-guide-banner .guide-icon {
  font-size: 20px;
  flex-shrink: 0;
}

.receipt-guide-banner strong {
  color: #38bdf8;
  margin-right: 4px;
}

.status-tabs-row {
  display: flex;
  gap: 8px;
  overflow-x: auto;
  border-bottom: 1px solid rgba(255, 255, 255, 0.08);
  padding-bottom: 8px;
}

.status-tab-btn {
  padding: 6px 14px;
  background: rgba(30, 41, 59, 0.4);
  border: 1px solid rgba(255, 255, 255, 0.08);
  border-radius: 6px;
  color: #94a3b8;
  font-size: 12px;
  cursor: pointer;
  transition: all 0.2s;
  white-space: nowrap;
}

.status-tab-btn:hover {
  background: rgba(51, 65, 85, 0.6);
  color: #f1f5f9;
}

.status-tab-btn.is-active {
  background: rgba(56, 189, 248, 0.12);
  border-color: rgba(56, 189, 248, 0.3);
  color: #38bdf8;
}

.mono-code {
  font-family: var(--font-mono, monospace);
  font-size: 12px;
  color: #38bdf8;
}

.supplier-cell {
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.supp-name {
  font-size: 13px;
  color: #f1f5f9;
}

.supp-code {
  font-size: 11px;
  color: #64748b;
  font-family: var(--font-mono, monospace);
}

.status-cell {
  display: flex;
  align-items: center;
  gap: 6px;
  justify-content: center;
}

.tag-manual {
  font-size: 10px;
  color: #f87171;
  background: rgba(239, 68, 68, 0.12);
  padding: 1px 4px;
  border-radius: 3px;
}

.table-actions {
  display: flex;
  align-items: center;
  gap: 8px;
  justify-content: center;
}

.btn-link {
  background: transparent;
  border: none;
  color: #38bdf8;
  font-size: 12px;
  cursor: pointer;
  padding: 2px 6px;
}

.act-receive {
  color: #fbbf24;
  font-weight: 600;
}

.btn-primary {
  padding: 8px 16px;
  background: #0284c7;
  color: #ffffff;
  border: 1px solid #0369a1;
  border-radius: 6px;
  font-size: 13px;
  font-weight: 500;
  cursor: pointer;
}

.btn-secondary {
  padding: 8px 16px;
  background: rgba(51, 65, 85, 0.6);
  color: #cbd5e1;
  border: 1px solid rgba(255, 255, 255, 0.1);
  border-radius: 6px;
  font-size: 13px;
  cursor: pointer;
}

.btn-create-sm {
  padding: 6px 14px;
  background: #0284c7;
  border: none;
  border-radius: 4px;
  color: #fff;
  font-size: 12px;
  cursor: pointer;
}

/* 模态弹窗 */
.modal-mask {
  position: fixed;
  inset: 0;
  background: rgba(0, 0, 0, 0.7);
  backdrop-filter: blur(4px);
  display: flex;
  align-items: center;
  justify-content: center;
  z-index: 1000;
  padding: 16px;
}

.modal-panel {
  background: #0f172a;
  border: 1px solid rgba(255, 255, 255, 0.15);
  border-radius: 10px;
  width: 100%;
  max-width: 520px;
  box-shadow: 0 20px 25px -5px rgba(0, 0, 0, 0.5);
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
  font-size: 16px;
  color: #f8fafc;
}

.btn-close {
  background: transparent;
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

.form-row {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 12px;
}

.form-item {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

label {
  font-size: 12px;
  color: #94a3b8;
}

.req {
  color: #f87171;
}

.options-search-row {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 8px 10px;
  border: 1px solid rgba(148, 163, 184, 0.18);
  border-radius: 6px;
}

.options-search-row label {
  flex: 0 0 auto;
  color: #94a3b8;
  font-size: 12px;
}

.options-search-row .form-input {
  flex: 1;
}

.form-input,
.form-select {
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
  padding-top: 10px;
}
</style>
