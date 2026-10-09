<script setup lang="ts">
import { computed, ref } from 'vue'
import AppIcon from './AppIcon.vue'
import { updatePassword } from '../api/auth'
import { ApiError } from '../api/http'

const emit = defineEmits<{ close: [] }>()

const oldPassword = ref('')
const newPassword = ref('')
const confirmPassword = ref('')
const submitting = ref(false)
const error = ref('')
const done = ref(false)

const canSubmit = computed(
  () =>
    oldPassword.value.length > 0 &&
    newPassword.value.length > 0 &&
    confirmPassword.value.length > 0 &&
    !submitting.value,
)

async function submit() {
  if (!canSubmit.value) return
  error.value = ''
  if (newPassword.value !== confirmPassword.value) {
    error.value = '两次输入的新密码不一致'
    return
  }
  submitting.value = true
  try {
    await updatePassword({ oldPassword: oldPassword.value, newPassword: newPassword.value })
    done.value = true
    window.setTimeout(() => emit('close'), 1200)
  } catch (e) {
    error.value = e instanceof ApiError ? e.message : '修改失败，请稍后重试'
  } finally {
    submitting.value = false
  }
}
</script>

<template>
  <Teleport to="body">
    <div class="dialog-mask" @click.self="emit('close')">
      <div class="dialog profile-dialog" role="dialog" aria-label="修改密码">
        <div class="dialog-head">
          <span class="dialog-title">修改密码</span>
          <button class="icon-btn" type="button" aria-label="关闭" @click="emit('close')">
            <AppIcon name="x" :size="16" />
          </button>
        </div>

        <div v-if="done" class="feedback-done">
          <AppIcon name="check" :size="22" />
          <p>修改成功</p>
        </div>

        <template v-else>
          <label class="login-field">
            <span class="login-label">原密码</span>
            <input
              v-model="oldPassword"
              class="login-input"
              type="password"
              autocomplete="current-password"
              placeholder="输入原密码"
            />
          </label>
          <label class="login-field">
            <span class="login-label">新密码</span>
            <input
              v-model="newPassword"
              class="login-input"
              type="password"
              autocomplete="new-password"
              placeholder="输入新密码"
            />
          </label>
          <label class="login-field">
            <span class="login-label">确认新密码</span>
            <input
              v-model="confirmPassword"
              class="login-input"
              type="password"
              autocomplete="new-password"
              placeholder="再次输入新密码"
            />
          </label>
          <p v-if="error" class="attach-error dialog-error">{{ error }}</p>
          <div class="dialog-foot">
            <button class="btn-ghost" type="button" @click="emit('close')">取消</button>
            <button class="btn-primary" type="button" :disabled="!canSubmit" @click="submit">
              {{ submitting ? '提交中…' : '确认修改' }}
            </button>
          </div>
        </template>
      </div>
    </div>
  </Teleport>
</template>
