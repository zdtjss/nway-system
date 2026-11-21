<template>
    <div>
        <div style="margin-bottom: 12px; display: flex; align-items: center; gap: 12px;">
            <span style="font-size: 14px; color: #606266;">左侧勾选菜单授权，右侧配置操作与数据范围（{{ scopeHint }}）</span>
            <el-select v-model="currentAppCode" placeholder="选择应用" style="width: 220px" filterable
                @change="onAppChange">
                <el-option v-for="app in appList" :key="app.appCode" :label="app.appName"
                    :value="app.appCode">
                    <span>{{ app.appName }}</span>
                    <el-tag v-if="app.appCode === roleAppCode" size="small" type="success"
                        style="margin-left: 6px;">角色主应用</el-tag>
                    <el-tag v-if="app.outOfScope" size="small" type="danger" style="margin-left: 6px;">非本租户</el-tag>
                </el-option>
            </el-select>
        </div>

        <div v-if="!currentAppCode" style="text-align: center; padding: 40px; color: #909399;">
            请先选择应用
        </div>

        <div v-else style="display: flex; gap: 16px;">
            <!-- 左侧：菜单树（目录 + 页面） -->
            <div style="flex: 1; border: 1px solid #ebeef5; border-radius: 6px; padding: 12px; max-height: 480px; overflow: auto;">
                <el-tree ref="treeRef" :key="currentAppCode" :data="currentTree" show-checkbox
                    node-key="id" :props="{ label: 'name', children: 'children' }"
                    :default-checked-keys="currentCheckedKeys" @check="onTreeCheck">
                    <template #default="{ data }">
                        <span>
                            {{ data.name }}
                            <el-tag v-if="data.type === 'DIR'" size="small" type="info"
                                style="margin-left: 6px;">目录</el-tag>
                        </span>
                    </template>
                </el-tree>
            </div>

            <!-- 右侧：已选页面配置 -->
            <div style="flex: 1.2; border: 1px solid #ebeef5; border-radius: 6px; padding: 12px; max-height: 480px; overflow: auto;">
                <div v-if="currentSelected.length === 0"
                    style="text-align: center; padding: 40px; color: #909399;">
                    请在左侧勾选页面
                </div>
                <div v-for="res in currentSelected" :key="res.resourceCode"
                    style="border: 1px solid #ebeef5; border-radius: 6px; padding: 12px; margin-bottom: 10px;">
                    <div style="display: flex; justify-content: space-between; align-items: center; margin-bottom: 8px;">
                        <span style="font-weight: 500;">
                            {{ res.resourceName }}
                            <el-tag v-if="res.unavailable" size="small" type="danger"
                                title="该菜单不存在或不属于目标角色租户，登录后不会显示（策略保留，可在此手动移除）">不会生效</el-tag>
                        </span>
                        <el-button type="danger" link @click="removeSelected(res.resourceCode)">移除</el-button>
                    </div>
                    <!-- 操作 -->
                    <div style="margin-bottom: 8px;">
                        <span style="font-size: 13px; color: #909399; margin-right: 8px;">操作：</span>
                        <el-radio-group v-model="res.actionsMode" style="margin-right: 12px;">
                            <el-radio value="all">全部</el-radio>
                            <el-radio value="custom">自定义</el-radio>
                        </el-radio-group>
                        <el-checkbox-group v-if="res.actionsMode === 'custom'" v-model="res.actionsList">
                            <el-checkbox v-for="act in actionsFor(res.appCode, res.resourceCode)" :key="act.value"
                                :value="act.value">
                                {{ act.label }}
                            </el-checkbox>
                        </el-checkbox-group>
                        <div v-if="res.actionsMode === 'custom' && actionsFor(res.appCode, res.resourceCode).length === 0"
                            style="font-size: 12px; color: #909399;">
                            该页面下暂无按钮，建议选「全部」
                        </div>
                    </div>
                    <!-- 数据范围 -->
                    <div>
                        <span style="font-size: 13px; color: #909399; margin-right: 8px;">数据：</span>
                        <el-radio-group v-model="res.dataScope">
                            <el-radio value="ALL">全部</el-radio>
                            <el-radio value="DEPT_WITH_SUB">部门及下级</el-radio>
                            <el-radio value="DEPT">仅本部门</el-radio>
                            <el-radio value="SELF">仅本人</el-radio>
                            <el-radio value="CUSTOM">自定义</el-radio>
                        </el-radio-group>
                    </div>
                    <div v-if="res.dataScope === 'CUSTOM'" style="margin-top: 8px;">
                        <el-input v-model="res.deptIdsText" placeholder="部门ID列表，逗号分隔" style="width: 100%" />
                    </div>
                </div>
            </div>
        </div>

        <div style="text-align: right; margin-top: 16px;">
            <el-button @click="emit('close')">取消</el-button>
            <el-button type="primary" @click="handleSave" :loading="saving">保存</el-button>
        </div>
    </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { queryByRole, saveRolePolicies, type PermissionPolicy } from '@/api/policy'
