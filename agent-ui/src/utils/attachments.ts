export interface ComposerAttachment {
  id?: string
  /** 前端本地标识，用于删除单个附件（与后端分配的 id 无关） */
  key: string
  name: string
  ext: string
  size: number
  url?: string
}

export const MAX_ATTACHMENTS = 5
export const MAX_FILE_SIZE = 10 * 1024 * 1024

const IMAGE_EXTS = ['png', 'jpg', 'jpeg', 'gif', 'webp', 'bmp']
const TEXT_EXTS = [
  'txt', 'md', 'csv', 'json', 'log', 'xml', 'yaml', 'yml', 'html', 'sql',
  'java', 'py', 'js', 'ts', 'vue', 'css', 'c', 'cpp', 'h', 'go', 'rs',
  'sh', 'properties', 'ini', 'toml',
]
const DOC_EXTS = ['pdf', 'doc', 'docx', 'xls', 'xlsx']

const ALLOWED_EXTS = new Set([...IMAGE_EXTS, ...TEXT_EXTS, ...DOC_EXTS])

export const ATTACHMENT_ACCEPT = `image/*,${[...TEXT_EXTS, ...DOC_EXTS].map((ext) => `.${ext}`).join(',')}`

export type AttachmentKind = 'image' | 'pdf' | 'doc' | 'xls' | 'other'

export function fileExt(name: string): string {
  const dot = name.lastIndexOf('.')
  return dot >= 0 ? name.slice(dot + 1).toLowerCase() : ''
}

export function isAllowedExt(name: string): boolean {
  return ALLOWED_EXTS.has(fileExt(name))
}

export function attachmentKind(ext: string): AttachmentKind {
  const value = ext.toLowerCase()
  if (IMAGE_EXTS.includes(value)) return 'image'
  if (value === 'pdf') return 'pdf'
  if (value === 'doc' || value === 'docx') return 'doc'
  if (value === 'xls' || value === 'xlsx') return 'xls'
  return 'other'
}

export function formatSize(size: number): string {
  if (size < 1024) return `${size} B`
  if (size < 1024 * 1024) return `${(size / 1024).toFixed(1)} KB`
  return `${(size / (1024 * 1024)).toFixed(1)} MB`
}
