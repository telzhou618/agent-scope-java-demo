<script setup lang="ts">
import { computed, ref } from 'vue'
import { NAlert, NButton, NIcon, NTooltip } from 'naive-ui'
import {
  CopyOutline,
  RefreshOutline,
  ThumbsDownOutline,
  ThumbsUpOutline,
} from '@vicons/ionicons5'
import MarkdownBlock from './MarkdownBlock.vue'
import ThinkingBlock from './ThinkingBlock.vue'
import ToolCallCard from './ToolCallCard.vue'
import { copyText } from '../utils/clipboard'
import { useChatStore } from '../stores/chat'
import type { AssistantTurn } from '../utils/model'

const props = defineProps<{ turn: AssistantTurn }>()

const chat = useChatStore()
const copied = ref(false)

/** 没有消息 ID（中断/报错的回合）时反馈无处锚定，禁用 */
const feedbackDisabled = computed(() => !props.turn.messageId || !!props.turn.error)

function copyTurn() {
  const text = props.turn.blocks
    .filter((block) => block.kind === 'text')
    .map((block) => block.markdown)
    .join('\n\n')
  void copyText(text)
  copied.value = true
  window.setTimeout(() => (copied.value = false), 1400)
}

function vote(value: 'up' | 'down') {
  if (feedbackDisabled.value) return
  void chat.setFeedback(props.turn, props.turn.feedback === value ? null : value)
}
</script>

<template>
  <div class="assistant-turn">
    <!-- 请求已发出但还没收到任何事件时，三点跳动占位，避免空白 -->
    <div
      v-if="props.turn.streaming && !props.turn.blocks.length && !props.turn.error"
      class="pending"
    >
      <span class="pending-dots" aria-hidden="true"><i /><i /><i /></span>
      <span>正在思考…</span>
    </div>

    <template v-for="(block, index) in props.turn.blocks" :key="index">
      <div class="block-in">
        <ThinkingBlock v-if="block.kind === 'thinking'" :block="block" />
        <ToolCallCard v-else-if="block.kind === 'tool'" :block="block" />
        <MarkdownBlock v-else :markdown="block.markdown" :streaming="props.turn.streaming" />
      </div>
    </template>

    <NAlert v-if="props.turn.error" type="error" size="small" :bordered="false" class="mb-2">
      <div class="flex items-center gap-2 flex-wrap">
        <span>{{ props.turn.error }}</span>
        <NButton size="tiny" secondary type="error" @click="chat.retryFailed(props.turn)">
          <template #icon>
            <NIcon><RefreshOutline /></NIcon>
          </template>
          重试
        </NButton>
      </div>
    </NAlert>

    <div v-if="!props.turn.streaming" class="turn-actions">
      <NTooltip>
        <template #trigger>
          <NButton quaternary size="tiny" @click="copyTurn">
            <template #icon>
              <NIcon><CopyOutline /></NIcon>
            </template>
            {{ copied ? '已复制' : '复制' }}
          </NButton>
        </template>
        复制全部回答文本
      </NTooltip>
      <NTooltip>
        <template #trigger>
          <NButton quaternary size="tiny" @click="chat.regenerate()">
            <template #icon>
              <NIcon><RefreshOutline /></NIcon>
            </template>
            重新生成
          </NButton>
        </template>
        重发最后一条用户消息
      </NTooltip>
      <NTooltip>
        <template #trigger>
          <NButton
            quaternary
            size="tiny"
            :type="props.turn.feedback === 'up' ? 'primary' : 'default'"
            :disabled="feedbackDisabled"
            @click="vote('up')"
          >
            <template #icon>
              <NIcon><ThumbsUpOutline /></NIcon>
            </template>
            有帮助
          </NButton>
        </template>
        {{ feedbackDisabled ? '本条回复无法反馈' : '有帮助' }}
      </NTooltip>
      <NTooltip>
        <template #trigger>
          <NButton
            quaternary
            size="tiny"
            :type="props.turn.feedback === 'down' ? 'primary' : 'default'"
            :disabled="feedbackDisabled"
            @click="vote('down')"
          >
            <template #icon>
              <NIcon><ThumbsDownOutline /></NIcon>
            </template>
          </NButton>
        </template>
        {{ feedbackDisabled ? '本条回复无法反馈' : '没帮助' }}
      </NTooltip>
    </div>
  </div>
</template>

<style scoped>
/* 回合出现：轻微上浮淡入 */
.assistant-turn {
  animation: turn-in 0.32s ease both;
}

@keyframes turn-in {
  from {
    opacity: 0;
    transform: translateY(8px);
  }
  to {
    opacity: 1;
    transform: none;
  }
}

/* 每个新块（思考/工具/文本）出现时的轻微浮入 */
.block-in {
  animation: block-in 0.3s ease both;
}

@keyframes block-in {
  from {
    opacity: 0;
    transform: translateY(6px);
  }
  to {
    opacity: 1;
    transform: none;
  }
}

/* 等待首个事件：三点跳动占位 */
.pending {
  display: flex;
  align-items: center;
  gap: 9px;
  padding: 8px 2px;
  font-size: 13px;
  opacity: 0.7;
}

.pending-dots {
  display: inline-flex;
  gap: 4px;
}

.pending-dots i {
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: var(--brand);
  animation: dot-bounce 1.2s ease-in-out infinite;
}

.pending-dots i:nth-child(2) {
  animation-delay: 0.15s;
}

.pending-dots i:nth-child(3) {
  animation-delay: 0.3s;
}

@keyframes dot-bounce {
  0%,
  60%,
  100% {
    transform: translateY(0);
    opacity: 0.5;
  }
  30% {
    transform: translateY(-4px);
    opacity: 1;
  }
}

.turn-actions {
  display: flex;
  align-items: center;
  gap: 2px;
  margin-top: 4px;
  opacity: 0;
  transition: opacity 0.15s;
}

.assistant-turn:hover .turn-actions,
.turn-actions:focus-within {
  opacity: 1;
}

@media (prefers-reduced-motion: reduce) {
  .assistant-turn,
  .block-in,
  .pending-dots i {
    animation: none;
  }
}
</style>
