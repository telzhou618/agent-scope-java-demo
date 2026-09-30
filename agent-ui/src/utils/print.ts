/**
 * 打印一份完整的 HTML 文档：用隐藏 iframe 装载，交给系统打印对话框，
 * 用户在对话框里选「另存为 PDF」。不开新标签页，也不会被弹窗拦截。
 */
export function printHtmlDocument(html: string) {
  const frame = document.createElement('iframe')
  frame.setAttribute('aria-hidden', 'true')
  // 0 尺寸 + 固定在角落：不能用 display:none，否则文档不渲染、打印会是空白
  frame.style.position = 'fixed'
  frame.style.right = '0'
  frame.style.bottom = '0'
  frame.style.width = '0'
  frame.style.height = '0'
  frame.style.border = '0'

  let cleaned = false
  const cleanup = () => {
    if (cleaned) return
    cleaned = true
    frame.remove()
  }

  frame.addEventListener('load', () => {
    const view = frame.contentWindow
    if (!view) {
      cleanup()
      return
    }
    // 打印对话框关闭后触发 afterprint；部分浏览器不触发，用超时兜底
    view.addEventListener('afterprint', cleanup)
    window.setTimeout(cleanup, 60_000)
    view.focus()
    view.print()
  })

  frame.srcdoc = html
  document.body.appendChild(frame)
}
