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
  let thinking: ThinkingBlockModel | null = null
  let thinkingStartedAt = 0
  let textBlock: TextBlockModel | null = null
  let closed = false

  function ensureThinking(): ThinkingBlockModel {
    if (!thinking) {
      thinking = reactive<ThinkingBlockModel>({ kind: 'thinking', text: '', open: true })
      thinkingStartedAt = Date.now()
      turn.blocks.push(thinking)
    }
    return thinking
  }

  function freezeThinking() {
    if (thinking && thinking.open) {
      thinking.open = false
      thinking.seconds = Math.max(1, Math.round((Date.now() - thinkingStartedAt) / 1000))
    }
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
        ensureThinking().text += event.content
        break
      }
      case 'text_block': {
        if (!event.content) return
        freezeThinking()
        if (!textBlock) {
          textBlock = reactive<TextBlockModel>({ kind: 'text', markdown: '' })
          turn.blocks.push(textBlock)
        }
        textBlock.markdown += event.content
        break
      }
      case 'tool_call': {
        const call = event.toolCall
        if (!call) return
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
        const tool = tools.get(call.toolCallId) ?? addTool(call.toolCallId, call.toolName ?? 'tool', '')
        tool.output += call.toolResults ?? ''
        break
      }
      case 'agent_end': {
        finish()
        break
      }
      default:
        break
    }
  }

  function fail(message: string) {
    if (closed) return
    freezeThinking()
    for (const tool of tools.values()) {
      if (tool.status === 'running') tool.status = tool.output ? 'ok' : 'error'
    }
    turn.error = message
    turn.streaming = false
    closed = true
  }

  function finish() {
    if (closed) return
    freezeThinking()
    for (const tool of tools.values()) {
      if (tool.status === 'running') tool.status = 'ok'
    }
    turn.streaming = false
    closed = true
  }

  return { turn, handle, fail, finish }
}
