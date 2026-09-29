import type { AgentSseEvent, ChatRequest } from '../api/types'
import { API_BASE } from '../api/http'

export interface StreamHandlers {
  onEvent: (event: AgentSseEvent) => void
  onError: (message: string) => void
}

export interface StreamHandle {
  abort: () => void
  done: Promise<void>
}

const FRAME_SEPARATOR = /\r?\n\r?\n/

const toMessage = (error: unknown) => (error instanceof Error ? error.message : String(error))

function dispatch(frame: string, handlers: StreamHandlers) {
  let eventName = 'message'
  const dataLines: string[] = []

  for (const line of frame.split(/\r?\n/)) {
    if (!line || line.startsWith(':')) continue
    const colon = line.indexOf(':')
    const field = colon === -1 ? line : line.slice(0, colon)
    let value = colon === -1 ? '' : line.slice(colon + 1)
    if (value.startsWith(' ')) value = value.slice(1)
    if (field === 'event') eventName = value
    else if (field === 'data') dataLines.push(value)
  }

  if (!dataLines.length) return
  const data = dataLines.join('\n')
  if (eventName === 'error') {
    handlers.onError(data)
    return
  }
  try {
    handlers.onEvent(JSON.parse(data) as AgentSseEvent)
  } catch {
    /* 无法解析的帧直接忽略 */
  }
}

/** POST 方式的 SSE：EventSource 只支持 GET，所以自己读流 */
export function chatStream(request: ChatRequest, handlers: StreamHandlers): StreamHandle {
  const controller = new AbortController()

  const done = (async () => {
    let response: Response
    try {
      response = await fetch(`${API_BASE}/chat_sse`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json', Accept: 'text/event-stream' },
        body: JSON.stringify(request),
        signal: controller.signal,
      })
    } catch (error) {
      if (!controller.signal.aborted) handlers.onError(toMessage(error))
      return
    }

    if (!response.ok || !response.body) {
      handlers.onError(`连接失败（HTTP ${response.status}）`)
      return
    }

    const reader = response.body.getReader()
    const decoder = new TextDecoder()
    let buffer = ''

    try {
      for (;;) {
        const { value, done: finished } = await reader.read()
        if (finished) break
        buffer += decoder.decode(value, { stream: true })

        for (;;) {
          const match = FRAME_SEPARATOR.exec(buffer)
          if (!match || match.index === undefined) break
          const frame = buffer.slice(0, match.index)
          buffer = buffer.slice(match.index + match[0].length)
          dispatch(frame, handlers)
        }
      }
      if (buffer.trim()) dispatch(buffer, handlers)
    } catch (error) {
      if (!controller.signal.aborted) handlers.onError(toMessage(error))
    }
  })()

  return { abort: () => controller.abort(), done }
}
