import { ApiError, http } from './requestJson.js';

const id = (value) => Number.isSafeInteger(value) && value > 0;
const nonempty = (value) => typeof value === 'string' && value.trim().length > 0;
const nullableString = (value) => value === null || typeof value === 'string';

export const isEnrollmentDto = (body, teachingClassId) =>
  Boolean(
    body &&
      id(body.id) &&
      id(body.teachingClassId) &&
      body.teachingClassId === Number(teachingClassId) &&
      body.status === 'ACTIVE' &&
      nonempty(body.courseName),
  );

export const isTimetable = (body) =>
  Array.isArray(body) &&
  body.every(
    (item) =>
      item &&
      id(item.id) &&
      id(item.teachingClassId) &&
      item.status === 'ACTIVE' &&
      nonempty(item.courseName) &&
      ['teacherName', 'room', 'week', 'time'].every(
        (key) => key in item && nullableString(item[key]),
      ),
  ) &&
  new Set(body.map((item) => item.id)).size === body.length;

export function assertEnrollmentDto(body, teachingClassId) {
  if (!isEnrollmentDto(body, teachingClassId)) {
    throw new ApiError('选课响应与原教学班或 ACTIVE 状态不一致', { kind: 'protocol', status: 200 });
  }
  return body;
}

export function createEnrollmentApi(client = http) {
  return {
    listTimetable: (semester) =>
      client.requestJson('/api/enrollments/me', {
        validate: isTimetable,
      }),
    enroll: (body, key) =>
      client.requestJson('/api/enrollments', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json', 'Idempotency-Key': key },
        body: JSON.stringify(body),
        validate: (value) => isEnrollmentDto(value, body.teachingClassId),
      }),
    withdraw: (id) =>
      client.requestJson(`/api/enrollments/${encodeURIComponent(id)}`, {
        method: 'DELETE',
        expectedStatus: 204,
      }),
    requestStatus: (key, teachingClassId) =>
      client.requestJson(`/api/enrollment-requests/${encodeURIComponent(key)}`, {
        validate: (value) => isEnrollmentDto(value, teachingClassId),
      }),
  };
}
export const enrollmentApi = createEnrollmentApi();
