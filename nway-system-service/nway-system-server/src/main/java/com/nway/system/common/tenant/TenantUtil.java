package com.nway.system.common.tenant;

import com.nway.system.common.security.LoginUserContext;
import com.nway.system.common.security.LoginUser;
import com.nway.system.common.exception.NwaySystemException;
import org.apache.commons.lang3.StringUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;

/**
 * 租户上下文工具。
 * <p>
 * 从 {@link LoginUserContext} 获取当前租户信息，供 DAO 层追加租户隔离条件。
 * 超级管理员可跨租户，不追加隔离条件。
 * <p>
 * 平台管理员的管理范围纯按授权租户控制（业务大类从属于租户，无需单独授权）。
 *
 * @author nway
 */
public final class TenantUtil {

    /**
     * 内置公共租户编码：该租户下的资源对所有租户只读共享。
     * 读隔离集合自动并入此租户；写权限默认仅超级管理员，管理员可经 sys_admin_tenant 显式授权。
     */
    public static final String PUBLIC_TENANT_CODE = "public";

    /**
     * 内置默认租户编码（系统初始化兜底，不可删除/改名）。
     */
    public static final String DEFAULT_TENANT_CODE = "default";

    /**
     * 系统任务身份占位工号（后台异步/定时任务使用，显式声明而非依赖"无上下文即全域放行"）。
     */
    public static final String SYSTEM_EMPLOYEE_ID = "system";

    private TenantUtil() {
    }

    /**
     * 以指定租户的身份执行代码块（异步任务等脱离请求线程的场景使用）。
     * <p>
     * 执行期间租户隔离收敛到该租户（+ public 读共享），执行结束后恢复原上下文。
     *
     * @param tenantCode 目标租户编码
     * @param action     待执行逻辑
     */
    public static void runAsTenant(String tenantCode, Runnable action) {
        LoginUser previous = LoginUserContext.current();
        LoginUser scoped = new LoginUser();
        scoped.setEmployeeId(SYSTEM_EMPLOYEE_ID);
        scoped.setTenantCode(tenantCode);
        scoped.setSuperAdmin(false);
        scoped.setAdmin(false);
        try {
            LoginUserContext.set(scoped);
            action.run();
        } finally {
            if (previous != null) {
                LoginUserContext.set(previous);
            } else {
                LoginUserContext.clean();
            }
        }
    }

    /**
     * 以系统身份（跨租户）执行代码块，用于显式声明的系统级跨租户任务（如失败消息重试扫描）。
     * <p>
     * 注意：这是唯一允许跨租户的系统入口，调用点必须明确说明业务原因；
     * 普通业务代码不得使用，异步业务逻辑请改用 {@link #runAsTenant(String, Runnable)}。
     *
     * @param action 待执行逻辑
     * @param <T>    返回值类型
     * @return 执行结果
     */
    public static <T> T callAsSystem(java.util.function.Supplier<T> action) {
        LoginUser previous = LoginUserContext.current();
        LoginUser system = new LoginUser();
        system.setEmployeeId(SYSTEM_EMPLOYEE_ID);
        system.setSuperAdmin(true);
        try {
            LoginUserContext.set(system);
            return action.get();
        } finally {
            if (previous != null) {
                LoginUserContext.set(previous);
            } else {
                LoginUserContext.clean();
            }
        }
    }

    /**
     * 获取当前租户编码。
     *
     * @return 租户编码，未登录或无上下文时返回 null
     */
    public static String currentTenantCode() {
        LoginUser user = LoginUserContext.current();
        return user != null ? user.getTenantCode() : null;
    }

    /**
     * 当前用户是否超级管理员。
     *
     * @return true 表示可跨租户
     */
    public static boolean isSuperAdmin() {
        LoginUser user = LoginUserContext.current();
        return user != null && user.isSuperAdmin();
    }

    /**
     * 当前用户是否为平台管理员（is_admin 标志，非超管）。
     *
     * @return true 表示是平台管理员
     */
    public static boolean isAdmin() {
        LoginUser user = LoginUserContext.current();
        return user != null && user.isAdmin();
    }

    /**
     * 获取当前管理员可管理的租户编码列表。
     * <p>
     * 管理员管理范围 = 授权租户列表（sys_admin_tenant）。
     * 非管理员或无上下文时返回空列表。
     *
     * @return 可管理的租户编码列表
     */
    public static List<String> currentAdminTenantCodes() {
        LoginUser user = LoginUserContext.current();
        if (user != null && user.isAdmin() && user.getAdminTenantCodes() != null) {
            return user.getAdminTenantCodes();
        }
        return Collections.emptyList();
    }

