<script setup lang="ts">
import { ref, onMounted, computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useUserStore } from '@/store/user'
import { ElMessage } from 'element-plus'
import { Star } from '@element-plus/icons-vue'
import axios from 'axios'

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()

const shopId = Number(route.params.id)
const shopInfo = ref<any>(null)
const goodsList = ref<any[]>([])
const loading = ref(false)
const selectedCategory = ref('招牌推荐')

// 本地 Mock 店铺详情与商品数据库数据 (若网关拉取接口失败则作为兜底)
const defaultShopDetails: Record<number, any> = {
  1: {
    id: 1,
    name: '品味法式烘焙',
    address: '上海市静安区南京西路123号',
    phone: '021-12345678',
    hours: '09:00 - 21:00',
    coverUrl: 'https://images.unsplash.com/photo-1540189549336-e6e99c3679fe?auto=format&fit=crop&w=800&q=80',
    categories: ['招牌推荐', '法式甜点', '经典咖啡', '下午茶套餐', '面包 & 土司']
  },
  2: {
    id: 2,
    name: '逸品轩·新奥尔良烤肉',
    address: '上海市静安区威海路456号',
    phone: '021-87654321',
    hours: '11:00 - 23:00',
    coverUrl: 'https://images.unsplash.com/photo-1607344645866-009c320c5ab8?auto=format&fit=crop&w=800&q=80',
    categories: ['招牌推荐', '经典烤肉', '冰爽啤酒', '超值套餐']
  }
}

const mockGoods: Record<number, any[]> = {
  1: [
    { id: 101, name: '经典草莓慕斯塔', price: 38.0, category: '法式甜点', score: 4.9, imgUrl: 'https://images.unsplash.com/photo-1544025162-d76694265947?auto=format&fit=crop&w=250&q=80' },
    { id: 102, name: '法式香草泡芙', price: 26.0, category: '法式甜点', score: 4.8, imgUrl: 'https://images.unsplash.com/photo-1607344645866-009c320c5ab8?auto=format&fit=crop&w=250&q=80' },
    { id: 103, name: '巧克力心太软', price: 45.0, category: '法式甜点', score: 4.8, imgUrl: 'https://images.unsplash.com/photo-1609592424109-dd9892f1b17c?auto=format&fit=crop&w=250&q=80' },
    { id: 104, name: '柠檬挞', price: 35.0, category: '法式甜点', score: 4.7, imgUrl: 'https://images.unsplash.com/photo-1610348725531-843dff163e2c?auto=format&fit=crop&w=250&q=80' },
    { id: 105, name: '马卡龙礼盒 (6枚)', price: 98.0, category: '招牌推荐', score: 5.0, imgUrl: 'https://images.unsplash.com/photo-1527443224154-c4a3942d3acf?auto=format&fit=crop&w=250&q=80' },
    { id: 106, name: '拿铁咖啡', price: 28.0, category: '经典咖啡', score: 4.8, imgUrl: 'https://images.unsplash.com/photo-1540189549336-e6e99c3679fe?auto=format&fit=crop&w=250&q=80' }
  ],
  2: [
    { id: 201, name: '招牌新奥尔良烤鸡翅 (4只)', price: 32.0, category: '招牌推荐', score: 4.9, imgUrl: 'https://images.unsplash.com/photo-1607344645866-009c320c5ab8?auto=format&fit=crop&w=250&q=80' },
    { id: 202, name: '经典烤五花肉串 (5支)', price: 28.0, category: '经典烤肉', score: 4.8, imgUrl: 'https://images.unsplash.com/photo-1607344645866-009c320c5ab8?auto=format&fit=crop&w=250&q=80' }
  ]
}

// 购物车本地状态（同步 localstorage 以防 cart 微服务未开启）
const localCart = ref<any[]>([])

const loadCart = () => {
  const cartData = localStorage.getItem('o2o_local_cart')
  localCart.value = cartData ? JSON.parse(cartData) : []
}

const saveCart = () => {
  localStorage.setItem('o2o_local_cart', JSON.stringify(localCart.value))
}

const fetchData = async () => {
  loading.value = true
  // 店铺详情拉取
  try {
    const res = await axios.get(`/api/shop/customer/shop/${shopId}`)
    if (res.data && res.data.code === 200) {
      shopInfo.value = res.data.data
    } else {
      shopInfo.value = defaultShopDetails[shopId] || defaultShopDetails[1]
    }
  } catch {
    shopInfo.value = defaultShopDetails[shopId] || defaultShopDetails[1]
  }

  // 商品列表拉取
  try {
    const res = await axios.get(`/api/shop/customer/shop/${shopId}/goods`)
    if (res.data && res.data.code === 200) {
      goodsList.value = res.data.data || []
    } else {
      goodsList.value = mockGoods[shopId] || mockGoods[1]
    }
  } catch {
    goodsList.value = mockGoods[shopId] || mockGoods[1]
  }
  loading.value = false
}

// 侧栏品类切换
const handleSelectCategory = (cat: string) => {
  selectedCategory.value = cat
}

