-- ========================================
-- 12. 管理员菜单补充（必要数据）
--   覆盖开放平台、审计日志、授权工作台、文件管理、系统配置等页面
--
--   注：管理员角色 r_admin 的授权（nway-system 全部资源 ALL）已在 11_core_init_data.sql 中创建，
--   覆盖基线应用的所有资源。此文件仅补充菜单定义。
-- ========================================

-- 权限管理：授权工作台（接口资源复用 role，见 ApiResourceRegistry）
INSERT INTO sys_menu (id, parent_id, name, url, icon, app_code, resource_code, type, action, sequence, is_enabled, source, tenant_code, creator_id, creator_name, create_time, is_deleted) VALUES
('m_sys_authz', 'm_sys_grp_perm', '授权工作台', '/views/system/authz/authzWorkbench', 'lock', 'nway-system', 'role', 'PAGE', NULL, 4, 1, 'PLATFORM', 'default', '001', '系统', NOW(), 0);

-- 平台配置：系统配置、文件管理
INSERT INTO sys_menu (id, parent_id, name, url, icon, app_code, resource_code, type, action, sequence, is_enabled, source, tenant_code, creator_id, creator_name, create_time, is_deleted) VALUES
('m_sys_sys_config', 'm_sys_grp_config', '系统配置', '/views/system/sysConfig/sysConfigList', 'setting',  'nway-system', 'sys_config', 'PAGE', NULL, 5, 1, 'PLATFORM', 'default', '001', '系统', NOW(), 0),
('m_sys_file',       'm_sys_grp_config', '文件管理', '/views/system/file/fileList',           'folder',   'nway-system', 'file',       'PAGE', NULL, 6, 1, 'PLATFORM', 'default', '001', '系统', NOW(), 0);

-- 开放平台菜单
INSERT INTO sys_menu (id, parent_id, name, url, icon, app_code, resource_code, type, action, sequence, is_enabled, source, tenant_code, creator_id, creator_name, create_time, is_deleted) VALUES
('m_sys_open',         '', '开放平台',         '', 'connection', 'nway-system', NULL,           'DIR',  NULL, 4, 1, 'PLATFORM', 'default', '001', '系统', NOW(), 0),
('m_sys_open_app',     'm_sys_open', '应用接入管理', '/views/system/openplatform/appList',    'grid',    'nway-system', 'open_app',    'PAGE', NULL, 1, 1, 'PLATFORM', 'default', '001', '系统', NOW(), 0),
('m_sys_open_log',     'm_sys_open', '调用日志',     '/views/system/openplatform/apiLogList', 'document','nway-system', 'open_api_log','PAGE', NULL, 2, 1, 'PLATFORM', 'default', '001', '系统', NOW(), 0),
('m_sys_open_webhook', 'm_sys_open', 'Webhook订阅',  '/views/system/openplatform/webhookList','connection','nway-system','open_webhook','PAGE', NULL, 3, 1, 'PLATFORM', 'default', '001', '系统', NOW(), 0),
('m_sys_open_stats',   'm_sys_open', '调用统计',     '/views/system/openplatform/apiStats',   'data-line','nway-system', 'open_api_log','PAGE', NULL, 4, 1, 'PLATFORM', 'default', '001', '系统', NOW(), 0);

-- 审计日志菜单
INSERT INTO sys_menu (id, parent_id, name, url, icon, app_code, resource_code, type, action, sequence, is_enabled, source, tenant_code, creator_id, creator_name, create_time, is_deleted) VALUES
('m_sys_audit', 'm_sys_grp_monitor', '审计日志', '/views/system/auditLog/auditLogList', 'document', 'nway-system', 'audit_log', 'PAGE', NULL, 2, 1, 'PLATFORM', 'default', '001', '系统', NOW(), 0);
