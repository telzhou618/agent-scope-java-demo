<script setup lang="ts">
import { computed, nextTick, ref, watch } from 'vue'
import { NButton, NIcon, NTooltip, useMessage } from 'naive-ui'
import {
  AttachOutline,
  BulbOutline,
  CloseOutline,
  DocumentOutline,
  SendOutline,
  StopOutline,
} from '@vicons/ionicons5'
import MentionEditor from './MentionEditor.vue'
import { useComposer } from '../composables/useComposer'
import { useChatStore } from '../stores/chat'
import { useAgentsStore } from '../stores/agents'
import { useAuthStore } from '../stores/auth'
import { mentionIconSvg } from '../utils/mentions'
import type { PluginInfo } from '../api/types'
import { ATTACHMENT_ACCEPT, formatSize } from '../utils/attachments'

const message = useMessage()
const chat = useChatStore()
const agents = useAgentsStore()
const auth = useAuthStore()
const {
  draft,
  focusToken,
  plugin,
  attachments,
  uploading,
  uploadError,
  clear,
  setPlugin,
  clearPlugin,
  attachFiles,
  removeAttachment,
  resetAttachments,
  allUploaded,
} = useComposer()

const editorRef = ref<InstanceType<typeof MentionEditor> | null>(null)
const fileInput = ref<HTMLInputElement | null>(null)

// 上传/校验错误统一走全局 message
watch(uploadError, (value) => {
  if (value) message.error(value)
})

watch(focusToken, async () => {
  await nextTick()
  editorRef.value?.focus()
})

const attachmentsEnabled = computed(
  () =>
    agents.currentAgent?.attachments !== false &&
    (auth.user?.isAdmin === true || auth.user?.permissions?.includes('upload_file')),
)
/** 思考模式标识：仅当前 Agent 显式开启时展示，纯只读 */
const thinkingEnabled = computed(() => agents.currentAgent?.thinking === true)

const DEFAULT_PLACEHOLDER = '给 Agent 发消息，输入 / 唤起插件，@ 选择技能和数据源…'
const placeholderText = computed(() => (plugin.value ? plugin.value.placeholder : DEFAULT_PLACEHOLDER))

const canSend = computed(
  () =>
    !chat.state.streaming &&
    uploading.value === 0 &&
    allUploaded() &&
    (draft.value.trim().length > 0 || attachments.value.length > 0 || !!plugin.value),
)

/** 只有附件没有文字时随附件一起走的默认文案 */
const DEFAULT_ATTACHMENT_MESSAGE = '请帮我分析这些附件'

function submit() {
  if (chat.state.streaming || uploading.value > 0) return
  const raw = draft.value.trim()
  if (!raw && attachments.value.length === 0 && !plugin.value) return
  const list = attachments.value.map((item) => ({ ...item }))
  const text = raw || (list.length > 0 ? DEFAULT_ATTACHMENT_MESSAGE : '')
  const selected = plugin.value
  const pluginMeta = selected
    ? { id: selected.id, name: selected.name, description: selected.description, args: text }
    : undefined
  clear()
  resetAttachments()
  void chat.send(text, list, { plugin: pluginMeta })
}

function onPluginPick(info: PluginInfo) {
  setPlugin(info)
}

function pickFiles() {
  fileInput.value?.click()
}

function onPicked(event: Event) {
  const target = event.target as HTMLInputElement
  if (target.files?.length) attachFiles(target.files)
  target.value = ''
}

function onPasteFiles(files: File[]) {
  if (attachmentsEnabled.value) attachFiles(files)
}
</script>

