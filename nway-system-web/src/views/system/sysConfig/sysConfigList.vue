<template>
    <div style="width: 98%;margin: 10px auto;">
        <div>
            <el-form :inline="true">
                <el-form-item label="配置键">
                    <el-input v-model="queryParam.configKey" @keyup.enter="loadData" style="width: 180px" clearable />
                </el-form-item>
                <el-form-item label="配置名称">
                    <el-input v-model="queryParam.configName" @keyup.enter="loadData" style="width: 180px" clearable />
                </el-form-item>
                <el-form-item label="配置分组">
                    <el-select v-model="queryParam.configGroup" clearable style="width: 140px">
                        <el-option v-for="g in groupOptions" :key="g.value" :label="g.label" :value="g.value" />
                    </el-select>
                </el-form-item>
                <el-form-item label="状态">
                    <el-select v-model="queryParam.status" clearable style="width: 140px">
                        <el-option label="启用" value="using" />
                        <el-option label="禁用" value="disabled" />
                    </el-select>
                </el-form-item>
                <el-form-item>
                    <el-button :icon="Search" @click="onSearch">查询</el-button>
                    <el-button :icon="Plus" type="primary" @click="openEdit(null)">新增配置</el-button>
                </el-form-item>
            </el-form>
        </div>
        <el-table :data="tableData" stripe border style="width: 100%">
            <el-table-column prop="configKey" label="配置键" width="180" />
            <el-table-column prop="configName" label="配置名称" width="160" />
            <el-table-column prop="configValue" label="配置值" min-width="200" show-overflow-tooltip />
            <el-table-column prop="configGroup" label="分组" width="120" />
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
            <el-table-column prop="remark" label="说明" min-width="160" show-overflow-tooltip />
            <el-table-column prop="modifiedTime" label="修改时间" width="180" />
            <el-table-column label="操作" width="160" fixed="right">
                <template #default="scope">
                    <el-button link type="primary" @click="openEdit(scope.row)">编辑</el-button>
                    <el-popconfirm title="确定删除该配置？" @confirm="handleDelete(scope.row.id)">
                        <template #reference>
                            <el-button link type="danger">删除</el-button>
                        </template>
                    </el-popconfirm>
                </template>
            </el-table-column>
        </el-table>
        <div style="display: flex; justify-content: flex-end; margin-top: 10px;">
            <el-pagination :page-sizes="[15, 30, 50, 100]" layout="total, sizes, prev, pager, next"
                v-model:current-page="currentPage" v-model:page-size="pageSize" :default-page-size="15"
                :total="total" />
        </div>

        <el-dialog v-model="editVisible" :title="editForm.id ? '编辑配置' : '新增配置'" width="600px" destroy-on-close>
            <el-form ref="editFormRef" :model="editForm" :rules="formRules" label-width="100px">
                <el-form-item label="配置键" prop="configKey">
                    <el-input v-model="editForm.configKey" :disabled="!!editForm.id" placeholder="如 sys.title" />
                </el-form-item>
                <el-form-item label="配置名称" prop="configName">
                    <el-input v-model="editForm.configName" />
                </el-form-item>
                <el-form-item label="配置值" prop="configValue">
                    <el-input v-model="editForm.configValue" type="textarea" :rows="4" />
                </el-form-item>
                <el-form-item label="配置分组" prop="configGroup">
                    <el-select v-model="editForm.configGroup" style="width: 100%" allow-create filterable>
                        <el-option v-for="g in groupOptions" :key="g.value" :label="g.label" :value="g.value" />
                    </el-select>
                </el-form-item>
                <el-form-item label="状态" prop="status">
                    <el-select v-model="editForm.status" style="width: 100%">
                        <el-option label="启用" value="using" />
                        <el-option label="禁用" value="disabled" />
                    </el-select>
                </el-form-item>
                <el-form-item label="说明">
                    <el-input v-model="editForm.remark" type="textarea" :rows="2" />
                </el-form-item>
                <el-form-item v-if="isSuperAdmin" label="租户">
                    <el-select v-model="editForm.tenantCode" placeholder="请选择租户" style="width: 100%" clearable>
                        <el-option v-for="t in tenantList" :key="t.id" :label="t.tenantName" :value="t.tenantCode" />
                    </el-select>
                </el-form-item>
            </el-form>
            <template #footer>
                <el-button @click="editVisible = false">取消</el-button>
                <el-button type="primary" :loading="saving" @click="submitConfig">保存</el-button>
            </template>
        </el-dialog>
    </div>
