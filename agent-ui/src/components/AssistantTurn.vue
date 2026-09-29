<script setup lang="ts">
import { ref } from 'vue'
import AppIcon from './AppIcon.vue'
import MarkdownBlock from './MarkdownBlock.vue'
import ThinkingCard from './ThinkingCard.vue'
import ToolCard from './ToolCard.vue'
import { copyText, flashLabel } from '../utils/clipboard'
import type { AssistantTurn } from '../utils/model'

const props = defineProps<{
  turn: AssistantTurn
  onRegenerate: () => void
  onFeedback: (value: 'up' | 'down' | null) => void
}>()

const copyLabel = ref<HTMLElement | null>(null)

function copyTurn() {
  const text = props.turn.blocks
    .filter((block) => block.kind === 'text')
    .map((block) => block.markdown)
    .join('\n\n')
  void copyText(text)
  if (copyLabel.value) flashLabel(copyLabel.value)
}

function vote(value: 'up' | 'down') {
  props.onFeedback(props.turn.feedback === value ? null : value)
}
</script>

<template>
  <div class="msg-assistant">
    <template v-for="(block, index) in props.turn.blocks" :key="index">
      <ThinkingCard v-if="block.kind === 'thinking'" :block="block" />
      <ToolCard v-else-if="block.kind === 'tool'" :block="block" />
      <MarkdownBlock v-else :markdown="block.markdown" />
    </template>

    <div v-if="props.turn.error" class="turn-error">
      <AppIcon name="x" :size="14" />
      <span>{{ props.turn.error }}</span>
    </div>

    <div v-if="!props.turn.streaming" class="msg-actions">
      <button ref="copyLabel" class="act-btn" type="button" @click="copyTurn">
        <AppIcon name="copy" :size="13" /><span>复制</span>
      </button>
      <button class="act-btn" type="button" @click="props.onRegenerate">
        <AppIcon name="refresh" :size="13" /><span>重新生成</span>
      </button>
      <button
        class="act-btn"
        type="button"
        :aria-pressed="props.turn.feedback === 'up'"
        @click="vote('up')"
      >
        <AppIcon name="up" :size="13" /><span>有帮助</span>
      </button>
      <button
        class="act-btn"
        type="button"
        aria-label="没帮助"
        :aria-pressed="props.turn.feedback === 'down'"
        @click="vote('down')"
      >
        <AppIcon name="down" :size="13" />
      </button>
    </div>
  </div>
</template>
