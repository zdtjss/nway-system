<template>
    <el-form ref="formRef" :model="form" :rules="rules" label-width="100px">
        <el-form-item label="应用标识" prop="appCode">
            <el-input v-model="form.appCode" :disabled="!!form.id" placeholder="唯一标识，如 order-system" />
        </el-form-item>
        <el-form-item label="应用名称" prop="appName">
            <el-input v-model="form.appName" placeholder="显示名称" />
        </el-form-item>
        <el-form-item v-if="isSuperAdmin" label="归属租户" prop="tenantCode">
            <el-select v-model="form.tenantCode" placeholder="请选择租户" style="width: 100%" clearable>
                <el-option v-for="t in tenantList" :key="t.id" :label="t.tenantName" :value="t.tenantCode" />
            </el-select>
        </el-form-item>
        <el-form-item label="前端地址" prop="host">
            <el-input v-model="form.host" placeholder="微前端加载入口，如 https://order.example.com" />
        </el-form-item>
        <el-form-item label="后端API" prop="apiBaseUrl">
            <el-input v-model="form.apiBaseUrl" placeholder="后端 API 基础地址（可选）" />
        </el-form-item>
        <el-form-item label="状态">
            <el-radio-group v-model="form.status">
                <el-radio value="using">启用</el-radio>
                <el-radio value="disabled">禁用</el-radio>
            </el-radio-group>
        </el-form-item>
        <el-form-item label="排序">
            <el-input-number v-model="form.sort" :min="0" :max="999" />
        </el-form-item>
        <el-form-item>
            <el-button type="primary" @click="handleSave">保存</el-button>
            <el-button @click="emit('close')">取消</el-button>
        </el-form-item>
    </el-form>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import type { FormInstance, FormRules } from 'element-plus'
import { save, type App } from '@/api/app'
import { queryAll as queryTenantAll, type TenantDto } from '@/api/tenant'
import auth from '@/js/auth.js'
import http from '@/js/httpProxy.js'

const props = defineProps<{ appId: string }>()
const emit = defineEmits<{ close: [] }>()

const isSuperAdmin = ref(auth.isSuperAdmin())
const tenantList = ref<TenantDto[]>([])
/** 原归属租户，用于识别租户变更（变更会级联同步该应用下所有菜单的租户） */
const originalTenantCode = ref('')

const formRef = ref<FormInstance>()
const form = ref<App>({
    appCode: '',
    appName: '',
    host: '',
    apiBaseUrl: '',
    status: 'using',
    sort: 0,
    tenantCode: ''
})

const rules: FormRules = {
    appCode: [{ required: true, message: '应用标识不能为空', trigger: 'blur' }],
    appName: [{ required: true, message: '应用名称不能为空', trigger: 'blur' }]
}

onMounted(async () => {
    if (isSuperAdmin.value) {
        loadTenantList()
    }
    if (props.appId) {
        const { data } = await http.get('/app/getById', { params: { id: props.appId } })
        if (data.data) {
            form.value = data.data
            originalTenantCode.value = data.data.tenantCode || ''
        }
    }
})

function loadTenantList() {
    queryTenantAll().then(({ code, data }) => {
        if (code === 200) {
            tenantList.value = data || []
        }
    })
}

async function handleSave() {
    await formRef.value?.validate()
    // 租户变更会级联更新该应用下所有菜单的归属租户，需二次确认
    if (form.value.id && form.value.tenantCode !== originalTenantCode.value) {
        try {
            await ElMessageBox.confirm(
                '归属租户已变更，保存后该应用下所有菜单的归属租户将同步更新。是否继续？',
                '确认变更租户', {
                type: 'warning',
                confirmButtonText: '继续保存',
                cancelButtonText: '取消'
            })
        } catch {
            return
        }
    }
    // 只提交业务字段，createTime/modifiedTime 等由后端填充策略维护，回传会导致 LocalDateTime 反序列化失败
    await save({
        id: form.value.id,
        appCode: form.value.appCode,
        appName: form.value.appName,
        host: form.value.host,
        apiBaseUrl: form.value.apiBaseUrl,
        status: form.value.status,
        sort: form.value.sort,
        tenantCode: form.value.tenantCode
    })
    ElMessage.success('保存成功')
    emit('close')
}
</script>
