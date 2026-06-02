<script setup lang="ts">
import { computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useUserStore } from '@/store/user'
import { ShoppingCart, Location, ArrowDown } from '@element-plus/icons-vue'

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()

// 登录页面不展示全局 Header 和 Footer
const showLayout = computed(() => route.path !== '/login')

const username = computed(() => userStore.userInfo?.username || '')
const isLoggedIn = computed(() => !!userStore.token)

const handleLogout = () => {
  userStore.logout()
  router.push('/login')
}

// 模拟 GPS 位置，方便在 PC 端测试 Redis Geo
const mockLocations = [
  { name: '上海市 静安区南京西路123号 (近距离)', latitude: 31.22997, longitude: 121.45529 },
  { name: '上海市 浦东新区世纪大道88号 (中等距离)', latitude: 31.23594, longitude: 121.50637 },
  { name: '上海市 宝山区顾村公园 (远距离)', latitude: 31.35336, longitude: 121.36531 }
]

const switchLocation = (loc: typeof mockLocations[0]) => {
  userStore.setLocation(loc)
  // 位置切换后，广播位置变更事件或直接刷新页面重新拉取 LBS 商家数据
  if (route.name === 'Home') {
    // 触发局部重载或全局刷新
    window.location.reload()
  }
}
</script>

<template>
  <div class="min-h-screen bg-[#F5F5F7] text-slate-800 flex flex-col font-sans">
    <!-- Header 顶部通栏导航 -->
    <header v-if="showLayout" class="h-16 bg-white border-b border-slate-200/80 sticky top-0 z-50 shadow-sm shadow-slate-100/50">
      <div class="max-w-7xl mx-auto h-full px-6 flex items-center justify-between">
        <!-- 左侧 Logo 与 定位选择 -->
        <div class="flex items-center space-x-6">
          <router-link to="/home" class="flex items-center space-x-2">
            <span class="text-xl font-bold bg-gradient-to-r from-orange-500 to-amber-600 bg-clip-text text-transparent">优选生活</span>
            <span class="text-xs bg-amber-50 text-amber-700 px-1.5 py-0.5 rounded border border-amber-200/40">PC版</span>
          </router-link>

          <!-- 模拟定位下拉菜单 -->
          <el-dropdown trigger="click">
            <span class="flex items-center text-sm text-slate-600 cursor-pointer hover:text-orange-500 transition-colors">
              <el-icon class="mr-1 text-orange-500"><Location /></el-icon>
              {{ userStore.currentLocation.name }}
              <el-icon class="ml-1 text-slate-400"><ArrowDown /></el-icon>
            </span>
            <template #dropdown>
              <el-dropdown-menu>
                <el-dropdown-item 
                  v-for="loc in mockLocations" 
                  :key="loc.name"
                  @click="switchLocation(loc)"
                >
                  {{ loc.name }}
                </el-dropdown-item>
              </el-dropdown-menu>
            </template>
          </el-dropdown>
        </div>

        <!-- 中间：页面导航链接 -->
        <div class="flex items-center space-x-8 text-sm font-medium">
          <router-link to="/home" class="text-slate-600 hover:text-orange-500 transition-colors" active-class="text-orange-500 font-semibold">首页</router-link>
          <router-link to="/seckill" class="text-slate-600 hover:text-orange-500 transition-colors" active-class="text-orange-500 font-semibold">特惠秒杀</router-link>
          <router-link to="/cart" class="text-slate-600 hover:text-orange-500 transition-colors" active-class="text-orange-500 font-semibold">我的购物车</router-link>
        </div>

        <!-- 右侧：快捷操作 -->
        <div class="flex items-center space-x-6">
          <!-- 购物车图标 -->
          <router-link to="/cart" class="relative p-1 text-slate-500 hover:text-orange-500 transition-colors">
            <el-icon :size="22"><ShoppingCart /></el-icon>
          </router-link>

          <!-- 用户资料 / 登录状态 -->
          <div class="flex items-center space-x-3 border-l border-slate-200 pl-6">
            <template v-if="isLoggedIn">
              <el-dropdown trigger="click">
                <span class="flex items-center space-x-2 text-sm text-slate-700 cursor-pointer font-medium hover:text-orange-500">
                  <el-avatar :size="28" src="https://images.unsplash.com/photo-1534528741775-53994a69daeb?auto=format&fit=crop&w=150&h=150&q=80" />
                  <span>{{ username }}</span>
                </span>
                <template #dropdown>
                  <el-dropdown-menu>
                    <el-dropdown-item @click="handleLogout">退出登录</el-dropdown-item>
                  </el-dropdown-menu>
                </template>
              </el-dropdown>
            </template>
            <template v-else>
              <router-link to="/login" class="text-sm font-semibold text-orange-500 hover:text-orange-600 transition-colors">登录</router-link>
            </template>
          </div>
        </div>
      </div>
    </header>

    <!-- 主体路由页面 -->
    <main class="flex-grow">
      <router-view />
    </main>

    <!-- 页脚 -->
    <footer v-if="showLayout" class="bg-white border-t border-slate-200/60 py-6 text-center text-xs text-slate-400">
      <div class="max-w-7xl mx-auto px-6 flex flex-col sm:flex-row items-center justify-between space-y-2 sm:space-y-0">
        <span>© 2026 O2O 智能聚合电商平台 - PC网页端演示版</span>
        <div class="flex items-center space-x-4">
          <span class="flex items-center text-slate-400">
            <span class="w-2 h-2 rounded-full bg-green-500 mr-1.5 animate-pulse"></span>
            微服务网关 (Port 8080) 连通中
          </span>
          <span class="text-slate-300">|</span>
          <span>高并发逻辑过期缓存 & GeoLBS 技术支持</span>
        </div>
      </div>
    </footer>
  </div>
</template>

<style>
/* 优雅的系统滚动条与字体微调 */
html, body {
  margin: 0;
  padding: 0;
  scroll-behavior: smooth;
}
.el-dropdown-menu__item:hover {
  background-color: #FFF7ED !important;
  color: #EA580C !important;
}
</style>
