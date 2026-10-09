import { http } from './http'
import type { PageResult, Result } from './types'

/** 提交用户反馈（意见反馈入口） */
export const submitFeedback = (payload: { type: string; content: string; contact?: string }) =>
  http.post<Result<void>>('/user/feedback/submit', payload).then((r) => r.data.data)

/** 用户管理列表项（管理员接口） */
export interface UserManageItem {
  id: number
  username: string
  nickname: string
  email: string
  avatar: string
  /** 1 启用 / 0 禁用 */
  status: number
  isAdmin: boolean
  agents: string[]
  createdBy: string
  updatedBy: string
  registerTime: string
  createTime: string
  updateTime: string
}

/** 新建/编辑用户共用表单；编辑时 password 留空表示不修改 */
export interface UserSavePayload {
  id?: number
  username?: string
  password?: string
  email?: string
  nickname?: string
  avatar?: string
  status?: number
  isAdmin?: boolean
  agents?: string[]
}

/** 用户分页（管理员）；keyword 匹配用户名/昵称/邮箱 */
export const pageUsers = (params: { page: number; size: number; keyword?: string }) =>
  http.get<Result<PageResult<UserManageItem>>>('/user/manage/page', { params }).then((r) => r.data.data)

/** 新建用户：username/password/email 必填，昵称空=用户名，状态默认启用 */
export const createUser = (payload: UserSavePayload) =>
  http.post<Result<void>>('/user/manage/create', payload).then((r) => r.data.data)

/** 编辑用户：password 留空表示不修改 */
export const updateUser = (payload: UserSavePayload) =>
  http.put<Result<void>>('/user/manage/update', payload).then((r) => r.data.data)

/** 启用/禁用用户 */
export const setUserStatus = (payload: { id: number; status: number }) =>
  http.post<Result<void>>('/user/manage/status', payload).then((r) => r.data.data)

/** 删除用户 */
export const deleteUser = (id: number) =>
  http.delete<Result<void>>(`/user/manage/${id}`).then((r) => r.data.data)

/** 操作日志列表项（管理员接口） */
export interface OperationLogItem {
  id: number
  userId: number
  username: string
  operation: string
  method: string
  path: string
  /** 请求参数的 JSON 文本 */
  params: string
  ip: string
  costMs: number
  /** 'success' 或 'fail:...' */
  result: string
  createTime: string
}

/** 意见反馈列表项（管理员接口） */
export interface FeedbackItem {
  id: number
  userId: number
  username: string
  type: 'bug' | 'idea' | 'other'
  content: string
  contact: string
  /** 0 待处理 / 1 已处理 */
  status: number
  createTime: string
  updateTime: string
}

/** 操作日志分页；keyword 匹配用户名/操作/路径 */
export const pageLogs = (params: { page: number; size: number; keyword?: string }) =>
  http.get<Result<PageResult<OperationLogItem>>>('/user/manage/logs/page', { params }).then((r) => r.data.data)

/** 意见反馈分页；status 不传表示全部，keyword 匹配内容/联系方式 */
export const pageFeedbacks = (params: { page: number; size: number; status?: number; keyword?: string }) =>
  http.get<Result<PageResult<FeedbackItem>>>('/user/manage/feedbacks/page', { params }).then((r) => r.data.data)

/** 标记反馈处理状态 */
export const setFeedbackStatus = (payload: { id: number; status: number }) =>
  http.post<Result<void>>('/user/manage/feedbacks/status', payload).then((r) => r.data.data)
