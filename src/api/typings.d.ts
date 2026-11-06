/**
 * 后端 API 类型定义
 *
 * 重要：后端 JsonConfig 注册了 Long -> String 的全局序列化器，
 * 因此所有 id / userId / spaceId / totalSize / total 等 Long 字段
 * 在响应 JSON 中都是**字符串**，此处统一按 string 声明。
 */
declare namespace API {
  /** 全局统一响应封装 BaseResponse<T>，code === 0 表示成功 */
  type BaseResponse<T = unknown> = {
    code: number
    data?: T
    message?: string
  }

  /** MyBatis-Plus 分页结构（total/size/current/pages 均为字符串） */
  type Page<T> = {
    records?: T[]
    total?: string
    size?: string
    current?: string
    pages?: string
    orders?: unknown[]
  }

  /** 分页请求基类 PageRequest */
  type PageRequest = {
    current?: number
    pageSize?: number
    sortField?: string
    sortOrder?: string
  }

  /** 通用删除请求 DeleteRequest */
  type DeleteRequest = {
    id?: string
  }

  // ==================== 用户域 ====================

  type UserVO = {
    id?: string
    userAccount?: string
    userName?: string
    userAvatar?: string
    userProfile?: string
    userRole?: string
    createTime?: string
  }

  type LoginUserVO = {
    id?: string
    userAccount?: string
    userName?: string
    userAvatar?: string
    userProfile?: string
    userRole?: string
    editTime?: string
    createTime?: string
    updateTime?: string
  }

  type User = {
    id?: string
    userAccount?: string
    userPassword?: string
    userName?: string
    userAvatar?: string
    userProfile?: string
    userRole?: string
    editTime?: string
    createTime?: string
    updateTime?: string
    isDelete?: number
  }

  type UserRegisterRequest = {
    userAccount?: string
    userPassword?: string
    checkPassword?: string
  }

  type UserLoginRequest = {
    userAccount?: string
    userPassword?: string
  }

  type UserAddRequest = {
    userName?: string
    userAccount?: string
    userAvatar?: string
    userProfile?: string
    userRole?: string
  }

  type UserUpdateRequest = {
    id?: string
    userPassword?: string
    userName?: string
    userAvatar?: string
    userProfile?: string
    userRole?: string
  }

  type UserUpdateMyRequest = {
    userName?: string
    userAvatar?: string
    userProfile?: string
  }

  type UserUpdatePasswordRequest = {
    oldPassword?: string
    newPassword?: string
  }

  type UserQueryRequest = PageRequest & {
    id?: string
    userAccount?: string
    userName?: string
    userProfile?: string
    userRole?: string
  }

  // ==================== 图片域 ====================

  type UserVOInPicture = UserVO

  type PictureVO = {
    id?: string
    url?: string
    thumbnailUrl?: string
    name?: string
    introduction?: string
    tags?: string[]
    category?: string
    picSize?: string
    picWidth?: number
    picHeight?: number
    picScale?: number
    picFormat?: string
    /** 主色 hex（如 #3B82F6），上传/编辑时自动提取；提取失败为空 */
    mainColor?: string
    /** 色系：blue/green/red/yellow/purple/pink/gray/dark */
    colorFamily?: string
    /** 下载量（点击下载接口时原子自增） */
    downloadCount?: number
    /** 收藏量（收藏功能计数） */
    collectCount?: number
    userId?: string
    spaceId?: string
    createTime?: string
    editTime?: string
    updateTime?: string
    user?: UserVOInPicture
  }

  type VideoUploadRequest = {
    id?: string
    name?: string
    introduction?: string
    category?: string
    tags?: string[]
    spaceId?: string
    /** 视频时长（秒），前端读 <video> metadata 回传 */
    duration?: number
  }

  type VideoEditRequest = {
    id?: string
    name?: string
    introduction?: string
    category?: string
    tags?: string[]
  }

  type VideoReviewRequest = {
    id?: string
    reviewStatus?: number
    reviewMessage?: string
  }

  type VideoQueryRequest = {
    id?: string
    name?: string
    introduction?: string
    category?: string
    tags?: string[]
    videoSize?: string
    videoWidth?: number
    videoHeight?: number
    videoScale?: number
    /** 屏幕方向筛选：landscape-横屏（电脑壁纸）/ portrait-竖屏（手机壁纸） */
    orientation?: string
    /** 清晰度档位下限（总像素）：1080P=921600 / 2K=2073600 / 4K=8294400 */
    minPixels?: string
    videoFormat?: string
    searchText?: string
    userId?: string
    reviewStatus?: number
    reviewMessage?: string
    reviewerId?: string
    spaceId?: string
    nullSpaceFlag?: boolean
    current?: number
    pageSize?: number
    sortField?: string
    sortOrder?: string
  }

