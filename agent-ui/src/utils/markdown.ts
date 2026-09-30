import DOMPurify from 'dompurify'
import hljs from 'highlight.js/lib/common'
import { Marked, type Tokens } from 'marked'
import { iconPaths } from './icons'

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
