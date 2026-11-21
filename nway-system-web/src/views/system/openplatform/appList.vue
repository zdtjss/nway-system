<template>
    <div style="width: 98%;margin: 10px auto;">
        <div>
            <el-form :inline="true">
                <el-form-item label="应用编码">
                    <el-input v-model="queryParam.appCode" @keyup.enter="loadData" style="width: 180px" />
                </el-form-item>
                <el-form-item label="应用名称">
                    <el-input v-model="queryParam.appName" @keyup.enter="loadData" style="width: 180px" />
                </el-form-item>
                <el-form-item>
                    <el-button :icon="Search" @click="loadData()">查询</el-button>
                    <el-button :icon="Plus" type="primary" @click="openEdit(null)">新增应用</el-button>
                </el-form-item>
            </el-form>
        </div>
        <el-table :data="tableData" stripe border style="width: 100%">
            <el-table-column prop="appCode" label="应用编码" width="140" />
            <el-table-column prop="appName" label="应用名称" width="180" />
            <el-table-column prop="tenantName" label="租户" width="120">
                <template #default="scope">
                    <el-tag v-if="scope.row.tenantName" size="small" type="info">{{ scope.row.tenantName }}</el-tag>
                </template>
            </el-table-column>
            <el-table-column label="状态" width="100">
                <template #default="scope">
                    <el-tag :type="scope.row.status === 'using' ? 'success' : 'danger'" size="small">
                        {{ scope.row.status === 'using' ? '启用' : '禁用' }}
                    </el-tag>
                </template>
            </el-table-column>
            <el-table-column label="数据范围" width="140">
                <template #default="scope">
                    {{ formatScopeType(scope.row.dataScopeType) }}
                </template>
            </el-table-column>
            <el-table-column prop="rateLimit" label="秒级限流" width="100" />
            <el-table-column prop="dailyLimit" label="日级限流" width="100" />
            <el-table-column prop="expireTime" label="过期时间" width="180" />
            <el-table-column label="操作" width="280">
                <template #default="scope">
                    <el-button link type="primary" @click="openEdit(scope.row)">编辑</el-button>
                    <el-button link type="warning" @click="resetSecret(scope.row)">重置密钥</el-button>
                    <el-button link type="info" @click="configScope(scope.row)">配置Scope</el-button>
                    <el-popconfirm title="确定删除该应用？" @confirm="removeApp(scope.row.id)">
                        <template #reference>
                            <el-button link type="danger">删除</el-button>
                        </template>
                    </el-popconfirm>
                </template>
            </el-table-column>
        </el-table>

        <!-- 应用编辑弹窗 -->
        <el-dialog v-model="editVisible" :title="editForm.id ? '编辑应用' : '新增应用'" width="640" destroy-on-close>
            <el-form :model="editForm" label-width="120px">
                <el-form-item label="应用编码">
                    <el-input v-model="editForm.appCode" :disabled="!!editForm.id" placeholder="如 crm / oa / hr" />
                </el-form-item>
                <el-form-item label="应用名称">
                    <el-input v-model="editForm.appName" />
                </el-form-item>
                <el-form-item v-if="isSuperAdmin" label="租户">
                    <el-select v-model="editForm.tenantCode" placeholder="请选择租户" style="width: 100%" clearable>
                        <el-option v-for="t in tenantList" :key="t.id" :label="t.tenantName" :value="t.tenantCode" />
                    </el-select>
                </el-form-item>
                <el-form-item label="状态">
                    <el-select v-model="editForm.status" style="width: 100%">
                        <el-option label="启用" value="using" />
                        <el-option label="禁用" value="disabled" />
                    </el-select>
                </el-form-item>
                <el-form-item label="授权Scope">
                    <el-select v-model="editForm.grantedScopesList" multiple filterable allow-create
                        placeholder="如 user:read, org:read" style="width: 100%">
                        <el-option label="用户读取" value="user:read" />
                        <el-option label="用户写入" value="user:write" />
                        <el-option label="组织架构读取" value="org:read" />
                        <el-option label="数据权限规则" value="dp:read" />
                        <el-option label="数据权限检查" value="dp:check" />
                        <el-option label="发送消息" value="msg:send" />
                        <el-option label="字典读取" value="dict:read" />
                        <el-option label="租户信息" value="tenant:read" />
                    </el-select>
                </el-form-item>
                <el-form-item label="数据范围类型">
                    <el-select v-model="editForm.dataScopeType" style="width: 100%">
                        <el-option label="全部数据" value="ALL" />
                        <el-option label="本部门" value="DEPT" />
                        <el-option label="本部门及下级" value="DEPT_WITH_SUB" />
                        <el-option label="自定义部门" value="CUSTOM" />
                    </el-select>
                </el-form-item>
                <el-form-item label="秒级限流">
                    <el-input-number v-model="editForm.rateLimit" :min="0" :max="10000" />
                </el-form-item>
                <el-form-item label="日级限流">
                    <el-input-number v-model="editForm.dailyLimit" :min="0" :max="1000000" />
                </el-form-item>
                <el-form-item label="IP白名单">
                    <el-input v-model="editForm.allowedIpsStr" type="textarea" :rows="2"
                        placeholder="一行一个IP，留空表示不限" />
                </el-form-item>
                <el-form-item label="过期时间">
                    <el-date-picker v-model="editForm.expireTime" type="datetime"
                        value-format="YYYY-MM-DDTHH:mm:ss" style="width: 100%"
                        placeholder="留空表示长期有效" />
                </el-form-item>
            </el-form>
            <template #footer>
                <el-button @click="editVisible = false">取消</el-button>
                <el-button type="primary" :loading="saving" @click="submitApp">保存</el-button>
            </template>
        </el-dialog>

        <!-- 密钥展示弹窗 -->
        <el-dialog v-model="secretVisible" title="应用密钥" width="500" destroy-on-close>
            <el-alert type="warning" :closable="false" show-icon style="margin-bottom: 16px">
                密钥仅展示一次，请妥善保存。关闭后将无法再次查看。
            </el-alert>
            <el-input :model-value="currentSecret" readonly type="textarea" :rows="3" />
            <template #footer>
                <el-button type="primary" @click="secretVisible = false">已保存</el-button>
            </template>
        </el-dialog>

        <!-- Scope 配置弹窗 -->
        <el-dialog v-model="scopeVisible" title="配置 Scope" width="500" destroy-on-close>
            <el-select v-model="scopeForm.scopes" multiple filterable allow-create
                placeholder="选择或输入 scope" style="width: 100%">
                <el-option label="用户读取" value="user:read" />
                <el-option label="用户写入" value="user:write" />
                <el-option label="组织架构读取" value="org:read" />
                <el-option label="数据权限规则" value="dp:read" />
                <el-option label="数据权限检查" value="dp:check" />
                <el-option label="发送消息" value="msg:send" />
                <el-option label="字典读取" value="dict:read" />
                <el-option label="文件读取/下载" value="file:read" />
                <el-option label="文件上传" value="file:write" />
                <el-option label="租户信息" value="tenant:read" />
            </el-select>
            <template #footer>
                <el-button @click="scopeVisible = false">取消</el-button>
                <el-button type="primary" :loading="savingScope" @click="submitScope">保存</el-button>
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

