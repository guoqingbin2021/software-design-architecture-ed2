import test from 'node:test';
import assert from 'node:assert/strict';
import { useEnrollmentPage } from '../src/composables/useEnrollmentPage.js';

const student = { username: 'student101', studentId: '101', role: 'STUDENT' };
const dto = { id: 1001, teachingClassId: 501, status: 'ACTIVE', courseName: '软件设计与体系结构' };
const item = {
  id: 1001,
  teachingClassId: 501,
  courseName: dto.courseName,
  teacherName: null,
  room: null,
  week: null,
  time: null,
  status: 'ACTIVE',
  score: null,
};
const failure = (kind, status = 0, message = '模拟失败') =>
  Object.assign(new Error(message), {
    kind,
    status,
    requestId: 'http-test',
    fields: {},
    code: status === 404 ? 'NOT_FOUND' : 'INTERNAL_ERROR',
  });
const deferred = () => {
  let resolve, reject;
  const promise = new Promise((yes, no) => {
    resolve = yes;
    reject = no;
  });
  return { promise, resolve, reject };
};
const memoryStorage = () => {
  const entries = new Map();
  return {
    getItem: (key) => entries.get(key) ?? null,
    setItem: (key, value) => entries.set(key, value),
    removeItem: (key) => entries.delete(key),
  };
};
function setup(overrides = {}, options = {}) {
  const api = {
    listTimetable: async () => [],
    enroll: async () => dto,
    requestStatus: async () => dto,
    withdraw: async () => null,
    ...overrides,
  };
  const page = useEnrollmentPage('2026秋', '501', {
    api,
    storage: null,
    createKey: () => 'original-business-key',
    pollDelays: [],
    delay: async () => {},
    ...options,
  });
  page.setIdentity(student);
  return { page, api };
}

test('GET A late success cannot overwrite B data, selected semester or query state', async () => {
  const a = deferred(),
    b = deferred();
  const { page } = setup({
    listTimetable: (semester) => (semester === 'A' ? a.promise : b.promise),
  });
  const first = page.loadClasses('A'),
    second = page.loadClasses('B');
  b.resolve([item]);
  await second;
  a.resolve([]);
  await first;
  assert.deepEqual(page.items.value, [item]);
  assert.equal(page.semester.value, 'B');
  assert.equal(page.queryState.value, 'data');
});

test('GET A late error including 401 cannot overwrite successful B or request reauthentication', async () => {
  const a = deferred(),
    b = deferred();
  const { page } = setup({
    listTimetable: (semester) => (semester === 'A' ? a.promise : b.promise),
  });
  const first = page.loadClasses('A'),
    second = page.loadClasses('B');
  b.resolve([item]);
  await second;
  a.reject(failure('http', 401));
  await first;
  assert.equal(page.queryState.value, 'data');
  assert.equal(page.queryError.value, '');
  assert.equal(page.authRequired.value, false);
});

test('latest GET failure retains its condition and does not show an empty successful table', async () => {
  const { page } = setup({
    listTimetable: async () => {
      throw failure('network');
    },
  });
  await page.loadClasses('B');
  assert.equal(page.semester.value, 'B');
  assert.equal(page.queryState.value, 'error');
  assert.equal(page.canEnroll.value, false);
});

test('write guards block initial loading, duplicate submit, class switching and withdrawal', async () => {
  const response = deferred();
  let calls = 0;
  const { page, api } = setup({
    enroll: async () => {
      ++calls;
      return response.promise;
    },
  });
  assert.equal(await page.enroll(), false);
  await page.loadClasses();
  const pending = page.enroll();
  assert.equal(page.submitState.value, 'submitting');
  assert.equal(page.writeBusy.value, true);
  assert.equal(await page.enroll(), false);
  assert.equal(page.selectTeachingClass('502'), false);
  assert.equal(page.askWithdraw(item), false);
  api.listTimetable = async () => [item];
  response.resolve(dto);
  await pending;
  assert.equal(calls, 1);
  assert.equal(page.submitState.value, 'confirmed');
  assert.equal(page.canEnroll.value, false);
  page.selectTeachingClass('502');
  assert.equal(page.canEnroll.value, true);
});

