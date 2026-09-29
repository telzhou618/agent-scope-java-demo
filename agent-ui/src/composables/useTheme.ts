import { computed, ref } from 'vue'
import type { IconName } from '../utils/icons'

export type ThemeChoice = 'light' | 'dark' | 'auto'

const THEME_KEY = 'agent-ui:theme'
const THEME_ORDER: ThemeChoice[] = ['light', 'dark', 'auto']

const THEME_META: Record<ThemeChoice, { icon: IconName; name: string; next: string }> = {
  light: { icon: 'sun', name: '浅色', next: '深色' },
  dark: { icon: 'moon', name: '深色', next: '跟随系统' },
  auto: { icon: 'monitor', name: '跟随系统', next: '浅色' },
}

const darkMedia = window.matchMedia('(prefers-color-scheme: dark)')

function readTheme(): ThemeChoice {
  try {
    const value = localStorage.getItem(THEME_KEY)
    if (value === 'light' || value === 'dark' || value === 'auto') return value
  } catch {
    /* 隐私模式下读取失败，交给系统 */
  }
  return 'auto'
}

const choice = ref<ThemeChoice>(readTheme())

function apply() {
  const dark = choice.value === 'dark' || (choice.value === 'auto' && darkMedia.matches)
  document.documentElement.dataset.theme = dark ? 'dark' : 'light'
}

darkMedia.addEventListener('change', () => {
  if (choice.value === 'auto') apply()
})

apply()

export function useTheme() {
  const meta = computed(() => THEME_META[choice.value])
  const label = computed(() => `主题：${meta.value.name}。点击切换到${meta.value.next}`)

  function cycle() {
    const index = THEME_ORDER.indexOf(choice.value)
    choice.value = THEME_ORDER[(index + 1) % THEME_ORDER.length]
    try {
      localStorage.setItem(THEME_KEY, choice.value)
    } catch {
      /* 忽略持久化失败 */
    }
    apply()
  }

  return { choice, meta, label, cycle }
}
