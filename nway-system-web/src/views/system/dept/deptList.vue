<template>
    <div style="width: 98%;margin: 10px auto;">
        <div style="margin-bottom: 10px;">
            <el-button :icon="Plus" type="primary" @click="openEdit(null, '')">新增顶级部门</el-button>
            <el-button :icon="Refresh" @click="loadTree">刷新</el-button>
        </div>
        <el-table :data="treeData" row-key="id" border default-expand-all
            :tree-props="{ children: 'children' }" style="width: 100%">
            <el-table-column prop="deptName" label="部门名称" width="260" />
            <el-table-column prop="deptCode" label="层级编码" width="160" />
            <el-table-column label="层级" width="80">
                <template #default="scope">
                    <el-tag size="small" type="info">L{{ scope.row.level }}</el-tag>
                </template>
            </el-table-column>
            <el-table-column prop="sort" label="排序" width="80" />
            <el-table-column label="状态" width="100">
                <template #default="scope">
                    <el-tag :type="scope.row.status === 'using' ? 'success' : 'danger'" size="small">
                        {{ scope.row.status === 'using' ? '启用' : '禁用' }}
                    </el-tag>
                </template>
            </el-table-column>
            <el-table-column prop="createTime" label="创建时间" width="180" />
            <el-table-column prop="tenantName" label="租户" width="120">
                <template #default="scope">
                    <el-tag v-if="scope.row.tenantName" size="small" type="info">{{ scope.row.tenantName }}</el-tag>
                </template>
            </el-table-column>
            <el-table-column label="操作" width="280">
                <template #default="scope">
                    <el-button link @click="openEdit(null, scope.row.id)">新增子部门</el-button>
                    <el-button link @click="openEdit(scope.row.id, scope.row.parentId)">编辑</el-button>
                    <el-button link @click="showUsers(scope.row.id, scope.row.deptName)">人员</el-button>
                    <el-popconfirm title="确定删除该部门？" @confirm="removeDept(scope.row.id)">
                        <template #reference>
                            <el-button link type="danger">删除</el-button>
                        </template>
                    </el-popconfirm>
                </template>
            </el-table-column>
        </el-table>

        <!-- 新增/编辑部门弹窗 -->
        <el-dialog v-model="editVisible" :title="editForm.id ? '编辑部门' : '新增部门'" width="500" destroy-on-close>
            <el-form :model="editForm" label-width="90px">
                <el-form-item label="部门名称">
                    <el-input v-model="editForm.deptName" placeholder="请输入部门名称" />
                </el-form-item>
                <el-form-item label="排序">
                    <el-input-number v-model="editForm.sort" :min="0" :max="999" />
                </el-form-item>
                <!-- 仅新增顶级部门时超管可选择租户；子部门继承父部门租户、编辑时租户均不可修改 -->
                <el-form-item v-if="isSuperAdmin && !editForm.id && !editForm.parentId" label="租户">
                    <el-select v-model="editForm.tenantCode" placeholder="请选择租户" style="width: 100%" clearable>
                        <el-option v-for="t in tenantList" :key="t.id" :label="t.tenantName" :value="t.tenantCode" />
                    </el-select>
                </el-form-item>
                <el-form-item v-else-if="editForm.id || editForm.parentId" label="租户">
                    <el-input :value="editForm.tenantName || '默认租户'" disabled />
                    <span style="color:#999;font-size:12px;">{{ editForm.id ? '租户不可修改' : '子部门继承上级部门租户' }}</span>
                </el-form-item>
                <el-form-item label="状态">
                    <el-select v-model="editForm.status" style="width: 100%">
                        <el-option label="启用" value="using" />
                        <el-option label="禁用" value="disabled" />
                    </el-select>
                </el-form-item>
                <el-form-item label="层级编码">
                    <el-input v-model="editForm.deptCode" :disabled="hasChildren" placeholder="留空保存时自动生成" maxlength="20">
                        <template #append>
                            <el-button :disabled="hasChildren" @click="autoGenCode">自动</el-button>
                        </template>
                    </el-input>
                    <span style="color:#999;font-size:12px;">
                        {{ hasChildren ? '存在子部门，层级编码不可修改' : '每级2位数字，可手动调整' }}
                    </span>
                </el-form-item>
            </el-form>
            <template #footer>
                <el-button @click="editVisible = false">取消</el-button>
                <el-button type="primary" :loading="saving" @click="submitDept">保存</el-button>
            </template>
        </el-dialog>

        <!-- 部门人员管理弹窗（行内编辑：双击行可编辑任职部门） -->
        <el-dialog v-model="userDialogVisible" :title="`${currentDeptName} - 人员管理`" width="900" destroy-on-close>
            <div style="display:flex;justify-content:space-between;align-items:center;margin-bottom:10px;">
                <span style="color:#999;font-size:12px;">双击行可编辑任职部门（支持跨部门），共 {{ deptUsers.length }} 人</span>
                <el-button :icon="Plus" type="primary" size="small" @click="addUserRow">添加人员</el-button>
            </div>
            <el-table :data="deptUsers" stripe border style="width: 100%" max-height="420"
                @row-dblclick="onRowDblClick">
                <el-table-column label="工号" width="160">
                    <template #default="scope">
                        <el-select v-if="isEditing(scope.row)" v-model="userEditForm.employeeId" filterable clearable
                            placeholder="选择人员" style="width: 100%" @change="onPickUser">
                            <el-option v-for="u in candidateUsers" :key="u.employeeId"
                                :label="`${u.employeeId}（${u.realName}）`" :value="u.employeeId"
                                :disabled="deptEmpIds.has(u.employeeId)" />
                        </el-select>
                        <span v-else>{{ scope.row.employeeId }}</span>
                    </template>
                </el-table-column>
                <el-table-column prop="realName" label="姓名" width="110" />
                <el-table-column prop="phoneNumber" label="手机号" width="140" />
                <el-table-column prop="email" label="邮箱" />
                <el-table-column label="任职部门" min-width="240">
                    <template #default="scope">
                        <template v-if="!isEditing(scope.row)">
                            <el-tag v-for="d in scope.row.depts" :key="d.deptId" size="small"
                                style="margin-right:4px;" :type="d.primary ? 'success' : 'info'">
                                {{ d.deptName }}{{ d.primary ? '·主' : '' }}
                            </el-tag>
                            <span v-if="!scope.row.depts?.length" style="color:#999;">未分配</span>
                        </template>
                        <el-tree-select v-else v-model="userEditForm.deptIds" :data="treeData"
                            :props="{ label: 'deptName', value: 'id', children: 'children' }"
                            node-key="id" multiple check-strictly filterable collapse-tags style="width: 100%"
                            placeholder="选择任职部门（可多选）" />
                    </template>
                </el-table-column>
                <el-table-column label="状态" width="90">
                    <template #default="scope">
                        <el-tag :type="scope.row.status === 'using' ? 'success' : 'danger'" size="small">
                            {{ scope.row.status === 'using' ? '在职' : '离职' }}
                        </el-tag>
                    </template>
                </el-table-column>
                <el-table-column label="操作" :width="editingId !== null ? 140 : 130" fixed="right">
                    <template #default="scope">
                        <template v-if="isEditing(scope.row)">
                            <el-button link type="primary" @click="saveRow">保存</el-button>
                            <el-button link @click="cancelEdit">取消</el-button>
                        </template>
                        <template v-else>
                            <el-button link @click="editRow(scope.row)">编辑</el-button>
                            <el-popconfirm title="确定将该人员移出本部门？" @confirm="removeUserFromDept(scope.row)">
                                <template #reference>
                                    <el-button link type="danger">移出</el-button>
                                </template>
                            </el-popconfirm>
                        </template>
                    </template>
                </el-table-column>
            </el-table>
            <template #footer>
                <el-button @click="userDialogVisible = false">关闭</el-button>
            </template>
        </el-dialog>
    </div>
