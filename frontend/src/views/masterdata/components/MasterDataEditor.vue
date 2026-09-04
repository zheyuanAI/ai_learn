<template>
  <div v-if="visible" class="editor-mask" @click.self="handleClose">
    <div class="editor-panel">
      <div class="editor-header">
        <h3 class="editor-title">{{ title }}</h3>
        <button type="button" class="btn-close" @click="handleClose">✕</button>
      </div>

      <div class="editor-body">
        <!-- 1. 物料表单 -->
        <form v-if="type === 'product'" class="form-grid" @submit.prevent="handleSubmit">
          <div class="form-item">
            <label>物料编码 (SKU) <span class="req">*</span></label>
            <input v-model="formData.sku" type="text" class="form-input" required :disabled="isEdit" placeholder="如: RM-SERVO-01" />
          </div>
          <div class="form-item">
            <label>物料名称 <span class="req">*</span></label>
            <input v-model="formData.name" type="text" class="form-input" required placeholder="如: 定子转子组件" />
          </div>
          <div class="form-item">
            <label>规格型号</label>
            <input v-model="formData.spec" type="text" class="form-input" placeholder="如: ST-80 / 精密铜组" />
          </div>
          <div class="form-item">
            <label>计量单位 (UOM) <span class="req">*</span></label>
            <input v-model="formData.uom" type="text" class="form-input" required placeholder="如: 件, 台, 套" />
          </div>
          <div class="form-item">
            <label>物料分类</label>
            <select v-model="formData.category" class="form-select">
              <option value="产成品">产成品</option>
              <option value="原材料">原材料</option>
              <option value="半成品">半成品</option>
              <option value="标准件">标准件</option>
              <option value="电子料">电子料</option>
              <option value="辅料包材">辅料包材</option>
            </select>
          </div>
          <div class="form-item">
            <label>参考单价 (元)</label>
            <input v-model="formData.unitPrice" type="text" class="form-input" placeholder="0.00" />
          </div>
          <div class="form-item">
            <label>最低库存预警值</label>
            <input v-model="formData.minStock" type="text" class="form-input" placeholder="0" />
          </div>
          <div class="form-item">
            <label>安全库存量</label>
            <input v-model="formData.safetyStock" type="text" class="form-input" placeholder="0" />
          </div>
          <div class="form-item full-width">
            <label class="checkbox-label">
              <input v-model="formData.batchMgmt" type="checkbox" />
              <span>启用批次管理 (Lot Management)</span>
            </label>
          </div>
          <div class="form-item full-width">
            <label>备注说明</label>
            <textarea v-model="formData.remark" class="form-textarea" rows="2" placeholder="填写物料补充说明..."></textarea>
          </div>
        </form>

        <!-- 2. 库位表单 -->
        <form v-else-if="type === 'location'" class="form-grid" @submit.prevent="handleSubmit">
          <div class="form-item">
            <label>库位编码 <span class="req">*</span></label>
            <input v-model="formData.code" type="text" class="form-input" required :disabled="isEdit" placeholder="如: ST-A-01" />
          </div>
          <div class="form-item">
            <label>库位名称 <span class="req">*</span></label>
            <input v-model="formData.name" type="text" class="form-input" required placeholder="如: 原料常规存储位A01" />
          </div>
          <div class="form-item">
            <label>所属仓库 <span class="req">*</span></label>
            <select v-model="formData.warehouseId" class="form-select" required>
              <option value="1">原料一仓</option>
              <option value="2">成品一仓</option>
              <option value="3">虚拟仓</option>
            </select>
          </div>
          <div class="form-item">
            <label>标准库位类型 <span class="req">*</span></label>
            <select v-model="formData.type" class="form-select" required>
              <option value="ReceivingStaging">ReceivingStaging (采购收货暂存位)</option>
              <option value="Storage">Storage (常规存储位)</option>
              <option value="Picking">Picking (拣货备料位)</option>
              <option value="ShippingStaging">ShippingStaging (发货暂存位)</option>
              <option value="QualityHold">QualityHold (质量隔离位)</option>
              <option value="Adjustment">Adjustment (差异调整位)</option>
            </select>
          </div>
          <div class="form-item">
            <label>库位容量</label>
            <input v-model="formData.capacity" type="text" class="form-input" placeholder="1000" />
          </div>
          <div class="form-item">
            <label>状态</label>
            <select v-model="formData.status" class="form-select">
              <option value="AVAILABLE">AVAILABLE (空闲可用)</option>
              <option value="OCCUPIED">OCCUPIED (已占用)</option>
              <option value="LOCKED">LOCKED (已锁定)</option>
            </select>
          </div>
          <div class="form-item full-width">
            <label>规则描述与用途</label>
            <textarea v-model="formData.description" class="form-textarea" rows="2" placeholder="如: 到货实际接收但未放行或不合格暂存位..."></textarea>
          </div>
        </form>

        <!-- 3. 客商通用表单 (客户/供应商) -->
        <form v-else class="form-grid" @submit.prevent="handleSubmit">
          <div class="form-item">
            <label>{{ type === 'customer' ? '客户编码' : '供应商编码' }} <span class="req">*</span></label>
            <input
              v-model="formData[type === 'customer' ? 'customerCode' : 'supplierCode']"
              type="text"
              class="form-input"
              required
              :disabled="isEdit"
              :placeholder="type === 'customer' ? 'CUS-NC-021' : 'SUP-HD-001'"
            />
          </div>
          <div class="form-item">
            <label>{{ type === 'customer' ? '客户名称' : '供应商名称' }} <span class="req">*</span></label>
            <input
              v-model="formData[type === 'customer' ? 'customerName' : 'supplierName']"
              type="text"
              class="form-input"
              required
              placeholder="输入完整公司名称"
            />
          </div>
          <div class="form-item">
            <label>联系人</label>
            <input v-model="formData.contactPerson" type="text" class="form-input" placeholder="联系人姓名" />
          </div>
          <div class="form-item">
            <label>联系电话</label>
            <input v-model="formData.contactPhone" type="text" class="form-input" placeholder="手机或固话" />
          </div>
          <div class="form-item full-width">
            <label>地址信息</label>
            <input
              v-model="formData[type === 'customer' ? 'shippingAddress' : 'address']"
              type="text"
              class="form-input"
              placeholder="填写详细送货或注册地址"
            />
          </div>
        </form>
      </div>

      <div class="editor-footer">
        <button type="button" class="btn btn-secondary" @click="handleClose">取消</button>
        <button type="button" class="btn btn-primary" :disabled="saving" @click="handleSubmit">
          <span v-if="saving">⏳ 保存中...</span>
          <span v-else>确认保存</span>
        </button>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
