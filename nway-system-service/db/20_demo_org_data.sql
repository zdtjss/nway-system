-- ========================================
-- 20. 【演示数据】集团组织架构（DML：部门 + 岗位）
--   非登录/权限必需，仅用于演示与联调；生产环境可按需跳过
--   来源：原 02_dept_position.sql 初始化数据部分
--   组织形态：集团总部 + 战略投资/生产制造/市场营销/技术研发/运营服务 五大事业部
--   层级编码：每级2位（01=总部，0101=总部二级部门，010101=三级部门）
--   约束：dept_code 全局唯一，重复执行会因唯一索引报错，请人工核对后执行
-- ========================================

-- ---------- 一级部门（集团顶层，parent_id 为空串） ----------
INSERT INTO sys_dept (id, dept_code, dept_name, parent_id, sort, status, tenant_code, creator_id, creator_name, create_time, is_deleted) VALUES
('dept_01', '01', '集团总部', '', 1, 'using', 'default', '001', '测试员', NOW(), 0),
('dept_02', '02', '战略投资事业部', '', 2, 'using', 'default', '001', '测试员', NOW(), 0),
('dept_03', '03', '生产制造事业部', '', 3, 'using', 'default', '001', '测试员', NOW(), 0),
('dept_04', '04', '市场营销中心', '', 4, 'using', 'default', '001', '测试员', NOW(), 0),
('dept_05', '05', '技术研发中心', '', 5, 'using', 'default', '001', '测试员', NOW(), 0),
('dept_06', '06', '运营服务中心', '', 6, 'using', 'default', '001', '测试员', NOW(), 0);

-- ---------- 二级部门：集团总部 ----------
INSERT INTO sys_dept (id, dept_code, dept_name, parent_id, sort, status, tenant_code, creator_id, creator_name, create_time, is_deleted) VALUES
('dept_0101', '0101', '董事会办公室', 'dept_01', 1, 'using', 'default', '001', '测试员', NOW(), 0),
('dept_0102', '0102', '总裁办公室', 'dept_01', 2, 'using', 'default', '001', '测试员', NOW(), 0),
('dept_0103', '0103', '人力资源中心', 'dept_01', 3, 'using', 'default', '001', '测试员', NOW(), 0),
('dept_0104', '0104', '财务管理中心', 'dept_01', 4, 'using', 'default', '001', '测试员', NOW(), 0),
('dept_0105', '0105', '资金管理中心', 'dept_01', 5, 'using', 'default', '001', '测试员', NOW(), 0),
('dept_0106', '0106', '法务合规部', 'dept_01', 6, 'using', 'default', '001', '测试员', NOW(), 0),
('dept_0107', '0107', '审计监察部', 'dept_01', 7, 'using', 'default', '001', '测试员', NOW(), 0),
('dept_0108', '0108', '信息技术中心', 'dept_01', 8, 'using', 'default', '001', '测试员', NOW(), 0),
('dept_0109', '0109', '行政管理部', 'dept_01', 9, 'using', 'default', '001', '测试员', NOW(), 0),
('dept_0110', '0110', '市场品牌部', 'dept_01', 10, 'using', 'default', '001', '测试员', NOW(), 0),
('dept_0111', '0111', '证券投资部', 'dept_01', 11, 'using', 'default', '001', '测试员', NOW(), 0),
('dept_0112', '0112', '党群工作部', 'dept_01', 12, 'using', 'default', '001', '测试员', NOW(), 0);

-- ---------- 三级部门：人力资源中心 ----------
INSERT INTO sys_dept (id, dept_code, dept_name, parent_id, sort, status, tenant_code, creator_id, creator_name, create_time, is_deleted) VALUES
('dept_010301', '010301', '招聘配置部', 'dept_0103', 1, 'using', 'default', '001', '测试员', NOW(), 0),
('dept_010302', '010302', '培训发展部', 'dept_0103', 2, 'using', 'default', '001', '测试员', NOW(), 0),
('dept_010303', '010303', '薪酬绩效部', 'dept_0103', 3, 'using', 'default', '001', '测试员', NOW(), 0),
('dept_010304', '010304', '员工关系部', 'dept_0103', 4, 'using', 'default', '001', '测试员', NOW(), 0);

