import test from 'node:test';
import assert from 'node:assert/strict';
import { createCourseStore } from '../src/store.js';

function deferred() {
  let resolve, reject;
  const promise = new Promise((yes, no) => { resolve = yes; reject = no; });
  return { promise, resolve, reject };
}

function fixture(overrides = {}, options = {}) {
  const api = {
    setCredentials() {}, clearCredentials() {},
    me: async () => ({ username: 'student1', role: 'STUDENT', personId: 1, name: '张同学' }),
    classes: async () => [], myEnrollments: async () => [],
    enroll: async () => ({ id: 7, teachingClassId: 1, status: 'ACTIVE' }),
    drop: async () => undefined, roster: async () => [], score: async () => ({}),
    ...overrides,
  };
  const saved = [];
  const store = createCourseStore({ api, makeKey: () => 'request-001', savePending: (value) => saved.push(value), ...options });
  return { store, saved, api };
}

test('较晚发出的教学班查询生效，较早返回的响应不会覆盖它', async () => {
  const first = deferred(), second = deferred();
  let count = 0;
  const { store } = fixture({ classes: () => (++count === 1 ? first.promise : second.promise) });
  const oldRead = store.loadClasses();
  const newRead = store.loadClasses();
  second.resolve([{ id: 2, courseName: '新结果' }]);
  await newRead;
  first.resolve([{ id: 1, courseName: '旧结果' }]);
  await oldRead;
  assert.equal(store.state.classes.status, 'data');
  assert.equal(store.state.classes.data[0].id, 2);
});

test('旧查询迟到的错误不会覆盖新查询成功状态', async () => {
  const first = deferred();
  let count = 0;
  const { store } = fixture({ classes: () => (++count === 1 ? first.promise : Promise.resolve([{ id: 2 }])) });
  const oldRead = store.loadClasses();
  await store.loadClasses();
  first.reject(new Error('旧请求失败'));
  await oldRead;
  assert.equal(store.state.classes.status, 'data');
  assert.equal(store.state.classes.error, null);
});

test('提交中的重复点击只发送一次 POST', async () => {
  const result = deferred();
  let posts = 0;
  const { store } = fixture({ enroll: () => { posts++; return result.promise; } });
  const firstClick = store.enroll(1);
  await store.enroll(1);
  assert.equal(posts, 1);
  assert.equal(store.state.enroll.status, 'submitting');
  result.resolve({ id: 7, teachingClassId: 1, status: 'ACTIVE' });
  await firstClick;
  assert.equal(store.state.enroll.status, 'confirmed');
});

test('写请求超时保留幂等键，普通重复点击只查询确认而不再次 POST', async () => {
  const seenKeys = [];
  const { store, saved } = fixture({ enroll: async (_, key) => { seenKeys.push(key); throw Object.assign(new Error('请求超时'), { uncertain: true }); } });
  await store.enroll(1);
  assert.equal(store.state.enroll.status, 'uncertain');
  assert.equal(store.state.enroll.key, 'request-001');
  await store.enroll(1);
  assert.deepEqual(seenKeys, ['request-001']);
  assert.equal(saved.at(-1).key, 'request-001');
  assert.equal(store.state.enroll.retryAllowed, true);
});

test('超时后的个人记录查询确认成功，不重发提交', async () => {
  let posts = 0;
  const { store } = fixture({
    enroll: async () => { posts++; throw Object.assign(new Error('连接中断'), { uncertain: true }); },
    myEnrollments: async () => [{ id: 7, teachingClassId: 1, status: 'ACTIVE' }],
  });
  await store.enroll(1);
  await store.confirmEnrollment();
  assert.equal(store.state.enroll.status, 'confirmed');
  assert.equal(posts, 1);
});

test('用户明确重试前先查记录，重试使用原来的幂等键', async () => {
  const calls = [];
  let posts = 0;
  const { store } = fixture({
    enroll: async (_, key) => { calls.push(['POST', key]); if (++posts === 1) throw Object.assign(new Error('超时'), { uncertain: true }); return { id: 7 }; },
    myEnrollments: async () => { calls.push(['GET']); return []; },
  });
  await store.enroll(1);
  await store.retryEnrollment();
  assert.deepEqual(calls.slice(0, 3), [['POST', 'request-001'], ['GET'], ['POST', 'request-001']]);
  assert.equal(store.state.enroll.status, 'confirmed');
});

test('旧键回放历史成功但当前已退课时，页面不宣称当前选课成功', async () => {
  const { store } = fixture({
    enroll: async () => ({ id: 7, teachingClassId: 1, status: 'ACTIVE', score: null }),
    myEnrollments: async () => [],
  });
  await store.enroll(1);
  assert.equal(store.state.enroll.status, 'confirmed');
  assert.match(store.state.enroll.message, /当前有效记录中未包含/);
  assert.doesNotMatch(store.state.enroll.message, /选课成功/);
});

test('查询确认本身失败时继续保留 uncertain，不允许再次 POST', async () => {
  let posts = 0;
  const { store } = fixture({
    enroll: async () => { posts++; throw Object.assign(new Error('超时'), { uncertain: true }); },
    myEnrollments: async () => { throw new Error('查询服务不可用'); },
  });
  await store.enroll(1);
  await store.retryEnrollment();
  assert.equal(store.state.enroll.status, 'uncertain');
  assert.equal(store.state.enroll.retryAllowed, false);
  assert.equal(posts, 1);
});

