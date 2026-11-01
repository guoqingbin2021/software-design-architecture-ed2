import test from 'node:test';
import assert from 'node:assert/strict';
import { timeLabel, statusLabel, timetableRows, canWithdraw, canRecordScore } from '../src/presenters.js';

test('教学班时间按星期和节次展示，缺少数据不生成错误时间', () => {
  assert.equal(timeLabel({ dayOfWeek: 2, startPeriod: 3, endPeriod: 4 }), '周二 第3–4节');
  assert.equal(timeLabel({}), '时间待安排');
});

test('状态翻译保留未知服务端状态以便诊断', () => {
  assert.equal(statusLabel('ACTIVE'), '已选');
  assert.equal(statusLabel('WITHDRAWN'), '已退课');
  assert.equal(statusLabel('OPEN'), '开放选课');
  assert.equal(statusLabel('OTHER'), 'OTHER');
});

test('课表只包含有效选课，并按星期和起始节次排序', () => {
  const rows = [
    { id: 1, status: 'ACTIVE', dayOfWeek: 3, startPeriod: 1 },
    { id: 2, status: 'WITHDRAWN', dayOfWeek: 1, startPeriod: 1 },
    { id: 3, status: 'ACTIVE', dayOfWeek: 1, startPeriod: 5 },
    { id: 4, status: 'ACTIVE', dayOfWeek: 1, startPeriod: 1 },
  ];
  assert.deepEqual(timetableRows(rows).map((row) => row.id), [4, 3, 1]);
  assert.equal(rows[0].id, 1);
});

test('仅OPEN教学班的未评分ACTIVE选课可退，CLOSED和已有成绩不可退', () => {
  assert.equal(canWithdraw({ status: 'ACTIVE', classStatus: 'OPEN', score: null }), true);
  assert.equal(canWithdraw({ status: 'ACTIVE', classStatus: 'CLOSED', score: null }), false);
  assert.equal(canWithdraw({ status: 'ACTIVE', classStatus: 'OPEN', score: 0 }), false);
  assert.equal(canWithdraw({ status: 'WITHDRAWN', classStatus: 'OPEN', score: null }), false);
});

test('仅CLOSED本人教学班的ACTIVE学生记录可录成绩', () => {
  assert.equal(canRecordScore({ status: 'CLOSED' }, { status: 'ACTIVE' }), true);
  assert.equal(canRecordScore({ status: 'OPEN' }, { status: 'ACTIVE' }), false);
  assert.equal(canRecordScore({ status: 'CLOSED' }, { status: 'WITHDRAWN' }), false);
  assert.equal(canRecordScore(null, { status: 'ACTIVE' }), false);
});

