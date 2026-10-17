<script setup lang="ts">
import { reactive, ref, computed, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import {
  NButton,
  NForm,
  NFormItem,
  NIcon,
  NInput,
  useMessage,
  type FormInst,
  type FormRules,
} from 'naive-ui'
import { ChatbubblesOutline, LogoGithub, LockClosedOutline, PersonOutline } from '@vicons/ionicons5'
import { oauthProviders } from '../api/auth'
import { setToken } from '../api/token'
import { useAuthStore } from '../stores/auth'
import { useThemeStore } from '../stores/theme'

const route = useRoute()
const router = useRouter()
const message = useMessage()
const auth = useAuthStore()
const theme = useThemeStore()

/** 玻璃拟态卡片底色：明暗主题下都保证可读 */
const cardStyle = computed(() => ({
  background: theme.isDark ? 'rgba(30, 27, 75, 0.72)' : 'rgba(255, 255, 255, 0.92)',
}))

const formRef = ref<FormInst | null>(null)
const loading = ref(false)

const model = reactive({
  account: '',
  password: '',
})

const rules: FormRules = {
  account: [{ required: true, message: '请输入用户名或邮箱', trigger: 'blur' }],
  password: [{ required: true, message: '请输入密码', trigger: 'blur' }],
}

async function onSubmit() {
  try {
    await formRef.value?.validate()
  } catch {
    return
  }
  loading.value = true
  try {
    await auth.login(model.account, model.password)
    message.success(`欢迎回来，${auth.user?.nickname || auth.user?.username}`)
    const redirect = typeof route.query.redirect === 'string' ? route.query.redirect : '/chat'
    router.replace(redirect)
  } catch (error) {
    message.error(error instanceof Error ? error.message : '登录失败')
  } finally {
    loading.value = false
  }
}

/* ---- OAuth 登录渠道 ---- */
const providers = ref<string[]>([])

onMounted(async () => {
  // OAuth 回调回填：后端登录成功后在 /login?token= 返回 token
  const token = route.query.token
  if (typeof token === 'string' && token) {
    setToken(token)
    await auth.fetchCurrent()
    message.success(`欢迎回来，${auth.user?.nickname || auth.user?.username}`)
    router.replace('/chat')
    return
  }
  const oauthError = route.query.oauthError
  if (typeof oauthError === 'string' && oauthError) {
    message.error(oauthError)
  }
  try {
    providers.value = (await oauthProviders()) ?? []
  } catch {
    providers.value = []
  }
})

/** 跳转到对应渠道授权页 */
function startOAuth(channel: string) {
  window.location.href = `/auth/oauth/${channel}/authorize`
}
</script>

<template>
  <div class="login-page">
    <div class="login-card" :style="cardStyle">
      <div class="mb-8 flex flex-col items-center gap-3">
        <div class="brand-badge">
          <NIcon size="30" color="#fff"><ChatbubblesOutline /></NIcon>
        </div>
        <div class="text-2xl font-bold">Agent Platform</div>
        <div class="text-sm op-55">登录以开始与智能体对话</div>
      </div>

      <NForm ref="formRef" :model="model" :rules="rules" size="large" @submit.prevent="onSubmit">
        <NFormItem path="account" :show-label="false">
          <NInput v-model:value="model.account" placeholder="用户名或邮箱" @keydown.enter="onSubmit">
            <template #prefix>
              <NIcon><PersonOutline /></NIcon>
            </template>
          </NInput>
        </NFormItem>
        <NFormItem path="password" :show-label="false">
          <NInput
            v-model:value="model.password"
            type="password"
            show-password-on="click"
            placeholder="密码"
            @keydown.enter="onSubmit"
          >
            <template #prefix>
              <NIcon><LockClosedOutline /></NIcon>
            </template>
          </NInput>
        </NFormItem>
        <NButton type="primary" block size="large" :loading="loading" attr-type="submit">
          登 录
        </NButton>
      </NForm>

      <!-- 第三方登录 -->
      <template v-if="providers.length">
        <div class="oauth-divider"><span>其他登录方式</span></div>
        <div class="oauth-list">
          <NButton
            v-for="channel in providers"
            :key="channel"
            block
            secondary
            class="oauth-btn"
            @click="startOAuth(channel)"
          >
            <template #icon>
              <NIcon><LogoGithub v-if="channel === 'github'" /></NIcon>
            </template>
            {{ channel === 'github' ? 'GitHub 登录' : channel }}
          </NButton>
        </div>
      </template>
    </div>
  </div>
</template>

<style scoped>
.login-page {
  min-height: 100vh;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 24px;
  background:
    radial-gradient(1200px 600px at 15% 0%, rgba(99, 102, 241, 0.35), transparent 60%),
    radial-gradient(900px 500px at 85% 100%, rgba(168, 85, 247, 0.3), transparent 60%),
    linear-gradient(160deg, #0f172a 0%, #1e1b4b 100%);
}

.login-card {
  width: 100%;
  max-width: 400px;
  padding: 32px 28px 28px;
  border: 1px solid rgba(255, 255, 255, 0.08);
  border-radius: 20px;
  backdrop-filter: blur(16px);
  box-shadow: 0 24px 64px rgba(15, 23, 42, 0.45);
}

.brand-badge {
  width: 60px;
  height: 60px;
  display: flex;
  align-items: center;
  justify-content: center;
  border-radius: 18px;
  background: linear-gradient(135deg, var(--brand-solid), var(--brand-solid-alt));
  box-shadow: 0 12px 28px var(--brand-ring);
}

/* 第三方登录分隔 */
.oauth-divider {
  display: flex;
  align-items: center;
  gap: 12px;
  margin: 20px 0 12px;
  font-size: 12px;
  opacity: 0.5;
}

.oauth-divider::before,
.oauth-divider::after {
  content: '';
  flex: 1;
  height: 1px;
  background: var(--divider-strong, rgba(100, 116, 139, 0.22));
}

.oauth-list {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.oauth-btn {
  gap: 8px;
}
</style>
