<script setup lang="ts">
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import { NButton, NEmpty, NIcon, NPopover, NTag } from 'naive-ui'
import {
  AttachOutline,
  CheckmarkOutline,
  ChevronDownOutline,
  ConstructOutline,
  FlashOutline,
  HardwareChipOutline,
  SparklesOutline,
} from '@vicons/ionicons5'
import { useAgentsStore } from '../stores/agents'
import { useChatStore } from '../stores/chat'
import type { AgentInfo } from '../api/types'

const router = useRouter()
const agents = useAgentsStore()
const chat = useChatStore()

const open = ref(false)

/** 能力徽章：支持则实色，不支持灰显 */
const CAPABILITIES: { key: 'thinking' | 'tools' | 'mcp' | 'skills' | 'attachments'; label: string; icon: typeof SparklesOutline }[] = [
  { key: 'thinking', label: '思考', icon: SparklesOutline },
  { key: 'tools', label: '工具', icon: ConstructOutline },
  { key: 'mcp', label: 'MCP', icon: HardwareChipOutline },
  { key: 'skills', label: '技能', icon: FlashOutline },
  { key: 'attachments', label: '附件', icon: AttachOutline },
]

/** 主动切换 Agent：回写偏好并开新会话 */
function onSelect(agent: AgentInfo) {
  open.value = false
  if (agent.name === agents.state.current) return
  agents.selectAgent(agent.name)
  chat.newChat()
  router.push('/chat')
}
</script>

<template>
  <NPopover v-model:show="open" trigger="click" placement="bottom-start" :width="360" raw>
    <template #trigger>
      <NButton size="small" secondary>
        {{ agents.currentAgent?.displayName || agents.state.current }}
        <template #icon>
          <NIcon class="transition-transform" :class="{ 'rotate-180': open }">
            <ChevronDownOutline />
          </NIcon>
        </template>
      </NButton>
    </template>
    <div class="agent-panel">
      <NEmpty v-if="!agents.state.agents.length" size="small" description="无可用 Agent" class="py-6" />
      <div
        v-for="agent in agents.state.agents"
        v-else
        :key="agent.name"
        class="agent-item"
        :class="{ current: agent.name === agents.state.current }"
        @click="onSelect(agent)"
      >
        <div class="flex items-center gap-2">
          <span class="text-sm font-600">{{ agent.displayName || agent.name }}</span>
          <span class="text-xs op-45">{{ agent.model }}</span>
          <NIcon
            v-if="agent.name === agents.state.current"
            size="15"
            color="var(--brand)"
            class="ml-auto"
          >
            <CheckmarkOutline />
          </NIcon>
        </div>
        <div v-if="agent.description" class="mt-0.5 text-xs op-60 line-clamp-2">
          {{ agent.description }}
        </div>
        <div class="mt-1.5 flex flex-wrap gap-1">
          <NTag
            v-for="cap in CAPABILITIES"
            :key="cap.key"
            size="tiny"
            round
            :bordered="false"
            :type="agent[cap.key] ? 'primary' : 'default'"
            :disabled="!agent[cap.key]"
          >
            <template #icon>
              <NIcon size="11"><component :is="cap.icon" /></NIcon>
            </template>
            {{ cap.label }}
          </NTag>
        </div>
      </div>
    </div>
  </NPopover>
</template>

<style scoped>
.agent-panel {
  border-radius: 12px;
  overflow: hidden;
  /* 实色卡片底 + 边框 + 重阴影：叠在消息内容上时文字清晰可读 */
  background: var(--surface-elevated);
  border: 1px solid var(--divider-strong);
  box-shadow: var(--shadow-pop);
}

.agent-item {
  padding: 10px 14px;
  cursor: pointer;
  border-bottom: 1px solid var(--divider);
  transition: background-color 0.15s ease;
}

.agent-item:last-child {
  border-bottom: 0;
}

.agent-item:hover {
  background: var(--brand-soft);
}

.agent-item.current {
  background: var(--brand-soft-strong);
}
</style>
