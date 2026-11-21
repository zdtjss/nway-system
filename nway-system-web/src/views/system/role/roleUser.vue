<template>
    <div>
        <div style="display:flex;justify-content:space-between;align-items:center;margin-bottom:3px;">
            <span style="color:#999;font-size:12px;">共 {{ userList.length }} 人</span>
            <el-button :icon="Plus" type="primary" size="small" @click="addRow">添加用户</el-button>
        </div>
        <el-table :data="userList" stripe style="width: 100%" max-height="400">
            <el-table-column label="工号" :width="editingId !== null ? 220 : 160">
                <template #default="scope">
                    <span v-if="!isEditing(scope.row)">{{ scope.row.employeeId }}</span>
                    <el-select v-else v-model="editForm.employeeId" filterable remote :remote-method="searchUsers"
                        :loading="userLoading" placeholder="输入工号/姓名搜索" style="width: 100%"
                        @visible-change="onSelectVisible" @change="onUserChange">
                        <el-option v-for="u in userOptions" :key="u.employeeId" :value="u.employeeId"
                            :label="`${u.realName} (${u.employeeId})`" />
                    </el-select>
                </template>
            </el-table-column>
            <el-table-column label="姓名">
                <template #default="scope">
                    <span>{{ isEditing(scope.row) ? (editForm.userName || '未选择') : scope.row.userName }}</span>
                </template>
            </el-table-column>
            <el-table-column label="操作" :width="editingId !== null ? 150 : 100">
                <template #default="scope">
                    <template v-if="isEditing(scope.row)">
                        <el-button link type="primary" :loading="saving" @click="saveRow()">保存</el-button>
                        <el-button link @click="cancelEdit()">取消</el-button>
                    </template>
                    <template v-else>
                        <el-popconfirm title="确定移除该用户？" @confirm="removeUser(scope.row.employeeId)">
                            <template #reference>
                                <el-button link type="danger">移除</el-button>
                            </template>
                        </el-popconfirm>
                    </template>
                </template>
            </el-table-column>
        </el-table>
    </div>
</template>

<script setup>
import { Plus } from '@element-plus/icons-vue'
import { ref, onMounted } from "vue"
import { ElMessage } from 'element-plus'
import http from '@/js/httpProxy.js'

const props = defineProps({
    roleId: String
})

const userList = ref([])
const saving = ref(false)

// 行内编辑状态：editingId 为 null 表示无编辑；为 "" 表示编辑新增行
const editingId = ref(null)
// 编辑表单副本：进入编辑时新建，避免取消时污染数据
const editForm = ref(null)
// 行内编辑用户选择器选项（同租户候选用户，已添加的排除）
const userOptions = ref([])
const userLoading = ref(false)

onMounted(() => {
    loadUsers()
})

function loadUsers() {
    http.get("/userRole/queryByRole", { params: { roleId: props.roleId } })
        .then(({ data }) => {
            userList.value = data.data || []
            editingId.value = null
            editForm.value = null
        });
}

function isEditing(row) {
    return editingId.value !== null && row.employeeId === editingId.value
}

function addRow() {
    // 若当前编辑的是新增占位行，先移除
    if (editingId.value === "") {
        const idx = userList.value.findIndex(r => r.employeeId === "")
        if (idx > -1) {
            userList.value.splice(idx, 1)
        }
    }
    const newRow = { roleId: props.roleId, employeeId: "", userName: "" }
    userList.value.unshift(newRow)
    editingId.value = ""
    editForm.value = { ...newRow }
    searchUsers('')
}

function searchUsers(keyword) {
    // 查询与该角色同租户的用户候选列表（后端按角色租户过滤），已添加的排除
    userLoading.value = true
    http.get("/user/queryForRoleBinding", {
        params: {
            roleId: props.roleId,
            keyword: keyword || ''
        }
    }).then(({ data }) => {
        const exist = new Set(userList.value.filter(u => u.employeeId).map(u => u.employeeId))
        userOptions.value = (data.data?.data || []).filter(u => !exist.has(u.employeeId))
    }).finally(() => {
        userLoading.value = false
    });
}

function onSelectVisible(visible) {
    if (visible) searchUsers('')
}

function onUserChange(employeeId) {
    const u = userOptions.value.find(x => x.employeeId === employeeId)
    editForm.value.userName = u ? u.realName : ''
}

function saveRow() {
    if (!editForm.value) return
    if (!editForm.value.employeeId) {
        ElMessage.warning('请选择要添加的用户')
        return
    }
    const empIds = userList.value
        .filter(u => u.employeeId)
        .map(u => u.employeeId)
    empIds.push(editForm.value.employeeId)
    saveUsers(empIds, () => loadUsers())
}

function cancelEdit() {
    // 新增行取消：移除占位行
    if (editingId.value === "") {
        const idx = userList.value.findIndex(r => r.employeeId === "")
        if (idx > -1) {
            userList.value.splice(idx, 1)
        }
    }
    editingId.value = null
    editForm.value = null
}

function removeUser(employeeId) {
    const empIds = userList.value
        .filter(u => u.employeeId !== employeeId)
        .map(u => u.employeeId)
    saveUsers(empIds, () => loadUsers())
}

function saveUsers(empIds, onSuccess) {
    saving.value = true
    http.post("/userRole/saveByRole", { roleId: props.roleId, employeeIds: empIds })
        .then(({ data }) => {
            if (data.code == 200) {
                ElMessage.success('保存成功')
                onSuccess && onSuccess()
            } else {
                ElMessage.error(data.msg || '保存失败')
            }
        }).finally(() => {
            saving.value = false
        });
}
</script>
