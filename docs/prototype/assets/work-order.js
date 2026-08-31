/**
 * 制造执行与质检高保真交互控制台 (work-order.js)
 * 严格执行已冻结业务规则：
 * 1. 生产工单人工关联来源销售订单明细，一期不触发自动 MRP/APS；
 * 2. 审核并下达时锁定 BOM 与工艺路线版本；
 * 3. 生产领料通过库存应用服务扣减原料库存，退料增加退回库位库存；
 * 4. 派工单与 OperationExecution 分离，记录开始、暂停（必填原因）、恢复、完成、人员和设备事实；
 * 5. 报工：reported_qty = qualified_qty + defective_qty；
 * 6. 成品质检判定合格品与不良品处置，合格成品分批确认入库到 FG-A-01；
 * 7. 人工完成终止未完工余量，已发生领退料与入库流水永久保留。
 */

const roleLabelsForWorkOrder = {
  inspector: "生产质检人员 (mes.inspector)",
  warehouse: "仓库人员 (wh.operator)"
};

const workOrderStatusLabels = {
  Draft: "未提交",
  Submitted: "待审核",
  Released: "已下达",
  InProgress: "生产中",
  Completed: "已完工"
};

const workOrderScenarios = {
  running: {
    featuredOrderId: "WO-20260826-018",
    orders: [
      {
        id: "WO-20260826-018",
        sku: "FG-SERVO-01",
        product: "伺服电机总成",
        uom: "台",
        plannedQty: 80,
        sourceSalesOrderLine: "SO-20260826-018 / L10",
        sourceSalesCustomer: "华北智造系统有限公司",
        bomVersion: "BOM-FG-SERVO-V1 (已锁定)",
        routingVersion: "RT-SERVO-01-V1 (已锁定)",
        plannedStartDate: "2026-08-26",
        plannedEndDate: "2026-08-28",
        status: "InProgress",
        completionType: null,
        completionReason: null,
        priority: "紧急",
        materialIssuedQty: 70,
        materialReturnedQty: 2,
        reportedQualifiedQty: 58,
        reportedDefectiveQty: 2,
        inspectedPassedQty: 58,
        inspectedFailedQty: 2,
        receivedToFgQty: 0,
        currentOperation: "工序 30 - 性能检测与标定包装",
        operations: [
          { seq: 10, name: "定子转子机加工与装配", workCenter: "WC-ASSY-01", operator: "张工", device: "无", status: "Completed", plannedQty: 80, completedQty: 80 },
          { seq: 20, name: "轴承与电气控制接线", workCenter: "WC-ELEC-01", operator: "李工", device: "无", status: "Completed", plannedQty: 80, completedQty: 80 },
          { seq: 30, name: "性能检测与标定包装", workCenter: "WC-TEST-01", operator: "王工", device: "DEV-C12 (全自动包装测试线)", status: "Running", plannedQty: 80, completedQty: 58, executionId: "OE-20260826-033", executionStatus: "Running" }
        ],
        events: [
          { time: "08-26 16:10", action: "工序 30 报工与成品质检", actor: "mes.inspector", session: "jti…310a", key: "RPT-20260826-08", impact: "申报合格 58 台并通过成品质检；申报不良 2 台待报废隔离" },
          { time: "08-26 14:00", action: "工序 30 OperationExecution 开始", actor: "mes.inspector", session: "jti…310a", key: "OE-20260826-033", impact: "王工在 DEV-C12 开始工序 30；记录设备与工序上下文" },
          { time: "08-26 11:30", action: "生产领料确认", actor: "wh.operator", session: "jti…7c91", key: "MI-20260826-01", impact: "原料一仓领用 RM-SERVO-ST 70 件；扣减原料库存" },
          { time: "08-26 09:00", action: "工单审核与下达", actor: "mes.inspector", session: "jti…310a", key: "WO-REL-20260826-01", impact: "工单下达，锁定 BOM 与工艺路线版本" }
        ]
      },
      {
        id: "WO-20260826-022",
        sku: "FG-CTRL-08",
        product: "边缘控制终端",
        uom: "台",
        plannedQty: 20,
        sourceSalesOrderLine: "SO-20260826-018 / L20",
        sourceSalesCustomer: "华北智造系统有限公司",
        bomVersion: "BOM-FG-CTRL-V2 (已锁定)",
        routingVersion: "RT-CTRL-08-V1 (已锁定)",
        plannedStartDate: "2026-08-27",
        plannedEndDate: "2026-08-29",
        status: "Released",
        completionType: null,
        completionReason: null,
        priority: "标准",
        materialIssuedQty: 0,
        materialReturnedQty: 0,
        reportedQualifiedQty: 0,
        reportedDefectiveQty: 0,
        inspectedPassedQty: 0,
        inspectedFailedQty: 0,
        receivedToFgQty: 0,
        currentOperation: "工序 10 - SMT 贴片焊接",
        operations: [
          { seq: 10, name: "SMT 贴片焊接", workCenter: "WC-SMT-01", operator: "赵工", device: "DEV-A01", status: "NotStarted", plannedQty: 20, completedQty: 0 },
          { seq: 20, name: "整机总装与老化测试", workCenter: "WC-ASSY-02", operator: "孙工", device: "无", status: "NotStarted", plannedQty: 20, completedQty: 0 }
        ],
        events: [
          { time: "08-26 10:00", action: "工单下达", actor: "mes.inspector", session: "jti…310a", key: "WO-REL-20260826-02", impact: "工单下达，待生产领料与派工" }
        ]
      }
    ]
  },
  inspected: {
    featuredOrderId: "WO-20260825-010",
    orders: [
      {
        id: "WO-20260825-010",
        sku: "FG-SERVO-01",
        product: "伺服电机总成",
        uom: "台",
        plannedQty: 70,
        sourceSalesOrderLine: "SO-20260825-009 / L10",
        sourceSalesCustomer: "宁波柔性制造中心",
        bomVersion: "BOM-FG-SERVO-V1 (已锁定)",
        routingVersion: "RT-SERVO-01-V1 (已锁定)",
        plannedStartDate: "2026-08-25",
        plannedEndDate: "2026-08-26",
        status: "InProgress",
        completionType: null,
        completionReason: null,
        priority: "标准",
        materialIssuedQty: 70,
        materialReturnedQty: 2,
        reportedQualifiedQty: 68,
        reportedDefectiveQty: 2,
        inspectedPassedQty: 68,
        inspectedFailedQty: 2,
        receivedToFgQty: 0,
        currentOperation: "全部工序已完工待入库",
        operations: [
          { seq: 10, name: "定子转子机加工与装配", workCenter: "WC-ASSY-01", operator: "张工", device: "无", status: "Completed", plannedQty: 70, completedQty: 70 },
          { seq: 20, name: "轴承与电气控制接线", workCenter: "WC-ELEC-01", operator: "李工", device: "无", status: "Completed", plannedQty: 70, completedQty: 70 },
          { seq: 30, name: "性能检测与标定包装", workCenter: "WC-TEST-01", operator: "王工", device: "DEV-C12", status: "Completed", plannedQty: 70, completedQty: 70, executionId: "OE-20260825-019", executionStatus: "Completed" }
        ],
        events: [
          { time: "08-26 15:30", action: "成品质检合格确认", actor: "mes.inspector", session: "jti…310a", key: "QA-FG-20260826-01", impact: "成品质检合格 68 台，不良 2 台报废；生成成品入库单待仓库确认" },
          { time: "08-26 14:10", action: "工序 30 全部完工报工", actor: "mes.inspector", session: "jti…310a", key: "RPT-20260826-05", impact: "报工 70 台：申报合格 68 台，不良 2 台" }
        ]
      }
    ]
  },
  completed: {
    featuredOrderId: "WO-20260824-001",
    orders: [
      {
        id: "WO-20260824-001",
        sku: "FG-SERVO-01",
        product: "伺服电机总成",
        uom: "台",
        plannedQty: 50,
        sourceSalesOrderLine: "SO-20260824-001 / L10",
        sourceSalesCustomer: "华东智能装备",
        bomVersion: "BOM-FG-SERVO-V1 (已锁定)",
        routingVersion: "RT-SERVO-01-V1 (已锁定)",
        plannedStartDate: "2026-08-24",
        plannedEndDate: "2026-08-25",
        status: "Completed",
        completionType: "Normal",
        completionReason: null,
        completedAt: "2026-08-25 17:00",
        completedBy: "wh.operator",
        priority: "标准",
        materialIssuedQty: 50,
        materialReturnedQty: 0,
        reportedQualifiedQty: 50,
        reportedDefectiveQty: 0,
        inspectedPassedQty: 50,
        inspectedFailedQty: 0,
        receivedToFgQty: 50,
        currentOperation: "已完工并全量入库",
        operations: [
          { seq: 10, name: "定子转子机加工与装配", workCenter: "WC-ASSY-01", operator: "张工", device: "无", status: "Completed", plannedQty: 50, completedQty: 50 },
          { seq: 20, name: "轴承与电气控制接线", workCenter: "WC-ELEC-01", operator: "李工", device: "无", status: "Completed", plannedQty: 50, completedQty: 50 },
          { seq: 30, name: "性能检测与标定包装", workCenter: "WC-TEST-01", operator: "王工", device: "DEV-C12", status: "Completed", plannedQty: 50, completedQty: 50, executionId: "OE-20260824-002", executionStatus: "Completed" }
        ],
        events: [
          { time: "08-25 17:00", action: "成品入库与正常完工", actor: "wh.operator", session: "jti…7c91", key: "FGR-20260825-01", impact: "50 台全量入库至 FG-A-01；工单 Completed / Normal" }
        ]
      }
    ]
  }
};

