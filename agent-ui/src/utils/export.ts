import type { TextBlockModel, Turn } from './model'

export interface ExportMeta {
  userId: string
  sessionId: string | null
  title: string
}

const pad = (value: number) => String(value).padStart(2, '0')

/** 2026-09-29 17:53 */
function formatStamp(date: Date): string {
  return (
    `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())} ` +
    `${pad(date.getHours())}:${pad(date.getMinutes())}`
  )
}

/** 把正文里的标题整体降级，避免与「## 我 / ## Agent」抢层级；跳过围栏代码块 */
function shiftHeadings(markdown: string, levels: number): string {
  let inFence = false
  return markdown
    .split('\n')
    .map((line) => {
      if (/^\s*(```|~~~)/.test(line)) {
        inFence = !inFence
        return line
      }
      if (inFence) return line
      const matched = /^(#{1,6})(\s+.*)$/.exec(line)
      if (!matched) return line
      return '#'.repeat(Math.min(6, matched[1].length + levels)) + matched[2]
    })
    .join('\n')
}

/**
 * 导出会话详情：只保留提问与回答正文，跳过思考过程和工具调用。
 * 只有工具调用、没有正文的回合整段略去，避免出现空的「## Agent」。
 */
export function turnsToMarkdown(turns: Turn[], meta: ExportMeta, exportedAt = new Date()): string {
  const parts: string[] = [
    `# ${meta.title || '会话'}`,
    [
      `- 用户 ID：${meta.userId}`,
      `- 会话 ID：${meta.sessionId ?? ''}`,
      `- 导出时间：${formatStamp(exportedAt)}`,
    ].join('\n'),
  ]

  for (const turn of turns) {
    if (turn.kind === 'user') {
      const text = turn.text.trim()
      if (text) parts.push(`## 我\n\n${text}`)
      continue
    }
    const text = turn.blocks
      .filter((block): block is TextBlockModel => block.kind === 'text')
      .map((block) => block.markdown.trim())
      .filter(Boolean)
      .join('\n\n')
    if (text) parts.push(`## Agent\n\n${shiftHeadings(text, 2)}`)
  }

  return `${parts.join('\n\n---\n\n')}\n`
}

/** 文件名：标题 + 时间戳，去掉 Windows 不允许的字符 */
export function exportFilename(title: string, sessionId: string | null, date = new Date()): string {
  const base = (title || sessionId || 'session')
    .replace(/[\\/:*?"<>|]+/g, '_')
    .replace(/\s+/g, ' ')
    .trim()
    .replace(/[. ]+$/, '')
  const clipped = base.slice(0, 40) || 'session'
  const stamp =
    `${date.getFullYear()}${pad(date.getMonth() + 1)}${pad(date.getDate())}` +
    `-${pad(date.getHours())}${pad(date.getMinutes())}`
  return `${clipped}-${stamp}.md`
}

export function downloadText(filename: string, text: string, mime = 'text/markdown;charset=utf-8') {
  const url = URL.createObjectURL(new Blob([text], { type: mime }))
  const link = document.createElement('a')
  link.href = url
  link.download = filename
  document.body.appendChild(link)
  link.click()
  link.remove()
  // 延迟释放：过早 revoke 会让还没读出内容的下载被取消
  window.setTimeout(() => URL.revokeObjectURL(url), 60_000)
}
