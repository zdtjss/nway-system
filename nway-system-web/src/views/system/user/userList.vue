<template>
    <div style="width: 98%;margin: 10px auto;">
        <div>
            <el-form :model="queryParam" label-width="100px" width="80%" :inline="true">
                <el-form-item label="工号">
                    <el-input v-model="queryParam.employeeId" />
                </el-form-item>
                <el-form-item label="姓名">
                    <el-input v-model="queryParam.realName" />
                </el-form-item>
                <el-form-item label="状态">
                    <el-select v-model="queryParam.status" clearable style="width: 140px">
                        <el-option label="使用中" value="using" />
                        <el-option label="已禁用" value="disabled" />
                    </el-select>
                </el-form-item>
                <el-form-item>
                    <el-button :icon="Search" @click="loadData">查询</el-button>
                </el-form-item>
            </el-form>
        </div>
        <div>
            <div style="position: relative;float: right;">
                <el-button :icon="Plus" size="small" link @click="editUser()" />
            </div>
            <el-table :data="tableData" v-loading="loading" stripe style="width: 100%">
                <el-table-column prop="employeeId" label="工号" width="180" />
                <el-table-column prop="realName" label="姓名" width="180" />
                <el-table-column prop="avatar" label="头像" width="100">
                    <template #default="scope">
                        <div style="display: flex; align-items: center">
                            <img v-if="scope.row.avatar" width="32" height="32" :src="scope.row.avatar">
                        </div>
                    </template>
                </el-table-column>
                <el-table-column label="角色" width="180">
                    <template #default="scope">
                        <el-button link @click="showRole(scope.row.employeeId)">查看</el-button>
                    </template>
                </el-table-column>
                <el-table-column prop="phoneNumber" label="手机号" width="180" />
                <el-table-column prop="deptName" label="部门" width="140">
                    <template #default="scope">
                        <span>{{ scope.row.deptName || '未分配' }}</span>
                    </template>
                </el-table-column>
                <el-table-column prop="email" label="邮箱" width="250" />
                <el-table-column prop="tenantName" label="租户" width="120">
                    <template #default="scope">
                        <el-tag v-if="scope.row.tenantName" size="small" type="info">{{ scope.row.tenantName }}</el-tag>
                    </template>
                </el-table-column>
                <el-table-column label="编辑人" width="250">
                    <template #default="scope">
                        <div>
                            <span style="margin-right: 5px;">{{ scope.row.modifierName }}</span>
                            <span>{{ scope.row.modifierId }}</span>
                        </div>
                    </template>
                </el-table-column>
                <el-table-column prop="modifiedTime" label="编辑时间" width="180" />
                <el-table-column prop="status" label="状态" width="100">
                    <template #default="scope">
                        <el-tag :type="scope.row.status === 'using' ? 'success' : 'danger'" size="small">
                            {{ scope.row.status === 'using' ? '使用中' : '已禁用' }}
                        </el-tag>
                    </template>
                </el-table-column>
                <el-table-column label="操作" width="380">
                    <template #default="scope">
                        <div>
                            <el-button link type="primary" @click="editUser(scope.row.id)">编辑</el-button>
                            <el-button link type="primary" @click="showRole(scope.row.employeeId, true)">角色</el-button>
                            <el-button link type="primary" @click="showDept(scope.row)">部门</el-button>
                            <el-button link type="primary" @click="showPosition(scope.row)">岗位</el-button>
                            <el-popconfirm title="确定重置该用户密码？重置后将生成随机新密码并需用户首登改密" @confirm="resetPassword(scope.row.employeeId)">
                                <template #reference>
                                    <el-button link type="warning">重置密码</el-button>
                                </template>
                            </el-popconfirm>
                            <el-popconfirm v-if="scope.row.status === 'using'"
                                title="确定禁用该用户？禁用后立即失效登录。" @confirm="disableUser(scope.row.employeeId)">
                                <template #reference>
                                    <el-button link type="warning">禁用</el-button>
                                </template>
                            </el-popconfirm>
                            <el-popconfirm v-else title="确定启用该用户？" @confirm="enableUser(scope.row.employeeId)">
                                <template #reference>
                                    <el-button link type="success">启用</el-button>
                                </template>
                            </el-popconfirm>
                        </div>
                    </template>
                </el-table-column>
            </el-table>
            <div style="display: flex; justify-content: flex-end; margin-top: 10px;">
                <el-pagination :page-sizes="[30, 50, 100]" layout="total, sizes, prev, pager, next"
                    v-model:current-page="currentPage" v-model:page-size="pageSize" :default-page-size="30"
                    :total="total" />
            </div>
        </div>

        <el-dialog v-model="dialogUserEditVisible" title="新增/修改用户" :draggable="true" width="800" style="height: 500px;"
            destroy-on-close>
            <user :user-id="userIdEdit" @close="onUserDialogClose" />
        </el-dialog>

        <el-dialog v-model="dialogRoleListViewVisible" title="角色查看" width="750" style="height: 350px;">
            <div>
                <span v-for="role in userRoleList" :title="role.roleCode" class="role_item">{{ role.roleName }}</span>
            </div>
            <template #footer>
            <div class="dialog-footer">
                <el-button @click="dialogRoleListViewVisible = false">关闭</el-button>
            </div>
            </template>
        </el-dialog>

        <el-dialog v-model="dialogRoleListVisible" title="角色管理" :draggable="true" width="750" style="height: 350px;" destroy-on-close>
            <userRole :employee-id="employeeId" @close="dialogRoleListVisible = false" />
        </el-dialog>

        <el-dialog v-model="dialogDeptVisible" title="调整部门" width="520" destroy-on-close>
            <div style="margin-bottom: 10px; color: #606266;">
                用户：<b>{{ currentUser.realName }}</b>（{{ currentUser.employeeId }}）
            </div>
            <el-form label-width="80px">
                <el-form-item label="任职部门">
                    <el-tree-select v-model="userDeptIds" :data="deptTreeData"
                        :props="{ label: 'deptName', value: 'id', children: 'children' }"
                        node-key="id" multiple check-strictly default-expand-all filterable collapse-tags
                        style="width: 100%" placeholder="选择任职部门（可多选）" @change="onUserDeptIdsChange" />
                </el-form-item>
                <el-form-item label="主部门">
                    <el-select v-model="primaryDeptId" style="width: 100%" placeholder="请选择主部门">
                        <el-option v-for="id in userDeptIds" :key="id" :label="deptNameOf(id)" :value="id" />
                    </el-select>
                </el-form-item>
            </el-form>
            <template #footer>
                <el-button @click="dialogDeptVisible = false">取消</el-button>
                <el-button type="primary" @click="submitDeptAssign">保存</el-button>
            </template>
        </el-dialog>

        <!-- 岗位管理弹窗：岗位 + 任职部门 + 主岗 -->
        <el-dialog v-model="dialogPositionVisible" title="岗位管理" width="720" destroy-on-close>
            <div style="display:flex;justify-content:space-between;align-items:center;margin-bottom:10px;">
                <span style="color:#999;font-size:12px;">
                    用户：<b>{{ currentUser.realName }}</b>（{{ currentUser.employeeId }}）·
                    主部门：{{ primaryDeptName || '未设置' }}
                </span>
                <el-button :icon="Plus" type="primary" size="small" @click="addPositionRow">添加岗位</el-button>
            </div>
            <el-table :data="positionRows" stripe border style="width: 100%" max-height="380">
                <el-table-column label="岗位" min-width="230">
                    <template #default="scope">
                        <el-select v-model="scope.row.positionId" filterable placeholder="选择岗位" style="width: 100%">
                            <el-option v-for="p in positionOptions" :key="p.id"
                                :label="`${p.positionName}（${p.positionCode}）`" :value="p.id" />
                        </el-select>
                    </template>
                </el-table-column>
                <el-table-column label="所属部门" min-width="160">
                    <template #default="scope">
                        <el-tag v-if="positionDeptName(scope.row.positionId)" size="small" type="info">
                            {{ positionDeptName(scope.row.positionId) }}
                        </el-tag>
                        <span v-else style="color:#999;">未选择岗位</span>
                    </template>
                </el-table-column>
                <el-table-column label="主岗" width="70" align="center">
                    <template #default="scope">
                        <el-radio :model-value="primaryRowKey" :value="scope.row._key"
                            @change="onPrimaryChange(scope.row)" />
                    </template>
                </el-table-column>
                <el-table-column label="操作" width="80" align="center">
                    <template #default="scope">
                        <el-button link type="danger" @click="removePositionRow(scope.row._key)">移除</el-button>
                    </template>
                </el-table-column>
            </el-table>
            <template #footer>
                <el-button @click="dialogPositionVisible = false">取消</el-button>
                <el-button type="primary" :loading="saving" @click="submitPositions">保存</el-button>
            </template>
        </el-dialog>
    </div>
