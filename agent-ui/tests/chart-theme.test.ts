import { test } from 'node:test'
import assert from 'node:assert/strict'
import { mkdirSync, writeFileSync } from 'node:fs'
import { dirname, join } from 'node:path'
import { fileURLToPath } from 'node:url'
import * as echarts from 'echarts'
import {
  applyChartTheme,
  CHART_THEMES,
  type ChartThemeColors,
  type ChartThemeName,
} from '../src/utils/chartTheme.ts'

/**
 * 图表主题示例测试：10 种图表 × 3 套皮肤，SSR 渲染成 SVG 示例
 * 输出到 test-output/chart-themes/，并校验每套配色确实生效。
 * 运行：pnpm test（node --experimental-strip-types --test）
 */

const OUT_DIR = join(dirname(fileURLToPath(import.meta.url)), '..', 'test-output', 'chart-themes')

/** 示例预览用的皮肤底色（与 tokens.css 的 --bg 一致） */
const THEME_BG: Record<ChartThemeName, string> = {
  classic: '#ffffff',
  obsidian: '#0f172a',
  amber: '#fffcf5',
}

const CHART_TYPES = [
  'line',
  'bar',
  'pie',
  'scatter',
  'radar',
  'funnel',
  'gauge',
  'heatmap',
  'candlestick',
  'boxplot',
] as const
type ChartType = (typeof CHART_TYPES)[number]

/** 每种图表一份最小可用示例（模型输出风格：不带 color/backgroundColor） */
const SAMPLE_OPTIONS: Record<ChartType, Record<string, unknown>> = {
  line: {
    title: { text: '一周气温' },
    tooltip: { trigger: 'axis' },
    legend: {},
    xAxis: { type: 'category', data: ['周一', '周二', '周三', '周四', '周五'] },
    yAxis: { type: 'value', name: '温度(°C)' },
    series: [
      { name: '最高气温', type: 'line', smooth: true, data: [22, 25, 23, 27, 26] },
      { name: '最低气温', type: 'line', smooth: true, data: [15, 16, 14, 18, 17] },
    ],
  },
  bar: {
    title: { text: '配件销量' },
    tooltip: { trigger: 'axis' },
    xAxis: { type: 'category', data: ['键盘', '鼠标', '耳机'] },
    yAxis: { type: 'value' },
    series: [{ type: 'bar', data: [128, 256, 96], label: { show: true, position: 'top' } }],
  },
  pie: {
    title: { text: '设备占比' },
    tooltip: { trigger: 'item' },
    legend: {},
    series: [
      {
        type: 'pie',
        radius: ['40%', '70%'],
        label: { show: true, formatter: '{b}: {d}%' },
        data: [
          { name: '手机', value: 45 },
          { name: '电脑', value: 30 },
          { name: '平板', value: 25 },
        ],
      },
    ],
  },
  scatter: {
    title: { text: '身高体重分布' },
    tooltip: { trigger: 'item' },
    legend: {},
    xAxis: { type: 'value', name: '身高(cm)' },
    yAxis: { type: 'value', name: '体重(kg)' },
    series: [
      {
        name: '男',
        type: 'scatter',
        data: [
          [172, 68],
          [178, 75],
          [165, 60],
          [180, 82],
        ],
      },
      {
        name: '女',
        type: 'scatter',
        data: [
          [160, 52],
          [168, 58],
          [155, 47],
          [170, 63],
        ],
      },
    ],
  },
  radar: {
    title: { text: '能力评估' },
    tooltip: {},
    legend: {},
    radar: {
      indicator: [
        { name: '沟通', max: 100 },
        { name: '技术', max: 100 },
        { name: '管理', max: 100 },
        { name: '协作', max: 100 },
        { name: '创新', max: 100 },
      ],
    },
    series: [
      {
        type: 'radar',
        data: [
          { name: '甲', value: [80, 90, 70, 85, 75] },
          { name: '乙', value: [70, 65, 88, 72, 90] },
        ],
      },
    ],
  },
  funnel: {
    title: { text: '销售漏斗' },
    tooltip: { trigger: 'item' },
    legend: {},
    series: [
      {
        type: 'funnel',
        data: [
          { name: '访问', value: 100 },
          { name: '咨询', value: 80 },
          { name: '订单', value: 60 },
          { name: '支付', value: 40 },
          { name: '复购', value: 20 },
        ],
      },
    ],
  },
  gauge: {
    title: { text: '完成率' },
    tooltip: {},
    series: [
      {
        type: 'gauge',
        progress: { show: true },
        detail: { formatter: '{value}%' },
        data: [{ name: '进度', value: 66 }],
      },
    ],
  },
  heatmap: {
    title: { text: '一周活跃度' },
    tooltip: {},
    xAxis: { type: 'category', data: ['周一', '周二', '周三', '周四', '周五'] },
    yAxis: { type: 'category', data: ['上午', '下午', '晚上'] },
    visualMap: { min: 0, max: 20, calculable: true },
    series: [
      {
        type: 'heatmap',
        data: [
          [0, 0, 5],
          [1, 0, 12],
          [2, 0, 8],
          [3, 0, 15],
          [4, 0, 9],
          [0, 1, 10],
          [1, 1, 18],
          [2, 1, 6],
          [3, 1, 20],
          [4, 1, 11],
          [0, 2, 3],
          [1, 2, 7],
          [2, 2, 13],
          [3, 2, 17],
          [4, 2, 4],
        ],
      },
    ],
  },
  candlestick: {
    title: { text: '股价走势' },
    tooltip: { trigger: 'axis' },
    xAxis: { type: 'category', data: ['周一', '周二', '周三', '周四', '周五'] },
    yAxis: { type: 'value' },
    series: [
      {
        type: 'candlestick',
        /* [开盘, 收盘, 最低, 最高] */
        data: [
          [100, 108, 98, 110],
          [108, 104, 101, 112],
          [104, 112, 103, 115],
          [112, 109, 106, 116],
          [109, 118, 108, 120],
        ],
      },
    ],
  },
  boxplot: {
    title: { text: '成绩分布' },
    tooltip: { trigger: 'item' },
    xAxis: { type: 'category', data: ['一班', '二班', '三班'] },
    yAxis: { type: 'value', name: '分数' },
    series: [
      {
        type: 'boxplot',
        /* [最小值, Q1, 中位数, Q3, 最大值] */
        data: [
          [55, 68, 78, 86, 98],
          [48, 62, 74, 84, 95],
          [60, 70, 80, 88, 100],
        ],
      },
    ],
  },
}

