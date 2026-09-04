<template>
  <div class="master-data-view-container">
    <!-- 统一页面头部 -->
    <PageHeader
      title="基础资料与主数据控制台"
      tag="CORE / MASTER DATA"
      description="维护物料主数据、仓库与 6 类标准库位（ReceivingStaging, Storage, Picking, ShippingStaging, QualityHold, Adjustment）、往来客户与供应商档案。"
    >
      <template #actions>
        <button type="button" class="btn-create" @click="openCreateModal">
          <span>＋ 新建{{ currentTabLabel }}</span>
        </button>
      </template>
    </PageHeader>

    <!-- 顶部分类标签切换 -->
    <div class="tab-nav-bar">
      <button
        v-for="tab in tabOptions"
        :key="tab.key"
        type="button"
        class="tab-btn"
        :class="{ 'is-active': activeTab === tab.key }"
        @click="switchTab(tab.key)"
      >
        <span>{{ tab.label }}</span>
        <span class="badge-count">{{ tabCounts[tab.key] || 0 }}</span>
      </button>
    </div>

    <!-- 筛选搜索栏 -->
    <FilterBar
      v-model="searchKeyword"
      :placeholder="`搜索${currentTabLabel}名称、编码或关键字...`"
      @search="fetchCurrentTabData"
      @reset="resetSearch"
    >
      <!-- 物料分类筛选 -->
      <template v-if="activeTab === 'products'">
        <select v-model="selectedCategory" class="filter-select-input" @change="fetchCurrentTabData">
          <option value="">全部分类</option>
          <option value="产成品">产成品</option>
          <option value="原材料">原材料</option>
          <option value="半成品">半成品</option>
          <option value="标准件">标准件</option>
          <option value="电子料">电子料</option>
          <option value="辅料包材">辅料包材</option>
        </select>
      </template>

      <!-- 库位类型筛选 -->
      <template v-else-if="activeTab === 'locations'">
        <select v-model="selectedLocationType" class="filter-select-input" @change="fetchCurrentTabData">
          <option value="">全部库位类型</option>
          <option value="ReceivingStaging">ReceivingStaging (收货暂存)</option>
          <option value="Storage">Storage (常规存储)</option>
          <option value="Picking">Picking (拣货备料)</option>
          <option value="ShippingStaging">ShippingStaging (发货暂存)</option>
          <option value="QualityHold">QualityHold (质量隔离)</option>
          <option value="Adjustment">Adjustment (差异调整)</option>
        </select>
      </template>
    </FilterBar>

    <!-- 错误状态 -->
    <ErrorState
      v-if="viewState === 'error'"
      title="主数据加载异常"
      :message="errorMessage"
      @retry="fetchCurrentTabData"
    />

    <!-- 空数据状态 -->
    <EmptyState
      v-else-if="viewState === 'empty'"
      :title="`暂无${currentTabLabel}数据`"
      :description="`当前筛选条件下未检索到任何${currentTabLabel}，您可以点击右上角新建。`"
    >
      <template #action>
        <button type="button" class="btn-create-sm" @click="openCreateModal">
          立即新建{{ currentTabLabel }}
        </button>
      </template>
    </EmptyState>

    <!-- 正常与加载数据表格 -->
    <MasterDataTable
      v-else
      :columns="currentColumns"
      :data="tableData"
      :loading="viewState === 'loading'"
      :total="totalCount"
      :page="currentPage"
      :size="pageSize"
      @page-change="handlePageChange"
      @edit="openEditModal"
    />

    <!-- 新建/编辑弹窗 -->
    <MasterDataEditor
      v-model:visible="isEditorVisible"
      :type="currentEditorType"
      :initial-data="editingItem"
      :saving="isSaving"
      @save="handleSave"
      @cancel="closeEditor"
    />
  </div>
</template>

<script setup lang="ts">
/**
 * 主数据管理主视图 (MasterDataView)
 * 职责：聚合商品物料、库位、客户、供应商4类基础主数据，支持完整四态（loading, ready, empty, error）
 * 流程：通过 activeTab 切换不同主数据模型，调用 masterData.ts API 并自动回退
 */
import { ref, computed, onMounted } from "vue";
import PageHeader from "@/components/common/PageHeader.vue";
import FilterBar from "@/components/common/FilterBar.vue";
import EmptyState from "@/components/common/EmptyState.vue";
import ErrorState from "@/components/common/ErrorState.vue";
import MasterDataTable from "./components/MasterDataTable.vue";
import MasterDataEditor from "./components/MasterDataEditor.vue";
import type { TableColumn } from "@/components/common/DataTable.vue";
import type { ViewState } from "@/types/common";
import {
  getProducts,
  getLocations,
  getCustomers,
  getSuppliers,
  createProduct,
  updateProduct,
  createLocation,
} from "@/api/masterData";

