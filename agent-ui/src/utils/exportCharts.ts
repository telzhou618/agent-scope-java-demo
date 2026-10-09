import type { Turn } from './model'

/**
 * 导出前预处理：把会话里的 echarts 围栏块离屏渲染成 PNG 图片（data URL）嵌入，
 * HTML / PDF / Markdown 三种导出共用，导出的文件离线也能看到图表。
 */

const ECHARTS_FENCE = /```echarts[^\S\n]*\n([\s\S]*?)(?:\n```|$)/g

/** 导出图片的渲染尺寸：与导出正文同宽（760px），高度对齐界面图表块 */
const CHART_WIDTH = 760
const CHART_HEIGHT = 340

/** 离屏渲染一份 echarts 配置为 PNG data URL；解析或渲染失败返回 null（调用方保留源码） */
async function renderChartPng(configJson: string): Promise<string | null> {
  let option: Record<string, unknown> | null = null
  try {
    const value: unknown = JSON.parse(configJson)
    if (value && typeof value === 'object') option = value as Record<string, unknown>
  } catch {
    return null
  }
  if (!option) return null

  const { echarts, withTheme, cssVar } = await import('./echarts')
  const host = document.createElement('div')
  host.style.cssText = `position:fixed;left:-10000px;top:0;width:${CHART_WIDTH}px;height:${CHART_HEIGHT}px;visibility:hidden;pointer-events:none;`
  document.body.appendChild(host)
  const chart = echarts.init(host, undefined, { renderer: 'canvas' })
  try {
    // 截图必须关掉入场动画，否则 canvas 停在第 0 帧，系列图形还没画出来
    chart.setOption(withTheme({ ...option, animation: false }))
    return chart.getDataURL({
      type: 'png',
      pixelRatio: 2,
      backgroundColor: cssVar('--surface') || '#ffffff',
    })
  } catch {
    return null
  } finally {
    chart.dispose()
    host.remove()
  }
}

/** 把 markdown 里的 echarts 围栏替换成渲染好的图表图片；渲染失败的块保留源码原样 */
async function embedChartImages(markdown: string): Promise<string> {
  const matches = [...markdown.matchAll(ECHARTS_FENCE)]
  if (matches.length === 0) return markdown
  // 每张图各自独立的离屏 div，并行渲染互不干扰；拼接仍按原文顺序
  const pngs = await Promise.all(matches.map((match) => renderChartPng(match[1].trim())))
  let result = ''
  let last = 0
  matches.forEach((match, index) => {
    const start = match.index ?? 0
    result += markdown.slice(last, start)
    result += pngs[index] ? `![图表](${pngs[index]})` : match[0]
    last = start + match[0].length
  })
  return result + markdown.slice(last)
}

/** 返回一份图表已替换为图片的 turns 副本，供导出函数使用 */
export async function withChartImages(turns: Turn[]): Promise<Turn[]> {
  const out: Turn[] = []
  for (const turn of turns) {
    if (turn.kind === 'user') {
      out.push({ ...turn, text: await embedChartImages(turn.text) })
      continue
    }
    out.push({
      ...turn,
      blocks: await Promise.all(
        turn.blocks.map(async (block) =>
          block.kind === 'text' ? { ...block, markdown: await embedChartImages(block.markdown) } : block,
        ),
      ),
    })
  }
  return out
}
