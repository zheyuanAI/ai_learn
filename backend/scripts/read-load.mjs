import { performance } from 'node:perf_hooks';
import { mkdir, writeFile } from 'node:fs/promises';
import { dirname, resolve } from 'node:path';

/** 用途：解析命令行并验证逐级压测参数；入参为命令行参数；出参为规范化配置，无默认容量承诺。 */
function options(args) {
  const values = new Map();
  for (let index = 0; index < args.length; index += 2) {
    if (!args[index].startsWith('--') || !args[index + 1]) throw new Error('参数必须为 --名称 值');
    values.set(args[index].slice(2), args[index + 1]);
  }
  const base = new URL(values.get('base-url') || 'http://127.0.0.1:20001');
  const path = values.get('path') || '/api/inventory/balances?page=1&size=20';
  if (!path.startsWith('/api/') || path.startsWith('//')) throw new Error('path 必须是现有 /api/ 查询路径');
  const levels = (values.get('concurrency') || '1,5,10,20').split(',').map(Number);
  const requests = Number(values.get('requests') || 200);
  const timeoutMs = Number(values.get('timeout-ms') || 10000);
  if (![...levels, requests, timeoutMs].every(value => Number.isSafeInteger(value) && value > 0)
      || levels.some(value => value > 500) || requests > 100000 || timeoutMs > 60000) {
    throw new Error('并发须为 1–500，请求数为 1–100000，超时为 1–60000ms 的整数');
  }
  const url = new URL(path, base);
  if (url.origin !== base.origin) throw new Error('查询地址必须属于指定服务');
  return { url, levels, requests, timeoutMs, output: values.get('output') };
}

/** 用途：计算观测分位数；入参为排序后的毫秒样本和分位值；出参为实际样本值，空样本返回 null。 */
function percentile(sorted, fraction) {
  return sorted.length ? Number(sorted[Math.max(0, Math.ceil(sorted.length * fraction) - 1)].toFixed(2)) : null;
}

/**
 * 用途：以固定工作线程数执行一个 GET 档位；入参为配置、并发数和令牌；出参为吞吐、延迟及错误统计。
 * 流程：领取请求序号、设置超时、校验 HTTP/业务结果，再聚合；不输出令牌或响应业务明细。
 */
async function measure(config, concurrency, token) {
  let next = 0;
  let succeeded = 0;
  const durations = [];
  const errors = {};
  const started = performance.now();
  await Promise.all(Array.from({ length: Math.min(concurrency, config.requests) }, async () => {
    while (next++ < config.requests) {
      const requestStart = performance.now();
      try {
        const response = await fetch(config.url, {
          headers: { Authorization: `Bearer ${token}` },
          signal: AbortSignal.timeout(config.timeoutMs),
          redirect: 'error',
        });
        const body = await response.json();
        if (!response.ok || body.code !== 200 || body.success === false) {
          const reason = `HTTP_${response.status}_CODE_${String(body.code).slice(0, 32)}`;
          errors[reason] = (errors[reason] || 0) + 1;
        } else {
          succeeded++;
        }
      } catch (error) {
        const reason = error.name || 'RequestError';
        errors[reason] = (errors[reason] || 0) + 1;
      } finally {
        durations.push(performance.now() - requestStart);
      }
    }
  }));
  const seconds = (performance.now() - started) / 1000;
  durations.sort((a, b) => a - b);
  return {
    concurrency, requests: durations.length, succeeded, failed: durations.length - succeeded,
    elapsedSeconds: Number(seconds.toFixed(3)), requestsPerSecond: Number((durations.length / seconds).toFixed(2)),
    successPerSecond: Number((succeeded / seconds).toFixed(2)),
    p50Ms: percentile(durations, 0.5), p95Ms: percentile(durations, 0.95), p99Ms: percentile(durations, 0.99), errors,
  };
}

const config = options(process.argv.slice(2));
const token = process.env.BENCHMARK_TOKEN;
if (!token) throw new Error('请通过 BENCHMARK_TOKEN 注入已登录业务角色令牌');
const report = { measuredAt: new Date().toISOString(), method: 'GET', endpoint: config.url.toString(), levels: [] };
for (const level of config.levels) {
  const result = await measure(config, level, token);
  report.levels.push(result);
  console.log(JSON.stringify(result));
}
if (config.output) {
  const output = resolve(config.output);
  await mkdir(dirname(output), { recursive: true });
  await writeFile(output, JSON.stringify(report, null, 2) + '\n');
}
if (report.levels.some(level => level.failed > 0)) process.exitCode = 1;
