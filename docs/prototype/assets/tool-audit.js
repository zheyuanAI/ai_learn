/**
 * AI 工具调用审计高保真交互控制台 (tool-audit.js)
 * 严格执行已冻结业务规则：
 * 1. 成功、失败、超时和拒绝调用的受控工具均必须留痕；
 * 2. 审计至少记录用户、会话、工具、输入/输出摘要、来源、时间范围、实际模型、耗时、状态和错误原因；
 * 3. 回答与审计通过同一 request_id 关联；
 * 4. 严禁记录登录密码、设备凭证、中转站密钥等无关敏感数据。
 */

/**
 * HTML 转义函数，防止 XSS / HTML 注入
 * @param {string} str - 待转义字符串
 * @returns {string} - 转义后的安全 HTML 字符串
 */
function escapeHtml(str) {
  if (typeof str !== "string") return str == null ? "" : String(str);
  return str
    .replace(/&/g, "&amp;")
    .replace(/</g, "&lt;")
    .replace(/>/g, "&gt;")
    .replace(/"/g, "&quot;")
    .replace(/'/g, "&#39;");
}

const mockAuditLogs = [
  {
    id: "req-ai-20260826-0182",
    time: "2026-08-26 16:30:05",
    tenant: "tenant_demo_a",
    user: "sales.liu",
    tool: "queryTrace",
    latency: "520ms",
    status: "Success",
    model: "grok-4.6",
    timeRange: "2026-08-26 00:00 - 16:30",
    inputParams: { orderId: "SO-20260826-018" },
    outputSummary: "成功聚合返回 SO-018 关联的工单 WO-018、采购 PO-001、设备 DEV-C12 告警及出库事实",
    sources: ["platform-core", "platform-iot"]
  },
  {
    id: "req-ai-20260826-0185",
    time: "2026-08-26 16:25:12",
    tenant: "tenant_demo_a",
    user: "iot.engineer",
    tool: "queryDeviceAlarm",
    latency: "290ms",
    status: "Success",
    model: "grok-4.6",
    timeRange: "2026-08-26 16:00 - 16:30",
    inputParams: { deviceId: "DEV-C12" },
    outputSummary: "返回 ALM-003 超温 78.5℃ 告警事实，关联工序 OE-033 与工单 WO-018",
    sources: ["platform-iot"]
  },
  {
    id: "req-ai-20260826-0188",
    time: "2026-08-26 16:15:44",
    tenant: "tenant_demo_a",
    user: "admin.zhang",
    tool: "generateDailyOperationReport",
    latency: "980ms",
    status: "Success",
    model: "grok-4.6",
    timeRange: "2026-08-26 08:00 - 16:30",
    inputParams: { date: "2026-08-26", shift: "day" },
    outputSummary: "完成今日白班仓储履约、制造执行、设备告警 3 域事实综合简报生成",
    sources: ["platform-core", "platform-iot"]
  },
  {
    id: "req-ai-20260826-0199",
    time: "2026-08-26 15:50:20",
    tenant: "tenant_demo_a",
    user: "buyer.chen",
    tool: "updatePurchaseOrder",
    latency: "45ms",
    status: "Denied",
    model: "grok-4.6",
    timeRange: "即时拦截",
    inputParams: { orderId: "PO-20260826-001", action: "approve" },
    outputSummary: "【安全防御拦截】调用前策略拒绝：AI 严禁执行写操作 (GUARD_WRITE_BLOCKED)",
    sources: ["Guardrail Policy Gateway"]
  },
  {
    id: "req-ai-20260826-0170",
    time: "2026-08-26 14:10:02",
    tenant: "tenant_demo_a",
    user: "wh.operator",
    tool: "queryInventoryByProductAndWarehouse",
    latency: "3000ms",
    status: "Timeout",
    model: "grok-4.6",
    timeRange: "2026-08-26 00:00 - 14:00",
    inputParams: { sku: "RM-SERVO-ST", warehouse: "WH-RM-01" },
    outputSummary: "上游应用服务响应超时（超过 3.0s 限制），触发熔断保护",
    sources: ["platform-core"]
  }
];

let currentAuditFilter = "all";
let auditSearchQuery = "";
let selectedAuditId = "req-ai-20260826-0182";

function showAuditToast(message, type = "success") {
  const toast = document.getElementById("auditToast");
  if (!toast) return;
  toast.textContent = message;
  toast.className = `console-toast is-visible is-${type}`;
  setTimeout(() => { toast.classList.remove("is-visible"); }, 3600);
}

function renderAuditTable() {
  const tbody = document.getElementById("auditTableBody");
  const countEl = document.getElementById("auditCountBadge");
  if (!tbody || !countEl) return;

  const filtered = mockAuditLogs.filter(log => {
    if (currentAuditFilter !== "all" && log.status !== currentAuditFilter) return false;
    if (auditSearchQuery) {
      const q = auditSearchQuery.toLowerCase();
      const matchId = log.id.toLowerCase().includes(q);
      const matchUser = log.user.toLowerCase().includes(q);
      const matchTool = log.tool.toLowerCase().includes(q);
      if (!matchId && !matchUser && !matchTool) return false;
    }
    return true;
  });

  countEl.textContent = `${filtered.length} 条记录`;

  if (filtered.length === 0) {
    tbody.innerHTML = `<tr><td colspan="6" style="text-align:center;padding:24px;color:var(--c-muted);">无匹配审计记录</td></tr>`;
    return;
  }

  tbody.innerHTML = filtered.map(log => {
    const isSelected = log.id === selectedAuditId;
    const isSuccess = log.status === "Success";
    const isDenied = log.status === "Denied";
    const isTimeout = log.status === "Timeout";

    return `
      <tr tabindex="0" role="button" aria-pressed="${isSelected}" onclick="selectAuditRecord('${escapeHtml(log.id)}')" onkeydown="if(event.key==='Enter'||event.key===' '){event.preventDefault();selectAuditRecord('${escapeHtml(log.id)}');}" style="cursor:pointer;background:${isSelected ? 'rgba(113,225,220,0.08)' : 'transparent'};">
        <td><small>${escapeHtml(log.time)}</small></td>
        <td><code>${escapeHtml(log.id)}</code></td>
        <td><strong>${escapeHtml(log.user)}</strong> <small style="color:var(--c-dim);">(${escapeHtml(log.tenant)})</small></td>
        <td><code style="color:var(--c-cyan);">${escapeHtml(log.tool)}</code></td>
        <td><small>${escapeHtml(log.latency)}</small></td>
        <td>
          <span class="console-badge ${isSuccess ? 'green' : isDenied ? 'red' : 'amber'}">
            ${escapeHtml(log.status)}
          </span>
        </td>
      </tr>
    `;
  }).join("");
}

function selectAuditRecord(id) {
  selectedAuditId = id;
  renderAuditTable();
  renderAuditDetailDrawer();
}

function renderAuditDetailDrawer() {
  const drawerEl = document.getElementById("auditDetailDrawer");
  const log = mockAuditLogs.find(l => l.id === selectedAuditId) || mockAuditLogs[0];
  if (!drawerEl || !log) return;

  const isSuccess = log.status === "Success";
  const isDenied = log.status === "Denied";

  drawerEl.innerHTML = `
    <header class="console-detail-head">
      <div>
        <div class="console-title-meta">
          <span class="console-module-code">AUDIT / REQUEST LOG</span>
          <span class="console-badge ${isSuccess ? 'green' : isDenied ? 'red' : 'amber'}">${escapeHtml(log.status)}</span>
        </div>
        <h2>${escapeHtml(log.id)}</h2>
        <p>调用时间：${escapeHtml(log.time)} · 耗时：${escapeHtml(log.latency)} · 模型：${escapeHtml(log.model)}</p>
      </div>
    </header>

    <div class="console-fact-grid">
      <div class="console-fact-item">
        <span class="console-fact-label">操作租户 / 用户</span>
        <span class="console-fact-val">${escapeHtml(log.tenant)} / ${escapeHtml(log.user)}</span>
      </div>
      <div class="console-fact-item">
        <span class="console-fact-label">调用工具名称</span>
        <span class="console-fact-val highlight">${escapeHtml(log.tool)}</span>
      </div>
      <div class="console-fact-item">
        <span class="console-fact-label">数据统计时间范围</span>
        <span class="console-fact-val">${escapeHtml(log.timeRange)}</span>
      </div>
      <div class="console-fact-item">
        <span class="console-fact-label">数据来源服务</span>
        <span class="console-fact-val">${log.sources.map(s => escapeHtml(s)).join(", ")}</span>
      </div>
    </div>

    <section class="console-section">
      <div class="console-section-head">
        <h3>工具入参 JSON (Input Payload)</h3>
        <small>校验租户隔离与参数合法性</small>
      </div>
      <pre style="padding:14px 18px;margin:0;font-size:12px;font-family:monospace;color:var(--c-cyan);background:rgba(0,0,0,0.3);overflow-x:auto;">${escapeHtml(JSON.stringify(log.inputParams, null, 2))}</pre>
    </section>

    <section class="console-section">
      <div class="console-section-head">
        <h3>执行结果与输出摘要 (Output Summary)</h3>
        <small>脱敏后的事实数据或拦截原因</small>
      </div>
      <div style="padding:14px 18px;font-size:12px;line-height:1.6;color:#c6d8df;white-space:pre-wrap;">
        ${escapeHtml(log.outputSummary)}
      </div>
    </section>
  `;
}

function exportAuditLogsJson() {
  const jsonStr = JSON.stringify(mockAuditLogs, null, 2);
  const blob = new Blob([jsonStr], { type: "application/json" });
  const url = URL.createObjectURL(blob);
  const a = document.createElement("a");
  a.href = url;
  a.download = `ai-tool-audit-${Date.now()}.json`;
  a.click();
  URL.revokeObjectURL(url);
  showAuditToast("审计日志 JSON 文件导出成功");
}

function initToolAuditConsole() {
  const tabs = document.querySelectorAll(".console-scenario-tabs button");
  tabs.forEach(tab => {
    tab.addEventListener("click", () => {
      tabs.forEach(t => {
        t.classList.remove("is-active");
        t.setAttribute("aria-pressed", "false");
      });
      tab.classList.add("is-active");
      tab.setAttribute("aria-pressed", "true");
      currentAuditFilter = tab.dataset.statusFilter;
      renderAuditTable();
    });
  });

  const searchInput = document.getElementById("auditSearchInput");
  searchInput?.addEventListener("input", (e) => {
    auditSearchQuery = e.target.value.trim();
    renderAuditTable();
  });

  renderAuditTable();
  renderAuditDetailDrawer();
}

document.addEventListener("DOMContentLoaded", initToolAuditConsole);
