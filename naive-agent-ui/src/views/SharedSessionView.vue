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
      <div class="share-header">
        <span class="header-mark">
          <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round" width="18" height="18">
            <path d="M22 2 11 13" />
            <path d="M22 2 15 22l-4-9-9-4z" />
          </svg>
        </span>
        <div>
          <div class="header-title">会话分享</div>
          <div class="header-sub">由 Agent 生成的对话分享，链接 7 天内有效</div>
        </div>
      </div>

      <div v-if="loading" class="share-body">
        <div class="flex justify-center py-16">
          <NSpin />
        </div>
      </div>

      <div v-else-if="error" class="share-body share-error">{{ error }}</div>

      <NEmpty v-else-if="turns.length === 0" description="会话暂无内容" class="share-body py-16" />

      <div v-else class="share-body">
        <div class="turn-list">
          <template v-for="turn in turns" :key="turn.id">
            <!-- 用户消息：附件已在后端剥离内容，只读展示 -->
            <UserBubble v-if="turn.kind === 'user'" :text="turn.text" />
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
        <div class="share-foot">
          <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.6" stroke-linecap="round" stroke-linejoin="round" width="13" height="13">
            <rect x="3" y="11" width="18" height="10" rx="2" />
            <path d="M7 11V7a5 5 0 0 1 10 0v4" />
          </svg>
          本分享页仅供阅读，附件与文件下载不可用
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped>
.share-page {
  min-height: 100vh;
  padding: 40px 16px 60px;
  background:
    radial-gradient(40% 30% at 15% 0%, var(--brand-soft, rgba(99, 102, 241, 0.08)), transparent 70%),
    radial-gradient(30% 26% at 90% 10%, var(--brand-soft, rgba(99, 102, 241, 0.08)), transparent 70%),
    var(--bg, #f6f7fb);
}

.share-inner {
  max-width: 1200px;
  margin: 0 auto;
}

/* 顶部标题栏 */
.share-header {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 18px;
}

.header-mark {
  flex: none;
  width: 40px;
  height: 40px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  border-radius: 12px;
  background: linear-gradient(135deg, var(--brand-solid, #6366f1), var(--brand-solid-alt, #a855f7));
  color: #fff;
  box-shadow: 0 6px 18px var(--brand-ring, rgba(99, 102, 241, 0.14));
}

.header-title {
  font-size: 18px;
  font-weight: 700;
}

.header-sub {
  font-size: 12.5px;
  opacity: 0.55;
  margin-top: 2px;
}

/* 内容容器 */
.share-body {
  background: var(--surface-elevated, #fff);
  border: 1px solid var(--divider, rgba(100, 116, 139, 0.14));
  border-radius: 16px;
  box-shadow: var(--shadow-float, 0 8px 30px rgba(15, 23, 42, 0.12));
  padding: 22px 26px 18px;
}

.turn-list {
  display: flex;
  flex-direction: column;
  gap: 22px;
}

/* 助手回合：内部卡片化，与外壳卡片有所区分 */
.asst-turn {
  border: 1px solid var(--divider, rgba(100, 116, 139, 0.14));
  border-radius: 12px;
  background: var(--surface, #fff);
  padding: 14px 18px;
}

.share-foot {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 6px;
  margin-top: 22px;
  padding-top: 16px;
  border-top: 1px dashed var(--divider-strong, rgba(100, 116, 139, 0.22));
  font-size: 12px;
  opacity: 0.5;
}

.share-error {
  padding: 60px 0;
  text-align: center;
  font-size: 14px;
  opacity: 0.6;
}

@media (prefers-reduced-motion: reduce) {
  .header-mark {
    box-shadow: none;
  }
}
</style>
