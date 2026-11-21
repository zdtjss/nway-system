-- ========================================
-- 25. AI 助手菜单
--   AI 对话（公共功能 public_app）、AI 接口配置（元数据管理）
--   资源 ai_chat / ai_interface 由启动时 ResourceCatalogSynchronizer 按
--   @RequiresPermission 自动登记，此处只建导航菜单
--   AI 对话的资源归属与后端注解一致：appCode=public_app，resource=ai_chat
-- ========================================

INSERT INTO sys_menu (id, parent_id, name, url, icon, app_code, resource_code, type, action, sequence, is_enabled, source, tenant_code, creator_id, creator_name, create_time, is_deleted) VALUES
('m_sys_ai',           '', 'AI 助手', '', 'icon_dir', 'nway-system', NULL, 'DIR', NULL, 5, 1, 'PLATFORM', 'default', '001', '系统', NOW(), 0),
('m_sys_ai_chat',      '', 'AI 对话', '/views/system/ai/chat',              'icon_page', 'public_app', 'ai_chat',      'PAGE', NULL, 1, 1, 'PLATFORM', 'public', '001', '系统', NOW(), 0),
('m_sys_ai_interface', 'm_sys_ai', 'AI 接口配置', '/views/system/ai/interfaceList', 'icon_page', 'nway-system', 'ai_interface', 'PAGE', NULL, 2, 1, 'PLATFORM', 'default', '001', '系统', NOW(), 0)
ON DUPLICATE KEY UPDATE name = VALUES(name), url = VALUES(url), app_code = VALUES(app_code), resource_code = VALUES(resource_code), is_enabled = VALUES(is_enabled);