    /**
     * 校验当前用户是否有权写入指定租户（租户维度写操作守卫）。
     * <p>
     * 超管放行；平台管理员必须拥有该租户授权；<b>普通用户只能写自己所属的租户</b>。
     * 最后一条是为 UPDATE 补的：INSERT 的租户由 fillStrategy 校验，但 UPDATE 的 SET 值不受
     * 数据层隔离约束；若不校验，就能把自有行的 tenant_code 改到公共租户或其他租户。
     * <p>
     * 公共租户另有限制：读共享 ≠ 写授权，非超管（含管理员）须经 {@code sys_admin_tenant}
     * 显式授权 public 才可写其资源本体。
     *
     * @param tenantCode 目标租户编码
     * @throws NwaySystemException 越权操作时抛出
     */
    public static void assertAdminCanManageTenant(String tenantCode) {
        LoginUser user = LoginUserContext.current();
        if (user == null || user.isSuperAdmin()) {
            return;
        }
        if (PUBLIC_TENANT_CODE.equals(tenantCode)) {
            if (user.isAdmin() && currentAdminTenantCodes().contains(tenantCode)) {
                return;
            }
            throw new NwaySystemException("无权限管理该租户：" + tenantCode);
        }
        if (!user.isAdmin()) {
            if (StringUtils.isBlank(tenantCode) || !tenantCode.equals(user.getTenantCode())) {
                throw new NwaySystemException("无权限管理该租户：" + tenantCode);
            }
            return;
        }
        if (StringUtils.isBlank(tenantCode) || !currentAdminTenantCodes().contains(tenantCode)) {
            throw new NwaySystemException("无权限管理该租户：" + tenantCode);
        }
    }

    /**
     * 引用/绑定 public 共享资源的守卫（"直接使用"共享资源的场景）。
     * <p>
     * public 资源的被引用（如把公共角色绑定给用户）视为使用行为，
     * 不要求管理员持有 public 管理授权；非 public 租户仍按常规管理权校验。
     * 注意：修改 public 资源本体仍须走 {@link #assertAdminCanManageTenant}。
     *
     * @param resourceTenantCode 目标资源所属租户编码
     * @throws NwaySystemException 管理员越权操作时抛出
     */
    public static void assertAdminCanReferenceTenantResource(String resourceTenantCode) {
        if (PUBLIC_TENANT_CODE.equals(resourceTenantCode)) {
            return;
        }
        assertAdminCanManageTenant(resourceTenantCode);
    }

    /**
     * 获取当前管理员可管理（写操作目标）的租户编码集合。
     * <p>
     * 与 {@link #currentTenantCodesForIsolation()} 的区别：不含内置公共租户 public
     * （读共享不等于写授权，写 public 须超管或显式授权）。
     * 超管/无上下文返回 null（不限制）；管理员为本租户 + 授权租户列表。
     *
     * @return 可管理租户编码集合；null=不限制
     */
    public static List<String> currentManageableTenantCodes() {
        LoginUser user = LoginUserContext.current();
        if (user == null || user.isSuperAdmin()) {
            return null;
        }
        // 管理员指定了工作租户时，写操作收敛到该租户
        if (user.isAdmin() && StringUtils.isNotBlank(user.getCurrentWorkTenantCode())) {
            return new ArrayList<>(List.of(user.getCurrentWorkTenantCode()));
        }
        LinkedHashSet<String> tenants = new LinkedHashSet<>();
        if (StringUtils.isNotBlank(user.getTenantCode())) {
            tenants.add(user.getTenantCode());
        }
        if (user.isAdmin() && user.getAdminTenantCodes() != null) {
            for (String code : user.getAdminTenantCodes()) {
                if (StringUtils.isNotBlank(code)) {
                    tenants.add(code);
                }
            }
        }
        return new ArrayList<>(tenants);
    }

    /**
     * 获取当前请求需要追加租户隔离的租户编码集合。
     * <p>
     * 返回 null 表示不追加隔离条件（超管或无登录上下文）。
     * 否则返回需要匹配的租户集合：普通用户为本租户；管理员为授权租户列表；
     * 内置公共租户 {@link #PUBLIC_TENANT_CODE} 始终包含（public 资源对所有租户只读共享）。
     * <p>
     * 如果管理员设置了工作租户，则仅返回该工作租户（缩小查询范围）。
     *
     * @return 租户编码集合；null=不隔离
     */
    public static List<String> currentTenantCodesForIsolation() {
        LoginUser user = LoginUserContext.current();
        if (user == null || user.isSuperAdmin()) {
            return null;
        }
        // 管理员指定了工作租户（X-Work-Tenant）时，读隔离收敛到该工作租户（+ public 读共享），
        // 避免"以为在 A 租户办公，实际读写集合仍是全部授权租户"导致误写他租户数据
        if (user.isAdmin() && StringUtils.isNotBlank(user.getCurrentWorkTenantCode())) {
            return new ArrayList<>(List.of(user.getCurrentWorkTenantCode(), PUBLIC_TENANT_CODE));
        }
        LinkedHashSet<String> tenants = new LinkedHashSet<>();
        if (StringUtils.isNotBlank(user.getTenantCode())) {
            tenants.add(user.getTenantCode());
        }
        if (user.isAdmin() && user.getAdminTenantCodes() != null) {
            for (String code : user.getAdminTenantCodes()) {
                if (StringUtils.isNotBlank(code)) {
                    tenants.add(code);
                }
            }
        }
        // public 共享租户：对所有非超管用户始终可读（读共享收口点）
        tenants.add(PUBLIC_TENANT_CODE);
        return new ArrayList<>(tenants);
    }
}
