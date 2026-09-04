import { createRouter, createWebHistory, type RouteRecordRaw } from "vue-router";
import AppLayout from "../components/Layout/AppLayout.vue";
import LoginView from "../views/LoginView.vue";
import PrototypeHome from "../views/PrototypeHome.vue";
import DomainView from "../views/DomainView.vue";
import { useAuthStore } from "../stores/auth";

/**
 * 路由配置表
 * 顶级路由分为：无需鉴权的独立登录页 (/login) 与承载在 AppLayout 统一布局下的业务工作区路由
 */
const routes: RouteRecordRaw[] = [
  {
    path: "/login",
    name: "Login",
    component: LoginView,
    meta: {
      requiresAuth: false,
      title: "系统登录与认证上下文",
    },
  },
  {
    path: "/",
    component: AppLayout,
    meta: { requiresAuth: true },
    children: [
      {
        path: "",
        name: "Overview",
        component: PrototypeHome,
        meta: {
          requiresAuth: true,
          title: "一期总览",
        },
      },
      // 占位域首页（保持向下兼容）
      {
        path: "erp-wms",
        name: "ErpWms",
        component: DomainView,
        props: {
          title: "供需与仓储",
          summary: "销售需求、生产工单与采购来源人工关联；收货进入暂存位，上架与拣货只移位，销售预留后由发货扣减实物库存。",
          specPath: "docs/specs/10-erp-wms",
          prototypePath: "docs/prototype/pages/purchase-inbound.html / sales-outbound.html",
        },
        meta: { requiresAuth: true, title: "供需与仓储" },
      },
      {
        path: "mes",
        name: "Mes",
        component: DomainView,
        props: {
          title: "制造执行",
          summary: "生产工单关联来源销售行，以领退料、派工、OperationExecution、报工、质检和成品入库记录执行事实。",
          specPath: "docs/specs/20-mes",
          prototypePath: "docs/prototype/pages/work-order.html",
        },
        meta: { requiresAuth: true, title: "制造执行" },
      },
      {
        path: "iot",
        name: "Iot",
        component: DomainView,
        props: {
          title: "IoT 设备事实",
          summary: "一期 MQTT 消息按 message_id/sequence 去重，遥测、设备状态与告警分别保存，并补充工序执行上下文。",
          specPath: "docs/specs/30-iot-digital-twin",
          prototypePath: "docs/prototype/pages/device-alarm.html",
        },
        meta: { requiresAuth: true, title: "IoT 设备事实" },
      },
      {
        path: "gis",
        name: "Gis",
        component: DomainView,
        props: {
          title: "二维地图与看板",
          summary: "只读展示库存、订单、生产、质量、设备与告警事实，不建立第二套事实来源。",
          specPath: "docs/specs/40-gis-dashboard",
          prototypePath: "docs/prototype/pages/site-map.html / dashboard.html",
        },
        meta: { requiresAuth: true, title: "二维地图与看板" },
      },
      {
        path: "ai",
        name: "Ai",
        component: DomainView,
        props: {
          title: "AI 只读助手",
          summary: "通过受权限约束的只读工具查询库存、订单、工单、告警与追溯信息，并展示来源与调用审计。",
          specPath: "docs/specs/50-ai-assistant",
          prototypePath: "docs/prototype/pages/ai-assistant.html / tool-audit.html",
        },
        meta: { requiresAuth: true, title: "AI 只读助手" },
      },

      // ====== 阶段 2：主数据与库存 (ERP/WMS) ======
      {
        path: "master-data",
        name: "MasterData",
        component: () => import("../views/masterdata/MasterDataView.vue"),
        meta: { requiresAuth: true, title: "主数据中心" },
      },
      {
        path: "inventory/balances",
        name: "InventoryBalance",
        component: () => import("../views/inventory/InventoryBalanceView.vue"),
        meta: { requiresAuth: true, title: "实时库存余额" },
      },
      {
        path: "inventory/transactions",
        name: "InventoryTransaction",
        component: () => import("../views/inventory/InventoryTransactionView.vue"),
        meta: { requiresAuth: true, title: "库存审计流水" },
      },
      {
        path: "inventory/reservations",
        name: "InventoryReservation",
        component: () => import("../views/inventory/ReservationView.vue"),
        meta: { requiresAuth: true, title: "库存预留明细" },
      },
      {
        path: "inventory/transfers",
        name: "InventoryTransferList",
        component: () => import("../views/inventory/TransferListView.vue"),
        meta: { requiresAuth: true, title: "库位调拨管理" },
      },
      {
        path: "inventory/transfers/:id",
        name: "InventoryTransferDetail",
        component: () => import("../views/inventory/TransferDetailView.vue"),
        meta: { requiresAuth: true, title: "调拨单执行详情" },
      },
      {
        path: "inventory/stocktakes",
        name: "InventoryStocktakeList",
        component: () => import("../views/inventory/StocktakeListView.vue"),
        meta: { requiresAuth: true, title: "差异盘点管理" },
      },
      {
        path: "inventory/stocktakes/:id",
        name: "InventoryStocktakeDetail",
        component: () => import("../views/inventory/StocktakeDetailView.vue"),
        meta: { requiresAuth: true, title: "盘点录入与调整" },
      },

      // ====== 阶段 3：采购进货与质检上架 ======
      {
        path: "purchasing/orders",
        name: "PurchaseOrderList",
        component: () => import("../views/purchasing/PurchaseOrderListView.vue"),
        meta: { requiresAuth: true, title: "采购订单管理" },
      },
      {
        path: "purchasing/orders/:id",
        name: "PurchaseOrderDetail",
        component: () => import("../views/purchasing/PurchaseOrderDetailView.vue"),
        meta: { requiresAuth: true, title: "采购单执行详情" },
      },
      {
        path: "purchasing/receipts",
        name: "PurchaseReceiptConfirm",
        component: () => import("../views/purchasing/ReceiptConfirmView.vue"),
        meta: { requiresAuth: true, title: "采购到货验收" },
      },
      {
        path: "purchasing/quality",
        name: "PurchaseQualityDisposition",
        component: () => import("../views/purchasing/QualityDispositionView.vue"),
        meta: { requiresAuth: true, title: "采购质检处置" },
      },
      {
        path: "purchasing/putaway",
        name: "PurchasePutawayTask",
        component: () => import("../views/purchasing/PutawayTaskView.vue"),
        meta: { requiresAuth: true, title: "入库上架任务" },
      },

      // ====== 阶段 4：销售履约与直接拣发 ======
      {
        path: "sales/orders",
        name: "SalesOrderList",
        component: () => import("../views/sales/SalesOrderListView.vue"),
        meta: { requiresAuth: true, title: "销售订单管理" },
      },
      {
        path: "sales/orders/:id",
        name: "SalesOrderDetail",
        component: () => import("../views/sales/SalesOrderDetailView.vue"),
        meta: { requiresAuth: true, title: "销售单履约详情" },
      },
      {
        path: "sales/reservations",
        name: "SalesReservationDetail",
        component: () => import("../views/sales/ReservationDetailView.vue"),
        meta: { requiresAuth: true, title: "销售预留分配" },
      },
      {
        path: "sales/picks",
        name: "SalesPickTask",
        component: () => import("../views/sales/PickTaskView.vue"),
        meta: { requiresAuth: true, title: "直接拣货工作台" },
      },
      {
        path: "sales/shipments",
        name: "SalesShipmentConfirm",
        component: () => import("../views/sales/ShipmentConfirmView.vue"),
        meta: { requiresAuth: true, title: "发货出库确认" },
      },

      // ====== 阶段 5：MES 制造执行 ======
      {
        path: "mes/boms",
        name: "MesBomList",
        component: () => import("../views/manufacturing/BomListView.vue"),
        meta: { requiresAuth: true, title: "BOM 物料清单" },
      },
      {
        path: "mes/routings",
        name: "MesRoutingList",
        component: () => import("../views/manufacturing/RoutingListView.vue"),
        meta: { requiresAuth: true, title: "工艺路线管理" },
      },
      {
        path: "mes/work-orders",
        name: "MesWorkOrderList",
        component: () => import("../views/manufacturing/WorkOrderListView.vue"),
        meta: { requiresAuth: true, title: "生产工单管理" },
      },
      {
        path: "mes/work-orders/:id",
        name: "MesWorkOrderDetail",
        component: () => import("../views/manufacturing/WorkOrderDetailView.vue"),
        meta: { requiresAuth: true, title: "工单全生命周期详情" },
      },
      {
        path: "mes/dispatch",
        name: "MesDispatch",
        component: () => import("../views/manufacturing/DispatchView.vue"),
        meta: { requiresAuth: true, title: "车间派工看板" },
      },
      {
        path: "mes/executions",
        name: "MesExecution",
        component: () => import("../views/manufacturing/OperationExecutionView.vue"),
        meta: { requiresAuth: true, title: "工序报工与质检" },
      },
      {
        path: "mes/materials",
        name: "MesMaterialMovement",
        component: () => import("../views/manufacturing/MaterialMovementView.vue"),
        meta: { requiresAuth: true, title: "生产领料与退料" },
      },
      {
        path: "mes/receipts",
        name: "MesFinishedGoodsReceipt",
        component: () => import("../views/manufacturing/FinishedGoodsReceiptView.vue"),
        meta: { requiresAuth: true, title: "成品完工入库" },
      },

      // ====== 阶段 6：IoT 设备与告警 ======
      {
        path: "iot/profiles",
        name: "IotProfileList",
        component: () => import("../views/iot/DeviceProfileView.vue"),
        meta: { requiresAuth: true, title: "设备配置文件" },
      },
      {
        path: "iot/devices",
        name: "IotDeviceList",
        component: () => import("../views/iot/DeviceListView.vue"),
        meta: { requiresAuth: true, title: "设备台账管理" },
      },
      {
        path: "iot/devices/:id",
        name: "IotDeviceDetail",
        component: () => import("../views/iot/DeviceDetailView.vue"),
        meta: { requiresAuth: true, title: "设备运行状态详情" },
      },
      {
        path: "iot/telemetry",
        name: "IotTelemetry",
        component: () => import("../views/iot/TelemetryView.vue"),
        meta: { requiresAuth: true, title: "时序遥测实时监控" },
      },
      {
        path: "iot/alarms",
        name: "IotAlarmList",
        component: () => import("../views/iot/AlarmListView.vue"),
        meta: { requiresAuth: true, title: "设备告警中心" },
      },
      {
        path: "iot/alarms/:id",
        name: "IotAlarmDetail",
        component: () => import("../views/iot/AlarmDetailView.vue"),
        meta: { requiresAuth: true, title: "告警处理详情" },
      },

      // ====== 阶段 7：追溯、二维 GIS 与综合看板 ======
      {
        path: "traceability",
        name: "Traceability",
        component: () => import("../views/insights/TraceabilityView.vue"),
        meta: { requiresAuth: true, title: "全链路全闭环追溯" },
      },
      {
        path: "gis/site-maps",
        name: "SiteMapList",
        component: () => import("../views/insights/SiteMapListView.vue"),
        meta: { requiresAuth: true, title: "二维 GIS 站点地图" },
      },
      {
        path: "gis/site-maps/:id",
        name: "SiteMapView",
        component: () => import("../views/insights/SiteMapView.vue"),
        meta: { requiresAuth: true, title: "空间点位画布" },
      },
      {
        path: "gis/site-maps/:id/edit",
        name: "SiteMapEditor",
        component: () => import("../views/insights/SiteMapEditorView.vue"),
        meta: { requiresAuth: true, title: "站点地图点位配置" },
      },
      {
        path: "dashboard",
        name: "InsightsDashboard",
        component: () => import("../views/insights/DashboardView.vue"),
        meta: { requiresAuth: true, title: "七类综合监控看板" },
      },
      {
        path: "system/tenant",
        name: "TenantSetting",
        component: () => import("../views/system/TenantSetting.vue"),
        meta: {
          requiresAuth: true,
          title: "租户配置",
        },
      },
      {
        path: "system/users",
        name: "UserList",
        component: () => import("../views/system/UserList.vue"),
        meta: {
          requiresAuth: true,
          title: "用户管理",
        },
      },
      {
        path: "system/roles",
        name: "RoleList",
        component: () => import("../views/system/RoleList.vue"),
        meta: {
          requiresAuth: true,
          title: "角色管理",
        },
      },
      {
        path: "system/permissions",
        name: "PermissionList",
        component: () => import("../views/system/PermissionList.vue"),
        meta: {
          requiresAuth: true,
          title: "权限目录",
        },
      },
      {
        path: "system/menus",
        name: "MenuList",
        component: () => import("../views/system/MenuList.vue"),
        meta: {
          requiresAuth: true,
          title: "菜单管理",
        },
      },
    ],
  },
  {
    path: "/:pathMatch(.*)*",
    redirect: "/",
  },
];

const router = createRouter({
  history: createWebHistory(),
  routes,
});

/**
 * 全局前置路由守卫 (beforeEach)
 * 核心流程：
 * 1. 初始化并检查 Pinia 登录上下文
 * 2. 未登录访问 requiresAuth !== false 路由时重定向至 /login，并保存 redirect 回调参数
 * 3. 已登录访问 /login 时自动重定向至工作台首页
 * 4. 同步更新 document.title
 */
router.beforeEach(async (to, from, next) => {
  const authStore = useAuthStore();

  // 若尚未初始化，先从本地存储恢复登录态
  if (!authStore.isInitialized) {
    await authStore.initAuth();
  }

  const requiresAuth = to.meta.requiresAuth !== false;
  const isLoggedIn = !!authStore.token;

  if (requiresAuth && !isLoggedIn) {
    next({
      path: "/login",
      query: { redirect: to.fullPath },
    });
    return;
  }

  if (to.path === "/login" && isLoggedIn) {
    const redirect = (to.query.redirect as string) || "/";
    next({ path: redirect });
    return;
  }

  if (to.meta.title) {
    document.title = `${to.meta.title} - AI Learn 协同平台`;
  }

  next();
});

export default router;
