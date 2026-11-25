import { createStore } from 'vuex'

// 创建一个新的 store 实例
const store = createStore({
  state() {
    return {
      // 展示用的短链域名，可在 .env.development 里用 VITE_SHORT_DOMAIN 覆盖
      domain: import.meta.env.VITE_SHORT_DOMAIN || '127.0.0.1:8001'
    }
  }
})

export default store
