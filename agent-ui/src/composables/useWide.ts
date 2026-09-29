import { ref } from 'vue'

const WIDE_KEY = 'agent-ui:wide'

function readWide(): boolean {
  try {
    return localStorage.getItem(WIDE_KEY) === 'on'
  } catch {
    return false
  }
}

const wide = ref(readWide())

function apply() {
  document.body.dataset.wide = wide.value ? 'on' : 'off'
}

apply()

export function useWide() {
  const label = () => (wide.value ? '退出宽屏模式' : '切换宽屏模式')

  function toggle() {
    wide.value = !wide.value
    try {
      localStorage.setItem(WIDE_KEY, wide.value ? 'on' : 'off')
    } catch {
      /* 忽略持久化失败 */
    }
    apply()
  }

  return { wide, label, toggle }
}