/**
 * 主数据维护弹窗编辑器 (MasterDataEditor)
 * 职责：支持物料、库位、往来客户、供应商的新建与编辑表单
 * 流程：通过 deep clone 传入的 initialData 进行编辑，提交时通过 save 事件派发
 */
import { ref, watch, computed } from "vue";

const props = withDefaults(
  defineProps<{
    visible: boolean;
    type: "product" | "location" | "customer" | "supplier";
    initialData?: Record<string, any> | null;
    saving?: boolean;
  }>(),
  {
    visible: false,
    initialData: null,
    saving: false,
  }
);

const emit = defineEmits<{
  (e: "update:visible", val: boolean): void;
  (e: "save", data: any): void;
  (e: "cancel"): void;
}>();

const isEdit = computed(() => !!props.initialData && !!props.initialData.id);

const title = computed(() => {
  const prefix = isEdit.value ? "编辑" : "新建";
  const typeMap: Record<string, string> = {
    product: "商品物料",
    location: "仓库库位",
    customer: "客户档案",
    supplier: "供应商档案",
  };
  return `${prefix}${typeMap[props.type] || "资料"}`;
});

const formData = ref<Record<string, any>>({});

watch(
  () => props.visible,
  (val) => {
    if (val) {
      if (props.initialData) {
        formData.value = JSON.parse(JSON.stringify(props.initialData));
      } else {
        resetForm();
      }
    }
  }
);

