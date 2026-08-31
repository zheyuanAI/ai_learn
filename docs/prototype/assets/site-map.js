/**
 * 厂区二维空间地图高保真交互控制台 (site-map.js)
 * 严格执行已冻结业务规则：
 * 1. 相对百分比坐标防偏移；
 * 2. 状态展示优先级：告警 (Alarm) > 离线 (Offline) > 预警 (Warning) > 正常 (Normal)；
 * 3. 只读空间投影，不反向修改业务状态；
 * 4. 点位抽屉展示实时业务事实与直达业务页面的跳转链接。
 */

const mapPointsData = [
  {
    id: "MAP-PT-0017",
    name: "包装测试线 DEV-C12",
    type: "Device",
    entityId: "DEV-C12",
    left: 68,
    top: 36,
    status: "Alarm",
    statusText: "超温严重告警 (78.5 ℃ > 75 ℃)",
    detail: "全自动包装测试设备，正执行工单 WO-20260826-018 工序30 (OE-20260826-033)",
    metrics: [
      { label: "主轴温度", value: "78.5 ℃", warn: true },
      { label: "主轴转速", value: "1200 RPM" },
      { label: "运行电流", value: "18.2 A" }
    ],
    targetLinks: [
      { label: "查看 IoT 遥测与告警控制台", href: "device-alarm.html" },
      { label: "查看制造执行工单 (WO-20260826-018)", href: "work-order.html" }
    ]
  },
  {
    id: "MAP-PT-0003",
    name: "智能堆垛机 DEV-B03",
    type: "Device",
    entityId: "DEV-B03",
    left: 22,
    top: 30,
    status: "Normal",
    statusText: "在线运行中",
    detail: "成品一仓立体库自动化堆垛出入库设备",
    metrics: [
      { label: "电机温度", value: "42.1 ℃" },
      { label: "运行速度", value: "850 mm/s" },
      { label: "当前载荷", value: "320 kg" }
    ],
    targetLinks: [
      { label: "查看 IoT 遥测与告警", href: "device-alarm.html" },
      { label: "查看销售出库拣货", href: "sales-outbound.html" }
    ]
  },
  {
    id: "MAP-PT-0001",
    name: "伺服冲压机 DEV-A01",
    type: "Device",
    entityId: "DEV-A01",
    left: 55,
    top: 72,
    status: "Normal",
    statusText: "停机待料中",
    detail: "机加一车间高精伺服冲压机床",
    metrics: [
      { label: "主轴温度", value: "36.2 ℃" },
      { label: "冲压力", value: "0 kN" },
      { label: "当前状态", value: "Stopped" }
    ],
    targetLinks: [
      { label: "查看 IoT 遥测与告警", href: "device-alarm.html" },
      { label: "查看制造工单列表", href: "work-order.html" }
    ]
  },
  {
    id: "MAP-PT-WH-FG",
    name: "成品一仓 (WH-FG-01)",
    type: "Warehouse",
    entityId: "WH-FG-01",
    left: 14,
    top: 20,
    status: "Normal",
    statusText: "出库作业中",
    detail: "存储伺服电机总成、控制终端等成品，包含 SHP-01 发货暂存位",
    metrics: [
      { label: "实物库存", value: "420 台" },
      { label: "发货暂存", value: "20 台" },
      { label: "库位利用率", value: "68%" }
    ],
    targetLinks: [
      { label: "进入销售出库履约控制台", href: "sales-outbound.html" },
      { label: "查看实时库存与库位分布", href: "master-data.html" }
    ]
  },
  {
    id: "MAP-PT-WH-RM",
    name: "原料一仓 (WH-RM-01)",
    type: "Warehouse",
    entityId: "WH-RM-01",
    left: 14,
    top: 65,
    status: "Normal",
    statusText: "收货质检中",
    detail: "包含 QH-01 质量隔离位与 RS-01 收货暂存位",
    metrics: [
      { label: "质量隔离位", value: "75 件" },
      { label: "收货暂存位", value: "70 件" },
      { label: "常规存储量", value: "450 件" }
    ],
    targetLinks: [
      { label: "进入采购收货与到货质检", href: "purchase-inbound.html" },
      { label: "查看库存主数据", href: "master-data.html" }
    ]
  },
  {
    id: "MAP-PT-AREA-02",
    name: "装配二车间 (AREA-PROD-02)",
    type: "Warehouse",
    entityId: "AREA-PROD-02",
    left: 78,
    top: 52,
    status: "Warning",
    statusText: "工序30告警排查中",
    detail: "负责伺服电机总成总装与检测标定工作",
    metrics: [
      { label: "在制工单", value: "WO-20260826-018" },
      { label: "在线设备", value: "DEV-C12" },
      { label: "当班人员", value: "王工 等4人" }
    ],
    targetLinks: [
      { label: "查看制造执行控制台", href: "work-order.html" },
      { label: "查看综合指标看板", href: "dashboard.html" }
    ]
  }
];

let currentMapFilter = "all";
let selectedPointId = "MAP-PT-0017";

