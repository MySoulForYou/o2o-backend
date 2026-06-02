import axios from 'axios'
import { ElMessage } from 'element-plus'

const service = axios.create({
  baseURL: '/api',
  timeout: 15000
})

// 请求拦截器
service.interceptors.request.use(
  (config) => {
    const token = localStorage.getItem('o2o_token')
    if (token) {
      config.headers['Authorization'] = `Bearer ${token}`
    }
    return config
  },
  (error) => {
    return Promise.reject(error)
  }
)

// 响应拦截器
service.interceptors.response.use(
  (response) => {
    const res = response.data
    // 如果返回 code 为 200，说明业务成功，直接返回内部的 data
    if (res.code === 200) {
      return res.data
    } else {
      // 业务失败，统一弹窗提示错误
      ElMessage.error(res.message || '操作失败')
      return Promise.reject(new Error(res.message || 'Error'))
    }
  },
  (error) => {
    const status = error.response?.status
    const resData = error.response?.data
    const msg = resData?.message || error.message || '网络连接异常'
    
    ElMessage.error(msg)

    if (status === 401) {
      // 鉴权失败，清除本地 Token 并重定向至登录页
      localStorage.removeItem('o2o_token')
      localStorage.removeItem('o2o_user')
      window.location.href = '#/login'
    }
    return Promise.reject(error)
  }
)

export default service
