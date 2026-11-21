-- ========================================
-- 06. 审计日志 + 租户表（DDL）
--   来源：原 07_audit_log.sql + 09_add_indexes.sql 中审计日志补充索引
-- ========================================

CREATE TABLE sys_audit_log (
  id VARCHAR(20) PRIMARY KEY,
  operator_id VARCHAR(20) COMMENT '操作人工号',
  operator_name VARCHAR(50) COMMENT '操作人姓名',
  operation_type VARCHAR(20) COMMENT '操作类型：CREATE/UPDATE/DELETE/LOGIN/LOGOUT/OTHER',
  api_path VARCHAR(256) NOT NULL COMMENT '请求路径',
  http_method VARCHAR(10) NOT NULL COMMENT 'HTTP方法',
  request_params TEXT COMMENT '请求参数（脱敏）',
  response_status INT COMMENT '响应状态码',
  duration_ms INT COMMENT '耗时毫秒',
  ip VARCHAR(64) COMMENT '请求IP',
  user_agent VARCHAR(512) COMMENT 'User-Agent',
  tenant_code VARCHAR(20) COMMENT '租户编码',
  create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间'
) COMMENT='审计日志';

CREATE INDEX idx_operator_id ON sys_audit_log(operator_id);
CREATE INDEX idx_create_time ON sys_audit_log(create_time);
CREATE INDEX idx_api_path ON sys_audit_log(api_path);
CREATE INDEX idx_operation_type ON sys_audit_log(operation_type);
-- 审计日志查询（操作人+时间、路径+时间）
CREATE INDEX idx_audit_log_user_time ON sys_audit_log(operator_id, create_time);
CREATE INDEX idx_audit_log_path_time ON sys_audit_log(api_path, create_time);

-- 租户表
CREATE TABLE sys_tenant (
  id VARCHAR(20) PRIMARY KEY,
  tenant_code VARCHAR(50) NOT NULL COMMENT '租户编码',
  tenant_name VARCHAR(100) NOT NULL COMMENT '租户名称',
  contact_name VARCHAR(50) COMMENT '联系人',
  contact_phone VARCHAR(20) COMMENT '联系电话',
  status VARCHAR(18) DEFAULT 'using' COMMENT '状态：using/disabled',
  expire_date DATE COMMENT '到期日期，为空表示长期有效',
  remark VARCHAR(255) COMMENT '备注',
  creator_id VARCHAR(10) COMMENT '创建人id',
  creator_name VARCHAR(10) COMMENT '创建人名称',
  modifier_id VARCHAR(10) COMMENT '修改人Id',
  modifier_name VARCHAR(10) COMMENT '修改人名称',
  create_time DATETIME COMMENT '创建时间',
  modified_time DATETIME COMMENT '修改日期',
  is_deleted BIT DEFAULT b'0' COMMENT '删除标记',
  -- 生成列：仅未删除记录（is_deleted=0）的编码/名称参与唯一性校验，删除后编码/名称可复用
  tenant_code_uniq VARCHAR(50) AS (CASE WHEN is_deleted = 0 THEN tenant_code END),
  tenant_name_uniq VARCHAR(100) AS (CASE WHEN is_deleted = 0 THEN tenant_name END)
) COMMENT='租户';

-- 唯一性约束：仅对未删除记录（is_deleted=0）生效，已删除租户的编码/名称可复用；
-- 禁用/过期等任意状态的未删除租户均参与唯一性校验（生成列为 NULL 时不参与唯一）
-- 注：MariaDB 12 不支持函数索引 CREATE INDEX ((expr)) 语法，改用生成列实现部分唯一索引
CREATE UNIQUE INDEX uidx_tenant_code ON sys_tenant (tenant_code_uniq);
CREATE UNIQUE INDEX uidx_tenant_name ON sys_tenant (tenant_name_uniq);