function renderMapPoints() {
  const container = document.getElementById("mapPointContainer");
  if (!container) return;

  const filtered = mapPointsData.filter(pt => {
    if (currentMapFilter === "alarm" && pt.status !== "Alarm") return false;
    if (currentMapFilter === "device" && pt.type !== "Device") return false;
    if (currentMapFilter === "warehouse" && pt.type !== "Warehouse") return false;
    return true;
  });

  container.innerHTML = filtered.map(pt => {
    const isSelected = pt.id === selectedPointId;
    const isAlarm = pt.status === "Alarm";
    const isWarn = pt.status === "Warning";
    const bgCol = isAlarm ? "var(--c-red)" : isWarn ? "var(--c-amber)" : "var(--c-green)";

    return `
      <div onclick="selectMapPoint('${pt.id}')" style="
        position:absolute;
        left:${pt.left}%;
        top:${pt.top}%;
        transform:translate(-50%, -50%);
        cursor:pointer;
        display:flex;
        align-items:center;
        gap:6px;
        padding:6px 10px;
        border-radius:20px;
        background:rgba(7,16,23,0.92);
        border:1px solid ${isSelected ? 'var(--c-cyan)' : 'var(--c-line-strong)'};
        box-shadow:${isAlarm ? '0 0 16px rgba(255,124,115,0.45)' : '0 4px 12px rgba(0,0,0,0.4)'};
        transition:all 160ms ease;
        z-index:${isSelected ? 10 : 2};
      ">
        <span style="width:8px;height:8px;border-radius:50%;background:${bgCol};${isAlarm ? 'animation:console-pulse 1.2s infinite;' : ''}"></span>
        <span style="font-size:11px;font-weight:700;color:${isSelected ? 'var(--c-cyan)' : 'var(--c-ink)'};">${pt.name}</span>
      </div>
    `;
  }).join("");
}

function selectMapPoint(ptId) {
  selectedPointId = ptId;
  renderMapPoints();
  renderMapPointDrawer();
}

function renderMapPointDrawer() {
  const drawerEl = document.getElementById("mapPointDrawer");
  const pt = mapPointsData.find(p => p.id === selectedPointId) || mapPointsData[0];
  if (!drawerEl || !pt) return;

  const isAlarm = pt.status === "Alarm";
  const isWarn = pt.status === "Warning";

  drawerEl.innerHTML = `
    <header class="console-detail-head">
      <div>
        <div class="console-title-meta">
          <span class="console-module-code">POINT / ${pt.type.toUpperCase()}</span>
          <span class="console-badge ${isAlarm ? 'red' : isWarn ? 'amber' : 'green'}">${pt.status}</span>
        </div>
        <h2>${pt.name}</h2>
        <p>点位编号：${pt.id} · 实体ID：${pt.entityId} · 坐标：(${pt.left}%, ${pt.top}%)</p>
      </div>
    </header>

    <div class="console-state-explainer">
      <div>
        <span>当前运行事实</span>
        <strong style="color:${isAlarm ? 'var(--c-red)' : 'var(--c-green)'};">${pt.statusText}</strong>
        <small>展示优先级：告警 > 离线 > 预警 > 正常</small>
      </div>
      <div class="console-state-divider">→</div>
      <div>
        <span>空间位置</span>
        <strong>X: ${pt.left}% · Y: ${pt.top}%</strong>
        <small>自适应相对百分比防偏移</small>
      </div>
      <p>${pt.detail}</p>
    </div>

    <section class="console-section">
      <div class="console-section-head">
        <h3>实时状态与关键指标事实</h3>
        <small>只读汇聚来源服务事实</small>
      </div>
      <div class="console-fact-grid">
        ${pt.metrics.map(m => `
          <div class="console-fact-item">
            <span class="console-fact-label">${m.label}</span>
            <span class="console-fact-val ${m.warn ? 'highlight' : ''}" style="${m.warn ? 'color:var(--c-red);' : ''}">${m.value}</span>
          </div>
        `).join("")}
      </div>
    </section>

    <section class="console-section">
      <div class="console-section-head">
        <h3>一键穿透跨模块直达链接</h3>
        <small>携带实体上下文跳转至具体业务控制台</small>
      </div>
      <div style="padding:16px 18px;display:grid;gap:10px;">
        ${pt.targetLinks.map(lnk => `
          <a href="${lnk.href}" class="console-action-btn primary" style="text-decoration:none;">
            <span class="console-action-title">🔗 ${lnk.label}</span>
            <span class="console-action-desc">跳转至对应业务页面查看全量明细与执行操作 ↗</span>
          </a>
        `).join("")}
      </div>
    </section>
  `;
}

function initSiteMapConsole() {
  const tabs = document.querySelectorAll(".console-scenario-tabs button");
  tabs.forEach(tab => {
    tab.addEventListener("click", () => {
      tabs.forEach(t => {
        t.classList.remove("is-active");
        t.setAttribute("aria-pressed", "false");
      });
      tab.classList.add("is-active");
      tab.setAttribute("aria-pressed", "true");
      currentMapFilter = tab.dataset.filter;
      renderMapPoints();
    });
  });

  renderMapPoints();
  renderMapPointDrawer();
}

document.addEventListener("DOMContentLoaded", initSiteMapConsole);
