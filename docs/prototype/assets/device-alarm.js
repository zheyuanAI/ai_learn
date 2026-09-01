/**
 * IoT 设备与告警高保真交互控制台 (device-alarm.js)
 * 严格执行已冻结业务规则：
 * 1. 一期只接入使用 MQTT 的模拟设备，使用 QoS 1；
 * 2. 消息去重键优先使用 device_id + message_id，否则使用 device_id + sequence；重复消息不重复保存遥测、推进状态或生成告警；
 * 3. 区分 DeviceTelemetry（原始遥测追加）、DeviceStatus（状态快照）和 DeviceAlarm（告警事实）；
 * 4. 单指标阈值告警生命周期：已触发（Triggered） -> 已确认（Acked） -> 已恢复（Recovered）；
 * 5. 告警软关联 OperationExecution 与生产工单上下文，支持人工补充更正；
 * 6. 地图展示状态优先级：告警 > 离线 > 预警 > 正常。
 */

const roleLabelsForDevice = {
  engineer: "IoT人员 (iot.engineer)",
  operator: "仓库人员 (wh.operator)"
};

const deviceScenarios = {
  alarm_active: {
    featuredDeviceId: "DEV-C12",
    devices: [
      {
        id: "DEV-C12",
        name: "全自动包装测试线 C12",
        profile: "包装测试设备 (PRO-PACK-TEST)",
        area: "装配二车间 (AREA-PROD-02)",
        mapPointId: "MAP-PT-0017",
        onlineStatus: "Online",
        runningStatus: "Running",
        alarmStatus: "Alarm",
        lastSeenAt: "2026-08-26 16:15:20",
        lastMessageKey: "DEV-C12 + MSG-C12-0001842",
        telemetry: {
          temperature: 78.5,
          speed: 1200,
          current: 18.2,
          vibration: 2.1
        },
        alarm: {
          id: "ALM-20260826-003",
          rule: "主轴轴承超温告警 (> 75.0 ℃)",
          metric: "temperature",
          triggerValue: "78.5 ℃",
          threshold: "75.0 ℃",
          level: "严重",
          stage: "Triggered",
          triggeredAt: "2026-08-26 16:10:05",
          ackedAt: null,
          ackedBy: null,
          ackNote: null,
          recoveredAt: null,
          operationExecutionId: "OE-20260826-033",
          workOrderId: "WO-20260826-018",
          product: "伺服电机总成 (FG-SERVO-01)"
        },
        telemetryHistory: [71.2, 72.5, 74.0, 75.8, 77.2, 78.5]
      },
      {
        id: "DEV-B03",
        name: "智能立体库堆垛机 B03",
        profile: "立库堆垛设备 (PRO-ASRS-STACK)",
        area: "成品一仓 (WH-FG-01)",
        mapPointId: "MAP-PT-0003",
        onlineStatus: "Online",
        runningStatus: "Running",
        alarmStatus: "Normal",
        lastSeenAt: "2026-08-26 16:15:10",
        lastMessageKey: "DEV-B03 + MSG-B03-0004921",
        telemetry: {
          temperature: 42.1,
          speed: 850,
          current: 12.4,
          vibration: 0.8
        },
        alarm: null,
        telemetryHistory: [40.5, 41.0, 41.5, 42.0, 42.1, 42.1]
      },
      {
        id: "DEV-A01",
        name: "伺服高精冲压机 A01",
        profile: "金属成型设备 (PRO-PRESS-01)",
        area: "机加一车间 (AREA-PROD-01)",
        mapPointId: "MAP-PT-0001",
        onlineStatus: "Online",
        runningStatus: "Stopped",
        alarmStatus: "Normal",
        lastSeenAt: "2026-08-26 16:14:50",
        lastMessageKey: "DEV-A01 + MSG-A01-0003112",
        telemetry: {
          temperature: 36.2,
          speed: 0,
          current: 1.2,
          vibration: 0.1
        },
        alarm: null,
        telemetryHistory: [38.0, 37.5, 37.0, 36.8, 36.5, 36.2]
      }
    ]
  },
  alarm_acked: {
    featuredDeviceId: "DEV-C12",
    devices: [
      {
        id: "DEV-C12",
        name: "全自动包装测试线 C12",
        profile: "包装测试设备 (PRO-PACK-TEST)",
        area: "装配二车间 (AREA-PROD-02)",
        mapPointId: "MAP-PT-0017",
        onlineStatus: "Online",
        runningStatus: "Running",
        alarmStatus: "Alarm",
        lastSeenAt: "2026-08-26 16:20:00",
        lastMessageKey: "DEV-C12 + MSG-C12-0001850",
        telemetry: {
          temperature: 76.8,
          speed: 1000,
          current: 16.5,
          vibration: 1.8
        },
        alarm: {
          id: "ALM-20260826-003",
          rule: "主轴轴承超温告警 (> 75.0 ℃)",
          metric: "temperature",
          triggerValue: "78.5 ℃",
          threshold: "75.0 ℃",
          level: "严重",
          stage: "Acked",
          triggeredAt: "2026-08-26 16:10:05",
          ackedAt: "2026-08-26 16:12:30",
          ackedBy: "iot.engineer",
          ackNote: "现场已调低负荷并开启辅助风冷，继续观察温度回落趋势。",
          recoveredAt: null,
          operationExecutionId: "OE-20260826-033",
          workOrderId: "WO-20260826-018",
          product: "伺服电机总成 (FG-SERVO-01)"
        },
        telemetryHistory: [74.0, 75.8, 77.2, 78.5, 77.8, 76.8]
      }
    ]
  },
  alarm_recovered: {
    featuredDeviceId: "DEV-C12",
    devices: [
      {
        id: "DEV-C12",
        name: "全自动包装测试线 C12",
        profile: "包装测试设备 (PRO-PACK-TEST)",
        area: "装配二车间 (AREA-PROD-02)",
        mapPointId: "MAP-PT-0017",
        onlineStatus: "Online",
        runningStatus: "Running",
        alarmStatus: "Normal",
        lastSeenAt: "2026-08-26 16:35:00",
        lastMessageKey: "DEV-C12 + MSG-C12-0001880",
        telemetry: {
          temperature: 71.2,
          speed: 1200,
          current: 15.8,
          vibration: 1.2
        },
        alarm: {
          id: "ALM-20260826-003",
          rule: "主轴轴承超温告警 (> 75.0 ℃)",
          metric: "temperature",
          triggerValue: "78.5 ℃",
          threshold: "75.0 ℃",
          level: "严重",
          stage: "Recovered",
          triggeredAt: "2026-08-26 16:10:05",
          ackedAt: "2026-08-26 16:12:30",
          ackedBy: "iot.engineer",
          ackNote: "现场辅助风冷生效，温度已恢复正常。",
          recoveredAt: "2026-08-26 16:32:10",
          operationExecutionId: "OE-20260826-033",
          workOrderId: "WO-20260826-018",
          product: "伺服电机总成 (FG-SERVO-01)"
        },
        telemetryHistory: [78.5, 77.2, 75.0, 73.5, 72.0, 71.2]
      }
    ]
  }
};

