import { ref } from 'vue'

const draft = ref('')
const focusToken = ref(0)

export function useComposer() {
  return {
    draft,
    focusToken,
    /** 由推荐卡片填入输入框并聚焦 */
    fill(text: string) {
      draft.value = text
      focusToken.value += 1
    },
    clear() {
      draft.value = ''
    },
  }
}
