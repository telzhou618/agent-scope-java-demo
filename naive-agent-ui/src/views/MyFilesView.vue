<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { NButton, NIcon, NPopconfirm, NSpin, NText, useMessage } from 'naive-ui'
import { DownloadOutline, EyeOutline, TrashOutline } from '@vicons/ionicons5'
import { deleteFile, myFiles } from '../api/agent'
import type { UserFileItem } from '../api/types'
import { useLightbox } from '../composables/useLightbox'
import { useWideMode } from '../composables/useWideMode'
import { formatSize } from '../utils/attachments'

const message = useMessage()
const { wide } = useWideMode()
const { open: openLightbox } = useLightbox()

const IMAGE_EXTS = new Set(['png', 'jpg', 'jpeg', 'gif', 'webp', 'bmp'])

function isImage(item: UserFileItem) {
  return IMAGE_EXTS.has(item.ext)
}

function preview(item: UserFileItem) {
  openLightbox(item.downloadUrl, item.fileName)
}

const files = ref<UserFileItem[]>([])
const loading = ref(false)

async function load() {
  loading.value = true
  try {
    files.value = (await myFiles()) ?? []
  } catch (error) {
    message.error(error instanceof Error ? error.message : '加载文件列表失败')
  } finally {
    loading.value = false
  }
}

function formatTime(value?: string) {
  return value ? value.replace('T', ' ').slice(0, 19) : ''
}

function download(item: UserFileItem) {
  window.open(item.downloadUrl, '_blank')
}

async function onDelete(item: UserFileItem) {
  try {
    await deleteFile(item.id)
    files.value = files.value.filter((f) => f.id !== item.id)
    message.success('已删除')
  } catch (error) {
    message.error(error instanceof Error ? error.message : '删除失败')
  }
}

onMounted(() => void load())
</script>

<template>
  <div class="p-4 pb-12 md:p-6 md:pb-12">
    <div class="mx-auto flex flex-col gap-4" :class="wide ? 'max-w-[1000px]' : 'max-w-[800px]'">
      <div class="flex items-center gap-3">
        <span class="text-lg font-600">我的文件</span>
        <NText depth="3" class="text-xs">{{ files.length }} 个文件</NText>
      </div>

      <div v-if="loading && files.length === 0" class="flex justify-center py-16">
        <NSpin />
      </div>

      <template v-else>
        <div v-if="files.length" class="file-list">
          <div v-for="item in files" :key="item.id" class="file-row">
            <div class="file-main">
              <div class="file-name">{{ item.fileName }}</div>
              <div class="file-meta">
                <span>{{ item.ext }}</span>
                <span>· {{ formatSize(item.size) }}</span>
                <span>· {{ formatTime(item.createTime) }}</span>
              </div>
            </div>
            <NButton v-if="isImage(item)" size="tiny" secondary @click="preview(item)">
              <template #icon>
                <NIcon><EyeOutline /></NIcon>
              </template>
              预览
            </NButton>
            <NButton size="tiny" secondary @click="download(item)">
              <template #icon>
                <NIcon><DownloadOutline /></NIcon>
              </template>
              下载
            </NButton>
            <NPopconfirm @positive-click="onDelete(item)" positive-text="删除" negative-text="取消">
              <template #trigger>
                <NButton size="tiny" type="error" quaternary>
                  <template #icon>
                    <NIcon><TrashOutline /></NIcon>
                  </template>
                  删除
                </NButton>
              </template>
              删除后不可恢复，确认删除？
            </NPopconfirm>
          </div>
        </div>
        <div v-else class="empty">暂无文件</div>
      </template>
    </div>
  </div>
</template>

<style scoped>
.file-list {
  background: var(--surface-elevated);
  border: 1px solid var(--divider);
  border-radius: 12px;
  overflow: hidden;
}

.file-row {
  display: flex;
  align-items: center;
  gap: 14px;
  padding: 13px 18px;
}

.file-row + .file-row {
  border-top: 1px solid var(--divider);
}

.file-main {
  flex: 1;
  min-width: 0;
}

.file-name {
  font-size: 13.5px;
  font-weight: 500;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.file-meta {
  font-size: 12px;
  opacity: 0.55;
  margin-top: 3px;
  font-variant-numeric: tabular-nums;
}

.empty {
  padding: 48px 0;
  text-align: center;
  font-size: 13px;
  opacity: 0.5;
}
</style>