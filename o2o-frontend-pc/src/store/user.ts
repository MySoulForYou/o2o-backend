import { defineStore } from 'pinia'

interface Location {
  latitude: number
  longitude: number
  name: string
}

interface UserState {
  token: string | null
  userInfo: any | null
  currentLocation: Location
}

export const useUserStore = defineStore('user', {
  state: (): UserState => ({
    token: localStorage.getItem('o2o_token'),
    userInfo: JSON.parse(localStorage.getItem('o2o_user') || 'null'),
    currentLocation: {
      latitude: 31.22997, // 上海静安区中心经度纬度
      longitude: 121.45529,
      name: '上海市 静安区南京西路123号'
    }
  }),
  actions: {
    setToken(token: string) {
      this.token = token
      localStorage.setItem('o2o_token', token)
    },
    setUserInfo(info: any) {
      this.userInfo = info
      localStorage.setItem('o2o_user', JSON.stringify(info))
    },
    setLocation(loc: Location) {
      this.currentLocation = loc
    },
    logout() {
      this.token = null
      this.userInfo = null
      localStorage.removeItem('o2o_token')
      localStorage.removeItem('o2o_user')
    }
  },
  persist: true
})
