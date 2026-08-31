const salesActionDefinitions = {
  pick: { label: "直接拣货", permission: "inventory.pick.confirm", role: "warehouse", tone: "primary", group: "normal" },
  ship: { label: "确认发货", permission: "inventory.shipment.confirm", role: "warehouse", tone: "primary", group: "normal" },
  return: { label: "退回拣货", permission: "inventory.pick.confirm", role: "warehouse", tone: "warning", group: "exception" },
  release: { label: "释放未拣预留", permission: "inventory.reservation.manage", role: "warehouse", tone: "neutral", group: "exception" },
  complete: { label: "人工完成", permission: "sales.order.complete", role: "sales", tone: "danger", group: "exception" }
};

const roleLabelsForSales = {
  warehouse: "仓库人员",
  sales: "销售人员"
};

const statusLabels = {
  Draft: "未提交",
  Submitted: "已提交",
  Approved: "已审核",
  Completed: "已完成"
};

const fulfillmentLabels = {
  NotStarted: "尚未开始",
  InProgress: "履约处理中",
  FullyShipped: "全部发货"
};

const salesScenarios = {
  partial: {
    featuredOrderId: "SO-20260826-018",
    orders: [
      {
        id: "SO-20260826-018",
        customer: "华北智造系统有限公司",
        customerCode: "CUS-NC-021",
        owner: "陈敏",
        plannedShipDate: "2026-08-28",
        status: "Approved",
        completionType: null,
        completionReason: null,
        priority: "紧急",
        warehouse: "成品一仓",
        shippingLocation: "SHP-01",
        lines: [
          { id: "L10", sku: "FG-SERVO-01", product: "伺服电机总成", uom: "件", orderedQty: 100, reservedQty: 40, pickedQty: 40, shippedQty: 20, sourceLocation: "FG-A-01" },
          { id: "L20", sku: "FG-CTRL-08", product: "边缘控制终端", uom: "台", orderedQty: 20, reservedQty: 10, pickedQty: 10, shippedQty: 10, sourceLocation: "FG-B-04" }
        ],
        events: [
          { time: "08-26 18:42", action: "第一次发货确认", actor: "wh.operator", session: "jti…7c91", key: "SHIP-20260826-01", impact: "L10 发货 20 件；发货暂存位与企业实物库存各减少 20" },
          { time: "08-26 17:56", action: "直接拣货", actor: "wh.operator", session: "jti…7c91", key: "PICK-20260826-03", impact: "L10 自动预留并拣货 40 件；FG-A-01 → SHP-01，总库存不变" }
        ]
      },
      {
        id: "SO-20260826-021",
        customer: "苏州精密装备研究院",
        customerCode: "CUS-EA-014",
        owner: "李珂",
        plannedShipDate: "2026-08-30",
        status: "Approved",
        completionType: null,
        completionReason: null,
        priority: "标准",
        warehouse: "成品一仓",
        shippingLocation: "SHP-01",
        lines: [
          { id: "L10", sku: "FG-SENSOR-12", product: "工业视觉传感器", uom: "套", orderedQty: 36, reservedQty: 0, pickedQty: 0, shippedQty: 0, sourceLocation: "FG-C-02" }
        ],
        events: [
          { time: "08-26 15:08", action: "销售订单审核", actor: "sales.reviewer", session: "jti…94aa", key: "APR-20260826-09", impact: "生命周期进入 Approved；未自动预留库存" }
        ]
      },
      {
        id: "SO-20260825-009",
        customer: "宁波柔性制造中心",
        customerCode: "CUS-EA-006",
        owner: "陈敏",
        plannedShipDate: "2026-08-26",
        status: "Completed",
        completionType: "Normal",
        completionReason: null,
        completedAt: "2026-08-26 11:18",
        completedBy: "wh.operator",
        completedSession: "jti…7c91",
        priority: "标准",
        warehouse: "成品一仓",
        shippingLocation: "SHP-02",
        lines: [
          { id: "L10", sku: "FG-DRIVE-05", product: "高性能驱动器", uom: "台", orderedQty: 48, reservedQty: 48, pickedQty: 48, shippedQty: 48, sourceLocation: "FG-A-06" }
        ],
        events: [
          { time: "08-26 11:18", action: "全部发货并正常完成", actor: "wh.operator", session: "jti…7c91", key: "SHIP-20260826-11", impact: "48 台全部发货；Completed / Normal" }
        ]
      }
    ]
  },
  manual: {
    featuredOrderId: "SO-20260824-032",
    orders: [
      {
        id: "SO-20260824-032",
        customer: "青岛自动化事业部",
        customerCode: "CUS-NC-030",
        owner: "周静",
        plannedShipDate: "2026-08-25",
        status: "Completed",
        completionType: "Manual",
        completionReason: "客户项目范围缩减，确认剩余 60 件不再交付。",
        completedAt: "2026-08-26 19:06",
        completedBy: "sales.operator",
        completedSession: "jti…2f18",
        priority: "关注",
        warehouse: "成品一仓",
        shippingLocation: "SHP-01",
        lines: [
          { id: "L10", sku: "FG-SERVO-01", product: "伺服电机总成", uom: "件", orderedQty: 100, reservedQty: 40, pickedQty: 40, shippedQty: 40, sourceLocation: "FG-A-01" }
        ],
        events: [
          { time: "08-26 19:06", action: "人工完成销售订单", actor: "sales.operator", session: "jti…2f18", key: "COMP-20260826-02", impact: "释放剩余有效预留 20 件；未补造发货；已发 40 件保留" },
          { time: "08-26 18:51", action: "退回未发货拣货", actor: "wh.operator", session: "jti…7c91", key: "RETURN-20260826-01", impact: "20 件从 SHP-01 退回 FG-A-01；总库存不变" },
          { time: "08-25 16:33", action: "第一次发货确认", actor: "wh.operator", session: "jti…7c91", key: "SHIP-20260825-05", impact: "发货 40 件并释放对应预留" }
        ]
      },
      {
        id: "SO-20260826-021",
        customer: "苏州精密装备研究院",
        customerCode: "CUS-EA-014",
        owner: "李珂",
        plannedShipDate: "2026-08-30",
        status: "Approved",
        completionType: null,
        completionReason: null,
        priority: "标准",
        warehouse: "成品一仓",
        shippingLocation: "SHP-01",
        lines: [
          { id: "L10", sku: "FG-SENSOR-12", product: "工业视觉传感器", uom: "套", orderedQty: 36, reservedQty: 0, pickedQty: 0, shippedQty: 0, sourceLocation: "FG-C-02" }
        ],
        events: []
      }
    ]
  },
  normal: {
    featuredOrderId: "SO-20260825-009",
    orders: [
      {
        id: "SO-20260825-009",
        customer: "宁波柔性制造中心",
        customerCode: "CUS-EA-006",
        owner: "陈敏",
        plannedShipDate: "2026-08-26",
        status: "Completed",
        completionType: "Normal",
        completionReason: null,
        completedAt: "2026-08-26 11:18",
        completedBy: "wh.operator",
        completedSession: "jti…7c91",
        priority: "标准",
        warehouse: "成品一仓",
        shippingLocation: "SHP-02",
        lines: [
          { id: "L10", sku: "FG-DRIVE-05", product: "高性能驱动器", uom: "台", orderedQty: 48, reservedQty: 48, pickedQty: 48, shippedQty: 48, sourceLocation: "FG-A-06" },
          { id: "L20", sku: "FG-CABLE-02", product: "动力线缆组件", uom: "套", orderedQty: 48, reservedQty: 48, pickedQty: 48, shippedQty: 48, sourceLocation: "FG-D-02" }
        ],
        events: [
          { time: "08-26 11:18", action: "全部发货并正常完成", actor: "wh.operator", session: "jti…7c91", key: "SHIP-20260826-11", impact: "全部订单行达到 ordered_qty；Completed / Normal" },
          { time: "08-26 10:46", action: "第二批直接拣货", actor: "wh.operator", session: "jti…7c91", key: "PICK-20260826-08", impact: "系统自动预留剩余数量并移入 SHP-02；总库存不变" }
        ]
      },
      {
        id: "SO-20260826-018",
        customer: "华北智造系统有限公司",
        customerCode: "CUS-NC-021",
        owner: "陈敏",
        plannedShipDate: "2026-08-28",
        status: "Approved",
        completionType: null,
        completionReason: null,
        priority: "紧急",
        warehouse: "成品一仓",
        shippingLocation: "SHP-01",
        lines: [
          { id: "L10", sku: "FG-SERVO-01", product: "伺服电机总成", uom: "件", orderedQty: 100, reservedQty: 40, pickedQty: 40, shippedQty: 20, sourceLocation: "FG-A-01" }
        ],
        events: []
      }
    ]
  }
};

