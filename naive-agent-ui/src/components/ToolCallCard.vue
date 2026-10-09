<script setup lang="ts">
import { computed } from 'vue'
import { NIcon, NTag } from 'naive-ui'
import {
  CheckmarkOutline,
  ChevronDownOutline,
  CloseOutline,
  ConstructOutline,
  RemoveOutline,
} from '@vicons/ionicons5'
import type { ToolBlockModel, ToolStatus } from '../utils/model'

const props = defineProps<{ block: ToolBlockModel }>()

const STATUS: Record<ToolStatus, { label: string; type: 'default' | 'info' | 'success' | 'error' }> = {
  running: { label: '运行中', type: 'info' },
  ok: { label: '成功', type: 'success' },
  error: { label: '失败', type: 'error' },
  none: { label: '无结果', type: 'default' },
}

const status = computed(() => STATUS[props.block.status])

function toggle() {
  props.block.open = !props.block.open
}
</script>

<template>
  <div class="tool-card" :data-status="props.block.status">
    <button class="tool-head" type="button" :aria-expanded="props.block.open" @click="toggle">
      <NIcon size="15" class="tool-icon"><ConstructOutline /></NIcon>
      <span class="tool-name">{{ props.block.name }}</span>
      <span v-if="props.block.args" class="tool-args">{{ props.block.args }}</span>
      <NTag size="tiny" :bordered="false" :type="status.type" round class="ml-auto shrink-0">
        <template #icon>
          <NIcon size="12" :class="{ spinning: props.block.status === 'running' }">
            <CheckmarkOutline v-if="props.block.status === 'ok'" />
            <CloseOutline v-else-if="props.block.status === 'error'" />
            <RemoveOutline v-else-if="props.block.status === 'none'" />
            <ConstructOutline v-else />
          </NIcon>
        </template>
        {{ status.label }}
      </NTag>
      <NIcon size="13" class="chevron" :class="{ open: props.block.open }">
        <ChevronDownOutline />
      </NIcon>
    </button>
    <div v-show="props.block.open" class="tool-body">
      <div v-if="props.block.args" class="tool-section">
        <div class="tool-section-title">参数</div>
        <pre class="tool-pre">{{ props.block.args }}</pre>
      </div>
      <div v-if="props.block.output" class="tool-section">
        <div class="tool-section-title">结果</div>
        <pre class="tool-pre">{{ props.block.output }}</pre>
      </div>
      <div v-if="!props.block.args && !props.block.output" class="tool-empty">暂无内容</div>
    </div>
  </div>
</template>

<style scoped>
.tool-card {
  margin-bottom: 10px;
  border: 1px solid rgba(100, 116, 139, 0.28);
  border-radius: 10px;
  overflow: hidden;
}

.tool-card[data-status='error'] {
  border-color: rgba(224, 49, 49, 0.4);
}

.tool-head {
  display: flex;
  align-items: center;
  gap: 7px;
  width: 100%;
  padding: 7px 12px;
  border: 0;
  background: rgba(100, 116, 139, 0.07);
  color: inherit;
  font-size: 12.5px;
  cursor: pointer;
}

.tool-icon {
  color: #6366f1;
  flex-shrink: 0;
}

.tool-name {
  font-weight: 600;
  flex-shrink: 0;
}

.tool-args {
  flex: 1;
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  opacity: 0.55;
  font-size: 12px;
}

.chevron {
  flex-shrink: 0;
  opacity: 0.6;
  transition: transform 0.2s;
}

.chevron.open {
  transform: rotate(180deg);
}

.spinning {
  animation: tool-spin 1s linear infinite;
}

@keyframes tool-spin {
  to { transform: rotate(360deg); }
}

.tool-body {
  padding: 8px 12px;
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.tool-section-title {
  font-size: 11.5px;
  opacity: 0.55;
  margin-bottom: 3px;
}

.tool-pre {
  margin: 0;
  padding: 8px 10px;
  border-radius: 8px;
  background: rgba(100, 116, 139, 0.08);
  font-size: 12px;
  line-height: 1.6;
  white-space: pre-wrap;
  word-break: break-all;
  max-height: 240px;
  overflow-y: auto;
}

.tool-empty {
  font-size: 12px;
  opacity: 0.5;
}
</style>
