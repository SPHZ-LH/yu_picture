<template>
  <div class="page-container">
    <div class="page-header">
      <div>
        <h2 class="page-header__title">批量创建图片</h2>
        <p class="page-header__desc">
          按关键词从外部图片源批量抓取图片并写入公共图库，适合快速初始化素材库
        </p>
      </div>
      <a-button @click="router.back()">返回</a-button>
    </div>

    <a-alert
      class="batch-alert"
      type="warning"
      show-icon
      message="批量抓取为管理员功能"
      description="抓取到的图片会直接入库并进入待审核状态，请在「图片管理」中确认内容合规后再通过审核。"
    />

    <a-card :bordered="false">
      <a-form :model="formState" layout="vertical" @finish="handleSubmit">
        <a-form-item
          label="图片名称前缀"
          name="namePrefix"
          :rules="[{ required: true, message: '请输入名称前缀' }]"
        >
          <a-input
            v-model:value="formState.namePrefix"
            placeholder="例如：风景图，最终名称为「风景图 1」「风景图 2」…"
            allow-clear
          />
        </a-form-item>

        <a-form-item
          label="抓取关键词"
          name="searchText"
          :rules="[{ required: true, message: '请输入抓取关键词' }]"
        >
          <a-input
            v-model:value="formState.searchText"
            placeholder="例如：二次元、宠物、美食"
            allow-clear
          />
        </a-form-item>

        <a-form-item
          label="抓取数量"
          name="count"
          :rules="[{ required: true, message: '请输入抓取数量' }]"
        >
          <a-input-number
            v-model:value="formState.count"
            :min="1"
            :max="50"
            style="width: 200px"
          />
          <span class="form-tip">建议不超过 50 张，数量过多会显著延长处理时间</span>
        </a-form-item>

        <a-form-item>
          <a-space>
            <a-button type="primary" html-type="submit" :loading="submitting">
              开始抓取
            </a-button>
            <a-button @click="router.push('/admin/pictureManage')">前往图片管理</a-button>
          </a-space>
        </a-form-item>
      </a-form>

      <a-result
        v-if="createdCount !== undefined"
        status="success"
        :title="`成功创建 ${createdCount} 张图片`"
        sub-title="图片已入库，请前往图片管理完成审核"
      >
        <template #extra>
          <a-button type="primary" @click="router.push('/admin/pictureManage')">
            去审核
          </a-button>
          <a-button @click="router.push('/')">查看公共图库</a-button>
        </template>
      </a-result>
    </a-card>
  </div>
</template>

<script setup lang="ts">
import { reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { message } from 'ant-design-vue'
import { uploadPictureByBatch } from '@/api/pictureController'

const router = useRouter()

const submitting = ref(false)
const createdCount = ref<number>()

const formState = reactive<API.PictureUploadByBatchRequest>({
  namePrefix: '',
  searchText: '',
  count: 10,
})

async function handleSubmit() {
  submitting.value = true
  try {
    const res = await uploadPictureByBatch({
      namePrefix: formState.namePrefix,
      searchText: formState.searchText,
      count: formState.count,
    })
    if (res.code === 0) {
      createdCount.value = Number(res.data ?? 0)
      message.success(`成功创建 ${createdCount.value} 张图片`)
    }
  } finally {
    submitting.value = false
  }
}
</script>

<style scoped>
.batch-alert {
  border-radius: 10px;
}

.form-tip {
  margin-left: 12px;
  color: var(--app-text-secondary, rgba(0, 0, 0, 0.45));
  font-size: 12px;
}
</style>
