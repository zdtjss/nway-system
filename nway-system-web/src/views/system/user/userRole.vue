<template>
    <div style="width: 80%;margin: 10px auto;">
        <el-select-v2 v-model="roleIds" :options="roleList" multiple filterable :props="{  label: 'roleName', value:  'id' }"/>
    </div>
    <div class="dialog-footer" style="right: 10px;float: right;position: absolute;bottom: 10px;">
        <el-button type="primary" @click="save()">
            保存
        </el-button>
    </div>
</template>

<script setup>
import { ref, onMounted } from "vue"
import http from '@/js/httpProxy.js'

const emit = defineEmits(["close"]);

const props = defineProps({
    employeeId: String
})

const roleIds = ref([])
const roleList = ref([])

onMounted(() => {
    queryUserRole(props.employeeId)
    queryRole()
})

function queryRole() {
    // 查询该用户同租户下可分配的角色（后端按用户租户过滤）
    http.get("/role/queryAvailableForUser", { params: { employeeId: props.employeeId } })
        .then(({ data }) => {
            roleList.value = data.data || []
        });
}

function queryUserRole(employeeId) {
    http.get("/userRole/queryByUser", { params: { userId: employeeId } })
        .then(({ data }) => {
            roleIds.value = data.data.map(userRole => userRole.roleId)
        });
}

function save() {
    http.post("/userRole/save", { employeeId: props.employeeId, roleIds: roleIds.value })
        .then(({ data }) => {
            if (data.code == 200) {
                emit('close')
            }
        });
}

</script>
