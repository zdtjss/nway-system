-- ========================================
-- 11. 核心初始化数据（必要数据）
--   默认租户 + 公共租户 + 应用 + 默认角色 + 系统管理菜单 + 管理员授权 + 默认账号
-- ========================================

-- 0. 默认租户（所有初始化资源的 tenant_code='default' 均关联此租户，
--    租户名解析 TenantDao.getByCode('default') 依赖此记录，必须先于其他资源数据插入）
INSERT INTO sys_tenant (id, tenant_code, tenant_name, status, creator_id, creator_name, create_time, is_deleted)
VALUES ('default', 'default', '默认租户', 'using', '001', '系统', NOW(), 0);

-- 0.1 公共租户（内置共享租户：其下资源对所有租户只读共享，
--      读隔离集合在 TenantUtil.currentTenantCodesForIsolation 自动并入 'public'）
INSERT INTO sys_tenant (id, tenant_code, tenant_name, status, remark, creator_id, creator_name, create_time, is_deleted)
VALUES ('public', 'public', '公共租户', 'using', '内置公共租户：资源对所有租户只读共享', '001', '系统', NOW(), 0);

-- 1. 默认应用
INSERT INTO sys_app (id, app_code, app_name, host, status, sort, tenant_code, creator_id, creator_name, create_time, is_deleted)
VALUES ('app_system', 'nway-system', '系统管理', '/', 'using', 0, 'default', '001', '系统', NOW(), 0);

-- 2. 默认管理员角色
INSERT INTO sys_role (id, role_name, role_code, app_code, description, tenant_code, creator_id, creator_name, create_time, is_deleted)
VALUES ('r_admin', '系统管理员', 'admin', '*', '平台级管理员角色，拥有所有应用的全部权限', 'default', '001', '系统', NOW(), 0);

-- 3. 系统管理菜单 —— 目录分组
INSERT INTO sys_menu (id, parent_id, name, url, icon, app_code, resource_code, type, action, sequence, is_enabled, source, tenant_code, creator_id, creator_name, create_time, is_deleted) VALUES
('m_sys_grp_org',     '', '组织架构', '', 'team',               'nway-system', NULL,  'DIR', NULL, 1, 1, 'PLATFORM', 'default', '001', '系统', NOW(), 0),
('m_sys_grp_perm',    '', '权限管理', '', 'safety-certificate', 'nway-system', NULL,  'DIR', NULL, 2, 1, 'PLATFORM', 'default', '001', '系统', NOW(), 0),
('m_sys_grp_config',  '', '平台配置', '', 'setting',            'nway-system', NULL,  'DIR', NULL, 3, 1, 'PLATFORM', 'default', '001', '系统', NOW(), 0),
('m_sys_grp_monitor', '', '系统监控', '', 'monitor',            'nway-system', NULL,  'DIR', NULL, 5, 1, 'PLATFORM', 'default', '001', '系统', NOW(), 0);

-- 4. 系统管理菜单 —— 组织架构
INSERT INTO sys_menu (id, parent_id, name, url, icon, app_code, resource_code, type, action, sequence, is_enabled, source, tenant_code, creator_id, creator_name, create_time, is_deleted) VALUES
('m_sys_user',     'm_sys_grp_org', '用户管理',   '/views/system/user/userList',         'user',     'nway-system', 'user',     'PAGE', NULL, 1, 1, 'PLATFORM', 'default', '001', '系统', NOW(), 0),
('m_sys_dept',     'm_sys_grp_org', '部门管理',   '/views/system/dept/deptList',         'apartment','nway-system', 'dept',     'PAGE', NULL, 2, 1, 'PLATFORM', 'default', '001', '系统', NOW(), 0),
('m_sys_position', 'm_sys_grp_org', '岗位管理',   '/views/system/position/positionList', 'id-card',  'nway-system', 'position', 'PAGE', NULL, 3, 1, 'PLATFORM', 'default', '001', '系统', NOW(), 0);

-- 5. 系统管理菜单 —— 权限管理
INSERT INTO sys_menu (id, parent_id, name, url, icon, app_code, resource_code, type, action, sequence, is_enabled, source, tenant_code, creator_id, creator_name, create_time, is_deleted) VALUES
('m_sys_role',  'm_sys_grp_perm', '角色管理',   '/views/system/role/roleList',   'user-group', 'nway-system', 'role',   'PAGE', NULL, 1, 1, 'PLATFORM', 'default', '001', '系统', NOW(), 0),
('m_sys_menu',  'm_sys_grp_perm', '菜单管理',   '/views/system/menu/menuList',   'menu',       'nway-system', 'menu',   'PAGE', NULL, 2, 1, 'PLATFORM', 'default', '001', '系统', NOW(), 0),
('m_sys_admin', 'm_sys_grp_perm', '管理员管理', '/views/system/admin/adminList', 'key',        'nway-system', 'admin',  'PAGE', NULL, 3, 1, 'PLATFORM', 'default', '001', '系统', NOW(), 0);

