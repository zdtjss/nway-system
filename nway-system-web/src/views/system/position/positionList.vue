<template>
    <div style="width: 98%;margin: 10px auto;">
        <div style="margin-bottom: 10px;">
            <el-button :icon="Plus" type="primary" @click="openEdit(null)">新增岗位</el-button>
            <el-input v-model="codeFilter" placeholder="岗位编码" clearable style="width: 160px;margin-left: 10px;"
                @keyup.enter="onSearch" @clear="onSearch" />
            <el-input v-model="nameFilter" placeholder="岗位名称" clearable style="width: 160px;margin-left: 10px;"
                @keyup.enter="onSearch" @clear="onSearch" />
            <el-select v-model="statusFilter" placeholder="状态筛选" clearable style="width: 160px;margin-left: 10px;"
                @change="onSearch">
                <el-option label="启用" value="using" />
                <el-option label="禁用" value="disabled" />
            </el-select>
            <el-button :icon="Search" style="margin-left: 10px;" @click="onSearch">查询</el-button>
            <el-button :icon="Refresh" style="margin-left: 10px;" @click="loadList">刷新</el-button>
        </div>
        <el-table :data="list" border stripe style="width: 100%">
            <el-table-column prop="positionCode" label="岗位编码" width="150" />
            <el-table-column prop="positionName" label="岗位名称" width="180" />
            <el-table-column prop="deptName" label="所属部门" width="160">
                <template #default="scope">
                    <el-tag v-if="scope.row.deptName" size="small" type="info">{{ scope.row.deptName }}</el-tag>
                </template>
            </el-table-column>
            <el-table-column prop="positionLevel" label="岗位职级" width="100">
                <template #default="scope">
                    {{ levelLabel(scope.row.positionLevel) }}
                </template>
            </el-table-column>
            <el-table-column label="状态" width="100">
                <template #default="scope">
                    <el-tag :type="scope.row.status === 'using' ? 'success' : 'danger'" size="small">
                        {{ scope.row.status === 'using' ? '启用' : '禁用' }}
                    </el-tag>
                </template>
            </el-table-column>
            <el-table-column prop="tenantName" label="租户" width="120">
                <template #default="scope">
                    <el-tag v-if="scope.row.tenantName" size="small" type="info">{{ scope.row.tenantName }}</el-tag>
                </template>
            </el-table-column>
            <el-table-column prop="remark" label="备注" show-overflow-tooltip />
            <el-table-column prop="createTime" label="创建时间" width="180" />
            <el-table-column label="操作" width="200">
                <template #default="scope">
                    <el-button link @click="openEdit(scope.row)">编辑</el-button>
                    <el-button link @click="showUsers(scope.row)">人员</el-button>
                    <el-popconfirm title="确定删除该岗位？" @confirm="removePosition(scope.row.id)">
                        <template #reference>
                            <el-button link type="danger">删除</el-button>
                        </template>
                    </el-popconfirm>
                </template>
            </el-table-column>
        </el-table>
        <div style="display: flex; justify-content: flex-end; margin-top: 10px;">
            <el-pagination :page-sizes="[30, 50, 100]" layout="total, sizes, prev, pager, next"
                v-model:current-page="currentPage" v-model:page-size="pageSize"
                :total="total" @current-change="loadList" @size-change="onSizeChange" />
        </div>

        <el-dialog v-model="editVisible" :title="editForm.id ? '编辑岗位' : '新增岗位'" width="500" destroy-on-close>
            <el-form :model="editForm" label-width="90px">
                <el-form-item label="岗位编码">
                    <el-input v-model="editForm.positionCode" :disabled="!!editForm.id" placeholder="请输入岗位编码" />
                </el-form-item>
                <el-form-item label="岗位名称">
                    <el-select v-model="editForm.positionName" filterable allow-create default-first-option clearable
                        placeholder="请选择或输入岗位名称" style="width: 100%">
                        <el-option v-for="item in nameOptions" :key="item.key" :label="item.value" :value="item.key" />
                    </el-select>
                </el-form-item>
                <el-form-item label="所属部门">
                    <el-tree-select v-model="editForm.deptId" :data="deptTree"
                        :props="{ label: 'deptName', value: 'id', children: 'children' }"
                        node-key="id" check-strictly default-expand-all filterable
                        style="width: 100%" placeholder="请选择所属部门（岗位唯一归属组织）" />
                    <div style="color:#999;font-size:12px;line-height:1.5;margin-top:4px;width:100%;">
                        {{ deptTip }}
                    </div>
                </el-form-item>
                <el-form-item label="岗位职级">
                    <el-select v-model="editForm.positionLevel" placeholder="请选择岗位职级" style="width: 100%" clearable>
                        <el-option v-for="item in levelOptions" :key="item.key"
                            :label="item.value" :value="item.key" />
                    </el-select>
                </el-form-item>
                <el-form-item label="状态">
                    <el-select v-model="editForm.status" style="width: 100%">
                        <el-option label="启用" value="using" />
                        <el-option label="禁用" value="disabled" />
                    </el-select>
                </el-form-item>
                <el-form-item v-if="isSuperAdmin" label="租户">
                    <el-select v-model="editForm.tenantCode" placeholder="请选择租户" style="width: 100%" clearable
                        @change="onTenantChange">
                        <el-option v-for="t in tenantList" :key="t.id" :label="t.tenantName" :value="t.tenantCode" />
                    </el-select>
                </el-form-item>
                <el-form-item label="备注">
                    <el-input v-model="editForm.remark" type="textarea" :rows="2" placeholder="请输入备注" />
                </el-form-item>
            </el-form>
            <template #footer>
                <el-button @click="editVisible = false">取消</el-button>
                <el-button type="primary" :loading="saving" @click="submitPosition">保存</el-button>
            </template>
        </el-dialog>

        <el-dialog v-model="userDialogVisible" :title="`${currentPositionName} - 人员管理`" width="800" destroy-on-close>
            <div style="display:flex;justify-content:space-between;align-items:center;margin-bottom:10px;">
                <span style="color:#999;font-size:12px;">双击行可编辑任职部门与主岗，共 {{ positionUsers.length }} 人</span>
                <el-button :icon="Plus" type="primary" size="small" @click="addUserRow">添加人员</el-button>
            </div>
            <el-table :data="positionUsers" stripe border style="width: 100%" max-height="400"
                @row-dblclick="onRowDblClick">
                <el-table-column label="工号" width="150">
                    <template #default="scope">
                        <el-select v-if="isEditing(scope.row)" v-model="userEditForm.employeeId" filterable clearable
                            placeholder="选择人员" style="width: 100%" @change="onPickUser">
                            <el-option v-for="u in candidateUsers" :key="u.employeeId"
                                :label="`${u.employeeId}（${u.realName}）`" :value="u.employeeId"
                                :disabled="positionEmpIds.has(u.employeeId)" />
                        </el-select>
                        <span v-else>{{ scope.row.employeeId }}</span>
                    </template>
                </el-table-column>
                <el-table-column prop="realName" label="姓名" width="110" />
                <el-table-column label="任职部门" min-width="190">
                    <template #default="scope">
                        <el-tag v-if="scope.row.deptName" size="small"
                            :type="scope.row.isPrimary ? 'success' : 'info'">
                            {{ scope.row.deptName }}{{ scope.row.isPrimary ? '·主' : '' }}
                        </el-tag>
                        <span v-else style="color:#999;">-</span>
                    </template>
                </el-table-column>
                <el-table-column label="主岗" width="70" align="center">
                    <template #default="scope">
                        <template v-if="!isEditing(scope.row)">
                            <el-tag v-if="scope.row.isPrimary" type="success" size="small">主岗</el-tag>
                            <span v-else>-</span>
                        </template>
                        <el-radio v-else :model-value="userEditForm.isPrimary" :value="true"
                            @change="onPrimaryToggle" />
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
                            <el-popconfirm title="确定将该人员移出本岗位？" @confirm="removeUserFromPosition(scope.row)">
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
import { Plus, Refresh, Search } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import {
    queryPage, save, deleteById, usersByPosition, saveUserPosition, removeUserPosition,
    type PositionDto, type UserPositionDto
} from '@/api/position'
import { queryByCode } from '@/api/dict'
import { queryAll as queryTenantAll, type TenantDto } from '@/api/tenant'
import auth from '@/js/auth.js'
import http from '@/js/httpProxy.js'

