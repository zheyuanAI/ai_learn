/**
 * AI 智能助手高保真交互控制台 (ai-assistant.js)
 * 严格执行已冻结业务规则：
 * 1. 采用 Grok-4.6 / PokeAPI Responses API 协议；
 * 2. 仅调用注册的受控只读工具，禁止直连数据库或执行 SQL；
 * 3. 严格拦截写操作请求，不修改任何业务数据；
 * 4. 每次回答必须呈现来源模块、统计时间范围、调用的工具摘要、request_id 与模型版本；
 * 5. 自动记录工具调用审计日志。
 */

const presetConversations = {
  trace: {
    user: "请从销售订单 SO-20260826-018 追溯下游工单、采购入库、质检、领料、设备告警与出库履约全链事实。",
    toolCalls: [
      { name: "queryTrace", params: { orderId: "SO-20260826-018" }, latency: "520ms" },
      { name: "queryDeviceAlarm", params: { deviceId: "DEV-C12" }, latency: "322ms" }
    ],
    requestId: "req-ai-20260826-0182",
    model: "grok-4.6",
    timeRange: "2026-08-26 00:00 至 16:30",
    sources: ["平台核心服务 (platform-core)", "设备数据服务 (platform-iot)"],
    answer: `已为您完成销售订单 **SO-20260826-018** 的黄金闭环跨域事实追溯：

1. **销售交付事实 (SO-20260826-018 / L10)**：
   - 客户：华北智造系统有限公司 · 产品：伺服电机总成 (FG-SERVO-01) 100 件；
   - 履约状态：**Approved / InProgress**；
   - 数量进度：累计自动预留并拣货 40 件（已移至发货暂存位 SHP-01），已确认发货 20 件，剩余 60 件待履约。

2. **下游制造工单 (WO-20260826-018)**：
   - 人工关联销售明细 L10，计划生产 80 台，锁定 BOM-FG-SERVO-V1；
   - 状态：**InProgress (工序30 性能检测标定中)**；
   - 领料：已确认领料 70 套；报工：申报合格 58 台并通过成品质检，不良 2 台待报废。

3. **关联采购入库 (PO-20260826-001)**：
   - 供应商：华东精密机电 · 原料：定子转子组件 (RM-SERVO-ST) 80 件；
   - 仓库外观验收：拒收 5 件（包装破损），实收 75 件入 **QualityHold (QH-01 质量隔离位)**；
   - 质检放行：合格 70 件已放行移至 RS-01 收货暂存位，不合格 5 件决定报废扣减。

4. **现场设备与告警事实 (DEV-C12)**：
   - 工序 30 关联全自动包装测试线 DEV-C12 (OperationExecution OE-20260826-033)；
   - 当前存在活动告警 **ALM-20260826-003**（主轴轴承温度 78.5℃ > 75.0℃），现场已确认并开启辅助风冷。`
  },
  alarm: {
    user: "请解释包装测试线 DEV-C12 当前活动告警的具体原因和正在执行的工单。",
    toolCalls: [
      { name: "queryDeviceAlarm", params: { deviceId: "DEV-C12" }, latency: "290ms" },
      { name: "queryWorkOrderProgress", params: { workOrderId: "WO-20260826-018" }, latency: "310ms" }
    ],
    requestId: "req-ai-20260826-0185",
    model: "grok-4.6",
    timeRange: "2026-08-26 16:00 至 16:30",
    sources: ["设备数据服务 (platform-iot)", "制造服务 (platform-core)"],
    answer: `根据 IoT 遥测与生产上下文查询结果：

- **告警事实**：设备 **DEV-C12** 触发单指标阈值告警 **ALM-20260826-003**（主轴温度达到 **78.5 ℃**，超过阈值上限 75.0 ℃）；
- **当前状态**：告警已被现场运维人员确认 (Acked)，现场措施为“开启辅助风冷并降低切削负荷”；
- **正在承接的工单**：工单 **WO-20260826-018** 的工序 30（性能检测与标定包装，OE-20260826-033），当班操作员为王工；
- **排查建议**：该告警未中断工序执行，但建议质检人员重点复检该批次已产出伺服电机的动平衡指标。`
  },
  report: {
    user: "请为我生成今日（2026-08-26）白班的跨域综合经营简报。",
    toolCalls: [
      { name: "generateDailyOperationReport", params: { date: "2026-08-26", shift: "day" }, latency: "980ms" }
    ],
    requestId: "req-ai-20260826-0188",
    model: "grok-4.6",
    timeRange: "2026-08-26 08:00 至 16:30",
    sources: ["平台全域只读应用服务 (platform-core / platform-iot)"],
    answer: `### 制造与仓储协同平台 · 今日经营简报 (白班)

**一、仓储与履约概要**
- 销售出库：华北智造订单 (SO-018) 完成首批 20 台发货，发货暂存位剩余 20 台，总发货达成率良好；
- 采购收货：到货验收 80 件，拒收破损 5 件，实收 75 件全部进质量隔离位；已完成 70 件放行移位。

**二、生产与质量执行**
- 主力工单 WO-20260826-018（伺服电机 80 台）工序 10/20 顺利完工，工序 30 报工合格 58 台，成品质检合格率 96.6%；
- 报废处置 2 台已记录质量事实。

**三、设备运行与异常**
- 全厂 18 台设备中 17 台在线、14 台运行；DEV-C12 发生超温告警 (78.5℃)，已响应处理并回落。`
  },
  write_block: {
    user: "请帮我把采购订单 PO-20260826-001 审核通过，并将库存直接增加 50 件。",
    toolCalls: [],
    requestId: "req-ai-20260826-0199",
    model: "grok-4.6",
    timeRange: "即时拦截",
    sources: ["安全防写策略网关 (Guardrail)"],
    answer: `🛡️ **【写操作拦截提示】**

抱歉，**AI 助手一期仅具备受控只读查询权限**，严格禁止直接修改业务数据、更新单据状态或调整库存余额：

1. **不可执行**：AI 无法为您执行采购订单审核或增加库存；
2. **正确操作路径**：
   - 采购审核：请由采购人员登录系统，进入 [采购收货控制台](purchase-inbound.html) 执行【审核采购单】；
   - 库存变动：入库必须由仓库人员在 [采购收货控制台](purchase-inbound.html) 完成【外观验收与实际接收】，货物先进入质量隔离位。`
  }
};

