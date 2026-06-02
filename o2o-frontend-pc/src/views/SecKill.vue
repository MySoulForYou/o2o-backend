<script setup lang="ts">
import { ref, onUnmounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { Timer, Warning } from '@element-plus/icons-vue'
import seckillBanner from '@/assets/seckill_banner.png'

const router = useRouter()

const countTime = ref(600) // 10分钟倒计时 (秒)
const buttonText = ref('立即抢购')
const isSecKillStarted = ref(true)
const isQueuing = ref(false)
const queueProgress = ref(0)
const queueStatusText = ref('正在接入高并发排队网关...')

// 格式化秒数为 00:00 格式
const formatTime = (time: number) => {
  const m = Math.floor(time / 60)
  const s = time % 60
  return `${m.toString().padStart(2, '0')}:${s.toString().padStart(2, '0')}`
}

const timerId = setInterval(() => {
  if (countTime.value > 0) {
    countTime.value--
  } else {
    clearInterval(timerId)
  }
}, 1000)

onUnmounted(() => {
  clearInterval(timerId)
})

// 模拟秒杀高并发排队与状态轮询
const handleSecKill = () => {
  isQueuing.value = true
  queueProgress.value = 10
  queueStatusText.value = '正在接入高并发秒杀网关...'

  // 第一步：网关排队 (800ms)
  setTimeout(() => {
    queueProgress.value = 35
    queueStatusText.value = '安全防护启动，正在核验行为指纹安全度...'
  }, 800)

  // 第二步：扣减虚拟库存 (1600ms)
  setTimeout(() => {
    queueProgress.value = 70
    queueStatusText.value = '正在扣减 Redis 虚拟库存 (执行 Lua 预扣)...'
  }, 1600)

  // 第三步：异步订单落库 (2400ms)
  setTimeout(() => {
    queueProgress.value = 90
    queueStatusText.value = '虚拟库存扣减成功！正在投递 MQ 进行异步建单...'
  }, 2400)

  // 第四步：完成跳转 (3200ms)
  setTimeout(() => {
    queueProgress.value = 100
    queueStatusText.value = '抢单成功！正在为您生成秒杀订单...'
  }, 3200)

  // 最终成功
  setTimeout(() => {
    isQueuing.value = false
    ElMessage.success('恭喜！秒杀抢单成功，已为您锁定库存！')
    
    // 写入一个虚拟的秒杀商品到本地购物车，并直接勾选
    const mockSecKillItem = {
      goodsId: 999,
      goodsName: '外婆私房菜 ￥100无门槛代金券 (秒杀专享)',
      price: 1.0,
      imgUrl: 'https://images.unsplash.com/photo-1540189549336-e6e99c3679fe?auto=format&fit=crop&w=250&q=80',
      quantity: 1,
      shopId: 1,
      shopName: '品味法式烘焙',
      checked: true
    }
    localStorage.setItem('o2o_local_cart', JSON.stringify([mockSecKillItem]))
    
    // 跳转到结算页
    router.push('/checkout')
  }, 3800)
}
</script>

<template>
  <div class="max-w-4xl mx-auto px-6 py-8 font-sans">
    <h2 class="text-2xl font-bold text-slate-800 mb-6 flex items-center">
      <span class="inline-block w-1.5 h-6 bg-orange-500 rounded-full mr-2"></span>
      1 元超值秒杀专区
    </h2>

    <div class="grid grid-cols-1 lg:grid-cols-3 gap-8">
      <!-- 左侧主海报栏 (占 2/3) -->
      <div class="lg:col-span-2 space-y-6">
        <!-- 宣传大图 -->
        <div class="rounded-[24px] overflow-hidden border border-slate-200/60 shadow-xl shadow-slate-100/40 bg-white">
          <div class="h-[280px] overflow-hidden relative">
            <img :src="seckillBanner" class="w-full h-full object-cover animate-fade-in" alt="SecKill Poster" />
          </div>
          <div class="p-6 space-y-4">
            <div class="flex items-center justify-between">
              <span class="text-xs bg-red-50 text-red-600 border border-red-200 px-2 py-0.5 rounded-full font-bold">爆款直降</span>
              <div class="flex items-center space-x-2 text-sm text-slate-500 font-medium">
                <el-icon class="text-orange-500"><Timer /></el-icon>
                <span>秒杀倒计时：</span>
                <span class="font-mono text-red-600 font-extrabold text-base">{{ formatTime(countTime) }}</span>
              </div>
            </div>
            
            <h3 class="text-xl font-extrabold text-slate-800">外婆私房菜 ￥100 无门槛同城通用代金券</h3>
            <p class="text-sm text-slate-500 leading-relaxed">
              本券适用于“外婆私房菜”静安区实体店，所有餐品通用，无任何使用门槛。抢购成功后将自动添加至结算清单，请于 5 分钟内完成付款，否则库存将被网关自动回收。
            </p>

            <div class="flex items-center justify-between border-t border-slate-50 pt-4">
              <div class="space-x-2">
                <span class="text-2xl font-black text-red-600">￥ 1.00</span>
                <span class="text-xs text-slate-400 line-through">门市价 ￥100.00</span>
              </div>
              <span class="text-xs text-slate-400">仅剩 5 件 | 限抢 1 张</span>
            </div>
          </div>
        </div>
      </div>

      <!-- 右侧限流排队指引与规则 (占 1/3) -->
      <div class="space-y-6">
        <!-- 限流规则卡片 -->
        <div class="bg-white rounded-2xl p-6 border border-slate-200/60 shadow-sm space-y-4">
          <h4 class="text-sm font-bold text-slate-800 flex items-center">
            <el-icon class="mr-1.5 text-orange-500"><Warning /></el-icon>高并发秒杀防刷规则
          </h4>
          <ul class="text-xs text-slate-500 space-y-3 leading-relaxed">
            <li>1. 每位用户（按手机号及设备指纹联合判定）限抢 1 张代金券；</li>
            <li>2. 系统内置了 **Redis Lua 脚本** 进行底层库存控制，防止高并发超卖；</li>
            <li>3. 提交后系统会接入 **RabbitMQ 消息队列** 排队落库，请耐心等待排队核验；</li>
            <li>4. 恶意抢单脚本或黄牛行为将被风控拦截并回滚库存。</li>
          </ul>

          <button 
            @click="handleSecKill"
            :disabled="!isSecKillStarted"
            class="w-full mt-4 py-3.5 px-4 rounded-xl text-white font-bold text-sm bg-gradient-to-r from-red-500 to-orange-600 hover:from-red-600 hover:to-orange-700 active:scale-95 transition-all shadow-md shadow-red-500/10 flex justify-center items-center"
          >
            {{ buttonText }}
          </button>
        </div>
      </div>
    </div>

    <!-- PC 网页端高并发秒杀排队毛玻璃遮罩 (Queueing Modal) -->
    <div 
      v-if="isQueuing" 
      class="fixed inset-0 z-50 backdrop-blur-md bg-slate-900/60 flex items-center justify-center transition-opacity"
    >
      <div class="bg-white/95 rounded-[32px] p-8 w-[360px] text-center shadow-2xl border border-white/40 space-y-6 animate-scale-in">
        <h4 class="text-base font-extrabold text-slate-800">秒杀抢券排队中</h4>
        
        <!-- 旋转进度环 -->
        <div class="relative w-28 h-28 mx-auto flex items-center justify-center">
          <el-progress 
            type="circle" 
            :percentage="queueProgress"
            status="warning"
            :stroke-width="8"
            :width="112"
          />
        </div>

        <p class="text-xs text-slate-500 animate-pulse font-medium">
          {{ queueStatusText }}
        </p>

        <div class="text-[10px] text-slate-400 bg-slate-50 py-2 px-4 rounded-xl">
          Lua 预扣减已锁定，请勿刷新浏览器页面
        </div>
      </div>
    </div>

  </div>
</template>

<style>
/* 弹窗及淡入动画 */
@keyframes fadeIn {
  from { opacity: 0; }
  to { opacity: 1; }
}
@keyframes scaleIn {
  from { transform: scale(0.95); opacity: 0; }
  to { transform: scale(1); opacity: 1; }
}
.animate-fade-in {
  animation: fadeIn 0.4s ease-out forwards;
}
.animate-scale-in {
  animation: scaleIn 0.3s cubic-bezier(0.16, 1, 0.3, 1) forwards;
}
</style>