test('恢复未确认的提交后沿用原幂等键，禁止直接发起另一教学班提交', async () => {
  let posts = 0;
  const { store } = fixture({ enroll: async () => { posts++; } }, { pending: { teachingClassId: 3, key: 'before-reload' } });
  assert.equal(store.state.enroll.status, 'uncertain');
  assert.equal(store.state.enroll.key, 'before-reload');
  await store.enroll(4);
  assert.equal(posts, 0);
});

test('明确的业务拒绝回到 idle 并显示服务端错误', async () => {
  const { store, saved } = fixture({ enroll: async () => { throw Object.assign(new Error('教学班容量已满'), { status: 409, code: 'CLASS_FULL', uncertain: false }); } });
  await store.enroll(1);
  assert.equal(store.state.enroll.status, 'idle');
  assert.match(store.state.enroll.error, /容量已满/);
  assert.equal(saved.at(-1), null);
});

test('成绩越界不发送请求，合法成绩能提交', async () => {
  const values = [];
  const { store } = fixture({ score: async (_, value) => { values.push(value); return { score: value }; } });
  await store.saveScore(7, 101);
  await store.saveScore(7, '');
  await store.saveScore(7, '85.555');
  await store.saveScore(7, '0.001');
  assert.equal(values.length, 0);
  await store.saveScore(7, '85.5');
  assert.deepEqual(values, [85.5]);
  assert.equal(store.state.score.status, 'confirmed');
});

test('教师登录只加载本人教学班，不调用学生记录接口', async () => {
  let studentReads = 0;
  const { store } = fixture({
    me: async () => ({ username: 'teacher1', role: 'TEACHER', personId: 1, name: '李老师' }),
    myEnrollments: async () => { studentReads++; return []; },
  });
  assert.equal(await store.login('teacher1', 'Chapter10!'), true);
  assert.equal(store.state.me.role, 'TEACHER');
  assert.equal(store.state.classes.status, 'data');
  assert.equal(studentReads, 0);
});

test('认证失败不进入工作台并清除内存凭据', async () => {
  let cleared = false;
  const { store } = fixture({ me: async () => { throw new Error('账号或密码错误'); }, clearCredentials: () => { cleared = true; } });
  assert.equal(await store.login('student1', 'wrong'), false);
  assert.equal(store.state.me, null);
  assert.equal(store.state.login.status, 'error');
  assert.equal(cleared, true);
});

test('退出登录后迟到的查询不能恢复上个账号的数据', async () => {
  const response = deferred();
  const { store } = fixture({ classes: () => response.promise });
  const request = store.loadClasses();
  store.logout();
  response.resolve([{ id: 1 }]);
  await request;
  assert.equal(store.state.me, null);
  assert.equal(store.state.classes.status, 'idle');
  assert.deepEqual(store.state.classes.data, []);
});

test('身份成功但首屏查询期间退出，不得返回登录成功并重新保存凭据', async () => {
  const response = deferred();
  const { store } = fixture({ classes: () => response.promise });
  const login = store.login('student1', 'Chapter10!');
  await Promise.resolve();
  assert.equal(store.state.me.username, 'student1');
  store.logout();
  response.resolve([]);
  assert.equal(await login, false);
  assert.equal(store.state.me, null);
});

test('退课超时后通过保留的历史记录确认结果', async () => {
  let writes = 0;
  const { store } = fixture({
    drop: async () => { writes++; throw Object.assign(new Error('超时'), { uncertain: true }); },
    myEnrollments: async () => [{ id: 7, teachingClassId: 1, status: 'WITHDRAWN' }],
  });
  await store.dropEnrollment(7);
  assert.equal(store.state.drop.status, 'uncertain');
  await store.dropEnrollment(7);
  assert.equal(writes, 1);
  await store.confirmDrop();
  assert.equal(store.state.drop.status, 'confirmed');
});

test('退课结果未确认时不能开始新的选课请求', async () => {
  let posts = 0;
  const { store } = fixture({
    drop: async () => { throw Object.assign(new Error('超时'), { uncertain: true }); },
    enroll: async () => { posts++; return { id: 8 }; },
  });
  await store.dropEnrollment(7);
  await store.enroll(2);
  assert.equal(posts, 0);
  assert.equal(store.state.drop.status, 'uncertain');
});

test('成绩保存超时后以本班名单中的值确认成功', async () => {
  const { store } = fixture({
    score: async () => { throw Object.assign(new Error('超时'), { uncertain: true }); },
    roster: async () => [{ id: 7, score: 85.5, status: 'ACTIVE' }],
  });
  await store.loadRoster(1);
  await store.saveScore(7, 85.5);
  assert.equal(store.state.score.status, 'uncertain');
  await store.confirmScore();
  assert.equal(store.state.score.status, 'confirmed');
});

test('教师快速切换教学班时仅显示最后选中班级的名单', async () => {
  const older = deferred();
  const { store } = fixture({ roster: (id) => id === 1 ? older.promise : Promise.resolve([{ id: 8, studentName: '新班学生' }]) });
  const first = store.loadRoster(1);
  await store.loadRoster(2);
  older.resolve([{ id: 7, studentName: '旧班学生' }]);
  await first;
  assert.equal(store.state.rosterClassId, 2);
  assert.equal(store.state.roster.data[0].id, 8);
});

