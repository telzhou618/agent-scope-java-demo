<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import {
  NAvatar,
  NButton,
  NCard,
  NDataTable,
  NDatePicker,
  NEmpty,
  NIcon,
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
import { useAuthStore } from '../stores/auth'
import { getRecentRequests, getUsageSummary } from '../api/agent'
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
const router = useRouter()

const displayName = computed(() => auth.user?.nickname || auth.user?.username || '未登录')

const editProfileOpen = ref(false)
const passwordOpen = ref(false)

/* ---- 用量统计 ---- */
const rangeKey = ref<RangeKey>('thisMonth')
const custom = ref<[number, number] | null>(defaultCustomRange())
const usage = ref<UsageSummary | null>(null)
const loadingUsage = ref(false)

const recent = ref<RecentRequestItem[]>([])
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
    recent.value = (await getRecentRequests({ start: toDateInput(start), end: toDateInput(end) })) ?? []
  } catch {
    recent.value = []
  } finally {
    loadingRecent.value = false
  }
}

watch(
  [rangeKey, custom],
  () => {
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

/** 行点击：跳到对应会话（没有 sessionId 的行不可点） */
function rowProps(row: RecentRequestItem) {
  return {
    style: row.sessionId ? 'cursor: pointer;' : '',
    title: row.sessionId ? '打开对应会话' : undefined,
    onClick: () => {
      if (row.sessionId) void router.push(`/chat/${row.sessionId}`)
    },
  }
}
</script>

<template>
  <div class="h-full overflow-y-auto p-4 md:p-6">
    <div class="max-w-4xl mx-auto flex flex-col gap-4">
      <!-- 身份卡 -->
      <NCard>
        <div class="flex items-center gap-4 flex-wrap">
          <NAvatar round :size="56" :src="auth.user?.avatar || undefined">
            {{ displayName.charAt(0) }}
          </NAvatar>
          <div class="min-w-0 flex-1">
            <div class="text-lg font-600 truncate">{{ displayName }}</div>
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
          <div class="grid grid-cols-1 sm:grid-cols-3 gap-3 mb-6">
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
          :columns="recentColumns"
          :data="recent"
          :loading="loadingRecent"
          :row-props="rowProps"
          :bordered="false"
          size="small"
        />
      </NCard>
    </div>
  </div>
</template>

<style scoped>
.stat-card {
  padding: 14px 16px;
  border-radius: 10px;
  background: rgba(99, 102, 241, 0.06);
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

.chart {
  --chart-bar: #6366f1;
  --chart-border: rgba(100, 116, 139, 0.25);
  --chart-tip-bg: #1e293b;
  --chart-tip-fg: #f1f5f9;
}

html[data-theme='dark'] .chart {
  --chart-bar: #818cf8;
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
