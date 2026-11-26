<template>
    <div class="gs-ai-msg" :class="msg.role">
        <div class="gs-ai-bubble" :class="{ 'is-error': !!msg.error, 'has-view': hasView || hasTable }">
            <template v-if="msg.role === 'assistant'">
                <div v-if="msg.thinking" class="gs-ai-thinking" :class="{ 'is-open': thinkingOpen.has(msg.id) }">
                    <button type="button" class="gs-ai-thinking-head" @click="emit('toggle-thinking', msg.id)">
                        <el-icon class="gs-ai-thinking-arrow" :class="{ 'is-open': thinkingOpen.has(msg.id) }">
                            <ArrowRight />
                        </el-icon>
                        <span>思考过程</span>
                        <span v-if="msg.streaming && !msg.text" class="gs-ai-thinking-live">思考中…</span>
                    </button>
                    <pre v-show="thinkingOpen.has(msg.id)" class="gs-ai-thinking-text" @scroll="emit('scroll-follow', $event)">{{ msg.thinking }}</pre>
                </div>
                <template v-for="tool in msg.tools || []" :key="tool.id">
                    <div v-if="tool.view" class="gs-ai-tool"
                        :class="{ 'is-open': !toolCollapsed.has(tool.id), 'is-failed': tool.view.success === false }">
                        <button type="button" class="gs-ai-tool-head" @click="emit('toggle-tool', tool.id)">
                            <el-icon class="gs-ai-tool-arrow" :class="{ 'is-open': !toolCollapsed.has(tool.id) }">
                                <ArrowRight />
                            </el-icon>
                            <span class="gs-ai-tool-name">
                                {{ tool.view.interfaceName || '接口调用结果' }}
                            </span>
                            <span v-if="tool.view.success === false" class="gs-ai-tool-flag">调用失败</span>
                        </button>
                        <div v-show="!toolCollapsed.has(tool.id)" class="gs-ai-tool-body">
                            <div v-if="tool.view.message" class="gs-ai-tool-msg" :class="{ 'is-error': tool.view.success === false }">
                                {{ tool.view.message }}
                            </div>
                            <AiViewBlock v-if="tool.view.success !== false" :view="tool.view" compact />
                        </div>
                    </div>
                </template>
                <template v-for="(segment, index) in msg.segments" :key="index">
                    <AiMarkdown v-if="segment.type === 'text' && segment.text" class="gs-ai-text" :text="segment.text" :streaming="msg.streaming" />
                    <AiViewBlock v-else-if="segment.type === 'view' && segment.view" class="gs-ai-view" :view="segment.view" compact />
                    <div v-else-if="segment.type === 'pending'" class="gs-ai-pending">正在整理数据…</div>
                </template>
                <div v-if="msg.streaming && !msg.text" class="gs-ai-typing">
                    <span></span><span></span><span></span>
                </div>
                <div v-if="msg.toolTip" class="gs-ai-tool-tip">{{ msg.toolTip }}</div>
                <div v-if="msg.error" class="gs-ai-error">{{ msg.error }}</div>
                <div v-if="msg.truncated" class="gs-ai-truncated">
                    回答已达模型单次输出上限，内容可能不完整，可在完整对话中回复「继续」。
                </div>
                <button v-if="msg.needFullChat" type="button" class="gs-ai-jump" @click="emit('open-full-chat')">
                    打开完整对话继续
                    <el-icon><TopRight /></el-icon>
                </button>
                <button v-if="ttsSupported && msg.text && !msg.streaming" type="button"
                    class="gs-ai-speak" :class="{ 'is-active': speakingId === msg.id }"
                    :title="speakingId === msg.id ? '停止播报' : '播报这条回复'"
                    @click="emit('speak', msg)">
                    <el-icon>
                        <Mute v-if="speakingId === msg.id" />
                        <Headset v-else />
                    </el-icon>
                    <span>{{ speakingId === msg.id ? '停止播报' : '语音播报' }}</span>
                </button>
            </template>
            <template v-else>
                <div v-if="msg.text">{{ msg.text }}</div>
                <div v-if="msg.attachments && msg.attachments.length" class="gs-ai-msg-files">
                    <div v-for="file in msg.attachments" :key="file.fileId || file.localId"
                        class="gs-ai-msg-file" :class="{ 'is-link': !!file.previewUrl }"
                        :title="file.size ? `${file.name} · ${formatFileSize(file.size)}` : file.name"
                        @click="emit('preview-file', file)">
                        <el-icon><Document /></el-icon>
                        <span class="gs-ai-msg-file-name">{{ file.name }}</span>
                        <span v-if="file.size" class="gs-ai-msg-file-size">{{ formatFileSize(file.size) }}</span>
                    </div>
                </div>
            </template>
        </div>
    </div>
