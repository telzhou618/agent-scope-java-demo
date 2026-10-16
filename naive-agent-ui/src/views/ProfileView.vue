<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import {
  NAvatar,
  NButton,
  NCard,
  NDataTable,
  NDatePicker,
  NEmpty,
  NIcon,
  NProgress,
  NSelect,
  NSpin,
  NText,
  type DataTableColumns,
} from 'naive-ui'
import {
  KeyOutline,
  PersonOutline,
} from '@vicons/ionicons5'
import EditProfileDialog from '../components/EditProfileDialog.vue'
import PasswordDialog from '../components/PasswordDialog.vue'
import { useWideMode } from '../composables/useWideMode'
import { useAuthStore } from '../stores/auth'
import { agentShortLabel, useAgentsStore } from '../stores/agents'
import type { AgentQuotaItem, MonthlyQuotaSummary } from '../api/types'
import { getMonthlyQuota, getRecentRequests, getUsageSummary } from '../api/agent'
import { formatInt, formatMoney, formatTokens } from '../utils/format'
import {
  RANGE_OPTIONS,
  defaultCustomRange,
  resolveBounds,
  toDateInput,
  type RangeKey,
  type RecentRequestItem,
  type UsageBucket,
  type UsageSummary,
} from '../utils/usage'

const auth = useAuthStore()
const { wide } = useWideMode()

const displayName = computed(() => auth.user?.nickname || auth.user?.username || '未登录')

const editProfileOpen = ref(false)
const passwordOpen = ref(false)

/* ---- 本月 token 额度 ---- */
const quota = ref<MonthlyQuotaSummary | null>(null)
const loadingQuota = ref(false)

/* ---- 用量统计 ---- */
const rangeKey = ref<RangeKey>('thisMonth')
const custom = ref<[number, number] | null>(defaultCustomRange())
const usage = ref<UsageSummary | null>(null)
const loadingUsage = ref(false)

/* ---- 最近请求记录（服务端分页） ---- */
const RECENT_PAGE_SIZE = 10
const recent = ref<RecentRequestItem[]>([])
const recentPage = ref(1)
const recentTotal = ref(0)
const loadingRecent = ref(false)

const rangeOptions = RANGE_OPTIONS.map((option) => ({ label: option.label, value: option.key }))

async function loadUsage() {
  const { start, end, granularity } = resolveBounds(rangeKey.value, custom.value)
  loadingUsage.value = true
  try {
    usage.value = await getUsageSummary({
      start: toDateInput(start),
      end: toDateInput(end),
      granularity,
    })
  } catch {
    usage.value = null
  } finally {
    loadingUsage.value = false
  }
}

async function loadRecent() {
  const { start, end } = resolveBounds(rangeKey.value, custom.value)
  loadingRecent.value = true
  try {
    const result = await getRecentRequests({
      start: toDateInput(start),
      end: toDateInput(end),
      page: recentPage.value,
      size: RECENT_PAGE_SIZE,
    })
    recent.value = result?.records ?? []
    recentTotal.value = result?.total ?? 0
    const totalPages = Math.max(1, Math.ceil(recentTotal.value / RECENT_PAGE_SIZE))
    if (recentPage.value > totalPages) {
      recentPage.value = totalPages
      return loadRecent()
    }
  } catch {
    recent.value = []
    recentTotal.value = 0
  } finally {
    loadingRecent.value = false
  }
}

async function loadQuota() {
  loadingQuota.value = true
  try {
    quota.value = await getMonthlyQuota()
  } catch {
    quota.value = null
  } finally {
    loadingQuota.value = false
  }
}

function quotaPercent(item: AgentQuotaItem): number {
  if (!item.quotaTokens || item.quotaTokens <= 0) return 0
  return Math.min(100, Math.round((item.usedTokens / item.quotaTokens) * 100))
}

const totalQuota = computed(() => {
  const agents = quota.value?.agents ?? []
  if (agents.some((item) => !item.quotaTokens || item.quotaTokens <= 0)) return null
  return agents.reduce((sum, item) => sum + item.quotaTokens, 0)
})

const totalQuotaText = computed(() =>
  totalQuota.value === null ? '不限' : formatTokens(totalQuota.value),
)

function quotaColor(item: AgentQuotaItem): string {
  const percent = quotaPercent(item)
  if (percent >= 85) return '#e5484d'
  if (percent >= 60) return '#f0a020'
  return '#18a058'
}