</template>

<script setup>
import { Search, Plus } from '@element-plus/icons-vue'
import { ref, onActivated, onMounted, watch, computed } from "vue"
import http from '@/js/httpProxy.js'
import user from './userEdit.vue'
import userRole from './userRole.vue'

const tableData = ref([])

const queryParam = ref({
    employeeId: "",
    realName: "",
    status: ""
})

const userIdEdit = ref()
const dialogUserEditVisible = ref(false)

const userRoleList = ref("")

const employeeId = ref("")
const dialogRoleListVisible = ref(false)
const dialogRoleListViewVisible = ref(false)

const deptTreeData = ref([])
const dialogDeptVisible = ref(false)
const currentUser = ref({})
const userDeptIds = ref([])
const primaryDeptId = ref('')

// 岗位管理弹窗状态
const dialogPositionVisible = ref(false)
const positionRows = ref([])
const allPositionOptions = ref([])
const positionOptions = ref([])
const userPositionDeptIds = ref([])
const primaryDeptName = ref('')
const primaryRowKey = computed(() => (positionRows.value.find(r => r.isPrimary) || {})._key || '')
let positionRowSeq = 0
const pageSize = ref(30)
const currentPage = ref(1)
const total = ref(0)

// 数据加载中标识：避免首次打开时空表格闪现
const loading = ref(false)

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
    loadData()
    loadDeptTree()
}

