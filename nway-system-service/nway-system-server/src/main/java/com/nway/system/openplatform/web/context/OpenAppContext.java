package com.nway.system.openplatform.web.context;

import com.nway.system.authorization.domain.DataScope;
import lombok.Data;

import java.util.List;

/**
 * 开放平台请求上下文（ThreadLocal）。
 * <p>
 * 在 OpenApiInterceptor 中从 JWT 解析后设置，供 Controller / Service 获取当前调用应用信息。
 * 请求结束时由 afterCompletion 清理。
 *
 * @author nway
 */
public final class OpenAppContext {

    private OpenAppContext() {
    }

    private static final ThreadLocal<OpenAppInfo> HOLDER = new ThreadLocal<>();

    /**
     * 设置开放平台应用信息。
     */
    public static void set(OpenAppInfo info) {
        HOLDER.set(info);
    }

    /**
     * 获取当前开放平台应用信息。
     */
    public static OpenAppInfo get() {
        return HOLDER.get();
    }

    /**
     * 清理上下文。
     */
    public static void clear() {
        HOLDER.remove();
    }

    /**
     * 是否存在开放平台上下文（当前请求是否为开放平台请求）。
     */
    public static boolean exists() {
        return HOLDER.get() != null;
    }

    /**
     * 获取当前应用的数据范围。
     *
     * @return DataScope 对象；无上下文时返回最小范围 NONE（fail-closed，不返回 ALL）
     */
    public static DataScope getDataScope() {
        OpenAppInfo info = HOLDER.get();
        if (info == null) {
            return DataScope.none();
        }
        return new DataScope(info.getDataScopeType(), info.getDataScopeDeptIds());
    }

    /**
     * 开放平台应用信息。
     */
    @Data
    public static class OpenAppInfo {

        private String appCode;
        private String tenantCode;
        private List<String> grantedScopes;
        private String dataScopeType;
        private List<String> dataScopeDeptIds;
        /** 代理用户工号（user_delegate / authorization_code 模式时有值） */
        private String employeeId;

        public OpenAppInfo() {
        }

        public OpenAppInfo(String appCode, String tenantCode, List<String> grantedScopes,
                           String dataScopeType, List<String> dataScopeDeptIds, String employeeId) {
            this.appCode = appCode;
            this.tenantCode = tenantCode;
            this.grantedScopes = grantedScopes;
            this.dataScopeType = dataScopeType;
            this.dataScopeDeptIds = dataScopeDeptIds;
            this.employeeId = employeeId;
        }
    }
}
