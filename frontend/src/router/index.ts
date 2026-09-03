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
        meta: {
          requiresAuth: true,
          title: "供需与仓储",
        },
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
        meta: {
          requiresAuth: true,
          title: "制造执行",
        },
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
        meta: {
          requiresAuth: true,
          title: "IoT 设备事实",
        },
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
        meta: {
          requiresAuth: true,
          title: "二维地图与看板",
        },
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
        meta: {
          requiresAuth: true,
          title: "AI 只读助手",
        },
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
