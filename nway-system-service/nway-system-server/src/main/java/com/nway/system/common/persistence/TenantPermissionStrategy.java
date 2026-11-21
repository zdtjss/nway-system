package com.nway.system.common.persistence;

import com.nway.spring.jdbc.sql.SqlType;
import com.nway.spring.jdbc.sql.permission.PermissionStrategy;
import com.nway.spring.jdbc.sql.permission.WhereCondition;
import com.nway.system.common.security.LoginUserContext;
import com.nway.system.common.tenant.TenantUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collections;
import java.util.List;

/**
 * 租户自动隔离策略（三态）。
 * <p>
 * 基于 nway-jdbc 的 {@link PermissionStrategy} 机制，在 SELECT/UPDATE/DELETE 时
 * 自动追加租户条件，无需 DAO 层手动判断。三态：
 * <ul>
 *   <li>INSERT：不追加（由 {@link TenantCodeStrategy} fillStrategy 负责填充）</li>
 *   <li>超级管理员/显式系统上下文（{@link TenantUtil#callAsSystem}）：不隔离（跨租户）</li>
 *   <li>普通用户：{@code tenant_code = 本租户}</li>
 *   <li>平台管理员：{@code tenant_code IN (本租户, 授权租户...)}</li>
 * </ul>
 * <p>
 * <b>fail-closed</b>：无登录上下文时不再"全域放行"，而是恒假条件（看不到任何数据）并记 ERROR 日志。
 * 需要在无请求上下文的场景（登录、开放端、异步/定时任务）访问租户数据，必须显式使用
 * {@link TenantUtil#callAsSystem}（系统级跨租户）或 {@link TenantUtil#runAsTenant}（收敛到单一租户）。
 *
 * @author nway
 */
public class TenantPermissionStrategy implements PermissionStrategy {

    private static final Logger log = LoggerFactory.getLogger(TenantPermissionStrategy.class);

    @Override
    public WhereCondition getSqlSegment(SqlType sqlType, String column) {
        // INSERT 不过滤（由 fillStrategy 负责填充）
        if (sqlType == SqlType.INSERT) {
            return null;
        }
        // fail-closed：无登录上下文 → 恒假条件（不返回任何行），并留痕
        if (LoginUserContext.current() == null) {
            log.error("租户隔离 fail-closed：无登录上下文，已拒绝 {} 对列 {} 的访问；"
                            + "如需系统级跨租户请用 TenantUtil.callAsSystem/runAsTenant 显式声明上下文",
                    sqlType, column);
            return new WhereCondition("1 = ?", 0);
        }
        // 读写边界分离：SELECT 用读取合（含 public 读共享）；UPDATE/DELETE 用可管理集合（排除 public）。
        // 这样"读共享 ≠ 写授权"由数据层强制成立，无需依赖每个写路径是否记得调断言。
        List<String> tenants = (sqlType == SqlType.SELECT)
                ? TenantUtil.currentTenantCodesForIsolation()
                : TenantUtil.currentManageableTenantCodes();
        // 超管/显式系统上下文 → 不隔离（两个集合在超管时均返回 null）
        if (tenants == null) {
            return null;
        }
        // 非超管但无任何租户归属 → 隔离为空，看不到任何数据（恒假条件）
        // 说明：TenantUtil.currentTenantCodesForIsolation 恒并入 public 读共享租户，
        //      正常路径不会走到这里；保留该分支作为防御性兜底，避免未来语义变更导致越权
        if (tenants.isEmpty()) {
            return new WhereCondition("1 = ?", 0);
        }
        // 单租户：= ?
        if (tenants.size() == 1) {
            return new WhereCondition(column + " = ?", tenants.get(0));
        }
        // 多租户（管理员授权租户）：IN (?, ?, ...)
        String placeholders = String.join(", ", Collections.nCopies(tenants.size(), "?"));
        return new WhereCondition(column + " IN (" + placeholders + ")", tenants);
    }
}