</template>

<script setup>
import { ref, onActivated, onMounted, watch } from 'vue'
import { Search, Plus } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import { queryPage, save, deleteById } from '@/api/sysConfig'
import { queryAll as queryTenantAll } from '@/api/tenant'
import auth from '@/js/auth.js'

const isSuperAdmin = ref(auth.isSuperAdmin())
const tenantList = ref([])

const tableData = ref([])

const queryParam = ref({
    configKey: '',
    configName: '',
    configGroup: '',
    status: ''
})

const groupOptions = [
    { value: 'system', label: 'system' },
    { value: 'app', label: 'app' },
    { value: 'email', label: 'email' },
    { value: 'security', label: 'security' }
]

const editVisible = ref(false)
const saving = ref(false)
const editFormRef = ref(null)
const formRules = {
    configKey: [{ required: true, message: '请输入配置键', trigger: 'blur' }],
    configName: [{ required: true, message: '请输入配置名称', trigger: 'blur' }],
    configValue: [{ required: true, message: '请输入配置值', trigger: 'blur' }],
    configGroup: [{ required: true, message: '请选择配置分组', trigger: 'change' }],
    status: [{ required: true, message: '请选择状态', trigger: 'change' }]
}
const editForm = ref({
    id: '',
    configKey: '',
    configValue: '',
    configName: '',
    configGroup: 'system',
    remark: '',
    status: 'using',
    tenantCode: ''
})

const pageSize = ref(15)
const currentPage = ref(1)
const total = ref(0)

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
    queryTenantAll().then(({ code, data }) => {
        if (code === 200) {
            tenantList.value = data || []
        }
    })
}

watch(currentPage, () => loadData())
watch(pageSize, () => loadData())

function onSearch() {
    currentPage.value = 1
    loadData()
}

function loadData() {
    queryPage({
        configKey: queryParam.value.configKey,
        configName: queryParam.value.configName,
        configGroup: queryParam.value.configGroup,
        status: queryParam.value.status,
        page: currentPage.value,
        pageSize: pageSize.value
    }).then((res) => {
        if (res.code === 200) {
            tableData.value = res.data.data || []
            total.value = res.data.total
        }
    })
}

function openEdit(row) {
    if (row) {
        editForm.value = {
            id: row.id,
            configKey: row.configKey,
            configValue: row.configValue || '',
            configName: row.configName,
            configGroup: row.configGroup || 'system',
            remark: row.remark || '',
            status: row.status || 'using',
            tenantCode: row.tenantCode || ''
        }
    } else {
        editForm.value = {
            id: '',
            configKey: '',
            configValue: '',
            configName: '',
            configGroup: 'system',
            remark: '',
            status: 'using',
            tenantCode: ''
        }
    }
    editVisible.value = true
}

async function submitConfig() {
    if (!editFormRef.value) return
    try {
        await editFormRef.value.validate()
    } catch {
        return
    }
    saving.value = true
    save(editForm.value).then((res) => {
        if (res.code === 200) {
            ElMessage.success('保存成功')
            editVisible.value = false
            loadData()
        } else {
            ElMessage.error(res.msg || '保存失败')
        }
    }).finally(() => {
        saving.value = false
    })
}

function handleDelete(id) {
    deleteById(id).then((res) => {
        if (res.code === 200) {
            ElMessage.success('删除成功')
            loadData()
        } else {
            ElMessage.error(res.msg || '删除失败')
        }
    })
}
</script>
