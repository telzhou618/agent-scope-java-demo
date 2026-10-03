<script setup lang="ts">
import { computed, ref } from 'vue'
import AppIcon from './AppIcon.vue'
import { submitFeedback } from '../api/user'
import { ApiError } from '../api/http'

const emit = defineEmits<{ close: [] }>()

const TYPES = [
  { value: 'bug', label: '问题反馈' },
  { value: 'idea', label: '功能建议' },
  { value: 'other', label: '其他' },
]

const type = ref('idea')
const content = ref('')
const contact = ref('')
const submitting = ref(false)
const error = ref('')
const done = ref(false)

const canSubmit = computed(() => content.value.trim().length > 0 && !submitting.value)

async function submit() {
  if (!canSubmit.value) return
  submitting.value = true
  error.value = ''
  try {
    await submitFeedback({ type: type.value, content: content.value.trim(), contact: contact.value.trim() })
    done.value = true
    window.setTimeout(() => emit('close'), 1200)
  } catch (e) {
    error.value = e instanceof ApiError ? e.message : '提交失败，请稍后重试'
  } finally {
    submitting.value = false
  }
}
</script>

<template>
  <Teleport to="body">
    <div class="dialog-mask" @click.self="emit('close')">
      <div class="dialog feedback-dialog" role="dialog" aria-label="意见反馈">
        <div class="dialog-head">
          <span class="dialog-title">意见反馈</span>
          <button class="icon-btn" type="button" aria-label="关闭" @click="emit('close')">
            <AppIcon name="x" :size="16" />
          </button>
        </div>

        <div v-if="done" class="feedback-done">
          <AppIcon name="check" :size="22" />
          <p>感谢反馈，我们会认真查看！</p>
        </div>

        <template v-else>
          <div class="feedback-types">
            <label
              v-for="item in TYPES"
              :key="item.value"
              class="feedback-type"
              :class="{ active: type === item.value }"
            >
              <input v-model="type" type="radio" name="feedback-type" :value="item.value" class="sr-only" />
              {{ item.label }}
            </label>
          </div>
          <textarea
            v-model="content"
            class="feedback-content"
            rows="5"
            maxlength="500"
            placeholder="描述你遇到的问题或建议…"
          />
          <div class="feedback-meta">
            <input
              v-model="contact"
              class="feedback-contact"
              maxlength="100"
              placeholder="联系方式（选填，邮箱/手机）"
            />
            <span class="feedback-count">{{ content.length }}/500</span>
          </div>
          <p v-if="error" class="attach-error">{{ error }}</p>
          <div class="dialog-foot">
            <button class="btn-ghost" type="button" @click="emit('close')">取消</button>
            <button class="btn-primary" type="button" :disabled="!canSubmit" @click="submit">
              {{ submitting ? '提交中…' : '提交' }}
            </button>
          </div>
        </template>
      </div>
    </div>
  </Teleport>
</template>
