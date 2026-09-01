/**
 * 采购收货与到货质检高保真交互控制台 (purchase-inbound.js)
 * 严格执行已冻结业务规则：
 * 1. 仓库人员到货外观验收：arrived_qty = rejected_qty + received_qty；
 * 2. 拒收数量不入库、不计入累计已收货并继续作为待收数量；
 * 3. 实际接收数量全部进入 QualityHold（质量隔离位）；
 * 4. 生产质检人员执行采购到货质检：inspected_qty = qualified_qty + unqualified_qty；
 * 5. 质量处置：生产质检决定放行/报废，采购人员决定退回供应方，仓库人员确认实物移位/扣减；
 * 6. 放行执行移入 ReceivingStaging（收货暂存位），上架确认后移至 Storage（存储位）；
 * 7. 人工完成仅终止未收货余量，已收货货物继续按质检上架流程流转。
 */

const purchaseActionDefinitions = {
  submit: { label: "提交采购单", permission: "purchase.order.submit", role: "buyer", tone: "primary", group: "normal" },
  approve: { label: "审核采购单", permission: "purchase.order.approve", role: "buyer", tone: "primary", group: "normal" },
  receive: { label: "外观验收与接收", permission: "inventory.receipt.confirm", role: "warehouse", tone: "primary", group: "normal" },
  inspect: { label: "采购到货质检", permission: "quality.purchase-inspection.submit", role: "inspector", tone: "primary", group: "normal" },
  release_decide: { label: "决定合格放行", permission: "quality.purchase-disposition.decide", role: "inspector", tone: "primary", group: "normal" },
  scrap_decide: { label: "决定不合格报废", permission: "quality.purchase-disposition.decide", role: "inspector", tone: "warning", group: "normal" },
  return_decide: { label: "决定退回供应方", permission: "quality.purchase-disposition.return", role: "buyer", tone: "warning", group: "normal" },
  disposition_execute: { label: "确认处置执行", permission: "inventory.quality-disposition.confirm", role: "warehouse", tone: "primary", group: "normal" },
  putaway: { label: "确认上架存储", permission: "inventory.putaway.confirm", role: "warehouse", tone: "primary", group: "normal" },
  complete: { label: "人工完成采购单", permission: "purchase.order.complete", role: "buyer", tone: "danger", group: "exception" }
};

const roleLabelsForPurchase = {
  warehouse: "仓库人员 (wh.operator)",
  inspector: "生产质检人员 (qa.inspector)",
  buyer: "采购人员 (buyer.chen)"
};

const statusLabelsPurchase = {
  Draft: "未提交",
  Submitted: "已提交",
  Approved: "已审核",
  PartiallyReceived: "部分收货",
  Completed: "已完成"
};

