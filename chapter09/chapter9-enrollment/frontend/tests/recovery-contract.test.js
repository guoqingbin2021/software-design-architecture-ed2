import test from 'node:test';
import assert from 'node:assert/strict';
import { useEnrollmentPage } from '../src/composables/useEnrollmentPage.js';
const item = {
  id: 1001,
  teachingClassId: 501,
  status: 'ACTIVE',
  courseName: '软件设计与体系结构',
  teacherName: null,
  room: null,
  week: null,
  time: null,
};
test('lost DELETE response can be confirmed by the student without an administrator', async () => {
  let rows = [item];
  const page = useEnrollmentPage('2026秋', '501', {
    storage: null,
    api: {
      listTimetable: async () => rows,
      withdraw: async () => {
        rows = [];
        throw Object.assign(new Error('lost response'), { kind: 'network' });
      },
    },
  });
  page.setIdentity({ username: 'student101', role: 'STUDENT' });
  await page.loadClasses();
  page.askWithdraw(item);
  await page.withdraw();
  assert.equal(page.withdrawState.value, 'uncertain');
  assert.equal(typeof page.confirmWithdrawal, 'function');
  await page.confirmWithdrawal();
  assert.equal(page.withdrawState.value, 'confirmed');
  assert.equal(page.writeBusy.value, false);
});

test('an active record restores confirmation and repeated DELETE targets the original id', async () => {
  let calls = 0,
    rows = [item];
  const page = useEnrollmentPage('2026秋', '501', {
    storage: null,
    api: {
      listTimetable: async () => rows,
      withdraw: async (id) => {
        assert.equal(id, 1001);
        if (++calls === 1) throw Object.assign(new Error('lost'), { kind: 'network' });
        rows = [];
      },
    },
  });
  page.setIdentity({ username: 'student101', role: 'STUDENT' });
  await page.loadClasses();
  page.askWithdraw(item);
  await page.withdraw();
  await page.confirmWithdrawal();
  assert.equal(page.withdrawState.value, 'confirming');
  assert.equal(calls, 1);
  await page.withdraw();
  assert.equal(calls, 2);
  assert.equal(page.withdrawState.value, 'confirmed');
});
test('failed confirmation keeps uncertain state and all writes blocked', async () => {
  let reads = 0;
  const page = useEnrollmentPage('2026秋', '501', {
    storage: null,
    api: {
      listTimetable: async () => {
        if (++reads === 1) return [item];
        throw new Error('offline');
      },
      withdraw: async () => {
        throw Object.assign(new Error('lost'), { kind: 'network' });
      },
    },
  });
  page.setIdentity({ username: 'student101', role: 'STUDENT' });
  await page.loadClasses();
  page.askWithdraw(item);
  await page.withdraw();
  await page.confirmWithdrawal();
  assert.equal(page.withdrawState.value, 'uncertain');
  assert.equal(page.writeBusy.value, true);
});
