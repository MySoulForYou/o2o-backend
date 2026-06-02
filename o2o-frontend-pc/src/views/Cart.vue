<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Delete } from '@element-plus/icons-vue'
import axios from 'axios'

const router = useRouter()
const cartList = ref<any[]>([])

const loadCart = () => {
  const data = localStorage.getItem('o2o_local_cart')
  cartList.value = data ? JSON.parse(data) : []
}

const saveCart = () => {
  localStorage.setItem('o2o_local_cart', JSON.stringify(cartList.value))
}

// 尝试同步后端购物车 (联调)
const syncBackendCart = async () => {
  try {
    const res = await axios.get('/api/cart/list')
    if (res.data && res.data.code === 200) {
      // 如果后端有数据，我们可以采用后端数据。
      // 为演示方便，本地优先做存储，联调时以此为基础。
    }
  } catch (err) {
    console.log('Cart service offline, running in mock/local mode')
  }
}

// 按照店铺分组聚合购物车商品
const groupedCart = computed(() => {
  const groups: Record<number, { shopId: number; shopName: string; items: any[] }> = {}
  cartList.value.forEach((item) => {
    if (!groups[item.shopId]) {
      groups[item.shopId] = {
        shopId: item.shopId,
        shopName: item.shopName || '同城商家',
        items: []
      }
    }
    groups[item.shopId].items.push(item)
  })
  return Object.values(groups)
})

// 改变商品数量
const handleUpdateQuantity = (item: any, delta: number) => {
  item.quantity += delta
  if (item.quantity <= 0) {
    item.quantity = 1
  }
  saveCart()
}

// 勾选状态切换
const handleToggleChecked = (item: any) => {
  item.checked = !item.checked
  saveCart()
}

// 店铺全选或全不选
const handleToggleShopAll = (group: any, event: any) => {
  const isChecked = event
  group.items.forEach((item: any) => {
    item.checked = isChecked
  })
  saveCart()
}

// 判断该店铺的所有商品是否全部勾选
const isShopAllChecked = (group: any) => {
  return group.items.every((item: any) => item.checked)
}

// 判断该店铺是否有商品被部分勾选
const isShopIndeterminate = (group: any) => {
  const hasChecked = group.items.some((item: any) => item.checked)
  const allChecked = group.items.every((item: any) => item.checked)
  return hasChecked && !allChecked
}

// 全选
const isAllChecked = computed({
  get() {
    return cartList.value.length > 0 && cartList.value.every((item) => item.checked)
  },
  set(val: boolean) {
    cartList.value.forEach((item) => {
      item.checked = val
    })
    saveCart()
  }
})

// 删除单品
const handleDeleteItem = (item: any) => {
  ElMessageBox.confirm(`确定要从购物车删除 ${item.goodsName} 吗？`, '提示', {
    confirmButtonText: '确定',
    cancelButtonText: '取消',
    type: 'warning'
  }).then(() => {
    cartList.value = cartList.value.filter(
      (i) => !(i.goodsId === item.goodsId && i.shopId === item.shopId)
    )
    saveCart()
    ElMessage.success('商品已移出购物车')
  })
}

// 选中商品的总件数
const checkedCount = computed(() => {
  return cartList.value.filter((item) => item.checked).reduce((sum, item) => sum + item.quantity, 0)
})

// 选中商品的总金额
const checkedTotal = computed(() => {
  return cartList.value
    .filter((item) => item.checked)
    .reduce((sum, item) => sum + item.price * item.quantity, 0)
})

// 去下单结算
const handleCheckout = () => {
  const checkedItems = cartList.value.filter((item) => item.checked)
  if (checkedItems.length === 0) {
    ElMessage.warning('请先勾选需要结算的商品')
    return
  }
  router.push('/checkout')
}

onMounted(() => {
  loadCart()
  syncBackendCart()
})
</script>

