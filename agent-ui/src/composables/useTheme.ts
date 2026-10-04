import { computed, ref } from 'vue'

export type ThemeChoice = 'classic' | 'obsidian' | 'amber'

const THEME_KEY = 'agent-ui:theme'

interface ThemeMeta {
  name: string
  /** 皮肤选择菜单里的色板 */
  swatch: string
}

export const THEME_LIST: Record<ThemeChoice, ThemeMeta> = {
  classic: { name: '经典蓝', swatch: '#2563eb' },
  amber: { name: '琥珀暖阳', swatch: '#d97706' },
  obsidian: { name: '曜石黑', swatch: '#1e293b' },
}

function readTheme(): ThemeChoice {
  try {
    const value = localStorage.getItem(THEME_KEY)
    if (value === 'obsidian' || value === 'amber' || value === 'classic') return value
  } catch {
    /* 隐私模式下读取失败，按默认皮肤处理 */
  }
  return 'classic'
}

const choice = ref<ThemeChoice>(readTheme())

function apply() {
  document.documentElement.dataset.theme = choice.value
}

apply()

export function useTheme() {
  const meta = computed(() => THEME_LIST[choice.value])

  function set(theme: ThemeChoice) {
    if (theme === choice.value) return
    choice.value = theme
    try {
      localStorage.setItem(THEME_KEY, theme)
    } catch {
      /* 忽略持久化失败 */
    }
    apply()
  }

  return { choice, meta, set }
}
