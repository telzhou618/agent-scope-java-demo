import { computed, ref } from 'vue'
import { useChat } from '../stores/chat'

/**
 * 侧栏的展开/收起。两种布局下含义不同，但对用户是同一个动作：
 * - 窄屏（≤900px）：侧栏是抽屉，收起 = 关抽屉
 * - 宽屏（>900px）：收起 = 整列折叠，顶栏汉堡按钮负责展开
 */
const SIDEBAR_KEY = 'agent-ui:sidebar'
const media = window.matchMedia('(max-width: 900px)')

const narrow = ref(media.matches)
media.addEventListener('change', (event) => {
  narrow.value = event.matches
})

function readCollapsed(): boolean {
  try {
    return localStorage.getItem(SIDEBAR_KEY) === 'collapsed'
  } catch {
    return false
  }
}

const collapsed = ref(readCollapsed())

function apply() {
  document.body.dataset.sidebar = collapsed.value ? 'collapsed' : 'open'
}

apply()

export function useSidebar() {
  const { state } = useChat()

  /** 实时读，不依赖媒体查询 change 事件是否派发（模拟视口、窗口缩放都可靠） */
  function isNarrow(): boolean {
    const current = media.matches
    if (narrow.value !== current) narrow.value = current
    return current
  }

  function persist(value: boolean) {
    collapsed.value = value
    try {
      localStorage.setItem(SIDEBAR_KEY, value ? 'collapsed' : 'open')
    } catch {
      /* 隐私模式下无法持久化，忽略 */
    }
    apply()
  }

  /** 顶栏汉堡按钮控制的对象是否处于展开状态 */
  const visible = computed(() => (narrow.value ? state.drawerOpen : !collapsed.value))

  return {
    collapsed,
    narrow,
    isNarrow,
    visible,
    /** 侧栏头部「收起」按钮 */
    collapse() {
      if (isNarrow()) state.drawerOpen = false
      else persist(true)
    },
    /** 顶栏汉堡按钮 */
    open() {
      if (isNarrow()) state.drawerOpen = true
      else persist(false)
    },
    toggle() {
      if (isNarrow()) state.drawerOpen = !state.drawerOpen
      else persist(!collapsed.value)
    },
  }
}
