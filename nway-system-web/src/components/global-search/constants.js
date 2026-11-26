/**
 * 全局搜索（悬浮图标 + 菜单搜索 + AI 助手）共用的尺寸与存储常量。
 */

export const ICON_SIZE = 40
export const GAP = 8
export const VIEWPORT_MARGIN = 8

// 菜单搜索
export const MENU_PANEL_WIDTH = 300
export const MENU_RESULT_WIDTH = 360
export const MENU_POS_KEY = 'nway_global_search_pos'

// AI 面板
export const AI_PANEL_WIDTH = 440
// 面板可拖拽到接近视口高度，仅在超出视口时才截断
export const AI_PANEL_MAX_HEIGHT = 900
export const AI_PANEL_DEFAULT_HEIGHT = 600
export const AI_PANEL_MIN_WIDTH = 300
export const AI_PANEL_MIN_HEIGHT = 280
export const AI_PANEL_MAX_WIDTH = 760
// AI 面板缩放尺寸持久化 key
export const AI_SIZE_KEY = 'nway_global_search_ai_size'

// AI 附件
export const AI_MAX_FILES = 5
export const AI_MAX_FILE_SIZE = 20 * 1024 * 1024

// AI 历史会话
export const AI_HISTORY_PAGE_SIZE = 10

// AI 助手完整对话页（与菜单 url 一致，由 main.ts 的 views/system 自动注册为 SystemAiChat）
export const AI_CHAT_URL = '/views/system/ai/chat'
