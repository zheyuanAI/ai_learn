const roleLabels = {
  "tenant-admin": "租户管理员",
  sales: "销售人员",
  purchase: "采购人员",
  warehouse: "仓库人员",
  "production-quality": "生产质检人员",
  iot: "IoT人员"
};

const workflowSteps = [
  {
    id: "access",
    index: "00",
    title: "登录与唯一会话",
    owner: "Auth Service",
    summary: "账号密码登录；同一账号的新登录替换旧会话。",
    state: "未登录 → 已登录；旧 jti → 失效",
    interfaces: ["POST /api/auth/login（待冻结）", "GET /api/me（待冻结）"],
    page: "login.html",
    actions: [
      { label: "登录", permission: "公开访问", roles: ["all"], note: "成功后签发包含 sub、jti 的 JWT" },
      { label: "管理用户与角色", permission: "auth.user.manage / auth.role.manage", roles: ["tenant-admin"], note: "仅当前租户" }
    ]
  },
  {
    id: "sales-demand",
    index: "01",
    title: "销售订单",
    owner: "销售人员",
    summary: "记录客户需求，并由销售角色内约定账号完成审核。",
    state: "生命周期 Draft → Submitted → Approved → Completed；履约进度动态派生",
    interfaces: ["POST /api/sales-orders", "POST /api/sales-orders/{id}/submit", "POST /api/sales-orders/{id}/approve", "POST /api/sales-orders/{id}/complete"],
    page: "sales-outbound.html",
    actions: [
      { label: "创建销售单", permission: "sales.order.create", roles: ["sales"], note: "记录创建账号和时间" },
      { label: "提交销售单", permission: "sales.order.submit", roles: ["sales"], note: "仅 Draft" },
      { label: "审核销售单", permission: "sales.order.approve", roles: ["sales"], note: "业务约定账号执行，技术上角色内均可" },
      { label: "人工完成", permission: "sales.order.complete", roles: ["sales"], note: "要求原因；存在未发货暂存数量时先退回拣货" }
    ]
  },
  {
    id: "work-order",
    index: "02",
    title: "生产工单与来源关联",
    owner: "生产质检人员",
    summary: "人工选择来源销售订单行，创建并下达生产工单。",
    state: "Draft → Released → InProgress → Completed",
    interfaces: ["POST /api/work-orders", "POST /api/work-orders/{id}/release", "POST /api/work-orders/{id}/complete"],
    page: "work-order.html",
    actions: [
      { label: "创建生产工单", permission: "manufacturing.work-order.create", roles: ["production-quality"], note: "来源销售行可为空或人工选择" },
      { label: "下达生产工单", permission: "manufacturing.work-order.release", roles: ["production-quality"], note: "要求 BOM 与工艺路线有效" }
    ]
  },
  {
    id: "purchase",
    index: "03",
    title: "采购订单",
    owner: "采购人员",
    summary: "采购明细可人工关联来源生产工单。",
    state: "Draft → Submitted → Approved → PartiallyReceived → Completed",
    interfaces: ["POST /api/purchase-orders", "POST /api/purchase-orders/{id}/submit", "POST /api/purchase-orders/{id}/approve"],
    page: "purchase-inbound.html",
    actions: [
      { label: "创建采购单", permission: "purchase.order.create", roles: ["purchase"], note: "人工填写来源工单" },
      { label: "提交采购单", permission: "purchase.order.submit", roles: ["purchase"], note: "仅 Draft" },
      { label: "审核采购单", permission: "purchase.order.approve", roles: ["purchase"], note: "业务约定账号执行" },
      { label: "人工完成", permission: "purchase.order.complete", roles: ["purchase"], note: "待补接口；未收货数量不再履约" }
    ]
  },
  {
    id: "inbound",
    index: "04",
    title: "外观验收、到货质检与上架",
    owner: "仓库人员",
    summary: "仓库外观验收后，实际接收货物全部进入质量隔离位；生产质检人员检验，合格放行后再上架。",
    state: "收货：未确认 → 已确认 / 质检：待检 → 待决定 / 处置：待执行 → 已完成 / 上架：待处理 → 已确认",
    interfaces: ["POST /api/purchase-receipts/{id}/confirm", "POST /api/purchase-receipts/{id}/quality/inspect", "POST /api/purchase-quality-dispositions/{id}/confirm", "POST /api/putaway-tasks/{id}/confirm"],
    page: "purchase-inbound.html",
    actions: [
      { label: "外观验收并确认接收", permission: "inventory.receipt.confirm", roles: ["warehouse"], note: "明显异常拒收；实际接收数量全部进入 QualityHold（质量隔离位）" },
      { label: "采购到货质检", permission: "quality.purchase-inspection.submit", roles: ["production-quality"], note: "记录质检合格/不合格数量，检验本身不改变库存" },
      { label: "确认上架", permission: "inventory.putaway.confirm", roles: ["warehouse"], note: "不重复增加企业总库存" }
    ]
  },
  {
    id: "material",
    index: "05",
    title: "生产领料与退料",
    owner: "生产质检人员 + 仓库人员",
    summary: "生产创建领退料单，仓库确认后由库存应用服务记账。",
    state: "Draft → Confirmed",
    interfaces: ["POST /api/material-issues", "POST /api/material-issues/{id}/confirm", "POST /api/material-returns", "POST /api/material-returns/{id}/confirm"],
    page: "work-order.html",
    actions: [
      { label: "创建领退料单", permission: "manufacturing.material.manage", roles: ["production-quality"], note: "表达生产物料需求" },
      { label: "确认领退料", permission: "inventory.material.confirm", roles: ["warehouse"], note: "确认后不可重复改变库存" }
    ]
  },
  {
    id: "execution",
    index: "06",
    title: "派工与工序执行",
    owner: "生产质检人员",
    summary: "派工表达安排，OperationExecution 记录现场实际执行。",
    state: "NotStarted → Running → Paused → Running → Completed",
    interfaces: ["POST /api/dispatch-orders", "POST /api/operation-executions", "POST /api/operation-executions/{id}/{action}"],
    page: "work-order.html",
    actions: [
      { label: "创建并下达派工", permission: "manufacturing.dispatch.manage", roles: ["production-quality"], note: "下达不代表现场已经开始" },
      { label: "开始 / 暂停 / 恢复 / 完成", permission: "manufacturing.execution.manage", roles: ["production-quality"], note: "每次记录账号和实际时间" }
    ]
  },
  {
    id: "iot",
    index: "07",
    title: "设备状态与告警",
    owner: "IoT人员",
    summary: "IoT 独立保存遥测、状态和告警，再补充生产上下文。",
    state: "Alarm: Triggered → Acked → Recovered",
    interfaces: ["GET /api/device-alarms", "POST /api/device-alarms/{id}/ack", "PUT /api/device-alarms/{id}/business-context"],
    page: "device-alarm.html",
    actions: [
      { label: "确认告警", permission: "iot.alarm.manage", roles: ["iot"], note: "只允许 Triggered → Acked" },
      { label: "补充业务上下文", permission: "iot.alarm-context.manage", roles: ["iot"], note: "不能改写原始设备事实" }
    ]
  },
  {
    id: "quality",
    index: "08",
    title: "报工与质检",
    owner: "生产质检人员",
    summary: "报工记录数量事实，质检决定合格数量能否进入成品库存。",
    state: "Inspection: Draft → Submitted → Passed / Failed",
    interfaces: ["POST /api/work-reports", "POST /api/quality-inspections", "POST /api/quality-inspections/{id}/submit"],
    page: "work-order.html",
    actions: [
      { label: "提交报工", permission: "manufacturing.work-report.create", roles: ["production-quality"], note: "合格数 + 不良数不得超过工单上限" },
      { label: "提交质检结果", permission: "quality.inspection.submit", roles: ["production-quality"], note: "Failed 阻止对应数量入库" }
    ]
  },
  {
    id: "finished-goods",
    index: "09",
    title: "成品入库",
    owner: "生产质检人员 + 仓库人员",
    summary: "生产创建成品入库单，仓库确认后增加成品库存。",
    state: "Draft → Confirmed",
    interfaces: ["POST /api/finished-goods-receipts", "POST /api/finished-goods-receipts/{id}/confirm"],
    page: "work-order.html",
    actions: [
      { label: "创建成品入库单", permission: "manufacturing.finished-receipt.create", roles: ["production-quality"], note: "数量不超过已检验合格且未入库数量" },
      { label: "确认成品入库", permission: "inventory.finished-receipt.confirm", roles: ["warehouse"], note: "确认后追加库存流水" }
    ]
  },
  {
    id: "outbound",
    index: "10",
    title: "直接拣货与发货",
    owner: "仓库人员",
    summary: "页面正常路径为直接拣货与发货；直接拣货在同一事务内自动预留本次数量并完成移位。",
    state: "生命周期保持 Approved；履约进度由数量派生；全部发货或人工完成 → Completed",
    interfaces: ["POST /api/pick-tasks/{id}/confirm", "POST /api/sales-shipments/{id}/confirm", "POST /api/pick-tasks/{id}/return（异常）", "POST /api/sales-orders/{id}/reservations/release（异常）"],
    page: "sales-outbound.html",
    actions: [
      { label: "直接拣货", permission: "inventory.pick.confirm", roles: ["warehouse"], note: "内部自动预留并移动到 ShippingStaging（发货暂存位）" },
      { label: "退回 / 释放（异常）", permission: "inventory.pick.confirm", roles: ["warehouse"], note: "仅异常恢复或终止使用，不属于正常履约步骤" },
      { label: "确认发货", permission: "inventory.shipment.confirm", roles: ["warehouse"], note: "扣减实物并释放对应预留" }
    ]
  },
  {
    id: "read-model",
    index: "11",
    title: "地图、看板、追溯与 AI",
    owner: "所有业务角色",
    summary: "只读入口复用底层领域权限，不建立第二套业务事实。",
    state: "只读查询，无业务状态迁移",
    interfaces: ["GET /api/site-map", "GET /api/dashboard/*", "POST /api/ai/chat", "POST /api/ai/tools/*"],
    page: "dashboard.html",
    actions: [
      { label: "查看授权摘要", permission: "dashboard.read", roles: ["all"], note: "结果仍按底层领域权限过滤" },
      { label: "调用 AI 只读工具", permission: "ai.chat.use + 底层 read", roles: ["all"], note: "工具白名单只能缩小权限" }
    ]
  }
];

