/**
 * vue-cropper@next（1.x）的官方 typings 会 import 回包内未编译的 .vue/.ts 源码，
 * vue-tsc 跟进去报 TS7016。tsconfig.app.json 用 paths 把本模块映射到这里做类型声明，
 * 运行时 vite 仍按 package.json main 解析到 dist/vue-cropper.es.js，两边互不影响。
 */
import type { DefineComponent } from 'vue'

declare const VueCropper: DefineComponent<Record<string, unknown>, Record<string, unknown>, unknown>

export { VueCropper }
