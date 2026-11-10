export function timeLabel(row) {
  const days = ['周一', '周二', '周三', '周四', '周五', '周六', '周日'];
  const day = days[Number(row.dayOfWeek) - 1];
  return day && row.startPeriod != null && row.endPeriod != null
    ? `${day} 第${row.startPeriod}–${row.endPeriod}节`
    : '时间待安排';
}

export const statusLabel = (status) =>
  ({ ACTIVE: '已选', WITHDRAWN: '已退课', OPEN: '开放选课', CLOSED: '已关闭' })[status] ||
  status ||
  '—';

export const timetableRows = (rows) =>
  rows
    .filter((row) => row.status === 'ACTIVE')
    .slice()
    .sort(
      (a, b) =>
        Number(a.dayOfWeek) - Number(b.dayOfWeek) || Number(a.startPeriod) - Number(b.startPeriod),
    );

export const canWithdraw = (row) =>
  row.status === 'ACTIVE' && row.classStatus === 'OPEN' && row.score == null;
export const canRecordScore = (teachingClass, row) =>
  teachingClass?.status === 'CLOSED' && row.status === 'ACTIVE';