-- ---------- 三级部门：财务管理中心 ----------
INSERT INTO sys_dept (id, dept_code, dept_name, parent_id, sort, status, tenant_code, creator_id, creator_name, create_time, is_deleted) VALUES
('dept_010401', '010401', '会计核算部', 'dept_0104', 1, 'using', 'default', '001', '测试员', NOW(), 0),
('dept_010402', '010402', '预算管理部', 'dept_0104', 2, 'using', 'default', '001', '测试员', NOW(), 0),
('dept_010403', '010403', '税务管理部', 'dept_0104', 3, 'using', 'default', '001', '测试员', NOW(), 0),
('dept_010404', '010404', '财务报告部', 'dept_0104', 4, 'using', 'default', '001', '测试员', NOW(), 0);

-- ---------- 三级部门：信息技术中心 ----------
INSERT INTO sys_dept (id, dept_code, dept_name, parent_id, sort, status, tenant_code, creator_id, creator_name, create_time, is_deleted) VALUES
('dept_010801', '010801', '系统开发部', 'dept_0108', 1, 'using', 'default', '001', '测试员', NOW(), 0),
('dept_010802', '010802', '系统运维部', 'dept_0108', 2, 'using', 'default', '001', '测试员', NOW(), 0),
('dept_010803', '010803', '网络安全部', 'dept_0108', 3, 'using', 'default', '001', '测试员', NOW(), 0),
('dept_010804', '010804', '数据管理部', 'dept_0108', 4, 'using', 'default', '001', '测试员', NOW(), 0);

-- ---------- 二级部门：战略投资事业部 ----------
INSERT INTO sys_dept (id, dept_code, dept_name, parent_id, sort, status, tenant_code, creator_id, creator_name, create_time, is_deleted) VALUES
('dept_0201', '0201', '战略规划部', 'dept_02', 1, 'using', 'default', '001', '测试员', NOW(), 0),
('dept_0202', '0202', '投资管理部', 'dept_02', 2, 'using', 'default', '001', '测试员', NOW(), 0),
('dept_0203', '0203', '行业研究部', 'dept_02', 3, 'using', 'default', '001', '测试员', NOW(), 0),
('dept_0204', '0204', '并购重组部', 'dept_02', 4, 'using', 'default', '001', '测试员', NOW(), 0),
('dept_0205', '0205', '产业协同部', 'dept_02', 5, 'using', 'default', '001', '测试员', NOW(), 0);

-- ---------- 二级部门：生产制造事业部 ----------
INSERT INTO sys_dept (id, dept_code, dept_name, parent_id, sort, status, tenant_code, creator_id, creator_name, create_time, is_deleted) VALUES
('dept_0301', '0301', '生产管理部', 'dept_03', 1, 'using', 'default', '001', '测试员', NOW(), 0),
('dept_0302', '0302', '质量管理部', 'dept_03', 2, 'using', 'default', '001', '测试员', NOW(), 0),
('dept_0303', '0303', '供应链管理部', 'dept_03', 3, 'using', 'default', '001', '测试员', NOW(), 0),
('dept_0304', '0304', '设备动力部', 'dept_03', 4, 'using', 'default', '001', '测试员', NOW(), 0),
('dept_0305', '0305', '安全环保部', 'dept_03', 5, 'using', 'default', '001', '测试员', NOW(), 0),
('dept_0306', '0306', '计划物控部', 'dept_03', 6, 'using', 'default', '001', '测试员', NOW(), 0),
('dept_0307', '0307', '制造一厂', 'dept_03', 7, 'using', 'default', '001', '测试员', NOW(), 0),
('dept_0308', '0308', '制造二厂', 'dept_03', 8, 'using', 'default', '001', '测试员', NOW(), 0),
('dept_0309', '0309', '制造三厂', 'dept_03', 9, 'using', 'default', '001', '测试员', NOW(), 0);

-- ---------- 三级部门：供应链管理部 ----------
INSERT INTO sys_dept (id, dept_code, dept_name, parent_id, sort, status, tenant_code, creator_id, creator_name, create_time, is_deleted) VALUES
('dept_030301', '030301', '采购管理科', 'dept_0303', 1, 'using', 'default', '001', '测试员', NOW(), 0),
('dept_030302', '030302', '供应商管理科', 'dept_0303', 2, 'using', 'default', '001', '测试员', NOW(), 0),
('dept_030303', '030303', '仓储管理科', 'dept_0303', 3, 'using', 'default', '001', '测试员', NOW(), 0);

-- ---------- 三级部门：制造一厂（车间） ----------
INSERT INTO sys_dept (id, dept_code, dept_name, parent_id, sort, status, tenant_code, creator_id, creator_name, create_time, is_deleted) VALUES
('dept_030701', '030701', '冲压车间', 'dept_0307', 1, 'using', 'default', '001', '测试员', NOW(), 0),
('dept_030702', '030702', '焊装车间', 'dept_0307', 2, 'using', 'default', '001', '测试员', NOW(), 0),
('dept_030703', '030703', '涂装车间', 'dept_0307', 3, 'using', 'default', '001', '测试员', NOW(), 0),
('dept_030704', '030704', '总装车间', 'dept_0307', 4, 'using', 'default', '001', '测试员', NOW(), 0);