  type VideoVO = {
    id?: string
    url?: string
    /** 封面 url（上传时由前端抽帧生成） */
    coverUrl?: string
    name?: string
    introduction?: string
    tags?: string[]
    category?: string
    videoSize?: string
    /** 宽高取自封面的云端实测值，非客户端自报 */
    videoWidth?: number
    videoHeight?: number
    videoScale?: number
    /** 视频时长（秒） */
    videoDuration?: number
    videoFormat?: string
    downloadCount?: number
    userId?: string
    spaceId?: string
    createTime?: string
    editTime?: string
    updateTime?: string
    user?: UserVOInPicture
  }

  type Video = {
    id?: string
    url?: string
    coverUrl?: string
    name?: string
    introduction?: string
    category?: string
    tags?: string
    videoSize?: string
    videoWidth?: number
    videoHeight?: number
    videoScale?: number
    videoDuration?: number
    videoFormat?: string
    downloadCount?: number
    userId?: string
    spaceId?: string
    createTime?: string
    editTime?: string
    updateTime?: string
    reviewStatus?: number
    reviewMessage?: string
    reviewerId?: string
    reviewTime?: string
    isDelete?: number
  }

  type Picture = {
    id?: string
    url?: string
    name?: string
    introduction?: string
    category?: string
    tags?: string
    picSize?: string
    picWidth?: number
    picHeight?: number
    picScale?: number
    picFormat?: string
    userId?: string
    createTime?: string
    editTime?: string
    updateTime?: string
    isDelete?: number
    reviewStatus?: number
    reviewMessage?: string
    reviewerId?: string
    reviewTime?: string
    spaceId?: string
  }

  type PictureUploadRequest = {
    id?: string
    fileUrl?: string
    picName?: string
    spaceId?: string
  }

  type PictureQueryRequest = PageRequest & {
    id?: string
    name?: string
    introduction?: string
    category?: string
    tags?: string[]
    picSize?: string
    picWidth?: number
    picHeight?: number
    picScale?: number
    /** 屏幕方向筛选：landscape-横屏（电脑壁纸）；portrait-竖屏（手机壁纸） */
    orientation?: 'landscape' | 'portrait'
    /** 清晰度档位下限（总像素）：1080P=921600 / 2K=2073600 / 4K=8294400 */
    minPixels?: number
    /** 色系筛选：blue/green/red/yellow/purple/pink/gray/dark */
    colorFamily?: string
    picFormat?: string
    searchText?: string
    userId?: string
    reviewStatus?: number
    reviewMessage?: string
    reviewerId?: string
    spaceId?: string
    nullSpaceFlag?: boolean
  }

  type PictureUpdateRequest = {
    id?: string
    name?: string
    introduction?: string
    category?: string
    tags?: string[]
  }

  type PictureEditRequest = PictureUpdateRequest

  type PictureCollectRequest = {
    pictureId?: string
  }

  /** 图片收藏状态：当前用户是否已收藏 + 最新收藏量（会话相关，故不并入共享缓存的 PictureVO） */
  type PictureCollectStatusVO = {
    pictureId?: string
    collected?: boolean
    collectCount?: number
  }

  /** 批量查询收藏状态请求：一次问一批图片 */
  type PictureCollectBatchRequest = {
    pictureIds?: string[]
  }

  type PictureExpandSubmitRequest = {
    pictureId?: string
    /** 目标宽高比：1:1 / 4:3 / 3:4 / 16:9 / 9:16（服务端支持集，21:9 会失败） */
    outputRatio?: string
  }

  type PictureExpandSaveRequest = {
    pictureId?: string
    taskId?: string
  }

  type PictureExpandResultVO = {
    /** PENDING / RUNNING / SUCCEEDED / FAILED */
    status?: string
    imageUrl?: string
    message?: string
  }

  type PictureAiPrefillRequest = {
    pictureId?: string
  }

  /** AI 预填建议：字段为空表示模型没给出这一项；整个对象为空表示本次读图没产出可用内容 */
  type PictureAiPrefillVO = {
    name?: string
    introduction?: string
    /** 必属既有分类词表 */
    category?: string
    /** 必属既有标签词表，最多 5 个 */
    tags?: string[]
  }

  type PictureTransformRequest = {
    id?: string
    /** 顺时针旋转角度：0/90/180/270 */
    rotate?: number
    /** 翻转方向 */
    flip?: 'horizontal' | 'vertical'
    /** 裁剪起点 x（基于旋转+翻转后预览图的像素坐标），与 cropY/cropW/cropH 成组出现 */
    cropX?: number
    cropY?: number
    cropW?: number
    cropH?: number
    /** 输出宽度（px），高度等比缩放 */
    outputWidth?: number
  }

