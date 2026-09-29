import { getJson } from './http'
import type { AgentSession, Msg } from './types'

export const getSessions = (userId: string) => getJson<AgentSession[]>('/getSessions', { userId })

export const getMessages = (userId: string, sessionId: string) =>
  getJson<Msg[]>('/getMessages', { userId, sessionId })

export const delSession = (userId: string, sessionId: string) =>
  getJson<void>('/delSession', { userId, sessionId })

export const interrupt = (userId: string, sessionId: string) =>
  getJson<void>('/interrupt', { userId, sessionId })
