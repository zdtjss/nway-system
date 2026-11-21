<template>
    <div style="width: 98%;margin: 10px auto;">
        <div>
            <div style="position: relative;float: right;margin-bottom: 6px;">
                <el-button type="primary" :icon="Plus" size="small" @click="openCreate()">任命管理员</el-button>
            </div>
            <el-table :data="tableData" v-loading="loading" stripe style="width: 100%">
                <el-table-column prop="employeeId" label="工号" width="160" />
                <el-table-column prop="realName" label="姓名" width="140" />
                <el-table-column label="业务范围" min-width="220">
                    <template #default="scope">
                        <el-tag v-for="code in scope.row.bizCodes" :key="code" size="small" style="margin: 2px;">
                            {{ bizNameOf(code) }}
                        </el-tag>
                        <span v-if="!scope.row.bizCodes || !scope.row.bizCodes.length" style="color: #909399;">未授权业务</span>
                    </template>
                </el-table-column>
                <el-table-column label="授权租户" min-width="220">
                    <template #default="scope">
                        <el-tag v-for="code in scope.row.tenantCodes" :key="code" size="small" type="info" style="margin: 2px;">
                            {{ tenantNameOf(code) }}
                        </el-tag>
                        <span v-if="!scope.row.tenantCodes || !scope.row.tenantCodes.length" style="color: #909399;">仅本租户</span>
                    </template>
                </el-table-column>
                <el-table-column label="操作" width="150" fixed="right">
                    <template #default="scope">
                        <el-button link type="primary" :icon="Edit" @click="openEdit(scope.row)">编辑</el-button>
                        <el-popconfirm title="确定撤销该管理员？撤销后立即失去管理权限。" @confirm="revoke(scope.row)">
                            <template #reference>
                                <el-button link type="danger" :icon="Delete">撤销</el-button>
                            </template>
                        </el-popconfirm>
                    </template>
                </el-table-column>
            </el-table>
        </div>

        <el-dialog v-model="dialogVisible" :title="isEdit ? '编辑管理员' : '任命管理员'" width="640px" destroy-on-close>
            <el-form :model="form" label-width="90px">
                <el-form-item label="管理员" required>
                    <el-select v-if="!isEdit" v-model="form.employeeId" filterable remote :remote-method="searchUsers"
                        :loading="userLoading" placeholder="输入姓名或工号搜索用户" style="width: 100%" @change="onUserChange">
                        <el-option v-for="u in userOptions" :key="u.employeeId"
                            :label="`${u.realName}（${u.employeeId}）`" :value="u.employeeId">
                            <div style="display: flex; justify-content: space-between; gap: 16px;">
                                <span>{{ u.realName }}</span>
                                <span style="color: #909399;">{{ u.employeeId }}</span>
                            </div>
                        </el-option>
                    </el-select>
                    <el-input v-else :model-value="`${form.realName}（${form.employeeId}）`" disabled />
                </el-form-item>
                <el-form-item label="业务大类">
                    <el-select v-model="form.bizCodes" multiple collapse-tags placeholder="选择可管理的业务大类" style="width: 100%">
                        <el-option v-for="b in bizOptions" :key="b.bizCode" :label="b.bizName" :value="b.bizCode" />
                    </el-select>
                </el-form-item>
                <el-form-item label="授权租户">
                    <el-select v-model="form.tenantCodes" multiple collapse-tags clearable placeholder="留空表示仅可管理本租户" style="width: 100%">
                        <el-option v-for="t in tenantList" :key="t.tenantCode" :label="t.tenantName" :value="t.tenantCode" />
                    </el-select>
                </el-form-item>
            </el-form>
            <template #footer>
                <el-button @click="dialogVisible = false">取消</el-button>
                <el-button type="primary" :loading="saving" @click="save()">保存</el-button>
            </template>
        </el-dialog>
    </div>
</template>

<script setup>
import { Plus, Edit, Delete } from '@element-plus/icons-vue'
import { ref, onMounted, computed } from 'vue'
import { ElMessage } from 'element-plus'
import http from '@/js/httpProxy.js'
import auth from '@/js/auth.js'

const tableData = ref([])
const loading = ref(false)
const bizList = ref([])
const tenantList = ref([])
const bizNameMap = ref({})
const tenantNameMap = ref({})

