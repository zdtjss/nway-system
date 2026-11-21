<template>
    <div class="dict-page">
        <!-- 左栏：字典类别 -->
        <aside class="dict-side">
            <div class="side-head">
                <div class="side-title">
                    <span>字典类别</span>
                    <span class="side-count">{{ tableData.length }}</span>
                </div>
                <el-button type="primary" size="small" :icon="Plus" @click="openTypeDialog()">新增</el-button>
            </div>
            <div class="side-filter">
                <el-input v-model="queryParam.keyword" class="name-filter" placeholder="搜索字典名称或编码"
                    :prefix-icon="Search" clearable @keyup.enter="loadData()" @clear="loadData()" />
                <el-select v-model="queryParam.tenantCode" placeholder="租户" clearable style="width: 120px"
                    @change="loadData()" @clear="loadData()" filterable>
                    <el-option v-for="t in tenantOptions" :key="t.tenantCode"
                        :label="t.tenantName || t.tenantCode" :value="t.tenantCode" />
                </el-select>
            </div>
            <div v-loading="loading" class="type-list">
                <button v-for="row in tableData" :key="row.id" type="button" class="type-item"
                    :class="{ active: row.id === selectedId }" @click="selectType(row)">
                    <span class="type-item-main">
                        <span class="type-name" :title="row.dictName">{{ row.dictName }}</span>
                        <el-tag size="small" :type="row.status === 'using' ? 'success' : 'danger'">
                            {{ row.status === 'using' ? '启用' : '禁用' }}
                        </el-tag>
                    </span>
                    <span class="type-item-sub">
                        <span class="type-code" :title="row.dictCode">{{ row.dictCode }}</span>
                        <span class="type-kind">{{ isMultiField(row) ? `多字段 ${row.fieldDefs.length}` : 'Key/Value' }}</span>
                    </span>
                </button>
                <el-empty v-if="!loading && tableData.length === 0" :image-size="64"
                    :description="hasFilter ? '没有匹配的字典类别' : '暂无字典类别'">
                    <el-button v-if="hasFilter" link type="primary" @click="resetQuery">清空筛选条件</el-button>
                    <el-button v-else type="primary" :icon="Plus" @click="openTypeDialog()">新增字典类别</el-button>
                </el-empty>
            </div>
        </aside>

        <!-- 右栏：选中字典的字典项 -->
        <main class="dict-main">
            <template v-if="selectedType">
                <div class="main-head">
                    <div class="main-head-info">
                        <div class="main-title">
                            <span class="main-name">{{ selectedType.dictName }}</span>
                            <el-tag size="small" :type="selectedType.status === 'using' ? 'success' : 'danger'">
                                {{ selectedType.status === 'using' ? '启用' : '禁用' }}
                            </el-tag>
                            <el-tag size="small" type="info" effect="plain">
                                {{ isMultiField(selectedType) ? `多字段 ${selectedType.fieldDefs.length}` : 'Key/Value' }}
                            </el-tag>
                        </div>
                        <div class="main-meta">
                            <span class="meta-code">{{ selectedType.dictCode }}</span>
                            <span v-if="selectedType.tenantName">租户：{{ selectedType.tenantName }}</span>
                            <span v-if="selectedType.remark" class="meta-remark" :title="selectedType.remark">
                                备注：{{ selectedType.remark }}
                            </span>
                            <span v-if="selectedType.modifiedTime">修改于 {{ selectedType.modifiedTime }}</span>
                        </div>
                    </div>
                    <div class="main-actions">
                        <el-button :icon="Edit" @click="openTypeDialog(selectedType.id)">编辑字典</el-button>
                        <el-popconfirm width="260" title="确定删除该字典类别？需先清空其字典项。" @confirm="handleDelete(selectedType)">
                            <template #reference>
                                <el-button type="danger" plain :icon="Delete">删除</el-button>
                            </template>
                        </el-popconfirm>
                    </div>
                </div>
                <div class="main-body">
                    <dictItemList :key="itemListKey" :dict-type-id="selectedType.id"
                        :field-defs="selectedType.fieldDefs || []" :dict-code="selectedType.dictCode" />
                </div>
            </template>
            <div v-else class="main-empty">
                <el-empty description="在左侧选择一个字典类别，或先新增一个" :image-size="90">
                    <el-button type="primary" :icon="Plus" @click="openTypeDialog()">新增字典类别</el-button>
                </el-empty>
            </div>
        </main>
    </div>
    <el-dialog v-model="dialogTypeEditVisible" :title="dictTypeIdEdit ? '编辑字典类别' : '新增字典类别'" width="860px"
        destroy-on-close :close-on-click-modal="false">
        <dictTypeEdit :dict-type-id="dictTypeIdEdit" @close="onTypeDialogClose" />
    </el-dialog>