test('POST timeout is uncertain, polling 404 is not failure, later snapshot confirms', async () => {
  let checks = 0;
  const status = deferred();
  const { page, api } = setup(
    {
      enroll: async () => {
        throw failure('network');
      },
      requestStatus: async (key, classId) => {
        assert.equal(key, 'original-business-key');
        assert.equal(classId, 501);
        if (++checks === 1) throw failure('http', 404);
        return status.promise;
      },
    },
    { pollDelays: [0, 0, 0] },
  );
  await page.loadClasses();
  const operation = page.enroll();
  await new Promise((resolve) => setImmediate(resolve));
  assert.equal(page.submitState.value, 'uncertain');
  assert.equal(page.writeBusy.value, true);
  assert.equal(page.canCheckResult.value, false);
  api.listTimetable = async () => [item];
  status.resolve(dto);
  await operation;
  assert.equal(checks, 2);
  assert.equal(page.submitState.value, 'confirmed');
  assert.equal(page.pendingPost.value, null);
});

test('finite 404 polling leaves original key/body and blocked writes', async () => {
  let checks = 0;
  const { page } = setup(
    {
      enroll: async () => {
        throw failure('http', 500);
      },
      requestStatus: async () => {
        ++checks;
        throw failure('http', 404);
      },
    },
    { pollDelays: [0, 0, 0] },
  );
  await page.loadClasses();
  await page.enroll();
  assert.equal(checks, 3);
  assert.equal(page.submitState.value, 'uncertain');
  assert.equal(page.canEnroll.value, false);
  assert.equal(page.canCheckResult.value, true);
  assert.equal(page.pendingPost.value.key, 'original-business-key');
  assert.match(page.message.value, /404 不能证明选课失败/);
});

test('manual retry preserves original key and original body', async () => {
  const calls = [];
  const { page, api } = setup({
    enroll: async (body, key) => {
      calls.push({ body, key });
      if (calls.length === 1) throw failure('network');
      return dto;
    },
  });
  await page.loadClasses();
  await page.enroll();
  assert.equal(page.selectTeachingClass('502'), false);
  api.listTimetable = async () => [item];
  await page.retryOriginal();
  assert.deepEqual(calls, [
    { body: { teachingClassId: 501 }, key: 'original-business-key' },
    { body: { teachingClassId: 501 }, key: 'original-business-key' },
  ]);
  assert.equal(page.submitState.value, 'confirmed');
});

test('a refused retry cannot disprove the earlier unknown write', async () => {
  let attempt = 0;
  const { page } = setup({
    enroll: async () => {
      throw failure(++attempt === 1 ? 'network' : 'http', attempt === 1 ? 0 : 403);
    },
  });
  await page.loadClasses();
  await page.enroll();
  await page.retryOriginal();
  assert.equal(page.submitState.value, 'uncertain');
  assert.notEqual(page.pendingPost.value, null);
});

test('first POST definite 409 refusal restores idle; protocol 400 does not', async () => {
  const { page, api } = setup({
    enroll: async () => {
      throw failure('http', 409);
    },
  });
  await page.loadClasses();
  await page.enroll();
  assert.equal(page.submitState.value, 'idle');
  assert.equal(page.pendingPost.value, null);
  api.enroll = async () => {
    throw failure('protocol', 400);
  };
  await page.enroll();
  assert.equal(page.submitState.value, 'uncertain');
});

test('invalid success DTO remains uncertain', async () => {
  const { page } = setup({ enroll: async () => ({ ...dto, teachingClassId: '999' }) });
  await page.loadClasses();
  await page.enroll();
  assert.equal(page.submitState.value, 'uncertain');
  assert.equal(page.canEnroll.value, false);
});

test('401 while confirming preserves key through reauthentication with the same account', async () => {
  const { page, api } = setup({
    enroll: async () => {
      throw failure('network');
    },
    requestStatus: async () => {
      throw failure('http', 401);
    },
  });
  await page.loadClasses();
  await page.enroll();
  await page.confirmResult();
  assert.equal(page.authRequired.value, true);
  assert.equal(page.submitState.value, 'uncertain');
  const key = page.pendingPost.value.key;
  page.setIdentity({ ...student });
  assert.equal(page.pendingPost.value.key, key);
  assert.equal(page.canCheckResult.value, true);
  api.requestStatus = async () => dto;
  api.listTimetable = async () => [item];
  await page.confirmResult();
  assert.equal(page.submitState.value, 'confirmed');
});

