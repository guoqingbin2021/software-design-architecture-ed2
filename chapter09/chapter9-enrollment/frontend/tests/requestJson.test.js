import test from 'node:test';
import assert from 'node:assert/strict';
import { createHttpClient } from '../src/api/requestJson.js';
import { createEnrollmentApi } from '../src/api/enrollment.js';
import { createAuthApi } from '../src/api/auth.js';

const json = (body, status = 200, requestId = 'server-1') =>
  new Response(JSON.stringify(body), {
    status,
    headers: {
      'Content-Type': 'application/json',
      ...(requestId ? { 'X-Request-Id': requestId } : {}),
    },
  });
const errorBody = (code = 'CLASS_FULL') => ({
  code,
  message: '没有可选名额',
  fields: {},
  requestId: 'server-1',
});
const dto = { id: 1001, teachingClassId: 501, status: 'ACTIVE', courseName: '软件设计与体系结构' };

test('204 never reads a response body', async () => {
  const http = createHttpClient({
    fetchImpl: async () => ({
      status: 204,
      ok: true,
      headers: new Headers(),
      text: () => {
        throw new Error('204 body must not be read');
      },
    }),
  });
  assert.equal(
    await http.requestJson('/api/enrollments/1001', { method: 'DELETE', expectedStatus: 204 }),
    null,
  );
});

test('unexpected 204 on a POST is a protocol error', async () => {
  const http = createHttpClient({ fetchImpl: async () => new Response(null, { status: 204 }) });
  await assert.rejects(http.requestJson('/api/enrollments'), { kind: 'protocol', status: 204 });
});

test('valid business error retains fields and matching requestId', async () => {
  const http = createHttpClient({
    fetchImpl: async () => json({ ...errorBody(), fields: { teachingClassId: '已满' } }, 409),
  });
  await assert.rejects(http.requestJson('/api/enrollments'), (error) => {
    assert.equal(error.kind, 'http');
    assert.equal(error.code, 'CLASS_FULL');
    assert.equal(error.requestId, 'server-1');
    assert.deepEqual(error.fields, { teachingClassId: '已满' });
    return true;
  });
});

for (const [name, response] of [
  ['400 HTML', () => new Response('<h1>bad proxy</h1>', { status: 400 })],
  ['400 missing fields', () => json({ code: 'INVALID_ARGUMENT', message: 'invalid' }, 400)],
  [
    '400 mismatched request identifiers',
    () => json({ ...errorBody(), requestId: 'different' }, 400),
  ],
]) {
  test(`${name} is protocol, never a definite business refusal`, async () => {
    const http = createHttpClient({ fetchImpl: async () => response() });
    await assert.rejects(http.requestJson('/test'), { kind: 'protocol', status: 400 });
  });
}

test('missing response requestId falls back to the error body', async () => {
  const http = createHttpClient({ fetchImpl: async () => json(errorBody(), 409, null) });
  await assert.rejects(http.requestJson('/test'), { kind: 'http', requestId: 'server-1' });
});

test('network timeout without response headers does not invent a server requestId', async () => {
  const http = createHttpClient({
    timeoutMs: 5,
    fetchImpl: async (_path, options) =>
      new Promise((_resolve, reject) => {
        options.signal.addEventListener('abort', () => reject(new Error('aborted')), {
          once: true,
        });
      }),
  });
  await assert.rejects(http.requestJson('/test'), { kind: 'network', status: 0, requestId: null });
});

test('same business key across retries, distinct server requestIds, explicit Basic credentials without cookies', async () => {
  const sent = [];
  let sequence = 0;
  const client = createHttpClient({
    fetchImpl: async (path, options) => {
      sent.push({ path, options });
      const requestId = `server-http-${++sequence}`;
      return json({ ...errorBody('INTERNAL_ERROR'), requestId }, 500, requestId);
    },
  });
  client.setCredentials('student101', 'demo');
  const api = createEnrollmentApi(client);
  await assert.rejects(api.enroll({ teachingClassId: 501 }, 'original-key'), {
    requestId: 'server-http-1',
  });
  await assert.rejects(api.enroll({ teachingClassId: 501 }, 'original-key'), {
    requestId: 'server-http-2',
  });
  for (const { path, options } of sent) {
    assert.equal(path, '/api/enrollments');
    assert.equal(options.credentials, 'omit');
    assert.equal(options.headers.get('Authorization'), `Basic ${btoa('student101:demo')}`);
    assert.equal(options.headers.has('X-CSRF-TOKEN'), false);
    assert.equal(options.headers.get('Idempotency-Key'), 'original-key');
    assert.equal(options.headers.has('X-Request-Id'), false);
    assert.deepEqual(JSON.parse(options.body), { teachingClassId: 501 });
  }
});

for (const [name, data] of [
  ['other teaching class', { ...dto, teachingClassId: 502 }],
  ['wrong state', { ...dto, status: 'WITHDRAWN' }],
  ['string identifier', { ...dto, id: '1001' }],
  ['missing course name', { ...dto, courseName: '' }],
]) {
  test(`200 with ${name} is not successful enrollment`, async () => {
    const api = createEnrollmentApi(createHttpClient({ fetchImpl: async () => json(data) }));
    await assert.rejects(api.enroll({ teachingClassId: 501 }, 'key'), {
      kind: 'protocol',
      status: 200,
    });
  });
}

test('timetable requires all seven fields, accepts nulls and rejects duplicate relationship identifiers', async () => {
  const item = {
    id: 1001,
    teachingClassId: 501,
    courseName: '课程',
    teacherName: null,
    room: null,
    week: null,
    time: null,
    status: 'ACTIVE',
    score: null,
  };
  let body = [item];
  const api = createEnrollmentApi(createHttpClient({ fetchImpl: async () => json(body) }));
  assert.deepEqual(await api.listTimetable('2026秋'), [item]);
  body = [{ ...item, room: undefined }];
  await assert.rejects(api.listTimetable('2026秋'), { kind: 'protocol' });
  body = [item, item];
  await assert.rejects(api.listTimetable('2026秋'), { kind: 'protocol' });
});

test('login uses per-request Basic and logout clears in-memory credentials', async () => {
  const sent = [];
  const client = createHttpClient({
    fetchImpl: async (path, options) => {
      sent.push({ path, options });
      return json({ username: 'student101', role: 'STUDENT', personId: 101, name: '张敏' });
    },
  });
  const auth = createAuthApi(client);
  assert.equal((await auth.login('student101', 'Chapter9!Demo')).username, 'student101');
  assert.equal(sent[0].path, '/api/me');
  assert.equal(
    sent[0].options.headers.get('Authorization'),
    `Basic ${btoa('student101:Chapter9!Demo')}`,
  );
  await auth.logout();
  await auth.me();
  assert.equal(sent[1].options.headers.has('Authorization'), false);
});
