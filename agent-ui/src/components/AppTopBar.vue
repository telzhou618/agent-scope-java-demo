<script setup lang="ts">
import { computed } from 'vue'
import AppIcon from './AppIcon.vue'
import { useAuthStore } from '../stores/auth'
import { useChat } from '../stores/chat'
import { useTheme, THEME_LIST, type ThemeChoice } from '../composables/useTheme'
import { useWide } from '../composables/useWide'
import { useDismissableMenu } from '../composables/useDismissableMenu'
import { downloadText, exportFilename, turnsToHtml, turnsToMarkdown } from '../utils/export'
import { withChartImages } from '../utils/exportCharts'
import { printHtmlDocument } from '../utils/print'
import type { IconName } from '../utils/icons'

const { state, title, toggleSidebar } = useChat()
const auth = useAuthStore()
const theme = useTheme()
const wide = useWide()
const {
  open: menuOpen,
  bindRoot: bindMenuRoot,
  close: closeMenu,
  toggle: toggleMenu,
} = useDismissableMenu()
const {
  open: skinOpen,
  bindRoot: bindSkinRoot,
  close: closeSkin,
  toggle: toggleSkin,
} = useDismissableMenu()

const skinItems = (Object.keys(THEME_LIST) as ThemeChoice[]).map((key) => ({
  key,
  ...THEME_LIST[key],
}))

function pickSkin(key: ThemeChoice) {
  theme.set(key)
  closeSkin()
}

const canExport = computed(() => !state.streaming && state.turns.length > 0)

const exportItems: { key: 'md' | 'html' | 'pdf'; label: string; icon: IconName; hint: string }[] = [
  { key: 'md', label: '导出 Markdown', icon: 'download', hint: '' },
  { key: 'html', label: '导出 HTML', icon: 'download', hint: '' },
  {
    key: 'pdf',
    label: '导出 PDF',
    icon: 'printer',
    hint: '会打开系统打印对话框，选「另存为 PDF」',
  },
]

async function exportSession(format: 'md' | 'html' | 'pdf') {
  if (!canExport.value) return
  const at = new Date()
  const meta = {
    userId: String(auth.user?.id ?? ''),
    sessionId: state.currentSessionId,
    title: title.value,
  }
  closeMenu()
  // echarts 围栏先渲染成图表图片再导出，三种格式看到的都是真实图表
  const turns = await withChartImages(state.turns)

  if (format === 'pdf') {
    printHtmlDocument(turnsToHtml(turns, meta, at))
    return
  }
  if (format === 'html') {
    downloadText(
      exportFilename(title.value, state.currentSessionId, { date: at, extension: 'html' }),
      turnsToHtml(turns, meta, at),
      'text/html;charset=utf-8',
    )
    return
  }
  downloadText(
    exportFilename(title.value, state.currentSessionId, { date: at }),
    turnsToMarkdown(turns, meta, at),
  )
}
</script>

<template>
  <header class="topbar">
    <button
      class="icon-btn menu-btn"
      type="button"
      aria-label="打开会话列表"
      aria-controls="sessionList"
      :aria-expanded="state.sidebarOpen"
      @click="toggleSidebar()"
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

    <div :ref="bindSkinRoot" class="more-wrap">
      <button
        class="icon-btn theme-btn"
        type="button"
        aria-label="切换皮肤主题"
        title="切换皮肤主题"
        aria-haspopup="menu"
        :aria-expanded="skinOpen"
        @click="toggleSkin()"
      >
        <span class="skin-dot" :style="{ background: theme.meta.value.swatch }" aria-hidden="true" />
      </button>
      <div v-if="skinOpen" class="more-menu" role="menu">
        <button
          v-for="item in skinItems"
          :key="item.key"
          class="more-item"
          type="button"
          role="menuitemradio"
          :aria-checked="theme.choice.value === item.key"
          @click="pickSkin(item.key)"
        >
          <span class="skin-dot" :style="{ background: item.swatch }" aria-hidden="true" />
          {{ item.name }}
          <AppIcon v-if="theme.choice.value === item.key" name="check" :size="14" class="skin-check" />
        </button>
      </div>
    </div>

    <div :ref="bindMenuRoot" class="more-wrap">
      <button
        class="icon-btn"
        type="button"
        aria-label="更多操作"
        aria-haspopup="menu"
        :aria-expanded="menuOpen"
        @click="toggleMenu()"
      >
        <AppIcon name="more" :size="16" filled />
      </button>
      <div v-if="menuOpen" class="more-menu" role="menu">
        <button
          v-for="item in exportItems"
          :key="item.key"
          class="more-item"
          type="button"
          role="menuitem"
          :disabled="!canExport"
          :title="canExport ? item.hint : '当前没有可导出的内容'"
          @click="exportSession(item.key)"
        >
          <AppIcon :name="item.icon" :size="14" />
          {{ item.label }}
        </button>
      </div>
    </div>
  </header>
</template>
