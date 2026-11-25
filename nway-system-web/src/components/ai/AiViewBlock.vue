<template>
  <div class="ai-view" :class="[`ai-view-${view.type}`, { 'is-compact': compact }]">
    <!-- 表格 -->
    <template v-if="view.type === 'table'">
      <div v-if="view.summary?.length" class="ai-view-summary">
        <div v-for="(metric, index) in view.summary" :key="'summary-' + index" class="ai-view-summary-item">
          <span class="ai-view-label">{{ metric.label }}</span>
          <span class="ai-view-summary-value">{{ metric.value }}<span v-if="metric.unit"
              class="ai-view-summary-unit">{{ metric.unit }}</span></span>
        </div>
      </div>
      <div v-if="summaryFields.length" class="ai-view-summary">
        <div v-for="field in summaryFields" :key="field.key" class="ai-view-summary-item"
             :class="{ block: field.value.kind === 'code' }">
          <span class="ai-view-label">{{ field.label }}</span>
          <a v-if="field.value.kind === 'link'" :href="safeHref(field.value.href)" target="_blank"
             rel="noopener">{{ field.value.text }}</a>
          <span v-else-if="field.value.kind === 'boolean'" class="ai-view-bool"
                :class="{ yes: field.value.bool }">{{ field.value.text }}</span>
          <pre v-else-if="field.value.kind === 'code'" class="ai-view-code">{{ field.value.text }}</pre>
          <span v-else class="ai-view-summary-value" :title="field.value.text"
                :class="{ 'is-empty': field.value.kind === 'empty' }">{{ field.value.text }}</span>
        </div>
      </div>

      <div v-if="view.title || view.total !== undefined" class="ai-view-head">
        <span class="ai-view-title">{{ view.title || '数据列表' }}</span>
        <span v-if="view.total !== undefined" class="ai-view-count">共 {{ view.total }} 条</span>
      </div>

      <div class="ai-view-table-wrap">
        <table class="ai-view-table">
          <thead>
            <tr>
              <th v-for="col in view.columns || []" :key="col.key" :title="col.label">{{ col.label }}</th>
            </tr>
          </thead>
          <tbody>
            <tr v-if="!(view.rows || []).length">
              <td :colspan="(view.columns || []).length || 1" class="ai-view-table-empty">暂无数据</td>
            </tr>
            <tr v-for="(row, rowIndex) in view.rows || []" :key="rowIndex">
              <td v-for="(cell, cellIndex) in row" :key="cellIndex" :title="cell.text">
                <a v-if="cell.kind === 'link'" :href="safeHref(cell.href)" target="_blank"
                   rel="noopener">{{ cell.text }}</a>
                <span v-else-if="cell.kind === 'boolean'" class="ai-view-bool"
                      :class="{ yes: cell.bool }">{{ cell.text }}</span>
                <code v-else-if="cell.kind === 'code'" :title="cell.text">{{ cell.text }}</code>
                <span v-else-if="columnType(cellIndex) === 'tag'" class="ai-view-tag"
                      :title="cell.text">{{ cell.text }}</span>
                <span v-else :class="{ 'is-empty': cell.kind === 'empty' }">{{ cell.text }}</span>
              </td>
            </tr>
          </tbody>
        </table>
      </div>
      <div v-if="tableTip" class="ai-view-tip">{{ tableTip }}</div>
    </template>

    <!-- 指标卡 -->
    <template v-else-if="view.type === 'metric'">
      <div v-if="view.title" class="ai-view-head">
        <span class="ai-view-title">{{ view.title }}</span>
      </div>
      <div class="ai-view-metrics">
        <div v-for="(metric, index) in view.metrics || []" :key="index" class="ai-view-metric">
          <div class="ai-view-metric-label">{{ metric.label }}</div>
          <div class="ai-view-metric-value">
            {{ metric.value }}<span v-if="metric.unit" class="ai-view-metric-unit">{{ metric.unit }}</span>
          </div>
          <div v-if="metric.trend" class="ai-view-metric-trend">{{ metric.trend }}</div>
        </div>
      </div>
      <div v-if="view.truncated" class="ai-view-tip">数据较多，已截断展示</div>
    </template>

    <!-- 图表 -->
    <template v-else-if="view.type === 'chart' && view.chart">
      <div v-if="view.title" class="ai-view-head">
        <span class="ai-view-title">{{ view.title }}</span>
      </div>
      <svg class="ai-view-chart" viewBox="0 0 340 180" role="img" :aria-label="view.title || '数据图表'">
        <template v-if="view.chart.kind === 'pie'">
          <template v-if="pieSlices.length === 1">
            <circle cx="100" cy="90" r="70" :fill="pieSlices[0].color" />
          </template>
          <path v-for="(slice, index) in pieSlices.length > 1 ? pieSlices : []" :key="index"
                :d="slice.path" :fill="slice.color">
            <title>{{ slice.label }}：{{ slice.value }}（{{ slice.percent }}%）</title>
          </path>
          <circle cx="100" cy="90" r="34" fill="#fff" />
          <text x="100" y="95" text-anchor="middle" class="ai-view-chart-total">合计 {{ pieTotal }}</text>
        </template>

        <template v-else>
          <g v-for="(line, index) in gridLines" :key="'grid-' + index">
            <line :x1="PLOT_LEFT" :x2="PLOT_RIGHT" :y1="line.y" :y2="line.y" class="ai-view-chart-grid" />
            <text :x="PLOT_LEFT - 6" :y="line.y + 3" text-anchor="end" class="ai-view-chart-axis">
              {{ line.label }}
            </text>
          </g>

          <template v-if="view.chart.kind === 'bar'">
            <g v-for="(group, index) in barGroups" :key="'bar-' + index">
              <rect v-for="(bar, barIndex) in group.bars" :key="barIndex" :x="bar.x" :y="bar.y"
                    :width="bar.w" :height="bar.h" :fill="bar.color" rx="2">
                <title>{{ group.label }} · {{ bar.name }}：{{ bar.value }}</title>
              </rect>
              <text v-if="group.showLabel" :x="group.center" :y="PLOT_BOTTOM + 14" text-anchor="middle"
                    class="ai-view-chart-axis">{{ group.label }}</text>
            </g>
          </template>

          <template v-else>
            <polyline v-for="(line, index) in lineSeries" :key="'line-' + index" :points="line.points"
                      fill="none" :stroke="line.color" stroke-width="2" stroke-linejoin="round" />
            <circle v-for="(dot, index) in lineDots" :key="'dot-' + index" :cx="dot.x" :cy="dot.y" r="2.5"
                    :fill="dot.color">
              <title>{{ dot.label }} · {{ dot.name }}：{{ dot.value }}</title>
            </circle>
            <text v-for="(label, index) in lineLabels" :key="'xlabel-' + index" :x="label.x"
                  :y="PLOT_BOTTOM + 14" text-anchor="middle" class="ai-view-chart-axis">{{ label.text }}</text>
          </template>
        </template>
      </svg>

      <div v-if="legend.length" class="ai-view-legend">
        <span v-for="(item, index) in legend" :key="index" class="ai-view-legend-item">
          <i class="ai-view-legend-dot" :style="{ background: item.color }"></i>{{ item.label }}
        </span>
      </div>
      <div v-if="view.truncated" class="ai-view-tip">数据较多，已截断展示</div>
    </template>

    <!-- 单条详情 -->
    <template v-else-if="view.type === 'fields'">
      <div v-if="view.title" class="ai-view-head">
        <span class="ai-view-title">{{ view.title }}</span>
      </div>
      <div class="ai-view-fields">
        <div v-for="field in view.fields || []" :key="field.key" class="ai-view-field"
             :class="{ block: field.value.kind === 'code' }">
          <span class="ai-view-label">{{ field.label }}</span>
          <a v-if="field.value.kind === 'link'" :href="safeHref(field.value.href)" target="_blank"
             rel="noopener">{{ field.value.text }}</a>
          <span v-else-if="field.value.kind === 'boolean'" class="ai-view-bool"
                :class="{ yes: field.value.bool }">{{ field.value.text }}</span>
          <pre v-else-if="field.value.kind === 'code'" class="ai-view-code">{{ field.value.text }}</pre>
          <span v-else class="ai-view-value" :title="field.value.text"
                :class="{ 'is-empty': field.value.kind === 'empty' }">{{ field.value.text }}</span>
        </div>
      </div>
      <div v-if="view.truncated" class="ai-view-tip">数据较多，已截断展示</div>
    </template>

    <!-- 简单清单 -->
    <template v-else-if="view.type === 'list'">
      <div v-if="view.title" class="ai-view-head">
        <span class="ai-view-title">{{ view.title }}</span>
      </div>
      <ul class="ai-view-list">
        <li v-for="(item, index) in view.list || []" :key="index">
          <a v-if="item.kind === 'link'" :href="safeHref(item.href)" target="_blank"
             rel="noopener">{{ item.text }}</a>
          <span v-else-if="item.kind === 'boolean'" class="ai-view-bool"
                :class="{ yes: item.bool }">{{ item.text }}</span>
          <span v-else>{{ item.text }}</span>
        </li>
      </ul>
      <div v-if="view.truncated" class="ai-view-tip">数据较多，已截断展示</div>
    </template>

    <!-- 文件卡片 -->
    <template v-else-if="view.type === 'file'">
      <div v-if="view.title" class="ai-view-head">
        <span class="ai-view-title">{{ view.title }}</span>
      </div>
      <div class="ai-view-files">
        <a v-for="(file, index) in view.files || []" :key="index" class="ai-view-file"
           :href="safeHref(file.url)" :target="file.url ? '_blank' : undefined" rel="noopener">
          <el-icon><Document /></el-icon>
          <span class="ai-view-file-name" :title="file.name">{{ file.name }}</span>
          <span v-if="file.size" class="ai-view-file-size">{{ file.size }}</span>
        </a>
      </div>
    </template>

    <!-- 纯文本 -->
    <template v-else-if="view.type === 'text'">
      <div v-if="view.title" class="ai-view-head">
        <span class="ai-view-title">{{ view.title }}</span>
      </div>
      <div class="ai-view-text">{{ view.text }}</div>
    </template>

    <!-- 空结果 -->
    <div v-else class="ai-view-tip">{{ view.text || '接口未返回数据' }}</div>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { Document } from '@element-plus/icons-vue'
