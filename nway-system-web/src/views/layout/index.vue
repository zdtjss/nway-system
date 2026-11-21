<template>
  <div class="layout-wrap">
    <toolbar />
    <child />
  </div>
</template>
<script lang="ts" setup>
import toolbar from './toolbar.vue';
import child from './child.vue';
</script>
<style scoped lang="scss">
.layout-wrap {
  position: relative;
  width: 100vw;
  height: 100vh;
  overflow: hidden;
}
</style>
