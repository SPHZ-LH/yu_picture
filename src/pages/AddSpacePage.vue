<template>
  <div class="page-container">
    <div class="page-header">
      <div>
        <h2 class="page-header__title">创建团队空间</h2>
        <p class="page-header__desc">
          团队空间可按账号邀请成员一起使用，空间内的内容只有成员能看到，且不进公共图库。
          私有空间在注册时已自动创建，不在这里建
        </p>
      </div>
      <a-button @click="router.back()">返回</a-button>
    </div>

    <a-card :bordered="false">
      <a-form :model="formState" layout="vertical" @finish="handleSubmit">
        <a-form-item
          label="空间名称"
          name="spaceName"
          :rules="[{ required: true, message: '请输入空间名称' }, { max: 30, message: '名称不超过 30 个字符' }]"
        >
          <a-input
            v-model:value="formState.spaceName"
            placeholder="例如：设计组素材库"
            allow-clear
            show-count
            :maxlength="30"
          />
        </a-form-item>

        <a-form-item label="空间等级" name="spaceLevel" :rules="[{ required: true, message: '请选择空间等级' }]">
          <a-radio-group v-model:value="formState.spaceLevel" class="space-level-group">
            <a-radio-button
              v-for="level in SPACE_LEVEL_OPTIONS"
              :key="level.value"
              :value="level.value"
            >
              {{ level.label }}
            </a-radio-button>
          </a-radio-group>
        </a-form-item>

        <!-- 等级配额对比 -->
        <a-form-item label="配额说明">
          <a-row :gutter="[16, 16]">
            <a-col
              v-for="level in SPACE_LEVEL_OPTIONS"
              :key="level.value"
              :xs="24"
              :sm="8"
            >
              <div
                class="quota-card"
                :class="{ 'quota-card--active': formState.spaceLevel === level.value }"
              >
                <div class="quota-card__head">
                  <span class="quota-card__name">{{ level.label }}</span>
                  <a-tag :color="SPACE_LEVEL_COLOR_MAP[level.value]" :bordered="false">
                    {{ formState.spaceLevel === level.value ? '当前选择' : '' }}
                  </a-tag>
                </div>
                <ul class="quota-card__list">
                  <li>
                    最大容量：<strong>{{ formatSize(SPACE_LEVEL_QUOTA_MAP[level.value]?.maxSize) }}</strong>
                  </li>
                  <li>
                    最大数量：<strong>{{ SPACE_LEVEL_QUOTA_MAP[level.value]?.maxCount }} 张</strong>
                  </li>
                  <li>
                    成员上限：<strong>{{ SPACE_LEVEL_QUOTA_MAP[level.value]?.maxMemberCount }} 人</strong>
                  </li>
                </ul>
              </div>
            </a-col>
          </a-row>
        </a-form-item>

        <a-form-item>
          <a-space>
            <a-button type="primary" html-type="submit" :loading="submitting">创建团队空间</a-button>
            <a-button @click="router.push('/my_space')">团队空间列表</a-button>
          </a-space>
        </a-form-item>
      </a-form>
    </a-card>
  </div>
</template>

<script setup lang="ts">
import { reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { message } from 'ant-design-vue'
import { addSpace } from '@/api/spaceController'
import {
  SPACE_LEVEL_COLOR_MAP,
  SPACE_LEVEL_OPTIONS,
  SPACE_LEVEL_QUOTA_MAP,
  SPACE_TYPE_ENUM,
} from '@/constant/space'
import { formatSize } from '@/utils'

const router = useRouter()
const submitting = ref(false)

// 这一页只建团队空间：私有空间在注册时自动创建，且每人限一个，没有第二次可建
const formState = reactive<API.SpaceAddRequest>({
  spaceName: '',
  spaceLevel: 0,
  spaceType: SPACE_TYPE_ENUM.TEAM,
})

async function handleSubmit() {
  if (!formState.spaceName?.trim()) {
    message.warning('请输入空间名称')
    return
  }
  submitting.value = true
  try {
    const res = await addSpace({
      spaceName: formState.spaceName,
      spaceLevel: formState.spaceLevel,
      spaceType: formState.spaceType,
    })
    if (res.code === 0 && res.data) {
      message.success('团队空间创建成功')
      router.push(`/space/${res.data}`)
    }
  } finally {
    submitting.value = false
  }
}
</script>

<style scoped>
.space-level-group {
  display: flex;
  gap: 8px;
}

.quota-card {
  padding: 14px 16px;
  border: 1px solid var(--app-border, rgba(5, 5, 5, 0.08));
  border-radius: 12px;
  background: #fafafa;
  transition: all 0.2s ease;
}

.quota-card--active {
  border-color: var(--app-accent, #1677ff);
  background: #e6f4ff;
  box-shadow: 0 0 0 2px rgba(22, 119, 255, 0.12);
}

.quota-card__head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 8px;
}

.quota-card__name {
  font-weight: 600;
}

.quota-card__list {
  margin: 0;
  padding-left: 18px;
  color: var(--app-text-secondary, rgba(0, 0, 0, 0.65));
  font-size: 13px;
  line-height: 1.9;
}
</style>
