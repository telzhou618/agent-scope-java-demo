import { API_BASE, http } from './http'
import type { AgentSession, Msg, Result } from './types'
import type { UsageSummary } from '../utils/usage'

/** 会话列表；用户身份由后端从 token 解析 */
export const getSessions = () =>
  http.get<Result<AgentSession[]>>(`${API_BASE}/getSessions`).then((r) => r.data.data)

export const getMessages = (sessionId: string) =>
  http.get<Result<Msg[]>>(`${API_BASE}/getMessages`, { params: { sessionId } }).then((r) => r.data.data)

export const delSession = (sessionId: string) =>
  http.get<Result<void>>(`${API_BASE}/delSession`, { params: { sessionId } }).then((r) => r.data.data)

export const interrupt = (sessionId: string) =>
  http.get<Result<void>>(`${API_BASE}/interrupt`, { params: { sessionId } }).then((r) => r.data.data)

/** token 消耗统计（按区间；用户身份由后端从 token 解析） */
export const getUsageSummary = (params: { start: string; end: string; granularity: 'hour' | 'day' }) =>
  http.get<Result<UsageSummary>>(`${API_BASE}/usage/summary`, { params }).then((r) => r.data.data)