import type { AiView } from '@/utils/aiView'

const props = withDefaults(defineProps<{ view: AiView; compact?: boolean }>(), {
  compact: false
})

const CHART_COLORS = ['#1664ff', '#00b42a', '#ff7d00', '#f53f3f']
const PLOT_LEFT = 38
const PLOT_RIGHT = 334
const PLOT_TOP = 12
const PLOT_BOTTOM = 150

const summaryFields = computed(() => {
  if (props.view.type !== 'table') return []
  return (props.view.fields || []).filter((field) => field.key !== 'total')
})

const tableTip = computed(() => {
  const view = props.view
  if (view.type !== 'table') return ''
  const total = view.total ?? (view.rows || []).length
  const shown = (view.rows || []).length
  if (total > shown) return `仅展示前 ${shown} 条`
  if (view.truncated) return '数据较多，已截断展示'
  return ''
})

function safeHref(href?: string): string | undefined {
  if (!href) return undefined
  // 接口返回的链接只允许 http(s)；导出文件使用站内票据地址，允许根路径
  return /^https?:\/\//i.test(href) || href.startsWith('/') ? href : undefined
}

function columnType(index: number): string {
  return (props.view.columns || [])[index]?.type || ''
}

// ==================== 图表 ====================

const chartSeries = computed(() => (props.view.type === 'chart' ? props.view.chart?.series || [] : []))
const chartCategories = computed(() => (props.view.type === 'chart' ? props.view.chart?.categories || [] : []))

