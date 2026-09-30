import { onBeforeUnmount, onMounted, ref, type ComponentPublicInstance } from 'vue'

/**
 * 弹层的开合：点外部或按 Esc 关闭。
 * `bindRoot` 要绑在「触发按钮 + 弹层」的共同父节点上（`:ref="bindRoot"`），
 * 否则点按钮会先被外层点击判定为「点到了外部」而立刻关掉。
 */
export function useDismissableMenu() {
  const open = ref(false)
  const root = ref<HTMLElement | null>(null)

  function bindRoot(element: Element | ComponentPublicInstance | null) {
    root.value = (element as HTMLElement | null) ?? null
  }

  function close() {
    open.value = false
  }

  function toggle() {
    open.value = !open.value
  }

  function onDocumentClick(event: MouseEvent) {
    if (!open.value) return
    const target = event.target as Node | null
    if (target && root.value?.contains(target)) return
    close()
  }

  function onKeydown(event: KeyboardEvent) {
    if (event.key === 'Escape') close()
  }

  onMounted(() => {
    document.addEventListener('click', onDocumentClick)
    document.addEventListener('keydown', onKeydown)
  })

  onBeforeUnmount(() => {
    document.removeEventListener('click', onDocumentClick)
    document.removeEventListener('keydown', onKeydown)
  })

  return { open, bindRoot, close, toggle }
}