type TabKey = "products" | "locations" | "customers" | "suppliers";

const activeTab = ref<TabKey>("products");
const viewState = ref<ViewState>("loading");
const errorMessage = ref("");
const searchKeyword = ref("");
const selectedCategory = ref("");
const selectedLocationType = ref("");
const currentPage = ref(1);
const pageSize = ref(10);
const totalCount = ref(0);
const tableData = ref<any[]>([]);

const tabCounts = ref<Record<string, number>>({
  products: 6,
  locations: 8,
  customers: 3,
  suppliers: 3,
});

const tabOptions = [
  { key: "products" as TabKey, label: "商品物料主数据" },
  { key: "locations" as TabKey, label: "仓库与6类标准库位" },
  { key: "customers" as TabKey, label: "往来客户档案" },
  { key: "suppliers" as TabKey, label: "往来供应商档案" },
];

const currentTabLabel = computed(() => {
  const t = tabOptions.find((o) => o.key === activeTab.value);
  return t ? t.label : "主数据";
});

const currentEditorType = computed(() => {
  if (activeTab.value === "products") return "product";
  if (activeTab.value === "locations") return "location";
  if (activeTab.value === "customers") return "customer";
  return "supplier";
});

// 各标签页表头列配置
const productColumns: TableColumn[] = [
  { key: "sku", label: "物料编码", width: "130px" },
  { key: "name", label: "物料名称", minWidth: "140px" },
  { key: "spec", label: "规格型号", width: "130px" },
  { key: "uom", label: "单位", width: "70px", align: "center" },
  { key: "category", label: "分类", width: "100px" },
  { key: "batchMgmt", label: "批次管理", width: "100px", align: "center" },
  { key: "unitPrice", label: "参考单价", width: "100px", align: "right" },
  { key: "minStock", label: "最低预警", width: "90px", align: "right" },
  { key: "status", label: "状态", width: "80px", align: "center" },
  { key: "actions", label: "操作", width: "80px", align: "center" },
];

const locationColumns: TableColumn[] = [
  { key: "code", label: "库位编码", width: "110px" },
  { key: "name", label: "库位名称", minWidth: "150px" },
  { key: "warehouseName", label: "所属仓库", width: "120px" },
  { key: "type", label: "标准库位类型", width: "140px", align: "center" },
  { key: "capacity", label: "容量", width: "90px", align: "right" },
  { key: "status", label: "当前状态", width: "100px", align: "center" },
  { key: "description", label: "用途说明", minWidth: "200px" },
  { key: "actions", label: "操作", width: "80px", align: "center" },
];

const customerColumns: TableColumn[] = [
  { key: "customerCode", label: "客户编码", width: "130px" },
  { key: "customerName", label: "客户名称", minWidth: "180px" },
  { key: "contactPerson", label: "联系人", width: "100px" },
  { key: "contactPhone", label: "联系电话", width: "130px" },
  { key: "shippingAddress", label: "送货地址", minWidth: "220px" },
  { key: "status", label: "状态", width: "80px", align: "center" },
  { key: "actions", label: "操作", width: "80px", align: "center" },
];

const supplierColumns: TableColumn[] = [
  { key: "supplierCode", label: "供应商编码", width: "130px" },
  { key: "supplierName", label: "供应商全称", minWidth: "180px" },
  { key: "contactPerson", label: "联系人", width: "100px" },
  { key: "contactPhone", label: "联系电话", width: "130px" },
  { key: "address", label: "经营地址", minWidth: "220px" },
  { key: "status", label: "状态", width: "80px", align: "center" },
  { key: "actions", label: "操作", width: "80px", align: "center" },
];

const currentColumns = computed(() => {
  if (activeTab.value === "products") return productColumns;
  if (activeTab.value === "locations") return locationColumns;
  if (activeTab.value === "customers") return customerColumns;
  return supplierColumns;
});

// 编辑器弹窗状态
const isEditorVisible = ref(false);
const editingItem = ref<any>(null);
const isSaving = ref(false);

/**
 * 切换顶层标签页
 * @param key 标签页键名
 */
function switchTab(key: TabKey) {
  activeTab.value = key;
  searchKeyword.value = "";
  selectedCategory.value = "";
  selectedLocationType.value = "";
  currentPage.value = 1;
  fetchCurrentTabData();
}

/**
 * 抓取当前选中标签页的数据
 */
