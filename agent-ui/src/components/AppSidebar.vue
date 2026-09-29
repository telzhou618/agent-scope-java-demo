<script setup lang="ts">
import { computed } from 'vue'
import AppIcon from './AppIcon.vue'
import { useChat } from '../stores/chat'
import { groupLabel, relativeTime } from '../utils/format'

const chat = useChat()

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
      <button class="icon-btn" type="button" aria-label="设置" title="设置（暂未开放）" disabled>
        <AppIcon name="settings" :size="16" />
      </button>
    </div>

    <button class="new-chat" type="button" @click="chat.newChat()">
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
          @click="chat.openSession(session.sessionId)"
          @keydown.enter.prevent="chat.openSession(session.sessionId)"
          @keydown.space.prevent="chat.openSession(session.sessionId)"
        >
          <div class="session-title">{{ session.summary || session.sessionId }}</div>
          <div class="session-meta">{{ relativeTime(session.timestamp) }}</div>
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
      <button
        class="account-btn"
        type="button"
        :aria-current="chat.state.view === 'profile'"
        @click="chat.setView('profile')"
      >
        <span class="avatar" aria-hidden="true">张</span>
        <span class="account-text">
          <span class="account-name">张三</span>
          <span class="account-plan">Pro 计划</span>
        </span>
        <AppIcon name="chevron" :size="14" />
      </button>
    </div>
  </aside>
</template>