onMounted(() => {
  void loadQuota()
})

watch(
  [rangeKey, custom],
  () => {
    recentPage.value = 1
    void loadUsage()
    void loadRecent()
  },
  { immediate: true },
)

/* ---- 柱状图取值维度：Tokens / 请求次数 / 费用 ---- */
type MetricKey = 'tokens' | 'requests' | 'cost'

const metricKey = ref<MetricKey>('tokens')
const metricOptions = [
  { label: 'Tokens', value: 'tokens' },
  { label: '请求次数', value: 'requests' },
  { label: '费用', value: 'cost' },
]
const metricLabel = computed(
  () => metricOptions.find((option) => option.value === metricKey.value)?.label ?? '',
)

const metricValue = (bucket: UsageBucket) => bucket[metricKey.value]

const peak = computed(() =>
  usage.value ? Math.max(1, ...usage.value.buckets.map((bucket) => metricValue(bucket))) : 1,
)

function formatMetric(value: number): string {
  if (metricKey.value === 'cost') return formatMoney(value)
  if (metricKey.value === 'requests') return formatInt(value)
  return formatTokens(value)
}

const barHeight = (value: number) => `${Math.max(3, Math.round((value / peak.value) * 100))}%`

/** x 轴只标 5 个刻度，避免柱子多时标签挤在一起 */
const axisLabels = computed(() => {
  if (!usage.value) return []
  const { buckets } = usage.value
  if (buckets.length <= 5) return buckets.map((bucket) => bucket.label)
  const step = (buckets.length - 1) / 4
  return Array.from({ length: 5 }, (_, index) => buckets[Math.round(index * step)].label)
})

/* ---- 最近请求表 ---- */
const agentsStore = useAgentsStore()
function agentNameLabel(name?: string): string {
  if (!name) return '—'
  return agentsStore.state.agents.find((item) => item.name === name)?.displayName || agentShortLabel(name)
}

/** createTime 是 ISO LocalDateTime（含 T），只展示到秒 */
const formatTime = (value: string) => value.replace('T', ' ').slice(0, 19)

const recentColumns: DataTableColumns<RecentRequestItem> = [
  {
    title: '请求时间',
    key: 'createTime',
    width: 165,
    render: (row) => formatTime(row.createTime),
  },
  {
    title: 'Agent',
    key: 'agentName',
    width: 120,
    render: (row) => agentNameLabel(row.agentName),
  },
  {
    title: '请求 ID',
    key: 'requestId',
    ellipsis: { tooltip: true },
    render: (row) => row.requestId || '—',
  },
  { title: '模型', key: 'modelName', width: 130, ellipsis: { tooltip: true } },
  {
    title: '输入',
    key: 'inputTokens',
    width: 80,
    align: 'right',
    render: (row) => formatInt(row.inputTokens),
  },
  {
    title: '输出',
    key: 'outputTokens',
    width: 80,
    align: 'right',
    render: (row) => formatInt(row.outputTokens),
  },
  {
    title: '耗时',
    key: 'durationSeconds',
    width: 80,
    align: 'right',
    render: (row) => `${row.durationSeconds.toFixed(1)} s`,
  },
  {
    title: '费用',
    key: 'cost',
    width: 90,
    align: 'right',
    render: (row) => formatMoney(row.cost),
  },
]
</script>