const tableData = ref([])
const queryParam = ref({ appCode: '', appName: '' })

const editVisible = ref(false)
const saving = ref(false)
const editForm = ref({
    id: '', appCode: '', appName: '', status: 'using',
    grantedScopesList: [], dataScopeType: 'ALL',
    rateLimit: 100, dailyLimit: 10000,
    allowedIpsStr: '', expireTime: '', tenantCode: ''
})

const secretVisible = ref(false)
const currentSecret = ref('')

const scopeVisible = ref(false)
const savingScope = ref(false)
const scopeForm = ref({ appId: '', scopes: [] })

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
    if (isSuperAdmin.value) {
        loadTenantList()
    }
}

function loadTenantList() {
    http.get('/tenant/queryAll').then(({ data }) => {
        if (data.code === 200) {
            tenantList.value = data.data || []
        }
    })
}

function loadData() {
    http.get('/openApp/queryAll').then(({ data }) => {
        if (data.code === 200) {
            let list = data.data || []
            // 前端过滤
            if (queryParam.value.appCode) {
                list = list.filter(a => a.appCode.includes(queryParam.value.appCode))
            }
            if (queryParam.value.appName) {
                list = list.filter(a => a.appName.includes(queryParam.value.appName))
            }
            tableData.value = list
        }
    })
}

