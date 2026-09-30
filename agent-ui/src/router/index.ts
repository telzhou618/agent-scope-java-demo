import { createRouter, createWebHistory, type RouteLocationNormalized } from 'vue-router'
import LoginView from '../views/LoginView.vue'
import ChatView from '../views/ChatView.vue'
import { getToken } from '../api/token'

const router = createRouter({
  history: createWebHistory(),
  routes: [
    { path: '/login', name: 'login', component: LoginView },
    { path: '/', redirect: '/chat' },
    { path: '/chat', name: 'chat', component: ChatView },
    { path: '/chat/:sessionId', name: 'chat-session', component: ChatView },
    { path: '/profile', name: 'profile', component: ChatView },
    { path: '/:pathMatch(.*)*', redirect: '/chat' },
  ],
})

/**
 * 守卫（需求 6）：除 /login 外都要求已登录。
 * 是否登录直接读 localStorage 的 token，避免路由 -> store 的模块循环依赖。
 */
router.beforeEach((to: RouteLocationNormalized) => {
  const loggedIn = !!getToken()
  if (to.name === 'login') {
    return loggedIn ? { path: '/chat' } : true
  }
  if (!loggedIn) {
    return { path: '/login', query: { redirect: to.fullPath } }
  }
  return true
})

export default router
