/**
 * 个人中心的用量演示数据。
 * 用「按小时」的确定性伪随机生成：同一小时永远得到同一个值，
 * 所以切换时间维度时数字是稳定的，不会每次渲染都跳。
 */

export type RangeKey =
  | 'today'
  | 'yesterday'
  | 'last7'
  | 'last30'
  | 'thisMonth'
  | 'lastMonth'
  | 'custom'

export interface RangeOption {
  key: RangeKey
  label: string
}

export const RANGE_OPTIONS: RangeOption[] = [
  { key: 'today', label: '今天' },
  { key: 'yesterday', label: '昨天' },
  { key: 'last7', label: '近 7 天' },
  { key: 'last30', label: '近 30 天' },
  { key: 'thisMonth', label: '本月' },
  { key: 'lastMonth', label: '上月' },
  { key: 'custom', label: '自定义' },
]

export interface UsageBucket {
  label: string
  tokens: number
  requests: number
  cost: number
}

export interface UsageSummary {
  granularity: 'hour' | 'day'
  buckets: UsageBucket[]
  tokens: number
  requests: number
  cost: number
  /** 供标题展示的区间描述，如「9 月 1 – 30 日 · 按天」 */
  description: string
}

/** 一小时内 token 量的节律（0–23 点），模拟白天高、深夜低 */
const HOUR_WEIGHTS = [
  0.06, 0.04, 0.03, 0.03, 0.04, 0.1, 0.35, 0.7, 1.0, 1.05, 1.1, 0.95, 0.6, 0.95, 1.15, 1.1, 0.95,
  0.7, 0.5, 0.55, 0.7, 0.8, 0.5, 0.25,
]

const BASE_TOKENS_PER_HOUR = 103_000
/** 单次请求平均消耗的 token，用来把请求次数与 token 量对上 */
const TOKENS_PER_REQUEST = 61_000
/** 综合单价（元 / 百万 token），用来把费用与 token 量对上 */
const COST_PER_MILLION = 0.154
/** 自定义区间最长跨度（天），防止一次生成过多柱子 */
const MAX_CUSTOM_DAYS = 180

/** 稳定伪随机：同一个 seed 永远同一个值 */
function noise(seed: number): number {
  const value = Math.sin(seed * 12.9898) * 43758.5453
  return value - Math.floor(value)
}

function hourTokens(date: Date): number {
  const seed = Math.floor(date.getTime() / 3_600_000)
  const weight = HOUR_WEIGHTS[date.getHours()]
  return Math.round(BASE_TOKENS_PER_HOUR * weight * (0.75 + noise(seed) * 0.5))
}

function dayTokens(date: Date): number {
  let total = 0
  for (let hour = 0; hour < 24; hour += 1) {
    total += hourTokens(new Date(date.getFullYear(), date.getMonth(), date.getDate(), hour))
  }
  return total
}

function toBucket(label: string, tokens: number): UsageBucket {
  return {
    label,
    tokens,
    requests: Math.max(1, Math.round(tokens / TOKENS_PER_REQUEST)),
    cost: (tokens / 1_000_000) * COST_PER_MILLION,
  }
}

const startOfDay = (date: Date) => new Date(date.getFullYear(), date.getMonth(), date.getDate())

const addDays = (date: Date, days: number) =>
  new Date(date.getFullYear(), date.getMonth(), date.getDate() + days)

const DAY = 86_400_000

/** 解析 <input type="date"> 的值，按本地时区构造，避免 UTC 偏移 */
export function parseDateInput(value: string): Date | null {
  const matched = /^(\d{4})-(\d{2})-(\d{2})$/.exec(value)
  if (!matched) return null
  return new Date(Number(matched[1]), Number(matched[2]) - 1, Number(matched[3]))
}

export function toDateInput(date: Date): string {
  const month = String(date.getMonth() + 1).padStart(2, '0')
  const day = String(date.getDate()).padStart(2, '0')
  return `${date.getFullYear()}-${month}-${day}`
}

/** 自定义区间的默认值：近 7 天 */
export const defaultCustomRange = () => ({
  start: toDateInput(addDays(startOfDay(new Date()), -6)),
  end: toDateInput(startOfDay(new Date())),
})

function resolveBounds(key: RangeKey, custom: { start: string; end: string }) {
  const today = startOfDay(new Date())

  switch (key) {
    case 'today':
      return { start: today, end: today, granularity: 'hour' as const }
    case 'yesterday': {
      const yesterday = addDays(today, -1)
      return { start: yesterday, end: yesterday, granularity: 'hour' as const }
    }
    case 'last7':
      return { start: addDays(today, -6), end: today, granularity: 'day' as const }
    case 'last30':
      return { start: addDays(today, -29), end: today, granularity: 'day' as const }
    case 'thisMonth':
      return {
        start: new Date(today.getFullYear(), today.getMonth(), 1),
        end: today,
        granularity: 'day' as const,
      }
    case 'lastMonth':
      return {
        start: new Date(today.getFullYear(), today.getMonth() - 1, 1),
        end: new Date(today.getFullYear(), today.getMonth(), 0),
        granularity: 'day' as const,
      }
    default: {
      let start = parseDateInput(custom.start) ?? addDays(today, -6)
      let end = parseDateInput(custom.end) ?? today
      if (end < start) [start, end] = [end, start]
      if (end.getTime() - start.getTime() > MAX_CUSTOM_DAYS * DAY) {
        start = new Date(end.getTime() - (MAX_CUSTOM_DAYS - 1) * DAY)
      }
      const single = start.getTime() === end.getTime()
      return { start, end, granularity: single ? ('hour' as const) : ('day' as const) }
    }
  }
}

const formatDay = (date: Date) => `${date.getMonth() + 1} 月 ${date.getDate()} 日`

/** 按时间维度汇总用量；今天/昨天按小时，其余按天 */
export function resolveRange(
  key: RangeKey,
  custom: { start: string; end: string } = defaultCustomRange(),
): UsageSummary {
  const { start, end, granularity } = resolveBounds(key, custom)
  const buckets: UsageBucket[] = []
  const now = new Date()

  if (granularity === 'hour') {
    // 今天只统计到当前小时（当天截至现在）
    const lastHour = key === 'today' ? now.getHours() : 23
    for (let hour = 0; hour <= lastHour; hour += 1) {
      const at = new Date(start.getFullYear(), start.getMonth(), start.getDate(), hour)
      buckets.push(toBucket(`${String(hour).padStart(2, '0')}:00`, hourTokens(at)))
    }
  } else {
    for (let time = start.getTime(); time <= end.getTime(); time += DAY) {
      const date = new Date(time)
      buckets.push(toBucket(`${date.getMonth() + 1}/${date.getDate()}`, dayTokens(date)))
    }
  }

  const totals = buckets.reduce(
    (acc, bucket) => ({
      tokens: acc.tokens + bucket.tokens,
      requests: acc.requests + bucket.requests,
      cost: acc.cost + bucket.cost,
    }),
    { tokens: 0, requests: 0, cost: 0 },
  )

  const range =
    start.getTime() === end.getTime()
      ? formatDay(start)
      : `${formatDay(start)} – ${formatDay(end)}`

  return {
    granularity,
    buckets,
    ...totals,
    description: `${range} · ${granularity === 'hour' ? '按小时' : '按天'}`,
  }
}
