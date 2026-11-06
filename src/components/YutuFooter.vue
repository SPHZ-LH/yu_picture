<template>
  <footer class="yutu-footer">
    <div class="yutu-wrap yutu-footer__top">
      <div>
        <div class="yutu-footer__brand">屿图</div>
        <p class="yutu-footer__tag">为每一个屏幕，找到它的画面。</p>
      </div>
      <nav class="yutu-footer__links" aria-label="页脚导航">
        <a href="javascript:;" @click.prevent>关于</a>
        <a href="javascript:;" @click.prevent>帮助中心</a>
        <a href="javascript:;" @click.prevent>创作者协议</a>
        <a href="javascript:;" @click.prevent>接口服务</a>
      </nav>
    </div>
    <div class="yutu-footer__bottom">
      <span>© {{ year }} 屿图 YUTU</span>
      <span>图片作品版权归原作者所有</span>
    </div>
  </footer>
</template>

<script setup lang="ts">
const year = new Date().getFullYear()
</script>