let currentWorkOrderScenario = "running";
let currentWorkOrders = JSON.parse(JSON.stringify(workOrderScenarios.running.orders));
let currentWorkOrderRole = "inspector";
let selectedWorkOrderId = "WO-20260826-018";
let workOrderStatusFilter = "all";
let workOrderSearchQuery = "";

function getSelectedWorkOrder() {
  return currentWorkOrders.find(o => o.id === selectedWorkOrderId) || currentWorkOrders[0];
}

function showWorkOrderToast(message, type = "success") {
  const toast = document.getElementById("workOrderToast");
  if (!toast) return;
  toast.textContent = message;
  toast.className = `console-toast is-visible is-${type}`;
  setTimeout(() => { toast.classList.remove("is-visible"); }, 3600);
}

function renderWorkOrderQueue() {
  const listEl = document.getElementById("workOrderList");
  const countEl = document.getElementById("workOrderCount");
  if (!listEl || !countEl) return;

  const filtered = currentWorkOrders.filter(wo => {
    if (workOrderStatusFilter !== "all" && wo.status !== workOrderStatusFilter) return false;
    if (workOrderSearchQuery) {
      const q = workOrderSearchQuery.toLowerCase();
      const matchId = wo.id.toLowerCase().includes(q);
      const matchProd = wo.product.toLowerCase().includes(q) || wo.sku.toLowerCase().includes(q);
      const matchSo = (wo.sourceSalesOrderLine || '').toLowerCase().includes(q);
      if (!matchId && !matchProd && !matchSo) return false;
    }
    return true;
  });

  countEl.textContent = filtered.length;

  if (filtered.length === 0) {
    listEl.innerHTML = '<div style="padding:20px;text-align:center;color:var(--c-muted);font-size:12px;">无匹配工单</div>';
    return;
  }

  listEl.innerHTML = filtered.map(wo => {
    const isSelected = wo.id === selectedWorkOrderId ? "is-active" : "";
    const accentClass = wo.status === "Released" ? "approved" : wo.status === "InProgress" ? "inprogress" : "completed";

    return `
      <div class="console-card ${isSelected}" onclick="selectWorkOrder('${wo.id}')">
        <span class="console-card-accent ${accentClass}"></span>
        <div class="console-card-top">
          <strong>${wo.id}</strong>
          <em class="${wo.priority === '紧急' ? 'urgent' : ''}">${wo.priority}</em>
        </div>
        <div class="console-card-title">${wo.product} (${wo.sku})</div>
        <div style="font-size:11px;color:var(--c-cyan);">计划: ${wo.plannedQty} ${wo.uom} · 完工: ${wo.receivedToFgQty}</div>
        <div class="console-card-foot">
          <span>来源: ${wo.sourceSalesOrderLine ? wo.sourceSalesOrderLine.split(' / ')[0] : '无'}</span>
          <span class="console-badge ${accentClass}">${workOrderStatusLabels[wo.status] || wo.status}</span>
        </div>
      </div>
    `;
  }).join("");
}

