import 'ant-design-vue/dist/reset.css'
import './assets/main.css'
import './styles/yutu.css'

import { createApp } from 'vue'
import { createPinia } from 'pinia'

import App from './App.vue'
import router from './router'
// 注册全局路由守卫（权限校验），必须在 router 之后引入
import './access'

const app = createApp(App)

app.use(createPinia())
app.use(router)
// ant-design-vue 组件由 unplugin-vue-components 按需自动引入（见 vite.config.ts），
// 不再 app.use(Antd) 全量注册，避免首屏打包全部组件

app.mount('#app')