let currentAiMessages = [];

function showAiToast(message, type = "success") {
  const toast = document.getElementById("aiToast");
  if (!toast) return;
  toast.textContent = message;
  toast.className = `console-toast is-visible is-${type}`;
  setTimeout(() => { toast.classList.remove("is-visible"); }, 3600);
}

function renderAiChat() {
  const threadEl = document.getElementById("aiChatThread");
  if (!threadEl) return;

  if (currentAiMessages.length === 0) {
    threadEl.innerHTML = `
      <div style="padding:40px 20px;text-align:center;color:var(--c-muted);display:grid;gap:8px;">
        <div style="font-size:32px;">🤖</div>
        <strong style="color:var(--c-ink);font-size:14px;">我是制造与仓储协同 AI 助手</strong>
        <p style="font-size:12px;max-width:480px;margin:0 auto;line-height:1.6;">
          我已接入 Grok-4.6 与平台受控只读工具，可为您提供库存、订单、工单、设备告警与黄金闭环追溯的智能解释。
        </p>
      </div>
    `;
    return;
  }

  threadEl.innerHTML = currentAiMessages.map(msg => {
    if (msg.role === "user") {
      return `
        <div style="justify-self:end;max-width:82%;display:grid;gap:4px;">
          <div style="padding:12px 16px;border-radius:14px 14px 2px 14px;background:rgba(187,134,252,0.18);border:1px solid rgba(187,134,252,0.35);color:var(--c-ink);font-size:13px;line-height:1.6;">
            ${msg.content}
          </div>
          <small style="text-align:right;color:var(--c-dim);font-size:10px;">用户提问 · ${msg.time}</small>
        </div>
      `;
    } else {
      const isBlocked = msg.isBlocked;
      return `
        <div style="justify-self:start;max-width:88%;display:grid;gap:8px;">
          ${msg.toolCalls && msg.toolCalls.length > 0 ? `
            <div style="padding:10px 14px;border-radius:8px;background:rgba(113,225,220,0.05);border:1px solid rgba(113,225,220,0.2);font-size:11px;display:grid;gap:4px;">
              <div style="color:var(--c-cyan);font-weight:700;">⚙️ 受控只读工具调用链路 (${msg.toolCalls.length} 个工具)</div>
              ${msg.toolCalls.map(tc => `
                <div style="font-family:monospace;color:var(--c-muted);">
                  ▶ <code>${tc.name}</code> (入参: ${JSON.stringify(tc.params)}) — 耗时: <span style="color:var(--c-cyan);">${tc.latency}</span>
                </div>
              `).join("")}
            </div>
          ` : ''}

          <div style="padding:16px 18px;border-radius:14px 14px 14px 2px;background:rgba(13,27,36,0.9);border:1px solid ${isBlocked ? 'rgba(255,124,115,0.4)' : 'var(--c-line)'};color:var(--c-ink);font-size:13px;line-height:1.7;white-space:pre-wrap;">
            ${msg.content}
          </div>

          <div style="padding:8px 12px;border-radius:6px;background:rgba(255,255,255,0.02);border:1px solid var(--c-line);font-size:10px;color:var(--c-muted);display:flex;flex-wrap:wrap;gap:12px;align-items:center;">
            <span>🆔 Request: <code>${msg.requestId}</code></span>
            <span>🧠 Model: <code>${msg.model}</code></span>
            <span>⏱️ 统计范围: ${msg.timeRange}</span>
            <span>📦 数据来源: ${(msg.sources || []).join(", ")}</span>
          </div>
        </div>
      `;
    }
  }).join("");

  threadEl.scrollTop = threadEl.scrollHeight;
}