function selectWorkOrder(orderId) {
  selectedWorkOrderId = orderId;
  renderWorkOrderQueue();
  renderWorkOrderDetail();
}

function renderWorkOrderDetail() {
  const detailEl = document.getElementById("workOrderDetail");
  const wo = getSelectedWorkOrder();
  if (!detailEl || !wo) return;

  const currentOp = wo.operations.find(o => o.status === "Running") || wo.operations.find(o => o.status === "NotStarted") || wo.operations[wo.operations.length - 1];
  const pendingIssue = Math.max(0, wo.plannedQty - wo.materialIssuedQty);
  const pendingReceipt = Math.max(0, wo.inspectedPassedQty - wo.receivedToFgQty);

  const canApproveRelease = wo.status === "Draft" && currentWorkOrderRole === "inspector";
  const canIssue = pendingIssue > 0 && (wo.status === "Released" || wo.status === "InProgress") && currentWorkOrderRole === "warehouse";
  const canReturnMat = wo.materialIssuedQty > 0 && currentWorkOrderRole === "warehouse";
  const canOperateOp = (wo.status === "Released" || wo.status === "InProgress") && currentWorkOrderRole === "inspector";
  const canReport = wo.status === "InProgress" && currentWorkOrderRole === "inspector";
  const canInspect = wo.reportedQualifiedQty > wo.inspectedPassedQty && currentWorkOrderRole === "inspector";
  const canReceiveFg = pendingReceipt > 0 && currentWorkOrderRole === "warehouse";
  const canManualComplete = (wo.status === "Released" || wo.status === "InProgress") && currentWorkOrderRole === "inspector";

  detailEl.innerHTML = `
    <header class="console-detail-head">
      <div>
        <div class="console-title-meta">
          <span class="console-module-code">WORK ORDER</span>
          <span class="console-badge ${wo.status === 'Completed' ? 'green' : 'amber'}">${workOrderStatusLabels[wo.status] || wo.status}</span>
        </div>
        <h2>${wo.id}</h2>
        <p>产品：${wo.product} (${wo.sku}) · 计划生产：${wo.plannedQty} ${wo.uom} · 计划周期：${wo.plannedStartDate} 至 ${wo.plannedEndDate}</p>
      </div>
      <div class="console-detail-status">
        <span class="console-badge cyan">来源需求: ${wo.sourceSalesOrderLine} (${wo.sourceSalesCustomer})</span>
        <small>BOM: ${wo.bomVersion}</small>
      </div>
    </header>

    <div class="console-state-explainer">
      <div>
        <span>工单执行进度</span>
        <strong>${wo.currentOperation}</strong>
        <small>当前节点状态: ${currentOp ? currentOp.status : '未开始'}</small>
      </div>
      <div class="console-state-divider">→</div>
      <div>
        <span>数量事实跟踪</span>
        <strong>已领料 ${wo.materialIssuedQty} / 已报工 ${wo.reportedQualifiedQty}</strong>
        <small>质检合格 ${wo.inspectedPassedQty} / 成品入库 ${wo.receivedToFgQty}</small>
      </div>
      <p>
        <strong>制造事实守恒：</strong><br/>
        报工申报：<code>reported_qty = qualified_qty + defective_qty</code><br/>
        成品入库通过库存应用服务增加 <code>FG-A-01</code> 成品库存，并生成来源明确的库存流水。
      </p>
    </div>

    ${wo.completionReason ? `
      <div style="margin:14px 24px 0;padding:12px 16px;border-left:3px solid var(--c-amber);background:rgba(243,180,93,0.08);border-radius:4px;font-size:12px;">
        <strong style="color:var(--c-amber);">人工完成记录：</strong> ${wo.completionReason}
      </div>
    ` : ''}

    <div class="console-kpi-strip">
      <div class="console-kpi-item">
        <span>计划生产量</span>
        <strong>${wo.plannedQty} <small>${wo.uom}</small></strong>
        <small>工单目标</small>
      </div>
      <div class="console-kpi-item highlight-cyan">
        <span>已领用原料</span>
        <strong>${wo.materialIssuedQty} <small>套</small></strong>
        <small>退料: ${wo.materialReturnedQty} 套</small>
      </div>
      <div class="console-kpi-item highlight-amber">
        <span>已申报合格</span>
        <strong>${wo.reportedQualifiedQty} <small>${wo.uom}</small></strong>
        <small>报工不良: ${wo.reportedDefectiveQty}</small>
      </div>
      <div class="console-kpi-item highlight-green">
        <span>成品质检合格</span>
        <strong>${wo.inspectedPassedQty} <small>${wo.uom}</small></strong>
        <small>不良报废: ${wo.inspectedFailedQty}</small>
      </div>
      <div class="console-kpi-item highlight-green">
        <span>成品已入库</span>
        <strong>${wo.receivedToFgQty} <small>${wo.uom}</small></strong>
        <small>存储位 FG-A-01</small>
      </div>
    </div>

    <section class="console-section">
      <div class="console-section-head">
        <h3>工艺路线与工序执行 (OperationExecution) 跟踪</h3>
        <small>工艺路线版本：${wo.routingVersion}</small>
      </div>
      <div class="console-table-shell">
        <table class="console-table">
          <thead>
            <tr>
              <th>工序序号</th>
              <th>工序名称</th>
              <th>工作中心</th>
              <th>执行人</th>
              <th>关联设备</th>
              <th>计划量 / 完工量</th>
              <th>工序状态</th>
              <th>OperationExecution 标识</th>
            </tr>
          </thead>
          <tbody>
            ${wo.operations.map(op => `
              <tr>
                <td><strong>${op.seq}</strong></td>
                <td><strong>${op.name}</strong></td>
                <td>${op.workCenter}</td>
                <td>${op.operator}</td>
                <td><code>${op.device}</code></td>
                <td>${op.plannedQty} / <strong style="color:var(--c-cyan);">${op.completedQty}</strong></td>
                <td>
                  <span class="console-badge ${op.status === 'Completed' ? 'green' : op.status === 'Running' ? 'amber' : ''}">
                    ${op.status === 'Completed' ? '已完工' : op.status === 'Running' ? '进行中' : op.status === 'Paused' ? '已暂停' : '未开始'}
                  </span>
                </td>
                <td><code>${op.executionId || '-'}</code></td>
              </tr>
            `).join("")}
          </tbody>
        </table>
      </div>
    </section>

    <div class="console-action-panel">
      <div class="console-action-group-head">
        <strong>可执行制造与仓储动作</strong>
        <span>当前身份：${roleLabelsForWorkOrder[currentWorkOrderRole]}</span>
      </div>

      <div class="console-action-buttons">
        <button class="console-action-btn primary" ${canIssue ? '' : 'disabled'} onclick="openWorkOrderActionDialog('issue')">
          <span class="console-action-title">1. 确认生产发料</span>
          <span class="console-action-desc">仓库确认发料，扣减原料库存并生成领料流水</span>
          <span class="console-action-perm">inventory.issue.confirm (仓库)</span>
        </button>

        <button class="console-action-btn primary" ${canOperateOp ? '' : 'disabled'} onclick="openWorkOrderActionDialog('operate_op')">
          <span class="console-action-title">2. 工序执行控制</span>
          <span class="console-action-desc">开始 / 暂停 / 恢复 / 完工 OperationExecution</span>
          <span class="console-action-perm">manufacturing.execution.manage (生产)</span>
        </button>

        <button class="console-action-btn primary" ${canReport ? '' : 'disabled'} onclick="openWorkOrderActionDialog('report')">
          <span class="console-action-title">3. 提交工序报工</span>
          <span class="console-action-desc">申报合格数与不良数，受工单上限策略约束</span>
          <span class="console-action-perm">manufacturing.work-report.submit (生产)</span>
        </button>

        <button class="console-action-btn primary" ${canInspect ? '' : 'disabled'} onclick="openWorkOrderActionDialog('inspect_fg')">
          <span class="console-action-title">4. 成品质检判定</span>
          <span class="console-action-desc">检验报工数量，判定合格成品与不良处置</span>
          <span class="console-action-perm">quality.inspection.submit (质检)</span>
        </button>

        <button class="console-action-btn primary" ${canReceiveFg ? '' : 'disabled'} onclick="openWorkOrderActionDialog('receive_fg')">
          <span class="console-action-title">5. 确认成品入库</span>
          <span class="console-action-desc">仓库确认合格成品入库至 FG-A-01，增加实物库存</span>
          <span class="console-action-perm">inventory.fgr.confirm (仓库)</span>
        </button>

        <button class="console-action-btn warning" ${canReturnMat ? '' : 'disabled'} onclick="openWorkOrderActionDialog('return_mat')">
          <span class="console-action-title">生产退料确认</span>
          <span class="console-action-desc">将未使用原料退回仓库，增加指定退回库位库存</span>
          <span class="console-action-perm">inventory.return.confirm (仓库)</span>
        </button>

        <button class="console-action-btn danger" ${canManualComplete ? '' : 'disabled'} onclick="openWorkOrderActionDialog('complete')">
          <span class="console-action-title">人工完成工单</span>
          <span class="console-action-desc">终止未生产余量，已发生流水永久保留</span>
          <span class="console-action-perm">manufacturing.work-order.complete (生产)</span>
        </button>
      </div>
    </div>

    <section class="console-section">
      <div class="console-section-head">
        <h3>制造执行与库存审计时间线</h3>
        <small>领退料、工序状态变更、报工与入库事实</small>
      </div>
      <div class="console-timeline">
        ${wo.events.map(ev => `
          <div class="console-timeline-item">
            <div class="console-timeline-head">
              <strong>${ev.action}</strong>
              <span>${ev.time} · ${ev.actor}</span>
            </div>
            <div class="console-timeline-body">
              ${ev.impact}
              <div style="font-family:monospace;font-size:10px;color:var(--c-dim);margin-top:2px;">幂等键 / Key: ${ev.key} · 会话: ${ev.session}</div>
            </div>
          </div>
        `).join("")}
      </div>
    </section>
  `;
}