const salesPageState = {
  scenario: "partial",
  role: "warehouse",
  statusFilter: "all",
  fulfillmentFilter: "all",
  search: "",
  selectedOrderId: null,
  orders: []
};

let salesDialogReturnFocus = null;

/**
 * 深拷贝指定评审场景的订单假数据。
 * 入参为场景编码，出参为可在浏览器内独立修改的订单数组。
 * 核心流程优先使用 structuredClone，并在旧浏览器中回退到 JSON 序列化，避免场景原始定义被演示动作污染。
 */
function cloneScenarioOrders(scenario) {
  const source = salesScenarios[scenario].orders;
  return typeof structuredClone === "function"
    ? structuredClone(source)
    : JSON.parse(JSON.stringify(source));
}

/**
 * 转义即将写入 HTML 模板的动态文本。
 * 入参为任意可显示值，出参为安全字符串。
 * 核心流程替换 HTML 特殊字符，避免订单或客户假数据破坏页面结构。
 */
function escapeSalesHtml(value) {
  return String(value ?? "").replace(/[&<>"']/g, (character) => ({
    "&": "&amp;",
    "<": "&lt;",
    ">": "&gt;",
    '"': "&quot;",
    "'": "&#039;"
  }[character]));
}

/**
 * 根据订单行事实数量计算剩余量和在途量。
 * 入参为订单行对象，出参为五个只读派生数量。
 * 核心流程只做减法，不修改源对象，确保所有视图和动作上限复用同一业务口径。
 */
function deriveLineProgress(line) {
  return {
    unreservedQty: line.orderedQty - line.reservedQty,
    unpickedQty: line.reservedQty - line.pickedQty,
    shippingStagedQty: line.pickedQty - line.shippedQty,
    activeReservedQty: line.reservedQty - line.shippedQty,
    unshippedQty: line.orderedQty - line.shippedQty
  };
}

/**
 * 根据全部订单行动态计算销售订单履约进度。
 * 入参为订单行数组，出参为 NotStarted、InProgress 或 FullyShipped。
 * 核心流程先识别全部发货，再识别完全未开始，其余组合统一归入处理中，且不会写回数据库字段。
 */
function deriveFulfillmentStatus(lines) {
  if (lines.every((line) => line.shippedQty === line.orderedQty)) return "FullyShipped";
  if (lines.every((line) => line.reservedQty === 0 && line.pickedQty === 0 && line.shippedQty === 0)) return "NotStarted";
  return "InProgress";
}

/**
 * 校验订单行是否满足销售履约数量不变量。
 * 入参为订单行对象，出参为布尔值。
 * 核心流程按 shipped <= picked <= reserved <= ordered 的顺序检查边界，演示动作若破坏不变量将被整体拒绝。
 */
function isLineQuantityValid(line) {
  return 0 <= line.shippedQty
    && line.shippedQty <= line.pickedQty
    && line.pickedQty <= line.reservedQty
    && line.reservedQty <= line.orderedQty;
}

/**
 * 获取当前选中的销售订单。
 * 无入参，出参为订单对象或 undefined。
 * 核心流程使用页面状态中的订单编号查询当前场景订单，供详情、动作和弹窗共享。
 */