</template>

<script setup lang="ts">
import { ref, onActivated, onMounted, computed } from 'vue'
import { Plus, Refresh } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import http from '@/js/httpProxy.js'
import auth from '@/js/auth.js'
import { queryAll as queryTenantAll, type TenantDto } from '@/api/tenant'

const isSuperAdmin = ref(auth.isSuperAdmin())
const tenantList = ref<TenantDto[]>([])

interface DeptTreeVO {
    id: string
    deptCode: string
    deptName: string
    parentId: string
    level: number
    sort: number
    status: string
    createTime: string
    tenantCode?: string
    tenantName?: string
    children: DeptTreeVO[]
}

/** 任职部门引用（主部门 primary=true） */
interface DeptRefItem {
    deptId: string
    deptName: string
    primary?: boolean
}

/** 部门下任职用户（支持跨部门） */
interface UserDeptItem {
    id: string
    employeeId: string
    realName: string
    phoneNumber: string
    email: string
    status: string
    depts: DeptRefItem[]
}

/** 候选用户（添加人员选择用） */
interface CandidateUser {
    id: string
    employeeId: string
    realName: string
    phoneNumber: string
    email: string
    status: string
    deptName?: string
}

const treeData = ref<DeptTreeVO[]>([])
const editVisible = ref(false)
const saving = ref(false)
const editForm = ref({
    id: '',
    parentId: '',
    deptName: '',
    sort: 0,
    status: 'using',
    deptCode: '',
    tenantCode: '',
    tenantName: ''
})
/** 当前编辑部门是否有子部门：有子部门时层级编码不可修改 */
const hasChildren = ref(false)

