<template>
    <div style="width: 98%; margin: 10px auto;">
        <div>
            <el-form :inline="true">
                <el-form-item label="文件名">
                    <el-input v-model="queryParam.fileName" placeholder="文件名关键字" @keyup.enter="loadData"
                        style="width: 200px" clearable />
                </el-form-item>
                <el-form-item label="业务类型">
                    <el-input v-model="queryParam.bizType" placeholder="如 factoryVisitApply" @keyup.enter="loadData"
                        style="width: 180px" clearable />
                </el-form-item>
                <el-form-item label="上传人">
                    <el-input v-model="queryParam.creatorId" placeholder="工号" @keyup.enter="loadData"
                        style="width: 140px" clearable />
                </el-form-item>
                <el-form-item label="上传时间">
                    <el-date-picker v-model="timeRange" type="datetimerange"
                        value-format="YYYY-MM-DD HH:mm:ss" range-separator="至"
                        start-placeholder="开始时间" end-placeholder="结束时间"
                        style="width: 360px" />
                </el-form-item>
                <el-form-item>
                    <el-button :icon="Search" @click="loadData">查询</el-button>
                    <el-button @click="resetQuery">重置</el-button>
                </el-form-item>
            </el-form>
        </div>

        <div style="position: relative; float: right; margin-bottom: 8px;">
            <el-button type="primary" :icon="Upload" size="small" @click="openUpload">上传文件</el-button>
        </div>

        <el-table v-loading="loading" :data="tableData" stripe border style="width: 100%" max-height="600">
            <el-table-column label="文件名" min-width="240">
                <template #default="scope">
                    <span class="file-type-tag">{{ fileTypeLabel(scope.row) }}</span>
                    <span :title="scope.row.fileName">{{ scope.row.fileName }}</span>
                </template>
            </el-table-column>
            <el-table-column label="类型/大小" width="150">
                <template #default="scope">
                    <span>{{ (scope.row.fileExt || '-').toUpperCase() }}</span>
                    <span style="color: #909399; margin-left: 8px;">{{ formatFileSize(scope.row.fileSize) }}</span>
                </template>
            </el-table-column>
            <el-table-column label="业务归属" width="220">
                <template #default="scope">
                    <span v-if="scope.row.bizType">{{ scope.row.bizType }}</span>
                    <span v-if="scope.row.bizId" style="color: #909399;"> / {{ scope.row.bizId }}</span>
                    <span v-if="!scope.row.bizType && !scope.row.bizId" style="color: #909399;">-</span>
                </template>
            </el-table-column>
            <el-table-column label="上传人" width="140">
                <template #default="scope">
                    <span>{{ scope.row.creatorName || scope.row.creatorId || '-' }}</span>
                </template>
            </el-table-column>
            <el-table-column prop="createTime" label="上传时间" width="180" />
            <el-table-column label="操作" width="220">
                <template #default="scope">
                    <el-button v-if="kindOf(scope.row) !== 'other'" link type="primary"
                        @click="openPreview(scope.row)">
                        {{ kindOf(scope.row) === 'video' ? '播放' : '预览' }}
                    </el-button>
                    <el-button link @click="download(scope.row)">下载</el-button>
                    <el-popconfirm title="确定删除该文件？删除后不可恢复" @confirm="handleDelete(scope.row)">
                        <template #reference>
                            <el-button type="danger" link>删除</el-button>
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
    </div>

    <el-dialog v-model="uploadVisible" title="上传文件" width="760px" destroy-on-close :close-on-click-modal="false"
        @closed="onUploadClosed">
        <el-form :inline="true">
            <el-form-item label="业务类型">
                <el-input v-model="uploadForm.bizType" placeholder="可选，如 factoryVisitApply" style="width: 200px" />
            </el-form-item>
            <el-form-item label="业务单据">
                <el-input v-model="uploadForm.bizId" placeholder="可选，如 FV20261105001" style="width: 200px" />
            </el-form-item>
            <el-form-item label="备注">
                <el-input v-model="uploadForm.remark" placeholder="可选" style="width: 200px" />
            </el-form-item>
        </el-form>
        <FileUpload v-model="uploadFiles" :client="fileClient" :biz-type="uploadForm.bizType"
            :biz-id="uploadForm.bizId" :remark="uploadForm.remark" :limit="10" :max-size-mb="200"
            :accept="acceptExtensions" @uploaded="onUploaded" @error="onUploadError" />
        <template #footer>
            <el-button @click="uploadVisible = false">关闭</el-button>
        </template>
    </el-dialog>

    <FilePreview v-model="previewVisible" :file="previewFile" :files="previewFiles" :client="fileClient"
        @change="onPreviewChange" />