import { queryEnabled, getByCode, type App } from '@/api/app'
import {
    queryById as queryRoleById,
    queryGrantableMenus,
    queryGrantableResources
} from '@/api/role'
import type { Menu } from '@/api/menu'

interface TreeMenu extends Menu {
    children?: TreeMenu[]
}

interface SelectedResource {
    appCode: string
    resourceCode: string
    resourceName: string
    actionsMode: 'all' | 'custom'
    actionsList: string[]
    dataScope: string
    deptIdsText: string
    /** 存量策略引用的资源不属于「目标角色租户 + public」或菜单已不存在：登录后不会生效 */
    unavailable?: boolean
}

const props = defineProps<{ roleId: string; tenantCode?: string }>()
const emit = defineEmits<{ close: [] }>()

const appList = ref<App[]>([])
const saving = ref(false)
const treeRef = ref()

/** 角色所属应用（主应用，仅用于提示与默认定位）；'*' 表示平台级（不受应用限制），空串表示未知 */
const roleAppCode = ref('')

/** 可选范围提示：以角色租户为边界（同租户跨应用可授权，与后端规则一致） */
const scopeHint = computed(() => {
    if (roleAppCode.value === '*') {
        return '平台级角色：可选全部应用'
    }
    const scope = props.tenantCode ? `租户「${props.tenantCode}」及 public 的应用与菜单` : '全部应用与菜单'
    return `仅可授权 ${scope}`
})

/** 应用 -> 启用菜单列表（含 BTN，供树构建与 action 加载） */
const menuListCache = ref<Record<string, Menu[]>>({})
/** 已选资源：应用 -> (resourceCode -> 配置) */
const selectedByApp = ref<Record<string, Record<string, SelectedResource>>>({})

const currentAppCode = ref('')

/** 应用 -> (resourceCode -> 动作列表)：动作以资源目录为准，不再依赖菜单 BTN */
const resourceActionsCache = ref<Record<string, Record<string, string[]>>>({})

async function ensureResourceActions(appCode: string) {
    if (!appCode || resourceActionsCache.value[appCode]) return
    const res = await queryGrantableResources(appCode)
    const map: Record<string, string[]> = {}
    for (const r of res.data || []) {
        map[r.resourceCode] = (r.actions || []).map(a => a.action)
    }
    resourceActionsCache.value[appCode] = map
}

/** 按应用加载菜单（带缓存；只取「目标角色租户 + public」的菜单） */
async function ensureMenus(appCode: string): Promise<Menu[]> {
    if (!appCode) return []
    if (menuListCache.value[appCode]) return menuListCache.value[appCode]
    const res = await queryGrantableMenus(appCode, props.tenantCode)
    const list = res.data || []
    menuListCache.value[appCode] = list
    return list
}

/** 构建菜单树（过滤 BTN，只保留目录 + 页面，用于勾选） */
function buildMenuTree(list: Menu[]): TreeMenu[] {
    const nodes = list.filter(m => m.type !== 'BTN').map(m => ({ ...m, children: [] as TreeMenu[] }))
    const nodeMap = new Map<string, TreeMenu>()
    for (const n of nodes) {
        if (n.id) nodeMap.set(n.id, n)
    }
    const roots: TreeMenu[] = []
    for (const n of nodes) {
        const parent = n.parentId && nodeMap.has(n.parentId) ? nodeMap.get(n.parentId) : undefined
        if (parent) parent.children!.push(n)
        else roots.push(n)
    }
    return roots
}

