<template>
  <el-popover placement="bottom" :width="200" trigger="click">
    <template #reference>
      <el-button :icon="Setting" circle size="small" title="列设置" />
    </template>
    <div class="column-config-panel">
      <div class="column-config-header">
        <span>列设置</span>
        <el-button text size="small" @click="reset">重置</el-button>
      </div>
      <div v-for="col in columns" :key="col.prop" class="column-config-item">
        <el-checkbox v-model="col.visible" @change="saveConfig">{{ col.label }}</el-checkbox>
      </div>
    </div>
  </el-popover>
</template>

<script setup lang="ts">
import { Setting } from '@element-plus/icons-vue'
import type { ColumnConfig } from '@/composables/useColumnConfig'

const props = defineProps<{
  columns: ColumnConfig[]
  onReset: () => void
  onSave: () => void
}>()

function saveConfig() {
  props.onSave()
}

function reset() {
  props.onReset()
}
</script>

<style scoped>
.column-config-panel {
  padding: 4px 0;
}

.column-config-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 8px;
  padding-bottom: 4px;
  border-bottom: 1px solid #eee;
}

.column-config-item {
  padding: 4px 0;
}
</style>
