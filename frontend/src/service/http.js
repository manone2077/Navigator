import axios from 'axios'
import { ElMessage } from 'element-plus'

const http = axios.create({
  baseURL: '/api/',
  timeout: 120000,
})

// 未登录（401）时通知页面显示登录界面
let onUnauthorized = () => {}
export const setUnauthorizedHandler = (fn) => (onUnauthorized = fn)

// 统一处理业务错误与网络错误：业务 code != 200 时提示后端返回的 msg 并 reject
// 请求配置 silent: true 时不弹出错误提示
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
    if (err.response?.status === 401) {
      onUnauthorized()
      if (!err.config?.silent) ElMessage.warning({ message: '请先登录', grouping: true })
    } else if (!err.config?.silent) {
      ElMessage.error(err.response ? `服务器错误（${err.response.status}）` : '网络异常，请检查服务是否正常运行')
    }
    err.handled = true
    return Promise.reject(err)
  },
)

export default http