function getSelectedSalesOrder() {
  return salesPageState.orders.find((order) => order.id === salesPageState.selectedOrderId);
}

/**
 * 计算指定动作在某订单行上的最大可操作数量。
 * 入参为订单行和动作编码，出参为非负数量。
 * 核心流程将直接拣货、异常释放、退回和发货映射到正式规则中的对应派生上限。
 */
function getActionMaximum(line, action) {
  const progress = deriveLineProgress(line);
  const maximumByAction = {
    release: progress.unpickedQty,
    pick: line.orderedQty - line.pickedQty,
    return: progress.shippingStagedQty,
    ship: progress.shippingStagedQty
  };
  return Math.max(0, maximumByAction[action] ?? 0);
}

/**
 * 判断动作在当前角色、生命周期和数量上下文中是否可执行。
 * 入参为订单和动作编码，出参包含 allowed 布尔值及禁用原因。
 * 核心流程依次校验角色、Approved 生命周期、人工完成暂存前置和数量上限，确保页面按钮不替代后端最终校验。
 */
function getActionAvailability(order, action) {
  const definition = salesActionDefinitions[action];
  if (salesPageState.role !== definition.role) {
    return { allowed: false, reason: `需要${roleLabelsForSales[definition.role]}权限` };
  }
  if (order.status !== "Approved") {
    return { allowed: false, reason: "订单已进入终态，仅允许查看" };
  }
  if (action === "complete") {
    const hasStagedQuantity = order.lines.some(
      (line) => deriveLineProgress(line).shippingStagedQty > 0
    );
    return hasStagedQuantity
      ? { allowed: false, reason: "存在未发货暂存数量，请先由仓库人员退回拣货" }
      : { allowed: true, reason: "填写原因后终止剩余履约" };
  }
  const hasQuantity = order.lines.some((line) => getActionMaximum(line, action) > 0);
  return hasQuantity
    ? { allowed: true, reason: definition.permission }
    : { allowed: false, reason: "当前没有可操作数量" };
}

/**
 * 生成订单列表中的双轴状态标签。
 * 入参为订单对象，出参为 HTML 字符串。
 * 核心流程并列展示持久化生命周期与动态履约进度，人工/正常完成方式作为第三个终态标签补充。
 */
function renderSalesStatusBadges(order) {
  const fulfillment = deriveFulfillmentStatus(order.lines);
  const completion = order.completionType
    ? `<span class="sales-badge completion ${order.completionType === "Manual" ? "manual" : ""}">${order.completionType === "Manual" ? "人工完成" : "正常完成"}</span>`
    : "";
  return `
    <div class="sales-badge-row">
      <span class="sales-badge lifecycle">${escapeSalesHtml(statusLabels[order.status])}</span>
      <span class="sales-badge fulfillment ${fulfillment.toLowerCase()}">${escapeSalesHtml(fulfillmentLabels[fulfillment])}</span>
      ${completion}
    </div>
  `;
}

/**
 * 按搜索词和生命周期筛选销售订单。
 * 无入参，出参为当前应展示的订单数组。
 * 核心流程组合状态筛选与订单号、客户、产品全文匹配，不修改原始订单集合。
 */
function getVisibleSalesOrders() {
  const keyword = salesPageState.search.trim().toLocaleLowerCase("zh-CN");
  return salesPageState.orders.filter((order) => {
    const matchesStatus = salesPageState.statusFilter === "all"
      || order.status === salesPageState.statusFilter;
    const matchesFulfillment = salesPageState.fulfillmentFilter === "all"
      || deriveFulfillmentStatus(order.lines) === salesPageState.fulfillmentFilter;
    const text = [
      order.id,
      order.customer,
      ...order.lines.flatMap((line) => [line.sku, line.product])
    ].join(" ").toLocaleLowerCase("zh-CN");
    return matchesStatus && matchesFulfillment && (!keyword || text.includes(keyword));
  });
}

/**
 * 渲染销售订单队列并绑定订单选择事件。
 * 无入参和返回值。
 * 核心流程使用筛选结果生成双轴状态卡，更新计数，并在点击后刷新当前订单详情。
 */
function renderSalesOrderList() {
  const list = document.querySelector("#salesOrderList");
  const count = document.querySelector("#salesOrderCount");
  if (!list || !count) return;

  const visibleOrders = getVisibleSalesOrders();
  count.textContent = String(visibleOrders.length).padStart(2, "0");
  list.innerHTML = visibleOrders.length
    ? visibleOrders.map((order) => {
      const isActive = order.id === salesPageState.selectedOrderId;
      const fulfillment = deriveFulfillmentStatus(order.lines);
      const shippedLines = order.lines.filter((line) => line.shippedQty === line.orderedQty).length;
      return `
        <button type="button" class="sales-order-card ${isActive ? "is-active" : ""}" data-order-id="${escapeSalesHtml(order.id)}">
          <span class="sales-order-accent ${fulfillment.toLowerCase()}"></span>
          <span class="sales-order-card-top">
            <strong>${escapeSalesHtml(order.id)}</strong>
            <em class="${order.priority === "紧急" ? "urgent" : ""}">${escapeSalesHtml(order.priority)}</em>
          </span>
          <span class="sales-order-customer">${escapeSalesHtml(order.customer)}</span>
          ${renderSalesStatusBadges(order)}
          <span class="sales-order-card-foot">
            <small>计划 ${escapeSalesHtml(order.plannedShipDate.slice(5))}</small>
            <small>完成行 ${shippedLines}/${order.lines.length}</small>
          </span>
        </button>
      `;
    }).join("")
    : `
      <div class="sales-empty">
        <strong>没有匹配订单</strong>
        <span>调整关键词或生命周期筛选后重试。</span>
      </div>
    `;

  list.querySelectorAll("[data-order-id]").forEach((button) => {
    button.addEventListener("click", () => {
      salesPageState.selectedOrderId = button.dataset.orderId;
      renderSalesWorkspace();
    });
  });
}

