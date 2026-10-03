/** 后端统一响应包装 */
export interface Result<T> {
  code: number
  msg: string
  data: T
  reason?: string
  traceId?: string
}

export interface AgentSession {
  userId: string
  sessionId: string
  /** 会话首条用户消息 */
  summary?: string
  /** yyyy-MM-dd HH:mm:ss.SSS */
  timestamp: string
  /** 是否置顶 */
  pinned?: boolean
}

export type MessageRole = 'USER' | 'ASSISTANT' | 'TOOL' | 'SYSTEM'

export interface TextBlock {
  type: 'text'
  text: string
}

export interface ThinkingBlock {
  type: 'thinking'
  thinking: string
  metadata?: unknown
}

export interface ToolUseBlock {
  type: 'tool_use'
  id: string
  name: string
  input?: Record<string, unknown>
  /** 工具参数的 JSON 字符串 */
  content?: string
  state?: string
}

export interface ToolResultBlock {
  type: 'tool_result'
  id: string
  name: string
  /** HTTP 返回是内容块数组，历史文件里是字符串 */
  output: ContentBlock[] | string
  state?: string
}

export interface UnknownBlock {
  type: string
  [key: string]: unknown
}

export type ContentBlock = TextBlock | ThinkingBlock | ToolUseBlock | ToolResultBlock | UnknownBlock

export interface Msg {
  id: string
  name: string
  role: MessageRole
  content: ContentBlock[]
  metadata?: Record<string, unknown>
  timestamp?: string
  usage?: unknown
}

export interface ChatAttachment {
  id: string
  name: string
  ext: string
  size: number
}

/** 已安装技能（/skill:<name> 快捷指令） */
export interface SkillInfo {
  name: string
  description: string
}

export interface ChatRequest {
  message: string
  sessionId: string
  requestId?: string
  attachments?: ChatAttachment[]
}

export interface ToolCallInfo {
  toolCallId: string
  toolName?: string
  toolParams?: string
  toolResults?: string
  /** tool_end 时携带：success/error/interrupted/denied */
  state?: string
}

export type SseEventType =
  | 'agent_start'
  | 'thinking'
  | 'text_block'
  | 'tool_call'
  | 'tool_result'
  | 'tool_end'
  | 'agent_result'
  | 'agent_end'

export interface AgentSseEvent {
  type: SseEventType
  content?: string
  role?: string
  /** agent_result 时携带：最终 Assistant 消息 ID（反馈锚点） */
  messageId?: string
  toolCall?: ToolCallInfo
}

export const isTextBlock = (block: ContentBlock): block is TextBlock =>
  block.type === 'text' && typeof (block as TextBlock).text === 'string'

export const isThinkingBlock = (block: ContentBlock): block is ThinkingBlock =>
  block.type === 'thinking' && typeof (block as ThinkingBlock).thinking === 'string'

export const isToolUseBlock = (block: ContentBlock): block is ToolUseBlock =>
  block.type === 'tool_use' && typeof (block as ToolUseBlock).id === 'string'

export const isToolResultBlock = (block: ContentBlock): block is ToolResultBlock =>
  block.type === 'tool_result' && typeof (block as ToolResultBlock).id === 'string'
