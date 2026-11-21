-- ========================================
-- 01. 用户、角色、菜单、应用核心表（DDL）
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
