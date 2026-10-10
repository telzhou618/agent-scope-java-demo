<script setup lang="ts">
import { computed, h, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import {
  NAvatar,
  NButton,
  NDropdown,
  NIcon,
  NLayout,
  NLayoutContent,
  NLayoutHeader,
  NLayoutSider,
  NText,
  NTooltip,
  useMessage,
  type DropdownOption,
} from 'naive-ui'
import {
  ChatbubblesOutline,
  ChatboxEllipsesOutline,
  ContractOutline,
  ExpandOutline,
  KeypadOutline,
  LogOutOutline,
  MenuOutline,
  MoonOutline,
  PersonCircleOutline,
  SettingsOutline,
  SunnyOutline,
} from '@vicons/ionicons5'
import AgentSelect from '../components/AgentSelect.vue'
import FeedbackDialog from '../components/FeedbackDialog.vue'
import ImageLightbox from '../components/ImageLightbox.vue'
import SessionList from '../components/SessionList.vue'
import ShortcutsDialog from '../components/ShortcutsDialog.vue'
import { useComposer } from '../composables/useComposer'
import { useWideMode } from '../composables/useWideMode'
import { useAuthStore } from '../stores/auth'
import { useAgentsStore } from '../stores/agents'
import { useChatStore } from '../stores/chat'
import { useThemeStore } from '../stores/theme'

const route = useRoute()
const router = useRouter()
const message = useMessage()
const auth = useAuthStore()
const agents = useAgentsStore()
const chat = useChatStore()
const theme = useThemeStore()
const composer = useComposer()
const { wide, toggle: toggleWide } = useWideMode()

/* ---- 侧栏：宽屏可折叠，窄屏（≤900px）默认收起、展开时浮层覆盖内容 ---- */
const narrowMedia = window.matchMedia('(max-width: 900px)')
const isNarrow = ref(narrowMedia.matches)
const collapsed = ref(narrowMedia.matches)

function onMediaChange(event: MediaQueryListEvent) {
  isNarrow.value = event.matches
  collapsed.value = event.matches
}

// 窄屏下路由跳转（开会话/新对话）后自动收起浮层侧栏
watch(
  () => route.fullPath,
  () => {
    if (isNarrow.value) collapsed.value = true
  },
)

const feedbackOpen = ref(false)
const shortcutsOpen = ref(false)

onMounted(async () => {
  narrowMedia.addEventListener('change', onMediaChange)
  window.addEventListener('keydown', onGlobalKeydown)
  if (!auth.user) {
    try {
      await auth.fetchCurrent()
    } catch {
      return // 401 由 http 拦截器统一兜底
    }
  }
  void agents.loadAgents()
  void chat.loadSessions()
})

onBeforeUnmount(() => {
  narrowMedia.removeEventListener('change', onMediaChange)
  window.removeEventListener('keydown', onGlobalKeydown)
})

/* ---- 全局快捷键（说明见 ShortcutsDialog） ---- */
function onGlobalKeydown(event: KeyboardEvent) {
  const mod = event.ctrlKey || event.metaKey
  if (mod && event.key.toLowerCase() === 'k') {
    event.preventDefault()
    chat.newChat()
    router.push('/chat')
    return
  }
  if (mod && event.key.toLowerCase() === 'b') {
    event.preventDefault()
    collapsed.value = !collapsed.value
    return
  }
  if (mod && event.key.toLowerCase() === 'j') {
    event.preventDefault()
    if (route.name !== 'chat' && route.name !== 'chat-session') router.push('/chat')
    composer.focus()
    return
  }
  if (event.key === 'Escape' && chat.state.streaming) {
    void chat.stop()
  }
}

const accountOptions = computed<DropdownOption[]>(() => {
  const options: DropdownOption[] = [
    { label: '个人中心', key: 'profile', icon: () => h(NIcon, null, { default: () => h(PersonCircleOutline) }) },
  ]
  if (auth.user?.isAdmin) {
    options.push({ label: '管理中心', key: 'admin', icon: () => h(NIcon, null, { default: () => h(SettingsOutline) }) })
  }
  options.push(
    { label: '意见反馈', key: 'feedback', icon: () => h(NIcon, null, { default: () => h(ChatboxEllipsesOutline) }) },
    { label: '键盘快捷键', key: 'shortcuts', icon: () => h(NIcon, null, { default: () => h(KeypadOutline) }) },
    { type: 'divider', key: 'd1' },
    { label: '退出登录', key: 'logout', icon: () => h(NIcon, null, { default: () => h(LogOutOutline) }) },
  )
  return options
})

async function onAccountSelect(key: string) {
  if (key === 'profile') {
    router.push('/profile')
  } else if (key === 'admin') {
    router.push('/admin')
  } else if (key === 'feedback') {
    feedbackOpen.value = true
  } else if (key === 'shortcuts') {
    shortcutsOpen.value = true
  } else if (key === 'logout') {
    await auth.logout()
    chat.reset()
    message.success('已退出登录')
    router.replace('/login')
  }
}

const displayName = computed(() => auth.user?.nickname || auth.user?.username || '')
</script>

<template>
  <NLayout has-sider class="h-screen" position="absolute">
    <NLayoutSider
      v-model:collapsed="collapsed"
      collapse-mode="width"
      :width="272"
      :collapsed-width="0"
      :position="isNarrow ? 'absolute' : 'static'"
      :show-trigger="isNarrow ? false : 'bar'"
      :style="isNarrow ? { top: '0px', bottom: '0px', left: '0px' } : undefined"
      :content-style="{ height: '100%', overflow: 'hidden' }"
      bordered
      class="z-30"
    >
      <!-- 外层滚动锁死（永不出现外层滚动条）：品牌行固定，只有会话列表内部滚动 -->
      <div class="h-full flex flex-col">
        <div class="h-14 shrink-0 flex items-center gap-2 px-4">
          <NIcon size="22" color="#6366f1"><ChatbubblesOutline /></NIcon>
          <NText strong class="text-base">Agent Platform</NText>
        </div>
        <SessionList class="flex-1 min-h-0" />
      </div>
    </NLayoutSider>

    <!-- 窄屏浮层侧栏的遮罩 -->
    <div
      v-if="isNarrow && !collapsed"
      class="sider-mask"
      @click="collapsed = true"
    />

    <NLayout>
      <NLayoutHeader bordered class="h-14 flex items-center justify-between px-3 md:px-4">
        <div class="flex items-center gap-2 md:gap-3 min-w-0">
          <NButton v-if="isNarrow" quaternary circle @click="collapsed = !collapsed">
            <template #icon>
              <NIcon><MenuOutline /></NIcon>
            </template>
          </NButton>
          <AgentSelect />
          <NText
            v-if="route.name === 'chat' || route.name === 'chat-session'"
            depth="3"
            class="truncate text-sm hidden md:block"
          >
            {{ chat.title }}
          </NText>
        </div>
        <div class="flex items-center gap-1 md:gap-2">
          <NTooltip>
            <template #trigger>
              <NButton quaternary circle @click="toggleWide">
                <template #icon>
                  <NIcon>
                    <ContractOutline v-if="wide" />
                    <ExpandOutline v-else />
                  </NIcon>
                </template>
              </NButton>
            </template>
            {{ wide ? '切换为窄屏阅读' : '切换为宽屏' }}
          </NTooltip>
          <NButton quaternary circle @click="theme.toggle()">
            <template #icon>
              <NIcon>
                <SunnyOutline v-if="theme.isDark" />
                <MoonOutline v-else />
              </NIcon>
            </template>
          </NButton>
          <NDropdown trigger="click" :options="accountOptions" @select="onAccountSelect">
            <div class="flex items-center gap-2 cursor-pointer">
              <NAvatar round size="small" :src="auth.user?.avatar || undefined">
                {{ displayName.slice(0, 1) }}
              </NAvatar>
              <NText class="text-sm hidden sm:block">{{ displayName }}</NText>
            </div>
          </NDropdown>
        </div>
      </NLayoutHeader>

      <!--
        滚动模型：外层 scroll-container 负责 Profile/Admin 等流式页面滚动；
        ChatView 恰好撑满高度（h-full）不溢出，不产生外层滚动条，消息列表内部自滚
      -->
      <NLayoutContent
        style="height: calc(100vh - 3.5rem)"
        :content-style="{ height: '100%' }"
        content-class="hover-scroll"
      >
        <RouterView />
      </NLayoutContent>
    </NLayout>
    <ImageLightbox />
    <FeedbackDialog v-model:show="feedbackOpen" />
    <ShortcutsDialog v-model:show="shortcutsOpen" />
  </NLayout>
</template>

<style scoped>
.sider-mask {
  position: absolute;
  inset: 0;
  z-index: 20;
  background: rgba(15, 23, 42, 0.45);
}
</style>
