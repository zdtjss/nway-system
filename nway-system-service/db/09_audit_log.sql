-- ========================================
-- 09. 审计日志：接口操作留痕（DDL）
--   覆盖：sys_audit_log
--   前置：01_platform_tenant_app.sql（tenant_code 语义）
--   说明：审计日志查询索引包含（操作人+时间、路径+时间）组合
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
