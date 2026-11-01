<script setup>
import { computed, onMounted, reactive, ref, watch } from 'vue';
import { createApiClient } from './api.js';
import { createCourseStore } from './store.js';
import { statusLabel, timeLabel, timetableRows, canWithdraw, canRecordScore } from './presenters.js';
import ReadStatus from './ReadStatus.vue';
import MutationStatus from './MutationStatus.vue';

const AUTH_KEY = 'chapter10.credentials';
const PENDING_PREFIX = 'chapter10.pending.';
const storage = {
  read(key) { try { return JSON.parse(sessionStorage.getItem(key)); } catch { return null; } },
  write(key, value) { try { if (value == null) sessionStorage.removeItem(key); else sessionStorage.setItem(key, JSON.stringify(value)); } catch { /* Session storage can be disabled by browser policy. */ } },
};
const api = createApiClient();
const store = createCourseStore({
  api, wrapState: reactive,
  savePending: (value, username) => { if (username) storage.write(`${PENDING_PREFIX}${username}`, value); },
  loadPending: (username) => storage.read(`${PENDING_PREFIX}${username}`),
});
const { state } = store;
const username = ref('student1');
const password = ref('');
const currentTab = ref('classes');
const keyword = ref('');
const scoreInputs = reactive({});
const students = ['student1', 'student2', 'student3'];
const teachers = ['teacher1', 'teacher2'];
let loginAttempt = 0;
const isStudent = computed(() => state.me?.role === 'STUDENT');
const activeEnrollments = computed(() => timetableRows(state.enrollments.data));
const credits = computed(() => activeEnrollments.value.reduce((sum, row) => sum + Number(row.credits || 0), 0));
const filteredClasses = computed(() => {
  const query = keyword.value.trim().toLowerCase();
  return state.classes.data.filter((row) => !query || `${row.courseCode} ${row.courseName} ${row.teacherName}`.toLowerCase().includes(query));
});
const studentWriteLocked = computed(() => ['submitting', 'uncertain'].includes(state.enroll.status) || ['submitting', 'uncertain'].includes(state.drop.status));
const scoreLocked = computed(() => ['submitting', 'uncertain'].includes(state.score.status));
const selectedClass = computed(() => state.classes.data.find((row) => row.id === state.rosterClassId));
const alreadyEnrolled = (id) => state.enrollments.data.some((row) => row.teachingClassId === id && row.status === 'ACTIVE');
const classUnavailable = (row) => row.status !== 'OPEN' || row.enrolledCount >= row.capacity;

async function signIn() {
  const attempt = ++loginAttempt;
  const credentials = { username: username.value.trim(), password: password.value };
  if (!credentials.username || !credentials.password) return;
  const loggedIn = await store.login(credentials.username, credentials.password);
  if (attempt !== loginAttempt) return;
  if (loggedIn) {
    storage.write(AUTH_KEY, credentials);
    password.value = '';
    currentTab.value = 'classes';
  } else storage.write(AUTH_KEY, null);
}

function signOut() {
  loginAttempt++;
  store.logout();
  storage.write(AUTH_KEY, null);
  password.value = '';
  keyword.value = '';
  for (const key of Object.keys(scoreInputs)) delete scoreInputs[key];
}

function useDemoAccount(account) { username.value = account; password.value = 'Chapter10!'; }

async function refreshStudent() {
  await Promise.all([store.loadClasses(), store.loadMyEnrollments()]);
}

watch(() => state.roster.data, (rows) => {
  for (const key of Object.keys(scoreInputs)) delete scoreInputs[key];
  for (const row of rows) scoreInputs[row.id] = row.score ?? '';
});

watch([() => state.me?.role, () => state.classes.status], ([role, status]) => {
  if (role === 'TEACHER' && status === 'data' && state.classes.data.length && !state.rosterClassId) {
    store.loadRoster(state.classes.data[0].id);
  }
});

onMounted(() => {
  const saved = storage.read(AUTH_KEY);
  if (saved?.username && saved?.password) {
    username.value = saved.username;
    password.value = saved.password;
    signIn();
  }
});
</script>

