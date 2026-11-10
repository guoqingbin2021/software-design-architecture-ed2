import { computed, ref } from 'vue';
import { enrollmentApi, assertEnrollmentDto } from '../api/enrollment.js';

const blockedWithdrawals = new Set(['confirming', 'withdrawing', 'uncertain']);
const knownRejection = (error) =>
  error?.kind === 'http' && [400, 403, 404, 409].includes(error.status);
const errorText = (error) =>
  `${error?.message ?? '请求失败'}${error?.requestId ? `（请求编号：${error.requestId}）` : ''}`;

function browserStorage() {
  try {
    return globalThis.sessionStorage;
  } catch {
    return null;
  }
}

/** Page state owns the three tasks; API modules own HTTP and components own presentation. */
export function useEnrollmentPage(
  initialSemester = '2026秋',
  initialClassId = '501',
  {
    api = enrollmentApi,
    storage = browserStorage(),
    createKey = () => crypto.randomUUID(),
    delay = (ms) => new Promise((resolve) => setTimeout(resolve, ms)),
    pollDelays = [300, 600, 1200],
    onAuthRequired = () => {},
  } = {},
) {
  const identity = ref(null);
  const authRequired = ref(false);
  const semester = ref(initialSemester);
  const teachingClassId = ref(initialClassId);
  const items = ref([]);
  const queryState = ref('loading');
  const queryError = ref('');
  const submitState = ref('idle');
  const withdrawState = ref('idle');
  const message = ref('');
  const pendingPost = ref(null);
  const pendingWithdrawal = ref(null);
  const confirmationBusy = ref(false);
  let readSequence = 0;
  let identityGeneration = 0;
  const storageError = ref(false);
  const isStudent = computed(() => identity.value?.role === 'STUDENT');
  const writeBusy = computed(
    () =>
      ['submitting', 'uncertain'].includes(submitState.value) ||
      blockedWithdrawals.has(withdrawState.value),
  );
  const canEnroll = computed(
    () =>
      isStudent.value &&
      !authRequired.value &&
      !storageError.value &&
      !writeBusy.value &&
      queryState.value === 'data' &&
      submitState.value === 'idle' &&
      /^[1-9]\d{0,18}$/.test(teachingClassId.value) &&
      Number.isSafeInteger(Number(teachingClassId.value)),
  );
  const canCheckResult = computed(
    () =>
      isStudent.value &&
      !authRequired.value &&
      submitState.value === 'uncertain' &&
      !confirmationBusy.value,
  );
  const canWithdraw = computed(
    () =>
      isStudent.value &&
      !authRequired.value &&
      !storageError.value &&
      queryState.value === 'data' &&
      !writeBusy.value,
  );

  const storageKey = () => `chapter9:pending:${identity.value?.username}`;
  function persist() {
    try {
      if (!identity.value || !storage) return;
      if (
        !pendingPost.value &&
        withdrawState.value !== 'uncertain' &&
        withdrawState.value !== 'withdrawing'
      ) {
        storage.removeItem(storageKey());
      } else {
        storage.setItem(
          storageKey(),
          JSON.stringify({
            post: pendingPost.value,
            withdrawal: ['uncertain', 'withdrawing'].includes(withdrawState.value)
              ? pendingWithdrawal.value
              : null,
          }),
        );
      }
    } catch {
      // Keep the live uncertainty state even if the browser refuses persistence.
      message.value += ' 本页无法保存待确认记录，请在完成核对前保持页面打开。';
    }
  }
  function requireAuth(error) {
    if (error?.status !== 401) return;
    authRequired.value = true;
    onAuthRequired(identity.value?.username);
  }
  function syncSelectedClass() {
    if (!['submitting', 'uncertain'].includes(submitState.value)) {
      submitState.value = items.value.some(
        (item) => item.teachingClassId === Number(teachingClassId.value),
      )
        ? 'confirmed'
        : 'idle';
    }
  }
  function setIdentity(value) {
    authRequired.value = false;
    if (value?.username === identity.value?.username) {
      identity.value = value;
      return;
    }
    ++identityGeneration;
    ++readSequence;
    identity.value = value;
    items.value = [];
    queryState.value = 'loading';
    queryError.value = '';
    submitState.value = 'idle';
    withdrawState.value = 'idle';
    pendingPost.value = null;
    pendingWithdrawal.value = null;
    confirmationBusy.value = false;
    message.value = '';
    storageError.value = false;
    if (!value || !storage) return;
    try {
      const savedText = storage.getItem(storageKey());
      if (!savedText) return;
      const saved = JSON.parse(savedText);
      if (saved.post) {
        if (
          typeof saved.post.key !== 'string' ||
          !saved.post.body ||
          !Number.isSafeInteger(saved.post.body.teachingClassId)
        )
          throw new Error('Invalid saved request');
        pendingPost.value = saved.post;
        pendingPost.value.wasUncertain = true;
        teachingClassId.value = String(saved.post.body.teachingClassId);
        submitState.value = 'uncertain';
        message.value = '已恢复待确认的选课请求。请先查询原请求结果。';
      }
      if (saved.withdrawal) {
        if (!Number.isSafeInteger(saved.withdrawal.id)) throw new Error('Invalid saved withdrawal');
        pendingWithdrawal.value = saved.withdrawal;
        withdrawState.value = 'uncertain';
        message.value = '已恢复结果未明的退课记录。请查询个人课表确认结果。';
      }
    } catch {
      storageError.value = true;
      message.value = '待确认记录无法读取，已暂停写操作。请联系教师核对此前请求后处理本页暂存。';
    }
  }

  async function loadClasses(semesterSnapshot = semester.value) {
    if (!isStudent.value || authRequired.value) return false;
    const sequence = ++readSequence;
    semester.value = semesterSnapshot;
    queryState.value = 'loading';
    queryError.value = '';
    try {
      const result = await api.listTimetable(semesterSnapshot);
      if (sequence !== readSequence) return false;
      items.value = result;
      queryState.value = 'data';
      syncSelectedClass();
      return true;
    } catch (error) {
      if (sequence !== readSequence) return false;
      queryState.value = 'error';
      queryError.value = errorText(error);
      requireAuth(error);
      return false;
    }
  }
  function selectTeachingClass(value) {
    if (writeBusy.value || confirmationBusy.value) return false;
    teachingClassId.value = value.trim();
    syncSelectedClass();
    return true;
  }
  async function acceptPost(dto, pending, generation) {
    assertEnrollmentDto(dto, pending.body.teachingClassId);
    if (generation !== identityGeneration) return;
    pendingPost.value = null;
    submitState.value = 'confirmed';
    message.value = `选课请求已确认：${dto.courseName}。当前状态以课程表为准。`;
    persist();
    const refreshed = await loadClasses(semester.value);
    if (generation === identityGeneration && !refreshed) {
      message.value = '选课请求已确认，课程表刷新失败。请重新读取课程表。';
    }
  }
  async function sendPost(pending, isRetry) {
    const generation = identityGeneration;
    submitState.value = 'submitting';
    message.value = isRetry ? '正在使用原请求键和原请求内容重试…' : '正在提交选课…';
    persist();
    try {
      const dto = await api.enroll({ ...pending.body }, pending.key);
      await acceptPost(dto, pending, generation);
    } catch (error) {
      if (generation !== identityGeneration) return;
      requireAuth(error);
      if (knownRejection(error) && !pending.wasUncertain) {
        pendingPost.value = null;
        submitState.value = 'idle';
        message.value = `选课未完成：${errorText(error)}`;
        persist();
        return;
      }
      pending.wasUncertain = true;
      pending.requestId = error.requestId ?? pending.requestId;
      pendingPost.value = pending;
      submitState.value = 'uncertain';
      message.value = `选课结果尚未确定：${errorText(error)}。请核对原请求，勿另建请求键提交。`;
      persist();
      if (!authRequired.value) await pollResult();
    }
  }
  async function enroll() {
    if (!canEnroll.value) return false;
    const pending = {
      key: createKey(),
      body: { teachingClassId: Number(teachingClassId.value) },
      submittedAt: new Date().toISOString(),
      wasUncertain: false,
    };
    pendingPost.value = pending;
    await sendPost(pending, false);
    return true;
  }
  async function checkOnce(pending, generation) {
    try {
      const dto = await api.requestStatus(pending.key, pending.body.teachingClassId);
      await acceptPost(dto, pending, generation);
      return true;
    } catch (error) {
      if (generation !== identityGeneration) return false;
      requireAuth(error);
      const detail =
        error.kind === 'http' && error.status === 404
          ? '尚未查到原请求的完成结果，404 不能证明选课失败。'
          : `暂时无法确认原请求：${errorText(error)}。`;
      message.value = `${detail}保留原请求键与内容，继续确认或使用原键重试。`;
      submitState.value = 'uncertain';
      return false;
    }
  }
  async function confirmResult() {
    if (!canCheckResult.value || !pendingPost.value) return false;
    confirmationBusy.value = true;
    const generation = identityGeneration;
    try {
      return await checkOnce(pendingPost.value, generation);
    } finally {
      if (generation === identityGeneration) confirmationBusy.value = false;
    }
  }
  async function pollResult() {
    if (!canCheckResult.value || !pendingPost.value) return;
    confirmationBusy.value = true;
    const generation = identityGeneration;
    const pending = pendingPost.value;
    try {
      for (const ms of pollDelays) {
        await delay(ms);
        if (
          generation !== identityGeneration ||
          submitState.value !== 'uncertain' ||
          authRequired.value
        )
          break;
        if (await checkOnce(pending, generation)) break;
      }
    } finally {
      if (generation === identityGeneration) confirmationBusy.value = false;
    }
  }
  async function retryOriginal() {
    if (!canCheckResult.value || !pendingPost.value) return false;
    await sendPost(pendingPost.value, true);
    return true;
  }
  function askWithdraw(item) {
    if (!canWithdraw.value || !items.value.some((row) => row.id === item.id)) return false;
    pendingWithdrawal.value = { ...item };
    withdrawState.value = 'confirming';
    message.value = '';
    return true;
  }
  function cancelWithdraw() {
    if (withdrawState.value !== 'confirming') return false;
    pendingWithdrawal.value = null;
    withdrawState.value = 'idle';
    return true;
  }
  async function withdraw() {
    if (
      !isStudent.value ||
      authRequired.value ||
      withdrawState.value !== 'confirming' ||
      !pendingWithdrawal.value
    )
      return false;
    const target = pendingWithdrawal.value;
    const generation = identityGeneration;
    target.submittedAt = new Date().toISOString();
    withdrawState.value = 'withdrawing';
    message.value = '正在退课…';
    persist();
    try {
      await api.withdraw(target.id);
    } catch (error) {
      if (generation !== identityGeneration) return false;
      requireAuth(error);
      if (knownRejection(error)) {
        withdrawState.value = 'confirming';
        message.value = `退课未完成：${errorText(error)}`;
      } else {
        withdrawState.value = 'uncertain';
        target.requestId = error.requestId ?? target.requestId;
        message.value =
          '退课结果尚未确定。请保留选课记录标识、提交时间及请求编号，查询个人课表，记录仍有效时可重试同一记录的退课。';
      }
      persist();
      return false;
    }
    if (generation !== identityGeneration) return false;
    withdrawState.value = 'confirmed';
    items.value = items.value.filter((item) => item.id !== target.id);
    syncSelectedClass();
    message.value = '退课已完成。';
    persist();
    // The DELETE result stays confirmed even when this independent GET fails.
    const refreshed = await loadClasses(semester.value);
    if (generation === identityGeneration && !refreshed)
      message.value = '退课已完成，课程表刷新失败。请重新读取课程表。';
    return true;
  }
  async function confirmWithdrawal() {
    if (
      !isStudent.value ||
      authRequired.value ||
      withdrawState.value !== 'uncertain' ||
      confirmationBusy.value
    )
      return false;
    const generation = identityGeneration;
    const target = pendingWithdrawal.value;
    confirmationBusy.value = true;
    try {
      if (!(await loadClasses(semester.value)) || generation !== identityGeneration) return false;
      if (items.value.some((row) => row.id === target.id)) {
        withdrawState.value = 'confirming';
        message.value = '该记录仍在有效课表中，可对同一记录再次确认退课。';
      } else {
        withdrawState.value = 'confirmed';
        pendingWithdrawal.value = null;
        message.value = '已通过个人课表确认该记录不再有效，退课已完成。';
      }
      persist();
      return true;
    } finally {
      if (generation === identityGeneration) confirmationBusy.value = false;
    }
  }
  return {
    identity,
    authRequired,
    semester,
    teachingClassId,
    items,
    queryState,
    queryError,
    submitState,
    withdrawState,
    message,
    pendingPost,
    pendingWithdrawal,
    confirmationBusy,
    isStudent,
    writeBusy,
    canEnroll,
    canWithdraw,
    canCheckResult,
    setIdentity,
    loadClasses,
    selectTeachingClass,
    enroll,
    confirmResult,
    retryOriginal,
    askWithdraw,
    cancelWithdraw,
    withdraw,
    confirmWithdrawal,
  };
}
