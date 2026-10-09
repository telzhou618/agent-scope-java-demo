import { reactive } from 'vue'

interface LightboxState {
  open: boolean
  src: string
  alt: string
}

const state = reactive<LightboxState>({ open: false, src: '', alt: '' })

/** 图片放大查看：MarkdownBlock / UserBubble / ChartBlock 共用，宿主组件挂在 AppLayout */
export function useLightbox() {
  function open(src: string, alt = '') {
    state.src = src
    state.alt = alt
    state.open = true
  }

  function close() {
    state.open = false
  }

  return { state, open, close }
}