<template>
  <header class="topbar">
    <div class="topbar-inner">
      <a class="brand" href="#" @click.prevent="currentTab = 'classes'">
        <span class="brand-mark" aria-hidden="true">课</span>
        <span>教学选课系统<small>软件设计与体系结构 · 第十章配套工程</small></span>
      </a>
      <div v-if="state.me" class="identity">
        <span><strong>{{ state.me.name }}</strong><small>{{ isStudent ? '学生' : '教师' }} · {{ state.me.username }}</small></span>
        <button class="on-dark" @click="signOut">退出登录</button>
      </div>
      <span v-else class="header-note">课程教学演示</span>
    </div>
  </header>

  <main v-if="!state.me" class="login-layout">
    <section class="login-intro">
      <p class="eyebrow">COURSE SELECTION</p>
      <h1>选课有序，<br>教学清晰。</h1>
      <p class="intro-text">从教学班查询到成绩录入，在一个完整流程中理解认证、业务规则与事务。</p>
      <ul class="feature-list">
        <li><span>01</span>学生：教学班查询、选课、退课与个人课表</li>
        <li><span>02</span>教师：本班名单与成绩录入</li>
        <li><span>03</span>提交有反馈，异常可查询确认</li>
      </ul>
    </section>

    <section class="login-card" aria-labelledby="login-title">
      <p class="eyebrow">欢迎使用</p>
      <h2 id="login-title">账号登录</h2>
      <p class="muted">使用预置的学生或教师账号开始演示。</p>
      <form @submit.prevent="signIn">
        <label for="username">账号</label>
        <input id="username" v-model="username" name="username" autocomplete="username" required :disabled="state.login.status === 'loading'" />
        <label for="password">密码</label>
        <input id="password" v-model="password" name="password" type="password" autocomplete="current-password" required :disabled="state.login.status === 'loading'" />
        <p v-if="state.login.error" class="inline-error" role="alert">{{ state.login.error }}</p>
        <button class="primary full-width" type="submit" :disabled="state.login.status === 'loading'">{{ state.login.status === 'loading' ? '正在登录…' : '登录系统' }}</button>
      </form>
      <div class="demo-accounts">
        <h3>演示账号</h3>
        <p>点击账号可填入登录信息。统一密码：<code>Chapter10!</code></p>
        <div class="account-row"><span>学生</span><button v-for="account in students" :key="account" class="account-chip" :disabled="state.login.status === 'loading'" @click="useDemoAccount(account)">{{ account }}</button></div>
        <div class="account-row"><span>教师</span><button v-for="account in teachers" :key="account" class="account-chip" :disabled="state.login.status === 'loading'" @click="useDemoAccount(account)">{{ account }}</button></div>
      </div>
      <p class="storage-note">登录信息保存在当前标签页的会话存储中，退出登录后清除。</p>
    </section>
  </main>

  <main v-else class="workspace">
    <section class="page-heading">
      <div><p class="eyebrow">{{ isStudent ? '学生工作台' : '教师工作台' }}</p><h1>{{ isStudent ? '我的课程学习' : '本班教学管理' }}</h1><p class="muted">{{ isStudent ? '查询开放的教学班，管理选课并查看每周安排。' : '选择本人教学班，查看学生名单并录入成绩。' }}</p></div>
      <div v-if="isStudent && state.enrollments.status === 'data'" class="summary"><span><strong>{{ activeEnrollments.length }}</strong>已选教学班</span><span><strong>{{ credits }}</strong>总学分</span></div>
    </section>

    <nav v-if="isStudent" class="tabs" aria-label="学生功能">
      <button :class="{ active: currentTab === 'classes' }" @click="currentTab = 'classes'">教学班查询</button>
      <button :class="{ active: currentTab === 'enrollments' }" @click="currentTab = 'enrollments'">我的选课</button>
      <button :class="{ active: currentTab === 'timetable' }" @click="currentTab = 'timetable'">个人课表</button>
    </nav>

    <template v-if="isStudent">
      <MutationStatus :operation="state.enroll">
        <div v-if="state.enroll.status === 'uncertain'" class="notice-actions">
          <button class="secondary small" :disabled="state.enroll.checking" @click="store.confirmEnrollment">{{ state.enroll.checking ? '正在确认…' : '查询个人记录确认' }}</button>
          <button v-if="state.enroll.retryAllowed" class="secondary small" :disabled="state.enroll.checking" @click="store.retryEnrollment">用原请求号重试</button>
        </div>
      </MutationStatus>
      <MutationStatus :operation="state.drop"><button v-if="state.drop.status === 'uncertain'" class="secondary small" :disabled="state.drop.checking" @click="store.confirmDrop">{{ state.drop.checking ? '正在确认…' : '查询退课结果' }}</button></MutationStatus>

      <section v-if="currentTab === 'classes'" class="panel" aria-labelledby="classes-title">
        <div class="panel-heading"><div><h2 id="classes-title">教学班查询</h2><p class="muted">选课规则与名额以服务端提交结果为准。</p></div><button class="secondary" :disabled="state.classes.status === 'loading'" @click="store.loadClasses">刷新教学班</button></div>
        <div class="toolbar"><label for="course-search">课程检索</label><input id="course-search" v-model="keyword" type="search" placeholder="输入课程名称、代码或教师姓名" /><span v-if="state.classes.status === 'data'" class="muted">共 {{ filteredClasses.length }} 个教学班</span></div>
        <ReadStatus :resource="state.classes" empty-text="暂无可查询的教学班。" @retry="store.loadClasses" />
        <div v-if="state.classes.status === 'data' && state.classes.data.length" class="table-scroll">
          <table><thead><tr><th>教学班 / 课程</th><th>任课教师</th><th>上课时间</th><th>学分</th><th>人数 / 容量</th><th>状态</th><th class="action-column">操作</th></tr></thead>
            <tbody><tr v-for="row in filteredClasses" :key="row.id"><td><strong>{{ row.courseName }}</strong><small>{{ row.courseCode }} · 教学班 {{ row.id }}</small></td><td>{{ row.teacherName }}</td><td>{{ timeLabel(row) }}</td><td>{{ row.credits }}</td><td><span :class="{ 'capacity-full': row.enrolledCount >= row.capacity }">{{ row.enrolledCount }} / {{ row.capacity }}</span></td><td><span class="badge" :class="{ neutral: row.status !== 'OPEN' }">{{ statusLabel(row.status) }}</span></td><td><button class="primary small" :aria-label="`选择${row.courseName}教学班${row.id}`" :disabled="studentWriteLocked || alreadyEnrolled(row.id) || classUnavailable(row) || state.enrollments.status !== 'data'" @click="store.enroll(row.id)">{{ alreadyEnrolled(row.id) ? '已选' : state.enroll.status === 'submitting' && state.enroll.teachingClassId === row.id ? '提交中…' : row.enrolledCount >= row.capacity ? '已满' : row.status !== 'OPEN' ? '已关闭' : '选课' }}</button></td></tr>
            <tr v-if="!filteredClasses.length"><td colspan="7" class="empty-cell">没有匹配的教学班，请调整检索词。</td></tr></tbody>
          </table>
        </div>
        <div v-if="state.enrollments.status === 'error'" class="notice warning"><span>个人选课记录尚未加载，暂不能提交选课。</span><button class="secondary small" @click="store.loadMyEnrollments">查询个人记录</button></div>
      </section>

      <section v-else-if="currentTab === 'enrollments'" class="panel" aria-labelledby="enrollments-title">
        <div class="panel-heading"><div><h2 id="enrollments-title">我的选课</h2><p class="muted">当前有效选课；历史保留在数据库供审计。仅开放教学班的未评分记录可退课。</p></div><button class="secondary" :disabled="state.enrollments.status === 'loading'" @click="refreshStudent">刷新选课记录</button></div>
        <ReadStatus :resource="state.enrollments" empty-text="你当前没有有效选课，可前往教学班查询开始选课。" @retry="store.loadMyEnrollments" />
        <div v-if="state.enrollments.status === 'data' && state.enrollments.data.length" class="table-scroll"><table><thead><tr><th>课程</th><th>任课教师</th><th>上课时间</th><th>学分</th><th>选课状态</th><th>成绩</th><th class="action-column">操作</th></tr></thead><tbody><tr v-for="row in state.enrollments.data" :key="row.id"><td><strong>{{ row.courseName }}</strong><small>{{ row.courseCode }} · 教学班 {{ row.teachingClassId }}</small></td><td>{{ row.teacherName }}</td><td>{{ timeLabel(row) }}</td><td>{{ row.credits }}</td><td><span class="badge" :class="{ neutral: row.status !== 'ACTIVE' }">{{ statusLabel(row.status) }}</span></td><td>{{ row.score ?? '未录入' }}</td><td><button v-if="row.status === 'ACTIVE'" class="danger small" :aria-label="`退选${row.courseName}教学班${row.teachingClassId}`" :disabled="studentWriteLocked || !canWithdraw(row)" @click="store.dropEnrollment(row.id)">{{ state.drop.status === 'submitting' && state.drop.id === row.id ? '退课中…' : row.classStatus !== 'OPEN' ? '已结课' : row.score != null ? '已有成绩' : '退课' }}</button><span v-else class="muted">—</span></td></tr></tbody></table></div>
      </section>

      <section v-else class="panel" aria-labelledby="timetable-title">
        <div class="panel-heading"><div><h2 id="timetable-title">个人课表</h2><p class="muted">仅展示当前有效的选课，按星期与节次排列。</p></div><button class="secondary" :disabled="state.enrollments.status === 'loading'" @click="store.loadMyEnrollments">刷新课表</button></div>
        <ReadStatus :resource="state.enrollments" empty-text="暂无课程安排。" @retry="store.loadMyEnrollments" />
        <p v-if="state.enrollments.status === 'data' && state.enrollments.data.length && !activeEnrollments.length" class="empty-state">暂无有效选课，已退课课程不会出现在课表中。</p>
        <div v-if="state.enrollments.status === 'data' && activeEnrollments.length" class="table-scroll"><table><thead><tr><th>上课时间</th><th>课程</th><th>任课教师</th><th>教学班</th><th>学分</th></tr></thead><tbody><tr v-for="row in activeEnrollments" :key="row.id"><td class="time-cell">{{ timeLabel(row) }}</td><td><strong>{{ row.courseName }}</strong><small>{{ row.courseCode }}</small></td><td>{{ row.teacherName }}</td><td>{{ row.teachingClassId }}</td><td>{{ row.credits }}</td></tr></tbody></table></div>
      </section>
    </template>

    <template v-else>
      <MutationStatus :operation="state.score"><button v-if="state.score.status === 'uncertain'" class="secondary small" :disabled="state.score.checking" @click="store.confirmScore">{{ state.score.checking ? '正在确认…' : '查询成绩保存结果' }}</button></MutationStatus>
      <section class="panel" aria-labelledby="roster-title">
        <div class="panel-heading"><div><h2 id="roster-title">本班名单与成绩</h2><p class="muted">仅已结课教学班可录入成绩，范围 0–100，最多两位小数。</p></div><button class="secondary" :disabled="state.classes.status === 'loading' || scoreLocked" @click="store.loadClasses">刷新教学班</button></div>
        <ReadStatus :resource="state.classes" empty-text="当前账号没有授课教学班。" @retry="store.loadClasses" />
        <div v-if="state.classes.status === 'data' && state.classes.data.length" class="toolbar"><label for="teacher-class">本人教学班</label><select id="teacher-class" :value="state.rosterClassId" :disabled="scoreLocked" @change="store.loadRoster(Number($event.target.value))"><option v-for="row in state.classes.data" :key="row.id" :value="row.id">{{ row.courseName }} · 教学班 {{ row.id }} · {{ timeLabel(row) }}</option></select><button class="secondary" :disabled="state.roster.status === 'loading' || scoreLocked" @click="store.loadRoster()">刷新名单</button></div>
        <p v-if="selectedClass" class="class-meta">{{ selectedClass.courseCode }} · {{ selectedClass.teacherName }} · {{ timeLabel(selectedClass) }} · {{ selectedClass.enrolledCount }} / {{ selectedClass.capacity }} 人</p>
        <p v-if="selectedClass?.status === 'OPEN'" class="notice">该教学班仍开放选课，结课后方可录入成绩。</p>
        <ReadStatus :resource="state.roster" empty-text="该教学班暂无学生记录。" @retry="store.loadRoster()" />
        <div v-if="state.roster.status === 'data' && state.roster.data.length" class="table-scroll"><table><thead><tr><th>学生编号</th><th>姓名</th><th>选课状态</th><th>已保存成绩</th><th>录入成绩（0–100）</th><th class="action-column">操作</th></tr></thead><tbody><tr v-for="row in state.roster.data" :key="row.id"><td>{{ row.studentId }}</td><td><strong>{{ row.studentName }}</strong></td><td><span class="badge" :class="{ neutral: row.status !== 'ACTIVE' }">{{ statusLabel(row.status) }}</span></td><td>{{ row.score ?? '未录入' }}</td><td><input v-model="scoreInputs[row.id]" class="score-input" type="number" min="0" max="100" step="0.01" :aria-label="`${row.studentName}的成绩`" :disabled="!canRecordScore(selectedClass, row) || scoreLocked" @keydown.enter.prevent="store.saveScore(row.id, scoreInputs[row.id])" /></td><td><button class="primary small" :aria-label="`保存${row.studentName}的成绩`" :disabled="!canRecordScore(selectedClass, row) || scoreLocked" @click="store.saveScore(row.id, scoreInputs[row.id])">{{ state.score.status === 'submitting' && state.score.id === row.id ? '保存中…' : '保存成绩' }}</button></td></tr></tbody></table></div>
      </section>
    </template>
    <p class="workspace-note">教学演示 · 页面数据以最近一次查询结果为准。</p>
  </main>
  <footer>软件设计与体系结构 · 第二版配套工程</footer>
</template>