function resetForm() {
  if (props.type === "product") {
    formData.value = {
      sku: "",
      name: "",
      spec: "",
      uom: "件",
      category: "原材料",
      batchMgmt: false,
      status: "ENABLE",
      unitPrice: "0.00",
      minStock: "0",
      maxStock: "1000",
      safetyStock: "0",
      remark: "",
    };
  } else if (props.type === "location") {
    formData.value = {
      code: "",
      name: "",
      warehouseId: "1",
      type: "Storage",
      capacity: "1000",
      status: "AVAILABLE",
      description: "",
    };
  } else if (props.type === "customer") {
    formData.value = {
      customerCode: "",
      customerName: "",
      contactPerson: "",
      contactPhone: "",
      shippingAddress: "",
      status: "ACTIVE",
    };
  } else {
    formData.value = {
      supplierCode: "",
      supplierName: "",
      contactPerson: "",
      contactPhone: "",
      address: "",
      status: "ACTIVE",
    };
  }
}

function handleClose() {
  emit("update:visible", false);
  emit("cancel");
}

function handleSubmit() {
  emit("save", { ...formData.value });
}
</script>

<style scoped>
.editor-mask {
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

.editor-panel {
  background: #0f172a;
  border: 1px solid rgba(255, 255, 255, 0.15);
  border-radius: 10px;
  width: 100%;
  max-width: 600px;
  max-height: 90vh;
  display: flex;
  flex-direction: column;
  box-shadow: 0 20px 25px -5px rgba(0, 0, 0, 0.5);
  animation: modal-enter 0.2s ease-out;
}

.editor-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 16px 20px;
  border-bottom: 1px solid rgba(255, 255, 255, 0.08);
}

.editor-title {
  margin: 0;
  font-size: 16px;
  font-weight: 600;
  color: #f8fafc;
}

.btn-close {
  background: transparent;
  border: none;
  color: #94a3b8;
  font-size: 16px;
  cursor: pointer;
  padding: 4px;
}

.btn-close:hover {
  color: #f8fafc;
}

.editor-body {
  padding: 20px;
  overflow-y: auto;
}

.form-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 14px;
}

.form-item {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.form-item.full-width {
  grid-column: 1 / -1;
}

label {
  font-size: 12px;
  color: #94a3b8;
}

.req {
  color: #f87171;
}

.form-input,
.form-select,
.form-textarea {
  background: rgba(30, 41, 59, 0.8);
  border: 1px solid rgba(255, 255, 255, 0.12);
  color: #f8fafc;
  padding: 7px 10px;
  border-radius: 6px;
  font-size: 13px;
  outline: none;
  transition: border-color 0.2s;
}

.form-input:focus,
.form-select:focus,
.form-textarea:focus {
  border-color: #38bdf8;
}

.checkbox-label {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 13px;
  color: #e2e8f0;
  cursor: pointer;
}

.editor-footer {
  display: flex;
  align-items: center;
  justify-content: flex-end;
  gap: 12px;
  padding: 14px 20px;
  background: rgba(0, 0, 0, 0.2);
  border-top: 1px solid rgba(255, 255, 255, 0.08);
}

.btn {
  padding: 8px 16px;
  border-radius: 6px;
  font-size: 13px;
  font-weight: 500;
  cursor: pointer;
  transition: all 0.2s;
  border: 1px solid transparent;
}

.btn-secondary {
  background: rgba(51, 65, 85, 0.6);
  color: #cbd5e1;
  border-color: rgba(255, 255, 255, 0.1);
}

.btn-primary {
  background: #0284c7;
  color: #ffffff;
}

@keyframes modal-enter {
  from {
    opacity: 0;
    transform: scale(0.95);
  }
  to {
    opacity: 1;
    transform: scale(1);
  }
}
</style>
