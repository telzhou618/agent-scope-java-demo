<script setup lang="ts">
import { onBeforeUnmount, onMounted, ref } from 'vue'
import AppIcon from './AppIcon.vue'
import { formatSize } from '../utils/attachments'
import { useLightbox } from '../composables/useLightbox'
import type { UserAttachment } from '../utils/model'

const props = defineProps<{ text: string; attachments?: UserAttachment[] }>()

const { open: openImage } = useLightbox()

/** 折叠态最大高度（约 6 行），明显超出才显示展开/收起按钮 */
const COLLAPSED_HEIGHT = 164

const bubbleRef = ref<HTMLElement>()
const collapsible = ref(false)
const expanded = ref(false)

let observer: ResizeObserver | null = null

onMounted(() => {
  const el = bubbleRef.value
  if (!el) return
  const check = () => {
    // 留一行缓冲：只超出一点点时不折叠，避免最后只藏起一行
    collapsible.value = el.scrollHeight > COLLAPSED_HEIGHT + 24
  }
  check()
  // 附件图片异步加载会改变高度，用 ResizeObserver 持续校正
  observer = new ResizeObserver(check)
  observer.observe(el)
})

onBeforeUnmount(() => observer?.disconnect())
</script>

<template>
  <div class="msg-user">
    <!-- pre-wrap 气泡：内部元素保持单行拼接，避免缩进空白被渲染出来 -->
    <div ref="bubbleRef" class="bubble" :class="{ collapsed: collapsible && !expanded }"
      ><span v-if="props.attachments?.length" class="bubble-attach"
        ><template v-for="(a, index) in props.attachments" :key="index"
          ><img v-if="a.url" class="attach-thumb" :src="a.url" :alt="a.name" @click="openImage(a.url, a.name)" /><span v-else
            class="attach-file"
            ><AppIcon name="file" :size="14" /><span
              class="attach-name"
              >{{ a.name }}</span
            ><span v-if="a.size > 0" class="attach-size">{{ formatSize(a.size) }}</span></span
          ></template
        ></span
      ><span v-if="props.text">{{ props.text }}</span
      ><button
        v-if="collapsible"
        type="button"
        class="bubble-toggle"
        :class="{ expanded }"
        :title="expanded ? '收起' : '展开全部'"
        @click="expanded = !expanded"
        ><AppIcon name="chevron" :size="14" class="chevron" /></button
    ></div>
  </div>
</template>