/**
 * 渲染订单行的内部自动预留、直接拣货和发货管线。
 * 入参为订单行对象，出参为 HTML 字符串。
 * 核心流程将四个事实数量与三个阶段宽度绑定，并在同一行展示可核对的剩余量。
 */
function renderLinePipeline(line) {
  const progress = deriveLineProgress(line);
  const ratio = (value) => Math.max(0, Math.min(100, (value / line.orderedQty) * 100));
  return `
    <article class="sales-line-card">
      <header>
        <div>
          <span>${escapeSalesHtml(line.id)} · ${escapeSalesHtml(line.sku)}</span>
          <h3>${escapeSalesHtml(line.product)}</h3>
        </div>
        <strong>${line.shippedQty} / ${line.orderedQty} ${escapeSalesHtml(line.uom)}</strong>
      </header>
      <div class="sales-pipeline-rail" aria-label="${escapeSalesHtml(line.product)}履约进度">
        <div class="sales-pipeline-layer reserved" style="width: ${ratio(line.reservedQty)}%"></div>
        <div class="sales-pipeline-layer picked" style="width: ${ratio(line.pickedQty)}%"></div>
        <div class="sales-pipeline-layer shipped" style="width: ${ratio(line.shippedQty)}%"></div>
      </div>
      <div class="sales-stage-grid">
        <div><i class="ordered"></i><span>订单</span><strong>${line.orderedQty}</strong></div>
        <div><i class="reserved"></i><span>已预留</span><strong>${line.reservedQty}</strong><small>未拣 ${progress.unpickedQty}</small></div>
        <div><i class="picked"></i><span>已拣货</span><strong>${line.pickedQty}</strong><small>暂存 ${progress.shippingStagedQty}</small></div>
        <div><i class="shipped"></i><span>已发货</span><strong>${line.shippedQty}</strong><small>未发 ${progress.unshippedQty}</small></div>
      </div>
      <footer>
        <span>来源库位 <strong>${escapeSalesHtml(line.sourceLocation)}</strong></span>
        <span>有效预留 <strong>${progress.activeReservedQty} ${escapeSalesHtml(line.uom)}</strong></span>
        <span class="sales-invariant">✓ 数量不变量成立</span>
      </footer>
    </article>
  `;
}

/**
 * 渲染当前订单可执行动作及禁用原因。
 * 入参为订单对象，出参为 HTML 字符串。
 * 核心流程遍历固定动作定义，根据当前角色和数量上下文计算可用性，并把原因作为按钮说明展示。
 */
function renderSalesActionButtons(order, group) {
  return Object.entries(salesActionDefinitions).filter(([, definition]) => definition.group === group).map(([action, definition]) => {
    const availability = getActionAvailability(order, action);
    return `
      <button type="button" class="sales-action ${definition.tone}" data-action="${action}" ${availability.allowed ? "" : "disabled"}>
        <span>${escapeSalesHtml(definition.label)}</span>
        <small>${escapeSalesHtml(availability.reason)}</small>
      </button>
    `;
  }).join("");
}

/**
 * 将销售动作拆分为正常履约与异常/终止两组。
 * 入参为订单对象，出参为分组后的 HTML 字符串。
 * 核心流程只把直接拣货和发货放入正常流程，避免退回或释放被误解为必经步骤。
 */
function renderSalesActions(order) {
  return `
    <section class="sales-action-group is-normal">
      <div class="sales-action-group-head"><strong>正常流程</strong><span>审核通过 → 直接拣货 → 发货</span></div>
      <div class="sales-action-buttons">${renderSalesActionButtons(order, "normal")}</div>
    </section>
    <section class="sales-action-group is-exception">
      <div class="sales-action-group-head"><strong>异常 / 终止</strong><span>不属于正常履约顺序</span></div>
      <div class="sales-action-buttons">${renderSalesActionButtons(order, "exception")}</div>
    </section>
  `;
}

/**
 * 渲染订单操作审计时间线。
 * 入参为订单对象，出参为 HTML 字符串。
 * 核心流程按事件当前顺序展示账号、会话、幂等键和库存影响，空记录时输出明确占位。
 */
function renderSalesTimeline(order) {
  if (!order.events.length) {
    return '<div class="sales-empty compact"><strong>暂无履约事件</strong><span>审核不自动产生库存事实。</span></div>';
  }
  return order.events.map((event, index) => `
    <article class="sales-event">
      <span class="sales-event-index">${String(index + 1).padStart(2, "0")}</span>
      <div class="sales-event-main">
        <header><strong>${escapeSalesHtml(event.action)}</strong><time>${escapeSalesHtml(event.time)}</time></header>
        <p>${escapeSalesHtml(event.impact)}</p>
        <footer>
          <span>账号 ${escapeSalesHtml(event.actor)}</span>
          <span>会话 ${escapeSalesHtml(event.session)}</span>
          <code>${escapeSalesHtml(event.key)}</code>
        </footer>
      </div>
    </article>
  `).join("");
}

/**
 * 渲染当前销售订单详情页。
 * 无入参和返回值。
 * 核心流程组合双轴状态、行级数量、角色化动作、库存语义和审计时间线，并为动作按钮绑定弹窗。
 */
