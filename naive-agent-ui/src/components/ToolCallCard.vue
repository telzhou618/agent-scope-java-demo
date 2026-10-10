<script setup lang="ts">
import { computed } from 'vue'
import { NIcon, NTag } from 'naive-ui'
import {
  CheckmarkOutline,
  ChevronDownOutline,
  CloseOutline,
  ConstructOutline,
  CopyOutline,
  RemoveOutline,
} from '@vicons/ionicons5'
import { copyText, flashLabel } from '../utils/clipboard'
import type { ToolBlockModel, ToolStatus } from '../utils/model'

const props = defineProps<{ block: ToolBlockModel }>()

const STATUS: Record<ToolStatus, { label: string; type: 'default' | 'info' | 'success' | 'error' }> = {
  running: { label: '运行中', type: 'info' },
  ok: { label: '成功', type: 'success' },
  error: { label: '失败', type: 'error' },
  none: { label: '无结果', type: 'default' },
}

const status = computed(() => STATUS[props.block.status])

/** 工具调用 ID 短码：去掉 call_ 前缀取末 6 位，用于区分同名并行调用；完整 ID 放 title 悬浮提示 */
const shortId = computed(() => {
  const raw = (props.block.id ?? '').replace(/^call_/, '')
  return raw.length > 6 ? raw.slice(-6) : raw
})

/** 参数/结果若为 JSON 则美化缩进展示，否则按原文本回退 */
function pretty(value: string): string {
  if (!value) return ''
  try {
    return JSON.stringify(JSON.parse(value), null, 2)
  } catch {
    return value
  }
}

const prettyArgs = computed(() => pretty(props.block.args))
const prettyOutput = computed(() => pretty(props.block.output))

async function onCopy(event: MouseEvent, text: string) {
  const button = event.currentTarget as HTMLElement
  if (!text) return
  await copyText(text)
  flashLabel(button, '已复制')
}

function toggle() {
  props.block.open = !props.block.open
}
</script>

<template>
  <div class="tool-card" :data-status="props.block.status">
    <button class="tool-head" type="button" :aria-expanded="props.block.open" @click="toggle">
      <span class="tool-badge">
        <NIcon size="14"><ConstructOutline /></NIcon>
      </span>
      <span class="tool-name">{{ props.block.name }}</span>
      <span v-if="shortId" class="tool-id" :title="`调用 ID：${props.block.id}`">#{{ shortId }}</span>
      <span class="tool-head-spacer" />
      <NTag size="tiny" :bordered="false" :type="status.type" round class="shrink-0">
        <template #icon>
          <NIcon size="12" :class="{ spinning: props.block.status === 'running' }">
            <CheckmarkOutline v-if="props.block.status === 'ok'" />
            <CloseOutline v-else-if="props.block.status === 'error'" />
            <RemoveOutline v-else-if="props.block.status === 'none'" />
            <ConstructOutline v-else />
          </NIcon>
        </template>
        {{ status.label }}
      </NTag>
      <NIcon size="13" class="chevron" :class="{ open: props.block.open }">
        <ChevronDownOutline />
      </NIcon>
    </button>
    <div class="tool-body" :class="{ open: props.block.open }">
      <div class="tool-inner">
        <div class="tool-pad">
          <div v-if="props.block.args" class="tool-section">
            <div class="tool-section-head">
              <span class="tool-section-title">参数</span>
              <button class="tool-copy" type="button" @click="onCopy($event, props.block.args)">
                <NIcon size="12"><CopyOutline /></NIcon>
                <span>复制</span>
              </button>
            </div>
            <pre class="tool-pre">{{ prettyArgs }}</pre>
          </div>
          <div v-if="props.block.output" class="tool-section">
            <div class="tool-section-head">
              <span class="tool-section-title">结果</span>
              <button class="tool-copy" type="button" @click="onCopy($event, props.block.output)">
                <NIcon size="12"><CopyOutline /></NIcon>
                <span>复制</span>
              </button>
            </div>
            <pre class="tool-pre">{{ prettyOutput }}</pre>
          </div>
          <div v-if="!props.block.args && !props.block.output" class="tool-empty">暂无内容</div>
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped>
.tool-card {
  margin-bottom: 12px;
  border: 1px solid var(--divider-strong);
  border-radius: 12px;
  overflow: hidden;
  transition:
    border-color 0.2s,
    box-shadow 0.2s;
}

