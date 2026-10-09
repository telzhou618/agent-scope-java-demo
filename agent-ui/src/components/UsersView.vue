<script setup lang="ts">
import { computed, onMounted, reactive, ref, watch } from 'vue'
import AppIcon from './AppIcon.vue'
import { ApiError } from '../api/http'
import { getAgents } from '../api/agent'
import type { AgentInfo } from '../api/types'
import {
  createUser,
  deleteUser,
  pageUsers,
  setUserStatus,
  updateUser,
  type UserManageItem,
  type UserSavePayload,
} from '../api/user'
import { useAuthStore } from '../stores/auth'
import { agentShortLabel } from '../stores/agents'

const auth = useAuthStore()

/* ---- 列表与分页 ---- */
const PAGE_SIZE = 10
const keyword = ref('')
const appliedKeyword = ref('')
const page = ref(1)
const total = ref(0)
const records = ref<UserManageItem[]>([])
const loading = ref(false)
const loadError = ref('')

const totalPages = computed(() => Math.max(1, Math.ceil(total.value / PAGE_SIZE)))

async function load() {
  loading.value = true
  loadError.value = ''
  try {
    const result = await pageUsers({ page: page.value, size: PAGE_SIZE, keyword: appliedKeyword.value })
    records.value = result.records ?? []
    total.value = result.total ?? 0
    // 删除末页最后一条等场景下页码可能超出，回退后重新拉
    if (page.value > totalPages.value) {
      page.value = totalPages.value
      return load()
    }
  } catch (e) {
    records.value = []
    total.value = 0
    loadError.value = e instanceof ApiError ? e.message : '加载用户列表失败'
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
const formError = ref('')
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
  formError.value = ''
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

/** 内置 admin 账号：编辑时不允许取消管理员身份(后端同样拦截) */
const isBuiltinAdmin = computed(() => editing.value && form.username === 'admin')

function closeDialog() {
  if (submitting.value) return
  dialogOpen.value = false
}

function toggleFormAgent(name: string) {
  const index = form.agents.indexOf(name)
  if (index >= 0) form.agents.splice(index, 1)
  else form.agents.push(name)
}

const EMAIL_RE = /^[^\s@]+@[^\s@]+\.[^\s@]+$/

async function submitForm() {
  formError.value = ''
  if (!editing.value && !form.username.trim()) {
    formError.value = '请填写用户名'
    return
  }
  if (!editing.value && !form.password) {
    formError.value = '请填写密码'
    return
  }
  if (!form.email.trim()) {
    formError.value = '请填写邮箱'
    return
  }
  if (!EMAIL_RE.test(form.email.trim())) {
    formError.value = '邮箱格式不正确'
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
    dialogOpen.value = false
    void load()
  } catch (e) {
    formError.value = e instanceof ApiError ? e.message : '保存失败，请稍后重试'
  } finally {
    submitting.value = false
  }
}

/* ---- 行操作 ---- */
/** 当前登录账号本人所在行不允许禁用/删除 */
const isSelf = (item: UserManageItem) => item.id === auth.user?.id

async function toggleStatus(item: UserManageItem) {
  if (isSelf(item)) return
  const next = item.status === 1 ? 0 : 1
  const action = next === 1 ? '启用' : '禁用'
  if (!window.confirm(`确认${action}用户「${item.nickname || item.username}」？`)) return
  try {
    await setUserStatus({ id: item.id, status: next })
    void load()
  } catch (e) {
    window.alert(e instanceof ApiError ? e.message : `${action}失败，请稍后重试`)
  }
}

async function removeUser(item: UserManageItem) {
  if (isSelf(item)) return
  if (!window.confirm(`删除用户「${item.nickname || item.username}」？删除后不可恢复。`)) return
  try {
    await deleteUser(item.id)
    void load()
  } catch (e) {
    window.alert(e instanceof ApiError ? e.message : '删除失败，请稍后重试')
  }
}

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
  <div class="profile-inner">
    <div class="section-head users-head">
      <h2 class="section-title">用户管理</h2>
      <span class="section-sub">共 {{ total }} 个用户</span>
    </div>

    <div class="users-toolbar">
      <div class="users-search">
        <AppIcon name="search" :size="14" class="users-search-icon" />
        <input
          v-model="keyword"
          class="login-input users-search-input"
          type="search"
          placeholder="搜索用户名 / 昵称 / 邮箱，回车确认"
          aria-label="搜索用户"
          @keydown.enter.prevent="applySearch(keyword)"
        />
      </div>
      <button class="btn-primary users-add" type="button" @click="openCreate">
        <AppIcon name="plus" :size="14" />
        新增用户
      </button>
    </div>

    <div class="recent-table">
      <div class="recent-scroll">
        <table v-if="records.length > 0">
          <thead>
            <tr>
              <th>用户名</th>
              <th>昵称</th>
              <th>邮箱</th>
              <th>Agents</th>
              <th>状态</th>
              <th>创建人</th>
              <th>编辑人</th>
              <th>操作</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="item in records" :key="item.id">
              <td>{{ item.username }}</td>
              <td>{{ item.nickname || '—' }}</td>
              <td>{{ item.email || '—' }}</td>
              <td>
                <span v-if="item.isAdmin" class="session-agent">全部</span>
                <template v-else-if="item.agents && item.agents.length">
                  <span v-for="name in item.agents" :key="name" class="session-agent">
                    {{ agentShortLabel(name) }}
                  </span>
                </template>
                <span v-else class="users-none">无</span>
              </td>
              <td>
                <span class="user-status" :class="item.status === 1 ? 'on' : 'off'">
                  {{ item.status === 1 ? '启用' : '禁用' }}
                </span>
              </td>
              <td>{{ item.createdBy || '—' }}</td>
              <td>{{ item.updatedBy || '—' }}</td>
              <td>
                <div class="row-actions">
                  <button class="row-action" type="button" @click="openEdit(item)">编辑</button>
                  <button
                    class="row-action"
                    type="button"
                    :disabled="isSelf(item)"
                    :title="isSelf(item) ? '不能操作当前登录账号' : ''"
                    @click="toggleStatus(item)"
                  >
                    {{ item.status === 1 ? '禁用' : '启用' }}
                  </button>
                  <button
                    class="row-action danger"
                    type="button"
                    :disabled="isSelf(item)"
                    :title="isSelf(item) ? '不能操作当前登录账号' : ''"
                    @click="removeUser(item)"
                  >
                    删除
                  </button>
                </div>
              </td>
            </tr>
          </tbody>
        </table>
        <div v-else class="recent-empty">
          {{ loading ? '正在加载用户列表…' : loadError || '暂无用户' }}
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

    <Teleport to="body">
      <div v-if="dialogOpen" class="dialog-mask" @click.self="closeDialog">
        <div class="dialog user-dialog" role="dialog" :aria-label="editing ? '编辑用户' : '新增用户'">
          <div class="dialog-head">
            <span class="dialog-title">{{ editing ? '编辑用户' : '新增用户' }}</span>
            <button class="icon-btn" type="button" aria-label="关闭" @click="closeDialog">
              <AppIcon name="x" :size="16" />
            </button>
          </div>

          <label class="login-field">
            <span class="login-label">用户名 *</span>
            <input
              v-model="form.username"
              class="login-input"
              type="text"
              maxlength="50"
              :readonly="editing"
              :disabled="editing"
              placeholder="登录账号"
            />
          </label>
          <label class="login-field">
            <span class="login-label">{{ editing ? '密码' : '密码 *' }}</span>
            <input
              v-model="form.password"
              class="login-input"
              type="password"
              maxlength="100"
              :placeholder="editing ? '留空则不修改' : '登录密码'"
              autocomplete="new-password"
            />
          </label>
          <label class="login-field">
            <span class="login-label">邮箱 *</span>
            <input v-model="form.email" class="login-input" type="email" maxlength="100" placeholder="邮箱地址" />
          </label>
          <label class="login-field">
            <span class="login-label">昵称</span>
            <input v-model="form.nickname" class="login-input" type="text" maxlength="50" placeholder="留空则使用用户名" />
          </label>
          <label class="login-field">
            <span class="login-label">头像</span>
            <input v-model="form.avatar" class="login-input" type="text" maxlength="500" placeholder="头像图片 URL（选填）" />
          </label>

          <div class="login-field">
            <span class="login-label">状态</span>
            <div class="user-status-radios">
              <label class="user-status-radio">
                <input v-model.number="form.status" type="radio" name="user-status" :value="1" />
                启用
              </label>
              <label class="user-status-radio">
                <input v-model.number="form.status" type="radio" name="user-status" :value="0" />
                禁用
              </label>
            </div>
          </div>

          <label class="user-admin-check">
            <input v-model="form.isAdmin" type="checkbox" :disabled="isBuiltinAdmin" />
            是否管理员
            <span v-if="isBuiltinAdmin" class="user-agents-tip">内置 admin 账号不能取消管理员身份</span>
          </label>

          <div class="login-field">
            <span class="login-label">Agents</span>
            <div class="user-agents" :class="{ disabled: form.isAdmin }">
              <label v-for="agent in agentOptions" :key="agent.name" class="user-agent-check">
                <input
                  type="checkbox"
                  :checked="form.agents.includes(agent.name)"
                  :disabled="form.isAdmin"
                  @change="toggleFormAgent(agent.name)"
                />
                {{ agent.displayName }}
              </label>
              <span v-if="!agentOptions.length" class="users-none">暂无可用 Agent</span>
            </div>
            <p v-if="form.isAdmin" class="user-agents-tip">管理员拥有全部 Agent 权限</p>
          </div>

          <p v-if="formError" class="attach-error">{{ formError }}</p>
          <div class="dialog-foot">
            <button class="btn-ghost" type="button" @click="closeDialog">取消</button>
            <button class="btn-primary" type="button" :disabled="submitting" @click="submitForm">
              {{ submitting ? '保存中…' : '保存' }}
            </button>
          </div>
        </div>
      </div>
    </Teleport>
  </div>
</template>
