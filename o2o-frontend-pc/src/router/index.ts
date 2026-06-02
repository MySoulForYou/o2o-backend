import { createRouter, createWebHashHistory } from 'vue-router'
import Home from '@/views/Home.vue'
import ShopDetail from '@/views/ShopDetail.vue'
import Cart from '@/views/Cart.vue'
import Checkout from '@/views/Checkout.vue'
import SecKill from '@/views/SecKill.vue'
import Login from '@/views/Login.vue'

const routes = [
  {
    path: '/',
    redirect: '/home'
  },
  {
    path: '/home',
    name: 'Home',
    component: Home
  },
  {
    path: '/shop/:id',
    name: 'ShopDetail',
    component: ShopDetail
  },
  {
    path: '/cart',
    name: 'Cart',
    component: Cart,
    meta: { requiresAuth: true }
  },
  {
    path: '/checkout',
    name: 'Checkout',
    component: Checkout,
    meta: { requiresAuth: true }
  },
  {
    path: '/seckill',
    name: 'SecKill',
    component: SecKill,
    meta: { requiresAuth: true }
  },
  {
    path: '/login',
    name: 'Login',
    component: Login
  }
]

const router = createRouter({
  history: createWebHashHistory(),
  routes
})

// 路由拦截器
router.beforeEach((to, _from, next) => {
  const token = localStorage.getItem('o2o_token')
  if (to.meta.requiresAuth && !token) {
    next('/login')
  } else {
    next()
  }
})

export default router
