<template>
    <!-- 菜单搜索框：点击菜单入口后由图标侧向推出 -->
    <div class="gs-panel" :class="['gs-' + horizontal, { 'is-expanded': expanded }]">
        <input ref="inputRef" v-model="keyword" class="gs-input" type="text" placeholder="请输入菜单名称" autocomplete="off"
            @input="onInput" @keydown="onKeydown">
    </div>

    <!-- 菜单搜索结果（浮层，靠图标一侧展开） -->
    <div v-if="showResult" ref="resultRef" class="gs-result" :class="['gs-' + horizontal, 'gs-' + vertical]"
        :style="{ width: resultWidth + 'px' }">
        <div class="gs-result-header">
            <span class="gs-result-title">搜索结果</span>
            <span class="gs-result-count">共 {{ resultList.length }} 项</span>
        </div>
        <div v-for="(item, index) in resultList" :key="item.id || item.url || index" class="gs-result-item"
            :class="{ active: index === selectedIndex }" @mouseenter="selectedIndex = index" @mousedown.prevent
            @click="openMenu(item)">
            <span class="gs-result-name" v-html="highlightName(item.name)"></span>
            <span v-if="item.module" class="gs-result-module" :title="item.module">{{ item.module }}</span>
        </div>
        <div v-if="resultList.length === 0" class="gs-result-empty">未找到相关菜单，请更换关键词重试</div>
    </div>
</template>

<script setup>
import { computed, nextTick, ref, watch } from 'vue'
import { match } from 'pinyin-pro'
import http from '@/js/httpProxy.js'

const props = defineProps({
    expanded: { type: Boolean, default: false },
    horizontal: { type: String, default: 'right' },
    vertical: { type: String, default: 'below' },
    resultWidth: { type: Number, default: 360 }
})

const emit = defineEmits(['select', 'close'])

const inputRef = ref(null)
const resultRef = ref(null)
const keyword = ref('')
const selectedIndex = ref(-1)
const menus = ref(null)

let menusLoading = false

function ensureMenus() {
    if (menus.value || menusLoading) return
    menusLoading = true
    http.get('/myMenu')
        .then(({ data }) => {
            const d = data && data.data
            if (!d) return
            menus.value = {
                sys: Array.isArray(d.sys) ? d.sys : [],
                menuList: Array.isArray(d.menuList) ? d.menuList : []
            }
        })
        .catch(() => {
            // 加载失败时保持未加载状态，下次展开重试
        })
        .finally(() => {
            menusLoading = false
        })
}

// 与 allMenu 搜索一致：名称包含或拼音匹配，仅返回 PAGE 节点
function deepSearch(nodes, title, out) {
    if (!Array.isArray(nodes)) return
    for (const node of nodes) {
        if (!node) continue
        const name = node.name || ''
        if (node.type === 'PAGE' && (name.includes(keyword.value) || match(name, keyword.value))) {
            out.push({ ...node, module: title })
        }
        if (node.children && node.children.length > 0) {
            const childTitle = node.type === 'MODULE' ? node.name : title
            deepSearch(node.children, childTitle, out)
        }
    }
}

const resultList = computed(() => {
    const kw = keyword.value
    if (!menus.value || !kw || kw.length < 2) return []
    const out = []
    // 不搜索“我的常用”：其中的菜单均来自其他应用/模块，与下方数据重复
    deepSearch(menus.value.sys, '系统/应用', out)
    deepSearch(menus.value.menuList, undefined, out)
    return out
})

const showResult = computed(() => props.expanded && !!menus.value && keyword.value.length >= 2)

function highlightName(name) {
    const kw = keyword.value
    if (!kw || !name || !name.includes(kw)) return name
    const idx = name.indexOf(kw)
    return name.slice(0, idx) + '<em class="gs-hl">' + name.slice(idx, idx + kw.length) + '</em>' + name.slice(idx + kw.length)
}

function scrollSelectedIntoView() {
    const container = resultRef.value
    if (!container || selectedIndex.value < 0) return
    const el = container.querySelectorAll('.gs-result-item')[selectedIndex.value]
    if (!el) return
    const cRect = container.getBoundingClientRect()
    const eRect = el.getBoundingClientRect()
    if (eRect.top < cRect.top) {
        container.scrollTop = Math.max(0, container.scrollTop - (cRect.top - eRect.top))
    } else if (eRect.bottom > cRect.bottom) {
        container.scrollTop += eRect.bottom - cRect.bottom
    }
}

function onInput() {
    selectedIndex.value = -1
}

