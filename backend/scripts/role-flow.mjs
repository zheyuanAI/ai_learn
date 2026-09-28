import assert from 'node:assert/strict';
import { randomUUID } from 'node:crypto';
import { mkdir, appendFile, readFile, writeFile } from 'node:fs/promises';
import { dirname, resolve } from 'node:path';
import { execFile, spawn } from 'node:child_process';
import { promisify } from 'node:util';

const executeFile = promisify(execFile);
const base = process.env.ROLE_FLOW_API_BASE || 'http://127.0.0.1:20001';
const password = process.env.ROLE_FLOW_PASSWORD;
const workCenter = process.env.ROLE_FLOW_WORK_CENTER_ID;
if (!password || !workCenter) throw new Error('必须注入 ROLE_FLOW_PASSWORD 和本轮生成或已核实的 ROLE_FLOW_WORK_CENTER_ID');
const output = resolve(process.env.ROLE_FLOW_OUTPUT || 'output/role-flow-result.json');
const run = new Date().toISOString().slice(2, 10).replaceAll('-', '') + '-' + randomUUID().slice(0, 4).toUpperCase();
const actors = {
  admin: { username: 'admin.zhang', name: '张管理', role: '租户管理员' },
  sales: { username: 'sales.liu', name: '刘销售', role: '销售' },
  purchase: { username: 'buyer.chen', name: '陈采购', role: '采购' },
  warehouse: { username: 'wh.operator', name: '王库管', role: '仓库' },
  mes: { username: 'mes.inspector', name: '李质检', role: '生产与质检' },
  iot: { username: 'iot.engineer', name: '赵物联', role: '物联工程师' },
};
const sessions = new Map();
const report = { run, startedAt: new Date().toISOString(), status: 'RUNNING', actors, steps: [], facts: {}, balances: [] };

/** 用途：保存无凭据执行证据；入参无；出参无；原始令牌和设备 secret 永不进入报告。 */
async function saveReport() {
  await mkdir(dirname(output), { recursive: true });
  await writeFile(output, JSON.stringify(report, null, 2) + '\n');
}

/** 用途：以实际预置岗位账号建立会话；入参为岗位名；出参为内存会话，不重复登录顶替现有令牌。 */
async function login(role) {
  if (sessions.has(role)) return sessions.get(role);
  const response = await fetch(`${base}/api/auth/login`, {
    method: 'POST', headers: { 'Content-Type': 'application/json' }, signal: AbortSignal.timeout(15000),
    body: JSON.stringify({ username: actors[role].username, password, tenantCode: 'tenant_demo_a' }),
  });
  const body = await response.json();
  assert(response.ok && body.code === 200 && body.data?.token, `岗位登录失败：${actors[role].username}`);
  sessions.set(role, body.data);
  return body.data;
}

/**
 * 用途：按真实角色执行已定义业务接口；入参为岗位、方法、路径、载荷及幂等键；出参为服务端事实。
 * 流程：取得角色会话、执行命令、校验响应、追加账号/请求号/事实身份证据，不记录原始载荷或凭据。
 */
async function api(role, method, path, payload, key = randomUUID()) {
  const session = await login(role);
  const response = await fetch(`${base}${path}`, {
    method, signal: AbortSignal.timeout(20000),
    headers: { Authorization: `Bearer ${session.token}`, 'Content-Type': 'application/json',
      ...(method === 'GET' ? {} : { 'Idempotency-Key': key }) },
    ...(method === 'GET' ? {} : { body: JSON.stringify(payload || {}) }),
  });
  const body = await response.json();
  assert(response.ok && body.code === 200 && body.success !== false,
    `${actors[role].username} ${method} ${path} 失败 HTTP=${response.status} ${body.message || ''}`);
  report.steps.push({ sequence: report.steps.length + 1, actor: actors[role].username, role: actors[role].role,
    method, path, requestId: body.request_id, entityId: body.data?.id, status: body.data?.status,
    at: new Date().toISOString() });
  console.log(`${actors[role].name} (${actors[role].username}) ${method} ${path} OK`);
  await saveReport();
  return body.data;
}

