<script setup lang="ts">
import { reactive, ref } from 'vue'
import {
  NButton,
  NForm,
  NFormItem,
  NInput,
  NModal,
  useMessage,
  type FormInst,
  type FormItemRule,
  type FormRules,
} from 'naive-ui'
import { updatePassword } from '../api/auth'

const props = defineProps<{ show: boolean }>()
const emit = defineEmits<{ 'update:show': [value: boolean] }>()

const message = useMessage()

const formRef = ref<FormInst | null>(null)
const submitting = ref(false)

const form = reactive({
  oldPassword: '',
  newPassword: '',
  confirmPassword: '',
})

const rules: FormRules = {
  oldPassword: [{ required: true, message: '请输入原密码', trigger: 'blur' }],
  newPassword: [{ required: true, message: '请输入新密码', trigger: 'blur' }],
  confirmPassword: [
    { required: true, message: '请再次输入新密码', trigger: 'blur' },
    {
      validator: (_rule: FormItemRule, value: string) =>
        value === form.newPassword || new Error('两次输入的新密码不一致'),
      trigger: ['blur', 'password-input'],
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
    await updatePassword({ oldPassword: form.oldPassword, newPassword: form.newPassword })
    message.success('密码修改成功')
    emit('update:show', false)
  } catch (error) {
    message.error(error instanceof Error ? error.message : '修改失败，请稍后重试')
  } finally {
    submitting.value = false
  }
}
</script>

<template>
  <NModal
    :show="props.show"
    preset="card"
    title="修改密码"
    class="max-w-md w-[92vw]"
    @update:show="emit('update:show', $event)"
  >
    <NForm ref="formRef" :model="form" :rules="rules" label-placement="top">
      <NFormItem label="原密码" path="oldPassword">
        <NInput
          v-model:value="form.oldPassword"
          type="password"
          show-password-on="click"
          autocomplete="current-password"
          placeholder="输入原密码"
        />
      </NFormItem>
      <NFormItem label="新密码" path="newPassword">
        <NInput
          v-model:value="form.newPassword"
          type="password"
          show-password-on="click"
          autocomplete="new-password"
          placeholder="输入新密码"
        />
      </NFormItem>
      <NFormItem label="确认新密码" path="confirmPassword">
        <NInput
          v-model:value="form.confirmPassword"
          type="password"
          show-password-on="click"
          autocomplete="new-password"
          placeholder="再次输入新密码"
        />
      </NFormItem>
    </NForm>
    <template #footer>
      <div class="flex justify-end gap-2">
        <NButton @click="emit('update:show', false)">取消</NButton>
        <NButton type="primary" :loading="submitting" @click="submit">确认修改</NButton>
      </div>
    </template>
  </NModal>
</template>
