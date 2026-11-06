/**
 * 空间级别枚举（与后端 SpaceLevel 对应）
 * 决定空间创建时的默认配额
 */
export const SPACE_LEVEL_ENUM = {
  COMMON: 0,
  PROFESSIONAL: 1,
  FLAGSHIP: 2,
} as const

export const SPACE_LEVEL_MAP: Record<number, string> = {
  0: '普通版',
  1: '专业版',
  2: '旗舰版',
}

export const SPACE_LEVEL_COLOR_MAP: Record<number, string> = {
  0: 'default',
  1: 'blue',
  2: 'gold',
}

export const SPACE_LEVEL_OPTIONS = Object.keys(SPACE_LEVEL_MAP).map((key) => {
  const value = Number(key)
  return {
    label: SPACE_LEVEL_MAP[value],
    value,
  }
})

/** 各等级默认配额（用于前端展示说明，实际配额以后端返回为准） */
export const SPACE_LEVEL_QUOTA_MAP: Record<
  number,
  { maxSize: number; maxCount: number; maxMemberCount: number }
> = {
  0: { maxSize: 100 * 1024 * 1024, maxCount: 100, maxMemberCount: 5 },
  1: { maxSize: 1024 * 1024 * 1024, maxCount: 1000, maxMemberCount: 20 },
  2: { maxSize: 10 * 1024 * 1024 * 1024, maxCount: 10000, maxMemberCount: 50 },
}

/**
 * 空间类型枚举
 * 0 - 私有空间：仅创建者能上传与管理，每位用户至多一个
 * 1 - 团队空间：靠成员关系授权，可多人协作，不限个数
 */
export const SPACE_TYPE_ENUM = {
  PRIVATE: 0,
  TEAM: 1,
} as const

export const SPACE_TYPE_MAP: Record<number, string> = {
  0: '私有空间',
  1: '团队空间',
}

export const SPACE_TYPE_OPTIONS = Object.keys(SPACE_TYPE_MAP).map((key) => {
  const value = Number(key)
  return {
    label: SPACE_TYPE_MAP[value],
    value,
  }
})

/**
 * 空间角色（与后端 SpaceRoleEnum 对应）
 * 存库与传参用英文小写值，中文只用于展示。
 * 别和站点级的 user/admin 混为一谈：空间角色按空间分别授予，同一个人在不同空间可以不同角色。
 * 也别把 viewer 读成「访问者」——那指任何发起查询的人，这里的 viewer 是一种被授予的身份。
 */
export const SPACE_ROLE_ENUM = {
  ADMIN: 'admin',
  EDITOR: 'editor',
  VIEWER: 'viewer',
} as const

export const SPACE_ROLE_MAP: Record<string, string> = {
  admin: '管理员',
  editor: '编辑者',
  viewer: '只读成员',
}

export const SPACE_ROLE_COLOR_MAP: Record<string, string> = {
  admin: 'gold',
  editor: 'blue',
  viewer: 'default',
}

export const SPACE_ROLE_OPTIONS = Object.keys(SPACE_ROLE_MAP).map((key) => ({
  label: SPACE_ROLE_MAP[key],
  value: key,
}))

/** 空间排序字段 */
export const SPACE_SORT_FIELD_OPTIONS = [
  { label: '最新', value: 'createTime' },
  { label: '名称', value: 'spaceName' },
  { label: '级别', value: 'spaceLevel' },
  { label: '大小', value: 'maxSize' },
  { label: '数量', value: 'maxCount' },
]
