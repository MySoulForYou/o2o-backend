<script setup lang="ts">
import { ref, onMounted, computed } from 'vue'
import { useRouter } from 'vue-router'
import { useUserStore } from '@/store/user'
import { Star, Search } from '@element-plus/icons-vue'
import axios from 'axios'

const router = useRouter()
const userStore = useUserStore()

const loading = ref(false)
const searchQuery = ref('')
const selectedCategory = ref('')
const shopList = ref<any[]>([])

// 预置分类
const categories = ['全部', '法式烘焙', '特色餐饮', '美容美体', '鲜花绿植', '数码便利']

// 本地高级感 Mock 店铺数据 (对接 API 失败时的兜底设计，坐标设计为静安区附近)
const mockShops = [
  {
    id: 1,
    name: '品味法式烘焙',
    category: '法式烘焙',
    status: 1,
    score: 5.0,
    avgPrice: 120,
    coverUrl: 'https://images.unsplash.com/photo-1540189549336-e6e99c3679fe?auto=format&fit=crop&w=400&q=80',
    latitude: 31.23305, // 静安区南京西路附近
    longitude: 121.45812
  },
  {
    id: 2,
    name: '逸品轩·新奥尔良烤肉',
    category: '特色餐饮',
    status: 1,
    score: 4.8,
    avgPrice: 180,
    coverUrl: 'https://images.unsplash.com/photo-1607344645866-009c320c5ab8?auto=format&fit=crop&w=400&q=80',
    latitude: 31.22120,
    longitude: 121.46830
  },
  {
    id: 3,
    name: '悦己SPA中心',
    category: '美容美体',
    status: 1,
    score: 4.9,
    avgPrice: 350,
    coverUrl: 'https://images.unsplash.com/photo-1610348725531-843dff163e2c?auto=format&fit=crop&w=400&q=80',
    latitude: 31.23410,
    longitude: 121.44810
  },
  {
    id: 4,
    name: '艺术花艺工作坊',
    category: '鲜花绿植',
    status: 1,
    score: 4.7,
    avgPrice: 200,
    coverUrl: 'https://images.unsplash.com/photo-1527443224154-c4a3942d3acf?auto=format&fit=crop&w=400&q=80',
    latitude: 31.24150,
    longitude: 121.45990
  }
]

// 简单的哈弗辛(Haversine)公式计算两点之间的物理距离 (km)
const getDistance = (lat1: number, lon1: number, lat2: number, lon2: number) => {
  const R = 6371 // 地球半径 km
  const dLat = ((lat2 - lat1) * Math.PI) / 180
  const dLon = ((lon2 - lon1) * Math.PI) / 180
  const a =
    Math.sin(dLat / 2) * Math.sin(dLat / 2) +
    Math.cos((lat1 * Math.PI) / 180) *
      Math.cos((lat2 * Math.PI) / 180) *
      Math.sin(dLon / 2) *
      Math.sin(dLon / 2)
  const c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a))
  return parseFloat((R * c).toFixed(1))
}

const fetchShops = async () => {
  loading.value = true
  try {
    const res = await axios.get('/api/shop/customer/shop/page', {
      params: {
        page: 1,
        pageSize: 20
      }
    })
    if (res.data && res.data.code === 200) {
      shopList.value = res.data.data.records || []
    } else {
      useFallbackData()
    }
  } catch (error) {
    console.warn('API connection failed, using fallback database-level mock data.')
    useFallbackData()
  } finally {
    loading.value = false
  }
}

const useFallbackData = () => {
  shopList.value = [...mockShops]
}

// 过滤和排序后的商铺列表 (支持实时根据 PC 定位改变距离)
const processedShops = computed(() => {
  const userLat = userStore.currentLocation.latitude
  const userLon = userStore.currentLocation.longitude

  return shopList.value
    .map((shop) => {
      // 动态计算距离
      const distance = getDistance(
        userLat,
        userLon,
        shop.latitude || 31.22997,
        shop.longitude || 121.45529
      )
      return {
        ...shop,
        distance
      }
    })
    .filter((shop) => {
      // 类别筛选
      if (selectedCategory.value && selectedCategory.value !== '全部') {
        return shop.category === selectedCategory.value
      }
      return true
    })
    .filter((shop) => {
      // 搜索筛选
      if (searchQuery.value) {
        return shop.name.toLowerCase().includes(searchQuery.value.toLowerCase())
      }
      return true
    })
    .sort((a, b) => a.distance - b.distance) // 按距离升序排列 (LBS 核心诉求)
})

const handleSelectCategory = (cat: string) => {
  selectedCategory.value = cat
}

const goToDetail = (id: number) => {
  router.push(`/shop/${id}`)
}

onMounted(() => {
  fetchShops()
})
</script>

