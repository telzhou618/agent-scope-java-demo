<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { NAvatar, NButton, NIcon, NPagination, NPopconfirm, NSpin, NTag, NText, useMessage } from 'naive-ui'
import { MailOutline, TrashOutline } from '@vicons/ionicons5'
import { deleteMessage, markMessageRead, pageMessages } from '../api/agent'
import type { MessageItem } from '../api/types'
import { useAgentsStore } from '../stores/agents'
import { useWideMode } from '../composables/useWideMode'

const router = useRouter()
const message = useMessage()
const agents = useAgentsStore()
const { wide } = useWideMode()

const PAGE_SIZE = 10
const records = ref<MessageItem[]>([])
const page = ref(1)
const total = ref(0)
const loading = ref(false)

async function load() {
  loading.value = true
  try {
    const result = await pageMessages({ page: page.value, size: PAGE_SIZE })
    records.value = result.records ?? []
    total.value = result.total ?? 0
    const totalPages = Math.max(1, Math.ceil(total.value / PAGE_SIZE))
    if (page.value > totalPages) {
      page.value = totalPages
      return load()
    }
  } catch (error) {
    message.error(error instanceof Error ? error.message : '加载消息失败')
  } finally {
    loading.value = false
  }
}

/** 发送方展示：系统消息用信封图标头像；Agent 消息用档位真实头像/显示名 */
function senderInfo(sender: string) {
  if (!sender) return { name: '系统通知', avatar: '', initial: '' }
  const agent = agents.state.agents.find((a) => a.name === sender)
  return {
    name: agent?.displayName || sender,
    avatar: agent?.avatar || '',
    initial: (agent?.displayName || sender).slice(0, 1),
  }
}

/** 内容预览暂由后端 title 承担；列表接口不含正文 */
function formatTime(value?: string) {
  return value ? value.replace('T', ' ').slice(0, 16) : ''
}

async function openMessage(item: MessageItem) {
  if (!item.isRead) {
    item.isRead = true
    markMessageRead(item.id).catch(() => undefined)
  }
  await router.push(`/messages/${item.id}`)
}

async function onDelete(item: MessageItem) {
  try {
    await deleteMessage(item.id)
    records.value = records.value.filter((m) => m.id !== item.id)
    total.value = Math.max(0, total.value - 1)
    message.success('已删除')
  } catch (error) {
    message.error(error instanceof Error ? error.message : '删除失败')
  }
}

onMounted(() => void load())
</script>

<template>
  <div class="p-4 pb-12 md:p-6 md:pb-12">
    <div class="mx-auto flex flex-col gap-4" :class="wide ? 'max-w-[1200px]' : 'max-w-[800px]'">
      <div class="flex items-center gap-3">
        <span class="text-lg font-600">消息中心</span>
        <NText depth="3" class="text-xs">共 {{ total }} 条</NText>
      </div>

      <div v-if="loading && records.length === 0" class="flex justify-center py-16">
        <NSpin />
      </div>

      <div v-else-if="records.length === 0" class="empty">暂无消息</div>

      <div v-else class="msg-list">
        <div
          v-for="item in records"
          :key="item.id"
          class="msg-row"
          :class="{ unread: !item.isRead }"
          @click="openMessage(item)"
        >
          <NAvatar v-if="senderInfo(item.sender).avatar" round :size="36" :src="senderInfo(item.sender).avatar" />
          <NAvatar v-else round :size="36" :class="item.sender ? 'av-agent' : 'av-system'">
            <NIcon v-if="!item.sender" :size="18"><MailOutline /></NIcon>
            <template v-else>{{ senderInfo(item.sender).initial }}</template>
          </NAvatar>
          <div class="msg-main">
            <div class="msg-from">
              {{ senderInfo(item.sender).name }}
              <NTag v-if="item.runStatus === 'failed'" size="tiny" type="error" :bordered="false">执行失败</NTag>
            </div>
            <div class="msg-title">{{ item.title }}</div>
          </div>
          <div class="msg-right">
            <span class="msg-time">{{ formatTime(item.createTime) }}</span>
            <span class="dot" :class="{ on: !item.isRead }" />
          </div>
          <!-- 悬停行才显示的删除按钮；阻止冒泡避免触发行点击 -->
          <NPopconfirm @positive-click="onDelete(item)">
            <template #trigger>
              <NButton
                quaternary
                circle
                size="small"
                class="msg-del"
                title="删除消息"
                @click.stop
              >
                <template #icon>
                  <NIcon size="15"><TrashOutline /></NIcon>
                </template>
              </NButton>
            </template>
            确认删除这条消息？
          </NPopconfirm>
        </div>
      </div>

      <div v-if="total > PAGE_SIZE" class="flex justify-center">
        <NPagination
          v-model:page="page"
          :item-count="total"
          :page-size="PAGE_SIZE"
          @update:page="load"
        />
      </div>
    </div>
  </div>
</template>

<style scoped>
.msg-list {
  background: var(--surface-elevated);
  border: 1px solid var(--divider);
  border-radius: 12px;
  overflow: hidden;
}

.msg-row {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 13px 16px;
  cursor: pointer;
  transition: background 0.15s;
}

.msg-row + .msg-row {
  border-top: 1px solid var(--divider);
}

.msg-row:hover {
  background: var(--surface-subtle);
}

.msg-main {
  flex: 1;
  min-width: 0;
}

.msg-from {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 12px;
  font-weight: 600;
  opacity: 0.65;
}

.msg-title {
  font-size: 13.5px;
  margin-top: 2px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.msg-row.unread .msg-title {
  font-weight: 700;
}

.msg-right {
  flex: none;
  display: flex;
  flex-direction: column;
  align-items: flex-end;
  gap: 6px;
}

.msg-time {
  font-size: 12px;
  opacity: 0.5;
  font-variant-numeric: tabular-nums;
}

.dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
}

.dot.on {
  background: #ef4444;
}

/* 行悬停才显示删除按钮 */
.msg-del {
  flex: none;
  align-self: center;
  opacity: 0;
  transition: opacity 0.15s;
  color: var(--text-3, #9aa0b4);
}

.msg-row:hover .msg-del,
.msg-del:focus-visible {
  opacity: 1;
}

.msg-del:hover {
  color: #ef4444;
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
