<script setup lang="ts">
import { computed } from 'vue'
import { NIcon } from 'naive-ui'
import { ChevronDownOutline, SparklesOutline } from '@vicons/ionicons5'
import type { ThinkingBlockModel } from '../utils/model'

const props = defineProps<{ block: ThinkingBlockModel }>()

/** 流式思考中：open 且还没回填耗时 */
const thinking = computed(() => props.block.open && !props.block.seconds)

const label = computed(() => {
  if (props.block.seconds) return `思考了 ${props.block.seconds} 秒`
  return props.block.open ? '正在思考…' : '思考过程'
})

function toggle() {
  props.block.open = !props.block.open
}
</script>

<template>
  <div class="thinking-block" :class="{ thinking }">
    <button class="thinking-toggle" type="button" :aria-expanded="props.block.open" @click="toggle">
      <NIcon size="14" class="thinking-icon" :class="{ pulsing: thinking }">
        <SparklesOutline />
      </NIcon>
      <span class="thinking-label">{{ label }}</span>
      <NIcon size="13" class="chevron" :class="{ open: props.block.open }">
        <ChevronDownOutline />
      </NIcon>
    </button>
    <!-- 网格行高过渡：展开/折叠平滑而不是瞬切 -->
    <div class="thinking-body" :class="{ open: props.block.open }">
      <div class="thinking-inner">
        <div class="thinking-content">{{ props.block.text }}</div>
      </div>
    </div>
  </div>
</template>

<style scoped>
.thinking-block {
  margin-bottom: 10px;
  border: 1px solid rgba(99, 102, 241, 0.22);
  border-radius: 10px;
  overflow: hidden;
}

.thinking-toggle {
  position: relative;
  display: flex;
  align-items: center;
  gap: 6px;
  width: 100%;
  padding: 7px 12px;
  border: 0;
  background: rgba(99, 102, 241, 0.06);
  color: inherit;
  font-size: 12.5px;
  cursor: pointer;
  opacity: 0.75;
  overflow: hidden;
  transition: opacity 0.15s;
}

.thinking-toggle:hover {
  opacity: 1;
}

/* 流式思考中：头行扫过的 shimmer 光带 */
.thinking .thinking-toggle::after {
  content: '';
  position: absolute;
  inset: 0;
  background: linear-gradient(
    100deg,
    transparent 20%,
    rgba(99, 102, 241, 0.14) 50%,
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

.thinking-icon {
  color: #6366f1;
}

.thinking-icon.pulsing {
  animation: thinking-pulse 1.2s ease-in-out infinite;
}

@keyframes thinking-pulse {
  50% {
    opacity: 0.35;
  }
}

.chevron {
  margin-left: auto;
  transition: transform 0.2s;
}

.chevron.open {
  transform: rotate(180deg);
}

.thinking-body {
  display: grid;
  grid-template-rows: 0fr;
  transition: grid-template-rows 0.25s ease;
}

.thinking-body.open {
  grid-template-rows: 1fr;
}

.thinking-inner {
  overflow: hidden;
  min-height: 0;
}

.thinking-content {
  padding: 8px 12px;
  font-size: 12.5px;
  line-height: 1.7;
  white-space: pre-wrap;
  word-break: break-word;
  opacity: 0.65;
  max-height: 280px;
  overflow-y: auto;
}

@media (prefers-reduced-motion: reduce) {
  .thinking .thinking-toggle::after,
  .thinking-icon.pulsing {
    animation: none;
  }

  .thinking-body {
    transition: none;
  }
}
</style>
