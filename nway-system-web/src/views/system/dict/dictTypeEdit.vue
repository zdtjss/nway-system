<template>
    <div class="dict-type-edit">
        <el-form ref="formRef" :model="form" :rules="rules" label-position="top">
            <div class="form-grid">
                <el-form-item label="字典编码" prop="dictCode">
                    <el-input v-model="form.dictCode" placeholder="如 employee_status" :disabled="!!form.id" />
                </el-form-item>
                <el-form-item label="字典名称" prop="dictName">
                    <el-input v-model="form.dictName" placeholder="如 员工状态" />
                </el-form-item>
                <el-form-item v-if="isSuperAdmin" label="所属租户">
                    <el-select v-model="form.tenantCode" placeholder="公共字典（不指定租户）" clearable
                        style="width: 100%">
                        <el-option v-for="t in tenantList" :key="t.id" :label="t.tenantName" :value="t.tenantCode" />
                    </el-select>
                </el-form-item>
                <el-form-item label="状态" prop="status">
                    <el-switch v-model="form.status" active-value="using" inactive-value="disabled"
                        active-text="启用" inactive-text="禁用" />
                </el-form-item>
            </div>
            <el-form-item label="备注">
                <el-input v-model="form.remark" type="textarea" :rows="2" maxlength="200" show-word-limit
                    placeholder="选填，说明该字典的用途" />
            </el-form-item>

            <div class="field-head">
                <div>
                    <span class="field-title">字段定义</span>
                    <span class="field-tip">不定义字段时为传统 Key/Value 字典；定义字段后为多字段字典，勾选“查询条件”的字段才会出现在字典项查询区</span>
                </div>
                <el-button :icon="Plus" @click="addField">添加字段</el-button>
            </div>
            <el-table :data="form.fieldDefs" border size="small" style="width: 100%"
                empty-text="暂无字段定义，不添加则为传统 Key/Value 字典">
                <el-table-column label="字段编码" width="170">
                    <template #default="scope">
                        <el-input v-model="scope.row.code" placeholder="如 employee_id" size="small" />
                    </template>
                </el-table-column>
                <el-table-column label="字段名称" width="150">
                    <template #default="scope">
                        <el-input v-model="scope.row.name" placeholder="如 工号" size="small" />
                    </template>
                </el-table-column>
                <el-table-column label="类型" width="120">
                    <template #default="scope">
                        <el-select v-model="scope.row.type" size="small" style="width: 100%">
                            <el-option v-for="t in fieldTypes" :key="t.value" :label="t.label" :value="t.value" />
                        </el-select>
                    </template>
                </el-table-column>
                <el-table-column label="必填" width="62" align="center">
                    <template #default="scope">
                        <el-switch v-model="scope.row.required" />
                    </template>
                </el-table-column>
                <el-table-column label="查询条件" width="82" align="center">
                    <template #default="scope">
                        <el-switch v-model="scope.row.searchable" />
                    </template>
                </el-table-column>
                <el-table-column label="排序" width="84">
                    <template #default="scope">
                        <el-input-number v-model="scope.row.sort" :min="0" :max="999" size="small"
                            controls-position="right" class="sort-input" />
                    </template>
                </el-table-column>
                <el-table-column label="操作" width="60" align="center">
                    <template #default="scope">
                        <el-button type="danger" link :icon="Delete" @click="removeField(scope.$index)" />
                    </template>
                </el-table-column>
            </el-table>
        </el-form>
        <div class="dialog-footer">
            <el-button @click="cancel">取消</el-button>
            <el-button type="primary" :loading="saving" @click="save">保存</el-button>
        </div>
    </div>
</template>

<script setup>
import { Delete, Plus } from '@element-plus/icons-vue'
import { ref, onMounted } from "vue"
import { ElMessage } from 'element-plus'
import http from '@/js/httpProxy.js'
import auth from '@/js/auth.js'

const emit = defineEmits(["close"])

const props = defineProps({
    dictTypeId: String
})

const isSuperAdmin = auth.isSuperAdmin()
const tenantList = ref([])

const fieldTypes = [
    { value: 'string', label: '字符串' },
    { value: 'number', label: '数字' },
    { value: 'date', label: '日期' },
    { value: 'datetime', label: '日期时间' },
    { value: 'boolean', label: '布尔' }
]

const form = ref({
    id: "",
    dictCode: "",
    dictName: "",
    remark: "",
    status: "using",
    tenantCode: "",
    fieldDefs: []
})

const formRef = ref()
const saving = ref(false)
const rules = {
    dictCode: [{ required: true, message: '请输入字典编码', trigger: 'blur' }],
    dictName: [{ required: true, message: '请输入字典名称', trigger: 'blur' }],
    status: [{ required: true, message: '请选择状态', trigger: 'change' }]
}

onMounted(() => {
    if (isSuperAdmin) {
        http.get('/tenant/queryAll').then(({ data }) => {
            if (data.code === 200) tenantList.value = data.data || []
        })
    }
    if (props.dictTypeId) {
        getInfo()
    }
})

function getInfo() {
    http.get("/dict/getTypeById", { params: { id: props.dictTypeId } }).then(({ data }) => {
        if (data.code === 200 && data.data) {
            form.value = {
                id: data.data.id,
                dictCode: data.data.dictCode,
                dictName: data.data.dictName,
                remark: data.data.remark,
                status: data.data.status,
                tenantCode: data.data.tenantCode || '',
                fieldDefs: data.data.fieldDefs || []
            }
        }
    })
}

function addField() {
    form.value.fieldDefs.push({ code: "", name: "", type: "string", required: false, searchable: false, sort: form.value.fieldDefs.length + 1 })
}

function removeField(idx) {
    form.value.fieldDefs.splice(idx, 1)
}

function cancel() {
    emit('close')
}

function save() {
    formRef.value?.validate((valid) => {
        if (!valid) return
        // 字段定义基础校验：编码/名称不能为空，编码不能重复
        const defs = form.value.fieldDefs || []
        const codes = new Set()
        for (const d of defs) {
            if (!d.code || !d.name) {
                ElMessage.warning('字段编码和名称不能为空')
                return
            }
            if (codes.has(d.code)) {
                ElMessage.warning('字段编码不能重复：' + d.code)
                return
            }
            codes.add(d.code)
        }
        saving.value = true
        http.post("/dict/saveType", form.value).then(({ data }) => {
            if (data.code === 200) {
                ElMessage.success('保存成功')
                // 回传 id/编码，父页面据此在刷新后保持（或定位到）正确的字典
                emit('close', { id: form.value.id, dictCode: form.value.dictCode })
            }
        }).catch(() => {
            // 错误提示由请求拦截器统一处理
        }).finally(() => {
            saving.value = false
        })
    })
}
</script>

<style scoped>
.form-grid {
    display: grid;
    grid-template-columns: repeat(2, minmax(0, 1fr));
    column-gap: 16px;
}

.field-head {
    display: flex;
    align-items: flex-start;
    justify-content: space-between;
    gap: 12px;
    margin: 4px 0 10px;
}

.field-title {
    font-weight: 600;
    color: var(--el-text-color-primary);
}

.field-tip {
    display: block;
    margin-top: 2px;
    color: var(--el-text-color-secondary);
    font-size: 12px;
    line-height: 1.5;
}

.sort-input {
    width: 70px;
}

.dialog-footer {
    display: flex;
    justify-content: flex-end;
    gap: 8px;
    margin-top: 16px;
}
</style>
