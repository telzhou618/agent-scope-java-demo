<script setup lang="ts">
import { h, onMounted, ref, watch } from 'vue'
import {
  NButton,
  NCheckbox,
  NDataTable,
  NInput,
  NModal,
  NSelect,
  NTag,
  useMessage,
  type DataTableColumns,
} from 'naive-ui'
import { NIcon } from 'naive-ui'
import { SearchOutline } from '@vicons/ionicons5'
import { pageFeedbacks, setFeedbackStatus, type FeedbackItem } from '../../api/user'

const message = useMessage()

/* ---- 列表与分页（服务端分页） ---- */
const PAGE_SIZE = 10
const keyword = ref('')
const appliedKeyword = ref('')
/** '' 全部 / 0 待处理 / 1 已处理 */
const statusFilter = ref<'' | 0 | 1>('')
const page = ref(1)
const total = ref(0)
const records = ref<FeedbackItem[]>([])
const loading = ref(false)

const statusOptions = [
  { label: '全部状态', value: '' },
  { label: '待处理', value: 0 },
  { label: '已处理', value: 1 },
]

async function load() {
  loading.value = true
  try {
    const result = await pageFeedbacks({
      page: page.value,
      size: PAGE_SIZE,
      keyword: appliedKeyword.value,
      status: statusFilter.value === '' ? undefined : statusFilter.value,
    })
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
    message.error(error instanceof Error ? error.message : '加载意见反馈失败')
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

watch(statusFilter, () => {
  page.value = 1
  void load()
})

/* ---- 行展示与操作 ---- */
const TYPE_LABELS: Record<string, string> = { bug: '缺陷', idea: '建议', other: '其他' }
const TYPE_TAG: Record<string, 'error' | 'info' | 'default'> = { bug: 'error', idea: 'info', other: 'default' }

const toggling = ref<number | null>(null)

/* ---- 标记已处理弹框：处理结果（选填）+ 是否通知用户 ---- */
const doneDialogOpen = ref(false)
const doneTarget = ref<FeedbackItem | null>(null)
const doneResult = ref('')
const doneNotify = ref(true)
const doneSubmitting = ref(false)

function openDoneDialog(item: FeedbackItem) {
  doneTarget.value = item
  doneResult.value = ''
  doneNotify.value = true
  doneDialogOpen.value = true
}

async function submitDone() {
  const item = doneTarget.value
  if (!item) return
  doneSubmitting.value = true
  try {
    await setFeedbackStatus({
      id: item.id,
      status: 1,
      result: doneResult.value.trim() || undefined,
      notify: doneNotify.value,
    })
    message.success('已标记为已处理')
    doneDialogOpen.value = false
    void load()
  } catch (error) {
    message.error(error instanceof Error ? error.message : '操作失败，请稍后重试')
  } finally {
    doneSubmitting.value = false
  }
}

async function toggleStatus(item: FeedbackItem) {
  // 标记为已处理走弹框（处理结果 + 是否通知用户）；改回待处理直接调接口
  if (item.status !== 1) {
    openDoneDialog(item)
    return
  }
  toggling.value = item.id
  try {
    await setFeedbackStatus({ id: item.id, status: 0 })
    message.success('已标记为待处理')
    void load()
  } catch (error) {
    message.error(error instanceof Error ? error.message : '操作失败，请稍后重试')
  } finally {
    toggling.value = null
  }
}

const columns: DataTableColumns<FeedbackItem> = [
  { title: '用户', key: 'username', width: 100, render: (row) => row.username || '—' },
  {
    title: '类型',
    key: 'type',
    width: 80,
    render: (row) =>
      h(
        NTag,
        { size: 'small', type: TYPE_TAG[row.type] ?? 'default', bordered: false, round: true },
        { default: () => TYPE_LABELS[row.type] ?? '其他' },
      ),
  },
  {
    title: '内容',
    key: 'content',
    ellipsis: { tooltip: true },
    render: (row) => row.content || '—',
  },
  { title: '联系方式', key: 'contact', width: 140, ellipsis: { tooltip: true }, render: (row) => row.contact || '—' },
  {
    title: '状态',
    key: 'status',
    width: 90,
    render: (row) =>
      h(
        NTag,
        { size: 'small', type: row.status === 1 ? 'success' : 'warning', bordered: false, round: true },
        { default: () => (row.status === 1 ? '已处理' : '待处理') },
      ),
  },
  {
    title: '提交时间',
    key: 'createTime',
    width: 165,
    render: (row) => (row.createTime ?? '').replace('T', ' ').slice(0, 19) || '—',
  },
  {
    title: '操作',
    key: 'actions',
    width: 110,
    render: (row) =>
      h(
        NButton,
        {
          size: 'tiny',
          quaternary: true,
          loading: toggling.value === row.id,
          onClick: () => toggleStatus(row),
        },
        { default: () => (row.status === 1 ? '标记待处理' : '标记已处理') },
      ),
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
        placeholder="搜索内容 / 联系方式"
        @keydown.enter="applySearch"
        @clear="applySearch"
      >
        <template #prefix>
          <NIcon><SearchOutline /></NIcon>
        </template>
      </NInput>
      <NButton size="small" @click="applySearch">搜索</NButton>
      <NSelect
        v-model:value="statusFilter"
        :options="statusOptions"
        size="small"
        class="w-32"
        :consistent-menu-width="false"
      />
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
      :scroll-x="860"
    />

    <!-- 标记已处理：处理结果（选填）+ 是否通知用户 -->
    <NModal
      v-model:show="doneDialogOpen"
      preset="card"
      title="标记为已处理"
      style="width: min(440px, calc(100vw - 32px))"
    >
      <div class="done-dialog">
        <div class="done-quote">{{ doneTarget?.content }}</div>
        <NInput
          v-model:value="doneResult"
          type="textarea"
          :autosize="{ minRows: 3, maxRows: 6 }"
          placeholder="处理结果（选填），勾选通知用户时将随系统消息发给对方"
        />
        <NCheckbox v-model:checked="doneNotify">发送系统消息通知该用户</NCheckbox>
        <div class="done-actions">
          <NButton size="small" @click="doneDialogOpen = false">取消</NButton>
          <NButton size="small" type="primary" :loading="doneSubmitting" @click="submitDone">
            确认已处理
          </NButton>
        </div>
      </div>
    </NModal>
  </div>
</template>

<style scoped>
.done-dialog {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.done-quote {
  padding: 8px 12px;
  border-left: 3px solid var(--brand-border);
  background: var(--brand-soft);
  border-radius: 0 8px 8px 0;
  font-size: 12.5px;
  opacity: 0.75;
}

.done-actions {
  display: flex;
  justify-content: flex-end;
  gap: 8px;
}
</style>
