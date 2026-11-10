// 习题9-8至9-10。函数使用注入的查询方法，不访问真实服务。
export function validateBody(body) {
  if (body === null || typeof body !== 'object' || Array.isArray(body)) throw new TypeError('body');
  const prototype = Object.getPrototypeOf(body);
  if (prototype !== Object.prototype && prototype !== null) throw new TypeError('body');
  const keys = Object.keys(body);
  if (
    keys.length !== 1 ||
    keys[0] !== 'teachingClassId' ||
    !Number.isSafeInteger(body.teachingClassId) ||
    body.teachingClassId <= 0
  )
    throw new TypeError('teachingClassId');
  return body.teachingClassId;
}
export class LatestQuery {
  constructor() {
    this.sequence = 0;
    this.data = [];
    this.error = null;
    this.loading = false;
  }
  async load(fetcher) {
    const token = ++this.sequence;
    this.loading = true;
    this.error = null;
    try {
      const data = await fetcher();
      if (token === this.sequence) this.data = data;
    } catch (error) {
      if (token === this.sequence) this.error = error;
    } finally {
      if (token === this.sequence) this.loading = false;
    }
  }
  logout() {
    this.sequence++;
    this.data = [];
    this.error = null;
    this.loading = false;
  }
}
function validSnapshot(body, expectedClass) {
  return (
    body !== null &&
    typeof body === 'object' &&
    Number.isSafeInteger(body.id) &&
    body.id > 0 &&
    body.teachingClassId === expectedClass
  );
}
function validTimetable(data) {
  return (
    Array.isArray(data) &&
    data.every(
      (row) =>
        row !== null && typeof row === 'object' && Number.isSafeInteger(row.id) && row.id > 0,
    )
  );
}
export async function recoverSelection(pending, queryOriginal, readTimetable) {
  const expectedClass = validateBody(pending.body);
  const waiting = { status: 'PENDING', pending };
  let response;
  try {
    response = await queryOriginal(pending.key);
  } catch (error) {
    return { ...waiting, error };
  }
  if (response?.status !== 200 || !validSnapshot(response.body, expectedClass)) return waiting;
  const snapshot = response.body;
  try {
    const data = await readTimetable();
    if (!validTimetable(data)) throw new TypeError('timetable');
    return { status: 'CONFIRMED', snapshot, data };
  } catch (error) {
    // 写入已经确认成功，当前课表仍需重读，不能重新发送新选课。
    return { status: 'CONFIRMED_READ_FAILED', snapshot, error };
  }
}
export async function recoverWithdrawal(id, readTimetable) {
  if (!Number.isSafeInteger(id) || id <= 0) throw new TypeError('id');
  try {
    const data = await readTimetable();
    if (!validTimetable(data)) throw new TypeError('timetable');
    return data.some((row) => row.id === id)
      ? { status: 'RETRY_SAME_DELETE', id }
      : { status: 'CONFIRMED', id };
  } catch (error) {
    return { status: 'PENDING', id, error };
  }
}
