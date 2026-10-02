import { reactive } from 'vue'

interface LightboxState {
  src: string
  alt: string
}

const state = reactive<LightboxState>({ src: '', alt: '' })

/** 图片查看弹层：全局单例，任何图片点击后 open 即可 */
export function useLightbox() {
  return {
    state,
    open(src: string, alt = '') {
      state.src = src
      state.alt = alt
    },
    close() {
      state.src = ''
      state.alt = ''
    },
  }
}
