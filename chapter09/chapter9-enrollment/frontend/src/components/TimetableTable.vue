<script setup>
defineProps({ items: { type: Array, required: true }, canWithdraw: Boolean });
defineEmits(['ask-withdraw']);
const display = (value) => (value === null ? '尚未安排' : value);
</script>

<template>
  <p v-if="items.length === 0" class="empty">本学期暂无课程。</p>
  <div v-else class="table-scroll">
    <table>
      <caption class="sr-only">本人的有效选课记录</caption>
      <thead>
        <tr>
          <th>课程 / 教学班</th>
          <th>教师</th>
          <th>地点</th>
          <th>周次</th>
          <th>节次</th>
          <th>操作</th>
        </tr>
      </thead>
      <tbody>
        <tr v-for="item in items" :key="item.id">
          <td>
            <strong>{{ item.courseName }}</strong
            ><span class="cell-detail">教学班 {{ item.teachingClassId }} · 记录 {{ item.id }}</span>
          </td>
          <td>{{ display(item.teacherName) }}</td>
          <td>{{ display(item.room) }}</td>
          <td>{{ display(item.week) }}</td>
          <td>{{ display(item.time) }}</td>
          <td>
            <button :disabled="!canWithdraw" @click="$emit('ask-withdraw', item)">退课</button>
          </td>
        </tr>
      </tbody>
    </table>
  </div>
</template>