function renderSalesOrderDetail() {
  const detail = document.querySelector("#salesOrderDetail");
  const order = getSelectedSalesOrder();
  if (!detail) return;
  if (!order) {
    detail.innerHTML = '<div class="sales-empty"><strong>当前筛选没有可展示的订单</strong><span>调整关键词或生命周期筛选后查看详情。</span></div>';
    return;
  }

  const fulfillment = deriveFulfillmentStatus(order.lines);
  const activeReservationLines = order.lines.filter((line) => deriveLineProgress(line).activeReservedQty > 0).length;
  const stagedLines = order.lines.filter((line) => deriveLineProgress(line).shippingStagedQty > 0).length;
  const shippedLines = order.lines.filter((line) => line.shippedQty === line.orderedQty).length;
  const completionPanel = order.completionType
    ? `
      <aside class="sales-completion-note ${order.completionType === "Manual" ? "manual" : "normal"}">
        <span>完成方式 / ${escapeSalesHtml(order.completionType)}</span>
        <strong>${order.completionType === "Manual" ? "剩余履约已受控终止" : "全部订单行已正常发货"}</strong>
        <p>${escapeSalesHtml(order.completionReason || "系统在最后一笔发货事务内自动完成订单。")}</p>
        <small>${escapeSalesHtml(order.completedAt || "")} ${order.completedBy ? `· ${escapeSalesHtml(order.completedBy)} · ${escapeSalesHtml(order.completedSession)}` : ""}</small>
      </aside>
    `
    : "";

  detail.innerHTML = `
    <header class="sales-detail-head">
      <div>
        <span class="sales-section-index">订单详情 / ${escapeSalesHtml(order.customerCode)}</span>
        <h2>${escapeSalesHtml(order.id)}</h2>
        <p>${escapeSalesHtml(order.customer)} · 销售负责人 ${escapeSalesHtml(order.owner)}</p>
      </div>
      <div class="sales-detail-status">
        ${renderSalesStatusBadges(order)}
        <small>计划发货 ${escapeSalesHtml(order.plannedShipDate)}</small>
      </div>
    </header>

    <section class="sales-state-explainer">
      <div>
        <span>生命周期 / 持久化</span>
        <strong>${escapeSalesHtml(order.status)}</strong>
        <small>负责动作边界</small>
      </div>
      <span class="sales-state-divider" aria-hidden="true">×</span>
      <div>
        <span>履约进度 / 动态派生</span>
        <strong>${escapeSalesHtml(fulfillment)}</strong>
        <small>由订单行数量计算</small>
      </div>
      <p>仓储动作不会把主状态改成 Reserved、Picking 或 Shipped。</p>
    </section>

    ${completionPanel}

    <section class="sales-kpi-strip" aria-label="订单履约摘要">
      <div><span>订单明细</span><strong>${order.lines.length}</strong><small>不跨计量单位求和</small></div>
      <div><span>有效预留行</span><strong>${activeReservationLines}</strong><small>reserved - shipped &gt; 0</small></div>
      <div><span>发货暂存行</span><strong class="${stagedLines ? "is-warning" : ""}">${stagedLines}</strong><small>picked - shipped &gt; 0</small></div>
      <div><span>全部发货行</span><strong class="is-success">${shippedLines} / ${order.lines.length}</strong><small>逐行核对 ordered</small></div>
    </section>

    <section class="sales-detail-section">
      <div class="sales-section-head">
        <div><span class="sales-section-index">01 / 数量事实</span><h3>分批履约管线</h3></div>
        <p><i class="reserved"></i>内部自动预留 <i class="picked"></i>直接拣货 <i class="shipped"></i>发货</p>
      </div>
      <div class="sales-line-stack">${order.lines.map(renderLinePipeline).join("")}</div>
    </section>

    <section class="sales-detail-section sales-action-section">
      <div class="sales-section-head">
        <div><span class="sales-section-index">02 / 动作权限</span><h3>${escapeSalesHtml(roleLabelsForSales[salesPageState.role])}操作台</h3></div>
        <p>正常操作与异常处理分区展示</p>
      </div>
      <div class="sales-action-grid">${renderSalesActions(order)}</div>
      <p class="sales-action-hint">禁用按钮会展示具体原因；后端仍需重新校验租户、权限、状态、数量、幂等键和库存条件。</p>
    </section>

    <section class="sales-fact-grid">
      <article>
        <span class="sales-section-index">03 / 库存影响</span>
        <h3>两个页面动作 · 三个内部时点</h3>
        <div class="sales-impact-list">
          <div><b>AUTO RESERVE</b><span>直接拣货内部自动预留</span><strong>available ↓</strong><small>与拣货同一事务</small></div>
          <div><b>PICK</b><span>直接拣货移位</span><strong>位置移动</strong><small>总实物不变</small></div>
          <div><b>SHIP</b><span>发货</span><strong>on_hand ↓</strong><small>释放对应预留</small></div>
        </div>
      </article>
      <article>
        <span class="sales-section-index">04 / 位置上下文</span>
        <h3>当前仓储路径</h3>
        <div class="sales-location-route">
          <div><span>来源</span><strong>${escapeSalesHtml(order.warehouse)}</strong><small>${escapeSalesHtml(order.lines.map((line) => line.sourceLocation).join(" / "))}</small></div>
          <span aria-hidden="true">→</span>
          <div><span>发货暂存</span><strong>ShippingStaging</strong><small>${escapeSalesHtml(order.shippingLocation)}</small></div>
        </div>
      </article>
    </section>

    <section class="sales-detail-section">
      <div class="sales-section-head">
        <div><span class="sales-section-index">05 / 不可变审计</span><h3>履约时间线</h3></div>
        <p>账号 · 会话 · 幂等键 · 库存影响</p>
      </div>
      <div class="sales-timeline">${renderSalesTimeline(order)}</div>
    </section>
  `;

  detail.querySelectorAll("[data-action]").forEach((button) => {
    button.addEventListener("click", () => openSalesActionDialog(button.dataset.action));
  });
}

/**
 * 一次性刷新订单队列和当前订单详情。
 * 无入参和返回值。
 * 核心流程先确保选择仍存在，再分别渲染列表和详情，供筛选、场景切换和动作成功后复用。
 */
function renderSalesWorkspace() {
  const visibleOrders = getVisibleSalesOrders();
  const selectedOrderIsVisible = visibleOrders.some(
    (order) => order.id === salesPageState.selectedOrderId
  );
  if (!selectedOrderIsVisible) {
    salesPageState.selectedOrderId = visibleOrders[0]?.id ?? null;
  }
  renderSalesOrderList();
  renderSalesOrderDetail();
}

/**
 * 根据动作生成弹窗中的订单行选项。
 * 入参为订单和动作编码，出参为 option HTML。
 * 核心流程显示每行当前最大可操作数量，并禁用上限为零的订单行。
 */
