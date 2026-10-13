<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { NButton, NIcon, NPopconfirm, NSpin, NSwitch, NText, useMessage } from 'naive-ui'
import { PlayOutline } from '@vicons/ionicons5'
import { deleteSchedule, listSchedules, toggleSchedule, triggerSchedule } from '../api/agent'
import type { ScheduleTaskItem } from '../api/types'
import { useAgentsStore } from '../stores/agents'
import { useWideMode } from '../composables/useWideMode'

const message = useMessage()
const agents = useAgentsStore()
const { wide } = useWideMode()

const tasks = ref<ScheduleTaskItem[]>([])
const loading = ref(false)

async function load() {
  loading.value = true
  try {
    tasks.value = (await listSchedules()) ?? []
  } catch (error) {
    message.error(error instanceof Error ? error.message : '加载定时任务失败')
  } finally {
    loading.value = false
  }
}

function agentDisplayName(agentName: string) {
  return agents.state.agents.find((a) => a.name === agentName)?.displayName || agentName
}

function formatTime(value?: string) {
  return value ? value.replace('T', ' ').slice(0, 16) : '—'
}

async function onToggle(item: ScheduleTaskItem, next: boolean) {
  try {
    await toggleSchedule(item.id, next ? 1 : 0)
    item.status = next ? 1 : 0
    message.success(next ? '已启用' : '已停用')
  } catch (error) {
    message.error(error instanceof Error ? error.message : '操作失败')
  }
}

/** 立即执行一次：后台跑完后结果发到消息中心 */
const triggering = ref<number | null>(null)

async function onTrigger(item: ScheduleTaskItem) {
  triggering.value = item.id
  try {
    await triggerSchedule(item.id)
    message.success('已触发执行，完成后结果会发到消息中心')
  } catch (error) {
    message.error(error instanceof Error ? error.message : '触发失败')
  } finally {
    triggering.value = null
  }
}

async function onDelete(item: ScheduleTaskItem) {
  try {
    await deleteSchedule(item.id)
    tasks.value = tasks.value.filter((t) => t.id !== item.id)
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
        <span class="text-lg font-600">定时任务</span>
        <NText depth="3" class="text-xs">{{ tasks.length }} 个任务</NText>
      </div>

      <div v-if="loading && tasks.length === 0" class="flex justify-center py-16">
        <NSpin />
      </div>

      <template v-else>
        <div v-if="tasks.length" class="task-list">
          <div v-for="item in tasks" :key="item.id" class="task-row">
            <div class="task-main">
              <div class="task-desc">{{ item.description }}</div>
              <div class="task-meta">
                <code>{{ item.cron }}</code>
                <span>· {{ agentDisplayName(item.agentName) }}</span>
                <span>· 上次执行 {{ formatTime(item.lastRunAt) }}</span>
                <span v-if="item.status === 1">· 下次 {{ formatTime(item.nextRunAt) }}</span>
                <span v-else>· 已停用</span>
              </div>
            </div>
            <NSwitch
              :value="item.status === 1"
              size="small"
              @update:value="(v: boolean) => onToggle(item, v)"
            />
            <NPopconfirm @positive-click="onTrigger(item)" positive-text="执行一次" negative-text="取消">
              <template #trigger>
                <NButton
                  size="tiny"
                  secondary
                  :loading="triggering === item.id"
                  title="立即执行一次"
                >
                  <template #icon>
                    <NIcon><PlayOutline /></NIcon>
                  </template>
                  执行一次
                </NButton>
              </template>
              立即执行该任务一次？
            </NPopconfirm>
            <NPopconfirm @positive-click="onDelete(item)">
              <template #trigger>
                <NButton size="tiny" type="error" quaternary>删除</NButton>
              </template>
              删除后不再定时执行，确认删除？
            </NPopconfirm>
          </div>
        </div>
        <div v-else class="empty">暂无定时任务</div>
        <div class="hint">定时任务通过对话创建，例如对 Agent 说「每周一把上周的运营报告发我」</div>
      </template>
    </div>
  </div>
</template>

<style scoped>
.task-list {
  background: var(--surface-elevated);
  border: 1px solid var(--divider);
  border-radius: 12px;
  overflow: hidden;
}

.task-row {
  display: flex;
  align-items: center;
  gap: 14px;
  padding: 13px 18px;
}

.task-row + .task-row {
  border-top: 1px solid var(--divider);
}

.task-main {
  flex: 1;
  min-width: 0;
}

.task-desc {
  font-size: 13.5px;
  font-weight: 500;
}

.task-meta {
  font-size: 12px;
  opacity: 0.55;
  margin-top: 3px;
  font-variant-numeric: tabular-nums;
}

.task-meta code {
  background: var(--surface-subtle);
  border-radius: 4px;
  padding: 0 5px;
  font-size: 11px;
}

.empty {
  padding: 48px 0;
  text-align: center;
  font-size: 13px;
  opacity: 0.5;
}

.hint {
  text-align: center;
  font-size: 12px;
  opacity: 0.45;
}
</style>
