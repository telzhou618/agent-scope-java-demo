import { computed, reactive } from 'vue'
import { ApiError } from '../api/http'
import { delSession, getMessages, getSessions, interrupt } from '../api/agent'
import type { AgentSession } from '../api/types'
import { chatStream, type StreamHandle } from '../sse/chatStream'
import { toTurns } from '../utils/history'
import { createLiveTurn, type LiveTurn } from '../utils/stream'
import type { AssistantTurn, Turn } from '../utils/model'

const SIDEBAR_KEY = 'agent-ui:sidebar'
const narrowMedia = window.matchMedia('(max-width: 900px)')

/**
 * 侧栏是否展开。同一个状态，两种布局含义：
 * 窄屏（≤900px）是抽屉，宽屏（>900px）是整列折叠。
 * 窄屏不沿用宽屏的折叠偏好——抽屉本来就该从收起状态进入。
 */
function readSidebarOpen(): boolean {
  if (narrowMedia.matches) return false
  try {
    return localStorage.getItem(SIDEBAR_KEY) !== 'collapsed'
  } catch {
    return true
  }
}

/** 只有宽屏下的折叠才值得记住 */
function persistSidebar(open: boolean) {
  if (narrowMedia.matches) return
  try {
    localStorage.setItem(SIDEBAR_KEY, open ? 'open' : 'collapsed')
  } catch {
    /* 隐私模式下无法持久化，忽略 */
  }
}

function toMessage(error: unknown): string {
  if (error instanceof ApiError) return error.message
  if (error instanceof TypeError) return '无法连接 Agent 服务，请确认 agent-app（8082）已启动'
  return error instanceof Error ? error.message : String(error)
}

interface ChatState {
  view: 'chat' | 'profile'
  sidebarOpen: boolean
  sessions: AgentSession[]
  currentSessionId: string | null
  turns: Turn[]
  streaming: boolean
  loadingSessions: boolean
  loadingMessages: boolean
  sessionsError: string
  messagesError: string
}

const state = reactive<ChatState>({
  view: 'chat',
  sidebarOpen: readSidebarOpen(),
  sessions: [],
  currentSessionId: null,
  turns: [],
  streaming: false,
  loadingSessions: false,
  loadingMessages: false,
  sessionsError: '',
  messagesError: '',
})

let handle: StreamHandle | null = null
let liveTurn: LiveTurn | null = null
let seq = 0

const newId = (prefix: string) => `${prefix}-${Date.now()}-${seq++}`

const isAssistant = (turn: Turn): turn is AssistantTurn => turn.kind === 'assistant'

function dropEmptyLiveTurn() {
  if (!liveTurn) return
  if (liveTurn.turn.blocks.length === 0 && !liveTurn.turn.error) {
    const index = state.turns.indexOf(liveTurn.turn)
    if (index >= 0) state.turns.splice(index, 1)
  }
}

/** 首条消息生成的 AI 标题：只更新侧栏，不进消息区 */
function updateSessionSummary(sessionId: string, title?: string) {
  const summary = title?.trim()
  if (!summary) return
  const session = state.sessions.find((item) => item.sessionId === sessionId)
  if (session) session.summary = summary
}

/** 会话列表；用户身份由后端从 token 解析 */
async function loadSessions() {
  state.loadingSessions = true
  state.sessionsError = ''
  try {
    state.sessions = (await getSessions()) ?? []
  } catch (error) {
    state.sessionsError = toMessage(error)
  } finally {
    state.loadingSessions = false
  }
}

async function openSession(sessionId: string) {
  if (state.streaming) await stop()
  state.currentSessionId = sessionId
  state.view = 'chat'
  closeDrawerOnNarrow()
  state.messagesError = ''
  state.turns = []
  state.loadingMessages = true
  try {
    const messages = await getMessages(sessionId)
    if (state.currentSessionId !== sessionId) return
    state.turns = toTurns(messages ?? [])
  } catch (error) {
    state.messagesError = toMessage(error)
  } finally {
    state.loadingMessages = false
  }
}

