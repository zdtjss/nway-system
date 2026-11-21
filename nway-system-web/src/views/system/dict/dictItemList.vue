<template>
    <div class="dict-item-list">
        <div class="item-toolbar">
            <el-form :inline="true" class="filter-form" @submit.prevent>
                <el-form-item v-if="isSimpleMode" label="Key">
                    <el-input v-model="queryParam.key" placeholder="按 Key 筛选" style="width: 200px" clearable />
                </el-form-item>
                <template v-else>
                    <el-form-item v-for="f in searchFields" :key="f.code" :label="f.name">
                        <el-input v-if="f.type === 'string'" v-model="queryParam[f.code]" style="width: 180px" clearable />
                        <el-input-number v-else-if="f.type === 'number'" v-model="queryParam[f.code]"
                            style="width: 160px" controls-position="right" />
                        <el-date-picker v-else-if="f.type === 'date'" v-model="queryParam[f.code]" type="date"
                            value-format="YYYY-MM-DD" style="width: 160px" />
                        <el-date-picker v-else-if="f.type === 'datetime'" v-model="queryParam[f.code]" type="datetime"
                            value-format="YYYY-MM-DD HH:mm:ss" style="width: 200px" />
                        <el-select v-else-if="f.type === 'boolean'" v-model="queryParam[f.code]" clearable style="width: 100px">
                            <el-option label="是" value="true" />
                            <el-option label="否" value="false" />
                        </el-select>
                        <el-input v-else v-model="queryParam[f.code]" style="width: 180px" clearable />
                    </el-form-item>
                </template>
                <el-form-item label="状态">
                    <el-switch v-model="statusFilter" active-value="using" inactive-value="disabled"
                        active-text="启用" inactive-text="禁用" />
                </el-form-item>
            </el-form>
            <div class="toolbar-right">
                <span class="item-count">共 {{ tableData.length }} 项</span>
                <el-button :icon="Upload" @click="openImportDialog">导入</el-button>
                <el-button type="primary" :icon="Plus" @click="addRow()">新增字典项</el-button>
            </div>
        </div>

        <el-table v-loading="loading" :data="tableData" stripe border style="width: 100%"
            @row-dblclick="onRowDblClick">
            <template v-if="isSimpleMode">
                <el-table-column label="Key" width="200" fixed="left">
                    <template #default="scope">
                        <span v-if="!isEditing(scope.row)">{{ scope.row.itemValue?.key }}</span>
                        <el-input v-else v-model="editForm.itemValue.key" size="small" placeholder="请输入Key" />
                    </template>
                </el-table-column>
                <el-table-column label="Value" min-width="200" show-overflow-tooltip>
                    <template #default="scope">
                        <span v-if="!isEditing(scope.row)">{{ scope.row.itemValue?.value }}</span>
                        <el-input v-else v-model="editForm.itemValue.value" size="small" placeholder="请输入Value" />
                    </template>
                </el-table-column>
            </template>
            <template v-else>
                <el-table-column v-for="f in fieldDefs" :key="f.code" :label="f.name" :min-width="colWidth(f)">
                    <template #default="scope">
                        <span v-if="!isEditing(scope.row)">{{ formatValue(scope.row.itemValue, f) }}</span>
                        <el-input v-else-if="f.type === 'string'" v-model="editForm.itemValue[f.code]" size="small"
                            :placeholder="'请输入' + f.name" />
                        <el-input-number v-else-if="f.type === 'number'" v-model="editForm.itemValue[f.code]"
                            size="small" style="width: 100%" controls-position="right" />
                        <el-date-picker v-else-if="f.type === 'date'" v-model="editForm.itemValue[f.code]" size="small"
                            type="date" value-format="YYYY-MM-DD" style="width: 100%" />
                        <el-date-picker v-else-if="f.type === 'datetime'" v-model="editForm.itemValue[f.code]"
                            size="small" type="datetime" value-format="YYYY-MM-DD HH:mm:ss" style="width: 100%" />
                        <el-switch v-else-if="f.type === 'boolean'" v-model="editForm.itemValue[f.code]" size="small" />
                        <el-input v-else v-model="editForm.itemValue[f.code]" size="small"
                            :placeholder="'请输入' + f.name" />
                    </template>
                </el-table-column>
            </template>
            <el-table-column label="排序" :width="editingId !== null ? 130 : 80">
                <template #default="scope">
                    <span v-if="!isEditing(scope.row)">{{ scope.row.sort }}</span>
                    <el-input-number v-else v-model="editForm.sort" size="small" :min="0" :max="999"
                        style="width: 100%" controls-position="right" />
                </template>
            </el-table-column>
            <el-table-column label="状态" width="100">
                <template #default="scope">
                    <el-tag v-if="!isEditing(scope.row)"
                        :type="scope.row.status === 'using' ? 'success' : 'danger'" size="small">
                        {{ scope.row.status === 'using' ? '启用' : '禁用' }}
                    </el-tag>
                    <el-switch v-else v-model="editForm.status" size="small" active-value="using"
                        inactive-value="disabled" />
                </template>
            </el-table-column>
            <el-table-column label="操作" width="150" fixed="right">
                <template #default="scope">
                    <template v-if="isEditing(scope.row)">
                        <el-button link type="primary" :icon="Check" @click="saveRow()">保存</el-button>
                        <el-button link @click="cancelEdit()">取消</el-button>
                    </template>
                    <template v-else>
                        <el-button link type="primary" :icon="Edit" @click="editRow(scope.row)">编辑</el-button>
                        <el-popconfirm title="确定删除该字典项？" @confirm="handleDelete(scope.row)">
                            <template #reference>
                                <el-button type="danger" link :icon="Delete">删除</el-button>
                            </template>
                        </el-popconfirm>
                    </template>
                </template>
            </el-table-column>
            <template #empty>
                <el-empty description="暂无字典项" :image-size="60">
                    <el-button type="primary" :icon="Plus" @click="addRow()">新增字典项</el-button>
                </el-empty>
            </template>
        </el-table>

        <el-dialog v-model="importVisible" title="导入字典项" width="600px" :close-on-click-modal="false"
            @closed="resetImport">
            <el-form label-width="90px" class="import-form">
                <el-form-item label="字典类别">
                    <span class="import-dict-code">{{ dictCode || '-' }}</span>
                </el-form-item>
                <el-form-item v-if="!isSimpleMode" label="匹配字段">
                    <div class="import-match">
                        <el-select v-model="importMatchField" style="width: 100%">
                            <el-option v-for="f in fieldDefs" :key="f.code" :value="f.code"
                                :label="`${f.name}（${f.code}）`" />
                        </el-select>
                        <div class="import-tip">按该字段的值判断新增或更新已有字典项</div>
                    </div>
                </el-form-item>
                <el-form-item label="选择文件">
                    <el-upload ref="importUploadRef" v-model:file-list="importFileList" class="import-upload" drag
                        :auto-upload="false" :limit="1" accept=".xlsx,.xls,.csv" :on-change="onImportFileChange"
                        :on-remove="onImportFileRemove" :on-exceed="onImportFileExceed">
                        <el-icon class="el-icon--upload"><UploadFilled /></el-icon>
                        <div class="el-upload__text">拖拽文件到此处，或<em>点击选择</em></div>
                        <template #tip>
                            <div class="el-upload__tip">支持 Excel（.xlsx / .xls）与 CSV，第一行为表头</div>
                        </template>
                    </el-upload>
                </el-form-item>
                <el-form-item label="表头说明">
                    <div class="import-header-tip">
                        <template v-if="isSimpleMode">
                            需包含 <b>Key</b>、<b>Value</b> 列，可选 <b>排序</b>、<b>状态</b> 列；按 Key 判断新增或更新。
                        </template>
                        <template v-else>
                            列名可使用字段名称或字段编码：{{ fieldDefs.map(f => f.name + '（' + f.code + '）').join('、') }}；
                            可选 <b>排序</b>、<b>状态</b> 列。
                        </template>
                    </div>
                </el-form-item>
            </el-form>

            <el-alert v-if="importErrors.length" type="error" :closable="false" class="import-errors"
                :title="`导入失败，共 ${importErrors.length} 处问题，未写入任何数据`">
                <div class="import-error-list">
                    <div v-for="(err, idx) in importErrors" :key="idx">{{ err }}</div>
                </div>
            </el-alert>

            <template #footer>
                <el-button @click="importVisible = false">取消</el-button>
                <el-button type="primary" :loading="importing" :disabled="!importFile" @click="submitImport">
                    开始导入
                </el-button>
            </template>
        </el-dialog>
    </div>
