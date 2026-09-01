/**
 * 系统登录与认证上下文高保真交互控制台 (login.js)
 * 严格执行已冻结业务规则：
 * 1. 登录由后端建立租户、账号、角色和权限上下文；
 * 2. 签发携带 sub、jti 的 JWT 会话；
 * 3. 单会话规则：后登录替换前登录，旧 jti 再次调用返回 401 并提示“已在其他位置登录”；
 * 4. 动态加载当前角色权限与菜单树。
 */

/**
 * 6 类正式角色配置列表（遵循 Issue #4 规范）
 * 包含：租户管理员、销售人员、采购人员、仓库人员、生产质检人员、IoT人员
 */
const roleProfiles = {
  admin: {
    username: "admin.zhang",
    name: "租户管理员 (admin.zhang)",
    role: "租户管理员",
    redirect: "dashboard.html",
    jti: "jti_9901aa48f712",
    perms: [
      "dashboard:view (全域综合看板查看)",
      "site-map:view (厂区二维空间地图)",
      "master-data:manage (主数据管理)",
      "ai:chat (AI 受控智能问答)",
      "ai:audit:view (AI 工具调用审计日志)",
      "system:tenant:manage (租户与权限管理)"
    ]
  },
  sales: {
    username: "sales.liu",
    name: "销售人员 (sales.liu)",
    role: "销售人员",
    redirect: "sales-outbound.html",
    jti: "jti_441bc882e190",
    perms: [
      "sales.order.create (创建销售订单)",
      "sales.order.submit (提交销售订单)",
      "sales.order.approve (审核销售订单)",
      "sales.order.track (销售订单履约跟踪)",
      "inventory.balance.view (查看可用库存余额)"
    ]
  },
  buyer: {
    username: "buyer.chen",
    name: "采购人员 (buyer.chen)",
    role: "采购人员",
    redirect: "purchase-inbound.html",
    jti: "jti_552de17a94cc",
    perms: [
      "purchase.order.create (创建采购单)",
      "purchase.order.submit (提交采购单)",
      "purchase.order.approve (审核采购单)",
      "purchase.order.complete (人工完成采购单)",
      "quality.purchase-disposition.return (决定退回供应方)"
    ]
  },
  warehouse: {
    username: "wh.operator",
    name: "仓库人员 (wh.operator)",
    role: "仓库人员",
    redirect: "sales-outbound.html",
    jti: "jti_7c91d8a4f210",
    perms: [
      "inventory.receipt.confirm (采购外观验收与实收)",
      "inventory.quality-disposition.confirm (处置执行移位与报废扣减)",
      "inventory.putaway.confirm (确认上架存储)",
      "inventory.pick.confirm (销售直接拣货内部自动预留)",
      "inventory.shipment.confirm (销售发货确认扣减总库存)",
      "inventory.issue.confirm (生产领料发货确认)",
      "inventory.transfer.confirm (库位调拨)"
    ]
  },
  inspector: {
    username: "mes.inspector",
    name: "生产质检人员 (mes.inspector)",
    role: "生产质检人员",
    redirect: "work-order.html",
    jti: "jti_310ad59c8814",
    perms: [
      "quality.purchase-inspection.submit (采购到货质检)",
      "quality.purchase-disposition.decide (质量放行/报废决定)",
      "manufacturing.work-order.approve (工单审核与下达)",
      "manufacturing.execution.manage (工序开始/暂停/完工)",
      "manufacturing.work-report.submit (工序报工申报)",
      "quality.inspection.submit (成品质检判定)"
    ]
  },
  iot: {
    username: "iot.engineer",
    name: "IoT人员 (iot.engineer)",
    role: "IoT人员",
    redirect: "device-alarm.html",
    jti: "jti_8842bc11df33",
    perms: [
      "iot.telemetry.view (设备遥测与状态监控)",
      "iot.alarm.ack (设备告警确认与消警)",
      "iot.device.manage (设备台账与点位配置)",
      "iot.digital-twin.view (数字孪生与拓扑查看)"
    ]
  }
};

