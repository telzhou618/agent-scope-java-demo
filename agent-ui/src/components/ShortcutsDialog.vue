<script setup lang="ts">
import AppIcon from './AppIcon.vue'

const emit = defineEmits<{ close: [] }>()

/** 快捷键说明：与 ChatView 全局监听、ChatComposer 输入框按键保持一致 */
const SHORTCUTS: { keys: string[]; label: string }[] = [
  { keys: ['Ctrl', 'K'], label: '新对话' },
  { keys: ['Ctrl', 'B'], label: '切换侧栏' },
  { keys: ['Ctrl', 'J'], label: '聚焦输入框' },
  { keys: ['Esc'], label: '停止生成 / 清空搜索 / 收起抽屉' },
  { keys: ['Enter'], label: '发送消息' },
  { keys: ['Shift', 'Enter'], label: '换行' },
]
</script>

<template>
  <Teleport to="body">
    <div class="dialog-mask" @click.self="emit('close')">
      <div class="dialog shortcuts-dialog" role="dialog" aria-label="键盘快捷键">
        <div class="dialog-head">
          <span class="dialog-title">键盘快捷键</span>
          <button class="icon-btn" type="button" aria-label="关闭" @click="emit('close')">
            <AppIcon name="x" :size="16" />
          </button>
        </div>

        <ul class="shortcut-list">
          <li v-for="item in SHORTCUTS" :key="item.label" class="shortcut-row">
            <span class="shortcut-label">{{ item.label }}</span>
            <span class="shortcut-keys">
              <template v-for="(key, index) in item.keys" :key="index">
                <kbd class="shortcut-kbd">{{ key }}</kbd>
                <span v-if="index < item.keys.length - 1" class="shortcut-plus">+</span>
              </template>
            </span>
          </li>
        </ul>
        <p class="shortcut-tip">macOS 上 Ctrl 对应 ⌘（Command）</p>
      </div>
    </div>
  </Teleport>
</template>
