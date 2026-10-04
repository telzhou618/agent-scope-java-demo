<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, ref, watch } from 'vue'
import { renderMarkdown } from '../utils/markdown'
import { copyText, flashLabel } from '../utils/clipboard'
import { useLightbox } from '../composables/useLightbox'

const props = defineProps<{ markdown: string }>()

const { open: openImage } = useLightbox()

const html = ref('')
const proseRef = ref<HTMLElement | null>(null)
let timer: number | null = null
let seeded = false

/* ---------- echarts 图表块 ---------- */

type EchartsLib = typeof import('../utils/echarts')
type ChartInstance = ReturnType<EchartsLib['echarts']['init']>

let charts: ChartInstance[] = []
let echartsLib: EchartsLib | null = null
const themeObserver = new MutationObserver(() => void mountCharts())
const resizeObserver = new ResizeObserver(() => {
  for (const chart of charts) chart.resize()
})

themeObserver.observe(document.documentElement, {
  attributes: true,
  attributeFilter: ['data-theme'],
})

watch(
  proseRef,
  (el) => {
    if (el) resizeObserver.observe(el)
  },
  { immediate: true },
)

function disposeCharts() {
  for (const chart of charts) chart.dispose()
  charts = []
}

/** 扫描 echarts 占位块并渲染；JSON 未写完/非法时按源码兜底 */
async function mountCharts() {
  disposeCharts()
  const blocks = proseRef.value?.querySelectorAll<HTMLElement>('.echarts-block')
  if (!blocks?.length) return
  if (!echartsLib) echartsLib = await import('../utils/echarts')
  for (const el of blocks) {
    const raw = el.dataset.config ?? ''
    let option: Record<string, unknown>
    try {
      option = JSON.parse(raw)
    } catch {
      el.textContent = raw
      el.classList.add('echarts-fallback')
      continue
    }
    const chart = echartsLib.echarts.init(el, undefined, { renderer: 'canvas' })
    chart.setOption(echartsLib.withTheme(option))
    charts.push(chart)
  }
}

// 流式增量很密，节流到 50ms 再跑一次 Markdown 解析
watch(
  () => props.markdown,
  (value) => {
    if (!seeded) {
      seeded = true
      html.value = renderMarkdown(value)
      void nextTick(() => void mountCharts())
      return
    }
    if (timer !== null) return
    timer = window.setTimeout(() => {
      timer = null
      html.value = renderMarkdown(props.markdown)
      void nextTick(() => void mountCharts())
    }, 50)
  },
  { immediate: true },
)

onBeforeUnmount(() => {
  if (timer !== null) window.clearTimeout(timer)
  disposeCharts()
  themeObserver.disconnect()
  resizeObserver.disconnect()
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

const content = computed(() => html.value)
</script>

<template>
  <div ref="proseRef" class="prose" @click="onClick" v-html="content" />
</template>