/** 每种图表在 SVG 中必须出现的主题色 */
function expectedColors(type: ChartType, theme: ChartThemeColors): string[] {
  switch (type) {
    case 'gauge':
      return [theme.palette[0], theme.palette[1], theme.palette[2]]
    case 'heatmap':
      return [theme.visualMap[1]]
    case 'candlestick':
      return [theme.candle.up, theme.candle.down]
    default:
      return [theme.palette[0]]
  }
}

/** ECharts SVG 输出里颜色可能是 hex 也可能被归一化成 rgb()，两种形式都认 */
function svgHasColor(svg: string, hex: string): boolean {
  const r = parseInt(hex.slice(1, 3), 16)
  const g = parseInt(hex.slice(3, 5), 16)
  const b = parseInt(hex.slice(5, 7), 16)
  return svg.toLowerCase().includes(hex.toLowerCase()) || svg.includes(`rgb(${r},${g},${b})`)
}

function renderSvg(option: Record<string, unknown>): string {
  const chart = echarts.init(null, null, { renderer: 'svg', ssr: true, width: 760, height: 340 })
  try {
    chart.setOption(option)
    return chart.renderToSVGString()
  } finally {
    chart.dispose()
  }
}

const HEX = /^#[0-9a-fA-F]{6}$/

test('三套主题配色结构完整：8 色色板、合法 hex、色板内不重复', () => {
  assert.deepEqual(Object.keys(CHART_THEMES).sort(), ['amber', 'classic', 'obsidian'])
  for (const theme of Object.values(CHART_THEMES)) {
    assert.equal(theme.palette.length, 8)
    assert.equal(new Set(theme.palette).size, 8, '色板颜色不应重复')
    const flat = [
      ...theme.palette,
      theme.text,
      theme.title,
      theme.axisLine,
      theme.splitLine,
      theme.tooltip.bg,
      theme.tooltip.border,
      theme.tooltip.text,
      theme.candle.up,
      theme.candle.down,
      ...theme.visualMap,
    ]
    for (const color of flat) assert.match(color, HEX, `${color} 不是合法的 6 位 hex`)
  }
})

