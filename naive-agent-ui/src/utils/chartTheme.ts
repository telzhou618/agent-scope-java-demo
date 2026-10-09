/**
 * 图表主题配色：明/暗两套（纯数据，无 DOM 依赖）。
 * 与 naive-theme.ts 的靛蓝主色保持同族：每套色板首色对应该主题的 accent。
 */

export type ChartThemeName = 'light' | 'dark'

export interface ChartThemeColors {
  /** 系列色板（8 色） */
  palette: string[]
  /** 坐标轴刻度、图例、tooltip 正文 */
  text: string
  /** 图表标题 */
  title: string
  /** 坐标轴线 */
  axisLine: string
  /** 网格分隔线 */
  splitLine: string
  tooltip: { bg: string; border: string; text: string }
  /** K 线：红涨绿跌 */
  candle: { up: string; down: string }
  /** 热力图 visualMap 连续渐变 [低值, 高值] */
  visualMap: [string, string]
}

export const CHART_THEMES: Record<ChartThemeName, ChartThemeColors> = {
  light: {
    palette: [
      '#6366f1',
      '#15803d',
      '#e11d48',
      '#d97706',
      '#0891b2',
      '#7c3aed',
      '#db2777',
      '#65a30d',
    ],
    text: '#64748b',
    title: '#1e293b',
    axisLine: '#cbd5e1',
    splitLine: '#e2e8f0',
    tooltip: { bg: '#ffffff', border: '#e2e8f0', text: '#1e293b' },
    candle: { up: '#dc2626', down: '#16a34a' },
    visualMap: ['#eef2ff', '#4f46e5'],
  },
  dark: {
    palette: [
      '#818cf8',
      '#4ade80',
      '#f87171',
      '#fbbf24',
      '#22d3ee',
      '#a78bfa',
      '#f472b6',
      '#a3e635',
    ],
    text: '#cbd5e1',
    title: '#f1f5f9',
    axisLine: '#475569',
    splitLine: '#334155',
    tooltip: { bg: '#1b2536', border: '#334155', text: '#e6edf6' },
    candle: { up: '#f87171', down: '#4ade80' },
    visualMap: ['#1e1b4b', '#818cf8'],
  },
}

function mergeDefaults(
  value: unknown,
  defaults: Record<string, unknown>,
): Record<string, unknown> {
  const base = value && typeof value === 'object' ? (value as Record<string, unknown>) : {}
  const result: Record<string, unknown> = { ...base }
  for (const key of Object.keys(defaults)) {
    const d = defaults[key]
    const v = result[key]
    if (v === undefined) {
      result[key] = d
    } else if (v && typeof v === 'object' && d && typeof d === 'object' && !Array.isArray(v) && !Array.isArray(d)) {
      result[key] = mergeDefaults(v, d as Record<string, unknown>)
    }
  }
  return result
}

function mergeAxis(axis: unknown, theme: ChartThemeColors): unknown {
  const defaults = {
    axisLine: { lineStyle: { color: theme.axisLine } },
    axisLabel: { color: theme.text },
    splitLine: { lineStyle: { color: theme.splitLine } },
  }
  if (Array.isArray(axis)) return axis.map((a) => mergeDefaults(a, defaults))
  return mergeDefaults(axis, defaults)
}

function mergeRadar(radar: unknown, theme: ChartThemeColors): unknown {
  const defaults = {
    axisName: { color: theme.text },
    axisLine: { lineStyle: { color: theme.splitLine } },
    splitLine: { lineStyle: { color: theme.splitLine } },
  }
  if (Array.isArray(radar)) return radar.map((r) => mergeDefaults(r, defaults))
  return mergeDefaults(radar, defaults)
}

function mergeVisualMap(visualMap: unknown, theme: ChartThemeColors): unknown {
  const merge = (vm: unknown) => {
    const out = mergeDefaults(vm, { textStyle: { color: theme.text } })
    /* 连续型 visualMap 未指定渐变色时，注入当前主题的明暗渐变 */
    if (!out.inRange && !out.pieces) out.inRange = { color: [...theme.visualMap] }
    return out
  }
  if (Array.isArray(visualMap)) return visualMap.map(merge)
  return merge(visualMap)
}

function mergeSeries(series: unknown, theme: ChartThemeColors): unknown {
  const mergeOne = (s: unknown): unknown => {
    if (!s || typeof s !== 'object') return s
    const item = s as Record<string, unknown>
    if (item.type === 'candlestick') {
      return mergeDefaults(item, {
        itemStyle: {
          color: theme.candle.up,
          color0: theme.candle.down,
          borderColor: theme.candle.up,
          borderColor0: theme.candle.down,
        },
      })
    }
    if (item.type === 'gauge') {
      return mergeDefaults(item, {
        axisLine: {
          lineStyle: {
            color: [
              [0.3, theme.palette[1]],
              [0.7, theme.palette[0]],
              [1, theme.palette[2]],
            ],
          },
        },
        /* 默认深灰文字在深色主题上不可读 */
        axisLabel: { color: theme.text },
        title: { color: theme.text },
        detail: { color: theme.title },
      })
    }
    return s
  }
  if (Array.isArray(series)) return series.map(mergeOne)
  return mergeOne(series)
}

/** 给模型输出的 option 注入主题默认配色（option 里显式指定的优先） */
export function applyChartTheme<T extends Record<string, unknown>>(
  option: T,
  theme: ChartThemeColors,
): T {
  const merged = option as Record<string, unknown>
  merged.backgroundColor = merged.backgroundColor ?? 'transparent'
  merged.color = merged.color ?? theme.palette
  merged.textStyle = mergeDefaults(merged.textStyle, { color: theme.text })
  merged.title = mergeDefaults(merged.title, { textStyle: { color: theme.title } })
  merged.legend = mergeDefaults(merged.legend, { textStyle: { color: theme.text } })
  merged.tooltip = mergeDefaults(merged.tooltip, {
    backgroundColor: theme.tooltip.bg,
    borderColor: theme.tooltip.border,
    textStyle: { color: theme.tooltip.text },
  })
  if (merged.xAxis !== undefined) merged.xAxis = mergeAxis(merged.xAxis, theme)
  if (merged.yAxis !== undefined) merged.yAxis = mergeAxis(merged.yAxis, theme)
  if (merged.radar !== undefined) merged.radar = mergeRadar(merged.radar, theme)
  if (merged.visualMap !== undefined) merged.visualMap = mergeVisualMap(merged.visualMap, theme)
  if (merged.series !== undefined) merged.series = mergeSeries(merged.series, theme)
  return option
}
