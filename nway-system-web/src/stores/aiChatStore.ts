import { defineStore } from 'pinia'

/** 轻量对话面板交接给完整对话页的数据 */
export interface AiChatHandoffPayload {
    conversationId: string | null
    messages: any[]
    input: string
    attachments: any[]
    confirm: any | null
}

/**
 * AI 轻量对话面板 → 完整对话页的一次性交接数据。
 * 面板打开完整对话时写入，完整对话页挂载或激活时消费并清空，避免重复带入。
 */
export const useAiChatHandoffStore = defineStore('aiChatHandoff', {
    state: () => ({
        payload: null as AiChatHandoffPayload | null
    }),
    actions: {
        setPayload(payload: AiChatHandoffPayload) {
            this.payload = payload
        },
        consume(): AiChatHandoffPayload | null {
            const payload = this.payload
            this.payload = null
            return payload
        }
    }
})
