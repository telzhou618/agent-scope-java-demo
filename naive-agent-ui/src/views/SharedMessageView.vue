<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useRoute } from 'vue-router'
import { NAvatar, NIcon, NSpin, NText } from 'naive-ui'
import { MailOutline } from '@vicons/ionicons5'
import { getSharedMessage } from '../api/agent'
import type { MessageItem } from '../api/types'
import MarkdownBlock from '../components/MarkdownBlock.vue'

const route = useRoute()

const detail = ref<MessageItem | null>(null)
const loading = ref(true)
const error = ref('')

function senderName(sender: string) {
  return sender || '系统通知'
}

function formatTime(value?: string) {
  return value ? value.replace('T', ' ').slice(0, 19) : ''
}

onMounted(async () => {
  const id = Number(route.query.id)
  const expires = Number(route.query.expires)
  const sign = String(route.query.sign ?? '')
  if (!id || !expires || !sign) {
    error.value = '分享链接无效'
    loading.value = false
    return
  }
  try {
    detail.value = await getSharedMessage({ id, expires, sign })
  } catch (e) {
    error.value = e instanceof Error ? e.message : '分享链接无效或已过期'
  } finally {
    loading.value = false
  }
})
</script>

<template>
  <div class="share-page">
    <div class="share-inner">
      <div v-if="loading" class="flex justify-center py-16">
        <NSpin />
      </div>

      <div v-else-if="error" class="share-error">{{ error }}</div>

      <div v-else-if="detail" class="share-card">
        <div class="detail-head">
          <NAvatar v-if="detail.sender" round :size="40" class="av-agent">
            {{ senderName(detail.sender).slice(0, 1) }}
          </NAvatar>
          <NAvatar v-else round :size="40" class="av-system">
            <NIcon :size="19"><MailOutline /></NIcon>
          </NAvatar>
          <div class="min-w-0">
            <div class="detail-title">{{ detail.title }}</div>
            <NText depth="3" class="text-xs">
              {{ senderName(detail.sender) }} · {{ formatTime(detail.createTime) }}
            </NText>
          </div>
        </div>
        <div class="detail-body">
          <MarkdownBlock :markdown="detail.content || ''" />
        </div>
        <div class="share-foot">由 AgentScope 定时任务生成并分享</div>
      </div>
    </div>
  </div>
</template>

<style scoped>
.share-page {
  min-height: 100vh;
  background: var(--bg, #f6f7fb);
  padding: 32px 16px 60px;
}

.share-inner {
  max-width: 1200px;
  margin: 0 auto;
}

.share-card {
  background: var(--surface-elevated, #fff);
  border: 1px solid var(--divider, rgba(100, 116, 139, 0.14));
  border-radius: 12px;
  overflow: hidden;
}

.detail-head {
  display: flex;
  align-items: flex-start;
  gap: 12px;
  padding: 18px 22px 14px;
  border-bottom: 1px solid var(--divider, rgba(100, 116, 139, 0.14));
}

.detail-title {
  font-size: 17px;
  font-weight: 700;
  margin-bottom: 4px;
}

.detail-body {
  padding: 16px 22px 24px;
}

.share-foot {
  padding: 10px 22px;
  border-top: 1px solid var(--divider, rgba(100, 116, 139, 0.14));
  font-size: 12px;
  opacity: 0.5;
  text-align: center;
}

.share-error {
  padding: 60px 0;
  text-align: center;
  font-size: 14px;
  opacity: 0.6;
}

.av-system {
  background: linear-gradient(135deg, #64748b, #94a3b8);
  color: #fff;
}

.av-agent {
  background: linear-gradient(135deg, var(--brand-solid, #6366f1), var(--brand-solid-alt, #a855f7));
  color: #fff;
}
</style>
