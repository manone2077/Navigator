import axios from 'axios'
import { ElMessage } from 'element-plus'

const http = axios.create({
  baseURL: '/api/',
  timeout: 120000,
  // 后端要求修改类请求带上该请求头，用于防止跨站请求伪造（其它网站无法添加自定义请求头）
  headers: { 'X-Requested-With': 'XMLHttpRequest' },
})

// 未登录（401）时通知页面显示登录界面
let onUnauthorized = () => {}
export const setUnauthorizedHandler = (fn) => (onUnauthorized = fn)

// 统一处理错误：优先显示后端返回的 msg；请求配置 silent: true 时不弹出提示
http.interceptors.response.use(
  (res) => {
    const body = res.data
    if (body && typeof body === 'object' && 'code' in body && body.code !== 200) {
      if (!res.config.silent) ElMessage.error(body.msg || '操作失败')
      return Promise.reject(Object.assign(new Error(body.msg), { handled: true, body }))
    }
    return body
  },
  (err) => {
    const status = err.response?.status
    const msg = err.response?.data?.msg
    if (status === 401) {
      onUnauthorized()
      if (!err.config?.silent) ElMessage.warning({ message: '请先登录', grouping: true })
    } else if (!err.config?.silent) {
      ElMessage.error(msg || (err.response ? `服务器错误（${status}）` : '网络异常，请检查服务是否正常运行'))
    }
    err.handled = true
    return Promise.reject(err)
  },
)

export default http