let currentDeviceScenario = "alarm_active";
let currentDevices = JSON.parse(JSON.stringify(deviceScenarios.alarm_active.devices));
let currentDeviceRole = "engineer";
let selectedDeviceId = "DEV-C12";
let deviceStatusFilter = "all";
let deviceSearchQuery = "";

function getSelectedDevice() {
  return currentDevices.find(d => d.id === selectedDeviceId) || currentDevices[0];
}

function showDeviceToast(message, type = "success") {
  const toast = document.getElementById("deviceToast");
  if (!toast) return;
  toast.textContent = message;
  toast.className = `console-toast is-visible is-${type}`;
  setTimeout(() => { toast.classList.remove("is-visible"); }, 3600);
}

function renderDeviceQueue() {
  const listEl = document.getElementById("deviceList");
  const countEl = document.getElementById("deviceCount");
  if (!listEl || !countEl) return;

  const filtered = currentDevices.filter(dev => {
    if (deviceStatusFilter === "Running" && dev.runningStatus !== "Running") return false;
    if (deviceStatusFilter === "Alarm" && dev.alarmStatus !== "Alarm") return false;
    if (deviceStatusFilter === "Offline" && dev.onlineStatus !== "Offline") return false;

    if (deviceSearchQuery) {
      const q = deviceSearchQuery.toLowerCase();
      const matchId = dev.id.toLowerCase().includes(q);
      const matchName = dev.name.toLowerCase().includes(q);
      const matchArea = dev.area.toLowerCase().includes(q);
      if (!matchId && !matchName && !matchArea) return false;
    }
    return true;
  });

  countEl.textContent = filtered.length;

  if (filtered.length === 0) {
    listEl.innerHTML = '<div style="padding:20px;text-align:center;color:var(--c-muted);font-size:12px;">无匹配设备</div>';
    return;
  }

  listEl.innerHTML = filtered.map(dev => {
    const isSelected = dev.id === selectedDeviceId ? "is-active" : "";
    const isAlarm = dev.alarmStatus === "Alarm";
    const accentClass = isAlarm ? "alarm" : dev.runningStatus === "Running" ? "running" : "draft";

    return `
      <div class="console-card ${isSelected}" tabindex="0" role="button" aria-label="设备 ${dev.id} ${dev.name}" onclick="selectDevice('${dev.id}')" onkeydown="if(event.key==='Enter'||event.key===' '){event.preventDefault();selectDevice('${dev.id}');}">
        <span class="console-card-accent ${accentClass}"></span>
        <div class="console-card-top">
          <strong>${dev.id}</strong>
          <em class="${isAlarm ? 'alarm' : ''}">${isAlarm ? '超温告警' : dev.runningStatus}</em>
        </div>
        <div class="console-card-title">${dev.name}</div>
        <div style="font-size:11px;color:var(--c-cyan);">${dev.area}</div>
        <div class="console-card-foot">
          <span>温度: ${dev.telemetry.temperature} ℃</span>
          <span class="console-badge ${isAlarm ? 'red' : 'green'}">${isAlarm ? '告警' : '正常'}</span>
        </div>
      </div>
    `;
  }).join("");
}

