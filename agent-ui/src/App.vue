<script setup lang="ts">
import { onBeforeUnmount, onMounted, watch } from 'vue'
import AppSidebar from './components/AppSidebar.vue'
import AppTopBar from './components/AppTopBar.vue'
import ChatComposer from './components/ChatComposer.vue'
import ChatThread from './components/ChatThread.vue'
import ProfileView from './components/ProfileView.vue'
import { useChat } from './stores/chat'

const { state, loadSessions, newChat, closeDrawerOnNarrow } = useChat()

// 布局状态写在 body 上，样式表全权负责两种布局下的呈现
watch(
  () => state.sidebarOpen,
  (open) => {
    document.body.dataset.sidebar = open ? 'open' : 'collapsed'
  },
  { immediate: true },
)

watch(
  () => state.view,
  (view) => {
    document.body.dataset.view = view
  },
  { immediate: true },
)

function onKeydown(event: KeyboardEvent) {
  if (event.key === 'Escape') {
    closeDrawerOnNarrow()
    return
  }
  if ((event.ctrlKey || event.metaKey) && event.key.toLowerCase() === 'k') {
    event.preventDefault()
    void newChat()
  }
}

onMounted(() => {
  void loadSessions()
  document.addEventListener('keydown', onKeydown)
})

onBeforeUnmount(() => {
  document.removeEventListener('keydown', onKeydown)
})
</script>

<template>
  <div class="app">
    <AppSidebar />
    <main class="main">
      <AppTopBar />
      <ChatThread />
      <div class="profile">
        <ProfileView />
      </div>
      <ChatComposer />
    </main>
  </div>
  <div class="scrim" @click="closeDrawerOnNarrow()" />
</template>
