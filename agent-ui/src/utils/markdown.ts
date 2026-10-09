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
import { iconPaths } from './icons'

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

const copyIcon = `<svg width="13" height="13" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">${iconPaths.copy}</svg>`

/** 代码块渲染成原型的 .code 卡片；导出到文件时不需要复制按钮 */
function codeBlock(token: Tokens.Code, withCopyButton: boolean): string {
  const language = (token.lang ?? '').trim().split(/\s+/)[0]
  const body =
    language && hljs.getLanguage(language)
      ? hljs.highlight(token.text, { language, ignoreIllegals: true }).value
      : escapeHtml(token.text)
  const copy = withCopyButton
    ? `<button class="act-btn" type="button" data-copy>${copyIcon}<span>复制</span></button>`
    : ''
  return (
    '<div class="code">' +
    '<div class="code-head">' +
    `<span class="code-lang">${escapeHtml(language || 'text')}</span>` +
    copy +
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
