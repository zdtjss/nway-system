<template>
    <div style="width: 90%;margin: 10px auto;">
        <el-form ref="formRef" :model="form" :rules="rules" label-width="120px">
            <template v-if="isSimpleMode">
                <el-form-item label="Key" prop="itemValue.key" :rules="requiredRule('请输入Key')">
                    <el-input v-model="form.itemValue.key" style="width: 300px;" />
                </el-form-item>
                <el-form-item label="Value" prop="itemValue.value" :rules="requiredRule('请输入Value')">
                    <el-input v-model="form.itemValue.value" style="width: 300px;" />
                </el-form-item>
            </template>

            <template v-else>
                <el-form-item v-for="f in fieldDefs" :key="f.code" :label="f.name"
                    :prop="'itemValue.' + f.code" :rules="buildRule(f)">
                    <el-input v-if="f.type === 'string'" v-model="form.itemValue[f.code]" style="width: 300px;" />
                    <el-input-number v-else-if="f.type === 'number'" v-model="form.itemValue[f.code]"
                        style="width: 300px;" controls-position="right" />
                    <el-date-picker v-else-if="f.type === 'date'" v-model="form.itemValue[f.code]" type="date"
                        value-format="YYYY-MM-DD" placeholder="选择日期" style="width: 300px;" />
                    <el-date-picker v-else-if="f.type === 'datetime'" v-model="form.itemValue[f.code]" type="datetime"
                        value-format="YYYY-MM-DD HH:mm:ss" placeholder="选择日期时间" style="width: 300px;" />
                    <el-switch v-else-if="f.type === 'boolean'" v-model="form.itemValue[f.code]" />
                    <el-input v-else v-model="form.itemValue[f.code]" style="width: 300px;" />
                </el-form-item>
            </template>

            <el-form-item label="排序" prop="sort">
                <el-input-number v-model="form.sort" :min="0" :max="999" controls-position="right" />
            </el-form-item>
            <el-form-item label="状态" prop="status">
                <el-select v-model="form.status" style="width: 200px;">
                    <el-option label="启用" value="using" />
                    <el-option label="禁用" value="disabled" />
                </el-select>
            </el-form-item>
        </el-form>
    </div>
    <div class="dialog-footer">
        <el-button type="primary" @click="save()">保存</el-button>
    </div>
</template>

<script setup>
import { ref, onMounted, reactive, computed } from "vue"
import { ElMessage } from 'element-plus'
import http from '@/js/httpProxy.js'

const emit = defineEmits(["close"])

const props = defineProps({
    dictTypeId: String,
    itemId: String,
    fieldDefs: { type: Array, default: () => [] }
})

const form = reactive({
    id: "",
    dictTypeId: "",
    itemValue: {},
    sort: 0,
    status: "using"
})

const formRef = ref()
const rules = {
    status: [{ required: true, message: '请选择状态', trigger: 'change' }]
}

// 传统字典项模式：未定义字段时，数据存 itemValue.key / itemValue.value
const isSimpleMode = computed(() => !props.fieldDefs || props.fieldDefs.length === 0)

function requiredRule(message) {
    return [{ required: true, message, trigger: 'blur' }]
}

onMounted(() => {
    form.dictTypeId = props.dictTypeId
    if (props.itemId) {
        getInfo()
    }
})

function getInfo() {
    http.get("/dict/getItemById", { params: { id: props.itemId } }).then(({ data }) => {
        if (data.code === 200 && data.data) {
            const d = data.data
            form.id = d.id
            form.dictTypeId = d.dictTypeId
            form.itemValue = d.itemValue || {}
            form.sort = d.sort
            form.status = d.status
        }
    })
}

function buildRule(f) {
    if (f.required) {
        return [{ required: true, message: `${f.name}不能为空`, trigger: 'blur' }]
    }
    return []
}

function save() {
    formRef.value?.validate((valid) => {
        if (!valid) return
        http.post("/dict/saveItem", form).then(({ data }) => {
            if (data.code === 200) {
                ElMessage.success('保存成功')
                emit('close')
            }
        })
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
