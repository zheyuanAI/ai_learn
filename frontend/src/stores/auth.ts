import { defineStore } from "pinia";
import { ref, computed } from "vue";
import {
  login as loginApi,
  logout as logoutApi,
  getMe as getMeApi,
  getMyMenus as getMyMenusApi,
  type LoginRequest,
  type LoginResponse,
  type UserInfo,
  type MenuItem,
} from "../api/auth";
import { TOKEN_KEY, TENANT_KEY } from "../utils/request";

/**
 * 6 类正式角色元数据与默认配置（供前端快速填充和静态展示参考）
 */
export const ROLE_PRESETS: Record<
  string,
  {
    roleName: string;
    realName: string;
    redirect: string;
    jti: string;
    permissions: string[];
    menus: MenuItem[];
  }
> = {
  "admin.zhang": {
    roleName: "租户管理员",
    realName: "张管理员",
    redirect: "/",
    jti: "jti_9901aa48f712",
    permissions: [
      "dashboard:view (全域综合看板查看)",
      "site-map:view (厂区二维空间地图)",
      "master-data:manage (主数据管理)",
      "ai:chat (AI 受控智能问答)",
      "ai:audit:view (AI 工具调用审计日志)",
      "system:tenant:manage (租户与权限管理)",
    ],
    menus: [
      { id: "1", menuCode: "Overview", routePath: "/", menuName: "一期总览", detail: "一条黄金业务闭环", sortOrder: 1 },
      { id: "2", menuCode: "ErpWms", routePath: "/erp-wms", menuName: "供需与仓储", detail: "人工关联、预留、收发存", sortOrder: 2 },
      { id: "3", menuCode: "Mes", routePath: "/mes", menuName: "制造执行", detail: "领退料、工序执行、质检", sortOrder: 3 },
      { id: "4", menuCode: "Iot", routePath: "/iot", menuName: "设备事实", detail: "遥测、状态、告警分离", sortOrder: 4 },
      { id: "5", menuCode: "Gis", routePath: "/gis", menuName: "地图与看板", detail: "业务事实只读展示", sortOrder: 5 },
      { id: "6", menuCode: "Ai", routePath: "/ai", menuName: "AI 只读助手", detail: "受控查询、来源与审计", sortOrder: 6 },
      {
        id: "7",
        menuCode: "System",
        routePath: "/system",
        menuName: "系统管理",
        detail: "租户、用户、角色与菜单",
        sortOrder: 7,
        children: [
          { id: "7-1", parentId: "7", menuCode: "SystemTenant", routePath: "/system/tenant", menuName: "租户配置", detail: "当前租户基础信息", sortOrder: 1 },
          { id: "7-2", parentId: "7", menuCode: "SystemUsers", routePath: "/system/users", menuName: "用户管理", detail: "工号、账号与角色分配", sortOrder: 2 },
          { id: "7-3", parentId: "7", menuCode: "SystemRoles", routePath: "/system/roles", menuName: "角色管理", detail: "角色与权限菜单绑定", sortOrder: 3 },
          { id: "7-4", parentId: "7", menuCode: "SystemPermissions", routePath: "/system/permissions", menuName: "权限目录", detail: "系统只读能力点清单", sortOrder: 4 },
          { id: "7-5", parentId: "7", menuCode: "SystemMenus", routePath: "/system/menus", menuName: "菜单管理", detail: "动态菜单树维护", sortOrder: 5 },
        ],
      },
    ],
  },
  "sales.liu": {
    roleName: "销售人员",
    realName: "刘销售",
    redirect: "/erp-wms",
    jti: "jti_441bc882e190",
    permissions: [
      "sales.order.create (创建销售订单)",
      "sales.order.submit (提交销售订单)",
      "sales.order.approve (审核销售订单)",
      "sales.order.track (销售订单履约跟踪)",
      "inventory.balance.view (查看可用库存余额)",
    ],
    menus: [
      { id: "1", menuCode: "Overview", routePath: "/", menuName: "一期总览", detail: "一条黄金业务闭环", sortOrder: 1 },
      { id: "2", menuCode: "ErpWms", routePath: "/erp-wms", menuName: "供需与仓储", detail: "销售订单与库存跟踪", sortOrder: 2 },
      { id: "6", menuCode: "Ai", routePath: "/ai", menuName: "AI 只读助手", detail: "订单与库存智能查询", sortOrder: 3 },
    ],
  },
  "buyer.chen": {
    roleName: "采购人员",
    realName: "陈采购",
    redirect: "/erp-wms",
    jti: "jti_552de17a94cc",
    permissions: [
      "purchase.order.create (创建采购单)",
      "purchase.order.submit (提交采购单)",
      "purchase.order.approve (审核采购单)",
      "purchase.order.complete (人工完成采购单)",
      "quality.purchase-disposition.return (决定退回供应方)",
    ],
    menus: [
      { id: "1", menuCode: "Overview", routePath: "/", menuName: "一期总览", detail: "一条黄金业务闭环", sortOrder: 1 },
      { id: "2", menuCode: "ErpWms", routePath: "/erp-wms", menuName: "供需与仓储", detail: "采购订单与供应商管理", sortOrder: 2 },
      { id: "6", menuCode: "Ai", routePath: "/ai", menuName: "AI 只读助手", detail: "采购与到货审计查询", sortOrder: 3 },
    ],
  },
  "wh.operator": {
    roleName: "仓库人员",
    realName: "王仓管",
    redirect: "/erp-wms",
    jti: "jti_7c91d8a4f210",
    permissions: [
      "inventory.receipt.confirm (采购外观验收与实收)",
      "inventory.quality-disposition.confirm (处置执行移位与报废扣减)",
      "inventory.putaway.confirm (确认上架存储)",
      "inventory.pick.confirm (销售直接拣货内部自动预留)",
      "inventory.shipment.confirm (销售发货确认扣减总库存)",
      "inventory.issue.confirm (生产领料发货确认)",
      "inventory.transfer.confirm (库位调拨)",
    ],
    menus: [
      { id: "1", menuCode: "Overview", routePath: "/", menuName: "一期总览", detail: "一条黄金业务闭环", sortOrder: 1 },
      { id: "2", menuCode: "ErpWms", routePath: "/erp-wms", menuName: "供需与仓储", detail: "收货、上架、拣货、发货", sortOrder: 2 },
      { id: "5", menuCode: "Gis", routePath: "/gis", menuName: "地图与看板", detail: "仓位与实物分布地图", sortOrder: 3 },
    ],
  },
  "mes.inspector": {
    roleName: "生产质检人员",
    realName: "赵质检",
    redirect: "/mes",
    jti: "jti_310ad59c8814",
    permissions: [
      "quality.purchase-inspection.submit (采购到货质检)",
      "quality.purchase-disposition.decide (质量放行/报废决定)",
      "manufacturing.work-order.approve (工单审核与下达)",
      "manufacturing.execution.manage (工序开始/暂停/完工)",
      "manufacturing.work-report.submit (工序报工申报)",
      "quality.inspection.submit (成品质检判定)",
    ],
    menus: [
      { id: "1", menuCode: "Overview", routePath: "/", menuName: "一期总览", detail: "一条黄金业务闭环", sortOrder: 1 },
      { id: "3", menuCode: "Mes", routePath: "/mes", menuName: "制造执行", detail: "工单排产、报工与质检", sortOrder: 2 },
      { id: "5", menuCode: "Gis", routePath: "/gis", menuName: "地图与看板", detail: "产线与在制工序看板", sortOrder: 3 },
    ],
  },
  "iot.engineer": {
    roleName: "IoT人员",
    realName: "孙工程师",
    redirect: "/iot",
    jti: "jti_8842bc11df33",
    permissions: [
      "iot.telemetry.view (设备遥测与状态监控)",
      "iot.alarm.ack (设备告警确认与消警)",
      "iot.device.manage (设备台账与点位配置)",
      "iot.digital-twin.view (数字孪生与拓扑查看)",
    ],
    menus: [
      { id: "1", menuCode: "Overview", routePath: "/", menuName: "一期总览", detail: "一条黄金业务闭环", sortOrder: 1 },
      { id: "4", menuCode: "Iot", routePath: "/iot", menuName: "设备事实", detail: "遥测、状态与告警", sortOrder: 2 },
      { id: "5", menuCode: "Gis", routePath: "/gis", menuName: "地图与看板", detail: "厂区设备遥测点位", sortOrder: 3 },
    ],
  },
};

