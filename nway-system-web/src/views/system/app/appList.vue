<template>
    <div style="width: 98%;margin: 10px auto;">
        <div>
            <div style="position: relative;float: right;">
                <el-button :icon="Plus" size="small" link @click="edit()" />
            </div>
            <el-table :data="tableData" stripe style="width: 100%">
                <el-table-column prop="appName" label="应用名称" width="200" />
                <el-table-column prop="appCode" label="应用标识" width="180" />
                <el-table-column prop="host" label="前端地址" />
                <el-table-column prop="apiBaseUrl" label="后端API地址" />
                <el-table-column prop="status" label="状态" width="100">
                    <template #default="scope">
                        <el-tag :type="scope.row.status === 'using' ? 'success' : 'info'" size="small">
                            {{ scope.row.status === 'using' ? '启用' : '禁用' }}
                        </el-tag>
                    </template>
                </el-table-column>
                <el-table-column prop="tenantName" label="租户" width="120">
                    <template #default="scope">
                        <el-tag v-if="scope.row.tenantName" size="small" type="info">{{ scope.row.tenantName }}</el-tag>
                    </template>
                </el-table-column>
                <el-table-column label="操作" width="150">
                    <template #default="scope">
                        <el-button :icon="Edit" link @click="edit(scope.row.id)" />
                        <el-popconfirm title="确定删除?" @confirm="handleDelete(scope.row.id)">
                            <template #reference>
                                <el-button type="danger" link :icon="Delete" />
                            </template>
                        </el-popconfirm>
                    </template>
                </el-table-column>
            </el-table>
        </div>
    </div>
    <el-dialog v-model="dialogVisible" title="新增/修改应用" width="650px" destroy-on-close>
        <appEdit :app-id="editId" @close="onDialogClose" />
    </el-dialog>
</template>

<script setup lang="ts">
import { Delete, Edit, Plus } from '@element-plus/icons-vue'
import { ref, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { queryAll, deleteById } from '@/api/app'
import appEdit from './appEdit.vue'

const tableData = ref<any[]>([])
const editId = ref('')
const dialogVisible = ref(false)

onMounted(() => loadData())

async function loadData() {
    const res = await queryAll()
    tableData.value = res.data || []
}

function edit(id?: string) {
    editId.value = id || ''
    dialogVisible.value = true
}

function onDialogClose() {
    dialogVisible.value = false
    loadData()
}

async function handleDelete(id: string) {
    await deleteById(id)
    ElMessage.success('删除成功')
    loadData()
}
</script>
