/**
 * 图表主题配色：三套皮肤各自的图表专用色板（纯数据，无 DOM 依赖，
 * 前端渲染与 Node 端示例测试共用同一份定义）。
 *
 * 配色与 tokens.css 的设计令牌保持同步：
 * 每套色板前两色对应该皮肤的 --accent / --ok，文字/轴线色对应该皮肤的
 * --fg-muted / --fg / --border-strong / --border。
 */

export type ChartThemeName = 'classic' | 'obsidian' | 'amber'

export interface ChartThemeColors {
  /** 系列色板（8 色），按皮肤明暗调整明度 */
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
  /* 经典蓝（浅色白底）：中等明度、高饱和，保证白底对比度 */
  classic: {
    palette: [
      '#2563eb',
      '#15803d',
      '#e11d48',
      '#d97706',
      '#7c3aed',
      '#0891b2',
      '#db2777',
      '#65a30d',
    ],
    text: '#64748b',
    title: '#1e293b',
    axisLine: '#cbd5e1',
    splitLine: '#e2e8f0',
    tooltip: { bg: '#ffffff', border: '#e2e8f0', text: '#1e293b' },
    candle: { up: '#dc2626', down: '#16a34a' },
    visualMap: ['#eff6ff', '#1d4ed8'],
  },
  /* 曜石黑（深色）：整体提亮到 300~400 色阶，深底上保持发光感 */
  obsidian: {
    palette: [
      '#60a5fa',
      '#4ade80',
      '#f87171',
      '#fbbf24',
      '#a78bfa',
      '#22d3ee',
      '#f472b6',
      '#a3e635',
    ],
    text: '#cbd5e1',
    title: '#f1f5f9',
    axisLine: '#475569',
    splitLine: '#334155',
    tooltip: { bg: '#1b2536', border: '#334155', text: '#e6edf6' },
    candle: { up: '#f87171', down: '#4ade80' },
    visualMap: ['#1e293b', '#60a5fa'],
  },
  /* 琥珀暖阳（暖白底）：偏暖的深色调，辅以冷色点缀避免一片橙 */
  amber: {
    palette: [
      '#d97706',
      '#15803d',
      '#b91c1c',
      '#0369a1',
      '#7c3aed',
      '#be185d',
      '#0f766e',
      '#9a3412',
    ],
    text: '#7c6a49',
    title: '#3a2d17',
    axisLine: '#d6c49c',
    splitLine: '#eadfc6',
    tooltip: { bg: '#fffdf8', border: '#eadfc6', text: '#3a2d17' },
    candle: { up: '#b91c1c', down: '#15803d' },
    visualMap: ['#fdf0da', '#b45309'],
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
    /* 连续型 visualMap 未指定渐变色时，注入当前皮肤的明暗渐变 */
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
        /* 默认深灰文字在深色皮肤上不可读 */
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
