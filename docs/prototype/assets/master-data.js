/**
 * 库存与基础资料高保真交互控制台 (master-data.js)
 * 严格执行已冻结业务规则：
 * 1. 统一公式：available_qty = on_hand_qty - reserved_qty >= 0；
 * 2. 6 类标准库位：ReceivingStaging, Storage, Picking, ShippingStaging, QualityHold, Adjustment；
 * 3. 质量隔离位（QualityHold）货物禁止销售预留或生产领料；
 * 4. 调拨在同一事务中扣减来源、增加目标，企业总库存不变；
 * 5. 盘点：未盘点 -> 盘点中（实盘） -> 已确认并调整（生成调整流水更新余额）；
 * 6. 不可篡改库存流水永久留痕。
 */

const roleLabelsForInventory = {
  warehouse: "仓库人员 (wh.operator)",
  admin: "租户管理员 (tenant.admin)"
};

let currentInventoryRole = "warehouse";
let currentInventoryTab = "balance";

// 基础物料主数据
const mockProducts = [
  { sku: "FG-SERVO-01", name: "伺服电机总成", spec: "220V / 1.5KW", uom: "台", category: "产成品", batchMgmt: true, status: "启用" },
  { sku: "FG-CTRL-08", name: "边缘控制终端", spec: "Octa-Core / 8G", uom: "台", category: "产成品", batchMgmt: true, status: "启用" },
  { sku: "RM-SERVO-ST", name: "定子转子组件", spec: "ST-80 / 精密铜组", uom: "件", category: "原材料", batchMgmt: true, status: "启用" },
  { sku: "RM-BEARING-01", name: "高精轴承组件", spec: "6008-2RS 高速", uom: "件", category: "标准件", batchMgmt: false, status: "启用" },
  { sku: "RM-ENCODER-01", name: "高分辨率编码器", spec: "23-bit 光电", uom: "套", category: "电子料", batchMgmt: true, status: "启用" },
  { sku: "PK-0088", name: "出货包装箱", spec: "L号加厚防静电", uom: "个", category: "辅料包材", batchMgmt: false, status: "启用" }
];

// 6 类标准库位
const mockLocations = [
  { code: "QH-01", name: "采购质量隔离位01", warehouse: "原料一仓 (WH-RM-01)", type: "QualityHold", desc: "到货实际接收但未放行或不合格暂存位，禁止预留与领料" },
  { code: "RS-01", name: "采购收货暂存位01", warehouse: "原料一仓 (WH-RM-01)", type: "ReceivingStaging", desc: "质检合格并放行后、尚未完成上架的过渡库位" },
  { code: "ST-A-01", name: "原料常规存储位A01", warehouse: "原料一仓 (WH-RM-01)", type: "Storage", desc: "定子转子与核心原材料长期存放位" },
  { code: "ST-B-02", name: "标准件存储位B02", warehouse: "原料一仓 (WH-RM-01)", type: "Storage", desc: "轴承与标准五金存储位" },
  { code: "PK-01", name: "拣选备料位01", warehouse: "原料一仓 (WH-RM-01)", type: "Picking", desc: "面向生产领料或发料的快速拣选库位" },
  { code: "FG-A-01", name: "成品常规存储位01", warehouse: "成品一仓 (WH-FG-01)", type: "Storage", desc: "伺服电机完工合格品常规存放位" },
  { code: "SHP-01", name: "发货暂存位01", warehouse: "成品一仓 (WH-FG-01)", type: "ShippingStaging", desc: "直接拣货移入但尚未发货确认的位置" },
  { code: "ADJ-01", name: "系统差异调整位", warehouse: "虚拟仓库 (WH-SYS)", type: "Adjustment", desc: "盘点溢缺与受控异常调整专用虚拟位" }
];

