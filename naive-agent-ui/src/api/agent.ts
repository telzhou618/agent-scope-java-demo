import { API_BASE, http } from './http'
import type {
  AgentInfo,
  AgentSession,
  ChatAttachment,
  DatasourceInfo,
  MessageItem,
  PluginInfo,
  MonthlyQuotaSummary,
  Msg,
  PageResult,
  Result,
  ScheduleTaskItem,
  SkillInfo,
  UserFileItem,
} from './types'
import type { RecentRequestItem, UsageSummary } from '../utils/usage'

/** 可用 Agent 列表（顶栏选择器；后端已按用户权限过滤） */
export const getAgents = () =>
  http.get<Result<AgentInfo[]>>(`${API_BASE}/agents`).then((r) => r.data.data)

/** 会话列表；用户身份由后端从 token 解析 */
export const getSessions = () =>
  http.get<Result<AgentSession[]>>(`${API_BASE}/getSessions`).then((r) => r.data.data)

/** 新建会话：写入占位标题并触发异步标题生成 */
export const createSession = (payload: { sessionId: string; message: string; agentName: string }) =>
  http.post<Result<void>>(`${API_BASE}/createSession`, payload).then((r) => r.data.data)

export const getMessages = (sessionId: string) =>
  http.get<Result<Msg[]>>(`${API_BASE}/getMessages`, { params: { sessionId } }).then((r) => r.data.data)

export const delSession = (sessionId: string) =>
  http.post<Result<void>>(`${API_BASE}/delSession`, { sessionId }).then((r) => r.data.data)

/** 置顶/取消置顶会话 */
export const pinSession = (sessionId: string, pinned: boolean) =>
  http.post<Result<void>>(`${API_BASE}/pinSession`, { sessionId, pinned }).then((r) => r.data.data)

export const interrupt = (sessionId: string, agentName: string) =>
  http.post<Result<void>>(`${API_BASE}/interrupt`, { sessionId, agentName }).then((r) => r.data.data)

/** 上传附件：按 <userId>/<sessionId>/ 归档，删会话时后端级联删除 */
export const uploadFile = (file: File, sessionId: string) => {
  const form = new FormData()
  form.append('file', file)
  form.append('sessionId', sessionId)
  return http.post<Result<ChatAttachment>>(`${API_BASE}/files/upload`, form).then((r) => r.data.data)
}

/** 已安装技能列表（输入 @ 唤出）；可按 agent 过滤 */
export const getSkills = (agentName?: string) =>
  http.get<Result<SkillInfo[]>>(`${API_BASE}/skills`, { params: agentName ? { agentName } : undefined }).then((r) => r.data.data)

/** 可查询数据源列表（输入 @ 唤出） */
export const getDatasources = () =>
  http.get<Result<DatasourceInfo[]>>(`${API_BASE}/datasources`).then((r) => r.data.data)

/** 插件列表（输入 / 唤出） */
export const getPlugins = () =>
  http.get<Result<PluginInfo[]>>(`${API_BASE}/plugins`).then((r) => r.data.data)

/** 消息反馈：feedback 为 null 表示取消 */
export const sendFeedback = (payload: { sessionId: string; messageId: string; feedback: 'up' | 'down' | null }) =>
  http.post<Result<void>>(`${API_BASE}/feedback`, payload).then((r) => r.data.data)

/** 会话的反馈映射：messageId -> up/down */
export const getFeedbacks = (sessionId: string) =>
  http.get<Result<Record<string, 'up' | 'down'>>>(`${API_BASE}/getFeedbacks`, { params: { sessionId } }).then((r) => r.data.data)

/** token 消耗统计（按区间；用户身份由后端从 token 解析） */
export const getUsageSummary = (params: { start: string; end: string; granularity: 'hour' | 'day' }) =>
  http.get<Result<UsageSummary>>(`${API_BASE}/usage/summary`, { params }).then((r) => r.data.data)

