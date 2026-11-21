<template>
    <div style="width: 98%;margin: 10px auto;">
        <div>
            <el-form :inline="true">
                <el-form-item label="应用编码">
                    <el-select v-model="queryParam.appCode" filterable allow-create clearable
                        placeholder="请选择或输入应用编码" style="width: 200px" @keyup.enter="loadData()">
                        <el-option v-for="app in appList" :key="app.id"
                            :label="`${app.appCode}（${app.appName}）`" :value="app.appCode" />
                    </el-select>
                </el-form-item>
                <el-form-item>
                    <el-button :icon="Search" @click="loadData()">查询</el-button>
                    <el-button @click="loadAll">查询全部</el-button>
                </el-form-item>
            </el-form>
        </div>
        <el-table :data="tableData" stripe border style="width: 100%" max-height="600">
            <el-table-column prop="appCode" label="应用编码" width="140" />
            <el-table-column prop="tenantName" label="租户" width="120">
                <template #default="scope">
                    <el-tag v-if="scope.row.tenantName" size="small" type="info">{{ scope.row.tenantName }}</el-tag>
                </template>
            </el-table-column>
            <el-table-column prop="apiPath" label="接口路径" min-width="200" />
            <el-table-column prop="httpMethod" label="方法" width="80" />
            <el-table-column label="状态码" width="80">
                <template #default="scope">
                    <el-tag :type="scope.row.responseStatus === 200 ? 'success' : 'danger'" size="small">
                        {{ scope.row.responseStatus }}
                    </el-tag>
                </template>
            </el-table-column>
            <el-table-column prop="durationMs" label="耗时(ms)" width="100" />
            <el-table-column prop="ip" label="IP" width="140" />
            <el-table-column prop="employeeId" label="代理用户" width="120" />
            <el-table-column prop="errorMsg" label="错误信息" min-width="160" show-overflow-tooltip />
            <el-table-column prop="createTime" label="调用时间" width="180" />
        </el-table>
    </div>
</template>

<script setup>
import { ref, onActivated, onMounted } from 'vue'
import { Search } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import http from '@/js/httpProxy.js'

const tableData = ref([])
const appList = ref([])
const queryParam = ref({ appCode: '' })

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
    // 进入页面静默加载，应用编码为空时不提示
    loadData(false)
    loadAppList()
}

function loadAppList() {
    http.get('/openApp/queryAll').then(({ data }) => {
        if (data.code === 200) {
            appList.value = data.data || []
        }
    })
}

function loadData(showWarning = true) {
    if (!queryParam.value.appCode) {
        if (showWarning) {
            ElMessage.warning('请输入应用编码')
        }
        tableData.value = []
        return
    }
    http.get('/openApiLog/queryByApp', { params: { appCode: queryParam.value.appCode } }).then(({ data }) => {
        if (data.code === 200) {
            tableData.value = data.data || []
        }
    })
}

function loadAll() {
    http.get('/openApiLog/queryAll').then(({ data }) => {
        if (data.code === 200) {
            tableData.value = data.data || []
        }
    })
}
</script>
