<script setup lang="ts">
import AppIcon from './AppIcon.vue'
import { useComposer } from '../composables/useComposer'

const suggestions = [
  {
    title: '排查一个 bug',
    desc: '描述现象，Agent 读代码并复现',
    fill: '登录接口在 Safari 上偶发 401，Chrome 正常，帮我定位原因',
  },
  {
    title: '改造一段代码',
    desc: '统一写法、补类型、换风格',
    fill: '把 src/components/Avatar.tsx 从 inline style 改成 Tailwind 类名',
  },
  {
    title: '读懂一个模块',
    desc: '让 Agent 读完再讲给你听',
    fill: '读完 src/auth 目录，用几句话讲清楚登录态的流转',
  },
  {
    title: '补齐测试',
    desc: '针对现有实现生成用例',
    fill: '给 src/auth/session.ts 补齐单元测试',
  },
]

const composer = useComposer()
</script>

<template>
  <div class="empty">
    <span class="empty-mark" aria-hidden="true">
      <AppIcon name="plus" :size="20" />
    </span>
    <h2>开始一个新对话</h2>
    <p>描述你想做的事，Agent 会自己读代码、跑命令、验证结果。</p>
    <div class="suggestions">
      <button
        v-for="item in suggestions"
        :key="item.title"
        class="suggestion"
        type="button"
        @click="composer.fill(item.fill)"
      >
        <strong>{{ item.title }}</strong><span>{{ item.desc }}</span>
      </button>
    </div>
  </div>
</template>
