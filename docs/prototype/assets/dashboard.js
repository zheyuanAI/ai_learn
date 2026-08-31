/**
 * 首页综合看板高保真交互控制台 (dashboard.js)
 * 严格执行已冻结业务规则：
 * 1. 7 组固定事实摘要：库存、采购销售履约、生产、质量、设备、告警、追溯；
 * 2. 纯只读汇总与展示，不执行任何写操作，不在展示层产生第二套事实；
 * 3. 30 秒自动定时刷新机制与手动即时刷新；
 * 4. 时间范围支持今日、近 7 天、近 30 天，响应返回生效起止时间；
 * 5. 故障降级标记：某个领域不可用时显著标出，不用 0 或伪造值代替。
 */

let currentTimeRange = "today";
let countdownSeconds = 30;
let refreshInterval = null;

const dashboardFactData = {
  today: {
    timeRangeLabel: "今日 (2026-08-26 00:00 - 23:59)",
    syncTime: "2026-08-26 16:30:15",
    inventory: {
      onHand: 600,
      reserved: 120,
      available: 480,
      qualityHold: 75,
      shippingStaged: 20
    },
    fulfillment: {
      poPendingArrival: 5,
      poPendingInspect: 75,
      poPendingPutaway: 70,
      soPendingPick: 60,
      soShippingStaged: 20,
      soShipped: 20
    },
    manufacturing: {
      draft: 0,
      submitted: 1,
      released: 2,
      inProgress: 3,
      completedNormal: 12,
      completedManual: 1
    },
    quality: {
      poPendingBatch: 1,
      mesPendingBatch: 1,
      passRate: "97.2%",
      scrappedTotal: 7
    },
    devices: {
      total: 18,
      online: 17,
      running: 14,
      offline: 1
    },
    alarms: {
      active: 1,
      unacked: 0,
      recovered24h: 5,
      levelSevere: 1,
      levelWarn: 2
    },
    traceability: {
      goldenChainCoverage: "84.5%",
      brokenChains: 2,
      lastTracedDoc: "SO-20260826-018 → WO-20260826-018 → PO-20260826-001"
    }
  },
  week: {
    timeRangeLabel: "近 7 天 (2026-08-20 至 2026-08-26)",
    syncTime: "2026-08-26 16:30:15",
    inventory: {
      onHand: 600,
      reserved: 120,
      available: 480,
      qualityHold: 75,
      shippingStaged: 20
    },
    fulfillment: {
      poPendingArrival: 24,
      poPendingInspect: 75,
      poPendingPutaway: 70,
      soPendingPick: 180,
      soShippingStaged: 20,
      soShipped: 160
    },
    manufacturing: {
      draft: 1,
      submitted: 2,
      released: 5,
      inProgress: 8,
      completedNormal: 48,
      completedManual: 3
    },
    quality: {
      poPendingBatch: 1,
      mesPendingBatch: 1,
      passRate: "98.1%",
      scrappedTotal: 19
    },
    devices: {
      total: 18,
      online: 17,
      running: 14,
      offline: 1
    },
    alarms: {
      active: 1,
      unacked: 0,
      recovered24h: 22,
      levelSevere: 2,
      levelWarn: 8
    },
    traceability: {
      goldenChainCoverage: "91.2%",
      brokenChains: 5,
      lastTracedDoc: "SO-20260820-009 → WO-20260820-011"
    }
  },
  month: {
    timeRangeLabel: "近 30 天 (2026-07-28 至 2026-08-26)",
    syncTime: "2026-08-26 16:30:15",
    inventory: {
      onHand: 600,
      reserved: 120,
      available: 480,
      qualityHold: 75,
      shippingStaged: 20
    },
    fulfillment: {
      poPendingArrival: 60,
      poPendingInspect: 75,
      poPendingPutaway: 70,
      soPendingPick: 450,
      soShippingStaged: 20,
      soShipped: 680
    },
    manufacturing: {
      draft: 3,
      submitted: 4,
      released: 12,
      inProgress: 15,
      completedNormal: 186,
      completedManual: 8
    },
    quality: {
      poPendingBatch: 1,
      mesPendingBatch: 1,
      passRate: "98.6%",
      scrappedTotal: 54
    },
    devices: {
      total: 18,
      online: 17,
      running: 14,
      offline: 1
    },
    alarms: {
      active: 1,
      unacked: 0,
      recovered24h: 68,
      levelSevere: 5,
      levelWarn: 21
    },
    traceability: {
      goldenChainCoverage: "94.8%",
      brokenChains: 11,
      lastTracedDoc: "SO-20260728-001 → WO-20260728-002"
    }
  }
};

function showDashboardToast(message, type = "success") {
  const toast = document.getElementById("dashToast");
  if (!toast) return;
  toast.textContent = message;
  toast.className = `console-toast is-visible is-${type}`;
  setTimeout(() => { toast.classList.remove("is-visible"); }, 3600);
}

function refreshDashboardNow() {
  countdownSeconds = 30;
  const timerEl = document.getElementById("dashRefreshTimer");
  if (timerEl) timerEl.textContent = "30s";
  renderDashboardView();
  showDashboardToast("综合看板事实快照已更新");
}