</template>

<script setup>
import { Delete, Edit, Plus, Search } from '@element-plus/icons-vue'
import { computed, onActivated, onMounted, ref } from "vue"
import { ElMessage } from 'element-plus'
import http from '@/js/httpProxy.js'
import auth from '@/js/auth.js'
import dictTypeEdit from './dictTypeEdit.vue'
import dictItemList from './dictItemList.vue'

// 与编辑对话框保持一致：超管可直接使用租户管理接口
const isSuperAdmin = auth.isSuperAdmin()

const tableData = ref([])
const loading = ref(false)
const queryParam = ref({ keyword: "", tenantCode: "" })
const tenantOptions = ref([])
const selectedId = ref("")

const dictTypeIdEdit = ref("")
const dialogTypeEditVisible = ref(false)

const selectedType = computed(() => tableData.value.find(t => t.id === selectedId.value) || null)
const hasFilter = computed(() => !!queryParam.value.keyword || !!queryParam.value.tenantCode)

// 字段定义变化时重建字典项组件，避免沿用旧的查询条件与编辑表单；仅名称/状态变化时不重建
const itemListKey = computed(() => {
    const type = selectedType.value
    if (!type) return "none"
    const signature = (type.fieldDefs || [])
        .map(f => [f.code, f.name, f.type, f.required ? 1 : 0, f.searchable ? 1 : 0, f.sort].join(':'))
        .join('|')
    return type.id + '|' + signature
})

// KeepAlive include 机制下，异步组件首次渲染（未加载完成）无法匹配组件名而不走缓存，onActivated 不触发；
// 用 onMounted 兜底保证任何渲染路径都执行加载。缓存路径下 onMounted 与 onActivated 连触发，用标志去重。
let mountedOnce = false
onMounted(() => {
    mountedOnce = true
    loadTenantOptions()
    loadData()
})
onActivated(() => {
    if (!mountedOnce) {
        loadData()
    }
    mountedOnce = false
})

async function loadData(prefer) {
    loading.value = true
    try {
        const { data } = await http.get("/dict/queryType", {
            params: { keyword: queryParam.value.keyword, tenantCode: queryParam.value.tenantCode }
        })
        if (data.code === 200) {
            tableData.value = data.data || []
            ensureSelection(prefer)
        }
    } catch {
        // 错误提示由请求拦截器统一处理
    } finally {
        loading.value = false
    }
}

// 保持当前选中；选中项被筛选掉或不存在时回落到第一项（空列表则清空选中）
function ensureSelection(prefer) {
    const list = tableData.value
    if (list.length === 0) {
        selectedId.value = ""
        return
    }
    if (prefer && (prefer.id || prefer.dictCode)) {
        const match = list.find(t => (prefer.id && t.id === prefer.id) || (!prefer.id && t.dictCode === prefer.dictCode))
        if (match) {
            selectedId.value = match.id
            return
        }
    }
    if (!list.some(t => t.id === selectedId.value)) {
        selectedId.value = list[0].id
    }
}

function selectType(row) {
    selectedId.value = row.id
}

function isMultiField(row) {
    return (row.fieldDefs || []).length > 0
}

// 超管与编辑对话框一致走 tenant/queryAll；其他用户走字典接口按权限范围取可读租户
async function loadTenantOptions() {
    try {
        const { data } = await http.get(isSuperAdmin ? "/tenant/queryAll" : "/dict/tenantOptions")
        if (data.code === 200) {
            tenantOptions.value = data.data || []
        }
    } catch {
        // 错误提示由请求拦截器统一处理
    }
}

function resetQuery() {
    queryParam.value = { keyword: "", tenantCode: "" }
    loadData()
}

function openTypeDialog(id) {
    dictTypeIdEdit.value = id || ""
    dialogTypeEditVisible.value = true
}

function onTypeDialogClose(saved) {
    dialogTypeEditVisible.value = false
    if (!saved) return
    // 新增后选中新字典（按编码匹配），编辑后按 id 保持选中
    loadData({ id: saved.id, dictCode: saved.dictCode })
}

