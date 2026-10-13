<script setup lang="ts">
import { computed, ref } from 'vue'
import { NButton, NIcon } from 'naive-ui'
import {
  CheckmarkOutline,
  ChevronDownOutline,
  CloseOutline,
  ShieldCheckmarkOutline,
} from '@vicons/ionicons5'
import { useChatStore } from '../stores/chat'
import type { ConfirmBlockModel } from '../utils/model'

const props = defineProps<{ block: ConfirmBlockModel }>()

const chat = useChatStore()

/** 每项的「查看原始参数」展开状态 */
const argsOpen = ref<Record<string, boolean>>({})

const approvedCount = computed(
  () => props.block.toolCalls.filter((c) => c.decision === 'approved').length,
)
const rejectedCount = computed(
  () => props.block.toolCalls.filter((c) => c.decision === 'rejected').length,
)
/** 逐项全部勾选后才能提交 */
const allDecided = computed(() => props.block.toolCalls.every((c) => !!c.decision))

const pendingText = computed(() => {
  if (props.block.status !== 'pending') return ''
  if (approvedCount.value === 0 && rejectedCount.value === 0) return '等待审批…'
  const parts: string[] = []
  if (approvedCount.value) parts.push(`已同意 ${approvedCount.value} 个`)
  if (rejectedCount.value) parts.push(`已拒绝 ${rejectedCount.value} 个`)
  return parts.join('，')
})

function decide(call: ConfirmBlockModel['toolCalls'][number], decision: 'approved' | 'rejected') {
  if (props.block.status !== 'pending') return
  call.decision = call.decision === decision ? undefined : decision
}

/** 底部「取消」：全部按拒绝提交 */
function cancelAll() {
  for (const call of props.block.toolCalls) {
    if (!call.decision) call.decision = 'rejected'
  }
  chat.respondConfirm(props.block)
}

function submit() {
  chat.respondConfirm(props.block)
}
</script>

<template>
  <div class="confirm-card">
    <div class="confirm-head">
      <span class="head-icon">
        <NIcon size="16"><ShieldCheckmarkOutline /></NIcon>
      </span>
      <div class="head-text">
        <div class="head-title">Agent 请求确认操作</div>
        <div class="head-sub">请对以下工具进行审批，完成后点击底部确认执行。</div>
      </div>
    </div>

    <!-- 逐项审批卡片 -->
    <div class="review-list">
      <div
        v-for="call in block.toolCalls"
        :key="call.id"
        class="review-item"
        :class="{ approved: call.decision === 'approved', rejected: call.decision === 'rejected' }"
      >
        <div class="item-row">
          <span class="item-name">{{ call.name }}</span>
          <span class="item-spacer" />
          <template v-if="block.status === 'pending'">
            <NButton
              size="tiny"
              :type="call.decision === 'approved' ? 'primary' : 'default'"
              :secondary="call.decision === 'approved'"
              @click="decide(call, 'approved')"
            >
              <template v-if="call.decision === 'approved'" #icon>
                <NIcon><CheckmarkOutline /></NIcon>
              </template>
              {{ call.decision === 'approved' ? '已同意' : '同意' }}
            </NButton>
            <NButton
              size="tiny"
              :type="call.decision === 'rejected' ? 'error' : 'default'"
              :secondary="call.decision === 'rejected'"
              @click="decide(call, 'rejected')"
            >
              <template v-if="call.decision === 'rejected'" #icon>
                <NIcon><CloseOutline /></NIcon>
              </template>
              {{ call.decision === 'rejected' ? '已拒绝' : '拒绝' }}
            </NButton>
          </template>
          <span v-else class="item-verdict" :class="call.decision">
            {{ call.decision === 'approved' ? '已同意' : '已拒绝' }}
          </span>
        </div>
        <template v-if="call.input && Object.keys(call.input).length">
          <button class="args-toggle" type="button" @click="argsOpen[call.id] = !argsOpen[call.id]">
            <NIcon size="11" class="chevron" :class="{ open: argsOpen[call.id] }">
              <ChevronDownOutline />
            </NIcon>
            查看原始参数 (JSON)
          </button>
          <pre v-if="argsOpen[call.id]" class="call-args">{{ JSON.stringify(call.input, null, 2) }}</pre>
        </template>
      </div>
    </div>

    <div class="confirm-foot">
      <span class="foot-status">
        <template v-if="block.status === 'pending'">{{ pendingText }}</template>
        <template v-else>已同意 {{ approvedCount }} 个，已拒绝 {{ rejectedCount }} 个</template>
      </span>
      <template v-if="block.status === 'pending'">
        <NButton size="small" quaternary @click="cancelAll">取消</NButton>
        <NButton size="small" type="primary" :disabled="!allDecided" @click="submit">
          <template #icon>
            <NIcon><CheckmarkOutline /></NIcon>
          </template>
          确认执行
        </NButton>
      </template>
    </div>
  </div>
