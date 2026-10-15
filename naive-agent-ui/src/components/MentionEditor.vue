<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { getDatasources, getSkills } from '../api/agent'
import type { DatasourceInfo, PluginInfo, SkillInfo } from '../api/types'
import { useAgentsStore } from '../stores/agents'
import { usePluginsStore } from '../stores/plugins'
import { mentionDataValue, mentionIconSvg, parseMentions, segmentFromDataValue, stringifySegments, type MentionSegment } from '../utils/mentions'

interface MentionItem {
  kind: 'skill' | 'ds'
  value: string
  label: string
}

const props = defineProps<{ modelValue: string; placeholder?: string; pluginActive?: boolean }>()
const emit = defineEmits<{
  'update:modelValue': [value: string]
  submit: []
  'paste-files': [files: File[]]
  'plugin-pick': [info: PluginInfo]
  'plugin-clear': []
}>()

const agents = useAgentsStore()
const pluginsStore = usePluginsStore()

const editorRef = ref<HTMLElement>()
const skills = ref<SkillInfo[]>([])
const datasources = ref<DatasourceInfo[]>([])
let listsRequested = false
let lastEmitted = ''

const picker = ref({ open: false, query: '' })
const activeIndex = ref(0)

/* ---------- 插件（/ 开头，单选） ---------- */

const slash = ref({ open: false, query: '' })
const pluginActiveIndex = ref(0)

/** 能力开关：技能未加载到（undefined）按可用处理，只有显式 false 才隐藏技能分组 */
const skillsEnabled = computed(() => agents.currentAgent?.skills !== false)

/* ---------- 分段渲染与序列化 ---------- */

function escapeHtml(value: string): string {
  return value.replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;')
}

function chipHtml(kind: 'skill' | 'ds', value: string): string {
  const label = kind === 'skill' ? `skill:${value}` : `ds:${value}`
  return `<span contenteditable="false" class="mention-tag mention-tag--${kind}" data-m="${mentionDataValue(kind, value)}">${mentionIconSvg(kind)}${escapeHtml(label)}<span class="mention-tag-x" role="button" aria-label="删除">×</span></span>`
}

function renderSegments(segments: MentionSegment[]): string {
  return segments
    .map((seg) => {
      if (seg.type === 'skill') return chipHtml('skill', seg.name)
      if (seg.type === 'ds') return chipHtml('ds', seg.id)
      return escapeHtml(seg.text)
    })
    .join('')
}

/** 从编辑器 DOM 重建分段（chip 用 data-m，其余为文本；<br> 记作换行） */
function domSegments(root: HTMLElement): MentionSegment[] {
  const segments: MentionSegment[] = []
  const visit = (node: Node): void => {
    if (node.nodeType === Node.TEXT_NODE) {
      const text = node.nodeValue ?? ''
      if (text) segments.push({ type: 'text', text })
      return
    }
    if (node.nodeType !== Node.ELEMENT_NODE) return
    const el = node as HTMLElement
    if (el.hasAttribute('data-m')) {
      segments.push(segmentFromDataValue(el.getAttribute('data-m')!))
      return
    }
    if (el.tagName === 'BR') {
      segments.push({ type: 'text', text: '\n' })
      return
    }
    for (const child of Array.from(el.childNodes)) visit(child)
  }
  for (const child of Array.from(root.childNodes)) visit(child)
  return segments
}

function render() {
  const el = editorRef.value
  if (!el) return
  el.innerHTML = renderSegments(parseMentions(props.modelValue || ''))
  lastEmitted = props.modelValue || ''
}

function sync() {
  const el = editorRef.value
  if (!el) return
  const text = stringifySegments(domSegments(el))
  if (text === lastEmitted) return
  lastEmitted = text
  emit('update:modelValue', text)
}

watch(
  () => props.modelValue,
  (value) => {
    if (value !== lastEmitted) render()
  },
)

/* ---------- @ 检测与下拉 ---------- */

const keyword = computed(() => picker.value.query)

const visibleSkills = computed(() => {
  if (!skillsEnabled.value || keyword.value.startsWith('ds:')) return []
  const k = (keyword.value.startsWith('skill:') ? keyword.value.slice(6) : keyword.value).toLowerCase()
  return skills.value.filter(
    (s) => !k || s.name.toLowerCase().includes(k) || (s.description ?? '').toLowerCase().includes(k),
  )
})

