<template>
    <div style="width: 98%; margin: 10px auto;">
        <div>
            <el-form :inline="true">
                <el-form-item label="关键字">
                    <el-input v-model="keyword" placeholder="工号/姓名" @keyup.enter="loadData" style="width: 220px"
                        clearable @clear="loadData" />
                </el-form-item>
                <el-form-item>
                    <el-button :icon="Search" @click="loadData">查询</el-button>
                    <el-button :icon="Refresh" @click="loadData">刷新</el-button>
                </el-form-item>
                <el-form-item>
                    <span style="color: #909399; font-size: 12px;">
                        在线 {{ tableData.length }} 人 · 30 秒自动刷新
                    </span>
                </el-form-item>
            </el-form>
        </div>
        <el-table :data="tableData" stripe style="width: 100%" v-loading="loading">
            <el-table-column prop="employeeId" label="工号" width="140" />
            <el-table-column prop="realName" label="姓名" width="140" />
            <el-table-column prop="tenantName" label="租户" width="160">
                <template #default="scope">
                    <el-tag v-if="scope.row.tenantName" size="small" type="info">{{ scope.row.tenantName }}</el-tag>
                </template>
            </el-table-column>
            <el-table-column prop="ip" label="IP" width="160" />
            <el-table-column prop="loginTime" label="登录时间" width="180" />
            <el-table-column prop="expireTime" label="过期时间" width="180" />
            <el-table-column label="操作" width="120" fixed="right">
                <template #default="scope">
                    <el-popconfirm
                        :title="`确定强制下线 ${scope.row.realName || scope.row.employeeId} ?`"
                        @confirm="handleForceLogout(scope.row.employeeId)">
                        <template #reference>
                            <el-button link type="danger" size="small">强制下线</el-button>
                        </template>
                    </el-popconfirm>
                </template>
            </el-table-column>
        </el-table>
    </div>
</template>

<script setup lang="ts">
import { ref, onMounted, onUnmounted } from 'vue'
import { Search, Refresh } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import * as onlineUserApi from '@/api/onlineUser'
import type { OnlineUser } from '@/api/onlineUser'

const REFRESH_INTERVAL = 30_000

const keyword = ref('')
const tableData = ref<OnlineUser[]>([])
const loading = ref(false)
let refreshTimer: number | null = null

async function loadData() {
    loading.value = true
    try {
        const res = await onlineUserApi.list(keyword.value || undefined)
        if (res.code === 200) {
            tableData.value = res.data || []
        } else {
            ElMessage.error(res.msg || '查询失败')
        }
    } catch (e: any) {
        ElMessage.error(e?.message || '查询失败')
    } finally {
        loading.value = false
    }
}

async function handleForceLogout(employeeId: string) {
    try {
        const res = await onlineUserApi.forceLogout(employeeId)
        if (res.code === 200) {
            ElMessage.success('已强制下线')
            loadData()
        } else {
            ElMessage.error(res.msg || '操作失败')
        }
    } catch (e: any) {
        ElMessage.error(e?.message || '操作失败')
    }
}

onMounted(() => {
    loadData()
    refreshTimer = window.setInterval(loadData, REFRESH_INTERVAL)
})

onUnmounted(() => {
    if (refreshTimer !== null) {
        clearInterval(refreshTimer)
        refreshTimer = null
    }
})
</script>