</template>

<style scoped>
.confirm-card {
  border: 1px solid var(--divider);
  border-radius: 12px;
  background: var(--surface-elevated);
  overflow: hidden;
}

/* 头部：图标方块 + 标题 + 副标题 */
.confirm-head {
  display: flex;
  align-items: flex-start;
  gap: 10px;
  padding: 14px 16px 12px;
  border-bottom: 1px solid var(--divider);
}

.head-icon {
  flex: none;
  width: 30px;
  height: 30px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  border-radius: 9px;
  background: var(--brand-soft-strong);
  color: var(--brand);
}

.head-title {
  font-size: 14px;
  font-weight: 700;
}

.head-sub {
  font-size: 12px;
  opacity: 0.55;
  margin-top: 2px;
}

/* 逐项审批卡片 */
.review-list {
  display: flex;
  flex-direction: column;
  gap: 8px;
  padding: 12px 16px;
}

.review-item {
  border: 1px solid var(--divider-strong);
  border-radius: 9px;
  background: var(--surface-elevated);
  padding: 8px 12px 9px;
  transition:
    border-color 0.15s,
    background 0.15s;
}

.review-item.approved {
  border-color: rgba(34, 197, 94, 0.45);
  background: rgba(34, 197, 94, 0.07);
}

.review-item.rejected {
  border-color: rgba(239, 68, 68, 0.4);
  background: rgba(239, 68, 68, 0.06);
}

.item-row {
  display: flex;
  align-items: center;
  gap: 8px;
}

.item-name {
  font-weight: 600;
  font-size: 13px;
  font-family: ui-monospace, SFMono-Regular, Menlo, Consolas, monospace;
}

.item-spacer {
  flex: 1;
}

.item-verdict {
  font-size: 12px;
  font-weight: 600;
}

.item-verdict.approved {
  color: #16a34a;
}

.item-verdict.rejected {
  color: #dc2626;
}

.args-toggle {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  margin-top: 6px;
  padding: 0;
  border: 0;
  background: none;
  font-size: 12px;
  color: inherit;
  opacity: 0.55;
  cursor: pointer;
}

.args-toggle:hover {
  opacity: 0.85;
}

.chevron {
  transition: transform 0.2s;
}

.chevron.open {
  transform: rotate(180deg);
}

.call-args {
  margin: 6px 0 0;
  padding: 8px 10px;
  border-radius: 6px;
  background: var(--surface-subtle);
  font-family: ui-monospace, SFMono-Regular, Menlo, Consolas, monospace;
  font-size: 12px;
  line-height: 1.6;
  white-space: pre-wrap;
  word-break: break-all;
}

/* 底部：左状态 + 右按钮 */
.confirm-foot {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  padding: 10px 16px;
  border-top: 1px solid var(--divider);
}

.foot-status {
  font-size: 12.5px;
  opacity: 0.6;
}
</style>
