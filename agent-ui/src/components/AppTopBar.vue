<script setup lang="ts">
import AppIcon from './AppIcon.vue'
import { useChat } from '../stores/chat'
import { useTheme } from '../composables/useTheme'
import { useWide } from '../composables/useWide'

const { state, title, toggleDrawer } = useChat()
const theme = useTheme()
const wide = useWide()
</script>

<template>
  <header class="topbar">
    <button
      class="icon-btn menu-btn"
      type="button"
      aria-label="打开会话列表"
      aria-controls="sessionList"
      :aria-expanded="state.drawerOpen"
      @click="toggleDrawer()"
    >
      <AppIcon name="menu" :size="18" />
    </button>

    <h1 class="topbar-title">{{ title }}</h1>

    <div class="topbar-spacer" />

    <button class="model-chip" type="button" title="模型由后端配置" disabled>
      <span class="dot" aria-hidden="true" />
      Agent Pro
      <AppIcon name="chevron" :size="13" />
    </button>

    <button
      class="icon-btn wide-btn"
      type="button"
      :aria-pressed="wide.wide.value"
      :aria-label="wide.label()"
      :title="wide.label()"
      @click="wide.toggle()"
    >
      <AppIcon :name="wide.wide.value ? 'collapse' : 'expand'" :size="16" />
    </button>

    <button
      class="icon-btn theme-btn"
      type="button"
      :aria-label="theme.label.value"
      :title="theme.label.value"
      @click="theme.cycle()"
    >
      <AppIcon :name="theme.meta.value.icon" :size="16" />
    </button>

    <button class="icon-btn" type="button" aria-label="更多操作" title="更多操作（暂未开放）" disabled>
      <AppIcon name="more" :size="16" filled />
    </button>
  </header>
</template>