<template>
  <div class="max-w-7xl mx-auto px-6 py-8 font-sans">
    
    <!-- 顶部 Banner 展示区与 Bento 布局 -->
    <section class="grid grid-cols-1 lg:grid-cols-3 gap-6 mb-10">
      <!-- 左侧主推荐轮播（占 2/3） -->
      <div class="lg:col-span-2 rounded-[24px] overflow-hidden shadow-xl shadow-slate-100/40 border border-slate-100 bg-white relative group h-[340px]">
        <img 
          src="https://images.unsplash.com/photo-1607082348824-0a96f2a4b9da?auto=format&fit=crop&w=1200&q=80" 
          class="w-full h-full object-cover transition-transform duration-700 group-hover:scale-105"
          alt="Premium Banner"
        />
        <div class="absolute inset-0 bg-gradient-to-t from-slate-900/60 via-transparent to-transparent flex flex-col justify-end p-8 text-white">
          <span class="text-xs bg-orange-500 font-semibold px-2.5 py-1 rounded-full w-max mb-3">限时狂欢</span>
          <h2 class="text-3xl font-extrabold mb-1">享受精致生活 | 本周优选</h2>
          <p class="text-sm text-white/85">网罗同城格调好店，极速达您的身边</p>
        </div>
      </div>

      <!-- 右侧双 Bento 卡片（占 1/3） -->
      <div class="grid grid-rows-2 gap-6 h-[340px]">
        <div class="bg-gradient-to-br from-amber-500 to-orange-600 rounded-[20px] p-6 text-white flex flex-col justify-between shadow-lg shadow-orange-500/10">
          <div>
            <h3 class="text-lg font-bold">1元秒杀</h3>
            <p class="text-xs text-white/80 mt-1">天天好券，超低价好店代金券限量抢购</p>
          </div>
          <router-link 
            to="/seckill"
            class="text-xs font-semibold bg-white text-orange-600 px-4 py-2 rounded-full w-max shadow-sm hover:scale-105 transition-transform active:scale-95"
          >
            立即前往
          </router-link>
        </div>

        <div class="bg-white border border-slate-100 rounded-[20px] p-6 flex flex-col justify-between shadow-xl shadow-slate-100/40">
          <div>
            <h3 class="text-lg font-bold text-slate-800">精选主食</h3>
            <p class="text-xs text-slate-400 mt-1">寻找静安区附近最受好评的美食佳肴</p>
          </div>
          <button 
            @click="handleSelectCategory('特色餐饮')"
            class="text-xs font-semibold bg-slate-900 text-white px-4 py-2 rounded-full w-max shadow-sm hover:bg-slate-800 transition-colors"
          >
            立即筛选
          </button>
        </div>
      </div>
    </section>

    <!-- 分类筛选与搜索条 -->
    <section class="flex flex-col md:flex-row items-center justify-between gap-4 mb-8 bg-white p-4 rounded-2xl border border-slate-100 shadow-sm">
      <!-- 左侧分类列表 -->
      <div class="flex flex-wrap gap-2">
        <button
          v-for="cat in categories"
          :key="cat"
          @click="handleSelectCategory(cat === '全部' ? '' : cat)"
          :class="[
            'px-4 py-1.5 rounded-full text-sm font-medium transition-all active:scale-95',
            (!selectedCategory && cat === '全部') || selectedCategory === cat
              ? 'bg-orange-500 text-white shadow-sm shadow-orange-500/20'
              : 'bg-slate-50 text-slate-600 hover:bg-slate-100'
          ]"
        >
          {{ cat }}
        </button>
      </div>

      <!-- 右侧搜索 -->
      <div class="relative w-full md:w-80">
        <el-input 
          v-model="searchQuery" 
          placeholder="搜索店铺名称..." 
          :prefix-icon="Search"
          size="large"
          clearable
        />
      </div>
    </section>

    <!-- 店铺大厅列表 ( Explore Stores Grid ) -->
    <section>
      <h3 class="text-xl font-bold text-slate-800 mb-6 flex items-center">
        <span class="inline-block w-1 h-5 bg-orange-500 rounded-full mr-2"></span>
        探索周边店铺
      </h3>

      <div v-loading="loading">
        <div v-if="processedShops.length === 0" class="text-center py-20 text-slate-400">
          暂无匹配的周边商铺，请更换 Mock 定位或分类重试
        </div>
        <div v-else class="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-6">
          <!-- 店铺卡片 -->
          <div 
            v-for="shop in processedShops" 
            :key="shop.id"
            @click="goToDetail(shop.id)"
            class="bg-white rounded-2xl overflow-hidden border border-slate-200/60 hover:border-orange-300 shadow-sm hover:shadow-xl hover:shadow-slate-100/80 transition-all duration-300 cursor-pointer flex flex-col h-full group"
          >
            <!-- 封面图 -->
            <div class="h-44 w-full overflow-hidden bg-slate-50 relative">
              <img 
                :src="shop.coverUrl || 'https://images.unsplash.com/photo-1540189549336-e6e99c3679fe?auto=format&fit=crop&w=400&q=80'" 
                class="w-full h-full object-cover transition-transform duration-500 group-hover:scale-105"
                alt="Shop Cover"
              />
              <span class="absolute top-3 right-3 bg-white/90 backdrop-blur-sm text-xs font-semibold px-2 py-0.5 rounded-full text-slate-700 shadow-sm">
                {{ shop.category }}
              </span>
            </div>

            <!-- 店铺文字信息 -->
            <div class="p-4 flex-grow flex flex-col justify-between">
              <div>
                <h4 class="font-bold text-slate-800 group-hover:text-orange-500 transition-colors text-base truncate mb-1">
                  {{ shop.name }}
                </h4>
                <div class="flex items-center space-x-1 mb-2">
                  <el-icon class="text-amber-500"><Star /></el-icon>
                  <span class="text-sm font-bold text-amber-500">{{ shop.score }}</span>
                  <span class="text-slate-300 text-xs">|</span>
                  <span class="text-slate-500 text-xs">人均 ￥{{ shop.avgPrice }}</span>
                </div>
              </div>

              <!-- LBS 物理距离展示 -->
              <div class="flex items-center justify-between border-t border-slate-50 pt-3 mt-3">
                <span class="text-xs text-slate-400">静安区</span>
                <span class="text-xs bg-orange-50 text-orange-600 px-2.5 py-0.5 rounded-full font-medium">
                  距离 {{ shop.distance }} km
                </span>
              </div>
            </div>
          </div>
        </div>
      </div>
    </section>

  </div>
</template>
