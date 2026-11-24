-- ========================================
-- 02. 权限与导航：角色、菜单、资源、统一授权（DDL + 视图 + 基线数据）
--   覆盖：
--     - 角色与用户角色关联：sys_role / sys_user_role
--     - 导航菜单与收藏：sys_menu / sys_favorite_menu
--     - 管理员授权租户：sys_admin_tenant
--     - 权限资源与统一授权：sys_resource / sys_resource_action / sys_grant /
--       sys_grant_template / sys_grant_change / sys_authz_version
--     - 视图：v_sys_user_role
--     - 基线数据：系统管理员角色、nway-system 平台导航菜单、管理员全量授权、全局授权版本
--   前置：01_platform_tenant_app.sql（租户 default 与应用 nway-system）
--   说明：
--     - 权限判定的真实物理存储为 sys_grant，sys_permission_policy 已废弃，
--       运行时不再读写（PermissionPolicyDao 已全部委托 GrantDao）
--     - 启动时 ResourceCatalogSynchronizer 会扫描 @RequiresPermission 并补登记资源/动作
--     - 平台基础导航（组织架构、权限管理、平台配置、系统监控、开放平台等页面入口）
--       统一在本文件维护；AI / BPM 等业务场景的导航在各自场景文件维护
--     - 管理员工号 admin 的角色绑定在 04_identity_user.sql 中初始化
-- ========================================

-- 角色表
CREATE TABLE sys_role (
    id VARCHAR(20) PRIMARY KEY COMMENT '主键',
    role_name VARCHAR(50) COMMENT '角色名称',
    role_code VARCHAR(50) COMMENT '角色编码',
    app_code VARCHAR(50) COMMENT '所属应用（*=平台级跨应用角色）',
    description VARCHAR(255) COMMENT '角色描述',
    tenant_code VARCHAR(20) COMMENT '租户编码',
    creator_id VARCHAR(10) COMMENT '创建人id',
    creator_name VARCHAR(10) COMMENT '创建人名称',
    modifier_id VARCHAR(10) COMMENT '修改人Id',
    modifier_name VARCHAR(10) COMMENT '修改人名称',
    create_time DATETIME COMMENT '创建时间',
    modified_time DATETIME COMMENT '修改日期',
    is_deleted BIT DEFAULT b'0' COMMENT '删除标记'
) COMMENT='角色';

CREATE UNIQUE INDEX uidx_role_code_app_tenant ON sys_role(role_code, app_code, tenant_code);

-- 用户角色关联表
CREATE TABLE sys_user_role (
    id VARCHAR(20) PRIMARY KEY COMMENT '主键',
    employee_id VARCHAR(20) COMMENT '工号',
    role_id VARCHAR(20) COMMENT '角色id',
    tenant_code VARCHAR(20) COMMENT '租户编码（冗余）',
    creator_id VARCHAR(10),
    create_time DATETIME,
    is_deleted BIT DEFAULT b'0'
) COMMENT='用户角色关联';

CREATE INDEX idx_user_role_employee ON sys_user_role(employee_id);
CREATE INDEX idx_user_role_role ON sys_user_role(role_id);
CREATE INDEX idx_user_role_tenant ON sys_user_role(tenant_code);

