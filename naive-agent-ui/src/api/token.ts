/** 登录 token 的本地存储：唯一持久化点，http 拦截器与路由守卫都从这里读 */
const TOKEN_KEY = 'naive-agent-ui:token'

export function getToken(): string {
  try {
    return localStorage.getItem(TOKEN_KEY) ?? ''
  } catch {
    return ''
  }
}

export function setToken(token: string) {
  try {
    localStorage.setItem(TOKEN_KEY, token)
  } catch {
    /* 隐私模式下无法持久化，忽略 */
  }
}

export function clearToken() {
  try {
    localStorage.removeItem(TOKEN_KEY)
  } catch {
    /* 忽略 */
  }
}