-- 6. 系统管理菜单 —— 平台配置
INSERT INTO sys_menu (id, parent_id, name, url, icon, app_code, resource_code, type, action, sequence, is_enabled, source, tenant_code, creator_id, creator_name, create_time, is_deleted) VALUES
('m_sys_tenant', 'm_sys_grp_config', '租户管理',   '/views/system/tenant/tenantList', 'home-filled', 'nway-system', 'tenant', 'PAGE', NULL, 1, 1, 'PLATFORM', 'default', '001', '系统', NOW(), 0),
('m_sys_app',    'm_sys_grp_config', '应用管理',   '/views/system/app/appList',       'grid',        'nway-system', 'app',    'PAGE', NULL, 2, 1, 'PLATFORM', 'default', '001', '系统', NOW(), 0),
('m_sys_dict',   'm_sys_grp_config', '字典管理',   '/views/system/dict/dictTypeList', 'collection',  'nway-system', 'dict',   'PAGE', NULL, 3, 1, 'PLATFORM', 'default', '001', '系统', NOW(), 0),
('m_sys_msg',    'm_sys_grp_config', '消息管理',   '/views/system/message/messageList','message',    'nway-system', 'message','PAGE', NULL, 4, 1, 'PLATFORM', 'default', '001', '系统', NOW(), 0);

-- 7. 系统管理菜单 —— 系统监控
INSERT INTO sys_menu (id, parent_id, name, url, icon, app_code, resource_code, type, action, sequence, is_enabled, source, tenant_code, creator_id, creator_name, create_time, is_deleted) VALUES
('m_sys_online_user', 'm_sys_grp_monitor', '在线用户', '/views/system/onlineUser/onlineUserList', 'monitor', 'nway-system', 'online_user', 'PAGE', NULL, 1, 1, 'PLATFORM', 'default', '001', '系统', NOW(), 0);

-- 8. 管理员角色的授权（基线应用 nway-system 的全部资源全部操作 + ALL 数据范围）
--    注：运行时物理存储为 sys_grant（sys_permission_policy 已废弃，不再建表）；
--        AuthzDecisionService 按 app_code 精确匹配，不能用 '*' 代替应用编码；
--        actions 必填，全部动作显式写 ["*"]。新增应用需另行为该应用授权。
--        tenant_code='public' 表示平台级授权，切换工作租户后仍生效。
INSERT INTO sys_grant (id, subject_type, subject_id, app_code, resource_code, actions, effect, data_scope, status, version, tenant_code, creator_id, creator_name, create_time, is_deleted)
VALUES ('grant_admin_all', 'ROLE', 'r_admin', 'nway-system', '*', '["*"]', 'ALLOW', 'ALL', 'ACTIVE', 1, 'public', '001', '系统', NOW(), 0);

-- 8.1 全局授权版本基线（每次授权变更单调递增，缓存按此版本失效）
INSERT INTO sys_authz_version (id, scope, version, updated_time)
VALUES ('ver_global', 'GLOBAL', 1, NOW());

-- 9. 默认超级管理员账号（登录名: admin，密码: 1）
INSERT INTO sys_user (id, employee_id, real_name, status, is_super_admin, is_admin, tenant_code, creator_id, creator_name, create_time, is_deleted)
VALUES ('u_admin', 'admin', '超级管理员', 'using', b'1', b'1', 'default', '001', '系统', NOW(), 0);

INSERT INTO sys_user_login_key (id, employee_id, login_name, password, is_valid, tenant_code, creator_id, creator_name, create_time, is_deleted)
VALUES ('ulk_admin', 'admin', 'admin', '$2a$10$LUbTOVtOwVm6cC2Ah5ALm.XJ6twzfQ/b6EqQUwlrUomB0beZLvUie', b'1', 'default', '001', '系统', NOW(), 0);

INSERT INTO sys_user_role (id, employee_id, role_id, tenant_code, creator_id, create_time, is_deleted)
VALUES ('ur_admin', 'admin', 'r_admin', 'default', '001', NOW(), 0);
