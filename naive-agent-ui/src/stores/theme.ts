import { computed, ref, watch } from 'vue'
import { defineStore } from 'pinia'
import { darkTheme, lightTheme, type GlobalTheme } from 'naive-ui'

const THEME_KEY = 'naive-agent-ui:theme'

type ThemeName = 'light' | 'dark'

function readStored(): ThemeName {
  try {
    return localStorage.getItem(THEME_KEY) === 'dark' ? 'dark' : 'light'
  } catch {
    return 'light'
  }
}

/** 明暗主题：NConfigProvider 的 theme 由这里驱动，localStorage 持久化 */
export const useThemeStore = defineStore('theme', () => {
  const name = ref<ThemeName>(readStored())

  const theme = computed<GlobalTheme>(() => (name.value === 'dark' ? darkTheme : lightTheme))
  const isDark = computed(() => name.value === 'dark')

  function setName(value: ThemeName) {
    name.value = value
    try {
      localStorage.setItem(THEME_KEY, value)
    } catch {
      /* 忽略持久化失败 */
    }
  }

  function toggle() {
    setName(name.value === 'dark' ? 'light' : 'dark')
  }

  /*
   * 把当前主题同步到 <html data-theme>：markdown / 代码高亮 / echarts 等
   * 不走 Naive 组件体系的内容靠它做明暗适配，系统 color-scheme 一并跟随
   */
  watch(
    name,
    (value) => {
      document.documentElement.dataset.theme = value
      document.documentElement.style.colorScheme = value
    },
    { immediate: true },
  )

  return { name, theme, isDark, setName, toggle }
})
