import type { TextBlockModel, Turn } from './model'
import { escapeHtml, renderMarkdownForExport } from './markdown'

export interface ExportMeta {
  userId: string
  sessionId: string | null
  title: string
}

interface ExportPart {
  speaker: '我' | 'Agent'
  markdown: string
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
 * 抽取要导出的内容：只保留提问与回答正文，跳过思考过程和工具调用。
 * 只有工具调用、没有正文的回合整段略去，避免出现空的「Agent」小节。
 */
function pickParts(turns: Turn[]): ExportPart[] {
  const parts: ExportPart[] = []
  for (const turn of turns) {
    if (turn.kind === 'user') {
      const text = turn.text.trim()
      if (text) parts.push({ speaker: '我', markdown: text })
      continue
    }
    const text = turn.blocks
      .filter((block): block is TextBlockModel => block.kind === 'text')
      .map((block) => block.markdown.trim())
      .filter(Boolean)
      .join('\n\n')
    if (text) parts.push({ speaker: 'Agent', markdown: shiftHeadings(text, 2) })
  }
  return parts
}

const metaLines = (meta: ExportMeta, exportedAt: Date) => [
  `- 用户 ID：${meta.userId}`,
  `- 会话 ID：${meta.sessionId ?? ''}`,
  `- 导出时间：${formatStamp(exportedAt)}`,
]

/** 导出为 Markdown */
export function turnsToMarkdown(turns: Turn[], meta: ExportMeta, exportedAt = new Date()): string {
  const parts = [`# ${meta.title || '会话'}`, metaLines(meta, exportedAt).join('\n')]

  for (const part of pickParts(turns)) {
    parts.push(`## ${part.speaker}\n\n${part.markdown}`)
  }

  return `${parts.join('\n\n---\n\n')}\n`
}

/** 导出文件里自带的一份样式，浅色/深色跟随系统 */
const HTML_STYLE = `
:root {
  color-scheme: light dark;
  --fg: #1e293b; --fg-muted: #64748b; --border: #e2e8f0;
  --bg: #ffffff; --bg-subtle: #f8fafc; --accent: #6366f1;
  --tok-kw: #a626a4; --tok-str: #4d7c0f; --tok-com: #6b7280;
  --tok-fn: #4f46e5; --tok-num: #b45309; --tok-typ: #0e7490;
}
@media (prefers-color-scheme: dark) {
  :root {
    --fg: #e6edf6; --fg-muted: #94a3b8; --border: #1e293b;
    --bg: #0f172a; --bg-subtle: #1b2536; --accent: #818cf8;
    --tok-kw: #c678dd; --tok-str: #98c379; --tok-com: #7f8ea3;
    --tok-fn: #61afef; --tok-num: #d19a66; --tok-typ: #56b6c2;
  }
}
* { box-sizing: border-box; }
body {
  margin: 0; padding: 32px 20px 72px;
  background: var(--bg); color: var(--fg);
  font: 15px/1.7 -apple-system, BlinkMacSystemFont, "Segoe UI", "PingFang SC",
    "Hiragino Sans GB", "Microsoft YaHei", sans-serif;
}
header, main { max-width: 760px; margin: 0 auto; }
h1 { font-size: 22px; margin: 0 0 10px; letter-spacing: -.01em; }
h2 { font-size: 15px; color: var(--fg-muted); font-weight: 600; margin: 0 0 10px; }
h3, h4, h5, h6 { font-size: 15px; margin: 22px 0 10px; }
.meta { list-style: none; margin: 0 0 8px; padding: 0; color: var(--fg-muted); font-size: 13px; }
.turn { border-top: 1px solid var(--border); padding: 22px 0; }
.turn-user { color: var(--fg); }
p { margin: 0 0 14px; }
ul, ol { margin: 0 0 14px; padding-left: 22px; }
li { margin-bottom: 5px; }
a { color: var(--accent); }
table { width: 100%; border-collapse: collapse; margin: 0 0 14px; font-size: 13.5px; }
th, td { border: 1px solid var(--border); padding: 6px 10px; text-align: left; vertical-align: top; }
th { background: var(--bg-subtle); font-weight: 600; }
blockquote { margin: 0 0 14px; padding-left: 12px; border-left: 2px solid var(--border); color: var(--fg-muted); }
hr { margin: 18px 0; border: 0; border-top: 1px solid var(--border); }
img { max-width: 100%; }
code { font-family: ui-monospace, SFMono-Regular, Menlo, Consolas, monospace; font-size: .875em; }
:not(pre) > code { padding: 1.5px 5px; border-radius: 5px; background: var(--bg-subtle); border: 1px solid var(--border); }
.md-code { margin: 0 0 14px; border: 1px solid var(--border); border-radius: 10px; overflow: hidden; }
.md-code-head { display: flex; align-items: center; padding: 6px 10px; border-bottom: 1px solid var(--border); background: var(--bg-subtle); }
.md-code-lang { font-size: 12px; color: var(--fg-muted); }
.md-code pre { margin: 0; padding: 11px 12px; overflow: auto; font-size: 12.5px; line-height: 1.7; }
.hljs-comment, .hljs-quote { color: var(--tok-com); font-style: italic; }
.hljs-keyword, .hljs-selector-tag, .hljs-literal, .hljs-section, .hljs-doctag, .hljs-name, .hljs-meta { color: var(--tok-kw); }
.hljs-string, .hljs-regexp, .hljs-addition, .hljs-attribute, .hljs-meta .hljs-string { color: var(--tok-str); }
.hljs-title, .hljs-title.function_, .hljs-function .hljs-title { color: var(--tok-fn); }
.hljs-number, .hljs-symbol, .hljs-bullet, .hljs-link { color: var(--tok-num); }
.hljs-type, .hljs-class .hljs-title, .hljs-built_in, .hljs-builtin-name, .hljs-params, .hljs-attr, .hljs-property, .hljs-variable, .hljs-template-variable { color: var(--tok-typ); }

/* 打印（导出 PDF）：A4、方块不被跨页切断、强制浅色以便纸面阅读 */
@media print {
  @page { size: A4; margin: 16mm 14mm; }
  :root {
    color-scheme: light;
    --fg: #1e293b; --fg-muted: #64748b; --border: #cbd5e1;
    --bg: #ffffff; --bg-subtle: #f8fafc; --accent: #4f46e5;
    --tok-kw: #a626a4; --tok-str: #3f6212; --tok-com: #6b7280;
    --tok-fn: #4f46e5; --tok-num: #b45309; --tok-typ: #0e7490;
  }
  * { -webkit-print-color-adjust: exact; print-color-adjust: exact; }
  body { padding: 0; }
  header, main { max-width: none; }
  .turn, .md-code, table, blockquote, pre, li, img { break-inside: avoid; }
  .turn { padding: 16px 0; }
  h1, h2, h3, h4 { break-after: avoid; }
  a { color: inherit; text-decoration: none; }
}
`.trim()

/** 导出为 HTML：内容与 Markdown 版一致，正文走同一套 Markdown 渲染 */
export function turnsToHtml(turns: Turn[], meta: ExportMeta, exportedAt = new Date()): string {
  const title = meta.title || '会话'
  const sections = pickParts(turns)
    .map((part) => {
      const kind = part.speaker === '我' ? 'user' : 'agent'
      const body = renderMarkdownForExport(part.markdown)
      return `<section class="turn turn-${kind}">\n<h2>${part.speaker}</h2>\n${body}</section>`
    })
    .join('\n')

  return `<!doctype html>
<html lang="zh-CN">
<head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1">
<title>${escapeHtml(exportBase(title, meta.sessionId, exportedAt))}</title>
<style>
${HTML_STYLE}
</style>
</head>
<body>
<header>
<h1>${escapeHtml(title)}</h1>
<ul class="meta">
${metaLines(meta, exportedAt)
  .map((line) => `<li>${escapeHtml(line.replace(/^- /, ''))}</li>`)
  .join('\n')}
</ul>
</header>
<main>
${sections}
</main>
</body>
</html>
`
}

/** 文件名主体：标题 + 时间戳，去掉 Windows 不允许的字符 */
function exportBase(title: string, sessionId: string | null, date: Date): string {
  const base = (title || sessionId || 'session')
    .replace(/[\\/:*?"<>|]+/g, '_')
    .replace(/\s+/g, ' ')
    .trim()
    .replace(/[. ]+$/, '')
  const clipped = base.slice(0, 40) || 'session'
  const stamp =
    `${date.getFullYear()}${pad(date.getMonth() + 1)}${pad(date.getDate())}` +
    `-${pad(date.getHours())}${pad(date.getMinutes())}`
  return `${clipped}-${stamp}`
}

export function exportFilename(
  title: string,
  sessionId: string | null,
  options: { date?: Date; extension?: string } = {},
): string {
  const date = options.date ?? new Date()
  return `${exportBase(title, sessionId, date)}.${options.extension ?? 'md'}`
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