function loadDeptTree() {
    http.get("/dept/tree").then(({ data }) => {
        if (data.code == 200) {
            deptTreeData.value = data.data || []
        }
    });
}

watch(currentPage, async (n, o) => {
    loadData()
})

watch(pageSize, async (n, o) => {
    loadData()
})

function loadData() {
    loading.value = true
    http.get("/user/queryPage", {
        params: {
            employeeId: queryParam.value.employeeId,
            realName: queryParam.value.realName,
            status: queryParam.value.status,
            current: currentPage.value,
            size: pageSize.value
        }
    }).then(({ data }) => {
        tableData.value = data.data.data
        total.value = data.data.total
    }).finally(() => {
        loading.value = false
    });
}

function disableUser(employeeId) {
    http.post("/user/disable", null, { params: { employeeId } }).then(({ data }) => {
        if (data.code == 200) {
            ElMessage({ message: "已禁用", type: 'success' })
            loadData()
        }
    });
}

function enableUser(employeeId) {
    http.post("/user/enable", null, { params: { employeeId } }).then(({ data }) => {
        if (data.code == 200) {
            ElMessage({ message: "已启用", type: 'success' })
            loadData()
        }
    });
}

function resetPassword(employeeId) {
    http.post("/user/resetPassword", null, { params: { employeeId } }).then(({ data }) => {
        if (data.code == 200) {
            // 服务端返回一次性随机密码，需转告用户（该用户下次登录后必须改密）
            ElMessageBox.alert(
                `新密码：${data.data}\n\n请将新密码转告该用户，该用户下次登录后必须修改密码。`,
                '密码重置成功', {
                confirmButtonText: '我已记录',
                closeOnClickModal: false,
                showClose: false
            })
        }
    });
}

function editUser(userId) {
    userIdEdit.value = userId || ""
    dialogUserEditVisible.value = true
}

function onUserDialogClose() {
    dialogUserEditVisible.value = false
    loadData()
}

function showRole(empid, isAdmin = false) {
    if (isAdmin) {
        employeeId.value = empid || ""
        dialogRoleListVisible.value = true
    } else {
        dialogRoleListViewVisible.value = true
        http.get("/userRole/queryByUser", { params: { userId: empid } })
            .then(({ data }) => {
                userRoleList.value = data.data
            });
    }
}

function showDept(row) {
    currentUser.value = row
    // 加载当前用户权限范围内的部门树（根节点已标识租户名称）
    http.get("/dept/permissionTree").then(({ data }) => {
        if (data.code == 200) {
            deptTreeData.value = data.data || []
        }
    });
    // 查询用户现有任职部门（含主部门）
    http.get("/userDept/queryByEmployee", { params: { employeeId: row.employeeId } })
        .then(({ data }) => {
            if (data.code == 200) {
                const rels = data.data || []
                userDeptIds.value = rels.map(r => r.deptId)
                const primary = rels.find(r => r.isPrimary)
                primaryDeptId.value = primary ? primary.deptId : (userDeptIds.value[0] || '')
                dialogDeptVisible.value = true
            }
        });
}

/** 任职部门变化时，主部门不在选中集则自动切换为第一个 */
function onUserDeptIdsChange(ids) {
    if (!ids.includes(primaryDeptId.value)) {
        primaryDeptId.value = ids[0] || ''
    }
}

function deptNameOf(deptId) {
    return findDeptName(deptTreeData.value, deptId)
}

function findDeptName(list, deptId) {
    for (const n of list) {
        if (n.id === deptId) return n.deptName
        if (n.children?.length) {
            const name = findDeptName(n.children, deptId)
            if (name) return name
        }
    }
    return ''
}

function submitDeptAssign() {
    if (!userDeptIds.value.length) {
        ElMessage({ message: '请至少选择一个任职部门', type: 'warning' })
        return
    }
    const primary = primaryDeptId.value || userDeptIds.value[0]
    const secondary = userDeptIds.value.filter(id => id !== primary)
    http.post("/userDept/save", secondary, { params: { employeeId: currentUser.value.employeeId, primaryDeptId: primary } })
        .then(({ data }) => {
            if (data.code == 200) {
                ElMessage({ message: "部门已更新", type: 'success' })
                dialogDeptVisible.value = false
                loadData()
            }
        });
}

