<script setup lang="ts">
import { ref } from 'vue'
import { NButton, NIcon } from 'naive-ui'
import {
  CheckmarkOutline,
  ChevronDownOutline,
  CloseOutline,
  ShieldCheckmarkOutline,
} from '@vicons/ionicons5'
import { useChatStore } from '../stores/chat'
import type { ConfirmBlockModel } from '../utils/model'
import { prettyJson } from '../utils/format'

const props = defineProps<{ block: ConfirmBlockModel }>()

const chat = useChatStore()
const showArgs = ref(false)

function respond(confirmed: boolean) {
  chat.respondConfirm(props.block, confirmed)
}
</script>

<template>
  <div class="confirm-card" :class="block.status">
    <div class="confirm-main">
      <NIcon size="16" class="confirm-icon"><ShieldCheckmarkOutline /></NIcon>
      <div class="confirm-text">
        <div class="confirm-title">
          Agent 请求确认操作
          <span v-for="call in block.toolCalls" :key="call.id" class="call-chip">{{ call.name }}</span>
        </div>
        <button
          v-if="block.toolCalls.some((c) => c.input && Object.keys(c.input).length)"
          class="args-toggle"
          type="button"
          @click="showArgs = !showArgs"
        >
          {{ showArgs ? '收起参数' : '查看参数' }}
          <NIcon size="12" :class="{ open: showArgs }" class="chevron"><ChevronDownOutline /></NIcon>
        </button>
        <pre v-if="showArgs" class="call-args">{{ block.toolCalls.map((c) => prettyJson(JSON.stringify(c.input ?? {}))).join('\n') }}</pre>
      </div>
    </div>
    <div class="confirm-foot">
      <template v-if="block.status === 'pending'">
        <NButton size="small" type="primary" @click="respond(true)">
          <template #icon>
            <NIcon><CheckmarkOutline /></NIcon>
          </template>
          确认执行
        </NButton>
        <NButton size="small" quaternary @click="respond(false)">
          <template #icon>
            <NIcon><CloseOutline /></NIcon>
          </template>
          取消
        </NButton>
      </template>
      <span v-else class="confirm-done" :class="block.status">
        {{ block.status === 'confirmed' ? '已确认，执行结果见下方' : '已取消' }}
      </span>
    </div>
  </div>
</template>

<style scoped>
.confirm-card {
  border: 1px solid var(--brand-border);
  border-radius: 10px;
  background: var(--brand-soft);
  padding: 10px 14px;
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.confirm-main {
  display: flex;
  align-items: flex-start;
  gap: 8px;
}

.confirm-icon {
  color: var(--brand);
  margin-top: 2px;
  flex: none;
}

.confirm-text {
  flex: 1;
  min-width: 0;
}

.confirm-title {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
  font-size: 13px;
  font-weight: 600;
}

.call-chip {
  padding: 1px 8px;
  border-radius: 999px;
  background: var(--brand-soft-strong);
  color: var(--brand);
  font-size: 11.5px;
  font-weight: 600;
  font-family: ui-monospace, SFMono-Regular, Menlo, Consolas, monospace;
}

.args-toggle {
  display: inline-flex;
  align-items: center;
  gap: 3px;
  margin-top: 4px;
  padding: 0;
  border: 0;
  background: none;
  color: var(--brand);
  font-size: 12px;
  cursor: pointer;
  opacity: 0.8;
}

.args-toggle:hover {
  opacity: 1;
}

.chevron {
  transition: transform 0.2s;
}

.chevron.open {
  transform: rotate(180deg);
}

.call-args {
  margin: 6px 0 0;
  padding: 6px 10px;
  border-radius: 6px;
  background: var(--surface-subtle);
  font-family: ui-monospace, SFMono-Regular, Menlo, Consolas, monospace;
  font-size: 12px;
  line-height: 1.6;
  white-space: pre-wrap;
  word-break: break-all;
}

.confirm-foot {
  display: flex;
  align-items: center;
  gap: 8px;
  padding-left: 24px;
}

.confirm-done {
  font-size: 12.5px;
  opacity: 0.6;
}

.confirm-done.confirmed {
  color: var(--brand);
  opacity: 1;
}
</style>
