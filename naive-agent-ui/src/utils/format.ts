/** 解析后端时间戳：yyyy-MM-dd HH:mm:ss.SSS */
export function parseTimestamp(value?: string): Date | null {
  if (!value) return null
  const matched = /^(\d{4})-(\d{2})-(\d{2})[ T](\d{2}):(\d{2}):(\d{2})(?:\.(\d{1,3}))?/.exec(value)
  if (!matched) return null
  const [, year, month, day, hour, minute, second, milli] = matched
  return new Date(
    Number(year),
    Number(month) - 1,
    Number(day),
    Number(hour),
    Number(minute),
    Number(second),
    milli ? Number(milli.padEnd(3, '0')) : 0,
  )
}

const startOfDay = (date: Date) => new Date(date.getFullYear(), date.getMonth(), date.getDate())

/** 会话列表里的相对时间 */
export function relativeTime(value?: string): string {
  const date = parseTimestamp(value)
  if (!date) return ''
  const diff = Date.now() - date.getTime()
  if (diff < 60_000) return '刚刚'
  if (diff < 3_600_000) return `${Math.floor(diff / 60_000)} 分钟前`

  const days = Math.round((startOfDay(new Date()).getTime() - startOfDay(date).getTime()) / 86_400_000)
  if (days <= 0) return `${Math.floor(diff / 3_600_000)} 小时前`
  if (days === 1) return '昨天'
  if (days < 7) return `${days} 天前`
  return `${date.getMonth() + 1}-${String(date.getDate()).padStart(2, '0')}`
}

/** 侧栏分组标签 */
export function groupLabel(value?: string): string {
  const date = parseTimestamp(value)
  if (!date) return '更早'
  const days = Math.round((startOfDay(new Date()).getTime() - startOfDay(date).getTime()) / 86_400_000)
  if (days <= 0) return '今天'
  if (days === 1) return '昨天'
  return '更早'
}

/** 工具参数：尽量格式化成多行 JSON */
export function prettyJson(raw?: string): string {
  if (!raw) return ''
  try {
    return JSON.stringify(JSON.parse(raw), null, 2)
  } catch {
    return raw
  }
}

export const formatInt = (value: number) => value.toLocaleString('zh-CN')

export const formatMoney = (value: number) => `¥${value.toFixed(2)}`

export function formatTokens(value: number): string {
  if (value >= 1e6) return `${(value / 1e6).toFixed(2).replace(/\.?0+$/, '')}M`
  if (value >= 1e3) return `${Math.round(value / 1e3)}K`
  return String(value)
}