const isSuperAdmin = ref(auth.isSuperAdmin())
const tenantList = ref<TenantDto[]>([])

// 部门下拉提示中的租户名：超管取所选租户，普通用户取当前登录租户
const selectedTenantName = computed(() => {
    if (isSuperAdmin.value) {
        const t = tenantList.value.find(i => i.tenantCode === editForm.value.tenantCode)
        return t ? t.tenantName : ''
    }
    return auth.getTenantName()
})

// 租户与部门关系提示：部门归属租户，岗位所属部门必须与岗位租户一致
const deptTip = computed(() => {
    const name = selectedTenantName.value
    if (!name) {
        return isSuperAdmin.value
            ? '请先选择租户，部门下拉将展示该租户下的全部部门'
            : '部门下拉仅展示当前租户下的全部部门；岗位归属部门须与岗位租户一致'
    }
    return isSuperAdmin.value
        ? `仅展示所选租户「${name}」下的全部部门；岗位归属部门须与岗位租户一致，切换租户后请重新选择`
        : `仅展示当前租户「${name}」下的全部部门；岗位归属部门须与岗位租户一致`
})

const list = ref<PositionDto[]>([])
const codeFilter = ref('')
const nameFilter = ref('')
const statusFilter = ref('')
const currentPage = ref(1)
const pageSize = ref(30)
const total = ref(0)
const editVisible = ref(false)
const saving = ref(false)
const editForm = ref<PositionDto>({
    id: '',
    positionCode: '',
    positionName: '',
    deptId: '',
    positionLevel: '',
    status: 'using',
    remark: '',
    tenantCode: ''
})