// 实时库存余额（黄金业务链同一时点快照：质量处置已完成，QH-01为0，RS-01为70待上架）
let mockBalances = [
  { sku: "FG-SERVO-01", product: "伺服电机总成", warehouse: "成品一仓", location: "FG-A-01", type: "Storage", onHand: 400, reserved: 0, batch: "LOT-20260825-01" },
  { sku: "FG-SERVO-01", product: "伺服电机总成", warehouse: "成品一仓", location: "SHP-01", type: "ShippingStaging", onHand: 20, reserved: 20, batch: "LOT-20260825-01" },
  { sku: "RM-SERVO-ST", product: "定子转子组件", warehouse: "原料一仓", location: "QH-01", type: "QualityHold", onHand: 0, reserved: 0, batch: "LOT-20260826-01" },
  { sku: "RM-SERVO-ST", product: "定子转子组件", warehouse: "原料一仓", location: "RS-01", type: "ReceivingStaging", onHand: 70, reserved: 0, batch: "LOT-20260826-01" },
  { sku: "RM-SERVO-ST", product: "定子转子组件", warehouse: "原料一仓", location: "ST-A-01", type: "Storage", onHand: 150, reserved: 0, batch: "LOT-20260820-09" },
  { sku: "RM-BEARING-01", product: "高精轴承组件", warehouse: "原料一仓", location: "ST-B-02", type: "Storage", onHand: 300, reserved: 0, batch: "-" }
];

// 盘点任务
let mockStocktake = {
  id: "STK-20260826-01",
  warehouse: "原料一仓",
  location: "ST-A-01",
  sku: "RM-SERVO-ST",
  systemQty: 150,
  countedQty: 150,
  variance: 0,
  reason: "",
  status: "InProgress" // NotStarted, InProgress, Adjusted
};

// 库存流水
let mockTransactions = [
  { time: "2026-08-26 16:30", type: "QUALITY_RELEASE", sku: "RM-SERVO-ST", qty: 70, from: "QH-01", to: "RS-01", doc: "PO-20260826-001", actor: "wh.operator" },
  { time: "2026-08-26 16:30", type: "QUALITY_SCRAP", sku: "RM-SERVO-ST", qty: -5, from: "QH-01", to: "-", doc: "PO-20260826-001", actor: "wh.operator" },
  { time: "2026-08-26 14:15", type: "PURCHASE_RECEIPT", sku: "RM-SERVO-ST", qty: 75, from: "-", to: "QH-01", doc: "PO-20260826-001", actor: "wh.operator" },
  { time: "2026-08-26 11:30", type: "MATERIAL_ISSUE", sku: "RM-SERVO-ST", qty: -70, from: "ST-A-01", to: "-", doc: "WO-20260826-018", actor: "wh.operator" },
  { time: "2026-08-26 17:56", type: "DIRECT_PICK", sku: "FG-SERVO-01", qty: 40, from: "FG-A-01", to: "SHP-01", doc: "SO-20260826-018", actor: "wh.operator" },
  { time: "2026-08-26 18:42", type: "SALES_SHIPMENT", sku: "FG-SERVO-01", qty: -20, from: "SHP-01", to: "-", doc: "SO-20260826-018", actor: "wh.operator" }
];

function showInventoryToast(message, type = "success") {
  const toast = document.getElementById("inventoryToast");
  if (!toast) return;
  toast.textContent = message;
  toast.className = `console-toast is-visible is-${type}`;
  setTimeout(() => { toast.classList.remove("is-visible"); }, 3600);
}

