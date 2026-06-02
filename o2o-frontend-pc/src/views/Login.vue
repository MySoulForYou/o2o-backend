<script setup lang="ts">
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import { useUserStore } from '@/store/user'
import { ElMessage } from 'element-plus'
import axios from 'axios'

const router = useRouter()
const userStore = useUserStore()

const activeTab = ref('login')
const loading = ref(false)

// 登录表单
const loginForm = ref({
  username: '',
  password: ''
})

// 注册表单
const registerForm = ref({
  username: '',
  password: '',
  phone: ''
})

const handleLogin = async () => {
  if (!loginForm.value.username || !loginForm.value.password) {
    ElMessage.warning('请填写完整的用户名和密码')
    return
  }
  loading.value = true
  try {
    // 请求网关登录接口
    const res = await axios.post('/api/user/login', loginForm.value)
    if (res.data && res.data.code === 200) {
      const data = res.data.data
      userStore.setToken(data.token)
      userStore.setUserInfo(data.userInfo)
      ElMessage.success('登录成功')
      router.push('/home')
    } else {
      ElMessage.error(res.data?.message || '登录失败')
    }
  } catch (error: any) {
    console.error('API Error, entering fallback mock mode:', error)
    // 降级兜底登录逻辑：如果后端服务未开启，支持任意密码登录，方便独立调试前端
    if (loginForm.value.username.length >= 4) {
      userStore.setToken('mock_jwt_token_for_' + loginForm.value.username)
      userStore.setUserInfo({
        id: 9999,
        username: loginForm.value.username,
        nickname: '开发者_' + loginForm.value.username,
        avatar: ''
      })
      ElMessage.warning('网关未连接，已进入本地 Mock 调试模式')
      router.push('/home')
    } else {
      ElMessage.error('用户名需至少 4 位（Mock 模式要求）')
    }
  } finally {
    loading.value = false
  }
}

const handleRegister = async () => {
  if (!registerForm.value.username || !registerForm.value.password || !registerForm.value.phone) {
    ElMessage.warning('请填写完整的注册信息')
    return
  }
  loading.value = true
  try {
    const res = await axios.post('/api/user/register', registerForm.value)
    if (res.data && res.data.code === 200) {
      ElMessage.success('注册成功，请登录')
      // 自动切回登录并填入用户名
      loginForm.value.username = registerForm.value.username
      activeTab.value = 'login'
    } else {
      ElMessage.error(res.data?.message || '注册失败')
    }
  } catch (error: any) {
    ElMessage.error(error.response?.data?.message || '后端服务异常，注册失败')
  } finally {
    loading.value = false
  }
}
</script>

<template>
  <div class="min-h-screen bg-[#F5F5F7] flex items-center justify-center py-12 px-4 sm:px-6 lg:px-8 font-sans">
    <div class="max-w-md w-full space-y-8 bg-white p-8 rounded-[24px] shadow-xl shadow-slate-200/50 border border-slate-100">
      <div class="text-center">
        <h2 class="text-3xl font-extrabold text-slate-800 tracking-tight">优选生活</h2>
        <p class="mt-2 text-sm text-slate-500">同城 LBS 智能生活聚合服务平台</p>
      </div>

      <div class="mt-6">
        <el-tabs v-model="activeTab" class="login-tabs">
          <!-- 登录选项卡 -->
          <el-tab-pane label="账号登录" name="login">
            <div class="space-y-4 mt-4">
              <div>
                <label class="block text-xs font-semibold text-slate-500 mb-1">用户名</label>
                <el-input 
                  v-model="loginForm.username" 
                  placeholder="请输入您的用户名" 
                  size="large"
                  clearable
                />
              </div>
              <div>
                <label class="block text-xs font-semibold text-slate-500 mb-1">密码</label>
                <el-input 
                  v-model="loginForm.password" 
                  type="password" 
                  placeholder="请输入密码" 
                  size="large"
                  show-password
                />
              </div>
              
              <button 
                @click="handleLogin" 
                :disabled="loading"
                class="w-full mt-6 py-3 px-4 rounded-xl text-white font-semibold text-sm bg-gradient-to-r from-orange-500 to-amber-600 hover:from-orange-600 hover:to-amber-700 active:scale-95 transition-all shadow-md shadow-orange-500/10 flex justify-center items-center"
              >
                <span v-if="loading" class="animate-spin border-2 border-white border-t-transparent rounded-full w-4 h-4 mr-2"></span>
                立即登录
              </button>
            </div>
          </el-tab-pane>

          <!-- 注册选项卡 -->
          <el-tab-pane label="新用户注册" name="register">
            <div class="space-y-4 mt-4">
              <div>
                <label class="block text-xs font-semibold text-slate-500 mb-1">用户名</label>
                <el-input 
                  v-model="registerForm.username" 
                  placeholder="字母/数字/下划线，4-20位" 
                  size="large"
                  clearable
                />
              </div>
              <div>
                <label class="block text-xs font-semibold text-slate-500 mb-1">密码</label>
                <el-input 
                  v-model="registerForm.password" 
                  type="password" 
                  placeholder="6-32位密码" 
                  size="large"
                  show-password
                />
              </div>
              <div>
                <label class="block text-xs font-semibold text-slate-500 mb-1">手机号</label>
                <el-input 
                  v-model="registerForm.phone" 
                  placeholder="11位手机号码" 
                  size="large"
                  clearable
                />
              </div>

              <button 
                @click="handleRegister" 
                :disabled="loading"
                class="w-full mt-6 py-3 px-4 rounded-xl text-white font-semibold text-sm bg-gradient-to-r from-orange-500 to-amber-600 hover:from-orange-600 hover:to-amber-700 active:scale-95 transition-all shadow-md shadow-orange-500/10 flex justify-center items-center"
              >
                <span v-if="loading" class="animate-spin border-2 border-white border-t-transparent rounded-full w-4 h-4 mr-2"></span>
                立即注册
              </button>
            </div>
          </el-tab-pane>
        </el-tabs>
      </div>

      <div class="text-center pt-2 text-xs text-slate-400">
        如微服务网关未开启，将默认采用 Local Mock 模式登录
      </div>
    </div>
  </div>
</template>

<style>
.login-tabs .el-tabs__nav {
  width: 100%;
  display: flex;
}
.login-tabs .el-tabs__item {
  flex: 1;
  text-align: center;
  font-weight: 600;
  font-size: 15px;
}
.login-tabs .el-tabs__active-bar {
  background-color: #F97316;
}
.login-tabs .el-tabs__item.is-active {
  color: #F97316;
}
</style>
