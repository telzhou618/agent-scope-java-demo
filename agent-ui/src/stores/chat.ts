import { computed, reactive } from 'vue'
import { ApiError } from '../api/http'
import { createSession, delSession, getFeedbacks, getMessages, getSessions, interrupt, pinSession, sendFeedback } from '../api/agent'
import type { AgentSession } from '../api/types'
import { chatStream, type StreamHandle } from '../sse/chatStream'
import { toTurns } from '../utils/history'
import { createLiveTurn, type LiveTurn } from '../utils/stream'
import type { AssistantTurn, Turn, UserAttachment, UserTurn } from '../utils/model'
import { useAgents } from './agents'

const agents = useAgents()

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

/**
 * 流式中途被切走的会话现场：流不随切换中断，在后台继续接收并累积进自己的 turns，
 * 切回时直接恢复（比 getMessages 历史新）；流结束后从缓存清掉，再以历史接口为准。
 */
interface BackgroundSession {
  turns: Turn[]
  handle: StreamHandle
  liveTurn: LiveTurn
}

const background = new Map<string, BackgroundSession>()

/** 新会话首条消息发出前，附件上传就需要 sessionId：预生成一个，发送时沿用 */
let pendingSessionId: string | null = null

const newId = (prefix: string) => `${prefix}-${Date.now()}-${seq++}`

const isAssistant = (turn: Turn): turn is AssistantTurn => turn.kind === 'assistant'

/** 用户消息附件的本地 blob 预览：turns 被整体丢弃时统一释放 */
function revokeTurnBlobs(turns: Turn[]) {
  for (const turn of turns) {
    if (turn.kind !== 'user' || !turn.attachments) continue
    for (const attachment of turn.attachments) {
      if (attachment.url?.startsWith('blob:')) URL.revokeObjectURL(attachment.url)
    }
  }
}

/**
 * 离开当前会话前收尾：正在流式的会话连同流句柄暂存进后台缓存（不 abort）；
 * 未在流式的 turns 直接丢弃并释放 blob 预览（重新打开走历史接口）。
 */
function stashCurrent() {
  const sessionId = state.currentSessionId
  if (sessionId && state.streaming && handle && liveTurn) {
    background.set(sessionId, { turns: state.turns, handle, liveTurn })
  } else {
    revokeTurnBlobs(state.turns)
  }
  handle = null
  liveTurn = null
  state.streaming = false
  pendingSessionId = null
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
    // 刷新直链打开会话的场景：列表晚于 openSession 到达，这里补上选择器的会话跟随
    if (state.currentSessionId) {
      agents.followSession(state.sessions.find((item) => item.sessionId === state.currentSessionId)?.agentName)
    }
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
  stashCurrent()
  cancelTitleRetry()
  state.currentSessionId = sessionId
  state.view = 'chat'
  closeDrawerOnNarrow()
  state.messagesError = ''
  // 顶栏选择器跟随会话的 Agent（只改当前选择，不回写偏好）
  agents.followSession(state.sessions.find((item) => item.sessionId === sessionId)?.agentName)

  // 流式中途切走的会话：恢复后台暂存的现场（比历史接口新），流继续实时渲染
  const cached = background.get(sessionId)
  if (cached) {
    background.delete(sessionId)
    state.turns = cached.turns
    state.streaming = true
    handle = cached.handle
    liveTurn = cached.liveTurn
    state.loadingMessages = false
    return
  }

  state.turns = []
  state.loadingMessages = true
  try {
    // 历史消息与反馈映射并行拉取
    const [messages, feedbacks] = await Promise.all([
      getMessages(sessionId),
      getFeedbacks(sessionId).catch(() => ({}) as Record<string, 'up' | 'down'>),
    ])
    if (state.currentSessionId !== sessionId) return
    state.turns = toTurns(messages ?? [])
    for (const turn of state.turns) {
      if (turn.kind === 'assistant' && turn.messageId && feedbacks[turn.messageId]) {
        turn.feedback = feedbacks[turn.messageId]
      }
    }
  } catch (error) {
    state.messagesError = toMessage(error)
  } finally {
    state.loadingMessages = false
  }
}

function newChat() {
  stashCurrent()
  cancelTitleRetry()
  state.currentSessionId = null
  state.turns = []
  state.messagesError = ''
  state.view = 'chat'
  closeDrawerOnNarrow()
  // 新对话回到持久化偏好，不沿用上一个老会话的 Agent
  agents.resetToPreference()
}

async function removeSession(sessionId: string) {
  cancelTitleRetry()
  try {
    await delSession(sessionId)
  } catch (error) {
    state.sessionsError = toMessage(error)
    return
  }
  // 后台还在流式的会话一并断开，释放暂存与 blob 预览
  const cached = background.get(sessionId)
  if (cached) {
    cached.handle.abort()
    revokeTurnBlobs(cached.turns)
    background.delete(sessionId)
  }
  state.sessions = state.sessions.filter((item) => item.sessionId !== sessionId)
  if (state.currentSessionId === sessionId) {
    handle?.abort()
    handle = null
    liveTurn = null
    state.streaming = false
    state.currentSessionId = null
    revokeTurnBlobs(state.turns)
    state.turns = []
  }
}

