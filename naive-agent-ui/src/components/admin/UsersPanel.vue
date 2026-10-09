<script setup lang="ts">
import { computed, h, onMounted, reactive, ref } from 'vue'
import {
  NButton,
  NCheckbox,
  NCheckboxGroup,
  NDataTable,
  NForm,
  NFormItem,
  NInput,
  NModal,
  NPopconfirm,
  NSwitch,
  NTag,
  useMessage,
  type DataTableColumns,
  type FormInst,
  type FormRules,
} from 'naive-ui'
import { AddOutline, SearchOutline } from '@vicons/ionicons5'
import { NIcon } from 'naive-ui'
import { getAgents } from '../../api/agent'
import type { AgentInfo } from '../../api/types'
import {
  createUser,
  deleteUser,
  pageUsers,
  setUserStatus,
  updateUser,
  type UserManageItem,
  type UserSavePayload,
} from '../../api/user'
import { useAuthStore } from '../../stores/auth'
import { agentShortLabel } from '../../stores/agents'

const message = useMessage()
const auth = useAuthStore()

/* ---- 列表与分页（服务端分页） ---- */
const PAGE_SIZE = 10
const keyword = ref('')
const appliedKeyword = ref('')
const page = ref(1)
const total = ref(0)
const records = ref<UserManageItem[]>([])
const loading = ref(false)