function openWorkOrderActionDialog(actionType) {
  const dialogEl = document.getElementById("workOrderActionDialog");
  const wo = getSelectedWorkOrder();
  if (!dialogEl || !wo) return;

  let formHtml = "";
  let dialogTitle = "";
  let impactNote = "";

  const pendingIssue = Math.max(0, wo.plannedQty - wo.materialIssuedQty);
  const pendingReceipt = Math.max(0, wo.inspectedPassedQty - wo.receivedToFgQty);

  if (actionType === "issue") {
    dialogTitle = "仓库确认生产发料";
    impactNote = "根据已锁定 BOM 从原料一仓确认发料，扣减对应物料（RM-SERVO-ST 等）库存，并生成领料流水。";
    formHtml = `
      <div class="console-form-field">
        <span>本次发料套数 <b>*</b></span>
        <input id="dlg_issue_qty" type="number" min="1" max="${pendingIssue}" value="${pendingIssue}" />
        <small>待发料套数：${pendingIssue} 套</small>
      </div>
      <div class="console-form-field">
        <span>发料出库仓库</span>
        <input type="text" readonly value="原料一仓 (WH-RM-01)" style="background:rgba(255,255,255,0.05);" />
      </div>
    `;
  } else if (actionType === "operate_op") {
    dialogTitle = "工序执行状态控制 (OperationExecution)";
    impactNote = "控制工序 30 的实际运行生命周期；暂停必须填写原因，恢复沿用同一次执行事实。";
    formHtml = `
      <div class="console-form-field">
        <span>操作目标工序</span>
        <input type="text" readonly value="工序 30 - 性能检测与标定包装 (DEV-C12)" style="background:rgba(255,255,255,0.05);" />
      </div>
      <div class="console-form-field">
        <span>执行动作选择 <b>*</b></span>
        <select id="dlg_op_action">
          <option value="complete">工序完工 (Complete)</option>
          <option value="pause">工序暂停 (Pause)</option>
          <option value="resume">工序恢复 (Resume)</option>
        </select>
      </div>
      <div class="console-form-field">
        <span>暂停原因 (仅暂停时必填)</span>
        <input id="dlg_pause_reason" type="text" placeholder="如：等待设备点检 / 刀具更换 / 现场换料" />
      </div>
    `;
  } else if (actionType === "report") {
    dialogTitle = "工序完工报工";
    impactNote = "申报工序 30 实际产出数量，报工总数 = 申报合格数 + 不良数，受工单计划总量约束。";
    const pendingReport = wo.plannedQty - (wo.reportedQualifiedQty + wo.reportedDefectiveQty);
    formHtml = `
      <div class="console-form-field">
        <span>本次申报合格数量 <b>*</b></span>
        <input id="dlg_report_qual" type="number" min="0" max="${pendingReport}" value="${Math.min(pendingReport, 20)}" />
      </div>
      <div class="console-form-field">
        <span>本次申报不良数量</span>
        <input id="dlg_report_defect" type="number" min="0" value="0" />
      </div>
      <div class="console-form-field">
        <span>不良现象说明</span>
        <input id="dlg_defect_reason" type="text" placeholder="如：绝缘阻抗不良 / 动平衡超差" />
      </div>
    `;
  } else if (actionType === "inspect_fg") {
    dialogTitle = "生产成品质检判定";
    impactNote = "对报工合格品进行最终成品质检判定，合格品进入待入库状态，不合格品报废隔离。";
    const pendingInspect = wo.reportedQualifiedQty - wo.inspectedPassedQty;
    formHtml = `
      <div class="console-form-field">
        <span>待质检判定数量</span>
        <input type="text" readonly value="${pendingInspect} ${wo.uom}" style="background:rgba(255,255,255,0.05);" />
      </div>
      <div class="console-form-field">
        <span>质检判定合格数 <b>*</b></span>
        <input id="dlg_fg_pass" type="number" min="0" max="${pendingInspect}" value="${pendingInspect}" />
      </div>
      <div class="console-form-field">
        <span>质检判定不合格数</span>
        <input id="dlg_fg_fail" type="number" min="0" value="0" />
      </div>
    `;
  } else if (actionType === "receive_fg") {
    dialogTitle = "仓库确认合格成品入库";
    impactNote = "将检验合格的成品入库至成品一仓 FG-A-01，增加企业实物库存，生成成品入库流水。";
    formHtml = `
      <div class="console-form-field">
        <span>本次入库成品数量 <b>*</b></span>
        <input id="dlg_fgr_qty" type="number" min="1" max="${pendingReceipt}" value="${pendingReceipt}" />
      </div>
      <div class="console-form-field">
        <span>入库目标库位</span>
        <input type="text" readonly value="成品一仓 (WH-FG-01) / 库位 FG-A-01" style="background:rgba(255,255,255,0.05);" />
      </div>
    `;
  } else if (actionType === "return_mat") {
    dialogTitle = "生产退料确认";
    impactNote = "将未使用原料确认退回仓库，增加指定退回库位库存，生成生产退料流水。";
    formHtml = `
      <div class="console-form-field">
        <span>退料物料与数量 <b>*</b></span>
        <input id="dlg_return_qty" type="number" min="1" max="${wo.materialIssuedQty}" value="2" />
        <small>已领料量：${wo.materialIssuedQty} 套</small>
      </div>
      <div class="console-form-field">
        <span>退料原因 <b>*</b></span>
        <input id="dlg_mat_ret_reason" type="text" value="工单批次结余原料，退回原料仓" />
      </div>
    `;
  } else if (actionType === "complete") {
    dialogTitle = "人工完成生产工单";
    impactNote = "终止尚未生产的剩余工单数量；已领料、已报工和已入库历史全部永久保留。";
    formHtml = `
      <div class="console-form-field">
        <span>人工完成原因 <b>*</b></span>
        <textarea id="dlg_wo_comp_reason" rows="3" placeholder="请录入终止剩余生产的原因..."></textarea>
      </div>
    `;
  }

  dialogEl.innerHTML = `
    <div class="console-dialog-backdrop" onclick="closeWorkOrderActionDialog()"></div>
    <div class="console-dialog-panel">
      <header>
        <h2>${dialogTitle}</h2>
        <button type="button" class="console-dialog-close" onclick="closeWorkOrderActionDialog()">&times;</button>
      </header>
      <div class="console-dialog-context">
        <span>工单单号</span><strong>${wo.id}</strong>
        <span>生产产品</span><strong>${wo.product} (${wo.sku})</strong>
      </div>
      <form onsubmit="handleWorkOrderActionSubmit(event, '${actionType}')">
        ${formHtml}
        <div class="console-dialog-impact">
          <span>业务与库存影响提示</span>
          <p>${impactNote}</p>
        </div>
        <p id="dlg_wo_error" class="console-form-error"></p>
        <footer>
          <button type="button" onclick="closeWorkOrderActionDialog()">取消</button>
          <button type="submit" class="primary">确认提交</button>
        </footer>
      </form>
    </div>
  `;
  dialogEl.hidden = false;
}

