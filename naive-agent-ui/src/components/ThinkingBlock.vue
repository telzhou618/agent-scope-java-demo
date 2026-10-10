<script setup lang="ts">
import { computed } from 'vue'
import { NIcon } from 'naive-ui'
import { ChevronDownOutline, SparklesOutline } from '@vicons/ionicons5'
import type { ThinkingBlockModel } from '../utils/model'

const props = defineProps<{ block: ThinkingBlockModel }>()

/** 流式思考中：未结束且处于展开态（done 优先于 open，历史消息展开不再显示"思考中"） */
const thinking = computed(() => !props.block.done && props.block.open)

const label = computed(() => (thinking.value ? '正在思考' : '思考过程'))

const secondsText = computed(() => {
  const seconds = props.block.seconds
  if (seconds === undefined || seconds <= 0) return ''
  return seconds >= 1 ? `${Math.round(seconds)}s` : `${seconds.toFixed(1)}s`
})

function toggle() {
  props.block.open = !props.block.open
}
</script>

<template>
  <div class="thinking-block" :class="{ thinking }">
    <button class="thinking-toggle" type="button" :aria-expanded="props.block.open" @click="toggle">
      <span class="thinking-badge">
        <NIcon size="13"><SparklesOutline /></NIcon>
      </span>
      <span class="thinking-label">{{ label }}</span>
      <span class="thinking-spacer" />
      <span v-if="secondsText" class="thinking-time">{{ secondsText }}</span>
      <NIcon size="13" class="chevron" :class="{ open: props.block.open }">
        <ChevronDownOutline />
      </NIcon>
    </button>
    <div class="thinking-body" :class="{ open: props.block.open }">
      <div class="thinking-inner">
        <div class="thinking-pad">
          <div class="thinking-content">{{ props.block.text }}</div>
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped>
.thinking-block {
  margin-bottom: 12px;
  border: 1px solid var(--divider);
  border-radius: 12px;
  overflow: hidden;
  background: var(--brand-soft);
}

.thinking-toggle {
  position: relative;
  display: flex;
  align-items: center;
  gap: 8px;
  width: 100%;
  padding: 8px 12px;
  border: 0;
  background: transparent;
  color: inherit;
  font-size: 13px;
  cursor: pointer;
  overflow: hidden;
}

.thinking-badge {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 22px;
  height: 22px;
  flex-shrink: 0;
  border-radius: 7px;
  background: var(--brand-soft-strong);
  color: var(--brand);
}

.thinking-label {
  font-weight: 500;
}

.thinking-spacer {
  flex: 1;
}

.thinking-time {
  padding: 1px 8px;
  border-radius: 999px;
  background: var(--surface-subtle);
  font-family: ui-monospace, SFMono-Regular, Menlo, Consolas, monospace;
  font-size: 11px;
  opacity: 0.6;
}

/* 流式思考中：头行扫过的 shimmer 光带 */
.thinking .thinking-toggle::after {
  content: '';
  position: absolute;
  inset: 0;
  background: linear-gradient(
    100deg,
    transparent 20%,
    var(--brand-ring) 50%,
    transparent 80%
  );
  background-size: 200% 100%;
  animation: thinking-shimmer 1.6s linear infinite;
  pointer-events: none;
}

@keyframes thinking-shimmer {
  from {
    background-position: 200% 0;
  }
  to {
    background-position: -200% 0;
  }
}

.chevron {
  flex-shrink: 0;
  opacity: 0.6;
  transition: transform 0.2s;
}

.chevron.open {
  transform: rotate(180deg);
}

.thinking-body {
  display: grid;
  grid-template-rows: 0fr;
  transition: grid-template-rows 0.22s ease;
}

.thinking-body.open {
  grid-template-rows: 1fr;
}

.thinking-inner {
  overflow: hidden;
  min-height: 0;
}

.thinking-pad {
  padding: 0 12px 12px;
}

.thinking-content {
  padding: 10px 12px;
  border: 1px solid var(--divider);
  border-left: 3px solid var(--brand);
  border-radius: 8px;
  background: var(--surface-elevated);
  font-size: 13px;
  line-height: 1.7;
  white-space: pre-wrap;
  word-break: break-word;
  opacity: 0.85;
  max-height: 264px;
  overflow-y: auto;
}

@media (prefers-reduced-motion: reduce) {
  .thinking .thinking-toggle::after {
    animation: none;
  }

  .thinking-body {
    transition: none;
  }
}
</style>