// 过滤显示中栏商品
const filteredGoods = computed(() => {
  return goodsList.value.filter((g) => g.category === selectedCategory.value)
})

// 购物车交互，支持微服务接口 / 降级本地存储双读写
const handleAddToCart = async (goods: any) => {
  // 要求登录校验
  if (!userStore.token) {
    ElMessage.warning('请先登录以使用购物车')
    router.push('/login')
    return
  }

  try {
    // 尝试调用网关的购物车接口
    const res = await axios.post('/api/cart/add', {
      goodsId: goods.id,
      shopId: shopId,
      quantity: 1
    })
    if (res.data && res.data.code === 200) {
      ElMessage.success('成功加入购物车')
    } else {
      fallbackAddToCart(goods)
    }
  } catch {
    // 降级本地逻辑
    fallbackAddToCart(goods)
  }
}

const fallbackAddToCart = (goods: any) => {
  const existing = localCart.value.find((item) => item.goodsId === goods.id && item.shopId === shopId)
  if (existing) {
    existing.quantity++
  } else {
    localCart.value.push({
      goodsId: goods.id,
      goodsName: goods.name,
      price: goods.price,
      imgUrl: goods.imgUrl || goods.coverUrl,
      quantity: 1,
      shopId: shopId,
      shopName: shopInfo.value?.name || '未知商铺',
      checked: true
    })
  }
  saveCart()
  ElMessage.success(`${goods.name} 已成功加入购物车`)
}

// 购物车右侧边栏计算
const currentShopCartItems = computed(() => {
  return localCart.value.filter((item) => item.shopId === shopId)
})

const cartTotal = computed(() => {
  return currentShopCartItems.value.reduce((sum, item) => sum + item.price * item.quantity, 0)
})

const updateQuantity = (item: any, delta: number) => {
  item.quantity += delta
  if (item.quantity <= 0) {
    localCart.value = localCart.value.filter((i) => !(i.goodsId === item.goodsId && i.shopId === shopId))
  }
  saveCart()
}

const goToCheckout = () => {
  if (currentShopCartItems.value.length === 0) {
    ElMessage.warning('订单中尚无商品')
    return
  }
  router.push('/checkout')
}

onMounted(() => {
  fetchData()
  loadCart()
})
</script>

