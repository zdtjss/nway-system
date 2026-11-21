-- ========================================
-- 26. AI 接口应用字典（DML）
--   AI 接口配置的「应用编码」下拉取自字典，字典编码：ai_app_code
--   多字段字典项：appCode=应用编码，appName=应用名称，baseUrl=后端接口基础路径（域名+contextPath）
--   接口调试与大模型工具调用统一按 baseUrl + 接口 path 拼接目标地址。
--   下方 baseUrl 为本地联调默认值，请按部署环境在「字典管理」中调整。
-- ========================================

INSERT INTO sys_dict_type (id, dict_code, dict_name, remark, field_defs, status, tenant_code, creator_id, creator_name, create_time, is_deleted)
VALUES ('dt_ai_app_code', 'ai_app_code', 'AI接口应用编码',
        'AI 接口配置可选的目标应用；baseUrl 为后端接口基础路径（域名+contextPath）',
        '[{"code":"appCode","name":"应用编码","type":"string","required":true,"searchable":true,"sort":1},{"code":"appName","name":"应用名称","type":"string","required":true,"searchable":true,"sort":2},{"code":"baseUrl","name":"后端接口基础路径","type":"string","required":true,"searchable":false,"sort":3}]',
        'using', 'default', '001', '系统', NOW(), 0);

INSERT INTO sys_dict_item (id, dict_type_id, item_value, sort, status, tenant_code, creator_id, creator_name, create_time, is_deleted) VALUES
('di_ai_app_system', 'dt_ai_app_code', '{"appCode":"nway-system","appName":"系统管理","baseUrl":"http://localhost:9081/nway-system"}', 2, 'using', 'default', '001', '系统', NOW(), 0);
