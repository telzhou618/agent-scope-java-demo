<script setup lang="ts">
import { iconPaths, type IconName } from '../utils/icons'

const props = withDefaults(
  defineProps<{ name: IconName; size?: number; filled?: boolean }>(),
  { size: 16, filled: false },
)
</script>

<template>
  <svg
    :width="props.size"
    :height="props.size"
    viewBox="0 0 24 24"
    :fill="props.filled ? 'currentColor' : 'none'"
    stroke="currentColor"
    stroke-width="1.5"
    stroke-linecap="round"
    stroke-linejoin="round"
    aria-hidden="true"
    v-html="iconPaths[props.name]"
  />
</template>