test('模型显式指定的配色不被主题覆盖', () => {
  const option = applyChartTheme(
    { color: ['#123456'], tooltip: { trigger: 'axis' }, series: [{ type: 'line', data: [1, 2] }] },
    CHART_THEMES.obsidian,
  ) as Record<string, unknown>
  assert.deepEqual(option.color, ['#123456'])
  assert.equal((option.tooltip as Record<string, unknown>).trigger, 'axis')
})

test('仪表盘/K线/热力图注入主题文字色与专用色', () => {
  const theme = CHART_THEMES.obsidian
  const gauge = applyChartTheme(
    { series: [{ type: 'gauge', data: [{ name: '进度', value: 66 }] }] },
    theme,
  ) as { series: Array<Record<string, unknown>> }
  const g = gauge.series[0]
  assert.equal((g.detail as Record<string, unknown>).color, theme.title)
  assert.equal((g.axisLabel as Record<string, unknown>).color, theme.text)

  const candle = applyChartTheme(
    { series: [{ type: 'candlestick', data: [[100, 108, 98, 110]] }] },
    theme,
  ) as { series: Array<Record<string, unknown>> }
  const itemStyle = candle.series[0].itemStyle as Record<string, unknown>
  assert.equal(itemStyle.color, theme.candle.up)
  assert.equal(itemStyle.color0, theme.candle.down)

  const heatmap = applyChartTheme(
    { visualMap: { min: 0, max: 20 }, series: [{ type: 'heatmap', data: [[0, 0, 5]] }] },
    theme,
  ) as { visualMap: Record<string, unknown> }
  assert.deepEqual(
    (heatmap.visualMap.inRange as Record<string, unknown>).color,
    theme.visualMap,
  )
})

const themeNames = Object.keys(CHART_THEMES) as ChartThemeName[]

for (const themeName of themeNames) {
  test(`主题 ${themeName}：10 种图表全部渲染成功且配色生效`, () => {
    const theme = CHART_THEMES[themeName]
    const themeDir = join(OUT_DIR, themeName)
    mkdirSync(themeDir, { recursive: true })

    for (const type of CHART_TYPES) {
      const option = applyChartTheme(
        structuredClone({ ...SAMPLE_OPTIONS[type], animation: false, backgroundColor: THEME_BG[themeName] }),
        theme,
      )
      const svg = renderSvg(option)
      assert.ok(svg.startsWith('<svg'), `${themeName}/${type} 未产出 SVG`)
      assert.ok(svg.includes('</svg>'), `${themeName}/${type} SVG 不完整`)
      for (const color of expectedColors(type, theme)) {
        assert.ok(
          svgHasColor(svg, color),
          `${themeName}/${type} 的 SVG 中缺少主题色 ${color}`,
        )
      }
      writeFileSync(join(themeDir, `${type}.svg`), svg)
    }
  })
}

test('生成三套主题的示例预览页 index.html', () => {
  const sections = themeNames
    .map((themeName) => {
      const imgs = CHART_TYPES.map(
        (type) => `      <figure><img src="${themeName}/${type}.svg" alt="${themeName} ${type}"><figcaption>${type}</figcaption></figure>`,
      ).join('\n')
      return `    <section style="background:${THEME_BG[themeName]}">
    <h2>${themeName}</h2>
    <div class="grid">
${imgs}
    </div>
    </section>`
    })
    .join('\n')
  const html = `<!doctype html>
<html lang="zh-CN">
<head>
<meta charset="utf-8">
<title>图表主题配色示例</title>
<style>
  body { margin: 0; font-family: system-ui, sans-serif; background: #888; }
  section { padding: 24px 32px 32px; }
  h2 { margin: 0 0 16px; font-size: 18px; }
  section[style*="0f172a"] h2, section[style*="0f172a"] figcaption { color: #e6edf6; }
  .grid { display: grid; grid-template-columns: repeat(auto-fill, minmax(380px, 1fr)); gap: 16px; }
  figure { margin: 0; border: 1px solid rgba(128,128,128,.3); border-radius: 8px; padding: 8px; }
  img { width: 100%; display: block; }
  figcaption { font-size: 12px; text-align: center; padding-top: 6px; }
</style>
</head>
<body>
${sections}
</body>
</html>
`
  writeFileSync(join(OUT_DIR, 'index.html'), html)
})
