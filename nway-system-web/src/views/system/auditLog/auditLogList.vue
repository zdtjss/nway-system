<template>
    <div style="width: 98%;margin: 10px auto;">
        <div>
            <el-form :inline="true">
                <el-form-item label="操作人">
                    <el-input v-model="queryParam.operatorId" placeholder="工号" @keyup.enter="loadData"
                        style="width: 160px" clearable />
                </el-form-item>
                <el-form-item label="操作类型">
                    <el-select v-model="queryParam.operationType" clearable style="width: 140px">
                        <el-option label="新增" value="CREATE" />
                        <el-option label="修改" value="UPDATE" />
                        <el-option label="删除" value="DELETE" />
                        <el-option label="登录" value="LOGIN" />
                        <el-option label="登出" value="LOGOUT" />
                        <el-option label="其它" value="OTHER" />
                    </el-select>
                </el-form-item>
                <el-form-item label="API路径">
                    <el-input v-model="queryParam.apiPath" placeholder="路径关键字" @keyup.enter="loadData"
                        style="width: 220px" clearable />
                </el-form-item>
                <el-form-item label="时间范围">
                    <el-date-picker v-model="timeRange" type="datetimerange"
                        value-format="YYYY-MM-DD HH:mm:ss" range-separator="至"
                        start-placeholder="开始时间" end-placeholder="结束时间"
                        style="width: 360px" />
                </el-form-item>
                <el-form-item>
                    <el-button :icon="Search" @click="loadData()">查询</el-button>
                    <el-button @click="resetQuery">重置</el-button>
                </el-form-item>
            </el-form>
        </div>
        <el-table :data="tableData" stripe border style="width: 100%" max-height="600" row-key="id">
            <el-table-column type="expand">
                <template #default="scope">
                    <div style="padding: 8px 16px;">
                        <div style="margin-bottom: 6px; color: #909399;">
                            请求参数：
                        </div>
                        <pre style="margin: 0; white-space: pre-wrap; word-break: break-all; background: #f5f7fa; padding: 10px; border-radius: 4px;">{{ formatParams(scope.row.requestParams) }}</pre>
                        <div style="margin: 8px 0 6px; color: #909399;">
                            User-Agent：
                        </div>
                        <div style="word-break: break-all; color: #606266;">{{ scope.row.userAgent || '-' }}</div>
                    </div>
                </template>
            </el-table-column>
            <el-table-column label="操作人" width="160">
                <template #default="scope">
                    <span>{{ scope.row.operatorName || scope.row.operatorId || '-' }}</span>
                </template>
            </el-table-column>
            <el-table-column prop="tenantName" label="租户" width="120">
                <template #default="scope">
                    <el-tag v-if="scope.row.tenantName" size="small" type="info">{{ scope.row.tenantName }}</el-tag>
                </template>
            </el-table-column>
            <el-table-column label="操作类型" width="100">
                <template #default="scope">
                    <el-tag :type="tagType(scope.row.operationType)" size="small">
                        {{ scope.row.operationType || '-' }}
                    </el-tag>
                </template>
            </el-table-column>
            <el-table-column prop="apiPath" label="API路径" min-width="200" show-overflow-tooltip />
            <el-table-column prop="httpMethod" label="HTTP方法" width="90" />
            <el-table-column label="响应状态" width="90">
                <template #default="scope">
                    <el-tag :type="scope.row.responseStatus === 200 ? 'success' : 'danger'" size="small">
                        {{ scope.row.responseStatus }}
                    </el-tag>
                </template>
            </el-table-column>
            <el-table-column label="耗时(ms)" width="100">
                <template #default="scope">
                    <span>{{ scope.row.durationMs }}</span>
                </template>
            </el-table-column>
            <el-table-column prop="ip" label="IP" width="140" />
            <el-table-column prop="createTime" label="操作时间" width="180" />
        </el-table>
        <div style="display: flex; justify-content: flex-end; margin-top: 10px;">
            <el-pagination :page-sizes="[15, 30, 50, 100]" layout="total, sizes, prev, pager, next"
                v-model:current-page="currentPage" v-model:page-size="pageSize" :default-page-size="15"
                :total="total" />
        </div>
    </div>
</template>

<script setup lang="ts">
import { ref, onActivated, onMounted, watch } from 'vue'
import { Search } from '@element-plus/icons-vue'
import { queryPage, type AuditLog } from '@/api/auditLog'

const tableData = ref<AuditLog[]>([])

const queryParam = ref({
    operatorId: '',
    operationType: '',
    apiPath: ''
})

const timeRange = ref<[string, string] | null>(null)

const pageSize = ref(15)
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
    queryPage({
        operatorId: queryParam.value.operatorId || undefined,
        operationType: queryParam.value.operationType || undefined,
        apiPath: queryParam.value.apiPath || undefined,
        startTime: timeRange.value ? timeRange.value[0] : undefined,
        endTime: timeRange.value ? timeRange.value[1] : undefined,
        page: currentPage.value,
        pageSize: pageSize.value
    }).then((res) => {
        if (res.code === 200) {
            tableData.value = res.data?.data || []
            total.value = res.data?.total || 0
        }
    })
}

function resetQuery() {
    queryParam.value = { operatorId: '', operationType: '', apiPath: '' }
    timeRange.value = null
    currentPage.value = 1
    loadData()
}

function tagType(operationType: string) {
    switch (operationType) {
        case 'CREATE':
            return 'success'
        case 'UPDATE':
            return 'warning'
        case 'DELETE':
            return 'danger'
        case 'LOGIN':
            return 'info'
        default:
            return ''
    }
}

function formatParams(params: string) {
    if (!params) {
        return '-'
    }
    try {
        return JSON.stringify(JSON.parse(params), null, 2)
    } catch (e) {
        return params
    }
}
</script>