test('DELETE 204 and failed refresh keep withdrawal confirmed, not uncertain', async () => {
  let count = 0;
  const { page } = setup({
    listTimetable: async () => {
      if (++count > 1) throw failure('network');
      return [item];
    },
  });
  await page.loadClasses();
  page.askWithdraw(item);
  await page.withdraw();
  assert.equal(page.withdrawState.value, 'confirmed');
  assert.equal(page.queryState.value, 'error');
  assert.equal(page.canEnroll.value, false);
  assert.match(page.message.value, /退课已完成，课程表刷新失败/);
});

test('DELETE uncertainty keeps identifiers, never polls POST status or interprets an empty table as success', async () => {
  let checks = 0;
  const { page, api } = setup({
    listTimetable: async () => [item],
    withdraw: async () => {
      throw failure('network');
    },
    requestStatus: async () => {
      ++checks;
      return dto;
    },
  });
  await page.loadClasses();
  page.askWithdraw(item);
  await page.withdraw();
  assert.equal(page.withdrawState.value, 'uncertain');
  assert.equal(page.pendingWithdrawal.value.id, 1001);
  assert.equal(page.pendingWithdrawal.value.requestId, 'http-test');
  assert.ok(page.pendingWithdrawal.value.submittedAt);
  api.listTimetable = async () => [];
  await page.loadClasses();
  await page.confirmResult();
  await page.withdraw();
  assert.equal(checks, 0);
  assert.equal(page.withdrawState.value, 'uncertain');
  assert.equal(page.canEnroll.value, false);
});

test('withdrawal confirmation can cancel; duplicate confirmation does not repeat DELETE', async () => {
  const response = deferred();
  let calls = 0;
  const { page, api } = setup({
    listTimetable: async () => [item],
    withdraw: async () => {
      ++calls;
      return response.promise;
    },
  });
  await page.loadClasses();
  page.askWithdraw(item);
  assert.equal(page.writeBusy.value, true);
  assert.equal(page.cancelWithdraw(), true);
  assert.equal(calls, 0);
  page.askWithdraw(item);
  const operation = page.withdraw();
  assert.equal(await page.withdraw(), false);
  assert.equal(page.cancelWithdraw(), false);
  api.listTimetable = async () => [];
  response.resolve(null);
  await operation;
  assert.equal(calls, 1);
  assert.equal(page.canEnroll.value, true);
});

test('nonstudent cannot trigger a write by calling the action directly', async () => {
  const { page } = setup({ listTimetable: async () => [item] });
  await page.loadClasses();
  page.setIdentity({ username: 'teacher201', role: 'TEACHER', studentId: null });
  assert.equal(page.isStudent.value, false);
  assert.equal(await page.enroll(), false);
  assert.equal(page.askWithdraw(item), false);
  assert.equal(await page.withdraw(), false);
});

test('reload restores pending original POST per identity; other student gets separate state', async () => {
  const storage = memoryStorage();
  const { page } = setup(
    {
      enroll: async () => {
        throw failure('network');
      },
    },
    { storage },
  );
  await page.loadClasses();
  await page.enroll();
  const reloaded = setup({}, { storage }).page;
  assert.equal(reloaded.submitState.value, 'uncertain');
  assert.equal(reloaded.pendingPost.value.key, page.pendingPost.value.key);
  assert.equal(reloaded.canEnroll.value, false);
  reloaded.setIdentity({ username: 'student102', studentId: '102', role: 'STUDENT' });
  assert.equal(reloaded.pendingPost.value, null);
  assert.equal(reloaded.submitState.value, 'idle');
});

test('reload restores unresolved DELETE; no automatic recovery from fresh timetable', async () => {
  const storage = memoryStorage();
  const { page } = setup(
    {
      listTimetable: async () => [item],
      withdraw: async () => {
        throw failure('network');
      },
    },
    { storage },
  );
  await page.loadClasses();
  page.askWithdraw(item);
  await page.withdraw();
  const restored = setup({}, { storage }).page;
  await restored.loadClasses();
  assert.equal(restored.withdrawState.value, 'uncertain');
  assert.equal(restored.pendingWithdrawal.value.id, 1001);
  assert.equal(restored.canEnroll.value, false);
});

test('unreadable persisted pending state fails closed instead of enabling a new write', async () => {
  const { page } = setup(
    {},
    { storage: { getItem: () => '{broken', setItem: () => {}, removeItem: () => {} } },
  );
  await page.loadClasses();
  assert.equal(page.canEnroll.value, false);
  assert.equal(page.canWithdraw.value, false);
  assert.match(page.message.value, /待确认记录无法读取/);
});
