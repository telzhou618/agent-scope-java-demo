<script setup lang="ts">
import { h, onMounted, ref } from 'vue'
import {
  NButton,
  NDataTable,
  NInput,
  NTag,
  useMessage,
  type DataTableColumns,
} from 'naive-ui'
import { NIcon } from 'naive-ui'
import { SearchOutline } from '@vicons/ionicons5'
import { pageLogs, type OperationLogItem } from '../../api/user'

const message = useMessage()

/* ---- 列表与分页（服务端分页） ---- */
const PAGE_SIZE = 10
const keyword = ref('')
const appliedKeyword = ref('')
const page = ref(1)
const total = ref(0)
const records = ref<OperationLogItem[]>([])
const loading = ref(false)

async function load() {
  loading.value = true
  try {
    const result = await pageLogs({ page: page.value, size: PAGE_SIZE, keyword: appliedKeyword.value })
    records.value = result.records ?? []
    total.value = result.total ?? 0
    const totalPages = Math.max(1, Math.ceil(total.value / PAGE_SIZE))
    if (page.value > totalPages) {
      page.value = totalPages
      return load()
    }
  } catch (error) {
    records.value = []
    total.value = 0
    message.error(error instanceof Error ? error.message : '加载操作日志失败')
  } finally {
    loading.value = false
  }
}

function applySearch() {
  const next = keyword.value.trim()
  if (next === appliedKeyword.value) return
  appliedKeyword.value = next
  page.value = 1
  void load()
}

const isSuccess = (item: OperationLogItem) => item.result === 'success'

const columns: DataTableColumns<OperationLogItem> = [
  {
    title: '时间',
    key: 'createTime',
    width: 165,
    render: (row) => (row.createTime ?? '').replace('T', ' ').slice(0, 19) || '—',
  },
  { title: '用户', key: 'username', width: 90, render: (row) => row.username || '—' },
  { title: '操作', key: 'operation', width: 130, ellipsis: { tooltip: true }, render: (row) => row.operation || '—' },
  { title: '方法', key: 'method', width: 70, render: (row) => row.method || '—' },
  {
    title: '路径',
    key: 'path',
    ellipsis: { tooltip: true },
    render: (row) => row.path || '—',
  },
  { title: '耗时(ms)', key: 'costMs', width: 90, align: 'right' },
  {
    title: '结果',
    key: 'result',
    width: 80,
    render: (row) =>
      h(
        NTag,
        {
          size: 'small',
          type: isSuccess(row) ? 'success' : 'error',
          bordered: false,
          round: true,
          title: row.result,
        },
        { default: () => (isSuccess(row) ? '成功' : '失败') },
      ),
  },
  {
    title: '参数',
    key: 'params',
    ellipsis: { tooltip: true },
    render: (row) => row.params || '—',
  },
]

onMounted(() => void load())
</script>

<template>
  <div>
    <div class="flex items-center gap-2 mb-3 flex-wrap">
      <NInput
        v-model:value="keyword"
        size="small"
        clearable
        class="max-w-70"
        placeholder="搜索用户名 / 操作 / 路径"
        @keydown.enter="applySearch"
        @clear="applySearch"
      >
        <template #prefix>
          <NIcon><SearchOutline /></NIcon>
        </template>
      </NInput>
      <NButton size="small" @click="applySearch">搜索</NButton>
    </div>

    <NDataTable
      remote
      :columns="columns"
      :data="records"
      :loading="loading"
      :bordered="false"
      size="small"
      :pagination="{
        page,
        pageSize: PAGE_SIZE,
        itemCount: total,
        onUpdatePage: (next: number) => {
          page = next
          load()
        },
      }"
      :scroll-x="1000"
    />
  </div>
</template>