-- ---------- 二级部门：市场营销中心 ----------
INSERT INTO sys_dept (id, dept_code, dept_name, parent_id, sort, status, tenant_code, creator_id, creator_name, create_time, is_deleted) VALUES
('dept_0401', '0401', '国内营销部', 'dept_04', 1, 'using', 'default', '001', '测试员', NOW(), 0),
('dept_0402', '0402', '海外营销部', 'dept_04', 2, 'using', 'default', '001', '测试员', NOW(), 0),
('dept_0403', '0403', '大客户部', 'dept_04', 3, 'using', 'default', '001', '测试员', NOW(), 0),
('dept_0404', '0404', '渠道管理部', 'dept_04', 4, 'using', 'default', '001', '测试员', NOW(), 0),
('dept_0405', '0405', '客户服务部', 'dept_04', 5, 'using', 'default', '001', '测试员', NOW(), 0),
('dept_0406', '0406', '品牌推广部', 'dept_04', 6, 'using', 'default', '001', '测试员', NOW(), 0);

-- ---------- 三级部门：国内营销部（大区） ----------
INSERT INTO sys_dept (id, dept_code, dept_name, parent_id, sort, status, tenant_code, creator_id, creator_name, create_time, is_deleted) VALUES
('dept_040101', '040101', '华东大区', 'dept_0401', 1, 'using', 'default', '001', '测试员', NOW(), 0),
('dept_040102', '040102', '华南大区', 'dept_0401', 2, 'using', 'default', '001', '测试员', NOW(), 0),
('dept_040103', '040103', '华北大区', 'dept_0401', 3, 'using', 'default', '001', '测试员', NOW(), 0),
('dept_040104', '040104', '华中大区', 'dept_0401', 4, 'using', 'default', '001', '测试员', NOW(), 0),
('dept_040105', '040105', '西南大区', 'dept_0401', 5, 'using', 'default', '001', '测试员', NOW(), 0),
('dept_040106', '040106', '西北大区', 'dept_0401', 6, 'using', 'default', '001', '测试员', NOW(), 0);

-- ---------- 三级部门：客户服务部 ----------
INSERT INTO sys_dept (id, dept_code, dept_name, parent_id, sort, status, tenant_code, creator_id, creator_name, create_time, is_deleted) VALUES
('dept_040501', '040501', '售前支持科', 'dept_0405', 1, 'using', 'default', '001', '测试员', NOW(), 0),
('dept_040502', '040502', '售后服务科', 'dept_0405', 2, 'using', 'default', '001', '测试员', NOW(), 0),
('dept_040503', '040503', '投诉处理科', 'dept_0405', 3, 'using', 'default', '001', '测试员', NOW(), 0);

-- ---------- 二级部门：技术研发中心 ----------
INSERT INTO sys_dept (id, dept_code, dept_name, parent_id, sort, status, tenant_code, creator_id, creator_name, create_time, is_deleted) VALUES
('dept_0501', '0501', '产品规划部', 'dept_05', 1, 'using', 'default', '001', '测试员', NOW(), 0),
('dept_0502', '0502', '软件研发部', 'dept_05', 2, 'using', 'default', '001', '测试员', NOW(), 0),
('dept_0503', '0503', '硬件研发部', 'dept_05', 3, 'using', 'default', '001', '测试员', NOW(), 0),
('dept_0504', '0504', '测试验证部', 'dept_05', 4, 'using', 'default', '001', '测试员', NOW(), 0),
('dept_0505', '0505', '知识产权部', 'dept_05', 5, 'using', 'default', '001', '测试员', NOW(), 0),
('dept_0506', '0506', '数据智能部', 'dept_05', 6, 'using', 'default', '001', '测试员', NOW(), 0);

-- ---------- 三级部门：软件研发部 ----------
INSERT INTO sys_dept (id, dept_code, dept_name, parent_id, sort, status, tenant_code, creator_id, creator_name, create_time, is_deleted) VALUES
('dept_050201', '050201', '平台研发组', 'dept_0502', 1, 'using', 'default', '001', '测试员', NOW(), 0),
('dept_050202', '050202', '应用研发组', 'dept_0502', 2, 'using', 'default', '001', '测试员', NOW(), 0),
('dept_050203', '050203', '前端研发组', 'dept_0502', 3, 'using', 'default', '001', '测试员', NOW(), 0),
('dept_050204', '050204', '移动研发组', 'dept_0502', 4, 'using', 'default', '001', '测试员', NOW(), 0);

