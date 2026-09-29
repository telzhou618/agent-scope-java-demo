<script setup lang="ts">
import { onBeforeUnmount, onMounted, watch } from 'vue'
import AppSidebar from './components/AppSidebar.vue'
import AppTopBar from './components/AppTopBar.vue'
import ChatComposer from './components/ChatComposer.vue'
import ChatThread from './components/ChatThread.vue'
import ProfileView from './components/ProfileView.vue'
import { useChat } from './stores/chat'

const { state, loadSessions, newChat, toggleDrawer } = useChat()

document.body.dataset.view = state.view
document.body.dataset.drawer = state.drawerOpen ? 'open' : 'closed'

watch(
  () => state.view,
  (view) => {
    document.body.dataset.view = view
  },
)

watch(
  () => state.drawerOpen,
  (open) => {
    document.body.dataset.drawer = open ? 'open' : 'closed'
  },
)

function onKeydown(event: KeyboardEvent) {
  if (event.key === 'Escape') {
    toggleDrawer(false)
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
  <div class="scrim" @click="toggleDrawer(false)" />
</template>