</template>

<script setup>
import { Check, Delete, Edit, Plus, Upload, UploadFilled } from '@element-plus/icons-vue'
import { ref, onMounted, computed, watch } from "vue"
import { ElMessage, genFileId } from 'element-plus'
import http from '@/js/httpProxy.js'

const props = defineProps({
    dictTypeId: String,
    fieldDefs: { type: Array, default: () => [] },
    dictCode: String
})

const tableData = ref([])
const queryParam = ref({})
const statusFilter = ref("using")
const loading = ref(false)

// 传统字典项模式：未定义字段，数据存 itemValue.key / itemValue.value
const isSimpleMode = computed(() => !props.fieldDefs || props.fieldDefs.length === 0)

// 仅配置为查询条件的字段才显示在查询区
const searchFields = computed(() => (props.fieldDefs || []).filter(f => f.searchable))

// 行内编辑状态：editingId 为 null 表示无编辑；为 "" 表示编辑新增行；否则为编辑中的行 id
const editingId = ref(null)
// 编辑表单副本：进入编辑时深拷贝行数据，避免取消时污染原行
const editForm = ref(null)

// 导入状态
const importVisible = ref(false)
const importing = ref(false)
const importUploadRef = ref()
const importFile = ref(null)
const importFileList = ref([])
const importMatchField = ref("")
const importErrors = ref([])

