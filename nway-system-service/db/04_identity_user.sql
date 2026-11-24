-- ========================================
-- 04. 用户身份与账号：用户、登录校验（DDL + 默认管理员）
--   覆盖：
--     - sys_user：用户信息
--     - sys_user_login_key：用户登录校验信息（密码 / 生物识别 / 微信）
--     - 基线数据：默认超级管理员账号 admin（密码 1）、其登录凭证与管理员角色绑定
--   前置：
--     - 01_platform_tenant_app.sql（租户 default）
--     - 02_permission_authz.sql（sys_user_role 表与管理员角色 r_admin）
--   说明：首次登录后请立即修改默认密码
-- ========================================

-- 用户表
CREATE TABLE sys_user (
    id VARCHAR(20) PRIMARY KEY COMMENT '主键',
    employee_id VARCHAR(10) COMMENT '工号',
    real_name VARCHAR(50) COMMENT '真实姓名',
    phone_number VARCHAR(18) COMMENT '手机号',
    avatar VARCHAR(255) COMMENT '头像地址',
    email VARCHAR(64) COMMENT '邮箱',
    dept_id VARCHAR(20) COMMENT '部门id（主部门）',
    id_number VARCHAR(18) COMMENT '身份证号',
    status VARCHAR(18) DEFAULT 'using' COMMENT '状态：using/disabled',
    is_super_admin BIT DEFAULT b'0' COMMENT '是否超级管理员',
    is_admin BIT DEFAULT b'0' COMMENT '是否平台管理员',
    tenant_code VARCHAR(20) COMMENT '租户编码',
    creator_id VARCHAR(10) COMMENT '创建人id',
    creator_name VARCHAR(10) COMMENT '创建人名称',
    modifier_id VARCHAR(10) COMMENT '修改人Id',
    modifier_name VARCHAR(10) COMMENT '修改人名称',
    create_time DATETIME COMMENT '创建时间',
    modified_time DATETIME COMMENT '修改日期',
    token_version INT DEFAULT 0 COMMENT 'Token版本号，强制下线时自增',
    is_deleted BIT DEFAULT b'0' COMMENT '删除标记'
) COMMENT='用户信息';

CREATE UNIQUE INDEX uidx_employee_id ON sys_user(employee_id);
CREATE INDEX idx_user_status_tenant ON sys_user(status, tenant_code, is_deleted);

-- 用户登录校验信息
CREATE TABLE sys_user_login_key (
    id VARCHAR(20) PRIMARY KEY COMMENT '主键',
    employee_id VARCHAR(10) COMMENT '工号',
    login_name VARCHAR(10) COMMENT '登录名',
    password VARCHAR(256) COMMENT '密码（BCrypt）',
    bio_mobile VARCHAR(256) COMMENT '移动端指纹标识',
    bio_pc VARCHAR(256) COMMENT 'PC端指纹标识',
    weixin_id VARCHAR(256) COMMENT '微信id',
    is_valid BIT DEFAULT b'0' COMMENT '是否可用',
    pwd_reset_required BIT DEFAULT b'0' COMMENT '是否要求修改密码（新建用户/重置密码后置1，用户改密后清0）',
    tenant_code VARCHAR(20) COMMENT '租户编码',
    creator_id VARCHAR(10) COMMENT '创建人id',
    creator_name VARCHAR(10) COMMENT '创建人名称',
    modifier_id VARCHAR(10) COMMENT '修改人Id',
    modifier_name VARCHAR(10) COMMENT '修改人名称',
    create_time DATETIME COMMENT '创建时间',
    modified_time DATETIME COMMENT '修改日期',
    is_deleted BIT DEFAULT b'0' COMMENT '删除标记'
) COMMENT='用户登录校验信息';

CREATE UNIQUE INDEX uidx_login_name ON sys_user_login_key(login_name);
CREATE UNIQUE INDEX uidx_employee_id_login ON sys_user_login_key(employee_id);

-- 默认超级管理员账号（登录名: admin，密码: 1）
INSERT INTO sys_user (id, employee_id, real_name, status, is_super_admin, is_admin, tenant_code, creator_id, creator_name, create_time, is_deleted)
VALUES ('u_admin', 'admin', '超级管理员', 'using', b'1', b'1', 'default', '001', '系统', NOW(), 0);

INSERT INTO sys_user_login_key (id, employee_id, login_name, password, is_valid, tenant_code, creator_id, creator_name, create_time, is_deleted)
VALUES ('ulk_admin', 'admin', 'admin', '$2a$10$LUbTOVtOwVm6cC2Ah5ALm.XJ6twzfQ/b6EqQUwlrUomB0beZLvUie', b'1', 'default', '001', '系统', NOW(), 0);

-- 默认管理员角色绑定
INSERT INTO sys_user_role (id, employee_id, role_id, tenant_code, creator_id, create_time, is_deleted)
VALUES ('ur_admin', 'admin', 'r_admin', 'default', '001', NOW(), 0);
