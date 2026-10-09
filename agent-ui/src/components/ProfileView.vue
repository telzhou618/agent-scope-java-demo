<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import AppIcon from './AppIcon.vue'
import EditProfileDialog from './EditProfileDialog.vue'
import PasswordDialog from './PasswordDialog.vue'
import { useDismissableMenu } from '../composables/useDismissableMenu'
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

/** 用户信息：来自 /auth/current 的真实数据 */
const displayName = computed(() => auth.user?.nickname || auth.user?.username || '未登录')
const avatarChar = computed(() => displayName.value.charAt(0) || 'A')

const editProfileOpen = ref(false)
const passwordOpen = ref(false)

/* ---- 用量统计：来自后端接口（t_token_usage 按区间聚合） ---- */
const {
  open: rangeOpen,
  bindRoot: bindRangeRoot,
  close: closeRange,
  toggle: toggleRange,
} = useDismissableMenu()

const rangeKey = ref<RangeKey>('thisMonth')
const custom = ref(defaultCustomRange())
const usage = ref<UsageSummary | null>(null)
const loadingUsage = ref(false)

/** 最近请求记录（区间内最近 10 条） */
const recent = ref<RecentRequestItem[] | null>(null)
const loadingRecent = ref(false)

const rangeLabel = computed(
  () => RANGE_OPTIONS.find((option) => option.key === rangeKey.value)?.label ?? '',
)

async function loadUsage() {
  const { start, end, granularity } = resolveBounds(rangeKey.value, custom.value)
  loadingUsage.value = true
  try {
    usage.value = await getUsageSummary({
      start: toDateInput(start),
      end: toDateInput(end),
      granularity,
    })
  } finally {
    loadingUsage.value = false
  }
}

/** 最近请求记录：跟随时间维度变化，取区间内最近 10 条 */
async function loadRecent() {
  const { start, end } = resolveBounds(rangeKey.value, custom.value)
  loadingRecent.value = true
  try {
    recent.value = await getRecentRequests({ start: toDateInput(start), end: toDateInput(end) })
  } finally {
    loadingRecent.value = false
  }
}

watch(
  [rangeKey, () => custom.value.start, () => custom.value.end],
  () => {
    void loadUsage()
    void loadRecent()
  },
  { immediate: true },
)

const peak = computed(() =>
  usage.value ? Math.max(1, ...usage.value.buckets.map((bucket) => metricValue(bucket))) : 1,
)

/* ---- 柱状图取值维度：Tokens / 请求次数 / 费用 ---- */
type MetricKey = 'tokens' | 'requests' | 'cost'

const METRIC_OPTIONS: { key: MetricKey; label: string }[] = [
  { key: 'tokens', label: 'Tokens' },
  { key: 'requests', label: '请求次数' },
  { key: 'cost', label: '费用' },
]

const {
  open: metricOpen,
  bindRoot: bindMetricRoot,
  close: closeMetric,
  toggle: toggleMetric,
} = useDismissableMenu()

const metricKey = ref<MetricKey>('tokens')
const metricLabel = computed(
  () => METRIC_OPTIONS.find((option) => option.key === metricKey.value)?.label ?? '',
)

function metricValue(bucket: UsageBucket): number {
  return bucket[metricKey.value]
}

/** 柱高/提示里的数值按当前维度格式化 */
function formatMetric(value: number): string {
  if (metricKey.value === 'cost') return formatMoney(value)
  if (metricKey.value === 'requests') return formatInt(value)
  return formatTokens(value)
}

function selectMetric(key: MetricKey) {
  metricKey.value = key
  closeMetric()
}

/** x 轴只标 5 个刻度，避免柱子多时标签挤在一起 */
const axisLabels = computed(() => {
  if (!usage.value) return []
  const { buckets } = usage.value
  if (buckets.length <= 5) return buckets.map((bucket) => bucket.label)
  const step = (buckets.length - 1) / 4
  return Array.from({ length: 5 }, (_, index) => buckets[Math.round(index * step)].label)
})

function barHeight(value: number): string {
  return `${Math.max(3, Math.round((value / peak.value) * 100))}%`
}

function selectRange(key: RangeKey) {
  rangeKey.value = key
  closeRange()
}

/**
 * 悬停提示横向贴边时会被裁掉，这里把提示拉回图表范围内。
 * 位移写进 --tip-shift，由 CSS 计算最终位置。
 */
function onBarEnter(event: MouseEvent) {
  const bar = event.currentTarget as HTMLElement
  const tip = bar.querySelector<HTMLElement>('.bar-tip')
  const chart = bar.closest<HTMLElement>('.chart')
  if (!tip || !chart) return

  const barRect = bar.getBoundingClientRect()
  const chartRect = chart.getBoundingClientRect()
  const center = barRect.left + barRect.width / 2
  const half = tip.offsetWidth / 2
  let shift = 0
  if (center - half < chartRect.left) shift = chartRect.left - (center - half)
  else if (center + half > chartRect.right) shift = chartRect.right - (center + half)
  tip.style.setProperty('--tip-shift', `${shift}px`)
}

/** createTime 是 ISO LocalDateTime（含 T），只展示到秒 */
function formatTime(value: string): string {
  return value.replace('T', ' ').slice(0, 19)
}

/** 最近请求行点击：跳到对应会话（没有 sessionId 的行不可点） */
function openRequest(item: RecentRequestItem) {
  if (!item.sessionId) return
  void router.push({ name: 'chat-session', params: { sessionId: item.sessionId } })
}
</script>

