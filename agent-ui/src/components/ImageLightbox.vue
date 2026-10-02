<script setup lang="ts">
import { onBeforeUnmount, watch } from 'vue'
import AppIcon from './AppIcon.vue'
import { useLightbox } from '../composables/useLightbox'

const { state, close } = useLightbox()

function onKeydown(event: KeyboardEvent) {
  if (event.key === 'Escape') close()
}

// 弹层打开期间监听 ESC；挂全局是因为焦点不一定在弹层内
watch(
  () => state.src,
  (src) => {
    if (src) window.addEventListener('keydown', onKeydown)
    else window.removeEventListener('keydown', onKeydown)
  },
)

onBeforeUnmount(() => window.removeEventListener('keydown', onKeydown))
</script>

<template>
  <Teleport to="body">
    <div v-if="state.src" class="lightbox" @click="close">
      <img class="lightbox-img" :src="state.src" :alt="state.alt" @click.stop />
      <button class="lightbox-close" type="button" title="关闭（ESC）" @click="close">
        <AppIcon name="x" :size="18" />
      </button>
    </div>
  </Teleport>
</template>
