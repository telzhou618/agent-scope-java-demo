<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useRoute } from 'vue-router'
import { NEmpty, NSpin } from 'naive-ui'
import { getSharedSession } from '../api/agent'
import { toTurns } from '../utils/history'
import type { Turn } from '../utils/model'
import UserBubble from '../components/UserBubble.vue'
import ThinkingBlock from '../components/ThinkingBlock.vue'
import ToolCallCard from '../components/ToolCallCard.vue'
import MarkdownBlock from '../components/MarkdownBlock.vue'

const route = useRoute()

const turns = ref<Turn[]>([])
const loading = ref(true)
const error = ref('')

onMounted(async () => {
  const token = String(route.query.t ?? '')
  if (!token) {
    error.value = '分享链接无效'
    loading.value = false
    return
  }
  try {
    const messages = await getSharedSession(token)
    turns.value = toTurns(messages ?? [])
  } catch (e) {
    error.value = e instanceof Error ? e.message : '分享链接无效或已失效'
  } finally {
    loading.value = false
  }
})
</script>

<template>
  <div class="share-page">
    <div class="share-inner">
      <div v-if="loading" class="flex justify-center py-16">
        <NSpin />
      </div>

      <div v-else-if="error" class="share-error">{{ error }}</div>

      <NEmpty v-else-if="turns.length === 0" description="会话暂无内容" class="py-16" />

      <template v-else>
        <div class="share-title">会话分享</div>
        <div class="turn-list">
          <template v-for="turn in turns" :key="turn.id">
            <!-- 用户消息：附件已在后端剥离内容，只读展示 -->
            <UserBubble v-if="turn.kind === 'user'" :text="turn.text" :skill="turn.skill" />
            <!-- 助手回合：只读渲染，无操作按钮；正文禁链接（禁下载） -->
            <div v-else class="asst-turn">
              <template v-for="(block, index) in turn.blocks" :key="index">
                <ThinkingBlock v-if="block.kind === 'thinking'" :block="block" />
                <ToolCallCard v-else-if="block.kind === 'tool'" :block="block" />
                <MarkdownBlock v-else-if="block.kind === 'text'" :markdown="block.markdown" :no-links="true" />
              </template>
            </div>
          </template>
        </div>
        <div class="share-foot">由 AgentScope 会话分享生成 · 附件与文件下载在分享页不可用</div>
      </template>
    </div>
  </div>
</template>

<style scoped>
.share-page {
  min-height: 100vh;
  background: var(--bg, #f6f7fb);
  padding: 32px 16px 60px;
}

.share-inner {
  max-width: 1200px;
  margin: 0 auto;
}

.share-title {
  font-size: 17px;
  font-weight: 700;
  margin-bottom: 16px;
}

.turn-list {
  display: flex;
  flex-direction: column;
  gap: 20px;
}

.asst-turn {
  border: 1px solid var(--divider, rgba(100, 116, 139, 0.14));
  border-radius: 12px;
  background: var(--surface-elevated, #fff);
  padding: 14px 18px;
}

.share-foot {
  margin-top: 20px;
  font-size: 12px;
  opacity: 0.5;
  text-align: center;
}

.share-error {
  padding: 60px 0;
  text-align: center;
  font-size: 14px;
  opacity: 0.6;
}
</style>
