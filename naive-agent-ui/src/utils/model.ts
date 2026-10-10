export type ToolStatus = 'running' | 'ok' | 'error' | 'none'

export interface ThinkingBlockModel {
  kind: 'thinking'
  text: string
  /** 思考耗时（秒），实时流结束时回填 */
  seconds?: number
  /** 思考是否已结束；历史消息无耗时回填，靠它与「正在思考」区分 */
  done?: boolean
  open: boolean
}

export interface ToolBlockModel {
  kind: 'tool'
  id: string
  name: string
  args: string
  output: string
  status: ToolStatus
  open: boolean
}

export interface TextBlockModel {
  kind: 'text'
  markdown: string
}

export type AssistantBlock = ThinkingBlockModel | ToolBlockModel | TextBlockModel

export interface UserAttachment {
  id?: string
  name: string
  ext: string
  size: number
  url?: string
}

export interface UserTurn {
  kind: 'user'
  id: string
  text: string
  timestamp: string
  attachments?: UserAttachment[]
}

export interface AssistantTurn {
  kind: 'assistant'
  id: string
  /** 最终 Assistant 消息 ID（反馈锚点；实时回合在 agent_result 时回填，历史回合取消息自身 id） */
  messageId?: string
  blocks: AssistantBlock[]
  streaming: boolean
  error: string
  feedback: 'up' | 'down' | null
}

export type Turn = UserTurn | AssistantTurn