<template>
  <div class="p-4 pb-12 md:p-6 md:pb-12">
    <div class="mx-auto flex flex-col gap-4" :class="wide ? 'max-w-[1200px]' : 'max-w-[800px]'">
      <!-- 身份卡 -->
      <NCard>
        <div class="flex items-center gap-4 flex-wrap">
          <NAvatar round :size="56" :src="auth.user?.avatar || undefined" />
          <div class="min-w-0 flex-1">
            <div class="flex items-center gap-2">
              <span class="text-lg font-600 truncate">{{ displayName }}</span>
              <span v-if="auth.user?.isAdmin" class="badge">管理员</span>
            </div>
            <NText depth="3" class="text-sm">@{{ auth.user?.username }} · {{ auth.user?.email || '—' }}</NText>
          </div>
          <div class="flex gap-2">
            <NButton secondary @click="editProfileOpen = true">
              <template #icon>
                <NIcon><PersonOutline /></NIcon>
              </template>
              编辑资料
            </NButton>
            <NButton secondary @click="passwordOpen = true">
              <template #icon>
                <NIcon><KeyOutline /></NIcon>
              </template>
              修改密码
            </NButton>
          </div>
        </div>
      </NCard>

      <EditProfileDialog v-model:show="editProfileOpen" />
      <PasswordDialog v-model:show="passwordOpen" />

      <!-- 本月 Token 额度 -->
      <NCard>
        <div class="flex items-center gap-3 flex-wrap mb-4">
          <div>
            <div class="text-base font-600">本月 Token 额度</div>
            <NText depth="3" class="text-xs">{{ quota?.month || '—' }}</NText>
          </div>
          <span class="flex-1" />
          <div class="flex items-center gap-6 text-right">
            <div>
              <div class="text-xs op-45">本月总消耗</div>
              <div class="text-base font-700">{{ formatTokens(quota?.totalTokens ?? 0) }}</div>
            </div>
            <div>
              <div class="text-xs op-45">总 Token 额度</div>
              <div class="text-base font-700">{{ totalQuotaText }}</div>
            </div>
          </div>
        </div>

        <div v-if="loadingQuota" class="flex justify-center py-6">
          <NSpin size="small" />
        </div>
        <div v-else-if="quota" class="grid grid-cols-3 gap-3">
          <div v-for="item in quota.agents" :key="item.name" class="stat-card">
            <div class="flex items-center gap-3 mb-3">
              <NAvatar round :size="36" :src="item.avatar || undefined" />
              <div class="min-w-0">
                <div class="text-sm font-600 truncate">{{ item.displayName }}</div>
                <div class="text-xs op-45">{{ item.name }}</div>
              </div>
            </div>
            <NProgress
              v-if="item.quotaTokens > 0"
              type="line"
              :percentage="quotaPercent(item)"
              :color="quotaColor(item)"
              :height="8"
              :show-indicator="false"
              :border-radius="4"
              class="mb-2"
            />
            <div v-else class="text-xs op-45 mb-2">不限</div>
            <div class="flex items-baseline justify-between gap-2" style="font-variant-numeric: tabular-nums">
              <span v-if="item.quotaTokens > 0" class="font-700" :style="{ color: quotaColor(item) }">{{ quotaPercent(item) }}%</span>
              <span v-else class="font-700">不限</span>
              <span class="text-xs op-45">{{ item.quotaTokens > 0 ? `${formatTokens(item.usedTokens)} / ${formatTokens(item.quotaTokens)}` : '' }}</span>
            </div>
          </div>
        </div>
        <div v-else class="text-sm op-45 py-2">暂无额度信息</div>
      </NCard>

      <!-- 用量统计 -->
      <NCard>
        <div class="flex items-center gap-3 flex-wrap mb-4">
          <span class="text-base font-600">用量与费用</span>
          <span class="flex-1" />
          <NDatePicker
            v-if="rangeKey === 'custom'"
            v-model:value="custom"
            type="daterange"
            size="small"
            clearable
            class="max-w-60"
          />
          <NSelect
            v-model:value="rangeKey"
            :options="rangeOptions"
            size="small"
            class="w-32"
            :consistent-menu-width="false"
          />
        </div>

        <div v-if="loadingUsage && !usage" class="flex justify-center py-10">
          <NSpin />
        </div>
        <template v-else-if="usage">
          <div class="grid grid-cols-3 gap-3 mb-6">
            <div class="stat-card">
              <div class="stat-label">消费金额</div>
              <div class="stat-value">{{ formatMoney(usage.totals.cost) }}<span class="stat-unit">CNY</span></div>
            </div>
            <div class="stat-card">
              <div class="stat-label">API 请求次数</div>
              <div class="stat-value">{{ formatInt(usage.totals.requests) }}</div>
            </div>
            <div class="stat-card">
              <div class="stat-label">Tokens</div>
              <div class="stat-value">{{ formatTokens(usage.totals.tokens) }}</div>
              <div class="stat-sub">
                输入 {{ formatTokens(usage.totals.inputTokens) }} / 输出 {{ formatTokens(usage.totals.outputTokens) }}
              </div>
            </div>
          </div>

          <div class="flex items-center gap-3 flex-wrap mb-3">
            <span class="font-600">用量趋势</span>
            <NText depth="3" class="text-xs">{{ usage.description }}</NText>
            <span class="flex-1" />
            <NSelect
              v-model:value="metricKey"
              :options="metricOptions"
              size="small"
              class="w-32"
              :consistent-menu-width="false"
            />
          </div>
          <div class="chart">
            <div class="chart-bars">
              <div
                v-for="(bucket, index) in usage.buckets"
                :key="index"
                class="bar"
                :style="{ height: barHeight(metricValue(bucket)) }"
                tabindex="0"
              >
                <span class="bar-tip">
                  <span class="bar-tip-title">{{ bucket.label }}</span>
                  <span>{{ metricLabel }} <b>{{ formatMetric(metricValue(bucket)) }}</b></span>
                  <span>Tokens <b>{{ formatTokens(bucket.tokens) }}</b></span>
                  <span>请求 <b>{{ formatInt(bucket.requests) }}</b></span>
                  <span>费用 <b>{{ formatMoney(bucket.cost) }}</b></span>
                </span>
              </div>
            </div>
            <div class="chart-axis">
              <span v-for="label in axisLabels" :key="label">{{ label }}</span>
            </div>
          </div>
        </template>
        <NEmpty v-else description="该时间段暂无用量数据" class="py-8" />
      </NCard>

      <!-- 最近请求 -->
      <NCard title="最近请求记录">
        <NDataTable
          remote
          :columns="recentColumns"
          :data="recent"
          :loading="loadingRecent"
          :bordered="false"
          size="small"
          :pagination="{
            page: recentPage,
            pageSize: RECENT_PAGE_SIZE,
            itemCount: recentTotal,
            onUpdatePage: (next: number) => {
              recentPage = next
              loadRecent()
            },
          }"
        />
      </NCard>
    </div>
  </div>
