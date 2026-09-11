<template>
  <section class="detail-page-host" data-testid="transfer-detail-page">
    <div v-if="viewState === 'loading'" class="detail-page-state">
      <span>⏳ 正在加载调拨单详情...</span>
    </div>

    <ErrorState
      v-else-if="viewState === 'error'"
      title="获取调拨单失败"
      :message="errorMessage"
      @retry="loadTransfer"
    >
      <template #actions>
        <el-button @click="handleClose">返回调拨列表</el-button>
      </template>
    </ErrorState>

    <TransferDetailView
      v-else-if="transfer"
      :visible="true"
      :transfer="transfer"
      :confirming="confirming"
      @confirm="handleConfirm"
      @close="handleClose"
    />
  </section>
</template>

<script setup lang="ts">
import { computed, ref, watch } from "vue";
import { useRoute, useRouter } from "vue-router";
import { ElMessage } from "element-plus";
import ErrorState from "@/components/common/ErrorState.vue";
import { confirmTransfer, getTransferById } from "@/api/inventory";
import { ApiError } from "@/utils/request";
import type { TransferOrder } from "@/types/inventory";
import type { ViewState } from "@/types/common";
import TransferDetailView from "./TransferDetailView.vue";

const route = useRoute();
const router = useRouter();
const transferId = computed(() => String(route.params.id || ""));

const viewState = ref<ViewState>("loading");
const errorMessage = ref("");
const transfer = ref<TransferOrder | null>(null);
const confirming = ref(false);

/**
 * 用途：按路由中的调拨单 ID 主动加载详情。
 * 入参：无，读取当前 route.params.id。
 * 出参：更新页面四态和真实调拨单数据。
 * 流程：校验 ID 后请求详情接口；成功进入 ready，接口异常进入 error 并保留重试入口。
 */
/**
 * 用途：把调拨详情查询错误转换为可区分的页面文案。
 * 入参：详情 API 抛出的错误对象；出参：面向用户的错误说明。
 * 流程：优先按 HTTP 403/404 显示权限或资源错误，其余错误沿用后端消息。
 */
function detailLoadErrorMessage(error: unknown): string {
  if (error instanceof ApiError && error.httpStatus === 404) return "调拨单资源不存在或已被删除（404）。";
  if (error instanceof ApiError && error.httpStatus === 403) return "您没有查看该调拨单的权限（403）。";
  return error instanceof Error ? error.message : "网络请求异常，请稍后重试。";
}

async function loadTransfer() {
  if (!transferId.value) {
    transfer.value = null;
    errorMessage.value = "调拨单编号为空，无法加载详情。";
    viewState.value = "error";
    return;
  }

  viewState.value = "loading";
  errorMessage.value = "";
  try {
    const response = await getTransferById(transferId.value);
    transfer.value = response.data;
    viewState.value = "ready";
  } catch (error: any) {
    console.error("[TransferDetailPage] 获取调拨单详情失败:", error);
    transfer.value = null;
    errorMessage.value = detailLoadErrorMessage(error);
    viewState.value = "error";
  }
}

// 修改：监听详情 ID 并立即加载，确保直接打开或刷新详情地址时不会依赖列表 props。
watch(transferId, () => void loadTransfer(), { immediate: true });

/**
 * 用途：关闭直达详情页并返回调拨列表。
 * 入参：无，保留当前路由 query 作为列表筛选上下文。
 * 出参：无；通过路由导航回调拨列表。
 * 流程：详情组件发出 close 后由路由宿主统一结束详情页面。
 */
function handleClose() {
  router.push({ name: "InventoryTransferList", query: route.query });
}

/**
 * 用途：提交调拨确认动作并用后端返回值刷新当前详情。
 * 入参：调拨单 ID。
 * 出参：无；成功更新详情状态，失败保留当前数据并提示用户。
 * 流程：锁定按钮避免重复提交，请求完成后释放锁；本次动作仍由详情组件的权限状态控制。
 */
async function handleConfirm(id: string | number) {
  confirming.value = true;
  try {
    const response = await confirmTransfer(id);
    transfer.value = response.data;
    ElMessage.success("调拨确认成功！");
  } catch (error: any) {
    console.error("[TransferDetailPage] 确认调拨失败:", error);
    ElMessage.error(error?.message || "确认调拨失败，请稍后重试。");
  } finally {
    confirming.value = false;
  }
}
</script>

<style scoped>
.detail-page-host {
  min-height: calc(100vh - 72px);
  padding: 20px;
}

.detail-page-state {
  min-height: 220px;
  display: grid;
  place-items: center;
  color: #a8bdcc;
  border: 1px dashed rgba(124, 162, 194, 0.24);
  border-radius: 12px;
  background: rgba(15, 28, 40, 0.6);
}

.btn {
  padding: 7px 13px;
  border-radius: 7px;
  font: inherit;
  cursor: pointer;
}

.btn-secondary {
  border: 1px solid rgba(124, 162, 194, 0.35);
  background: rgba(124, 162, 194, 0.1);
  color: #d2e1ed;
}
</style>
