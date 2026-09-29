<script setup lang="ts">
import { computed, ref } from 'vue'
import { useChat } from '../stores/chat'
import { formatInt, formatMoney, formatTokens } from '../utils/format'

const { state, setUserId } = useChat()

/* ---- 演示数据：后端暂无用量相关接口，先按原型填充 ---- */
const profile = {
  name: '张三',
  email: 'zhangsan@example.com',
  plan: 'Pro 计划',
  joined: '2025 年 3 月加入',
  period: '2026 年 9 月',
  lastMonthCost: 60.84,
  models: [
    { name: 'Agent Pro', requests: 612, input: 1420000, output: 386000, cost: 52.8 },
    { name: 'Agent Flash', requests: 672, input: 780000, output: 254000, cost: 15.6 },
  ],
  // 9 月 1–29 日每日 token 消耗（单位：千）
  daily: [
    108, 120, 127, 114, 48, 37, 122, 115, 129, 117, 124, 52, 41, 118, 130, 113, 125, 104, 49, 34,
    117, 123, 134, 126, 112, 57, 40, 114, 90,
  ],
}

const userIdDraft = ref(state.userId)

const totals = computed(() => {
  const sum = (pick: (item: (typeof profile.models)[number]) => number) =>
    profile.models.reduce((total, item) => total + pick(item), 0)
  const totalRequests = sum((item) => item.requests)
  const totalInput = sum((item) => item.input)
  const totalOutput = sum((item) => item.output)
  const totalTokens = totalInput + totalOutput
  const totalCost = sum((item) => item.cost)
  return {
    totalRequests,
    totalInput,
    totalOutput,
    totalTokens,
    totalCost,
    delta: (totalCost / profile.lastMonthCost - 1) * 100,
  }
})

const stats = computed(() => {
  const t = totals.value
  return [
    {
      label: '本月 Token',
      value: formatTokens(t.totalTokens),
      note: `输入 ${formatTokens(t.totalInput)} · 输出 ${formatTokens(t.totalOutput)}`,
    },
    { label: '本月费用', value: formatMoney(t.totalCost), note: '按量计费' },
    {
      label: '请求次数',
      value: formatInt(t.totalRequests),
      note: `平均 ${(t.totalTokens / t.totalRequests / 1000).toFixed(1)}K / 次`,
    },
    {
      label: '较上月',
      value: `+${t.delta.toFixed(1)}%`,
      note: `上月 ${formatMoney(profile.lastMonthCost)}`,
    },
  ]
})

const peak = Math.max(...profile.daily)
const low = Math.min(...profile.daily)

const chartLabel = computed(
  () =>
    `${profile.period}每日 token 消耗，最高 ${formatTokens(peak * 1000)}，最低 ${formatTokens(low * 1000)}`,
)

function applyUserId() {
  void setUserId(userIdDraft.value)
}
</script>

<template>
  <div class="profile-inner">
    <div class="identity">
      <span class="identity-avatar" aria-hidden="true">张</span>
      <div class="identity-main">
        <h1 class="identity-name">{{ profile.name }}</h1>
        <div class="identity-email">{{ profile.email }}</div>
        <div class="identity-meta">
          <span class="badge">{{ profile.plan }}</span>
          <span>{{ profile.joined }}</span>
        </div>
      </div>
      <button class="btn-ghost" type="button" disabled title="暂未开放">管理订阅</button>
    </div>

    <div class="settings-row">
      <label class="settings-label" for="userId">用户 ID</label>
      <input
        id="userId"
        v-model="userIdDraft"
        class="settings-input"
        type="text"
        inputmode="numeric"
        placeholder="例如 1"
        @keydown.enter.prevent="applyUserId"
      />
      <button class="btn-ghost" type="button" @click="applyUserId">切换</button>
      <span class="settings-note">切换后按该用户加载会话列表（当前 {{ state.userId }}）</span>
    </div>

    <div class="section-head">
      <h2 class="section-title">用量与费用</h2>
      <span class="badge badge-demo">演示数据</span>
      <span class="section-sub">{{ profile.period }}</span>
    </div>
    <div class="stats">
      <div v-for="item in stats" :key="item.label" class="stat">
        <div class="stat-label">{{ item.label }}</div>
        <div class="stat-value">{{ item.value }}</div>
        <div class="stat-note">{{ item.note }}</div>
      </div>
    </div>

    <div class="section-head"><h2 class="section-title">按模型拆分</h2></div>
    <div class="table-wrap">
      <table class="usage">
        <thead>
          <tr><th>模型</th><th>请求</th><th>输入</th><th>输出</th><th>费用</th></tr>
        </thead>
        <tbody>
          <tr v-for="item in profile.models" :key="item.name">
            <td class="model-name">{{ item.name }}</td>
            <td class="num">{{ formatInt(item.requests) }}</td>
            <td class="num">{{ formatTokens(item.input) }}</td>
            <td class="num">{{ formatTokens(item.output) }}</td>
            <td class="num">{{ formatMoney(item.cost) }}</td>
          </tr>
        </tbody>
        <tfoot>
          <tr>
            <td>合计</td>
            <td class="num">{{ formatInt(totals.totalRequests) }}</td>
            <td class="num">{{ formatTokens(totals.totalInput) }}</td>
            <td class="num">{{ formatTokens(totals.totalOutput) }}</td>
            <td class="num">{{ formatMoney(totals.totalCost) }}</td>
          </tr>
        </tfoot>
      </table>
    </div>

    <div class="section-head">
      <h2 class="section-title">每日用量</h2>
      <span class="section-sub">9 月 1 – 29 日</span>
    </div>
    <div class="chart" role="img" :aria-label="chartLabel">
      <div class="chart-bars">
        <div
          v-for="(value, index) in profile.daily"
          :key="index"
          class="bar"
          :style="{ height: `${Math.max(4, Math.round((value / peak) * 100))}%` }"
          :title="`9 月 ${index + 1} 日 · ${formatTokens(value * 1000)} tokens`"
        />
      </div>
      <div class="chart-axis">
        <span>9/1</span><span>9/8</span><span>9/15</span><span>9/22</span><span>9/29</span>
      </div>
    </div>
    <p class="chart-note">9 月 29 日为当日截至现在，所以柱体偏低。</p>
  </div>
</template>
