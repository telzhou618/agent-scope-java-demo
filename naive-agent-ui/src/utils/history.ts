import type { ContentBlock, Msg, ToolResultBlock } from '../api/types'
import {
  isTextBlock,
  isThinkingBlock,
  isToolResultBlock,
  isToolUseBlock,
} from '../api/types'
import type { AssistantTurn, ToolBlockModel, Turn, UserAttachment, UserTurn } from './model'
import { fileExt } from './attachments'
import { prettyJson } from './format'

/** 后端存库时附件文本块的标记格式：【附件：文件名】\n<内容>（见 AgentScopeController.attachmentText） */
const ATTACHMENT_MARK = /^【附件：([^\n】]+)】/

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

/** 历史 USER 消息里的图片块（OSS 签名 URL 优先，兼容 base64）→ 气泡附件 */
function imageAttachment(block: ContentBlock, seq: number): UserAttachment | null {
  const source = (block as {
    source?: { url?: string; mime_type?: string; media_type?: string; data?: string }
  }).source
  if (!source) return null
  if (typeof source.url === 'string' && source.url) {
    const mediaType = typeof source.mime_type === 'string' ? source.mime_type : 'image/png'
    const ext = mediaType.replace(/^image\//, '').split(';')[0] || 'png'
    return { name: `图片-${seq}.${ext}`, ext, size: 0, url: source.url }
  }
  const data = typeof source.data === 'string' ? source.data : ''
  if (!data) return null
  const mediaType = typeof source.media_type === 'string' ? source.media_type : 'image/png'
  const ext = mediaType.replace(/^image\//, '').split(';')[0] || 'png'
  return { name: `图片-${seq}.${ext}`, ext, size: 0, url: `data:${mediaType};base64,${data}` }
}

/** 历史消息归一化成回合模型，与实时流共用同一套结构 */
export function toTurns(messages: Msg[]): Turn[] {
  const turns: Turn[] = []
  let current: AssistantTurn | null = null
  let toolsById = new Map<string, ToolBlockModel>()
  let seq = 0

  const openAssistant = (id: string): AssistantTurn => {
    current = { kind: 'assistant', id, messageId: id, blocks: [], streaming: false, error: '', feedback: null }
    toolsById = new Map()
    turns.push(current)
    return current
  }

  for (const message of messages) {
    if (message.role === 'USER' || message.role === 'SYSTEM') {
      const plugin = (message.metadata?.plugin as string | undefined) || undefined
      const pluginArgs = (message.metadata?.pluginArgs as string | undefined) ?? ''
      let text = plugin ? pluginArgs : ''
      const attachments: UserAttachment[] = []
      for (const block of message.content ?? []) {
        if (plugin) {
          // 插件消息：正文是改写后的内部文案，历史只展示原始参数（metadata.pluginArgs）；附件仍还原
          const image = imageAttachment(block, seq)
          if (image) attachments.push(image)
          continue
        }
        if (isTextBlock(block)) {
          // 附件文本块（【附件：文件名】\n内容）还原成文件 chip，与首次发送的展示一致；
          // 大小未存库传 0，chip 自动不显示大小
          const matched = ATTACHMENT_MARK.exec(block.text)
          if (matched) {
            attachments.push({ name: matched[1], ext: fileExt(matched[1]), size: 0 })
          } else {
            text += block.text
          }
          continue
        }
        const image = imageAttachment(block, seq)
        if (image) attachments.push(image)
      }
      if (!text.trim() && attachments.length === 0 && !plugin) continue
      const turn: UserTurn = {
        kind: 'user',
        id: message.id || `user-${seq++}`,
        text,
        timestamp: message.timestamp ?? '',
      }
      if (plugin) turn.plugin = plugin
      if (attachments.length) turn.attachments = attachments.map((item) => ({ ...item }))
      turns.push(turn)
      current = null
      continue
    }

    const turn = current ?? openAssistant(message.id || `assistant-${seq++}`)

    for (const block of message.content ?? []) {
      if (isThinkingBlock(block)) {
        if (!block.thinking.trim()) continue
        turn.blocks.push({ kind: 'thinking', text: block.thinking, open: false, done: true })
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
