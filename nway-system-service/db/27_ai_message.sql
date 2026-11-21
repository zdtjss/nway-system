-- ========================================
-- 27. AI 会话消息（DDL）
--   ai_conversation 仍为会话索引（标题/时间/状态），消息正文落到 ai_message；
--   历史记录读取不再依赖 AgentScope 本地 journal，换机器/换启动目录都不会丢。
-- ========================================

CREATE TABLE IF NOT EXISTS ai_message (
  id              VARCHAR(64)  PRIMARY KEY COMMENT '消息 id（AgentScope 消息 id）',
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
  KEY idx_ai_message_conversation (conversation_id, message_time)
) COMMENT='AI 会话消息';
