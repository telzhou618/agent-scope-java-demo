import { ref } from 'vue'

const KEY = 'naive-agent-ui:wide'

function readStored(): boolean {
  try {
    return localStorage.getItem(KEY) === '1'
  } catch {
    return false
  }
}

/** 聊天内容区宽/窄偏好：false 窄（居中 800px，默认），true 宽（1200px） */
const wide = ref<boolean>(readStored())

export function useWideMode() {
  function toggle() {
    wide.value = !wide.value
    try {
      localStorage.setItem(KEY, wide.value ? '1' : '0')
    } catch {
      /* 忽略持久化失败 */
    }
  }

  return { wide, toggle }
}
