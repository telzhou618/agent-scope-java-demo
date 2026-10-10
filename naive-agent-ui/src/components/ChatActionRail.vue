<script setup lang="ts">
import { computed, h, ref, type Component } from 'vue'
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
 * 聊天页右侧悬浮操作栏：竖向图标按钮，hover 右侧空白区浮现。
 * 配置数组驱动——新增操作只需在 ACTIONS 里加一项（export 走下拉菜单，其余走 onAction）。
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
</script>

<template>
  <!-- 悬浮感应区：固定在聊天内容区右缘外侧的空白里，hover 浮现操作栏 -->
  <div class="rail-zone" :class="{ wide }" role="group" aria-label="会话操作栏">
    <div class="rail">
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
  </div>
</template>

<style scoped>
/*
 * 定位：聊天内容区居中（窄 800px / 宽 1200px），右侧空白 = (视口 - 侧栏 272px - 内容宽) / 2。
 * 悬浮栏贴在内容区右缘外侧（空白 - 栏宽 40px - 12px 间距）；空白不够时内缩到视口右缘 8px。
 */
.rail-zone {
  position: fixed;
  top: 50%;
  transform: translateY(-50%);
  right: max(8px, calc((100vw - 272px - 800px) / 2 - 52px));
  z-index: 10;
  padding: 32px 8px;
}

.rail-zone.wide {
  right: max(8px, calc((100vw - 272px - 1200px) / 2 - 52px));
}

.rail {
  display: flex;
  flex-direction: column;
  gap: 4px;
  padding: 6px;
  border-radius: 999px;
  background: #ffffff;
  border: 1px solid rgba(100, 116, 139, 0.22);
  box-shadow: 0 8px 24px rgba(15, 23, 42, 0.12);
  opacity: 0;
  transform: translateX(10px);
  transition:
    opacity 0.18s,
    transform 0.18s;
  pointer-events: none;
}

.rail-zone:hover .rail,
.rail:focus-within {
  opacity: 1;
  transform: translateX(0);
  pointer-events: auto;
}

html[data-theme='dark'] .rail {
  background: #232634;
  border-color: rgba(255, 255, 255, 0.12);
  box-shadow: 0 8px 24px rgba(0, 0, 0, 0.45);
}
</style>
