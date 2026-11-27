<template>
    <div id="userAllMenuDiv" class="main">
        <div class="menu_root">
            <div class="toolbar">
                <div style="display: inline-block; margin: 0px 8px;cursor: pointer;position: absolute;"
                    @click="close()">
                    <i class="icon32 icon_back" title="返回"></i>
                </div>
                <div>
                    <div class="search">
                        <div style="display: inline-block;">
                            <input ref="search" v-model="menuName" class="search_input" @input="searchMenuLocal()"
                                @keydown="onSearchKeydown" type="text" placeholder="请输入菜单名称"
                                autocomplete="off">
                        </div>
                        <div style="display: inline-block;vertical-align: middle; margin-left: -38px;margin-top: 6px;">
                            <i class="icon32 icon_search"></i>
                        </div>
                        <!-- 搜索结果（浮层展示，相对搜索框居中）-->
                        <div v-if="menuListSearch.length > 0" class="search_result" ref="searchResult">
                            <div class="search_result_header">
                                <span class="search_result_title">搜索结果</span>
                                <span class="search_result_count">共 {{ menuListSearch.length }} 项</span>
                            </div>
                            <div class="menu_item" :class="{ 'active': index === selectedIndex }"
                                v-for="(menu, index) in menuListSearch" :key="menu.url" @click="openMenu(menu)"
                                @mouseenter="selectedIndex = index">
                                <i v-if="menu.icon" class="icon" :class="menu.icon"></i>
                                <span class="menu_name" :title="menu.name" v-html="highlightName(menu.name)"></span>
                                <span v-if="menu.module" class="menu_module" :title="menu.module">{{ menu.module }}</span>
                                <div class="menu-actions">
                                    <div class="menu-action-btn" :class="{ 'target': !isFavorite(menu) }"
                                        :title="isFavorite(menu) ? '取消常用' : '设为常用'"
                                        @click.stop="isFavorite(menu) ? unFavMenu(menu) : favMenu(menu)">
                                        <i class="action-icon icon_fav"></i>
                                    </div>
                                    <div class="menu-action-btn" :class="{ 'target': !isHomeMenu(menu) }"
                                        :title="isHomeMenu(menu) ? '取消首页' : '设为首页'"
                                        @click.stop="setHomeAsMenu(menu)">
                                        <i class="action-icon icon_home"></i>
                                    </div>
                                </div>
                            </div>
                        </div>
                        <div v-else-if="menuName && menuName.length >= 2" class="search_result search_result_empty">
                            <div class="search_result_header">
                                <span class="search_result_title">搜索结果</span>
                                <span class="search_result_count">共 0 项</span>
                            </div>
                            <div class="search_empty">未找到相关菜单，请更换关键词重试</div>
                        </div>
                    </div>
                    <!-- <div class="close" @click="close()" title="关闭">
                        <i class="icon32 icon_close"></i>
                    </div> -->
                </div>
            </div>
            <div class="content">
                <div v-if="menus.favorite && menus.favorite.length > 0">
                    <div class="title">我的常用</div>
                    <div class="menu_list">
                        <div class="menu_item" v-for="menu in menus.favorite" @click="openMenu(menu)">
                            <i class="icon" :class="menu.icon"></i><span class="menu_name">{{ menu.name }}</span>
                            <div class="menu-actions">
                                <div class="menu-action-btn" title="取消常用" @click.stop="unFavMenu(menu)">
                                    <i class="action-icon icon_fav"></i>
                                </div>
                                <div class="menu-action-btn" :class="{ 'target': !isHomeMenu(menu) }"
                                    :title="isHomeMenu(menu) ? '取消首页' : '设为首页'"
                                    @click.stop="setHomeAsMenu(menu)">
                                    <i class="action-icon icon_home"></i>
                                </div>
                            </div>
                        </div>
                    </div>
                </div>
                <div v-if="menus.sys && menus.sys.length > 0">
                    <div class="title">系统/应用</div>
                    <div class="menu_list">
                        <div class="menu_item" v-for="menu in menus.sys" @click="openMenu(menu)">
                            <i class="icon" :class="menu.icon"></i><span class="menu_name">{{ menu.name }}</span>
                            <div class="menu-actions">
                                <div class="menu-action-btn" :class="{ 'target': !isFavorite(menu) }"
                                    :title="isFavorite(menu) ? '取消常用' : '设为常用'"
                                    @click.stop="isFavorite(menu) ? unFavMenu(menu) : favMenu(menu)">
                                    <i class="action-icon icon_fav"></i>
                                </div>
                                <div class="menu-action-btn" :class="{ 'target': !isHomeMenu(menu) }"
                                    :title="isHomeMenu(menu) ? '取消首页' : '设为首页'"
                                    @click.stop="setHomeAsMenu(menu)">
                                    <i class="action-icon icon_home"></i>
                                </div>
                            </div>
                        </div>
                    </div>
                </div>
                <div class="first_menu" v-for="first in menus.menuList" :key="first.name">
                    <div class="title">{{ first.name }}</div>
                    <div class="second_menu_group" v-for="(grp, gi) in groupPages(first)" :key="gi">
                        <div v-if="grp.path" class="menu_name">
                            <i class="icon icon16 icon_module"></i><span class="group_title_text">{{ grp.path
                                }}</span>
                        </div>
                        <div class="third_menu" v-for="third in grp.pages" :key="third.id" @click="openMenu(third)">
                            <span class="menu_name">{{ third.name }}</span>
                            <div class="menu-actions">
                                <div class="menu-action-btn" :class="{ 'target': !isFavorite(third) }"
                                    :title="isFavorite(third) ? '取消常用' : '设为常用'"
                                    @click.stop="isFavorite(third) ? unFavMenu(third) : favMenu(third)">
                                    <i class="action-icon icon_fav"></i>
                                </div>
                                <div class="menu-action-btn" :class="{ 'target': !isHomeMenu(third) }"
                                    :title="isHomeMenu(third) ? '取消首页' : '设为首页'"
                                    @click.stop="setHomeAsMenu(third)">
                                    <i class="action-icon icon_home"></i>
                                </div>
                            </div>
                        </div>
                    </div>
                </div>
            </div>
        </div>
    </div>
