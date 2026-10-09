import { ref } from 'vue'

const KEY_PREFIX = 'naive-agent-ui:agent'
const DEFAULT_AGENT = 'flash'

/** 当前绑定的用户；未登录或用户信息未就绪时用公共 key 兜底 */
const userId = ref<string | null>(null)

function storageKey() {
  return userId.value ? `${KEY_PREFIX}:${userId.value}` : KEY_PREFIX
}

function readStored(): string {
  try {
    return localStorage.getItem(storageKey()) || DEFAULT_AGENT
  } catch {
    /* 隐私模式下读取失败，按默认 Agent 处理 */
    return DEFAULT_AGENT
  }
}

/** 各用户独立的 Agent 偏好（localStorage 持久化） */
const choice = ref<string>(readStored())

export function useAgentPreference() {
  /** 登录/拉取当前用户成功后绑定用户，并按该用户重新加载偏好；退出时传 null 回到兜底 key */
  function bindUser(id: string | null) {
    userId.value = id
    choice.value = readStored()
  }

  function set(name: string) {
    if (name === choice.value) return
    choice.value = name
    try {
      localStorage.setItem(storageKey(), name)
    } catch {
      /* 忽略持久化失败 */
    }
  }

  return { choice, bindUser, set }
}
