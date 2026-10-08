<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, ref, watch } from 'vue'
import { useLightbox } from '../composables/useLightbox'
import { iconPaths } from '../utils/icons'

/**
 * echarts 图表块：独立于 v-html 字符串渲染的 Vue 组件，
 * 流式期间 DOM 不重建，加载动画不会被反复重启（闪烁根因）
 */
const props = defineProps<{ config: string; streaming?: boolean }>()

const { open: openImage } = useLightbox()

type EchartsLib = typeof import('../utils/echarts')
type ChartInstance = ReturnType<EchartsLib['echarts']['init']>

const canvasRef = ref<HTMLElement | null>(null)
let chart: ChartInstance | null = null
let echartsLib: EchartsLib | null = null
let toolbarAttached = false

/** JSON 完整且合法时得到 option；未写完/非法为 null */
const option = computed<Record<string, unknown> | null>(() => {
  try {
    const value = JSON.parse(props.config)
    return value && typeof value === 'object' ? (value as Record<string, unknown>) : null
  } catch {
    return null
  }
})

const toolIcon = (path: string) =>
  `<svg width="13" height="13" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">${path}</svg>`

/** 图表块右上角工具栏：下载 PNG / 弹层放大（均为 hover 显示） */
function attachToolbar(chartInstance: ChartInstance, opt: Record<string, unknown>) {
  const wrap = canvasRef.value?.parentElement
  if (!wrap || toolbarAttached) return
  toolbarAttached = true
  const title = (opt.title as { text?: string } | undefined)?.text?.trim() || 'chart'
  const snapshot = () =>
    chartInstance.getDataURL({
      type: 'png',
      pixelRatio: 2,
      backgroundColor: echartsLib?.cssVar('--surface') || '#ffffff',
    })

  const bar = document.createElement('div')
  bar.className = 'echarts-toolbar'

  const downloadBtn = document.createElement('button')
  downloadBtn.className = 'echarts-tool'
  downloadBtn.type = 'button'
  downloadBtn.title = '下载 PNG'
  downloadBtn.innerHTML = toolIcon(iconPaths.download)
  downloadBtn.addEventListener('click', (event) => {
    event.stopPropagation()
    const link = document.createElement('a')
    link.href = snapshot()
    link.download = `${title}.png`
    link.click()
  })

  const zoomBtn = document.createElement('button')
  zoomBtn.className = 'echarts-tool'
  zoomBtn.type = 'button'
  zoomBtn.title = '放大查看'
  zoomBtn.innerHTML = toolIcon(iconPaths.expand)
  zoomBtn.addEventListener('click', (event) => {
    event.stopPropagation()
    openImage(snapshot(), title)
  })

  bar.append(downloadBtn, zoomBtn)
  wrap.appendChild(bar)
}

async function renderChart(opt: Record<string, unknown>) {
  await nextTick()
  if (!canvasRef.value) return
  if (!echartsLib) echartsLib = await import('../utils/echarts')
  if (!chart) chart = echartsLib.echarts.init(canvasRef.value, undefined, { renderer: 'canvas' })
  chart.setOption(echartsLib.withTheme(opt))
  attachToolbar(chart, opt)
}

watch(
  option,
  (opt) => {
    if (opt) void renderChart(opt)
  },
  { immediate: true },
)

// 皮肤切换时重渲染配色
const themeObserver = new MutationObserver(() => {
  if (option.value) void renderChart(option.value)
})
themeObserver.observe(document.documentElement, {
  attributes: true,
  attributeFilter: ['data-theme'],
})

const resizeObserver = new ResizeObserver(() => chart?.resize())
watch(
  canvasRef,
  (el) => {
    if (el) resizeObserver.observe(el)
  },
  { immediate: true },
)

onBeforeUnmount(() => {
  chart?.dispose()
  chart = null
  themeObserver.disconnect()
  resizeObserver.disconnect()
})
</script>

<template>
  <div
    class="echarts-block"
    :class="{ 'echarts-loading': !option && streaming, 'echarts-error': !option && !streaming }"
  >
    <div v-if="!option && streaming" class="echarts-loading-mask">
      <span class="echarts-spinner" /><span>图表生成中…</span>
    </div>
    <span v-else-if="!option">图表数据解析失败</span>
    <div v-show="option" ref="canvasRef" class="echarts-canvas"></div>
  </div>
</template>