async function load() {
  loading.value = true
  try {
    const result = await pageUsers({ page: page.value, size: PAGE_SIZE, keyword: appliedKeyword.value })
    records.value = result.records ?? []
    total.value = result.total ?? 0
    // 删除末页最后一条等场景下页码可能超出，回退后重新拉
    const totalPages = Math.max(1, Math.ceil(total.value / PAGE_SIZE))
    if (page.value > totalPages) {
      page.value = totalPages
      return load()
    }
  } catch (error) {
    records.value = []
    total.value = 0
    message.error(error instanceof Error ? error.message : '加载用户列表失败')
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

/* ---- Agent 选项（管理员拿到的是全量列表） ---- */
const agentOptions = ref<AgentInfo[]>([])

/* ---- 新建 / 编辑弹窗 ---- */
interface UserForm {
  id: number | null
  username: string
  password: string
  email: string
  nickname: string
  avatar: string
  status: number
  isAdmin: boolean
  agents: string[]
}

const dialogOpen = ref(false)
const editing = ref(false)
const submitting = ref(false)
const formRef = ref<FormInst | null>(null)
const form = reactive<UserForm>({
  id: null,
  username: '',
  password: '',
  email: '',
  nickname: '',
  avatar: '',
  status: 1,
  isAdmin: false,
  agents: [],
})

const formRules = computed<FormRules>(() => ({
  username: [{ required: !editing.value, message: '请填写用户名', trigger: 'blur' }],
  password: [{ required: !editing.value, message: '请填写密码', trigger: 'blur' }],
  email: [
    { required: true, message: '请填写邮箱', trigger: 'blur' },
    { pattern: /^[^\s@]+@[^\s@]+\.[^\s@]+$/, message: '邮箱格式不正确', trigger: 'blur' },
  ],
}))

function resetForm() {
  form.id = null
  form.username = ''
  form.password = ''
  form.email = ''
  form.nickname = ''
  form.avatar = ''
  form.status = 1
  form.isAdmin = false
  form.agents = []
}

function openCreate() {
  resetForm()
  editing.value = false
  dialogOpen.value = true
}

function openEdit(item: UserManageItem) {
  resetForm()
  editing.value = true
  form.id = item.id
  form.username = item.username
  form.email = item.email
  form.nickname = item.nickname
  form.avatar = item.avatar
  form.status = item.status
  form.isAdmin = item.isAdmin
  form.agents = [...(item.agents ?? [])]
  dialogOpen.value = true
}

/** 内置 admin 账号：编辑时不允许取消管理员身份（后端同样拦截） */
const isBuiltinAdmin = computed(() => editing.value && form.username === 'admin')

async function submitForm() {
  try {
    await formRef.value?.validate()
  } catch {
    return
  }
  submitting.value = true
  try {
    const payload: UserSavePayload = {
      email: form.email.trim(),
      nickname: form.nickname.trim(),
      avatar: form.avatar.trim(),
      status: form.status,
      isAdmin: form.isAdmin,
      agents: [...form.agents],
    }
    if (editing.value) {
      payload.id = form.id ?? undefined
      // 编辑时密码留空表示不修改，不下发该字段
      if (form.password) payload.password = form.password
      await updateUser(payload)
    } else {
      payload.username = form.username.trim()
      payload.password = form.password
      await createUser(payload)
    }
    message.success(editing.value ? '用户已更新' : '用户已创建')
    dialogOpen.value = false
    void load()
  } catch (error) {
    message.error(error instanceof Error ? error.message : '保存失败，请稍后重试')
  } finally {
    submitting.value = false
  }
}

/* ---- 行操作 ---- */
/** 当前登录账号本人所在行不允许禁用/删除 */
const isSelf = (item: UserManageItem) => item.id === auth.user?.id

async function toggleStatus(item: UserManageItem) {
  const next = item.status === 1 ? 0 : 1
  try {
    await setUserStatus({ id: item.id, status: next })
    message.success(next === 1 ? '已启用' : '已禁用')
    void load()
  } catch (error) {
    message.error(error instanceof Error ? error.message : '操作失败，请稍后重试')
  }
}

async function removeUser(item: UserManageItem) {
  try {
    await deleteUser(item.id)
    message.success('用户已删除')
    void load()
  } catch (error) {
    message.error(error instanceof Error ? error.message : '删除失败，请稍后重试')
  }
}

const columns: DataTableColumns<UserManageItem> = [
  { title: '用户名', key: 'username', width: 110 },
  {
    title: '昵称',
    key: 'nickname',
    width: 110,
    render: (row) => row.nickname || '—',
  },
  {
    title: '邮箱',
    key: 'email',
    ellipsis: { tooltip: true },
    render: (row) => row.email || '—',
  },
  {
    title: 'Agents',
    key: 'agents',
    width: 180,
    render: (row) => {
      if (row.isAdmin) return h(NTag, { size: 'small', type: 'primary', bordered: false }, { default: () => '全部' })
      if (row.agents?.length) {
        return h(
          'div',
          { class: 'flex flex-wrap gap-1' },
          row.agents.map((name) =>
            h(NTag, { key: name, size: 'small', bordered: false }, { default: () => agentShortLabel(name) }),
          ),
        )
      }
      return h('span', { class: 'op-45' }, '无')
    },
  },
  {
    title: '状态',
    key: 'status',
    width: 80,
    render: (row) =>
      h(
        NTag,
        { size: 'small', type: row.status === 1 ? 'success' : 'default', bordered: false, round: true },
        { default: () => (row.status === 1 ? '启用' : '禁用') },
      ),
  },
  {
    title: '创建人',
    key: 'createdBy',
    width: 90,
    render: (row) => row.createdBy || '—',
  },
  {
    title: '编辑人',
    key: 'updatedBy',
    width: 90,
    render: (row) => row.updatedBy || '—',
  },
  {
    title: '操作',
    key: 'actions',
    width: 170,
    render: (row) =>
      h('div', { class: 'flex items-center gap-1' }, [
        h(NButton, { size: 'tiny', quaternary: true, onClick: () => openEdit(row) }, { default: () => '编辑' }),
        h(
          NPopconfirm,
          {
            onPositiveClick: () => toggleStatus(row),
            positiveText: '确认',
            negativeText: '取消',
          },
          {
            trigger: () =>
              h(
                NButton,
                {
                  size: 'tiny',
                  quaternary: true,
                  type: row.status === 1 ? 'warning' : 'success',
                  disabled: isSelf(row),
                },
                { default: () => (row.status === 1 ? '禁用' : '启用') },
              ),
            default: () =>
              `${row.status === 1 ? '禁用' : '启用'}用户「${row.nickname || row.username}」？${row.status === 1 ? '禁用后立即踢下线。' : ''}`,
          },
        ),
        h(
          NPopconfirm,
          {
            onPositiveClick: () => removeUser(row),
            positiveText: '删除',
            negativeText: '取消',
          },
          {
            trigger: () =>
              h(
                NButton,
                { size: 'tiny', quaternary: true, type: 'error', disabled: isSelf(row) },
                { default: () => '删除' },
              ),
            default: () => `删除用户「${row.nickname || row.username}」？删除后不可恢复。`,
          },
        ),
      ]),
  },
]

onMounted(() => {
  void load()
  void getAgents()
    .then((list) => {
      agentOptions.value = list ?? []
    })
    .catch(() => {
      /* Agent 选项加载失败时多选组为空，不影响列表展示 */
    })
})
</script>

<template>
  <div>
    <div class="flex items-center gap-2 mb-3 flex-wrap">
      <NInput
        v-model:value="keyword"
        size="small"
        clearable
        class="max-w-70"
        placeholder="搜索用户名 / 昵称 / 邮箱"
        @keydown.enter="applySearch"
        @clear="applySearch"
      >
        <template #prefix>
          <NIcon><SearchOutline /></NIcon>
        </template>
      </NInput>
      <NButton size="small" @click="applySearch">搜索</NButton>
      <span class="flex-1" />
      <NButton size="small" type="primary" @click="openCreate">
        <template #icon>
          <NIcon><AddOutline /></NIcon>
        </template>
        新增用户
      </NButton>
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
      :scroll-x="900"
    />

    <NModal
      v-model:show="dialogOpen"
      preset="card"
      :title="editing ? '编辑用户' : '新增用户'"
      class="max-w-lg w-[94vw]"
      :mask-closable="!submitting"
    >
      <NForm ref="formRef" :model="form" :rules="formRules" label-placement="top">
        <div class="grid grid-cols-1 sm:grid-cols-2 gap-x-4">
          <NFormItem label="用户名" path="username">
            <NInput
              v-model:value="form.username"
              maxlength="50"
              :disabled="editing"
              placeholder="登录账号"
            />
          </NFormItem>
          <NFormItem :label="editing ? '密码（留空不修改）' : '密码'" path="password">
            <NInput
              v-model:value="form.password"
              type="password"
              show-password-on="click"
              maxlength="100"
              autocomplete="new-password"
              :placeholder="editing ? '留空则不修改' : '登录密码'"
            />
          </NFormItem>
          <NFormItem label="邮箱" path="email">
            <NInput v-model:value="form.email" maxlength="100" placeholder="邮箱地址" />
          </NFormItem>
          <NFormItem label="昵称" path="nickname">
            <NInput v-model:value="form.nickname" maxlength="50" placeholder="留空则使用用户名" />
          </NFormItem>
        </div>
        <NFormItem label="头像" path="avatar">
          <NInput v-model:value="form.avatar" maxlength="500" placeholder="头像图片 URL（选填）" />
        </NFormItem>
        <div class="flex items-center gap-6 flex-wrap mb-2">
          <NFormItem label="启用" class="mb-0!">
            <NSwitch
              :value="form.status === 1"
              @update:value="(value: boolean) => (form.status = value ? 1 : 0)"
            />
          </NFormItem>
          <NFormItem label="管理员" class="mb-0!">
            <NCheckbox v-model:checked="form.isAdmin" :disabled="isBuiltinAdmin">
              {{ isBuiltinAdmin ? '内置 admin 账号不能取消管理员身份' : '是否管理员' }}
            </NCheckbox>
          </NFormItem>
        </div>
        <NFormItem label="Agents">
          <div class="flex flex-col gap-1">
            <NCheckboxGroup v-model:value="form.agents" :disabled="form.isAdmin">
              <div class="flex flex-wrap gap-x-4 gap-y-1">
                <NCheckbox v-for="agent in agentOptions" :key="agent.name" :value="agent.name">
                  {{ agent.displayName || agent.name }}
                </NCheckbox>
              </div>
            </NCheckboxGroup>
            <span v-if="!agentOptions.length" class="text-xs op-50">暂无可用 Agent</span>
            <span v-else-if="form.isAdmin" class="text-xs op-50">管理员拥有全部 Agent 权限</span>
          </div>
        </NFormItem>
      </NForm>
      <template #footer>
        <div class="flex justify-end gap-2">
          <NButton :disabled="submitting" @click="dialogOpen = false">取消</NButton>
          <NButton type="primary" :loading="submitting" @click="submitForm">保存</NButton>
        </div>
      </template>
    </NModal>
  </div>
</template>
