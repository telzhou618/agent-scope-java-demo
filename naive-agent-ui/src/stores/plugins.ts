import { reactive } from 'vue'
import { defineStore } from 'pinia'
import { getPlugins } from '../api/agent'
import type { PluginInfo } from '../api/types'

export const usePluginsStore = defineStore('plugins', () => {
  const state = reactive<{ plugins: PluginInfo[]; loaded: boolean }>({
    plugins: [],
    loaded: false,
  })

  async function loadPlugins(force = false) {
    if (state.loaded && !force) return
    try {
      state.plugins = (await getPlugins()) ?? []
      state.loaded = true
    } catch {
      /* 保留空列表，界面按无插件渲染 */
    }
  }

  function byId(id: string) {
    return state.plugins.find((p) => p.id === id)
  }

  function reset() {
    state.plugins = []
    state.loaded = false
  }

  return { state, loadPlugins, byId, reset }
})