  type PictureReviewRequest = {
    id?: string
    reviewStatus?: number
    reviewMessage?: string
  }

  type PictureUploadByBatchRequest = {
    namePrefix?: string
    searchText?: string
    count?: number
  }

  type PictureTagCategory = {
    tagList?: string[]
    categoryList?: string[]
  }

  // ==================== 空间域 ====================

  type SpaceVO = {
    id?: string
    spaceName?: string
    spaceLevel?: number
    spaceType?: number
    maxSize?: string
    maxCount?: string
    totalSize?: string
    totalCount?: string
    /** 成员数量上限（含创建者） */
    maxMemberCount?: string
    /** 当前成员数。降低上限不清退已有成员，所以可能大于 maxMemberCount */
    memberCount?: string
    /** 当前登录用户在该空间的角色，非成员或未登录为空 */
    myRole?: string
    userId?: string
    createTime?: string
    updateTime?: string
    user?: UserVO
  }

  type Space = {
    id?: string
    spaceName?: string
    spaceLevel?: number
    maxSize?: string
    maxCount?: string
    totalSize?: string
    totalCount?: string
    maxMemberCount?: string
    userId?: string
    spaceType?: number
    createTime?: string
    editTime?: string
    updateTime?: string
    isDelete?: number
  }

  type SpaceAddRequest = {
    spaceName?: string
    spaceLevel?: number
    /** 0-私有 1-团队，不传按私有处理 */
    spaceType?: number
  }

  /** 空间成员视图 */
  type SpaceUserVO = {
    id?: string
    spaceId?: string
    userId?: string
    spaceRole?: string
    spaceRoleText?: string
    /** 创建者不可被移出、不可被降级 */
    creator?: boolean
    createTime?: string
    user?: UserVO
  }

  type SpaceUserAddRequest = {
    spaceId?: string
    userId?: string
    spaceRole?: string
  }

  type SpaceUserEditRoleRequest = {
    spaceId?: string
    userId?: string
    spaceRole?: string
  }

  type SpaceUserDeleteRequest = {
    spaceId?: string
    userId?: string
  }

  type SpaceUpdateRequest = {
    id?: string
    spaceName?: string
    spaceLevel?: number
  }

  type SpaceEditRequest = {
    id?: string
    spaceName?: string
  }

  type SpaceQueryRequest = PageRequest & {
    id?: string
    spaceName?: string
    spaceLevel?: number
    spaceType?: number
    userId?: string
  }

  /** 空间分析查询请求（query 参数） */
  type SpaceAnalyzeQueryRequest = {
    querySpaceId?: string
    queryAll?: boolean
    granularity?: string
    startTime?: string
    endTime?: string
    topN?: number
  }

  type SpaceUsageAnalyzeResponse = {
    usedSize?: string
    maxSize?: string
    usedSizeRatio?: number
    usedCount?: string
    maxCount?: string
    usedCountRatio?: number
  }

  type SpaceCategoryAnalyzeResponse = {
    category?: string
    count?: string
    totalSize?: string
  }

  type SpaceTagAnalyzeResponse = {
    tag?: string
    count?: string
  }

  type SpaceSizeAnalyzeResponse = {
    sizeRange?: string
    count?: string
    totalSize?: string
  }

  type SpaceUploadTrendAnalyzeResponse = {
    timePoint?: string
    count?: string
    totalSize?: string
  }

  type SpaceRankAnalyzeResponse = {
    spaceId?: string
    spaceName?: string
    spaceType?: number
    userId?: string
    totalSize?: string
    totalCount?: string
    maxSize?: string
  }

  // ==================== 缓存运维（管理员） ====================

  type CacheEvictRequest = {
    /** 缓存类型：picture / user */
    type?: string
    /** 图片 id 或用户 id（雪花 id 超出 JS 安全整数范围，必须用字符串传输） */
    id?: string
  }

  type CacheEvictResult = {
    type?: string
    id?: string
    /** 一级缓存（本地 Caffeine）中是否存在该 key 并已清除 */
    localRemoved?: boolean
    /** 二级缓存（Redis）中是否存在该 key 并已清除 */
    redisRemoved?: boolean
    /** 二级缓存因熔断被跳过（Redis 恢复后需重新执行失效） */
    redisSkipped?: boolean
  }

  type CacheStatsItem = {
    hitCount?: number
    missCount?: number
    /** 命中率 0~1 */
    hitRate?: number
    evictionCount?: number
    size?: number
  }

  type CacheStats = {
    pictureLocal?: CacheStatsItem
    userLocal?: CacheStatsItem
    l2Circuit?: {
      /** true 表示 Redis 故障熔断中，缓存退化为直查数据库 */
      open?: boolean
      consecutiveFailures?: number
    }
  }
}
