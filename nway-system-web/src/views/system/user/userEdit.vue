<template>
    <div style="width: 80%;margin: 10px auto;">
        <el-form :model="user" :rules="rules" ref="formRef" label-width="100px" width="80%" :inline="true">
            <el-form-item label="姓名" prop="realName">
                <el-input v-model="user.realName" maxlength="50" show-word-limit style="width: 200px;"/>
            </el-form-item>
            <el-form-item label="工号" prop="employeeId">
                <el-input v-model="user.employeeId" style="width: 200px;"/>
            </el-form-item>
            <el-form-item label="头像">
                <el-input v-model="user.avatar" style="width: 200px;"/>
            </el-form-item>
            <el-form-item label="手机号">
                <el-input v-model="user.phoneNumber" style="width: 200px;"/>
            </el-form-item>
            <el-form-item label="邮箱">
                <el-input v-model="user.email" style="width: 200px;"/>
            </el-form-item>
            <el-form-item label="部门">
                <el-tree-select v-model="user.deptId" :data="deptTree" :props="treeProps"
                    node-key="id" check-strictly clearable filterable
                    placeholder="请选择部门" style="width: 200px;" />
                <div style="color:#999;font-size:12px;line-height:1.5;margin-top:4px;width:100%;">
                    {{ deptTip }}
                </div>
            </el-form-item>
            <el-form-item label="状态">
                <el-select v-model="user.status" placeholder="请选择" style="width: 200px">
                    <el-option v-for="item in userStatusList" :key="item.value" :label="item.label" :value="item.value" />
                </el-select>
            </el-form-item>
            <el-form-item v-if="isSuperAdmin" label="租户">
                <el-select v-model="user.tenantCode" placeholder="请选择租户" style="width: 200px" clearable
                    @change="onTenantChange">
                    <el-option v-for="t in tenantList" :key="t.id" :label="t.tenantName" :value="t.tenantCode" />
                </el-select>
            </el-form-item>
        </el-form>
    </div>
    <div class="dialog-footer" style="right: 10px;float: right;position: absolute;bottom: 10px;">
        <el-button type="primary" @click="saveUser()">
            保存
        </el-button>
    </div>
</template>

<script setup>
import { ref, onMounted, computed } from "vue"
import http from '@/js/httpProxy.js'
import auth from '@/js/auth.js'
const emit = defineEmits(["close"]);

const props = defineProps({
    userId: String
})

const formRef = ref()

const rules = {
    employeeId: [{ required: true, message: '请输入工号', trigger: 'blur' }],
    realName: [
        { required: true, message: '请输入姓名', trigger: 'blur' },
        { max: 50, message: '姓名长度不能超过 50 个字符', trigger: 'blur' }
    ]
}

const user = ref({
    id: "",
    employeeId: "",
    realName: "",
    avatar: "",
    phoneNumber: "",
    email: "",
    idNumber: "",
    deptId: "",
    status: "using",
    tenantCode: ""
})

const deptTree = ref([])
const treeProps = { label: 'deptName', children: 'children' }

// 部门下拉提示中的租户名：超管取所选租户，普通用户取当前登录租户
const selectedTenantName = computed(() => {
    if (isSuperAdmin.value) {
        const t = tenantList.value.find(i => i.tenantCode === user.value.tenantCode)
        return t ? t.tenantName : ''
    }
    return auth.getTenantName()
})

// 租户与部门关系提示：部门归属租户，用户所属部门必须与用户租户一致
const deptTip = computed(() => {
    const name = selectedTenantName.value
    if (!name) {
        return isSuperAdmin.value
            ? '请先选择租户，部门下拉将展示该租户下的全部部门'
            : '部门下拉仅展示当前租户下的全部部门'
    }
    return isSuperAdmin.value
        ? `部门下拉展示「${name}」租户下的全部部门`
        : `部门下拉展示当前租户「${name}」下的全部部门`
})

const isSuperAdmin = ref(auth.isSuperAdmin())
const tenantList = ref([])

const userStatusList = [
    {
        value: 'using',
        label: '使用中',
    },
    {
        value: 'disabled',
        label: '已停用',
    },
]

onMounted(() => {
    loadDeptTree()
    if (isSuperAdmin.value) {
        loadTenantList()
    }
    if (props.userId) {
        getUserInfo()
    }
})

function loadDeptTree(tenantCode) {
    if (isSuperAdmin.value && tenantCode) {
        http.get('/dept/treeByTenantCode', { params: { tenantCode } }).then(({ data }) => {
            if (data.code === 200) deptTree.value = data.data || []
        })
    } else if (!isSuperAdmin.value) {
        http.get('/dept/tree').then(({ data }) => {
            if (data.code === 200) deptTree.value = data.data || []
        })
    } else {
        // 超管新增用户且未选租户：无可展示部门
        deptTree.value = []
    }
}

/** 超管切换租户：清空已选部门并重新加载该租户部门树 */
function onTenantChange() {
    user.value.deptId = ''
    loadDeptTree(user.value.tenantCode || undefined)
}

function loadTenantList() {
    http.get('/tenant/queryAll').then(({ data }) => {
        if (data.code === 200) {
            tenantList.value = data.data || []
        }
    })
}

function getUserInfo() {
    http.get("/user/getById", {
        params: {
            id: props.userId
        }
    }).then(({ data }) => {
        user.value = data.data
        // 部门下拉按用户租户加载：编辑回显用户租户下部门
        loadDeptTree(user.value.tenantCode || undefined)
    });
}

function saveUser() {
    formRef.value?.validate((valid) => {
        if (!valid) return
        http.post("/user/save", user.value)
            .then(({ data }) => {
                if (data.code === 200) {
                    // 新增用户时服务端返回一次性初始密码，需转告用户（首次登录须改密）
                    if (data.data) {
                        ElMessageBox.alert(
                            `初始密码：${data.data}\n\n请将初始密码转告该用户，用户首次登录后必须修改密码。`,
                            '用户创建成功', {
                            confirmButtonText: '我已记录',
                            dangerouslyUseHTMLString: false,
                            closeOnClickModal: false,
                            showClose: false
                        }).finally(() => emit('close'))
                        return
                    }
                    ElMessage.success('保存成功')
                    emit('close')
                }
            });
    })
}

</script>