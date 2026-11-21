-- ========================================
-- 30. 办公智能体菜单信息架构
--   1) AI 目录更名为「办公智能体」
--   2) 「AI 接口配置」属于管理员能力，移入「平台配置」
--   3) 「AI 对话」保留在办公智能体目录下，名称不变
--   只调整菜单数据，不改 URL、资源编码与权限编码；可重复执行。
--   方案文档原编号 26 已被 ai_app_code 字典占用，顺延为 30。
-- ========================================

-- 1) AI 目录更名为办公智能体
UPDATE sys_menu SET name = '办公智能体' WHERE id = 'm_sys_ai';

-- 2) 接口配置属于管理员能力，移入“平台配置”
UPDATE sys_menu
SET parent_id = 'm_sys_grp_config', sequence = 7
WHERE id = 'm_sys_ai_interface';

-- 3) 对话页仍保留在办公智能体目录下，名称保持“AI 对话”

-- 回滚：
-- UPDATE sys_menu SET name = 'AI 助手' WHERE id = 'm_sys_ai';
-- UPDATE sys_menu SET parent_id = 'm_sys_ai', sequence = 2 WHERE id = 'm_sys_ai_interface';