let currentActiveRoleKey = "admin";
let isCurrentSessionValid = true;

function showLoginToast(message, type = "success") {
  const toast = document.getElementById("loginToast");
  if (!toast) return;
  toast.textContent = message;
  toast.className = `console-toast is-visible is-${type}`;
  setTimeout(() => { toast.classList.remove("is-visible"); }, 3600);
}

function renderAuthContext() {
  const p = roleProfiles[currentActiveRoleKey] || roleProfiles.warehouse;
  const ctxUser = document.getElementById("ctxUser");
  const ctxRole = document.getElementById("ctxRole");
  const ctxJti = document.getElementById("ctxJti");
  const badge = document.getElementById("sessionStatusBadge");
  const menuTree = document.getElementById("authMenuTree");

  if (ctxUser) ctxUser.textContent = p.username;
  if (ctxRole) ctxRole.textContent = p.role;
  if (ctxJti) ctxJti.textContent = isCurrentSessionValid ? p.jti : "jti_INVALIDATED_401";

  if (badge) {
    badge.textContent = isCurrentSessionValid ? "会话正常 (Active)" : "已失效 (401 Displaced)";
    badge.className = `console-badge ${isCurrentSessionValid ? 'green' : 'red'}`;
  }

  if (menuTree) {
    menuTree.innerHTML = p.perms.map(perm => `
      <div style="padding:6px 10px;border-radius:5px;background:rgba(113,225,220,0.04);border:1px solid rgba(113,225,220,0.15);font-family:monospace;color:var(--c-cyan);">
        🔑 <code>${perm}</code>
      </div>
    `).join("");
  }
}

function quickFillRole(username, roleKey) {
  const userInput = document.getElementById("loginUsername");
  if (userInput) userInput.value = username;
  currentActiveRoleKey = roleKey;
  isCurrentSessionValid = true;
  renderAuthContext();
  showLoginToast(`已切换演示账号：${username}`);
}

function handleLoginSubmit(event) {
  event.preventDefault();
  const tenant = document.getElementById("loginTenant")?.value?.trim();
  const username = document.getElementById("loginUsername")?.value?.trim();
  const password = document.getElementById("loginPassword")?.value?.trim();

  if (!tenant) {
    showLoginToast("登录失败：请选择所属租户", "danger");
    return;
  }
  if (!username) {
    showLoginToast("登录失败：请输入登录用户名", "danger");
    return;
  }
  if (!password) {
    showLoginToast("登录失败：请输入登录密码", "danger");
    return;
  }

  // 根据输入的用户名匹配对应的角色配置
  let matchedKey = currentActiveRoleKey;
  for (const [key, profile] of Object.entries(roleProfiles)) {
    if (profile.username.toLowerCase() === username.toLowerCase()) {
      matchedKey = key;
      break;
    }
  }
  currentActiveRoleKey = matchedKey;
  const p = roleProfiles[currentActiveRoleKey] || roleProfiles.warehouse;

  isCurrentSessionValid = true;
  renderAuthContext();

  showLoginToast(`登录成功！当前身份：${p.role}，正在进入默认首页：${p.redirect}...`);
  setTimeout(() => {
    window.location.href = p.redirect;
  }, 1200);
}

function simulateSessionKicked() {
  isCurrentSessionValid = false;
  renderAuthContext();
  showLoginToast("⚠️ 401 告警：该账号已在另一客户端登录，旧会话已失效！", "danger");
}

function initLoginConsole() {
  const tenantSelect = document.getElementById("loginTenant");
  tenantSelect?.addEventListener("change", (e) => {
    const ctxTenant = document.getElementById("ctxTenant");
    if (ctxTenant) ctxTenant.textContent = e.target.value;
    showLoginToast(`已切换租户：${e.target.value}`);
  });

  renderAuthContext();
}

document.addEventListener("DOMContentLoaded", initLoginConsole);
