import { computed, ref } from 'vue'
import type { IconName } from '../utils/icons'

export type ThemeChoice = 'light' | 'dark'

const THEME_KEY = 'agent-ui:theme'

const THEME_META: Record<ThemeChoice, { icon: IconName; name: string; next: string }> = {
  light: { icon: 'sun', name: '浅色', next: '深色' },
  dark: { icon: 'moon', name: '深色', next: '浅色' },
}

function readTheme(): ThemeChoice {
  try {
    // 旧版本的 auto（跟随系统）已下线，统一回退为浅色
    if (localStorage.getItem(THEME_KEY) === 'dark') return 'dark'
  } catch {
    /* 隐私模式下读取失败，按浅色处理 */
  }
  return 'light'
}

const choice = ref<ThemeChoice>(readTheme())

function apply() {
  document.documentElement.dataset.theme = choice.value
}

apply()

export function useTheme() {
  const meta = computed(() => THEME_META[choice.value])
  const label = computed(() => `主题：${meta.value.name}。点击切换到${meta.value.next}`)

  /** 明/暗直接切换，无过渡动画 */
  function cycle() {
    choice.value = choice.value === 'dark' ? 'light' : 'dark'
    try {
      localStorage.setItem(THEME_KEY, choice.value)
    } catch {
      /* 忽略持久化失败 */
    }
    apply()
  }

  return { choice, meta, label, cycle }
}