const purchaseScenarios = {
  partial: {
    featuredOrderId: "PO-20260826-001",
    orders: [
      {
        id: "PO-20260826-001",
        supplier: "华东精密机电制造有限公司",
        supplierCode: "SUP-HD-001",
        owner: "陈采购",
        sourceWorkOrderId: "WO-20260826-018",
        plannedArrivalDate: "2026-08-27",
        status: "PartiallyReceived",
        completionType: null,
        completionReason: null,
        priority: "紧急",
        warehouse: "原料一仓",
        qualityHoldLocation: "QH-01",
        receivingStagingLocation: "RS-01",
        storageLocation: "ST-A-01",
        lines: [
          {
            id: "PL10",
            sku: "RM-SERVO-ST",
            product: "定子转子组件",
            uom: "件",
            orderedQty: 80,
            arrivedQty: 80,
            rejectedQty: 5,
            rejectionReason: "外包装变形破损，批号标签模糊，现场拒收",
            receivedQty: 75,
            inspectedQty: 75,
            qualifiedQty: 70,
            unqualifiedQty: 5,
            unqualifiedReason: "轴向尺寸公差超差 0.15mm",
            releaseDecidedQty: 70,
            scrapDecidedQty: 5,
            returnDecidedQty: 0,
            releaseExecutedQty: 70,
            scrapExecutedQty: 5,
            returnExecutedQty: 0,
            putawayQty: 0
          }
        ],
        events: [
          { time: "08-26 16:30", action: "质量放行与报废执行确认", actor: "wh.operator", session: "jti…7c91", key: "DISP-20260826-01", impact: "70 件合格品从 QH-01 移至 RS-01 待上架；5 件报废从 QH-01 扣减实物并生成报废流水" },
          { time: "08-26 15:40", action: "质量处置决定", actor: "qa.inspector", session: "jti…310a", key: "DEC-20260826-04", impact: "70 件合格品决定放行；5 件尺寸超差决定报废" },
          { time: "08-26 14:50", action: "采购到货质检", actor: "qa.inspector", session: "jti…310a", key: "QA-20260826-02", impact: "检验 75 件：合格 70 件，不合格 5 件（尺寸超差）" },
          { time: "08-26 14:15", action: "仓库外观验收与实际接收", actor: "wh.operator", session: "jti…7c91", key: "RCV-20260826-01", impact: "到货 80 件：拒收 5 件（破损），实收 75 件入 QH-01（质量隔离位）；实物库存+75" },
          { time: "08-26 10:20", action: "采购订单审核", actor: "buyer.chen", session: "jti…552d", key: "APR-20260826-01", impact: "采购单审核通过，已审核待收货" }
        ]
      },
      {
        id: "PO-20260826-002",
        supplier: "精密轴承制造中心",
        supplierCode: "SUP-NB-008",
        owner: "李采购",
        sourceWorkOrderId: "WO-20260826-018",
        plannedArrivalDate: "2026-08-28",
        status: "Approved",
        completionType: null,
        completionReason: null,
        priority: "标准",
        warehouse: "原料一仓",
        qualityHoldLocation: "QH-01",
        receivingStagingLocation: "RS-01",
        storageLocation: "ST-B-02",
        lines: [
          {
            id: "PL10",
            sku: "RM-BEARING-01",
            product: "高精轴承组件",
            uom: "件",
            orderedQty: 160,
            arrivedQty: 0,
            rejectedQty: 0,
            rejectionReason: null,
            receivedQty: 0,
            inspectedQty: 0,
            qualifiedQty: 0,
            unqualifiedQty: 0,
            unqualifiedReason: null,
            releaseDecidedQty: 0,
            scrapDecidedQty: 0,
            returnDecidedQty: 0,
            releaseExecutedQty: 0,
            scrapExecutedQty: 0,
            returnExecutedQty: 0,
            putawayQty: 0
          }
        ],
        events: [
          { time: "08-26 11:00", action: "采购订单审核", actor: "buyer.chen", session: "jti…552d", key: "APR-20260826-02", impact: "采购单审核通过，等待供方送达" }
        ]
      }
    ]
  },
  manual: {
    featuredOrderId: "PO-20260825-004",
    orders: [
      {
        id: "PO-20260825-004",
        supplier: "苏州传感器工业科技",
        supplierCode: "SUP-SZ-012",
        owner: "陈采购",
        sourceWorkOrderId: "WO-20260825-009",
        plannedArrivalDate: "2026-08-25",
        status: "Completed",
        completionType: "Manual",
        completionReason: "下游产品方案优化，双方协商终止剩余 60 件传感器供货。",
        completedAt: "2026-08-26 10:15",
        completedBy: "buyer.chen",
        completedSession: "jti…552d",
        priority: "关注",
        warehouse: "原料一仓",
        qualityHoldLocation: "QH-01",
        receivingStagingLocation: "RS-01",
        storageLocation: "ST-C-01",
        lines: [
          {
            id: "PL10",
            sku: "RM-ENCODER-01",
            product: "高分辨率编码器",
            uom: "套",
            orderedQty: 100,
            arrivedQty: 40,
            rejectedQty: 0,
            rejectionReason: null,
            receivedQty: 40,
            inspectedQty: 40,
            qualifiedQty: 40,
            unqualifiedQty: 0,
            unqualifiedReason: null,
            releaseDecidedQty: 40,
            scrapDecidedQty: 0,
            returnDecidedQty: 0,
            releaseExecutedQty: 40,
            scrapExecutedQty: 0,
            returnExecutedQty: 0,
            putawayQty: 40
          }
        ],
        events: [
          { time: "08-26 10:15", action: "人工完成采购单", actor: "buyer.chen", session: "jti…552d", key: "COMP-20260826-09", impact: "终止剩余 60 件待收货余量；已入库 40 件不受影响" },
          { time: "08-25 17:20", action: "上架确认", actor: "wh.operator", session: "jti…7c91", key: "PUT-20260825-04", impact: "40 套从 RS-01 上架至 ST-C-01" },
          { time: "08-25 15:10", action: "外观验收与到货质检放行", actor: "wh.operator", session: "jti…7c91", key: "RCV-20260825-03", impact: "实收 40 套全部合格并完成放行执行" }
        ]
      }
    ]
  },
  normal: {
    featuredOrderId: "PO-20260824-002",
    orders: [
      {
        id: "PO-20260824-002",
        supplier: "精密轴承制造中心",
        supplierCode: "SUP-NB-008",
        owner: "李采购",
        sourceWorkOrderId: "WO-20260824-001",
        plannedArrivalDate: "2026-08-24",
        status: "Completed",
        completionType: "Normal",
        completionReason: null,
        completedAt: "2026-08-24 16:40",
        completedBy: "wh.operator",
        completedSession: "jti…7c91",
        priority: "标准",
        warehouse: "原料一仓",
        qualityHoldLocation: "QH-01",
        receivingStagingLocation: "RS-01",
        storageLocation: "ST-B-02",
        lines: [
          {
            id: "PL10",
            sku: "RM-BEARING-01",
            product: "高精轴承组件",
            uom: "件",
            orderedQty: 50,
            arrivedQty: 50,
            rejectedQty: 0,
            rejectionReason: null,
            receivedQty: 50,
            inspectedQty: 50,
            qualifiedQty: 50,
            unqualifiedQty: 0,
            unqualifiedReason: null,
            releaseDecidedQty: 50,
            scrapDecidedQty: 0,
            returnDecidedQty: 0,
            releaseExecutedQty: 50,
            scrapExecutedQty: 0,
            returnExecutedQty: 0,
            putawayQty: 50
          }
        ],
        events: [
          { time: "08-24 16:40", action: "全量上架并正常完成", actor: "wh.operator", session: "jti…7c91", key: "PUT-20260824-01", impact: "50 件全部上架至 ST-B-02；Completed / Normal" }
        ]
      }
    ]
  }
};

let currentPurchaseScenario = "partial";
let currentPurchaseOrders = JSON.parse(JSON.stringify(purchaseScenarios.partial.orders));
let currentPurchaseRole = "warehouse";
let selectedPurchaseOrderId = "PO-20260826-001";
let purchaseStatusFilter = "all";
let purchaseSearchQuery = "";

function getSelectedPurchaseOrder() {
  return currentPurchaseOrders.find(o => o.id === selectedPurchaseOrderId) || currentPurchaseOrders[0];
}

function showPurchaseToast(message, type = "success") {
  const toast = document.getElementById("purchaseToast");
  if (!toast) return;
  toast.textContent = message;
  toast.className = `console-toast is-visible is-${type}`;
  setTimeout(() => { toast.classList.remove("is-visible"); }, 3600);
}