/** 用途：核对指定物料的所有库位总量；入参为物料、仓库和期望量；出参为实测余额，缺失不按零处理。 */
async function balance(productId, warehouseId, expected, label) {
  const page = await api('warehouse', 'GET', `/api/inventory/balances?product_id=${productId}&warehouse_id=${warehouseId}&page=1&size=1000`);
  assert(page.records?.length > 0, `${label} 缺少余额事实`);
  const actual = page.records.reduce((sum, row) => ({
    onHand: sum.onHand + Number(row.onHandQty), reserved: sum.reserved + Number(row.reservedQty),
    available: sum.available + Number(row.availableQty),
  }), { onHand: 0, reserved: 0, available: 0 });
  assert.deepEqual(actual, expected, label);
  report.balances.push({ label, productId, warehouseId, ...actual });
  await saveReport();
  return actual;
}

/** 用途：按实际设备凭证启动本轮本机 Broker；入参为首次凭证；出参无，密码仅用于外部 password_file。 */
async function startBroker(credential) {
  for (const name of ['ROLE_FLOW_MQTT_BIN', 'ROLE_FLOW_MQTT_CONFIG', 'ROLE_FLOW_MQTT_PASSWORD_FILE', 'ROLE_FLOW_MQTT_ACL_FILE', 'ROLE_FLOW_IOT_LOG']) {
    if (!process.env[name]) throw new Error(`缺少 ${name}，真实 MQTT 验收不回退为 HTTP 模拟`);
  }
  try {
    await executeFile(resolve(process.env.ROLE_FLOW_MQTT_BIN, 'mosquitto_passwd.exe'), ['-b',
      process.env.ROLE_FLOW_MQTT_PASSWORD_FILE, credential.credential_reference, credential.plain_secret], { windowsHide: true });
  } catch (error) {
    // 修改用途：子进程错误对象可能包含凭据参数，只传播退出码。
    throw new Error(`设备 Broker 凭证准备失败，退出码 ${error.code}`);
  }
  await appendFile(process.env.ROLE_FLOW_MQTT_ACL_FILE,
    `\nuser ${credential.credential_reference}\ntopic write devices/${credential.credential_reference}/telemetry\n`);
  // 修改用途：隔离角色流程结束后仍需继续观测 MQTT 并发读取；在 Windows 上让 Broker 脱离 Node 父进程的作业树，避免验收进程退出时误杀 Broker。
  const broker = spawn(resolve(process.env.ROLE_FLOW_MQTT_BIN, 'mosquitto.exe'),
    ['-c', process.env.ROLE_FLOW_MQTT_CONFIG], { detached: true, windowsHide: true, stdio: 'ignore' });
  await new Promise((resolveStarted, reject) => {
    broker.once('spawn', resolveStarted);
    broker.once('error', () => reject(new Error('本轮 Broker 启动失败')));
  });
  report.facts.brokerPid = broker.pid;
  broker.unref();
  await saveReport();
  // 修改用途：新 Broker 的订阅建立前发布不会得到离线会话保护，明确等待正式监听器订阅成功。
  await waitFor(() => readFile(process.env.ROLE_FLOW_IOT_LOG, 'utf8'),
    text => text.includes('IoT MQTT 已订阅遥测主题'), '正式IoT监听器订阅');
}

/** 用途：发送一次真实 QoS1 遥测；入参为设备凭证和完整消息；出参无，不把 secret 输出到命令日志。 */
async function publish(credential, message) {
  try {
    await executeFile(resolve(process.env.ROLE_FLOW_MQTT_BIN, 'mosquitto_pub.exe'), [
      '-h', '127.0.0.1', '-p', '1883', '-u', credential.credential_reference, '-P', credential.plain_secret,
      '-q', '1', '-t', `devices/${credential.credential_reference}/telemetry`, '-m',
      JSON.stringify(message).replace(/[^\x00-\x7f]/g, character =>
        '\\u' + character.charCodeAt(0).toString(16).padStart(4, '0')),
    ], { windowsHide: true, timeout: 15000 });
  } catch (error) {
    throw new Error(`真实 MQTT 发布失败，退出码 ${error.code}`);
  }
}