-- ---------- 三级部门：数据智能部 ----------
INSERT INTO sys_dept (id, dept_code, dept_name, parent_id, sort, status, tenant_code, creator_id, creator_name, create_time, is_deleted) VALUES
('dept_050601', '050601', '算法组', 'dept_0506', 1, 'using', 'default', '001', '测试员', NOW(), 0),
('dept_050602', '050602', '数据分析组', 'dept_0506', 2, 'using', 'default', '001', '测试员', NOW(), 0),
('dept_050603', '050603', '数据平台组', 'dept_0506', 3, 'using', 'default', '001', '测试员', NOW(), 0);

-- ---------- 二级部门：运营服务中心 ----------
INSERT INTO sys_dept (id, dept_code, dept_name, parent_id, sort, status, tenant_code, creator_id, creator_name, create_time, is_deleted) VALUES
('dept_0601', '0601', '运营管理部', 'dept_06', 1, 'using', 'default', '001', '测试员', NOW(), 0),
('dept_0602', '0602', '售后服务部', 'dept_06', 2, 'using', 'default', '001', '测试员', NOW(), 0),
('dept_0603', '0603', '物流仓储部', 'dept_06', 3, 'using', 'default', '001', '测试员', NOW(), 0),
('dept_0604', '0604', '商务拓展部', 'dept_06', 4, 'using', 'default', '001', '测试员', NOW(), 0),
('dept_0605', '0605', '海外运营部', 'dept_06', 5, 'using', 'default', '001', '测试员', NOW(), 0);

-- ---------- 三级部门：售后服务部 ----------
INSERT INTO sys_dept (id, dept_code, dept_name, parent_id, sort, status, tenant_code, creator_id, creator_name, create_time, is_deleted) VALUES
('dept_060201', '060201', '服务调度科', 'dept_0602', 1, 'using', 'default', '001', '测试员', NOW(), 0),
('dept_060202', '060202', '技术支援科', 'dept_0602', 2, 'using', 'default', '001', '测试员', NOW(), 0);

-- ---------- 三级部门：物流仓储部 ----------
INSERT INTO sys_dept (id, dept_code, dept_name, parent_id, sort, status, tenant_code, creator_id, creator_name, create_time, is_deleted) VALUES
('dept_060301', '060301', '仓储中心', 'dept_0603', 1, 'using', 'default', '001', '测试员', NOW(), 0),
('dept_060302', '060302', '运输调度科', 'dept_0603', 2, 'using', 'default', '001', '测试员', NOW(), 0);

