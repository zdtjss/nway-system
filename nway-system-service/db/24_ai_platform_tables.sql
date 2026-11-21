-- ========================================
-- 24. AI 对话驱动接口调用（DDL）
--   对应文档：AI接口元数据规范-v2.0.md / AI对话驱动接口调用技术方案.md v4.0
--   仅 4 张表：ai_interface / ai_model / ai_prompt / ai_conversation
--   消息、确认状态、工具调用历史复用 AgentScope Session，审计复用 sys_audit_log
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
  id                   VARCHAR(32)  PRIMARY KEY,
  name                 VARCHAR(100) NOT NULL COMMENT '配置名',
  base_url             VARCHAR(500) NOT NULL COMMENT 'OpenAI 兼容端点',
  api_key_cipher       VARCHAR(1000) COMMENT 'AES-GCM 密文',
  model_name           VARCHAR(100) NOT NULL COMMENT '模型名',
  temperature          DECIMAL(3,2) DEFAULT 0.30,
  max_tokens           INT          DEFAULT 4096,
  supports_tool_calling BIT         DEFAULT b'1',
  is_default           BIT          DEFAULT b'0',
  status               VARCHAR(20)  NOT NULL DEFAULT 'ACTIVE' COMMENT 'ACTIVE/DISABLED',
  tenant_code          VARCHAR(20),
  creator_id           VARCHAR(10),
  creator_name         VARCHAR(50),
  modifier_id          VARCHAR(10),
  modifier_name        VARCHAR(50),
  create_time          DATETIME,
  modified_time        DATETIME,
  is_deleted           BIT          DEFAULT b'0',
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