/** 用途：按有界轮询等待摄取和自动补链；入参为读取函数及成立条件；出参为最终事实，不超过30秒。 */
async function waitFor(read, predicate, label) {
  const deadline = Date.now() + 30000;
  while (Date.now() < deadline) {
    const result = await read();
    if (predicate(result)) return result;
    await new Promise(resolveWait => setTimeout(resolveWait, 500));
  }
  throw new Error(`${label} 未在30秒内达成`);
}

try {
  // 用途：防止在未清理的历史单据上混入本轮验收，不自动删除数据库。
  for (const path of ['/api/sales-orders', '/api/purchase-orders', '/api/work-orders', '/api/inventory/balances']) {
    const page = await api(path.includes('work-orders') ? 'mes' : 'admin', 'GET', `${path}?page=1&size=1`);
    assert.equal(Number(page.total), 0, `请先按授权清理旧业务数据：${path}`);
  }
  const uom = await api('admin', 'POST', '/api/uoms', { code: 'PCS', name: '件', symbol: 'PCS', decimalScale: 0, status: 'ACTIVE' });
  const finishedUom = await api('admin', 'POST', '/api/uoms', { code: 'SET', name: '套', symbol: 'SET', decimalScale: 0, status: 'ACTIVE' });
  const rawWarehouse = await api('admin', 'POST', '/api/warehouses', { code: 'LY-RM-02', name: '南区铝件原料仓',
    type: 'RAW_MATERIAL', manager: '王库管', contact: '', address: '厂区二号门西侧', status: 'ACTIVE' });
  const finishedWarehouse = await api('admin', 'POST', '/api/warehouses', { code: 'LY-FG-01', name: '装配成品待发仓',
    type: 'FINISHED_GOODS', manager: '王库管', contact: '', address: '装配车间南侧装卸口', status: 'ACTIVE' });
  const customer = await api('admin', 'POST', '/api/customers', { customerCode: 'CUS-JS018', customerName: '嘉盛液压设备有限公司',
    contactPerson: '许工', contactPhone: '', shippingAddress: '苏州市吴中区设备厂收货区', status: 'ACTIVE' });
  const supplier = await api('admin', 'POST', '/api/suppliers', { supplierCode: 'SUP-HH007', supplierName: '恒华精密铝业有限公司',
    contactPerson: '周经理', contactPhone: '', address: '无锡市惠山区金属加工园', status: 'ACTIVE' });
  const locations = {};
  for (const [name, type, warehouse, label] of [
    ['hold', 'QualityHold', rawWarehouse, 'QH-02 铝坯待检区'], ['receiving', 'ReceivingStaging', rawWarehouse, 'RCV-03 收货暂存'],
    ['raw', 'Storage', rawWarehouse, 'A-03-07 铝件货架'], ['finished', 'Storage', finishedWarehouse, 'FG-B-02 支架成品区'],
    ['shipping', 'ShippingStaging', finishedWarehouse, 'DOCK-02 嘉盛待发位'],
  ]) locations[name] = await api('admin', 'POST', '/api/locations', { warehouseId: warehouse.id, code: label.split(' ')[0],
    name: label, type, capacity: '1800', status: 'ACTIVE' });
  const raw = await api('admin', 'POST', '/api/products', { sku: 'AL6061-824618', name: '6061铝合金支架坯件',
    spec: '82×46×18mm / 去毛刺前', uom: uom.code, category: 'RAW_MATERIAL', batchManaged: true, unitPrice: '18.73', status: 'ACTIVE' });
  const finished = await api('admin', 'POST', '/api/products', { sku: 'ZJ-HV08-R2', name: 'HV-08液压阀安装支架组件',
    spec: '左右支架配套 / 阳极氧化银白', uom: finishedUom.code, category: 'FINISHED_GOODS', batchManaged: true, unitPrice: '86.45', status: 'ACTIVE' });
  report.facts = { ...report.facts, rawId: raw.id, finishedId: finished.id, rawWarehouseId: rawWarehouse.id, finishedWarehouseId: finishedWarehouse.id };

  const sales = await api('sales', 'POST', '/api/sales-orders', { soNo: `XS-${run}`, customerId: customer.id,
    plannedShipDate: new Date(Date.now() + 2 * 86400000).toISOString().slice(0, 10), remark: '嘉盛维修批次113套；先送47套，其余随整机备件车发运',
    lines: [{ lineNo: 1, productId: finished.id, uom: finishedUom.code, orderedQty: '113' }] });
  await api('sales', 'POST', `/api/sales-orders/${sales.id}/submit`);
  await api('sales', 'POST', `/api/sales-orders/${sales.id}/approve`);
  const bom = await api('mes', 'POST', '/api/boms', { bomCode: `BOM-HV08-${run}`, productId: finished.id, version: 'R2', status: 'ACTIVE',
    components: [{ componentProductId: raw.id, componentQty: 2, uom: uom.code, scrapRate: 0 }] });
  const routing = await api('mes', 'POST', '/api/routings', { routingCode: `RT-HV08-${run}`, productId: finished.id, version: 'R3', status: 'ACTIVE',
    operations: [{ operationNo: 20, operationName: '左右支架配对装配与扭矩复核', workCenterId: workCenter, standardTimeMinutes: 7.5 }] });
  const createdWorkOrder = await api('mes', 'POST', '/api/work-orders', { workOrderNo: `SC-${run}`, productId: finished.id, plannedQty: 143,
    bomId: bom.id, routingId: routing.id, sourceSalesOrderLineId: sales.lines[0].id,
    plannedStartTime: new Date().toISOString(), plannedFinishTime: new Date(Date.now() + 6 * 3600000).toISOString() });
  const order = createdWorkOrder.workOrder || createdWorkOrder;
  assert(order.id, '工单响应缺少正式业务 ID');
  await api('mes', 'POST', `/api/work-orders/${order.id}/submit`);
  await api('mes', 'POST', `/api/work-orders/${order.id}/approve`);
  const purchase = await api('purchase', 'POST', '/api/purchase-orders', { poNo: `CG-${run}`, supplierId: supplier.id,
    expectedArrivalDate: new Date().toISOString().slice(0, 10), remark: '配套坯件补料；供应商尾箱受潮，按现场实收结算',
    lines: [{ lineNo: 1, productId: raw.id, uom: uom.code, orderedQty: '352', targetWarehouseId: rawWarehouse.id, sourceWorkOrderId: order.id }] });
  await api('purchase', 'POST', `/api/purchase-orders/${purchase.id}/submit`);
  await api('purchase', 'POST', `/api/purchase-orders/${purchase.id}/approve`);
  const receiptPayload = { purchaseOrderId: purchase.id, receiptNo: `DH-${run}`, receiptTime: new Date().toISOString(),
    qualityHoldLocationId: locations.hold.id, lines: [{ purchaseOrderLineId: purchase.lines[0].id, productId: raw.id, uom: uom.code,
      arrivedQty: '347', rejectedQty: '8', receivedQty: '339', lotNo: `HH-6061-${run}`, rejectionReason: '尾箱受潮，8件氧化斑明显，收货前退回供应商' }] };
  const receiptKey = randomUUID();
  const receipt = await api('warehouse', 'POST', '/api/purchase-receipts/confirm', receiptPayload, receiptKey);
  assert.equal((await api('warehouse', 'POST', '/api/purchase-receipts/confirm', receiptPayload, receiptKey)).id, receipt.id);
  await balance(raw.id, rawWarehouse.id, { onHand: 339, reserved: 0, available: 339 }, '实际接收339，拒收8未入库');
  await api('purchase', 'POST', `/api/purchase-orders/${purchase.id}/complete`, { completionReason: '改用既有备料，未收13件停止补送；已收货继续质检处置' });
  const inspection = await api('mes', 'POST', `/api/purchase-receipts/${receipt.id}/quality/inspect`, {
    purchaseOrderId: purchase.id, purchaseReceiptId: receipt.id, purchaseReceiptLineId: receipt.lines[0].id,
    productId: raw.id, inspectedQty: '339', qualifiedQty: '320', unqualifiedQty: '19',
    unqualifiedReason: '19件定位孔偏差超限', inspectionRemark: '隔离待供方处理，不混入合格备料' });
  const disposition = await api('mes', 'POST', `/api/purchase-receipts/${receipt.id}/quality/release`, {
    inspectionId: inspection.id, dispositionType: 'Release', dispositionQty: '320', reason: '320件尺寸及表面复核通过，19件保留隔离' });
  await api('warehouse', 'POST', `/api/purchase-quality-dispositions/${disposition.id}/confirm`, {
    dispositionId: disposition.id, toLocationId: locations.receiving.id, putawayTargetLocationId: locations.raw.id });
  const putaways = await api('warehouse', 'GET', '/api/putaway-tasks?page=1&size=1000');
  const putaway = putaways.records.find(item => item.purchaseReceiptId === receipt.id);
  assert(putaway?.id, '放行后缺少上架任务');
  await api('warehouse', 'POST', `/api/putaway-tasks/${putaway.id}/confirm`, { taskId: putaway.id, toLocationId: locations.raw.id, putawayQty: '320' });

  const profile = await api('iot', 'POST', '/api/device-profiles', { profile_code: `ASM-HV-${run}`, profile_name: '装配工位温度与运行状态',
    offline_timeout_seconds: 120, metrics: [{ metric_code: 'temperature', metric_name: '夹具温度', value_type: 'NUMBER', unit: '℃', required: true },
      { metric_code: 'running_status', metric_name: '工位状态', value_type: 'TEXT', unit: '', required: true }] });
  const device = await api('iot', 'POST', '/api/devices', { device_code: `ASM-02-${run}`, device_name: '二号支架装配工位采集器',
    device_profile_id: profile.id, protocol_type: 'MQTT', work_center_id: workCenter });
  await api('iot', 'POST', '/api/device-alarm-rules', { rule_code: `TEMP-HIGH-${run}`, device_profile_id: profile.id,
    device_id: device.id, metric_code: 'temperature', operator: 'GT', trigger_threshold: 78.5, recovery_threshold: 72, alarm_level: 'Warning' });
  const credential = await api('iot', 'POST', `/api/devices/${device.id}/credentials`);
  await startBroker(credential);
  const session = await login('mes');
  const dispatch = await api('mes', 'POST', '/api/dispatch-orders', { work_order_id: order.id, operation_id: routing.operations[0].id,
    operator_id: session.user.userId, dispatch_qty: 143, device_id: device.id });
  await api('mes', 'POST', `/api/dispatch-orders/${dispatch.id}/release`);
  await api('mes', 'POST', `/api/dispatch-orders/${dispatch.id}/start-processing`);
  const execution = await api('mes', 'POST', '/api/operation-executions', { dispatch_order_id: dispatch.id,
    work_order_id: order.id, operation_id: routing.operations[0].id });
  const issue = await api('mes', 'POST', '/api/material-issues', { issueNo: `LL-${run}`, workOrderId: order.id,
    overageReason: '首件调机追加7件备料，未耗用件随工单退回',
    items: [{ productId: raw.id, warehouseId: rawWarehouse.id, locationId: locations.raw.id, quantity: 293 }] });
  await api('warehouse', 'POST', `/api/material-issues/${issue.id}/confirm`);
  const materialReturn = await api('mes', 'POST', '/api/material-returns', { returnNo: `TL-${run}`, workOrderId: order.id,
    items: [{ productId: raw.id, warehouseId: rawWarehouse.id, locationId: locations.raw.id, quantity: 7 }] });
  await api('warehouse', 'POST', `/api/material-returns/${materialReturn.id}/confirm`);
  await balance(raw.id, rawWarehouse.id, { onHand: 53, reserved: 0, available: 53 }, '领293退7后原料剩53，含隔离19');
  await api('mes', 'POST', `/api/operation-executions/${execution.id}/start`, { occurredAt: new Date().toISOString() });
  const message = { message_id: `ASM-HOT-${run}`, ts: new Date().toISOString(),
    metrics: [{ metric_code: 'temperature', metric_value: 81.7, metric_unit: '℃' }, { metric_code: 'running_status', metric_value: 'Running', metric_unit: '' }] };
  await publish(credential, message);
  const alarm = await waitFor(async () => (await api('iot', 'GET', `/api/device-alarms?device_id=${device.id}&page=1&size=20`)).records[0],
    value => value?.context_status === 'Linked', '温度告警及Core自动补链');
  assert.equal(alarm.operation_execution_id, execution.id, '告警应自动关联当前工序');
  await publish(credential, message);
  await api('iot', 'POST', `/api/device-alarms/${alarm.id}/ack`, { ack_comment: '赵物联检查散热风道并更换积尘滤网，先确认现场处理' });
  await publish(credential, { ...message, message_id: `ASM-COOL-${run}`, ts: new Date().toISOString(),
    metrics: [{ metric_code: 'temperature', metric_value: 69.3, metric_unit: '℃' }, { metric_code: 'running_status', metric_value: 'Running', metric_unit: '' }] });
  await waitFor(() => api('iot', 'GET', `/api/device-alarms/${alarm.id}`), value => value.status === 'Recovered', '现场处理后恢复');
  // 修改用途：冷消息在同一正式订阅回调中排在重放之后，核对末端事实才能证明重放已消费且没有重复落库。
  const telemetry = await waitFor(() => api('iot', 'GET', `/api/devices/${device.id}/telemetry?limit=100`), value => value.length === 4, '冷热消息及重放末端事实');
  assert.equal(telemetry.filter(item => item.messageId === message.message_id).length, 2, '热消息重放后仍只有两个指标');
  assert.equal(telemetry.filter(item => item.messageId === `ASM-COOL-${run}`).length, 2, '恢复消息恰好保存两个指标');
  await api('mes', 'POST', `/api/operation-executions/${execution.id}/complete`, { occurredAt: new Date().toISOString() });
  await api('mes', 'POST', `/api/dispatch-orders/${dispatch.id}/complete`);
  const workReport = await api('mes', 'POST', '/api/work-reports', { reportNo: `BG-${run}`, workOrderId: order.id,
    operationId: routing.operations[0].id, operationExecutionId: execution.id, reportTime: new Date().toISOString(),
    qualifiedQty: 137, defectQty: 6, remark: '143套装配完成，6套扭矩复核不通过；合格137套，其中24套作为维修备件' });
  const quality = await api('mes', 'POST', '/api/quality-inspections', { inspectionNo: `ZJ-${run}`, workReportId: workReport.id,
    inspectionType: 'FINAL', sampleQty: 137 });
  await api('mes', 'POST', `/api/quality-inspections/${quality.id}/submit`, { qualifiedQty: 137, defectQty: 0, result: 'Passed' });
  const finishedReceipt = await api('mes', 'POST', '/api/finished-goods-receipts', { receiptNo: `RK-${run}`, workOrderId: order.id,
    receiptQty: 137, warehouseId: finishedWarehouse.id, locationId: locations.finished.id });
  await api('warehouse', 'POST', `/api/finished-goods-receipts/${finishedReceipt.id}/confirm`);
  await api('mes', 'POST', `/api/work-orders/${order.id}/complete`);
  await balance(finished.id, finishedWarehouse.id, { onHand: 137, reserved: 0, available: 137 }, '137合格成品入库');

  const pick = quantity => ({ salesOrderId: sales.id, lines: [{ salesOrderLineId: sales.lines[0].id, pickedQty: String(quantity),
    sourceLocationId: locations.finished.id, shippingLocationId: locations.shipping.id }] });
  await api('warehouse', 'POST', '/api/pick-tasks/confirm', pick(51));
  await api('warehouse', 'POST', '/api/pick-tasks/return', { salesOrderId: sales.id,
    lines: [{ salesOrderLineId: sales.lines[0].id, returnQty: '4', toLocationId: locations.finished.id }] });
  await api('warehouse', 'POST', '/api/sales-shipments/confirm', { salesOrderId: sales.id, shipTime: new Date().toISOString(),
    shipmentLines: [{ salesOrderLineId: sales.lines[0].id, productId: finished.id, shipQty: '47' }] });
  await api('warehouse', 'POST', '/api/pick-tasks/confirm', pick(66));
  const shipment = await api('warehouse', 'POST', '/api/sales-shipments/confirm', { salesOrderId: sales.id, shipTime: new Date().toISOString(),
    shipmentLines: [{ salesOrderLineId: sales.lines[0].id, productId: finished.id, shipQty: '66' }] });
  assert.equal(shipment.order.status, 'Completed');
  assert.equal(Number(shipment.order.lines[0].shippedQty), 113);
  await balance(finished.id, finishedWarehouse.id, { onHand: 24, reserved: 0, available: 24 }, '分批发47+66，成品余24');
  const records = await api('purchase', 'GET', `/api/operation-audits?entity_type=PURCHASE_ORDER&entity_id=${purchase.id}&limit=100`);
  assert.equal(records.filter(item => item.actionCode === 'pur:receipt:confirm').length, 1, '收货重放不重复记录');
  // 修改用途：按采购订单行核对分批履约累计，而不是从产品 SKU 或库存余额推算质检、放行与上架事实。
  const purchaseDetail = await api('purchase', 'GET', `/api/purchase-orders/${purchase.id}`);
  const purchaseLine = purchaseDetail.lines.find(line => line.id === purchase.lines[0].id);
  assert(purchaseLine, '采购详情缺少本轮订单行');
  for (const [field, expected] of Object.entries({ arrivedQty: 347, rejectedQty: 8,
    qualifiedQty: 320, releaseExecutedQty: 320, putawayQty: 320 })) {
    assert.equal(Number(purchaseLine[field]), expected, `采购详情 ${field} 累计不符`);
  }
  report.purchaseLineCumulative = { lineId: purchaseLine.id, arrivedQty: purchaseLine.arrivedQty,
    rejectedQty: purchaseLine.rejectedQty, receivedQty: purchaseLine.receivedQty,
    qualifiedQty: purchaseLine.qualifiedQty, releaseExecutedQty: purchaseLine.releaseExecutedQty,
    putawayQty: purchaseLine.putawayQty, pendingQty: purchaseLine.pendingQty };
  const trace = await api('admin', 'GET', `/api/traceability?entity_type=SALES_ORDER&entity_id=${sales.id}`);
  assert.deepEqual(trace.missing_sources, [], '所有已启用来源应提供真实追溯事实');
  for (const [entityType, entityId] of [['sales_order', sales.id], ['work_order', order.id], ['purchase_order', purchase.id]]) {
    assert(trace.nodes.some(node => node.entityType === entityType && node.entityId === entityId), `追溯缺少 ${entityType} 事实`);
  }
  report.trace = { nodeCount: trace.nodes.length, linkCount: trace.links.length, missingSources: trace.missing_sources,
    hiddenNodeCount: trace.hidden_node_count, truncated: trace.truncated, requestId: trace.request_id };
  // 用途：正式关系从告警指向 Core 工序上下文，单独从告警入口核验，不假设已实现反向设备关系。
  const alarmTrace = await api('admin', 'GET', `/api/traceability?entity_type=alarm&entity_id=${alarm.id}`);
  assert.deepEqual(alarmTrace.missing_sources, [], '告警追溯应能读取 Core 与 IoT 事实');
  for (const [entityType, entityId] of [['alarm', alarm.id], ['device', device.id], ['work_order', order.id]]) {
    assert(alarmTrace.nodes.some(node => node.entityType === entityType && node.entityId === entityId), `告警追溯缺少 ${entityType} 事实`);
  }
  assert(alarmTrace.nodes.find(node => node.entityType === 'work_order' && node.entityId === order.id).complete,
    '取得真实工单后不应仍标为上下文引用缺口');
  assert.equal(alarmTrace.nodes.find(node => node.entityType === 'work_order' && node.entityId === order.id).status, 'Completed');
  assert.equal(alarmTrace.nodes.find(node => node.entityType === 'work_order' && node.entityId === order.id).label, order.workOrderNo);
  assert(!alarmTrace.nodes.some(node => node.entityType === 'operation_execution'), '管理员没有工序执行权限，节点应按现有权限隐藏');
  report.alarmTrace = { nodeCount: alarmTrace.nodes.length, linkCount: alarmTrace.links.length,
    missingSources: alarmTrace.missing_sources, hiddenNodeCount: alarmTrace.hidden_node_count,
    truncated: alarmTrace.truncated, requestId: alarmTrace.request_id };
  // 用途：按既有岗位权限核验同一链路，不给管理员补权限，也不要求物联角色看到生产执行事实。
  const mesTrace = await api('mes', 'GET', `/api/traceability?entity_type=alarm&entity_id=${alarm.id}`);
  for (const [entityType, entityId] of [['operation_execution', execution.id], ['work_order', order.id]]) {
    assert(mesTrace.nodes.some(node => node.entityType === entityType && node.entityId === entityId && node.complete && node.status === 'Completed'),
      `生产角色应读取真实完整的 ${entityType} 事实`);
  }
  const iotTrace = await api('iot', 'GET', `/api/traceability?entity_type=alarm&entity_id=${alarm.id}`);
  assert(iotTrace.nodes.some(node => node.entityType === 'alarm' && node.entityId === alarm.id && node.complete));
  assert(iotTrace.nodes.some(node => node.entityType === 'device' && node.entityId === device.id && node.complete));
  assert(!iotTrace.nodes.some(node => ['operation_execution', 'work_order'].includes(node.entityType)), '物联角色无生产执行权限，不能显示越权节点');
  report.roleTrace = [{ role: 'mes', nodeCount: mesTrace.nodes.length, hiddenNodeCount: mesTrace.hidden_node_count },
    { role: 'iot', nodeCount: iotTrace.nodes.length, hiddenNodeCount: iotTrace.hidden_node_count }];
  report.facts = { ...report.facts, salesOrderId: sales.id, salesOrderNo: sales.soNo, purchaseOrderId: purchase.id,
    purchaseOrderNo: purchase.poNo, workOrderId: order.id, workOrderNo: order.workOrderNo, deviceId: device.id,
    alarmId: alarm.id, operationExecutionId: execution.id, receiptId: receipt.id,
    rawLotNo: receiptPayload.lines[0].lotNo, traceEntityType: 'SALES_ORDER' };
  report.status = 'PASSED';
  report.completedAt = new Date().toISOString();
  await saveReport();
  console.log(`角色流程及库存对账完成，证据：${output}`);
} catch (error) {
  report.status = 'FAILED';
  report.error = error.message;
  await saveReport();
  console.error(error.message);
  process.exitCode = 1;
}
