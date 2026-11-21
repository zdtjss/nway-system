<template>
    <div style="width: 98%;margin: 10px auto;">
        <div>
            <el-form :inline="true">
                <el-form-item label="所属应用">
                    <el-select v-model="queryParam.appCode" clearable filterable style="width: 200px"
                        @change="loadData">
                        <el-option v-for="item in appList" :key="item.appCode" :label="item.appName"
                            :value="item.appCode" />
                    </el-select>
                </el-form-item>
                <el-form-item label="上级菜单">
                    <el-tree-select v-model="queryParam.parentId" :data="parentTree"
                        :props="{ label: 'name', children: 'children' }" node-key="id" check-strictly
                        :render-after-expand="false" clearable placeholder="全部（树形展示）" style="width: 220px"
                        @change="loadData" />
                </el-form-item>
                <el-form-item label="名称">
                    <el-input v-model="queryParam.name" @keyup.enter="loadData" style="width: 160px" />
                </el-form-item>
                <el-form-item>
                    <el-button :icon="Search" @click="loadData()">查询</el-button>
                </el-form-item>
            </el-form>
        </div>
        <div>
            <div style="position: relative;float: right;">
                <el-button :icon="Plus" size="small" link @click="edit()" />
            </div>
            <el-table :data="tableData" stripe style="width: 100%" row-key="id" default-expand-all
                :tree-props="{ children: 'children' }">
                <el-table-column prop="name" label="菜单名称" width="200" />
                <el-table-column prop="type" label="类型" width="80">
                    <template #default="scope">
                        <el-tag :type="typeTagMap[scope.row.type]" size="small">{{ scope.row.type }}</el-tag>
                    </template>
                </el-table-column>
                <el-table-column prop="resourceCode" label="资源标识" width="160" />
                <el-table-column prop="action" label="操作标识" width="120" />
                <el-table-column prop="url" label="路由路径" />
                <el-table-column prop="renderType" label="渲染类型" width="160">
                    <template #default="scope">
                        <el-tag v-if="scope.row.renderType" :type="renderTypeTagMap[scope.row.renderType]" size="small">
                            {{ renderTypeLabelMap[scope.row.renderType] || scope.row.renderType }}
                        </el-tag>
                        <span v-else>-</span>
                    </template>
                </el-table-column>
                <el-table-column prop="source" label="来源" width="90">
                    <template #default="scope">
                        <el-tag :type="scope.row.source === 'APP' ? 'warning' : ''" size="small">
                            {{ scope.row.source }}
                        </el-tag>
                    </template>
                </el-table-column>
                <el-table-column prop="sequence" label="排序" width="70" />
                <el-table-column label="操作" width="150">
                    <template #default="scope">
                        <el-button :icon="Edit" link @click="edit(scope.row.id)" />
                        <el-popconfirm title="确定删除?" @confirm="handleDelete(scope.row.id)">
                            <template #reference>
                                <el-button type="danger" link :icon="Delete" />
                            </template>
                        </el-popconfirm>
                    </template>
                </el-table-column>
            </el-table>
        </div>
    </div>
    <el-dialog v-model="dialogVisible" title="新增/修改菜单" width="750px" destroy-on-close>
        <menuEdit :menu-id="editId" :app-code="queryParam.appCode" :app-list="appList" @close="onDialogClose" />
    </el-dialog>
</template>

<script setup lang="ts">
import { Search, Plus, Edit, Delete } from '@element-plus/icons-vue'
import { ref, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { queryForAdmin, deleteById, type Menu } from '@/api/menu'
import { queryEnabled, type App } from '@/api/app'
import menuEdit from './menuEdit.vue'

const tableData = ref<Menu[]>([])
const appList = ref<App[]>([])
const editId = ref('')
const dialogVisible = ref(false)
/** 当前应用全部菜单（树构建/筛选的数据源） */
const allMenus = ref<Menu[]>([])
/** 上级菜单筛选用的菜单树 */
const parentTree = ref<Menu[]>([])

const queryParam = ref({
    appCode: '',
    parentId: '',
    name: ''
})

const typeTagMap: Record<string, string> = {
    DIR: 'info',
    PAGE: '',
    BTN: 'warning'
}

/** 渲染类型展示文案（与编辑表单选项一致） */
const renderTypeLabelMap: Record<string, string> = {
    native: '本地页面',
    microApp: 'microApp',
    'microApp-iframe': 'microApp-iframe',
    wujie: '无界',
    'wujie-iframe': '无界-iframe',
    iFrame: 'iFrame'
}

const renderTypeTagMap: Record<string, string> = {
    native: 'info',
    microApp: '',
    'microApp-iframe': '',
    wujie: 'warning',
    'wujie-iframe': 'warning',
    iFrame: 'success'
}

onMounted(async () => {
    const res = await queryEnabled()
    appList.value = res.data || []
    if (appList.value.length > 0) {
        queryParam.value.appCode = appList.value[0].appCode
    }
    loadData()
})

async function loadData() {
    const res = await queryForAdmin({ appCode: queryParam.value.appCode })
    allMenus.value = res.data || []
    parentTree.value = buildTree(allMenus.value)
    tableData.value = filterMenus(allMenus.value, queryParam.value)
}

/**
 * 按 parentId 构建菜单树，孤儿节点（父级缺失）挂到顶级
 */
function buildTree(list: Menu[]): Menu[] {
    const nodeMap = new Map<string, Menu>()
    list.forEach(m => nodeMap.set(m.id!, { ...m, children: [] }))
    const roots: Menu[] = []
    nodeMap.forEach(node => {
        const parent = node.parentId ? nodeMap.get(node.parentId) : undefined
        if (parent) {
            parent.children!.push(node)
        } else {
            roots.push(node)
        }
    })
    return roots
}

/**
 * 本地筛选：名称模糊匹配（保留祖先链保证层级完整）；
 * 指定上级菜单时返回其直接子级；否则构建树形表格
 */
function filterMenus(list: Menu[], param: { parentId?: string; name?: string }): Menu[] {
    let result = list
    const name = param.name?.trim()
    if (name) {
        const idMap = new Map(list.map(m => [m.id!, m]))
        const keepIds = new Set(list.filter(m => m.name?.includes(name)).map(m => m.id!))
        let changed = true
        while (changed) {
            changed = false
            for (const m of list) {
                if (keepIds.has(m.id!) && m.parentId && idMap.has(m.parentId) && !keepIds.has(m.parentId)) {
                    keepIds.add(m.parentId)
                    changed = true
                }
            }
        }
        result = list.filter(m => keepIds.has(m.id!))
    }
    if (param.parentId) {
        return result.filter(m => m.parentId === param.parentId)
    }
    return buildTree(result)
}

function edit(id?: string) {
    editId.value = id || ''
    dialogVisible.value = true
}

function onDialogClose() {
    dialogVisible.value = false
    loadData()
}

async function handleDelete(id: string) {
    await deleteById(id)
    ElMessage.success('删除成功')
    loadData()
}
</script>