function closeWorkOrderActionDialog() {
  const dialogEl = document.getElementById("workOrderActionDialog");
  if (dialogEl) dialogEl.hidden = true;
}

function handleWorkOrderActionSubmit(event, actionType) {
  event.preventDefault();
  const wo = getSelectedWorkOrder();
  const errEl = document.getElementById("dlg_wo_error");

  const now = new Date();
  const timeStr = `${String(now.getMonth()+1).padStart(2,'0')}-${String(now.getDate()).padStart(2,'0')} ${String(now.getHours()).padStart(2,'0')}:${String(now.getMinutes()).padStart(2,'0')}`;
  const key = `MES-CMD-${Date.now().toString().slice(-6)}`;

  if (actionType === "issue") {
    const qty = parseInt(document.getElementById("dlg_issue_qty")?.value, 10) || 0;
    wo.materialIssuedQty += qty;
    wo.status = "InProgress";

    wo.events.unshift({
      time: timeStr,
      action: "生产领料确认",
      actor: "wh.operator",
      session: "jti…7c91",
      key,
      impact: `领用原料 ${qty} 套；原料一仓实物库存扣减并生成领料流水`
    });

    showWorkOrderToast(`生产发料确认成功：${qty} 套原料已出库交付生产`);
  } else if (actionType === "operate_op") {
    const opAction = document.getElementById("dlg_op_action")?.value;
    const pauseReason = document.getElementById("dlg_pause_reason")?.value.trim();
    const op30 = wo.operations.find(o => o.seq === 30);

    if (opAction === "pause") {
      if (!pauseReason) { errEl.textContent = "暂停工序必须填写原因"; return; }
      op30.status = "Paused";
      op30.executionStatus = "Paused";
      wo.events.unshift({
        time: timeStr,
        action: "工序 30 暂停执行",
        actor: "mes.inspector",
        session: "jti…310a",
        key,
        impact: `工序 30 暂停执行；原因：${pauseReason}`
      });
      showWorkOrderToast("工序 30 已暂停");
    } else if (opAction === "resume") {
      op30.status = "Running";
      op30.executionStatus = "Running";
      wo.events.unshift({
        time: timeStr,
        action: "工序 30 恢复执行",
        actor: "mes.inspector",
        session: "jti…310a",
        key,
        impact: "工序 30 恢复执行；沿用原 OE 记录"
      });
      showWorkOrderToast("工序 30 已恢复执行");
    } else if (opAction === "complete") {
      op30.status = "Completed";
      op30.executionStatus = "Completed";
      op30.completedQty = wo.plannedQty;
      wo.events.unshift({
        time: timeStr,
        action: "工序 30 全部完工",
        actor: "mes.inspector",
        session: "jti…310a",
        key,
        impact: `工序 30 完工；OE 状态进入 Completed`
      });
      showWorkOrderToast("工序 30 已全部完工");
    }
  } else if (actionType === "report") {
    const qual = parseInt(document.getElementById("dlg_report_qual")?.value, 10) || 0;
    const defect = parseInt(document.getElementById("dlg_report_defect")?.value, 10) || 0;

    wo.reportedQualifiedQty += qual;
    wo.reportedDefectiveQty += defect;
    wo.inspectedPassedQty += qual;
    wo.inspectedFailedQty += defect;

    wo.events.unshift({
      time: timeStr,
      action: "工序报工与成品质检",
      actor: "mes.inspector",
      session: "jti…310a",
      key,
      impact: `申报产出 ${qual + defect} 台（合格 ${qual} 台，不良 ${defect} 台）；质检合格 ${qual} 台待入库`
    });

    showWorkOrderToast(`报工与质检成功：合格 ${qual} 台待入库`);
  } else if (actionType === "receive_fg") {
    const qty = parseInt(document.getElementById("dlg_fgr_qty")?.value, 10) || 0;
    wo.receivedToFgQty += qty;

    if (wo.receivedToFgQty >= wo.plannedQty) {
      wo.status = "Completed";
      wo.completionType = "Normal";
    }

    wo.events.unshift({
      time: timeStr,
      action: "成品入库确认",
      actor: "wh.operator",
      session: "jti…7c91",
      key,
      impact: `成品 ${qty} 台入库至 FG-A-01；实物库存增加并生成 FGR 流水`
    });

    showWorkOrderToast(`成品入库成功：${qty} 台已进入成品一仓`);
  } else if (actionType === "return_mat") {
    const qty = parseInt(document.getElementById("dlg_return_qty")?.value, 10) || 0;
    wo.materialReturnedQty += qty;

    wo.events.unshift({
      time: timeStr,
      action: "生产退料确认",
      actor: "wh.operator",
      session: "jti…7c91",
      key,
      impact: `退回原料 ${qty} 套至原料仓退料库位；库存增加并生成退料流水`
    });

    showWorkOrderToast(`退料确认成功：${qty} 套已退回仓库`);
  } else if (actionType === "complete") {
    const reason = document.getElementById("dlg_wo_comp_reason")?.value.trim();
    if (!reason) { errEl.textContent = "人工完成必须填写原因"; return; }

    wo.status = "Completed";
    wo.completionType = "Manual";
    wo.completionReason = reason;

    wo.events.unshift({
      time: timeStr,
      action: "工单人工完成",
      actor: "mes.inspector",
      session: "jti…310a",
      key,
      impact: `人工终止剩余生产数量；原因：${reason}`
    });

    showWorkOrderToast("生产工单已人工完成");
  }

  closeWorkOrderActionDialog();
  renderWorkOrderQueue();
  renderWorkOrderDetail();
}

