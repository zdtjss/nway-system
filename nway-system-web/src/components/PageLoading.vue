<template>
    <div class="page-loading">
        <div class="loading-spinner">
            <div class="spinner-ring"></div>
            <div class="spinner-ring"></div>
            <div class="spinner-ring"></div>
        </div>
        <div class="loading-text">正在加载</div>
        <div class="loading-dots">
            <span class="dot"></span>
            <span class="dot"></span>
            <span class="dot"></span>
        </div>
    </div>
</template>

<script setup>
</script>

<style scoped>
.page-loading {
    /* 内容区本身为全屏（工具栏浮动覆盖），loading 需铺满整个视口，避免底部留白 */
    position: fixed;
    inset: 0;
    display: flex;
    flex-direction: column;
    align-items: center;
    justify-content: center;
    background: linear-gradient(135deg, #f5f7fa 0%, #e8ecf1 100%);
    user-select: none;
}

.loading-spinner {
    position: relative;
    width: 64px;
    height: 64px;
    margin-bottom: 24px;
}

.spinner-ring {
    position: absolute;
    inset: 0;
    border-radius: 50%;
    border: 3px solid transparent;
}

.spinner-ring:nth-child(1) {
    border-top-color: #409eff;
    animation: spin 1.2s cubic-bezier(0.5, 0, 0.5, 1) infinite;
}

.spinner-ring:nth-child(2) {
    inset: 6px;
    border-right-color: #67c23a;
    animation: spin 1.6s cubic-bezier(0.5, 0, 0.5, 1) infinite reverse;
}

.spinner-ring:nth-child(3) {
    inset: 12px;
    border-bottom-color: #e6a23c;
    animation: spin 2s cubic-bezier(0.5, 0, 0.5, 1) infinite;
}

.loading-text {
    font-size: 16px;
    font-weight: 500;
    color: #303133;
    letter-spacing: 2px;
    margin-bottom: 8px;
}

.loading-dots {
    display: flex;
    gap: 6px;
}

.dot {
    width: 6px;
    height: 6px;
    border-radius: 50%;
    background-color: #409eff;
    animation: bounce 1.4s ease-in-out infinite;
}

.dot:nth-child(2) {
    animation-delay: 0.2s;
}

.dot:nth-child(3) {
    animation-delay: 0.4s;
}

@keyframes spin {
    from {
        transform: rotate(0deg);
    }
    to {
        transform: rotate(360deg);
    }
}

@keyframes bounce {
    0%, 80%, 100% {
        opacity: 0.3;
        transform: scale(0.8);
    }
    40% {
        opacity: 1;
        transform: scale(1.2);
    }
}
</style>
