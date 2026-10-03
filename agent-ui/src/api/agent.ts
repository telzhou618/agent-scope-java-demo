import { API_BASE, http } from './http'
import type { AgentSession, ChatAttachment, Msg, Result, SkillInfo } from './types'
import type { RecentRequestItem, UsageSummary } from '../utils/usage'

/** 会话列表；用户身份由后端从 token 解析 */
export const getSessions = () =>
  http.get<Result<AgentSession[]>>(`${API_BASE}/getSessions`).then((r) => r.data.data)

/** 新建会话：写入占位标题并触发异步标题生成 */
export const createSession = (payload: { sessionId: string; message: string }) =>
  http.post<Result<void>>(`${API_BASE}/createSession`, payload).then((r) => r.data.data)

export const getMessages = (sessionId: string) =>
  http.get<Result<Msg[]>>(`${API_BASE}/getMessages`, { params: { sessionId } }).then((r) => r.data.data)

export const delSession = (sessionId: string) =>
  http.get<Result<void>>(`${API_BASE}/delSession`, { params: { sessionId } }).then((r) => r.data.data)

/** 置顶/取消置顶会话 */
export const pinSession = (sessionId: string, pinned: boolean) =>
  http.get<Result<void>>(`${API_BASE}/pinSession`, { params: { sessionId, pinned } }).then((r) => r.data.data)

export const interrupt = (sessionId: string) =>
  http.get<Result<void>>(`${API_BASE}/interrupt`, { params: { sessionId } }).then((r) => r.data.data)

/** token 消耗统计（按区间；用户身份由后端从 token 解析） */
export const getUsageSummary = (params: { start: string; end: string; granularity: 'hour' | 'day' }) =>
  http.get<Result<UsageSummary>>(`${API_BASE}/usage/summary`, { params }).then((r) => r.data.data)

/** 最近请求记录（区间内最近 10 条，按时间倒序） */
export const getRecentRequests = (params: { start: string; end: string }) =>
  http.get<Result<RecentRequestItem[]>>(`${API_BASE}/usage/recent`, { params }).then((r) => r.data.data)

export const uploadFile = (file: File) => {
  const form = new FormData()
  form.append('file', file)
  return http.post<Result<ChatAttachment>>(`${API_BASE}/files/upload`, form).then((r) => r.data.data)
}

/** 已安装技能列表（输入 / 唤出） */
export const getSkills = () =>
  http.get<Result<SkillInfo[]>>(`${API_BASE}/skills`).then((r) => r.data.data)

/** 消息反馈：feedback 为 null 表示取消 */
export const sendFeedback = (payload: { sessionId: string; messageId: string; feedback: 'up' | 'down' | null }) =>
  http.post<Result<void>>(`${API_BASE}/feedback`, payload).then((r) => r.data.data)

/** 会话的反馈映射：messageId -> up/down */
export const getFeedbacks = (sessionId: string) =>
  http.get<Result<Record<string, 'up' | 'down'>>>(`${API_BASE}/getFeedbacks`, { params: { sessionId } }).then((r) => r.data.data)
