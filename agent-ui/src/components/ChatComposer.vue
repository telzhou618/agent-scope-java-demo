<script setup lang="ts">
import { computed, nextTick, ref, watch } from 'vue'
import AppIcon from './AppIcon.vue'
import { useComposer } from '../composables/useComposer'
import { useChat } from '../stores/chat'
import { getSkills } from '../api/agent'
import type { SkillInfo } from '../api/types'
import { ATTACHMENT_ACCEPT, formatSize } from '../utils/attachments'
import { fileIconName } from '../utils/icons'

const chat = useChat()
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

const textarea = ref<HTMLTextAreaElement | null>(null)
const fileInput = ref<HTMLInputElement | null>(null)

const canSend = computed(
  () =>
    !chat.state.streaming &&
    uploading.value === 0 &&
    allUploaded() &&
    (draft.value.trim().length > 0 || attachments.value.length > 0 || !!skill.value),
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
  const raw = draft.value.trim()
  // 选中技能时拼回 /skill:<name> 前缀，后端据此提示 Agent 使用该技能
  const text = skill.value ? `/skill:${skill.value}${raw ? ' ' + raw : ''}` : raw
  if (!text && attachments.value.length === 0) return
  const list = attachments.value.map((item) => ({ ...item }))
  clear()
  clearSkill()
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

/* ---------- 技能快捷指令：输入 / 唤出 ---------- */

const skills = ref<SkillInfo[]>([])
let skillsRequested = false

/** 首次输入 / 时才拉取技能列表，之后本地过滤 */
async function ensureSkills() {
  if (skillsRequested) return
  skillsRequested = true
  try {
    skills.value = (await getSkills()) ?? []
  } catch {
    skillsRequested = false
  }
}

/** 正在输入指令：以 / 开头且还没输入空格；已选中技能时不再弹出 */
const commandQuery = computed(() => {
  if (skill.value) return null
  const value = draft.value
  return value.startsWith('/') && !/\s/.test(value) ? value : null
})

const matchedSkills = computed(() => {
  const query = commandQuery.value
  if (query === null) return []
  // 去掉前导 /（及可选的 skill:），对名称和描述做任意位置子串匹配
  const needle = query.toLowerCase().replace(/^\//, '').replace(/^skill:/, '')
  return skills.value.filter(
    (skill) =>
      skill.name.toLowerCase().includes(needle) ||
      skill.description.toLowerCase().includes(needle),
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
  void nextTick(() => {
    resize()
    textarea.value?.focus()
  })
}

function onKeydown(event: KeyboardEvent) {
  // 光标在输入框最前面时按退格：整体移除技能标签，而不是逐字母删除
  if (event.key === 'Backspace' && skill.value) {
    const el = textarea.value
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
  <div class="composer-wrap">
    <form class="composer" @submit.prevent="submit">
      <div v-if="popupOpen" class="skill-popup" role="listbox" aria-label="技能列表">
        <div
          v-for="(skill, i) in matchedSkills"
          :key="skill.name"
          class="skill-item"
          :class="{ active: i === activeIndex }"
          role="option"
          :aria-selected="i === activeIndex"
          @mousedown.prevent="pickSkill(skill)"
          @mouseenter="activeIndex = i"
        >
          <span class="skill-cmd">/skill:{{ skill.name }}</span>
          <span class="skill-desc">{{ skill.description }}</span>
        </div>
      </div>
      <div v-if="attachments.length" class="attach-chips">
        <div v-for="a in attachments" :key="a.key" class="attach-chip" :title="a.name">
          <img v-if="a.url" class="chip-thumb" :src="a.url" :alt="a.name" />
          <AppIcon v-else :name="fileIconName(a.ext)" :size="16" />
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
      <div class="composer-input">
        <span v-if="skill" class="skill-tag"
          >/skill:{{ skill
          }}<button
            class="skill-tag-x"
            type="button"
            aria-label="移除技能"
            title="移除技能（退格键同效）"
            @click="clearSkill"
            ><AppIcon name="x" :size="11" /></button
        ></span>
        <textarea
          id="input"
          ref="textarea"
          v-model="draft"
          rows="1"
          placeholder="给 Agent 发消息…"
          @keydown="onKeydown"
          @paste="onPaste"
        />
      </div>
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
    <p class="composer-copyright">© 2026 agent-scope-java-demo. All rights reserved.</p>
  </div>
</template>
