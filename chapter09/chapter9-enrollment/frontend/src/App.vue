<script setup>
import { computed, onMounted, reactive, ref } from 'vue';
import { authApi } from './api/auth.js';
import { enrollmentApi } from './api/enrollment.js';
import { useEnrollmentPage } from './composables/useEnrollmentPage.js';
import SignInForm from './components/SignInForm.vue';
import TimetableTable from './components/TimetableTable.vue';
import WithdrawalConfirm from './components/WithdrawalConfirm.vue';

const authBusy = ref(true);
const authError = ref('');
const roleMessage = ref('');
const semesterInput = ref('2026秋');
const page = reactive(useEnrollmentPage('2026秋', '501'));
const showLogin = computed(() => !page.identity || page.authRequired);
const queryLabel = computed(
  () => ({ loading: '正在读取', data: '读取完成', error: '读取失败' })[page.queryState],
);
const submitLabel = computed(
  () =>
    ({ idle: '可提交', submitting: '正在提交', confirmed: '已确认', uncertain: '结果未明' })[
      page.submitState
    ],
);
const withdrawalLabel = computed(
  () =>
    ({
      idle: '未发起',
      confirming: '待确认',
      withdrawing: '正在退课',
      confirmed: '已完成',
      uncertain: '结果未明',
    })[page.withdrawState],
);
const describe = (error) =>
  `${error.message}${error.requestId ? `（请求编号：${error.requestId}）` : ''}`;

async function loadUser(user) {
  page.setIdentity(user);
  roleMessage.value = '';
  if (page.isStudent) await page.loadClasses(semesterInput.value);
}
async function signIn(credentials) {
  if (authBusy.value) return;
  if (page.writeBusy && page.identity?.username !== credentials.username) return;
  authBusy.value = true;
  authError.value = '';
  try {
    await loadUser(await authApi.login(credentials.username, credentials.password));
  } catch (error) {
    authError.value = describe(error);
  } finally {
    authBusy.value = false;
  }
}
async function signOut() {
  if (page.writeBusy || page.confirmationBusy || authBusy.value) return;
  authBusy.value = true;
  try {
    await authApi.logout();
    page.setIdentity(null);
    authError.value = '';
  } catch (error) {
    authError.value = describe(error);
  } finally {
    authBusy.value = false;
  }
}
async function checkStudentPermission() {
  roleMessage.value = '正在检查…';
  try {
    await enrollmentApi.listTimetable('2026秋');
    roleMessage.value = '服务器允许了访问；请检查角色配置是否符合教学约定。';
  } catch (error) {
    roleMessage.value = `HTTP ${error.status || '未取得响应'}：${describe(error)}`;
    if (error.status === 401) page.authRequired = true;
  }
}
onMounted(async () => {
  try {
    await loadUser(await authApi.me());
  } catch (error) {
    if (error.status !== 401) authError.value = describe(error);
  } finally {
    authBusy.value = false;
  }
});
</script>

