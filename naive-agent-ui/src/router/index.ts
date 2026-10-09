import { createRouter, createWebHistory, type RouteLocationNormalized } from 'vue-router'
import { getToken } from '../api/token'

// 视图懒加载：登录页保持轻量，主界面按需加载
const LoginView = () => import('../views/LoginView.vue')
const AppLayout = () => import('../layouts/AppLayout.vue')
const ChatView = () => import('../views/ChatView.vue')
const ProfileView = () => import('../views/ProfileView.vue')
const AdminView = () => import('../views/AdminView.vue')

const router = createRouter({
  history: createWebHistory(),
  routes: [
    { path: '/login', name: 'login', component: LoginView },
    {
      path: '/',
      component: AppLayout,
      children: [
        { path: '', redirect: '/chat' },
        { path: 'chat', name: 'chat', component: ChatView },
        { path: 'chat/:sessionId', name: 'chat-session', component: ChatView },
        { path: 'profile', name: 'profile', component: ProfileView },
        { path: 'admin', name: 'admin', component: AdminView },
      ],
    },
    { path: '/:pathMatch(.*)*', redirect: '/chat' },
  ],
})

/**
 * 守卫：除 /login 外都要求已登录。
 * 是否登录直接读 localStorage 的 token，避免路由 -> store 的模块循环依赖。
 * /admin 额外要求管理员：用户信息未加载时先拉取，非管理员打回 /chat。
 */
router.beforeEach(async (to: RouteLocationNormalized) => {
  const loggedIn = !!getToken()
  if (to.name === 'login') {
    return loggedIn ? { path: '/chat' } : true
  }
  if (!loggedIn) {
    return { path: '/login', query: { redirect: to.fullPath } }
  }
  if (to.name === 'admin') {
    // 动态导入避免模块循环依赖（与 http.ts 的 401 兜底同款写法）
    const { useAuthStore } = await import('../stores/auth')
    const auth = useAuthStore()
    if (!auth.user) await auth.fetchCurrent()
    if (auth.user?.isAdmin !== true) return { path: '/chat' }
  }
  return true
})

export default router
