import DOMPurify from 'dompurify'
import hljs from 'highlight.js/lib/common'
import { Marked, type Tokens } from 'marked'
import { iconPaths } from '../utils/icons'

const escapeHtml = (value: string) =>
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

const marked = new Marked({
  gfm: true,
  breaks: true,
  renderer: {
    code(token: Tokens.Code): string {
      const language = (token.lang ?? '').trim().split(/\s+/)[0]
      const body =
        language && hljs.getLanguage(language)
          ? hljs.highlight(token.text, { language, ignoreIllegals: true }).value
          : escapeHtml(token.text)
      return (
        '<div class="code">' +
        '<div class="code-head">' +
        `<span class="code-lang">${escapeHtml(language || 'text')}</span>` +
        `<button class="act-btn" type="button" data-copy>${copyIcon}<span>复制</span></button>` +
        '</div>' +
        `<pre><code>${body}</code></pre>` +
        '</div>'
      )
    },
  },
})

export function renderMarkdown(markdown: string): string {
  const raw = marked.parse(markdown) as string
  return DOMPurify.sanitize(raw)
}