const currentTree = computed(() => buildMenuTree(menuListCache.value[currentAppCode.value] || []))

/** 由已选资源推导树的勾选节点 key（菜单 id） */
const currentCheckedKeys = computed(() => {
    const selected = selectedByApp.value[currentAppCode.value] || {}
    const list = menuListCache.value[currentAppCode.value] || []
    const keys: string[] = []
    for (const rc of Object.keys(selected)) {
        const page = list.find(m => m.type === 'PAGE' && m.resourceCode === rc)
        if (page?.id) keys.push(page.id)
    }
    return keys
})

const currentSelected = computed(() => {
    const map = selectedByApp.value[currentAppCode.value] || {}
    return Object.values(map)
})

function createDefaultSelected(appCode: string, resourceCode: string, resourceName: string): SelectedResource {
    return {
        appCode,
        resourceCode,
        resourceName,
        actionsMode: 'all',
        actionsList: [],
        dataScope: 'SELF',
        deptIdsText: ''
    }
}

/** 树勾选变化时同步已选资源（勾选目录会级联带入其下所有页面） */
function onTreeCheck() {
    const tree = treeRef.value
    if (!tree) return
    const checkedNodes = tree.getCheckedNodes() as TreeMenu[]
    const pages = checkedNodes.filter(n => n.type === 'PAGE' && n.resourceCode)
    const old = selectedByApp.value[currentAppCode.value] || {}
    const next: Record<string, SelectedResource> = {}
    for (const page of pages) {
        const rc = page.resourceCode!
        next[rc] = old[rc] || createDefaultSelected(currentAppCode.value, rc, page.name)
    }
    selectedByApp.value[currentAppCode.value] = next
}

/** 从已选列表移除，并同步取消树的勾选 */
function removeSelected(resourceCode: string) {
    const selected = selectedByApp.value[currentAppCode.value] || {}
    delete selected[resourceCode]
    const list = menuListCache.value[currentAppCode.value] || []
    const page = list.find(m => m.type === 'PAGE' && m.resourceCode === resourceCode)
    if (page?.id && treeRef.value) {
        treeRef.value.setChecked(page.id, false)
    }
}

/** 某应用某资源下可勾选的操作（沿 parentId 链归集到父 PAGE 的按钮 action） */
function actionsFor(appCode: string, resourceCode: string): { value: string; label: string }[] {
    const fromCatalog = resourceActionsCache.value[appCode]?.[resourceCode]
    if (fromCatalog && fromCatalog.length > 0) {
        return fromCatalog.map(a => ({ value: a, label: a }))
    }
    const list = menuListCache.value[appCode] || []
    const idMap = new Map<string, Menu>()
    for (const m of list) {
        if (m.id) idMap.set(m.id, m)
    }
    const actions = new Set<string>()
    for (const m of list) {
        if (m.type !== 'BTN' || !m.action) continue
        if (resourceCode === '*') {
            actions.add(m.action)
            continue
        }
        let cur: Menu | undefined = m
        let page: Menu | undefined
        while (cur?.parentId) {
            const parent = idMap.get(cur.parentId)
            if (!parent) break
            if (parent.type === 'PAGE') {
                page = parent
                break
            }
            cur = parent
        }
        if (page?.resourceCode === resourceCode) actions.add(m.action)
    }
    return Array.from(actions).map(a => ({ value: a, label: a }))
}

async function onAppChange(appCode: string) {
    currentAppCode.value = appCode
    if (appCode) {
        await Promise.all([ensureMenus(appCode), ensureResourceActions(appCode)])
    }
}