function formatScopeType(type) {
    const map = { ALL: '全部', DEPT: '本部门', DEPT_WITH_SUB: '本部门及下级', CUSTOM: '自定义' }
    return map[type] || type || '全部'
}

function openEdit(row) {
    if (row) {
        editForm.value = {
            id: row.id, appCode: row.appCode, appName: row.appName,
            status: row.status || 'using',
            grantedScopesList: parseScopes(row.grantedScopes),
            dataScopeType: row.dataScopeType || 'ALL',
            rateLimit: row.rateLimit || 100, dailyLimit: row.dailyLimit || 10000,
            allowedIpsStr: parseIps(row.allowedIps),
            expireTime: row.expireTime || '',
            tenantCode: row.tenantCode || ''
        }
    } else {
        editForm.value = {
            id: '', appCode: '', appName: '', status: 'using',
            grantedScopesList: [], dataScopeType: 'ALL',
            rateLimit: 100, dailyLimit: 10000,
            allowedIpsStr: '', expireTime: '', tenantCode: ''
        }
    }
    editVisible.value = true
}

function parseScopes(json) {
    if (!json) return []
    try { return JSON.parse(json) } catch { return [] }
}

function parseIps(json) {
    if (!json) return ''
    try { return JSON.parse(json).join('\n') } catch { return '' }
}

function submitApp() {
    if (!editForm.value.appCode || !editForm.value.appName) {
        ElMessage.warning('应用编码与名称必填')
        return
    }
    saving.value = true
    const payload = {
        id: editForm.value.id || undefined,
        appCode: editForm.value.appCode,
        appName: editForm.value.appName,
        status: editForm.value.status,
        grantedScopes: JSON.stringify(editForm.value.grantedScopesList),
        dataScopeType: editForm.value.dataScopeType,
        rateLimit: editForm.value.rateLimit,
        dailyLimit: editForm.value.dailyLimit,
        allowedIps: editForm.value.allowedIpsStr.trim()
            ? JSON.stringify(editForm.value.allowedIpsStr.trim().split('\n').filter(Boolean))
            : null,
        expireTime: editForm.value.expireTime || null,
        tenantCode: editForm.value.tenantCode || null
    }
    const url = editForm.value.id ? '/openApp/update' : '/openApp/create'
    http.post(url, payload).then(({ data }) => {
        if (data.code === 200) {
            if (!editForm.value.id && data.data && data.data.appSecret) {
                currentSecret.value = data.data.appSecret
                secretVisible.value = true
            }
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

function resetSecret(row) {
    http.post('/openApp/resetSecret', null, { params: { appId: row.id } }).then(({ data }) => {
        if (data.code === 200) {
            currentSecret.value = data.data.appSecret
            secretVisible.value = true
            ElMessage.success('密钥已重置')
        } else {
            ElMessage.error(data.msg || '重置失败')
        }
    })
}

function configScope(row) {
    scopeForm.value = { appId: row.id, scopes: parseScopes(row.grantedScopes) }
    scopeVisible.value = true
}

function submitScope() {
    savingScope.value = true
    http.post('/openApp/grantScopes', scopeForm.value.scopes, {
        params: { appId: scopeForm.value.appId }
    }).then(({ data }) => {
        if (data.code === 200) {
            ElMessage.success('Scope 配置成功')
            scopeVisible.value = false
            loadData()
        } else {
            ElMessage.error(data.msg || '配置失败')
        }
    }).finally(() => {
        savingScope.value = false
    })
}

function removeApp(id) {
    http.post('/openApp/delete', null, { params: { appId: id } }).then(({ data }) => {
        if (data.code === 200) {
            ElMessage.success('删除成功')
            loadData()
        } else {
            ElMessage.error(data.msg || '删除失败')
        }
    })
}
</script>
