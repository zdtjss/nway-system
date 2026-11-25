-- ========================================
-- 11. 办公智能体：AI 接口、模型、提示词、会话与消息（DDL + 基线数据）
--   覆盖：
--     - ai_interface：AI 接口元数据（含 access_mode 访问模式）
--     - ai_model：模型 Provider（含视觉 / 上下文 / 采样 / 思考等生成参数）
--     - ai_prompt：提示词（SYSTEM / TASK / FEW_SHOT）
--     - ai_conversation：会话索引
--     - ai_message：会话消息（id 后端生成，msg_id 为 AgentScope 消息 id）
--     - 基线数据：AI 接口授权分组、办公智能体菜单、AI 应用编码字典
--   前置：
--     - 01_platform_tenant_app.sql（sys_app）
--     - 02_permission_authz.sql（sys_menu / sys_resource / sys_resource_action）
--     - 05_dict_config.sql（sys_dict_type / sys_dict_item）
--   说明：
--     - 本文件为 AI 场景的完整结构，直接建表；结构变更时直接修改本文件
--     - 消息、接口、模型等表按最终字段一次性创建，不维护增量迁移脚本
--     - 资源 ai_chat / ai_interface 由启动时 ResourceCatalogSynchronizer 按
--       @RequiresPermission 自动登记，此处只建导航菜单
--     - AI 对话的资源归属与后端注解一致：appCode=public_app，resource=ai_chat
-- ========================================

-- 接口元数据：业务接口是数据，不是工具
CREATE TABLE IF NOT EXISTS ai_interface (
  id                  VARCHAR(32)  PRIMARY KEY,
  interface_code      VARCHAR(64)  NOT NULL COMMENT '全局唯一接口编码',
  name                VARCHAR(100) NOT NULL COMMENT '接口名称',
  description         VARCHAR(1000) NOT NULL COMMENT '面向模型的场景/动作描述',
  app_code            VARCHAR(50)  NOT NULL COMMENT '目标应用，对应 sys_app.app_code',
  http_method         VARCHAR(10)  NOT NULL COMMENT 'GET/POST/PUT/DELETE',
  path                VARCHAR(500) NOT NULL COMMENT '相对路径，支持 {pathParam}',
  content_type        VARCHAR(100) COMMENT '默认 application/json',
  risk_level          VARCHAR(10)  NOT NULL DEFAULT 'READ' COMMENT 'READ/WRITE/HIGH_RISK',
  confirm_required    BIT          NOT NULL DEFAULT b'0' COMMENT '写操作必须为 1',
  input_schema        LONGTEXT     NOT NULL COMMENT '入参字段 JSON 数组',
  output_schema       LONGTEXT     COMMENT '出参字段 JSON 数组',
  invoke_config       LONGTEXT     NOT NULL COMMENT 'body 模板/响应提取/固定头/超时',
  search_text         TEXT         COMMENT '名称+描述+关键词+字段标签，保存时生成',
  search_keywords     VARCHAR(1000) COMMENT '同义词/简称/业务黑话',
  examples            LONGTEXT     COMMENT '用户表达示例 JSON 数组',
  metadata_confidence VARCHAR(20)  DEFAULT 'AUTO' COMMENT 'AUTO/MERGED/HUMAN_REVIEWED',
  status              VARCHAR(20)  NOT NULL DEFAULT 'DRAFT' COMMENT 'DRAFT/PUBLISHED/OFFLINE',
  access_mode         VARCHAR(20)  NOT NULL DEFAULT 'PUBLIC' COMMENT '访问模式：PUBLIC/RESTRICTED',
  version             INT          NOT NULL DEFAULT 1 COMMENT '版本号',
  tenant_code         VARCHAR(20),
  creator_id          VARCHAR(10),
  creator_name        VARCHAR(50),
  modifier_id         VARCHAR(10),
  modifier_name       VARCHAR(50),
  create_time         DATETIME,
  modified_time       DATETIME,
  is_deleted          BIT          DEFAULT b'0',
  -- 含 is_deleted：逻辑删除的接口不阻塞同编码重新创建；活跃行仍全局唯一
  UNIQUE KEY uidx_ai_interface_code (interface_code, is_deleted),
  KEY idx_ai_interface_status (status, tenant_code),
  KEY idx_ai_interface_app (app_code)
) COMMENT='AI 接口元数据';