-- 菜单表（纯导航，权限由 sys_grant 控制）
CREATE TABLE sys_menu (
    id VARCHAR(20) PRIMARY KEY,
    parent_id VARCHAR(20) DEFAULT '' COMMENT '上级菜单id',
    name VARCHAR(50) NOT NULL COMMENT '菜单名称',
    url VARCHAR(256) COMMENT '页面路由路径（相对于 app.host）',
    render_type VARCHAR(50) COMMENT '渲染类型：native/microApp/microApp-iframe/wujie/wujie-iframe/iFrame',
    icon VARCHAR(256) COMMENT '图标',
    app_code VARCHAR(50) NOT NULL COMMENT '所属应用',
    resource_code VARCHAR(50) COMMENT '资源标识（PAGE类型必填）',
    resource_id VARCHAR(32) COMMENT '权限资源id（sys_resource.id），导航引用资源，权限语义以资源目录为准',
    type VARCHAR(36) NOT NULL COMMENT 'DIR:目录, PAGE:页面, BTN:按钮',
    action VARCHAR(50) COMMENT '操作标识（BTN类型必填）',
    sequence TINYINT UNSIGNED DEFAULT 0 COMMENT '排序',
    is_enabled BIT DEFAULT b'1' COMMENT '是否启用',
    source VARCHAR(10) DEFAULT 'PLATFORM' COMMENT '来源：PLATFORM/APP/LOWCODE',
    external_id VARCHAR(64) COMMENT '外部唯一标识（低代码菜单同步幂等键，仅 source=LOWCODE 使用）',
    visible BIT DEFAULT b'1' COMMENT '是否在导航中显示（0=隐藏页面：可路由可授权但不进菜单）',
    tenant_code VARCHAR(20) COMMENT '菜单归属租户，语义随 source 分叉：LOWCODE=页面租户（页面与数据同租户）；PLATFORM/APP=资源归属租户（default/public，对所有租户只读可见）',
    creator_id VARCHAR(10),
    creator_name VARCHAR(10),
    modifier_id VARCHAR(10),
    modifier_name VARCHAR(10),
    create_time DATETIME,
    modified_time DATETIME,
    is_deleted BIT DEFAULT b'0'
) COMMENT='菜单（纯导航，权限由 sys_grant 控制）';

CREATE INDEX idx_menu_app_resource ON sys_menu(app_code, resource_code);
CREATE INDEX idx_menu_parent ON sys_menu(parent_id);
-- 低代码菜单同步幂等键：同一应用内 external_id 唯一（NULL 不参与唯一约束）
CREATE UNIQUE INDEX uidx_menu_app_external ON sys_menu(app_code, external_id);

-- 常用菜单收藏
CREATE TABLE sys_favorite_menu (
    id VARCHAR(20) PRIMARY KEY COMMENT '主键',
    user_id VARCHAR(10) COMMENT '工号',
    menu_id VARCHAR(20) COMMENT '菜单id',
    fav_type VARCHAR(36) COMMENT '收藏类型 menu：菜单  index:首页',
    seq TINYINT UNSIGNED COMMENT '顺序'
) COMMENT='常用菜单';

-- 管理员授权租户
--   平台管理员身份：sys_user.is_admin
--   授权维度：仅租户（管理某租户即可管理该租户下全部应用、角色、菜单、用户）
--   说明：授权表不继承租户隔离，仅超管可维护
create table sys_admin_tenant (
	id varchar(20) primary key comment '主键',
	employee_id varchar(20) comment '管理员工号',
	tenant_code varchar(20) comment '可管理的租户编码'
) COMMENT='管理员授权租户';

create unique index uidx_admin_tenant on sys_admin_tenant(employee_id, tenant_code);

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

-- 用户角色视图
CREATE OR REPLACE VIEW v_sys_user_role AS
SELECT a.*, b.role_id
FROM sys_role a
    LEFT JOIN sys_user_role b ON a.id = b.role_id;

-- ========================================
-- 基线数据
-- ========================================

-- 默认管理员角色
INSERT INTO sys_role (id, role_name, role_code, app_code, description, tenant_code, creator_id, creator_name, create_time, is_deleted)
VALUES ('r_admin', '系统管理员', 'admin', '*', '平台级管理员角色，拥有所有应用的全部权限', 'default', '001', '系统', NOW(), 0);

