-- ========================================
-- 10. OA / BPM 集成：OA 租户与 BPM 流程菜单（DML）
--   覆盖：
--     - OA 迁移租户 nway（幂等：已存在未删除记录时跳过）
--     - BPM 应用菜单：13 个业务域目录 + 137 个申请页
--   前置：
--     - 01_platform_tenant_app.sql（sys_tenant）
--     - 02_permission_authz.sql（sys_menu）
--   说明：
--     - 目标应用 sys_app.app_code='bpm' 的 host / api_base_url 需在库中配置，未纳入本脚本
--     - 菜单来源：nway-app-web 流程中心 col.js detailListConfig
--     - 页面地址：相对路径 /#/<路由>，由后端 MenuService 拼接 app.host；
--       app.host 需包含 app-web 部署子路径（本地 http://localhost:9528；
--       生产如 https://sso.nway.net.cn/digital，拼接后即 /digital/#/<路由>）
--     - 渲染方式：wujie（app-web 为 Vue2 子应用）
--     - 数据过滤规则：
--       1) 目录与页面名称、路由、流程编码均取自 col.js detailListConfig；
--       2) 跳过 path 为 '11111111'/空/非页面路由的旧引擎专属流程；
--       3) 跳过 app-web 路由表中不存在的 CLFK（材料付款单）；
--       4) 跳过与 IT 类重复的 ECM/SystemPermissionApply
--     - 幂等：先按本脚本固定 id 前缀清理，再全量写入（仅影响本脚本生成的菜单）
-- ========================================

-- OA 登录替代：初始化 OA 租户（首期单租户 nway，承载 OA 用户登录、菜单与应用）
INSERT INTO sys_tenant (id, tenant_code, tenant_name, status, remark, creator_id, creator_name, create_time, is_deleted)
SELECT 'tenant_nway', 'nway', '演示', 'using', 'OA 体系迁移租户：承载 nway 登录与 OA 应用', '001', '系统', NOW(), 0
FROM DUAL
WHERE NOT EXISTS (
    SELECT 1 FROM sys_tenant WHERE tenant_code = 'nway' AND is_deleted = 0
);

-- ---------- BPM 流程菜单 ----------
DELETE FROM sys_menu WHERE app_code = 'bpm' AND id LIKE 'm_bpm_%';

-- 1. 业务域目录
INSERT INTO sys_menu (id, parent_id, name, url, render_type, icon, app_code, resource_code, type, action, sequence, is_enabled, source, visible, tenant_code, creator_id, creator_name, create_time, is_deleted) VALUES
('m_bpm_dir_01', '', '日常管理类', '', NULL, '', 'bpm', NULL, 'DIR', NULL, 1, 1, 'PLATFORM', 1, 'OA', '001', '系统', NOW(), 0),
('m_bpm_dir_02', '', '主数据类', '', NULL, '', 'bpm', NULL, 'DIR', NULL, 2, 1, 'PLATFORM', 1, 'OA', '001', '系统', NOW(), 0),
('m_bpm_dir_03', '', '财务类', '', NULL, '', 'bpm', NULL, 'DIR', NULL, 3, 1, 'PLATFORM', 1, 'OA', '001', '系统', NOW(), 0),
('m_bpm_dir_04', '', '人事行政类', '', NULL, '', 'bpm', NULL, 'DIR', NULL, 4, 1, 'PLATFORM', 1, 'OA', '001', '系统', NOW(), 0),
('m_bpm_dir_05', '', '考勤类', '', NULL, '', 'bpm', NULL, 'DIR', NULL, 5, 1, 'PLATFORM', 1, 'OA', '001', '系统', NOW(), 0),
('m_bpm_dir_06', '', '生产管理类', '', NULL, '', 'bpm', NULL, 'DIR', NULL, 6, 1, 'PLATFORM', 1, 'OA', '001', '系统', NOW(), 0),
('m_bpm_dir_07', '', '新产品管理类', '', NULL, '', 'bpm', NULL, 'DIR', NULL, 7, 1, 'PLATFORM', 1, 'OA', '001', '系统', NOW(), 0),
('m_bpm_dir_08', '', '物流类', '', NULL, '', 'bpm', NULL, 'DIR', NULL, 8, 1, 'PLATFORM', 1, 'OA', '001', '系统', NOW(), 0),
('m_bpm_dir_09', '', '合同类', '', NULL, '', 'bpm', NULL, 'DIR', NULL, 9, 1, 'PLATFORM', 1, 'OA', '001', '系统', NOW(), 0),
('m_bpm_dir_10', '', '设计类', '', NULL, '', 'bpm', NULL, 'DIR', NULL, 10, 1, 'PLATFORM', 1, 'OA', '001', '系统', NOW(), 0),
('m_bpm_dir_11', '', '市场营销类', '', NULL, '', 'bpm', NULL, 'DIR', NULL, 11, 1, 'PLATFORM', 1, 'OA', '001', '系统', NOW(), 0),
('m_bpm_dir_12', '', 'IT类', '', NULL, '', 'bpm', NULL, 'DIR', NULL, 12, 1, 'PLATFORM', 1, 'OA', '001', '系统', NOW(), 0),
('m_bpm_dir_13', '', '差旅服务', '', NULL, '', 'bpm', NULL, 'DIR', NULL, 13, 1, 'PLATFORM', 1, 'OA', '001', '系统', NOW(), 0);