-- 模型 Provider：OpenAI 兼容端点
CREATE TABLE IF NOT EXISTS ai_model (
  id                    VARCHAR(32)  PRIMARY KEY,
  name                  VARCHAR(100) NOT NULL COMMENT '配置名',
  base_url              VARCHAR(500) NOT NULL COMMENT 'OpenAI 兼容端点',
  api_key_cipher        VARCHAR(1000) COMMENT 'AES-GCM 密文',
  model_name            VARCHAR(100) NOT NULL COMMENT '模型名',
  temperature           DECIMAL(3,2) DEFAULT 0.30,
  max_tokens            INT          DEFAULT 8192,
  supports_vision       BIT          NULL COMMENT '视觉分析：NULL 自动识别，1 支持，0 不支持',
  context_window_tokens INT          NULL COMMENT '最大上下文 token，空则按模型名自动识别',
  top_p                 DECIMAL(4,3) NULL COMMENT '核采样 top_p，范围 0~1',
  frequency_penalty     DECIMAL(3,2) NULL COMMENT '频率惩罚，范围 -2~2',
  presence_penalty      DECIMAL(3,2) NULL COMMENT '存在惩罚，范围 -2~2',
  reasoning_effort      VARCHAR(20)  NULL COMMENT '推理深度：low/medium/high/max 等',
  thinking_enabled      BIT          NULL COMMENT '是否思考：NULL 模型默认，1 开启，0 关闭',
  thinking_budget       INT          NULL COMMENT '思考 token 预算',
  extra_body            LONGTEXT     NULL COMMENT '额外请求参数 JSON 对象，用于透传厂商专有字段',
  supports_tool_calling BIT          DEFAULT b'1',
  is_default            BIT          DEFAULT b'0',
  status                VARCHAR(20)  NOT NULL DEFAULT 'ACTIVE' COMMENT 'ACTIVE/DISABLED',
  tenant_code           VARCHAR(20),
  creator_id            VARCHAR(10),
  creator_name          VARCHAR(50),
  modifier_id           VARCHAR(10),
  modifier_name         VARCHAR(50),
  create_time           DATETIME,
  modified_time         DATETIME,
  is_deleted            BIT          DEFAULT b'0',
  KEY idx_ai_model_default (is_default, status)
) COMMENT='AI 模型 Provider';

-- 提示词：SYSTEM / ROLE / USER 三级继承
CREATE TABLE IF NOT EXISTS ai_prompt (
  id             VARCHAR(32)  PRIMARY KEY,
  name           VARCHAR(100) NOT NULL COMMENT '提示词名称（按名称去重）',
  prompt_type    VARCHAR(20)  NOT NULL DEFAULT 'TASK' COMMENT 'SYSTEM/TASK/FEW_SHOT',
  scope_type     VARCHAR(20)  NOT NULL COMMENT 'SYSTEM/ROLE/USER',
  scope_id       VARCHAR(64)  NOT NULL COMMENT 'SYSTEM 固定 SYSTEM；角色 id；用户工号',
  role_code      VARCHAR(50)  COMMENT '角色编码（scope_type=ROLE 时）',
  content        LONGTEXT     NOT NULL COMMENT '内容，支持 {{variable}}',
  variables_json LONGTEXT     COMMENT '变量定义',
  priority       INT          DEFAULT 0 COMMENT '同 scope 优先级',
  status         VARCHAR(20)  NOT NULL DEFAULT 'ACTIVE' COMMENT 'ACTIVE/ARCHIVED',
  tenant_code    VARCHAR(20),
  creator_id     VARCHAR(10),
  creator_name   VARCHAR(50),
  modifier_id    VARCHAR(10),
  modifier_name  VARCHAR(50),
  create_time    DATETIME,
  modified_time  DATETIME,
  is_deleted     BIT          DEFAULT b'0',
  KEY idx_ai_prompt_scope (scope_type, scope_id, status),
  KEY idx_ai_prompt_name (name)
) COMMENT='AI 提示词';

-- 会话索引：只解决按用户/时间列会话、重命名、归档，不存消息正文
CREATE TABLE IF NOT EXISTS ai_conversation (
  id              VARCHAR(48)  PRIMARY KEY COMMENT '会话 id，同时作为 AgentScope sessionId',
  user_id         VARCHAR(32)  NOT NULL COMMENT '用户工号',
  agent_id        VARCHAR(64)  COMMENT 'Agent 标识',
  title           VARCHAR(200) COMMENT '标题',
  last_message_at DATETIME     COMMENT '最后消息时间',
  status          VARCHAR(20)  NOT NULL DEFAULT 'ACTIVE' COMMENT 'ACTIVE/ARCHIVED',
  tenant_code     VARCHAR(20),
  creator_id      VARCHAR(10),
  creator_name    VARCHAR(50),
  modifier_id     VARCHAR(10),
  modifier_name   VARCHAR(50),
  create_time     DATETIME,
  modified_time   DATETIME,
  is_deleted      BIT          DEFAULT b'0',
  KEY idx_ai_conversation_user (user_id, last_message_at)
) COMMENT='AI 会话索引';