onMounted(() => {
    loadData()
})

async function loadData() {
    loading.value = true
    try {
        const qs = new URLSearchParams()
        qs.append('dictTypeId', props.dictTypeId)
        if (statusFilter.value) {
            qs.append('status', statusFilter.value)
        }
        Object.entries(queryParam.value).forEach(([code, v]) => {
            if (v !== '' && v !== null && v !== undefined) {
                qs.append(`fieldFilters[${code}]`, String(v))
            }
        })
        const { data } = await http.get("/dict/queryItem?" + qs.toString())
        if (data.code === 200) {
            tableData.value = data.data || []
            editingId.value = null
            editForm.value = null
        }
    } catch {
        // 错误提示由请求拦截器统一处理
    } finally {
        loading.value = false
    }
}

// 查询条件变化后自动刷新（300ms 防抖，避免连续输入触发多次请求）
let searchTimer = null
watch([queryParam, statusFilter], () => {
    clearTimeout(searchTimer)
    searchTimer = setTimeout(loadData, 300)
}, { deep: true })

function isEditing(row) {
    return editingId.value !== null && row.id === editingId.value
}

function onRowDblClick(row, column) {
    // 操作列双击不进入编辑
    if (column && column.label === '操作') return
    editRow(row)
}

function editRow(row) {
    // 若当前编辑的是新增占位行，先移除
    if (editingId.value === "") {
        const idx = tableData.value.findIndex(r => r.id === "")
        if (idx > -1) {
            tableData.value.splice(idx, 1)
        }
    }
    if (editingId.value === row.id) return
    editingId.value = row.id
    editForm.value = JSON.parse(JSON.stringify(row))
    if (!editForm.value.itemValue) {
        editForm.value.itemValue = {}
    }
}

function addRow() {
    // 若已有编辑行：新增行先移除占位行，已有行直接覆盖（编辑绑定的是副本，原行未被修改）
    if (editingId.value === "") {
        const idx = tableData.value.findIndex(r => r.id === "")
        if (idx > -1) {
            tableData.value.splice(idx, 1)
        }
    }
    const newRow = {
        id: "",
        dictTypeId: props.dictTypeId,
        itemValue: {},
        sort: 0,
        status: "using"
    }
    tableData.value.unshift(newRow)
    editingId.value = ""
    editForm.value = JSON.parse(JSON.stringify(newRow))
}

function saveRow() {
    if (!editForm.value) return
    // 必填校验
    if (isSimpleMode.value) {
        const key = editForm.value.itemValue?.key
        if (!key || String(key).trim() === '') {
            ElMessage.warning('请输入Key')
            return
        }
    } else {
        for (const f of props.fieldDefs) {
            if (f.required) {
                const v = editForm.value.itemValue?.[f.code]
                if (v === null || v === undefined || v === '') {
                    ElMessage.warning(`${f.name}不能为空`)
                    return
                }
            }
        }
        // boolean 字段统一转为布尔值存储
        for (const f of props.fieldDefs) {
            if (f.type === 'boolean') {
                const v = editForm.value.itemValue[f.code]
                editForm.value.itemValue[f.code] = v === true || v === 'true'
            }
        }
    }
    const payload = {
        id: editForm.value.id,
        dictTypeId: props.dictTypeId,
        itemValue: editForm.value.itemValue,
        sort: editForm.value.sort,
        status: editForm.value.status
    }
    http.post("/dict/saveItem", payload).then(({ data }) => {
        if (data.code === 200) {
            ElMessage.success('保存成功')
            loadData()
        }
    }).catch(() => {
        // 错误提示由请求拦截器统一处理
    })
}