onMounted(async () => {
    // 只取「目标角色租户 + public」的应用：可授权范围以租户为边界，其他租户的应用下不可能配出生效的策略
    const appRes = await queryEnabled(props.tenantCode)
    appList.value = appRes.data || []

    if (props.roleId) {
        // 角色主应用只影响默认定位与提示：页面菜单常发布在本租户的低代码应用下，
        // 若把下拉收窄到角色应用，新表单上线后就无法通过正常入口授权（C8）
        const roleRes = await queryRoleById(props.roleId)
        roleAppCode.value = roleRes.data?.appCode || ''

        const res = await queryByRole(props.roleId)
        const policies = res.data || []

        // 先加载所有涉及应用的菜单，便于把 resourceCode 回填为菜单名、把 * 展开为具体页面
        const appCodes = new Set(policies.map(p => p.appCode).filter(Boolean) as string[])
        // 存量策略引用的应用若不在可选范围内，仍纳入下拉并标注
        for (const code of appCodes) {
            if (!appList.value.some(a => a.appCode === code)) {
                const appRes2 = await getByCode(code)
                if (appRes2.data) appList.value.push({ ...appRes2.data, outOfScope: true })
            }
        }
        await Promise.all(Array.from(appCodes).map(code => ensureMenus(code)))
        await Promise.all(Array.from(appCodes).map(code => ensureResourceActions(code)))

        const grouped: Record<string, Record<string, SelectedResource>> = {}
        for (const p of policies) {
            const appCode = p.appCode
            if (!grouped[appCode]) grouped[appCode] = {}
            const actionsArr = p.actions ? JSON.parse(p.actions) : null
            const deptIds = p.scopeDeptIds ? JSON.parse(p.scopeDeptIds) : []
            const base = {
                appCode,
                actionsMode: (actionsArr === null ? 'all' : 'custom') as 'all' | 'custom',
                actionsList: actionsArr || [],
                dataScope: p.dataScope || 'SELF',
                deptIdsText: deptIds.join(',')
            }
            const list = menuListCache.value[appCode] || []
            if (p.resourceCode === '*') {
                // 通配策略等价展开为该应用下所有页面
                for (const m of list) {
                    if (m.type === 'PAGE' && m.resourceCode) {
                        grouped[appCode][m.resourceCode] = {
                            ...base,
                            resourceCode: m.resourceCode,
                            resourceName: m.name
                        }
                    }
                }
            } else {
                const page = list.find(m => m.type === 'PAGE' && m.resourceCode === p.resourceCode)
                grouped[appCode][p.resourceCode] = {
                    ...base,
                    resourceCode: p.resourceCode,
                    resourceName: page?.name || p.resourceCode,
                    // 菜单不在可选范围内（其他租户或已被删除）：保留策略，但明确提示不会生效
                    unavailable: !page
                }
            }
        }
        selectedByApp.value = grouped

        // 默认定位：角色主应用（在候选集内时）→ 第一个有策略的应用 → 第一个应用
        const firstApp = roleAppCode.value && appList.value.some(a => a.appCode === roleAppCode.value)
            ? roleAppCode.value
            : (appCodes.size > 0 ? Array.from(appCodes)[0] : (appList.value[0]?.appCode || ''))
        currentAppCode.value = firstApp
        if (firstApp) {
            await Promise.all([ensureMenus(firstApp), ensureResourceActions(firstApp)])
        }
    }
})

async function handleSave() {
    const policyList: PermissionPolicy[] = []
    for (const appCode of Object.keys(selectedByApp.value)) {
        const map = selectedByApp.value[appCode]
        for (const res of Object.values(map)) {
            const deptIds = res.deptIdsText.trim()
                ? JSON.stringify(res.deptIdsText.split(',').map(s => s.trim()).filter(Boolean))
                : null
            policyList.push({
                subjectType: 'ROLE',
                subjectId: props.roleId,
                appCode: res.appCode,
                resourceCode: res.resourceCode,
                actions: res.actionsMode === 'all' ? null : JSON.stringify(res.actionsList),
                dataScope: res.dataScope,
                scopeDeptIds: res.dataScope === 'CUSTOM' ? deptIds : null,
                scopeCondition: null,
                priority: 0
            })
        }
    }

    saving.value = true
    try {
        await saveRolePolicies(props.roleId, policyList)
        ElMessage.success('策略保存成功')
        emit('close')
    } finally {
        saving.value = false
    }
}
</script>
