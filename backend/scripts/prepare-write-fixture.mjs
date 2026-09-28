import assert from 'node:assert/strict';
import { randomUUID } from 'node:crypto';
import { readFile, mkdir, writeFile } from 'node:fs/promises';
import { dirname, resolve } from 'node:path';
import { parseOptions, pgRows, apiData, quantityUnits, validateManifest } from './write-baseline.mjs';

// 用途：给隔离写入批次提供可复现但不规则的业务样本；入参为 1-based 序号，出参为客户、供应商和数量事实。
// 这些值只用于测试租户，单号仍带本轮 runId 以便预检和追溯，避免把压测数据伪装成生产单据。
const SAMPLE_PROFILES = [
  { customer: '苏州锐衡液压设备', supplier: '无锡恒华精密铝业', contact: '许工', shipOffset: 3, qty: 37 },
  { customer: '常州拓铭自动化', supplier: '宁波诚锐金属加工', contact: '沈工', shipOffset: 6, qty: 12 },
  { customer: '嘉兴云启装备制造', supplier: '昆山联晟表面处理', contact: '顾工', shipOffset: 2, qty: 58 },
  { customer: '合肥远川泵阀系统', supplier: '镇江华铸机电配套', contact: '周经理', shipOffset: 9, qty: 23 },
  { customer: '南通衡岳工程机械', supplier: '常熟新锐铝材供应', contact: '陆工', shipOffset: 4, qty: 91 },
  { customer: '湖州启辰维修中心', supplier: '绍兴越虎机加工', contact: '方工', shipOffset: 12, qty: 16 },
  { customer: '无锡博远成套设备', supplier: '太仓精工热处理', contact: '马工', shipOffset: 5, qty: 44 },
  { customer: '苏州翰泽工业服务', supplier: '扬州顺达铝件', contact: '丁工', shipOffset: 8, qty: 29 },
  { customer: '宁波恒拓液压维修', supplier: '杭州启盛精密制造', contact: '梁工', shipOffset: 7, qty: 63 },
  { customer: '泰州瑞禾装配工厂', supplier: '盐城东虎机械配件', contact: '沈经理', shipOffset: 10, qty: 18 },
  { customer: '镇江澄明机电', supplier: '金华众联铝业', contact: '蒋工', shipOffset: 14, qty: 76 },
  { customer: '嘉善弘毅设备维护', supplier: '常州恒准五金', contact: '袁工', shipOffset: 1, qty: 31 },
];

function sampleProfile(index) {
  return SAMPLE_PROFILES[(index - 1) % SAMPLE_PROFILES.length];
}

/** 用途：复用写基线隔离地址校验并解析准备参数；入参为 CLI；出参为源报告、目标模式和隔离配置。 */
function options(args) {
  const extra = new Map();
  const baseArgs = [];
  for (let index = 0; index < args.length; index++) {
    const name = args[index];
    if (['--mode', '--count', '--run-id'].includes(name)) {
      assert(index + 1 < args.length && !extra.has(name), `缺少或重复 ${name}`);
      extra.set(name, args[++index]);
    } else if (name === '--source-report') baseArgs.push('--manifest', args[++index]);
    else if (name === '--output') baseArgs.push('--report', args[++index]);
    else if (name === '--help') return { help: true };
    else if (name === '--execute-isolated') baseArgs.push(name);
    else baseArgs.push(name, args[++index]);
  }
  const base = parseOptions(baseArgs);
  assert(base.report, '缺少 --output 清单路径');
  const mode = extra.get('--mode');
  const count = Number(extra.get('--count'));
  const runId = extra.get('--run-id');
  assert(['hotspot', 'multiRole'].includes(mode), 'mode 只能是 hotspot 或 multiRole');
  assert(Number.isSafeInteger(count) && count >= 2 && count <= 30, 'count 必须为 2–30');
  assert(/^BENCH-[A-Z0-9]{8,24}$/.test(runId), 'runId 必须为 BENCH- 加 8–24 位大写字母或数字');
  return { ...base, mode, count, runId };
}

/** 用途：向明确岗位发送一次业务命令；入参为配置、令牌、路径和完整 DTO；出参为服务端 data，不自动重试或记录凭据。 */
async function command(config, token, path, payload) {
  const response = await fetch(new URL(path, config.api), { method: 'POST', redirect: 'error',
    headers: { Authorization: `Bearer ${token}`, 'Content-Type': 'application/json', 'Idempotency-Key': randomUUID() },
    body: JSON.stringify(payload ?? {}), signal: AbortSignal.timeout(config.timeoutMs) });
  const body = await response.json();
  assert(response.ok && body.code === 200 && body.success !== false, `准备单据失败：${path} HTTP ${response.status} CODE ${String(body.code).slice(0, 24)}`);
  return body.data;
}

