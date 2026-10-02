import { computed, reactive } from 'vue'
import { ApiError } from '../api/http'
import { createSession, delSession, getMessages, getSessions, interrupt, pinSession } from '../api/agent'
import type { AgentSession } from '../api/types'
import { chatStream, type StreamHandle } from '../sse/chatStream'
import { toTurns } from '../utils/history'
import { createLiveTurn, type LiveTurn } from '../utils/stream'
import type { AssistantTurn, Turn, UserAttachment, UserTurn } from '../utils/model'

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

/** 与后端 Msg.timestamp 一致的本地时间格式：yyyy-MM-dd HH:mm:ss.SSS */
function nowTimestamp(): string {
  const d = new Date()
  const pad = (n: number, w = 2) => String(n).padStart(w, '0')
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())} ${pad(d.getHours())}:${pad(d.getMinutes())}:${pad(d.getSeconds())}.${pad(d.getMilliseconds(), 3)}`
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

const TITLE_PLACEHOLDER = '新会话'
/** 占位标题退避重试间隔：覆盖绝大多数异步标题生成耗时 */
const TITLE_RETRY_DELAYS = [2000, 4000, 8000]
let titleRetryTimer: ReturnType<typeof setTimeout> | null = null

function cancelTitleRetry() {
  if (titleRetryTimer) {
    clearTimeout(titleRetryTimer)
    titleRetryTimer = null
  }
}

/**
 * 首轮回答结束后刷新会话列表。异步标题可能还没生成完（拿到的仍是占位符），
 * 此时按退避间隔重试，直到拿到正式标题、会话被删或重试次数用完（兜底：下次刷新自然更新）。
 */
async function refreshSessionsUntilTitled(sessionId: string, attempt = 0) {
  await loadSessions()
  const session = state.sessions.find((item) => item.sessionId === sessionId)
  if (!session || session.summary !== TITLE_PLACEHOLDER) return
  if (attempt >= TITLE_RETRY_DELAYS.length) return
  titleRetryTimer = setTimeout(() => {
    titleRetryTimer = null
    void refreshSessionsUntilTitled(sessionId, attempt + 1)
  }, TITLE_RETRY_DELAYS[attempt])
}

async function openSession(sessionId: string) {
  if (state.streaming) await stop()
  cancelTitleRetry()
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
  cancelTitleRetry()
  state.currentSessionId = null
  state.turns = []
  state.messagesError = ''
  state.view = 'chat'
  closeDrawerOnNarrow()
}

async function removeSession(sessionId: string) {
  cancelTitleRetry()
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

async function send(text: string, attachments?: UserAttachment[]) {
  const message = text.trim()
  if ((!message && !attachments?.length) || state.streaming) return

  const sessionId = state.currentSessionId ?? crypto.randomUUID()
  const isNew = !state.currentSessionId

  // 新建会话：先调创建会话接口（后端写入占位标题并异步生成正式标题），侧栏立即显示「新会话」
  if (isNew) {
    cancelTitleRetry()
    try {
      await createSession({ sessionId, message: message || '请查看我发送的文件' })
    } catch (error) {
      state.messagesError = toMessage(error)
      return
    }
    state.sessions.unshift({ userId: '', sessionId, summary: TITLE_PLACEHOLDER, timestamp: nowTimestamp() })
  }

  state.currentSessionId = sessionId
  const requestId = crypto.randomUUID()
  state.messagesError = ''
  const userTurn: UserTurn = { kind: 'user', id: newId('user'), text: message, timestamp: '' }
  if (attachments?.length) userTurn.attachments = attachments.map((item) => ({ ...item }))
  state.turns.push(userTurn)

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
    {
      message,
      sessionId,
      requestId,
      attachments: attachments
        ?.filter((item) => item.id)
        .map((item) => ({ id: item.id ?? '', name: item.name, ext: item.ext, size: item.size })),
    },
    {
      onEvent: (event) => live.handle(event),
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

  if (isNew) {
    // 首轮回答结束刷新列表；异步标题可能还没写完，仍是占位符时退避重试
    void refreshSessionsUntilTitled(sessionId)
  } else if (!state.sessions.some((item) => item.sessionId === sessionId)) {
    void loadSessions()
  }
}

/** 置顶/取消置顶：成功后本地更新并按「置顶优先、时间倒序」重排，不整表刷新 */
async function togglePin(sessionId: string) {
  const session = state.sessions.find((item) => item.sessionId === sessionId)
  if (!session) return
  const pinned = !session.pinned
  try {
    await pinSession(sessionId, pinned)
  } catch (error) {
    state.sessionsError = toMessage(error)
    return
  }
  session.pinned = pinned
  state.sessions.sort((a, b) => {
    if (!!a.pinned !== !!b.pinned) return a.pinned ? -1 : 1
    return (b.timestamp ?? '').localeCompare(a.timestamp ?? '')
  })
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
      void send(turn.text, turn.attachments)
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
    togglePin,
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
