<template>
    <div style="width: 98%;margin: 10px auto;">
        <div>
            <el-form :inline="true">
                <el-form-item label="标题">
                    <el-input v-model="queryParam.title" @keyup.enter="loadData" style="width: 200px" clearable />
                </el-form-item>
                <el-form-item label="发送人">
                    <el-input v-model="queryParam.senderName" @keyup.enter="loadData" style="width: 200px" clearable />
                </el-form-item>
                <el-form-item>
                    <el-button :icon="Search" @click="loadData">查询</el-button>
                </el-form-item>
            </el-form>
        </div>
        <el-table :data="tableData" stripe style="width: 100%">
            <el-table-column prop="title" label="标题" width="240" />
            <el-table-column label="发件人" width="180">
                <template #default="scope">
                    <span>{{ scope.row.senderName }} &nbsp;&nbsp;{{ scope.row.senderId }}</span>
                </template>
            </el-table-column>
            <el-table-column prop="sendTime" label="发送时间" width="180" />
            <el-table-column label="渠道" width="200">
                <template #default="scope">
                    <el-tag v-for="ch in scope.row.channels" :key="ch" size="small" style="margin-right: 4px;">
                        {{ channelLabel(ch) }}
                    </el-tag>
                </template>
            </el-table-column>
            <el-table-column prop="receiverCount" label="接收人数" width="100" />
            <el-table-column prop="readCount" label="已读数" width="100" />
            <el-table-column prop="tenantName" label="租户" width="120">
                <template #default="scope">
                    <el-tag v-if="scope.row.tenantName" size="small" type="info">{{ scope.row.tenantName }}</el-tag>
                </template>
            </el-table-column>
            <el-table-column label="操作" width="120">
                <template #default="scope">
                    <el-button link @click="showSendLogs(scope.row.messageId)">发送日志</el-button>
                </template>
            </el-table-column>
        </el-table>
        <div style="display: flex; justify-content: flex-end; margin-top: 10px;">
            <el-pagination :page-sizes="[15, 30, 50, 100]" layout="total, sizes, prev, pager, next"
                v-model:current-page="currentPage" v-model:page-size="pageSize" :default-page-size="15"
                :total="total" @size-change="loadData" @current-change="loadData" />
        </div>

        <el-dialog v-model="logDialogVisible" title="发送日志" width="900" destroy-on-close>
            <el-table :data="logData" stripe style="width: 100%" max-height="500">
                <el-table-column prop="userId" label="接收人" width="140" />
                <el-table-column label="渠道" width="120">
                    <template #default="scope">
                        {{ channelLabel(scope.row.channel) }}
                    </template>
                </el-table-column>
                <el-table-column label="状态" width="100">
                    <template #default="scope">
                        <el-tag :type="statusTagType(scope.row.status)" size="small">
                            {{ statusLabel(scope.row.status) }}
                        </el-tag>
                    </template>
                </el-table-column>
                <el-table-column prop="errorMsg" label="错误信息" show-overflow-tooltip />
                <el-table-column label="重试" width="100">
                    <template #default="scope">
                        {{ scope.row.retryCount }} / {{ scope.row.maxRetry }}
                    </template>
                </el-table-column>
                <el-table-column prop="sendTime" label="发送时间" width="180" />
            </el-table>
            <template #footer>
                <el-button @click="logDialogVisible = false">关闭</el-button>
            </template>
        </el-dialog>
    </div>
</template>

<script setup lang="ts">
import { ref, onActivated, onMounted } from 'vue'
import { Search } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import http from '@/js/httpProxy.js'

interface MessageManageVO {
    messageId: string
    title: string
    senderId: string
    senderName: string
    sendTime: string
    receiverCount: number
    readCount: number
    channels: string[]
}

interface MessageSendLog {
    id: string
    messageId: string
    receiverId: string
    userId: string
    channel: string
    status: string
    errorMsg: string
    retryCount: number
    maxRetry: number
    sendTime: string
    nextRetryTime: string
}

const channelOptions = [
    { value: 'inner', label: '站内信' },
    { value: 'inner_pop', label: '站内弹窗' },
    { value: 'email', label: '邮件' },
    { value: 'wxwork', label: '企业微信' }
]

const tableData = ref<MessageManageVO[]>([])
const queryParam = ref({ title: '', senderName: '' })
const currentPage = ref(1)
const pageSize = ref(15)
const total = ref(0)

const logDialogVisible = ref(false)
const logData = ref<MessageSendLog[]>([])

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

function channelLabel(code: string): string {
    return channelOptions.find(c => c.value === code)?.label || code
}

function statusLabel(code: string): string {
    if (code === 'success') return '成功'
    if (code === 'failed') return '失败'
    if (code === 'pending') return '待发送'
    return code
}

function statusTagType(code: string): 'success' | 'danger' | 'warning' {
    if (code === 'success') return 'success'
    if (code === 'failed') return 'danger'
    return 'warning'
}

function loadData() {
    http.get('/message/manage/list', {
        params: {
            title: queryParam.value.title,
            senderName: queryParam.value.senderName,
            current: currentPage.value,
            size: pageSize.value
        }
    }).then(({ data }) => {
        if (data.code === 200) {
            tableData.value = data.data?.data || []
            total.value = data.data?.total || 0
        } else {
            ElMessage.error(data.msg || '查询失败')
        }
    })
}

function showSendLogs(messageId: string) {
    http.get('/message/sendLog', { params: { messageId } }).then(({ data }) => {
        if (data.code === 200) {
            logData.value = data.data || []
            logDialogVisible.value = true
        } else {
            ElMessage.error(data.msg || '查询失败')
        }
    })
}
</script>

<style lang="css" scoped>
.dialog-footer {
    bottom: 15px;
    right: 20px;
    position: absolute;
}
</style>
