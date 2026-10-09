import { http } from './http'
import type { Result, UserInfo } from './types'

export interface LoginResult {
  token: string
  user: UserInfo
}

export const login = (account: string, password: string) =>
  http.post<Result<LoginResult>>('/auth/login', { account, password }).then((r) => r.data.data)

export const logoutApi = () => http.post<Result<void>>('/auth/logout').then((r) => r.data.data)

export const currentUser = () => http.get<Result<UserInfo>>('/auth/current').then((r) => r.data.data)

export interface UpdateProfilePayload {
  nickname?: string
  email?: string
  avatar?: string
}

/** 更新资料：返回更新后的 UserVO，直接回写 auth.user */
export const updateProfile = (payload: UpdateProfilePayload) =>
  http.put<Result<UserInfo>>('/user/profile', payload).then((r) => r.data.data)

export interface UpdatePasswordPayload {
  oldPassword: string
  newPassword: string
}

/** 修改密码：旧密码错误时后端返回 code 非 0，经拦截器抛 ApiError（msg「原密码不正确」） */
export const updatePassword = (payload: UpdatePasswordPayload) =>
  http.put<Result<void>>('/user/password', payload).then((r) => r.data.data)