<template>
  <div class="max-w-4xl mx-auto px-6 py-8 font-sans">
    <h2 class="text-2xl font-bold text-slate-800 mb-6 flex items-center">
      <span class="inline-block w-1.5 h-6 bg-orange-500 rounded-full mr-2"></span>
      我的购物车
    </h2>

    <div v-if="cartList.length === 0" class="bg-white rounded-2xl p-16 text-center border border-slate-200/60 shadow-sm space-y-4">
      <p class="text-slate-400 text-sm">购物车空空如也，快去附近店铺看看吧！</p>
      <router-link 
        to="/home" 
        class="inline-block py-2.5 px-6 rounded-xl text-white font-bold text-sm bg-gradient-to-r from-orange-500 to-amber-600 shadow-md shadow-orange-500/10 active:scale-95 transition-all"
      >
        去逛逛
      </router-link>
    </div>

    <div v-else class="space-y-6">
      
      <!-- 店铺分组卡片 -->
      <div 
        v-for="group in groupedCart" 
        :key="group.shopId"
        class="bg-white rounded-2xl border border-slate-200/60 shadow-sm overflow-hidden"
      >
        <!-- 店铺 Header -->
        <div class="bg-[#FAFAFA] border-b border-slate-100 px-6 py-4 flex items-center justify-between">
          <div class="flex items-center space-x-3">
            <el-checkbox 
              :model-value="isShopAllChecked(group)" 
              :indeterminate="isShopIndeterminate(group)"
              @change="handleToggleShopAll(group, $event)"
            />
            <span class="font-bold text-slate-800 text-sm flex items-center">
              🏪 {{ group.shopName }}
            </span>
          </div>
          <router-link :to="`/shop/${group.shopId}`" class="text-xs text-orange-500 hover:text-orange-600 font-medium">进入店铺点单 &gt;</router-link>
        </div>

        <!-- 内部商品列表 -->
        <ul class="divide-y divide-slate-100 px-6">
          <li 
            v-for="item in group.items" 
            :key="item.goodsId"
            class="py-5 flex items-center justify-between"
          >
            <!-- 勾选与图文 -->
            <div class="flex items-center space-x-4 flex-1">
              <el-checkbox 
                :model-value="item.checked"
                @change="handleToggleChecked(item)"
              />
              <div class="w-16 h-16 rounded-xl overflow-hidden bg-slate-50 border border-slate-100 flex-shrink-0">
                <img :src="item.imgUrl" class="w-full h-full object-cover" alt="Product" />
              </div>
              <div class="space-y-1 pr-4">
                <h4 class="font-bold text-slate-800 text-sm line-clamp-1">{{ item.goodsName }}</h4>
                <p class="text-xs text-slate-400">单价 ￥{{ item.price.toFixed(2) }}</p>
              </div>
            </div>

            <!-- 数量微调 & 删除 -->
            <div class="flex items-center space-x-8">
              <!-- 计数器 -->
              <div class="flex items-center space-x-2 border border-slate-200 rounded-full px-2.5 py-0.5">
                <button 
                  @click="handleUpdateQuantity(item, -1)"
                  class="text-slate-500 hover:text-orange-500 font-bold w-4 text-center"
                >-</button>
                <span class="font-mono text-sm text-slate-800 font-bold w-8 text-center">{{ item.quantity }}</span>
                <button 
                  @click="handleUpdateQuantity(item, 1)"
                  class="text-slate-500 hover:text-orange-500 font-bold w-4 text-center"
                >+</button>
              </div>

              <!-- 小计 -->
              <span class="text-sm font-extrabold text-orange-600 w-20 text-right">
                ￥{{ (item.price * item.quantity).toFixed(2) }}
              </span>

              <!-- 删除按钮 -->
              <button 
                @click="handleDeleteItem(item)"
                class="p-1 text-slate-400 hover:text-red-500 transition-colors"
              >
                <el-icon :size="16"><Delete /></el-icon>
              </button>
            </div>
          </li>
        </ul>
      </div>

      <!-- 底部固定汇总通栏 (Sticky Bill Panel) -->
      <div class="bg-white rounded-2xl border border-slate-200/60 p-6 shadow-xl shadow-slate-200/40 flex items-center justify-between sticky bottom-2 z-40">
        <div class="flex items-center space-x-6">
          <el-checkbox v-model="isAllChecked" label="全选" size="large" />
          <span class="text-xs text-slate-500">已选择 <strong class="text-orange-600 font-extrabold text-sm mx-1">{{ checkedCount }}</strong> 件商品</span>
        </div>

        <div class="flex items-center space-x-6">
          <div class="text-right">
            <span class="text-sm text-slate-500 font-medium">合计：</span>
            <span class="text-2xl font-extrabold text-orange-600">￥{{ checkedTotal.toFixed(2) }}</span>
          </div>

          <button 
            @click="handleCheckout"
            class="py-3 px-8 rounded-xl text-white font-bold text-sm bg-gradient-to-r from-orange-500 to-amber-600 shadow-md shadow-orange-500/10 hover:from-orange-600 hover:to-amber-700 active:scale-95 transition-all flex items-center"
          >
            去结算
          </button>
        </div>
      </div>

    </div>
  </div>
</template>