async function handleDelete(row) {
    try {
        const { data } = await http.post('/dict/deleteType?id=' + row.id)
        if (data.code === 200) {
            ElMessage.success('删除成功')
            if (selectedId.value === row.id) {
                selectedId.value = ""
            }
            loadData()
        }
    } catch {
        // 错误提示由请求拦截器统一处理
    }
}
</script>

<style scoped>
.dict-page {
    box-sizing: border-box;
    display: flex;
    gap: 12px;
    width: 98%;
    min-height: 520px;
    height: calc(100vh - 44px);
    margin: 10px auto;
}

.dict-side {
    display: flex;
    flex: 0 0 350px;
    min-width: 260px;
    flex-direction: column;
    overflow: hidden;
    background: #fff;
    border: 1px solid var(--el-border-color-lighter);
    border-radius: 6px;
}

.side-head {
    display: flex;
    align-items: center;
    justify-content: space-between;
    padding: 12px;
}

.side-title {
    display: flex;
    align-items: center;
    gap: 8px;
    font-size: 15px;
    font-weight: 600;
    color: var(--el-text-color-primary);
}

.side-count {
    padding: 0 7px;
    border-radius: 10px;
    background: var(--el-fill-color-light);
    color: var(--el-text-color-secondary);
    font-size: 12px;
    font-weight: 400;
    line-height: 18px;
}

.side-filter {
    display: flex;
    gap: 8px;
    padding: 0 12px 10px;
}

.name-filter {
    flex: 1;
    min-width: 0;
}

.type-list {
    flex: 1;
    min-height: 0;
    padding: 8px;
    overflow-y: auto;
}

.type-item {
    display: block;
    width: 100%;
    padding: 9px 10px;
    border: 1px solid transparent;
    border-radius: 6px;
    background: transparent;
    font: inherit;
    text-align: left;
    cursor: pointer;
    transition: background-color .15s, border-color .15s;
}

.type-item + .type-item {
    margin-top: 6px;
}

.type-item:hover {
    background: var(--el-fill-color-light);
}

.type-item.active {
    background: var(--el-color-primary-light-9);
    border-color: var(--el-color-primary-light-7);
}

.type-item-main {
    display: flex;
    align-items: center;
    justify-content: space-between;
    gap: 8px;
}

.type-name {
    overflow: hidden;
    font-size: 14px;
    font-weight: 500;
    color: var(--el-text-color-primary);
    text-overflow: ellipsis;
    white-space: nowrap;
}

.type-item-sub {
    display: flex;
    align-items: center;
    gap: 8px;
    margin-top: 4px;
    min-width: 0;
}

.type-code,
.meta-code {
    overflow: hidden;
    font-family: Consolas, Monaco, monospace;
    font-size: 12px;
    color: var(--el-text-color-secondary);
    text-overflow: ellipsis;
    white-space: nowrap;
}

.type-kind {
    flex: none;
    padding: 0 6px;
    border-radius: 10px;
    background: var(--el-fill-color-light);
    color: var(--el-text-color-secondary);
    font-size: 11px;
    line-height: 18px;
}

.dict-main {
    display: flex;
    flex: 1;
    min-width: 0;
    flex-direction: column;
    overflow: hidden;
    background: #fff;
    border: 1px solid var(--el-border-color-lighter);
    border-radius: 6px;
}

.main-head {
    display: flex;
    flex-wrap: wrap;
    align-items: flex-start;
    justify-content: space-between;
    gap: 12px;
    padding: 12px 16px;
    border-bottom: 1px solid var(--el-border-color-lighter);
}

.main-head-info {
    min-width: 0;
}

.main-title {
    display: flex;
    flex-wrap: wrap;
    align-items: center;
    gap: 8px;
}

.main-name {
    font-size: 16px;
    font-weight: 600;
    color: var(--el-text-color-primary);
}

.main-meta {
    display: flex;
    flex-wrap: wrap;
    gap: 4px 14px;
    margin-top: 6px;
    font-size: 12px;
    color: var(--el-text-color-secondary);
}

.meta-remark {
    max-width: 420px;
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
}

.main-actions {
    display: flex;
    flex: none;
    gap: 8px;
}

.main-body {
    flex: 1;
    min-height: 0;
    overflow: auto;
}

.main-empty {
    display: flex;
    flex: 1;
    align-items: center;
    justify-content: center;
}
</style>