// ===== 岗位管理 =====

function showPosition(row) {
    currentUser.value = row
    // 先查用户任职部门，岗位下拉仅展示任职部门下的岗位
    http.get("/userDept/queryByEmployee", { params: { employeeId: row.employeeId } })
        .then(({ data }) => {
            if (data.code == 200) {
                const rels = data.data || []
                userPositionDeptIds.value = rels.map(r => r.deptId)
                const primary = rels.find(r => r.isPrimary)
                primaryDeptId.value = primary ? primary.deptId : ''
                primaryDeptName.value = primary ? (deptNameOf(primary.deptId) || primary.deptId) : ''
                loadPositionOptions()
                loadUserPositions()
            }
        });
}

/** 加载全部岗位并过滤出用户任职部门下的岗位 */
function loadPositionOptions() {
    if (!allPositionOptions.value.length) {
        http.get("/position/queryAll").then(({ data }) => {
            if (data.code == 200) {
                allPositionOptions.value = data.data || []
                positionOptions.value = filterPositionOptions()
            }
        });
    } else {
        positionOptions.value = filterPositionOptions()
    }
}

/** 岗位选项 = 用户任职部门（含主部门与兼职部门）下的岗位 */
function filterPositionOptions() {
    const deptIds = userPositionDeptIds.value
    return deptIds.length ? allPositionOptions.value.filter(p => deptIds.includes(p.deptId)) : []
}

function loadUserPositions() {
    http.get("/position/userPositions", { params: { employeeId: currentUser.value.employeeId } })
        .then(({ data }) => {
            if (data.code == 200) {
                positionRows.value = (data.data || []).map(r => ({
                    _key: ++positionRowSeq,
                    positionId: r.positionId,
                    isPrimary: !!r.isPrimary
                }))
                dialogPositionVisible.value = true
            }
        });
}

/** 岗位所属部门名称（从全量岗位列表推导，避免被过滤后查不到） */
function positionDeptName(positionId) {
    const p = allPositionOptions.value.find(x => x.id === positionId)
    return p ? (p.deptName || '') : ''
}

function addPositionRow() {
    positionRows.value.push({ _key: ++positionRowSeq, positionId: '', isPrimary: false })
}

function removePositionRow(key) {
    positionRows.value = positionRows.value.filter(r => r._key !== key)
}

/** 设置主岗：主岗唯一，且岗位必须位于用户主部门 */
function onPrimaryChange(row) {
    positionRows.value.forEach(r => { r.isPrimary = (r._key === row._key) })
    if (row.isPrimary) {
        if (!primaryDeptId.value) {
            ElMessage({ message: '该用户未设置主部门，无法设置主岗，请先在部门管理中设置', type: 'warning' })
            row.isPrimary = false
            return
        }
        const pos = allPositionOptions.value.find(x => x.id === row.positionId)
        if (!pos || pos.deptId !== primaryDeptId.value) {
            ElMessage({ message: '主岗必须位于主部门下（该岗位属于其他部门）', type: 'warning' })
            row.isPrimary = false
        }
    }
}

function submitPositions() {
    const rows = positionRows.value
    if (rows.some(r => !r.positionId)) {
        ElMessage({ message: '请为每行选择岗位', type: 'warning' })
        return
    }
    const ids = rows.map(r => r.positionId)
    if (new Set(ids).size !== ids.length) {
        ElMessage({ message: '岗位不能重复', type: 'warning' })
        return
    }
    const primaryRows = rows.filter(r => r.isPrimary)
    if (primaryRows.length === 1) {
        const pos = allPositionOptions.value.find(x => x.id === primaryRows[0].positionId)
        if (!pos || pos.deptId !== primaryDeptId.value) {
            ElMessage({ message: '主岗必须位于主部门下（该岗位属于其他部门）', type: 'warning' })
            return
        }
    }
    const payload = rows.map(r => ({
        employeeId: currentUser.value.employeeId,
        positionId: r.positionId,
        isPrimary: r.isPrimary
    }))
    http.post("/position/saveUserPositions", payload, { params: { employeeId: currentUser.value.employeeId } })
        .then(({ data }) => {
            if (data.code == 200) {
                ElMessage({ message: "岗位已更新", type: 'success' })
                dialogPositionVisible.value = false
            }
        });
}

</script>

<style lang="css" scoped>
.role_item {
    display: inline-block;
    margin: 5px 3px;
    padding: 5px;
}
.dialog-footer {
    bottom: 15px;
    right: 20px;
    position: absolute;
}
</style>