function initWorkOrderConsole() {
  const roleSelect = document.getElementById("workOrderRole");
  roleSelect?.addEventListener("change", (e) => {
    currentWorkOrderRole = e.target.value;
    renderWorkOrderDetail();
  });

  const scenarioTabs = document.querySelectorAll(".console-scenario-tabs button");
  scenarioTabs.forEach(tab => {
    tab.addEventListener("click", () => {
      scenarioTabs.forEach(t => {
        t.classList.remove("is-active");
        t.setAttribute("aria-pressed", "false");
      });
      tab.classList.add("is-active");
      tab.setAttribute("aria-pressed", "true");

      currentWorkOrderScenario = tab.dataset.scenario;
      currentWorkOrders = JSON.parse(JSON.stringify(workOrderScenarios[currentWorkOrderScenario].orders));
      selectedWorkOrderId = workOrderScenarios[currentWorkOrderScenario].featuredOrderId;
      renderWorkOrderQueue();
      renderWorkOrderDetail();
    });
  });

  const filterBtns = document.querySelectorAll(".console-filter-row button");
  filterBtns.forEach(btn => {
    btn.addEventListener("click", () => {
      filterBtns.forEach(b => {
        b.classList.remove("is-active");
        b.setAttribute("aria-pressed", "false");
      });
      btn.classList.add("is-active");
      btn.setAttribute("aria-pressed", "true");
      workOrderStatusFilter = btn.dataset.statusFilter;
      renderWorkOrderQueue();
    });
  });

  const searchInput = document.getElementById("workOrderSearch");
  searchInput?.addEventListener("input", (e) => {
    workOrderSearchQuery = e.target.value.trim();
    renderWorkOrderQueue();
  });

  renderWorkOrderQueue();
  renderWorkOrderDetail();
}

document.addEventListener("DOMContentLoaded", initWorkOrderConsole);
