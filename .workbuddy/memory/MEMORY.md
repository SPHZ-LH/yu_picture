# 云图库前端 · 项目长期约定

## 项目定位
`yu-picture-frontend` 是 `yu-picture-backend`（Spring Boot 单体）的配套前端，UI 语言为中文。

## 技术栈（已锁定）
- Vue 3.5 + Vite 5 + TypeScript 5.7 + Pinia + Vue Router 4
- ant-design-vue 4.x（中文 locale 为默认，无需额外配置）
- echarts 6（仅空间分析页使用，已做 manualChunks 拆分）
- dayjs（日期格式化，`src/utils/index.ts` 中封装）

## 必须遵守的约定

### 1. 请求层
- 一律通过 `src/request.ts` 的 `request` / `request.upload` 发请求，不要在页面里直接用 axios。
- 响应拦截器已经把 `BaseResponse` 解包，调用方直接判断 `res.code === 0`，**不要写 `res.data.code`**。
- `baseURL = '/api'`，开发环境靠 Vite proxy 转发到 `http://localhost:8080`，**代理不做 rewrite**。
- `/user/get/login` 传 `skipErrorMessage: true`，未登录时静默返回，避免首屏弹错。

### 2. 类型
- 所有后端类型集中在 `src/api/typings.d.ts` 的全局 `API` 命名空间，不要散落到各文件。
- 后端 `JsonConfig` 把 `Long` 全局序列化为 **String**：id / userId / spaceId / picSize / maxSize / totalSize / totalCount / total 等一律按 `string` 声明。

### 3. 常量
- 审核状态 → `src/constant/picture.ts`
- 空间等级 / 类型 / 配额 → `src/constant/space.ts`
- 不要在各页面重复写魔法数字。

### 4. 权限
- 路由守卫统一在 `src/access.ts`（`requiresLogin` / `requiresAdmin` 写在路由 `meta` 上，不要在页面里各写一遍）。
- 登录态是 Session Cookie，`withCredentials: true` 必须保持开启。

### 5. 已知后端限制（前端已做规避，改动前先确认后端是否补齐）
| 限制 | 前端应对 |
|---|---|
| `PictureVO` 无 `reviewStatus` | 审核状态只在管理端表格展示（走 `/picture/list/page` 拿实体） |
| `/picture/list/page/vo` 传空 spaceId 强制 `reviewStatus=PASS` | 管理端改用 `/picture/list/page` |
| `pageSize` 上限 20（picture/space 分页） | 前端分页选项最大 20 |
| 分析接口 `startTime/endTime` 无 `@DateTimeFormat` | 不传时间范围，粒度只用 `granularity` |
| 无「以图搜图」接口 | `SearchPicturePage` 跳转外部识图服务，并在页面明确说明 |
| 无批量编辑图片接口 | 批量操作（审核）在前端并发调用单条接口 |

## 本地启动
- 后端：`yu-picture-backend` 需运行在 8080；**改动后端后必须重启进程**，否则新接口仍会 404。
- 前端：`npm run dev` → http://localhost:5173
- 校验：`npm run type-check`（必须零错误）、`npx vite build`

## ⚠️ 后端进程陷阱：WorkBuddy worktree

后端仓库存在 WorkBuddy 自动创建的 git worktree：
`C:\Users\SPHZ\WorkBuddy\Worktrees\yu-picture-backend\main-f92bec67`（分支 `workbuddy/main-f92bec67`）

**这个 worktree 停在旧提交，不含空间模块**。如果 8080 上的进程是从这个 worktree 的 `target/classes` 启动的，那么：
- `/api/user/**`、`/api/picture/**` 正常（那些模块旧提交里就有）
- `/api/space/**`、`/api/space/analyze/**` 全部 404

**排查手法**：看返回体形态。容器级 404 返回 Spring Boot 默认结构
`{"timestamp":...,"status":404,"error":"Not Found","path":"..."}`；
而应用业务错误一定被包成 `{"code":...,"data":...,"message":"..."}`。
看到前者说明**路由根本没注册**，不是业务逻辑问题。

**定位命令**
```bash
netstat -ano | grep LISTENING | grep :8080          # 拿 PID
# 再查该 PID 的 CommandLine，重点看 -cp 指向哪个 target/classes
git -C <主仓库> worktree list                        # 确认 worktree 存在
git -C <worktree> log -3 --format="%h %ad %s" --date=short
```

**结论**：启动后端一定要确认 classpath 指向**主仓库**的 `target/classes`，不要复用 worktree 的产物。发现是 worktree 的，结束该进程后从主仓库重启。
