<script setup lang="ts">
import { computed, nextTick, ref, watch } from 'vue'
import AppIcon from './AppIcon.vue'
import { useComposer } from '../composables/useComposer'
import { useChat } from '../stores/chat'

const chat = useChat()
const { draft, focusToken, clear } = useComposer()
const textarea = ref<HTMLTextAreaElement | null>(null)

const canSend = computed(() => !chat.state.streaming && draft.value.trim().length > 0)

function resize() {
  const element = textarea.value
  if (!element) return
  element.style.height = 'auto'
  element.style.height = `${Math.min(element.scrollHeight, 200)}px`
}

watch(draft, () => nextTick(resize))

watch(focusToken, async () => {
  await nextTick()
  textarea.value?.focus()
})

function submit() {
  if (chat.state.streaming) return
  const message = draft.value.trim()
  if (!message) return
  clear()
  nextTick(resize)
  void chat.send(message)
}

function onKeydown(event: KeyboardEvent) {
  if (event.key === 'Enter' && !event.shiftKey && !event.isComposing) {
    event.preventDefault()
    submit()
  }
}
</script>

<template>
  <div class="composer-wrap">
    <form class="composer" @submit.prevent="submit">
      <label class="sr-only" for="input">消息输入框</label>
      <textarea
        id="input"
        ref="textarea"
        v-model="draft"
        rows="1"
        placeholder="给 Agent 发消息…"
        @keydown="onKeydown"
      />
      <div class="composer-bar">
        <button class="icon-btn" type="button" aria-label="添加附件" disabled>
          <AppIcon name="clip" :size="17" />
        </button>
        <button class="icon-btn" type="button" aria-label="选择工具" disabled>
          <AppIcon name="wrench" :size="17" />
        </button>
        <span class="spacer" />
        <button
          v-if="chat.state.streaming"
          class="send-btn"
          type="button"
          aria-label="停止生成"
          title="停止生成"
          @click="chat.stop()"
        >
          <AppIcon name="stop" :size="17" />
        </button>
        <button v-else class="send-btn" type="submit" aria-label="发送" :disabled="!canSend">
          <AppIcon name="send" :size="17" />
        </button>
      </div>
    </form>
  </div>
</template>
