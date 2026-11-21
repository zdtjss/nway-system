package com.nway.system.common.persistence;

import com.nway.spring.jdbc.sql.SqlType;
import com.nway.spring.jdbc.sql.fill.FillStrategy;
import com.nway.system.common.security.LoginUser;
import com.nway.system.common.exception.NwaySystemException;
import com.nway.system.common.security.LoginUserContext;
import com.nway.system.tenant.domain.TenantCodePolicy;
import com.nway.system.common.tenant.TenantUtil;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 租户编码自动填充策略。
 * <p>
 * INSERT 时自动填充当前登录用户的 tenant_code。
 * <p>
 * <b>fail-closed</b>：无登录上下文或上下文无租户时，默认<b>拒绝写入</b>（抛异常），不再静默回落
 * {@code default} —— 静默回落会让数据悄无声息地归属默认租户，事后极难归因。
 * 仅当显式开启 {@code nway.tenant.allow-default-fallback=true}（见 {@code TenantIsolationProperties}，
 * 供初始化/迁移等特殊场景）时才回落到 {@link TenantUtil#DEFAULT_TENANT_CODE}。
 * 需要在系统上下文写入时，请用 {@link TenantUtil#runAsTenant} 明确指定租户。
 *
 * @author nway
 */
public class TenantCodeStrategy implements FillStrategy {

    private static final Logger log = LoggerFactory.getLogger(TenantCodeStrategy.class);

    /** 是否允许无租户上下文时回落默认租户（默认关闭，仅特殊初始化场景显式开启）。 */
    private static volatile boolean allowDefaultFallback = false;

    /** 由 {@code TenantIsolationProperties} 在启动时注入配置值。 */
    public static void setAllowDefaultFallback(boolean value) {
        allowDefaultFallback = value;
    }

    @Override
    public boolean isSupport(SqlType sqlType) {
        return SqlType.INSERT == sqlType;
    }

    @Override
    public Object getValue(SqlType sqlType, Object val) {
        if (val != null && val != DEFAULT_NONE) {
            // 显式指定了租户编码（可能来自请求体）：先校验契约规则，再校验可写范围
            String tenantCode = String.valueOf(val);
            TenantCodePolicy.requireValid(tenantCode);
            assertWritableTenant(tenantCode);
            return val;
        }
        if (val == DEFAULT_NONE || val == null) {
            LoginUser user = LoginUserContext.current();
            String tenantCode = user != null ? user.getTenantCode() : null;
            if (StringUtils.isNotBlank(tenantCode)) {
                TenantCodePolicy.requireValid(tenantCode);
                return tenantCode;
            }
            if (allowDefaultFallback) {
                log.warn("写入缺少租户上下文，回落默认租户 '{}'（allow-default-fallback=true）",
                        TenantUtil.DEFAULT_TENANT_CODE);
                return TenantUtil.DEFAULT_TENANT_CODE;
            }
            throw new NwaySystemException(
                    "租户隔离 fail-closed：写入缺少租户上下文，已拒绝（如需系统级写入请用 TenantUtil.runAsTenant 指定租户）");
        }
        return val;
    }

    /**
     * 显式指定租户的可写校验：超管/系统上下文不限制，其余须落在可管理租户集合内。
     * <p>
     * <b>public 不再无条件放行</b>：公共租户承载跨租户共享资源，写其「本体」须超管或经
     * {@code sys_admin_tenant} 显式授权（读共享 ≠ 写授权）。仅「引用」公共资源（如把公共角色
     * 绑定给本租户用户）才允许写 public，见 {@link TenantReferenceCodeStrategy}。
     */
    protected void assertWritableTenant(String tenantCode) {
        java.util.List<String> manageable = TenantUtil.currentManageableTenantCodes();
        if (manageable == null) {
            return; // 超管 / 显式系统上下文
        }
        if (manageable.contains(tenantCode)) {
            return;
        }
        throw new NwaySystemException(
                "租户隔离 fail-closed：无权在租户 [" + tenantCode + "] 下写入数据");
    }
}