function renderActionLineOptions(order, action) {
  return order.lines.map((line) => {
    const maximum = getActionMaximum(line, action);
    return `<option value="${escapeSalesHtml(line.id)}" ${maximum > 0 ? "" : "disabled"}>${escapeSalesHtml(line.id)} · ${escapeSalesHtml(line.product)} · 可操作 ${maximum} ${escapeSalesHtml(line.uom)}</option>`;
  }).join("");
}

/**
 * 打开指定销售履约动作弹窗。
 * 入参为动作编码，无返回值。
 * 核心流程再次检查动作可用性，人工完成展示原因输入，其余动作展示订单行、数量与业务影响摘要。
 */
function openSalesActionDialog(action) {
  const dialog = document.querySelector("#salesActionDialog");
  const shell = document.querySelector(".shell");
  const order = getSelectedSalesOrder();
  const definition = salesActionDefinitions[action];
  if (!dialog || !order || !definition) return;

  const availability = getActionAvailability(order, action);
  if (!availability.allowed) {
    showSalesToast(availability.reason, "warning");
    return;
  }

  const firstEligibleLine = order.lines.find((line) => getActionMaximum(line, action) > 0);
  const isComplete = action === "complete";
  salesDialogReturnFocus = document.activeElement;
  dialog.dataset.orderId = order.id;
  dialog.dataset.action = action;
  dialog.dataset.role = salesPageState.role;
  dialog.hidden = false;
  if (shell) shell.inert = true;
  dialog.innerHTML = `
    <div class="sales-dialog-backdrop" data-close-dialog></div>
    <section class="sales-dialog-panel" role="dialog" aria-modal="true" aria-labelledby="salesDialogTitle">
      <header>
        <div><span class="sales-section-index">${escapeSalesHtml(definition.permission)}</span><h2 id="salesDialogTitle">${escapeSalesHtml(definition.label)}</h2></div>
        <button type="button" class="sales-dialog-close" data-close-dialog aria-label="关闭">×</button>
      </header>
      <div class="sales-dialog-context">
        <span>订单</span><strong>${escapeSalesHtml(order.id)}</strong>
        <span>生命周期</span><strong>${escapeSalesHtml(order.status)}</strong>
        <span>模拟角色</span><strong>${escapeSalesHtml(roleLabelsForSales[salesPageState.role])}</strong>
      </div>
      <form id="salesActionForm" novalidate>
        ${isComplete ? `
          <label class="sales-form-field">
            <span>人工完成原因 <b>*</b></span>
            <textarea name="completionReason" rows="4" maxlength="200" aria-describedby="completionReasonHelp salesFormError" placeholder="说明客户变更、业务终止或其他真实原因"></textarea>
            <small id="completionReasonHelp">完成后保留已发生事实，不补造剩余数量的拣货或发货。</small>
          </label>
        ` : `
          <label class="sales-form-field">
            <span>订单明细</span>
            <select name="lineId">${renderActionLineOptions(order, action)}</select>
          </label>
          <label class="sales-form-field">
            <span>本次数量 <b>*</b></span>
            <div class="sales-quantity-input">
              <input name="quantity" type="number" min="1" step="1" aria-describedby="quantityHint salesFormError" value="${firstEligibleLine ? getActionMaximum(firstEligibleLine, action) : 0}" />
              <em data-quantity-unit>${escapeSalesHtml(firstEligibleLine?.uom || "")}</em>
            </div>
            <small id="quantityHint" data-quantity-hint>当前最大可操作 ${firstEligibleLine ? getActionMaximum(firstEligibleLine, action) : 0} ${escapeSalesHtml(firstEligibleLine?.uom || "")}</small>
          </label>
        `}
        <div class="sales-dialog-impact">
          <span>动作语义</span>
          <p>${escapeSalesHtml(getActionImpactDescription(action))}</p>
        </div>
        <p id="salesFormError" class="sales-form-error" role="alert" aria-live="assertive" data-form-error></p>
        <footer>
          <button type="button" data-close-dialog>取消</button>
          <button type="submit" class="primary">确认演示</button>
        </footer>
      </form>
    </section>
  `;

  dialog.querySelectorAll("[data-close-dialog]").forEach((element) => {
    element.addEventListener("click", closeSalesActionDialog);
  });

  const form = dialog.querySelector("#salesActionForm");
  const lineSelect = form?.elements.lineId;
  lineSelect?.addEventListener("change", () => updateActionQuantityHint(form, order, action));
  form?.addEventListener("submit", (event) => {
    event.preventDefault();
    submitSalesAction(form, action);
  });
  dialog.addEventListener("keydown", trapSalesDialogFocus);
  dialog.querySelector("textarea, select, input")?.focus();
}

/**
 * 返回动作对数量或库存的简洁业务影响说明。
 * 入参为动作编码，出参为中文说明字符串。
 * 核心流程从固定映射读取规则摘要，保证弹窗提示与正式规格一致。
 */
function getActionImpactDescription(action) {
  return {
    release: "只释放未拣数量；减少订单行与库存有效预留；on_hand_qty 不变。",
    pick: "优先使用已有未拣预留，不足部分自动预留；自动预留与实物/预留分配移入 ShippingStaging（发货暂存位）在同一事务内完成，企业总实物不变。",
    return: "减少 picked_qty；实物与有效预留分配从 ShippingStaging 同步退回来源库位；不做业务释放。",
    ship: "增加 shipped_qty；扣减发货暂存、企业总实物及对应预留分配，并释放对应库存预留。",
    complete: "释放剩余有效预留并进入 Completed / Manual；不补造库存流水。"
  }[action];
}

/**
 * 在订单行切换后更新动作数量上限和计量单位。
 * 入参为表单、订单和动作编码，无返回值。
 * 核心流程重新读取选中行、更新输入最大值和提示，并把默认数量设为该行最大可操作量。
 */