const visibleDatasources = computed(() => {
  if (keyword.value.startsWith('skill:')) return []
  const k = (keyword.value.startsWith('ds:') ? keyword.value.slice(3) : keyword.value).toLowerCase()
  return datasources.value.filter(
    (d) =>
      !k ||
      d.id.toLowerCase().includes(k) ||
      (d.name ?? '').toLowerCase().includes(k) ||
      (d.description ?? '').toLowerCase().includes(k),
  )
})

const flatCount = computed(() => visibleSkills.value.length + visibleDatasources.value.length)
const popupOpen = computed(() => picker.value.open && flatCount.value > 0)

async function ensureLists() {
  if (listsRequested) return
  listsRequested = true
  try {
    const [s, d] = await Promise.all([
      skillsEnabled.value ? getSkills(agents.state.current || undefined) : Promise.resolve([]),
      getDatasources(),
    ])
    skills.value = s ?? []
    datasources.value = d ?? []
  } catch {
    listsRequested = false
  }
}

function currentMentionQuery(): string | null {
  const sel = window.getSelection()
  if (!sel || sel.rangeCount === 0) return null
  const range = sel.getRangeAt(0)
  const node = range.startContainer
  if (node.nodeType !== Node.TEXT_NODE) return null
  if (!editorRef.value?.contains(node)) return null
  const before = (node.nodeValue ?? '').slice(0, range.startOffset)
  const match = /@([\w:.-]*)$/.exec(before)
  return match ? match[1] : null
}

let lastQuery: string | null = null
function refreshMention() {
  const query = currentMentionQuery()
  if (query === null) {
    picker.value.open = false
    lastQuery = null
    return
  }
  if (!listsRequested) void ensureLists()
  picker.value.open = true
  picker.value.query = query
  if (query !== lastQuery) activeIndex.value = 0
  lastQuery = query
}

/* ---------- / 插件检测与下拉 ---------- */

const visiblePlugins = computed(() => {
  const q = slash.value.query.toLowerCase()
  return pluginsStore.state.plugins.filter(
    (p) =>
      !q || p.id.toLowerCase().includes(q) || p.name.toLowerCase().includes(q) || (p.description ?? '').toLowerCase().includes(q),
  )
})
const pluginPopupOpen = computed(() => slash.value.open && visiblePlugins.value.length > 0)

function refreshSlash() {
  if (props.pluginActive) {
    slash.value.open = false
    return
  }
  const el = editorRef.value
  const text = el ? stringifySegments(domSegments(el)) : ''
  if (text.startsWith('/') && !/\s/.test(text)) {
    slash.value.open = true
    slash.value.query = text.slice(1)
    pluginActiveIndex.value = 0
    void pluginsStore.loadPlugins()
  } else {
    slash.value.open = false
  }
}

function pickPlugin(info: PluginInfo) {
  slash.value.open = false
  const el = editorRef.value
  if (el) el.innerHTML = ''
  lastEmitted = ''
  emit('update:modelValue', '')
  emit('plugin-pick', info)
  el?.focus()
}

function onInput() {
  sync()
  refreshMention()
  refreshSlash()
}

/* ---------- 插入 / 删除 chip ---------- */

function insertMention(item: MentionItem) {
  const sel = window.getSelection()
  if (!sel || sel.rangeCount === 0) {
    picker.value.open = false
    return
  }
  const range = sel.getRangeAt(0)
  const node = range.startContainer
  if (node.nodeType === Node.TEXT_NODE) {
    const text = node.nodeValue ?? ''
    const before = text.slice(0, range.startOffset)
    const match = /@([\w:.-]*)$/.exec(before)
    if (match) {
      range.setStart(node, before.length - match[0].length)
      range.setEnd(node, before.length)
      range.deleteContents()
    }
  }
  const chip = document.createElement('span')
  chip.setAttribute('contenteditable', 'false')
  chip.setAttribute('data-m', mentionDataValue(item.kind, item.value))
  chip.className = `mention-tag mention-tag--${item.kind}`
  chip.innerHTML = `${mentionIconSvg(item.kind)}${escapeHtml(item.label)}<span class="mention-tag-x" role="button" aria-label="删除">×</span>`
  range.insertNode(chip)
  range.setStartAfter(chip)
  range.collapse(true)
  sel.removeAllRanges()
  sel.addRange(range)
  picker.value.open = false
  editorRef.value?.focus()
  sync()
}

