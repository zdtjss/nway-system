-- ========================================
-- 05. 字典与系统配置（DDL）
--   覆盖：
--     - sys_dict_type：字典类别（通用多字段类字典）
--     - sys_dict_item：字典项（最终结构：数据统一存 item_value JSON，无 item_key/item_text 列）
--     - sys_config：系统配置（最终结构：config_name/remark/status，config_group 默认 system）
--   前置：01_platform_tenant_app.sql（tenant_code 语义）
--   说明：演示用岗位职级 / 岗位名称字典见 demo/02_dict_demo.sql（可选）
-- ========================================

-- 字典类别
create table sys_dict_type (
	id varchar(20) primary key comment '主键',
	dict_code varchar(50) NOT NULL comment '字典编码，唯一',
	dict_name varchar(50) NOT NULL comment '字典名称',
	remark varchar(255) comment '备注',
	field_defs text comment '字段定义JSON',
	status varchar(18) DEFAULT 'using' comment '状态：using/disabled',
	tenant_code varchar(20) comment '租户编码',
	creator_id varchar(10) COMMENT '创建人id',
	creator_name varchar(10) COMMENT '创建人名称',
	modifier_id varchar(10) COMMENT '修改人Id',
	modifier_name varchar(10) COMMENT '修改人名称',
	create_time datetime COMMENT '创建时间',
	modified_time datetime COMMENT '修改日期',
	is_deleted bit DEFAULT b'0' COMMENT '删除标记'
) COMMENT='字典类别（通用多字段类字典）';

create UNIQUE index uidx_dict_code on sys_dict_type(dict_code);

-- 字典项（最终结构：数据统一存 item_value JSON，无 item_key/item_text 列）
create table sys_dict_item (
	id varchar(20) primary key comment '主键',
	dict_type_id varchar(20) NOT NULL comment '字典类别id',
	item_value text comment '字典项值JSON：传统模式{"key":"using","value":"启用"}，自定义字段模式{"employee_id":"001","name":"张三"}',
	sort int DEFAULT 0 comment '排序',
	tenant_code varchar(20) comment '租户编码',
	status varchar(18) DEFAULT 'using' comment '状态：using/disabled',
	creator_id varchar(10) COMMENT '创建人id',
	creator_name varchar(10) COMMENT '创建人名称',
	modifier_id varchar(10) COMMENT '修改人Id',
	modifier_name varchar(10) COMMENT '修改人名称',
	create_time datetime COMMENT '创建时间',
	modified_time datetime COMMENT '修改日期',
	is_deleted bit DEFAULT b'0' COMMENT '删除标记'
) COMMENT='字典项';

create index idx_dict_type_id on sys_dict_item(dict_type_id);

-- 系统配置表（最终结构：config_name/remark/status，config_group 默认 system）
CREATE TABLE sys_config (
  id VARCHAR(20) PRIMARY KEY,
  config_key VARCHAR(100) NOT NULL COMMENT '配置键',
  config_value TEXT COMMENT '配置值',
  config_name VARCHAR(100) NOT NULL COMMENT '配置名称',
  config_group VARCHAR(50) DEFAULT 'system' COMMENT '配置分组',
  remark VARCHAR(255) COMMENT '说明',
  status VARCHAR(18) DEFAULT 'using' COMMENT '状态：using/disabled',
  tenant_code VARCHAR(20) COMMENT '租户编码',
  creator_id VARCHAR(10),
  creator_name VARCHAR(10),
  modifier_id VARCHAR(10),
  modifier_name VARCHAR(10),
  create_time DATETIME,
  modified_time DATETIME,
  is_deleted BIT DEFAULT b'0'
) COMMENT='系统配置';

CREATE UNIQUE INDEX uidx_config_key ON sys_config(config_key);
CREATE INDEX idx_config_group ON sys_config(config_group);