/**
 * 认证与全局用户会话状态管理 Store
 */
export const useAuthStore = defineStore("auth", () => {
  // 核心状态
  const token = ref<string | null>(localStorage.getItem(TOKEN_KEY));
  const user = ref<UserInfo | null>(null);
  const roles = ref<string[]>([]);
  const permissions = ref<string[]>([]);
  const menus = ref<MenuItem[]>([]);
  const activeTenant = ref<string>(localStorage.getItem(TENANT_KEY) || "tenant_demo_a");
  const isInitialized = ref<boolean>(false);
  const isSessionValid = ref<boolean>(true);

  // 计算属性
  const isLoggedIn = computed(() => !!token.value && !!user.value);
  const currentRoleName = computed(() => {
    if (!user.value) return "未登录";
    return roles.value[0] || "普通用户";
  });

  /**
   * 真实登录 Action
   * 入参为租户编码、用户名及密码，核心流程调用真实 /api/auth/login 接口，成功后初始化用户信息与菜单树
   */
  async function loginAction(credentials: LoginRequest): Promise<LoginResponse> {
    activeTenant.value = credentials.tenantCode;
    localStorage.setItem(TENANT_KEY, credentials.tenantCode);

    try {
      // 1. 调用网关认证接口
      const res = await loginApi(credentials);
      if (res.data && res.data.token) {
        token.value = res.data.token;
        user.value = res.data.user;
        roles.value = res.data.user?.roles || [];
        permissions.value = res.data.user?.perms || res.data.user?.permissions || [];
        isSessionValid.value = true;
        localStorage.setItem(TOKEN_KEY, res.data.token);

        // 2. 真实初始化用户全量画像与动态菜单树
        await fetchUserInfo();
        await fetchUserMenus();
        return res.data;
      }
      throw new Error(res.message || "登录接口返回数据异常");
    } catch (error: any) {
      // 仅当显式开启 VITE_DEMO_MODE=true 时允许离线兜底，默认必须严格抛出真实接口异常
      if (import.meta.env.VITE_DEMO_MODE === "true") {
        console.warn("[AuthStore] DEMO 模式启用，执行角色预设配置：", error.message);
        const username = credentials.username.toLowerCase().trim();
        const preset = ROLE_PRESETS[username] || ROLE_PRESETS["admin.zhang"];
        const mockToken = `jwt_${preset.jti}_${Date.now()}`;
        const mockUser: UserInfo = {
          userId: `u_${username.replace(".", "_")}`,
          id: `u_${username.replace(".", "_")}`,
          username: credentials.username,
          realName: preset.realName,
          tenantId: "a0000000-0000-0000-0000-000000000001",
          tenantCode: credentials.tenantCode,
          roles: [preset.roleName],
          permissions: preset.permissions,
          perms: preset.permissions,
          jti: preset.jti,
        };

        token.value = mockToken;
        user.value = mockUser;
        roles.value = mockUser.roles || [];
        permissions.value = mockUser.permissions || [];
        menus.value = preset.menus;
        isSessionValid.value = true;
        localStorage.setItem(TOKEN_KEY, mockToken);

        return {
          token: mockToken,
          tokenType: "Bearer",
          expiresIn: 7200,
          jti: preset.jti,
          user: mockUser,
        };
      }

      // 默认情况：清理可能存在的残留状态并抛出真实错误
      token.value = null;
      user.value = null;
      roles.value = [];
      permissions.value = [];
      menus.value = [];
      localStorage.removeItem(TOKEN_KEY);
      throw error;
    }
  }

  /**
   * 登出 Action
   * 调用登出接口并清除本地全部会话状态
   */
  async function logoutAction(): Promise<void> {
    try {
      if (token.value) {
        await logoutApi();
      }
    } catch (e) {
      console.warn("[AuthStore] 登出请求完成或被跳过", e);
    } finally {
      token.value = null;
      user.value = null;
      roles.value = [];
      permissions.value = [];
      menus.value = [];
      isSessionValid.value = false;
      localStorage.removeItem(TOKEN_KEY);
    }
  }

  /**
   * 获取并恢复当前登录用户信息 (GET /api/me)
   */
  async function fetchUserInfo(): Promise<UserInfo | null> {
    if (!token.value) return null;
    try {
      const res = await getMeApi();
      if (res.data) {
        user.value = res.data;
        roles.value = res.data.roles || [];
        permissions.value = res.data.perms || res.data.permissions || [];
        if (res.data.tenantCode) {
          activeTenant.value = res.data.tenantCode;
          localStorage.setItem(TENANT_KEY, res.data.tenantCode);
        } else if (res.data.tenantId) {
          activeTenant.value = res.data.tenantId;
          localStorage.setItem(TENANT_KEY, res.data.tenantId);
        }
        return res.data;
      }
    } catch (e: any) {
      console.error("[AuthStore] fetchUserInfo 失败:", e.message);
    }
    return null;
  }

  /**
   * 获取当前用户动态菜单树 (GET /api/me/menus)
   */
  async function fetchUserMenus(): Promise<MenuItem[]> {
    if (!token.value) return [];
    try {
      const res = await getMyMenusApi();
      if (res.data && res.data.length > 0) {
        menus.value = res.data;
        return res.data;
      }
    } catch (e: any) {
      console.error("[AuthStore] fetchUserMenus 失败:", e.message);
    }
    return [];
  }

  /**
   * 应用初始化时恢复登录态
   */
  async function initAuth(): Promise<void> {
    if (isInitialized.value) return;
    const storedToken = localStorage.getItem(TOKEN_KEY);
    const storedTenant = localStorage.getItem(TENANT_KEY);
    if (storedTenant) {
      activeTenant.value = storedTenant;
    }

    if (storedToken) {
      token.value = storedToken;
      const fetchedUser = await fetchUserInfo();
      if (fetchedUser) {
        await fetchUserMenus();
      } else {
        // 如果 token 已经无法通过真实 /api/me 校验，清除无效 token
        if (import.meta.env.VITE_DEMO_MODE !== "true") {
          token.value = null;
          user.value = null;
          roles.value = [];
          permissions.value = [];
          menus.value = [];
          localStorage.removeItem(TOKEN_KEY);
        }
      }
    }
    isInitialized.value = true;
  }

  /**
   * 模拟单会话被新登录顶替（触发 401 演示）
   */
  function simulateSessionKicked(): void {
    isSessionValid.value = false;
    if (user.value) {
      user.value.jti = "jti_INVALIDATED_401";
    }
  }

  /**
   * 刷新当前用户画像与动态菜单树
   * 在角色授权、权限点变更或菜单维护后联动调用
   */
  async function reloadAuthAndMenus(): Promise<void> {
    await Promise.all([fetchUserInfo(), fetchUserMenus()]);
  }

  return {
    token,
    user,
    roles,
    permissions,
    menus,
    activeTenant,
    isInitialized,
    isSessionValid,
    isLoggedIn,
    currentRoleName,
    loginAction,
    logoutAction,
    fetchUserInfo,
    fetchUserMenus,
    reloadAuthAndMenus,
    initAuth,
    simulateSessionKicked,
  };
});