<template>
  <div v-if="shopInfo" class="max-w-7xl mx-auto px-6 py-8 font-sans" v-loading="loading">
    
    <!-- 店铺通栏招牌 Header -->
    <section class="bg-white rounded-[24px] overflow-hidden border border-slate-200/80 mb-8 shadow-sm flex flex-col md:flex-row items-center justify-between p-6">
      <div class="flex items-center space-x-6">
        <!-- 面包店小内景/封面 -->
        <div class="w-20 h-20 rounded-2xl overflow-hidden bg-slate-50 border border-slate-100 flex-shrink-0">
          <img :src="shopInfo.coverUrl" class="w-full h-full object-cover" alt="Shop Internal" />
        </div>
        <div class="space-y-1">
          <h2 class="text-2xl font-extrabold text-slate-800">{{ shopInfo.name }}</h2>
          <p class="text-xs text-slate-500">营业时间：{{ shopInfo.hours }} | 电话：{{ shopInfo.phone }}</p>
          <p class="text-xs text-slate-400">地址：{{ shopInfo.address }}</p>
        </div>
      </div>
      <!-- 店铺状态 -->
      <div class="mt-4 md:mt-0 flex items-center space-x-3 bg-amber-50/50 border border-amber-200/40 px-4 py-2.5 rounded-2xl">
        <span class="w-2.5 h-2.5 rounded-full bg-green-500 animate-pulse"></span>
        <span class="text-xs font-bold text-amber-700">正在营业 (Geo 极速达)</span>
      </div>
    </section>

    <!-- 黄金分栏主体结构 (左菜单, 中商品, 右订单) -->
    <div class="flex flex-col lg:flex-row items-start gap-8">
      
      <!-- 1. 左栏：品类导航 (w-60) -->
      <aside class="w-full lg:w-60 bg-white rounded-2xl p-4 border border-slate-200/60 sticky top-20 shadow-sm flex-shrink-0">
        <h3 class="text-sm font-bold text-slate-400 mb-3 uppercase tracking-wider px-2">菜品目录</h3>
        <nav class="space-y-1">
          <button
            v-for="cat in (shopInfo.categories || ['法式甜点', '经典咖啡', '招牌推荐'])"
            :key="cat"
            @click="handleSelectCategory(cat)"
            :class="[
              'w-full text-left px-3 py-2.5 rounded-xl text-sm font-medium transition-all duration-100 active:scale-98',
              selectedCategory === cat 
                ? 'bg-orange-50 text-orange-600 font-bold border-l-4 border-orange-500' 
                : 'text-slate-600 hover:bg-slate-50 hover:text-slate-800'
            ]"
          >
            {{ cat }}
          </button>
        </nav>
      </aside>

      <!-- 2. 中栏：商品列表网格 (flex-1) -->
      <section class="flex-grow">
        <div class="bg-white rounded-2xl p-6 border border-slate-200/60 shadow-sm">
          <h3 class="text-lg font-bold text-slate-800 mb-6 flex items-center">
            <span class="inline-block w-1.5 h-4 bg-orange-500 rounded-full mr-2"></span>
            {{ selectedCategory }}
          </h3>

          <div v-if="filteredGoods.length === 0" class="text-center py-20 text-slate-400">
            该类别下暂无商品
          </div>
          <div v-else class="grid grid-cols-1 md:grid-cols-3 gap-6">
            <div 
              v-for="goods in filteredGoods" 
              :key="goods.id"
              class="border border-slate-100 rounded-2xl overflow-hidden hover:shadow-lg hover:shadow-slate-100/50 transition-all flex flex-col h-full bg-[#FAFAFA]/50 group"
            >
              <!-- 甜点大图 -->
              <div class="h-36 w-full overflow-hidden bg-slate-50 relative">
                <img :src="goods.imgUrl" class="w-full h-full object-cover group-hover:scale-105 transition-transform duration-500" alt="Goods Photo" />
                <span class="absolute top-2 right-2 bg-white/90 backdrop-blur-sm px-1.5 py-0.5 rounded text-[10px] font-bold text-amber-500 flex items-center">
                  <el-icon class="mr-0.5"><Star /></el-icon>{{ goods.score }}
                </span>
              </div>

              <!-- 商品详情及加购 -->
              <div class="p-3 flex-grow flex flex-col justify-between space-y-3">
                <div>
                  <h4 class="font-bold text-slate-800 text-sm line-clamp-1 mb-1">{{ goods.name }}</h4>
                  <p class="text-xs text-slate-400">招牌工艺，精选新鲜原料</p>
                </div>
                <div class="flex items-center justify-between">
                  <span class="text-sm font-extrabold text-orange-600">￥{{ goods.price.toFixed(2) }}</span>
                  <button 
                    @click="handleAddToCart(goods)"
                    class="h-8 px-3 rounded-lg bg-orange-500 hover:bg-orange-600 text-white text-xs font-bold transition-all active:scale-95 flex items-center space-x-1"
                  >
                    <span>加入</span>
                  </button>
                </div>
              </div>
            </div>
          </div>
        </div>
      </section>

      <!-- 3. 右栏：我的订单结算常驻栏 (w-80) -->
      <aside class="w-full lg:w-80 bg-white rounded-2xl p-6 border border-slate-200/60 sticky top-20 shadow-sm flex-shrink-0 flex flex-col justify-between min-h-[380px]">
        <div>
          <h3 class="text-base font-bold text-slate-800 mb-4 border-b border-slate-100 pb-3 flex items-center justify-between">
            <span>我的订单</span>
            <span class="text-xs bg-slate-100 text-slate-500 px-2 py-0.5 rounded-full">已加购 {{ currentShopCartItems.length }} 件</span>
          </h3>

          <div v-if="currentShopCartItems.length === 0" class="text-center py-16 text-slate-400 text-xs">
            您的点单袋空空如也，快去加购美味吧！
          </div>
          
          <ul v-else class="space-y-4 max-h-[260px] overflow-y-auto pr-1">
            <li 
              v-for="item in currentShopCartItems" 
              :key="item.goodsId"
              class="flex items-center justify-between text-xs border-b border-slate-50 pb-3"
            >
              <div class="space-y-0.5 flex-1 pr-3">
                <p class="font-bold text-slate-800 truncate">{{ item.goodsName }}</p>
                <p class="text-slate-400">单价 ￥{{ item.price }}</p>
              </div>

              <!-- 计数器 -->
              <div class="flex items-center space-x-2.5">
                <button 
                  @click="updateQuantity(item, -1)"
                  class="w-5 h-5 rounded-full border border-slate-200 hover:border-orange-500 hover:text-orange-500 flex items-center justify-center font-bold"
                >-</button>
                <span class="font-mono text-slate-700 font-bold">{{ item.quantity }}</span>
                <button 
                  @click="updateQuantity(item, 1)"
                  class="w-5 h-5 rounded-full border border-slate-200 hover:border-orange-500 hover:text-orange-500 flex items-center justify-center font-bold"
                >+</button>
              </div>
            </li>
          </ul>
        </div>

        <div class="border-t border-slate-100 pt-4 mt-6">
          <div class="flex items-center justify-between mb-4">
            <span class="text-sm font-medium text-slate-500">合计金额：</span>
            <span class="text-xl font-extrabold text-orange-600">￥{{ cartTotal.toFixed(2) }}</span>
          </div>

          <button 
            @click="goToCheckout"
            :disabled="currentShopCartItems.length === 0"
            class="w-full py-3 px-4 rounded-xl text-white font-bold text-sm bg-gradient-to-r from-orange-500 to-amber-600 hover:from-orange-600 hover:to-amber-700 active:scale-95 transition-all shadow-md shadow-orange-500/10 flex justify-center items-center disabled:opacity-50 disabled:pointer-events-none"
          >
            立即结算
          </button>
        </div>
      </aside>

    </div>
  </div>
</template>
