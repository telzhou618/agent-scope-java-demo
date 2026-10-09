<script setup lang="ts">
import { reactive, ref, computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import {
  NButton,
  NCard,
  NForm,
  NFormItem,
  NIcon,
  NInput,
  useMessage,
  type FormInst,
  type FormRules,
} from 'naive-ui'
import { ChatbubblesOutline, LockClosedOutline, PersonOutline } from '@vicons/ionicons5'
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
</script>

<template>
  <div class="login-page">
    <NCard class="login-card" :bordered="false" :style="cardStyle">
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
    </NCard>
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
  padding: 16px 8px;
  border-radius: 16px;
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
  background: linear-gradient(135deg, #6366f1, #a855f7);
  box-shadow: 0 12px 28px rgba(99, 102, 241, 0.45);
}
</style>
