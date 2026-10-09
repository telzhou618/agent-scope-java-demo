import { http } from './http'
import type { Result } from './types'

export interface UserInfo {
  id: number
  username: string
  nickname: string
  email: string
  avatar: string
  /** 是否管理员（用户管理入口按此显示） */
  isAdmin: boolean
  /** 当前用户可用的 Agent 名称列表；管理员为全量 */
  agents: string[]
}

export interface LoginResult {
  token: string
  user: UserInfo
}

export const login = (account: string, password: string) =>
  http.post<Result<LoginResult>>('/auth/login', { account, password }).then((r) => r.data.data)

export const logoutApi = () => http.post<Result<void>>('/auth/logout').then((r) => r.data.data)

export const currentUser = () => http.get<Result<UserInfo>>('/auth/current').then((r) => r.data.data)