function selectDevice(deviceId) {
  selectedDeviceId = deviceId;
  renderDeviceQueue();
  renderDeviceDetail();
}

function renderDeviceDetail() {
  const detailEl = document.getElementById("deviceDetail");
  const dev = getSelectedDevice();
  if (!detailEl || !dev) return;

  const alarm = dev.alarm;
  const canAck = currentDeviceRole === "engineer" && dev.alarm && (dev.alarm.stage === "Triggered" || (dev.alarm.stage === "Recovered" && !dev.alarm.ackedAt));
  const canEditContext = currentDeviceRole === "engineer" && dev.alarm !== null;

  const alarmStageBadge = alarm
    ? (alarm.stage === 'Triggered'
        ? 'red'
        : (alarm.stage === 'Acked' || (alarm.stage === 'Recovered' && !alarm.ackedAt) ? 'amber' : 'green'))
    : '';
  const alarmStageText = alarm
    ? (alarm.stage === 'Triggered'
        ? 'Triggered (未确认)'
        : (alarm.stage === 'Acked'
            ? 'Acked (已确认待恢复)'
            : (!alarm.ackedAt ? 'Recovered (待确认)' : 'Recovered (已闭环)')))
    : '';

  detailEl.innerHTML = `
    <header class="console-detail-head">
      <div>
        <div class="console-title-meta">
          <span class="console-module-code">IOT / TELEMETRY &amp; ALARM</span>
          <span class="console-badge ${dev.alarmStatus === 'Alarm' ? 'red' : 'green'}">${dev.alarmStatus === 'Alarm' ? '超温告警' : '运行正常'}</span>
        </div>
        <h2>${dev.name} (${dev.id})</h2>
        <p>设备类型：${dev.profile} · 安装位置：${dev.area} · 地图点位：${dev.mapPointId}</p>
      </div>
      <div class="console-detail-status">
        <span class="console-badge amber">通信去重键: ${dev.lastMessageKey}</span>
        <small>最后通信时间: ${dev.lastSeenAt}</small>
      </div>
    </header>

    <div class="console-state-explainer">
      <div>
        <span>当前设备状态快照</span>
        <strong>${dev.onlineStatus} / ${dev.runningStatus}</strong>
        <small>${dev.alarmStatus === 'Alarm' ? '活动告警中' : (alarm && alarm.stage === 'Recovered' && !alarm.ackedAt ? '已恢复待确认' : '无未恢复告警')}</small>
      </div>
      <div class="console-state-divider">→</div>
      <div>
        <span>实时遥测指标</span>
        <strong>主轴温度 ${dev.telemetry.temperature} ℃ / 转速 ${dev.telemetry.speed} RPM</strong>
        <small>电流: ${dev.telemetry.current} A · 振动: ${dev.telemetry.vibration} mm/s</small>
      </div>
      <p>
        <strong>QoS 1 幂等与事实边界：</strong><br/>
        以 <code>device_id + message_id</code> 幂等去重；遥测只追加不覆盖历史；状态由最新快照驱动；告警独立记录生命周期。
      </p>
    </div>

    ${alarm ? `
      <section class="console-section" style="border-color:${alarm.stage === 'Recovered' ? (alarm.ackedAt ? 'var(--c-green)' : 'var(--c-amber)') : 'var(--c-red)'};background:${alarm.stage === 'Recovered' ? (alarm.ackedAt ? 'rgba(124,224,167,0.04)' : 'rgba(243,180,93,0.06)') : 'rgba(255,124,115,0.06)'};">
        <div class="console-section-head">
          <h3 style="color:${alarm.stage === 'Recovered' ? (alarm.ackedAt ? 'var(--c-green)' : 'var(--c-amber)') : 'var(--c-red)'};">
            ${alarm.stage === 'Triggered' ? '🚨 活动单指标阈值告警' : alarm.stage === 'Acked' ? '⚠️ 告警已确认待恢复' : (!alarm.ackedAt ? '⚠️ 告警已恢复（待人工确认）' : '✅ 告警已恢复闭环')} (${alarm.id})
          </h3>
          <span class="console-badge ${alarmStageBadge}">${alarmStageText}</span>
        </div>
        <div class="console-fact-grid">
          <div class="console-fact-item">
            <span class="console-fact-label">告警规则</span>
            <span class="console-fact-val">${alarm.rule}</span>
          </div>
          <div class="console-fact-item">
            <span class="console-fact-label">当前/最新指标值</span>
            <span class="console-fact-val highlight">${alarm.triggerValue} (阈值 ${alarm.threshold})</span>
          </div>
          <div class="console-fact-item">
            <span class="console-fact-label">首次触发时间</span>
            <span class="console-fact-val">${alarm.triggeredAt}</span>
          </div>
          <div class="console-fact-item">
            <span class="console-fact-label">确认状态</span>
            <span class="console-fact-val">${alarm.ackedBy ? `${alarm.ackedBy} (${alarm.ackedAt})` : '待确认'}</span>
          </div>
          <div class="console-fact-item">
            <span class="console-fact-label">恢复时间</span>
            <span class="console-fact-val">${alarm.recoveredAt || '尚未恢复'}</span>
          </div>
          <div class="console-fact-item">
            <span class="console-fact-label">工序执行上下文 (OE)</span>
            <span class="console-fact-val" style="color:var(--c-cyan);">${alarm.operationExecutionId} · ${alarm.workOrderId}</span>
          </div>
        </div>
        ${alarm.ackNote ? `
          <div style="padding:10px 18px;font-size:11px;color:var(--c-muted);border-top:1px solid var(--c-line);">
            <strong style="color:var(--c-amber);">人工确认意见：</strong>${alarm.ackNote}
          </div>
        ` : ''}
      </section>
    ` : ''}

    <div class="console-kpi-strip">
      <div class="console-kpi-item ${dev.telemetry.temperature > 75 ? 'highlight-red' : 'highlight-green'}">
        <span>主轴温度 (℃)</span>
        <strong>${dev.telemetry.temperature} <small>℃</small></strong>
        <small>阈值上限: 75.0 ℃</small>
      </div>
      <div class="console-kpi-item highlight-cyan">
        <span>主轴转速 (RPM)</span>
        <strong>${dev.telemetry.speed} <small>RPM</small></strong>
        <small>额定: 1200 RPM</small>
      </div>
      <div class="console-kpi-item">
        <span>运行电流 (A)</span>
        <strong>${dev.telemetry.current} <small>A</small></strong>
        <small>负载电流</small>
      </div>
      <div class="console-kpi-item">
        <span>机身振动 (mm/s)</span>
        <strong>${dev.telemetry.vibration} <small>mm/s</small></strong>
        <small>正常范围 &lt; 3.0</small>
      </div>
    </div>

    <section class="console-section">
      <div class="console-section-head">
        <h3>实时遥测趋势曲线 (Temperature Telemetry Trend)</h3>
        <small>最近 6 次有效上报遥测点</small>
      </div>
      <div style="padding:20px;display:grid;gap:12px;">
        <div class="chart-bars" style="position:relative;height:120px;left:0;right:0;bottom:0;">
          ${dev.telemetryHistory.map(val => {
            const hPct = Math.min(100, Math.max(10, (val / 90) * 100));
            const isOver = val > 75;
            return `
              <div style="flex:1;display:grid;justify-items:center;gap:6px;align-items:end;">
                <span style="font-size:10px;color:${isOver ? 'var(--c-red)' : 'var(--c-cyan)'};font-weight:700;">${val}℃</span>
                <span style="width:100%;height:${hPct}%;border-radius:6px 6px 0 0;background:${isOver ? 'linear-gradient(180deg, var(--c-red), rgba(255,124,115,0.3))' : 'linear-gradient(180deg, var(--c-cyan), rgba(113,225,220,0.2))'};"></span>
              </div>
            `;
          }).join("")}
        </div>
      </div>
    </section>

    <div class="console-action-panel">
      <div class="console-action-group-head">
        <strong>可执行设备与告警操作</strong>
        <span>当前身份：${roleLabelsForDevice[currentDeviceRole]}</span>
      </div>

      <div class="console-action-buttons">
        <button class="console-action-btn primary" onclick="openDeviceActionDialog('simulate_normal')">
          <span class="console-action-title">模拟正常遥测上报</span>
          <span class="console-action-desc">上报温度 71.2℃，MQTT QoS 1 正常心跳更新快照</span>
          <span class="console-action-perm">protocol:simulate (IoT)</span>
        </button>

        <button class="console-action-btn warning" onclick="openDeviceActionDialog('simulate_alarm')">
          <span class="console-action-title">模拟超温异常上报</span>
          <span class="console-action-desc">上报温度 78.5℃ > 75℃，触发单指标告警并关联工序</span>
          <span class="console-action-perm">protocol:simulate (IoT)</span>
        </button>

        <button class="console-action-btn primary" onclick="openDeviceActionDialog('simulate_dup')">
          <span class="console-action-title">模拟重复消息去重</span>
          <span class="console-action-desc">发送相同 message_id 报文，演示幂等去重拦截</span>
          <span class="console-action-perm">protocol:simulate (IoT)</span>
        </button>

        <button class="console-action-btn primary" ${canAck ? '' : 'disabled'} onclick="openDeviceActionDialog('ack_alarm')">
          <span class="console-action-title">人工确认告警</span>
          <span class="console-action-desc">现场确认告警，记录确认人、时间与处理措施</span>
          <span class="console-action-perm">alarm:ack (IoT人员)</span>
        </button>

        <button class="console-action-btn primary" ${canEditContext ? '' : 'disabled'} onclick="openDeviceActionDialog('edit_context')">
          <span class="console-action-title">补充工序执行上下文</span>
          <span class="console-action-desc">人工补充或更正关联的 OperationExecution 与工单</span>
          <span class="console-action-perm">alarm:context:edit (IoT人员)</span>
        </button>
      </div>
    </div>
  `;
}