/** 最近请求记录（区间内按时间倒序分页） */
export const getRecentRequests = (params: { start: string; end: string; page: number; size: number }) =>
  http.get<Result<PageResult<RecentRequestItem>>>(`${API_BASE}/usage/recent`, { params }).then((r) => r.data.data)

/** 本月 token 限额与每个 Agent 消耗 */
export const getMonthlyQuota = () =>
  http.get<Result<MonthlyQuotaSummary>>(`${API_BASE}/usage/month-quota`).then((r) => r.data.data)

/* ---------- 消息中心 ---------- */

export const pageMessages = (params: { page: number; size: number }) =>
  http.get<Result<PageResult<MessageItem>>>(`${API_BASE}/message/page`, { params }).then((r) => r.data.data)

export const getUnreadCount = () =>
  http.get<Result<number>>(`${API_BASE}/message/unreadCount`).then((r) => r.data.data)

export const getMessageDetail = (id: number) =>
  http.get<Result<MessageItem>>(`${API_BASE}/message/detail`, { params: { id } }).then((r) => r.data.data)

export const markMessageRead = (id: number) =>
  http.post<Result<void>>(`${API_BASE}/message/read`, { id }).then((r) => r.data.data)

export const deleteMessage = (id: number) =>
  http.post<Result<void>>(`${API_BASE}/message/delete`, { id }).then((r) => r.data.data)

/** 生成分享链接（7 天有效，免登录可访问）；返回相对路径，前端拼 origin */
export const getMessageShareLink = (id: number) =>
  http.get<Result<string>>(`${API_BASE}/message/shareLink`, { params: { id } }).then((r) => r.data.data)

/** 分享页取消息详情（UUID 短链，免登录） */
export const getSharedMessage = (t: string) =>
  http.get<Result<MessageItem>>(`${API_BASE}/message/shared`, { params: { t } }).then((r) => r.data.data)

/** 生成会话分享链接（7 天有效，免登录可访问）；返回相对路径，前端拼 origin */
export const getSessionShareLink = (sessionId: string) =>
  http.get<Result<string>>(`${API_BASE}/share/sessionLink`, { params: { sessionId } }).then((r) => r.data.data)

/** 分享页取会话消息（UUID 短链，免登录；附件/图片内容块不下发） */
export const getSharedSession = (t: string) =>
  http.get<Result<Msg[]>>(`${API_BASE}/share/session`, { params: { t } }).then((r) => r.data.data)

/* ---------- 我的文件 ---------- */

export const myFiles = (params: { page: number; size: number }) =>
  http.get<Result<PageResult<UserFileItem>>>(`${API_BASE}/files/my/list`, { params }).then((r) => r.data.data)

export const deleteFile = (id: number) =>
  http.delete<Result<void>>(`${API_BASE}/files/${id}`).then((r) => r.data.data)

/* ---------- 定时任务 ---------- */

export const listSchedules = () =>
  http.get<Result<ScheduleTaskItem[]>>(`${API_BASE}/schedule/list`).then((r) => r.data.data)

export const toggleSchedule = (id: number, status: number) =>
  http.post<Result<void>>(`${API_BASE}/schedule/toggle`, { id, status }).then((r) => r.data.data)

/** 立即手动执行一次（不影响正常周期，结果发到消息中心） */
export const triggerSchedule = (id: number) =>
  http.post<Result<void>>(`${API_BASE}/schedule/trigger`, { id }).then((r) => r.data.data)

export const deleteSchedule = (id: number) =>
  http.post<Result<void>>(`${API_BASE}/schedule/delete`, { id }).then((r) => r.data.data)

/** 新增定时任务 */
export const createSchedule = (payload: { title: string; agentName: string; description: string; cron: string }) =>
  http.post<Result<void>>(`${API_BASE}/schedule/create`, payload).then((r) => r.data.data)

/** 编辑定时任务 */
export const updateSchedule = (payload: { id: number; title: string; agentName: string; description: string; cron: string }) =>
  http.post<Result<void>>(`${API_BASE}/schedule/update`, payload).then((r) => r.data.data)
