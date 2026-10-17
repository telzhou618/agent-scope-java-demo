<script setup lang="ts">
/**
 * 页脚版权信息：集中在此维护，后期调整文案/主体/年份只需改这一个文件
 */
const PRODUCT = 'Agent Platform'
const YEAR = new Date().getFullYear()
</script>

<template>
  <footer class="app-footer">© {{ YEAR }} {{ PRODUCT }} · 保留所有权利</footer>
</template>

<style scoped>
.app-footer {
  width: 100%;
  text-align: center;
  font-size: 12px;
  opacity: 0.5;
}
</style>