-- ========================================
-- 10. 视图（必要初始化）
--   说明：当前代码没有依赖任何数据库视图，以下视图为基线保留。
--   历史 system.sql 中的 v_sys_user_menu / v_sys_role_menu 依赖已废弃的
--   sys_role_menu 表，无法也不应重建。
-- ========================================

-- 用户角色视图
CREATE OR REPLACE VIEW v_sys_user_role AS
SELECT a.*, b.role_id
FROM sys_role a
    LEFT JOIN sys_user_role b ON a.id = b.role_id;
