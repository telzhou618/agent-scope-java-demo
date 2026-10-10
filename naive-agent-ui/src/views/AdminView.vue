<script setup lang="ts">
import { NCard, NTabs, NTabPane } from 'naive-ui'
import UsersPanel from '../components/admin/UsersPanel.vue'
import FeedbacksPanel from '../components/admin/FeedbacksPanel.vue'
import LogsPanel from '../components/admin/LogsPanel.vue'
</script>

<template>
  <div class="p-4 pb-12 md:p-6 md:pb-12">
    <NCard class="max-w-6xl mx-auto" title="管理中心" :content-style="{ paddingTop: '8px' }">
      <NTabs type="line" animated placement="top">
        <NTabPane name="users" tab="用户" display-directive="show:lazy">
          <UsersPanel />
        </NTabPane>
        <NTabPane name="feedbacks" tab="意见反馈" display-directive="show:lazy">
          <FeedbacksPanel />
        </NTabPane>
        <NTabPane name="logs" tab="操作日志" display-directive="show:lazy">
          <LogsPanel />
        </NTabPane>
      </NTabs>
    </NCard>
  </div>
</template>
