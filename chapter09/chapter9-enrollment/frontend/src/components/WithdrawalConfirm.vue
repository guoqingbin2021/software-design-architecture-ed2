<script setup>
defineProps({ item: { type: Object, required: true }, disabled: Boolean });
defineEmits(['cancel', 'confirm']);
</script>

<template>
  <section class="confirmation" role="group" aria-labelledby="withdraw-title">
    <h3 id="withdraw-title">确认退课</h3>
    <p>确认退出「{{ item.courseName }}」（教学班 {{ item.teachingClassId }}）？</p>
    <div class="actions">
      <button @click="$emit('cancel')">取消</button>
      <button class="danger" :disabled="disabled" @click="$emit('confirm')">确认退课</button>
    </div>
  </section>
</template>
