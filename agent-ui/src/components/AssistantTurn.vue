<script setup lang="ts">
import { computed, ref } from 'vue'
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

/** 没有消息 ID（中断/报错的回合）时反馈无处锚定，禁用 */
const feedbackDisabled = computed(() => !props.turn.messageId || !!props.turn.error)

function copyTurn() {
  const text = props.turn.blocks
    .filter((block) => block.kind === 'text')
    .map((block) => block.markdown)
    .join('\n\n')
  void copyText(text)
  if (copyLabel.value) flashLabel(copyLabel.value)
}

function vote(value: 'up' | 'down') {
  if (feedbackDisabled.value) return
  props.onFeedback(props.turn.feedback === value ? null : value)
}
</script>

<template>
  <div class="msg-assistant">
    <!-- 请求已发出但还没收到任何事件时，先给出「正在思考…」，避免空白 -->
    <div v-if="props.turn.streaming && !props.turn.blocks.length" class="thinking">
      <div class="thinking-toggle thinking-pending">
        <AppIcon name="sparkle" :size="14" />
        <span>正在思考…</span>
      </div>
    </div>

    <template v-for="(block, index) in props.turn.blocks" :key="index">
      <ThinkingCard v-if="block.kind === 'thinking'" :block="block" />
      <ToolCard v-else-if="block.kind === 'tool'" :block="block" />
      <MarkdownBlock v-else :markdown="block.markdown" :streaming="props.turn.streaming" />
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
        :disabled="feedbackDisabled"
        :title="feedbackDisabled ? '本条回复无法反馈' : '有帮助'"
        :aria-pressed="props.turn.feedback === 'up'"
        @click="vote('up')"
      >
        <AppIcon name="up" :size="13" /><span>有帮助</span>
      </button>
      <button
        class="act-btn"
        type="button"
        aria-label="没帮助"
        :disabled="feedbackDisabled"
        :title="feedbackDisabled ? '本条回复无法反馈' : '没帮助'"
        :aria-pressed="props.turn.feedback === 'down'"
        @click="vote('down')"
      >
        <AppIcon name="down" :size="13" />
      </button>
    </div>
  </div>
</template>