/** 用途：读角色流程留下的可复用主数据并与隔离 PG 对照；入参为配置、报告和岗位令牌；出参为源工单、销售、采购和原料余额。 */
async function sourceFacts(config, source, tokens) {
  assert(source.status === 'PASSED' && source.facts, '源角色流程必须为 PASSED 且包含正式事实');
  const facts = source.facts;
  for (const key of ['workOrderId', 'salesOrderId', 'purchaseOrderId', 'rawId', 'rawWarehouseId']) {
    assert(/^[0-9a-f-]{36}$/i.test(facts[key] || ''), `源角色流程缺少 ${key}`);
  }
  const pg = await pgRows(config, `SELECT row_to_json(t) FROM (SELECT s.id AS "salesId", s.so_no AS "soNo", p.id AS "purchaseId", p.po_no AS "poNo", w.id AS "workOrderId" FROM sales_order s CROSS JOIN purchase_order p CROSS JOIN mes_work_order w WHERE s.id='${facts.salesOrderId}'::uuid AND p.id='${facts.purchaseOrderId}'::uuid AND w.id='${facts.workOrderId}'::uuid AND s.isdel=0 AND p.isdel=0 AND w.isdel=0 AND s.tenant_id=p.tenant_id AND p.tenant_id=w.tenant_id) t`);
  assert(pg.length === 1 && pg[0].soNo === facts.salesOrderNo && pg[0].poNo === facts.purchaseOrderNo, '源报告事实未在指定隔离 PG 找到');
  const [workOrder, sales, purchase, balancePage] = await Promise.all([
    apiData(config, tokens.mes, `/api/work-orders/${facts.workOrderId}`),
    apiData(config, tokens.sales, `/api/sales-orders/${facts.salesOrderId}`),
    apiData(config, tokens.purchase, `/api/purchase-orders/${facts.purchaseOrderId}`),
    apiData(config, tokens.warehouse, `/api/inventory/balances?product_id=${facts.rawId}&warehouse_id=${facts.rawWarehouseId}&page=1&size=1000`),
  ]);
  assert(workOrder?.id === facts.workOrderId && sales?.soNo === pg[0].soNo && purchase?.poNo === pg[0].poNo,
    '隔离 API 读取的源事实与隔离 PG 不一致');
  assert(workOrder.bomId && workOrder.routingId && workOrder.productId, '源工单缺少可复用 BOM、Routing 或成品');
  const candidates = balancePage.records.filter(row => row.dimension?.productId === facts.rawId
    && row.dimension?.warehouseId === facts.rawWarehouseId && quantityUnits(row.availableQty) > 0n);
  assert(candidates.length > 0, '源角色流程没有可用原料库存余额');
  const locations = await pgRows(config, `SELECT row_to_json(t) FROM (SELECT id, type FROM md_location WHERE id IN (${candidates.map(row => `'${row.dimension.locationId}'::uuid`).join(',')}) AND isdel=0) t`);
  const storageIds = new Set(locations.filter(row => row.type === 'Storage').map(row => row.id));
  const storages = candidates.filter(row => storageIds.has(row.dimension.locationId));
  assert(storages.length === 1 && quantityUnits(storages[0].availableQty) >= BigInt(config.count) * 1000000n,
    '须有唯一可用 Storage 热点余额，且至少满足 count 件');
  return { facts, workOrder, sales, purchase, balance: storages[0] };
}

/** 用途：持久化部分准备证据或完整 manifest；入参为目标文件和数据；出参无，不写令牌。 */
async function persist(path, value) {
  await mkdir(dirname(path), { recursive: true });
  await writeFile(path, JSON.stringify(value, null, 2) + '\n');
}

/** 用途：顺序创建各自独立的 Released 工单和 Draft 领料；入参为源事实及第 N 个序号；出参为可确认 job。 */
async function prepareIssue(config, tokens, source, index) {
  const suffix = String(index).padStart(3, '0');
  const issueQty = index % 3 === 0 ? 2 : 1;
  const now = Date.now();
  const created = await command(config, tokens.mes, '/api/work-orders', {
    workOrderNo: `${config.runId}-WO-${suffix}`, productId: source.workOrder.productId,
    plannedQty: issueQty, bomId: source.workOrder.bomId, routingId: source.workOrder.routingId,
    plannedStartTime: new Date(now).toISOString(), plannedFinishTime: new Date(now + 6 * 3600000).toISOString(),
  });
  const workOrderId = created?.workOrder?.id || created?.id;
  assert(workOrderId, '新工单响应缺少 ID');
  await command(config, tokens.mes, `/api/work-orders/${workOrderId}/submit`);
  await command(config, tokens.mes, `/api/work-orders/${workOrderId}/approve`);
  const issue = await command(config, tokens.mes, '/api/material-issues', {
    issueNo: `${config.runId}-MI-${suffix}`, workOrderId,
    items: [{ productId: source.facts.rawId, warehouseId: source.facts.rawWarehouseId,
      locationId: source.balance.dimension.locationId, quantity: issueQty }],
  });
  assert(issue?.id, '新领料单响应缺少 ID');
  return { kind: 'materialIssueConfirm', id: issue.id, workOrderId };
}

