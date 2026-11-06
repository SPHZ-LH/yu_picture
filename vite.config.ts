import { fileURLToPath, URL } from 'node:url'

import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'
import Components from 'unplugin-vue-components/vite'
import { AntDesignVueResolver } from 'unplugin-vue-components/resolvers'

// https://vite.dev/config/
export default defineConfig({
  plugins: [
    vue(),
    // ant-design-vue 按需自动引入：模板里的 <a-xxx> 组件在构建时才解析 import，
    // 替代 main.ts 的 app.use(Antd) 全量注册，首屏不必下载全部组件
    Components({
      resolvers: [
        // v4 组件样式走 CSS-in-JS 运行时注入，无需引入样式文件
        AntDesignVueResolver({ importStyle: false, resolveIcons: true }),
      ],
      dts: 'src/components.d.ts',
    }),
  ],
  resolve: {
    alias: {
      '@': fileURLToPath(new URL('./src', import.meta.url)),
    },
  },
  server: {
    port: 5173,
    open: false,
    proxy: {
      /**
       * 后端 context-path 本身就是 /api，因此这里不做 rewrite，
       * /api/picture/list/page/vo -> http://localhost:8080/api/picture/list/page/vo
       * 走代理后前后端同源，Session Cookie（user_login）可正常携带。
       */
      '/api': {
        target: 'http://localhost:8080',
        changeOrigin: true,
      },
      /**
       * 图片编辑器（vue-cropper）要把图片画进 canvas 读像素，还会用 XHR 拉
       * arraybuffer 解析 EXIF：跨域直连 COS 的请求受 CORS 配置、浏览器缓存与
       * 本地代理环境影响，容易静默失败。编辑页预览统一改走 /cos-file 同源代理；
       * 生产环境需在 nginx 配置等价转发：
       *   location /cos-file/ { proxy_pass https://<bucket>.cos.<region>.myqcloud.com/; }
       */
      '/cos-file': {
        target: 'https://sphz-1390064104.cos.ap-guangzhou.myqcloud.com',
        changeOrigin: true,
        rewrite: (path) => path.replace(/^\/cos-file/, ''),
      },
    },
  },
  build: {
    chunkSizeWarningLimit: 1500,
    rollupOptions: {
      output: {
        // 拆分体积较大的第三方库，避免主包过大。
        // 注意必须用函数式写法：对象式写法会把包的入口模块整个强制进 chunk，
        // 使 ant-design-vue 的 tree-shaking 失效（整库约 1MB 全部进首屏）
        manualChunks(id) {
          if (id.includes('node_modules')) {
            if (id.includes('echarts') || id.includes('zrender')) return 'vendor-echarts'
            if (id.includes('ant-design-vue') || id.includes('@ant-design/icons-vue')) {
              return 'vendor-antd'
            }
            if (id.includes('vue') || id.includes('pinia')) return 'vendor-vue'
          }
        },
      },
    },
  },
})
