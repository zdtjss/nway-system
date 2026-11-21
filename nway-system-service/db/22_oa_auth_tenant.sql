-- ========================================
-- 22. OA 登录替代：初始化 OA 租户
--     技术方案 D12：首期单租户 nway，承载 OA 用户登录、菜单与应用
--     幂等：已存在（未删除）时跳过
-- ========================================

INSERT INTO sys_tenant (id, tenant_code, tenant_name, status, remark, creator_id, creator_name, create_time, is_deleted)
SELECT 'tenant_nway', 'nway', '演示', 'using', 'OA 体系迁移租户：承载 nway 登录与 OA 应用', '001', '系统', NOW(), 0
FROM DUAL
WHERE NOT EXISTS (
    SELECT 1 FROM sys_tenant WHERE tenant_code = 'nway' AND is_deleted = 0
);
