<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import AppIcon from './AppIcon.vue'
import { useChat } from '../stores/chat'
import { useTheme } from '../composables/useTheme'
import { useWide } from '../composables/useWide'
import { useSidebar } from '../composables/useSidebar'
import { downloadText, exportFilename, turnsToHtml, turnsToMarkdown } from '../utils/export'

const { state, title } = useChat()
const theme = useTheme()
const wide = useWide()
const sidebar = useSidebar()

const menuOpen = ref(false)
const menuRoot = ref<HTMLElement | null>(null)

const canExport = computed(() => !state.streaming && state.turns.length > 0)

function exportSession(format: 'md' | 'html') {
  if (!canExport.value) return
  const meta = {
    userId: state.userId,
    sessionId: state.currentSessionId,
    title: title.value,
  }
  if (format === 'html') {
    downloadText(
      exportFilename(title.value, state.currentSessionId, { extension: 'html' }),
      turnsToHtml(state.turns, meta),
      'text/html;charset=utf-8',
    )
  } else {
    downloadText(
      exportFilename(title.value, state.currentSessionId),
      turnsToMarkdown(state.turns, meta),
    )
  }
  menuOpen.value = false
}

function onDocumentClick(event: MouseEvent) {
  if (!menuOpen.value) return
  const target = event.target as Node | null
  if (target && menuRoot.value?.contains(target)) return
  menuOpen.value = false
}

function onKeydown(event: KeyboardEvent) {
  if (event.key === 'Escape') menuOpen.value = false
}

onMounted(() => {
  document.addEventListener('click', onDocumentClick)
  document.addEventListener('keydown', onKeydown)
})

onBeforeUnmount(() => {
  document.removeEventListener('click', onDocumentClick)
  document.removeEventListener('keydown', onKeydown)
})
</script>

<template>
  <header class="topbar">
    <button
      class="icon-btn menu-btn"
      type="button"
      aria-label="打开会话列表"
      aria-controls="sessionList"
      :aria-expanded="sidebar.visible.value"
      @click="sidebar.toggle()"
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

    <div ref="menuRoot" class="more-wrap">
      <button
        class="icon-btn"
        type="button"
        aria-label="更多操作"
        aria-haspopup="menu"
        :aria-expanded="menuOpen"
        @click="menuOpen = !menuOpen"
      >
        <AppIcon name="more" :size="16" filled />
      </button>
      <div v-if="menuOpen" class="more-menu" role="menu">
        <button
          class="more-item"
          type="button"
          role="menuitem"
          :disabled="!canExport"
          :title="canExport ? '' : '当前没有可导出的内容'"
          @click="exportSession('md')"
        >
          <AppIcon name="download" :size="14" />
          导出 Markdown
        </button>
        <button
          class="more-item"
          type="button"
          role="menuitem"
          :disabled="!canExport"
          :title="canExport ? '' : '当前没有可导出的内容'"
          @click="exportSession('html')"
        >
          <AppIcon name="download" :size="14" />
          导出 HTML
        </button>
      </div>
    </div>
  </header>
</template>