function updateActionQuantityHint(form, order, action) {
  const line = order.lines.find((item) => item.id === form.elements.lineId.value);
  if (!line) return;
  const maximum = getActionMaximum(line, action);
  form.elements.quantity.max = maximum;
  form.elements.quantity.value = maximum;
  form.querySelector("[data-quantity-unit]").textContent = line.uom;
  form.querySelector("[data-quantity-hint]").textContent = `当前最大可操作 ${maximum} ${line.uom}`;
}

/**
 * 关闭销售履约动作弹窗。
 * 无入参和返回值。
 * 核心流程清空弹窗内容并恢复 hidden，防止旧表单状态影响下一次演示。
 */
function closeSalesActionDialog() {
  const dialog = document.querySelector("#salesActionDialog");
  const shell = document.querySelector(".shell");
  if (!dialog) return;
  dialog.removeEventListener("keydown", trapSalesDialogFocus);
  dialog.hidden = true;
  dialog.innerHTML = "";
  delete dialog.dataset.orderId;
  delete dialog.dataset.action;
  delete dialog.dataset.role;
  if (shell) shell.inert = false;
  if (salesDialogReturnFocus instanceof HTMLElement) salesDialogReturnFocus.focus();
  salesDialogReturnFocus = null;
}

/**
 * 将键盘焦点限制在销售动作弹窗内。
 * 入参为键盘事件，无返回值。
 * 核心流程支持 Escape 关闭，并在 Tab 到达首尾时循环焦点，避免焦点落入已 inert 的后台内容。
 */
function trapSalesDialogFocus(event) {
  const dialog = document.querySelector("#salesActionDialog");
  if (!dialog || dialog.hidden) return;
  if (event.key === "Escape") {
    event.preventDefault();
    closeSalesActionDialog();
    return;
  }
  if (event.key !== "Tab") return;
  const focusable = [...dialog.querySelectorAll(
    'button:not([disabled]), input:not([disabled]), select:not([disabled]), textarea:not([disabled]), [tabindex]:not([tabindex="-1"])'
  )].filter((element) => !element.hidden);
  if (!focusable.length) return;
  const first = focusable[0];
  const last = focusable[focusable.length - 1];
  if (event.shiftKey && document.activeElement === first) {
    event.preventDefault();
    last.focus();
  } else if (!event.shiftKey && document.activeElement === last) {
    event.preventDefault();
    first.focus();
  }
}

/**
 * 统一更新销售动作表单的校验错误状态。
 * 入参为表单、错误消息及可选字段名，无返回值。
 * 核心流程清理旧 aria-invalid，写入实时错误区，并将焦点移到需要修正的字段。
 */
function setSalesFormError(form, message, fieldName = null) {
  form.querySelectorAll('[aria-invalid="true"]').forEach((field) => field.removeAttribute("aria-invalid"));
  const error = form.querySelector("[data-form-error]");
  if (error) error.textContent = message;
  const field = fieldName ? form.elements[fieldName] : null;
  if (message && field instanceof HTMLElement) {
    field.setAttribute("aria-invalid", "true");
    field.focus();
  }
}

/**
 * 提交并在浏览器内应用销售履约演示动作。
 * 入参为表单和动作编码，无返回值。
 * 核心流程校验原因或数量，克隆订单用于回滚，按动作更新事实数量，验证不变量，追加审计事件并刷新页面。
 */
function submitSalesAction(form, action) {
  const dialog = document.querySelector("#salesActionDialog");
  const order = getSelectedSalesOrder();
  if (!dialog || !order) return;
  setSalesFormError(form, "");

  const availability = getActionAvailability(order, action);
  const contextIsCurrent = dialog.dataset.orderId === order.id
    && dialog.dataset.action === action
    && dialog.dataset.role === salesPageState.role;
  if (!contextIsCurrent || !availability.allowed) {
    setSalesFormError(form, contextIsCurrent ? availability.reason : "订单、角色或动作上下文已变化，请关闭后重新发起。");
    return;
  }

  if (action === "complete") {
    const reason = form.elements.completionReason.value.trim();
    if (!reason) {
      setSalesFormError(form, "请填写人工完成原因。", "completionReason");
      return;
    }
    const hasStagedQuantity = order.lines.some(
      (line) => deriveLineProgress(line).shippingStagedQty > 0
    );
    if (hasStagedQuantity) {
      setSalesFormError(form, "存在未发货暂存数量，请先由仓库人员退回拣货。");
      return;
    }
    order.lines.forEach((line) => {
      line.reservedQty = line.shippedQty;
      line.pickedQty = line.shippedQty;
    });
    order.status = "Completed";
    order.completionType = "Manual";
    order.completionReason = reason;
    order.completedAt = formatSalesDateTime(new Date());
    order.completedBy = "sales.operator";
    order.completedSession = "jti…demo";
    appendSalesEvent(order, action, null, "剩余有效预留已释放；未补造拣货、发货或库存流水");
  } else {
    const line = order.lines.find((item) => item.id === form.elements.lineId.value);
    const quantity = Number(form.elements.quantity.value);
    const maximum = line ? getActionMaximum(line, action) : 0;
    if (!line || !Number.isFinite(quantity) || quantity <= 0 || quantity > maximum) {
      setSalesFormError(form, `数量必须大于 0 且不能超过当前上限 ${maximum}。`, "quantity");
      return;
    }

    const snapshot = { ...line };
    if (action === "release") line.reservedQty -= quantity;
    let actionImpact = getActionImpactDescription(action);
    if (action === "pick") {
      const existingUnpickedQty = line.reservedQty - line.pickedQty;
      const autoReservedQty = Math.max(0, quantity - existingUnpickedQty);
      line.reservedQty += autoReservedQty;
      line.pickedQty += quantity;
      actionImpact = `自动预留 ${autoReservedQty} ${line.uom}，直接拣货 ${quantity} ${line.uom}；预留和移位在同一事务内完成。`;
    }
    if (action === "return") line.pickedQty -= quantity;
    if (action === "ship") line.shippedQty += quantity;

    if (!isLineQuantityValid(line)) {
      Object.assign(line, snapshot);
      setSalesFormError(form, "本次动作会破坏 shipped ≤ picked ≤ reserved ≤ ordered 数量不变量。");
      return;
    }

    if (action === "ship" && deriveFulfillmentStatus(order.lines) === "FullyShipped") {
      order.status = "Completed";
      order.completionType = "Normal";
      order.completedAt = formatSalesDateTime(new Date());
      order.completedBy = "wh.operator";
      order.completedSession = "jti…demo";
    }
    appendSalesEvent(order, action, line, actionImpact, quantity);
  }

  closeSalesActionDialog();
  renderSalesWorkspace();
  showSalesToast(`${salesActionDefinitions[action].label}演示成功，双轴状态与数量事实已重新计算。`, "success");
}

