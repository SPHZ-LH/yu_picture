import { createRouter, createWebHistory } from 'vue-router'

const router = createRouter({
  history: createWebHistory(import.meta.env.BASE_URL),
  routes: [
    {
      path: '/',
      component: () => import('@/layouts/BasicLayout.vue'),
      children: [
        {
          path: '',
          name: 'home',
          component: () => import('@/pages/HomePage.vue'),
          meta: { title: '屿图 YUTU — 发现好图' },
        },
        {
          path: 'topics',
          name: '专题',
          component: () => import('@/pages/TopicsPage.vue'),
          meta: { title: '专题' },
        },
        {
          path: 'ranking',
          name: '榜单',
          component: () => import('@/pages/RankingPage.vue'),
          meta: { title: '热门下载榜' },
        },
        {
          path: 'creators',
          name: '创作者',
          component: () => import('@/pages/CreatorsPage.vue'),
          meta: { title: '创作者' },
        },
        {
          path: 'picture/:id',
          name: '图片详情',
          component: () => import('@/pages/PictureDetailPage.vue'),
          props: true,
          meta: { title: '图片详情' },
        },
        {
          path: 'user_profile/:id',
          name: '用户主页',
          component: () => import('@/pages/UserProfilePage.vue'),
          props: true,
          meta: { title: '用户主页' },
        },
        {
          path: 'video/:id',
          name: '视频详情',
          component: () => import('@/pages/VideoDetailPage.vue'),
          props: true,
          meta: { title: '动态壁纸详情' },
        },
        {
          path: 'search_picture',
          name: '相似图搜索',
          component: () => import('@/pages/SearchPicturePage.vue'),
          meta: { title: '相似图搜索' },
        },
        {
          path: 'add_picture',
          name: '创建图片',
          component: () => import('@/pages/AddPicturePage.vue'),
          meta: { title: '创建图片', requiresLogin: true },
        },
        {
          path: 'add_video',
          name: '上传动态壁纸',
          component: () => import('@/pages/AddVideoPage.vue'),
          meta: { title: '上传动态壁纸', requiresLogin: true },
        },
        {
          path: 'edit_picture/:id',
          name: '编辑图片',
          component: () => import('@/pages/EditPicturePage.vue'),
          props: true,
          meta: { title: '编辑图片', requiresLogin: true },
        },
        {
          path: 'picture/expand/:id',
          name: '画面扩展',
          component: () => import('@/pages/ExpandPicturePage.vue'),
          props: true,
          meta: { title: '画面扩展', requiresLogin: true },
        },
        {
          path: 'add_picture/batch',
          name: '批量创建图片',
          component: () => import('@/pages/AddPictureBatchPage.vue'),
          meta: { title: '批量创建图片', requiresAdmin: true },
        },
        {
          path: 'my_space',
          name: '团队空间',
          component: () => import('@/pages/MySpacePage.vue'),
          meta: { title: '团队空间', requiresLogin: true },
        },
        {
          // 私有空间每人恰好一个，这里只做解析后直达空间详情，不是一个列表页
          path: 'my_private_space',
          name: '私有空间',
          component: () => import('@/pages/MyPrivateSpacePage.vue'),
          meta: { title: '私有空间', requiresLogin: true },
        },
        {
          // 收藏已并入私有空间页（内容区的「我的收藏」段），此路由只为旧链接兜底
          path: 'my_collect',
          redirect: '/my_private_space?view=collect',
        },
        {
          path: 'user_settings',
          name: '账号设置',
          component: () => import('@/pages/UserSettingsPage.vue'),
          meta: { title: '账号设置', requiresLogin: true },
        },
        {
          path: 'add_space',
          name: '创建团队空间',
          component: () => import('@/pages/AddSpacePage.vue'),
          meta: { title: '创建团队空间', requiresLogin: true },
        },
        {
          path: 'space/:id',
          name: '空间详情',
          component: () => import('@/pages/SpaceDetailPage.vue'),
          props: true,
          meta: { title: '空间详情', requiresLogin: true },
        },
        {
          path: 'space/:id/analyze',
          name: '空间分析',
          component: () => import('@/pages/SpaceAnalyzePage.vue'),
          props: true,
          meta: { title: '空间分析', requiresLogin: true },
        },
        {
          path: 'admin/userManage',
          name: '用户管理',
          component: () => import('@/pages/admin/UserManagePage.vue'),
          meta: { title: '用户管理', requiresAdmin: true },
        },
        {
          path: 'admin/pictureManage',
          name: '图片管理',
          component: () => import('@/pages/admin/PictureManagePage.vue'),
          meta: { title: '图片管理', requiresAdmin: true },
        },
        {
          path: 'admin/videoManage',
          name: '视频管理',
          component: () => import('@/pages/admin/VideoManagePage.vue'),
          meta: { title: '视频管理', requiresAdmin: true },
        },
        {
          path: 'admin/spaceManage',
          name: '空间管理',
          component: () => import('@/pages/admin/SpaceManagePage.vue'),
          meta: { title: '空间管理', requiresAdmin: true },
        },
        {
          path: 'admin/analyze',
          name: '全空间分析',
          component: () => import('@/pages/admin/SpaceAnalyzeAllPage.vue'),
          meta: { title: '全空间分析', requiresAdmin: true },
        },
        {
          path: 'admin/cacheManage',
          name: '缓存运维',
          component: () => import('@/pages/admin/CacheManagePage.vue'),
          meta: { title: '缓存运维', requiresAdmin: true },
        },
      ],
    },
    {
      path: '/user',
      component: () => import('@/layouts/UserLayout.vue'),
      children: [
        {
          path: 'login',
          name: '用户登录',
          component: () => import('@/pages/user/UserLoginPage.vue'),
          meta: { title: '用户登录' },
        },
        {
          path: 'register',
          name: '用户注册',
          component: () => import('@/pages/user/UserRegisterPage.vue'),
          meta: { title: '用户注册' },
        },
      ],
    },
    {
      path: '/:pathMatch(.*)*',
      name: '404',
      component: () => import('@/pages/exception/NotFoundPage.vue'),
      meta: { title: '页面不存在' },
    },
  ],
})

router.afterEach((to) => {
  const title = to.meta?.title as string | undefined
  document.title = title ? `${title} - 云图库` : '云图库'
})

export default router
