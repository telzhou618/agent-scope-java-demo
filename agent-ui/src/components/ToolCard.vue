<script setup lang="ts">
import { computed } from 'vue'
import AppIcon from './AppIcon.vue'
import { toolIconName, type IconName } from '../utils/icons'
import type { ToolBlockModel, ToolStatus } from '../utils/model'

const props = defineProps<{ block: ToolBlockModel }>()

const STATUS: Record<ToolStatus, { icon: IconName; label: string }> = {
  running: { icon: 'loader', label: '运行中' },
  ok: { icon: 'check', label: '成功' },
  error: { icon: 'x', label: '失败' },
  none: { icon: 'minus', label: '无结果' },
}

const status = computed(() => STATUS[props.block.status])
const icon = computed(() => toolIconName(props.block.name))

function toggle() {
  props.block.open = !props.block.open
}
</script>

<template>
  <div class="tool" :data-status="props.block.status" :data-collapsed="String(!props.block.open)">
    <button
      class="tool-head"
      type="button"
      :aria-expanded="props.block.open"
      @click="toggle"
    >
      <AppIcon name="chevron" :size="14" class="chevron" />
      <span class="tool-icon"><AppIcon :name="icon" :size="15" /></span>
      <span class="tool-name">{{ props.block.name }}</span>
      <span class="tool-args">{{ props.block.args }}</span>
      <span class="tool-status">
        <AppIcon
          :name="status.icon"
          :size="14"
          :class="{ spinner: props.block.status === 'running' }"
        />
        <span>{{ status.label }}</span>
      </span>
    </button>
    <div class="tool-body">
      <div>
        <pre class="tool-output">{{ props.block.output }}</pre>
      </div>
    </div>
  </div>
</template>
