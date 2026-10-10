<script setup lang="ts">
import { computed } from 'vue'
import { NIcon } from 'naive-ui'
import { ChevronForwardOutline, SparklesOutline } from '@vicons/ionicons5'
import type { ThinkingBlockModel } from '../utils/model'

const props = defineProps<{ block: ThinkingBlockModel }>()

/** 流式思考中：未结束且处于展开态（done 优先于 open，历史消息展开不再显示"思考中"） */
const thinking = computed(() => !props.block.done && props.block.open)

/** 思考了 N 秒 / 正在思考… / 思考过程（历史无耗时回填时兜底） */
const label = computed(() => {
  if (thinking.value) return '正在思考…'
  const seconds = props.block.seconds
  if (seconds !== undefined && seconds > 0) return `思考了 ${Math.max(1, Math.round(seconds))} 秒`
  return '思考过程'
})

function toggle() {
  props.block.open = !props.block.open
}
</script>

<template>
  <div class="thinking-block">
    <button class="thinking-toggle" type="button" :aria-expanded="props.block.open" @click="toggle">
      <NIcon size="13" class="sparkle" :class="{ thinking }"><SparklesOutline /></NIcon>
      <span class="thinking-label">{{ label }}</span>
      <NIcon size="12" class="chevron" :class="{ open: props.block.open }">
        <ChevronForwardOutline />
      </NIcon>
    </button>
    <div class="thinking-body" :class="{ open: props.block.open }">
      <div class="thinking-inner">
        <div class="thinking-content">{{ props.block.text }}</div>
      </div>
    </div>
  </div>
</template>

<style scoped>
.thinking-block {
  margin-bottom: 12px;
}

.thinking-toggle {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 2px 0;
  border: 0;
  background: transparent;
  color: var(--text-faint);
  font-size: 13px;
  cursor: pointer;
}

.sparkle {
  flex-shrink: 0;
}

/* 流式思考中：图标呼吸闪烁 */
.sparkle.thinking {
  animation: sparkle-blink 1.4s ease-in-out infinite;
}

@keyframes sparkle-blink {
  0%,
  100% {
    opacity: 0.4;
  }
  50% {
    opacity: 1;
  }
}

.chevron {
  flex-shrink: 0;
  opacity: 0.7;
  transition: transform 0.2s;
}

.chevron.open {
  transform: rotate(90deg);
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

/* 展开内容：左侧一条竖线 + 灰字，无卡片无底色 */
.thinking-content {
  margin: 6px 0 4px 6px;
  padding-left: 12px;
  border-left: 1px dashed var(--divider-strong);
  color: var(--text-faint);
  font-size: 13px;
  line-height: 1.8;
  white-space: pre-wrap;
  word-break: break-word;
  max-height: 320px;
  overflow-y: auto;
}

@media (prefers-reduced-motion: reduce) {
  .sparkle.thinking {
    animation: none;
  }

  .thinking-body {
    transition: none;
  }
}
</style>
