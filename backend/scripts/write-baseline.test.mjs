import assert from 'node:assert/strict';
import { test } from 'node:test';
import { parseOptions, percentile, quantityUnits, validateManifest } from './write-baseline.mjs';

const idA = '11111111-1111-4111-8111-111111111111';
const idB = '22222222-2222-4222-8222-222222222222';
const orderA = '33333333-3333-4333-8333-333333333333';
const orderB = '44444444-4444-4444-8444-444444444444';
const isolated = ['--manifest', 'fixture.json', '--base-url', 'http://127.0.0.1:21001/',
  '--db-host', '127.0.0.1', '--db-port', '55432', '--db-name', 'wmscap_20260928_5xnobauy'];

/** 用途：离线验证隔离地址门槛；入参和出参由 node:test 管理，所有断言都不打开网络连接。 */
test('拒绝开发端口、开发库、外部地址及重复参数', () => {
  assert.equal(parseOptions(isolated).execute, false);
  assert.throws(() => parseOptions(isolated.map(value => value === '55432' ? '5433' : value)), /隔离 PostgreSQL/);
  assert.throws(() => parseOptions(isolated.map(value => value === 'wmscap_20260928_5xnobauy' ? 'ai_learn' : value)), /数据库名/);
  assert.throws(() => parseOptions(isolated.map(value => value === 'http://127.0.0.1:21001/' ? 'http://127.0.0.1:20001/' : value)), /默认端口/);
  assert.throws(() => parseOptions(isolated.map(value => value === 'http://127.0.0.1:21001/' ? 'http://192.0.2.1:21001/' : value)), /127.0.0.1/);
  assert.throws(() => parseOptions([...isolated, '--db-port', '55433']), /重复参数/);
});

/** 用途：离线验证只接受不重复的受控业务对象；入参和出参由 node:test 管理，不构造写请求。 */
test('热点和多岗位清单必须唯一且类型固定', () => {
  const hotspot = { runId: 'BENCH-ABCDEF12', mode: 'hotspot', hotspot: { productId: idA, warehouseId: idB, locationId: orderA },
    jobs: [{ kind: 'materialIssueConfirm', id: idA, workOrderId: orderA },
      { kind: 'materialIssueConfirm', id: idB, workOrderId: orderB }] };
  assert.equal(validateManifest(hotspot).length, 2);
  assert.throws(() => validateManifest({ ...hotspot, jobs: [hotspot.jobs[0], hotspot.jobs[0]] }), /同一单据/);
  assert.throws(() => validateManifest({ ...hotspot, jobs: [hotspot.jobs[0], { ...hotspot.jobs[1], workOrderId: orderA }] }), /不同工单/);
  assert.throws(() => validateManifest({ ...hotspot, jobs: [hotspot.jobs[0], { kind: 'salesSubmit', id: idB }] }), /只接受领料/);
  assert.throws(() => validateManifest({ ...hotspot, jobs: [{ ...hotspot.jobs[0], path: '/api/any' }, hotspot.jobs[1]] }), /字段非法/);
  assert.equal(validateManifest({ runId: 'BENCH-ABCDEF12', mode: 'multiRole', jobs: [
    { kind: 'salesSubmit', id: idA }, { kind: 'purchaseSubmit', id: idB },
  ] }).length, 2);
});

/** 用途：离线验证六位定点库存与尾部延迟统计；入参和出参由 node:test 管理，防止浮点及空样本误报。 */
test('数量和延迟统计有界', () => {
  assert.equal(quantityUnits('34.000001'), 34000001n);
  assert.throws(() => quantityUnits('1.0000001'), /六位小数/);
  assert.equal(percentile([1, 2, 3, 4, 5], 0.95), 5);
  assert.equal(percentile([], 0.99), null);
});
