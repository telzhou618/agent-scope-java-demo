import { http } from './http'
import type { Result } from './types'

/** 提交用户反馈（意见反馈入口） */
export const submitFeedback = (payload: { type: string; content: string; contact?: string }) =>
  http.post<Result<void>>('/user/feedback/submit', payload).then((r) => r.data.data)
