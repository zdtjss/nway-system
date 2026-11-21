<template>
    <div class="not-found">
        <div class="not-found-card">
            <div class="not-found-code">404</div>
            <div class="not-found-title">页面不存在或没有访问权限</div>
            <div class="not-found-desc">
                你访问的地址不在当前平台的页面范围内，或需要先开通对应菜单权限。
                <br />
                如需访问，请联系管理员分配菜单/角色后，从左侧导航进入。
            </div>
            <div class="not-found-path">当前地址：{{ currentPath }}</div>
            <el-button type="primary" size="large" @click="goHome">返回工作台</el-button>
        </div>
    </div>
</template>

<script setup>
import { computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'

// D12：旧版直接访问页面 URL（如 /views/system/user/userList）曾被 catch-all 静默重定向回首页，
// 用户无法区分「无权限」与「页面不存在」。改为显式空态提示，并保留返回工作台入口。
const route = useRoute()
const router = useRouter()
const currentPath = computed(() => route.fullPath)

function goHome() {
    router.replace('/main')
}
</script>

<style scoped>
.not-found {
    min-height: 100vh;
    display: flex;
    align-items: center;
    justify-content: center;
    background: #f5f7fa;
}

.not-found-card {
    width: 460px;
    padding: 48px 40px;
    border-radius: 12px;
    background: #fff;
    box-shadow: 0 6px 24px rgba(0, 0, 0, 0.06);
    text-align: center;
}

.not-found-code {
    font-size: 64px;
    font-weight: 600;
    line-height: 1;
    color: #409eff;
}

.not-found-title {
    margin-top: 16px;
    font-size: 18px;
    font-weight: 600;
    color: #303133;
}

.not-found-desc {
    margin-top: 12px;
    font-size: 13px;
    line-height: 1.8;
    color: #909399;
}

.not-found-path {
    margin: 16px 0 24px;
    font-size: 12px;
    color: #b1b3b8;
    word-break: break-all;
}
</style>
