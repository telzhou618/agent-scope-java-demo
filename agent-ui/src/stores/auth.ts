import { computed, ref } from 'vue'
import { defineStore } from 'pinia'
import { currentUser, login as loginApi, logoutApi, type UserInfo } from '../api/auth'
import { clearToken, getToken, setToken } from '../api/token'
import { useAgentPreference } from '../composables/useAgentPreference'

/**
 * 用户登录状态（Pinia，需求 13）。
 * token 持久化在 localStorage（见 api/token.ts），store 里只放用户信息。
 */
export const useAuthStore = defineStore('auth', () => {
  const user = ref<UserInfo | null>(null)

  const token = computed(() => getToken())
  const isLoggedIn = computed(() => !!getToken())

  /** 登录成功会顺手把 token 写入 localStorage */
  async function login(account: string, password: string) {
    const response = await loginApi(account.trim(), password)
    setToken(response.token)
    user.value = response.user
    useAgentPreference().bindUser(String(response.user.id))
  }

  /** 退出：通知后端失效 token，失败也清空本地状态 */
  async function logout() {
    try {
      if (getToken()) await logoutApi()
    } catch {
      /* 网络失败也要退出本地状态 */
    }
    clearLocalState()
  }

  /** 只清本地状态（http 401 兜底时也会调） */
  function clearLocalState() {
    clearToken()
    user.value = null
    useAgentPreference().bindUser(null)
  }

  /** 刷新场景下根据 token 重新拉取用户信息 */
  async function fetchCurrent() {
    if (!getToken()) return
    user.value = await currentUser()
    useAgentPreference().bindUser(String(user.value.id))
  }

  return { user, token, isLoggedIn, login, logout, clearLocalState, fetchCurrent }
})