function onKeydown(event) {
    if (event.key === 'Escape') {
        event.stopPropagation()
        emit('close')
        return
    }
    const list = resultList.value
    if (list.length === 0) return
    if (event.key === 'ArrowDown') {
        event.preventDefault()
        selectedIndex.value = selectedIndex.value < 0 ? 0 : (selectedIndex.value + 1) % list.length
        scrollSelectedIntoView()
    } else if (event.key === 'ArrowUp') {
        event.preventDefault()
        selectedIndex.value = selectedIndex.value < 0 ? list.length - 1 : (selectedIndex.value - 1 + list.length) % list.length
        scrollSelectedIntoView()
    } else if (event.key === 'Enter') {
        event.preventDefault()
        if (selectedIndex.value >= 0 && selectedIndex.value < list.length) {
            openMenu(list[selectedIndex.value])
        }
    }
}

function openMenu(menu) {
    if (!menu) return
    emit('select', menu)
}

// 面板收起后清空关键词与选中项，下次展开是干净状态
watch(() => props.expanded, (open) => {
    if (open) {
        ensureMenus()
        return
    }
    keyword.value = ''
    selectedIndex.value = -1
}, { immediate: true })

function focus() {
    nextTick(() => inputRef.value?.focus())
}

function blur() {
    inputRef.value?.blur()
}

defineExpose({ focus, blur })
</script>

<style scoped>
/* ---------- 菜单搜索 ---------- */
.gs-panel {
    position: absolute;
    top: 0;
    width: 0;
    height: 40px;
    opacity: 0;
    overflow: hidden;
    background: #fff;
    border-radius: 20px;
    box-shadow: 0 4px 16px rgba(0, 0, 0, 0.16);
    transition: width 0.45s cubic-bezier(0.25, 0.8, 0.35, 1), opacity 0.3s ease;
    pointer-events: none;
}

.gs-panel.is-expanded {
    width: var(--gs-panel-w, 300px);
    opacity: 1;
    pointer-events: auto;
}

.gs-panel.gs-right {
    left: 48px;
}

.gs-panel.gs-left {
    right: 48px;
}

.gs-input {
    width: 100%;
    height: 100%;
    box-sizing: border-box;
    padding: 0 16px;
    border: none;
    outline: none;
    background: transparent;
    font-size: 14px;
    color: #303133;
}

.gs-input::placeholder {
    color: #a8abb2;
}

.gs-result {
    position: absolute;
    max-width: calc(100vw - 24px);
    max-height: 60vh;
    overflow-y: auto;
    box-sizing: border-box;
    padding: 6px;
    background: #fff;
    border: 1px solid #ebeef5;
    border-radius: 10px;
    box-shadow: 0 8px 24px rgba(0, 0, 0, 0.12);
    animation: gsResultIn 0.15s ease-out;
}

.gs-result.gs-right {
    left: 48px;
}

.gs-result.gs-left {
    right: 48px;
}

.gs-result.gs-below {
    top: 48px;
}

.gs-result.gs-above {
    bottom: 48px;
}

@keyframes gsResultIn {
    from {
        opacity: 0;
        transform: translateY(-4px);
    }

    to {
        opacity: 1;
        transform: translateY(0);
    }
}

.gs-result-header {
    display: flex;
    align-items: center;
    justify-content: space-between;
    padding: 4px 10px 8px 10px;
}

.gs-result-title {
    font-size: 13px;
    font-weight: 600;
    color: #303133;
}

.gs-result-count {
    font-size: 12px;
    color: #909399;
}

.gs-result-item {
    display: flex;
    align-items: center;
    gap: 8px;
    padding: 7px 10px;
    border-radius: 6px;
    cursor: pointer;
    transition: background-color 0.15s;
}

.gs-result-item.active {
    background: #e6f2ff;
}

.gs-result-name {
    flex: 1;
    min-width: 0;
    font-size: 13px;
    color: #303133;
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
}

.gs-result-item.active .gs-result-name {
    color: #0078d7;
}

.gs-result-name :deep(em.gs-hl) {
    font-style: normal;
    color: #409eff;
    font-weight: 600;
}

.gs-result-module {
    flex-shrink: 0;
    padding: 1px 6px;
    font-size: 11px;
    color: #909399;
    background: #f4f4f5;
    border-radius: 4px;
    white-space: nowrap;
    max-width: 110px;
    overflow: hidden;
    text-overflow: ellipsis;
}

.gs-result-empty {
    padding: 20px 0;
    text-align: center;
    font-size: 13px;
    color: #909399;
}

.gs-result::-webkit-scrollbar {
    width: 6px;
}

.gs-result::-webkit-scrollbar-thumb {
    background: #dcdfe6;
    border-radius: 3px;
}

.gs-result::-webkit-scrollbar-thumb:hover {
    background: #c0c4cc;
}

.gs-result::-webkit-scrollbar-track {
    background: transparent;
}
</style>