</template>

<script setup>
import { computed } from 'vue'
import { ArrowRight, Document, Headset, Mute, TopRight } from '@element-plus/icons-vue'
import AiMarkdown from '@/components/ai/AiMarkdown.vue'
import AiViewBlock from '@/components/ai/AiViewBlock.vue'
import { formatFileSize } from '@/utils/fileSize'

const props = defineProps({
    msg: { type: Object, required: true },
    thinkingOpen: { type: Object, default: () => new Set() },
    toolCollapsed: { type: Object, default: () => new Set() },
    speakingId: { type: String, default: null },
    ttsSupported: { type: Boolean, default: false }
})

const emit = defineEmits(['toggle-thinking', 'toggle-tool', 'speak', 'open-full-chat', 'preview-file', 'scroll-follow'])

// 消息中是否包含结构化视图，面板内视图块需要占满气泡宽度
const hasView = computed(() => {
    const hasSegmentView = Array.isArray(props.msg.segments)
        && props.msg.segments.some((segment) => segment.type === 'view')
    const hasToolView = Array.isArray(props.msg.tools) && props.msg.tools.some((tool) => !!tool.view)
    return hasSegmentView || hasToolView
})

// 正文包含 Markdown 表格时同样占满气泡宽度，避免宽表格被压缩
const hasTable = computed(() => Array.isArray(props.msg.segments) && props.msg.segments.some(
    (segment) => segment.type === 'text' && !!segment.text && /^\s*\|.*\|\s*$/m.test(segment.text)
))
</script>

<style scoped>
.gs-ai-msg {
    display: flex;
    margin-bottom: 14px;
    animation: gsAiMsgIn 0.22s ease-out both;
}

@keyframes gsAiMsgIn {
    from {
        opacity: 0;
        transform: translateY(6px);
    }

    to {
        opacity: 1;
        transform: translateY(0);
    }
}

.gs-ai-msg:last-child {
    margin-bottom: 0;
}

.gs-ai-msg.user {
    justify-content: flex-end;
}

.gs-ai-bubble {
    box-sizing: border-box;
    padding: 7px 11px;
    border-radius: 14px;
    font-size: 13.5px;
    line-height: 1.68;
    white-space: pre-wrap;
    word-break: break-word;
}