/**
 * 流收尾：回合在自己的 turns 数组上收尾（切走后在后台也照跑）。
 * 模块级的 handle/liveTurn/streaming 只在该会话仍是当前会话时复位；
 * 后台会话流结束则丢弃暂存（历史接口已是最新），并释放其中的 blob 预览。
 */
function finishStream(sessionId: string, turns: Turn[], live: LiveTurn) {
  live.finish()
  if (live.turn.blocks.length === 0 && !live.turn.error) {
    const index = turns.indexOf(live.turn)
    if (index >= 0) turns.splice(index, 1)
  }
  if (state.currentSessionId === sessionId && liveTurn === live) {
    liveTurn = null
    handle = null
    state.streaming = false
    return
  }
  const cached = background.get(sessionId)
  if (cached && cached.liveTurn === live) {
    revokeTurnBlobs(cached.turns)
    background.delete(sessionId)
  }
}

/** 当前会话 id；新会话还没有时预生成一个 pending id（附件上传、首条消息共用） */
function ensureSessionId(): string {
  if (state.currentSessionId) return state.currentSessionId
  if (!pendingSessionId) pendingSessionId = crypto.randomUUID()
  return pendingSessionId
}

async function send(text: string, attachments?: UserAttachment[]) {
  const message = text.trim()
  if ((!message && !attachments?.length) || state.streaming) return

  const sessionId = state.currentSessionId ?? pendingSessionId ?? crypto.randomUUID()
  const isNew = !state.currentSessionId
  const agentName = agents.state.current

  // 新建会话：先调创建会话接口（后端写入占位标题并异步生成正式标题），侧栏立即显示「新会话」
  if (isNew) {
    cancelTitleRetry()
    try {
      await createSession({ sessionId, message: message || '请查看我发送的文件', agentName })
    } catch (error) {
      state.messagesError = toMessage(error)
      return
    }
    state.sessions.unshift({ userId: '', sessionId, summary: TITLE_PLACEHOLDER, timestamp: nowTimestamp(), agentName })
    pendingSessionId = null
  }

  state.currentSessionId = sessionId
  const requestId = crypto.randomUUID()
  state.messagesError = ''
  // 闭包持有当前会话的 turns：流式中途切走后，事件仍累积进这个数组
  const turns = state.turns
  const userTurn: UserTurn = { kind: 'user', id: newId('user'), text: message, timestamp: '' }
  if (attachments?.length) userTurn.attachments = attachments.map((item) => ({ ...item }))
  turns.push(userTurn)

  const live = createLiveTurn(newId('live'))
  turns.push(live.turn)
  state.streaming = true
  liveTurn = live

  const stream = chatStream(
    {
      message,
      sessionId,
      agentName,
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
  // 先建好流再更新路由：句柄同步就位，切换会话暂存后台现场时不会有残缺窗口
  handle = stream

  // 新建会话：地址补上 sessionId（需求 12）。动态导入避免 store -> router 循环依赖
  if (isNew) {
    const { default: router } = await import('../router')
    await router.replace(`/chat/${sessionId}`)
  }

  await stream.done
  finishStream(sessionId, turns, live)

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
  const pending = sessionId ? interrupt(sessionId, agents.state.current).catch(() => undefined) : null
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

/** 反馈落库：先乐观更新本地，接口失败则回滚 */
async function setFeedback(turn: AssistantTurn, feedback: 'up' | 'down' | null) {
  const sessionId = state.currentSessionId
  const messageId = turn.messageId
  if (!sessionId || !messageId) return
  const prev = turn.feedback
  turn.feedback = feedback
  try {
    await sendFeedback({ sessionId, messageId, feedback })
  } catch (error) {
    turn.feedback = prev
    state.messagesError = toMessage(error)
  }
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

/** 退出登录/401：断开所有流（含后台暂存），清空全部会话状态与 blob 预览 */
function reset() {
  cancelTitleRetry()
  agents.reset()
  handle?.abort()
  handle = null
  liveTurn = null
  for (const cached of background.values()) {
    cached.handle.abort()
    revokeTurnBlobs(cached.turns)
  }
  background.clear()
  revokeTurnBlobs(state.turns)
  pendingSessionId = null
  state.view = 'chat'
  state.sessions = []
  state.currentSessionId = null
  state.turns = []
  state.streaming = false
  state.loadingSessions = false
  state.loadingMessages = false
  state.sessionsError = ''
  state.messagesError = ''
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
    ensureSessionId,
    reset,
    setView,
    setSidebarOpen,
    toggleSidebar,
    closeDrawerOnNarrow,
  }
}
