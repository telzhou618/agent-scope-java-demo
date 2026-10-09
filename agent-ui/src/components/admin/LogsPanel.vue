<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import AppIcon from '../AppIcon.vue'
import { ApiError } from '../../api/http'
import { pageLogs, type OperationLogItem } from '../../api/user'

/* ---- 列表与分页 ---- */
const PAGE_SIZE = 10
const keyword = ref('')
const appliedKeyword = ref('')
const page = ref(1)
const total = ref(0)
const records = ref<OperationLogItem[]>([])
const loading = ref(false)
const loadError = ref('')

const totalPages = computed(() => Math.max(1, Math.ceil(total.value / PAGE_SIZE)))

async function load() {
  loading.value = true
  loadError.value = ''
  try {
    const result = await pageLogs({ page: page.value, size: PAGE_SIZE, keyword: appliedKeyword.value })
    records.value = result.records ?? []
    total.value = result.total ?? 0
    if (page.value > totalPages.value) {
      page.value = totalPages.value
      return load()
    }
  } catch (e) {
    records.value = []
    total.value = 0
    loadError.value = e instanceof ApiError ? e.message : '加载操作日志失败'
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

function goPage(next: number) {
  if (next < 1 || next > totalPages.value || next === page.value) return
  page.value = next
  void load()
}

const clip = (text: string, max = 40) => (text && text.length > max ? `${text.slice(0, max)}…` : text || '—')

const isSuccess = (item: OperationLogItem) => item.result === 'success'

onMounted(() => void load())
</script>

<template>
  <div>
    <div class="section-head users-head">
      <h2 class="section-title">操作日志</h2>
      <span class="section-sub">共 {{ total }} 条日志</span>
    </div>

    <div class="users-toolbar">
      <div class="users-search">
        <AppIcon name="search" :size="14" class="users-search-icon" />
        <input
          v-model="keyword"
          class="login-input users-search-input"
          type="search"
          placeholder="搜索用户名 / 操作 / 路径，回车确认"
          aria-label="搜索日志"
          @keydown.enter.prevent="applySearch(keyword)"
        />
      </div>
    </div>

    <div class="recent-table">
      <div class="recent-scroll">
        <table v-if="records.length > 0">
          <thead>
            <tr>
              <th>时间</th>
              <th>用户</th>
              <th>操作</th>
              <th>方法</th>
              <th>路径</th>
              <th>耗时(ms)</th>
              <th>结果</th>
              <th>参数</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="item in records" :key="item.id">
              <td class="col-time">{{ item.createTime }}</td>
              <td>{{ item.username || '—' }}</td>
              <td>{{ item.operation || '—' }}</td>
              <td>{{ item.method || '—' }}</td>
              <td>
                <span class="cell-clip" :title="item.path">{{ clip(item.path) }}</span>
              </td>
              <td class="col-num">{{ item.costMs }}</td>
              <td>
                <span
                  class="user-status"
                  :class="isSuccess(item) ? 'log-result-ok' : 'log-result-fail'"
                  :title="item.result"
                >
                  {{ isSuccess(item) ? '成功' : '失败' }}
                </span>
              </td>
              <td>
                <span class="cell-clip" :title="item.params">{{ clip(item.params) }}</span>
              </td>
            </tr>
          </tbody>
        </table>
        <div v-else class="recent-empty">
          {{ loading ? '正在加载操作日志…' : loadError || '暂无日志' }}
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
