<script setup lang="ts">
import { computed } from 'vue'
import AppIcon from './AppIcon.vue'
import type { ThinkingBlockModel } from '../utils/model'

const props = defineProps<{ block: ThinkingBlockModel }>()

const label = computed(() => {
  if (props.block.seconds) return `思考了 ${props.block.seconds} 秒`
  return props.block.open ? '正在思考…' : '思考过程'
})

function toggle() {
  props.block.open = !props.block.open
}
</script>

<template>
  <div class="thinking" :data-collapsed="String(!props.block.open)">
    <button
      class="thinking-toggle"
      type="button"
      :aria-expanded="props.block.open"
      @click="toggle"
    >
      <AppIcon name="sparkle" :size="14" />
      <span>{{ label }}</span>
      <AppIcon name="chevron" :size="14" class="chevron" />
    </button>
    <div class="thinking-body">
      <div>
        <div class="thinking-inner">
          <p>{{ props.block.text }}</p>
        </div>
      </div>
    </div>
  </div>
</template>
