-- ========================================
-- 06. 消息中心：消息主表、目标、接收人与发送日志（DDL）
--   覆盖：message_info / message_target / message_receiver / message_send_log
--   前置：01_platform_tenant_app.sql（tenant_code 语义）
--   说明：消息发送渠道、重试与已读状态均由本场景表承载
-- ========================================

-- 消息主表
CREATE TABLE message_info (
   id varchar(32) NOT NULL COMMENT '消息ID，主键',
   title VARCHAR(200) DEFAULT '' COMMENT '消息标题',
   content TEXT NOT NULL COMMENT '消息内容',
   sender_id varchar(32) NOT NULL COMMENT '发送者用户ID',
   sender_name varchar(32) NOT NULL COMMENT '发送者用户名称',
   send_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '消息发送时间',
   expire_time DATETIME COMMENT '消息过期时间',
   tenant_code varchar(20) comment '租户编码',
   creator_id varchar(10) COMMENT '创建人id',
   creator_name varchar(10) COMMENT '创建人名称',
   modifier_id varchar(10) COMMENT '修改人Id',
   modifier_name varchar(10) COMMENT '修改人名称',
   create_time datetime COMMENT '创建时间',
   modified_time datetime COMMENT '修改日期',
   is_deleted bit DEFAULT b'0' COMMENT '删除标记',
   PRIMARY KEY (id),
   INDEX idx_sender_id (sender_id),
   INDEX idx_send_time (send_time)
) COMMENT = '消息主表';

-- 消息目标表
CREATE TABLE message_target (
  id varchar(32) NOT NULL COMMENT '记录ID，主键',
  message_id varchar(32) NOT NULL COMMENT '关联的消息ID',
  target_type TINYINT NOT NULL COMMENT '目标类型：1=用户，2=部门，3=角色',
  target_id varchar(32) NOT NULL COMMENT '目标ID',
  create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (id),
  INDEX idx_message_id (message_id),
  INDEX idx_target (target_type, target_id)
) COMMENT='消息目标中间表';

-- 消息接收人表
CREATE TABLE message_receiver (
  id varchar(32) NOT NULL COMMENT '记录ID，主键',
  message_id varchar(32) NOT NULL COMMENT '关联的消息ID',
  user_id varchar(32) NOT NULL COMMENT '接收用户ID',
  is_read TINYINT DEFAULT 0 COMMENT '是否已读：0=未读，1=已读',
  is_new TINYINT DEFAULT 1 COMMENT '是否为新消息：1=是，0=否',
  read_time DATETIME COMMENT '阅读时间',
  channels JSON NOT NULL COMMENT '计划发送的渠道列表JSON数组',
  channel_status JSON COMMENT '各渠道发送状态JSON',
  tenant_code varchar(20) comment '租户编码',
  creator_id varchar(10) COMMENT '创建人id',
  creator_name varchar(10) COMMENT '创建人名称',
  modifier_id varchar(10) COMMENT '修改人Id',
  modifier_name varchar(10) COMMENT '修改人名称',
  create_time datetime COMMENT '创建时间',
  modified_time datetime COMMENT '修改日期',
  is_deleted bit DEFAULT b'0' COMMENT '删除标记',
  PRIMARY KEY (id),
  UNIQUE KEY uk_msg_user (message_id, user_id),
  INDEX idx_user_unread_new (user_id, is_read, is_new, create_time),
  INDEX idx_user_create_time (user_id, create_time),
  INDEX idx_message_id (message_id),
  INDEX idx_read_time (read_time)
) COMMENT = '消息接收人明细表';

-- 消息接收人查询（按用户+已读状态）
CREATE INDEX idx_msg_receiver_user_read ON message_receiver(user_id, is_read);

-- 消息发送日志
CREATE TABLE message_send_log (
  id varchar(32) NOT NULL COMMENT '主键',
  message_id varchar(32) NOT NULL COMMENT '关联的消息ID',
  receiver_id varchar(32) NOT NULL COMMENT '消息接收人记录ID',
  user_id varchar(32) NOT NULL COMMENT '接收用户ID',
  channel varchar(32) NOT NULL COMMENT '发送渠道：inner/inner_pop/email/wxwork',
  status varchar(16) NOT NULL DEFAULT 'pending' COMMENT '发送状态：pending/success/failed',
  error_msg varchar(1000) COMMENT '失败原因',
  retry_count int DEFAULT 0 COMMENT '重试次数',
  max_retry int DEFAULT 3 COMMENT '最大重试次数',
  send_time datetime COMMENT '本次发送时间',
  next_retry_time datetime COMMENT '下次重试时间',
  tenant_code varchar(20) comment '租户编码',
  creator_id varchar(10) COMMENT '创建人id',
  creator_name varchar(10) COMMENT '创建人名称',
  modifier_id varchar(10) COMMENT '修改人Id',
  modifier_name varchar(10) COMMENT '修改人名称',
  create_time datetime COMMENT '创建时间',
  modified_time datetime COMMENT '修改日期',
  is_deleted bit DEFAULT b'0' COMMENT '删除标记',
  PRIMARY KEY (id),
  INDEX idx_message_id (message_id),
  INDEX idx_user_channel (user_id, channel),
  INDEX idx_status_retry (status, next_retry_time),
  INDEX idx_receiver_id (receiver_id)
) COMMENT = '消息发送日志';
