<script setup lang="ts">
import { nextTick, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import { NAlert, NButton, NEmpty, NIcon, NSpin } from 'naive-ui'
import { ArrowDownOutline, ChatbubblesOutline } from '@vicons/ionicons5'
import AssistantTurn from '../components/AssistantTurn.vue'
import ChatActionRail from '../components/ChatActionRail.vue'
import ChatComposer from '../components/ChatComposer.vue'
import UserBubble from '../components/UserBubble.vue'
import { useChatStore } from '../stores/chat'
import { useWideMode } from '../composables/useWideMode'

const route = useRoute()
const chat = useChatStore()
const { wide } = useWideMode()

const listRef = ref<HTMLElement | null>(null)

/**
 * 流式跟随：following=true 时每次回合更新都滚到底；
 * 用户明显上翻（离底 >120px）暂停跟随并显示「回到底部」，回到底部附近（<40px）自动恢复。
 */
const following = ref(true)

function onListScroll() {
  const el = listRef.value
  if (!el) return
  const distance = el.scrollHeight - el.scrollTop - el.clientHeight
  if (distance < 40) following.value = true
  else if (distance > 120) following.value = false
}

async function scrollToBottom() {
  await nextTick()
  const el = listRef.value
  if (el) el.scrollTop = el.scrollHeight
}

function backToBottom() {
  following.value = true
  void scrollToBottom()
}

/** 路由驱动会话打开：/chat/:sessionId 变化时加载历史；/chat 保持现状（新对话由侧栏按钮显式触发） */
watch(
  () => route.params.sessionId,
  (sessionId) => {
    if (typeof sessionId === 'string' && sessionId && sessionId !== chat.state.currentSessionId) {
      following.value = true
      void chat.openSession(sessionId).then(scrollToBottom)
    }
  },
  { immediate: true },
)

// 流式增量 / 新回合 / 流结束：跟随开启时始终滚到底
watch(
  () => chat.state.turns,
  () => {
    if (following.value) void scrollToBottom()
  },
  { deep: true },
)

// 发送新消息（开始流式）/ 切换会话：强制恢复跟随并回底
watch(
  () => chat.state.streaming,
  (streaming) => {
    if (streaming) backToBottom()
  },
)

watch(
  () => chat.state.currentSessionId,
  () => {
    following.value = true
  },
)
</script>

<template>
  <div
    class="h-full flex flex-col mx-auto w-full px-4 relative"
    :class="wide ? 'max-w-[1200px]' : 'max-w-[800px]'"
  >
    <div ref="listRef" class="flex-1 overflow-y-auto py-6 hover-scroll" @scroll="onListScroll">
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

    <!-- 上翻暂停跟随时浮现的回底按钮 -->
    <Transition name="fade-up">
      <NButton
        v-if="!following && chat.state.turns.length"
        class="back-to-bottom"
        circle
        size="small"
        secondary
        aria-label="回到底部"
        @click="backToBottom"
      >
        <template #icon>
          <NIcon><ArrowDownOutline /></NIcon>
        </template>
      </NButton>
    </Transition>

    <div class="pb-4 pt-2">
      <ChatComposer />
    </div>

    <ChatActionRail />
  </div>
</template>

<style scoped>
.back-to-bottom {
  position: fixed;
  right: 20px;
  bottom: 108px;
  z-index: 10;
  box-shadow: var(--shadow-raised);
}

.fade-up-enter-active,
.fade-up-leave-active {
  transition:
    opacity 0.18s,
    transform 0.18s;
}

.fade-up-enter-from,
.fade-up-leave-to {
  opacity: 0;
  transform: translateY(8px);
}
</style>
