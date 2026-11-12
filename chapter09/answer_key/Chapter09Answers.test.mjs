import test from 'node:test';
import assert from 'node:assert/strict';
import {
  validateBody,
  selectionInput,
  validateSuccessDto,
  LatestQuery,
  recoverSelection,
  recoverWithdrawal,
} from './Chapter09Answers.mjs';

test('9-8 接受安全正整数', () => {
  assert.equal(validateBody({ teachingClassId: 1 }), 1);
  assert.equal(validateBody({ teachingClassId: Number.MAX_SAFE_INTEGER }), Number.MAX_SAFE_INTEGER);
});
test('9-8 拒绝字符串、小数、额外身份、空值与数组', () => {
  for (const body of [
    null,
    [],
    {},
    { teachingClassId: '1' },
    { teachingClassId: 1.5 },
    { teachingClassId: 0 },
    { teachingClassId: Number.MAX_SAFE_INTEGER + 1 },
    { teachingClassId: 1, studentId: 101 },
    { teachingClassId: NaN },
  ])
    assert.throws(() => validateBody(body), TypeError);
});
function deferred() {
  let resolve, reject;
  const promise = new Promise((a, b) => {
    resolve = a;
    reject = b;
  });
  return { promise, resolve, reject };
}
test('9-8 身份只从可信Actor取得，额外客户端身份拒绝', () => {
  const actor = Object.freeze({ role: 'STUDENT', studentId: 101 });
  assert.deepEqual(selectionInput(actor, { teachingClassId: 501 }), {
    studentId: 101, teachingClassId: 501,
  });
  assert.throws(() => selectionInput(actor, { teachingClassId: 501, studentId: 102 }), TypeError);
  for (const invalid of [null, { role: 'TEACHER', studentId: 101 }, { role: 'STUDENT', studentId: 0 }])
    assert.throws(() => selectionInput(invalid, { teachingClassId: 501 }), TypeError);
});
test('9-8 成功DTO名称、目标、数值与状态符合契约', () => {
  const dto = { id: 1001, teachingClassId: 501, status: 'ACTIVE' };
  assert.equal(validateSuccessDto(dto, 501), dto);
  for (const invalid of [
    null, { enrollmentId: 1001, teachingClassId: 501, status: 'ACTIVE' },
    { ...dto, id: '1001' }, { ...dto, id: 0 },
    { ...dto, teachingClassId: 502 }, { ...dto, status: 'WITHDRAWN' },
    { id: 1001, teachingClassId: 501 },
  ]) assert.throws(() => validateSuccessDto(invalid, 501), TypeError);
});
test('9-9 迟到成功不能覆盖新课表', async () => {
  const state = new LatestQuery(),
    a = deferred(),
    b = deferred();
  const old = state.load(() => a.promise),
    latest = state.load(() => b.promise);
  b.resolve(['new']);
  await latest;
  a.resolve(['old']);
  await old;
  assert.deepEqual(state.data, ['new']);
  assert.equal(state.loading, false);
});
test('9-9 旧错误与finally不改变新查询状态', async () => {
  const state = new LatestQuery(),
    a = deferred(),
    b = deferred();
  const old = state.load(() => a.promise),
    latest = state.load(() => b.promise);
  a.reject(new Error('old'));
  await old;
  assert.equal(state.loading, true);
  assert.equal(state.error, null);
  b.resolve(['current']);
  await latest;
  assert.deepEqual(state.data, ['current']);
});
test('9-9 退出后旧响应失效', async () => {
  const state = new LatestQuery(),
    a = deferred();
  const pending = state.load(() => a.promise);
  state.logout();
  a.resolve(['private']);
  await pending;
  assert.deepEqual(state.data, []);
  assert.equal(state.loading, false);
});
const pending = Object.freeze({ key: 'K', body: Object.freeze({ teachingClassId: 501 }) });
test('9-10 404继续待确认且保留原键原请求', async () => {
  let calls = 0;
  const result = await recoverSelection(
    pending,
    async (key) => {
      assert.equal(key, 'K');
      return { status: 404 };
    },
    async () => {
      calls++;
      return [];
    },
  );
  assert.equal(result.status, 'PENDING');
  assert.equal(result.pending, pending);
  assert.equal(calls, 0);
});
test('9-10 历史成功后按当前空课表显示', async () => {
  const trace = [];
  const result = await recoverSelection(
    pending,
    async (key) => {
      trace.push(key);
      return { status: 200, body: { id: 1001, teachingClassId: 501, status: 'ACTIVE' } };
    },
    async () => {
      trace.push('timetable');
      return [];
    },
  );
  assert.equal(result.status, 'CONFIRMED');
  assert.deepEqual(result.data, []);
  assert.equal(result.snapshot.id, 1001);
  assert.deepEqual(trace, ['K', 'timetable']);
});
test('9-10 网络错误和不匹配快照保持待确认', async () => {
  let result = await recoverSelection(
    pending,
    async () => {
      throw new Error('network');
    },
    async () => [],
  );
  assert.equal(result.status, 'PENDING');
  result = await recoverSelection(
    pending,
    async () => ({ status: 200, body: { id: 1001, teachingClassId: 502, status: 'ACTIVE' } }),
    async () => [],
  );
  assert.equal(result.status, 'PENDING');
});
test('9-10 快照已确认但课表读取失败保留查询恢复入口', async () => {
  const result = await recoverSelection(
    pending,
    async () => ({ status: 200, body: { id: 1001, teachingClassId: 501, status: 'ACTIVE' } }),
    async () => {
      throw new Error('read failed');
    },
  );
  assert.equal(result.status, 'CONFIRMED_READ_FAILED');
  assert.equal(result.snapshot.id, 1001);
});
test('9-10 退课原id消失为已不再有效', async () => {
  assert.equal((await recoverWithdrawal(1001, async () => [{ id: 1002 }])).status, 'CONFIRMED');
});
test('9-10 原id仍在只提供重试同一DELETE的选项', async () => {
  const result = await recoverWithdrawal(1001, async () => [{ id: 1001 }]);
  assert.equal(result.status, 'RETRY_SAME_DELETE');
  assert.equal(result.id, 1001);
});
test('9-10 退课查询失败或格式错误继续待确认', async () => {
  assert.equal(
    (
      await recoverWithdrawal(1001, async () => {
        throw new Error('offline');
      })
    ).status,
    'PENDING',
  );
  assert.equal((await recoverWithdrawal(1001, async () => ({ items: [] }))).status, 'PENDING');
});