function openDeviceActionDialog(actionType) {
  const dialogEl = document.getElementById("deviceActionDialog");
  const dev = getSelectedDevice();
  if (!dialogEl || !dev) return;

  if (actionType === "ack_alarm" || actionType === "edit_context") {
    if (currentDeviceRole !== "engineer") {
      showDeviceToast("权限不足：该操作仅允许 IoT人员 (iot.engineer) 执行", "danger");
      return;
    }
  }

  let formHtml = "";
  let dialogTitle = "";
  let impactNote = "";

  if (actionType === "simulate_normal") {
    dialogTitle = "模拟 MQTT 正常遥测上报";
    impactNote = "设备通过 MQTT QoS 1 发布正常指标（温度 71.2℃），更新设备状态快照；若存在活动告警则自动触发恢复。";
    formHtml = `
      <div class="console-form-field">
        <span>上报温度值 (temperature)</span>
        <input id="dlg_sim_temp" type="number" step="0.1" value="71.2" />
      </div>
      <div class="console-form-field">
        <span>模拟 Message ID</span>
        <input id="dlg_sim_msgid" type="text" value="MSG-C12-${Date.now().toString().slice(-6)}" />
      </div>
    `;
  } else if (actionType === "simulate_alarm") {
    dialogTitle = "模拟 MQTT 超温告警上报";
    impactNote = "设备上报温度 78.5℃，超过规则上限 75.0℃；系统创建 ALM 告警事实并自动关联 OE-20260826-033 工序执行。";
    formHtml = `
      <div class="console-form-field">
        <span>超温遥测值 <b>*</b></span>
        <input id="dlg_sim_temp_alarm" type="number" step="0.1" value="78.5" />
      </div>
      <div class="console-form-field">
        <span>模拟 Message ID</span>
        <input id="dlg_sim_msgid_alarm" type="text" value="MSG-C12-${Date.now().toString().slice(-6)}" />
      </div>
    `;
  } else if (actionType === "simulate_dup") {
    dialogTitle = "模拟 MQTT 重复消息幂等拦截";
    impactNote = `发送与上一条完全相同的去重键（${dev.lastMessageKey}），系统校验后按幂等成功返回，不重复写遥测、不生成重复告警。`;
    formHtml = `
      <div class="console-form-field">
        <span>去重标识 (device_id + message_id)</span>
        <input type="text" readonly value="${dev.lastMessageKey}" style="background:rgba(255,255,255,0.05);" />
      </div>
    `;
  } else if (actionType === "ack_alarm") {
    dialogTitle = "现场人工确认设备告警";
    impactNote = "记录确认人、时间和排查措施，告警状态进入 Acked，等待遥测回落自动恢复。";
    formHtml = `
      <div class="console-form-field">
        <span>告警编号</span>
        <input type="text" readonly value="${dev.alarm ? dev.alarm.id : '-'}" style="background:rgba(255,255,255,0.05);" />
      </div>
      <div class="console-form-field">
        <span>排查处理意见 <b>*</b></span>
        <input id="dlg_ack_note" type="text" value="现场已开启辅助风冷降温，降低主轴切削负荷，持续观察" />
      </div>
    `;
  } else if (actionType === "edit_context") {
    dialogTitle = "补充/修正工序执行上下文";
    impactNote = "更新告警关联的 OperationExecution 标识与工单编号，不修改原始告警时间与遥测。";
    formHtml = `
      <div class="console-form-field">
        <span>关联工序执行标识 (OperationExecution ID) <b>*</b></span>
        <input id="dlg_ctx_oe" type="text" value="${dev.alarm ? dev.alarm.operationExecutionId : 'OE-20260826-033'}" />
      </div>
      <div class="console-form-field">
        <span>关联生产工单号 (WorkOrder ID) <b>*</b></span>
        <input id="dlg_ctx_wo" type="text" value="${dev.alarm ? dev.alarm.workOrderId : 'WO-20260826-018'}" />
      </div>
    `;
  }

  dialogEl.innerHTML = `
    <div class="console-dialog-backdrop" onclick="closeDeviceActionDialog()"></div>
    <div class="console-dialog-panel">
      <header>
        <h2>${dialogTitle}</h2>
        <button type="button" class="console-dialog-close" onclick="closeDeviceActionDialog()">&times;</button>
      </header>
      <div class="console-dialog-context">
        <span>设备编号</span><strong>${dev.id} (${dev.name})</strong>
        <span>安装区域</span><strong>${dev.area}</strong>
      </div>
      <form onsubmit="handleDeviceActionSubmit(event, '${actionType}')">
        ${formHtml}
        <div class="console-dialog-impact">
          <span>业务与事实影响提示</span>
          <p>${impactNote}</p>
        </div>
        <footer>
          <button type="button" onclick="closeDeviceActionDialog()">取消</button>
          <button type="submit" class="primary">确认提交</button>
        </footer>
      </form>
    </div>
  `;
  dialogEl.hidden = false;
}