function cancelEdit() {
    // 新增行取消：移除占位行
    if (editingId.value === "") {
        const idx = tableData.value.findIndex(r => r.id === "")
        if (idx > -1) {
            tableData.value.splice(idx, 1)
        }
    }
    editingId.value = null
    editForm.value = null
}

function handleDelete(row) {
    http.post('/dict/deleteItem?id=' + row.id).then(({ data }) => {
        if (data.code === 200) {
            ElMessage.success('删除成功')
            loadData()
        }
    }).catch(() => {
        // 错误提示由请求拦截器统一处理
    })
}

function openImportDialog() {
    importErrors.value = []
    importFile.value = null
    importFileList.value = []
    importMatchField.value = props.fieldDefs?.[0]?.code || ''
    importVisible.value = true
}

function onImportFileChange(uploadFile) {
    importFile.value = uploadFile?.raw || null
    importErrors.value = []
}

function onImportFileRemove() {
    importFile.value = null
    importErrors.value = []
}

// limit=1 时再次选择文件：替换已选文件
function onImportFileExceed(files) {
    const file = files?.[0]
    if (!file) return
    importUploadRef.value?.clearFiles()
    file.uid = genFileId()
    importUploadRef.value?.handleStart(file)
}

function resetImport() {
    importFile.value = null
    importFileList.value = []
    importMatchField.value = ''
    importErrors.value = []
}

async function submitImport() {
    if (!importFile.value) {
        ElMessage.warning('请选择导入文件')
        return
    }
    if (!isSimpleMode.value && !importMatchField.value) {
        ElMessage.warning('请选择匹配字段')
        return
    }
    importing.value = true
    importErrors.value = []
    try {
        const formData = new FormData()
        formData.append('file', importFile.value)
        formData.append('dictTypeId', props.dictTypeId)
        if (!isSimpleMode.value) {
            formData.append('matchField', importMatchField.value)
        }
        // 批量导入耗时较长，单独放宽超时
        const { data } = await http.post('/dict/importItems', formData, { timeout: 300000 })
        if (data.code === 200) {
            const result = data.data || {}
            if (result.errors && result.errors.length) {
                importErrors.value = result.errors
                return
            }
            ElMessage.success(`导入成功：新增 ${result.inserted || 0} 条，更新 ${result.updated || 0} 条`)
            importVisible.value = false
            loadData()
        }
    } catch {
        // 错误提示由请求拦截器统一处理
    } finally {
        importing.value = false
    }
}

function colWidth(f) {
    if (f.type === 'datetime') return 170
    if (f.type === 'date') return 130
    if (f.type === 'boolean') return 80
    return 130
}

function formatValue(itemValue, f) {
    if (!itemValue) return ''
    const v = itemValue[f.code]
    if (v === null || v === undefined || v === '') return ''
    if (f.type === 'boolean') {
        return v === true || v === 'true' ? '是' : '否'
    }
    return v
}
</script>

<style scoped>
.dict-item-list {
    padding: 10px 12px 12px;
}

.item-toolbar {
    display: flex;
    flex-wrap: wrap;
    align-items: flex-start;
    justify-content: space-between;
    gap: 8px 12px;
}

.filter-form {
    flex: 1;
    min-width: 0;
}

.filter-form :deep(.el-form-item) {
    margin-right: 12px;
    margin-bottom: 8px;
}

.toolbar-right {
    display: flex;
    flex: none;
    align-items: center;
    gap: 10px;
    margin-left: auto;
    padding-top: 2px;
}

.item-count {
    color: var(--el-text-color-secondary);
    font-size: 12px;
    white-space: nowrap;
}

.import-dict-code {
    font-family: Consolas, Monaco, monospace;
    font-size: 13px;
    color: var(--el-text-color-regular);
}

.import-match {
    width: 100%;
}

.import-tip {
    margin-top: 2px;
    font-size: 12px;
    line-height: 18px;
    color: var(--el-text-color-secondary);
}

.import-upload {
    width: 100%;
}

.import-header-tip {
    font-size: 12px;
    line-height: 20px;
    color: var(--el-text-color-secondary);
}

.import-errors {
    margin-top: 4px;
}

.import-error-list {
    max-height: 180px;
    margin-top: 6px;
    overflow-y: auto;
    font-size: 12px;
    line-height: 20px;
    white-space: pre-wrap;
}
</style>
