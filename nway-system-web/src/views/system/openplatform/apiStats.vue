<template>
    <div style="width: 98%;margin: 10px auto;">
        <el-tabs v-model="activeTab">
            <el-tab-pane label="按应用统计" name="byApp">
                <el-table :data="appStats" stripe border style="width: 100%">
                    <el-table-column prop="appCode" label="应用编码" width="180" />
                    <el-table-column prop="tenantName" label="租户" width="140">
                        <template #default="scope">
                            <el-tag v-if="scope.row.tenantName" size="small" type="info">{{ scope.row.tenantName }}</el-tag>
                        </template>
                    </el-table-column>
                    <el-table-column prop="totalCalls" label="总调用次数" width="120" />
                    <el-table-column prop="successCount" label="成功次数" width="120" />
                    <el-table-column label="成功率" width="120">
                        <template #default="scope">
                            <el-progress :percentage="Math.round(scope.row.successRate * 100)" :stroke-width="12"
                                :status="scope.row.successRate > 0.9 ? 'success' : 'warning'" />
                        </template>
                    </el-table-column>
                    <el-table-column prop="avgDurationMs" label="平均耗时(ms)" width="140" />
                </el-table>
            </el-tab-pane>
            <el-tab-pane label="按接口统计" name="byApi">
                <el-table :data="apiStats" stripe border style="width: 100%">
                    <el-table-column prop="api" label="接口" min-width="240" />
                    <el-table-column prop="totalCalls" label="总调用次数" width="120" />
                    <el-table-column prop="successCount" label="成功次数" width="120" />
                    <el-table-column label="成功率" width="120">
                        <template #default="scope">
                            <el-progress :percentage="Math.round(scope.row.successRate * 100)" :stroke-width="12"
                                :status="scope.row.successRate > 0.9 ? 'success' : 'warning'" />
                        </template>
                    </el-table-column>
                    <el-table-column prop="avgDurationMs" label="平均耗时(ms)" width="140" />
                </el-table>
            </el-tab-pane>
        </el-tabs>
        <div style="margin-top: 16px;">
            <el-button type="primary" @click="loadData">刷新统计</el-button>
        </div>
    </div>
</template>

<script setup>
import { ref, onActivated, onMounted } from 'vue'
import http from '@/js/httpProxy.js'

const activeTab = ref('byApp')
const appStats = ref([])
const apiStats = ref([])

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

function loadData() {
    http.get('/openApiLog/statsByApp').then(({ data }) => {
        if (data.code === 200) {
            appStats.value = data.data || []
        }
    })
    http.get('/openApiLog/statsByApi').then(({ data }) => {
        if (data.code === 200) {
            apiStats.value = data.data || []
        }
    })
}
</script>
