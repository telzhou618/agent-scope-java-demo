import * as echarts from 'echarts/core'
import {
  BarChart,
  BoxplotChart,
  CandlestickChart,
  FunnelChart,
  GaugeChart,
  HeatmapChart,
  LineChart,
  PieChart,
  RadarChart,
  ScatterChart,
} from 'echarts/charts'
import {
  DataZoomComponent,
  DatasetComponent,
  GridComponent,
  LegendComponent,
  MarkLineComponent,
  MarkPointComponent,
  TitleComponent,
  ToolboxComponent,
  TooltipComponent,
  VisualMapComponent,
} from 'echarts/components'
import { CanvasRenderer } from 'echarts/renderers'

/** 按需注册：常见图表类型 + 交互组件，避免全量包 */
echarts.use([
  LineChart,
  BarChart,
  PieChart,
  ScatterChart,
  RadarChart,
  FunnelChart,
  GaugeChart,
  HeatmapChart,
  CandlestickChart,
  BoxplotChart,
  GridComponent,
  TooltipComponent,
  LegendComponent,
  TitleComponent,
  DatasetComponent,
  VisualMapComponent,
  DataZoomComponent,
  ToolboxComponent,
  MarkLineComponent,
  MarkPointComponent,
  CanvasRenderer,
])

export { echarts }

const cssVar = (name: string) =>
  getComputedStyle(document.documentElement).getPropertyValue(name).trim()

export { cssVar }

/** 跟随当前皮肤的图表配色与文字色 */
export function chartTheme() {
  return {
    palette: [
      cssVar('--accent'),
      cssVar('--ok'),
      '#e11d48',
      '#d97706',
      '#7c3aed',
      '#0891b2',
      '#db2777',
      '#65a30d',
    ],
    textColor: cssVar('--chart-text'),
    titleColor: cssVar('--chart-title'),
    axisLine: cssVar('--chart-axis'),
    splitLine: cssVar('--chart-split'),
  }
}

function mergeObject(
  target: Record<string, unknown>,
  source: Record<string, unknown>,
): Record<string, unknown> {
  const result = { ...target }
  for (const key of Object.keys(source)) {
    const s = source[key]
    const t = result[key]
    if (s && typeof s === 'object' && t && typeof t === 'object') {
      result[key] = mergeObject(t as Record<string, unknown>, s as Record<string, unknown>)
    } else {
      result[key] = s
    }
  }
  return result
}

function withDefaults<T>(value: unknown, defaults: Record<string, unknown>): T {
  if (!value || typeof value !== 'object') return defaults as T
  return mergeObject(value as Record<string, unknown>, defaults) as T
}

function mergeAxis(axis: unknown, theme: ReturnType<typeof chartTheme>): unknown {
  const defaults = {
    axisLine: { lineStyle: { color: theme.axisLine } },
    axisLabel: { color: theme.textColor },
    splitLine: { lineStyle: { color: theme.splitLine } },
  }
  if (Array.isArray(axis)) {
    return axis.map((a) => withDefaults(a, defaults))
  }
  return axis ? withDefaults(axis, defaults) : undefined
}

/** 给模型输出的 option 注入主题默认值（模型显式指定的优先） */
export function withTheme<T extends Record<string, unknown>>(option: T): T {
  const theme = chartTheme()
  const merged = option as Record<string, unknown>
  merged.backgroundColor = merged.backgroundColor ?? 'transparent'
  merged.color = merged.color ?? theme.palette
  merged.textStyle = { color: theme.textColor, ...(merged.textStyle as object | undefined) }
  merged.title = withDefaults(merged.title, { textStyle: { color: theme.titleColor } })
  merged.legend = withDefaults(merged.legend, { textStyle: { color: theme.textColor } })
  if (merged.xAxis !== undefined) merged.xAxis = mergeAxis(merged.xAxis, theme)
  if (merged.yAxis !== undefined) merged.yAxis = mergeAxis(merged.yAxis, theme)
  return option
}
