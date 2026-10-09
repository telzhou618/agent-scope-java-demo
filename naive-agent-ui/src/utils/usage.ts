/**
 * 用量统计：区间定义与数据类型。
 * 数据来自后端 /agent/scope/usage/summary（按用户与区间聚合 token 消耗记录）。
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

export interface UsageTotals {
  cost: number
  requests: number
  inputTokens: number
  outputTokens: number
  tokens: number
}

export interface UsageSummary {
  totals: UsageTotals
  buckets: UsageBucket[]
  description: string
}

/** 最近请求记录：/agent/scope/usage/recent 返回的单条记录 */
export interface RecentRequestItem {
  sessionId: string
  requestId: string
  modelName: string
  inputTokens: number
  outputTokens: number
  durationSeconds: number
  cost: number
  /** ISO LocalDateTime，如 2026-09-30T10:24:35 */
  createTime: string
}

const startOfDay = (date: Date) => new Date(date.getFullYear(), date.getMonth(), date.getDate())

const addDays = (date: Date, days: number) =>
  new Date(date.getFullYear(), date.getMonth(), date.getDate() + days)

const DAY = 86_400_000
/** 自定义区间最长跨度（天），防止一次请求过多柱子 */
const MAX_CUSTOM_DAYS = 180

export function toDateInput(date: Date): string {
  const month = String(date.getMonth() + 1).padStart(2, '0')
  const day = String(date.getDate()).padStart(2, '0')
  return `${date.getFullYear()}-${month}-${day}`
}

/** 自定义区间的默认值（NDatePicker daterange 的时间戳形式）：近 7 天 */
export const defaultCustomRange = (): [number, number] => [
  addDays(startOfDay(new Date()), -6).getTime(),
  startOfDay(new Date()).getTime(),
]

export interface RangeBounds {
  start: Date
  end: Date
  granularity: 'hour' | 'day'
}

/**
 * 把选择的时间维度解析成起止日期与统计粒度；今天/昨天按小时，其余按天。
 * custom 是 NDatePicker daterange 的起止时间戳（取各自当天 0 点）。
 */
export function resolveBounds(key: RangeKey, custom: [number, number] | null): RangeBounds {
  const today = startOfDay(new Date())

  switch (key) {
    case 'today':
      return { start: today, end: today, granularity: 'hour' }
    case 'yesterday': {
      const yesterday = addDays(today, -1)
      return { start: yesterday, end: yesterday, granularity: 'hour' }
    }
    case 'last7':
      return { start: addDays(today, -6), end: today, granularity: 'day' }
    case 'last30':
      return { start: addDays(today, -29), end: today, granularity: 'day' }
    case 'thisMonth':
      return { start: new Date(today.getFullYear(), today.getMonth(), 1), end: today, granularity: 'day' }
    case 'lastMonth':
      return {
        start: new Date(today.getFullYear(), today.getMonth() - 1, 1),
        end: new Date(today.getFullYear(), today.getMonth(), 0),
        granularity: 'day',
      }
    default: {
      let start = custom?.[0] ? startOfDay(new Date(custom[0])) : addDays(today, -6)
      let end = custom?.[1] ? startOfDay(new Date(custom[1])) : today
      if (end < start) [start, end] = [end, start]
      if (end.getTime() - start.getTime() > MAX_CUSTOM_DAYS * DAY) {
        start = new Date(end.getTime() - (MAX_CUSTOM_DAYS - 1) * DAY)
      }
      const single = start.getTime() === end.getTime()
      return { start, end, granularity: single ? 'hour' : 'day' }
    }
  }
}
