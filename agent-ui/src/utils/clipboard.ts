/** 复制文本：优先用剪贴板 API，非安全上下文回退到 execCommand */
export async function copyText(text: string): Promise<void> {
  if (navigator.clipboard && window.isSecureContext) {
    try {
      await navigator.clipboard.writeText(text)
      return
    } catch {
      /* 落到下面的兜底实现 */
    }
  }
  const area = document.createElement('textarea')
  area.value = text
  area.style.position = 'fixed'
  area.style.opacity = '0'
  document.body.appendChild(area)
  area.select()
  try {
    document.execCommand('copy')
  } catch {
    /* 复制失败时静默处理 */
  }
  area.remove()
}

/** 按下的按钮上的文字短暂变成「已复制」 */
export function flashLabel(button: HTMLElement, text = '已复制', duration = 1400) {
  const label = button.querySelector('span')
  if (!label) return
  const original = label.textContent ?? ''
  label.textContent = text
  window.setTimeout(() => {
    label.textContent = original
  }, duration)
}
