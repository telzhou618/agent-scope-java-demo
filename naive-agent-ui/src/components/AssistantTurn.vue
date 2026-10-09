<script setup lang="ts">
import { computed, ref } from 'vue'
import { NAlert, NButton, NIcon, NSpin, NTooltip } from 'naive-ui'
import {
  CopyOutline,
  HandLeftOutline,
  HandRightOutline,
  RefreshOutline,
  SparklesOutline,
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
    <!-- 请求已发出但还没收到任何事件时，先给出「正在思考…」，避免空白 -->
    <div
      v-if="props.turn.streaming && !props.turn.blocks.length && !props.turn.error"
      class="pending"
    >
      <NSpin size="small" />
      <NIcon size="14" color="#6366f1"><SparklesOutline /></NIcon>
      <span>正在思考…</span>
    </div>

    <template v-for="(block, index) in props.turn.blocks" :key="index">
      <ThinkingBlock v-if="block.kind === 'thinking'" :block="block" />
      <ToolCallCard v-else-if="block.kind === 'tool'" :block="block" />
      <MarkdownBlock v-else :markdown="block.markdown" :streaming="props.turn.streaming" />
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
              <NIcon><HandRightOutline /></NIcon>
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
              <NIcon><HandLeftOutline /></NIcon>
            </template>
          </NButton>
        </template>
        {{ feedbackDisabled ? '本条回复无法反馈' : '没帮助' }}
      </NTooltip>
    </div>
  </div>
</template>

<style scoped>
.pending {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 6px 0;
  font-size: 13px;
  opacity: 0.65;
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
</style>
