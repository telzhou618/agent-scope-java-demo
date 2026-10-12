import DOMPurify from 'dompurify'
import hljs from 'highlight.js/lib/core'
import bash from 'highlight.js/lib/languages/bash'
import css from 'highlight.js/lib/languages/css'
import diff from 'highlight.js/lib/languages/diff'
import java from 'highlight.js/lib/languages/java'
import javascript from 'highlight.js/lib/languages/javascript'
import json from 'highlight.js/lib/languages/json'
import markdownLang from 'highlight.js/lib/languages/markdown'
import python from 'highlight.js/lib/languages/python'
import sql from 'highlight.js/lib/languages/sql'
import typescript from 'highlight.js/lib/languages/typescript'
import xml from 'highlight.js/lib/languages/xml'
import yaml from 'highlight.js/lib/languages/yaml'
import { Marked, type Tokens } from 'marked'

/*
 * 只注册用得上的语言：highlight.js 的 common 包会带 36 种（约 300KB 源码），
 * 这里 12 种约 110KB。别名由各语言自己声明（js / ts / sh / html / py / yml 都能识别），
 * 未注册的语言按纯文本输出。需要新增时在这里加一行。
 */
const LANGUAGES = {
  bash,
  css,
  diff,
  java,
  javascript,
  json,
  markdown: markdownLang,
  python,
  sql,
  typescript,
  xml,
  yaml,
}

for (const [name, language] of Object.entries(LANGUAGES)) {
  hljs.registerLanguage(name, language)
}

export const escapeHtml = (value: string) =>
  value.replace(/[&<>"']/g, (char) => {
    switch (char) {
      case '&':
        return '&amp;'
      case '<':
        return '&lt;'
      case '>':
        return '&gt;'
      case '"':
        return '&quot;'
      default:
        return '&#39;'
    }
  })

const copyIcon = `<svg width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><rect x="9" y="9" width="12" height="12" rx="2"/><path d="M5 15V5a2 2 0 0 1 2-2h10"/></svg>`
const playIcon = `<svg width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><circle cx="12" cy="12" r="9"/><polygon points="10 8 16 12 10 16 10 8"/></svg>`
const downloadIcon = `<svg width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><path d="M12 3v12"/><path d="m7 11 5 5 5-5"/><path d="M5 20h14"/></svg>`

/** 围栏代码块是否已闭合（流式输出中未闭合 = 代码还没输出完，不提供运行/下载） */
function isClosedFence(raw: string): boolean {
  return /(`{3,}|~{3,})\s*$/.test(raw)
}

/** 代码块渲染成 .md-code 卡片（样式见 styles/markdown.css）；导出到文件时不需要操作按钮 */
function codeBlock(token: Tokens.Code, withCopyButton: boolean): string {
  const language = (token.lang ?? '').trim().split(/\s+/)[0]
  const body =
    language && hljs.getLanguage(language)
      ? hljs.highlight(token.text, { language, ignoreIllegals: true }).value
      : escapeHtml(token.text)
  // 完整的 html 代码块才给运行/下载按钮；点击处理在 MarkdownBlock 的点击委托里
  const runnable =
    withCopyButton && (language === 'html' || language === 'xml') && isClosedFence(token.raw)
  const copy = withCopyButton
    ? `<button class="md-code-copy" type="button" data-copy>${copyIcon}<span>复制</span></button>`
    : ''
  const run = runnable
    ? `<button class="md-code-copy" type="button" data-download-html title="下载为 .html 文件">${downloadIcon}<span>下载</span></button>` +
      `<button class="md-code-copy" type="button" data-run title="在新标签页运行">${playIcon}<span>运行</span></button>`
    : ''
  // 操作按钮成组靠右，顺序：复制 / 下载 / 运行
  const actions = withCopyButton ? `<span class="md-code-actions">${copy}${run}</span>` : ''
  return (
    '<div class="md-code">' +
    '<div class="md-code-head">' +
    `<span class="md-code-lang">${escapeHtml(language || 'text')}</span>` +
    actions +
    '</div>' +
    `<pre><code>${body}</code></pre>` +
    '</div>'
  )
}

function createRenderer(withCopyButton: boolean) {
  return {
    code(token: Tokens.Code): string {
      // echarts 围栏不经过这里：界面内由 MarkdownBlock 切给 ChartBlock，导出前由 withChartImages 替换成图片
      return codeBlock(token, withCopyButton)
    },
  }
}

const appMarked = new Marked({ gfm: true, breaks: true, renderer: createRenderer(true) })
const exportMarked = new Marked({ gfm: true, breaks: true, renderer: createRenderer(false) })

/** 界面内渲染：代码块带复制按钮 */
export function renderMarkdown(markdown: string): string {
  return DOMPurify.sanitize(appMarked.parse(markdown) as string)
}

/** 导出文件用：与界面同一套渲染，去掉复制按钮 */
export function renderMarkdownForExport(markdown: string): string {
  return DOMPurify.sanitize(exportMarked.parse(markdown) as string)
}
