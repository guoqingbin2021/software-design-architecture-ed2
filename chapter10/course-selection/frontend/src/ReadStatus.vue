<script setup>
defineProps({
  resource: { type: Object, required: true },
  emptyText: { type: String, default: '暂无记录。' },
});
defineEmits(['retry']);
</script>

<template>
  <p v-if="resource.status === 'loading'" class="status-line" role="status">正在加载，请稍候…</p>
  <div v-else-if="resource.status === 'error'" class="notice error" role="alert">
    <span>{{ resource.error }}</span>
    <button class="small secondary" @click="$emit('retry')">重新查询</button>
  </div>
  <p
    v-else-if="resource.status === 'data' && !resource.data.length"
    class="empty-state"
    role="status"
  >
    {{ emptyText }}
  </p>
</template>