/* 运行中：边框呼吸微光，落定后平滑过渡到常态/失败态 */
.tool-card[data-status='running'] {
  border-color: var(--brand-border-strong);
  animation: tool-running 1.6s ease-in-out infinite;
}

@keyframes tool-running {
  50% {
    box-shadow: 0 0 0 3px var(--brand-ring);
  }
}

.tool-card[data-status='error'] {
  border-color: rgba(224, 49, 49, 0.4);
}

.tool-head {
  display: flex;
  align-items: center;
  gap: 8px;
  width: 100%;
  padding: 8px 12px;
  border: 0;
  background: var(--surface-subtle);
  color: inherit;
  font-size: 13px;
  cursor: pointer;
}

.tool-badge {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 22px;
  height: 22px;
  flex-shrink: 0;
  border-radius: 7px;
  border: 1px solid var(--divider);
  background: var(--surface-elevated);
  color: var(--brand);
}

.tool-name {
  font-weight: 600;
  flex-shrink: 0;
}

/* 调用 ID 短码：等宽灰字，区分同名并行调用 */
.tool-id {
  flex-shrink: 0;
  font-family: ui-monospace, SFMono-Regular, Menlo, Consolas, monospace;
  font-size: 11px;
  color: var(--text-faint);
}

.tool-head-spacer {
  flex: 1;
}

.chevron {
  flex-shrink: 0;
  opacity: 0.6;
  transition: transform 0.2s;
}

.chevron.open {
  transform: rotate(180deg);
}

.spinning {
  animation: tool-spin 1s linear infinite;
}

@keyframes tool-spin {
  to { transform: rotate(360deg); }
}

.tool-body {
  display: grid;
  grid-template-rows: 0fr;
  transition: grid-template-rows 0.22s ease;
}

.tool-body.open {
  grid-template-rows: 1fr;
}

.tool-inner {
  overflow: hidden;
  min-height: 0;
}

/* padding 放在再内一层：0fr 折叠时 tool-inner 高度归 0，连 padding 一起被裁掉 */
.tool-pad {
  display: flex;
  flex-direction: column;
  gap: 10px;
  padding: 10px 12px 12px;
}

.tool-section {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.tool-section-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.tool-section-title {
  font-size: 11.5px;
  font-weight: 600;
  letter-spacing: 0.04em;
  opacity: 0.55;
}

.tool-copy {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  padding: 2px 6px;
  border: 0;
  border-radius: 6px;
  background: transparent;
  color: var(--brand);
  font-size: 12px;
  cursor: pointer;
  opacity: 0.75;
  transition:
    opacity 0.15s,
    background-color 0.15s;
}

.tool-copy:hover {
  opacity: 1;
  background: var(--brand-soft);
}

.tool-pre {
  margin: 0;
  padding: 10px 12px;
  border: 1px solid var(--divider);
  border-radius: 8px;
  background: var(--surface-subtle);
  font-family: ui-monospace, SFMono-Regular, Menlo, Consolas, monospace;
  font-size: 12px;
  line-height: 1.6;
  white-space: pre-wrap;
  word-break: break-word;
  max-height: 240px;
  overflow-y: auto;
}

.tool-empty {
  font-size: 12px;
  opacity: 0.5;
}

@media (prefers-reduced-motion: reduce) {
  .tool-card[data-status='running'] {
    animation: none;
  }

  .tool-body {
    transition: none;
  }
}
</style>