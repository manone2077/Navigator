// 站点相关的通用工具函数

export const STATUS_UNTESTED = -1
export const STATUS_TIMEOUT = 88888
export const STATUS_UNREACHABLE = 99999

/** 根据测速结果返回展示信息 */
export function latencyInfo(status) {
  if (status == null || status < 0) return { level: 'none', text: '未测速', tip: '还没有测过速度' }
  if (status === STATUS_TIMEOUT) return { level: 'bad', text: '超时', tip: '连接超时（>5s）' }
  if (status === STATUS_UNREACHABLE) return { level: 'bad', text: '不可达', tip: '网络不可达或域名无法解析' }
  if (status < 300) return { level: 'good', text: `${status} ms`, tip: '速度非常好' }
  if (status < 1000) return { level: 'ok', text: `${status} ms`, tip: '速度一般' }
  return { level: 'slow', text: `${status} ms`, tip: '速度有点慢哟' }
}

export function parseUrl(url) {
  try {
    return new URL(url)
  } catch {
    return null
  }
}

export function hostOf(url) {
  return parseUrl(url)?.host || url
}

export function faviconOf(url) {
  const u = parseUrl(url)
  return u ? `${u.origin}/favicon.ico` : ''
}

/** 补全协议头：example.com -> https://example.com */
export function normalizeUrl(url) {
  const v = (url || '').trim()
  if (!v) return v
  return /^https?:\/\//i.test(v) ? v : `https://${v}`
}

const PALETTE = ['#5b8def', '#e86a92', '#f0a35e', '#43b88c', '#9a6ae8', '#3fb0c9', '#e0675a', '#7c8ba1']

/** 图标加载失败时使用的文字头像颜色，同一站点名称颜色固定 */
export function avatarColor(text) {
  let h = 0
  for (const ch of text || '') h = (h * 31 + ch.charCodeAt(0)) >>> 0
  return PALETTE[h % PALETTE.length]
}
