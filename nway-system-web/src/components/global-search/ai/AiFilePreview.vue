<template>
    <!-- 附件页内预览：图片走全屏看图（同完整对话页），PDF/文本用半透明无标题栏浮层 -->
    <Teleport to="body">
        <el-image-viewer v-if="preview.open && preview.type === 'image'" :url-list="[preview.url]"
            :z-index="100000003" @close="emit('close')" />
        <transition name="gs-preview">
            <div v-if="preview.open && preview.type !== 'image'" class="gs-preview" @click.self="emit('close')">
                <button type="button" class="gs-preview-close" title="关闭" @click="emit('close')">
                    <el-icon><Close /></el-icon>
                </button>
                <div v-if="preview.type === 'pdf' || preview.type === 'text'" v-loading="preview.loading"
                    class="gs-preview-paper">
                    <iframe v-if="preview.type === 'pdf'" :src="preview.url" title="PDF 预览"></iframe>
                    <pre v-else>{{ preview.text }}</pre>
                </div>
                <div v-else class="gs-preview-tip">
                    <el-icon><Document /></el-icon>
                    <p>该文件类型暂不支持页内预览</p>
                </div>
            </div>
        </transition>
    </Teleport>
</template>

<script setup>
import { Close, Document } from '@element-plus/icons-vue'

defineProps({
    preview: { type: Object, required: true }
})

const emit = defineEmits(['close'])
</script>

<style scoped>
/* 附件页内预览：半透明无标题栏浮层 */
.gs-preview {
    position: fixed;
    inset: 0;
    z-index: 100000002;
    box-sizing: border-box;
    display: flex;
    align-items: center;
    justify-content: center;
    padding: 5vh 5vw;
    background: rgba(15, 23, 42, 0.55);
    backdrop-filter: blur(2px);
}

.gs-preview-close {
    position: absolute;
    top: 20px;
    right: 24px;
    display: flex;
    align-items: center;
    justify-content: center;
    width: 36px;
    height: 36px;
    padding: 0;
    border: none;
    border-radius: 50%;
    background: rgba(255, 255, 255, 0.14);
    color: #fff;
    font-size: 18px;
    cursor: pointer;
    transition: background-color 0.16s;
}

.gs-preview-close:hover {
    background: rgba(255, 255, 255, 0.28);
}

/* PDF / 文本内容：浮在遮罩上的白纸，无标题栏 */
.gs-preview-paper {
    display: flex;
    box-sizing: border-box;
    width: min(1000px, 100%);
    height: 100%;
    background: #fff;
    border-radius: 10px;
    overflow: hidden;
    box-shadow: 0 30px 80px -30px rgba(0, 0, 0, 0.6);
}

.gs-preview-paper iframe {
    width: 100%;
    height: 100%;
    border: 0;
    background: #fff;
}

.gs-preview-paper pre {
    width: 100%;
    height: 100%;
    margin: 0;
    padding: 20px 22px;
    box-sizing: border-box;
    overflow: auto;
    color: #33405c;
    font-size: 12.5px;
    line-height: 1.7;
    white-space: pre-wrap;
    word-break: break-word;
}

.gs-preview-tip {
    display: flex;
    flex-direction: column;
    align-items: center;
    gap: 8px;
    color: rgba(255, 255, 255, 0.82);
    font-size: 13px;
}

.gs-preview-tip .el-icon {
    font-size: 30px;
    color: rgba(255, 255, 255, 0.5);
}

.gs-preview-tip p {
    margin: 0;
}

.gs-preview-enter-active,
.gs-preview-leave-active {
    transition: opacity 0.18s ease;
}

.gs-preview-enter-from,
.gs-preview-leave-to {
    opacity: 0;
}
</style>
