<script setup lang="ts">
import { computed, ref } from 'vue'
import { useRouter } from 'vue-router'
import {
  NAlert,
  NButton,
  NEmpty,
  NIcon,
  NInput,
  NPopconfirm,
  NSpin,
  NTag,
  NTooltip,
} from 'naive-ui'
import {
  AddOutline,
  Bookmark,
  BookmarkOutline,
  SearchOutline,
  TrashOutline,
} from '@vicons/ionicons5'
import { useChatStore } from '../stores/chat'
import { agentShortLabel } from '../stores/agents'
import { useThemeStore } from '../stores/theme'
import type { AgentSession } from '../api/types'
import { groupLabel, relativeTime } from '../utils/format'

const router = useRouter()
const chat = useChatStore()
const theme = useThemeStore()

/** 新对话主按钮：暗色主题下用更深的靛蓝，弱化高亮 */
const newChatColor = computed(() => (theme.isDark ? '#4338ca' : undefined))

const keyword = ref('')

const GROUP_ORDER = ['今天', '昨天', '更早']

interface SessionGroup {
  label: string
  items: AgentSession[]
}

/** 搜索过滤（summary/sessionId 本地过滤）+ 分组：置顶组在前，其余按日期分组 */
const groups = computed<SessionGroup[]>(() => {
  const query = keyword.value.trim().toLowerCase()
  const filtered = query
    ? chat.state.sessions.filter(
        (item) =>
          (item.summary ?? '').toLowerCase().includes(query) ||
          item.sessionId.toLowerCase().includes(query),
      )
    : chat.state.sessions

  const pinned = filtered.filter((item) => item.pinned)
  const rest = filtered.filter((item) => !item.pinned)

  const result: SessionGroup[] = []
  if (pinned.length) result.push({ label: '置顶', items: pinned })
  for (const label of GROUP_ORDER) {
    const items = rest.filter((item) => groupLabel(item.timestamp) === label)
    if (items.length) result.push({ label, items })
  }
  return result
})

function onNewChat() {
  chat.newChat()
  router.push('/chat')
}

function onOpen(session: AgentSession) {
  if (session.sessionId === chat.state.currentSessionId) return
  router.push(`/chat/${session.sessionId}`)
}

async function onRemove(session: AgentSession) {
  const wasCurrent = session.sessionId === chat.state.currentSessionId
  await chat.removeSession(session.sessionId)
  if (wasCurrent) router.replace('/chat')
}
</script>

<template>
  <div class="h-full flex flex-col">
    <div class="p-3 flex flex-col gap-2">
      <NButton type="primary" block :color="newChatColor" @click="onNewChat">
        <template #icon>
          <NIcon><AddOutline /></NIcon>
        </template>
        新对话
      </NButton>
      <NInput v-model:value="keyword" size="small" clearable placeholder="搜索会话">
        <template #prefix>
          <NIcon><SearchOutline /></NIcon>
        </template>
      </NInput>
    </div>

    <div class="flex-1 overflow-y-auto px-2 pb-3 hover-scroll">
      <div v-if="chat.state.loadingSessions" class="flex justify-center py-8">
        <NSpin size="small" />
      </div>
      <NAlert v-else-if="chat.state.sessionsError" type="error" size="small" :bordered="false">
        <div class="flex items-center gap-2 flex-wrap">
          <span>{{ chat.state.sessionsError }}</span>
          <NButton size="tiny" secondary type="error" @click="chat.loadSessions()">重试</NButton>
        </div>
      </NAlert>
      <NEmpty
        v-else-if="groups.length === 0"
        class="py-10"
        size="small"
        :description="keyword ? '没有匹配的会话' : '暂无会话，点击上方开始新对话'"
      />
      <template v-else>
        <div v-for="(group, groupIndex) in groups" :key="group.label" class="session-group">
          <div class="group-label" :class="{ 'has-divider': groupIndex > 0 }">
            {{ group.label }}
          </div>
          <div
            v-for="session in group.items"
            :key="session.sessionId"
            class="session-item group"
            :class="{ active: session.sessionId === chat.state.currentSessionId }"
            @click="onOpen(session)"
          >
            <div class="min-w-0 flex-1">
              <div class="truncate text-sm">{{ session.summary || '新会话' }}</div>
              <div class="mt-0.5 flex items-center gap-1.5">
                <NTag v-if="session.agentName" size="tiny" :bordered="false" round>
                  {{ agentShortLabel(session.agentName) }}
                </NTag>
                <span class="text-xs op-45">{{ relativeTime(session.timestamp) }}</span>
              </div>
            </div>
            <div class="session-actions" @click.stop>
              <NTooltip>
                <template #trigger>
                  <NButton
                    text
                    size="tiny"
                    class="pin-btn"
                    :class="{ pinned: session.pinned }"
                    @click="chat.togglePin(session.sessionId)"
                  >
                    <template #icon>
                      <NIcon size="15" :color="session.pinned ? 'var(--brand)' : undefined">
                        <Bookmark v-if="session.pinned" />
                        <BookmarkOutline v-else />
                      </NIcon>
                    </template>
                  </NButton>
                </template>
                {{ session.pinned ? '取消置顶' : '置顶' }}
              </NTooltip>
              <NPopconfirm @positive-click="onRemove(session)">
                <template #trigger>
                  <NButton text size="tiny" type="error" class="del-btn">
                    <template #icon>
                      <NIcon size="15"><TrashOutline /></NIcon>
                    </template>
                  </NButton>
                </template>
                删除该会话？
              </NPopconfirm>
            </div>
          </div>
        </div>
      </template>
    </div>
  </div>
</template>

<style scoped>
/* 分组头：小字号 + 宽字距 + muted，sticky 吸附列表顶部；后续组带细分隔线 */
.group-label {
  position: sticky;
  top: 0;
  z-index: 5;
  padding: 6px 8px;
  font-size: 11px;
  letter-spacing: 0.08em;
  opacity: 0.5;
  background: var(--surface-sider);
}

.group-label.has-divider::before {
  content: '';
  position: absolute;
  left: 8px;
  right: 8px;
  top: 0;
  border-top: 1px solid var(--divider-strong);
}

.group-label.has-divider {
  margin-top: 6px;
  padding-top: 10px;
}

.session-item {
  display: flex;
  align-items: center;
  gap: 4px;
  padding: 8px 10px;
  border-radius: 10px;
  cursor: pointer;
  transition: background-color 0.15s ease;
}

.session-item:hover {
  background: var(--brand-soft);
}

.session-item.active {
  background: var(--brand-soft-strong);
}

.session-actions {
  display: flex;
  align-items: center;
  gap: 4px;
  flex-shrink: 0;
}

/* 操作按钮只在 hover 会话项时出现（当前会话也不常显），保持列表干净 */
.pin-btn,
.del-btn {
  display: none;
}

.session-item:hover .pin-btn,
.session-item:hover .del-btn {
  display: inline-flex;
}
</style>