</template>

<style scoped>
.stat-card {
  padding: 14px 16px;
  border-radius: 12px;
  background: var(--brand-soft);
  border: 1px solid var(--divider);
  transition:
    border-color 0.15s ease,
    box-shadow 0.15s ease,
    transform 0.15s ease;
}

.stat-card:hover {
  border-color: var(--brand-border);
  box-shadow: var(--shadow-float);
  transform: translateY(-1px);
}

.stat-label {
  font-size: 12.5px;
  opacity: 0.6;
  margin-bottom: 6px;
}

.stat-value {
  font-size: 22px;
  font-weight: 700;
}

.stat-unit {
  font-size: 12px;
  font-weight: 400;
  opacity: 0.55;
  margin-left: 4px;
}

.stat-sub {
  font-size: 12px;
  opacity: 0.6;
  margin-top: 2px;
}

.badge {
  flex: none;
  padding: 1px 8px;
  border-radius: 999px;
  font-size: 11.5px;
  line-height: 1.6;
  color: var(--brand);
  background: var(--brand-soft);
  border: 1px solid var(--brand-border);
}

.chart {
  --chart-bar: var(--brand);
  --chart-border: var(--divider-strong);
  --chart-tip-bg: #1e293b;
  --chart-tip-fg: #f1f5f9;
}

html[data-theme='dark'] .chart {
  --chart-tip-bg: #e2e8f0;
  --chart-tip-fg: #1e293b;
}

.chart-bars {
  display: flex;
  align-items: flex-end;
  gap: 3px;
  height: 180px;
  border-bottom: 1px solid var(--chart-border);
}

.bar {
  position: relative;
  flex: 1;
  min-width: 3px;
  border-radius: 3px 3px 0 0;
  background: var(--chart-bar);
  opacity: 0.85;
  cursor: default;
}

.bar:hover,
.bar:focus-visible {
  opacity: 1;
}

.bar-tip {
  position: absolute;
  left: 50%;
  bottom: calc(100% + 8px);
  transform: translateX(-50%);
  display: none;
  flex-direction: column;
  gap: 2px;
  padding: 8px 10px;
  border-radius: 8px;
  background: var(--chart-tip-bg);
  color: var(--chart-tip-fg);
  font-size: 12px;
  white-space: nowrap;
  z-index: 10;
  pointer-events: none;
}

.bar:hover .bar-tip,
.bar:focus-visible .bar-tip {
  display: flex;
}

.bar-tip-title {
  font-weight: 600;
  margin-bottom: 2px;
}

.chart-axis {
  display: flex;
  justify-content: space-between;
  padding-top: 6px;
  font-size: 11.5px;
  opacity: 0.55;
}
</style>
