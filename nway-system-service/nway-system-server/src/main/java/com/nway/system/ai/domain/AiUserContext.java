package com.nway.system.ai.domain;

import lombok.Data;

import java.util.List;

/**
 * 一次 AI 请求的用户上下文。
 * <p>
 * 由 Controller 线程从登录态构造，放进 AgentScope RuntimeContext；
 * 工具在 reactor 线程执行时从这里取身份，避免依赖 ThreadLocal。
 *
 * @author nway
 */
@Data
public class AiUserContext {

    private String employeeId;

    private String employeeName;

    private String tenantCode;

    /** 管理员当前工作租户（X-Work-Tenant），空表示未切换 */
    private String workTenantCode;

    private List<String> roleCodes;

    private List<String> deptCodes;

    /** 用户 JWT，调用业务接口时透传 */
    private String token;

    private boolean superAdmin;

    private boolean admin;

    private String conversationId;

    /** 本轮用户原始消息，供接口检索兜底，避免模型改写或翻译导致词面召回失败 */
    private String currentMessage;

    /** 实际业务租户：管理员切到工作租户后以工作租户为准 */
    public String effectiveTenantCode() {
        return workTenantCode == null || workTenantCode.isBlank() ? tenantCode : workTenantCode;
    }
}
