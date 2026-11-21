<template>
  <div ref="rootEl" class="markdown-body" v-html="html"></div>
</template>

<script setup lang="ts">
import { computed, nextTick, onMounted, ref, watch } from 'vue'
import { renderMarkdown, renderMermaidBlocks } from '@/utils/markdown'

const props = defineProps<{
  text: string
  /** 流式过程中只渲染文本，结束后再渲染 mermaid，避免半截语法反复报错 */
  streaming?: boolean
}>()

const rootEl = ref<HTMLElement | null>(null)
const html = computed(() => renderMarkdown(props.text))

function renderDiagrams() {
  if (props.streaming || !rootEl.value) return
  renderMermaidBlocks(rootEl.value).catch(() => {
    // 图表库加载失败时保留源码展示，不影响正文
  })
}

onMounted(renderDiagrams)

watch([html, () => props.streaming], () => {
  if (props.streaming) return
  nextTick(renderDiagrams)
})
</script>
