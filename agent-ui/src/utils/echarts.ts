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
import {
  applyChartTheme,
  CHART_THEMES,
  type ChartThemeColors,
  type ChartThemeName,
} from './chartTheme'

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

/** 当前皮肤对应的图表主题名；未知值回落到经典蓝 */
export function currentChartThemeName(): ChartThemeName {
  const value = document.documentElement.dataset.theme
  return value === 'obsidian' || value === 'amber' ? value : 'classic'
}

/** 跟随当前皮肤的图表配色与文字色 */
export function chartTheme(): ChartThemeColors {
  return CHART_THEMES[currentChartThemeName()]
}

/** 给模型输出的 option 注入主题默认值（模型显式指定的优先） */
export function withTheme<T extends Record<string, unknown>>(option: T): T {
  return applyChartTheme(option, chartTheme())
}
