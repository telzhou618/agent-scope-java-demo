<script setup lang="ts">
import { computed, onBeforeUnmount, ref, watch } from 'vue'
import { renderMarkdown } from '../composables/useMarkdown'
import { copyText, flashLabel } from '../utils/clipboard'

const props = defineProps<{ markdown: string }>()

const html = ref('')
let timer: number | null = null
let seeded = false

// 流式增量很密，节流到 50ms 再跑一次 Markdown 解析
watch(
  () => props.markdown,
  (value) => {
    if (!seeded) {
      seeded = true
      html.value = renderMarkdown(value)
      return
    }
    if (timer !== null) return
    timer = window.setTimeout(() => {
      timer = null
      html.value = renderMarkdown(props.markdown)
    }, 50)
  },
  { immediate: true },
)

onBeforeUnmount(() => {
  if (timer !== null) window.clearTimeout(timer)
})

function onClick(event: MouseEvent) {
  const target = event.target as HTMLElement | null
  const button = target?.closest<HTMLElement>('[data-copy]')
  if (!button) return
  const code = button.closest('.code')?.querySelector('code')
  if (!code) return
  void copyText(code.textContent ?? '')
  flashLabel(button)
}

const content = computed(() => html.value)
</script>

<template>
  <div class="prose" @click="onClick" v-html="content" />
</template>
