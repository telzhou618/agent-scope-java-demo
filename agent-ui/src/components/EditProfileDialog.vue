<script setup lang="ts">
import { computed, ref } from 'vue'
import AppIcon from './AppIcon.vue'
import { updateProfile } from '../api/auth'
import { ApiError } from '../api/http'
import { useAuthStore } from '../stores/auth'

const emit = defineEmits<{ close: [] }>()

const auth = useAuthStore()

// 预填当前值
const nickname = ref(auth.user?.nickname ?? '')
const email = ref(auth.user?.email ?? '')
const avatar = ref(auth.user?.avatar ?? '')
const submitting = ref(false)
const error = ref('')
const done = ref(false)

/** 昵称与邮箱至少填一项才有东西可提交 */
const canSubmit = computed(
  () => (nickname.value.trim().length > 0 || email.value.trim().length > 0) && !submitting.value,
)

async function submit() {
  if (!canSubmit.value) return
  submitting.value = true
  error.value = ''
  try {
    const updated = await updateProfile({
      nickname: nickname.value.trim(),
      email: email.value.trim(),
      avatar: avatar.value.trim(),
    })
    auth.user = updated
    done.value = true
    window.setTimeout(() => emit('close'), 1200)
  } catch (e) {
    error.value = e instanceof ApiError ? e.message : '保存失败，请稍后重试'
  } finally {
    submitting.value = false
  }
}
</script>

<template>
  <Teleport to="body">
    <div class="dialog-mask" @click.self="emit('close')">
      <div class="dialog profile-dialog" role="dialog" aria-label="编辑资料">
        <div class="dialog-head">
          <span class="dialog-title">编辑资料</span>
          <button class="icon-btn" type="button" aria-label="关闭" @click="emit('close')">
            <AppIcon name="x" :size="16" />
          </button>
        </div>

        <div v-if="done" class="feedback-done">
          <AppIcon name="check" :size="22" />
          <p>资料已更新！</p>
        </div>

        <template v-else>
          <label class="login-field">
            <span class="login-label">昵称</span>
            <input v-model="nickname" class="login-input" maxlength="50" placeholder="输入昵称" />
          </label>
          <label class="login-field">
            <span class="login-label">邮箱</span>
            <input
              v-model="email"
              class="login-input"
              type="email"
              maxlength="100"
              placeholder="输入邮箱"
            />
          </label>
          <label class="login-field">
            <span class="login-label">头像 URL（选填）</span>
            <input v-model="avatar" class="login-input" maxlength="500" placeholder="https://…" />
          </label>
          <p v-if="error" class="attach-error dialog-error">{{ error }}</p>
          <div class="dialog-foot">
            <button class="btn-ghost" type="button" @click="emit('close')">取消</button>
            <button class="btn-primary" type="button" :disabled="!canSubmit" @click="submit">
              {{ submitting ? '保存中…' : '保存' }}
            </button>
          </div>
        </template>
      </div>
    </div>
  </Teleport>
</template>