function closeDeviceActionDialog() {
  const dialogEl = document.getElementById("deviceActionDialog");
  if (dialogEl) dialogEl.hidden = true;
}

function handleDeviceActionSubmit(event, actionType) {
  event.preventDefault();
  const dev = getSelectedDevice();
  if (!dev) return;
  const now = new Date();
  const timeStr = `2026-08-26 ${String(now.getHours()).padStart(2,'0')}:${String(now.getMinutes()).padStart(2,'0')}:${String(now.getSeconds()).padStart(2,'0')}`;

  if (actionType === "simulate_normal" || actionType === "simulate_alarm") {
    const isNormalDlg = actionType === "simulate_normal";
    const tempInputId = isNormalDlg ? "dlg_sim_temp" : "dlg_sim_temp_alarm";
    const msgInputId = isNormalDlg ? "dlg_sim_msgid" : "dlg_sim_msgid_alarm";
    const defaultTemp = isNormalDlg ? 71.2 : 78.5;

    const inputVal = document.getElementById(tempInputId)?.value;
    const temp = (inputVal !== undefined && inputVal !== "" && !isNaN(parseFloat(inputVal)))
      ? parseFloat(inputVal)
      : defaultTemp;
    const msgId = document.getElementById(msgInputId)?.value || `MSG-${dev.id}-${Date.now().toString().slice(-6)}`;

    dev.telemetry.temperature = temp;
    dev.telemetryHistory.push(temp);
    if (dev.telemetryHistory.length > 6) dev.telemetryHistory.shift();
    dev.lastSeenAt = timeStr;
    dev.lastMessageKey = `${dev.id} + ${msgId}`;

    if (temp <= 75.0) {
      dev.alarmStatus = "Normal";
      if (dev.alarm && dev.alarm.stage !== "Recovered") {
        dev.alarm.stage = "Recovered";
        dev.alarm.recoveredAt = timeStr;
        if (!dev.alarm.ackedAt) {
          showDeviceToast(`MQTT 遥测上报成功：温度 ${temp}℃ <= 75.0℃，告警 (${dev.alarm.id}) 已恢复（待人工确认）`);
        } else {
          showDeviceToast(`MQTT 遥测上报成功：温度 ${temp}℃ <= 75.0℃，告警 (${dev.alarm.id}) 已恢复并完成闭环`);
        }
      } else {
        showDeviceToast(`MQTT 遥测上报成功：温度 ${temp}℃ <= 75.0℃，设备运行正常`);
      }
    } else {
      dev.alarmStatus = "Alarm";
      if (dev.alarm && dev.alarm.stage !== "Recovered") {
        // 已有活动告警（Triggered 或 Acked），更新最新值与最后上报时间，严禁覆盖首次触发时间 triggeredAt
        dev.alarm.triggerValue = `${temp} ℃`;
        dev.alarm.lastTriggeredAt = timeStr;
        showDeviceToast(`超温告警持续！温度 ${temp}℃ > 75.0℃，已更新活动告警 (${dev.alarm.id}) 最新遥测指标 (${temp}℃)`, "danger");
      } else {
        // 之前无告警或已恢复，生成新的单指标阈值告警
        dev.alarm = {
          id: `ALM-${Date.now().toString().slice(-6)}`,
          rule: "主轴轴承超温告警 (> 75.0 ℃)",
          metric: "temperature",
          triggerValue: `${temp} ℃`,
          threshold: "75.0 ℃",
          level: "严重",
          stage: "Triggered",
          triggeredAt: timeStr,
          lastTriggeredAt: timeStr,
          ackedAt: null,
          ackedBy: null,
          ackNote: null,
          recoveredAt: null,
          operationExecutionId: "OE-20260826-033",
          workOrderId: "WO-20260826-018",
          product: "伺服电机总成 (FG-SERVO-01)"
        };
        showDeviceToast(`超温告警触发！温度 ${temp}℃ > 75.0℃，已生成告警 ${dev.alarm.id} 并关联工序 OE-20260826-033`, "danger");
      }
    }
  } else if (actionType === "simulate_dup") {
    showDeviceToast(`QoS 1 幂等拦截：重复消息编号已成功去重，不重复记录遥测与告警`, "warning");
  } else if (actionType === "ack_alarm") {
    if (currentDeviceRole !== "engineer") {
      showDeviceToast("权限不足：仅 IoT人员 (iot.engineer) 允许确认告警", "danger");
      return;
    }
    const note = document.getElementById("dlg_ack_note")?.value.trim() || "现场已开启辅助风冷降温，降低主轴切削负荷，持续观察";
    if (dev.alarm && (dev.alarm.stage === "Triggered" || (dev.alarm.stage === "Recovered" && !dev.alarm.ackedAt))) {
      if (dev.alarm.stage !== "Recovered") {
        dev.alarm.stage = "Acked";
      }
      dev.alarm.ackedAt = timeStr;
      dev.alarm.ackedBy = "iot.engineer";
      dev.alarm.ackNote = note;
      showDeviceToast(`告警 ${dev.alarm.id} 已完成人工确认，排查措施已记录`);
    } else {
      showDeviceToast("当前无可确认的活动告警", "warning");
    }
  } else if (actionType === "edit_context") {
    if (currentDeviceRole !== "engineer") {
      showDeviceToast("权限不足：仅 IoT人员 (iot.engineer) 允许补充工序上下文", "danger");
      return;
    }
    const oe = document.getElementById("dlg_ctx_oe")?.value.trim();
    const wo = document.getElementById("dlg_ctx_wo")?.value.trim();
    if (dev.alarm) {
      dev.alarm.operationExecutionId = oe || dev.alarm.operationExecutionId;
      dev.alarm.workOrderId = wo || dev.alarm.workOrderId;
      showDeviceToast("生产工序执行上下文已更新");
    } else {
      showDeviceToast("当前设备无告警事实可关联上下文", "warning");
    }
  }

  closeDeviceActionDialog();
  renderDeviceQueue();
  renderDeviceDetail();
}

function initDeviceConsole() {
  const roleSelect = document.getElementById("deviceRole");
  roleSelect?.addEventListener("change", (e) => {
    currentDeviceRole = e.target.value;
    renderDeviceDetail();
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

      currentDeviceScenario = tab.dataset.scenario;
      currentDevices = JSON.parse(JSON.stringify(deviceScenarios[currentDeviceScenario].devices));
      selectedDeviceId = deviceScenarios[currentDeviceScenario].featuredDeviceId;
      renderDeviceQueue();
      renderDeviceDetail();
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
      deviceStatusFilter = btn.dataset.statusFilter;
      renderDeviceQueue();
    });
  });

  const searchInput = document.getElementById("deviceSearch");
  searchInput?.addEventListener("input", (e) => {
    deviceSearchQuery = e.target.value.trim();
    renderDeviceQueue();
  });

  renderDeviceQueue();
  renderDeviceDetail();
}

document.addEventListener("DOMContentLoaded", initDeviceConsole);
