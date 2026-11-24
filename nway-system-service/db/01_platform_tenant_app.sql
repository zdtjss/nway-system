-- ========================================
-- 01. 平台基座：租户与应用（DDL + 基线数据）
--   覆盖：
--     - sys_tenant：租户（含生成列部分唯一索引）
--     - sys_app：微前端子应用注册
--     - 基线数据：default/public 租户、nway-system 系统管理应用
--   前置：无（全新库第一个执行）
--   说明：
--     - sys_tenant 唯一索引基于生成列，仅未删除记录（is_deleted=0）参与唯一性校验
--     - 其他场景的初始化数据均以 tenant_code='default' 关联默认租户，
--       TenantDao.getByCode('default') 依赖默认租户记录，必须先于其他数据插入
-- ========================================

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

-- 应用表（微前端子应用）
CREATE TABLE sys_app (
    id VARCHAR(20) PRIMARY KEY,
    app_code VARCHAR(50) NOT NULL COMMENT '应用标识（唯一）',
    app_name VARCHAR(100) NOT NULL COMMENT '应用名称',
    host VARCHAR(512) COMMENT '前端部署地址（微前端加载入口）',
    api_base_url VARCHAR(512) COMMENT '后端 API 基础地址',
    status VARCHAR(18) DEFAULT 'using' COMMENT '状态：using/disabled',
    sort INT DEFAULT 0 COMMENT '排序',
    tenant_code VARCHAR(20) COMMENT '租户编码',
    creator_id VARCHAR(10),
    creator_name VARCHAR(10),
    modifier_id VARCHAR(10),
    modifier_name VARCHAR(10),
    create_time DATETIME,
    modified_time DATETIME,
    is_deleted BIT DEFAULT b'0'
) COMMENT='应用（微前端子应用）';

CREATE UNIQUE INDEX uidx_app_code ON sys_app(app_code);

-- 默认租户（其他场景初始化资源的 tenant_code='default' 均关联此租户）
INSERT INTO sys_tenant (id, tenant_code, tenant_name, status, creator_id, creator_name, create_time, is_deleted)
VALUES ('default', 'default', '默认租户', 'using', '001', '系统', NOW(), 0);

-- 公共租户（内置共享租户：其下资源对所有租户只读共享，
-- 读隔离集合在 TenantUtil.currentTenantCodesForIsolation 自动并入 'public'）
INSERT INTO sys_tenant (id, tenant_code, tenant_name, status, remark, creator_id, creator_name, create_time, is_deleted)
VALUES ('public', 'public', '公共租户', 'using', '内置公共租户：资源对所有租户只读共享', '001', '系统', NOW(), 0);

-- 默认应用
INSERT INTO sys_app (id, app_code, app_name, host, status, sort, tenant_code, creator_id, creator_name, create_time, is_deleted)
VALUES ('app_system', 'nway-system', '系统管理', '/', 'using', 0, 'default', '001', '系统', NOW(), 0);
