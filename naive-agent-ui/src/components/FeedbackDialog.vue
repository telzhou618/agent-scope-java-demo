<script setup lang="ts">
import { reactive, ref } from 'vue'
import {
  NButton,
  NForm,
  NFormItem,
  NInput,
  NModal,
  NRadioButton,
  NRadioGroup,
  useMessage,
  type FormInst,
  type FormRules,
} from 'naive-ui'
import { submitFeedback } from '../api/user'

const props = defineProps<{ show: boolean }>()
const emit = defineEmits<{ 'update:show': [value: boolean] }>()

const message = useMessage()

const formRef = ref<FormInst | null>(null)
const submitting = ref(false)

const form = reactive({
  type: 'idea',
  content: '',
  contact: '',
})

const rules: FormRules = {
  content: [{ required: true, message: '请填写反馈内容', trigger: 'blur' }],
}

async function submit() {
  try {
    await formRef.value?.validate()
  } catch {
    return
  }
  submitting.value = true
  try {
    await submitFeedback({
      type: form.type,
      content: form.content.trim(),
      contact: form.contact.trim() || undefined,
    })
    message.success('感谢反馈，我们会认真查看！')
    form.content = ''
    form.contact = ''
    emit('update:show', false)
  } catch (error) {
    message.error(error instanceof Error ? error.message : '提交失败，请稍后重试')
  } finally {
    submitting.value = false
  }
}
</script>

<template>
  <NModal
    :show="props.show"
    preset="card"
    title="意见反馈"
    class="max-w-md w-[92vw]"
    @update:show="emit('update:show', $event)"
  >
    <NForm ref="formRef" :model="form" :rules="rules" label-placement="top">
      <NFormItem label="类型" path="type">
        <NRadioGroup v-model:value="form.type">
          <NRadioButton value="bug">缺陷</NRadioButton>
          <NRadioButton value="idea">建议</NRadioButton>
          <NRadioButton value="other">其他</NRadioButton>
        </NRadioGroup>
      </NFormItem>
      <NFormItem label="内容" path="content">
        <NInput
          v-model:value="form.content"
          type="textarea"
          :autosize="{ minRows: 4, maxRows: 8 }"
          maxlength="500"
          show-count
          placeholder="描述你遇到的问题或建议…"
        />
      </NFormItem>
      <NFormItem label="联系方式（选填）" path="contact">
        <NInput v-model:value="form.contact" maxlength="100" placeholder="邮箱 / 手机" />
      </NFormItem>
    </NForm>
    <template #footer>
      <div class="flex justify-end gap-2">
        <NButton @click="emit('update:show', false)">取消</NButton>
        <NButton type="primary" :loading="submitting" @click="submit">提交</NButton>
      </div>
    </template>
  </NModal>
</template>