</template>

<script setup lang="ts">
import { onActivated, onMounted, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { Search, Upload } from '@element-plus/icons-vue'
import type { FileMeta } from '@nway/file'
import { FilePreview, FileUpload, fileTypeLabel, formatFileSize, resolvePreviewKind } from '@nway/file/vue'
import { fileClient } from '@/api/fileClient'

const acceptExtensions = '.jpg,.jpeg,.png,.gif,.webp,.bmp,.mp4,.webm,.ogg,.pdf,.doc,.docx,.xls,.xlsx,.ppt,.pptx,.txt,.csv,.zip,.rar,.7z'

const tableData = ref<FileMeta[]>([])
const loading = ref(false)

const queryParam = ref({
    fileName: '',
    bizType: '',
    creatorId: ''
})
const timeRange = ref<[string, string] | null>(null)

const pageSize = ref(15)
const currentPage = ref(1)
const total = ref(0)

const uploadVisible = ref(false)
const uploadFiles = ref<FileMeta[]>([])
const uploadForm = ref({ bizType: '', bizId: '', remark: '' })

const previewVisible = ref(false)
const previewFile = ref<FileMeta | null>(null)
const previewFiles = ref<FileMeta[]>([])

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

async function loadData() {
    loading.value = true
    try {
        const result = await fileClient.queryPage({
            fileName: queryParam.value.fileName || undefined,
            bizType: queryParam.value.bizType || undefined,
            creatorId: queryParam.value.creatorId || undefined,
            startTime: timeRange.value ? timeRange.value[0] : undefined,
            endTime: timeRange.value ? timeRange.value[1] : undefined,
            current: currentPage.value,
            size: pageSize.value
        })
        tableData.value = result?.data || []
        total.value = result?.total || 0
    } catch (e) {
        ElMessage.error((e as { message?: string })?.message || '查询失败')
    } finally {
        loading.value = false
    }
}

function resetQuery() {
    queryParam.value = { fileName: '', bizType: '', creatorId: '' }
    timeRange.value = null
    currentPage.value = 1
    loadData()
}

function kindOf(file: FileMeta) {
    return resolvePreviewKind(file)
}

function openUpload() {
    uploadForm.value = { bizType: '', bizId: '', remark: '' }
    uploadFiles.value = []
    uploadVisible.value = true
}

function onUploadClosed() {
    uploadFiles.value = []
}

function onUploaded() {
    loadData()
}

function onUploadError(message: string) {
    ElMessage.error(message)
}

async function download(file: FileMeta) {
    try {
        await fileClient.download(file.id, file.fileName)
    } catch (e) {
        ElMessage.error((e as { message?: string })?.message || '下载失败')
    }
}

function openPreview(file: FileMeta) {
    previewFiles.value = tableData.value.filter((item) => resolvePreviewKind(item) !== 'other')
    previewFile.value = file
    previewVisible.value = true
}

function onPreviewChange(file: FileMeta) {
    previewFile.value = file
}

async function handleDelete(file: FileMeta) {
    try {
        await fileClient.remove(file.id)
        ElMessage.success('删除成功')
        loadData()
    } catch (e) {
        ElMessage.error((e as { message?: string })?.message || '删除失败')
    }
}
</script>

<style scoped>
.file-type-tag {
    display: inline-block;
    min-width: 42px;
    margin-right: 8px;
    padding: 1px 6px;
    border-radius: 4px;
    background: #f0f2f5;
    color: #606266;
    font-size: 11px;
    text-align: center;
}
</style>
