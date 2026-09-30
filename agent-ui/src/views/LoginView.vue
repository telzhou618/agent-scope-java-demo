<script setup lang="ts">
import { ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import AppIcon from '../components/AppIcon.vue'
import { useAuthStore } from '../stores/auth'
import { ApiError } from '../api/http'

const auth = useAuthStore()
const route = useRoute()
const router = useRouter()

const account = ref('')
const password = ref('')
const loading = ref(false)
const error = ref('')

async function onSubmit() {
  if (loading.value || !account.value.trim() || !password.value) return
  loading.value = true
  error.value = ''
  try {
    await auth.login(account.value, password.value)
    const redirect = typeof route.query.redirect === 'string' ? route.query.redirect : ''
    await router.replace(redirect || '/chat')
  } catch (e) {
    error.value = e instanceof ApiError ? e.message : '登录失败，请稍后再试'
    password.value = ''
  } finally {
    loading.value = false
  }
}
</script>

<template>
  <div class="login-page">
    <div class="login-card">
      <div class="login-brand">
        <span class="brand-mark" aria-hidden="true">A</span>
        <span class="login-title">登录 Agent</span>
      </div>
      <p class="login-sub">登录后继续你的对话</p>

      <form @submit.prevent="onSubmit">
        <label class="login-field">
          <span class="login-label">用户名或邮箱</span>
          <input
            v-model="account"
            class="login-input"
            type="text"
            autocomplete="username"
            placeholder="admin 或 admin@example.com"
            autofocus
          />
        </label>
        <label class="login-field">
          <span class="login-label">密码</span>
          <input
            v-model="password"
            class="login-input"
            type="password"
            autocomplete="current-password"
            placeholder="输入密码"
          />
        </label>

        <p v-if="error" class="login-error">
          <AppIcon name="x" :size="13" />
          <span>{{ error }}</span>
        </p>

        <button
          class="login-submit"
          type="submit"
          :disabled="loading || !account.trim() || !password"
        >
          {{ loading ? '登录中…' : '登 录' }}
        </button>
      </form>
    </div>
  </div>
</template>