const userDialogVisible = ref(false)
const currentPositionName = ref('')
const currentPositionId = ref('')
/** 当前岗位所属部门（主岗校验用） */
const currentPositionDeptId = ref('')
const positionUsers = ref<UserPositionDto[]>([])

// ===== 人员管理行内编辑状态 =====
// editingId：null=无编辑；""=新增占位行；否则为编辑中的行id（关联记录id）
const editingId = ref<string | null>(null)
const userEditForm = ref({ id: '', employeeId: '', isPrimary: false })
const candidateUsers = ref<any[]>([])
const positionEmpIds = computed(() => new Set(positionUsers.value.filter(u => u.employeeId).map(u => u.employeeId)))
const deptTree = ref<any[]>([])

// 岗位职级字典项（字典编码：position_level，接口仅返回数据内容 {key, value}）
const levelOptions = ref<Record<string, any>[]>([])
// 岗位名称字典项（字典编码：position_name）
const nameOptions = ref<Record<string, any>[]>([])

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
    loadList()
    loadLevelOptions()
    loadNameOptions()
    if (isSuperAdmin.value) {
        loadTenantList()
    }
}

function loadLevelOptions() {
    if (levelOptions.value.length > 0) {
        return
    }
    queryByCode('position_level').then(({ code, data }) => {
        if (code === 200) {
            levelOptions.value = data || []
        }
    })
}

function loadNameOptions() {
    if (nameOptions.value.length > 0) {
        return
    }
    queryByCode('position_name').then(({ code, data }) => {
        if (code === 200) {
            nameOptions.value = data || []
        }
    })
}

// 职级 key 转展示文本，未匹配时原样返回
function levelLabel(level?: string) {
    if (!level) return ''
    const item = levelOptions.value.find(i => i.key === level)
    return item ? String(item.value ?? level) : level
}

