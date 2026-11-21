-- ========================================
-- 07. 管理员授权（DDL）
--   平台管理员身份：sys_user.is_admin
--   授权维度：仅租户（管理某租户即可管理该租户下全部应用、角色、菜单、用户）
--   租户范围：sys_admin_tenant（管理员可管理的授权租户，多对多）
--   说明：授权表不继承租户隔离，仅超管可维护。
-- ========================================

create table sys_admin_tenant (
	id varchar(20) primary key comment '主键',
	employee_id varchar(20) comment '管理员工号',
	tenant_code varchar(20) comment '可管理的租户编码'
) COMMENT='管理员授权租户';

create unique index uidx_admin_tenant on sys_admin_tenant(employee_id, tenant_code);
