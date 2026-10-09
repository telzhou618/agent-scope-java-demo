import { computed, reactive, watch } from 'vue'
import { getAgents } from '../api/agent'
import type { AgentInfo } from '../api/types'
import { useAgentPreference } from '../composables/useAgentPreference'

/** name -> 简称：侧栏会话标签、列表加载失败时的兜底显示 */
export const AGENT_SHORT_LABELS: Record<string, string> = {
  flash: 'Flash',
  plus: 'Plus',
  max: 'Max',
}

export const agentShortLabel = (name: string) => AGENT_SHORT_LABELS[name] ?? name

interface AgentsState {
  agents: AgentInfo[]
  loaded: boolean
  /**
   * 当前生效的 Agent：默认跟随持久化偏好，打开老会话时跟随该会话（不回写偏好），
   * 用户在选择器里主动切换时回写偏好并由调用方开新会话。
   */
  current: string
}

const preference = useAgentPreference()

const state = reactive<AgentsState>({
  agents: [],
  loaded: false,
  current: preference.choice.value,
})

// 偏好变化（登录切换用户、主动切换 Agent）时当前选择跟着走
watch(preference.choice, (name) => {
  state.current = name
})

const currentAgent = computed(() => state.agents.find((item) => item.name === state.current))

export function useAgents() {
  /** 拉取 Agent 列表；失败时选择器退化为按 name 显示，能力开关按「全部可用」处理 */
  async function loadAgents(force = false) {
    if (state.loaded && !force) return
    try {
      state.agents = (await getAgents()) ?? []
      state.loaded = true
      // 当前选择不在列表里（偏好过期等）时回退到后端默认 Agent
      if (state.agents.length && !state.agents.some((item) => item.name === state.current)) {
        const fallback = state.agents.find((item) => item.defaultAgent) ?? state.agents[0]
        state.current = fallback.name
      }
    } catch {
      /* 保留空列表，界面按兜底逻辑渲染 */
    }
  }

  /** 用户在选择器里主动切换：生效并回写持久化偏好 */
  function selectAgent(name: string) {
    state.current = name
    preference.set(name)
  }

  /** 打开老会话时跟随会话的 Agent：只改当前选择，不动持久化偏好 */
  function followSession(agentName?: string) {
    if (agentName) state.current = agentName
  }

  /** 新对话回到持久化偏好（而不是沿用上一个老会话的 Agent） */
  function resetToPreference() {
    state.current = preference.choice.value
  }

  /** 退出登录/401：清掉列表，选择回到偏好 */
  function reset() {
    state.agents = []
    state.loaded = false
    state.current = preference.choice.value
  }

  return { state, currentAgent, loadAgents, selectAgent, followSession, resetToPreference, reset }
}
