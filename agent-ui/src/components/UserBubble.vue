<script setup lang="ts">
import AppIcon from './AppIcon.vue'
import { formatSize } from '../utils/attachments'
import type { UserAttachment } from '../utils/model'

const props = defineProps<{ text: string; attachments?: UserAttachment[] }>()
</script>

<template>
  <div class="msg-user">
    <!-- pre-wrap 气泡：内部元素保持单行拼接，避免缩进空白被渲染出来 -->
    <div class="bubble"
      ><span v-if="props.attachments?.length" class="bubble-attach"
        ><template v-for="(a, index) in props.attachments" :key="index"
          ><img v-if="a.url" class="attach-thumb" :src="a.url" :alt="a.name" /><span v-else
            class="attach-file"
            ><AppIcon name="file" :size="14" /><span
              class="attach-name"
              >{{ a.name }}</span
            ><span v-if="a.size > 0" class="attach-size">{{ formatSize(a.size) }}</span></span
          ></template
        ></span
      ><span v-if="props.text">{{ props.text }}</span></div
    >
  </div>
</template>
