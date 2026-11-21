/**
 * 表格列配置持久化 composable。
 * 用户可自定义列的显隐和顺序，配置存储在 localStorage。
 *
 * 用法：
 * const { visibleColumns, toggleColumn, resetColumns, moveColumn } = useColumnConfig('userList', allColumns)
 */
import { ref, computed } from 'vue'

export interface ColumnConfig {
  prop: string
  label: string
  visible: boolean
  sort: number
}

export function useColumnConfig(tableKey: string, defaultColumns: ColumnConfig[]) {
  const STORAGE_KEY = `nway_col_config:${tableKey}`

  // 从 localStorage 加载已保存的配置，合并默认配置
  function loadConfig(): ColumnConfig[] {
    try {
      const saved = localStorage.getItem(STORAGE_KEY)
      if (saved) {
        const savedCols = JSON.parse(saved) as ColumnConfig[]
        // 合并：以默认列为准，保留用户的 visible 和 sort 设置
        return defaultColumns.map(def => {
          const saved = savedCols.find(s => s.prop === def.prop)
          return saved ? { ...def, visible: saved.visible, sort: saved.sort } : def
        })
      }
    } catch (e) {
      // 忽略解析错误
    }
    return [...defaultColumns]
  }

  const columns = ref<ColumnConfig[]>(loadConfig())

  // 仅返回可见列，按 sort 排序
  const visibleColumns = computed(() =>
    columns.value.filter(c => c.visible).sort((a, b) => a.sort - b.sort)
  )

  // 切换列显隐
  function toggleColumn(prop: string) {
    const col = columns.value.find(c => c.prop === prop)
    if (col) {
      col.visible = !col.visible
      saveConfig()
    }
  }

  // 重置为默认配置
  function resetColumns() {
    columns.value = [...defaultColumns]
    saveConfig()
  }

  // 移动列顺序
  function moveColumn(prop: string, direction: 'up' | 'down') {
    const sorted = [...columns.value].sort((a, b) => a.sort - b.sort)
    const index = sorted.findIndex(c => c.prop === prop)
    if (index < 0) return
    const targetIndex = direction === 'up' ? index - 1 : index + 1
    if (targetIndex < 0 || targetIndex >= sorted.length) return
    const currentSort = sorted[index].sort
    sorted[index].sort = sorted[targetIndex].sort
    sorted[targetIndex].sort = currentSort
    saveConfig()
  }

  // 保存到 localStorage
  function saveConfig() {
    localStorage.setItem(STORAGE_KEY, JSON.stringify(columns.value))
  }

  return {
    columns,
    visibleColumns,
    toggleColumn,
    resetColumns,
    moveColumn,
    saveConfig,
  }
}
