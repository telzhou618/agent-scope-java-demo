<script setup lang="ts">
import { computed, h, ref } from 'vue'
import { NButton, NDropdown, NIcon, useMessage } from 'naive-ui'
import {
  CodeSlashOutline,
  DocumentOutline,
  DownloadOutline,
  PrintOutline,
} from '@vicons/ionicons5'
import { useAuthStore } from '../stores/auth'
import { useChatStore } from '../stores/chat'
import { downloadText, exportFilename, turnsToHtml, turnsToMarkdown, type ExportMeta } from '../utils/export'
import { withChartImages } from '../utils/exportCharts'
import { printHtmlDocument } from '../utils/print'

const message = useMessage()
const auth = useAuthStore()
const chat = useChatStore()

const exporting = ref(false)

/** 流式中或没有内容时不可导出 */
const disabled = computed(() => chat.state.streaming || chat.state.turns.length === 0)

const options = [
  {
    label: '导出 Markdown',
    key: 'md',
    icon: () => h(NIcon, null, { default: () => h(CodeSlashOutline) }),
  },
  {
    label: '导出 HTML',
    key: 'html',
    icon: () => h(NIcon, null, { default: () => h(DocumentOutline) }),
  },
  {
    label: '导出 PDF',
    key: 'pdf',
    icon: () => h(NIcon, null, { default: () => h(PrintOutline) }),
  },
]

function meta(): ExportMeta {
  return {
    userId: auth.user ? String(auth.user.id) : '',
    sessionId: chat.state.currentSessionId,
    title: chat.title,
  }
}

async function onSelect(key: string) {
  if (disabled.value || exporting.value) return
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
</script>

<template>
  <NDropdown trigger="click" :options="options" :disabled="disabled" @select="onSelect">
    <NButton quaternary circle :loading="exporting" :disabled="disabled" title="导出会话">
      <template #icon>
        <NIcon><DownloadOutline /></NIcon>
      </template>
    </NButton>
  </NDropdown>
</template>
