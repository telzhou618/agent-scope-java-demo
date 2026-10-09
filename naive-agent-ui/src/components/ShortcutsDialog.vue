<script setup lang="ts">
import { NModal } from 'naive-ui'

const props = defineProps<{ show: boolean }>()
const emit = defineEmits<{ 'update:show': [value: boolean] }>()

/** 快捷键说明：与 AppLayout 全局监听、ChatComposer 输入框按键保持一致 */
const SHORTCUTS: { keys: string[]; label: string }[] = [
  { keys: ['Ctrl', 'K'], label: '新对话' },
  { keys: ['Ctrl', 'B'], label: '切换侧栏' },
  { keys: ['Ctrl', 'J'], label: '聚焦输入框' },
  { keys: ['Esc'], label: '停止生成' },
  { keys: ['Enter'], label: '发送消息' },
  { keys: ['Shift', 'Enter'], label: '换行' },
  { keys: ['/'], label: '唤起技能选择器' },
]
</script>

<template>
  <NModal
    :show="props.show"
    preset="card"
    title="键盘快捷键"
    class="max-w-sm w-[92vw]"
    @update:show="emit('update:show', $event)"
  >
    <ul class="m-0 p-0 list-none flex flex-col gap-2.5">
      <li v-for="item in SHORTCUTS" :key="item.label" class="flex items-center justify-between">
        <span class="text-sm">{{ item.label }}</span>
        <span class="flex items-center gap-1">
          <template v-for="(key, index) in item.keys" :key="index">
            <kbd class="shortcut-kbd">{{ key }}</kbd>
            <span v-if="index < item.keys.length - 1" class="op-45 text-xs">+</span>
          </template>
        </span>
      </li>
    </ul>
    <p class="mt-4 mb-0 text-xs op-50">macOS 上 Ctrl 对应 ⌘（Command）</p>
  </NModal>
</template>

<style scoped>
.shortcut-kbd {
  padding: 2px 7px;
  border: 1px solid rgba(100, 116, 139, 0.4);
  border-bottom-width: 2px;
  border-radius: 6px;
  background: rgba(100, 116, 139, 0.08);
  font-family: ui-monospace, SFMono-Regular, Menlo, Consolas, monospace;
  font-size: 12px;
}
</style>
