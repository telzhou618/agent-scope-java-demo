<script setup lang="ts">
import { NModal } from 'naive-ui'
import { useLightbox } from '../composables/useLightbox'

const { state, close } = useLightbox()
</script>

<template>
  <NModal
    :show="state.open"
    preset="card"
    :title="state.alt || '图片预览'"
    class="max-w-6xl w-[95vw]"
    :bordered="false"
    @update:show="close"
  >
    <div class="flex justify-center">
      <img :src="state.src" :alt="state.alt" class="max-h-[85vh] max-w-full rounded-2" />
    </div>
  </NModal>
</template>
