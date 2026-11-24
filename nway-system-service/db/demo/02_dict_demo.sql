-- ========================================
-- DEMO-02. 【演示数据】业务字典（DML）
--   业务场景：岗位职级 / 岗位名称演示字典（对应 05_dict_config.sql）
--   非登录/权限必需，仅用于演示与联调；生产环境可按需跳过
--   前置：01_platform_tenant_app.sql、05_dict_config.sql
--   注意：sys_dict_item 为最终版结构（item_value JSON 存储，无 item_key/item_text 列）
-- ========================================

-- ========================================
-- DEMO-02.1 岗位职级字典
--   字典编码：position_level（传统字典项模式，无字段定义）
--   职级：管理岗 M1~M5，技术岗 P1~P5
-- ========================================

-- 字典类别
INSERT INTO sys_dict_type (id, dict_code, dict_name, remark, status, tenant_code, creator_id, creator_name, create_time, is_deleted)
VALUES ('dt_position_level', 'position_level', '岗位职级', '岗位职级：管理岗M序列、技术岗P序列', 'using', 'default', '001', '测试员', NOW(), 0);

-- 字典项：管理岗 M1~M5
INSERT INTO sys_dict_item (id, dict_type_id, item_value, sort, status, tenant_code, creator_id, creator_name, create_time, is_deleted) VALUES
('di_pl_m1', 'dt_position_level', '{"key":"M1","value":"管理M1"}', 1, 'using', 'default', '001', '测试员', NOW(), 0),
('di_pl_m2', 'dt_position_level', '{"key":"M2","value":"管理M2"}', 2, 'using', 'default', '001', '测试员', NOW(), 0),
('di_pl_m3', 'dt_position_level', '{"key":"M3","value":"管理M3"}', 3, 'using', 'default', '001', '测试员', NOW(), 0),
('di_pl_m4', 'dt_position_level', '{"key":"M4","value":"管理M4"}', 4, 'using', 'default', '001', '测试员', NOW(), 0),
('di_pl_m5', 'dt_position_level', '{"key":"M5","value":"管理M5"}', 5, 'using', 'default', '001', '测试员', NOW(), 0);

-- 字典项：技术岗 P1~P5
INSERT INTO sys_dict_item (id, dict_type_id, item_value, sort, status, tenant_code, creator_id, creator_name, create_time, is_deleted) VALUES
('di_pl_p1', 'dt_position_level', '{"key":"P1","value":"技术P1"}', 6, 'using', 'default', '001', '测试员', NOW(), 0),
('di_pl_p2', 'dt_position_level', '{"key":"P2","value":"技术P2"}', 7, 'using', 'default', '001', '测试员', NOW(), 0),
('di_pl_p3', 'dt_position_level', '{"key":"P3","value":"技术P3"}', 8, 'using', 'default', '001', '测试员', NOW(), 0),
('di_pl_p4', 'dt_position_level', '{"key":"P4","value":"技术P4"}', 9, 'using', 'default', '001', '测试员', NOW(), 0),
('di_pl_p5', 'dt_position_level', '{"key":"P5","value":"技术P5"}', 10, 'using', 'default', '001', '测试员', NOW(), 0);

-- ========================================
-- DEMO-02.2 岗位名称字典
--   字典编码：position_name（传统字典项模式，无字段定义）
--   预置公司常见岗位，key 与 value 相同（存储值即岗位名称）
-- ========================================

-- 字典类别
INSERT INTO sys_dict_type (id, dict_code, dict_name, remark, status, tenant_code, creator_id, creator_name, create_time, is_deleted)
VALUES ('dt_position_name', 'position_name', '岗位名称', '公司常见岗位名称', 'using', 'default', '001', '测试员', NOW(), 0);

-- 字典项：常见岗位
INSERT INTO sys_dict_item (id, dict_type_id, item_value, sort, status, tenant_code, creator_id, creator_name, create_time, is_deleted) VALUES
('di_pn_01', 'dt_position_name', '{"key":"总监","value":"总监"}', 1, 'using', 'default', '001', '测试员', NOW(), 0),
('di_pn_02', 'dt_position_name', '{"key":"总经理","value":"总经理"}', 2, 'using', 'default', '001', '测试员', NOW(), 0),
('di_pn_03', 'dt_position_name', '{"key":"副总经理","value":"副总经理"}', 3, 'using', 'default', '001', '测试员', NOW(), 0),
('di_pn_04', 'dt_position_name', '{"key":"部门经理","value":"部门经理"}', 4, 'using', 'default', '001', '测试员', NOW(), 0),
('di_pn_05', 'dt_position_name', '{"key":"部门副经理","value":"部门副经理"}', 5, 'using', 'default', '001', '测试员', NOW(), 0),
('di_pn_06', 'dt_position_name', '{"key":"主管","value":"主管"}', 6, 'using', 'default', '001', '测试员', NOW(), 0),
('di_pn_07', 'dt_position_name', '{"key":"组长","value":"组长"}', 7, 'using', 'default', '001', '测试员', NOW(), 0),
('di_pn_08', 'dt_position_name', '{"key":"高级工程师","value":"高级工程师"}', 8, 'using', 'default', '001', '测试员', NOW(), 0),
('di_pn_09', 'dt_position_name', '{"key":"工程师","value":"工程师"}', 9, 'using', 'default', '001', '测试员', NOW(), 0),
('di_pn_10', 'dt_position_name', '{"key":"助理工程师","value":"助理工程师"}', 10, 'using', 'default', '001', '测试员', NOW(), 0),
('di_pn_11', 'dt_position_name', '{"key":"专员","value":"专员"}', 11, 'using', 'default', '001', '测试员', NOW(), 0),
('di_pn_12', 'dt_position_name', '{"key":"人事专员","value":"人事专员"}', 12, 'using', 'default', '001', '测试员', NOW(), 0),
('di_pn_13', 'dt_position_name', '{"key":"财务专员","value":"财务专员"}', 13, 'using', 'default', '001', '测试员', NOW(), 0),
('di_pn_14', 'dt_position_name', '{"key":"行政专员","value":"行政专员"}', 14, 'using', 'default', '001', '测试员', NOW(), 0),
('di_pn_15', 'dt_position_name', '{"key":"销售代表","value":"销售代表"}', 15, 'using', 'default', '001', '测试员', NOW(), 0),
('di_pn_16', 'dt_position_name', '{"key":"客服专员","value":"客服专员"}', 16, 'using', 'default', '001', '测试员', NOW(), 0),
('di_pn_17', 'dt_position_name', '{"key":"助理","value":"助理"}', 17, 'using', 'default', '001', '测试员', NOW(), 0),
('di_pn_18', 'dt_position_name', '{"key":"秘书","value":"秘书"}', 18, 'using', 'default', '001', '测试员', NOW(), 0);
