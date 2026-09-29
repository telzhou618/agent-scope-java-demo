<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import AssistantTurn from './AssistantTurn.vue'
import EmptyState from './EmptyState.vue'
import UserBubble from './UserBubble.vue'
import { useChat } from '../stores/chat'
import type { AssistantTurn as AssistantTurnModel } from '../utils/model'

const chat = useChat()
const scroller = ref<HTMLElement | null>(null)
const thread = ref<HTMLElement | null>(null)

let observer: MutationObserver | null = null
let stick = true

const hasTurns = computed(() => chat.state.turns.length > 0)

function nearBottom() {
  const element = scroller.value
  if (!element) return true
  return element.scrollHeight - element.scrollTop - element.clientHeight < 140
}

function scrollToBottom() {
  const element = scroller.value
  if (element) element.scrollTop = element.scrollHeight
}

function onScroll() {
  stick = nearBottom()
}

onMounted(() => {
  const element = thread.value
  if (element) {
    observer = new MutationObserver(() => {
      if (stick) scrollToBottom()
    })
    observer.observe(element, { childList: true, subtree: true, characterData: true })
  }
  scrollToBottom()
})

onBeforeUnmount(() => observer?.disconnect())

watch(
  () => chat.state.currentSessionId,
  async () => {
    stick = true
    await nextTick()
    scrollToBottom()
  },
)

watch(
  () => chat.state.turns.length,
  async () => {
    if (!chat.state.streaming) return
    stick = true
    await nextTick()
    scrollToBottom()
  },
)

function onFeedback(turn: AssistantTurnModel, value: 'up' | 'down' | null) {
  chat.setFeedback(turn, value)
}
</script>

<template>
  <div ref="scroller" class="scroll" @scroll="onScroll">
    <div v-show="hasTurns" ref="thread" class="thread">
      <template v-for="turn in chat.state.turns" :key="turn.id">
        <UserBubble v-if="turn.kind === 'user'" :text="turn.text" />
        <AssistantTurn
          v-else
          :turn="turn"
          :on-regenerate="chat.regenerate"
          :on-feedback="(value) => onFeedback(turn, value)"
        />
      </template>
    </div>

    <div v-if="chat.state.messagesError" class="turn-error thread-error">
      {{ chat.state.messagesError }}
    </div>

    <EmptyState v-if="!hasTurns && !chat.state.loadingMessages" />
    <div v-if="chat.state.loadingMessages" class="thread-loading">正在加载历史消息…</div>
  </div>
</template>
