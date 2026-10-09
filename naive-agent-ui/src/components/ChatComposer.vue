<script setup lang="ts">
import { computed, nextTick, ref, watch } from 'vue'
import { NButton, NIcon, NInput, NTooltip, useMessage } from 'naive-ui'
import {
  AttachOutline,
  CloseOutline,
  DocumentOutline,
  FlashOutline,
  SendOutline,
  StopOutline,
} from '@vicons/ionicons5'
import { useComposer } from '../composables/useComposer'
import { useChatStore } from '../stores/chat'
import { useAgentsStore } from '../stores/agents'
import { getSkills } from '../api/agent'
import type { SkillInfo } from '../api/types'
import { ATTACHMENT_ACCEPT, formatSize } from '../utils/attachments'

const message = useMessage()
const chat = useChatStore()
const agents = useAgentsStore()
const {
  draft,
  focusToken,
  skill,
  attachments,
  uploading,
  uploadError,
  clear,
  setSkill,
  clearSkill,
  attachFiles,
  removeAttachment,
  resetAttachments,
  allUploaded,
} = useComposer()

const inputRef = ref<InstanceType<typeof NInput> | null>(null)
const fileInput = ref<HTMLInputElement | null>(null)

// 上传/校验错误统一走全局 message
watch(uploadError, (value) => {
  if (value) message.error(value)
})

watch(focusToken, async () => {
  await nextTick()
  inputRef.value?.focus()
})

const canSend = computed(
  () =>
    !chat.state.streaming &&
    uploading.value === 0 &&
    allUploaded() &&
    (draft.value.trim().length > 0 || attachments.value.length > 0 || !!skill.value),
)

/** 只有附件没有文字时随附件一起走的默认文案 */
const DEFAULT_ATTACHMENT_MESSAGE = '请帮我分析这些附件'

function submit() {
  if (chat.state.streaming || uploading.value > 0) return
  const raw = draft.value.trim()
  // 选中技能时拼回 /skill:<name> 前缀，后端据此提示 Agent 使用该技能
  const text = skill.value ? `/skill:${skill.value}${raw ? ' ' + raw : ''}` : raw
  if (!text && attachments.value.length === 0) return
  const list = attachments.value.map((item) => ({ ...item }))
  clear()
  clearSkill()
  resetAttachments()
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
  if (!attachmentsEnabled.value) return
  const files = Array.from(event.clipboardData?.files ?? [])
  if (!files.length) return
  event.preventDefault()
  attachFiles(files)
}

/* ---------- 技能快捷指令：输入 / 唤出 ---------- */

/** 能力开关：列表未加载到时按「全部可用」处理，只有显式 false 才隐藏入口 */
const skillsEnabled = computed(() => agents.currentAgent?.skills !== false)
const attachmentsEnabled = computed(() => agents.currentAgent?.attachments !== false)

const skills = ref<SkillInfo[]>([])
let skillsRequested = false

/** 首次输入 / 时才拉取技能列表，之后本地过滤；技能按当前 agent 过滤 */
async function ensureSkills() {
  if (skillsRequested) return
  skillsRequested = true
  try {
    skills.value = (await getSkills(agents.state.current || undefined)) ?? []
  } catch {
    skillsRequested = false
  }
}

// 切换 Agent 后技能缓存作废（不同 agent 的技能集可能不同）；不支持技能时清掉已选技能
watch(
  () => agents.state.current,
  () => {
    skillsRequested = false
    skills.value = []
    if (!skillsEnabled.value) clearSkill()
  },
)

/** 正在输入指令：以 / 开头且还没输入空格；已选中技能或当前 agent 不支持技能时不再弹出 */
const commandQuery = computed(() => {
  if (!skillsEnabled.value || skill.value) return null
  const value = draft.value
  return value.startsWith('/') && !/\s/.test(value) ? value : null
})