function removeChip(chip: HTMLElement) {
  const range = document.createRange()
  range.setStartAfter(chip)
  range.collapse(true)
  chip.remove()
  const sel = window.getSelection()
  sel?.removeAllRanges()
  sel?.addRange(range)
  editorRef.value?.focus()
  sync()
}

function onEditorClick(event: MouseEvent) {
  const target = event.target as HTMLElement
  const x = target.closest('.mention-tag-x')
  if (!x) return
  event.preventDefault()
  const chip = x.closest('.mention-tag') as HTMLElement | null
  if (chip && editorRef.value?.contains(chip)) removeChip(chip)
}

function onKeydown(event: KeyboardEvent) {
  if (event.isComposing) return
  if (event.key === 'Backspace' && props.pluginActive) {
    const el = editorRef.value
    const text = el ? stringifySegments(domSegments(el)) : ''
    if (text.trim() === '') {
      event.preventDefault()
      emit('plugin-clear')
      return
    }
  }
  if (pluginPopupOpen.value) {
    if (event.key === 'ArrowDown') {
      event.preventDefault()
      pluginActiveIndex.value = (pluginActiveIndex.value + 1) % visiblePlugins.value.length
      return
    }
    if (event.key === 'ArrowUp') {
      event.preventDefault()
      pluginActiveIndex.value = (pluginActiveIndex.value - 1 + visiblePlugins.value.length) % visiblePlugins.value.length
      return
    }
    if (event.key === 'Escape') {
      event.preventDefault()
      slash.value.open = false
      return
    }
    if (event.key === 'Enter' || event.key === 'Tab') {
      event.preventDefault()
      if (visiblePlugins.value.length) pickPlugin(visiblePlugins.value[pluginActiveIndex.value])
      return
    }
  }
  if (popupOpen.value) {
    if (event.key === 'ArrowDown') {
      event.preventDefault()
      activeIndex.value = (activeIndex.value + 1) % flatCount.value
      return
    }
    if (event.key === 'ArrowUp') {
      event.preventDefault()
      activeIndex.value = (activeIndex.value - 1 + flatCount.value) % flatCount.value
      return
    }
    if (event.key === 'Escape') {
      event.preventDefault()
      picker.value.open = false
      return
    }
    if (event.key === 'Enter' || event.key === 'Tab') {
      event.preventDefault()
      const active = flatCount.value ? activeItem() : null
      if (active) insertMention(active)
      return
    }
  }
  if (event.key === 'Enter' && !event.shiftKey) {
    event.preventDefault()
    emit('submit')
  }
}

function activeItem(): MentionItem | null {
  if (activeIndex.value < visibleSkills.value.length) {
    const s = visibleSkills.value[activeIndex.value]
    return { kind: 'skill', value: s.name, label: `skill:${s.name}` }
  }
  const d = visibleDatasources.value[activeIndex.value - visibleSkills.value.length]
  return d ? { kind: 'ds', value: d.id, label: `ds:${d.id}` } : null
}

function skillItem(s: SkillInfo): MentionItem {
  return { kind: 'skill', value: s.name, label: `skill:${s.name}` }
}

function dsItem(d: DatasourceInfo): MentionItem {
  return { kind: 'ds', value: d.id, label: `ds:${d.id}` }
}

function onPaste(event: ClipboardEvent) {
  const files = Array.from(event.clipboardData?.files ?? [])
  if (files.length) {
    event.preventDefault()
    emit('paste-files', files)
  }
}

// 切换 Agent 后技能集可能不同：清缓存，下次 @ 重新拉取
watch(
  () => agents.state.current,
  () => {
    listsRequested = false
    skills.value = []
  },
)

onMounted(render)

function focus() {
  editorRef.value?.focus()
}
defineExpose({ focus })
</script>