const userDialogVisible = ref(false)
const currentDeptName = ref('')
const currentDeptId = ref('')
const deptUsers = ref<UserDeptItem[]>([])

// ===== 行内编辑状态 =====
// editingId：null=无编辑；""=新增占位行；否则为编辑中的行id（用户id）
const editingId = ref<string | null>(null)
const userEditForm = ref<{ id: string; employeeId: string; deptIds: string[]; primaryDeptId: string }>({
    id: '',
    employeeId: '',
    deptIds: [],
    primaryDeptId: ''
})
const candidateUsers = ref<CandidateUser[]>([])
const deptEmpIds = computed(() => new Set(deptUsers.value.filter(u => u.employeeId).map(u => u.employeeId)))

// KeepAlive include 机制下，异步组件首次渲染（未加载完成）无法匹配组件名而不走缓存，onActivated 不触发；
// 用 onMounted 兜底保证任何渲染路径都执行加载。缓存路径下 onMounted 与 onActivated 连触发，用标志去重。
let mountedOnce = false
onMounted(() => {
    mountedOnce = true
    initList()
})
onActivated(() => {
    if (!mountedOnce) {
        initList()
    }
    mountedOnce = false
})

function initList() {
    loadTree()
    if (isSuperAdmin.value) {
        loadTenantList()
    }
}

function loadTenantList() {
    queryTenantAll().then(({ code, data }) => {
        if (code === 200) {
            tenantList.value = data || []
        }
    })
}

function loadTree() {
    http.get('/dept/tree').then(({ data }) => {
        if (data.code === 200) {
            treeData.value = data.data || []
        }
    })
}

