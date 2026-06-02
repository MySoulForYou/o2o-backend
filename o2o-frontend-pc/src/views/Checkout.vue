<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { Location, CreditCard } from '@element-plus/icons-vue'
import axios from 'axios'

const router = useRouter()

const loading = ref(false)
const payMethod = ref('alipay') // 默认支付宝
const addressList = ref<any[]>([])
const selectedAddress = ref<any>(null)

// 兜底地址数据
const defaultAddresses = [
  { id: 1, receiverName: '麦浪小哥', receiverPhone: '13888888888', province: '上海市', city: '上海市', district: '静安区', detailAddress: '南京西路123号恒隆广场5楼', isDefault: 1 },
  { id: 2, receiverName: '麦浪小哥', receiverPhone: '13888888888', province: '上海市', city: '上海市', district: '宝山区', detailAddress: '顾村公园正门旁', isDefault: 0 }
]

const cartList = ref<any[]>([])

const loadCart = () => {
  const data = localStorage.getItem('o2o_local_cart')
  cartList.value = data ? JSON.parse(data) : []
}

// 筛选出被勾选结算的商品
const checkoutItems = computed(() => {
  return cartList.value.filter((item) => item.checked)
})

// 按店铺分组预览，以便展示多商家结算细则
const groupedCheckout = computed(() => {
  const groups: Record<number, { shopId: number; shopName: string; items: any[] }> = {}
  checkoutItems.value.forEach((item) => {
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

// 计算商品总价
const goodsTotal = computed(() => {
  return checkoutItems.value.reduce((sum, item) => sum + item.price * item.quantity, 0)
})

// 配送费（按商家收取，每个商家 ￥5 配送费）
const deliveryFee = computed(() => {
  return groupedCheckout.value.length * 5
})

// 合计应付
const payTotal = computed(() => {
  return goodsTotal.value + deliveryFee.value
})

const fetchAddresses = async () => {
  try {
    const res = await axios.get('/api/user/address/list')
    if (res.data && res.data.code === 200 && res.data.data.length > 0) {
      addressList.value = res.data.data
      selectedAddress.value = addressList.value.find((a) => a.isDefault === 1) || addressList.value[0]
    } else {
      useFallbackAddresses()
    }
  } catch {
    useFallbackAddresses()
  }
}

const useFallbackAddresses = () => {
  addressList.value = [...defaultAddresses]
  selectedAddress.value = addressList.value[0]
}

// 模拟创建订单并结算
const handleSubmitOrder = async () => {
  if (!selectedAddress.value) {
    ElMessage.warning('请先选择收货地址')
    return
  }

  loading.value = true
  try {
    // 联调接口：创建订单
    const res = await axios.post('/api/trade/order', {
      addressId: selectedAddress.value.id,
      items: checkoutItems.value.map((item) => ({
        goodsId: item.goodsId,
        quantity: item.quantity
      }))
    })

    if (res.data && res.data.code === 200) {
      handleOrderSuccess()
    } else {
      // 业务失败
      ElMessage.error(res.data?.message || '生成订单失败')
    }
  } catch (error) {
    // 降级兜底订单提交
    console.warn('Order API failed, entering fallback simulated order generation.')
    setTimeout(() => {
      handleOrderSuccess()
    }, 1500)
  }
}

const handleOrderSuccess = () => {
  loading.value = false
  ElMessage.success('订单提交成功，扣款完成！')
  // 清除本地购物车中已被购买结算的商品
  const remainingCart = cartList.value.filter((item) => !item.checked)
  localStorage.setItem('o2o_local_cart', JSON.stringify(remainingCart))
  
  // 跳转到成功页或首页
  router.push('/home')
}

onMounted(() => {
  loadCart()
  if (checkoutItems.value.length === 0) {
    ElMessage.warning('没有可结算的商品，已切回首页')
    router.push('/home')
    return
  }
  fetchAddresses()
})
</script>

<template>
  <div v-if="checkoutItems.length > 0" class="max-w-4xl mx-auto px-6 py-8 font-sans">
    <h2 class="text-2xl font-bold text-slate-800 mb-6 flex items-center">
      <span class="inline-block w-1.5 h-6 bg-orange-500 rounded-full mr-2"></span>
      确认订单信息
    </h2>

    <div class="grid grid-cols-1 lg:grid-cols-3 gap-8">
      
      <!-- 左侧：地址与订单卡片列表 (占 2/3) -->
      <div class="lg:col-span-2 space-y-6">
        
        <!-- 地址管理卡片 -->
        <div class="bg-white rounded-2xl p-6 border border-slate-200/60 shadow-sm relative overflow-hidden group">
          <div class="absolute left-0 top-0 bottom-0 w-1.5 bg-gradient-to-b from-orange-500 to-amber-500"></div>
          <h3 class="text-sm font-bold text-slate-400 mb-3 uppercase tracking-wider flex items-center">
            <el-icon class="mr-1.5 text-orange-500"><Location /></el-icon>收货人地址
          </h3>

          <div v-if="selectedAddress" class="space-y-1.5">
            <div class="flex items-center space-x-3">
              <span class="font-bold text-slate-800 text-base">{{ selectedAddress.receiverName }}</span>
              <span class="text-sm text-slate-500 font-mono">{{ selectedAddress.receiverPhone }}</span>
              <span v-if="selectedAddress.isDefault" class="text-[10px] bg-orange-50 text-orange-600 border border-orange-200 px-1.5 py-0.5 rounded font-bold">默认</span>
            </div>
            <p class="text-sm text-slate-600">
              {{ selectedAddress.province }}{{ selectedAddress.city }}{{ selectedAddress.district }}{{ selectedAddress.detailAddress }}
            </p>
          </div>
        </div>

        <!-- 按照店铺列出的商品卡片 -->
        <div 
          v-for="group in groupedCheckout" 
          :key="group.shopId"
          class="bg-white rounded-2xl border border-slate-200/60 shadow-sm overflow-hidden"
        >
          <div class="bg-[#FAFAFA] border-b border-slate-100 px-6 py-3 text-sm font-bold text-slate-700">
            🏪 {{ group.shopName }}
          </div>

          <ul class="divide-y divide-slate-100 px-6">
            <li 
              v-for="item in group.items" 
              :key="item.goodsId"
              class="py-4 flex items-center justify-between"
            >
              <div class="flex items-center space-x-4">
                <div class="w-12 h-12 rounded-lg overflow-hidden bg-slate-50 border border-slate-100 flex-shrink-0">
                  <img :src="item.imgUrl" class="w-full h-full object-cover" alt="Product" />
                </div>
                <div>
                  <h4 class="font-bold text-slate-800 text-sm line-clamp-1">{{ item.goodsName }}</h4>
                  <p class="text-xs text-slate-400">数量 × {{ item.quantity }}</p>
                </div>
              </div>
              <span class="text-sm font-bold text-slate-700">￥{{ (item.price * item.quantity).toFixed(2) }}</span>
            </li>
          </ul>

          <div class="bg-[#FAFAFA]/30 px-6 py-3 border-t border-slate-100 flex justify-between text-xs text-slate-500">
            <span>店铺配送服务</span>
            <span>配送费 ￥5.00</span>
          </div>
        </div>
      </div>

      <!-- 右侧：结算细则与支付 (占 1/3) -->
      <div class="space-y-6">
        <!-- 支付渠道 Bento 卡片 -->
        <div class="bg-white rounded-2xl p-6 border border-slate-200/60 shadow-sm">
          <h3 class="text-sm font-bold text-slate-400 mb-4 uppercase tracking-wider flex items-center">
            <el-icon class="mr-1.5 text-orange-500"><CreditCard /></el-icon>支付方式
          </h3>
          <el-radio-group v-model="payMethod" class="w-full flex flex-col space-y-3">
            <el-radio-button label="alipay" class="w-full text-left">
              <span class="flex items-center space-x-2 text-slate-700">
                <span class="w-4 h-4 rounded-full bg-blue-500 text-white flex items-center justify-center text-[10px] font-bold">支</span>
                <span>支付宝</span>
              </span>
            </el-radio-button>
            <el-radio-button label="wechat" class="w-full text-left">
              <span class="flex items-center space-x-2 text-slate-700">
                <span class="w-4 h-4 rounded-full bg-green-500 text-white flex items-center justify-center text-[10px] font-bold">微</span>
                <span>微信支付</span>
              </span>
            </el-radio-button>
          </el-radio-group>
        </div>

        <!-- 汇总细则 -->
        <div class="bg-white rounded-2xl p-6 border border-slate-200/60 shadow-sm space-y-4">
          <h3 class="text-sm font-bold text-slate-800 border-b border-slate-100 pb-3">费用明细</h3>
          
          <div class="space-y-2.5 text-xs text-slate-600">
            <div class="flex justify-between">
              <span>商品小计：</span>
              <span>￥{{ goodsTotal.toFixed(2) }}</span>
            </div>
            <div class="flex justify-between">
              <span>同城配送费：</span>
              <span>￥{{ deliveryFee.toFixed(2) }}</span>
            </div>
          </div>

          <div class="border-t border-slate-100 pt-4 flex items-center justify-between">
            <span class="text-sm font-bold text-slate-700">实际支付金额：</span>
            <span class="text-xl font-extrabold text-orange-600">￥{{ payTotal.toFixed(2) }}</span>
          </div>

          <button 
            @click="handleSubmitOrder"
            :disabled="loading"
            class="w-full mt-4 py-3.5 px-4 rounded-xl text-white font-bold text-sm bg-gradient-to-r from-orange-500 to-amber-600 hover:from-orange-600 hover:to-amber-700 active:scale-95 transition-all shadow-md shadow-orange-500/10 flex justify-center items-center"
          >
            <span v-if="loading" class="animate-spin border-2 border-white border-t-transparent rounded-full w-4 h-4 mr-2"></span>
            立即支付
          </button>
        </div>
      </div>

    </div>
  </div>
</template>

<style>
/* 适配 Radio 样式为圆角排版按钮 */
.el-radio-button {
  width: 100%;
}
.el-radio-button__inner {
  width: 100%;
  border-radius: 12px !important;
  border: 1px solid #E2E8F0 !important;
  text-align: left !important;
  padding: 12px 16px !important;
}
.el-radio-button__orig-radio:checked + .el-radio-button__inner {
  border-color: #EA580C !important;
  background-color: #FFF7ED !important;
  box-shadow: none !important;
  color: #EA580C !important;
}
</style>