.gs-ai-msg.user .gs-ai-bubble {
    max-width: 86%;
    padding: 9px 14px;
    border-radius: 16px 16px 6px 16px;
    background: linear-gradient(135deg, #eef3ff 0%, #f1ecff 100%);
    font-size: 13.5px;
    line-height: 1.6;
    color: #26324d;
    border: 1px solid rgba(79, 124, 255, 0.14);
    box-shadow: 0 1px 2px rgba(16, 24, 48, 0.03), 0 8px 20px -22px rgba(16, 24, 48, 0.18);
}

.gs-ai-msg.assistant .gs-ai-bubble {
    max-width: 100%;
    padding: 11px 13px 12px;
    background: #fff;
    color: #2f3542;
    border: 1px solid rgba(228, 233, 244, 0.95);
    border-radius: 16px 16px 16px 6px;
    box-shadow: 0 1px 2px rgba(16, 24, 48, 0.03), 0 12px 28px -22px rgba(16, 24, 48, 0.22);
    /* 助手气泡由块级元素拼装，需取消 pre-wrap，否则模板换行会撑出空行 */
    white-space: normal;
}

.gs-ai-bubble.is-error {
    border-color: #f3d5d5;
}

/* 含结构化视图的回复占满面板宽度，避免表格被气泡收缩 */
.gs-ai-bubble.has-view {
    width: 100%;
    max-width: 100%;
}

.gs-ai-text {
    white-space: normal;
    overflow-x: auto;
    word-break: break-word;
}

/* 回复正文的 Markdown 样式 */
.gs-ai-text :deep(p) {
    margin: 0 0 8px;
}

.gs-ai-text :deep(p:last-child) {
    margin-bottom: 0;
}

.gs-ai-text :deep(ul),
.gs-ai-text :deep(ol) {
    margin: 4px 0 6px;
    padding-left: 18px;
}

.gs-ai-text :deep(li) {
    margin: 2px 0;
}

.gs-ai-text :deep(h1),
.gs-ai-text :deep(h2),
.gs-ai-text :deep(h3),
.gs-ai-text :deep(h4),
.gs-ai-text :deep(h5),
.gs-ai-text :deep(h6) {
    margin: 10px 0 5px;
    font-weight: 600;
    line-height: 1.4;
}

.gs-ai-text :deep(h1) {
    font-size: 16px;
}

.gs-ai-text :deep(h2) {
    font-size: 15px;
}

.gs-ai-text :deep(h3),
.gs-ai-text :deep(h4),
.gs-ai-text :deep(h5),
.gs-ai-text :deep(h6) {
    font-size: 14px;
}

.gs-ai-text :deep(code) {
    padding: 1px 4px;
    border-radius: 4px;
    background: #f2f4f8;
    font-size: 12px;
    font-family: Consolas, Monaco, monospace;
}

.gs-ai-text :deep(pre) {
    margin: 8px 0;
    padding: 10px 12px;
    background: #f7f9fd;
    border: 1px solid #edf1f8;
    border-radius: 10px;
    overflow-x: auto;
}

.gs-ai-text :deep(pre code) {
    padding: 0;
    background: transparent;
}

.gs-ai-text :deep(blockquote) {
    margin: 6px 0;
    padding: 2px 10px;
    border-left: 3px solid #dbe3f0;
    color: #6b7280;
}

.gs-ai-text :deep(table) {
    width: 100%;
    margin: 8px 0;
    border: 1px solid #e8edf7;
    border-collapse: separate;
    border-spacing: 0;
    border-radius: 10px;
    overflow: hidden;
    background: #fff;
    font-size: 12.5px;
}

.gs-ai-text :deep(th),
.gs-ai-text :deep(td) {
    padding: 7px 10px;
    border: none;
    border-bottom: 1px solid #eff2f8;
    text-align: left;
}

.gs-ai-text :deep(tr:last-child td) {
    border-bottom: none;
}

.gs-ai-text :deep(th) {
    background: #f7f9fd;
    color: #5b6579;
    font-weight: 600;
}

.gs-ai-text :deep(tbody tr:nth-child(even) td) {
    background: #fbfcfe;
}

.gs-ai-text :deep(a) {
    color: #2f6bff;
    text-decoration: none;
}

.gs-ai-text :deep(img) {
    max-width: 100%;
}

.gs-ai-text :deep(hr) {
    margin: 8px 0;
    border: none;
    border-top: 1px solid #edf1f8;
}

.gs-ai-text :deep(.mermaid-block) {
    margin: 6px 0;
    padding: 8px;
    background: #fff;
    border: 1px solid #edf1f8;
    border-radius: 8px;
    overflow-x: auto;
    text-align: center;
}

.gs-ai-text :deep(.mermaid-block.mermaid-error) {
    border-color: #f3d5d5;
}

.gs-ai-text :deep(.mermaid-block svg) {
    max-width: 100%;
    height: auto;
}

.gs-ai-text :deep(.mermaid-block .mermaid-source) {
    margin: 0;
    padding: 0;
    border: none;
    background: transparent;
    text-align: left;
}

.gs-ai-view {
    margin-top: 6px;
}

.gs-ai-pending {
    margin-top: 6px;
    color: #8b95a7;
    font-size: 12px;
}

/* 思考过程：收起时是一条带左侧强调线的轻量标签，展开后成为浅色卡片 */
.gs-ai-thinking {
    margin-bottom: 7px;
    padding: 2px 0 2px 10px;
    border-left: 2px solid #e1e8f8;
    border-radius: 0;
    transition: border-color 0.16s, background-color 0.16s;
}

.gs-ai-thinking.is-open {
    padding: 8px 10px 9px 11px;
    border: 1px solid #e9edf9;
    border-left: 2px solid #c3d1ff;
    border-radius: 11px;
    background: linear-gradient(180deg, #f7f9ff 0%, #f4f6fd 100%);
}

.gs-ai-thinking-head {
    display: flex;
    align-items: center;
    gap: 6px;
    width: 100%;
    padding: 0;
    border: none;
    background: transparent;
    color: #8b95a7;
    font-size: 11.5px;
    font-weight: 500;
    font-family: inherit;
    cursor: pointer;
    transition: color 0.15s;
}

.gs-ai-thinking-head:hover {
    color: #40527f;
}

.gs-ai-thinking-arrow {
    font-size: 11px;
    transition: transform 0.15s;
}

.gs-ai-thinking-arrow.is-open {
    transform: rotate(90deg);
}

.gs-ai-thinking-live {
    margin-left: auto;
    color: #4f7cff;
}

.gs-ai-thinking-text {
    margin: 8px 0 0;
    max-height: 135px;
    overflow-y: auto;
    white-space: pre-wrap;
    word-break: break-word;
    font-size: 11.5px;
    line-height: 1.7;
    color: #6b7488;
    font-family: inherit;
}

/* 面板内的接口调用结果：紧凑分组，避免与正文混排 */
.gs-ai-tool {
    min-width: 0;
    margin-bottom: 9px;
    padding: 8px 10px;
    background: linear-gradient(180deg, #f8fafe 0%, #f5f8fd 100%);
    border: 1px solid #e9eef9;
    border-radius: 11px;
    transition: border-color 0.16s, background-color 0.16s;
}

.gs-ai-tool.is-failed {
    border-color: #f6dada;
    background: #fffafa;
}

.gs-ai-tool-head {
    display: flex;
    align-items: center;
    gap: 6px;
    width: 100%;
    padding: 0;
    border: 0;
    background: transparent;
    font-family: inherit;
    text-align: left;
    cursor: pointer;
}

.gs-ai-tool-arrow {
    flex: 0 0 auto;
    font-size: 11px;
    color: #97a1b6;
    transition: transform 0.15s;
}

.gs-ai-tool-arrow.is-open {
    transform: rotate(90deg);
}

.gs-ai-tool-name {
    min-width: 0;
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
    color: #33405a;
    font-size: 12.5px;
    font-weight: 600;
    line-height: 1.5;
    transition: color 0.15s;
}

.gs-ai-tool-head:hover .gs-ai-tool-name {
    color: #3b5bdb;
}

.gs-ai-tool-flag {
    flex: 0 0 auto;
    margin-left: auto;
    padding: 0 7px;
    border-radius: 999px;
    background: #fdecec;
    color: #d9483f;
    font-size: 10.5px;
    line-height: 17px;
}

.gs-ai-tool-body {
    margin-top: 6px;
}

.gs-ai-tool-msg {
    margin-top: 0;
    color: #6b7280;
    font-size: 11px;
    line-height: 1.6;
}

.gs-ai-tool-msg.is-error {
    color: #d9534f;
}

/* 用户消息里的附件：底部右对齐的紧凑胶囊 */
.gs-ai-msg-files {
    display: flex;
    flex-wrap: wrap;
    justify-content: flex-end;
    gap: 6px;
    margin-top: 8px;
}

.gs-ai-msg-files:first-child {
    margin-top: 0;
}

.gs-ai-msg-file {
    display: inline-flex;
    align-items: center;
    gap: 6px;
    max-width: 220px;
    height: 26px;
    padding: 0 9px;
    border-radius: 999px;
    background: rgba(255, 255, 255, 0.72);
    border: 1px solid rgba(79, 124, 255, 0.16);
    font-size: 12px;
    color: #3f4a63;
    transition: border-color 0.16s, background-color 0.16s, box-shadow 0.16s;
}

.gs-ai-msg-file.is-link {
    cursor: pointer;
}

.gs-ai-msg-file.is-link:hover {
    background: #fff;
    border-color: rgba(79, 124, 255, 0.4);
    box-shadow: 0 4px 12px -6px rgba(79, 124, 255, 0.45);
}

.gs-ai-msg-file .el-icon {
    flex: 0 0 auto;
    font-size: 13px;
    color: #4f7cff;
}

.gs-ai-msg-file-name {
    flex: 0 1 auto;
    min-width: 0;
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
}

.gs-ai-msg-file-size {
    flex: 0 0 auto;
    font-size: 11px;
    color: #9aa3b5;
}

/* 回复播报按钮 */
.gs-ai-speak {
    margin-top: 10px;
    height: 24px;
    padding: 0 10px;
    display: inline-flex;
    align-items: center;
    gap: 4px;
    border: 1px solid #e6ebf5;
    border-radius: 999px;
    background: #fafbfe;
    color: #8892a6;
    font-size: 11px;
    font-family: inherit;
    cursor: pointer;
    transition: border-color 0.15s, color 0.15s, background-color 0.15s;
}

.gs-ai-speak .el-icon {
    font-size: 12px;
}

.gs-ai-speak:hover,
.gs-ai-speak.is-active {
    border-color: #b9c8f2;
    background: #fff;
    color: #3b5bdb;
}

.gs-ai-typing {
    display: flex;
    align-items: center;
    gap: 4px;
    height: 18px;
}

.gs-ai-typing span {
    width: 5px;
    height: 5px;
    border-radius: 50%;
    background: #9aa7bd;
    animation: gsAiTyping 1.2s infinite ease-in-out;
}

.gs-ai-typing span:nth-child(2) {
    animation-delay: 0.15s;
}

.gs-ai-typing span:nth-child(3) {
    animation-delay: 0.3s;
}

@keyframes gsAiTyping {

    0%,
    60%,
    100% {
        opacity: 0.35;
        transform: translateY(0);
    }

    30% {
        opacity: 1;
        transform: translateY(-3px);
    }
}

.gs-ai-tool-tip {
    margin-top: 6px;
    display: flex;
    align-items: center;
    gap: 5px;
    font-size: 11px;
    color: #8b95a7;
}

.gs-ai-tool-tip::before {
    content: '';
    width: 5px;
    height: 5px;
    border-radius: 50%;
    background: #7aa7ff;
    animation: gsAiPulse 1s infinite ease-in-out;
}

@keyframes gsAiPulse {

    0%,
    100% {
        opacity: 0.4;
    }

    50% {
        opacity: 1;
    }
}

.gs-ai-error {
    margin-top: 6px;
    font-size: 12px;
    line-height: 1.5;
    color: #d9534f;
}

.gs-ai-truncated {
    margin-top: 6px;
    padding: 6px 9px;
    border: 1px solid #f7e2b7;
    border-radius: 8px;
    background: #fffaf0;
    font-size: 11.5px;
    line-height: 1.5;
    color: #a8730a;
}

.gs-ai-jump {
    margin-top: 8px;
    padding: 0;
    display: inline-flex;
    align-items: center;
    gap: 3px;
    border: none;
    background: transparent;
    color: #2f6bff;
    font-size: 12px;
    cursor: pointer;
}

.gs-ai-jump:hover {
    text-decoration: underline;
}

.gs-ai-thinking-text::-webkit-scrollbar {
    width: 6px;
}

.gs-ai-thinking-text::-webkit-scrollbar-thumb {
    background-color: rgba(148, 163, 184, 0.24);
    border-radius: 3px;
}

.gs-ai-thinking-text::-webkit-scrollbar-thumb:hover {
    background-color: rgba(148, 163, 184, 0.45);
}

.gs-ai-thinking-text::-webkit-scrollbar-track {
    background: transparent;
}
</style>