<template>
  <div class="composer">
    <div v-if="attachments.length" class="mb-2 flex flex-wrap gap-2">
      <div v-for="a in attachments" :key="a.key" class="attach-chip" :title="a.name">
        <img v-if="a.url" class="chip-thumb" :src="a.url" :alt="a.name" />
        <NIcon v-else size="15" class="shrink-0"><DocumentOutline /></NIcon>
        <span class="chip-name">{{ a.name }}</span>
        <span v-if="a.size > 0" class="chip-size">{{ formatSize(a.size) }}</span>
        <span v-if="!a.id" class="chip-loading" />
        <button class="chip-del" type="button" aria-label="移除附件" @click="removeAttachment(a.key)">
          <NIcon size="12"><CloseOutline /></NIcon>
        </button>
      </div>
    </div>

    <div class="composer-input-row">
      <span v-if="plugin" class="plugin-prefix">
        <span class="mention-tag mention-tag--plugin">
          <span class="mention-icon" v-html="mentionIconSvg('plugin')"></span>
          <span>{{ plugin.name }}</span>
          <span class="mention-tag-x" role="button" aria-label="移除插件" @click="clearPlugin">×</span>
        </span>
        <span class="plugin-colon">：</span>
      </span>
      <MentionEditor
        ref="editorRef"
        v-model="draft"
        class="flex-1"
        :placeholder="placeholderText"
        :plugin-active="!!plugin"
        @submit="submit"
        @paste-files="onPasteFiles"
        @plugin-pick="onPluginPick"
        @plugin-clear="clearPlugin"
      />
    </div>

    <div class="composer-bar">
      <NTooltip v-if="attachmentsEnabled">
        <template #trigger>
          <NButton quaternary circle class="bar-btn-left" @click="pickFiles">
            <template #icon>
              <NIcon><AttachOutline /></NIcon>
            </template>
          </NButton>
        </template>
        添加文件（支持粘贴）
      </NTooltip>
      <NTooltip v-if="thinkingEnabled">
        <template #trigger>
          <span class="thinking-badge">
            <NIcon size="13"><BulbOutline /></NIcon>
            已开启思考
          </span>
        </template>
        当前 Agent 已开启思考模式
      </NTooltip>
      <span class="flex-1" />
      <NTooltip v-if="chat.state.streaming">
        <template #trigger>
          <NButton type="error" circle @click="chat.stop()">
            <template #icon>
              <NIcon><StopOutline /></NIcon>
            </template>
          </NButton>
        </template>
        停止生成
      </NTooltip>
      <NTooltip v-else>
        <template #trigger>
          <NButton type="primary" circle :disabled="!canSend" @click="submit">
            <template #icon>
              <NIcon><SendOutline /></NIcon>
            </template>
          </NButton>
        </template>
        发送
      </NTooltip>
    </div>

    <input
      ref="fileInput"
      type="file"
      :accept="ATTACHMENT_ACCEPT"
      multiple
      class="hidden"
      @change="onPicked"
    />
  </div>
</template>

<style scoped>
.composer {
  position: relative;
  padding: 10px 12px;
  border: 1px solid var(--brand-border);
  border-radius: 16px;
  background: var(--surface-elevated);
  box-shadow:
    0 1px 2px rgba(15, 23, 42, 0.04),
    0 6px 20px rgba(15, 23, 42, 0.06);
  transition:
    border-color 0.2s,
    box-shadow 0.2s;
}

.composer:focus-within {
  border-color: var(--brand);
  box-shadow:
    0 0 0 3px var(--brand-ring),
    0 6px 20px rgba(15, 23, 42, 0.06);
}

.composer-input-row {
  display: flex;
  align-items: flex-start;
}

.plugin-prefix {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  flex-shrink: 0;
  margin-right: 4px;
  padding-top: 2px;
  font-size: 14px;
}

.plugin-colon {
  color: var(--text-faint, #94a3b8);
  flex-shrink: 0;
}

.composer-bar {
  display: flex;
  align-items: center;
  gap: 4px;
  margin-top: 4px;
}

/* 圆形按钮图标居中，左移 8px 让图标左缘与上方文本起始线对齐 */
.bar-btn-left {
  margin-left: -8px;
}

.attach-chip {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  max-width: 260px;
  padding: 4px 8px;
  border: 1px solid var(--brand-border);
  border-radius: 8px;
  font-size: 12px;
}

.chip-thumb {
  width: 28px;
  height: 28px;
  object-fit: cover;
  border-radius: 6px;
}

.chip-name {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.chip-size {
  opacity: 0.55;
  flex-shrink: 0;
}

.chip-loading {
  width: 12px;
  height: 12px;
  border: 2px solid var(--brand-border);
  border-top-color: var(--brand);
  border-radius: 50%;
  animation: chip-spin 0.8s linear infinite;
  flex-shrink: 0;
}

@keyframes chip-spin {
  to { transform: rotate(360deg); }
}

.chip-del {
  display: inline-flex;
  align-items: center;
  border: 0;
  background: transparent;
  color: inherit;
  opacity: 0.55;
  cursor: pointer;
  padding: 2px;
}

.chip-del:hover {
  opacity: 1;
}

/* 「已开启思考」只读标识 */
.thinking-badge {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  margin-left: 6px;
  padding: 2px 9px;
  border-radius: 999px;
  background: var(--brand-soft-strong);
  color: var(--brand);
  font-size: 12px;
  white-space: nowrap;
  user-select: none;
  cursor: default;
}
</style>