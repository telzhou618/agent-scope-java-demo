<script setup lang="ts">
import { computed } from 'vue'
import { useRouter } from 'vue-router'
import AppIcon from './AppIcon.vue'
import { useAuthStore } from '../stores/auth'
import { useChat } from '../stores/chat'
import { useDismissableMenu } from '../composables/useDismissableMenu'
import { groupLabel } from '../utils/format'

const chat = useChat()
const auth = useAuthStore()
const router = useRouter()
const {
  open: accountOpen,
  bindRoot: bindAccountRoot,
  close: closeAccount,
  toggle: toggleAccount,
} = useDismissableMenu()

/** 侧栏底部账号：昵称与头像首字来自真实登录用户 */
const accountName = computed(() => auth.user?.nickname || '未登录')
const accountAvatar = computed(() => accountName.value.charAt(0) || 'A')

function openProfile() {
  closeAccount()
  void router.push('/profile')
}

/** 退出登录后跳转到登录页（需求 9） */
async function onLogout() {
  closeAccount()
  await auth.logout()
  await router.replace('/login')
}

const groups = computed(() => {
  const result: { label: string; items: typeof chat.state.sessions }[] = []
  for (const session of chat.state.sessions) {
    const label = groupLabel(session.timestamp)
    const last = result[result.length - 1]
    if (last && last.label === label) last.items.push(session)
    else result.push({ label, items: [session] })
  }
  return result
})

function confirmRemove(sessionId: string, summary: string) {
  if (!window.confirm(`删除会话「${summary || sessionId}」？删除后不可恢复。`)) return
  void chat.removeSession(sessionId)
}
</script>

<template>
  <aside class="sidebar" aria-label="会话列表">
    <div class="sidebar-head">
      <div class="brand">
        <span class="brand-mark" aria-hidden="true">A</span>
        <span class="brand-name">Agent</span>
      </div>
      <button
        class="icon-btn"
        type="button"
        aria-label="收起侧栏"
        title="收起侧栏"
        @click="chat.setSidebarOpen(false)"
      >
        <AppIcon name="sidebarCollapse" :size="16" />
      </button>
    </div>

    <button class="new-chat" type="button" @click="router.push('/chat')">
      <AppIcon name="plus" :size="15" />
      新对话
      <span class="kbd-hint" aria-hidden="true">Ctrl K</span>
    </button>

    <div class="session-scroll">
      <p v-if="chat.state.sessionsError" class="sidebar-empty">{{ chat.state.sessionsError }}</p>
      <p v-else-if="chat.state.loadingSessions && !chat.state.sessions.length" class="sidebar-empty">
        正在加载会话…
      </p>
      <p v-else-if="!chat.state.sessions.length" class="sidebar-empty">还没有会话，发一条消息开始。</p>

      <template v-for="group in groups" :key="group.label">
        <div class="session-group-label">{{ group.label }}</div>
        <div
          v-for="session in group.items"
          :key="session.sessionId"
          class="session-item"
          role="button"
          tabindex="0"
          :aria-current="session.sessionId === chat.state.currentSessionId"
          @click="router.push({ name: 'chat-session', params: { sessionId: session.sessionId } })"
          @keydown.enter.prevent="router.push({ name: 'chat-session', params: { sessionId: session.sessionId } })"
          @keydown.space.prevent="router.push({ name: 'chat-session', params: { sessionId: session.sessionId } })"
        >
          <div class="session-title">{{ session.summary || session.sessionId }}</div>
          <button
            class="session-del"
            type="button"
            aria-label="删除会话"
            title="删除会话"
            @click.stop="confirmRemove(session.sessionId, session.summary || '')"
          >
            <AppIcon name="trash" :size="14" />
          </button>
        </div>
      </template>
    </div>

    <div class="sidebar-foot">
      <div :ref="bindAccountRoot" class="account-wrap">
        <button
          class="account-btn"
          type="button"
          aria-haspopup="menu"
          :aria-expanded="accountOpen"
          :aria-current="chat.state.view === 'profile'"
          @click="toggleAccount()"
        >
          <span class="avatar" aria-hidden="true">{{ accountAvatar }}</span>
          <span class="account-text">
            <span class="account-name">{{ accountName }}</span>
            <span class="account-plan">Pro 计划</span>
          </span>
          <AppIcon name="chevron" :size="14" class="chevron" />
        </button>

        <div v-if="accountOpen" class="account-menu" role="menu">
          <button class="more-item" type="button" role="menuitem" @click="openProfile">
            <AppIcon name="user" :size="14" />
            个人主页
          </button>
          <button class="more-item" type="button" role="menuitem" @click="onLogout">
            <AppIcon name="logout" :size="14" />
            退出
          </button>
        </div>
      </div>
    </div>
  </aside>
</template>