-- 会话消息：消息正文落库，历史记录读取不依赖 AgentScope 本地 journal
CREATE TABLE IF NOT EXISTS ai_message (
  id              VARCHAR(64)  PRIMARY KEY COMMENT '消息 id（后端生成全局唯一）',
  msg_id          VARCHAR(64)  NOT NULL COMMENT 'AgentScope 消息 id（会话内幂等）',
  conversation_id VARCHAR(48)  NOT NULL COMMENT '会话 id',
  user_id         VARCHAR(32)  NOT NULL COMMENT '用户工号',
  role            VARCHAR(16)  NOT NULL COMMENT 'user/assistant',
  blocks_json     LONGTEXT     NOT NULL COMMENT '消息块 JSON 数组（text/thinking/tool_use）',
  message_time    VARCHAR(32)  COMMENT '消息时间（SDK 原始时间戳，按字符串排序）',
  tenant_code     VARCHAR(20),
  creator_id      VARCHAR(10),
  creator_name    VARCHAR(50),
  modifier_id     VARCHAR(10),
  modifier_name   VARCHAR(50),
  create_time     DATETIME,
  modified_time   DATETIME,
  is_deleted      BIT          DEFAULT b'0',
  KEY idx_ai_message_conversation (conversation_id, message_time),
  UNIQUE KEY uk_ai_message_conv_msg (conversation_id, msg_id)
) COMMENT='AI 会话消息';

-- ========================================
-- 基线数据
-- ========================================

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

-- 办公智能体菜单
--   1) 目录：办公智能体
--   2) AI 对话：公共功能，资源归属 public_app
--   3) AI 接口配置：管理员能力，挂在「平台配置」下
INSERT INTO sys_menu (id, parent_id, name, url, icon, app_code, resource_code, type, action, sequence, is_enabled, source, tenant_code, creator_id, creator_name, create_time, is_deleted) VALUES
('m_sys_ai',           '',                  '办公智能体',   '',                                'icon_dir',  'nway-system', NULL,           'DIR',  NULL, 5, 1, 'PLATFORM', 'default', '001', '系统', NOW(), 0),
('m_sys_ai_chat',      '',                  'AI 对话',      '/views/system/ai/chat',           'icon_page', 'public_app',  'ai_chat',      'PAGE', NULL, 1, 1, 'PLATFORM', 'public',  '001', '系统', NOW(), 0),
('m_sys_ai_interface', 'm_sys_grp_config',  'AI 接口配置',  '/views/system/ai/interfaceList',  'icon_page', 'nway-system', 'ai_interface', 'PAGE', NULL, 7, 1, 'PLATFORM', 'default', '001', '系统', NOW(), 0)
ON DUPLICATE KEY UPDATE name = VALUES(name), url = VALUES(url), app_code = VALUES(app_code), resource_code = VALUES(resource_code), is_enabled = VALUES(is_enabled);

-- AI 接口应用字典
--   AI 接口配置的「应用编码」下拉取自字典，字典编码：ai_app_code
--   多字段字典项：appCode=应用编码，appName=应用名称，baseUrl=后端接口基础路径（域名+contextPath）
--   接口调试与大模型工具调用统一按 baseUrl + 接口 path 拼接目标地址。
--   下方 baseUrl 为本地联调默认值，请按部署环境在「字典管理」中调整。
INSERT INTO sys_dict_type (id, dict_code, dict_name, remark, field_defs, status, tenant_code, creator_id, creator_name, create_time, is_deleted)
VALUES ('dt_ai_app_code', 'ai_app_code', 'AI接口应用编码',
        'AI 接口配置可选的目标应用；baseUrl 为后端接口基础路径（域名+contextPath）',
        '[{"code":"appCode","name":"应用编码","type":"string","required":true,"searchable":true,"sort":1},{"code":"appName","name":"应用名称","type":"string","required":true,"searchable":true,"sort":2},{"code":"baseUrl","name":"后端接口基础路径","type":"string","required":true,"searchable":false,"sort":3}]',
        'using', 'default', '001', '系统', NOW(), 0);

INSERT INTO sys_dict_item (id, dict_type_id, item_value, sort, status, tenant_code, creator_id, creator_name, create_time, is_deleted) VALUES
('di_ai_app_system', 'dt_ai_app_code', '{"appCode":"nway-system","appName":"系统管理","baseUrl":"http://localhost:9081/nway-system"}', 2, 'using', 'default', '001', '系统', NOW(), 0);
