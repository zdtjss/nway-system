-- ========================================
-- 08. 开放平台：应用、Webhook 订阅与调用日志（DDL）
--   覆盖：sys_open_app / sys_open_webhook / sys_open_api_log
--   前置：01_platform_tenant_app.sql（tenant_code 语义）
--   说明：
--     - 开放应用的数据范围、限流、IP 白名单均在本场景表内配置
--     - 调用日志查询索引包含（应用+时间）组合
-- ========================================

-- 开放应用表
CREATE TABLE sys_open_app (
  id VARCHAR(20) PRIMARY KEY,
  app_code VARCHAR(50) NOT NULL COMMENT '应用编码',
  app_name VARCHAR(100) NOT NULL COMMENT '应用名称',
  app_secret VARCHAR(256) NOT NULL COMMENT '密钥（BCrypt）',
  status VARCHAR(18) DEFAULT 'using' COMMENT '状态：using/disabled',
  granted_scopes TEXT NOT NULL COMMENT '已授权scope JSON数组',
  -- 应用数据范围（数据级，类似飞书通讯录范围）
  data_scope_type VARCHAR(20) NOT NULL DEFAULT 'ALL' COMMENT 'ALL/DEPT/DEPT_WITH_SUB/CUSTOM',
  data_scope_dept_ids TEXT COMMENT '可访问部门ID JSON数组，CUSTOM时有值',
  rate_limit INT DEFAULT 100 COMMENT '每秒请求上限',
  daily_limit INT DEFAULT 100000 COMMENT '每日请求上限',
  allowed_ips TEXT COMMENT 'IP白名单JSON，空为不限',
  owner_employee_id VARCHAR(20) COMMENT '负责人工号',
  tenant_code VARCHAR(20) COMMENT '租户编码',
  expire_time DATETIME COMMENT '过期时间',
  creator_id VARCHAR(10) COMMENT '创建人id',
  creator_name VARCHAR(10) COMMENT '创建人名称',
  modifier_id VARCHAR(10) COMMENT '修改人Id',
  modifier_name VARCHAR(10) COMMENT '修改人名称',
  create_time DATETIME COMMENT '创建时间',
  modified_time DATETIME COMMENT '修改日期',
  is_deleted BIT DEFAULT b'0' COMMENT '删除标记'
) COMMENT='开放平台应用';

CREATE UNIQUE INDEX uidx_app_code ON sys_open_app(app_code);

-- Webhook 事件订阅表
CREATE TABLE sys_open_webhook (
  id VARCHAR(20) PRIMARY KEY,
  app_id VARCHAR(20) NOT NULL COMMENT '应用id',
  event_type VARCHAR(50) NOT NULL COMMENT '事件类型：user.created/user.updated/user.disabled/dept.changed/role.changed/data_permission.changed',
  callback_url VARCHAR(512) NOT NULL COMMENT '回调URL',
  secret VARCHAR(256) COMMENT 'HMAC签名密钥',
  status VARCHAR(18) DEFAULT 'using' COMMENT '状态：using/disabled',
  retry_count INT DEFAULT 3 COMMENT '重试次数',
  tenant_code VARCHAR(20) COMMENT '租户编码',
  creator_id VARCHAR(10) COMMENT '创建人id',
  creator_name VARCHAR(10) COMMENT '创建人名称',
  modifier_id VARCHAR(10) COMMENT '修改人Id',
  modifier_name VARCHAR(10) COMMENT '修改人名称',
  create_time DATETIME COMMENT '创建时间',
  modified_time DATETIME COMMENT '修改日期',
  is_deleted BIT DEFAULT b'0' COMMENT '删除标记'
) COMMENT='开放平台Webhook订阅';

CREATE INDEX idx_app_id ON sys_open_webhook(app_id);
CREATE INDEX idx_event_type ON sys_open_webhook(event_type);

-- 开放平台调用日志
CREATE TABLE sys_open_api_log (
  id VARCHAR(20) PRIMARY KEY,
  app_code VARCHAR(50) NOT NULL COMMENT '应用编码',
  api_path VARCHAR(256) NOT NULL COMMENT '接口路径',
  http_method VARCHAR(10) NOT NULL COMMENT 'HTTP方法',
  request_params TEXT COMMENT '请求参数',
  response_status INT COMMENT '响应状态码',
  duration_ms INT COMMENT '耗时毫秒',
  ip VARCHAR(64) COMMENT '请求IP',
  employee_id VARCHAR(20) COMMENT '代理的用户工号（如有）',
  error_msg VARCHAR(500) COMMENT '错误信息',
  tenant_code VARCHAR(20) COMMENT '租户编码',
  create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间'
) COMMENT='开放平台调用日志';

CREATE INDEX idx_app_code ON sys_open_api_log(app_code);
CREATE INDEX idx_create_time ON sys_open_api_log(create_time);
CREATE INDEX idx_api_path ON sys_open_api_log(api_path);
-- 开放平台调用日志查询（应用+时间）
CREATE INDEX idx_open_api_log_app_time ON sys_open_api_log(app_code, create_time);