function loadTenantList() {
    queryTenantAll().then(({ code, data }) => {
        if (code === 200) {
            tenantList.value = data || []
        }
    })
}

function loadList() {
    queryPage({
        positionCode: codeFilter.value || undefined,
        positionName: nameFilter.value || undefined,
        status: statusFilter.value || undefined,
        current: currentPage.value,
        size: pageSize.value
    }).then(({ code, data }) => {
        if (code === 200) {
            list.value = data?.data || []
            total.value = data?.total || 0
        }
    })
}

/** 条件变更/查询：回到第一页后重新加载（刷新保留当前页） */
function onSearch() {
    currentPage.value = 1
    loadList()
}

function onSizeChange() {
    currentPage.value = 1
    loadList()
}

function openEdit(row: PositionDto | null) {
    if (row) {
        editForm.value = {
            id: row.id,
            positionCode: row.positionCode,
            positionName: row.positionName,
            deptId: row.deptId || '',
            positionLevel: row.positionLevel ?? '',
            status: row.status || 'using',
            remark: row.remark || '',
            tenantCode: row.tenantCode || ''
        }
    } else {
        editForm.value = {
            id: '',
            positionCode: '',
            positionName: '',
            deptId: '',
            positionLevel: '',
            status: 'using',
            remark: '',
            tenantCode: ''
        }
    }
    editVisible.value = true
    // 部门下拉按岗位租户加载：编辑回显岗位租户下部门，超管新增时需先选租户
    loadDeptTree(editForm.value.tenantCode || undefined)
}

function submitPosition() {
    if (!editForm.value.positionCode) {
        ElMessage.warning('请输入岗位编码')
        return
    }
    if (!editForm.value.positionName) {
        ElMessage.warning('请输入岗位名称')
        return
    }
    if (!editForm.value.deptId) {
        ElMessage.warning('请选择所属部门')
        return
    }
    saving.value = true
    save(editForm.value).then(({ code, msg }) => {
        if (code === 200) {
            ElMessage.success('保存成功')
            editVisible.value = false
            loadList()
        } else {
            ElMessage.error(msg || '保存失败')
        }
    }).finally(() => {
        saving.value = false
    })
}

function removePosition(id: string) {
    deleteById(id).then(({ code, msg }) => {
        if (code === 200) {
            ElMessage.success('删除成功')
            loadList()
        } else {
            ElMessage.error(msg || '删除失败')
        }
    })
}

function showUsers(row: PositionDto) {
    currentPositionName.value = row.positionName
    currentPositionId.value = row.id || ''
    currentPositionDeptId.value = row.deptId || ''
    loadCandidateUsers()
    usersByPosition(currentPositionId.value).then(({ code, data }) => {
        if (code === 200) {
            positionUsers.value = data || []
            editingId.value = null
            userDialogVisible.value = true
        }
    })
}

/** 按租户加载部门树：超管指定租户走 treeByTenantCode，普通用户走 /dept/tree（后端自动租户隔离） */
function loadDeptTree(tenantCode?: string) {
    if (isSuperAdmin.value && tenantCode) {
        http.get('/dept/treeByTenantCode', { params: { tenantCode } }).then(({ data }) => {
            if (data.code === 200) deptTree.value = data.data || []
        })
    } else if (!isSuperAdmin.value) {
        http.get('/dept/tree').then(({ data }) => {
            if (data.code === 200) deptTree.value = data.data || []
        })
    } else {
        // 超管新增岗位且未选租户：无可展示部门
        deptTree.value = []
    }
}

/** 超管切换租户：清空已选部门并重新加载该租户部门树 */
function onTenantChange() {
    editForm.value.deptId = ''
    loadDeptTree(editForm.value.tenantCode || undefined)
}

// ===== 行内编辑 =====

function isEditing(row: UserPositionDto) {
    return editingId.value !== null && row.id === editingId.value
}

