<template>
    <el-form ref="formRef" :model="form" :rules="rules" label-width="100px">
        <el-form-item label="角色编码" prop="roleCode">
            <el-input v-model="form.roleCode" :disabled="!!form.id" />
        </el-form-item>
        <el-form-item label="角色名称" prop="roleName">
            <el-input v-model="form.roleName" />
        </el-form-item>
        <el-form-item label="所属应用" prop="appCode">
            <el-select v-model="form.appCode" filterable style="width: 100%">
                <el-option label="平台级（跨应用）" value="*" />
                <el-option v-for="app in appList" :key="app.appCode" :label="app.appName" :value="app.appCode" />
            </el-select>
        </el-form-item>
        <el-form-item v-if="showTenantSelect" label="归属租户" prop="tenantCode"
            :rules="[{ required: true, message: '请选择归属租户', trigger: 'change' }]">
            <el-select v-model="form.tenantCode" placeholder="请选择租户" style="width: 100%">
                <el-option v-for="t in tenantList" :key="t.id" :label="t.tenantName" :value="t.tenantCode" />
            </el-select>
        </el-form-item>
        <el-form-item v-else-if="deriveTenant" label="归属租户">
            <el-input :model-value="ownTenantName" disabled />
            <div class="form-hint">公共共享应用/平台级角色需显式指定归属租户；本租户角色由后端按当前租户自动归属</div>
        </el-form-item>
        <el-form-item label="描述">
            <el-input v-model="form.description" type="textarea" :rows="2" />
        </el-form-item>
        <el-form-item>
            <el-button type="primary" @click="handleSave">保存</el-button>
            <el-button @click="emit('close')">取消</el-button>
        </el-form-item>
    </el-form>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import type { FormInstance, FormRules } from 'element-plus'
import { save, queryById, type RoleDto } from '@/api/role'
import { queryEnabled, type App } from '@/api/app'
import { queryAll as queryTenantAll, type TenantDto } from '@/api/tenant'
import auth from '@/js/auth.js'

const props = defineProps<{ roleId: string }>()
const emit = defineEmits<{ close: [] }>()

const formRef = ref<FormInstance>()
const appList = ref<App[]>([])
const tenantList = ref<TenantDto[]>([])
const isSuperAdmin = ref(auth.isSuperAdmin())
const ownTenantName = ref(auth.getTenantName())
const form = ref<RoleDto>({
    roleCode: '',
    roleName: '',
    appCode: '',
    description: ''
})

const rules: FormRules = {
    roleCode: [{ required: true, message: '角色编码不能为空', trigger: 'blur' }],
    roleName: [{ required: true, message: '角色名称不能为空', trigger: 'blur' }],
    appCode: [{ required: true, message: '所属应用不能为空', trigger: 'change' }]
}

/** 所选应用是否为 public 租户下的共享应用（如 wform/kql） */
const sharedApp = computed(() => {
    const app = appList.value.find(a => a.appCode === form.value.appCode)
    return app?.tenantCode === 'public'
})

/**
 * 需要显式指定归属租户的场景（仅在新增时）：共享应用或平台级角色。
 * 私有应用的角色租户恒等于应用租户，由后端强制；编辑时租户不可变更。
 */
const deriveTenant = computed(() =>
    !form.value.id && (form.value.appCode === '*' || sharedApp.value))

/** 超管才提供下拉（其余用户的租户上下文唯一，后端会自动归属本租户） */
const showTenantSelect = computed(() => deriveTenant.value && isSuperAdmin.value)

onMounted(async () => {
    // 每次打开对话框都重新拉取最新应用列表，保证 appCode 能正确转义为应用名
    const appRes = await queryEnabled()
    appList.value = appRes.data || []

    if (isSuperAdmin.value) {
        loadTenantList()
    }

    if (props.roleId) {
        const res = await queryById(props.roleId)
        if (res.data) {
            form.value = res.data
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
    // 只提交业务字段，createTime/modifiedTime 等由后端维护，回传无意义
    await save({
        id: form.value.id,
        roleCode: form.value.roleCode,
        roleName: form.value.roleName,
        appCode: form.value.appCode,
        description: form.value.description,
        tenantCode: form.value.tenantCode
    })
    ElMessage.success('保存成功')
    emit('close')
}
</script>

<style scoped>
.form-hint {
    font-size: 12px;
    color: var(--el-text-color-secondary);
    line-height: 1.5;
    margin-top: 4px;
}
</style>
