import test from 'node:test';
import assert from 'node:assert/strict';
import { createApiClient } from '../src/api.js';

test('请求发送 Basic 身份和 JSON；选课必须带原幂等键', async () => {
  const requests = [];
  const api = createApiClient({
    fetchImpl: async (url, init) => {
      requests.push({ url, init });
      return new Response(
        JSON.stringify({
          id: 7,
          teachingClassId: 1,
          courseName: '课程',
          status: 'ACTIVE',
          score: null,
        }),
        { status: 200, headers: { 'Content-Type': 'application/json' } },
      );
    },
  });
  api.setCredentials('student1', 'Chapter10!');
  await api.enroll(1, 'same-key');
  assert.equal(requests[0].url, '/api/enrollments');
  assert.equal(requests[0].init.method, 'POST');
  assert.equal(requests[0].init.headers.Authorization, `Basic ${btoa('student1:Chapter10!')}`);
  assert.equal(requests[0].init.headers['Idempotency-Key'], 'same-key');
  assert.equal(requests[0].init.body, '{"teachingClassId":1}');
});

test('明确业务错误保留 code/message/requestId，结果确定', async () => {
  const api = createApiClient({
    fetchImpl: async () =>
      new Response(
        JSON.stringify({
          code: 'CLASS_FULL',
          message: '教学班已满',
          requestId: 'trace-1',
          fields: {},
        }),
        { status: 409 },
      ),
  });
  await assert.rejects(api.enroll(1, 'key'), (error) => {
    assert.equal(error.status, 409);
    assert.equal(error.code, 'CLASS_FULL');
    assert.equal(error.message, '教学班已满');
    assert.equal(error.requestId, 'trace-1');
    assert.equal(error.uncertain, false);
    return true;
  });
});

test('网络断开和 5xx 的写请求均标记结果不确定', async () => {
  for (const fetchImpl of [
    async () => {
      throw new TypeError('fetch failed');
    },
    async () => new Response('temporary unavailable', { status: 503 }),
  ]) {
    const api = createApiClient({ fetchImpl });
    await assert.rejects(api.enroll(1, 'key'), (error) => error.uncertain === true);
  }
});

test('写请求计时器中止后返回 TIMEOUT 和 uncertain', async () => {
  const api = createApiClient({
    timeoutMs: 5,
    fetchImpl: (_, { signal }) =>
      new Promise((_, reject) => {
        signal.addEventListener('abort', () => reject(new DOMException('Aborted', 'AbortError')));
      }),
  });
  await assert.rejects(
    api.enroll(1, 'key'),
    (error) => error.code === 'TIMEOUT' && error.uncertain === true,
  );
});

test('GET 超时只是查询错误，不报告写入结果不确定', async () => {
  const api = createApiClient({
    timeoutMs: 5,
    fetchImpl: (_, { signal }) =>
      new Promise((_, reject) => {
        signal.addEventListener('abort', () => reject(new DOMException('Aborted', 'AbortError')));
      }),
  });
  await assert.rejects(
    api.classes(),
    (error) => error.code === 'TIMEOUT' && error.uncertain === false,
  );
});

test('响应头已到达但响应体读取超时仍报告 TIMEOUT', async () => {
  const api = createApiClient({
    timeoutMs: 5,
    fetchImpl: async (_, { signal }) => ({
      status: 200,
      ok: true,
      json: () =>
        new Promise((_, reject) => {
          signal.addEventListener('abort', () => reject(new DOMException('Aborted', 'AbortError')));
        }),
    }),
  });
  await assert.rejects(
    api.enroll(1, 'key'),
    (error) => error.code === 'TIMEOUT' && error.uncertain === true,
  );
});

test('204 退课正确返回，退出后清除身份请求头', async () => {
  let authorization;
  const api = createApiClient({
    fetchImpl: async (_, init) => {
      authorization = init.headers.Authorization;
      return new Response(null, { status: 204 });
    },
  });
  api.setCredentials('student1', 'Chapter10!');
  assert.equal(await api.drop(7), undefined);
  assert.equal(authorization, `Basic ${btoa('student1:Chapter10!')}`);
  api.clearCredentials();
  await api.drop(7);
  assert.equal(authorization, undefined);
});

test('接口路径和成绩字段遵守约定', async () => {
  const requests = [];
  const api = createApiClient({
    fetchImpl: async (url, init) => {
      requests.push([url, init.method, init.body]);
      return new Response('[]', { status: 200 });
    },
  });
  await api.me();
  await api.classes();
  await api.myEnrollments();
  await api.roster(3);
  await api.score(7, 85);
  assert.deepEqual(requests, [
    ['/api/me', 'GET', undefined],
    ['/api/teaching-classes', 'GET', undefined],
    ['/api/enrollments/me', 'GET', undefined],
    ['/api/teaching-classes/3/roster', 'GET', undefined],
    ['/api/enrollments/7/score', 'PUT', '{"score":85}'],
  ]);
});

test('缺少统一错误字段的400仍是未确认写请求', async () => {
  const api = createApiClient({
    fetchImpl: async () =>
      new Response(JSON.stringify({ code: 'BAD', message: 'bad' }), { status: 400 }),
  });
  await assert.rejects(
    api.enroll(1, 'original'),
    (error) => error.code === 'INVALID_RESPONSE' && error.uncertain,
  );
});
test('成功响应包含不安全主键时不能显示已确认', async () => {
  const api = createApiClient({
    fetchImpl: async () =>
      new Response(
        JSON.stringify({
          id: 9007199254740992,
          teachingClassId: 1,
          status: 'ACTIVE',
          courseName: '课程',
        }),
        { status: 200 },
      ),
  });
  await assert.rejects(api.enroll(1, 'original'), (error) => error.uncertain);
});
test('原请求结果查询保持原键，不产生新写入', async () => {
  let path;
  const api = createApiClient({
    fetchImpl: async (url) => {
      path = url;
      return new Response(
        JSON.stringify({ id: 7, teachingClassId: 1, status: 'ACTIVE', courseName: '课程' }),
        { status: 200 },
      );
    },
  });
  const result = await api.requestStatus('original-key');
  assert.equal(path, '/api/enrollment-requests/original-key');
  assert.equal(result.id, 7);
});

test('原请求快照必须具有安全主键与有效目标，异常快照不能确认成功', async () => {
  for (const changed of [
    { id: '7' },
    { id: 9007199254740992 },
    { teachingClassId: 0 },
    { courseName: '' },
    { status: 'WITHDRAWN' },
  ]) {
    const api = createApiClient({
      fetchImpl: async () =>
        new Response(
          JSON.stringify({
            id: 7,
            teachingClassId: 1,
            status: 'ACTIVE',
            courseName: '课程',
            ...changed,
          }),
          { status: 200 },
        ),
    });
    await assert.rejects(
      api.requestStatus('original-key'),
      (error) => error.code === 'INVALID_RESPONSE',
    );
  }
});
