<script setup lang="ts">
import { computed, onBeforeUnmount, ref, watch } from 'vue'
import { renderMarkdown } from '../utils/markdown'
import { copyText, flashLabel } from '../utils/clipboard'
import { useLightbox } from '../composables/useLightbox'
import ChartBlock from './ChartBlock.vue'

const props = defineProps<{ markdown: string; streaming?: boolean }>()

const { open: openImage } = useLightbox()

let timer: number | null = null

/**
 * 把 echarts 围栏块从 Markdown 里拆出来：图表块走 ChartBlock 组件（DOM 由 Vue 复用，
 * 流式更新时不会随 v-html 每 50ms 重建，加载动画平滑不闪烁），其余片段仍走 v-html
 */
type Segment = { kind: 'html'; html: string } | { kind: 'chart'; config: string }

const ECHARTS_FENCE = /```echarts[^\S\n]*\n([\s\S]*?)(?:\n```|$)/g

function splitSegments(markdown: string): Segment[] {
  const segments: Segment[] = []
  let last = 0
  for (const match of markdown.matchAll(ECHARTS_FENCE)) {
    const index = match.index ?? 0
    if (index > last) {
      segments.push({ kind: 'html', html: renderMarkdown(markdown.slice(last, index)) })
    }
    segments.push({ kind: 'chart', config: match[1].trim() })
    last = index + match[0].length
  }
  if (last < markdown.length) {
    segments.push({ kind: 'html', html: renderMarkdown(markdown.slice(last)) })
  }
  return segments
}

const rendered = ref<Segment[]>(splitSegments(props.markdown))

// 流式增量很密，节流到 50ms 再跑一次 Markdown 解析
watch(
  () => props.markdown,
  () => {
    if (timer !== null) return
    timer = window.setTimeout(() => {
      timer = null
      rendered.value = splitSegments(props.markdown)
    }, 50)
  },
)

onBeforeUnmount(() => {
  if (timer !== null) window.clearTimeout(timer)
})

function onClick(event: MouseEvent) {
  const target = event.target as HTMLElement | null
  // 正文里的图片：点击弹层放大查看
  if (target instanceof HTMLImageElement && target.src) {
    openImage(target.src, target.alt)
    return
  }
  const button = target?.closest<HTMLElement>('[data-copy]')
  if (!button) return
  const code = button.closest('.code')?.querySelector('code')
  if (!code) return
  void copyText(code.textContent ?? '')
  flashLabel(button)
}

const segments = computed(() => rendered.value)
</script>

<template>
  <div class="prose" @click="onClick">
    <template v-for="(segment, index) in segments" :key="index">
      <div v-if="segment.kind === 'html'" class="md-segment" v-html="segment.html" />
      <ChartBlock v-else :config="segment.config" :streaming="props.streaming" />
    </template>
  </div>
</template>