const chartMax = computed(() => {
  let max = 0
  chartSeries.value.forEach((series) => {
    series.data.forEach((value) => {
      if (Number.isFinite(value)) max = Math.max(max, value)
    })
  })
  return max > 0 ? max : 1
})

const gridLines = computed(() => Array.from({ length: 4 }, (_, index) => {
  const ratio = index / 3
  const y = PLOT_BOTTOM - ratio * (PLOT_BOTTOM - PLOT_TOP)
  return { y, label: formatAxis(chartMax.value * ratio) }
}))

const barGroups = computed(() => {
  const categories = chartCategories.value
  const seriesList = chartSeries.value
  if (!categories.length || !seriesList.length) return []
  const plotWidth = PLOT_RIGHT - PLOT_LEFT
  const groupWidth = plotWidth / categories.length
  const barWidth = Math.max(2, (groupWidth * 0.72) / seriesList.length)
  const step = Math.ceil(categories.length / 8) || 1
  return categories.map((label, groupIndex) => {
    const offset = PLOT_LEFT + groupIndex * groupWidth + groupWidth * 0.14
    return {
      label,
      showLabel: groupIndex % step === 0,
      center: PLOT_LEFT + groupIndex * groupWidth + groupWidth / 2,
      bars: seriesList.map((series, seriesIndex) => {
        const value = series.data[groupIndex] || 0
        const height = Math.max(0, (value / chartMax.value) * (PLOT_BOTTOM - PLOT_TOP))
        return {
          x: offset + seriesIndex * barWidth,
          y: PLOT_BOTTOM - height,
          w: Math.max(1, barWidth - 1),
          h: height,
          color: CHART_COLORS[seriesIndex % CHART_COLORS.length],
          name: series.name,
          value
        }
      })
    }
  })
})

