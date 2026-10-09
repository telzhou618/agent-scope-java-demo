<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import { NIcon, NTag } from 'naive-ui'
import {
  ChevronDownOutline,
  DocumentOutline,
  FlashOutline,
} from '@vicons/ionicons5'
import { formatSize } from '../utils/attachments'
import { useLightbox } from '../composables/useLightbox'
import type { UserAttachment } from '../utils/model'

const props = defineProps<{ text: string; attachments?: UserAttachment[] }>()

const { open: openImage } = useLightbox()

/** 技能前缀（/skill:<name>）解析成标签，与正文区分展示 */
const skillName = computed(() => /^\/skill:([\w-]+)/.exec(props.text)?.[1] ?? null)
const restText = computed(() =>
  skillName.value ? props.text.replace(/^\/skill:[\w-]+\s*/, '') : props.text,
)

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
  <div class="flex justify-end">
    <div ref="bubbleRef" class="user-bubble" :class="{ collapsed: collapsible && !expanded }">
      <div v-if="props.attachments?.length" class="mb-1.5 flex flex-wrap gap-1.5">
        <template v-for="(attachment, index) in props.attachments" :key="index">
          <img
            v-if="attachment.url"
            class="attach-thumb"
            :src="attachment.url"
            :alt="attachment.name"
            @click="openImage(attachment.url!, attachment.name)"
          />
          <span v-else class="attach-chip">
            <NIcon size="12"><DocumentOutline /></NIcon>
            <span class="max-w-40 truncate">{{ attachment.name }}</span>
            <span v-if="attachment.size > 0" class="op-70">{{ formatSize(attachment.size) }}</span>
          </span>
        </template>
      </div>
      <div v-if="skillName" class="mb-1">
        <NTag size="small" :bordered="false" class="skill-tag">
          <template #icon>
            <NIcon size="12"><FlashOutline /></NIcon>
          </template>
          /skill:{{ skillName }}
        </NTag>
      </div>
      <span v-if="restText" class="whitespace-pre-wrap break-words">{{ restText }}</span>
      <button
        v-if="collapsible"
        type="button"
        class="bubble-toggle"
        :title="expanded ? '收起' : '展开全部'"
        @click="expanded = !expanded"
      >
        <NIcon size="13" class="toggle-chevron" :class="{ expanded }">
          <ChevronDownOutline />
        </NIcon>
      </button>
    </div>
  </div>
</template>

<style scoped>
.user-bubble {
  position: relative;
  max-width: 75%;
  padding: 10px 14px;
  border-radius: 14px 14px 4px 14px;
  background: #6366f1;
  color: #fff;
  font-size: 14px;
  line-height: 1.6;
}

.user-bubble.collapsed {
  max-height: 164px;
  overflow: hidden;
}

.attach-thumb {
  max-width: 180px;
  max-height: 120px;
  border-radius: 8px;
  cursor: zoom-in;
}

.attach-chip {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  padding: 2px 8px;
  border-radius: 6px;
  background: rgba(255, 255, 255, 0.2);
  font-size: 12px;
}

.skill-tag {
  background: rgba(255, 255, 255, 0.2);
  color: #fff;
}

.bubble-toggle {
  position: absolute;
  right: 8px;
  bottom: 6px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 22px;
  height: 22px;
  border: 0;
  border-radius: 50%;
  background: rgba(255, 255, 255, 0.25);
  color: #fff;
  cursor: pointer;
}

.toggle-chevron {
  transition: transform 0.2s;
}

.toggle-chevron.expanded {
  transform: rotate(180deg);
}
</style>