-- 平台导航 —— 目录分组
INSERT INTO sys_menu (id, parent_id, name, url, icon, app_code, resource_code, type, action, sequence, is_enabled, source, tenant_code, creator_id, creator_name, create_time, is_deleted) VALUES
('m_sys_grp_org',     '', '组织架构', '', 'team',               'nway-system', NULL,  'DIR', NULL, 1, 1, 'PLATFORM', 'default', '001', '系统', NOW(), 0),
('m_sys_grp_perm',    '', '权限管理', '', 'safety-certificate', 'nway-system', NULL,  'DIR', NULL, 2, 1, 'PLATFORM', 'default', '001', '系统', NOW(), 0),
('m_sys_grp_config',  '', '平台配置', '', 'setting',            'nway-system', NULL,  'DIR', NULL, 3, 1, 'PLATFORM', 'default', '001', '系统', NOW(), 0),
('m_sys_open',        '', '开放平台', '', 'connection',         'nway-system', NULL,  'DIR', NULL, 4, 1, 'PLATFORM', 'default', '001', '系统', NOW(), 0),
('m_sys_grp_monitor', '', '系统监控', '', 'monitor',            'nway-system', NULL,  'DIR', NULL, 5, 1, 'PLATFORM', 'default', '001', '系统', NOW(), 0);

-- 平台导航 —— 组织架构
INSERT INTO sys_menu (id, parent_id, name, url, icon, app_code, resource_code, type, action, sequence, is_enabled, source, tenant_code, creator_id, creator_name, create_time, is_deleted) VALUES
('m_sys_user',     'm_sys_grp_org', '用户管理',   '/views/system/user/userList',         'user',     'nway-system', 'user',     'PAGE', NULL, 1, 1, 'PLATFORM', 'default', '001', '系统', NOW(), 0),
('m_sys_dept',     'm_sys_grp_org', '部门管理',   '/views/system/dept/deptList',         'apartment','nway-system', 'dept',     'PAGE', NULL, 2, 1, 'PLATFORM', 'default', '001', '系统', NOW(), 0),
('m_sys_position', 'm_sys_grp_org', '岗位管理',   '/views/system/position/positionList', 'id-card',  'nway-system', 'position', 'PAGE', NULL, 3, 1, 'PLATFORM', 'default', '001', '系统', NOW(), 0);

-- 平台导航 —— 权限管理
INSERT INTO sys_menu (id, parent_id, name, url, icon, app_code, resource_code, type, action, sequence, is_enabled, source, tenant_code, creator_id, creator_name, create_time, is_deleted) VALUES
('m_sys_role',  'm_sys_grp_perm', '角色管理',   '/views/system/role/roleList',           'user-group', 'nway-system', 'role',   'PAGE', NULL, 1, 1, 'PLATFORM', 'default', '001', '系统', NOW(), 0),
('m_sys_menu',  'm_sys_grp_perm', '菜单管理',   '/views/system/menu/menuList',           'menu',       'nway-system', 'menu',   'PAGE', NULL, 2, 1, 'PLATFORM', 'default', '001', '系统', NOW(), 0),
('m_sys_admin', 'm_sys_grp_perm', '管理员管理', '/views/system/admin/adminList',         'key',        'nway-system', 'admin',  'PAGE', NULL, 3, 1, 'PLATFORM', 'default', '001', '系统', NOW(), 0),
('m_sys_authz', 'm_sys_grp_perm', '授权工作台', '/views/system/authz/authzWorkbench',    'lock',       'nway-system', 'role',   'PAGE', NULL, 4, 1, 'PLATFORM', 'default', '001', '系统', NOW(), 0);

-- 平台导航 —— 平台配置
INSERT INTO sys_menu (id, parent_id, name, url, icon, app_code, resource_code, type, action, sequence, is_enabled, source, tenant_code, creator_id, creator_name, create_time, is_deleted) VALUES
('m_sys_tenant',     'm_sys_grp_config', '租户管理',   '/views/system/tenant/tenantList',          'home-filled', 'nway-system', 'tenant',     'PAGE', NULL, 1, 1, 'PLATFORM', 'default', '001', '系统', NOW(), 0),
('m_sys_app',        'm_sys_grp_config', '应用管理',   '/views/system/app/appList',                'grid',        'nway-system', 'app',        'PAGE', NULL, 2, 1, 'PLATFORM', 'default', '001', '系统', NOW(), 0),
('m_sys_dict',       'm_sys_grp_config', '字典管理',   '/views/system/dict/dictTypeList',          'collection',  'nway-system', 'dict',       'PAGE', NULL, 3, 1, 'PLATFORM', 'default', '001', '系统', NOW(), 0),
('m_sys_msg',        'm_sys_grp_config', '消息管理',   '/views/system/message/messageList',        'message',     'nway-system', 'message',    'PAGE', NULL, 4, 1, 'PLATFORM', 'default', '001', '系统', NOW(), 0),
('m_sys_sys_config', 'm_sys_grp_config', '系统配置',   '/views/system/sysConfig/sysConfigList',    'setting',     'nway-system', 'sys_config', 'PAGE', NULL, 5, 1, 'PLATFORM', 'default', '001', '系统', NOW(), 0),
('m_sys_file',       'm_sys_grp_config', '文件管理',   '/views/system/file/fileList',              'folder',      'nway-system', 'file',       'PAGE', NULL, 6, 1, 'PLATFORM', 'default', '001', '系统', NOW(), 0);