async function newChat() {
  if (state.streaming) await stop()
  state.currentSessionId = null
  state.turns = []
  state.messagesError = ''
  state.view = 'chat'
  closeDrawerOnNarrow()
}

async function removeSession(sessionId: string) {
  try {
    await delSession(sessionId)
  } catch (error) {
    state.sessionsError = toMessage(error)
    return
  }
  state.sessions = state.sessions.filter((item) => item.sessionId !== sessionId)
  if (state.currentSessionId === sessionId) {
    state.currentSessionId = null
    state.turns = []
  }
}

async function send(text: string) {
  const message = text.trim()
  if (!message || state.streaming) return

  const sessionId = state.currentSessionId ?? crypto.randomUUID()
  const isNew = !state.currentSessionId
  state.currentSessionId = sessionId
  const requestId = crypto.randomUUID()
  state.messagesError = ''
  state.turns.push({ kind: 'user', id: newId('user'), text: message, timestamp: '' })

  const live = createLiveTurn(newId('live'))
  state.turns.push(live.turn)
  state.streaming = true
  liveTurn = live

  // 新建会话：地址补上 sessionId（需求 12）。动态导入避免 store -> router 循环依赖
  if (isNew) {
    const { default: router } = await import('../router')
    await router.replace(`/chat/${sessionId}`)
  }

  const stream = chatStream(
    { message, sessionId, requestId },
    {
      onEvent: (event) => {
        if (event.type === 'title') {
          updateSessionSummary(sessionId, event.content)
          return
        }
        live.handle(event)
      },
      onError: (error) => live.fail(error),
    },
  )
  handle = stream

  await stream.done

  const finished = liveTurn
  liveTurn = null
  handle = null
  finished?.finish()
  dropEmptyLiveTurn()
  state.streaming = false

  if (isNew || !state.sessions.some((item) => item.sessionId === sessionId)) {
    void loadSessions()
  }
}

/** 中断：先通知后端停止，再断开本地流 */
async function stop() {
  const sessionId = state.currentSessionId
  const pending = sessionId ? interrupt(sessionId).catch(() => undefined) : null
  handle?.abort()
  if (pending) await pending
}

function regenerate() {
  if (state.streaming) return
  for (let index = state.turns.length - 1; index >= 0; index -= 1) {
    const turn = state.turns[index]
    if (turn.kind === 'user') {
      void send(turn.text)
      return
    }
  }
}

function setFeedback(turn: AssistantTurn, feedback: 'up' | 'down' | null) {
  turn.feedback = feedback
}

function setView(view: 'chat' | 'profile') {
  state.view = view
  closeDrawerOnNarrow()
}

/** 用户显式开合（侧栏头部按钮 / 顶栏汉堡按钮）：宽屏下记住偏好 */
function setSidebarOpen(open: boolean) {
  state.sidebarOpen = open
  persistSidebar(open)
}

function toggleSidebar() {
  setSidebarOpen(!state.sidebarOpen)
}

/** 打开会话/新对话时的自动收起：只对窄屏抽屉生效，宽屏侧栏不该被动折叠 */
function closeDrawerOnNarrow() {
  if (narrowMedia.matches) state.sidebarOpen = false
}

const title = computed(() => {
  if (state.view === 'profile') return '个人主页'
  const session = state.sessions.find((item) => item.sessionId === state.currentSessionId)
  if (session?.summary) return session.summary
  const firstUser = state.turns.find((turn) => turn.kind === 'user')
  return firstUser && firstUser.kind === 'user' ? firstUser.text : '新对话'
})

export function useChat() {
  return {
    state,
    title,
    isAssistant,
    loadSessions,
    openSession,
    newChat,
    removeSession,
    send,
    stop,
    regenerate,
    setFeedback,
    setView,
    setSidebarOpen,
    toggleSidebar,
    closeDrawerOnNarrow,
  }
}