<template>
  <div class="shell">
    <header class="page-header">
      <div>
        <p class="eyebrow">软件设计与体系结构 · 第九章</p>
        <h1>从选课请求到页面反馈</h1>
        <p class="subtitle">查询课程表，提交选课，并确认退课结果。</p>
      </div>
      <div v-if="page.identity" class="account">
        <span>{{ page.identity.username }}</span
        ><span class="muted">{{ page.isStudent ? '学生' : '教师' }}</span>
        <button :disabled="page.writeBusy || page.confirmationBusy || authBusy" @click="signOut">退出登录</button>
      </div>
    </header>
    <main>
      <SignInForm
        v-if="showLogin"
        :busy="authBusy"
        :error="authError"
        :required-username="page.writeBusy ? page.identity?.username : ''"
        @sign-in="signIn"
      />
      <p v-else-if="authError" class="error" role="alert">{{ authError }}</p>

      <section v-if="page.identity && !page.isStudent" class="panel">
        <h2>当前账号为教师角色</h2>
        <p>本页选课与退课仅供学生使用。接口会再次检查角色与数据归属。</p>
        <button @click="checkStudentPermission">验证学生接口的权限限制</button>
        <p role="status">{{ roleMessage }}</p>
      </section>
      <template v-if="page.identity && page.isStudent">
        <section class="panel" aria-labelledby="timetable-title">
          <div class="section-heading">
            <h2 id="timetable-title">01 我的课程表</h2>
            <span class="badge">{{ queryLabel }}</span>
          </div>
          <form class="form-row" @submit.prevent="page.loadClasses(semesterInput)">
            <label>学期<input v-model="semesterInput" readonly /></label>
            <button :disabled="page.authRequired">查询课程表</button>
          </form>
          <p v-if="page.queryState === 'loading'" role="status">正在读取课程表…</p>
          <div v-if="page.queryState === 'error'" class="error" role="alert">
            <p>{{ page.queryError }}</p>
            <button :disabled="page.authRequired" @click="page.loadClasses(semesterInput)">重新读取</button>
          </div>
          <TimetableTable
            v-if="page.queryState === 'data'"
            :items="page.items"
            :can-withdraw="page.canWithdraw"
            @ask-withdraw="page.askWithdraw"
          />
        </section>

        <section class="panel" aria-labelledby="enroll-title">
          <div class="section-heading">
            <h2 id="enroll-title">02 选课提交</h2>
            <span class="badge">{{ submitLabel }}</span>
          </div>
          <p class="muted">基础案例使用教学班 501，容量为 30。以本人的实际课程表和服务器结果为准。</p>
          <div class="form-row">
            <label
              >教学班标识<input
                :value="page.teachingClassId"
                :disabled="page.writeBusy"
                inputmode="numeric"
                @change="page.selectTeachingClass($event.target.value)"
            /></label>
            <button class="primary" :disabled="!page.canEnroll" @click="page.enroll">
              {{ page.submitState === 'confirmed' ? '当前教学班已选' : '提交选课' }}
            </button>
          </div>
          <div v-if="page.submitState === 'uncertain'" class="notice">
            <strong>选课结果未明，请先确认。</strong>
            <p>当前暂停新的选课和退课。重新查询没有结果，也不能据此认定原请求失败。</p>
            <div class="actions">
              <button :disabled="!page.canCheckResult" @click="page.confirmResult">
                {{ page.confirmationBusy ? '正在确认…' : '查询原请求结果' }}</button
              ><button :disabled="!page.canCheckResult" @click="page.retryOriginal">使用原键和原内容重试</button>
            </div>
            <details v-if="page.pendingPost">
              <summary>待确认请求信息</summary>
              <dl>
                <dt>原请求键</dt>
                <dd>{{ page.pendingPost.key }}</dd>
                <dt>教学班</dt>
                <dd>{{ page.pendingPost.body.teachingClassId }}</dd>
                <dt>提交时间</dt>
                <dd>{{ page.pendingPost.submittedAt }}</dd>
                <dt>原提交请求编号</dt>
                <dd>{{ page.pendingPost.requestId || '尚未取得' }}</dd>
              </dl>
            </details>
          </div>
        </section>

        <section class="panel" aria-labelledby="withdrawal-title">
          <div class="section-heading">
            <h2 id="withdrawal-title">03 退课确认</h2>
            <span class="badge">{{ withdrawalLabel }}</span>
          </div>
          <p v-if="page.withdrawState === 'idle'" class="muted">在课程表中选择需要退出的课程，再确认操作。</p>
          <WithdrawalConfirm
            v-if="page.withdrawState === 'confirming'"
            :item="page.pendingWithdrawal"
            :disabled="page.authRequired"
            @cancel="page.cancelWithdraw"
            @confirm="page.withdraw"
          />
          <p v-if="page.withdrawState === 'withdrawing'" role="status">正在等待退课响应…</p>
          <p v-if="page.withdrawState === 'confirmed'">退课已完成。课程表刷新结果单独显示。</p>
          <div v-if="page.withdrawState === 'uncertain'" class="notice">
            <strong>退课结果未明，暂不允许再次写入。</strong>
            <p>请查询当前有效课表。记录已消失时确认退课完成；记录仍有效时，对同一记录重试退课。</p>
            <button
              :disabled="page.authRequired || page.confirmationBusy"
              @click="page.confirmWithdrawal"
            >查询并恢复退课操作</button>
            <dl>
              <dt>选课记录</dt>
              <dd>{{ page.pendingWithdrawal.id }}</dd>
              <dt>提交时间</dt>
              <dd>{{ page.pendingWithdrawal.submittedAt }}</dd>
              <dt>请求编号</dt>
              <dd>{{ page.pendingWithdrawal.requestId || '尚未取得' }}</dd>
            </dl>
          </div>
        </section>
        <p v-if="page.message" class="page-message" role="status" aria-live="polite">
          {{ page.message }}
        </p>
      </template>
    </main>
    <footer>教学演示环境 · 仅使用预置测试账号和数据</footer>
  </div>
</template>
