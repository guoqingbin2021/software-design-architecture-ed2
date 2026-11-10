<script setup>
import { ref, watch } from 'vue';
const props = defineProps({ busy: Boolean, error: String, requiredUsername: String });
const emit = defineEmits(['sign-in']);
const username = ref(props.requiredUsername || 'student101');
const password = ref('');
watch(
  () => props.requiredUsername,
  (value) => {
    if (value) username.value = value;
  },
);
function submit() {
  if (!props.busy) emit('sign-in', { username: username.value.trim(), password: password.value });
  password.value = '';
}
</script>

<template>
  <section class="panel sign-in" aria-labelledby="sign-in-title">
    <h2 id="sign-in-title">{{ requiredUsername ? '重新登录以确认原请求' : '登录教学账号' }}</h2>
    <p class="muted">学生账号 student101、student102；教师账号 teacher201。教学密码 Chapter9!Demo。</p>
    <form @submit.prevent="submit" class="form-row">
      <label
        >账号<input
          v-model="username"
          autocomplete="username"
          required
          :readonly="!!requiredUsername"
      /></label>
      <label
        >密码<input v-model="password" type="password" autocomplete="current-password" required
      /></label>
      <button class="primary" :disabled="busy">{{ busy ? '正在登录…' : '登录' }}</button>
    </form>
    <p v-if="error" class="error" role="alert">{{ error }}</p>
  </section>
</template>
