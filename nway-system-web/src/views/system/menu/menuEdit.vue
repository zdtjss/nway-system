<template>
    <el-form ref="formRef" :model="form" :rules="rules" label-width="100px">
        <el-form-item label="所属应用" prop="appCode">
            <el-select v-model="form.appCode" filterable style="width: 100%">
                <el-option v-for="app in appList" :key="app.appCode" :label="app.appName" :value="app.appCode" />
            </el-select>
        </el-form-item>
        <el-form-item label="菜单类型" prop="type">
            <el-radio-group v-model="form.type">
                <el-radio value="DIR">目录</el-radio>
                <el-radio value="PAGE">页面</el-radio>
                <el-radio value="BTN">按钮</el-radio>
            </el-radio-group>
        </el-form-item>
        <el-form-item label="菜单名称" prop="name">
            <el-input v-model="form.name" />
        </el-form-item>
        <el-form-item label="上级菜单">
            <el-tree-select v-model="form.parentId" :data="menuTree"
                :props="{ label: 'name', children: 'children' }" node-key="id" check-strictly
                :render-after-expand="false" clearable filterable
                placeholder="留空为顶级菜单" style="width: 100%" />
        </el-form-item>
        <el-form-item v-if="form.type === 'PAGE'" label="资源标识" prop="resourceCode">
            <el-input v-model="form.resourceCode" placeholder="如 user、contract（关联权限策略）" />
        </el-form-item>
        <el-form-item v-if="form.type === 'BTN'" label="操作标识" prop="action">
            <el-input v-model="form.action" placeholder="如 create、edit、delete、approve" />
        </el-form-item>
        <el-form-item v-if="form.type !== 'BTN'" label="路由路径">
            <el-input v-model="form.url" placeholder="相对于应用 host 的路径" />
        </el-form-item>
        <el-form-item v-if="form.type !== 'BTN'" label="图标">
            <el-input v-model="form.icon" />
        </el-form-item>
        <el-form-item v-if="form.type !== 'BTN'" label="渲染类型">
            <el-select v-model="form.renderType" clearable style="width: 100%">
                <el-option label="本地页面" value="native" />
                <el-option label="microApp（WebComponent）" value="microApp" />
                <el-option label="microApp（iframe沙箱）" value="microApp-iframe" />
                <el-option label="无界（标准）" value="wujie" />
                <el-option label="无界（降级iframe）" value="wujie-iframe" />
                <el-option label="iFrame" value="iFrame" />
            </el-select>
        </el-form-item>
        <el-form-item label="排序">
            <el-input-number v-model="form.sequence" :min="0" :max="255" />
        </el-form-item>
        <el-form-item label="启用">
            <el-switch v-model="form.isEnabled" />
        </el-form-item>
        <el-form-item>
            <el-button type="primary" @click="handleSave">保存</el-button>
            <el-button @click="emit('close')">取消</el-button>
        </el-form-item>
    </el-form>
</template>

<script setup lang="ts">
import { ref, onMounted, watch } from 'vue'
import { ElMessage } from 'element-plus'
import type { FormInstance, FormRules } from 'element-plus'
import { save, getById, queryByApp, type Menu } from '@/api/menu'
import type { App } from '@/api/app'

const props = defineProps<{ menuId: string; appCode: string; appList: App[] }>()
const emit = defineEmits<{ close: [] }>()

const formRef = ref<FormInstance>()
/** 上级菜单下拉树（编辑时排除自身及子孙，避免循环层级） */
const menuTree = ref<Menu[]>([])
const form = ref<Menu>({
    name: '',
    appCode: props.appCode || '',
    type: 'PAGE',
    parentId: '',
    url: '',
    icon: '',
    resourceCode: '',
    action: '',
    renderType: '',
    sequence: 0,
    isEnabled: true
})

const rules: FormRules = {
    appCode: [{ required: true, message: '所属应用不能为空', trigger: 'change' }],
    name: [{ required: true, message: '菜单名称不能为空', trigger: 'blur' }],
    type: [{ required: true, message: '类型不能为空', trigger: 'change' }],
    resourceCode: [{ required: true, message: 'PAGE类型必须填写资源标识', trigger: 'blur' }],
    action: [{ required: true, message: 'BTN类型必须填写操作标识', trigger: 'blur' }]
}

onMounted(async () => {
    if (props.menuId) {
        const res = await getById(props.menuId)
        if (res.data) {
            form.value = res.data
        }
    }
    loadMenuTree()
})

/** 所属应用切换时重新加载菜单树 */
watch(() => form.value.appCode, loadMenuTree)

/**
 * 加载当前应用的菜单树；编辑时排除自身及全部子孙节点
 */
async function loadMenuTree() {
    const res = await queryByApp(form.value.appCode)
    menuTree.value = buildTree(res.data || [], props.menuId)
}

/**
 * 按 parentId 构建菜单树，孤儿节点（父级缺失）挂到顶级；
 * excludeId 及其全部子孙节点不参与构建（防止把自己挂到自己的子树下）
 */
function buildTree(list: Menu[], excludeId?: string): Menu[] {
    const excluded = new Set<string>()
    if (excludeId) {
        const collect = (id: string) => {
            if (excluded.has(id)) {
                return
            }
            excluded.add(id)
            list.filter(m => m.parentId === id).forEach(m => collect(m.id!))
        }
        collect(excludeId)
    }
    const nodeMap = new Map<string, Menu>()
    list.filter(m => !excluded.has(m.id!)).forEach(m => nodeMap.set(m.id!, { ...m, children: [] }))
    const roots: Menu[] = []
    nodeMap.forEach(node => {
        const parent = node.parentId && !excluded.has(node.parentId) ? nodeMap.get(node.parentId) : undefined
        if (parent) {
            parent.children!.push(node)
        } else {
            roots.push(node)
        }
    })
    return roots
}

async function handleSave() {
    await formRef.value?.validate()
    // 清空上级菜单后归一化为空串，保持“顶级”语义
    form.value.parentId = form.value.parentId || ''
    await save(form.value)
    ElMessage.success('保存成功')
    emit('close')
}
</script>