function renderMasterDataView() {
  const mainEl = document.getElementById("masterDataMainView");
  if (!mainEl) return;

  if (currentInventoryTab === "balance") {
    const totalOnHand = mockBalances.reduce((s, b) => s + b.onHand, 0);
    const totalReserved = mockBalances.reduce((s, b) => s + b.reserved, 0);
    const totalAvailable = totalOnHand - totalReserved;
    const qhOnHand = mockBalances.filter(b => b.type === "QualityHold").reduce((s, b) => s + b.onHand, 0);

    mainEl.innerHTML = `
      <div class="console-kpi-strip" style="margin:0 0 18px;">
        <div class="console-kpi-item highlight-cyan">
          <span>企业总实物库存 (On Hand)</span>
          <strong>${totalOnHand} <small>件</small></strong>
          <small>实际拥有总实物 (400+20+0+70+150+300)</small>
        </div>
        <div class="console-kpi-item highlight-amber">
          <span>总业务预留 (Reserved)</span>
          <strong>${totalReserved} <small>件</small></strong>
          <small>发货暂存/业务锁定 (SHP-01: 20)</small>
        </div>
        <div class="console-kpi-item highlight-green">
          <span>总可用库存 (Available)</span>
          <strong>${totalAvailable} <small>件</small></strong>
          <small>公式: 实物 - 预留 (940 - 20 = 920)</small>
        </div>
        <div class="console-kpi-item highlight-red">
          <span>质量隔离位数量 (QualityHold)</span>
          <strong>${qhOnHand} <small>件</small></strong>
          <small>处置完成放行70至RS-01/报废5</small>
        </div>
      </div>

      <section class="console-section" style="margin:0;">
        <div class="console-section-head">
          <h3>实时库存余额看板 (Inventory Balance Snapshot)</h3>
          <small>实物满足 available_qty = on_hand_qty - reserved_qty >= 0</small>
        </div>
        <div class="console-table-shell">
          <table class="console-table">
            <thead>
              <tr>
                <th>物料SKU</th>
                <th>物料名称</th>
                <th>仓库名称</th>
                <th>库位编码</th>
                <th>库位类型</th>
                <th>批次号</th>
                <th>实物数量 (On-Hand)</th>
                <th>预留数量 (Reserved)</th>
                <th>可用数量 (Available)</th>
                <th>库位状态与限制</th>
              </tr>
            </thead>
            <tbody>
              ${mockBalances.map(b => {
                const avail = b.onHand - b.reserved;
                const isQH = b.type === "QualityHold";
                const isSHP = b.type === "ShippingStaging";
                const isRS = b.type === "ReceivingStaging";
                return `
                  <tr>
                    <td><code>${b.sku}</code></td>
                    <td><strong>${b.product}</strong></td>
                    <td>${b.warehouse}</td>
                    <td><strong style="color:var(--c-cyan);">${b.location}</strong></td>
                    <td><span class="console-badge ${isQH ? 'red' : isSHP ? 'amber' : isRS ? 'cyan' : 'green'}">${b.type}</span></td>
                    <td><small>${b.batch}</small></td>
                    <td><strong>${b.onHand}</strong></td>
                    <td style="color:var(--c-amber);">${b.reserved}</td>
                    <td><strong style="color:${isQH ? 'var(--c-dim)' : 'var(--c-green)'};">${isQH ? '0 (隔离)' : avail}</strong></td>
                    <td>
                      <small style="color:${isQH ? 'var(--c-muted)' : isSHP ? 'var(--c-amber)' : isRS ? 'var(--c-cyan)' : 'var(--c-muted)'};">
                        ${isQH ? '⚠️ 隔离位：处置已完成，实物为0 (禁止预留)' : isSHP ? '🚚 发货暂存：已拣货待发货' : isRS ? '📦 收货暂存：质检合格待上架' : '✅ 正常可用'}
                      </small>
                    </td>
                  </tr>
                `;
              }).join("")}
            </tbody>
          </table>
        </div>
      </section>
    `;
  } else if (currentInventoryTab === "transfer_stocktake") {
    mainEl.innerHTML = `
      <div style="display:grid;grid-template-columns:repeat(auto-fit, minmax(420px, 1fr));gap:18px;">
        <section class="console-section" style="margin:0;">
          <div class="console-section-head">
            <h3>库位调拨模拟 (Location Transfer)</h3>
            <small>同一事务减少来源、增加目标，企业总库存不变</small>
          </div>
          <form onsubmit="handleTransferSubmit(event)" style="padding:18px;display:grid;gap:14px;">
            <div class="console-form-field">
              <span>调拨产品 <b>*</b></span>
              <select id="tr_sku">
                <option value="RM-SERVO-ST">定子转子组件 (RM-SERVO-ST)</option>
                <option value="FG-SERVO-01">伺服电机总成 (FG-SERVO-01)</option>
              </select>
            </div>
            <div class="console-form-field">
              <span>来源库位 <b>*</b></span>
              <select id="tr_from">
                <option value="ST-A-01">原料常规存储位 (ST-A-01) - 可用: 150</option>
                <option value="RS-01">采购收货暂存位 (RS-01) - 可用: 70</option>
              </select>
            </div>
            <div class="console-form-field">
              <span>目标库位 <b>*</b></span>
              <select id="tr_to">
                <option value="PK-01">拣选备料位01 (PK-01)</option>
                <option value="ST-A-01">原料常规存储位 (ST-A-01)</option>
              </select>
            </div>
            <div class="console-form-field">
              <span>调拨数量 <b>*</b></span>
              <input id="tr_qty" type="number" min="1" max="100" value="10" />
            </div>
            <button type="submit" class="console-action-btn primary" style="justify-content:center;min-height:42px;" tabindex="0" role="button">
              <span class="console-action-title">执行库位调拨</span>
              <span class="console-action-perm">inventory.transfer.confirm</span>
            </button>
          </form>
        </section>

        <section class="console-section" style="margin:0;">
          <div class="console-section-head">
            <h3>盘点差异调整控制台 (Stocktake &amp; Cycle Count)</h3>
            <small>未盘点 → 录入实盘 → 确认调整生成差异流水</small>
          </div>
          <div style="padding:18px;display:grid;gap:14px;">
            <div class="console-fact-grid" style="padding:0;">
              <div class="console-fact-item">
                <span class="console-fact-label">盘点单号</span>
                <span class="console-fact-val">${mockStocktake.id}</span>
              </div>
              <div class="console-fact-item">
                <span class="console-fact-label">盘点库位</span>
                <span class="console-fact-val">${mockStocktake.location} (${mockStocktake.warehouse})</span>
              </div>
              <div class="console-fact-item">
                <span class="console-fact-label">盘点产品</span>
                <span class="console-fact-val">${mockStocktake.sku}</span>
              </div>
              <div class="console-fact-item">
                <span class="console-fact-label">系统账面数量</span>
                <span class="console-fact-val">${mockStocktake.systemQty}</span>
              </div>
            </div>

            <div class="console-form-field">
              <span>实盘数量 (Counted Qty) <b>*</b></span>
              <input id="stk_counted" type="number" value="${mockStocktake.countedQty}" oninput="calcStocktakeDiff()" />
            </div>
            <div class="console-form-field">
              <span>盘点差异 (Variance)</span>
              <input id="stk_diff" type="text" readonly value="${mockStocktake.variance}" style="background:rgba(255,255,255,0.05);" />
            </div>
            <div class="console-form-field">
              <span>差异原因说明</span>
              <input id="stk_reason" type="text" placeholder="如有盘盈/盘亏请录入调查原因" value="${mockStocktake.reason}" />
            </div>
            <button type="button" class="console-action-btn primary" onclick="handleStocktakeConfirm()" style="justify-content:center;min-height:42px;" tabindex="0" role="button">
              <span class="console-action-title">确认并调整库存</span>
              <span class="console-action-perm">inventory.stocktake.adjust</span>
            </button>
          </div>
        </section>
      </div>
    `;
  } else if (currentInventoryTab === "products") {
    mainEl.innerHTML = `
      <section class="console-section" style="margin:0;">
        <div class="console-section-head">
          <h3>商品与物料主数据定义 (Product Master Data)</h3>
          <small>一期支持简单批次管理；序列号与保质期属于二期</small>
        </div>
        <div class="console-table-shell">
          <table class="console-table">
            <thead>
              <tr>
                <th>物料SKU</th>
                <th>物料名称</th>
                <th>规格型号</th>
                <th>计量单位</th>
                <th>物料分类</th>
                <th>一期批次管理</th>
                <th>启用状态</th>
              </tr>
            </thead>
            <tbody>
              ${mockProducts.map(p => `
                <tr>
                  <td><code>${p.sku}</code></td>
                  <td><strong>${p.name}</strong></td>
                  <td>${p.spec}</td>
                  <td>${p.uom}</td>
                  <td><span class="console-badge cyan">${p.category}</span></td>
                  <td>${p.batchMgmt ? '<span style="color:var(--c-green);">是 (支持批号)</span>' : '<span style="color:var(--c-muted);">否</span>'}</td>
                  <td><span class="console-badge green">${p.status}</span></td>
                </tr>
              `).join("")}
            </tbody>
          </table>
        </div>
      </section>
    `;
  } else if (currentInventoryTab === "locations") {
    mainEl.innerHTML = `
      <section class="console-section" style="margin:0;">
        <div class="console-section-head">
          <h3>仓库与 6 类标准库位架构 (Locations Standard)</h3>
          <small>严禁跨越库位类型直接分配或非法流转</small>
        </div>
        <div class="console-table-shell">
          <table class="console-table">
            <thead>
              <tr>
                <th>库位编码</th>
                <th>库位名称</th>
                <th>所属仓库</th>
                <th>库位类型 (LocationType)</th>
                <th>业务用途与约束说明</th>
              </tr>
            </thead>
            <tbody>
              ${mockLocations.map(l => `
                <tr>
                  <td><code>${l.code}</code></td>
                  <td><strong>${l.name}</strong></td>
                  <td>${l.warehouse}</td>
                  <td><span class="console-badge ${l.type === 'QualityHold' ? 'red' : l.type === 'ShippingStaging' ? 'amber' : l.type === 'ReceivingStaging' ? 'cyan' : 'green'}">${l.type}</span></td>
                  <td><small style="color:var(--c-muted);">${l.desc}</small></td>
                </tr>
              `).join("")}
            </tbody>
          </table>
        </div>
      </section>
    `;
  } else if (currentInventoryTab === "transactions") {
    mainEl.innerHTML = `
      <section class="console-section" style="margin:0;">
        <div class="console-section-head">
          <h3>不可篡改库存流水 (Inventory Transactions)</h3>
          <small>每次数量、位置或预留变更均形成可审计流水记录</small>
        </div>
        <div class="console-table-shell">
          <table class="console-table">
            <thead>
              <tr>
                <th>发生时间</th>
                <th>流水类型 (TxType)</th>
                <th>物料SKU</th>
                <th>变更数量</th>
                <th>来源库位</th>
                <th>目标库位</th>
                <th>关联业务单据</th>
                <th>操作人</th>
              </tr>
            </thead>
            <tbody>
              ${mockTransactions.map(tx => `
                <tr>
                  <td>${tx.time}</td>
                  <td><code>${tx.type}</code></td>
                  <td><strong>${tx.sku}</strong></td>
                  <td>
                    <strong style="color:${tx.qty > 0 ? 'var(--c-green)' : 'var(--c-red)'};">
                      ${tx.qty > 0 ? `+${tx.qty}` : tx.qty}
                    </strong>
                  </td>
                  <td><code>${tx.from}</code></td>
                  <td><code>${tx.to}</code></td>
                  <td><span class="console-badge cyan">${tx.doc}</span></td>
                  <td><small>${tx.actor}</small></td>
                </tr>
              `).join("")}
            </tbody>
          </table>
        </div>
      </section>
    `;
  }
}

