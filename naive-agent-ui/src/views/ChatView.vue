<script setup lang="ts">
import { nextTick, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import { NAlert, NEmpty, NIcon, NSpin } from 'naive-ui'
import { ChatbubblesOutline } from '@vicons/ionicons5'
import AssistantTurn from '../components/AssistantTurn.vue'
import ChatComposer from '../components/ChatComposer.vue'
import UserBubble from '../components/UserBubble.vue'
import { useChatStore } from '../stores/chat'
import { useWideMode } from '../composables/useWideMode'

const route = useRoute()
const chat = useChatStore()
const { wide } = useWideMode()

const listRef = ref<HTMLElement | null>(null)

/** 路由驱动会话打开：/chat/:sessionId 变化时加载历史；/chat 保持现状（新对话由侧栏按钮显式触发） */
watch(
  () => route.params.sessionId,
  (sessionId) => {
    if (typeof sessionId === 'string' && sessionId && sessionId !== chat.state.currentSessionId) {
      void chat.openSession(sessionId).then(() => scrollToBottom(true))
    }
  },
  { immediate: true },
)

async function scrollToBottom(force = false) {
  await nextTick()
  const el = listRef.value
  if (!el) return
  // 用户上翻读历史时不强行拽回底部
  const nearBottom = el.scrollHeight - el.scrollTop - el.clientHeight < 120
  if (force || nearBottom) el.scrollTop = el.scrollHeight
}

// 流式增量 / 新回合 / 流结束：跟随到底部
watch(
  () => chat.state.turns,
  () => void scrollToBottom(),
  { deep: true },
)
</script>

<template>
  <div
    class="h-full flex flex-col mx-auto w-full px-4"
    :class="wide ? 'max-w-[1200px]' : 'max-w-[800px]'"
  >
    <div ref="listRef" class="flex-1 overflow-y-auto py-6 hover-scroll">
      <div v-if="chat.state.loadingMessages" class="flex justify-center py-16">
        <NSpin />
      </div>
      <NAlert v-else-if="chat.state.messagesError" type="error" :bordered="false" class="my-4">
        {{ chat.state.messagesError }}
      </NAlert>
      <NEmpty
        v-else-if="chat.state.turns.length === 0"
        class="py-24"
        description="开始一段新对话吧"
      >
        <template #icon>
          <NIcon size="48" :depth="3"><ChatbubblesOutline /></NIcon>
        </template>
      </NEmpty>

      <div v-else class="flex flex-col gap-5">
        <template v-for="turn in chat.state.turns" :key="turn.id">
          <UserBubble
            v-if="turn.kind === 'user'"
            :text="turn.text"
            :attachments="turn.attachments"
          />
          <AssistantTurn v-else :turn="turn" />
        </template>
      </div>
    </div>

    <div class="pb-4 pt-2">
      <ChatComposer />
    </div>
  </div>
</template>
