<script setup lang="ts">
import { computed } from 'vue'
import { NIcon } from 'naive-ui'
import { ChevronDownOutline, SparklesOutline } from '@vicons/ionicons5'
import type { ThinkingBlockModel } from '../utils/model'

const props = defineProps<{ block: ThinkingBlockModel }>()

const label = computed(() => {
  if (props.block.seconds) return `思考了 ${props.block.seconds} 秒`
  return props.block.open ? '正在思考…' : '思考过程'
})

function toggle() {
  props.block.open = !props.block.open
}
</script>

<template>
  <div class="thinking-block">
    <button class="thinking-toggle" type="button" :aria-expanded="props.block.open" @click="toggle">
      <NIcon size="14" class="thinking-icon" :class="{ pulsing: props.block.open }">
        <SparklesOutline />
      </NIcon>
      <span>{{ label }}</span>
      <NIcon size="13" class="chevron" :class="{ open: props.block.open }">
        <ChevronDownOutline />
      </NIcon>
    </button>
    <div v-show="props.block.open" class="thinking-content">{{ props.block.text }}</div>
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
}

.thinking-toggle:hover {
  opacity: 1;
}

.thinking-icon {
  color: #6366f1;
}

.thinking-icon.pulsing {
  animation: thinking-pulse 1.2s ease-in-out infinite;
}

@keyframes thinking-pulse {
  50% { opacity: 0.35; }
}

.chevron {
  margin-left: auto;
  transition: transform 0.2s;
}

.chevron.open {
  transform: rotate(180deg);
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
</style>