/** 用途：使用正式 DTO 创建采购和销售 Draft；入参为源事实和序号；出参为两条岗位提交 job。 */
async function prepareOrders(config, tokens, source, index) {
  const suffix = String(index).padStart(3, '0');
  const profile = sampleProfile(index);
  // 修改用途：主数据由租户管理员真实 API 创建；每个岗位单据使用不同客户、供应商与数量/日期。
  const customer = await command(config, tokens.admin, '/api/customers', {
    customerCode: `${config.runId}-CU-${suffix}`, customerName: profile.customer,
    contactPerson: profile.contact, shippingAddress: `苏南维修备件库${index % 3 + 1}号收货区`, status: 'ACTIVE',
  });
  const supplier = await command(config, tokens.admin, '/api/suppliers', {
    supplierCode: `${config.runId}-SU-${suffix}`, supplierName: profile.supplier,
    contactPerson: profile.contact, address: `华东${index % 4 + 1}号供应商交货窗口`, status: 'ACTIVE',
  });
  assert(customer?.id && supplier?.id, '新客户或供应商响应缺少 ID');
  const sales = await command(config, tokens.sales, '/api/sales-orders', {
    soNo: `${config.runId}-SO-${suffix}`, customerId: customer.id,
    plannedShipDate: new Date(Date.now() + profile.shipOffset * 86400000).toISOString().slice(0, 10),
    lines: [{ lineNo: 1, productId: source.sales.lines[0].productId, uom: source.sales.lines[0].uom, orderedQty: String(profile.qty) }],
  });
  const purchase = await command(config, tokens.purchase, '/api/purchase-orders', {
    poNo: `${config.runId}-PO-${suffix}`, supplierId: supplier.id,
    expectedArrivalDate: new Date(Date.now() + Math.max(1, profile.shipOffset - 1) * 86400000).toISOString().slice(0, 10),
    lines: [{ lineNo: 1, productId: source.facts.rawId, uom: source.purchase.lines[0].uom,
      orderedQty: String(profile.qty), targetWarehouseId: source.facts.rawWarehouseId }],
  });
  assert(sales?.id && purchase?.id, '新销售或采购订单响应缺少 ID');
  return [{ kind: 'salesSubmit', id: sales.id }, { kind: 'purchaseSubmit', id: purchase.id }];
}

/** 用途：仅在明确隔离开关下创建预置事实；入参为规范化配置；出参为写基线 manifest，失败保留部分准备证据。 */
async function prepare(config) {
  const tokens = {};
  for (const role of ['mes', 'warehouse', 'sales', 'purchase', ...(config.mode === 'multiRole' ? ['admin'] : [])]) {
    tokens[role] = process.env[`WRITE_BENCHMARK_${role.toUpperCase()}_TOKEN`];
    assert(tokens[role], `缺少 ${role} 岗位令牌环境变量`);
  }
  const source = await sourceFacts(config, JSON.parse(await readFile(config.manifest, 'utf8')), tokens);
  const preview = { runId: config.runId, mode: config.mode, planned: config.mode === 'hotspot' ? config.count : config.count * 3,
    hotspot: { productId: source.facts.rawId, warehouseId: source.facts.rawWarehouseId,
      locationId: source.balance.dimension.locationId }, availableQty: source.balance.availableQty,
    database: config.dbName, apiOrigin: config.api.origin, status: 'PRECHECKED' };
  if (!config.execute) return preview;
  assert(process.env.WRITE_BENCHMARK_ISOLATED === config.runId, '执行前必须把 WRITE_BENCHMARK_ISOLATED 设为本轮 runId');
  const manifest = { runId: config.runId, mode: config.mode, jobs: [],
    ...(config.mode === 'hotspot' ? { hotspot: preview.hotspot } : {}) };
  for (let index = 1; index <= config.count; index++) {
    const issue = await prepareIssue(config, tokens, source, index);
    manifest.jobs.push(issue);
    if (config.mode === 'multiRole') manifest.jobs.push(...await prepareOrders(config, tokens, source, index));
    await persist(config.report, { status: 'PREPARING', ...manifest });
  }
  validateManifest(manifest);
  await persist(config.report, manifest);
  return { status: 'PREPARED', runId: manifest.runId, mode: manifest.mode, jobs: manifest.jobs.length,
    output: config.report, hotspot: manifest.hotspot };
}

/** 用途：提供统一入口的准备阶段；入参为 CLI，可带 --prepare-isolated；出参为预检或完整 manifest 摘要。 */
export async function prepareFixture(args) {
  const requested = args.includes('--prepare-isolated');
  const normalized = args.filter(value => value !== '--prepare-isolated');
  if (requested && !normalized.includes('--execute-isolated')) normalized.push('--execute-isolated');
  const config = options(normalized);
  if (config.help) return '用法：node backend/scripts/write-baseline.mjs --prepare-isolated --source-report role-flow-result.json --mode hotspot|multiRole --count 2..30 --run-id BENCH-XXXXXXXX --output manifest.json --base-url http://127.0.0.1:21001/ --db-host 127.0.0.1 --db-port 55432 --db-name wmscap_隔离库。详见 WRITE-BASELINE.md';
  return prepare(config);
}
