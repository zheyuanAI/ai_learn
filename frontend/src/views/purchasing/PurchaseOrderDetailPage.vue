<template>
  <div class="detail-page-host" data-testid="purchase-order-detail-page">
    <!-- 路由详情宿主固定传入 visible=true，由详情组件主动按 orderId 加载真实数据。 -->
    <PurchaseOrderDetailView
      :visible="true"
      :order-id="orderId"
      @close="handleClose"
    />
  </div>
</template>

<script setup lang="ts">
import { computed } from "vue";
import { useRoute, useRouter } from "vue-router";
import PurchaseOrderDetailView from "./PurchaseOrderDetailView.vue";

const route = useRoute();
const router = useRouter();
const orderId = computed(() => String(route.params.id || ""));

/**
 * 用途：关闭直达详情页并返回采购订单业务列表。
 * 入参：无，保留当前路由 query 作为列表筛选上下文。
 * 出参：无；通过路由导航回采购订单列表。
 * 流程：详情组件只负责发出关闭事件，宿主负责结束详情路由，避免列表抽屉和直达页互相耦合。
 */
function handleClose() {
  router.push({ name: "PurchaseOrderList", query: route.query });
}
</script>

<style scoped>
.detail-page-host {
  min-height: calc(100vh - 72px);
}
</style>
