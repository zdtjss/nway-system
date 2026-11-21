<template>
    <div style="width: 98%;margin: 10px auto;">
        <div>
            <el-form :inline="true">
                <el-form-item label="应用ID">
                    <el-select v-model="queryParam.appId" filterable allow-create clearable
                        placeholder="请选择或输入应用ID" style="width: 220px" @keyup.enter="loadData()">
                        <el-option v-for="app in appList" :key="app.id"
                            :label="`${app.appCode}（${app.appName}）`" :value="app.id" />
                    </el-select>
                </el-form-item>
                <el-form-item>
                    <el-button :icon="Search" @click="loadData()">查询</el-button>
                    <el-button :icon="Plus" type="primary" @click="openEdit(null)">新增Webhook</el-button>
                </el-form-item>
            </el-form>
        </div>
        <el-table :data="tableData" stripe border style="width: 100%">
            <el-table-column prop="eventType" label="事件类型" width="200" />
            <el-table-column prop="tenantName" label="租户" width="120">
                <template #default="scope">
                    <el-tag v-if="scope.row.tenantName" size="small" type="info">{{ scope.row.tenantName }}</el-tag>
                </template>
            </el-table-column>
            <el-table-column prop="callbackUrl" label="回调地址" min-width="240" />
            <el-table-column label="状态" width="100">
                <template #default="scope">
                    <el-tag :type="scope.row.status === 'using' ? 'success' : 'danger'" size="small">
                        {{ scope.row.status === 'using' ? '启用' : '禁用' }}
                    </el-tag>
                </template>
            </el-table-column>
            <el-table-column prop="retryCount" label="重试次数" width="100" />
            <el-table-column label="操作" width="200">
                <template #default="scope">
                    <el-button link type="primary" @click="openEdit(scope.row)">编辑</el-button>
                    <el-button link type="warning" @click="testWebhook(scope.row.id)">测试</el-button>
                    <el-popconfirm title="确定删除？" @confirm="removeWebhook(scope.row.id)">
                        <template #reference>
                            <el-button link type="danger">删除</el-button>
                        </template>
                    </el-popconfirm>
                </template>
            </el-table-column>
        </el-table>

        <el-dialog v-model="editVisible" :title="editForm.id ? '编辑Webhook' : '新增Webhook'" width="560" destroy-on-close>
            <el-form :model="editForm" label-width="100px">
                <el-form-item label="应用ID">
                    <el-input v-model="editForm.appId" />
                </el-form-item>
                <el-form-item label="事件类型">
                    <el-select v-model="editForm.eventType" filterable allow-create
                        placeholder="选择或输入事件类型" style="width: 100%">
                        <el-option label="用户创建" value="user.created" />
                        <el-option label="用户更新" value="user.updated" />
                        <el-option label="用户禁用" value="user.disabled" />
                        <el-option label="部门变更" value="dept.changed" />
                        <el-option label="角色变更" value="role.changed" />
                        <el-option label="数据权限变更" value="data_permission.changed" />
                    </el-select>
                </el-form-item>
                <el-form-item label="回调URL">
                    <el-input v-model="editForm.callbackUrl" placeholder="https://..." />
                </el-form-item>
                <el-form-item label="签名密钥">
                    <el-input v-model="editForm.secret" placeholder="HMAC-SHA256 签名密钥" />
                </el-form-item>
                <el-form-item label="状态">
                    <el-select v-model="editForm.status" style="width: 100%">
                        <el-option label="启用" value="using" />
                        <el-option label="禁用" value="disabled" />
                    </el-select>
                </el-form-item>
                <el-form-item label="重试次数">
                    <el-input-number v-model="editForm.retryCount" :min="0" :max="10" />
                </el-form-item>
                <el-form-item v-if="isSuperAdmin" label="租户">
                    <el-select v-model="editForm.tenantCode" placeholder="请选择租户" style="width: 100%" clearable>
                        <el-option v-for="t in tenantList" :key="t.id" :label="t.tenantName" :value="t.tenantCode" />
                    </el-select>
                </el-form-item>
            </el-form>
            <template #footer>
                <el-button @click="editVisible = false">取消</el-button>
                <el-button type="primary" :loading="saving" @click="submitWebhook">保存</el-button>
            </template>
        </el-dialog>
    </div>
</template>

<script setup>
import { ref, onActivated, onMounted } from 'vue'
import { Search, Plus } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import http from '@/js/httpProxy.js'
import auth from '@/js/auth.js'

const isSuperAdmin = ref(auth.isSuperAdmin())
const tenantList = ref([])
const appList = ref([])

const tableData = ref([])
const queryParam = ref({ appId: '' })

const editVisible = ref(false)
const saving = ref(false)
const editForm = ref({
    id: '', appId: '', eventType: '', callbackUrl: '',
    secret: '', status: 'using', retryCount: 3, tenantCode: ''
})

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
    // 进入页面静默加载，应用ID为空时不提示
    loadData(false)
    loadAppList()
    if (isSuperAdmin.value) {
        loadTenantList()
    }
}

function loadAppList() {
    http.get('/openApp/queryAll').then(({ data }) => {
        if (data.code === 200) {
            appList.value = data.data || []
        }
    })
}

function loadTenantList() {
    http.get('/tenant/queryAll').then(({ data }) => {
        if (data.code === 200) {
            tenantList.value = data.data || []
        }
    })
}

function loadData(showWarning = true) {
    if (!queryParam.value.appId) {
        if (showWarning) {
            ElMessage.warning('请输入应用ID')
        }
        tableData.value = []
        return
    }
    http.get('/openWebhook/queryByApp', { params: { appId: queryParam.value.appId } }).then(({ data }) => {
        if (data.code === 200) {
            tableData.value = data.data || []
        }
    })
}

function openEdit(row) {
    if (row) {
        editForm.value = { ...row }
    } else {
        editForm.value = {
            id: '', appId: queryParam.value.appId, eventType: '',
            callbackUrl: '', secret: '', status: 'using', retryCount: 3, tenantCode: ''
        }
    }
    editVisible.value = true
}

function submitWebhook() {
    if (!editForm.value.appId || !editForm.value.eventType || !editForm.value.callbackUrl) {
        ElMessage.warning('应用ID、事件类型、回调URL必填')
        return
    }
    saving.value = true
    const url = editForm.value.id ? '/openWebhook/update' : '/openWebhook/create'
    http.post(url, editForm.value).then(({ data }) => {
        if (data.code === 200) {
            ElMessage.success('保存成功')
            editVisible.value = false
            loadData()
        } else {
            ElMessage.error(data.msg || '保存失败')
        }
    }).finally(() => {
        saving.value = false
    })
}

function testWebhook(id) {
    http.post('/openWebhook/test', null, { params: { webhookId: id } }).then(({ data }) => {
        if (data.code === 200) {
            const status = data.data.statusCode
            if (status >= 200 && status < 300) {
                ElMessage.success(`测试成功 (HTTP ${status})`)
            } else {
                ElMessage.warning(`测试失败 (HTTP ${status})`)
            }
        } else {
            ElMessage.error(data.msg || '测试失败')
        }
    })
}

function removeWebhook(id) {
    http.post('/openWebhook/delete', null, { params: { id } }).then(({ data }) => {
        if (data.code === 200) {
            ElMessage.success('删除成功')
            loadData()
        }
    })
}
</script>
