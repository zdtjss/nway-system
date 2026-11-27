import { computed, onBeforeUnmount, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { getMessages, mergeAssistantMessages, streamChat, uploadChatFile } from '@/api/ai'
import { rememberInterfaceConfig, splitViewSegments } from '@/utils/aiView'
import {
    INVOKE_TOOL,
    buildToolViews,
    isConfigTool,
    isResultTool,
    restoreCompletedTools
} from '@/utils/aiTool'
import { buildPageTextSnapshot, capturePageScreenshot } from '@/utils/pageContext'
import { useCurrentPageStore } from '@/stores/menuStore'
import { AI_MAX_FILES } from '@/components/global-search/constants'

/**
 * AI 轻量对话的核心状态：消息、流式事件、历史消息恢复与完整对话交接。
 * 面板只负责布局与交互，发送/停止等业务逻辑集中在这里。
 */
export function useAiChatSession({ input, attachments, scrollToBottom, followTail }) {
    const currentPageStore = useCurrentPageStore()

    const aiMessages = ref([])
    const aiStreaming = ref(false)
    const aiMessagesLoading = ref(false)
    const aiConversationId = ref(null)

    // 页面上下文开关：默认不附带，按需开启
    const pageTextEnabled = ref(false)
    const pageShotEnabled = ref(false)

    const currentPageName = computed(() => {
        const url = currentPageStore.currentUrl
        const menu = currentPageStore.pageList?.find((item) => item.url === url)
        return menu?.name || currentPageStore.currentComp || currentPageStore.currentApp?.name || ''
    })

    const pageContextHint = computed(() => {
        if (!pageTextEnabled.value && !pageShotEnabled.value) return '仅发送输入内容'
        const parts = []
        if (pageTextEnabled.value) parts.push('文本')
        if (pageShotEnabled.value) parts.push('截图')
        const name = currentPageName.value
        return name ? `附带${parts.join(' + ')}：${name}` : `附带页面${parts.join(' + ')}`
    })

    function currentPageMeta() {
        return {
            title: currentPageName.value || document.title,
            url: currentPageStore.currentUrl || currentPageStore.currentApp?.host || '',
            renderType: currentPageStore.currentApp?.renderType || 'native'
        }
    }

    // 待确认的写操作：完整对话页接管后可直接弹出确认表单继续提交
    let aiPendingConfirm = null
    let aiAbort = null
    // 每轮流的序号：停止后立即发新消息时，旧请求的收尾不能覆盖新请求的状态
    let aiStreamSeq = 0
    let aiSeq = 0
    let aiMessageSeq = 0

    function aiUid(role) {
        aiSeq += 1
        return `gs-ai-${role}-${aiSeq}-${Date.now()}`
    }

    const aiCanSend = computed(() => {
        if (aiStreaming.value) return false
        const hasText = input.value.trim().length > 0
        const hasFiles = attachments.value.some((file) => !file.error && !file.uploading)
        const uploading = attachments.value.some((file) => file.uploading)
        return (hasText || hasFiles) && !uploading
    })

    // 思考过程默认收起，流式生成思考时自动展开
    const aiThinkingOpen = ref(new Set())

    function toggleAiThinking(id) {
        if (aiThinkingOpen.value.has(id)) aiThinkingOpen.value.delete(id)
        else aiThinkingOpen.value.add(id)
        aiThinkingOpen.value = new Set(aiThinkingOpen.value)
    }

    // 接口调用结果默认展开，点击标题收起
    const aiToolCollapsed = ref(new Set())

    function toggleAiTool(id) {
        if (aiToolCollapsed.value.has(id)) aiToolCollapsed.value.delete(id)
        else aiToolCollapsed.value.add(id)
        aiToolCollapsed.value = new Set(aiToolCollapsed.value)
    }

    // 正文变化后重新分段：已闭合的视图块渲染，流式期间的未闭合块显示占位
    function syncAiSegments(message) {
        message.segments = splitViewSegments(message.text, !!message.streaming)
    }

    // get_interface_config 的流式结果先暂存，结束后整体解析为接口字段定义
    const aiPendingConfig = new Map()

    function handleAiEvent(event, target) {
        switch (event.type) {
            case 'conversation':
                if (event.conversationId) aiConversationId.value = event.conversationId
                break
            case 'reply.start':
                if (event.replyId) target.id = event.replyId
                break
            case 'thinking.delta':
                target.thinking += event.delta || ''
                if (target.streaming && !aiThinkingOpen.value.has(target.id)) {
                    aiThinkingOpen.value = new Set(aiThinkingOpen.value).add(target.id)
                }
                followTail('.gs-ai-thinking-text')
                break
            case 'message.delta':
                target.text += event.delta || ''
                syncAiSegments(target)
                break
            case 'tool.call.start':
                target.toolTip = '正在调用：' + (event.name || '工具')
                if (isResultTool(event.name)) {
                    target.tools.push({
                        id: event.toolCallId || aiUid('tool'),
                        name: event.name,
                        status: 'running',
                        result: '',
                        view: null
                    })
                }
                break
            case 'tool.result.delta':
                if (isConfigTool(event.name)) {
                    const key = event.toolCallId || ''
                    aiPendingConfig.set(key, (aiPendingConfig.get(key) || '') + (event.delta || ''))
                } else if (isResultTool(event.name)) {
                    const tool = target.tools.find((item) => item.id === event.toolCallId)
                    if (tool) tool.result += event.delta || ''
                }
                break
            case 'tool.result.end':
                target.toolTip = ''
                if (isConfigTool(event.name)) {
                    const key = event.toolCallId || ''
                    rememberInterfaceConfig(aiPendingConfig.get(key) || '')
                    aiPendingConfig.delete(key)
                } else if (isResultTool(event.name)) {
                    const tool = target.tools.find((item) => item.id === event.toolCallId)
                    if (tool) {
                        tool.status = event.state === 'ERROR' || event.state === 'DENIED' ? 'error' : 'done'
                        const views = buildToolViews(tool.name, tool.result)
                        tool.views = views
                        tool.view = views[0] || null
                    }
                }
                break
            case 'confirm.required':
                target.streaming = false
                syncAiSegments(target)
                target.needFullChat = true
                target.error = '该操作需要你确认后才能提交，请到「完整对话」继续完成。'
                aiPendingConfirm = {
                    type: 'confirm.required',
                    replyId: event.replyId || '',
                    toolCalls: event.toolCalls || []
                }
                break
            case 'reply.end':
                target.streaming = false
                // 模型因单次输出上限结束时没有异常，只能靠结束原因识别
                if (event.truncated || /length|max_token/i.test(String(event.finishReason || ''))) {
                    target.truncated = true
                }
                // 思考过程只在流式期间自动展开，结束后收起，避免看起来模型仍在推理
                if (aiThinkingOpen.value.has(target.id)) {
                    aiThinkingOpen.value.delete(target.id)
                    aiThinkingOpen.value = new Set(aiThinkingOpen.value)
                }
                syncAiSegments(target)
                break
            case 'error':
                target.streaming = false
                if (aiThinkingOpen.value.has(target.id)) {
                    aiThinkingOpen.value.delete(target.id)
                    aiThinkingOpen.value = new Set(aiThinkingOpen.value)
                }
                syncAiSegments(target)
                target.error = event.message || 'AI 服务异常'
                break
            default:
                break
        }
        scrollToBottom()
    }

    async function sendAi() {
        const text = input.value.trim()
        const files = attachments.value.filter((file) => !file.error && !file.uploading)
        if ((!text && files.length === 0) || aiStreaming.value) return
        if (attachments.value.some((file) => file.uploading)) return
        // 新消息会重新发起一轮对话，之前待确认的操作不再保留
        aiPendingConfirm = null
        aiMessages.value.push({
            id: aiUid('user'),
            role: 'user',
            text,
            attachments: files.map((file) => ({ ...file }))
        })
        // 取响应式代理：截图上传成功后追加到该条用户消息的附件区
        const userMessage = aiMessages.value[aiMessages.value.length - 1]
        const target = reactive({
            id: aiUid('assistant'),
            role: 'assistant',
            text: '',
            thinking: '',
            segments: [],
            tools: [],
            error: '',
            toolTip: '',
            needFullChat: false,
            streaming: true
        })
        aiMessages.value.push(target)
        input.value = ''
        attachments.value = []
        scrollToBottom(true)
        aiStreaming.value = true
        const controller = new AbortController()
        aiAbort = controller
        const streamSeq = ++aiStreamSeq
        const pageMeta = currentPageMeta()
        const usePageText = pageTextEnabled.value
        const usePageShot = pageShotEnabled.value
        // 页面上下文为一次性附带：发送即取消选择，避免后续消息继续携带页面信息浪费 token
        pageTextEnabled.value = false
        pageShotEnabled.value = false
        const fileIds = files.map((file) => file.fileId)
        let pageContext = null
        if (usePageText) {
            try {
                pageContext = buildPageTextSnapshot(pageMeta)
            } catch (e) {
                console.warn('[page-context] 页面文本快照失败', e)
            }
        }
        try {
            if (usePageShot) {
                if (fileIds.length >= AI_MAX_FILES) {
                    ElMessage.warning(`附件已达 ${AI_MAX_FILES} 个，本次未附带页面截图`)
                } else {
                    target.toolTip = '正在截取当前页面…'
                    try {
                        const shot = await capturePageScreenshot(pageMeta)
                        if (shot) {
                            const res = await uploadChatFile(shot)
                            if (res?.data?.fileId) {
                                fileIds.push(res.data.fileId)
                                userMessage.attachments.push({
                                    localId: aiUid('file'),
                                    fileId: res.data.fileId,
                                    name: shot.name,
                                    size: shot.size,
                                    mime: shot.type
                                })
                            }
                        }
                    } catch (e) {
                        console.warn('[page-context] 页面截图失败', e)
                        ElMessage.warning('页面截图失败，本次未附带截图')
                    } finally {
                        target.toolTip = ''
                    }
                }
            }
            if (usePageText && !pageContext) {
                ElMessage.warning('当前页面内容无法读取，本次未附带页面文本')
            }
            // 截图/上传期间用户已点击停止：不再发起流式请求
            if (controller.signal.aborted) {
                if (!target.text) target.text = '已停止生成。'
                return
            }
            await streamChat({
                conversationId: aiConversationId.value,
                message: text,
                fileIds,
                pageContext
            }, (event) => handleAiEvent(event, target), controller.signal)
        } catch (e) {
            if (e?.name === 'AbortError') {
                if (!target.text) target.text = '已停止生成。'
            } else {
                target.error = e?.message || '请求失败，请稍后重试'
            }
        } finally {
            target.streaming = false
            target.toolTip = ''
            syncAiSegments(target)
            // 仅清理本轮流式状态：停止后立即发送的新消息不能被旧请求收尾覆盖
            if (streamSeq === aiStreamSeq) {
                aiStreaming.value = false
                aiAbort = null
            }
            scrollToBottom()
        }
    }

    function stopAi() {
        aiAbort?.abort()
        aiStreaming.value = false
        // 停止后不会再有工具结果事件，为执行中的调用补一条说明，避免留下空白卡片
        aiMessages.value.forEach((message) => {
            ;(message.tools || []).forEach((tool) => {
                if (isResultTool(tool.name) && !tool.view && !tool.result) {
                    tool.view = {
                        type: 'empty',
                        text: '本次调用未完成（等待确认或会话已中断），暂无返回结果。'
                    }
                }
            })
        })
    }

    async function openConversation(conv) {
        aiConversationId.value = conv.id
        aiMessages.value = []
        aiThinkingOpen.value = new Set()
        aiToolCollapsed.value = new Set()
        aiPendingConfig.clear()
        aiPendingConfirm = null
        const seq = ++aiMessageSeq
        aiMessagesLoading.value = true
        try {
            const res = await getMessages(conv.id)
            if (seq !== aiMessageSeq) return
            const list = mergeAssistantMessages(res.data || []).map(toPanelMessage)
            restoreCompletedTools(list)
            aiMessages.value = list
            scrollToBottom(true)
        } catch (e) {
            // http 拦截器已提示
        } finally {
            if (seq === aiMessageSeq) aiMessagesLoading.value = false
        }
    }

    // 历史消息块转面板消息结构：文本/思考/工具结果按现有气泡样式展示
    function toPanelMessage(message) {
        const ui = {
            id: message.id || aiUid('assistant'),
            role: message.role === 'user' ? 'user' : 'assistant',
            text: '',
            thinking: '',
            segments: [],
            tools: [],
            attachments: [],
            error: '',
            toolTip: '',
            needFullChat: false,
            streaming: false
        }
        const texts = []
        ;(message.blocks || []).forEach((block) => {
            if (!block) return
            if (block.type === 'text') {
                if (block.text) texts.push(block.text)
            } else if (block.type === 'thinking') {
                if (block.text) ui.thinking = ui.thinking ? `${ui.thinking}\n\n${block.text}` : block.text
            } else if (block.type === 'attachments') {
                // 历史附件：后端已按 fileId 重新生成预览地址
                ui.attachments.push(...(block.files || []))
            } else if (block.type === 'tool_use') {
                const name = block.name || INVOKE_TOOL
                if (isConfigTool(name)) {
                    rememberInterfaceConfig(block.result)
                    return
                }
                const result = block.result || ''
                const views = isResultTool(name) ? buildToolViews(name, result) : []
                let view = views[0] || null
                if (isResultTool(name) && !view) {
                    // 简易面板不展开原始报文：无结果说明调用未完成，有结果则提示去完整对话查看
                    view = {
                        type: 'empty',
                        text: result
                            ? '工具已返回结果，但内容不便在简易窗口展示，请在完整对话中查看。'
                            : '本次调用未完成（等待确认或会话已中断），暂无返回结果。'
                    }
                }
                ui.tools.push({
                    id: block.id || aiUid('tool'),
                    name,
                    // 无结果说明调用未完成，不能标成 done 交接给完整对话页
                    status: block.state === 'ERROR' || block.state === 'DENIED'
                        ? 'error'
                        : result ? 'done' : 'interrupted',
                    result,
                    view,
                    views
                })
            }
        })
        ui.text = texts.join('\n')
        ui.segments = splitViewSegments(ui.text, false)
        return ui
    }

    function newAiChat() {
        if (aiStreaming.value) stopAi()
        // 取消未完成的历史消息加载，避免回来后覆盖新对话
        aiMessageSeq += 1
        aiMessagesLoading.value = false
        aiMessages.value = []
        attachments.value = []
        aiThinkingOpen.value = new Set()
        aiToolCollapsed.value = new Set()
        aiConversationId.value = null
        aiPendingConfirm = null
        input.value = ''
        aiPendingConfig.clear()
    }

    // 轻量对话 → 完整对话：把会话、消息、未发送的输入与附件、待确认操作一并交接过去
    function buildHandoffPayload() {
        const hasContent = aiMessages.value.length > 0 || input.value.trim().length > 0
            || attachments.value.length > 0 || !!aiConversationId.value
        if (!hasContent) return null
        // 生成中的回复先停止，已生成的部分随消息一起带去完整对话页
        if (aiStreaming.value) stopAi()
        return {
            conversationId: aiConversationId.value,
            // 深拷贝脱离面板响应式状态，之后面板重置不会影响已交接的内容
            messages: JSON.parse(JSON.stringify(aiMessages.value)),
            input: input.value,
            attachments: attachments.value
                .filter((file) => file.fileId && !file.uploading && !file.error)
                .map((file) => ({ ...file })),
            confirm: aiPendingConfirm ? JSON.parse(JSON.stringify(aiPendingConfirm)) : null
        }
    }

    onBeforeUnmount(() => {
        aiAbort?.abort()
    })

    return {
        aiMessages,
        aiStreaming,
        aiMessagesLoading,
        aiConversationId,
        pageTextEnabled,
        pageShotEnabled,
        pageContextHint,
        aiCanSend,
        aiThinkingOpen,
        aiToolCollapsed,
        toggleAiThinking,
        toggleAiTool,
        sendAi,
        stopAi,
        openConversation,
        newAiChat,
        buildHandoffPayload
    }
}
