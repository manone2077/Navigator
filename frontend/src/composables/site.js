// 站点相关的通用工具函数

export const STATUS_UNTESTED = -1
export const STATUS_TIMEOUT = 88888
export const STATUS_UNREACHABLE = 99999

/** 根据测速结果返回展示信息 */
export function latencyInfo(status, msg) {
  if (status == null || status < 0) return { level: 'none', text: '未测速', tip: '还没有测过速度' }
  if (status === STATUS_TIMEOUT) return { level: 'bad', text: '超时', tip: msg || '连接超时（>5s）' }
  if (status === STATUS_UNREACHABLE) return { level: 'bad', text: '不可达', tip: msg || '网络不可达或域名无法解析' }
  if (status < 300) return { level: 'good', text: `${status} ms`, tip: '速度非常好' }
  if (status < 1000) return { level: 'ok', text: `${status} ms`, tip: '速度一般' }
  return { level: 'slow', text: `${status} ms`, tip: '速度有点慢哟' }
}

/** 相对时间：刚刚 / 3 分钟前 / 2 小时前 / 具体日期 */
export function timeAgo(value) {
  if (!value) return ''
  const t = new Date(value).getTime()
  if (Number.isNaN(t)) return ''
  const sec = Math.max(0, (Date.now() - t) / 1000)
  if (sec < 60) return '刚刚'
  if (sec < 3600) return `${Math.floor(sec / 60)} 分钟前`
  if (sec < 86400) return `${Math.floor(sec / 3600)} 小时前`
  return new Date(t).toLocaleString()
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

/**
 * 补全协议头：内网地址（IP、localhost、.local/.lan 等）默认 http，其它默认 https
 * 192.168.1.2:8080 -> http://192.168.1.2:8080，192.168.1.2:9443 -> https://…，example.com -> https://example.com
 */
export function normalizeUrl(url) {
  const v = (url || '').trim()
  if (!v || /^https?:\/\//i.test(v)) return v
  const host = v.split(/[/:?#]/)[0].toLowerCase()
  const port = (v.match(/^[^/?#]*:(\d+)/) || [])[1]
  // 常见的 https 端口：443、8443、9443（Portainer）、8006（PVE）
  if (['443', '8443', '9443', '8006'].includes(port)) return `https://${v}`
  const isLan = /^\d{1,3}(\.\d{1,3}){3}$/.test(host) || host === 'localhost' || /\.(local|lan|home|internal)$/.test(host) || !host.includes('.')
  return `${isLan ? 'http' : 'https'}://${v}`
}

const PALETTE = ['#5b8def', '#e86a92', '#f0a35e', '#43b88c', '#9a6ae8', '#3fb0c9', '#e0675a', '#7c8ba1']

/** 图标加载失败时使用的文字头像颜色，同一站点名称颜色固定 */
export function avatarColor(text) {
  let h = 0
  for (const ch of text || '') h = (h * 31 + ch.charCodeAt(0)) >>> 0
  return PALETTE[h % PALETTE.length]
}
