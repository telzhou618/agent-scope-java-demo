import { reactive } from 'vue'
import type { AgentSseEvent } from '../api/types'
import type { AssistantTurn, TextBlockModel, ThinkingBlockModel, ToolBlockModel } from './model'
import { prettyJson } from './format'

export interface LiveTurn {
  turn: AssistantTurn
  handle: (event: AgentSseEvent) => void
  fail: (message: string) => void
  finish: () => void
}

/**
 * 把 SSE 事件累积成一个 assistant 回合。
 * turn 与其中每个块都用 reactive 包装：组件是通过响应式代理读取它们的，
 * 只有经由代理写入才会触发重渲染。
 */
export function createLiveTurn(id: string): LiveTurn {
  const turn = reactive<AssistantTurn>({
    kind: 'assistant',
    id,
    blocks: [],
    streaming: true,
    error: '',
    feedback: null,
  })

  const tools = new Map<string, ToolBlockModel>()
  /**
   * 当前正在累积的块。只有紧跟其后的同类型增量才追加进去；
   * 一旦来了别的类型（或流结束），这一段就收尾，之后再收到该类型时另起一个条目。
   */
  let run:
    | { kind: 'thinking'; block: ThinkingBlockModel; startedAt: number }
    | { kind: 'text'; block: TextBlockModel }
    | null = null
  let closed = false

  function endRun() {
    const current = run
    run = null
    if (current?.kind !== 'thinking') return
    current.block.done = true
    if (current.block.open) {
      current.block.open = false
      current.block.seconds = Math.max(1, Math.round((Date.now() - current.startedAt) / 1000))
    }
  }

  function appendThinking(delta: string) {
    const current = run
    if (current?.kind === 'thinking') {
      current.block.text += delta
      return
    }
    endRun()
    const block = reactive<ThinkingBlockModel>({ kind: 'thinking', text: '', open: true })
    turn.blocks.push(block)
    run = { kind: 'thinking', block, startedAt: Date.now() }
    block.text += delta
  }

  function appendText(delta: string) {
    const current = run
    if (current?.kind === 'text') {
      current.block.markdown += delta
      return
    }
    endRun()
    const block = reactive<TextBlockModel>({ kind: 'text', markdown: '' })
    turn.blocks.push(block)
    run = { kind: 'text', block }
    block.markdown += delta
  }

  function addTool(id: string, name: string, args: string): ToolBlockModel {
    const tool = reactive<ToolBlockModel>({
      kind: 'tool',
      id,
      name,
      args,
      output: '',
      status: 'running',
      open: false,
    })
    tools.set(id, tool)
    turn.blocks.push(tool)
    return tool
  }

  function handle(event: AgentSseEvent) {
    if (closed) return

    switch (event.type) {
      case 'thinking': {
        if (!event.content) return
        appendThinking(event.content)
        break
      }
      case 'text_block': {
        if (!event.content) return
        appendText(event.content)
        break
      }
      case 'tool_call': {
        const call = event.toolCall
        if (!call) return
        endRun()
        const existing = tools.get(call.toolCallId)
        if (existing) {
          existing.name = call.toolName ?? existing.name
          existing.args = prettyJson(call.toolParams) || existing.args
          return
        }
        addTool(call.toolCallId, call.toolName ?? 'tool', prettyJson(call.toolParams))
        break
      }
      case 'tool_result': {
        const call = event.toolCall
        if (!call) return
        endRun()
        const tool = tools.get(call.toolCallId) ?? addTool(call.toolCallId, call.toolName ?? 'tool', '')
        tool.output += call.toolResults ?? ''
        break
      }
      case 'tool_end': {
        const call = event.toolCall
        if (!call) return
        const tool = tools.get(call.toolCallId) ?? addTool(call.toolCallId, call.toolName ?? 'tool', '')
        // 工具结果落定即收尾：success 为成功，其余（error/interrupted/denied）为失败
        tool.status = call.state === 'success' ? 'ok' : 'error'
        break
      }
      case 'agent_result': {
        // 最终消息 ID：反馈功能的持久化锚点
        if (event.messageId) turn.messageId = event.messageId
        break
      }
      case 'agent_end': {
        finish()
        break
      }
      case 'notice': {
        if (event.content) turn.notice = event.content
        break
      }
      default:
        break
    }
  }

  function fail(message: string) {
    if (closed) return
    endRun()
    for (const tool of tools.values()) {
      if (tool.status === 'running') tool.status = tool.output ? 'ok' : 'error'
    }
    turn.error = message
    turn.streaming = false
    closed = true
  }

  function finish() {
    if (closed) return
    endRun()
    for (const tool of tools.values()) {
      if (tool.status === 'running') tool.status = 'ok'
    }
    turn.streaming = false
    closed = true
  }

  return { turn, handle, fail, finish }
}
