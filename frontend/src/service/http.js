import axios from 'axios'
import { ElMessage } from 'element-plus'

const http = axios.create({
  baseURL: '/api/',
  timeout: 120000,
})

// 统一处理业务错误与网络错误：业务 code != 200 时提示后端返回的 msg 并 reject
http.interceptors.response.use(
  (res) => {
    const body = res.data
    if (body && typeof body === 'object' && 'code' in body && body.code !== 200) {
      ElMessage.error(body.msg || '操作失败')
      return Promise.reject(Object.assign(new Error(body.msg), { handled: true, body }))
    }
    return body
  },
  (err) => {
    const msg = err.response ? `服务器错误（${err.response.status}）` : '网络异常，请检查服务是否正常运行'
    ElMessage.error(msg)
    err.handled = true
    return Promise.reject(err)
  },
)

export default http
