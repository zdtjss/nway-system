<template>
    <div style="width: 98%;margin: 10px auto;">
        <div>
            <el-form :inline="true">
                <el-form-item label="所属应用">
                    <el-select v-model="queryParam.appCode" clearable filterable style="width: 200px"
                        @change="loadData">
                        <el-option v-for="item in appList" :key="item.appCode" :label="item.appName"
                            :value="item.appCode" />
                    </el-select>
                </el-form-item>
                <el-form-item label="角色名称">
                    <el-input v-model="queryParam.roleName" @keyup.enter="loadData" style="width: 200px" />
                </el-form-item>
                <el-form-item>
                    <el-button :icon="Search" @click="loadData()">查询</el-button>
                </el-form-item>
            </el-form>
        </div>
        <div>
            <div style="position: relative;float: right; display: flex; align-items: center; gap: 8px;">
                <el-button :icon="Plus" size="small" link @click="edit()" />
            </div>
            <el-table :data="tableData" stripe style="width: 100%">
                <el-table-column prop="roleName" label="角色名称" min-width="200" />
                <el-table-column prop="roleCode" label="角色编码" min-width="180" />
                <el-table-column prop="appName" label="所属应用" min-width="150" />
                <el-table-column prop="tenantName" label="租户" min-width="120">
                    <template #default="scope">
                        <el-tag v-if="scope.row.tenantName" size="small" type="info">{{ scope.row.tenantName }}</el-tag>
                    </template>
                </el-table-column>
                <el-table-column prop="modifiedTime" label="修改时间" min-width="180" />
                <el-table-column label="操作" width="360">
                    <template #default="scope">
                        <el-button link @click="showUser(scope.row.id)">用户</el-button>
                        <el-button link type="primary" @click="showPolicy(scope.row)">策略配置</el-button>
                        <el-button link @click="showCopy(scope.row)">复制到租户</el-button>
                        <el-button link @click="edit(scope.row.id)">编辑</el-button>
                        <el-popconfirm title="确定删除?" @confirm="remove(scope.row.id)">
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
        </div>
    </div>
    <el-dialog v-model="dialogEditVisible" title="新增/修改角色" width="650px" destroy-on-close>
        <roleEditVue v-if="dialogEditVisible" :role-id="roleId" @close="onEditClose" />
    </el-dialog>
    <el-dialog v-model="dialogUserVisible" title="用户管理" :draggable="true" width="800" destroy-on-close>
        <roleUser v-if="dialogUserVisible" :role-id="currentRoleId" @close="dialogUserVisible = false" />
    </el-dialog>
    <el-dialog v-model="dialogPolicyVisible" title="策略配置" :draggable="true" width="950" destroy-on-close>
        <rolePolicy :role-id="roleId" :tenant-code="roleTenantCode" @close="dialogPolicyVisible = false" />
    </el-dialog>
    <el-dialog v-model="dialogCopyVisible" title="复制角色到租户" width="520px" destroy-on-close>
        <el-form label-width="90px">
            <el-form-item label="源角色">
                <span>{{ copySource.roleName }}<el-tag v-if="copySource.appName" size="small" type="info"
                        style="margin-left: 8px">{{ copySource.appName }}</el-tag></span>
            </el-form-item>
            <el-form-item label="目标租户">
                <el-select v-model="copyTargetTenant" filterable placeholder="请选择目标租户" style="width: 100%">
                    <el-option v-for="t in tenantList" :key="t.tenantCode"
                        :label="`${t.tenantName}(${t.tenantCode})`" :value="t.tenantCode" />
                </el-select>
            </el-form-item>
        </el-form>
        <template #footer>
            <el-button @click="dialogCopyVisible = false">取消</el-button>
            <el-button type="primary" :disabled="!copyTargetTenant" :loading="copying" @click="doCopy">确定</el-button>
        </template>
    </el-dialog>
</template>

<script setup lang="ts">
import { Search, Plus } from '@element-plus/icons-vue'
import { ref, onMounted, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { queryPage, deleteById, copyToTenant, queryTenantOptions } from '@/api/role'
import { queryEnabled, type App } from '@/api/app'
import type { TenantDto } from '@/api/tenant'
import roleEditVue from './roleEdit.vue'
import roleUser from './roleUser.vue'
import rolePolicy from './rolePolicy.vue'

const tableData = ref<any[]>([])
const appList = ref<App[]>([])

const queryParam = ref({ roleName: '', appCode: '' })
const roleId = ref('')
/** 策略配置：目标角色所属租户（弹窗据此只展示该租户 + public 的应用与菜单） */
const roleTenantCode = ref('')
const currentRoleId = ref('')

const dialogEditVisible = ref(false)
const dialogUserVisible = ref(false)
const dialogPolicyVisible = ref(false)

/** 复制角色到租户 */
const dialogCopyVisible = ref(false)
const copying = ref(false)
const copySource = ref<any>({})
const copyTargetTenant = ref('')
const tenantList = ref<TenantDto[]>([])

const pageSize = ref(30)
const currentPage = ref(1)
const total = ref(0)

onMounted(() => {
    // 列表与下拉选项解耦：选项接口失败（如权限收紧）不得阻塞首屏列表加载
    loadData()
    loadOptions()
})

async function loadOptions() {
    try {
        const [appRes, tenantRes] = await Promise.all([queryEnabled(), queryTenantOptions()])
        appList.value = appRes.data || []
        tenantList.value = tenantRes.data || []
    } catch {
        // 失败提示由请求拦截器统一给出，此处仅保证主列表不受影响
    }
}

watch([currentPage, pageSize], () => loadData())

async function loadData() {
    const res = await queryPage({
        roleName: queryParam.value.roleName,
        appCode: queryParam.value.appCode,
        current: currentPage.value,
        size: pageSize.value
    })
    tableData.value = res.data?.data || []
    total.value = res.data?.total || 0
}

function edit(id?: string) {
    roleId.value = id || ''
    dialogEditVisible.value = true
}

function onEditClose() {
    dialogEditVisible.value = false
    loadData()
}

function showUser(id: string) {
    currentRoleId.value = id
    dialogUserVisible.value = true
}

function showPolicy(row: any) {
    roleId.value = row.id
    roleTenantCode.value = row.tenantCode || ''
    dialogPolicyVisible.value = true
}

function showCopy(row: any) {
    copySource.value = row
    copyTargetTenant.value = ''
    dialogCopyVisible.value = true
}

async function doCopy() {
    copying.value = true
    try {
        await copyToTenant(copySource.value.id, copyTargetTenant.value)
        ElMessage.success('复制成功')
        dialogCopyVisible.value = false
        loadData()
    } finally {
        copying.value = false
    }
}

async function remove(id: string) {
    await deleteById(id)
    ElMessage.success('删除成功')
    loadData()
}
</script>