let activeStepId = workflowSteps[0].id;
let activeRole = "sales";

/**
 * 判断当前角色是否可以执行指定动作。
 * 入参为动作定义和角色编码，返回布尔值；核心流程支持 all 通配角色并匹配动作的角色清单。
 */
function canExecuteAction(action, role) {
  return action.roles.includes("all") || action.roles.includes(role);
}

/**
 * 渲染黄金业务闭环的流程节点。
 * 无入参和返回值；核心流程按步骤生成可点击按钮，并高亮当前正在评审的节点。
 */
function renderFlowBoard() {
  const board = document.querySelector("#flowBoard");
  if (!board) return;
  board.innerHTML = workflowSteps.map((step) => `
    <button class="flow-node ${step.id === activeStepId ? "is-active" : ""}" type="button" data-step-id="${step.id}">
      <span>${step.index}</span>
      <strong>${step.title}</strong>
      <small>${step.owner}</small>
    </button>
  `).join("");

  board.querySelectorAll("[data-step-id]").forEach((button) => {
    button.addEventListener("click", () => {
      activeStepId = button.dataset.stepId;
      renderFlowBoard();
      renderStepDetail();
    });
  });
}

/**
 * 渲染当前流程节点的权限、状态、接口和动作详情。
 * 无入参和返回值；核心流程根据当前角色计算动作是否可执行，并生成对应页面跳转入口。
 */
