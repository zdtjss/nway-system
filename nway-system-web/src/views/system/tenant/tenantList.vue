<template>
    <div style="width: 98%;margin: 10px auto;">
        <div>
            <el-form :inline="true">
                <el-form-item label="租户编码">
                    <el-input v-model="queryParam.tenantCode" @keyup.enter="loadData" style="width: 180px" />
                </el-form-item>
                <el-form-item label="租户名称">
                    <el-input v-model="queryParam.tenantName" @keyup.enter="loadData" style="width: 180px" />
                </el-form-item>
                <el-form-item label="状态">
                    <el-select v-model="queryParam.status" clearable style="width: 140px">
                        <el-option label="启用" value="using" />
                        <el-option label="禁用" value="disabled" />
                    </el-select>
                </el-form-item>
                <el-form-item>
                    <el-button :icon="Search" @click="loadData()">查询</el-button>
                    <el-button :icon="Plus" type="primary" @click="openEdit(null)">新增租户</el-button>
                </el-form-item>
            </el-form>
        </div>
        <el-table :data="tableData" stripe border style="width: 100%">
            <el-table-column prop="tenantCode" label="租户编码" width="160" />
            <el-table-column prop="tenantName" label="租户名称" width="200" />
            <el-table-column label="状态" width="100">
                <template #default="scope">
                    <el-tag :type="scope.row.status === 'using' ? 'success' : 'danger'" size="small">
                        {{ scope.row.status === 'using' ? '启用' : '禁用' }}
                    </el-tag>
                </template>
            </el-table-column>
            <el-table-column prop="expireDate" label="到期日期" width="180">
                <template #default="scope">
                    {{ scope.row.expireDate || '长期' }}
                </template>
            </el-table-column>
            <el-table-column prop="remark" label="备注" min-width="160" />
            <el-table-column prop="modifiedTime" label="更新时间" width="180" />
            <el-table-column label="操作" width="180">
                <template #default="scope">
                    <el-button link type="primary" @click="openEdit(scope.row)">编辑</el-button>
                    <el-popconfirm title="确定删除该租户？默认租户不可删除。" @confirm="removeTenant(scope.row.id)">
                        <template #reference>
                            <el-button link type="danger">删除</el-button>
                        </template>
                    </el-popconfirm>
                </template>
            </el-table-column>
        </el-table>
        <div style="display: flex; justify-content: flex-end; margin-top: 10px;">
            <el-pagination :page-sizes="[30, 50, 100]" layout="total, sizes, prev, pager, next"
                v-model:current-page="currentPage" v-model:page-size="pageSize" :default-page-size="30"
                :total="total" />
        </div>

        <el-dialog v-model="editVisible" :title="editForm.id ? '编辑租户' : '新增租户'" width="520" destroy-on-close>
            <el-form ref="editFormRef" :model="editForm" :rules="formRules" label-width="100px">
                <el-form-item label="租户编码" prop="tenantCode">
                    <el-input v-model="editForm.tenantCode" :disabled="!!editForm.id" placeholder="如 default / acme" />
                </el-form-item>
                <el-form-item label="租户名称" prop="tenantName">
                    <el-input v-model="editForm.tenantName" />
                </el-form-item>
                <el-form-item label="状态">
                    <el-select v-model="editForm.status" style="width: 100%">
                        <el-option label="启用" value="using" />
                        <el-option label="禁用" value="disabled" />
                    </el-select>
                </el-form-item>
                <el-form-item label="到期日期">
                    <el-date-picker v-model="editForm.expireDate" type="date" value-format="YYYY-MM-DD"
                        style="width: 100%" placeholder="留空表示长期有效" />
                </el-form-item>
                <el-form-item label="备注">
                    <el-input v-model="editForm.remark" type="textarea" :rows="2" />
                </el-form-item>
            </el-form>
            <template #footer>
                <el-button @click="editVisible = false">取消</el-button>
                <el-button type="primary" :loading="saving" @click="submitTenant">保存</el-button>
            </template>
        </el-dialog>
    </div>
</template>

<script setup>
import { ref, onActivated, onMounted, watch } from 'vue'
import { Search, Plus } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import http from '@/js/httpProxy.js'
import { createRequired, createCodeRule } from '@/js/validators.js'

const tableData = ref([])

const queryParam = ref({
    tenantCode: '',
    tenantName: '',
    status: ''
})

const editVisible = ref(false)
const saving = ref(false)
const editFormRef = ref(null)
const formRules = {
    tenantCode: [createRequired('请输入租户编码'), createCodeRule()],
    tenantName: [createRequired('请输入租户名称')]
}
const editForm = ref({
    id: '',
    tenantCode: '',
    tenantName: '',
    status: 'using',
    expireDate: '',
    remark: ''
})

const pageSize = ref(30)
const currentPage = ref(1)
const total = ref(0)

// KeepAlive include 机制下，异步组件首次渲染（未加载完成）无法匹配组件名而不走缓存，onActivated 不触发；
// 用 onMounted 兜底保证任何渲染路径都执行加载。缓存路径下 onMounted 与 onActivated 连触发，用标志去重。
let mountedOnce = false
onMounted(() => {
    mountedOnce = true
    loadData()
})
onActivated(() => {
    if (!mountedOnce) {
        loadData()
    }
    mountedOnce = false
})

watch(currentPage, () => loadData())
watch(pageSize, () => loadData())

function loadData() {
    http.get('/tenant/queryPage', {
        params: {
            tenantCode: queryParam.value.tenantCode,
            tenantName: queryParam.value.tenantName,
            status: queryParam.value.status,
            current: currentPage.value,
            size: pageSize.value
        }
    }).then(({ data }) => {
        if (data.code === 200) {
            tableData.value = data.data.data || []
            total.value = data.data.total
        }
    })
}

function openEdit(row) {
    if (row) {
        editForm.value = {
            id: row.id,
            tenantCode: row.tenantCode,
            tenantName: row.tenantName,
            status: row.status,
            expireDate: row.expireDate || '',
            remark: row.remark || ''
        }
    } else {
        editForm.value = {
            id: '',
            tenantCode: '',
            tenantName: '',
            status: 'using',
            expireDate: '',
            remark: ''
        }
    }
    editVisible.value = true
}

async function submitTenant() {
    if (!editFormRef.value) return
    try {
        await editFormRef.value.validate()
    } catch {
        return
    }
    saving.value = true
    http.post('/tenant/save', editForm.value).then(({ data }) => {
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

function removeTenant(id) {
    http.post('/tenant/deleteById', null, { params: { id } }).then(({ data }) => {
        if (data.code === 200) {
            ElMessage.success('删除成功')
            loadData()
        } else {
            ElMessage.error(data.msg || '删除失败')
        }
    })
}
</script>