-- 2. 流程申请页
INSERT INTO sys_menu (id, parent_id, name, url, render_type, icon, app_code, resource_code, type, action, sequence, is_enabled, source, visible, tenant_code, creator_id, creator_name, create_time, is_deleted) VALUES
('m_bpm_sos', 'm_bpm_dir_01', 'SOGSTAC报告', '/#/SOSTACReportApply', 'wujie', '', 'bpm', 'SOS', 'PAGE', NULL, 1, 1, 'PLATFORM', 1, 'OA', '001', '系统', NOW(), 0),
('m_bpm_crp', 'm_bpm_dir_01', '证件领用申请', '/#/CertificateCollarApply', 'wujie', '', 'bpm', 'CRP', 'PAGE', NULL, 2, 1, 'PLATFORM', 1, 'OA', '001', '系统', NOW(), 0),
('m_bpm_daa', 'm_bpm_dir_01', '经销商授权申请单', '/#/DealerAuthorizationApply', 'wujie', '', 'bpm', 'DAA', 'PAGE', NULL, 3, 1, 'PLATFORM', 1, 'OA', '001', '系统', NOW(), 0),
('m_bpm_vfe', 'm_bpm_dir_01', '访厂体验申请单', '/#/VisitFactoryExperienceApply', 'wujie', '', 'bpm', 'VFE', 'PAGE', NULL, 4, 1, 'PLATFORM', 1, 'OA', '001', '系统', NOW(), 0),
('m_bpm_tda', 'm_bpm_dir_01', '任务派发单', '/#/TaskDispatchApply', 'wujie', '', 'bpm', 'TDA', 'PAGE', NULL, 5, 1, 'PLATFORM', 1, 'OA', '001', '系统', NOW(), 0),
('m_bpm_saf', 'm_bpm_dir_01', '印章使用申请单', '/#/SealsUseApply', 'wujie', '', 'bpm', 'SAF', 'PAGE', NULL, 6, 1, 'PLATFORM', 1, 'OA', '001', '系统', NOW(), 0),
('m_bpm_ast', 'm_bpm_dir_01', '资产转移（报废）申请单', '/#/AssetTransformationApply', 'wujie', '', 'bpm', 'AST', 'PAGE', NULL, 7, 1, 'PLATFORM', 1, 'OA', '001', '系统', NOW(), 0),
('m_bpm_dea', 'm_bpm_dir_01', '资料导出申请单', '/#/DerivedDataApply', 'wujie', '', 'bpm', 'DEA', 'PAGE', NULL, 8, 1, 'PLATFORM', 1, 'OA', '001', '系统', NOW(), 0),
('m_bpm_dcl', 'm_bpm_dir_01', 'DCloud数据提报申请单', '/#/DcloudDataReporteApply', 'wujie', '', 'bpm', 'DCL', 'PAGE', NULL, 9, 1, 'PLATFORM', 1, 'OA', '001', '系统', NOW(), 0),
('m_bpm_dclr', 'm_bpm_dir_01', 'DCloud系统报备申请单', '/#/DcloudSystemReporteApply', 'wujie', '', 'bpm', 'DCLR', 'PAGE', NULL, 10, 1, 'PLATFORM', 1, 'OA', '001', '系统', NOW(), 0),
('m_bpm_prcn', 'm_bpm_dir_01', '包装风险会签申请单', '/#/PackingRiskCountersignApply', 'wujie', '', 'bpm', 'PRCN', 'PAGE', NULL, 11, 1, 'PLATFORM', 1, 'OA', '001', '系统', NOW(), 0),
('m_bpm_cdg', 'm_bpm_dir_01', '一卡通补办申请单', '/#/OmnipotentCardReapplyApply', 'wujie', '', 'bpm', 'CDG', 'PAGE', NULL, 12, 1, 'PLATFORM', 1, 'OA', '001', '系统', NOW(), 0),
('m_bpm_caf', 'm_bpm_dir_01', '合同领用申请单', '/#/ContractCollectionApply', 'wujie', '', 'bpm', 'CAF', 'PAGE', NULL, 13, 1, 'PLATFORM', 1, 'OA', '001', '系统', NOW(), 0),
('m_bpm_scmr', 'm_bpm_dir_01', 'SCM工程与设备项目需求评审申请单', '/#/SCMDemandApply', 'wujie', '', 'bpm', 'SCMR', 'PAGE', NULL, 14, 1, 'PLATFORM', 1, 'OA', '001', '系统', NOW(), 0),
('m_bpm_scmp', 'm_bpm_dir_01', 'SCM工程与设备项目方案评审申请单', '/#/SCMSchemeApply', 'wujie', '', 'bpm', 'SCMP', 'PAGE', NULL, 15, 1, 'PLATFORM', 1, 'OA', '001', '系统', NOW(), 0),
('m_bpm_eoa', 'm_bpm_dir_01', '公司线上账户申请单', '/#/CompanyOnlineAccountApply', 'wujie', '', 'bpm', 'EOA', 'PAGE', NULL, 16, 1, 'PLATFORM', 1, 'OA', '001', '系统', NOW(), 0),
('m_bpm_crd', 'm_bpm_dir_01', '诚信记录单', '/#/CreditRecordApply', 'wujie', '', 'bpm', 'CRD', 'PAGE', NULL, 17, 1, 'PLATFORM', 1, 'OA', '001', '系统', NOW(), 0),
('m_bpm_fia', 'm_bpm_dir_01', '旭耕（海阳）农场访问', '/#/FarmVisitApply', 'wujie', '', 'bpm', 'FIA', 'PAGE', NULL, 18, 1, 'PLATFORM', 1, 'OA', '001', '系统', NOW(), 0),
('m_bpm_rdkv', 'm_bpm_dir_01', '研发中心拜访申请单', '/#/RDCenterVisitApply', 'wujie', '', 'bpm', 'RDKV', 'PAGE', NULL, 19, 1, 'PLATFORM', 1, 'OA', '001', '系统', NOW(), 0),
('m_bpm_pbc', 'm_bpm_dir_01', '产品条形码申请单', '/#/ProductCodeApply', 'wujie', '', 'bpm', 'PBC', 'PAGE', NULL, 20, 1, 'PLATFORM', 1, 'OA', '001', '系统', NOW(), 0),
('m_bpm_ppan', 'm_bpm_dir_01', '旭耕采购申请单', '/#/XugengPurchaseApply', 'wujie', '', 'bpm', 'PPAN', 'PAGE', NULL, 21, 1, 'PLATFORM', 1, 'OA', '001', '系统', NOW(), 0),
('m_bpm_scl', 'm_bpm_dir_01', '业务拜访报备申请单', '/#/BusinessVisitApply', 'wujie', '', 'bpm', 'SCL', 'PAGE', NULL, 22, 1, 'PLATFORM', 1, 'OA', '001', '系统', NOW(), 0),
('m_bpm_cca', 'm_bpm_dir_01', '纸箱申请单', '/#/CartonApply', 'wujie', '', 'bpm', 'CCA', 'PAGE', NULL, 23, 1, 'PLATFORM', 1, 'OA', '001', '系统', NOW(), 0),
('m_bpm_rdm', 'm_bpm_dir_01', '研发资料申请单', '/#/ResearchDataApply', 'wujie', '', 'bpm', 'RDM', 'PAGE', NULL, 24, 1, 'PLATFORM', 1, 'OA', '001', '系统', NOW(), 0),
('m_bpm_ebd', 'm_bpm_dir_01', '企业品牌资料申请单', '/#/EnterpriseBrandDataApply', 'wujie', '', 'bpm', 'EBD', 'PAGE', NULL, 25, 1, 'PLATFORM', 1, 'OA', '001', '系统', NOW(), 0),
('m_bpm_hyjy', 'm_bpm_dir_01', '会议决议会签申请单', '/#/MeetingResolutionCountersignApply', 'wujie', '', 'bpm', 'HYJY', 'PAGE', NULL, 26, 1, 'PLATFORM', 1, 'OA', '001', '系统', NOW(), 0),
('m_bpm_sap', 'm_bpm_dir_01', '方案签核流程', '/#/PlanSignatureVerificationProcessApply', 'wujie', '', 'bpm', 'SAP', 'PAGE', NULL, 27, 1, 'PLATFORM', 1, 'OA', '001', '系统', NOW(), 0),
('m_bpm_mha', 'm_bpm_dir_01', '物料交接申请单', '/#/MaterialHandoverApply', 'wujie', '', 'bpm', 'MHA', 'PAGE', NULL, 28, 1, 'PLATFORM', 1, 'OA', '001', '系统', NOW(), 0),
('m_bpm_cdp', 'm_bpm_dir_01', '川大采购申请单', '/#/ChuandaPurchaseApply', 'wujie', '', 'bpm', 'CDP', 'PAGE', NULL, 29, 1, 'PLATFORM', 1, 'OA', '001', '系统', NOW(), 0),
('m_bpm_ccn', 'm_bpm_dir_02', 'SAP成本中心&工作中心新增申请单', '/#/CostCenterNewApply', 'wujie', '', 'bpm', 'CCN', 'PAGE', NULL, 1, 1, 'PLATFORM', 1, 'OA', '001', '系统', NOW(), 0),
('m_bpm_bdn', 'm_bpm_dir_02', 'SAP物料主数据修改申请单', '/#/MaterialMasterDataModificationApply', 'wujie', '', 'bpm', 'BDN', 'PAGE', NULL, 2, 1, 'PLATFORM', 1, 'OA', '001', '系统', NOW(), 0),
('m_bpm_sup', 'm_bpm_dir_02', 'SAP供应商主数据新增/修改申请单', '/#/SAPSupplierMasterDataApply', 'wujie', '', 'bpm', 'SUP', 'PAGE', NULL, 3, 1, 'PLATFORM', 1, 'OA', '001', '系统', NOW(), 0),
('m_bpm_cmrn', 'm_bpm_dir_02', 'SAP公司间调拨主数据申请单', '/#/SAPCompanyTransferMasterDataApply', 'wujie', '', 'bpm', 'CMRN', 'PAGE', NULL, 4, 1, 'PLATFORM', 1, 'OA', '001', '系统', NOW(), 0),
('m_bpm_npcf', 'm_bpm_dir_02', 'SAP新产品项目确认单', '/#/SAPNewProductProjectConfirmationFormApply', 'wujie', '', 'bpm', 'NPCF', 'PAGE', NULL, 5, 1, 'PLATFORM', 1, 'OA', '001', '系统', NOW(), 0),
('m_bpm_pmdn', 'm_bpm_dir_02', 'SAP生产类物料主数据新增申请单', '/#/SAPProductionMaterialsMasterDataApply', 'wujie', '', 'bpm', 'PMDN', 'PAGE', NULL, 6, 1, 'PLATFORM', 1, 'OA', '001', '系统', NOW(), 0),
('m_bpm_qir', 'm_bpm_dir_02', 'SAP质量信息记录创建申请单', '/#/SAPQualityInformationRecordCreationApply', 'wujie', '', 'bpm', 'QIR', 'PAGE', NULL, 7, 1, 'PLATFORM', 1, 'OA', '001', '系统', NOW(), 0),
('m_bpm_cmd', 'm_bpm_dir_02', '客户主数据新增&修改申请单', '/#/CustomerMasterDataAddModifyApply', 'wujie', '', 'bpm', 'CMD', 'PAGE', NULL, 8, 1, 'PLATFORM', 1, 'OA', '001', '系统', NOW(), 0),
('m_bpm_eca', 'm_bpm_dir_02', '工程变更(BOM)申请单', '/#/EngineeringChangeOrBOMApply', 'wujie', '', 'bpm', 'ECA', 'PAGE', NULL, 9, 1, 'PLATFORM', 1, 'OA', '001', '系统', NOW(), 0),
('m_bpm_prc', 'm_bpm_dir_02', '工程变更(工艺路线)申请单', '/#/EngineeringChangeOrProcessRouteApply', 'wujie', '', 'bpm', 'PRC', 'PAGE', NULL, 10, 1, 'PLATFORM', 1, 'OA', '001', '系统', NOW(), 0),
('m_bpm_acf', 'm_bpm_dir_02', '合同客户关户、冻结、解冻申请单', '/#/ContractCustomerApply', 'wujie', '', 'bpm', 'ACF', 'PAGE', NULL, 11, 1, 'PLATFORM', 1, 'OA', '001', '系统', NOW(), 0),
('m_bpm_tpmn', 'm_bpm_dir_02', '价盘维护申请单（新产品）', '/#/PriceMaintenanceNewProductApply', 'wujie', '', 'bpm', 'TPMN', 'PAGE', NULL, 12, 1, 'PLATFORM', 1, 'OA', '001', '系统', NOW(), 0),
('m_bpm_osan', 'm_bpm_dir_02', '组织架构调整申请单', '/#/OrganizationAdjustmentApply', 'wujie', '', 'bpm', 'OSAN', 'PAGE', NULL, 13, 1, 'PLATFORM', 1, 'OA', '001', '系统', NOW(), 0),
('m_bpm_pdmn', 'm_bpm_dir_02', '价盘维护申请单（老产品）', '/#/PriceMaintenanceOldProductApply', 'wujie', '', 'bpm', 'PDMN', 'PAGE', NULL, 14, 1, 'PLATFORM', 1, 'OA', '001', '系统', NOW(), 0),
('m_bpm_lmd', 'm_bpm_dir_02', 'SAP库位新增/修改申请单', '/#/SAPLibraryLocationAdditionModificationApply', 'wujie', '', 'bpm', 'LMD', 'PAGE', NULL, 15, 1, 'PLATFORM', 1, 'OA', '001', '系统', NOW(), 0),
('m_bpm_npmd', 'm_bpm_dir_02', 'SAP非生产类物料主数据新增申请单', '/#/SAPNonProductionMaterialsMasterDataApply', 'wujie', '', 'bpm', 'NPMD', 'PAGE', NULL, 16, 1, 'PLATFORM', 1, 'OA', '001', '系统', NOW(), 0),
('m_bpm_frotg', 'm_bpm_dir_03', '费用报销单TG', '/#/ExpenseReimbursementTgApply', 'wujie', '', 'bpm', 'FROTG', 'PAGE', NULL, 1, 1, 'PLATFORM', 1, 'OA', '001', '系统', NOW(), 0),
('m_bpm_raf', 'm_bpm_dir_03', '退款申请单', '/#/RefundApply', 'wujie', '', 'bpm', 'RAF', 'PAGE', NULL, 2, 1, 'PLATFORM', 1, 'OA', '001', '系统', NOW(), 0),
('m_bpm_yg', 'm_bpm_dir_03', '员工补贴费用报销单', '/#/EmployeeSubsidyApply', 'wujie', '', 'bpm', 'YG', 'PAGE', NULL, 3, 1, 'PLATFORM', 1, 'OA', '001', '系统', NOW(), 0),
('m_bpm_tea', 'm_bpm_dir_03', '打车费用报销单', '/#/TaxiExpenseApply', 'wujie', '', 'bpm', 'TEA', 'PAGE', NULL, 4, 1, 'PLATFORM', 1, 'OA', '001', '系统', NOW(), 0),
('m_bpm_ber', 'm_bpm_dir_03', '宴请费用报销单', '/#/EntertainExpenseApply', 'wujie', '', 'bpm', 'BER', 'PAGE', NULL, 5, 1, 'PLATFORM', 1, 'OA', '001', '系统', NOW(), 0),
('m_bpm_omr', 'm_bpm_dir_03', '驻外/驻区&营销岗位交通费用报销单', '/#/OutAreaAndBackHomeExpenseApply', 'wujie', '', 'bpm', 'OMR', 'PAGE', NULL, 6, 1, 'PLATFORM', 1, 'OA', '001', '系统', NOW(), 0),
('m_bpm_rfi', 'm_bpm_dir_03', '财务资料调取审批', '/#/FinancialRetrievalApply', 'wujie', '', 'bpm', 'RFI', 'PAGE', NULL, 7, 1, 'PLATFORM', 1, 'OA', '001', '系统', NOW(), 0),
('m_bpm_ucc', 'm_bpm_dir_03', '客户授信额度申请单', '/#/CustomerCreditLimitApply', 'wujie', '', 'bpm', 'UCC', 'PAGE', NULL, 8, 1, 'PLATFORM', 1, 'OA', '001', '系统', NOW(), 0),
('m_bpm_sac', 'm_bpm_dir_04', '员工活动申请单', '/#/StaffActivitiesApply', 'wujie', '', 'bpm', 'SAC', 'PAGE', NULL, 1, 1, 'PLATFORM', 1, 'OA', '001', '系统', NOW(), 0),
('m_bpm_bcm', 'm_bpm_dir_04', '名片制作申请单', '/#/BusinessCardMakeApply', 'wujie', '', 'bpm', 'BCM', 'PAGE', NULL, 2, 1, 'PLATFORM', 1, 'OA', '001', '系统', NOW(), 0),
('m_bpm_nhsn', 'm_bpm_dir_04', '拟需员工申请单', '/#/NeedEmployeeNewApply', 'wujie', '', 'bpm', 'NHSN', 'PAGE', NULL, 3, 1, 'PLATFORM', 1, 'OA', '001', '系统', NOW(), 0),
('m_bpm_iern', 'm_bpm_dir_04', '电脑/IT设备申请单', '/#/ITRequisitionApply', 'wujie', '', 'bpm', 'IERN', 'PAGE', NULL, 4, 1, 'PLATFORM', 1, 'OA', '001', '系统', NOW(), 0),
('m_bpm_mbpn', 'm_bpm_dir_04', '手持权限申请单', '/#/HandheldPhonePermissionsApply', 'wujie', '', 'bpm', 'MBPN', 'PAGE', NULL, 5, 1, 'PLATFORM', 1, 'OA', '001', '系统', NOW(), 0),
('m_bpm_lca', 'm_bpm_dir_04', '弱电工程申请单', '/#/WeakCurrentEngineeringApply', 'wujie', '', 'bpm', 'LCA', 'PAGE', NULL, 6, 1, 'PLATFORM', 1, 'OA', '001', '系统', NOW(), 0),
('m_bpm_ldan', 'm_bpm_dir_04', '入住宿舍申请单', '/#/ApplyForDormitoryApply', 'wujie', '', 'bpm', 'LDAN', 'PAGE', NULL, 7, 1, 'PLATFORM', 1, 'OA', '001', '系统', NOW(), 0),
('m_bpm_ldau', 'm_bpm_dir_04', '退住宿舍申请单', '/#/RetreatDormitoryApply', 'wujie', '', 'bpm', 'LDAU', 'PAGE', NULL, 8, 1, 'PLATFORM', 1, 'OA', '001', '系统', NOW(), 0),
('m_bpm_olpn', 'm_bpm_dir_04', '空间租赁申请单', '/#/SpaceRentalApply', 'wujie', '', 'bpm', 'OLPN', 'PAGE', NULL, 9, 1, 'PLATFORM', 1, 'OA', '001', '系统', NOW(), 0),
('m_bpm_ebf', 'm_bpm_dir_04', '员工福利申请单', '/#/EmployeeBenefitsApply', 'wujie', '', 'bpm', 'EBF', 'PAGE', NULL, 10, 1, 'PLATFORM', 1, 'OA', '001', '系统', NOW(), 0),
('m_bpm_top', 'm_bpm_dir_04', '门禁开通申请单', '/#/TheDoorOpeningApply', 'wujie', '', 'bpm', 'TOP', 'PAGE', NULL, 11, 1, 'PLATFORM', 1, 'OA', '001', '系统', NOW(), 0),
('m_bpm_cah', 'm_bpm_dir_04', '入住公司协议酒店申请单', '/#/StayCompanyHotelApply', 'wujie', '', 'bpm', 'CAH', 'PAGE', NULL, 12, 1, 'PLATFORM', 1, 'OA', '001', '系统', NOW(), 0),
('m_bpm_mfa', 'm_bpm_dir_04', '手机话费补助申请单', '/#/MobileAllowanceApply', 'wujie', '', 'bpm', 'MFA', 'PAGE', NULL, 13, 1, 'PLATFORM', 1, 'OA', '001', '系统', NOW(), 0),
('m_bpm_gol', 'm_bpm_dir_04', '外出学习申请单', '/#/GoOutLearnApply', 'wujie', '', 'bpm', 'GOL', 'PAGE', NULL, 14, 1, 'PLATFORM', 1, 'OA', '001', '系统', NOW(), 0),
('m_bpm_dab', 'm_bpm_dir_04', '宴请申请单', '/#/EntertainApply', 'wujie', '', 'bpm', 'DAB', 'PAGE', NULL, 15, 1, 'PLATFORM', 1, 'OA', '001', '系统', NOW(), 0),
('m_bpm_pr', 'm_bpm_dir_04', '行政请购单', '/#/AdministrativeRequisitionApply', 'wujie', '', 'bpm', 'PR', 'PAGE', NULL, 16, 1, 'PLATFORM', 1, 'OA', '001', '系统', NOW(), 0),
('m_bpm_vmrt', 'm_bpm_dir_04', '车辆维保申请单', '/#/VehicleMaintenanceApply', 'wujie', '', 'bpm', 'VMRT', 'PAGE', NULL, 17, 1, 'PLATFORM', 1, 'OA', '001', '系统', NOW(), 0),
('m_bpm_osa', 'm_bpm_dir_04', '组织架构调整申请单', '/#/OrganizationalStructureAdjustmentApply', 'wujie', '', 'bpm', 'OSA', 'PAGE', NULL, 18, 1, 'PLATFORM', 1, 'OA', '001', '系统', NOW(), 0),
('m_bpm_odc', 'm_bpm_dir_04', '空间故障维修&办公装修施工申请单', '/#/RepairApply', 'wujie', '', 'bpm', 'ODC', 'PAGE', NULL, 19, 1, 'PLATFORM', 1, 'OA', '001', '系统', NOW(), 0),
('m_bpm_rpa', 'm_bpm_dir_04', '奖惩申请单', '/#/RewardPunishmentApply', 'wujie', '', 'bpm', 'RPA', 'PAGE', NULL, 20, 1, 'PLATFORM', 1, 'OA', '001', '系统', NOW(), 0),
('m_bpm_rop', 'm_bpm_dir_04', '复职申请单', '/#/ReinstateApply', 'wujie', '', 'bpm', 'ROP', 'PAGE', NULL, 21, 1, 'PLATFORM', 1, 'OA', '001', '系统', NOW(), 0),
('m_bpm_pim', 'm_bpm_dir_04', '个人信息维护申请单', '/#/PersonalInformationMaintenanceApply', 'wujie', '', 'bpm', 'PIM', 'PAGE', NULL, 22, 1, 'PLATFORM', 1, 'OA', '001', '系统', NOW(), 0),
('m_bpm_opa', 'm_bpm_dir_04', '线下渠道绩效调整申请单', '/#/OfflineChannelPerformanceAdjustmentApply', 'wujie', '', 'bpm', 'OPA', 'PAGE', NULL, 23, 1, 'PLATFORM', 1, 'OA', '001', '系统', NOW(), 0),
('m_bpm_pal', 'm_bpm_dir_05', '请假申请单', '/#/LeaveApply', 'wujie', '', 'bpm', 'PAL', 'PAGE', NULL, 1, 1, 'PLATFORM', 1, 'OA', '001', '系统', NOW(), 0),
('m_bpm_pwo', 'm_bpm_dir_05', '加班申请单', '/#/OverTimeApply', 'wujie', '', 'bpm', 'PWO', 'PAGE', NULL, 2, 1, 'PLATFORM', 1, 'OA', '001', '系统', NOW(), 0),
('m_bpm_pgo', 'm_bpm_dir_05', '外出申请单', '/#/GoOutApply', 'wujie', '', 'bpm', 'PGO', 'PAGE', NULL, 3, 1, 'PLATFORM', 1, 'OA', '001', '系统', NOW(), 0),
('m_bpm_pbl', 'm_bpm_dir_05', '销假申请单', '/#/SalesLeaveApply', 'wujie', '', 'bpm', 'PBL', 'PAGE', NULL, 4, 1, 'PLATFORM', 1, 'OA', '001', '系统', NOW(), 0),
('m_bpm_pnp', 'm_bpm_dir_05', '未打卡证明', '/#/NoPunchApply', 'wujie', '', 'bpm', 'PNP', 'PAGE', NULL, 5, 1, 'PLATFORM', 1, 'OA', '001', '系统', NOW(), 0),
('m_bpm_bra', 'm_bpm_dir_05', '班车乘坐申请单', '/#/BusRideApply', 'wujie', '', 'bpm', 'BRA', 'PAGE', NULL, 6, 1, 'PLATFORM', 1, 'OA', '001', '系统', NOW(), 0),
('m_bpm_bla', 'm_bpm_dir_05', '班车迟到申请单', '/#/BusBeLateApply', 'wujie', '', 'bpm', 'BLA', 'PAGE', NULL, 7, 1, 'PLATFORM', 1, 'OA', '001', '系统', NOW(), 0),
('m_bpm_wfh', 'm_bpm_dir_05', '居家办公申请单', '/#/WorkFromHomeApply', 'wujie', '', 'bpm', 'WFH', 'PAGE', NULL, 8, 1, 'PLATFORM', 1, 'OA', '001', '系统', NOW(), 0),
('m_bpm_pla', 'm_bpm_dir_05', '育儿假申请单', '/#/ParentalLeaveApply', 'wujie', '', 'bpm', 'PLA', 'PAGE', NULL, 9, 1, 'PLATFORM', 1, 'OA', '001', '系统', NOW(), 0),
('m_bpm_aom', 'm_bpm_dir_06', '工厂物料领用申请单', '/#/FactoryMaterialRequisitionsApply', 'wujie', '', 'bpm', 'AOM', 'PAGE', NULL, 1, 1, 'PLATFORM', 1, 'OA', '001', '系统', NOW(), 0),
('m_bpm_pmpp', 'm_bpm_dir_06', '生产物料采购价格变动申请单', '/#/ChangePurchasePriceApply', 'wujie', '', 'bpm', 'PMPP', 'PAGE', NULL, 2, 1, 'PLATFORM', 1, 'OA', '001', '系统', NOW(), 0),
('m_bpm_epcn', 'm_bpm_dir_06', '出口产品确认会签', '/#/ExportProductsConfirmedApply', 'wujie', '', 'bpm', 'EPCN', 'PAGE', NULL, 3, 1, 'PLATFORM', 1, 'OA', '001', '系统', NOW(), 0),
('m_bpm_pea', 'm_bpm_dir_06', '包装实验申请单', '/#/PackagingTestApply', 'wujie', '', 'bpm', 'PEA', 'PAGE', NULL, 4, 1, 'PLATFORM', 1, 'OA', '001', '系统', NOW(), 0),
('m_bpm_emm', 'm_bpm_dir_06', '异常信息管理申请单', '/#/ExceptionInformationManagementApply', 'wujie', '', 'bpm', 'EMM', 'PAGE', NULL, 5, 1, 'PLATFORM', 1, 'OA', '001', '系统', NOW(), 0),
('m_bpm_pcc', 'm_bpm_dir_06', '产品认证体系审核会签流程', '/#/ProductCertificationApply', 'wujie', '', 'bpm', 'PCC', 'PAGE', NULL, 6, 1, 'PLATFORM', 1, 'OA', '001', '系统', NOW(), 0),
('m_bpm_apc', 'm_bpm_dir_06', '包装物料处理申请单', '/#/PackageMaterialHandleApply', 'wujie', '', 'bpm', 'APC', 'PAGE', NULL, 7, 1, 'PLATFORM', 1, 'OA', '001', '系统', NOW(), 0),
('m_bpm_mss', 'm_bpm_dir_06', '工厂物料自行销售申请单', '/#/FactoryMaterialSelfSellingApply', 'wujie', '', 'bpm', 'MSS', 'PAGE', NULL, 8, 1, 'PLATFORM', 1, 'OA', '001', '系统', NOW(), 0),
('m_bpm_pitr', 'm_bpm_dir_06', '提案改善激励物品领取记录单', '/#/ProposalImproveIncentiveItemsApply', 'wujie', '', 'bpm', 'PITR', 'PAGE', NULL, 9, 1, 'PLATFORM', 1, 'OA', '001', '系统', NOW(), 0),
('m_bpm_pit', 'm_bpm_dir_06', '提案改善申请单', '/#/ProposalImprovementApply', 'wujie', '', 'bpm', 'PIT', 'PAGE', NULL, 10, 1, 'PLATFORM', 1, 'OA', '001', '系统', NOW(), 0),
('m_bpm_cpl', 'm_bpm_dir_06', '转产申请单', '/#/TransferRequestApply', 'wujie', '', 'bpm', 'CPL', 'PAGE', NULL, 11, 1, 'PLATFORM', 1, 'OA', '001', '系统', NOW(), 0),
('m_bpm_lia', 'm_bpm_dir_06', 'LIMS检验申请单', '/#/LIMSInspectionRequestApply', 'wujie', '', 'bpm', 'LIA', 'PAGE', NULL, 12, 1, 'PLATFORM', 1, 'OA', '001', '系统', NOW(), 0),
('m_bpm_acs', 'm_bpm_dir_07', '品类SPU新增申请单', '/#/SpuAddApply', 'wujie', '', 'bpm', 'ACS', 'PAGE', NULL, 1, 1, 'PLATFORM', 1, 'OA', '001', '系统', NOW(), 0),
('m_bpm_mdd', 'm_bpm_dir_07', '市场需求文档审核单', '/#/MarketDemandDocumentReviewFormApply', 'wujie', '', 'bpm', 'MDD', 'PAGE', NULL, 2, 1, 'PLATFORM', 1, 'OA', '001', '系统', NOW(), 0),
('m_bpm_pcd', 'm_bpm_dir_07', '产品概念方向调研申请单', '/#/ProductConceptDirectionSurveyApply', 'wujie', '', 'bpm', 'PCD', 'PAGE', NULL, 3, 1, 'PLATFORM', 1, 'OA', '001', '系统', NOW(), 0),
('m_bpm_npa', 'm_bpm_dir_07', '新品立项评审单', '/#/NewProductApprovalReviewApply', 'wujie', '', 'bpm', 'NPA', 'PAGE', NULL, 4, 1, 'PLATFORM', 1, 'OA', '001', '系统', NOW(), 0),
('m_bpm_pfc', 'm_bpm_dir_07', '产品口味确认单', '/#/ProductTasteConfirmationApply', 'wujie', '', 'bpm', 'PFC', 'PAGE', NULL, 5, 1, 'PLATFORM', 1, 'OA', '001', '系统', NOW(), 0),
('m_bpm_pcf', 'm_bpm_dir_07', '新品包装确认单', '/#/PackingConfirmationApply', 'wujie', '', 'bpm', 'PCF', 'PAGE', NULL, 6, 1, 'PLATFORM', 1, 'OA', '001', '系统', NOW(), 0),
('m_bpm_pdn', 'm_bpm_dir_07', '包装开发需求申请单', '/#/PackagingDevelopmentRequirementsApply', 'wujie', '', 'bpm', 'PDN', 'PAGE', NULL, 7, 1, 'PLATFORM', 1, 'OA', '001', '系统', NOW(), 0),
('m_bpm_nppm', 'm_bpm_dir_07', '新产品包装模具开发确认单', '/#/NewProductPackagingMoldDevelopmentApply', 'wujie', '', 'bpm', 'NPPM', 'PAGE', NULL, 8, 1, 'PLATFORM', 1, 'OA', '001', '系统', NOW(), 0),
('m_bpm_sam', 'm_bpm_dir_07', '样品申请单', '/#/SampleApplicationFormApply', 'wujie', '', 'bpm', 'SAM', 'PAGE', NULL, 9, 1, 'PLATFORM', 1, 'OA', '001', '系统', NOW(), 0),
('m_bpm_drp', 'm_bpm_dir_08', 'TMS单据反向处理申请单', '/#/TMDBillsApply', 'wujie', '', 'bpm', 'DRP', 'PAGE', NULL, 1, 1, 'PLATFORM', 1, 'OA', '001', '系统', NOW(), 0),
('m_bpm_ipr', 'm_bpm_dir_09', '知识产权信息、需求提报单', '/#/IntellectualPropertyDemandApply', 'wujie', '', 'bpm', 'IPR', 'PAGE', NULL, 1, 1, 'PLATFORM', 1, 'OA', '001', '系统', NOW(), 0),
('m_bpm_tmr', 'm_bpm_dir_09', '商标注册申请单', '/#/TradeMarkRegistrationApply', 'wujie', '', 'bpm', 'TMR', 'PAGE', NULL, 2, 1, 'PLATFORM', 1, 'OA', '001', '系统', NOW(), 0),
('m_bpm_crr', 'm_bpm_dir_09', '著作权登记保护申请单', '/#/CopyrightRegistrationApply', 'wujie', '', 'bpm', 'CRR', 'PAGE', NULL, 3, 1, 'PLATFORM', 1, 'OA', '001', '系统', NOW(), 0),
('m_bpm_afp', 'm_bpm_dir_09', '专利申请单', '/#/PatentApplicationApply', 'wujie', '', 'bpm', 'AFP', 'PAGE', NULL, 4, 1, 'PLATFORM', 1, 'OA', '001', '系统', NOW(), 0),
('m_bpm_plr', 'm_bpm_dir_09', '知识产权奖励申请单', '/#/IntellectualPropertyRewardsApply', 'wujie', '', 'bpm', 'PLR', 'PAGE', NULL, 5, 1, 'PLATFORM', 1, 'OA', '001', '系统', NOW(), 0),
('m_bpm_crn', 'm_bpm_dir_09', '合同评审申请单', '/#/ContractReviewApply', 'wujie', '', 'bpm', 'CRN', 'PAGE', NULL, 6, 1, 'PLATFORM', 1, 'OA', '001', '系统', NOW(), 0),
('m_bpm_yhp', 'm_bpm_dir_09', '研发采购合同评审', '/#/DevelopProcurementContractReviewApply', 'wujie', '', 'bpm', 'YHP', 'PAGE', NULL, 7, 1, 'PLATFORM', 1, 'OA', '001', '系统', NOW(), 0),
('m_bpm_ipir', 'm_bpm_dir_09', '知识产权信息发布申请单', '/#/IntellectualPropertyInformationReleaseApply', 'wujie', '', 'bpm', 'IPIR', 'PAGE', NULL, 8, 1, 'PLATFORM', 1, 'OA', '001', '系统', NOW(), 0),
('m_bpm_icr', 'm_bpm_dir_09', '投资合同评审申请单', '/#/InvestmentContractReviewApply', 'wujie', '', 'bpm', 'ICR', 'PAGE', NULL, 9, 1, 'PLATFORM', 1, 'OA', '001', '系统', NOW(), 0),
('m_bpm_sva', 'm_bpm_dir_10', '影像拍摄制作申请单', '/#/ImageShootingApply', 'wujie', '', 'bpm', 'SVA', 'PAGE', NULL, 1, 1, 'PLATFORM', 1, 'OA', '001', '系统', NOW(), 0),
('m_bpm_pmmr', 'm_bpm_dir_10', '广宣品统采物料申请单', '/#/PublicityMaterialPurchaseApply', 'wujie', '', 'bpm', 'PMMR', 'PAGE', NULL, 2, 1, 'PLATFORM', 1, 'OA', '001', '系统', NOW(), 0),
('m_bpm_pdl', 'm_bpm_dir_10', '包装物审核单', '/#/PackagingAuditApply', 'wujie', '', 'bpm', 'PDL', 'PAGE', NULL, 3, 1, 'PLATFORM', 1, 'OA', '001', '系统', NOW(), 0),
('m_bpm_dpo', 'm_bpm_dir_10', '设计申请单', '/#/DesignApply', 'wujie', '', 'bpm', 'DPO', 'PAGE', NULL, 4, 1, 'PLATFORM', 1, 'OA', '001', '系统', NOW(), 0),
('m_bpm_bcp', 'm_bpm_dir_10', '包装物变更单', '/#/PackagingChangeApply', 'wujie', '', 'bpm', 'BCP', 'PAGE', NULL, 5, 1, 'PLATFORM', 1, 'OA', '001', '系统', NOW(), 0),
('m_bpm_flc', 'm_bpm_dir_11', '窜货投诉处理申请单', '/#/FleeingGoodsComplaintApply', 'wujie', '', 'bpm', 'FLC', 'PAGE', NULL, 1, 1, 'PLATFORM', 1, 'OA', '001', '系统', NOW(), 0),
('m_bpm_lpc', 'm_bpm_dir_11', '低价投诉处理申请单', '/#/LowPriceComplaintApply', 'wujie', '', 'bpm', 'LPC', 'PAGE', NULL, 2, 1, 'PLATFORM', 1, 'OA', '001', '系统', NOW(), 0),
('m_bpm_fpa', 'm_bpm_dir_11', '履约/申诉申请单', '/#/FleeingGoodsPerformanceApply', 'wujie', '', 'bpm', 'FPA', 'PAGE', NULL, 3, 1, 'PLATFORM', 1, 'OA', '001', '系统', NOW(), 0),
('m_bpm_karn', 'm_bpm_dir_11', '客户调账申请单', '/#/KACustomerAdjustmentAccountApply', 'wujie', '', 'bpm', 'KARN', 'PAGE', NULL, 4, 1, 'PLATFORM', 1, 'OA', '001', '系统', NOW(), 0),
('m_bpm_lpp', 'm_bpm_dir_11', '低价履约申诉申请单', '/#/LowPricePerformanceAppealApply', 'wujie', '', 'bpm', 'LPP', 'PAGE', NULL, 5, 1, 'PLATFORM', 1, 'OA', '001', '系统', NOW(), 0),
('m_bpm_argn', 'm_bpm_dir_11', '客户返利申请报告', '/#/CustomerRebateApplicationReportApply', 'wujie', '', 'bpm', 'ARGN', 'PAGE', NULL, 6, 1, 'PLATFORM', 1, 'OA', '001', '系统', NOW(), 0),
('m_bpm_sco', 'm_bpm_dir_12', '系统权限申请单', '/#/SystemPermissionApply', 'wujie', '', 'bpm', 'SCO', 'PAGE', NULL, 1, 1, 'PLATFORM', 1, 'OA', '001', '系统', NOW(), 0),
('m_bpm_scf', 'm_bpm_dir_12', '系统配置变更申请单', '/#/SystemConfigurationChangeApply', 'wujie', '', 'bpm', 'SCF', 'PAGE', NULL, 2, 1, 'PLATFORM', 1, 'OA', '001', '系统', NOW(), 0),
('m_bpm_sran', 'm_bpm_dir_12', '服务器资源申请单', '/#/ServerResourceApply', 'wujie', '', 'bpm', 'SRAN', 'PAGE', NULL, 3, 1, 'PLATFORM', 1, 'OA', '001', '系统', NOW(), 0),
('m_bpm_bud', 'm_bpm_dir_12', '业务需求申请单', '/#/BusinessDemandApply', 'wujie', '', 'bpm', 'BUD', 'PAGE', NULL, 4, 1, 'PLATFORM', 1, 'OA', '001', '系统', NOW(), 0),
('m_bpm_dua', 'm_bpm_dir_12', '域名使用申请单', '/#/DomainNameApply', 'wujie', '', 'bpm', 'DUA', 'PAGE', NULL, 5, 1, 'PLATFORM', 1, 'OA', '001', '系统', NOW(), 0),
('m_bpm_sed', 'm_bpm_dir_12', '敏感邮件删除申请单', '/#/MailSensitiveDeleteApply', 'wujie', '', 'bpm', 'SED', 'PAGE', NULL, 6, 1, 'PLATFORM', 1, 'OA', '001', '系统', NOW(), 0),
('m_bpm_des', 'm_bpm_dir_12', '数据外发申请单', '/#/DataOutboundApply', 'wujie', '', 'bpm', 'DES', 'PAGE', NULL, 7, 1, 'PLATFORM', 1, 'OA', '001', '系统', NOW(), 0),
('m_bpm_pob', 'm_bpm_dir_13', '公出申请单', '/#/OfficialBusinessApply', 'wujie', '', 'bpm', 'POB', 'PAGE', NULL, 1, 1, 'PLATFORM', 1, 'OA', '001', '系统', NOW(), 0),
('m_bpm_cot', 'm_bpm_dir_13', '取消公出申请单', '/#/CancelOfficialBusinessApply', 'wujie', '', 'bpm', 'COT', 'PAGE', NULL, 2, 1, 'PLATFORM', 1, 'OA', '001', '系统', NOW(), 0),
('m_bpm_sta', 'm_bpm_dir_13', '特殊机票申请单', '/#/SpecialTicketApply', 'wujie', '', 'bpm', 'STA', 'PAGE', NULL, 3, 1, 'PLATFORM', 1, 'OA', '001', '系统', NOW(), 0);
