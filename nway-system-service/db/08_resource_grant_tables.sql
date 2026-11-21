-- ========================================
-- 08. 权限资源目录 + 统一授权表（DDL）
--   权限判定的真实物理存储：运行时不再读写 sys_permission_policy
--   （PermissionPolicyDao 已全部委托 GrantDao，旧的 sys_permission_policy 表废弃）
--   启动时 ResourceCatalogSynchronizer 会扫描 @RequiresPermission 并补登记资源/动作
-- ========================================

-- 权限资源（与导航菜单解耦，跨租户元数据）
CREATE TABLE sys_resource (
  id VARCHAR(32) PRIMARY KEY,
  app_code VARCHAR(50) NOT NULL COMMENT '所属应用',
  resource_code VARCHAR(50) NOT NULL COMMENT '资源标识（应用内唯一，*=该应用全部资源）',
  resource_type VARCHAR(20) NOT NULL DEFAULT 'PAGE' COMMENT '资源类型：PAGE/API/CAPABILITY/DATA/FIELD',
  name VARCHAR(100) COMMENT '资源名称',
  description VARCHAR(255) COMMENT '说明',
  parent_id VARCHAR(32) DEFAULT '' COMMENT '上级资源id（能力分组）',
  tenant_scope VARCHAR(20) NOT NULL DEFAULT 'PLATFORM' COMMENT 'PLATFORM/SHARED/TENANT',
  owner_tenant_code VARCHAR(20) COMMENT '资源归属租户（策略写入作用的租户）',
  status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE' COMMENT 'ACTIVE/DEPRECATED',
  source VARCHAR(20) COMMENT '来源：CODE/PLATFORM/APP/LOWCODE',
  manifest_hash VARCHAR(64) COMMENT '最近一次 manifest 校验哈希',
  sort INT DEFAULT 0 COMMENT '排序',
  creator_id VARCHAR(10) COMMENT '创建人id（启动同步使用 codesync，长度不可超过 10）',
  creator_name VARCHAR(50) COMMENT '创建人名称',
  modifier_id VARCHAR(10) COMMENT '修改人Id',
  modifier_name VARCHAR(50) COMMENT '修改人名称',
  create_time DATETIME COMMENT '创建时间',
  modified_time DATETIME COMMENT '修改日期',
  is_deleted BIT DEFAULT b'0' COMMENT '删除标记'
) COMMENT='权限资源（与导航菜单解耦）';

CREATE UNIQUE INDEX uidx_resource_app_code ON sys_resource(app_code, resource_code);
CREATE INDEX idx_resource_scope ON sys_resource(tenant_scope, status);

-- 资源动作（受控词表：未登记的动作不允许出现在策略中）
CREATE TABLE sys_resource_action (
  id VARCHAR(32) PRIMARY KEY,
  resource_id VARCHAR(32) NOT NULL COMMENT '资源id',
  action VARCHAR(50) NOT NULL COMMENT '动作标识',
  label VARCHAR(100) COMMENT '展示名',
  risk_level VARCHAR(10) DEFAULT 'LOW' COMMENT 'LOW/MEDIUM/HIGH',
  sort INT DEFAULT 0 COMMENT '排序',
  creator_id VARCHAR(10) COMMENT '创建人id',
  creator_name VARCHAR(50) COMMENT '创建人名称',
  modifier_id VARCHAR(10) COMMENT '修改人Id',
  modifier_name VARCHAR(50) COMMENT '修改人名称',
  create_time DATETIME COMMENT '创建时间',
  modified_time DATETIME COMMENT '修改日期',
  is_deleted BIT DEFAULT b'0' COMMENT '删除标记'
) COMMENT='资源动作（受控词表）';

CREATE UNIQUE INDEX uidx_resource_action ON sys_resource_action(resource_id, action);

