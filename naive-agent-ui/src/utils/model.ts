export type ToolStatus = 'running' | 'ok' | 'error' | 'none' | 'confirming' | 'denied'

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
  /** 人工确认状态：pending 等待用户选择（confirm-tools 名单内的工具） */
  confirm?: 'pending' | 'confirmed' | 'rejected'
  open: boolean
}

export interface TextBlockModel {
  kind: 'text'
  markdown: string
}

/** 工具调用待确认卡片（agent.confirm-tools 名单内的工具触发） */
/** 工具调用待确认卡片（agent.confirm-tools 名单内的工具触发）：逐项审批后底部统一提交 */
export interface ConfirmBlockModel {
  kind: 'confirm'
  toolCalls: {
    id: string
    name: string
    input?: Record<string, unknown>
    content?: string
    /** 用户逐项审批的勾选结果；提交前为 undefined */
    decision?: 'approved' | 'rejected'
  }[]
  /** pending 等待逐项审批；done 已提交；历史会话里无此块，仅实时出现 */
  status: 'pending' | 'done'
}

export type AssistantBlock = ThinkingBlockModel | ToolBlockModel | TextBlockModel | ConfirmBlockModel

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
  /** 轻量提示（如达到最大执行步数的收尾提醒），与 error 区分，不阻塞反馈 */
  notice?: string
  feedback: 'up' | 'down' | null
}

export type Turn = UserTurn | AssistantTurn