function renderStepDetail() {
  const detail = document.querySelector("#stepDetail");
  const step = workflowSteps.find((item) => item.id === activeStepId);
  if (!detail || !step) return;

  const actions = step.actions.map((action) => {
    const allowed = canExecuteAction(action, activeRole);
    return `
      <li class="wire-action ${allowed ? "is-allowed" : "is-denied"}">
        <span class="wire-action-mark">${allowed ? "可执行" : "无权限"}</span>
        <div>
          <strong>${action.label}</strong>
          <code>${action.permission}</code>
          <p>${action.note}</p>
        </div>
      </li>
    `;
  }).join("");

  detail.innerHTML = `
    <div class="detail-heading">
      <span class="detail-index">${step.index}</span>
      <div>
        <p class="wire-kicker">当前评审节点</p>
        <h2>${step.title}</h2>
        <p>${step.summary}</p>
      </div>
    </div>
    <div class="wire-facts">
      <div><span>主责角色</span><strong>${step.owner}</strong></div>
      <div><span>当前模拟角色</span><strong>${roleLabels[activeRole]}</strong></div>
      <div class="wire-fact-wide"><span>状态流转</span><strong>${step.state}</strong></div>
    </div>
    <h3>页面动作与权限</h3>
    <ul class="wire-actions">${actions}</ul>
    <h3>目标接口</h3>
    <ul class="wire-interfaces">${step.interfaces.map((item) => `<li><code>${item}</code></li>`).join("")}</ul>
    <a class="wire-page-link" href="${step.page}">打开对应现有原型页面 →</a>
  `;
}

/**
 * 初始化角色切换和流程评审交互。
 * 无入参和返回值；核心流程监听角色选择变化，并重新计算当前节点的可执行动作。
 */
function initializeWorkflowReview() {
  const roleSelect = document.querySelector("#roleSelect");
  if (roleSelect) {
    roleSelect.addEventListener("change", (event) => {
      activeRole = event.target.value;
      renderStepDetail();
    });
  }
  renderFlowBoard();
  renderStepDetail();
}

document.addEventListener("DOMContentLoaded", initializeWorkflowReview);
