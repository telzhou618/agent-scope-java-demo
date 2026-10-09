<script setup lang="ts">
import { computed, ref } from 'vue'
import { useRouter } from 'vue-router'
import AppIcon from './AppIcon.vue'
import FeedbackDialog from './FeedbackDialog.vue'
import { useAuthStore } from '../stores/auth'
import { useChat } from '../stores/chat'
import { agentShortLabel } from '../stores/agents'
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

function openUsers() {
  closeAccount()
  void router.push('/users')
}

/** 意见反馈弹窗 */
const feedbackOpen = ref(false)

function openFeedback() {
  closeAccount()
  feedbackOpen.value = true
}

/** 退出登录后跳转到登录页（需求 9）；先清聊天状态：断开所有流并释放 blob 预览 */
async function onLogout() {
  closeAccount()
  chat.reset()
  await auth.logout()
  await router.replace('/login')
}

const groups = computed(() => {
  const result: { label: string; items: typeof chat.state.sessions }[] = []
  // 置顶会话单独成组，固定排在日期分组之前
  const pinned = chat.state.sessions.filter((item) => item.pinned)
  if (pinned.length) result.push({ label: '置顶', items: pinned })
  for (const session of chat.state.sessions) {
    if (session.pinned) continue
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
          <div class="session-title">
            {{ session.summary || session.sessionId
            }}<span v-if="session.agentName" class="session-agent">{{ agentShortLabel(session.agentName) }}</span>
          </div>
          <button
            class="session-pin"
            :class="{ active: session.pinned }"
            type="button"
            :aria-label="session.pinned ? '取消置顶' : '置顶会话'"
            :title="session.pinned ? '取消置顶' : '置顶会话'"
            @click.stop="chat.togglePin(session.sessionId)"
          >
            <AppIcon name="pin" :size="13" :filled="!!session.pinned" />
          </button>
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
          :aria-current="chat.state.view === 'profile' || chat.state.view === 'users'"
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
          <button
            v-if="auth.user?.isAdmin"
            class="more-item"
            type="button"
            role="menuitem"
            @click="openUsers"
          >
            <AppIcon name="users" :size="14" />
            用户管理
          </button>
          <button class="more-item" type="button" role="menuitem" @click="openFeedback">
            <AppIcon name="message" :size="14" />
            意见反馈
          </button>
          <button class="more-item" type="button" role="menuitem" @click="onLogout">
            <AppIcon name="logout" :size="14" />
            退出
          </button>
        </div>
      </div>
    </div>

    <FeedbackDialog v-if="feedbackOpen" @close="feedbackOpen = false" />
  </aside>
</template>