const matchedSkills = computed(() => {
  const query = commandQuery.value
  if (query === null) return []
  // 去掉前导 /（及可选的 skill:），对名称和描述做任意位置子串匹配
  const needle = query.toLowerCase().replace(/^\//, '').replace(/^skill:/, '')
  return skills.value.filter(
    (item) =>
      item.name.toLowerCase().includes(needle) ||
      item.description.toLowerCase().includes(needle),
  )
})

const activeIndex = ref(0)
/** ESC 关闭后到下一次按键前不再弹出 */
const dismissed = ref(false)
const popupOpen = computed(() => !dismissed.value && matchedSkills.value.length > 0)

watch(commandQuery, (query) => {
  activeIndex.value = 0
  if (query !== null) void ensureSkills()
})

watch(draft, () => {
  dismissed.value = false
})

function pickSkill(skillInfo: SkillInfo) {
  setSkill(skillInfo.name)
  void nextTick(() => inputRef.value?.focus())
}

function onKeydown(event: KeyboardEvent) {
  // 光标在输入框最前面时按退格：整体移除技能标签，而不是逐字母删除
  if (event.key === 'Backspace' && skill.value) {
    const el = event.target as HTMLTextAreaElement
    if (el && el.selectionStart === 0 && el.selectionEnd === 0) {
      event.preventDefault()
      clearSkill()
      return
    }
  }
  if (popupOpen.value) {
    if (event.key === 'ArrowDown') {
      event.preventDefault()
      activeIndex.value = (activeIndex.value + 1) % matchedSkills.value.length
      return
    }
    if (event.key === 'ArrowUp') {
      event.preventDefault()
      activeIndex.value =
        (activeIndex.value - 1 + matchedSkills.value.length) % matchedSkills.value.length
      return
    }
    if (event.key === 'Escape') {
      event.preventDefault()
      dismissed.value = true
      return
    }
    if ((event.key === 'Enter' || event.key === 'Tab') && !event.isComposing) {
      event.preventDefault()
      pickSkill(matchedSkills.value[activeIndex.value])
      return
    }
  }
  if (event.key === 'Enter' && !event.shiftKey && !event.isComposing) {
    event.preventDefault()
    submit()
  }
}
</script>

<template>
  <div class="composer">
    <div v-if="popupOpen" class="skill-popup" role="listbox" aria-label="技能列表">
      <div
        v-for="(item, i) in matchedSkills"
        :key="item.name"
        class="skill-item"
        :class="{ active: i === activeIndex }"
        role="option"
        :aria-selected="i === activeIndex"
        @mousedown.prevent="pickSkill(item)"
        @mouseenter="activeIndex = i"
      >
        <span class="skill-cmd">/skill:{{ item.name }}</span>
        <span class="skill-desc">{{ item.description }}</span>
      </div>
    </div>

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

    <div v-if="skill" class="mb-1.5">
      <span class="skill-tag">
        <NIcon size="12"><FlashOutline /></NIcon>
        /skill:{{ skill }}
        <button class="skill-tag-x" type="button" aria-label="移除技能" @click="clearSkill">
          <NIcon size="11"><CloseOutline /></NIcon>
        </button>
      </span>
    </div>

    <div class="flex items-end gap-2">
      <NTooltip v-if="attachmentsEnabled">
        <template #trigger>
          <NButton quaternary circle @click="pickFiles">
            <template #icon>
              <NIcon><AttachOutline /></NIcon>
            </template>
          </NButton>
        </template>
        添加文件（支持粘贴）
      </NTooltip>
      <NInput
        ref="inputRef"
        v-model:value="draft"
        type="textarea"
        :autosize="{ minRows: 3, maxRows: 12 }"
        placeholder="给 Agent 发消息，输入 / 唤起技能…"
        @keydown="onKeydown"
        @paste="onPaste"
      />
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
  border: 1px solid rgba(99, 102, 241, 0.35);
  border-radius: 16px;
  transition:
    border-color 0.2s,
    box-shadow 0.2s;
}

.composer:focus-within {
  border-color: #6366f1;
  box-shadow: 0 0 0 3px rgba(99, 102, 241, 0.14);
}

/* 输入框自身不要再来一层边框/阴影，视觉统一由外层卡片负责 */
.composer :deep(.n-input) {
  --n-border: none !important;
  --n-border-hover: none !important;
  --n-border-focus: none !important;
  --n-box-shadow-focus: none !important;
  --n-caret-color: #6366f1 !important;
}

.skill-popup {
  position: absolute;
  left: 8px;
  right: 8px;
  bottom: calc(100% + 6px);
  max-height: 260px;
  overflow-y: auto;
  border: 1px solid rgba(99, 102, 241, 0.25);
  border-radius: 10px;
  background: var(--n-color, #fff);
  box-shadow: 0 8px 28px rgba(15, 23, 42, 0.16);
  z-index: 20;
}

html[data-theme='dark'] .skill-popup {
  background: #1f2230;
}

.skill-item {
  display: flex;
  align-items: baseline;
  gap: 10px;
  padding: 8px 12px;
  cursor: pointer;
  font-size: 13px;
}

.skill-item.active {
  background: rgba(99, 102, 241, 0.1);
}

.skill-cmd {
  font-weight: 600;
  color: #6366f1;
  flex-shrink: 0;
}

.skill-desc {
  opacity: 0.6;
  font-size: 12px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.attach-chip {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  max-width: 260px;
  padding: 4px 8px;
  border: 1px solid rgba(99, 102, 241, 0.25);
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
  border: 2px solid rgba(99, 102, 241, 0.25);
  border-top-color: #6366f1;
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

.skill-tag {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  padding: 3px 10px;
  border-radius: 999px;
  background: rgba(99, 102, 241, 0.12);
  color: #6366f1;
  font-size: 12.5px;
  font-weight: 600;
}

.skill-tag-x {
  display: inline-flex;
  align-items: center;
  border: 0;
  background: transparent;
  color: inherit;
  cursor: pointer;
  padding: 1px;
  opacity: 0.7;
}

.skill-tag-x:hover {
  opacity: 1;
}
</style>
