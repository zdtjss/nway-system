-- ========================================
-- 29. AI 接口显式访问模式 + “全部 AI 接口”授权分组
--   PUBLIC：所有具备 AI 对话权限的用户可调用
--   RESTRICTED：必须按 sys_grant 显式授权 execute
--   已发布接口回填 RESTRICTED，与改造前“资源已登记则严格校验”的行为保持一致
-- ========================================

ALTER TABLE ai_interface
  ADD COLUMN access_mode VARCHAR(20) NOT NULL DEFAULT 'PUBLIC' COMMENT 'PUBLIC/RESTRICTED';

UPDATE ai_interface SET access_mode = 'RESTRICTED' WHERE status = 'PUBLISHED';

-- 分组资源：授予 ai:* 的 execute 即代表全部 AI 接口，不影响 nway-system 其他资源
INSERT INTO sys_resource (id, app_code, resource_code, resource_type, name, description, parent_id,
                          tenant_scope, owner_tenant_code, status, source, sort,
                          creator_id, creator_name, create_time, is_deleted)
VALUES ('ai_permission_group', 'nway-system', 'ai:*', 'CAPABILITY', '全部 AI 接口',
        'AI 接口授权分组：授予后可调用全部 AI 接口', '', 'PLATFORM', 'default', 'ACTIVE', 'APP', 0,
        'ai-sync', 'AI 接口', NOW(), 0)
ON DUPLICATE KEY UPDATE name = VALUES(name), status = 'ACTIVE', is_deleted = 0;

INSERT INTO sys_resource_action (id, resource_id, action, label, risk_level, sort,
                                 creator_id, creator_name, create_time, is_deleted)
VALUES ('ai_perm_group_exec', 'ai_permission_group', 'execute', '调用', 'MEDIUM', 0,
        'ai-sync', 'AI 接口', NOW(), 0)
ON DUPLICATE KEY UPDATE label = VALUES(label), risk_level = VALUES(risk_level);