const dialogVisible = ref(false)
const saving = ref(false)
const isEdit = ref(false)
const form = ref({ employeeId: '', realName: '', bizCodes: [], tenantCodes: [] })

const userOptions = ref([])
const userLoading = ref(false)

onMounted(() => {
    loadData()
    loadBizOptions()
    loadTenantOptions()
})

function loadData() {
    loading.value = true
    http.get('/admin/list').then(({ data }) => {
        if (data.code === 200) {
            tableData.value = data.data || []
        }
    }).finally(() => {
        loading.value = false
    })
}

function loadBizOptions() {
    http.get('/app/queryByStatus', { params: { status: 'using' } }).then(({ data }) => {
        if (data.code === 200) {
            bizList.value = (data.data || []).map(a => ({ bizCode: a.appCode, bizName: a.appName, tenantCode: a.tenantCode }))
            const map = {}
            bizList.value.forEach(b => { map[b.bizCode] = b.bizName })
            bizNameMap.value = map
        }
    })
}

// 业务大类下拉：仅展示本租户 + 授权租户下的业务（已合并业务租户筛选，与后端授权校验保持一致）；编辑时已选但不在范围内的业务一并保留展示
const bizOptions = computed(() => {
    const allowedTenants = new Set([auth.getTenantCode(), ...(form.value.tenantCodes || [])].filter(Boolean))
    const list = bizList.value.filter(b => allowedTenants.has(b.tenantCode))
    const codes = new Set(list.map(b => b.bizCode))
    const extra = (form.value.bizCodes || [])
        .filter(c => !codes.has(c))
        .map(c => ({ bizCode: c, bizName: bizNameMap.value[c] || c }))
    return [...list, ...extra]
})

function loadTenantOptions() {
    http.get('/tenant/queryAll').then(({ data }) => {
        if (data.code === 200) {
            tenantList.value = data.data || []
            const map = {}
            tenantList.value.forEach(t => { map[t.tenantCode] = t.tenantName })
            tenantNameMap.value = map
        }
    })
}

function bizNameOf(code) {
    return bizNameMap.value[code] || code
}

function tenantNameOf(code) {
    return tenantNameMap.value[code] || code
}

function openCreate() {
    isEdit.value = false
    form.value = { employeeId: '', realName: '', bizCodes: [], tenantCodes: [] }
    userOptions.value = []
    loadTenantOptions()
    dialogVisible.value = true
}

function openEdit(row) {
    isEdit.value = true
    loadTenantOptions()
    dialogVisible.value = true
    http.get('/admin/queryByEmployee', { params: { employeeId: row.employeeId } }).then(({ data }) => {
        if (data.code === 200) {
            const dto = data.data || {}
            const bizCodes = dto.bizCodes || []
            form.value = {
                employeeId: dto.employeeId || row.employeeId,
                realName: dto.realName || row.realName || '',
                bizCodes: bizCodes,
                tenantCodes: dto.tenantCodes || []
            }
        }
    })
}

function searchUsers(keyword) {
    if (!keyword) {
        userOptions.value = []
        return
    }
    userLoading.value = true
    http.get('/user/queryPage', { params: { keyword, status: 'using', current: 1, size: 30 } })
        .then(({ data }) => {
            if (data.code === 200) {
                userOptions.value = data.data?.data || []
            }
        })
        .finally(() => {
            userLoading.value = false
        })
}

function onUserChange(employeeId) {
    const u = userOptions.value.find(x => x.employeeId === employeeId)
    if (u) form.value.realName = u.realName || ''
}

function save() {
    if (!form.value.employeeId) {
        ElMessage.warning('请选择管理员')
        return
    }
    saving.value = true
    http.post('/admin/grant', {
        employeeId: form.value.employeeId,
        bizCodes: form.value.bizCodes || [],
        tenantCodes: form.value.tenantCodes || []
    }).then(({ data }) => {
        if (data.code === 200) {
            ElMessage.success('保存成功')
            dialogVisible.value = false
            loadData()
        }
    }).finally(() => {
        saving.value = false
    })
}

function revoke(row) {
    http.post('/admin/revoke', null, { params: { employeeId: row.employeeId } }).then(({ data }) => {
        if (data.code === 200) {
            ElMessage.success('已撤销管理员')
            loadData()
        }
    })
}
</script>
