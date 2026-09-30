import axios, { AxiosError } from 'axios'
import type { Result } from './types'
import { clearToken, getToken } from './token'

export const API_BASE = '/agent/scope'

export class ApiError extends Error {
  readonly code?: number

  constructor(message: string, code?: number) {
    super(message)
    this.name = 'ApiError'
    this.code = code
  }
}

export const http = axios.create({ timeout: 30_000 })

/** 所有请求自动带上 token */
http.interceptors.request.use((config) => {
  if (getToken()) config.headers.Authorization = `Bearer ${getToken()}`
  return config
})

/** 401 兜底：清掉本地登录态并回登录页（动态导入避免模块循环依赖） */
async function handleUnauthorized() {
  clearToken()
  const [{ default: router }, { useAuthStore }] = await Promise.all([
    import('../router'),
    import('../stores/auth'),
  ])
  useAuthStore().clearLocalState()
  if (router.currentRoute.value.name !== 'login') {
    await router.replace({
      path: '/login',
      query: { redirect: router.currentRoute.value.fullPath },
    })
  }
}

http.interceptors.response.use(
  (response) => {
    const body = response.data as Result<unknown> | undefined
    if (body && typeof body === 'object' && typeof body.code === 'number' && body.code !== 0) {
      throw new ApiError(body.msg || '请求失败', body.code)
    }
    return response
  },
  (error: AxiosError<Result<unknown>>) => {
    const status = error.response?.status
    if (status === 401) {
      void handleUnauthorized()
      return Promise.reject(new ApiError('未登录或登录已过期，请重新登录', 401))
    }
    const body = error.response?.data
    if (body && typeof body === 'object' && typeof body.code === 'number' && body.msg) {
      return Promise.reject(new ApiError(body.msg, body.code))
    }
    if (!error.response) {
      return Promise.reject(new ApiError('无法连接 Agent 服务，请确认 agent-app（8082）已启动'))
    }
    return Promise.reject(new ApiError(`请求失败（HTTP ${status}）`))
  },
)
