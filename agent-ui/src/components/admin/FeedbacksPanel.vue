<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import AppIcon from '../AppIcon.vue'
import { ApiError } from '../../api/http'
import { pageFeedbacks, setFeedbackStatus, type FeedbackItem } from '../../api/user'

/* ---- 列表与分页 ---- */
const PAGE_SIZE = 10
const keyword = ref('')
const appliedKeyword = ref('')
/** '' 全部 / '0' 待处理 / '1' 已处理 */
const statusFilter = ref('')
const page = ref(1)
const total = ref(0)
const records = ref<FeedbackItem[]>([])
const loading = ref(false)
const loadError = ref('')

const totalPages = computed(() => Math.max(1, Math.ceil(total.value / PAGE_SIZE)))

async function load() {
  loading.value = true
  loadError.value = ''
  try {
    const result = await pageFeedbacks({
      page: page.value,
      size: PAGE_SIZE,
      keyword: appliedKeyword.value,
      status: statusFilter.value === '' ? undefined : Number(statusFilter.value),
    })
    records.value = result.records ?? []
    total.value = result.total ?? 0
    // 状态变更后末页可能超出，回退后重新拉
    if (page.value > totalPages.value) {
      page.value = totalPages.value
      return load()
    }
  } catch (e) {
    records.value = []
    total.value = 0
    loadError.value = e instanceof ApiError ? e.message : '加载意见反馈失败'
  } finally {
    loading.value = false
  }
}

/** 搜索：回车立即生效，输入防抖 300ms；条件变化回到第一页 */
let searchTimer: ReturnType<typeof setTimeout> | null = null
watch(keyword, (value) => {
  if (searchTimer) clearTimeout(searchTimer)
  searchTimer = setTimeout(() => applySearch(value), 300)
})

function applySearch(value: string) {
  const next = value.trim()
  if (next === appliedKeyword.value) return
  appliedKeyword.value = next
  page.value = 1
  void load()
}

watch(statusFilter, () => {
  page.value = 1
  void load()
})

function goPage(next: number) {
  if (next < 1 || next > totalPages.value || next === page.value) return
  page.value = next
  void load()
}

/* ---- 行展示与操作 ---- */
const TYPE_LABELS: Record<string, string> = { bug: '缺陷', idea: '建议', other: '其他' }
const typeLabel = (type: string) => TYPE_LABELS[type] ?? '其他'

const clip = (text: string, max = 40) => (text && text.length > max ? `${text.slice(0, max)}…` : text || '—')

const toggling = ref<number | null>(null)

async function toggleStatus(item: FeedbackItem) {
  const next = item.status === 1 ? 0 : 1
  toggling.value = item.id
  try {
    await setFeedbackStatus({ id: item.id, status: next })
    void load()
  } catch (e) {
    window.alert(e instanceof ApiError ? e.message : '操作失败，请稍后重试')
  } finally {
    toggling.value = null
  }
}

onMounted(() => void load())
</script>

<template>
  <div>
    <div class="section-head users-head">
      <h2 class="section-title">意见反馈</h2>
      <span class="section-sub">共 {{ total }} 条反馈</span>
    </div>

    <div class="users-toolbar">
      <div class="users-search">
        <AppIcon name="search" :size="14" class="users-search-icon" />
        <input
          v-model="keyword"
          class="login-input users-search-input"
          type="search"
          placeholder="搜索内容 / 联系方式，回车确认"
          aria-label="搜索反馈"
          @keydown.enter.prevent="applySearch(keyword)"
        />
      </div>
      <select v-model="statusFilter" class="admin-select" aria-label="按状态筛选">
        <option value="">全部状态</option>
        <option value="0">待处理</option>
        <option value="1">已处理</option>
      </select>
    </div>

    <div class="recent-table">
      <div class="recent-scroll">
        <table v-if="records.length > 0">
          <thead>
            <tr>
              <th>用户</th>
              <th>类型</th>
              <th>内容</th>
              <th>联系方式</th>
              <th>状态</th>
              <th>提交时间</th>
              <th>操作</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="item in records" :key="item.id">
              <td>{{ item.username || '—' }}</td>
              <td>{{ typeLabel(item.type) }}</td>
              <td>
                <span class="cell-clip" :title="item.content">{{ clip(item.content) }}</span>
              </td>
              <td>{{ item.contact || '—' }}</td>
              <td>
                <span class="user-status" :class="item.status === 1 ? 'fb-status-done' : 'fb-status-pending'">
                  {{ item.status === 1 ? '已处理' : '待处理' }}
                </span>
              </td>
              <td class="col-time">{{ item.createTime }}</td>
              <td>
                <div class="row-actions">
                  <button
                    class="row-action"
                    type="button"
                    :disabled="toggling === item.id"
                    @click="toggleStatus(item)"
                  >
                    {{ item.status === 1 ? '标记待处理' : '标记已处理' }}
                  </button>
                </div>
              </td>
            </tr>
          </tbody>
        </table>
        <div v-else class="recent-empty">
          {{ loading ? '正在加载意见反馈…' : loadError || '暂无反馈' }}
        </div>
      </div>
    </div>

    <div class="users-pager">
      <button class="btn-ghost" type="button" :disabled="page <= 1 || loading" @click="goPage(page - 1)">
        上一页
      </button>
      <span class="users-pager-info">第 {{ page }} / {{ totalPages }} 页 · 共 {{ total }} 条</span>
      <button
        class="btn-ghost"
        type="button"
        :disabled="page >= totalPages || loading"
        @click="goPage(page + 1)"
      >
        下一页
      </button>
    </div>
  </div>
</template>