function openEdit(id: string | null, parentId: string) {
    if (id) {
        // 编辑：从树中找到节点
        const node = findNode(treeData.value, id)
        if (node) {
            editForm.value = {
                id: node.id,
                parentId: node.parentId,
                deptName: node.deptName,
                sort: node.sort,
                status: node.status,
                deptCode: node.deptCode,
                tenantCode: node.tenantCode || '',
                tenantName: node.tenantName || ''
            }
            hasChildren.value = !!(node.children && node.children.length)
        }
    } else {
        // 新增：子部门租户继承父部门
        const parentNode = parentId ? findNode(treeData.value, parentId) : null
        editForm.value = {
            id: '',
            parentId: parentId || '',
            deptName: '',
            sort: 0,
            status: 'using',
            deptCode: '',
            tenantCode: parentNode ? (parentNode.tenantCode || '') : '',
            tenantName: parentNode ? (parentNode.tenantName || '') : ''
        }
        hasChildren.value = false
        autoGenCode()
    }
    editVisible.value = true
}

function findNode(list: DeptTreeVO[], id: string): DeptTreeVO | null {
    for (const item of list) {
        if (item.id === id) return item
        if (item.children?.length) {
            const found = findNode(item.children, id)
            if (found) return found
        }
    }
    return null
}

/**
 * 前端按同级最大编码+1 生成下一级编码并填入输入框（仅作默认值，保存时后端兜底校验/生成）
 */
function autoGenCode() {
    const parentId = editForm.value.parentId
    let parentCode = ''
    if (parentId) {
        const parent = findNode(treeData.value, parentId)
        parentCode = parent?.deptCode || ''
    }
    let max = 0
    collectSiblings(treeData.value, parentId).forEach(s => {
        const n = parseInt((s.deptCode || '').slice(-2), 10)
        if (!isNaN(n) && n > max) max = n
    })
    editForm.value.deptCode = parentCode + String(max + 1).padStart(2, '0')
}

/** 收集指定父级下的所有同级节点（顶层父级为空串） */
function collectSiblings(list: DeptTreeVO[], parentId: string): DeptTreeVO[] {
    const result: DeptTreeVO[] = []
    const walk = (nodes: DeptTreeVO[]) => {
        nodes.forEach(n => {
            if ((n.parentId || '') === (parentId || '')) result.push(n)
            if (n.children?.length) walk(n.children)
        })
    }
    walk(list)
    return result
}

function submitDept() {
    if (!editForm.value.deptName) {
        ElMessage.warning('请输入部门名称')
        return
    }
    saving.value = true
    http.post('/dept/save', editForm.value).then(({ data }) => {
        if (data.code === 200) {
            ElMessage.success('保存成功')
            editVisible.value = false
            loadTree()
        } else {
            ElMessage.error(data.msg || '保存失败')
        }
    }).finally(() => {
        saving.value = false
    })
}

function removeDept(id: string) {
    const formData = new FormData(); formData.append('id', id)
    http.post('/dept/deleteById', formData).then(({ data }) => {
        if (data.code === 200) {
            ElMessage.success('删除成功')
            loadTree()
        } else {
            ElMessage.error(data.msg || '删除失败')
        }
    })
}

function showUsers(deptId: string, deptName: string) {
    currentDeptId.value = deptId
    currentDeptName.value = deptName
    loadDeptUsers()
    userDialogVisible.value = true
}

function loadDeptUsers() {
    http.get('/dept/users', { params: { deptId: currentDeptId.value } }).then(({ data }) => {
        if (data.code === 200) {
            deptUsers.value = data.data || []
            editingId.value = null
            userEditForm.value = { id: '', employeeId: '', deptIds: [], primaryDeptId: '' }
        }
    })
}

// ===== 行内编辑 =====

function isEditing(row: UserDeptItem) {
    return editingId.value !== null && row.id === editingId.value
}

function onRowDblClick(row: UserDeptItem, column: any) {
    // 操作列双击不进入编辑
    if (column && column.label === '操作') return
    editRow(row)
}

function editRow(row: UserDeptItem) {
    // 若当前编辑的是新增占位行，先移除
    if (editingId.value === '') {
        removePlaceholder()
    }
    if (editingId.value === row.id) return
    editingId.value = row.id
    userEditForm.value = {
        id: row.id,
        employeeId: row.employeeId,
        deptIds: (row.depts || []).map(d => d.deptId),
        primaryDeptId: (row.depts || []).find(d => d.primary)?.deptId || ''
    }
}

