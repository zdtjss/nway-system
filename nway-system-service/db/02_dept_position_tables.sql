-- ========================================
-- 02. 部门 + 岗位 + 跨部门任职表（DDL）
--   来源：原 02_dept_position.sql 建表部分（初始化数据已移至 20_demo_org_data.sql）
-- ========================================

-- 部门表（层级编码设计）
create table sys_dept (
	id varchar(20) primary key comment '主键',
	dept_code varchar(20) NOT NULL comment '层级编码，每级2位：01, 0101, 010102',
	dept_name varchar(50) NOT NULL comment '部门名称',
	parent_id varchar(20) DEFAULT '' comment '上级部门id，顶级为空串',
	sort int DEFAULT 0 comment '同级排序',
	status varchar(18) DEFAULT 'using' comment '状态：using/disabled',
	tenant_code varchar(20) comment '租户编码',
	creator_id varchar(10) COMMENT '创建人id',
	creator_name varchar(10) COMMENT '创建人名称',
	modifier_id varchar(10) COMMENT '修改人Id',
	modifier_name varchar(10) COMMENT '修改人名称',
	create_time datetime COMMENT '创建时间',
	modified_time datetime COMMENT '修改日期 默认为当前时间',
	is_deleted bit DEFAULT b'0' COMMENT '删除标记'
) COMMENT='部门信息（层级编码设计）';

create UNIQUE index uidx_dept_code on sys_dept(dept_code);
create index idx_parent_id on sys_dept(parent_id);

-- 跨部门任职表
--   注：本表为纯关联表，唯一索引 uidx_user_dept 不区分 is_deleted，
--       代码层删除必须物理删除（见 SysUserDeptDao），否则重新任职同一部门会撞唯一键
CREATE TABLE sys_user_dept (
  id VARCHAR(20) PRIMARY KEY,
  employee_id VARCHAR(20) NOT NULL comment '工号',
  dept_id VARCHAR(20) NOT NULL comment '部门id',
  is_primary BIT DEFAULT b'0' COMMENT '是否主部门（唯一）',
  tenant_code VARCHAR(20) comment '租户编码',
  creator_id VARCHAR(10) comment '创建人id',
  create_time DATETIME comment '创建时间',
  is_deleted BIT DEFAULT b'0' COMMENT '删除标记'
) COMMENT='用户部门关系（跨部门任职）';

CREATE UNIQUE INDEX uidx_user_dept ON sys_user_dept(employee_id, dept_id);
CREATE INDEX idx_dept_id ON sys_user_dept(dept_id);

-- 岗位表（岗位=部门下的岗位编制实例，dept_id 非空，岗位与部门唯一挂钩）
CREATE TABLE sys_position (
  id VARCHAR(20) PRIMARY KEY,
  position_code VARCHAR(50) NOT NULL COMMENT '岗位编码（全局唯一）',
  position_name VARCHAR(50) NOT NULL COMMENT '岗位名称',
  dept_id VARCHAR(20) NOT NULL COMMENT '所属部门（岗位唯一归属组织）',
  position_level VARCHAR(20) DEFAULT '' COMMENT '岗位职级（可用于审批层级）',
  status VARCHAR(18) DEFAULT 'using' comment '状态：using/disabled',
  remark VARCHAR(255) comment '备注',
  tenant_code VARCHAR(20) comment '租户编码',
  creator_id VARCHAR(10) COMMENT '创建人id',
  creator_name VARCHAR(10) COMMENT '创建人名称',
  modifier_id VARCHAR(10) COMMENT '修改人Id',
  modifier_name VARCHAR(10) COMMENT '修改人名称',
  create_time DATETIME COMMENT '创建时间',
  modified_time DATETIME COMMENT '修改日期',
  is_deleted BIT DEFAULT b'0' COMMENT '删除标记'
) COMMENT='岗位';

CREATE UNIQUE INDEX uidx_position_code ON sys_position(position_code);
CREATE INDEX idx_position_dept ON sys_position(dept_id);

-- 用户岗位关联表
CREATE TABLE sys_user_position (
  id VARCHAR(20) PRIMARY KEY,
  employee_id VARCHAR(20) NOT NULL comment '工号',
  position_id VARCHAR(20) NOT NULL comment '岗位id',
  dept_id VARCHAR(20) COMMENT '在哪个部门担任此岗位（NULL=全局）',
  is_primary BIT DEFAULT b'0' COMMENT '是否主岗',
  tenant_code VARCHAR(20) comment '租户编码',
  creator_id VARCHAR(10) comment '创建人id',
  create_time DATETIME comment '创建时间',
  is_deleted BIT DEFAULT b'0' COMMENT '删除标记'
) COMMENT='用户岗位关联';

CREATE INDEX idx_employee ON sys_user_position(employee_id);
CREATE INDEX idx_position ON sys_user_position(position_id);
CREATE INDEX idx_dept ON sys_user_position(dept_id);