const linePointsOf = (seriesIndex: number): { x: number; y: number }[] => {
  const categories = chartCategories.value
  const data = chartSeries.value[seriesIndex]?.data || []
  const plotWidth = PLOT_RIGHT - PLOT_LEFT
  return categories.map((_, index) => {
    const ratio = categories.length <= 1 ? 0.5 : index / (categories.length - 1)
    const value = data[index] || 0
    return {
      x: PLOT_LEFT + ratio * plotWidth,
      y: PLOT_BOTTOM - (value / chartMax.value) * (PLOT_BOTTOM - PLOT_TOP)
    }
  })
}

const lineSeries = computed(() => chartSeries.value.map((series, index) => ({
  name: series.name,
  color: CHART_COLORS[index % CHART_COLORS.length],
  points: linePointsOf(index).map((point) => `${point.x},${point.y}`).join(' ')
})))

const lineDots = computed(() => {
  const dots: { x: number; y: number; color: string; name: string; label: string; value: number }[] = []
  chartSeries.value.forEach((series, seriesIndex) => {
    const points = linePointsOf(seriesIndex)
    points.forEach((point, index) => {
      dots.push({
        x: point.x,
        y: point.y,
        color: CHART_COLORS[seriesIndex % CHART_COLORS.length],
        name: series.name,
        label: chartCategories.value[index],
        value: series.data[index] || 0
      })
    })
  })
  return dots
})

const lineLabels = computed(() => {
  const categories = chartCategories.value
  const points = linePointsOf(0)
  const step = Math.ceil(categories.length / 8) || 1
  return categories.map((label, index) => ({
    x: points[index]?.x || 0,
    text: label.length > 6 && index % step === 0 ? `${label.slice(0, 6)}…` : label,
    show: index % step === 0
  })).filter((item) => item.show)
})