/**
 * 向订单时间线追加一条浏览器内审计事件。
 * 入参为订单、动作编码、可选订单行、影响说明和可选数量，无返回值。
 * 核心流程生成模拟账号、会话和幂等键，并把最新事件插入时间线顶部。
 */
function appendSalesEvent(order, action, line, impact, quantity = null) {
  const actor = salesPageState.role === "sales" ? "sales.operator" : "wh.operator";
  const quantityText = line && quantity !== null ? `${line.id} · ${quantity} ${line.uom}；` : "";
  order.events.unshift({
    time: formatSalesDateTime(new Date()).slice(5),
    action: salesActionDefinitions[action].label,
    actor,
    session: "jti…demo",
    key: `DEMO-${action.toUpperCase()}-${String(Date.now()).slice(-6)}`,
    impact: `${quantityText}${impact}`
  });
}

/**
 * 将日期格式化为原型使用的本地时间文本。
 * 入参为 Date 对象，出参为 yyyy-MM-dd HH:mm 字符串。
 * 核心流程按本地时区补零拼接年月日时分，避免引入外部日期依赖。
 */
function formatSalesDateTime(date) {
  const pad = (value) => String(value).padStart(2, "0");
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())} ${pad(date.getHours())}:${pad(date.getMinutes())}`;
}

/**
 * 展示短暂的页面操作反馈。
 * 入参为消息和 success/warning 类型，无返回值。
 * 核心流程更新状态区内容和类型，清理旧计时器，并在约三秒后自动隐藏。
 */
function showSalesToast(message, type = "success") {
  const toast = document.querySelector("#salesToast");
  if (!toast) return;
  toast.textContent = message;
  toast.className = `sales-toast is-visible ${type === "warning" ? "is-warning" : "is-success"}`;
  window.clearTimeout(showSalesToast.timeoutId);
  showSalesToast.timeoutId = window.setTimeout(() => {
    toast.classList.remove("is-visible");
  }, 3200);
}

/**
 * 切换评审场景并恢复该场景原始假数据。
 * 入参为场景编码，无返回值。
 * 核心流程克隆数据、选择场景主订单、同步标签选中态并完整刷新工作台。
 */
function switchSalesScenario(scenario) {
  if (!salesScenarios[scenario]) return;
  salesPageState.scenario = scenario;
  salesPageState.statusFilter = "all";
  salesPageState.fulfillmentFilter = "all";
  salesPageState.orders = cloneScenarioOrders(scenario);
  salesPageState.selectedOrderId = salesScenarios[scenario].featuredOrderId;
  document.querySelectorAll("[data-scenario]").forEach((button) => {
    const isSelected = button.dataset.scenario === scenario;
    button.setAttribute("aria-pressed", String(isSelected));
  });
  document.querySelectorAll("[data-status-filter]").forEach((button) => {
    button.classList.toggle("is-active", button.dataset.statusFilter === "all");
    button.setAttribute("aria-pressed", String(button.dataset.statusFilter === "all"));
  });
  document.querySelectorAll("[data-fulfillment-filter]").forEach((button) => {
    button.classList.toggle("is-active", button.dataset.fulfillmentFilter === "all");
    button.setAttribute("aria-pressed", String(button.dataset.fulfillmentFilter === "all"));
  });
  renderSalesWorkspace();
}

/**
 * 初始化销售交付高保真原型的全部交互。
 * 无入参和返回值。
 * 核心流程装载默认场景，并绑定角色、场景、筛选、搜索、Escape 关闭弹窗等页面级事件。
 */
function initializeSalesOutboundPrototype() {
  const roleSelect = document.querySelector("#salesRole");
  const search = document.querySelector("#salesSearch");
  salesPageState.orders = cloneScenarioOrders(salesPageState.scenario);
  salesPageState.selectedOrderId = salesScenarios[salesPageState.scenario].featuredOrderId;

  roleSelect?.addEventListener("change", (event) => {
    salesPageState.role = event.target.value;
    renderSalesOrderDetail();
  });
  search?.addEventListener("input", (event) => {
    salesPageState.search = event.target.value;
    renderSalesWorkspace();
  });
  document.querySelectorAll("[data-scenario]").forEach((button) => {
    button.addEventListener("click", () => switchSalesScenario(button.dataset.scenario));
  });
  document.querySelectorAll("[data-status-filter]").forEach((button) => {
    button.addEventListener("click", () => {
      salesPageState.statusFilter = button.dataset.statusFilter;
      document.querySelectorAll("[data-status-filter]").forEach((item) => {
        const isActive = item === button;
        item.classList.toggle("is-active", isActive);
        item.setAttribute("aria-pressed", String(isActive));
      });
      renderSalesWorkspace();
    });
  });
  document.querySelectorAll("[data-fulfillment-filter]").forEach((button) => {
    button.addEventListener("click", () => {
      salesPageState.fulfillmentFilter = button.dataset.fulfillmentFilter;
      document.querySelectorAll("[data-fulfillment-filter]").forEach((item) => {
        const isActive = item === button;
        item.classList.toggle("is-active", isActive);
        item.setAttribute("aria-pressed", String(isActive));
      });
      renderSalesWorkspace();
    });
  });
  document.addEventListener("keydown", (event) => {
    if (event.key === "Escape") closeSalesActionDialog();
  });
  renderSalesWorkspace();
}

document.addEventListener("DOMContentLoaded", initializeSalesOutboundPrototype);
