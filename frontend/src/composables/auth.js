import { computed, reactive } from 'vue'
import http from '@/service/http.js'

// 访问密码状态：未启用时 enabled=false，所有功能可用
export const auth = reactive({ checked: false, enabled: false, loggedIn: true, guestView: false, version: '' })

/** 是否可以修改数据 */
export const canEdit = computed(() => !auth.enabled || auth.loggedIn)

export async function refreshAuth() {
  try {
    const res = await http.get('auth/status', { silent: true })
    Object.assign(auth, res.data, { checked: true })
  } catch {
    // 旧版本后端没有该接口，按未启用处理
    Object.assign(auth, { checked: true, enabled: false, loggedIn: true })
  }
}

export async function login(password, remember) {
  const res = await http.post('auth/login', { password, remember })
  auth.loggedIn = true
  return res
}

export async function logout() {
  await http.post('auth/logout')
  auth.loggedIn = false
}
