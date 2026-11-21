<template>
  <div class="content-wrap" :class="dockedOffsetClass">
    <router-view />
  </div>
</template>
<script lang="ts" setup>
import { computed } from 'vue'
import { storeToRefs } from 'pinia'
import { useCurrentPageStore } from '@/stores/menuStore'

// 内容区铺满视口（留 8px 间距），工具栏未固定时悬浮覆盖不占用空间，
// 保证 wujie/micro-app 子应用获得完整视口尺寸；
// 工具栏固定(docked)时内容区按工具栏所在方向避让，避免微前端页面被常驻工具栏遮挡。
const { toolbarDocked, layoutPosition } = storeToRefs(useCurrentPageStore())
const dockedOffsetClass = computed(() => (toolbarDocked.value ? `docked-${layoutPosition.value}` : ''))
</script>
<style scoped lang="scss">
.content-wrap {
  position: absolute;
  top: 8px;
  right: 8px;
  bottom: 8px;
  left: 8px;
  overflow: auto;
}

/* 固定工具栏尺寸：顶部/底部 44px、左侧/右侧 220px，各留 8px 间距 */
.content-wrap.docked-top    { top: 52px; }
.content-wrap.docked-bottom { bottom: 52px; }
.content-wrap.docked-left   { left: 228px; }
.content-wrap.docked-right  { right: 228px; }
</style>
