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

/** 当前明/暗主题名：theme store 把当前主题同步在 documentElement.dataset.theme 上 */
export function currentChartThemeName(): ChartThemeName {
  return document.documentElement.dataset.theme === 'dark' ? 'dark' : 'light'
}

/** 跟随当前主题的图表配色与文字色 */
export function chartTheme(): ChartThemeColors {
  return CHART_THEMES[currentChartThemeName()]
}

/** 图表截图（导出/下载 PNG）的底色，与界面底色一致 */
export function chartSurface(): string {
  return currentChartThemeName() === 'dark' ? '#18181c' : '#ffffff'
}

/** 给模型输出的 option 注入主题默认值（模型显式指定的优先） */
export function withTheme<T extends Record<string, unknown>>(option: T): T {
  return applyChartTheme(option, chartTheme())
}