function renderPurchaseQueue() {
  const listEl = document.getElementById("purchaseOrderList");
  const countEl = document.getElementById("purchaseOrderCount");
  if (!listEl || !countEl) return;

  const filtered = currentPurchaseOrders.filter(order => {
    if (purchaseStatusFilter !== "all" && order.status !== purchaseStatusFilter) return false;
    if (purchaseSearchQuery) {
      const q = purchaseSearchQuery.toLowerCase();
      const matchId = order.id.toLowerCase().includes(q);
      const matchSup = order.supplier.toLowerCase().includes(q);
      const matchSku = order.lines.some(l => l.sku.toLowerCase().includes(q) || l.product.toLowerCase().includes(q));
      if (!matchId && !matchSup && !matchSku) return false;
    }
    return true;
  });

  countEl.textContent = filtered.length;

  if (filtered.length === 0) {
    listEl.innerHTML = '<div style="padding:20px;text-align:center;color:var(--c-muted);font-size:12px;">无匹配采购单据</div>';
    return;
  }

  listEl.innerHTML = filtered.map(order => {
    const isSelected = order.id === selectedPurchaseOrderId ? "is-active" : "";
    const line = order.lines[0] || {};
    const unreceived = line.orderedQty - line.receivedQty;
    const accentClass = order.status === "Draft" ? "draft" : order.status === "Approved" ? "approved" : order.status === "PartiallyReceived" ? "partial" : "completed";

    return `
      <div class="console-card ${isSelected}" tabindex="0" role="button" aria-label="采购订单 ${order.id}" onclick="selectPurchaseOrder('${order.id}')" onkeydown="if(event.key==='Enter'||event.key===' '){event.preventDefault();selectPurchaseOrder('${order.id}');}">
        <span class="console-card-accent ${accentClass}"></span>
        <div class="console-card-top">
          <strong>${order.id}</strong>
          <em class="${order.priority === '紧急' ? 'urgent' : ''}">${order.priority}</em>
        </div>
        <div class="console-card-title">${order.supplier}</div>
        <div style="font-size:11px;color:var(--c-cyan);">${line.product || ''} · ${line.orderedQty || 0} ${line.uom || '件'}</div>
        <div class="console-card-foot">
          <span>待收: ${Math.max(0, unreceived)}</span>
          <span class="console-badge ${accentClass}">${statusLabelsPurchase[order.status] || order.status}</span>
        </div>
      </div>
    `;
  }).join("");
}

function selectPurchaseOrder(orderId) {
  selectedPurchaseOrderId = orderId;
  renderPurchaseQueue();
  renderPurchaseDetail();
}

