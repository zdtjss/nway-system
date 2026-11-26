import { computed, nextTick, onBeforeUnmount, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { createSpeechStream, speak, stopSpeak, toSpeakableText, ttsSupported } from '@/utils/tts'

const SpeechRecognitionClass = window.SpeechRecognition || window.webkitSpeechRecognition || null
const speechRecognitionSupported = !!SpeechRecognitionClass

const VOICE_ERRORS = {
    'not-allowed': '麦克风权限被拒绝，请在浏览器设置中允许后重试',
    'service-not-allowed': '当前环境未授权语音识别服务',
    'audio-capture': '未检测到可用的麦克风',
    network: '语音识别服务连接失败，请检查网络后重试'
}

const AUTO_SPEAK_KEY = 'nway-ai-auto-speak'

function readAutoSpeak() {
    try {
        return localStorage.getItem(AUTO_SPEAK_KEY) === '1'
    } catch (e) {
        return false
    }
}

/**
 * AI 输入区语音输入与回复语音播报。
 * - 语音识别结果实时写入 input；
 * - 单条回复可手动播报；
 * - enableAutoSpeak 时支持流式自动播报（完整对话页）。
 */
export function useAiVoice({ input, onDictation, enableAutoSpeak = false }) {
    const voiceActive = ref(false)
    const speakingId = ref(null)
    const autoSpeak = ref(enableAutoSpeak ? readAutoSpeak() : false)

    let recognition = null
    let voiceBaseText = ''
    let voiceManualStop = false

    const voiceTitle = computed(() => {
        if (!speechRecognitionSupported) return '当前浏览器不支持语音输入，建议使用 Chrome 或 Edge'
        return voiceActive.value ? '停止语音输入' : '语音输入'
    })

    const autoSpeakTips = computed(() => {
        if (!ttsSupported) return '当前浏览器不支持语音播报'
        return autoSpeak.value ? '关闭回复自动播报' : '开启回复自动播报'
    })

    function toggleAutoSpeak() {
        autoSpeak.value = !autoSpeak.value
        try {
            if (autoSpeak.value) localStorage.setItem(AUTO_SPEAK_KEY, '1')
            else localStorage.removeItem(AUTO_SPEAK_KEY)
        } catch (e) {
            // 本地存储不可用时仅在当前会话生效
        }
        if (!autoSpeak.value) stopAiSpeaking()
    }

    function toggleAiVoice() {
        if (voiceActive.value) stopAiVoice()
        else startAiVoice()
    }

    /** 语音识别结果实时写入输入框：最终结果累加，临时结果只做展示。 */
    function startAiVoice() {
        if (!SpeechRecognitionClass || voiceActive.value) return
        stopAiSpeaking()
        const instance = new SpeechRecognitionClass()
        instance.lang = 'zh-CN'
        instance.continuous = true
        instance.interimResults = true
        voiceBaseText = input.value
        voiceManualStop = false
        instance.onresult = (event) => {
            let finalText = ''
            let interimText = ''
            for (let i = event.resultIndex; i < event.results.length; i += 1) {
                const result = event.results[i]
                const transcript = result?.[0]?.transcript || ''
                if (!transcript) continue
                if (result.isFinal) finalText += transcript
                else interimText += transcript
            }
            if (finalText) voiceBaseText = appendAiDictation(voiceBaseText, finalText)
            input.value = voiceBaseText + interimText
            nextTick(() => onDictation?.())
        }
        instance.onerror = (event) => {
            if (event.error === 'aborted') return
            voiceManualStop = true
            voiceActive.value = false
            recognition = null
            const message = VOICE_ERRORS[event.error]
            if (message) ElMessage.warning(message)
        }
        instance.onend = () => {
            if (voiceManualStop || !voiceActive.value) {
                voiceActive.value = false
                return
            }
            // 浏览器在静音后会自动结束识别，未手动停止时继续监听
            voiceBaseText = input.value
            try {
                instance.start()
            } catch (e) {
                voiceActive.value = false
            }
        }
        try {
            instance.start()
            recognition = instance
            voiceActive.value = true
        } catch (e) {
            ElMessage.warning('语音输入启动失败，请稍后重试')
        }
    }

    function stopAiVoice() {
        voiceManualStop = true
        voiceActive.value = false
        recognition?.stop()
        recognition = null
    }

    /** 中英文混说时在英文单词之间补空格，中文直接拼接。 */
    function appendAiDictation(base, text) {
        if (!base) return text
        return /[A-Za-z0-9]$/.test(base) && /^[A-Za-z0-9]/.test(text) ? `${base} ${text}` : base + text
    }

    // ---------- 流式自动播报（完整对话页） ----------
    let speechStream = null
    let speechTarget = null
    let speechEnded = false

    function startAutoSpeech(message) {
        if (!enableAutoSpeak || !ttsSupported || !autoSpeak.value) return
        cancelAutoSpeech()
        speechTarget = message
        speechEnded = false
        speakingId.value = message.id
        speechStream = createSpeechStream({
            onEnd: () => clearAutoSpeech(message),
            onError: () => clearAutoSpeech(message)
        })
    }

    function pushAutoSpeech(message, delta) {
        if (speechTarget !== message) return
        speechStream?.push(delta)
    }

    /** 本轮输出结束：把剩余文本补读完，不打断正在播报的队列 */
    function finishAutoSpeech(message) {
        if (speechTarget !== message) return
        speechEnded = true
        speechStream?.end()
    }

    /** 立即停止自动播报；传 message 时只处理绑定在该消息上的会话 */
    function cancelAutoSpeech(message) {
        if (message && speechTarget !== message) return
        const stream = speechStream
        speechStream = null
        speechTarget = null
        speechEnded = false
        stream?.cancel()
        if (!message || speakingId.value === message.id) speakingId.value = null
    }

    /** 流意外结束时清理未完成的自动播报 */
    function cancelPendingAutoSpeech(message) {
        if (speechTarget === message && !speechEnded) cancelAutoSpeech(message)
    }

    function clearAutoSpeech(message) {
        if (speechTarget !== message) return
        speechStream = null
        speechTarget = null
        speechEnded = false
        if (speakingId.value === message.id) speakingId.value = null
    }

    function isAutoSpeechTarget(message) {
        return speechTarget === message
    }

    // ---------- 手动播报 ----------
    function speakAiMessage(message) {
        if (!ttsSupported || !message.text) return
        if (speakingId.value === message.id) {
            stopAiSpeaking()
            return
        }
        const text = toSpeakableText(message.text)
        if (!text) return
        stopAiSpeaking()
        const id = message.id
        speakingId.value = id
        const clear = () => {
            if (speakingId.value === id) speakingId.value = null
        }
        speak(text, { onEnd: clear, onError: clear })
    }

    function stopAiSpeaking() {
        cancelAutoSpeech()
        speakingId.value = null
        stopSpeak()
    }

    onBeforeUnmount(() => {
        stopAiVoice()
        stopAiSpeaking()
    })

    return {
        voiceActive,
        voiceTitle,
        speechRecognitionSupported,
        speakingId,
        ttsSupported,
        toggleAiVoice,
        stopAiVoice,
        speakAiMessage,
        stopAiSpeaking,
        autoSpeak,
        autoSpeakTips,
        toggleAutoSpeak,
        startAutoSpeech,
        pushAutoSpeech,
        finishAutoSpeech,
        cancelAutoSpeech,
        cancelPendingAutoSpeech,
        isAutoSpeechTarget
    }
}