function renderDashboardView() {
  const container = document.getElementById("dashboardGrid");
  const timeLabel = document.getElementById("dashTimeRangeLabel");
  const lastSync = document.getElementById("dashLastSync");
  const data = dashboardFactData[currentTimeRange] || dashboardFactData.today;

  if (timeLabel) timeLabel.textContent = data.timeRangeLabel;
  if (lastSync) lastSync.textContent = data.syncTime;

  if (!container) return;

  container.innerHTML = `
    <!-- 1. 库存事实卡片 -->
    <div style="display:grid;grid-template-columns:repeat(auto-fit, minmax(360px, 1fr));gap:18px;">
      <section class="console-section" style="margin:0;">
        <div class="console-section-head">
          <h3>1. 库存实时事实 (Inventory)</h3>
          <a href="master-data.html" class="console-badge cyan" style="text-decoration:none;">查看明细 ↗</a>
        </div>
        <div class="console-kpi-strip" style="margin:14px;grid-template-columns:repeat(3, 1fr);">
          <div class="console-kpi-item highlight-cyan">
            <span>实物总库存</span>
            <strong>${data.inventory.onHand}</strong>
            <small>件</small>
          </div>
          <div class="console-kpi-item highlight-amber">
            <span>总业务预留</span>
            <strong>${data.inventory.reserved}</strong>
            <small>件</small>
          </div>
          <div class="console-kpi-item highlight-green">
            <span>总可用库存</span>
            <strong>${data.inventory.available}</strong>
            <small>件</small>
          </div>
        </div>
        <div class="console-fact-grid" style="padding-top:0;">
          <div class="console-fact-item">
            <span class="console-fact-label">质量隔离位 (QH-01)</span>
            <span class="console-fact-val" style="color:var(--c-red);">${data.inventory.qualityHold} 件 (禁止正常使用)</span>
          </div>
          <div class="console-fact-item">
            <span class="console-fact-label">发货暂存位 (SHP-01)</span>
            <span class="console-fact-val" style="color:var(--c-amber);">${data.inventory.shippingStaged} 件 (已拣待发)</span>
          </div>
        </div>
      </section>

      <!-- 2. 采购与销售履约 -->
      <section class="console-section" style="margin:0;">
        <div class="console-section-head">
          <h3>2. 采购与销售双向履约 (Fulfillment)</h3>
          <a href="purchase-inbound.html" class="console-badge cyan" style="text-decoration:none;">采购/销售 ↗</a>
        </div>
        <div class="console-fact-grid">
          <div class="console-fact-item">
            <span class="console-fact-label">采购：外观待收货</span>
            <span class="console-fact-val">${data.fulfillment.poPendingArrival} 件 (拒收待补)</span>
          </div>
          <div class="console-fact-item">
            <span class="console-fact-label">采购：到货待质检</span>
            <span class="console-fact-val highlight">${data.fulfillment.poPendingInspect} 件 (隔离中)</span>
          </div>
          <div class="console-fact-item">
            <span class="console-fact-label">采购：合格待上架</span>
            <span class="console-fact-val" style="color:var(--c-green);">${data.fulfillment.poPendingPutaway} 件 (暂存中)</span>
          </div>
          <div class="console-fact-item">
            <span class="console-fact-label">销售：待直接拣货</span>
            <span class="console-fact-val">${data.fulfillment.soPendingPick} 件</span>
          </div>
          <div class="console-fact-item">
            <span class="console-fact-label">销售：发货暂存中</span>
            <span class="console-fact-val highlight">${data.fulfillment.soShippingStaged} 件</span>
          </div>
          <div class="console-fact-item">
            <span class="console-fact-label">销售：已发货确认</span>
            <span class="console-fact-val" style="color:var(--c-green);">${data.fulfillment.soShipped} 件</span>
          </div>
        </div>
      </section>
    </div>

    <!-- 3. 生产制造与质量 -->
    <div style="display:grid;grid-template-columns:repeat(auto-fit, minmax(360px, 1fr));gap:18px;">
      <section class="console-section" style="margin:0;">
        <div class="console-section-head">
          <h3>3. 生产工单制造执行 (MES)</h3>
          <a href="work-order.html" class="console-badge cyan" style="text-decoration:none;">制造控制台 ↗</a>
        </div>
        <div class="console-kpi-strip" style="margin:14px;grid-template-columns:repeat(3, 1fr);">
          <div class="console-kpi-item">
            <span>已下达工单</span>
            <strong>${data.manufacturing.released}</strong>
            <small>单</small>
          </div>
          <div class="console-kpi-item highlight-amber">
            <span>执行中工单</span>
            <strong>${data.manufacturing.inProgress}</strong>
            <small>单</small>
          </div>
          <div class="console-kpi-item highlight-green">
            <span>正常完工</span>
            <strong>${data.manufacturing.completedNormal}</strong>
            <small>单</small>
          </div>
        </div>
        <div class="console-fact-grid" style="padding-top:0;">
          <div class="console-fact-item">
            <span class="console-fact-label">待审核工单</span>
            <span class="console-fact-val">${data.manufacturing.submitted} 单</span>
          </div>
          <div class="console-fact-item">
            <span class="console-fact-label">人工完成工单</span>
            <span class="console-fact-val" style="color:var(--c-amber);">${data.manufacturing.completedManual} 单 (终止余量)</span>
          </div>
        </div>
      </section>

      <section class="console-section" style="margin:0;">
        <div class="console-section-head">
          <h3>4. 质量检验统计 (Quality)</h3>
          <span class="console-badge green">综合合格率 ${data.quality.passRate}</span>
        </div>
        <div class="console-fact-grid">
          <div class="console-fact-item">
            <span class="console-fact-label">采购到货待检</span>
            <span class="console-fact-val">${data.quality.poPendingBatch} 批次 (QH-01)</span>
          </div>
          <div class="console-fact-item">
            <span class="console-fact-label">生产报工待检</span>
            <span class="console-fact-val">${data.quality.mesPendingBatch} 批次 (工单30)</span>
          </div>
          <div class="console-fact-item">
            <span class="console-fact-label">质量放行率</span>
            <span class="console-fact-val highlight">${data.quality.passRate}</span>
          </div>
          <div class="console-fact-item">
            <span class="console-fact-label">报废扣减总数</span>
            <span class="console-fact-val" style="color:var(--c-red);">${data.quality.scrappedTotal} 件 (形成库存流水)</span>
          </div>
        </div>
      </section>
    </div>

    <!-- 5. 设备与告警 & 黄金闭环追溯 -->
    <div style="display:grid;grid-template-columns:repeat(auto-fit, minmax(360px, 1fr));gap:18px;">
      <section class="console-section" style="margin:0;">
        <div class="console-section-head">
          <h3>5. IoT 设备接入与告警监控 (IoT)</h3>
          <a href="device-alarm.html" class="console-badge cyan" style="text-decoration:none;">设备与告警 ↗</a>
        </div>
        <div class="console-kpi-strip" style="margin:14px;grid-template-columns:repeat(3, 1fr);">
          <div class="console-kpi-item">
            <span>在线设备总数</span>
            <strong>${data.devices.online} / ${data.devices.total}</strong>
          </div>
          <div class="console-kpi-item highlight-green">
            <span>正常运行</span>
            <strong>${data.devices.running}</strong>
          </div>
          <div class="console-kpi-item highlight-red">
            <span>活动超温告警</span>
            <strong>${data.alarms.active}</strong>
            <small>DEV-C12</small>
          </div>
        </div>
        <div class="console-fact-grid" style="padding-top:0;">
          <div class="console-fact-item">
            <span class="console-fact-label">未确认告警</span>
            <span class="console-fact-val" style="color:var(--c-green);">${data.alarms.unacked} (全部已响应)</span>
          </div>
          <div class="console-fact-item">
            <span class="console-fact-label">24h已恢复告警</span>
            <span class="console-fact-val">${data.alarms.recovered24h} 起</span>
          </div>
        </div>
      </section>

      <section class="console-section" style="margin:0;">
        <div class="console-section-head">
          <h3>6. 黄金闭环跨域追溯 (Traceability)</h3>
          <a href="ai-assistant.html" class="console-badge purple" style="text-decoration:none;">AI 智能追溯 ↗</a>
        </div>
        <div class="console-fact-grid">
          <div class="console-fact-item">
            <span class="console-fact-label">闭环全链路覆盖率</span>
            <span class="console-fact-val highlight">${data.traceability.goldenChainCoverage}</span>
          </div>
          <div class="console-fact-item">
            <span class="console-fact-label">跨域异常断点</span>
            <span class="console-fact-val" style="color:var(--c-amber);">${data.traceability.brokenChains} 处</span>
          </div>
          <div class="console-fact-item" style="grid-column:1 / -1;">
            <span class="console-fact-label">最近协同链路</span>
            <span class="console-fact-val" style="font-size:12px;font-family:monospace;color:var(--c-cyan);">
              ${data.traceability.lastTracedDoc}
            </span>
          </div>
        </div>
      </section>
    </div>
  `;
}

function initDashboardConsole() {
  const tabs = document.querySelectorAll(".console-scenario-tabs button");
  tabs.forEach(tab => {
    tab.addEventListener("click", () => {
      tabs.forEach(t => {
        t.classList.remove("is-active");
        t.setAttribute("aria-pressed", "false");
      });
      tab.classList.add("is-active");
      tab.setAttribute("aria-pressed", "true");
      currentTimeRange = tab.dataset.range;
      renderDashboardView();
    });
  });

  renderDashboardView();

  // 30s 定时刷新倒计时
  if (refreshInterval) clearInterval(refreshInterval);
  refreshInterval = setInterval(() => {
    countdownSeconds--;
    const timerEl = document.getElementById("dashRefreshTimer");
    if (timerEl) timerEl.textContent = `${countdownSeconds}s`;
    if (countdownSeconds <= 0) {
      countdownSeconds = 30;
      renderDashboardView();
    }
  }, 1000);
}

document.addEventListener("DOMContentLoaded", initDashboardConsole);
