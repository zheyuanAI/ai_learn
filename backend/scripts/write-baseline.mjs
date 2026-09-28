import assert from 'node:assert/strict';
import { randomUUID } from 'node:crypto';
import { execFile } from 'node:child_process';
import { readFile, mkdir, writeFile } from 'node:fs/promises';
import { performance } from 'node:perf_hooks';
import { dirname, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';
import { promisify } from 'node:util';

const execFileAsync = promisify(execFile);
const UUID = /^[0-9a-f]{8}-[0-9a-f]{4}-[1-8][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/i;
const RUN_ID = /^BENCH-[A-Z0-9]{8,24}$/;
const JOBS = {
  materialIssueConfirm: { role: 'warehouse', table: 'mes_material_issue', number: 'issue_no', path: id => `/api/material-issues/${id}/confirm` },
  salesSubmit: { role: 'sales', table: 'sales_order', number: 'so_no', path: id => `/api/sales-orders/${id}/submit` },
  purchaseSubmit: { role: 'purchase', table: 'purchase_order', number: 'po_no', path: id => `/api/purchase-orders/${id}/submit` },
};

/** 用途：解析有界命令参数并拒绝项目开发服务；入参为 argv；出参为隔离运行配置，不读取凭据。 */
export function parseOptions(args) {
  if (args.length === 1 && args[0] === '--help') return { help: true };
  const values = new Map();
  for (let index = 0; index < args.length; index++) {
    const name = args[index];
    if (name === '--execute-isolated') {
      assert(!values.has('execute-isolated'), '重复执行开关');
      values.set('execute-isolated', true);
      continue;
    }
    assert(name.startsWith('--') && index + 1 < args.length && !args[index + 1].startsWith('--'), '参数必须为 --名称 值');
    assert(!values.has(name.slice(2)), `重复参数：${name}`);
    values.set(name.slice(2), args[++index]);
  }
  for (const key of values.keys()) assert(['manifest', 'base-url', 'db-host', 'db-port', 'db-name', 'concurrency', 'timeout-ms', 'sample-ms', 'report', 'execute-isolated'].includes(key), `未知参数：${key}`);
  for (const key of ['manifest', 'base-url', 'db-host', 'db-port', 'db-name']) assert(values.get(key), `缺少 --${key}`);
  const api = new URL(values.get('base-url'));
  assert(api.protocol === 'http:' && api.hostname === '127.0.0.1' && !api.username && !api.password && api.pathname === '/' && !api.search && !api.hash, 'API 只能是无凭据的 127.0.0.1 隔离 HTTP 根地址');
  assert(!['20001', '10003'].includes(api.port) && Number(api.port) >= 1024, '禁止使用现有 Gateway/Core 默认端口');
  const dbPort = Number(values.get('db-port'));
  const dbName = values.get('db-name');
  assert(values.get('db-host') === '127.0.0.1' && Number.isSafeInteger(dbPort) && dbPort >= 1024 && dbPort <= 65535 && dbPort !== 5433, '隔离 PostgreSQL 只能是 127.0.0.1 非 5433 端口');
  assert(/^(bench|wmscap)_[a-z0-9_]{8,48}$/.test(dbName) && dbName !== 'ai_learn', '数据库名必须是独立 bench_ 或 wmscap_ 前缀');
  const concurrency = Number(values.get('concurrency') ?? 4);
  const timeoutMs = Number(values.get('timeout-ms') ?? 15000);
  const sampleMs = Number(values.get('sample-ms') ?? 500);
  assert(Number.isSafeInteger(concurrency) && concurrency >= 1 && concurrency <= 64, '并发必须为 1–64');
  assert(Number.isSafeInteger(timeoutMs) && timeoutMs >= 1000 && timeoutMs <= 60000, '请求超时必须为 1000–60000ms');
  assert(Number.isSafeInteger(sampleMs) && sampleMs >= 200 && sampleMs <= 5000, '锁采样间隔必须为 200–5000ms');
  return { manifest: resolve(values.get('manifest')), api, dbHost: '127.0.0.1', dbPort, dbName,
    concurrency, timeoutMs, sampleMs, report: values.get('report') && resolve(values.get('report')),
    execute: values.has('execute-isolated') };
}

/** 用途：验证预置工作单据唯一性和热点维度；入参为 manifest；出参为只含受控路由的命令列表。 */
export function validateManifest(manifest) {
  assert(manifest && RUN_ID.test(manifest.runId), 'runId 必须是 BENCH- 加 8–24 位大写字母或数字');
  assert(['hotspot', 'multiRole'].includes(manifest.mode), 'mode 只能是 hotspot 或 multiRole');
  assert(Array.isArray(manifest.jobs) && manifest.jobs.length >= 2 && manifest.jobs.length <= 1000, '预置命令数必须为 2–1000');
  const ids = new Set();
  const jobs = manifest.jobs.map((item, index) => {
    assert(item && ['id,kind,workOrderId', 'id,kind'].includes(Object.keys(item).sort().join()), `第 ${index + 1} 条命令字段非法`);
    assert(JOBS[item.kind] && UUID.test(item.id), `第 ${index + 1} 条命令类型或 ID 非法`);
    assert(!ids.has(item.id.toLowerCase()), '同一单据不得重复进入批次');
    ids.add(item.id.toLowerCase());
    if (item.kind === 'materialIssueConfirm') assert(UUID.test(item.workOrderId), '领料确认必须指定 workOrderId 供只读预检');
    else assert(!('workOrderId' in item), '销售/采购命令不接受 workOrderId');
    return { kind: item.kind, id: item.id.toLowerCase(), workOrderId: item.workOrderId?.toLowerCase(), ...JOBS[item.kind] };
  });
  if (manifest.mode === 'hotspot') {
    assert(jobs.every(job => job.kind === 'materialIssueConfirm'), '热点库存只接受领料确认');
    assert(manifest.hotspot && ['productId', 'warehouseId', 'locationId'].every(key => UUID.test(manifest.hotspot[key])), '热点维度必须包含三个真实 UUID');
    assert(new Set(jobs.map(job => job.workOrderId)).size === jobs.length, '热点样本必须使用不同工单，避免只测工单行锁');
  } else {
    assert(!('hotspot' in manifest), '多岗位批次不得传入热点维度');
    assert(new Set(jobs.map(job => job.role)).size >= 2, '多岗位批次至少包含两个不同角色');
  }
  return jobs;
}

/** 用途：将库存数量转成六位定点整数；入参为 PostgreSQL 数值；出参为 BigInt，避免浮点余额误判。 */
export function quantityUnits(value) {
  const text = String(value);
  assert(/^\d+(?:\.\d{1,6})?$/.test(text), '库存数量必须是非负且最多六位小数');
  const [whole, fractional = ''] = text.split('.');
  return BigInt(whole) * 1000000n + BigInt(fractional.padEnd(6, '0'));
}

/** 用途：由六位定点整数输出数量；入参为 BigInt；出参为无多余尾零的十进制字符串。 */
function quantityText(units) {
  const sign = units < 0n ? '-' : '';
  const absolute = units < 0n ? -units : units;
  return sign + `${absolute / 1000000n}.${String(absolute % 1000000n).padStart(6, '0')}`.replace(/\.?0+$/, '');
}

/** 用途：计算真实样本延迟分位数；入参为升序毫秒样本与百分位；出参为毫秒或 null。 */
export function percentile(sorted, fraction) {
  return sorted.length ? Number(sorted[Math.ceil(sorted.length * fraction) - 1].toFixed(2)) : null;
}

/** 用途：通过独立只读 psql 连接查询隔离库；入参为配置与固定模板 SQL；出参为 JSON 行，不输出连接口令。 */
export async function pgRows(config, sql) {
  assert(process.env.WRITE_BENCHMARK_PG_USER, '缺少隔离 PG 用户环境变量');
  const command = process.env.WRITE_BENCHMARK_PSQL || 'psql';
  try {
    const { stdout } = await execFileAsync(command, ['-X', '-qAt', '-v', 'ON_ERROR_STOP=1', '-h', config.dbHost,
      '-p', String(config.dbPort), '-U', process.env.WRITE_BENCHMARK_PG_USER, '-d', config.dbName, '-c', sql],
    { windowsHide: true, timeout: 10000, maxBuffer: 4 * 1024 * 1024,
      env: { ...process.env, PGPASSWORD: process.env.WRITE_BENCHMARK_PG_PASSWORD || '',
        PGCONNECT_TIMEOUT: '4', PGOPTIONS: '-c default_transaction_read_only=on' } });
    return stdout.trim().split(/\r?\n/).filter(Boolean).map(line => JSON.parse(line));
  } catch {
    throw new Error('隔离 PG 只读查询失败；检查 psql、权限、数据库与网络，凭据不会写入报告');
  }
}

/** 用途：查询已验证 UUID 对应的当前数据库事实；入参为命令集合；出参为 ID 映射，所有 SQL 只读且过滤 isdel=0。 */
async function databaseFacts(config, jobs) {
  const result = new Map();
  for (const kind of Object.keys(JOBS)) {
    const current = jobs.filter(job => job.kind === kind);
    if (!current.length) continue;
    const definition = JOBS[kind];
    const ids = current.map(job => `'${job.id}'::uuid`).join(',');
    const extra = kind === 'materialIssueConfirm'
      ? ", work_order_id AS \"workOrderId\", (SELECT coalesce(json_agg(json_build_object('productId', l.product_id, 'warehouseId', l.warehouse_id, 'locationId', l.location_id, 'quantity', l.issue_qty::text)), '[]'::json) FROM mes_material_issue_line l WHERE l.material_issue_id = d.id AND l.tenant_id = d.tenant_id AND l.isdel = 0) AS items"
      : '';
    const sql = `SELECT row_to_json(t) FROM (SELECT id, tenant_id AS \"tenantId\", ${definition.number} AS \"number\", status${extra} FROM ${definition.table} d WHERE id IN (${ids}) AND isdel = 0) t`;
    for (const row of await pgRows(config, sql)) result.set(String(row.id).toLowerCase(), row);
  }
  return result;
}

/** 用途：读取隔离数据库的单一热点余额；入参为三个 UUID；出参为活跃余额，拒绝多批次歧义。 */
async function hotspotBalance(config, dimension) {
  const { productId, warehouseId, locationId } = dimension;
  const sql = `SELECT row_to_json(t) FROM (SELECT id, tenant_id AS \"tenantId\", lot_no AS \"lotNo\", on_hand_qty::text AS \"onHandQty\", reserved_qty::text AS \"reservedQty\", version FROM inv_inventory_balance WHERE product_id='${productId}'::uuid AND warehouse_id='${warehouseId}'::uuid AND location_id='${locationId}'::uuid AND isdel=0) t`;
  const rows = await pgRows(config, sql);
  assert(rows.length === 1, '热点维度必须只有一条有效余额，避免批次选择改变锁争用对象');
  return rows[0];
}

/** 用途：使用对应岗位令牌读取实际服务事实；入参为 URL、令牌、路径和超时；出参为 data，拒绝跳转与错误包装。 */
export async function apiData(config, token, path) {
  const response = await fetch(new URL(path, config.api), { headers: { Authorization: `Bearer ${token}` },
    redirect: 'error', signal: AbortSignal.timeout(config.timeoutMs) });
  const body = await response.json();
  assert(response.ok && body.code === 200 && body.success !== false, `API 预检失败：${path} HTTP ${response.status}`);
  return body.data;
}

/** 用途：核对 API 与隔离 PG 指向同一批唯一 Draft 事实；入参为配置、清单和岗位令牌；出参为热点初始余额或 null。 */
async function preflight(config, manifest, jobs, tokens) {
  const identity = (await pgRows(config, 'SELECT row_to_json(t) FROM (SELECT current_database() AS database, inet_server_port() AS port, current_setting(\'server_version_num\') AS version) t'))[0];
  assert(identity?.database === config.dbName && Number(identity.port) === config.dbPort, 'PostgreSQL 实际身份与指定隔离库不一致');
  const rows = await databaseFacts(config, jobs);
  assert(rows.size === jobs.length, '隔离 PG 缺少预置单据或含已软删除单据');
  for (const job of jobs) {
    const row = rows.get(job.id);
    assert(row.status === 'Draft' && row.number?.startsWith(`${manifest.runId}-`), '单据必须是本轮 runId 专属 Draft');
    if (job.kind === 'materialIssueConfirm') {
      assert(String(row.workOrderId).toLowerCase() === job.workOrderId && row.items.length === 1, '领料单必须绑定声明工单且只有一行');
    }
    const data = job.kind === 'materialIssueConfirm'
      ? (await apiData(config, tokens[job.role], `/api/material-issues?work_order_id=${job.workOrderId}`)).find(item => String(item.id).toLowerCase() === job.id)
      : await apiData(config, tokens[job.role], job.kind === 'salesSubmit' ? `/api/sales-orders/${job.id}` : `/api/purchase-orders/${job.id}`);
    assert(data && String(data.id).toLowerCase() === job.id && data.status === 'Draft'
      && data[job.kind === 'materialIssueConfirm' ? 'issueNo' : job.kind === 'salesSubmit' ? 'soNo' : 'poNo'] === row.number,
    '隔离 API 和 PG 预置事实不一致');
    if (job.kind === 'materialIssueConfirm') assert(String(data.tenantId).toLowerCase() === String(row.tenantId).toLowerCase(), '领料租户不一致');
  }
  if (manifest.mode !== 'hotspot') return null;
  const first = rows.get(jobs[0].id);
  const balance = await hotspotBalance(config, manifest.hotspot);
  assert(String(balance.tenantId).toLowerCase() === String(first.tenantId).toLowerCase(), '热点余额租户不一致');
  let needed = 0n;
  for (const job of jobs) {
    const row = rows.get(job.id);
    const line = row.items[0];
    assert(String(row.tenantId).toLowerCase() === String(balance.tenantId).toLowerCase()
      && ['productId', 'warehouseId', 'locationId'].every(key => String(line[key]).toLowerCase() === manifest.hotspot[key].toLowerCase()), '领料必须全部争用同一租户与库存维度');
    needed += quantityUnits(line.quantity);
  }
  assert(needed > 0n && needed <= quantityUnits(balance.onHandQty) - quantityUnits(balance.reservedQty), '热点待领总量超过可用库存');
  return { balance, needed: quantityText(needed) };
}

/** 用途：读取 PG 原生锁等待和死锁计数；入参为隔离配置；出参为单次指标，不把未观测视为无等待。 */
async function databaseMetrics(config) {
  const sql = "SELECT row_to_json(t) FROM (SELECT (SELECT count(*) FROM pg_stat_activity WHERE datname=current_database() AND wait_event_type='Lock') AS \"lockWaiters\", (SELECT deadlocks FROM pg_stat_database WHERE datname=current_database()) AS deadlocks) t";
  return (await pgRows(config, sql))[0];
}

/** 用途：发出一次无请求体的真实业务命令；入参为隔离 API、预置命令、令牌与固定新键；出参为请求结果，不自动重试。 */
async function command(config, job, token, key) {
  const started = performance.now();
  try {
    const response = await fetch(new URL(job.path(job.id), config.api), { method: 'POST', redirect: 'error',
      headers: { Authorization: `Bearer ${token}`, 'Idempotency-Key': key },
      signal: AbortSignal.timeout(config.timeoutMs) });
    const body = await response.json();
    return { kind: job.kind, role: job.role, id: job.id, ok: response.ok && body.code === 200 && body.success !== false,
      reason: response.ok && body.code === 200 && body.success !== false ? null : `HTTP_${response.status}_CODE_${String(body.code).slice(0, 24)}`,
      elapsedMs: performance.now() - started };
  } catch (error) {
    return { kind: job.kind, role: job.role, id: job.id, ok: false, reason: error.name || 'RequestError', elapsedMs: performance.now() - started };
  }
}

/** 用途：汇总一次有限写批次；入参为结果列表及实耗秒数；出参为 QPS、分位数和失败分类。 */
function summarize(results, seconds) {
  const times = results.map(result => result.elapsedMs).sort((a, b) => a - b);
  const succeeded = results.filter(result => result.ok).length;
  const errors = {};
  for (const result of results) if (!result.ok) errors[result.reason] = (errors[result.reason] || 0) + 1;
  return { requests: results.length, succeeded, failed: results.length - succeeded,
    elapsedSeconds: Number(seconds.toFixed(3)), qps: Number((results.length / seconds).toFixed(2)),
    successQps: Number((succeeded / seconds).toFixed(2)), p95Ms: percentile(times, 0.95),
    p99Ms: percentile(times, 0.99), errors };
}

/** 用途：将无凭据报告写入调用方指定位置；入参为路径和报告；出参无，不自动创建数据库对象。 */
async function saveReport(path, report) {
  if (!path) return;
  await mkdir(dirname(path), { recursive: true });
  await writeFile(path, JSON.stringify(report, null, 2) + '\n');
}

/** 用途：执行严格预检及一次有界批次；入参为规范化配置；出参为最终报告，绝不清理业务或数据库数据。 */
async function run(config) {
  const manifest = JSON.parse(await readFile(config.manifest, 'utf8'));
  const jobs = validateManifest(manifest);
  const tokens = {};
  for (const role of new Set(jobs.map(job => job.role))) {
    tokens[role] = process.env[`WRITE_BENCHMARK_${role.toUpperCase()}_TOKEN`];
    assert(tokens[role], `缺少 ${role} 岗位令牌环境变量`);
  }
  const initial = await preflight(config, manifest, jobs, tokens);
  const report = { runId: manifest.runId, mode: manifest.mode, database: { host: config.dbHost, port: config.dbPort, name: config.dbName },
    apiOrigin: config.api.origin, checkedAt: new Date().toISOString(), status: 'PRECHECKED',
    concurrency: config.concurrency, planned: jobs.length, roles: [...new Set(jobs.map(job => job.role))],
    hotspot: initial && { dimension: manifest.hotspot, balanceBefore: initial.balance, plannedQty: initial.needed } };
  await saveReport(config.report, report);
  if (!config.execute) return report;
  assert(process.env.WRITE_BENCHMARK_ISOLATED === manifest.runId, '执行前必须把 WRITE_BENCHMARK_ISOLATED 设为本轮 runId');
  report.status = 'RUNNING';
  report.startedAt = new Date().toISOString();
  await saveReport(config.report, report);
  const before = await databaseMetrics(config);
  const lockSamples = [];
  let completed = false;
  const sampler = (async () => {
    while (!completed) {
      try { lockSamples.push(Number((await databaseMetrics(config)).lockWaiters)); }
      catch { lockSamples.push(null); }
      await new Promise(resolveWait => setTimeout(resolveWait, config.sampleMs));
    }
  })();
  const results = [];
  let next = 0;
  const started = performance.now();
  let requestSeconds;
  try {
    await Promise.all(Array.from({ length: Math.min(config.concurrency, jobs.length) }, async () => {
      while (next < jobs.length) {
        const job = jobs[next++];
        // 修改用途：一个业务 ID 仅发送一次；网络不确定时不自动重复提交或更换载荷。
        results.push(await command(config, job, tokens[job.role], randomUUID()));
      }
    }));
    // 修改用途：吞吐耗时只覆盖请求批次；等待最后一次锁采样退出不计入 QPS 分母。
    requestSeconds = (performance.now() - started) / 1000;
  } finally {
    completed = true;
    await sampler;
  }
  const after = await databaseMetrics(config);
  const finalRows = await databaseFacts(config, jobs);
  const expectedStatus = kind => kind === 'materialIssueConfirm' ? 'Confirmed' : 'Submitted';
  const committed = jobs.filter(job => finalRows.get(job.id)?.status === expectedStatus(job.kind));
  const ambiguousCommitted = results.filter(result => !result.ok && finalRows.get(result.id)?.status === expectedStatus(result.kind)).length;
  report.status = results.every(result => result.ok) && committed.length === jobs.length ? 'PASS' : 'FAIL';
  report.finishedAt = new Date().toISOString();
  report.summary = summarize(results, requestSeconds);
  report.perRole = Object.fromEntries(report.roles.map(role => [role, summarize(results.filter(result => result.role === role), requestSeconds)]));
  report.committed = committed.length;
  report.ambiguousCommitted = ambiguousCommitted;
  report.postconditionMismatch = jobs.filter(job => finalRows.get(job.id)?.status !== expectedStatus(job.kind)).map(job => ({ kind: job.kind, id: job.id }));
  report.lockObservation = { intervalMs: config.sampleMs, samples: lockSamples.length,
    validSamples: lockSamples.filter(value => value !== null).length,
    positiveSamples: lockSamples.filter(value => value > 0).length,
    maxWaiters: lockSamples.filter(value => value !== null).length ? Math.max(...lockSamples.filter(value => value !== null)) : null,
    deadlocksDelta: Number(after.deadlocks) - Number(before.deadlocks) };
  if (initial) {
    const balanceAfter = await hotspotBalance(config, manifest.hotspot);
    const committedQty = committed.reduce((sum, job) => sum + quantityUnits(finalRows.get(job.id).items[0].quantity), 0n);
    const expected = quantityUnits(initial.balance.onHandQty) - committedQty;
    report.hotspot.balanceAfter = balanceAfter;
    report.hotspot.expectedOnHandQty = quantityText(expected);
    report.hotspot.invariantPassed = quantityUnits(balanceAfter.onHandQty) === expected
      && quantityUnits(balanceAfter.reservedQty) === quantityUnits(initial.balance.reservedQty)
      && quantityUnits(balanceAfter.onHandQty) >= quantityUnits(balanceAfter.reservedQty);
    if (!report.hotspot.invariantPassed) report.status = 'FAIL';
  }
  await saveReport(config.report, report);
  return report;
}

/** 用途：从同一 CLI 入口分派只读预检、隔离准备或有限写批次；入参为命令行参数；出参无，错误转为非零退出码。 */
async function main(args) {
  try {
    if (args.includes('--prepare-isolated') || args.includes('--source-report')) {
      const { prepareFixture } = await import('./prepare-write-fixture.mjs');
      console.log(JSON.stringify(await prepareFixture(args)));
    } else {
      const config = parseOptions(args);
      if (config.help) console.log('用法：node backend/scripts/write-baseline.mjs --manifest 文件 --base-url http://127.0.0.1:非默认端口/ --db-host 127.0.0.1 --db-port 非5433 --db-name bench_或wmscap_隔离库 [--concurrency 4] [--report 文件] [--execute-isolated]。准备阶段：同脚本 --prepare-isolated --source-report 等，详见 WRITE-BASELINE.md');
      else {
        const report = await run(config);
        console.log(JSON.stringify(report));
        if (report.status === 'FAIL') process.exitCode = 1;
      }
    }
  } catch (error) {
    console.error(`写基线拒绝运行：${error.message}`);
    process.exitCode = 1;
  }
}

// 修改用途：避免 CLI 顶层 await 与准备模块的静态导入形成循环等待，拒绝路径须明确报错。
if (resolve(process.argv[1] || '') === fileURLToPath(import.meta.url)) void main(process.argv.slice(2));
