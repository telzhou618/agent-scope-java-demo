export type ToolStatus = 'running' | 'ok' | 'error' | 'none'

export interface ThinkingBlockModel {
  kind: 'thinking'
  text: string
  /** 思考耗时（秒），实时流结束时回填 */
  seconds?: number
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

export interface UserTurn {
  kind: 'user'
  id: string
  text: string
  timestamp: string
}

export interface AssistantTurn {
  kind: 'assistant'
  id: string
  blocks: AssistantBlock[]
  streaming: boolean
  error: string
  feedback: 'up' | 'down' | null
}

export type Turn = UserTurn | AssistantTurn
