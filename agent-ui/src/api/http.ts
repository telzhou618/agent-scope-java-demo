import type { Result } from './types'

export const API_BASE = '/agent/scope'

export class ApiError extends Error {
  readonly code?: number

  constructor(message: string, code?: number) {
    super(message)
    this.name = 'ApiError'
    this.code = code
  }
}

export async function getJson<T>(path: string, params: Record<string, string>): Promise<T> {
  const url = new URL(API_BASE + path, window.location.origin)
  for (const [key, value] of Object.entries(params)) {
    url.searchParams.set(key, value)
  }
  const response = await fetch(url, { headers: { Accept: 'application/json' } })
  if (!response.ok) {
    throw new ApiError(`请求失败（HTTP ${response.status}）`)
  }
  const body = (await response.json()) as Result<T>
  if (body.code !== 0) {
    throw new ApiError(body.msg || '请求失败', body.code)
  }
  return body.data
}
