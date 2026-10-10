<script setup lang="ts">
import { reactive, ref, watch } from 'vue'
import {
  NButton,
  NForm,
  NFormItem,
  NInput,
  NModal,
  useMessage,
  type FormInst,
  type FormRules,
} from 'naive-ui'
import { updateProfile } from '../api/auth'
import { useAuthStore } from '../stores/auth'

const props = defineProps<{ show: boolean }>()
const emit = defineEmits<{ 'update:show': [value: boolean] }>()

const message = useMessage()
const auth = useAuthStore()

const formRef = ref<FormInst | null>(null)
const submitting = ref(false)

const form = reactive({
  nickname: '',
  email: '',
  avatar: '',
})

// 组件常驻挂载而用户信息可能晚到：每次打开时回填当前值
watch(
  () => props.show,
  (show) => {
    if (!show) return
    form.nickname = auth.user?.nickname ?? ''
    form.email = auth.user?.email ?? ''
    form.avatar = auth.user?.avatar ?? ''
  },
)

const rules: FormRules = {
  email: [
    {
      pattern: /^[^\s@]+@[^\s@]+\.[^\s@]+$/,
      message: '邮箱格式不正确',
      trigger: 'blur',
    },
  ],
}

async function submit() {
  try {
    await formRef.value?.validate()
  } catch {
    return
  }
  submitting.value = true
  try {
    const updated = await updateProfile({
      nickname: form.nickname.trim(),
      email: form.email.trim(),
      avatar: form.avatar.trim(),
    })
    auth.user = updated
    message.success('资料已更新')
    emit('update:show', false)
  } catch (error) {
    message.error(error instanceof Error ? error.message : '保存失败，请稍后重试')
  } finally {
    submitting.value = false
  }
}
</script>

<template>
  <NModal
    :show="props.show"
    preset="card"
    title="编辑资料"
    class="max-w-md w-[92vw]"
    @update:show="emit('update:show', $event)"
  >
    <NForm ref="formRef" :model="form" :rules="rules" label-placement="top">
      <NFormItem label="昵称" path="nickname">
        <NInput v-model:value="form.nickname" maxlength="50" placeholder="输入昵称" />
      </NFormItem>
      <NFormItem label="邮箱" path="email">
        <NInput v-model:value="form.email" maxlength="100" placeholder="输入邮箱" />
      </NFormItem>
      <NFormItem label="头像 URL（选填）" path="avatar">
        <NInput v-model:value="form.avatar" maxlength="500" placeholder="https://…" />
      </NFormItem>
    </NForm>
    <template #footer>
      <div class="flex justify-end gap-2">
        <NButton @click="emit('update:show', false)">取消</NButton>
        <NButton type="primary" :loading="submitting" @click="submit">保存</NButton>
      </div>
    </template>
  </NModal>
</template>
