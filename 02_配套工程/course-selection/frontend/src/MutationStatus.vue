<script setup>
defineProps({ operation: { type: Object, required: true } });
</script>

<template>
  <div v-if="operation.message || operation.error" class="notice" :class="{ error: operation.error && operation.status !== 'uncertain', warning: operation.status === 'uncertain', success: operation.status === 'confirmed' }" :role="operation.error ? 'alert' : 'status'" aria-live="polite">
    <div>
      <strong v-if="operation.status === 'uncertain'">结果待确认</strong>
      <p v-if="operation.message">{{ operation.message }}</p>
      <p v-if="operation.error">{{ operation.error }}</p>
    </div>
    <slot />
  </div>
</template>
