export interface TextSegment {
  type: 'text'
  text: string
}

export interface SkillSegment {
  type: 'skill'
  name: string
}

export interface DsSegment {
  type: 'ds'
  id: string
}

export type MentionSegment = TextSegment | SkillSegment | DsSegment

const MENTION_RE = /\[\[skill:([\w-]+)\]\]|\[\[ds:([\w-]+)\]\]/g

/** 解析正文里的 [[skill:<name>]] / [[ds:<id>]]，其余为普通文本 */
export function parseMentions(text: string): MentionSegment[] {
  const segments: MentionSegment[] = []
  let pos = 0
  for (const match of text.matchAll(MENTION_RE)) {
    const index = match.index ?? 0
    if (index > pos) segments.push({ type: 'text', text: text.slice(pos, index) })
    if (match[1] !== undefined) {
      segments.push({ type: 'skill', name: match[1] })
    } else if (match[2] !== undefined) {
      segments.push({ type: 'ds', id: match[2] })
    }
    pos = index + match[0].length
  }
  if (pos < text.length) segments.push({ type: 'text', text: text.slice(pos) })
  return segments
}

/** 分段序列化回纯文本 */
export function stringifySegments(segments: MentionSegment[]): string {
  return segments
    .map((segment) => {
      if (segment.type === 'skill') return `[[skill:${segment.name}]]`
      if (segment.type === 'ds') return `[[ds:${segment.id}]]`
      return segment.text
    })
    .join('')
}

/** chip 的 data-m 值（skill:<name> / ds:<id>） */
export function mentionDataValue(kind: 'skill' | 'ds', value: string): string {
  return `${kind}:${value}`
}

/** 由 data-m 反解出分段 */
export function segmentFromDataValue(data: string): MentionSegment {
  if (data.startsWith('skill:')) return { type: 'skill', name: data.slice(6) }
  return { type: 'ds', id: data.slice(3) }
}

/** 技能/数据源标签与下拉列表共用的小图标（继承 currentColor） */
export function mentionIconSvg(kind: 'skill' | 'ds'): string {
  const body =
    kind === 'skill'
      ? '<path d="M13 2 3 14h7l-1 8 10-12h-7l1-8z"/>'
      : '<rect x="3" y="3" width="18" height="7" rx="1"/><rect x="3" y="14" width="18" height="7" rx="1"/>'
  return `<svg class="mention-icon" viewBox="0 0 24 24" width="12" height="12" fill="currentColor" aria-hidden="true">${body}</svg>`
}