function calcStocktakeDiff() {
  const countedEl = document.getElementById("stk_counted");
  const diffEl = document.getElementById("stk_diff");
  if (!countedEl || !diffEl) return;
  const counted = parseInt(countedEl.value, 10) || 0;
  const diff = counted - mockStocktake.systemQty;
  diffEl.value = diff > 0 ? `+${diff} (盘盈)` : diff < 0 ? `${diff} (盘亏)` : "0 (无差异)";
}

function handleTransferSubmit(event) {
  event.preventDefault();
  const sku = document.getElementById("tr_sku")?.value;
  const from = document.getElementById("tr_from")?.value;
  const to = document.getElementById("tr_to")?.value;
  const qty = parseInt(document.getElementById("tr_qty")?.value, 10) || 0;

  if (qty <= 0) {
    showInventoryToast("调拨失败：调拨数量必须大于 0", "danger");
    return;
  }
  if (from === to) {
    showInventoryToast("调拨失败：调拨来源与目标库位不能相同", "danger");
    return;
  }

  const fromBal = mockBalances.find(b => b.location === from && b.sku === sku);
  if (!fromBal) {
    showInventoryToast(`调拨失败：来源库位 ${from} 未找到物料 ${sku}`, "danger");
    return;
  }
  if (fromBal.available < qty) {
    showInventoryToast(`调拨失败：来源库位 ${from} 可用数量不足（当前可用: ${fromBal.available} 件，申请调拨: ${qty} 件）`, "danger");
    return;
  }

  // 1. 真实扣减来源库位余额
  fromBal.onHand -= qty;
  fromBal.available = fromBal.onHand - fromBal.reserved;

  // 2. 真实增加目标库位余额
  let toBal = mockBalances.find(b => b.location === to && b.sku === sku);
  if (toBal) {
    toBal.onHand += qty;
    toBal.available = toBal.onHand - toBal.reserved;
  } else {
    const locNameMap = {
      "ST-A-01": { wh: "原料一仓", name: "标准货架 A-01", type: "Storage" },
      "ST-B-02": { wh: "原料一仓", name: "重型货架 B-02", type: "Storage" },
      "RS-01": { wh: "原料一仓", name: "收货暂存位", type: "ReceivingStaging" },
      "FG-A-01": { wh: "成品一仓", name: "成品立体货架 A-01", type: "Storage" }
    };
    const locMeta = locNameMap[to] || { wh: "原料一仓", name: to, type: "Storage" };
    mockBalances.push({
      warehouse: locMeta.wh,
      location: to,
      locationName: locMeta.name,
      type: locMeta.type,
      sku: sku,
      product: fromBal.product,
      onHand: qty,
      reserved: 0,
      available: qty,
      status: "Normal"
    });
  }

  const now = new Date();
  const timeStr = `2026-08-26 ${String(now.getHours()).padStart(2,'0')}:${String(now.getMinutes()).padStart(2,'0')}`;

  mockTransactions.unshift({
    time: timeStr,
    type: "LOCATION_TRANSFER",
    sku,
    qty,
    from,
    to,
    doc: `TR-${Date.now().toString().slice(-6)}`,
    actor: "wh.operator"
  });

  showInventoryToast(`调拨成功：${sku} ${qty} 件已从 ${from} 转移至 ${to}，实际库位余额已实时更新`);
  renderMasterDataView();
}

