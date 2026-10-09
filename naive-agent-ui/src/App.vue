<script setup lang="ts">
import { computed } from 'vue'
import { NConfigProvider, NDialogProvider, NMessageProvider, zhCN, dateZhCN } from 'naive-ui'
import { useThemeStore } from './stores/theme'
import { themeOverrides } from './styles/naive-theme'

const themeStore = useThemeStore()
const naiveTheme = computed(() => themeStore.theme)
</script>

<template>
  <NConfigProvider
    :theme="naiveTheme"
    :theme-overrides="themeOverrides"
    :locale="zhCN"
    :date-locale="dateZhCN"
  >
    <NMessageProvider>
      <NDialogProvider>
        <RouterView />
      </NDialogProvider>
    </NMessageProvider>
  </NConfigProvider>
</template>