</template>


<script setup>
import { onActivated, onDeactivated, onMounted, onUnmounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import http from '@/js/httpProxy.js'
import { match } from 'pinyin-pro';
import { storeToRefs } from 'pinia'
import { useCurrentPageStore } from '@/stores/menuStore'

const currentPageStore = useCurrentPageStore()
const { userMenuFull, favorite } = storeToRefs(currentPageStore)

const router = useRouter()

const menus = ref([])

const search = ref(null)
const menuName = ref(null)
const menuListSearch = ref([])
const selectedIndex = ref(-1)
const searchResult = ref(null)

// KeepAlive include 机制下，异步组件首次渲染（未加载完成）无法匹配组件名而不走缓存，onActivated 不触发；
// 用 onMounted/onUnmounted 兜底保证任何渲染路径都执行初始化/清理。
// 缓存路径下挂载时 onMounted 与 onActivated 连触发，用 mountedOnce 标志去重。
let mountedOnce = false
onMounted(() => {
    mountedOnce = true
    initAllMenu()
})

onActivated(() => {
    if (!mountedOnce) {
        initAllMenu()
    }
    mountedOnce = false
})

function initAllMenu() {
    console.log('allMenu mounted')
    search.value.focus()
    window.addEventListener('keyup', close)
    document.addEventListener('click', handleGlobalClick)
    currentPageStore.toolbarVisible = false
    loadMenu()
}

let unmountedOnce = false
onUnmounted(() => {
    unmountedOnce = true
    cleanupAllMenu()
})

onDeactivated(() => {
    if (!unmountedOnce) {
        cleanupAllMenu()
    }
    unmountedOnce = false
})

function cleanupAllMenu() {
    menuName.value = ""
    menuListSearch.value = []
    selectedIndex.value = -1
    window.removeEventListener('keyup', close)
    document.removeEventListener('click', handleGlobalClick)
    currentPageStore.toolbarVisible = true
    // 刷新top.vue中的数据
    loadMenuForTop()
}

function openMenu(menu) {
    currentPageStore.justCloseUserMenu()
    currentPageStore.showPage(menu)
    currentPageStore.setShowIndex(false)
    router.push("/main")
}

function showHome() {
    currentPageStore.showPage(favorite.value.index, true)
    currentPageStore.setShowIndex(true)
    router.push({ path: "/main" })
}

// 点击搜索结果/输入框外部区域时收起搜索面板
function handleGlobalClick(event) {
    if (!event.target.closest || event.target.closest('.search')) {
        return
    }
    // 排除 Element Plus 弹层，避免干扰弹层自身的关闭逻辑
    if (event.target.closest('.el-popper')) {
        return
    }
    menuName.value = ""
    menuListSearch.value = []
    selectedIndex.value = -1
}

function close(event) {
    if (event && event.key !== 'Escape') {
        return
    }
    console.log('close triggered', event, new Error().stack)
    currentPageStore.del(userMenuFull.value)
    if (currentPageStore.pageList.length === 0) {
        currentPageStore.showPage(favorite.value.index, true)
        currentPageStore.setShowIndex(true)
        router.push({ path: "/main" })
    }
}

// 高亮名称中直接命中的关键词（拼音命中的场景不做高亮）
function highlightName(name) {
    const keyword = menuName.value
    if (!keyword || !name || !name.includes(keyword)) {
        return name
    }
    const idx = name.indexOf(keyword)
    return name.slice(0, idx) + '<em class="highlight">' + name.slice(idx, idx + keyword.length) + '</em>' + name.slice(idx + keyword.length)
}

function searchMenuLocal() {

    selectedIndex.value = -1

    if (!menuName.value || menuName.value.length === 0) {
        menuListSearch.value = []
        return
    }

    if (!menuName.value || menuName.value.length < 2) {
        return
    }

    menuListSearch.value = []

    deepSearch(menus.value.favorite, "我的常用")

    deepSearch(menus.value.sys, "系统/应用")

    deepSearch(menus.value.menuList)

}

function onSearchKeydown(event) {
    if (menuListSearch.value.length === 0) {
        return
    }
    switch (event.key) {
        case 'ArrowDown':
            event.preventDefault()
            moveVertical(1)
            break
        case 'ArrowUp':
            event.preventDefault()
            moveVertical(-1)
            break
        case 'ArrowLeft':
            event.preventDefault()
            moveHorizontal(-1)
            break
        case 'ArrowRight':
            event.preventDefault()
            moveHorizontal(1)
            break
        case 'Enter':
            event.preventDefault()
            if (selectedIndex.value >= 0 && selectedIndex.value < menuListSearch.value.length) {
                openMenu(menuListSearch.value[selectedIndex.value])
            }
            break
    }
}

// 左右方向键：按相邻顺序移动选择（inline-block 布局下自然跨行）
function moveHorizontal(dir) {
    const total = menuListSearch.value.length
    if (total === 0) return
    if (selectedIndex.value === -1) {
        selectedIndex.value = dir > 0 ? 0 : total - 1
    } else {
        const next = selectedIndex.value + dir
        if (next < 0 || next >= total) return
        selectedIndex.value = next
    }
    scrollSelectedIntoView()
}

// 上下方向键：按行移动选择，在目标行中选取水平位置最近的项
function moveVertical(dir) {
    const items = searchResult.value ? searchResult.value.querySelectorAll('.menu_item') : []
    if (!items || items.length === 0) return
    if (selectedIndex.value === -1) {
        selectedIndex.value = dir > 0 ? 0 : items.length - 1
        scrollSelectedIntoView()
        return
    }
    const currentRect = items[selectedIndex.value].getBoundingClientRect()
    const currentCenter = currentRect.left + currentRect.width / 2
    // 以行 top 为 key 收集各项所在行，并找出距离当前项最近的目标行
    const rows = new Map()
    let targetTop = null
    for (let i = 0; i < items.length; i++) {
        if (i === selectedIndex.value) continue
        const rect = items[i].getBoundingClientRect()
        if (dir > 0 && rect.top > currentRect.top + 1) {
            if (targetTop === null || rect.top < targetTop) targetTop = rect.top
        } else if (dir < 0 && rect.top < currentRect.top - 1) {
            if (targetTop === null || rect.top > targetTop) targetTop = rect.top
        }
        const key = Math.round(rect.top)
        if (!rows.has(key)) rows.set(key, [])
        rows.get(key).push(i)
    }
    if (targetTop === null) return
    const rowIndices = rows.get(Math.round(targetTop)) || []
    let best = -1
    let bestDist = Infinity
    for (const i of rowIndices) {
        const rect = items[i].getBoundingClientRect()
        const dist = Math.abs(rect.left + rect.width / 2 - currentCenter)
        if (dist < bestDist) {
            bestDist = dist
            best = i
        }
    }
    if (best >= 0) {
        selectedIndex.value = best
        scrollSelectedIntoView()
    }
}

// 确保选中项在搜索结果区域内可见（区域可滚动时）
function scrollSelectedIntoView() {
    const container = searchResult.value
    if (!container || selectedIndex.value < 0) return
    const items = container.querySelectorAll('.menu_item')
    const el = items[selectedIndex.value]
    if (!el) return
    const cRect = container.getBoundingClientRect()
    const eRect = el.getBoundingClientRect()
    if (eRect.top < cRect.top) {
        container.scrollTop = Math.max(0, container.scrollTop - (cRect.top - eRect.top))
    } else if (eRect.bottom > cRect.bottom) {
        container.scrollTop += (eRect.bottom - cRect.bottom)
    }
}

// 将模块的菜单树按“目录层级”归一为分组列表，供抽屉渲染。
// 抽屉模板只支持「模块 → 分组标题 → 页面」两层，但低代码同步可能产出「目录套目录」
// （如 外出 → 国内外出 → 测试1），页面落在第四层会被丢弃。这里递归遍历：
// 每个页面按其祖先目录链归入一个分组，分组标题为完整路径（如“外出 / 国内外出”），
// 既保证任意深度下的页面都能可见、可点击，又忠实还原 wform 侧的层级归属。
// 直接挂在模块下的页面（无目录祖先）归为 path 为空的分组，不显示分组标题。
// 不变量：低代码侧（wform MenuSyncItemAssembler）已保证只下发页面所在目录且不带父级，
// 即 PAGE 的祖先 DIR ≤ 1 层；本函数仍保留递归，是为了兼容平台管理员手工配置的深层目录，
// 不是为低代码菜单兜底，不要据此反向放宽低代码侧的层级基线。
function groupPages(module) {
    const groups = []
    const directPages = []
    const findOrCreate = (path) => {
        let g = groups.find(x => x.path === path)
        if (!g) {
            g = { path, pages: [] }
            groups.push(g)
        }
        return g
    }
    const walk = (node, pathArr) => {
        for (const child of node.children || []) {
            if (child.type === 'PAGE') {
                if (pathArr.length === 0) {
                    directPages.push(child)
                } else {
                    findOrCreate(pathArr.join(' / ')).pages.push(child)
                }
            } else if (child.type === 'DIR') {
                walk(child, [...pathArr, child.name])
            }
        }
    }
    walk(module, [])
    const result = []
    if (directPages.length) {
        result.push({ path: '', pages: directPages })
    }
    result.push(...groups)
    return result
}

function deepSearch(menuList, title) {
    if (menuList && menuList.length === 0) {
        return;
    }
    for (let i = 0; i < menuList.length; i++) {
        if (menuList[i].type === "PAGE" && (menuList[i].name.includes(menuName.value) || match(menuList[i].name, menuName.value))) {
            menuList[i].module = title
            menuListSearch.value.push(menuList[i])
        }
        if (menuList[i].children && menuList[i].children.length > 0) {
            // 使用局部变量避免修改外层 title，确保兄弟节点使用正确的模块名
            let childTitle = title
            if (menuList[i].type === "MODULE") {
                childTitle = menuList[i].name
            }
            deepSearch(menuList[i].children, childTitle)
        }
    }
}

function loadMenu() {
    http.get("/myMenu")
        .then(({ data }) => {
            const d = data.data;
            if (d && Array.isArray(d.favorite)) {
                // 过滤历史收藏中菜单已被删除等产生的空项，避免 v-for 渲染到 undefined 时读取 .icon 报错
                d.favorite = d.favorite.filter(item => item && item.url);
            }
            menus.value = d;
        });

}

function favMenu(menu) {
    if (!menu || !menu.id) {
        console.warn('Invalid menu provided to favMenu');
        return;
    }

    http.post("/myMenu/saveFav", { menuId: menu.id })
        .then(({ data }) => {
            ElMessage("收藏成功");
            // 重新加载菜单以更新视图
            loadMenu();
            // 同步更新top.vue中的数据
            loadMenuForTop();
        });
}

function unFavMenu(menu) {
    if (!menu || !menu.id) {
        console.warn('Invalid menu provided to unFavMenu');
        return;
    }

    http.post("/myMenu/unstar?menuId=" + menu.id)
        .then(({ data }) => {
            ElMessage("取消收藏成功");
            // 重新加载菜单以更新视图
            loadMenu();
            // 同步更新top.vue中的数据
            loadMenuForTop();
        });
}

function setHomeAsMenu(menu) {
    if (!menu || !menu.id) {
        console.warn('Invalid menu provided to setHomeAsMenu');
        return;
    }

    // 已是首页则取消（取消后回落到默认欢迎页），否则设为当前首页
    const cancel = isHomeMenu(menu)
    const url = (cancel ? "/myMenu/unstarIndex?menuId=" : "/myMenu/favIndex?menuId=") + menu.id
    http.post(url)
        .then(({ data }) => {
            ElMessage(cancel ? "已取消首页" : "已设置为首页");
            // 首页状态存在 store（favorite.index）中，刷新后图标与 tooltip 才会跟着变
            loadMenuForTop();
        })
        .catch(error => {
            console.error(cancel ? '取消首页失败:' : '设置首页失败:', error);
        });
}

function isFavorite(menu) {
    if (!menu) return false;
    if (!menus.value || !menus.value.favorite) return false;
    return menus.value.favorite.some(fav => sameMenu(fav, menu));
}

function isHomeMenu(menu) {
    if (!menu) return false;
    const index = favorite.value && favorite.value.index;
    return !!index && sameMenu(index, menu);
}

// 同一菜单判定：优先比主键（收藏/首页后端存的是 menu_id），
// 同 url 的不同菜单（子应用页面被多次同步）不会互相牵制；id 缺失时退回 url
function sameMenu(a, b) {
    if (!a || !b) return false;
    if (a.id && b.id) return a.id === b.id;
    return !!(a.url && a.url === b.url);
}

function loadMenuForTop() {
    http.get("/myMenu/userFav")
        .then((resp) => {
            currentPageStore.setFavorite(resp.data.data)
        })
}

</script>

<style>
/* 所有菜单页铺满视口：脱离 layout 的 100vw/100vh + padding 叠盒（其滚动条会被 layout-wrap 的
   overflow:hidden 裁掉），因此自身铺满并自持滚动。底部不留 margin/padding：留白放在 .content 的
   滚动区内，既保留底部间距又不露出页面外的白色画布（否则会出现一条“空白条”）。
   顶部返回/搜索栏固定不跟随滚动。 */
.main {
    background-color: #F5F5F5;
    position: fixed;
    top: 0;
    right: 0;
    bottom: 0;
    left: 0;
    display: flex;
    flex-direction: column;
    overflow: hidden;
    box-sizing: border-box;
    z-index: 99999999;
}

.menu_root {
    flex: 1 1 auto;
    min-height: 0;
    display: flex;
    flex-direction: column;
}

.content {
    flex: 1 1 auto;
    min-height: 0;
    overflow-y: auto;
    overflow-x: hidden;
    padding: 0 10px 8px 10px;
    box-sizing: border-box;
}

.content::-webkit-scrollbar {
    width: 8px;
}

.content::-webkit-scrollbar-thumb {
    background: #dcdfe6;
    border-radius: 4px;
}

.content::-webkit-scrollbar-thumb:hover {
    background: #c0c4cc;
}

.content::-webkit-scrollbar-track {
    background: transparent;
}

.toolbar {
    flex: 0 0 auto;
    padding-top: 10px;
    height: 60px;
    box-sizing: border-box;
}

/* 搜索区域必须是独立层：.search 为 fixed（z-index:auto）时它与内容里 .third_menu/.menu_item
   这类 position:relative 无 z-index 元素同层、按 DOM 顺序绘制，位于 .toolbar 之后的
   内容会把搜索结果面板盖住（表现为“面板与菜单重影”）。显式抬高 z-index 让整块浮在最上层。 */
.search {
    position: fixed;
    right: calc(50% - 150px);
    z-index: 1000;
    transition: all 0.3s ease-in-out;
}

.search_result {
    position: absolute;
    top: 45px;
    left: 50%;
    transform: translateX(-50%);
    width: 640px;
    max-width: calc(100vw - 20px);
    max-height: calc(60vh);
    background-color: white;
    border-radius: 10px;
    overflow-y: auto;
    box-shadow: 0 8px 24px rgba(0, 0, 0, 0.12);
    border: 1px solid #ebeef5;
    z-index: 1000;
    padding-bottom: 10px;
    animation: searchResultIn 0.15s ease-out;
}

@keyframes searchResultIn {
    from {
        opacity: 0;
        transform: translateX(-50%) translateY(-6px);
    }
    to {
        opacity: 1;
        transform: translateX(-50%) translateY(0);
    }
}

.search_result_header {
    position: sticky;
    top: 0;
    z-index: 1;
    display: flex;
    align-items: center;
    justify-content: space-between;
    padding: 10px 16px;
    margin-bottom: 4px;
    background: #fafafa;
    border-bottom: 1px solid #f0f0f0;
    border-radius: 10px 10px 0 0;
}

.search_result_title {
    font-size: 14px;
    font-weight: 600;
    color: #303133;
}

.search_result_count {
    font-size: 12px;
    color: #909399;
}

.search_result .menu_item {
    display: inline-flex;
    vertical-align: top;
    align-items: center;
    /* 右侧预留操作按钮空间，避免悬浮显示按钮时条目宽度变化导致换行跳动 */
    padding: 6px 58px 6px 12px;
    margin: 3px 4px;
    border-radius: 8px;
    cursor: pointer;
    transition: background-color 0.15s ease;
    white-space: nowrap;
}

.search_result .menu_item:hover {
    background-color: #f5f7fa;
}

.search_result .menu_item.active {
    background-color: #e6f2ff;
}

/* 搜索结果内的常用/首页操作：常驻占位、悬浮淡入。
   不复用列表的 top:-22px 浮层定位，避免在换行排布中遮挡相邻条目或被面板滚动区裁切。 */
.search_result .menu-actions {
    display: flex;
    top: 50%;
    right: 2px;
    transform: translateY(-50%);
    background: transparent;
    box-shadow: none;
    opacity: 0;
    /* 未悬浮时按钮不可见，需同时禁用点击，避免误触静默收藏/设置首页 */
    pointer-events: none;
    transition: opacity 0.15s ease;
}

.search_result .menu_item:hover .menu-actions {
    opacity: 1;
    pointer-events: auto;
}

.search_result .menu_item .icon {
    display: inline-block;
    width: 18px;
    height: 18px;
    margin-right: 10px;
    flex-shrink: 0;
}

.search_result .menu_name {
    margin: 0;
    padding: 0;
    font-size: 14px;
    color: #303133;
    white-space: nowrap;
    overflow: hidden;
    text-overflow: ellipsis;
    max-width: 240px;
}

.search_result .menu_name em.highlight {
    font-style: normal;
    color: #409eff;
    font-weight: 600;
}

.search_result .menu_item:hover .menu_name {
    color: #409eff;
}

.search_result .menu_module {
    flex-shrink: 0;
    margin-left: 8px;
    padding: 1px 6px;
    font-size: 12px;
    color: #909399;
    background: #f4f4f5;
    border-radius: 4px;
    white-space: nowrap;
    overflow: hidden;
    text-overflow: ellipsis;
    max-width: 120px;
}

.search_result_empty {
    padding-bottom: 0;
}

.search_empty {
    padding: 24px 0;
    text-align: center;
    font-size: 13px;
    color: #909399;
}

.search_result::-webkit-scrollbar {
    width: 6px;
}

.search_result::-webkit-scrollbar-thumb {
    background: #dcdfe6;
    border-radius: 3px;
}

.search_result::-webkit-scrollbar-thumb:hover {
    background: #c0c4cc;
}

.search_result::-webkit-scrollbar-track {
    background: transparent;
}

.search_input {
    width: 350px;
    height: 38px;
    font-size: 23px;
    line-height: 30px;
    padding: 4px 10px;
    outline: none;
    border-radius: 15px;
    border: 1px solid gray;
    transition: transform 0.3s ease-in-out;
}

.search_input:hover {
    transform: scale(1.08);
}

.close {
    float: right;
    cursor: pointer;
    margin-right: 10px;
}

.title {
    font-size: 22px;
    margin: 15px 5px 5px 5px;
}

.icon {
    width: 16px;
    height: 16px;
    vertical-align: middle;
    display: none;
    /* inline-block; */
    background-size: contain;
}

/* 常用图标：目标=设为常用（当前未收藏）金色实心星；目标=取消常用（当前已收藏）灰色空心星 */
.menu-action-btn .action-icon {
    width: 14px;
    height: 14px;
    background-size: contain;
    background-repeat: no-repeat;
    background-position: center;
    pointer-events: none;
}

.menu-action-btn .icon_fav {
    width: 12px;
    height: 12px;
    background-image: url("@/assets/icon/收藏-outline.svg");
}

.menu-action-btn.target .icon_fav {
    background-image: url("@/assets/icon/收藏.svg");
}

/* 首页图标：目标=设为首页（当前未设置）金色房子；目标=取消首页（当前已设置）灰色房子 */
.menu-action-btn .icon_home {
    background-image: url("@/assets/icon/主页收藏-outline.svg");
}

.menu-action-btn.target .icon_home {
    background-image: url("@/assets/icon/主页收藏.svg");
}

/* .icon1 {
    background-image: url("@/assets/icon/icon.jpg")
} */

.menu_list {
    padding-left: 22px;
}

.menu_item {
    display: inline-block;
    margin: 5px 5px;
    cursor: pointer;
}

.menu_name {
    padding-left: 5px;
    font-size: 16px;
    text-align: center;
    margin-bottom: 10px;
}

.second_menu_group {
    margin: 10px 0px;
    padding-left: 25px;
    display: inline-block;
    text-align: center;
}

/* 分组标题（wform 同步出的模块/目录名，如“QA模块114855”）：与下方 .third_menu 行居中，
   使模块名显示在其菜单的正上方中央；此前强制左对齐会与居中的菜单行错位。 */
.second_menu_group > .menu_name {
    padding-left: 0;
    display: flex;
    align-items: center;
    justify-content: center;
    gap: 5px;
    text-align: center;
    font-size: 14px;
    font-weight: 600;
    color: #606266;
    margin-bottom: 6px;
}

.second_menu {
    margin: 10px 0px;
    text-align: center;
    font-size: 18px;
}

.third_menu {
    margin: 5px;
    cursor: pointer;
    display: inline-block;
    position: relative;
}


.menu_item {
    display: inline-block;
    margin: 5px 5px;
    cursor: pointer;
    position: relative;
}

.menu_item:hover .menu-actions,
.third_menu:hover .menu-actions {
    display: flex;
}

.menu-actions {
    position: absolute;
    top: -22px;
    right: -30px;
    display: none;
    background: white;
    border-radius: 5px;
    padding: 2px;
    box-shadow: 0 2px 4px rgba(0, 0, 0, 0.2);
    z-index: 10;
}

.menu-action-btn {
    width: 18px;
    height: 18px;
    display: flex;
    align-items: center;
    justify-content: center;
    border-radius: 50%;
    margin: 0 3px;
    cursor: pointer;
    background: #f0f0f0;
    transition: background-color 0.2s;
}

.menu-action-btn:hover {
    background: #e0e0e0;
}

/* 目标状态（点击后变为收藏/设为首页）：金色图标 + 浅金背景强调 */
.menu-action-btn.target {
    background: #fff3c4;
    box-shadow: 0 0 3px rgba(247, 181, 44, 0.4);
}
</style>