import type { ContentBlock, Msg, ToolResultBlock } from '../api/types'
import {
  isTextBlock,
  isThinkingBlock,
  isToolResultBlock,
  isToolUseBlock,
} from '../api/types'
import type { AssistantTurn, ToolBlockModel, Turn } from './model'
import { prettyJson } from './format'

/** tool_result.output 在 HTTP 返回是内容块数组，兼容字符串形式 */
export function toolResultText(output: ToolResultBlock['output']): string {
  if (typeof output === 'string') return output
  if (!Array.isArray(output)) return ''
  return output
    .filter((block): block is ContentBlock => !!block && typeof block === 'object')
    .map((block) => (isTextBlock(block) ? block.text : ''))
    .join('')
}

const toolArgs = (content?: string, input?: Record<string, unknown>): string => {
  if (content) return prettyJson(content)
  if (input && Object.keys(input).length) return prettyJson(JSON.stringify(input))
  return ''
}

/** 历史消息归一化成回合模型，与实时流共用同一套结构 */
export function toTurns(messages: Msg[]): Turn[] {
  const turns: Turn[] = []
  let current: AssistantTurn | null = null
  let toolsById = new Map<string, ToolBlockModel>()
  let seq = 0

  const openAssistant = (id: string): AssistantTurn => {
    current = { kind: 'assistant', id, blocks: [], streaming: false, error: '', feedback: null }
    toolsById = new Map()
    turns.push(current)
    return current
  }

  for (const message of messages) {
    if (message.role === 'USER' || message.role === 'SYSTEM') {
      const text = (message.content ?? []).filter(isTextBlock).map((block) => block.text).join('')
      if (!text.trim()) continue
      turns.push({
        kind: 'user',
        id: message.id || `user-${seq++}`,
        text,
        timestamp: message.timestamp ?? '',
      })
      current = null
      continue
    }

    const turn = current ?? openAssistant(message.id || `assistant-${seq++}`)

    for (const block of message.content ?? []) {
      if (isThinkingBlock(block)) {
        if (!block.thinking.trim()) continue
        turn.blocks.push({ kind: 'thinking', text: block.thinking, open: false })
      } else if (isToolUseBlock(block)) {
        const tool: ToolBlockModel = {
          kind: 'tool',
          id: block.id,
          name: block.name,
          args: toolArgs(block.content, block.input),
          output: '',
          status: 'none',
          open: false,
        }
        toolsById.set(block.id, tool)
        turn.blocks.push(tool)
      } else if (isToolResultBlock(block)) {
        const output = toolResultText(block.output)
        const tool = toolsById.get(block.id)
        if (tool) {
          tool.output = output
          tool.status = block.state === 'success' ? 'ok' : 'error'
        } else {
          turn.blocks.push({
            kind: 'tool',
            id: block.id,
            name: block.name,
            args: '',
            output,
            status: block.state === 'success' ? 'ok' : 'error',
            open: false,
          })
        }
      } else if (isTextBlock(block)) {
        if (!block.text.trim()) continue
        turn.blocks.push({ kind: 'text', markdown: block.text })
      }
    }
  }

  return turns.filter((turn) => turn.kind === 'user' || turn.blocks.length > 0)
}
