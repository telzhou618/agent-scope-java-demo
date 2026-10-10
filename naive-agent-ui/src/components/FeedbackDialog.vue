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
        <NRadioGroup v-model:value="form.type" class="feedback-type-group">
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

<style scoped>
/* 类型选择：拆成等宽独立圆角片,选中态用品牌色描边+浅底 */
.feedback-type-group {
  display: flex;
  gap: 8px;
  width: 100%;
}

.feedback-type-group :deep(.n-radio-button) {
  flex: 1;
  border: 1px solid rgba(128, 128, 128, 0.28);
  border-radius: 8px;
  box-shadow: none !important;
  text-align: center;
  transition: border-color 0.2s, color 0.2s, background-color 0.2s;
}

.feedback-type-group :deep(.n-radio-button:hover) {
  border-color: #6366f1;
  color: #6366f1;
}

.feedback-type-group :deep(.n-radio-button.n-radio-button--checked) {
  border-color: #6366f1;
  color: #6366f1;
  background: rgba(99, 102, 241, 0.08);
  font-weight: 500;
}

.feedback-type-group :deep(.n-radio-button .n-radio-button__state-border) {
  display: none;
}

/* 隐藏 naive 按钮组自带的竖向分隔线,只留 gap 空隙 */
.feedback-type-group :deep(.n-radio-group__splitor) {
  display: none;
}
</style>
