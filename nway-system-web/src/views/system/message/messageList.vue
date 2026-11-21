<template>
    <div style="width: 98%;margin: 10px auto;">
        <div style="margin-bottom: 10px;">
            <el-button :icon="Promotion" type="primary" @click="openSendDialog">发送消息</el-button>
            <el-button :icon="Refresh" @click="loadData">刷新</el-button>
        </div>
        <el-table :data="tableData" stripe style="width: 100%">
            <el-table-column label="标题" width="260">
                <template #default="scope">
                    <el-button link @dblclick="showMsgDetails(scope.row)">{{ scope.row.title }}</el-button>
                </template>
            </el-table-column>
            <el-table-column label="发件人" width="180">
                <template #default="scope">
                    <span>{{ scope.row.senderName }} &nbsp;&nbsp;{{ scope.row.senderId }}</span>
                </template>
            </el-table-column>
            <el-table-column prop="content" label="内容" show-overflow-tooltip />
            <el-table-column prop="createTime" label="接收时间" width="180" />
            <el-table-column label="渠道" width="220">
                <template #default="scope">
                    <el-tag v-for="ch in scope.row.channels" :key="ch" size="small" style="margin-right: 4px;"
                        :type="channelStatusType(scope.row, ch)">
                        {{ channelLabel(ch) }}{{ channelStatusText(scope.row, ch) }}
                    </el-tag>
                </template>
            </el-table-column>
            <el-table-column prop="isRead" label="是否已读" width="100">
                <template #default="scope">
                    <span>{{ scope.row.isRead ? '已读' : '未读' }}</span>
                </template>
            </el-table-column>
            <el-table-column label="操作" width="200">
                <template #default="scope">
                    <el-button link @click="showMsgDetails(scope.row)">查看</el-button>
                    <el-button link @click="msgRead(scope.row.receiverId)">标记已读</el-button>
                    <el-button v-if="hasFailedChannel(scope.row)" link type="danger" @click="resendFailed(scope.row)">
                        重发失败渠道
                    </el-button>
                </template>
            </el-table-column>
        </el-table>

        <el-dialog v-model="msgDetailsVisible" title="消息详情" width="750">
            <div class="msg-detail">
                <div class="msg-detail-header">
                    <span class="msg-title">{{ currentMsg.title }}</span>
                    <el-tag size="small" :type="currentMsg.isRead ? 'info' : 'danger'">
                        {{ currentMsg.isRead ? '已读' : '未读' }}
                    </el-tag>
                </div>
                <div class="msg-detail-meta">
                    <span>{{ currentMsg.senderName }}&nbsp;&nbsp;{{ currentMsg.senderId }}</span>
                    <span>{{ currentMsg.createTime }}</span>
                </div>
                <div class="msg-detail-channels">
                    <el-tag v-for="ch in currentMsg.channels" :key="ch" size="small" style="margin-right: 4px;"
                        :type="channelStatusType(currentMsg, ch)">
                        {{ channelLabel(ch) }}{{ channelStatusText(currentMsg, ch) }}
                    </el-tag>
                </div>
                <div class="msg-detail-content">{{ currentMsg.content }}</div>
            </div>
            <template #footer>
                <div class="dialog-footer">
                    <el-button @click="msgDetailsVisible = false">关闭</el-button>
                </div>
            </template>
        </el-dialog>

        <el-dialog v-model="sendDialogVisible" title="发送消息" width="680" destroy-on-close>
            <el-form :model="sendForm" label-width="80px">
                <el-form-item label="标题">
                    <el-input v-model="sendForm.title" placeholder="请输入消息标题" />
                </el-form-item>
                <el-form-item label="内容">
                    <el-input v-model="sendForm.content" type="textarea" :rows="4" placeholder="请输入消息内容" />
                </el-form-item>
                <el-form-item label="发送渠道">
                    <el-select v-model="sendForm.channels" multiple placeholder="请选择发送渠道" style="width: 100%">
                        <el-option v-for="opt in channelOptions" :key="opt.value" :label="opt.label" :value="opt.value" />
                    </el-select>
                </el-form-item>
                <el-form-item label="目标类型">
                    <el-radio-group v-model="sendForm.targetType" @change="onTargetTypeChange">
                        <el-radio :value="1">用户</el-radio>
                        <el-radio :value="2">部门</el-radio>
                        <el-radio :value="3">角色</el-radio>
                    </el-radio-group>
                </el-form-item>
                <el-form-item label="选择目标">
                    <el-select v-if="sendForm.targetType !== 2" v-model="sendForm.targetIds" multiple filterable
                        placeholder="请选择目标" style="width: 100%"
                        :loading="targetLoading" remote :remote-method="searchTargets">
                        <el-option v-for="t in targetOptions" :key="t.id" :label="t.label" :value="t.id" />
                    </el-select>
                    <el-tree-select v-else v-model="sendForm.targetIds" :data="deptTree" :props="treeProps"
                        node-key="id" multiple check-strictly clearable filterable
                        placeholder="请选择部门（含子部门）" style="width: 100%" />
                </el-form-item>
            </el-form>
            <template #footer>
                <el-button @click="sendDialogVisible = false">取消</el-button>
                <el-button type="primary" :loading="sending" @click="submitSend">发送</el-button>
            </template>
        </el-dialog>
    </div>