const pieTotal = computed(() => {
  const data = chartSeries.value[0]?.data || []
  return formatAxis(data.reduce((sum, value) => sum + (Number.isFinite(value) ? value : 0), 0))
})

const pieSlices = computed(() => {
  const series = chartSeries.value[0]
  if (!series) return []
  const values = chartCategories.value.map((_, index) => Math.max(0, series.data[index] || 0))
  const total = values.reduce((sum, value) => sum + value, 0)
  if (total <= 0) return []
  let angle = -Math.PI / 2
  return values.map((value, index) => {
    const start = angle
    const end = angle + (value / total) * Math.PI * 2
    angle = end
    return {
      label: chartCategories.value[index],
      value,
      percent: Math.round((value / total) * 100),
      color: CHART_COLORS[index % CHART_COLORS.length],
      path: describeArc(100, 90, 70, start, end)
    }
  })
})

const legend = computed(() => {
  if (props.view.type !== 'chart') return []
  const kind = props.view.chart?.kind
  if (kind === 'pie') {
    return pieSlices.value.slice(0, 8).map((slice) => ({ label: slice.label, color: slice.color }))
  }
  return chartSeries.value.map((series, index) => ({
    label: series.name,
    color: CHART_COLORS[index % CHART_COLORS.length]
  }))
})

function polarPoint(cx: number, cy: number, radius: number, angle: number) {
  return { x: cx + radius * Math.cos(angle), y: cy + radius * Math.sin(angle) }
}

function describeArc(cx: number, cy: number, radius: number, start: number, end: number): string {
  if (end - start >= Math.PI * 2 - 0.0001) {
    return `M ${cx - radius} ${cy} A ${radius} ${radius} 0 1 1 ${cx + radius} ${cy} A ${radius} ${radius} 0 1 1 ${cx - radius} ${cy} Z`
  }
  const startPoint = polarPoint(cx, cy, radius, start)
  const endPoint = polarPoint(cx, cy, radius, end)
  const largeArc = end - start > Math.PI ? 1 : 0
  return `M ${cx} ${cy} L ${startPoint.x} ${startPoint.y} A ${radius} ${radius} 0 ${largeArc} 1 ${endPoint.x} ${endPoint.y} Z`
}

function formatAxis(value: number): string {
  if (!Number.isFinite(value)) return '0'
  if (Math.abs(value) >= 10000) return `${(value / 10000).toFixed(1)}万`
  return Number.isInteger(value) ? String(value) : value.toFixed(1)
}
</script>

<style scoped lang="scss">
.ai-view {
  width: 100%;
  min-width: 0;
  white-space: normal;
  word-break: break-word;
  font-size: 12px;
  line-height: 1.6;
}

/* ============ 标题与摘要 ============ */
.ai-view-head {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  gap: 8px;
  margin-top: 10px;
}

.ai-view-title {
  color: #4e5969;
  font-size: 12px;
  font-weight: 600;
}

.ai-view-count {
  color: #a9aeb8;
  font-size: 11px;
}

.ai-view-summary {
  display: flex;
  flex-wrap: wrap;
  align-items: baseline;
  gap: 4px 18px;
  margin-top: 8px;
  padding: 8px 10px;
  background: #f7f9fd;
  border-radius: 10px;
}

.ai-view-summary-item {
  display: inline-flex;
  align-items: baseline;
  gap: 4px;
  min-width: 0;
  max-width: 100%;
  font-size: 12px;
}

.ai-view-summary-item.block {
  display: block;
  flex: 1 0 100%;
}

.ai-view-summary-item.block .ai-view-code {
  margin-top: 4px;
}

.ai-view-summary-value {
  max-width: 320px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  font-weight: 500;
  color: #1f2329;
}

.ai-view-summary-unit {
  margin-left: 2px;
  color: #8f959e;
  font-size: 11px;
  font-weight: 400;
}

