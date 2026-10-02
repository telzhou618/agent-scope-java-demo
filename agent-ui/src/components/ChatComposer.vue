<script setup lang="ts">
import { computed, nextTick, ref, watch } from 'vue'
import AppIcon from './AppIcon.vue'
import { useComposer } from '../composables/useComposer'
import { useChat } from '../stores/chat'
import { ATTACHMENT_ACCEPT, formatSize } from '../utils/attachments'

const chat = useChat()
const {
  draft,
  focusToken,
  attachments,
  uploading,
  uploadError,
  clear,
  attachFiles,
  removeAttachment,
  resetAttachments,
  allUploaded,
} = useComposer()

const textarea = ref<HTMLTextAreaElement | null>(null)
const fileInput = ref<HTMLInputElement | null>(null)

const canSend = computed(
  () =>
    !chat.state.streaming &&
    uploading.value === 0 &&
    allUploaded() &&
    (draft.value.trim().length > 0 || attachments.value.length > 0),
)

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

/** 只有附件没有文字时随附件一起走的默认文案 */
const DEFAULT_ATTACHMENT_MESSAGE = '请帮我分析这些附件'

function submit() {
  if (chat.state.streaming || uploading.value > 0) return
  const text = draft.value.trim()
  if (!text && attachments.value.length === 0) return
  const list = attachments.value.map((item) => ({ ...item }))
  clear()
  resetAttachments()
  nextTick(resize)
  void chat.send(text || DEFAULT_ATTACHMENT_MESSAGE, list)
}

function pickFiles() {
  fileInput.value?.click()
}

function onPicked(event: Event) {
  const target = event.target as HTMLInputElement
  if (target.files?.length) attachFiles(target.files)
  target.value = ''
}

/** 支持直接粘贴剪贴板中的文件（截图、资源管理器里复制的文档等），格式/大小校验交给 attachFiles */
function onPaste(event: ClipboardEvent) {
  const files = Array.from(event.clipboardData?.files ?? [])
  if (!files.length) return
  event.preventDefault()
  attachFiles(files)
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
      <div v-if="attachments.length" class="attach-chips">
        <div v-for="a in attachments" :key="a.key" class="attach-chip" :title="a.name">
          <img v-if="a.url" class="chip-thumb" :src="a.url" :alt="a.name" />
          <AppIcon v-else name="file" :size="16" />
          <span class="chip-name">{{ a.name }}</span>
          <span v-if="a.size > 0" class="chip-size">{{ formatSize(a.size) }}</span>
          <span v-if="!a.id" class="chip-loading"><AppIcon name="loader" :size="14" /></span>
          <button
            class="attach-del"
            type="button"
            aria-label="移除附件"
            @click="removeAttachment(a.key)"
          >
            <AppIcon name="x" :size="13" />
          </button>
        </div>
      </div>
      <p v-if="uploadError" class="attach-error">{{ uploadError }}</p>
      <label class="sr-only" for="input">消息输入框</label>
      <textarea
        id="input"
        ref="textarea"
        v-model="draft"
        rows="1"
        placeholder="给 Agent 发消息…"
        @keydown="onKeydown"
        @paste="onPaste"
      />
      <div class="composer-bar">
        <button class="icon-btn" type="button" aria-label="添加文件" title="添加文件" @click="pickFiles">
          <AppIcon name="clip" :size="17" />
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
      <input
        ref="fileInput"
        type="file"
        :accept="ATTACHMENT_ACCEPT"
        multiple
        class="sr-only"
        @change="onPicked"
      />
    </form>
  </div>
</template>
