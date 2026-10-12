<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { NAvatar, NButton, NIcon, NSpin, NText, useMessage } from 'naive-ui'
import { ChevronBackOutline, MailOutline } from '@vicons/ionicons5'
import { getMessageDetail } from '../api/agent'
import type { MessageItem } from '../api/types'
import { useAgentsStore } from '../stores/agents'
import MarkdownBlock from '../components/MarkdownBlock.vue'

const route = useRoute()
const router = useRouter()
const message = useMessage()
const agents = useAgentsStore()

const detail = ref<MessageItem | null>(null)
const loading = ref(true)

function senderInfo(sender: string) {
  if (!sender) return { name: '系统通知', avatar: '', initial: '' }
  const agent = agents.state.agents.find((a) => a.name === sender)
  return {
    name: agent?.displayName || sender,
    avatar: agent?.avatar || '',
    initial: (agent?.displayName || sender).slice(0, 1),
  }
}

function formatTime(value?: string) {
  return value ? value.replace('T', ' ').slice(0, 19) : ''
}

onMounted(async () => {
  const id = Number(route.params.id)
  try {
    detail.value = await getMessageDetail(id)
  } catch (error) {
    message.error(error instanceof Error ? error.message : '加载消息失败')
  } finally {
    loading.value = false
  }
})
</script>

<template>
  <div class="p-4 pb-12 md:p-6 md:pb-12">
    <div class="max-w-3xl mx-auto flex flex-col gap-4">
      <div>
        <NButton quaternary size="small" @click="router.push('/messages')">
          <template #icon>
            <NIcon><ChevronBackOutline /></NIcon>
          </template>
          返回列表
        </NButton>
      </div>

      <div v-if="loading" class="flex justify-center py-16">
        <NSpin />
      </div>

      <div v-else-if="detail" class="detail-card">
        <div class="detail-head">
          <NAvatar v-if="senderInfo(detail.sender).avatar" round :size="40" :src="senderInfo(detail.sender).avatar" />
          <NAvatar v-else round :size="40" :class="detail.sender ? 'av-agent' : 'av-system'">
            <NIcon v-if="!detail.sender" :size="19"><MailOutline /></NIcon>
            <template v-else>{{ senderInfo(detail.sender).initial }}</template>
          </NAvatar>
          <div class="min-w-0">
            <div class="detail-title">{{ detail.title }}</div>
            <NText depth="3" class="text-xs">
              {{ senderInfo(detail.sender).name }} · {{ formatTime(detail.createTime) }}
            </NText>
          </div>
        </div>
        <!-- 与对话消息同一套 Markdown 渲染（含 echarts 图表块） -->
        <div class="detail-body">
          <MarkdownBlock :markdown="detail.content || ''" />
        </div>
      </div>

      <div v-else class="empty">消息不存在或已被删除</div>
    </div>
  </div>
</template>

<style scoped>
.detail-card {
  background: var(--surface-elevated);
  border: 1px solid var(--divider);
  border-radius: 12px;
  overflow: hidden;
}

.detail-head {
  display: flex;
  align-items: flex-start;
  gap: 12px;
  padding: 18px 22px 14px;
  border-bottom: 1px solid var(--divider);
}

.detail-title {
  font-size: 17px;
  font-weight: 700;
  margin-bottom: 4px;
}

.detail-body {
  padding: 16px 22px 24px;
}

.av-system {
  background: linear-gradient(135deg, #64748b, #94a3b8);
  color: #fff;
}

.av-agent {
  background: linear-gradient(135deg, var(--brand-solid), var(--brand-solid-alt));
  color: #fff;
}

.empty {
  padding: 48px 0;
  text-align: center;
  font-size: 13px;
  opacity: 0.5;
}
</style>
