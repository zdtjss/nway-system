package com.nway.system.authorization.service.decision;

import java.util.List;

/**
 * 决策主体上下文。
 * <p>
 * 只包含判定所需的身份维度：工号、所属租户、工作租户（管理员跨租户办公时的操作范围）、
 * 超管标志与部门编码（DEPT/DEPT_TREE 范围解析用）。
 * <p>
 * 工作租户参与决策：切换后授权视野收敛到 {@code workTenant} 的授权与资源
 * （PLATFORM/SHARED 资源对所有租户可用，TENANT 资源仅归属租户可用）。
 * 未指定工作租户时按归属租户收敛。
 *
 * @param employeeId 工号
 * @param tenantCode 所属租户
 * @param workTenant 工作租户（X-Work-Tenant，空=未指定）
 * @param superAdmin 是否超级管理员
 * @param deptCodes  主部门编码（单元素；无部门时为空）
 */
public record SubjectContext(String employeeId, String tenantCode, String workTenant, boolean superAdmin,
                             List<String> deptCodes) {
}
