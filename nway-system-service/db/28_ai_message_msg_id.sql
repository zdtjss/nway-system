-- ========================================
-- 28. AI 会话消息主键调整（存量库升级）
--   模型返回的 chatcmpl-* 响应 id 不保证全局唯一，原先直接作为 ai_message 主键，
--   会在不同会话间主键冲突，导致整轮回写失败。
--   改为：id 由后端生成全局唯一值，AgentScope 消息 id 存入 msg_id，
--   会话内按 (conversation_id, msg_id) 幂等。
-- ========================================

ALTER TABLE ai_message
  ADD COLUMN msg_id VARCHAR(64) NULL COMMENT 'AgentScope 消息 id（会话内幂等）' AFTER id;

UPDATE ai_message SET msg_id = id WHERE msg_id IS NULL;

ALTER TABLE ai_message
  MODIFY COLUMN msg_id VARCHAR(64) NOT NULL COMMENT 'AgentScope 消息 id（会话内幂等）',
  ADD UNIQUE KEY uk_ai_message_conv_msg (conversation_id, msg_id);