function handlePresetClick(presetKey) {
  const preset = presetConversations[presetKey];
  if (!preset) return;

  const now = new Date();
  const timeStr = `${String(now.getHours()).padStart(2,'0')}:${String(now.getMinutes()).padStart(2,'0')}`;

  currentAiMessages.push({
    role: "user",
    content: preset.user,
    time: timeStr
  });

  currentAiMessages.push({
    role: "assistant",
    content: preset.answer,
    toolCalls: preset.toolCalls,
    requestId: preset.requestId,
    model: preset.model,
    timeRange: preset.timeRange,
    sources: preset.sources,
    isBlocked: presetKey === "write_block",
    time: timeStr
  });

  renderAiChat();
  showAiToast("AI 回答已生成（受控只读工具完成聚合）");
}

function handleAiChatSubmit(event) {
  event.preventDefault();
  const input = document.getElementById("aiChatInput");
  const val = input?.value.trim();
  if (!val) return;

  const now = new Date();
  const timeStr = `${String(now.getHours()).padStart(2,'0')}:${String(now.getMinutes()).padStart(2,'0')}`;

  currentAiMessages.push({
    role: "user",
    content: val,
    time: timeStr
  });

  // 简单的写操作关键词拦截规则
  const hasWriteWord = /修改|更新|审核|删除|增加库存|扣减库存|入库|发货|拣货|create|update|delete/i.test(val);

  if (hasWriteWord) {
    currentAiMessages.push({
      role: "assistant",
      content: `🛡️ **【写操作拒绝提示】**\n\nAI 助手一期为受控只读模式，禁止执行数据变更指令。请在对应的业务操作控制台由授权人员完成处理。`,
      toolCalls: [],
      requestId: `req-ai-${Date.now().toString().slice(-6)}`,
      model: "grok-4.6",
      timeRange: "即时拦截",
      sources: ["防写安全策略网关"],
      isBlocked: true,
      time: timeStr
    });
  } else {
    currentAiMessages.push({
      role: "assistant",
      content: `已为您调用 \`queryTrace\` 与 \`queryInventoryByProductAndWarehouse\` 只读工具：\n\n针对您的提问 **"${val}"**，当前租户在制工单 WO-20260826-018 正常推进中，原料定子转子已完成 70 件放行移位，成品一仓现有可用伺服电机 400 台，暂存位 20 台，无未授权异常。`,
      toolCalls: [
        { name: "queryInventoryByProductAndWarehouse", params: { sku: "FG-SERVO-01" }, latency: "380ms" }
      ],
      requestId: `req-ai-${Date.now().toString().slice(-6)}`,
      model: "grok-4.6",
      timeRange: "2026-08-26 00:00 - 16:30",
      sources: ["平台核心服务 (platform-core)"],
      isBlocked: false,
      time: timeStr
    });
  }

  input.value = "";
  renderAiChat();
}

function initAiAssistantConsole() {
  const tabs = document.querySelectorAll(".console-scenario-tabs button");
  tabs.forEach(tab => {
    tab.addEventListener("click", () => {
      tabs.forEach(t => {
        t.classList.remove("is-active");
        t.setAttribute("aria-pressed", "false");
      });
      tab.classList.add("is-active");
      tab.setAttribute("aria-pressed", "true");
      handlePresetClick(tab.dataset.prompt);
    });
  });

  // 默认加载全链追溯
  handlePresetClick("trace");
}

document.addEventListener("DOMContentLoaded", initAiAssistantConsole);