<template>
  <div class="profile-inner">
    <div class="identity">
      <img
        v-if="auth.user?.avatar"
        class="identity-avatar"
        :src="auth.user.avatar"
        alt="头像"
      />
      <span v-else class="identity-avatar" aria-hidden="true">{{ avatarChar }}</span>
      <div class="identity-main">
        <h1 class="identity-name">{{ displayName }}</h1>
        <div class="identity-email">{{ auth.user?.email || '—' }}</div>
      </div>
      <div class="identity-actions">
        <button class="btn-ghost" type="button" @click="editProfileOpen = true">编辑资料</button>
        <button class="btn-ghost" type="button" @click="passwordOpen = true">修改密码</button>
      </div>
    </div>

    <EditProfileDialog v-if="editProfileOpen" @close="editProfileOpen = false" />
    <PasswordDialog v-if="passwordOpen" @close="passwordOpen = false" />

    <div class="usage-head">
      <h2 class="section-title">用量与费用</h2>

      <div :ref="bindRangeRoot" class="range-picker">
        <button
          class="range-btn"
          type="button"
          aria-haspopup="menu"
          :aria-expanded="rangeOpen"
          @click="toggleRange()"
        >
          <span class="range-label">时间维度</span>
          <span class="range-current">{{ rangeLabel }}</span>
          <AppIcon name="chevron" :size="13" class="chevron" />
        </button>
        <div v-if="rangeOpen" class="more-menu range-menu" role="menu">
          <button
            v-for="option in RANGE_OPTIONS"
            :key="option.key"
            class="more-item"
            type="button"
            role="menuitemradio"
            :aria-checked="option.key === rangeKey"
            @click="selectRange(option.key)"
          >
            {{ option.label }}
            <AppIcon v-if="option.key === rangeKey" name="check" :size="14" class="range-check" />
          </button>
        </div>
      </div>

      <span v-if="rangeKey === 'custom'" class="range-custom">
        <input v-model="custom.start" class="range-date" type="date" aria-label="开始日期" />
        <span>至</span>
        <input v-model="custom.end" class="range-date" type="date" aria-label="结束日期" />
      </span>
    </div>

    <template v-if="usage">
      <div class="usage-cards">
        <div class="stat">
          <div class="stat-label">消费金额</div>
          <div class="stat-value">
            {{ formatMoney(usage.totals.cost) }}<span class="stat-unit">CNY</span>
          </div>
        </div>
        <div class="stat">
          <div class="stat-label">API 请求次数</div>
          <div class="stat-value">{{ formatInt(usage.totals.requests) }}</div>
        </div>
        <div class="stat">
          <div class="stat-label">Tokens</div>
          <div class="stat-value">{{ formatTokens(usage.totals.tokens) }}</div>
          <div class="stat-sub">
            输入 {{ formatTokens(usage.totals.inputTokens) }} / 输出
            {{ formatTokens(usage.totals.outputTokens) }}
          </div>
        </div>
      </div>

      <div class="section-head">
        <h2 class="section-title">每日用量</h2>
        <span class="section-sub">{{ usage.description }}</span>

        <div :ref="bindMetricRoot" class="range-picker metric-picker">
          <button
            class="range-btn"
            type="button"
            aria-haspopup="menu"
            :aria-expanded="metricOpen"
            @click="toggleMetric()"
          >
            <span class="range-label">统计维度</span>
            <span class="range-current">{{ metricLabel }}</span>
            <AppIcon name="chevron" :size="13" class="chevron" />
          </button>
          <div v-if="metricOpen" class="more-menu range-menu" role="menu">
            <button
              v-for="option in METRIC_OPTIONS"
              :key="option.key"
              class="more-item"
              type="button"
              role="menuitemradio"
              :aria-checked="option.key === metricKey"
              @click="selectMetric(option.key)"
            >
              {{ option.label }}
              <AppIcon v-if="option.key === metricKey" name="check" :size="14" class="range-check" />
            </button>
          </div>
        </div>
      </div>
      <div class="chart">
        <div class="chart-bars">
          <div
            v-for="(bucket, index) in usage.buckets"
            :key="index"
            class="bar"
            :style="{ height: barHeight(metricValue(bucket)) }"
            tabindex="0"
            @mouseenter="onBarEnter"
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
    <div v-else class="usage-loading">{{ loadingUsage ? '正在加载用量统计…' : '' }}</div>

    <div class="section-head">
      <h2 class="section-title">最近请求记录</h2>
      <span class="section-sub">{{ rangeLabel }}</span>
    </div>
    <div class="recent-table">
      <div class="recent-scroll">
        <table v-if="recent && recent.length > 0">
          <thead>
            <tr>
              <th class="col-time">请求时间</th>
              <th class="col-title">请求 ID</th>
              <th>模型</th>
              <th class="col-num">输入</th>
              <th class="col-num">输出</th>
              <th class="col-num">耗时</th>
              <th class="col-num">费用</th>
            </tr>
          </thead>
          <tbody>
            <tr
              v-for="(item, index) in recent"
              :key="index"
              :class="{ clickable: !!item.sessionId }"
              :title="item.sessionId ? '打开对应会话' : undefined"
              @click="openRequest(item)"
            >
              <td class="col-time">{{ formatTime(item.createTime) }}</td>
              <td class="col-title" :title="item.requestId">{{ item.requestId }}</td>
              <td>{{ item.modelName }}</td>
              <td class="col-num">{{ formatInt(item.inputTokens) }}</td>
              <td class="col-num">{{ formatInt(item.outputTokens) }}</td>
              <td class="col-num">{{ item.durationSeconds.toFixed(1) }} s</td>
              <td class="col-num">{{ formatMoney(item.cost) }}</td>
            </tr>
          </tbody>
        </table>
        <div v-else class="recent-empty">
          {{ loadingRecent ? '正在加载最近请求…' : '该时间段暂无请求记录' }}
        </div>
      </div>
    </div>
  </div>
</template>
