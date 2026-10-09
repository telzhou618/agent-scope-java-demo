<script setup lang="ts">
import { onBeforeUnmount, onMounted, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import AppSidebar from '../components/AppSidebar.vue'
import AppTopBar from '../components/AppTopBar.vue'
import ChatComposer from '../components/ChatComposer.vue'
import ChatThread from '../components/ChatThread.vue'
import ProfileView from '../components/ProfileView.vue'
import UsersView from '../components/UsersView.vue'
import { useAuthStore } from '../stores/auth'
import { useChat } from '../stores/chat'
import { useComposer } from '../composables/useComposer'

const { state, loadSessions, openSession, newChat, stop, setView, toggleSidebar, closeDrawerOnNarrow } = useChat()
const composer = useComposer()
const auth = useAuthStore()
const route = useRoute()
const router = useRouter()

// 布局状态写在 body 上，样式表全权负责两种布局下的呈现
watch(
  () => state.sidebarOpen,
  (open) => {
    document.body.dataset.sidebar = open ? 'open' : 'collapsed'
  },
  { immediate: true },
)

watch(
  () => state.view,
  (view) => {
    document.body.dataset.view = view
  },
  { immediate: true },
)

// 刷新场景：token 还在但用户信息没载进来
if (auth.token && !auth.user) void auth.fetchCurrent()

// 路由驱动视图与会话（需求 14）
watch(
  () => [route.name, route.params.sessionId],
  () => {
    if (route.name === 'profile') {
      setView('profile')
      return
    }
    if (route.name === 'users') {
      // 用户管理页不碰会话状态（守卫已拦截非管理员）
      setView('users')
      return
    }
    setView('chat')
    const sessionId = route.params.sessionId as string | undefined
    if (sessionId) {
      if (sessionId !== state.currentSessionId) void openSession(sessionId)
    } else if (state.currentSessionId !== null) {
      void newChat()
    }
  },
  { immediate: true },
)

function onKeydown(event: KeyboardEvent) {
  if (event.key === 'Escape') {
    // 流式生成中 Esc 优先停止生成；未在生成时才收起窄屏抽屉
    if (state.streaming) {
      void stop()
      return
    }
    closeDrawerOnNarrow()
    return
  }
  if (!(event.ctrlKey || event.metaKey)) return
  const key = event.key.toLowerCase()
  if (key === 'k') {
    event.preventDefault()
    void newChat()
    return
  }
  if (key === 'b') {
    event.preventDefault()
    toggleSidebar()
    return
  }
  // Ctrl/Cmd+J 聚焦聊天输入框；不在聊天路由时先导航回聊天页
  if (key === 'j') {
    event.preventDefault()
    if (route.name !== 'chat' && route.name !== 'chat-session') void router.push('/chat')
    composer.focus()
  }
}

onMounted(() => {
  void loadSessions()
  document.addEventListener('keydown', onKeydown)
})

onBeforeUnmount(() => {
  document.removeEventListener('keydown', onKeydown)
})
</script>

<template>
  <div class="app">
    <AppSidebar />
    <main class="main">
      <AppTopBar />
      <ChatThread />
      <div class="profile">
        <ProfileView />
      </div>
      <div class="users">
        <!-- 仅进入用户管理页时挂载：避免非管理员触发 403 请求 -->
        <UsersView v-if="state.view === 'users'" />
      </div>
      <ChatComposer />
    </main>
  </div>
  <div class="scrim" @click="closeDrawerOnNarrow()" />
</template>
