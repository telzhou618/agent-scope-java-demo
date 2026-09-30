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
const THEME_TRANSITION_CLASS = 'theme-transition'
let transitionTimer: number | null = null

/**
 * 应用主题。animate=true 时给 <html> 挂一个短暂类，让颜色在 260ms 内过渡；
 * 只在切换瞬间挂，避免常驻 `*` 过渡拖慢首屏与滚动。
 */
function apply(animate = false) {
  if (animate) {
    const root = document.documentElement
    root.classList.add(THEME_TRANSITION_CLASS)
    if (transitionTimer !== null) window.clearTimeout(transitionTimer)
    transitionTimer = window.setTimeout(() => {
      root.classList.remove(THEME_TRANSITION_CLASS)
      transitionTimer = null
    }, 260)
  }
  const dark = choice.value === 'dark' || (choice.value === 'auto' && darkMedia.matches)
  document.documentElement.dataset.theme = dark ? 'dark' : 'light'
}

// 「跟随系统」时，系统主题变化同样带过渡
darkMedia.addEventListener('change', () => {
  if (choice.value === 'auto') apply(true)
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
    apply(true)
  }

  return { choice, meta, label, cycle }
}
