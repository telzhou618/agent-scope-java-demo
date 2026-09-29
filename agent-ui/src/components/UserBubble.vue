<script setup lang="ts">
const props = defineProps<{ text: string }>()
</script>

<template>
  <div class="msg-user">
    <div class="bubble">{{ props.text }}</div>
  </div>
</template>