-- 平台导航 —— 系统监控
INSERT INTO sys_menu (id, parent_id, name, url, icon, app_code, resource_code, type, action, sequence, is_enabled, source, tenant_code, creator_id, creator_name, create_time, is_deleted) VALUES
('m_sys_online_user', 'm_sys_grp_monitor', '在线用户', '/views/system/onlineUser/onlineUserList', 'monitor',  'nway-system', 'online_user', 'PAGE', NULL, 1, 1, 'PLATFORM', 'default', '001', '系统', NOW(), 0),
('m_sys_audit',       'm_sys_grp_monitor', '审计日志', '/views/system/auditLog/auditLogList',    'document', 'nway-system', 'audit_log',   'PAGE', NULL, 2, 1, 'PLATFORM', 'default', '001', '系统', NOW(), 0);

-- 平台导航 —— 开放平台
INSERT INTO sys_menu (id, parent_id, name, url, icon, app_code, resource_code, type, action, sequence, is_enabled, source, tenant_code, creator_id, creator_name, create_time, is_deleted) VALUES
('m_sys_open_app',     'm_sys_open', '应用接入管理', '/views/system/openplatform/appList',     'grid',       'nway-system', 'open_app',     'PAGE', NULL, 1, 1, 'PLATFORM', 'default', '001', '系统', NOW(), 0),
('m_sys_open_log',     'm_sys_open', '调用日志',     '/views/system/openplatform/apiLogList',  'document',   'nway-system', 'open_api_log', 'PAGE', NULL, 2, 1, 'PLATFORM', 'default', '001', '系统', NOW(), 0),
('m_sys_open_webhook', 'm_sys_open', 'Webhook订阅',  '/views/system/openplatform/webhookList', 'connection', 'nway-system', 'open_webhook', 'PAGE', NULL, 3, 1, 'PLATFORM', 'default', '001', '系统', NOW(), 0),
('m_sys_open_stats',   'm_sys_open', '调用统计',     '/views/system/openplatform/apiStats',    'data-line',  'nway-system', 'open_api_log', 'PAGE', NULL, 4, 1, 'PLATFORM', 'default', '001', '系统', NOW(), 0);

-- 管理员角色的授权（基线应用 nway-system 的全部资源全部操作 + ALL 数据范围）
--   注：运行时物理存储为 sys_grant（sys_permission_policy 已废弃，不再建表）；
--       AuthzDecisionService 按 app_code 精确匹配，不能用 '*' 代替应用编码；
--       actions 必填，全部动作显式写 ["*"]。新增应用需另行为该应用授权。
--       tenant_code='public' 表示平台级授权，切换工作租户后仍生效。
INSERT INTO sys_grant (id, subject_type, subject_id, app_code, resource_code, actions, effect, data_scope, status, version, tenant_code, creator_id, creator_name, create_time, is_deleted)
VALUES ('grant_admin_all', 'ROLE', 'r_admin', 'nway-system', '*', '["*"]', 'ALLOW', 'ALL', 'ACTIVE', 1, 'public', '001', '系统', NOW(), 0);

-- 全局授权版本基线（每次授权变更单调递增，缓存按此版本失效）
INSERT INTO sys_authz_version (id, scope, version, updated_time)
VALUES ('ver_global', 'GLOBAL', 1, NOW());