<template>
  <div class="mention-editor-wrap">
    <div
      ref="editorRef"
      class="mention-editor"
      contenteditable="true"
      role="textbox"
      aria-multiline="true"
      :data-placeholder="placeholder"
      @input="onInput"
      @keydown="onKeydown"
      @click="onEditorClick"
      @paste="onPaste"
    ></div>

    <div v-if="pluginPopupOpen" class="mention-popup" role="listbox" aria-label="插件">
      <div class="mention-group">
        <div class="mention-group-title">插件</div>
        <div
          v-for="(p, i) in visiblePlugins"
          :key="`plugin-${p.id}`"
          class="mention-item"
          :class="{ active: i === pluginActiveIndex }"
          role="option"
          :aria-selected="i === pluginActiveIndex"
          @mousedown.prevent="pickPlugin(p)"
          @mouseenter="pluginActiveIndex = i"
        >
          <span class="mention-token plugin">
            <span class="mention-icon" v-html="mentionIconSvg('plugin')"></span>
            {{ p.name }}
          </span>
          <span class="mention-desc">{{ p.description }}</span>
        </div>
      </div>
    </div>

    <div v-if="popupOpen" class="mention-popup" role="listbox" aria-label="技能与数据源">
      <div v-if="visibleSkills.length" class="mention-group">
        <div class="mention-group-title">技能</div>
        <div
          v-for="(s, i) in visibleSkills"
          :key="`skill-${s.name}`"
          class="mention-item"
          :class="{ active: i === activeIndex }"
          role="option"
          :aria-selected="i === activeIndex"
          @mousedown.prevent="insertMention(skillItem(s))"
          @mouseenter="activeIndex = i"
        >
          <span class="mention-token skill">
            <span class="mention-icon" v-html="mentionIconSvg('skill')"></span>
            skill:{{ s.name }}
          </span>
          <span class="mention-desc">{{ s.description }}</span>
        </div>
      </div>

      <div v-if="visibleDatasources.length" class="mention-group">
        <div class="mention-group-title">数据源</div>
        <div
          v-for="(d, i) in visibleDatasources"
          :key="`ds-${d.id}`"
          class="mention-item"
          :class="{ active: i + visibleSkills.length === activeIndex }"
          role="option"
          :aria-selected="i + visibleSkills.length === activeIndex"
          @mousedown.prevent="insertMention(dsItem(d))"
          @mouseenter="activeIndex = i + visibleSkills.length"
        >
          <span class="mention-token ds">
            <span class="mention-icon" v-html="mentionIconSvg('ds')"></span>
            ds:{{ d.id }}
          </span>
          <span class="mention-desc">{{ d.name }}<template v-if="d.description"> · {{ d.description }}</template></span>
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped>
.mention-editor-wrap {
  position: relative;
}

.mention-editor {
  min-height: 44px;
  max-height: 220px;
  overflow-y: auto;
  outline: none;
  white-space: pre-wrap;
  word-break: break-word;
  font-size: 14px;
  line-height: 1.7;
}

.mention-editor:empty::before {
  content: attr(data-placeholder);
  color: var(--text-muted, rgba(148, 163, 184, 0.7));
  pointer-events: none;
}

.mention-popup {
  position: absolute;
  left: 0;
  right: 0;
  bottom: calc(100% + 6px);
  max-height: 280px;
  overflow-y: auto;
  border: 1px solid var(--brand-border);
  border-radius: 10px;
  background: var(--surface-elevated);
  box-shadow: var(--shadow-float);
  z-index: 30;
}

.mention-group-title {
  padding: 8px 12px 4px;
  font-size: 12px;
  font-weight: 600;
  opacity: 0.55;
}

.mention-item {
  display: flex;
  align-items: baseline;
  gap: 10px;
  padding: 7px 12px;
  cursor: pointer;
  font-size: 13px;
}

.mention-item.active {
  background: var(--brand-soft-strong);
}

.mention-token {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  font-weight: 600;
  flex-shrink: 0;
}

.mention-token.skill {
  color: var(--brand);
}

.mention-token.ds {
  color: #22c55e;
}

.mention-token.plugin {
  color: var(--mention-plugin-fg);
}

.mention-desc {
  opacity: 0.6;
  font-size: 12px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
</style>