</template>

<script setup lang="ts">
import { ref, onActivated, onDeactivated, onMounted, onUnmounted } from 'vue'
import { Promotion, Refresh } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import http from '@/js/httpProxy.js'
import auth from '@/js/auth.js'

interface MessageVO {
    id: string
    receiverId: string
    title: string
    senderId: string
    senderName: string
    content: string
    createTime: string
    isRead: boolean
    channels: string[]
    channelStatus: Record<string, number>
}

interface TargetOption {
    id: string
    label: string
}

const channelOptions = [
    { value: 'inner', label: '站内信' },
    { value: 'inner_pop', label: '站内弹窗' },
    { value: 'email', label: '邮件' },
    { value: 'wxwork', label: '企业微信' }
]

const tableData = ref<MessageVO[]>([])
const msgDetailsVisible = ref(false)
const currentMsg = ref<Partial<MessageVO>>({})
let pollingTimer: ReturnType<typeof setInterval> | null = null

const sendDialogVisible = ref(false)
const sending = ref(false)
const sendForm = ref({
    title: '',
    content: '',
    channels: ['inner'] as string[],
    targetType: 1 as 1 | 2 | 3,
    targetIds: [] as string[]
})
const targetOptions = ref<TargetOption[]>([])
const targetLoading = ref(false)
const deptTree = ref<any[]>([])
const treeProps = { label: 'deptName', children: 'children' }

// KeepAlive include 机制下，异步组件首次渲染（未加载完成）无法匹配组件名而不走缓存，onActivated 不触发；
// 用 onMounted/onUnmounted 兜底保证任何渲染路径都执行加载/清理。
// 缓存路径下挂载时 onMounted 与 onActivated 连触发，用 mountedOnce 标志去重。
let mountedOnce = false
onMounted(() => {
    mountedOnce = true
    startPolling()
})
onActivated(() => {
    if (!mountedOnce) {
        startPolling()
    }
    mountedOnce = false
})

let unmountedOnce = false
onUnmounted(() => {
    unmountedOnce = true
    stopPolling()
})
onDeactivated(() => {
    if (!unmountedOnce) {
        stopPolling()
    }
    unmountedOnce = false
})

function startPolling() {
    loadData()
    if (!pollingTimer) {
        pollingTimer = setInterval(loadData, 60 * 1000)
    }
}

function stopPolling() {
    if (pollingTimer) {
        clearInterval(pollingTimer)
        pollingTimer = null
    }
}

function channelLabel(code: string): string {
    return channelOptions.find(c => c.value === code)?.label || code
}

function channelStatusType(row: Partial<MessageVO>, ch: string): 'success' | 'danger' | 'info' {
    const st = row.channelStatus?.[ch]
    if (st === 1) return 'success'
    if (st === 0) return 'danger'
    return 'info'
}

function channelStatusText(row: Partial<MessageVO>, ch: string): string {
    const st = row.channelStatus?.[ch]
    if (st === 1) return '(成功)'
    if (st === 0) return '(失败)'
    return ''
}

function failedChannels(row: Partial<MessageVO>): string[] {
    return (row.channels || []).filter(ch => row.channelStatus?.[ch] === 0)
}

function hasFailedChannel(row: Partial<MessageVO>): boolean {
    return failedChannels(row).length > 0
}

