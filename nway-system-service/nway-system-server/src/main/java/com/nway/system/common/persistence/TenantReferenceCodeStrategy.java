package com.nway.system.common.persistence;

import com.nway.system.common.tenant.TenantUtil;

/**
 * 租户编码自动填充策略（引用语义）。
 * <p>
 * 与 {@link TenantCodeStrategy} 的唯一区别：显式指定 {@code public} 时放行。
 * <p>
 * 专用于<b>关联表</b>（{@code sys_user_role} / {@code sys_user_dept} / {@code sys_user_position}）——
 * 这类表的 {@code tenant_code} 记录的是<b>被引用资源</b>的租户，而不是本行自身的归属租户：
 * 「把公共角色绑给本租户用户」「把用户挂到公共部门」都是引用行为，不应要求对公共租户持有写授权
 * （修改公共资源本体仍须 {@link TenantUtil#assertAdminCanManageTenant}）。
 * <p>
 * 新增关联表时须显式选择本策略还是 {@link TenantCodeStrategy}，不要默认复用前者，
 * 否则会把「引用公共资源」误判成「写公共资源本体」而拦截。
 *
 * @author nway
 */
public class TenantReferenceCodeStrategy extends TenantCodeStrategy {

    @Override
    protected void assertWritableTenant(String tenantCode) {
        if (TenantUtil.PUBLIC_TENANT_CODE.equals(tenantCode)) {
            return;
        }
        super.assertWritableTenant(tenantCode);
    }
}
