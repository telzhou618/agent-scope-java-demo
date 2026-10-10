<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref, watch, type Component } from 'vue'
import { NButton, NDropdown, NIcon, NTooltip, useMessage } from 'naive-ui'
import { DownloadOutline, ShareSocialOutline } from '@vicons/ionicons5'
import { useAuthStore } from '../stores/auth'
import { useChatStore } from '../stores/chat'
import { useWideMode } from '../composables/useWideMode'
import {
  downloadText,
  exportFilename,
  turnsToHtml,
  turnsToMarkdown,
  type ExportMeta,
} from '../utils/export'
import { withChartImages } from '../utils/exportCharts'
import { printHtmlDocument } from '../utils/print'

/**
 * 聊天页右侧悬浮操作栏：竖向图标按钮，鼠标进入内容列右侧空白区浮现。
 * 感应用 window mousemove 实现（不铺任何覆盖层，绝不遮挡消息点击/选中）：
 * 感应起点 = 内容列右缘（实测父容器 getBoundingClientRect），宽屏内容占满时
 * 回退到视口右缘 56px 内。
 */
const message = useMessage()
const auth = useAuthStore()
const chat = useChatStore()
const { wide } = useWideMode()

interface RailAction {
  key: string
  icon: Component
  tooltip: string
  disabled?: boolean
}

/** 流式中或没有内容时不可导出 */
const exportDisabled = computed(() => chat.state.streaming || chat.state.turns.length === 0)

const actions = computed<RailAction[]>(() => [
  { key: 'export', icon: DownloadOutline, tooltip: '导出会话', disabled: exportDisabled.value },
  { key: 'share', icon: ShareSocialOutline, tooltip: '分享会话' },
])

const exportOptions = [
  { label: '导出 Markdown', key: 'md' },
  { label: '导出 HTML', key: 'html' },
  { label: '导出 PDF', key: 'pdf' },
]

const exporting = ref(false)

function meta(): ExportMeta {
  return {
    userId: auth.user ? String(auth.user.id) : '',
    sessionId: chat.state.currentSessionId,
    title: chat.title,
  }
}

async function onExport(key: string) {
  if (exportDisabled.value || exporting.value) return
  exporting.value = true
  const title = meta().title
  const sessionId = chat.state.currentSessionId
  try {
    // echarts 围栏先离屏渲染成图片，三种格式导出的文件离线可见
    const turns = await withChartImages(chat.state.turns)
    if (key === 'md') {
      downloadText(exportFilename(title, sessionId), turnsToMarkdown(turns, meta()))
    } else if (key === 'html') {
      downloadText(exportFilename(title, sessionId, { extension: 'html' }), turnsToHtml(turns, meta()), 'text/html;charset=utf-8')
    } else if (key === 'pdf') {
      printHtmlDocument(turnsToHtml(turns, meta()))
    }
  } catch {
    message.error('导出失败，请重试')
  } finally {
    exporting.value = false
  }
}

function onAction(key: string) {
  if (key === 'share') {
    message.info('分享功能敬请期待')
  }
}

/* ---------- 右侧空白区感应 ---------- */

const visible = ref(false)
/** 组件根元素（rail 本体）的父元素就是 ChatView 内容容器，用它的右缘做感应起点 */
const railRef = ref<HTMLElement | null>(null)

/** 感应起点 x：内容列右缘；内容占满视口时回退到视口右缘内 56px */
let triggerLeft = 0

function measure() {
  const container = railRef.value?.parentElement
  const contentRight = container?.getBoundingClientRect().right ?? 0
  triggerLeft = Math.min(contentRight, window.innerWidth - 56)
}

function onMouseMove(event: MouseEvent) {
  // 顶栏（56px）以下、感应起点以右的整块空白都算感应区
  visible.value = event.clientX >= triggerLeft && event.clientY > 56
}

function onMouseLeaveWindow() {
  visible.value = false
}

onMounted(() => {
  measure()
  window.addEventListener('resize', measure)
  window.addEventListener('mousemove', onMouseMove)
  document.documentElement.addEventListener('mouseleave', onMouseLeaveWindow)
})

onBeforeUnmount(() => {
  window.removeEventListener('resize', measure)
  window.removeEventListener('mousemove', onMouseMove)
  document.documentElement.removeEventListener('mouseleave', onMouseLeaveWindow)
})

// 宽/窄切换、侧栏开合都会改变内容列位置，下一帧重新测量
watch(wide, () => requestAnimationFrame(measure))
</script>

<template>
  <div
    ref="railRef"
    class="rail"
    :class="{ visible }"
    role="group"
    aria-label="会话操作栏"
  >
    <template v-for="action in actions" :key="action.key">
      <NTooltip placement="left">
        <template #trigger>
          <NDropdown
            v-if="action.key === 'export'"
            trigger="click"
            placement="left"
            :options="exportOptions"
            :disabled="action.disabled"
            @select="onExport"
          >
            <NButton quaternary circle :disabled="action.disabled" :loading="exporting">
              <template #icon>
                <NIcon><component :is="action.icon" /></NIcon>
              </template>
            </NButton>
          </NDropdown>
          <NButton
            v-else
            quaternary
            circle
            :disabled="action.disabled"
            @click="onAction(action.key)"
          >
            <template #icon>
              <NIcon><component :is="action.icon" /></NIcon>
            </template>
          </NButton>
        </template>
        {{ action.tooltip }}
      </NTooltip>
    </template>
  </div>
</template>

<style scoped>
.rail {
  position: fixed;
  top: 50%;
  right: 12px;
  transform: translateY(-50%) translateX(10px);
  z-index: 10;
  display: flex;
  flex-direction: column;
  gap: 4px;
  padding: 6px;
  border-radius: 999px;
  background: var(--surface-elevated);
  border: 1px solid var(--divider-strong);
  box-shadow: var(--shadow-float);
  opacity: 0;
  pointer-events: none;
  transition:
    opacity 0.18s,
    transform 0.18s;
}

.rail.visible {
  opacity: 1;
  transform: translateY(-50%) translateX(0);
  pointer-events: auto;
}
</style>