function handleStocktakeConfirm() {
  const countedEl = document.getElementById("stk_counted");
  const counted = parseInt(countedEl?.value, 10);
  if (isNaN(counted) || counted < 0) {
    showInventoryToast("盘点失败：实盘数量必须为大于等于 0 的整数", "danger");
    return;
  }
  const reasonInput = document.getElementById("stk_reason")?.value?.trim();
  const diff = counted - mockStocktake.systemQty;

  // 严格校验：存在非零差异时必须录入真实原因，禁止默认为无差异
  if (diff !== 0 && (!reasonInput || reasonInput === "例行盘点无差异")) {
    showInventoryToast("盘点差异处理：存在盘盈/盘亏差异时必须录入调查调整原因", "danger");
    return;
  }
  const reason = diff === 0 ? "例行盘点无差异" : reasonInput;

  mockStocktake.countedQty = counted;
  mockStocktake.variance = diff;
  mockStocktake.reason = reason;
  mockStocktake.status = "Adjusted";

  // 真实更新目标库位库存余额
  const targetBal = mockBalances.find(b => b.location === mockStocktake.location && b.sku === mockStocktake.sku);
  if (targetBal) {
    targetBal.onHand = counted;
    targetBal.available = targetBal.onHand - targetBal.reserved;
  }

  const now = new Date();
  const timeStr = `2026-08-26 ${String(now.getHours()).padStart(2,'0')}:${String(now.getMinutes()).padStart(2,'0')}`;

  if (diff !== 0) {
    mockTransactions.unshift({
      time: timeStr,
      type: "STOCKTAKE_ADJUST",
      sku: mockStocktake.sku,
      qty: diff,
      from: diff < 0 ? mockStocktake.location : "ADJ-01",
      to: diff > 0 ? mockStocktake.location : "ADJ-01",
      doc: mockStocktake.id,
      actor: "wh.operator"
    });
  }

  showInventoryToast(`盘点调整确认完成：库位 ${mockStocktake.location} 实盘 ${counted} 件 (差异: ${diff > 0 ? '+' + diff : diff})，实际余额已完成更新`);
  renderMasterDataView();
}

function initMasterDataConsole() {
  const roleSelect = document.getElementById("inventoryRole");
  roleSelect?.addEventListener("change", (e) => {
    currentInventoryRole = e.target.value;
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
      currentInventoryTab = tab.dataset.tab;
      renderMasterDataView();
    });

    tab.addEventListener("keydown", (e) => {
      if (e.key === "Enter" || e.key === " ") {
        e.preventDefault();
        tab.click();
      }
    });
  });

  renderMasterDataView();
}

document.addEventListener("DOMContentLoaded", initMasterDataConsole);
