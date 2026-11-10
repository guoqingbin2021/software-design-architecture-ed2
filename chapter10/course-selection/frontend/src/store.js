const resource = () => ({ status: 'idle', data: [], error: null });
const mutation = () => ({ status: 'idle', error: null, message: '' });
const enrollmentMutation = () => ({
  ...mutation(),
  teachingClassId: null,
  key: null,
  checking: false,
  retryAllowed: false,
});
const active = (row) => row.status === 'ACTIVE';
const errorText = (error) =>
  `${error.message || '请求失败'}${error.requestId ? `（请求号：${error.requestId}）` : ''}`;

/** 页面状态与接口调用分开，使查询乱序、重复提交和结果确认可以独立测试。 */
export function createCourseStore({
  api,
  wrapState = (value) => value,
  makeKey = () => crypto.randomUUID(),
  savePending = () => {},
  loadPending = () => null,
  pending = null,
}) {
  const state = wrapState({
    me: null,
    login: { status: 'idle', error: null },
    classes: resource(),
    enrollments: resource(),
    roster: resource(),
    rosterClassId: null,
    enroll: enrollmentMutation(),
    drop: { ...mutation(), id: null, checking: false },
    score: { ...mutation(), id: null, value: null, checking: false },
  });
  let epoch = 0;
  const sequence = { classes: 0, enrollments: 0, roster: 0 };

  function restorePending(value) {
    if (value?.key && Number.isInteger(value.teachingClassId)) {
      Object.assign(state.enroll, {
        ...enrollmentMutation(),
        status: 'uncertain',
        teachingClassId: value.teachingClassId,
        key: value.key,
        message: '上次选课尚未确认，请先查询个人记录。',
      });
    }
  }
  restorePending(pending);

  async function read(name, request) {
    const serial = ++sequence[name];
    const startEpoch = epoch;
    const target = state[name];
    target.status = 'loading';
    target.error = null;
    try {
      const data = await request();
      if (serial !== sequence[name] || startEpoch !== epoch) return null;
      target.data = data;
      target.status = 'data';
      return data;
    } catch (error) {
      if (serial !== sequence[name] || startEpoch !== epoch) return null;
      target.status = 'error';
      target.error = errorText(error);
      return null;
    }
  }

  const loadClasses = () => read('classes', () => api.classes());
  const loadMyEnrollments = () => read('enrollments', () => api.myEnrollments());
  async function loadRoster(classId = state.rosterClassId) {
    if (classId == null) return null;
    state.rosterClassId = Number(classId);
    return read('roster', () => api.roster(classId));
  }

  async function login(username, password) {
    if (state.login.status === 'loading') return false;
    const startEpoch = ++epoch;
    state.login.status = 'loading';
    state.login.error = null;
    api.setCredentials(username, password);
    try {
      const me = await api.me();
      if (startEpoch !== epoch) return false;
      state.me = me;
      state.login.status = 'data';
      restorePending(loadPending(me.username));
      await Promise.all([
        loadClasses(),
        me.role === 'STUDENT' ? loadMyEnrollments() : Promise.resolve(),
      ]);
      return startEpoch === epoch;
    } catch (error) {
      if (startEpoch !== epoch) return false;
      api.clearCredentials();
      state.login.status = 'error';
      state.login.error = errorText(error);
      return false;
    }
  }

  function logout() {
    epoch++;
    api.clearCredentials();
    state.me = null;
    Object.assign(state.login, { status: 'idle', error: null });
    for (const name of ['classes', 'enrollments', 'roster']) Object.assign(state[name], resource());
    state.rosterClassId = null;
    Object.assign(state.enroll, enrollmentMutation());
    Object.assign(state.drop, { ...mutation(), id: null, checking: false });
    Object.assign(state.score, { ...mutation(), id: null, value: null, checking: false });
  }

  function confirmEnrollmentSuccess(message, username = state.me?.username) {
    state.enroll.status = 'confirmed';
    state.enroll.error = null;
    state.enroll.message = message;
    state.enroll.retryAllowed = false;
    savePending(null, username);
  }

  async function sendEnrollment() {
    const target = state.enroll;
    if (target.status === 'submitting') return;
    const startEpoch = epoch;
    const username = state.me?.username;
    const submittedKey = target.key;
    target.status = 'submitting';
    target.error = null;
    target.message = '正在提交选课，请稍候。';
    target.retryAllowed = false;
    savePending({ teachingClassId: target.teachingClassId, key: target.key }, username);
    try {
      await api.enroll(target.teachingClassId, target.key);
      if (startEpoch !== epoch) return;
      confirmEnrollmentSuccess('选课请求已处理，正在查询当前记录。', username);
      const [, rows] = await Promise.all([loadClasses(), loadMyEnrollments()]);
      if (startEpoch !== epoch || target.key !== submittedKey) return;
      // 旧请求返回历史成功快照，页面还要查询当前有效选课。
      target.message =
        rows == null
          ? '选课请求已处理，请刷新个人记录查看当前状态。'
          : rows.some((row) => row.teachingClassId === target.teachingClassId && active(row))
            ? '选课成功。'
            : '选课请求已处理，但当前有效记录中未包含此教学班；请核对当前选课。';
    } catch (error) {
      if (startEpoch !== epoch) return;
      target.error = errorText(error);
      if (error.uncertain !== false && (!error.status || error.status >= 500 || error.uncertain)) {
        target.status = 'uncertain';
        target.message =
          '服务端可能已完成选课。请先查询个人记录确认，再决定是否使用同一请求号重试。';
      } else {
        target.status = 'idle';
        target.message = '';
        savePending(null, username);
        await loadClasses();
      }
    }
  }

  async function enroll(teachingClassId) {
    if (
      state.enroll.status === 'submitting' ||
      ['submitting', 'uncertain'].includes(state.drop.status)
    )
      return;
    if (state.enroll.status === 'uncertain') {
      if (state.enroll.teachingClassId === teachingClassId) await confirmEnrollment();
      return;
    }
    Object.assign(state.enroll, { ...enrollmentMutation(), teachingClassId, key: makeKey() });
    await sendEnrollment();
  }

  async function confirmEnrollment() {
    const target = state.enroll;
    if (target.status !== 'uncertain' || target.checking) return false;
    target.checking = true;
    target.retryAllowed = false;
    const startEpoch = epoch;
    try {
      let remembered = false;
      try {
        const snapshot = await api.requestStatus(target.key);
        if (startEpoch !== epoch) return false;
        remembered =
          snapshot?.teachingClassId === target.teachingClassId && snapshot.status === 'ACTIVE';
      } catch (error) {
        if (startEpoch !== epoch) return false;
        if (error.status === 401) return false;
        // 404 means no completed snapshot is visible yet; current records may still confirm success.
      }
      const rows = await loadMyEnrollments();
      if (startEpoch !== epoch || rows == null) return false;
      if (remembered) {
        confirmEnrollmentSuccess('已确认原选课请求完成；当前状态以个人课表为准。');
        await loadClasses();
        return true;
      }
      if (rows.some((row) => row.teachingClassId === target.teachingClassId && active(row))) {
        confirmEnrollmentSuccess('已通过个人记录确认选课成功。');
        await loadClasses();
        return true;
      }
      target.error = null;
      target.retryAllowed = true;
      target.message = '当前记录尚未显示成功。你可以再次查询，或使用原请求号重试。';
      return false;
    } finally {
      if (startEpoch === epoch) target.checking = false;
    }
  }

  async function retryEnrollment() {
    if (state.enroll.status !== 'uncertain' || state.enroll.checking) return;
    const startEpoch = epoch;
    await confirmEnrollment();
    if (startEpoch !== epoch) return;
    if (state.enroll.status === 'uncertain' && state.enroll.retryAllowed) await sendEnrollment();
  }

  async function dropEnrollment(id) {
    if (
      ['submitting', 'uncertain'].includes(state.drop.status) ||
      ['submitting', 'uncertain'].includes(state.enroll.status)
    )
      return;
    Object.assign(state.drop, { ...mutation(), status: 'submitting', id, checking: false });
    const startEpoch = epoch;
    try {
      await api.drop(id);
      if (startEpoch !== epoch) return;
      Object.assign(state.drop, { status: 'confirmed', message: '退课成功，历史记录已保留。' });
      await Promise.all([loadClasses(), loadMyEnrollments()]);
    } catch (error) {
      if (startEpoch !== epoch) return;
      Object.assign(state.drop, {
        status: error.uncertain ? 'uncertain' : 'idle',
        error: errorText(error),
        message: error.uncertain ? '退课结果尚未确认，请查询个人记录。' : '',
      });
    }
  }

  async function confirmDrop() {
    if (state.drop.status !== 'uncertain' || state.drop.checking) return;
    const startEpoch = epoch;
    state.drop.checking = true;
    const rows = await loadMyEnrollments();
    if (startEpoch !== epoch) return;
    state.drop.checking = false;
    if (rows == null) return;
    const remains = rows.some((row) => row.id === state.drop.id && active(row));
    Object.assign(state.drop, {
      status: remains ? 'idle' : 'confirmed',
      error: null,
      message: remains ? '记录仍为已选，可再次执行退课。' : '已通过个人记录确认退课完成。',
    });
    await loadClasses();
  }

  async function saveScore(id, input) {
    if (['submitting', 'uncertain'].includes(state.score.status)) return;
    const value = Number(input);
    if (
      input == null ||
      String(input).trim() === '' ||
      !Number.isFinite(value) ||
      value < 0 ||
      value > 100 ||
      Math.round(value * 100) / 100 !== value
    ) {
      Object.assign(state.score, {
        status: 'idle',
        id,
        error: '请输入 0–100 之间且最多两位小数的成绩。',
        message: '',
      });
      return;
    }
    const startEpoch = epoch;
    Object.assign(state.score, { ...mutation(), status: 'submitting', id, value, checking: false });
    try {
      await api.score(id, value);
      if (startEpoch !== epoch) return;
      Object.assign(state.score, { status: 'confirmed', message: '成绩已保存。' });
      await loadRoster();
    } catch (error) {
      if (startEpoch !== epoch) return;
      Object.assign(state.score, {
        status: error.uncertain ? 'uncertain' : 'idle',
        error: errorText(error),
        message: error.uncertain ? '保存结果尚未确认，请查询本班名单。' : '',
      });
    }
  }

  async function confirmScore() {
    if (state.score.status !== 'uncertain' || state.score.checking) return;
    const startEpoch = epoch;
    state.score.checking = true;
    const rows = await loadRoster();
    if (startEpoch !== epoch) return;
    state.score.checking = false;
    if (rows == null) return;
    const row = rows.find((item) => item.id === state.score.id);
    const matched = row != null && row.score != null && Number(row.score) === state.score.value;
    Object.assign(state.score, {
      status: matched ? 'confirmed' : 'idle',
      error: null,
      message: matched
        ? '已通过本班名单确认成绩保存成功。'
        : '名单尚未显示这次成绩，可核对后再次保存。',
    });
  }

  return {
    state,
    login,
    logout,
    loadClasses,
    loadMyEnrollments,
    loadRoster,
    enroll,
    confirmEnrollment,
    retryEnrollment,
    dropEnrollment,
    confirmDrop,
    saveScore,
    confirmScore,
  };
}
