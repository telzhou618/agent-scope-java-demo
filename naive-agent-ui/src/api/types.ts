/** 后端统一响应包装 */
export interface Result<T> {
  code: number
  msg: string
  data: T
  reason?: string
  traceId?: string
}

/** MyBatis-Plus Page 的分页结构 */
export interface PageResult<T> {
  records: T[]
  total: number
  current: number
  size: number
}

export interface UserInfo {
  id: number
  username: string
  nickname: string
  email: string
  avatar: string
  /** 是否管理员（管理中心入口按此显示） */
  isAdmin: boolean
  /** 当前用户可用的 Agent 名称列表；管理员为全量 */
  agents: string[]
  /** 权限码列表（如 schedule）；管理员自动拥有全部 */
  permissions: string[]
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
  /** 会话使用的 Agent（老数据可能没有） */
  agentName?: string
}

/** Agent 元信息：能力开关平铺，为 false 时前端隐藏对应入口 */
export interface AgentInfo {
  name: string
  displayName: string
  /** 头像 URL（data URI 或 http 链接） */
  avatar: string
  description: string
  model: string
  thinking: boolean
  tools: boolean
  mcp: boolean
  skills: boolean
  attachments: boolean
  /** 是否支持定时任务 */
  schedule: boolean
  defaultAgent: boolean
}

/** 本月 token 限额：每个 Agent 的限额与消耗 */
export interface AgentQuotaItem {
  name: string
  displayName: string
  /** 0 = 不限 */
  quotaTokens: number
  usedTokens: number
}

export interface MonthlyQuotaSummary {
  /** 如 2026-10 */
  month: string
  totalTokens: number
  agents: AgentQuotaItem[]
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

/** 我的文件 */
export interface UserFileItem {
  id: number
  fileName: string
  ext: string
  size: number
  createTime: string
  /** OSS 签名下载地址（列表返回时新生成） */
  downloadUrl: string
}

/** 已安装技能（/skill:<name> 快捷指令） */
export interface SkillInfo {
  name: string
  description: string
}

export interface ChatRequest {
  message: string
  sessionId: string
  agentName: string
  requestId?: string
  /** 技能名（/skill:<name> 快捷指令选中的技能），独立传参 */
  skill?: string
  /** 工具确认结果：响应 require_confirm 事件时携带 */
  confirmResults?: ConfirmResultItem[]
  attachments?: ChatAttachment[]
}

/** 消息中心条目（列表不含 content，详情含） */
export interface MessageItem {
  id: number
  /** report 定时报告 / system 系统通知 */
  type: 'report' | 'system'
  /** 发送方 Agent 名；系统消息为空串 */
  sender: string
  title: string
  content?: string
  isRead: boolean
  runStatus: 'success' | 'failed'
  createTime: string
}

/** 定时任务 */
export interface ScheduleTaskItem {
  id: number
  agentName: string
  /** 任务标题（老数据可能为空串） */
  title: string
  description: string
  cron: string
  /** 1 启用 / 0 停用 */
  status: number
  lastRunAt?: string
  nextRunAt?: string
  createTime: string
}

export interface ToolCallInfo {
  toolCallId: string
  toolName?: string
  toolParams?: string
  toolResults?: string
  /** tool_end 时携带：success/error/interrupted/denied */
  state?: string
  /** require_confirm 时携带：结构化入参（确认后原样回传） */
  toolInput?: Record<string, unknown>
  /** require_confirm 时携带：ToolUseBlock 原始 content（确认后原样回传，框架校验需要） */
  toolContent?: string
}

/** 工具确认结果（响应 require_confirm 事件） */
export interface ConfirmResultItem {
  toolCallId: string
  toolName: string
  toolInput?: Record<string, unknown>
  toolContent?: string
  confirmed: boolean
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
  /** 轻量提示（如达到最大执行步数） */
  | 'notice'
  /** 工具调用待用户确认（agent.confirm-tools 名单内） */
  | 'require_confirm'

export interface AgentSseEvent {
  type: SseEventType
  content?: string
  role?: string
  /** agent_result 时携带：最终 Assistant 消息 ID（反馈锚点） */
  messageId?: string
  toolCall?: ToolCallInfo
  /** require_confirm 时携带：待确认的工具调用列表 */
  toolCalls?: ToolCallInfo[]
}

export const isTextBlock = (block: ContentBlock): block is TextBlock =>
  block.type === 'text' && typeof (block as TextBlock).text === 'string'

export const isThinkingBlock = (block: ContentBlock): block is ThinkingBlock =>
  block.type === 'thinking' && typeof (block as ThinkingBlock).thinking === 'string'

export const isToolUseBlock = (block: ContentBlock): block is ToolUseBlock =>
  block.type === 'tool_use' && typeof (block as ToolUseBlock).id === 'string'

export const isToolResultBlock = (block: ContentBlock): block is ToolResultBlock =>
  block.type === 'tool_result' && typeof (block as ToolResultBlock).id === 'string'