-- ========================================
-- 岗位初始化数据（职级与 position_level 字典 M1~M5 / P1~P5 对齐）
--   编码规则：HM=高层管理，DV=事业部/中心负责人，DM=部门负责人，SP=基层管理
--             TE=技术序列，FN=职能序列，SL=销售序列
--   归属规则：岗位实例必须挂一个部门（dept_id 非空）；同名岗位在不同部门需分别建岗
-- ========================================
INSERT INTO sys_position (id, position_code, position_name, dept_id, position_level, status, remark, tenant_code, creator_id, creator_name, create_time, is_deleted) VALUES
('pos_hm_001', 'HM-001', '董事长', 'dept_01', 'M5', 'using', '集团最高决策人', 'default', '001', '测试员', NOW(), 0),
('pos_hm_002', 'HM-002', '总裁', 'dept_01', 'M5', 'using', '集团经营管理总负责人', 'default', '001', '测试员', NOW(), 0),
('pos_hm_003', 'HM-003', '常务副总裁', 'dept_01', 'M4', 'using', '协助总裁主持日常经营管理', 'default', '001', '测试员', NOW(), 0),
('pos_hm_004', 'HM-004', '副总裁', 'dept_01', 'M4', 'using', '分管各职能/业务条线', 'default', '001', '测试员', NOW(), 0),
('pos_hm_005', 'HM-005', '董事会秘书', 'dept_0101', 'M4', 'using', '董事会日常事务与信息披露', 'default', '001', '测试员', NOW(), 0),
('pos_dv_001', 'DV-001', '事业部总经理', 'dept_02', 'M4', 'using', '事业部经营第一负责人（各事业部可自建）', 'default', '001', '测试员', NOW(), 0),
('pos_dv_002', 'DV-002', '事业部副总经理', 'dept_02', 'M3', 'using', '协助事业部总经理（各事业部可自建）', 'default', '001', '测试员', NOW(), 0),
('pos_dv_003', 'DV-003', '中心总监', 'dept_0103', 'M3', 'using', '职能中心负责人（各中心可自建）', 'default', '001', '测试员', NOW(), 0),
('pos_dv_004', 'DV-004', '中心副总监', 'dept_0103', 'M2', 'using', '职能中心副职（各中心可自建）', 'default', '001', '测试员', NOW(), 0),
('pos_dm_001', 'DM-001', '部门经理', 'dept_010301', 'M2', 'using', '部门负责人（各部门可自建）', 'default', '001', '测试员', NOW(), 0),
('pos_dm_002', 'DM-002', '部门副经理', 'dept_010302', 'M1', 'using', '部门副职（各部门可自建）', 'default', '001', '测试员', NOW(), 0),
('pos_sp_001', 'SP-001', '主管', 'dept_010801', 'M1', 'using', '团队管理', 'default', '001', '测试员', NOW(), 0),
('pos_sp_002', 'SP-002', '组长', 'dept_050201', 'M1', 'using', '班组管理', 'default', '001', '测试员', NOW(), 0),
('pos_te_001', 'TE-001', '首席专家', 'dept_05', 'P5', 'using', '技术权威，牵头重大技术攻关', 'default', '001', '测试员', NOW(), 0),
('pos_te_002', 'TE-002', '高级工程师', 'dept_0502', 'P4', 'using', '独立承担复杂技术工作', 'default', '001', '测试员', NOW(), 0),
('pos_te_003', 'TE-003', '工程师', 'dept_0502', 'P3', 'using', '独立承担常规技术工作', 'default', '001', '测试员', NOW(), 0),
('pos_te_004', 'TE-004', '助理工程师', 'dept_0502', 'P2', 'using', '辅助技术工作', 'default', '001', '测试员', NOW(), 0),
('pos_fn_001', 'FN-001', '高级专员', 'dept_0104', 'P3', 'using', '资深职能专业岗位', 'default', '001', '测试员', NOW(), 0),
('pos_fn_002', 'FN-002', '专员', 'dept_0109', 'P2', 'using', '职能专业岗位', 'default', '001', '测试员', NOW(), 0),
('pos_fn_003', 'FN-003', '人事专员', 'dept_0103', 'P2', 'using', '人力资源日常事务', 'default', '001', '测试员', NOW(), 0),
('pos_fn_004', 'FN-004', '财务专员', 'dept_0104', 'P2', 'using', '财务核算与税务', 'default', '001', '测试员', NOW(), 0),
('pos_fn_005', 'FN-005', '行政专员', 'dept_0109', 'P2', 'using', '行政后勤事务', 'default', '001', '测试员', NOW(), 0),
('pos_fn_006', 'FN-006', '法务专员', 'dept_0106', 'P2', 'using', '合同审查与法律支持', 'default', '001', '测试员', NOW(), 0),
('pos_fn_007', 'FN-007', '审计专员', 'dept_0107', 'P2', 'using', '内部审计', 'default', '001', '测试员', NOW(), 0),
('pos_fn_008', 'FN-008', '助理', 'dept_0102', 'P1', 'using', '辅助性事务工作', 'default', '001', '测试员', NOW(), 0),
('pos_fn_009', 'FN-009', '秘书', 'dept_0102', 'P1', 'using', '文秘事务', 'default', '001', '测试员', NOW(), 0),
('pos_fn_010', 'FN-010', '前台文员', 'dept_0109', 'P1', 'using', '前台接待与来访登记', 'default', '001', '测试员', NOW(), 0),
('pos_sl_001', 'SL-001', '销售经理', 'dept_0401', 'M1', 'using', '销售团队管理', 'default', '001', '测试员', NOW(), 0),
('pos_sl_002', 'SL-002', '销售代表', 'dept_040101', 'P2', 'using', '一线销售', 'default', '001', '测试员', NOW(), 0),
('pos_sl_003', 'SL-003', '大客户经理', 'dept_0403', 'M1', 'using', '大客户维护与开拓', 'default', '001', '测试员', NOW(), 0),
('pos_sl_004', 'SL-004', '客服专员', 'dept_040501', 'P2', 'using', '客户咨询与售后受理', 'default', '001', '测试员', NOW(), 0),
('pos_sl_005', 'SL-005', '商务专员', 'dept_0604', 'P2', 'using', '商务合同与招投标支持', 'default', '001', '测试员', NOW(), 0);