/* ============ 表格 ============ */
.ai-view-table-wrap {
  margin-top: 8px;
  max-height: 320px;
  overflow: auto;
  border: 1px solid #e8edf7;
  border-radius: 12px;
  background: #fff;
}

.ai-view-table {
  width: 100%;
  border-collapse: collapse;
  font-size: 13.5px;
}

.ai-view-table th,
.ai-view-table td {
  max-width: 280px;
  padding: 9px 12px;
  border-bottom: 1px solid #eff2f8;
  text-align: left;
  vertical-align: top;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.ai-view-table th {
  position: sticky;
  top: 0;
  z-index: 1;
  background: #f7f9fd;
  color: #5b6579;
  font-weight: 600;
}

.ai-view-table tbody tr:nth-child(even) td {
  background: #fbfcfe;
}

.ai-view-table tr:last-child td {
  border-bottom: none;
}

.ai-view-table td code {
  padding: 1px 4px;
  border-radius: 4px;
  background: #f7f8fa;
  color: #646a73;
  font-size: 11px;
  font-family: Consolas, Monaco, monospace;
}

.ai-view-table-empty {
  color: #a9aeb8;
  text-align: center;
}

.ai-view-tag {
  display: inline-block;
  padding: 0 6px;
  border-radius: 4px;
  background: #eef4ff;
  color: #1664ff;
  font-size: 11px;
  line-height: 18px;
}

/* ============ 字段 ============ */
.ai-view-fields {
  margin-top: 8px;
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.ai-view-field {
  display: grid;
  grid-template-columns: minmax(88px, 160px) minmax(0, 1fr);
  gap: 4px 12px;
  min-width: 0;
  font-size: 12px;
  line-height: 1.6;
}

.ai-view-field.block {
  grid-template-columns: minmax(0, 1fr);
  gap: 2px;
}

.ai-view-label {
  flex: 0 0 auto;
  color: #8f959e;
}

.ai-view-label::after {
  content: '：';
}

.ai-view-value {
  color: #1f2329;
  word-break: break-word;
}

.ai-view-code {
  margin: 0;
  padding: 8px 10px;
  max-height: 220px;
  overflow: auto;
  background: #fafbfc;
  border-radius: 8px;
  white-space: pre-wrap;
  word-break: break-word;
  color: #4e5969;
  font-size: 12px;
  line-height: 1.7;
  font-family: inherit;
}

.ai-view-bool {
  color: #8f959e;
}

.ai-view-bool.yes {
  color: #00b42a;
}

.ai-view .is-empty {
  color: #c9cdd4;
}

/* ============ 指标卡 ============ */
.ai-view-metrics {
  margin-top: 8px;
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(120px, 1fr));
  gap: 8px;
}

.ai-view-metric {
  padding: 10px 12px;
  background: #fafbfc;
  border: 1px solid #eef0f3;
  border-radius: 8px;
}

.ai-view-metric-label {
  color: #8f959e;
  font-size: 11px;
}

.ai-view-metric-value {
  margin-top: 2px;
  color: #1f2329;
  font-size: 20px;
  font-weight: 600;
  line-height: 1.3;
}

.ai-view-metric-unit {
  margin-left: 3px;
  color: #8f959e;
  font-size: 11px;
  font-weight: 400;
}

.ai-view-metric-trend {
  margin-top: 2px;
  color: #8f959e;
  font-size: 11px;
}

/* ============ 图表 ============ */
.ai-view-chart {
  display: block;
  width: 100%;
  margin-top: 6px;
}

.ai-view-chart-grid {
  stroke: #eef0f3;
  stroke-width: 1;
}

.ai-view-chart-axis {
  fill: #a9aeb8;
  font-size: 9px;
}

.ai-view-chart-total {
  fill: #4e5969;
  font-size: 10px;
}

.ai-view-chart-label {
  fill: #6b7280;
  font-size: 9px;
}

.ai-view-legend {
  margin-top: 4px;
  display: flex;
  flex-wrap: wrap;
  gap: 4px 14px;
}

.ai-view-legend-item {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  color: #6b7280;
  font-size: 11px;
}

.ai-view-legend-dot {
  width: 8px;
  height: 8px;
  border-radius: 2px;
}

/* ============ 清单与文件 ============ */
.ai-view-list {
  margin: 8px 0 0;
  padding-left: 18px;
  max-height: 260px;
  overflow-y: auto;
  color: #1f2329;
  font-size: 12px;
  line-height: 1.7;
}

.ai-view-list li {
  margin: 2px 0;
  word-break: break-word;
}

.ai-view-files {
  margin-top: 8px;
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.ai-view-file {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 6px 10px;
  border: 1px solid #eef0f3;
  border-radius: 8px;
  color: #1f2329;
  font-size: 12px;
  text-decoration: none;
}

.ai-view-file:hover {
  border-color: #cfe0ff;
  background: #f7faff;
}

.ai-view-file-name {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.ai-view-file-size {
  margin-left: auto;
  color: #a9aeb8;
  font-size: 11px;
}

/* ============ 文本与提示 ============ */
.ai-view-text {
  margin-top: 8px;
  color: #1f2329;
  font-size: 12px;
  line-height: 1.6;
  white-space: pre-wrap;
  word-break: break-word;
}

.ai-view-tip {
  margin-top: 6px;
  color: #a9aeb8;
  font-size: 11px;
  text-align: center;
}

/* ============ 紧凑模式（全局搜索面板） ============ */
.ai-view.is-compact {
  font-size: 12px;
}

.ai-view.is-compact .ai-view-summary {
  margin-top: 6px;
  padding: 5px 8px;
}

.ai-view.is-compact .ai-view-table-wrap {
  margin-top: 8px;
  max-height: 240px;
  border-color: #e8edf7;
  border-radius: 10px;
  background: #fff;
}

.ai-view.is-compact .ai-view-table {
  font-size: 12.5px;
}

.ai-view.is-compact .ai-view-table th,
.ai-view.is-compact .ai-view-table td {
  max-width: 160px;
  padding: 7px 10px;
  border-bottom-color: #eff2f8;
}

.ai-view.is-compact .ai-view-table th {
  background: #f7f9fd;
  color: #5b6579;
}

.ai-view.is-compact .ai-view-table tbody tr:nth-child(even) td {
  background: #fbfcfe;
}

.ai-view.is-compact .ai-view-metric {
  padding: 8px 10px;
}

.ai-view.is-compact .ai-view-metric-value {
  font-size: 17px;
}

/* ============ 滚动条 ============ */
.ai-view-table-wrap::-webkit-scrollbar,
.ai-view-list::-webkit-scrollbar,
.ai-view-code::-webkit-scrollbar {
  width: 8px;
  height: 8px;
}

.ai-view-table-wrap::-webkit-scrollbar-thumb,
.ai-view-list::-webkit-scrollbar-thumb,
.ai-view-code::-webkit-scrollbar-thumb {
  background-color: rgba(148, 163, 184, 0.24);
  border: 2px solid transparent;
  background-clip: padding-box;
  border-radius: 8px;
}

.ai-view-table-wrap::-webkit-scrollbar-thumb:hover,
.ai-view-list::-webkit-scrollbar-thumb:hover,
.ai-view-code::-webkit-scrollbar-thumb:hover {
  background-color: rgba(148, 163, 184, 0.45);
}

.ai-view-table-wrap::-webkit-scrollbar-track,
.ai-view-list::-webkit-scrollbar-track,
.ai-view-code::-webkit-scrollbar-track {
  background: transparent;
}

.ai-view-table-wrap::-webkit-scrollbar-corner {
  background: transparent;
}
</style>