/** 添加人员：插入新增占位行（默认分配到本部门） */
function addUserRow() {
    if (editingId.value === '') {
        removePlaceholder()
    }
    deptUsers.value.unshift({
        id: '',
        employeeId: '',
        realName: '',
        phoneNumber: '',
        email: '',
        status: 'using',
        depts: [{ deptId: currentDeptId.value, deptName: currentDeptName.value, primary: false }]
    })
    editingId.value = ''
    userEditForm.value = {
        id: '',
        employeeId: '',
        deptIds: [currentDeptId.value],
        primaryDeptId: currentDeptId.value
    }
    loadCandidateUsers()
}

function removePlaceholder() {
    const idx = deptUsers.value.findIndex(r => r.id === '')
    if (idx > -1) {
        deptUsers.value.splice(idx, 1)
    }
}

/** 新增行选中人员后补全姓名等信息 */
function onPickUser(employeeId: string) {
    const user = candidateUsers.value.find(u => u.employeeId === employeeId)
    const row = deptUsers.value.find(r => r.id === '')
    if (user && row) {
        row.realName = user.realName
        row.phoneNumber = user.phoneNumber || ''
        row.email = user.email || ''
    }
}

function saveRow() {
    const form = userEditForm.value
    if (editingId.value === '') {
        // 新增行：分配到当前部门（无任职部门时自动作为主部门）
        if (!form.employeeId) {
            ElMessage.warning('请选择人员')
            return
        }
        http.post('/userDept/assign', null, { params: { employeeId: form.employeeId, deptId: currentDeptId.value } })
            .then(({ data }) => {
                if (data.code === 200) {
                    ElMessage.success('添加成功')
                    loadDeptUsers()
                }
            })
    } else {
        // 编辑已有行：全量保存任职部门（主部门保留原主部门，被移除时取第一个）
        if (!form.deptIds.length) {
            ElMessage.warning('请至少选择一个任职部门')
            return
        }
        const primary = form.deptIds.includes(form.primaryDeptId) ? form.primaryDeptId : form.deptIds[0]
        const secondary = form.deptIds.filter(id => id !== primary)
        http.post('/userDept/save', secondary, {
            params: { employeeId: form.employeeId, primaryDeptId: primary }
        }).then(({ data }) => {
            if (data.code === 200) {
                ElMessage.success('保存成功')
                loadDeptUsers()
            }
        })
    }
}

function cancelEdit() {
    // 新增行取消：移除占位行
    if (editingId.value === '') {
        removePlaceholder()
    }
    editingId.value = null
    userEditForm.value = { id: '', employeeId: '', deptIds: [], primaryDeptId: '' }
}

/** 移出部门：主部门需先变更，兼职直接移出 */
function removeUserFromDept(row: UserDeptItem) {
    const rel = (row.depts || []).find(d => d.deptId === currentDeptId.value)
    if (rel && rel.primary) {
        ElMessage.warning('该人员主部门为本部门，请先编辑任职部门变更主部门')
        return
    }
    http.post('/userDept/removeSecondary', null, {
        params: { employeeId: row.employeeId, deptId: currentDeptId.value }
    }).then(({ data }) => {
        if (data.code === 200) {
            ElMessage.success('已移出本部门')
            loadDeptUsers()
        }
    })
}

function loadCandidateUsers() {
    http.get('/user/queryPage', {
        params: {
            employeeId: '',
            realName: '',
            current: 1,
            size: 100
        }
    }).then(({ data }) => {
        if (data.code === 200) {
            candidateUsers.value = data.data?.data || []
        }
    })
}
</script>

<style lang="css" scoped>
.dialog-footer {
    bottom: 15px;
    right: 20px;
    position: absolute;
}
</style>