async function resendFailed(row: MessageVO) {
    const failed = failedChannels(row)
    if (failed.length === 0) return
    let ok = 0
    for (const ch of failed) {
        try {
            const { data } = await http.post('/message/resend', {
                messageId: row.id,
                receiverId: row.receiverId,
                channel: ch
            })
            if (data.code === 200) {
                ok++
            }
        } catch (e) {
            // 错误提示已由 http 拦截器统一处理
        }
    }
    if (ok > 0) {
        ElMessage.success(`已重发 ${ok} 个失败渠道`)
        loadData()
    }
}

function showMsgDetails(msg: MessageVO) {
    currentMsg.value = msg
    msgDetailsVisible.value = true
}

function loadData() {
    http.get('/message/list', { params: {} }).then(({ data }) => {
        tableData.value = data.data || []
    })
}

function msgRead(receiveId: string) {
    const formData = new FormData()
    formData.append('receiveId', receiveId)
    http.post('/message/read', formData).then(({ data }) => {
        if (data.code === 200) {
            loadData()
        } else {
            ElMessage.error(data.msg || '操作失败')
        }
    })
}

function openSendDialog() {
    sendForm.value = {
        title: '',
        content: '',
        channels: ['inner'],
        targetType: 1,
        targetIds: []
    }
    targetOptions.value = []
    sendDialogVisible.value = true
    if (deptTree.value.length === 0) {
        loadDeptTree()
    }
    searchTargets('')
}

function onTargetTypeChange() {
    sendForm.value.targetIds = []
    targetOptions.value = []
    if (sendForm.value.targetType === 2 && deptTree.value.length === 0) {
        loadDeptTree()
    }
    if (sendForm.value.targetType !== 2) {
        searchTargets('')
    }
}

function loadDeptTree() {
    http.get('/dept/tree').then(({ data }) => {
        deptTree.value = data.data || []
    })
}

function searchTargets(keyword: string) {
    targetLoading.value = true
    const type = sendForm.value.targetType
    if (type === 1) {
        http.get('/user/queryPage', { params: { realName: keyword, current: 1, size: 50 } })
            .then(({ data }) => {
                targetOptions.value = (data.data?.data || []).map((u: any) => ({
                    id: u.employeeId,
                    label: `${u.realName} (${u.employeeId})`
                }))
            })
            .finally(() => { targetLoading.value = false })
    } else if (type === 3) {
        http.post('/role/queryPage', { roleName: keyword, current: 1, size: 50 })
            .then(({ data }) => {
                targetOptions.value = (data.data?.data || []).map((r: any) => ({
                    id: r.id,
                    label: `${r.roleName} (${r.roleCode})`
                }))
            })
            .finally(() => { targetLoading.value = false })
    }
}

function submitSend() {
    if (!sendForm.value.title) {
        ElMessage.warning('请输入标题')
        return
    }
    if (!sendForm.value.content) {
        ElMessage.warning('请输入内容')
        return
    }
    if (sendForm.value.channels.length === 0) {
        ElMessage.warning('请选择发送渠道')
        return
    }
    if (sendForm.value.targetIds.length === 0) {
        ElMessage.warning('请选择目标')
        return
    }

    const user = auth.getUser()
    sending.value = true
    const payload = {
        title: sendForm.value.title,
        content: sendForm.value.content,
        senderId: user?.employeeId || '',
        senderName: user?.realName || '',
        channels: sendForm.value.channels,
        targets: sendForm.value.targetIds.map(id => ({
            targetType: sendForm.value.targetType,
            targetId: id
        }))
    }
    http.post('/message/send', payload).then(({ data }) => {
        if (data.code === 200) {
            ElMessage.success('发送成功')
            sendDialogVisible.value = false
            loadData()
        } else {
            ElMessage.error(data.msg || '发送失败')
        }
    }).finally(() => {
        sending.value = false
    })
}
</script>

<style lang="css" scoped>
.dialog-footer {
    bottom: 15px;
    right: 20px;
    position: absolute;
}

.msg-detail-header {
    display: flex;
    align-items: center;
    justify-content: space-between;
    font-weight: bold;
    font-size: 16px;
    margin-bottom: 10px;
}

.msg-detail-meta {
    display: flex;
    justify-content: space-between;
    color: #909399;
    font-size: 13px;
    margin-bottom: 10px;
}

.msg-detail-channels {
    margin-bottom: 10px;
}

.msg-detail-content {
    max-height: 260px;
    overflow-y: auto;
    white-space: pre-wrap;
    word-break: break-all;
    line-height: 1.7;
    padding: 10px;
    background: #f5f7fa;
    border-radius: 4px;
}
</style>
