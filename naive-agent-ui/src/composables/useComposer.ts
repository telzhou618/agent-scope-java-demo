import { ref } from 'vue'
import { uploadFile } from '../api/agent'
import { ApiError } from '../api/http'
import { useChatStore } from '../stores/chat'
import type { ChatAttachment } from '../api/types'
import { MAX_ATTACHMENTS, MAX_FILE_SIZE, attachmentKind, fileExt, isAllowedExt, type ComposerAttachment } from '../utils/attachments'

const draft = ref('')
const focusToken = ref(0)
/** 当前选中的技能名（/skill:<name>），发送时拼回消息前缀 */
const skill = ref<string | null>(null)
const attachments = ref<ComposerAttachment[]>([])
const uploading = ref(0)
const uploadError = ref('')

let keySeq = 0

function toMessage(error: unknown): string {
  if (error instanceof ApiError) return error.message
  if (error instanceof TypeError) return '无法连接 Agent 服务（8082）'
  return error instanceof Error ? error.message : String(error)
}

/** 单个附件上传：成功回填 id，失败则从草稿中移除并提示 */
function upload(item: ComposerAttachment, file: File) {
  uploading.value += 1
  // 后端按 <userId>/<sessionId>/ 归档；新会话还没发首条消息时用 store 预生成的 pending id
  uploadFile(file, useChatStore().ensureSessionId())
    .then((res) => {
      item.id = res.id
    })
    .catch((error) => {
      const index = attachments.value.indexOf(item)
      if (index >= 0) attachments.value.splice(index, 1)
      if (item.url?.startsWith('blob:')) URL.revokeObjectURL(item.url)
      uploadError.value = `${item.name} 上传失败：${toMessage(error)}`
    })
    .finally(() => {
      uploading.value -= 1
    })
}

export function useComposer() {
  return {
    draft,
    focusToken,
    skill,
    attachments,
    uploading,
    uploadError,
    /** 选中技能：输入框只保留参数文本，技能以标签形式展示 */
    setSkill(name: string) {
      skill.value = name
      draft.value = ''
    },
    clearSkill() {
      skill.value = null
    },
    /** 由推荐卡片填入输入框并聚焦 */
    fill(text: string) {
      draft.value = text
      focusToken.value += 1
    },
    /** 外部触发聚焦输入框（快捷键等），ChatComposer 监听 focusToken */
    focus() {
      focusToken.value += 1
    },
    clear() {
      draft.value = ''
    },
    attachFiles(list: FileList | File[]) {
      const files = Array.from(list)
      for (const file of files) {
        if (attachments.value.length >= MAX_ATTACHMENTS) {
          uploadError.value = `最多附加 ${MAX_ATTACHMENTS} 个文件`
          break
        }
        if (!isAllowedExt(file.name)) {
          uploadError.value = `"${file.name}" 格式不支持`
          continue
        }
        if (file.size > MAX_FILE_SIZE) {
          uploadError.value = `"${file.name}" 超过 10 MB 限制`
          continue
        }
        const kind = attachmentKind(fileExt(file.name))
        const item: ComposerAttachment = {
          key: `att-${keySeq++}`,
          name: file.name,
          ext: fileExt(file.name),
          size: file.size,
        }
        if (kind === 'image') item.url = URL.createObjectURL(file)
        attachments.value.push(item)
        upload(item, file)
      }
    },
    removeAttachment(key: string) {
      const index = attachments.value.findIndex((item) => item.key === key)
      if (index < 0) return
      const [item] = attachments.value.splice(index, 1)
      if (item.url?.startsWith('blob:')) URL.revokeObjectURL(item.url)
      if (!attachments.value.length) uploadError.value = ''
    },
    /** 发送成功后清空附件；blob 预览已随 userTurn 进会话，由 chat store 在丢弃 turns 时统一 revoke */
    resetAttachments() {
      attachments.value = []
      uploadError.value = ''
    },
    /** 全部附件都已上传完成，可以随消息一起发送 */
    allUploaded(): boolean {
      return attachments.value.every((item) => !!item.id)
    },
    /** 转成接口载荷：本地 key/blob 不出后端 */
    toPayload(): ChatAttachment[] {
      return attachments.value.map((item) => ({
        id: item.id ?? '',
        name: item.name,
        ext: item.ext,
        size: item.size,
      }))
    },
  }
}