function renderPurchaseDetail() {
  const detailEl = document.getElementById("purchaseOrderDetail");
  const order = getSelectedPurchaseOrder();
  if (!detailEl || !order) return;

  const line = order.lines[0] || {};
  const pendingArrival = Math.max(0, line.orderedQty - line.receivedQty);
  const inQualityHold = Math.max(0, line.receivedQty - line.releaseExecutedQty - line.scrapExecutedQty - line.returnExecutedQty);
  const inStaging = Math.max(0, line.releaseExecutedQty - line.putawayQty);
  const inStorage = line.putawayQty;

  const canSubmit = order.status === "Draft" && currentPurchaseRole === "buyer";
  const canApprove = order.status === "Submitted" && currentPurchaseRole === "buyer";
  const canReceive = (order.status === "Approved" || order.status === "PartiallyReceived") && pendingArrival > 0 && currentPurchaseRole === "warehouse";
  const canInspect = inQualityHold > 0 && (line.inspectedQty < line.receivedQty) && currentPurchaseRole === "inspector";
  const canDecideRelease = (line.qualifiedQty > line.releaseDecidedQty) && currentPurchaseRole === "inspector";
  const canDecideScrap = (line.unqualifiedQty > (line.scrapDecidedQty + line.returnDecidedQty)) && currentPurchaseRole === "inspector";
  const canDecideReturn = (line.unqualifiedQty > (line.scrapDecidedQty + line.returnDecidedQty)) && currentPurchaseRole === "buyer";
  const canExecDisp = ((line.releaseDecidedQty > line.releaseExecutedQty) || (line.scrapDecidedQty > line.scrapExecutedQty) || (line.returnDecidedQty > line.returnExecutedQty)) && currentPurchaseRole === "warehouse";
  const canPutaway = inStaging > 0 && currentPurchaseRole === "warehouse";
  const canManualComplete = (order.status === "Approved" || order.status === "PartiallyReceived") && pendingArrival > 0 && currentPurchaseRole === "buyer";

  detailEl.innerHTML = `
    <header class="console-detail-head">
      <div>
        <div class="console-title-meta">
          <span class="console-module-code">PURCHASE ORDER</span>
          <span class="console-badge ${order.status === 'Completed' ? 'green' : 'amber'}">${statusLabelsPurchase[order.status] || order.status}</span>
        </div>
        <h2>${order.id}</h2>
        <p>供应商：${order.supplier} (${order.supplierCode}) · 计划到货：${order.plannedArrivalDate}</p>
      </div>
      <div class="console-detail-status">
        <span class="console-badge cyan">来源工单: ${order.sourceWorkOrderId || '无'}</span>
        <small>负责采购: ${order.owner}</small>
      </div>
    </header>

    <div class="console-state-explainer">
      <div>
        <span>生命周期状态</span>
        <strong>${statusLabelsPurchase[order.status] || order.status}</strong>
        <small>${order.completionType ? `完成方式: ${order.completionType === 'Manual' ? '人工完成' : '正常完成'}` : '流转中'}</small>
      </div>
      <div class="console-state-divider">→</div>
      <div>
        <span>仓储执行事实</span>
        <strong>待收 ${pendingArrival} / 隔离 ${inQualityHold}</strong>
        <small>暂存 ${inStaging} / 已上架 ${inStorage}</small>
      </div>
      <p>
        <strong>收货质检守恒公式：</strong><br/>
        外观验收：<code>arrived_qty = rejected_qty + received_qty</code><br/>
        到货质检：<code>inspected_qty = qualified_qty + unqualified_qty</code>
      </p>
    </div>

    ${order.completionReason ? `
      <div style="margin:14px 24px 0;padding:12px 16px;border-left:3px solid var(--c-amber);background:rgba(243,180,93,0.08);border-radius:4px;font-size:12px;">
        <strong style="color:var(--c-amber);">人工完成记录：</strong> ${order.completionReason} (操作人: ${order.completedBy}, 时间: ${order.completedAt})
      </div>
    ` : ''}

    <div class="console-kpi-strip">
      <div class="console-kpi-item">
        <span>采购总计划</span>
        <strong>${line.orderedQty} <small>${line.uom}</small></strong>
        <small>合同下单量</small>
      </div>
      <div class="console-kpi-item highlight-amber">
        <span>外观拒收(待补)</span>
        <strong>${line.rejectedQty} <small>${line.uom}</small></strong>
        <small>不入库/仍待收</small>
      </div>
      <div class="console-kpi-item highlight-cyan">
        <span>实收进隔离位</span>
        <strong>${line.receivedQty} <small>${line.uom}</small></strong>
        <small>QualityHold (QH-01)</small>
      </div>
      <div class="console-kpi-item highlight-green">
        <span>质检合格放行</span>
        <strong>${line.releaseExecutedQty} <small>${line.uom}</small></strong>
        <small>已移至 RS-01 暂存</small>
      </div>
      <div class="console-kpi-item highlight-green">
        <span>已完成上架</span>
        <strong>${line.putawayQty} <small>${line.uom}</small></strong>
        <small>存储位 ${order.storageLocation}</small>
      </div>
    </div>

    <section class="console-section">
      <div class="console-section-head">
        <h3>采购明细与质检上架流水看板</h3>
        <small>SKU: ${line.sku} · ${line.product}</small>
      </div>
      <div class="console-table-shell">
        <table class="console-table">
          <thead>
            <tr>
              <th>物料SKU</th>
              <th>物料名称</th>
              <th>计划量</th>
              <th>外观验收(拒收/实收)</th>
              <th>到货质检(合格/不良)</th>
              <th>处置执行(放行/报废/退回)</th>
              <th>上架数量</th>
              <th>当前库位分布</th>
            </tr>
          </thead>
          <tbody>
            <tr>
              <td><code>${line.sku}</code></td>
              <td><strong>${line.product}</strong></td>
              <td>${line.orderedQty} ${line.uom}</td>
              <td>
                <span style="color:var(--c-red);">${line.rejectedQty} 拒收</span> /
                <span style="color:var(--c-cyan);">${line.receivedQty} 实收</span>
              </td>
              <td>
                <span style="color:var(--c-green);">${line.qualifiedQty} 合格</span> /
                <span style="color:var(--c-red);">${line.unqualifiedQty} 不合格</span>
              </td>
              <td>
                <span style="color:var(--c-green);">${line.releaseExecutedQty} 放行</span> /
                <span style="color:var(--c-red);">${line.scrapExecutedQty} 报废</span> /
                <span style="color:var(--c-amber);">${line.returnExecutedQty} 退回</span>
              </td>
              <td><strong>${line.putawayQty} ${line.uom}</strong></td>
              <td>
                <div style="font-size:11px;line-height:1.5;">
                  QH-01 (隔离): ${inQualityHold}<br/>
                  RS-01 (暂存): ${inStaging}<br/>
                  ${order.storageLocation} (存储): ${inStorage}
                </div>
              </td>
            </tr>
          </tbody>
        </table>
      </div>
      ${line.rejectionReason ? `
        <div style="padding:10px 18px;font-size:11px;color:var(--c-muted);border-top:1px solid var(--c-line);">
          <strong style="color:var(--c-red);">拒收原因记录：</strong>${line.rejectionReason}
        </div>
      ` : ''}
    </section>

    <div class="console-action-panel">
      <div class="console-action-group-head">
        <strong>可执行业务动作</strong>
        <span>当前身份：${roleLabelsForPurchase[currentPurchaseRole]}</span>
      </div>

      <div class="console-action-buttons">
        <button class="console-action-btn primary" ${canReceive ? '' : 'disabled'} onclick="openPurchaseActionDialog('receive')">
          <span class="console-action-title">1. 外观验收与接收</span>
          <span class="console-action-desc">外观检查破损拒收，实收全部进入质量隔离位</span>
          <span class="console-action-perm">inventory.receipt.confirm (仓库)</span>
        </button>

        <button class="console-action-btn primary" ${canInspect ? '' : 'disabled'} onclick="openPurchaseActionDialog('inspect')">
          <span class="console-action-title">2. 采购到货质检</span>
          <span class="console-action-desc">生产质检人员检验，判定合格与不合格数量</span>
          <span class="console-action-perm">quality.purchase-inspection.submit (质检)</span>
        </button>

        <button class="console-action-btn primary" ${canDecideRelease ? '' : 'disabled'} onclick="openPurchaseActionDialog('release_decide')">
          <span class="console-action-title">3. 决定合格放行</span>
          <span class="console-action-desc">质检决定合格品放行，生成待移位任务</span>
          <span class="console-action-perm">quality.purchase-disposition.decide (质检)</span>
        </button>

        <button class="console-action-btn warning" ${canDecideScrap ? '' : 'disabled'} onclick="openPurchaseActionDialog('scrap_decide')">
          <span class="console-action-title">3. 决定不合格报废</span>
          <span class="console-action-desc">质检决定不合格品报废，生成扣减任务</span>
          <span class="console-action-perm">quality.purchase-disposition.decide (质检)</span>
        </button>

        <button class="console-action-btn warning" ${canDecideReturn ? '' : 'disabled'} onclick="openPurchaseActionDialog('return_decide')">
          <span class="console-action-title">3. 决定退回供应方</span>
          <span class="console-action-desc">采购人员决定退回供应商，生成出库任务</span>
          <span class="console-action-perm">quality.purchase-disposition.return (采购)</span>
        </button>

        <button class="console-action-btn primary" ${canExecDisp ? '' : 'disabled'} onclick="openPurchaseActionDialog('disposition_execute')">
          <span class="console-action-title">4. 确认处置执行</span>
          <span class="console-action-desc">仓库确认放行移至RS-01，或报废/退回扣减记流水</span>
          <span class="console-action-perm">inventory.quality-disposition.confirm (仓库)</span>
        </button>

        <button class="console-action-btn primary" ${canPutaway ? '' : 'disabled'} onclick="openPurchaseActionDialog('putaway')">
          <span class="console-action-title">5. 确认上架入库</span>
          <span class="console-action-desc">将暂存位货物移至最终存储位，总库存不变</span>
          <span class="console-action-perm">inventory.putaway.confirm (仓库)</span>
        </button>

        <button class="console-action-btn danger" ${canManualComplete ? '' : 'disabled'} onclick="openPurchaseActionDialog('complete')">
          <span class="console-action-title">人工完成采购单</span>
          <span class="console-action-desc">终止未到货余量，已入库货物继续流转</span>
          <span class="console-action-perm">purchase.order.complete (采购)</span>
        </button>
      </div>
    </div>

    <section class="console-section">
      <div class="console-section-head">
        <h3>操作与审计时间线</h3>
        <small>不可篡改的业务与库存事实记录</small>
      </div>
      <div class="console-timeline">
        ${order.events.map(ev => `
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

function openPurchaseActionDialog(actionType) {
  const dialogEl = document.getElementById("purchaseActionDialog");
  const order = getSelectedPurchaseOrder();
  if (!dialogEl || !order) return;

  const line = order.lines[0] || {};
  const pendingArrival = Math.max(0, line.orderedQty - line.receivedQty);
  const inQualityHold = Math.max(0, line.receivedQty - line.releaseExecutedQty - line.scrapExecutedQty - line.returnExecutedQty);
  const inStaging = Math.max(0, line.releaseExecutedQty - line.putawayQty);

  let formHtml = "";
  let dialogTitle = "";
  let impactNote = "";

  if (actionType === "receive") {
    dialogTitle = "仓库外观验收与实际接收";
    impactNote = "外观破损或型号错误在接收前拒收；拒收不入库且仍为待收；实际接收数量全部增加实物库存并进入 QualityHold（质量隔离位）。";
    formHtml = `
      <div class="console-form-field">
        <span>到货验收总数 (arrived_qty) <b>*</b></span>
        <input id="dlg_arrived_qty" type="number" min="1" max="${pendingArrival}" value="${pendingArrival}" oninput="calcReceiveSplit()" />
        <small>不得超过待收数量：${pendingArrival} ${line.uom}</small>
      </div>
      <div class="console-form-field">
        <span>收货前拒收数量 (rejected_qty)</span>
        <input id="dlg_rejected_qty" type="number" min="0" max="${pendingArrival}" value="0" oninput="calcReceiveSplit()" />
        <small>包装破损、型号错误等拒绝接收的数量</small>
      </div>
      <div class="console-form-field">
        <span>拒收原因 (拒收 > 0 时必填)</span>
        <input id="dlg_rejection_reason" type="text" placeholder="如：外包装变形破损 / 标签模糊 / 规格不符" />
      </div>
      <div class="console-form-field">
        <span>实际接收数量 (received_qty)</span>
        <input id="dlg_received_qty" type="number" readonly value="${pendingArrival}" style="background:rgba(255,255,255,0.05);" />
        <small style="color:var(--c-cyan);">计算公式：received_qty = arrived_qty - rejected_qty（全部进 QH-01 隔离位）</small>
      </div>
    `;
  } else if (actionType === "inspect") {
    dialogTitle = "生产质检人员采购到货质检";
    impactNote = "对质量隔离位中的货物进行检验；记录合格与不合格数量，只生成质检结论，不改变库存余额。";
    const uninspectedQty = Math.max(0, line.receivedQty - line.inspectedQty);
    formHtml = `
      <div class="console-form-field">
        <span>本次检验总数 (inspected_qty) <b>*</b></span>
        <input id="dlg_inspected_qty" type="number" min="1" max="${uninspectedQty}" value="${uninspectedQty}" oninput="calcInspectSplit()" />
        <small>待检验最大数量：${uninspectedQty} ${line.uom}（实际接收 ${line.receivedQty} - 累计已检 ${line.inspectedQty}）</small>
      </div>
      <div class="console-form-field">
        <span>质检合格数量 (qualified_qty) <b>*</b></span>
        <input id="dlg_qualified_qty" type="number" min="0" max="${uninspectedQty}" value="${uninspectedQty}" oninput="calcInspectSplit()" />
      </div>
      <div class="console-form-field">
        <span>质检不合格数量 (unqualified_qty)</span>
        <input id="dlg_unqualified_qty" type="number" readonly value="0" style="background:rgba(255,255,255,0.05);" />
      </div>
      <div class="console-form-field">
        <span>不合格原因 / 检验备注</span>
        <input id="dlg_inspect_reason" type="text" placeholder="如：尺寸超差 / 绝缘阻抗不良 / 外观擦伤" />
      </div>
    `;
  } else if (actionType === "release_decide") {
    dialogTitle = "质量处置：合格品放行决定";
    impactNote = "生产质检人员对合格品作出放行决定，状态进入待执行，等待仓库人员移至收货暂存位。";
    const pendingReleaseDecide = line.qualifiedQty - line.releaseDecidedQty;
    formHtml = `
      <div class="console-form-field">
        <span>决定放行数量 <b>*</b></span>
        <input id="dlg_release_decide_qty" type="number" min="1" max="${pendingReleaseDecide}" value="${pendingReleaseDecide}" />
        <small>未处置合格数量：${pendingReleaseDecide} ${line.uom}</small>
      </div>
    `;
  } else if (actionType === "scrap_decide") {
    dialogTitle = "质量处置：不合格品报废决定";
    impactNote = "生产质检人员对不合格品作出报废决定，状态进入待执行，后续由仓库人员扣减实物并生成报废流水。";
    const pendingScrapDecide = line.unqualifiedQty - (line.scrapDecidedQty + line.returnDecidedQty);
    formHtml = `
      <div class="console-form-field">
        <span>决定报废数量 <b>*</b></span>
        <input id="dlg_scrap_decide_qty" type="number" min="1" max="${pendingScrapDecide}" value="${pendingScrapDecide}" />
        <small>未处置不合格数量：${pendingScrapDecide} ${line.uom}</small>
      </div>
      <div class="console-form-field">
        <span>报废处置原因 <b>*</b></span>
        <input id="dlg_scrap_reason" type="text" value="尺寸超差严重，无法修复使用，按报废处置" />
      </div>
    `;
  } else if (actionType === "return_decide") {
    dialogTitle = "质量处置：退回供应方决定";
    impactNote = "采购人员与供应商协商退回不合格品，后续由仓库人员执行出库扣减。";
    const pendingReturnDecide = line.unqualifiedQty - (line.scrapDecidedQty + line.returnDecidedQty);
    formHtml = `
      <div class="console-form-field">
        <span>决定退货数量 <b>*</b></span>
        <input id="dlg_return_decide_qty" type="number" min="1" max="${pendingReturnDecide}" value="${pendingReturnDecide}" />
        <small>未处置不合格数量：${pendingReturnDecide} ${line.uom}</small>
      </div>
      <div class="console-form-field">
        <span>退回协调原因 <b>*</b></span>
        <input id="dlg_return_reason" type="text" value="质量检验不合格，供应商同意退货补发" />
      </div>
    `;
  } else if (actionType === "disposition_execute") {
    dialogTitle = "仓库确认质量处置实物执行";
    impactNote = "放行执行将实物从 QH-01 移至 RS-01 暂存位；报废与退回执行将从 QH-01 扣减实物库存并记不可篡改流水。";
    const pendingRelExec = line.releaseDecidedQty - line.releaseExecutedQty;
    const pendingScrapExec = line.scrapDecidedQty - line.scrapExecutedQty;
    const pendingRetExec = line.returnDecidedQty - line.returnExecutedQty;
    formHtml = `
      <div style="font-size:12px;display:grid;gap:6px;">
        <div>待执行放行移位：<strong style="color:var(--c-green);">${pendingRelExec} ${line.uom}</strong>（QH-01 → RS-01）</div>
        <div>待执行报废扣减：<strong style="color:var(--c-red);">${pendingScrapExec} ${line.uom}</strong>（扣减 QH-01 实物）</div>
        <div>待执行退货出库：<strong style="color:var(--c-amber);">${pendingRetExec} ${line.uom}</strong>（扣减 QH-01 实物）</div>
      </div>
    `;
  } else if (actionType === "putaway") {
    dialogTitle = "仓库确认上架存储";
    impactNote = `将收货暂存位（RS-01）中的货物移动至最终存储位（${order.storageLocation}），企业总实物库存不变。`;
    formHtml = `
      <div class="console-form-field">
        <span>本次上架数量 <b>*</b></span>
        <input id="dlg_putaway_qty" type="number" min="1" max="${inStaging}" value="${inStaging}" />
        <small>暂存位待上架量：${inStaging} ${line.uom}</small>
      </div>
      <div class="console-form-field">
        <span>目标存储库位</span>
        <input type="text" readonly value="${order.storageLocation}" style="background:rgba(255,255,255,0.05);" />
      </div>
    `;
  } else if (actionType === "complete") {
    dialogTitle = "人工完成采购单";
    impactNote = "终止尚未收货的剩余待收数量；已入库与已放行货物继续正常流转，不补造虚假收货流水。";
    formHtml = `
      <div class="console-form-field">
        <span>未收货终止数量</span>
        <input type="text" readonly value="${pendingArrival} ${line.uom}" style="background:rgba(255,255,255,0.05);" />
      </div>
      <div class="console-form-field">
        <span>人工完成原因 <b>*</b></span>
        <textarea id="dlg_complete_reason" rows="3" placeholder="请详细录入终止剩余供货的原因与审批依据..."></textarea>
      </div>
    `;
  }

  dialogEl.innerHTML = `
    <div class="console-dialog-backdrop" onclick="closePurchaseActionDialog()"></div>
    <div class="console-dialog-panel">
      <header>
        <h2>${dialogTitle}</h2>
        <button type="button" class="console-dialog-close" onclick="closePurchaseActionDialog()">&times;</button>
      </header>
      <div class="console-dialog-context">
        <span>单据单号</span><strong>${order.id}</strong>
        <span>商品物料</span><strong>${line.product} (${line.sku})</strong>
      </div>
      <form onsubmit="handlePurchaseActionSubmit(event, '${actionType}')">
        ${formHtml}
        <div class="console-dialog-impact">
          <span>业务与库存影响提示</span>
          <p>${impactNote}</p>
        </div>
        <p id="dlg_error" class="console-form-error"></p>
        <footer>
          <button type="button" onclick="closePurchaseActionDialog()">取消</button>
          <button type="submit" class="primary">确认提交</button>
        </footer>
      </form>
    </div>
  `;
  dialogEl.hidden = false;
}

function closePurchaseActionDialog() {
  const dialogEl = document.getElementById("purchaseActionDialog");
  if (dialogEl) dialogEl.hidden = true;
}

function calcReceiveSplit() {
  const arrEl = document.getElementById("dlg_arrived_qty");
  const rejEl = document.getElementById("dlg_rejected_qty");
  const recEl = document.getElementById("dlg_received_qty");
  if (!arrEl || !rejEl || !recEl) return;
  const arrived = parseInt(arrEl.value, 10) || 0;
  const rejected = parseInt(rejEl.value, 10) || 0;
  recEl.value = Math.max(0, arrived - rejected);
}

function calcInspectSplit() {
  const inspEl = document.getElementById("dlg_inspected_qty");
  const qualEl = document.getElementById("dlg_qualified_qty");
  const unqEl = document.getElementById("dlg_unqualified_qty");
  if (!inspEl || !qualEl || !unqEl) return;
  const inspected = parseInt(inspEl.value, 10) || 0;
  const qualified = parseInt(qualEl.value, 10) || 0;
  unqEl.value = Math.max(0, inspected - qualified);
}

function handlePurchaseActionSubmit(event, actionType) {
  event.preventDefault();
  const order = getSelectedPurchaseOrder();
  const line = order.lines[0];
  const errEl = document.getElementById("dlg_error");

  const now = new Date();
  const timeStr = `${String(now.getMonth()+1).padStart(2,'0')}-${String(now.getDate()).padStart(2,'0')} ${String(now.getHours()).padStart(2,'0')}:${String(now.getMinutes()).padStart(2,'0')}`;
  const key = `PO-CMD-${Date.now().toString().slice(-6)}`;

  if (actionType === "receive") {
    const arrived = parseInt(document.getElementById("dlg_arrived_qty")?.value, 10) || 0;
    const rejected = parseInt(document.getElementById("dlg_rejected_qty")?.value, 10) || 0;
    const reason = document.getElementById("dlg_rejection_reason")?.value.trim() || "";
    const received = arrived - rejected;

    if (arrived <= 0) { errEl.textContent = "到货数量必须大于 0"; return; }
    if (rejected > 0 && !reason) { errEl.textContent = "存在拒收数量时必须填写拒收原因"; return; }

    line.arrivedQty += arrived;
    line.rejectedQty += rejected;
    if (reason) line.rejectionReason = reason;
    line.receivedQty += received;
    order.status = "PartiallyReceived";

    order.events.unshift({
      time: timeStr,
      action: "仓库外观验收与实际接收",
      actor: "wh.operator",
      session: "jti…7c91",
      key,
      impact: `到货 ${arrived} 件：拒收 ${rejected} 件（不入库/待收），实收 ${received} 件进入 QualityHold（QH-01）`
    });

    showPurchaseToast(`外观验收完成：实收 ${received} 件入质量隔离位`);
  } else if (actionType === "inspect") {
    const inspected = parseInt(document.getElementById("dlg_inspected_qty")?.value, 10) || 0;
    const qualified = parseInt(document.getElementById("dlg_qualified_qty")?.value, 10) || 0;
    const unqualified = inspected - qualified;
    const reason = document.getElementById("dlg_inspect_reason")?.value.trim() || "";
    const uninspected = line.receivedQty - line.inspectedQty;

    if (inspected <= 0) { errEl.textContent = "检验总数必须大于 0"; return; }
    if (inspected > uninspected) { errEl.textContent = `本次检验总数不得超过待检数量（${uninspected} 件）`; return; }
    if (qualified < 0 || qualified > inspected) { errEl.textContent = "合格数量必须在 0 到检验总数之间"; return; }
    if (unqualified > 0 && !reason) { errEl.textContent = "存在不合格品时必须录入不合格原因"; return; }

    line.inspectedQty += inspected;
    line.qualifiedQty += qualified;
    line.unqualifiedQty += unqualified;
    if (reason) line.unqualifiedReason = reason;

    order.events.unshift({
      time: timeStr,
      action: "采购到货质量检验",
      actor: "qa.inspector",
      session: "jti…310a",
      key,
      impact: `检验 ${inspected} 件：合格 ${qualified} 件，不合格 ${unqualified} 件；等待处置决定`
    });

    showPurchaseToast(`质检完成：合格 ${qualified} 件，不合格 ${unqualified} 件`);
  } else if (actionType === "release_decide") {
    const qty = parseInt(document.getElementById("dlg_release_decide_qty")?.value, 10) || 0;
    const unreleased = line.qualifiedQty - line.releaseDecidedQty;

    if (qty <= 0) { errEl.textContent = "放行数量必须大于 0"; return; }
    if (qty > unreleased) { errEl.textContent = `放行数量不得超过未处置合格数量（${unreleased} 件）`; return; }

    line.releaseDecidedQty += qty;

    order.events.unshift({
      time: timeStr,
      action: "质量处置：决定合格放行",
      actor: "qa.inspector",
      session: "jti…310a",
      key,
      impact: `决定放行 ${qty} 件合格品；进入 PendingExecution 待执行`
    });

    showPurchaseToast(`已作出放行决定：${qty} 件待移位`);
  } else if (actionType === "scrap_decide") {
    const qty = parseInt(document.getElementById("dlg_scrap_decide_qty")?.value, 10) || 0;
    const reason = document.getElementById("dlg_scrap_reason")?.value.trim() || "报废处置";
    const undisposed = line.unqualifiedQty - (line.scrapDecidedQty + line.returnDecidedQty);

    if (qty <= 0) { errEl.textContent = "报废数量必须大于 0"; return; }
    if (qty > undisposed) { errEl.textContent = `报废数量不得超过未处置不合格数量（${undisposed} 件）`; return; }

    line.scrapDecidedQty += qty;

    order.events.unshift({
      time: timeStr,
      action: "质量处置：决定报废",
      actor: "qa.inspector",
      session: "jti…310a",
      key,
      impact: `决定报废 ${qty} 件不合格品（原因：${reason}）`
    });

    showPurchaseToast(`已作出报废决定：${qty} 件待执行扣减`);
  } else if (actionType === "return_decide") {
    const qty = parseInt(document.getElementById("dlg_return_decide_qty")?.value, 10) || 0;
    const reason = document.getElementById("dlg_return_reason")?.value.trim() || "退回供应方";
    const undisposed = line.unqualifiedQty - (line.scrapDecidedQty + line.returnDecidedQty);

    if (qty <= 0) { errEl.textContent = "退货数量必须大于 0"; return; }
    if (qty > undisposed) { errEl.textContent = `退货数量不得超过未处置不合格数量（${undisposed} 件）`; return; }

    line.returnDecidedQty += qty;

    order.events.unshift({
      time: timeStr,
      action: "质量处置：决定退回供应方",
      actor: "buyer.chen",
      session: "jti…552d",
      key,
      impact: `采购决定退回 ${qty} 件（原因：${reason}）`
    });

    showPurchaseToast(`已作出退货决定：${qty} 件待出库`);
  } else if (actionType === "disposition_execute") {
    const relDiff = line.releaseDecidedQty - line.releaseExecutedQty;
    const scDiff = line.scrapDecidedQty - line.scrapExecutedQty;
    const retDiff = line.returnDecidedQty - line.returnExecutedQty;

    line.releaseExecutedQty = line.releaseDecidedQty;
    line.scrapExecutedQty = line.scrapDecidedQty;
    line.returnExecutedQty = line.returnDecidedQty;

    const totalAccountedDisp = line.putawayQty + line.rejectedQty + line.scrapExecutedQty + line.returnExecutedQty;
    if (totalAccountedDisp >= line.orderedQty && line.releaseDecidedQty === line.putawayQty) {
      order.status = "Completed";
      order.completionType = "Normal";
    }

    order.events.unshift({
      time: timeStr,
      action: "仓库确认质量处置实物执行",
      actor: "wh.operator",
      session: "jti…7c91",
      key,
      impact: `执行移位放行 ${relDiff} 件至 RS-01 暂存位；执行报废扣减 ${scDiff} 件；执行退货 ${retDiff} 件`
    });

    showPurchaseToast(`处置执行完成：${relDiff} 件已移入收货暂存位`);
  } else if (actionType === "putaway") {
    const qty = parseInt(document.getElementById("dlg_putaway_qty")?.value, 10) || 0;
    line.putawayQty += qty;

    const totalAccounted = line.putawayQty + line.rejectedQty + line.scrapExecutedQty + line.returnExecutedQty;
    if (totalAccounted >= line.orderedQty) {
      order.status = "Completed";
      order.completionType = "Normal";
    }

    order.events.unshift({
      time: timeStr,
      action: "仓库确认上架存储",
      actor: "wh.operator",
      session: "jti…7c91",
      key,
      impact: `${qty} 件从 RS-01 移至存储位 ${order.storageLocation}；总库存不变`
    });

    showPurchaseToast(`上架确认成功：${qty} 件已入存储位`);
  } else if (actionType === "complete") {
    const reason = document.getElementById("dlg_complete_reason")?.value.trim();
    if (!reason) { errEl.textContent = "人工完成必须填写原因"; return; }

    order.status = "Completed";
    order.completionType = "Manual";
    order.completionReason = reason;
    order.completedAt = timeStr;
    order.completedBy = "buyer.chen";
    order.completedSession = "jti…552d";

    order.events.unshift({
      time: timeStr,
      action: "采购订单人工完成",
      actor: "buyer.chen",
      session: "jti…552d",
      key,
      impact: `人工终止剩余数量；原因：${reason}`
    });

    showPurchaseToast("采购订单已人工完成");
  }

  closePurchaseActionDialog();
  renderPurchaseQueue();
  renderPurchaseDetail();
}

function initPurchaseConsole() {
  const roleSelect = document.getElementById("purchaseRole");
  roleSelect?.addEventListener("change", (e) => {
    currentPurchaseRole = e.target.value;
    renderPurchaseDetail();
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

      currentPurchaseScenario = tab.dataset.scenario;
      currentPurchaseOrders = JSON.parse(JSON.stringify(purchaseScenarios[currentPurchaseScenario].orders));
      selectedPurchaseOrderId = purchaseScenarios[currentPurchaseScenario].featuredOrderId;
      renderPurchaseQueue();
      renderPurchaseDetail();
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
      purchaseStatusFilter = btn.dataset.statusFilter;
      renderPurchaseQueue();
    });
  });

  const searchInput = document.getElementById("purchaseSearch");
  searchInput?.addEventListener("input", (e) => {
    purchaseSearchQuery = e.target.value.trim();
    renderPurchaseQueue();
  });

  renderPurchaseQueue();
  renderPurchaseDetail();
}

document.addEventListener("DOMContentLoaded", initPurchaseConsole);