async function fetchCurrentTabData() {
  viewState.value = "loading";
  errorMessage.value = "";
  try {
    if (activeTab.value === "products") {
      const res = await getProducts({
        page: currentPage.value,
        size: pageSize.value,
        keyword: searchKeyword.value,
        category: selectedCategory.value,
      });
      tableData.value = res.data.records;
      totalCount.value = res.data.total;
      tabCounts.value.products = res.data.total;
    } else if (activeTab.value === "locations") {
      const res = await getLocations({
        page: currentPage.value,
        size: pageSize.value,
        keyword: searchKeyword.value,
        type: (selectedLocationType.value as any) || undefined,
      });
      tableData.value = res.data.records;
      totalCount.value = res.data.total;
      tabCounts.value.locations = res.data.total;
    } else if (activeTab.value === "customers") {
      const res = await getCustomers({ keyword: searchKeyword.value });
      const raw = res.data as any;
      const list: any[] = Array.isArray(raw) ? raw : (raw?.records || []);
      tableData.value = list;
      totalCount.value = raw?.total ?? list.length;
      tabCounts.value.customers = totalCount.value;
    } else {
      const res = await getSuppliers({ keyword: searchKeyword.value });
      const raw = res.data as any;
      const list: any[] = Array.isArray(raw) ? raw : (raw?.records || []);
      tableData.value = list;
      totalCount.value = raw?.total ?? list.length;
      tabCounts.value.suppliers = totalCount.value;
    }

    viewState.value = tableData.value.length === 0 ? "empty" : "ready";
  } catch (err: any) {
    console.error("[MasterDataView] 获取数据失败:", err);
    errorMessage.value = err?.message || "服务器响应超时，请重试";
    viewState.value = "error";
  }
}

function handlePageChange(page: number) {
  currentPage.value = page;
  fetchCurrentTabData();
}

function resetSearch() {
  searchKeyword.value = "";
  selectedCategory.value = "";
  selectedLocationType.value = "";
  currentPage.value = 1;
  fetchCurrentTabData();
}

function openCreateModal() {
  editingItem.value = null;
  isEditorVisible.value = true;
}

function openEditModal(row: any) {
  editingItem.value = row;
  isEditorVisible.value = true;
}

function closeEditor() {
  isEditorVisible.value = false;
  editingItem.value = null;
}

async function handleSave(data: any) {
  isSaving.value = true;
  try {
    if (activeTab.value === "products") {
      if (data.id) {
        await updateProduct(data.id, data);
      } else {
        await createProduct(data);
      }
    } else if (activeTab.value === "locations") {
      await createLocation(data);
    }
    closeEditor();
    await fetchCurrentTabData();
  } catch (err: any) {
    alert(err?.message || "保存失败");
  } finally {
    isSaving.value = false;
  }
}

onMounted(() => {
  fetchCurrentTabData();
});
</script>

<style scoped>
.master-data-view-container {
  display: flex;
  flex-direction: column;
  gap: 14px;
}

.tab-nav-bar {
  display: flex;
  gap: 8px;
  border-bottom: 1px solid rgba(255, 255, 255, 0.08);
  padding-bottom: 10px;
  overflow-x: auto;
}

.tab-btn {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 8px 16px;
  background: rgba(30, 41, 59, 0.4);
  border: 1px solid rgba(255, 255, 255, 0.06);
  border-radius: 6px;
  color: #94a3b8;
  font-size: 13px;
  font-weight: 500;
  cursor: pointer;
  transition: all 0.2s;
  white-space: nowrap;
}

.tab-btn:hover {
  background: rgba(51, 65, 85, 0.6);
  color: #f1f5f9;
}

.tab-btn.is-active {
  background: rgba(56, 189, 248, 0.12);
  border-color: rgba(56, 189, 248, 0.3);
  color: #38bdf8;
}

.badge-count {
  font-size: 11px;
  font-family: var(--font-mono, monospace);
  background: rgba(0, 0, 0, 0.25);
  padding: 1px 6px;
  border-radius: 10px;
}

.filter-select-input {
  background: rgba(30, 41, 59, 0.8);
  border: 1px solid rgba(255, 255, 255, 0.12);
  color: #f8fafc;
  padding: 7px 12px;
  border-radius: 6px;
  font-size: 13px;
  outline: none;
}

.btn-create {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 8px 16px;
  background: #0284c7;
  border: 1px solid #0369a1;
  border-radius: 6px;
  color: #ffffff;
  font-size: 13px;
  font-weight: 500;
  cursor: pointer;
  transition: background 0.2s;
}

.btn-create:hover {
  background: #0369a1;
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
</style>