function onRowDblClick(row: UserPositionDto, column: any) {
    // 操作列双击不进入编辑
    if (column && column.label === '操作') return
    editRow(row)
}

function editRow(row: UserPositionDto) {
    if (editingId.value === '') {
        removePlaceholder()
    }
    if (editingId.value === row.id) return
    editingId.value = row.id || null
    userEditForm.value = {
        id: row.id || '',
        employeeId: row.employeeId,
        isPrimary: !!row.isPrimary
    }
}

function addUserRow() {
    if (editingId.value === '') {
        removePlaceholder()
    }
    positionUsers.value.unshift({
        id: '', employeeId: '', realName: '', positionId: currentPositionId.value,
        deptId: currentPositionDeptId.value, deptName: '', isPrimary: false
    })
    editingId.value = ''
    userEditForm.value = { id: '', employeeId: '', isPrimary: false }
}

function removePlaceholder() {
    const idx = positionUsers.value.findIndex(r => r.id === '')
    if (idx > -1) {
        positionUsers.value.splice(idx, 1)
    }
}

/** 候选人员 = 当前岗位所属部门下的任职用户（含兼职），需先任职该部门才能担任该岗位 */
function loadCandidateUsers() {
    if (!currentPositionDeptId.value) {
        candidateUsers.value = []
        return
    }
    http.get('/userDept/queryUsersByDept', {
        params: { deptId: currentPositionDeptId.value }
    }).then(({ data }) => {
        if (data.code === 200) candidateUsers.value = data.data || []
    })
}

function onPickUser(employeeId: string) {
    const user = candidateUsers.value.find(u => u.employeeId === employeeId)
    const row = positionUsers.value.find(r => r.id === '')
    if (user && row) {
        row.realName = user.realName
    }
}

/** 勾选主岗：主岗岗位必须位于用户主部门，否则回退 */
function onPrimaryToggle() {
    if (!userEditForm.value.isPrimary) return
    if (!userEditForm.value.employeeId) {
        ElMessage.warning('请先选择人员')
        userEditForm.value.isPrimary = false
        return
    }
    http.get('/userDept/queryByEmployee', { params: { employeeId: userEditForm.value.employeeId } })
        .then(({ data }) => {
            if (data.code === 200) {
                const rels = data.data || []
                const primaryRel = rels.find((r: any) => r.isPrimary)
                if (!primaryRel) {
                    ElMessage.warning('该用户未设置主部门，无法设置主岗')
                    userEditForm.value.isPrimary = false
                    return
                }
                if (primaryRel.deptId !== currentPositionDeptId.value) {
                    ElMessage.warning('主岗必须位于主部门下（该岗位属于其他部门）')
                    userEditForm.value.isPrimary = false
                }
            }
        })
}

function saveRow() {
    const form = userEditForm.value
    if (!form.employeeId) {
        ElMessage.warning('请选择人员')
        return
    }
    saveUserPosition({
        employeeId: form.employeeId,
        positionId: currentPositionId.value,
        isPrimary: form.isPrimary
    }).then(({ code, msg }) => {
        if (code === 200) {
            ElMessage.success('保存成功')
            loadUsersOfPosition()
        } else {
            ElMessage.error(msg || '保存失败')
        }
    })
}

function loadUsersOfPosition() {
    usersByPosition(currentPositionId.value).then(({ code, data }) => {
        if (code === 200) {
            positionUsers.value = data || []
            editingId.value = null
        }
    })
}

function cancelEdit() {
    // 新增行取消：移除占位行
    if (editingId.value === '') {
        removePlaceholder()
    }
    editingId.value = null
}

function removeUserFromPosition(row: UserPositionDto) {
    removeUserPosition(row.employeeId, currentPositionId.value).then(({ code, msg }) => {
        if (code === 200) {
            ElMessage.success('已移出本岗位')
            loadUsersOfPosition()
        } else {
            ElMessage.error(msg || '移出失败')
        }
    })
}
</script>