-- 统一授权（主体 × 资源 × 动作 × 数据范围）
CREATE TABLE sys_grant (
  id VARCHAR(32) PRIMARY KEY,
  subject_type VARCHAR(10) NOT NULL COMMENT '主体类型：ROLE/USER/POSITION/ORG/APP',
  subject_id VARCHAR(64) NOT NULL COMMENT '主体id（角色id/工号/岗位id/应用编码）',
  app_code VARCHAR(50) NOT NULL COMMENT '应用标识',
  resource_code VARCHAR(50) NOT NULL COMMENT '资源标识；* 表示该应用全部资源（需显式）',
  actions TEXT NOT NULL COMMENT 'JSON数组，非空；全部动作写 ["*"]',
  effect VARCHAR(10) NOT NULL DEFAULT 'ALLOW' COMMENT 'ALLOW/DENY（DENY 优先）',
  data_scope VARCHAR(20) NOT NULL DEFAULT 'SELF' COMMENT 'ALL/SELF/DEPT/DEPT_TREE/DEPT_LIST/NONE',
  scope_dept_ids TEXT COMMENT 'DEPT_LIST 的部门id列表（JSON数组）',
  effective_from DATETIME COMMENT '生效时间',
  expire_at DATETIME COMMENT '失效时间',
  status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE' COMMENT 'ACTIVE/DISABLED',
  version BIGINT DEFAULT 1 COMMENT '行版本，配合全局授权版本做缓存失效',
  tenant_code VARCHAR(20) COMMENT '主体归属租户',
  creator_id VARCHAR(10) COMMENT '创建人id',
  creator_name VARCHAR(50) COMMENT '创建人名称',
  modifier_id VARCHAR(10) COMMENT '修改人Id',
  modifier_name VARCHAR(50) COMMENT '修改人名称',
  create_time DATETIME COMMENT '创建时间',
  modified_time DATETIME COMMENT '修改日期',
  is_deleted BIT DEFAULT b'0' COMMENT '删除标记'
) COMMENT='统一授权';

-- 唯一键刻意不包含 is_deleted：逻辑删除的墓碑行仍占用该键，
-- 重新授权 / 替换式保存必须走 GrantDao.insertOrRevive 复用墓碑行，否则会 Duplicate entry
CREATE UNIQUE INDEX uidx_grant_subject_resource ON sys_grant(subject_type, subject_id, app_code, resource_code, effect);
CREATE INDEX idx_grant_resource ON sys_grant(app_code, resource_code);
CREATE INDEX idx_grant_tenant ON sys_grant(tenant_code);

-- 授权模板（租户开通套餐：一组角色 + 角色内授权清单）
CREATE TABLE sys_grant_template (
  id VARCHAR(32) PRIMARY KEY,
  template_code VARCHAR(50) NOT NULL COMMENT '模板编码',
  name VARCHAR(100) NOT NULL COMMENT '模板名称',
  description VARCHAR(255) COMMENT '说明',
  app_code VARCHAR(50) COMMENT '主应用（角色默认所属应用）',
  items_json TEXT COMMENT '角色与授权清单 JSON',
  status VARCHAR(20) DEFAULT 'ACTIVE' COMMENT 'ACTIVE/DISABLED',
  creator_id VARCHAR(10) COMMENT '创建人id',
  creator_name VARCHAR(50) COMMENT '创建人名称',
  modifier_id VARCHAR(10) COMMENT '修改人Id',
  modifier_name VARCHAR(50) COMMENT '修改人名称',
  create_time DATETIME COMMENT '创建时间',
  modified_time DATETIME COMMENT '修改日期',
  is_deleted BIT DEFAULT b'0' COMMENT '删除标记'
) COMMENT='授权模板';

CREATE UNIQUE INDEX uidx_grant_template_code ON sys_grant_template(template_code);

-- 授权变更留痕（与授权写入同事务，支持审计与回滚）
CREATE TABLE sys_grant_change (
  id VARCHAR(32) PRIMARY KEY,
  actor_id VARCHAR(20) COMMENT '操作人工号',
  actor_tenant VARCHAR(20) COMMENT '操作人租户',
  reason VARCHAR(255) COMMENT '变更原因',
  target_type VARCHAR(20) COMMENT 'ROLE/USER/POSITION/APP/RESOURCE',
  target_id VARCHAR(64) COMMENT '变更目标id',
  before_json LONGTEXT COMMENT '变更前 JSON',
  after_json LONGTEXT COMMENT '变更后 JSON',
  authz_version BIGINT COMMENT '变更后的全局授权版本',
  request_id VARCHAR(64) COMMENT '请求/追踪 id',
  create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间'
) COMMENT='授权变更留痕';

CREATE INDEX idx_grant_change_target ON sys_grant_change(target_type, target_id, create_time);
CREATE INDEX idx_grant_change_time ON sys_grant_change(create_time);

-- 授权版本（GLOBAL 或租户维度），每次授权变更单调递增
CREATE TABLE sys_authz_version (
  id VARCHAR(32) PRIMARY KEY,
  scope VARCHAR(50) NOT NULL COMMENT 'GLOBAL 或租户编码',
  version BIGINT NOT NULL DEFAULT 1 COMMENT '当前版本号',
  updated_time DATETIME COMMENT '更新时间'
) COMMENT='授权版本';

CREATE INDEX idx_authz_version_scope ON sys_authz_version(scope);
