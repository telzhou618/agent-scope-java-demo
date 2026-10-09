<script setup lang="ts">
import { onBeforeUnmount, ref, watch } from 'vue'
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

/* ---------- 流式增量解析 ----------
 * 流式期间每 50ms 全量重解析整段 markdown 是 O(n²)。流式内容只往后追加，
 * 最后一个未闭合围栏代码块之前的部分是稳定前缀，只解析一次并缓存；
 * 每帧只重解析自该围栏起的增量尾部（未闭合的 echarts 围栏仍切给 ChartBlock，
 * 加载动画行为与全量解析一致）。
 */

/** 行首围栏（CommonMark：最多 3 空格缩进，``` 或 ~~~） */
const FENCE_OPEN = /^ {0,3}(`{3,}|~{3,})/

/** 未闭合围栏的起始偏移；全部闭合返回 -1 */
function unclosedFenceStart(markdown: string): number {
  let openChar = ''
  let openLen = 0
  let openStart = -1
  let offset = 0
  for (const line of markdown.split('\n')) {
    if (openStart < 0) {
      const match = FENCE_OPEN.exec(line)
      if (match) {
        openChar = match[1][0]
        openLen = match[1].length
        openStart = offset
      }
    } else if (new RegExp(`^ {0,3}${openChar}{${openLen},}[ \\t]*$`).test(line)) {
      // 闭合围栏：同字符、长度不少于开围栏、不带 info string
      openStart = -1
    }
    offset += line.length + 1
  }
  return openStart
}

/** 尾部出现链接引用定义会回溯影响前缀的渲染，拿不准就回退全量解析 */
const REF_DEFINITION = /^ {0,3}\[[^\]]+\]:/m

let cache: { prefix: string; segments: Segment[] } = { prefix: '', segments: [] }

function render(markdown: string): Segment[] {
  const fenceStart = unclosedFenceStart(markdown)
  if (fenceStart <= 0) return splitSegments(markdown)
  const prefix = markdown.slice(0, fenceStart)
  const tail = markdown.slice(fenceStart)
  if (REF_DEFINITION.test(tail)) return splitSegments(markdown)
  if (cache.prefix !== prefix) {
    cache = { prefix, segments: splitSegments(prefix) }
  }
  return [...cache.segments, ...splitSegments(tail)]
}

const rendered = ref<Segment[]>(splitSegments(props.markdown))

// 流式增量很密，节流到 50ms 再跑一次 Markdown 解析
watch(
  () => props.markdown,
  () => {
    if (timer !== null) return
    timer = window.setTimeout(() => {
      timer = null
      rendered.value = render(props.markdown)
    }, 50)
  },
)

// 流结束后丢掉缓存全量重解析一次：增量切分是流式期间的近似，收尾以完整解析为准
watch(
  () => props.streaming,
  (streaming) => {
    if (streaming) return
    if (timer !== null) {
      window.clearTimeout(timer)
      timer = null
    }
    cache = { prefix: '', segments: [] }
    rendered.value = splitSegments(props.markdown)
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
</script>

<template>
  <div class="prose" @click="onClick">
    <template v-for="(segment, index) in rendered" :key="index">
      <div v-if="segment.kind === 'html'" class="md-segment" v-html="segment.html" />
      <ChartBlock v-else :config="segment.config" :streaming="props.streaming" />
    </template>
  </div>
</template>
