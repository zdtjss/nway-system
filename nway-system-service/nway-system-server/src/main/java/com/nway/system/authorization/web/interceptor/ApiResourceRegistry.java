package com.nway.system.authorization.web.interceptor;

import org.apache.commons.lang3.StringUtils;

import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 服务端 API → 资源权限映射表。
 * <p>
 * 接口权限判定的唯一依据（不再信任客户端传来的 X-App-Code / X-Resource-Code / X-Action 头，
 * 避免"客户端不传即绕过"的 fail-open）。映射规则：
 * <ul>
 *   <li>appCode：内部管理接口统一归属平台内置应用 {@link #PLATFORM_APP_CODE}</li>
 *   <li>resourceCode：按 URL 首段路径映射到 sys_menu.resource_code（如 /user/save → user）</li>
 *   <li>action：按 URL 末段语义词组映射，返回"可接受的候选动作集合"，
 *       策略授予其中任意一个即放行（如 save 同时接受 create 与 edit）</li>
 * </ul>
 * 未登记的资源路径一律拒绝（fail-closed），新增受控接口需在此显式登记。
 *
 * @author nway
 */
public final class ApiResourceRegistry {

    /** 平台内置应用编码（系统管理接口的统一归属） */
    public static final String PLATFORM_APP_CODE = "nway-system";

    /**
     * URL 首段路径 → 资源标识。
     * <p>
     * 多个路径段可映射到同一资源（如 /userRole、/policy 归属其所属页面资源）。
     */
    private static final Map<String, String> PREFIX_RESOURCE = Map.ofEntries(
            Map.entry("user", "user"),
            Map.entry("userDept", "user"),
            Map.entry("dept", "dept"),
            Map.entry("position", "position"),
            Map.entry("role", "role"),
            Map.entry("userRole", "role"),
            Map.entry("policy", "role"),
            Map.entry("authz", "role"),
            Map.entry("menu", "menu"),
            Map.entry("resource", "menu"),
            Map.entry("admin", "admin"),
            Map.entry("tenant", "tenant"),
            Map.entry("app", "app"),
            Map.entry("dict", "dict"),
            Map.entry("message", "message"),
            Map.entry("onlineUser", "online_user"),
            Map.entry("sysConfig", "sys_config"),
            Map.entry("auditLog", "audit_log"),
            Map.entry("openApp", "open_app"),
            Map.entry("openWebhook", "open_webhook"),
            Map.entry("openApiLog", "open_api_log"),
            Map.entry("system", "api_scan"));

    /** 删除类动作词根 */
    private static final List<String> DELETE_ROOTS =
            List.of("delete", "remove", "revoke", "cancel", "clear", "drop");

    /** 新增/保存类动作词根（save 兼具新增与修改语义，故同时接受 create 与 edit） */
    private static final List<String> CREATE_ROOTS =
            List.of("save", "add", "create", "insert", "sync", "publish", "grant", "submit", "import");

    /** 修改类动作词根 */
    private static final List<String> EDIT_ROOTS =
            List.of("update", "edit", "modify", "enable", "disable", "reset", "assign",
                    "bind", "unbind", "sort", "move", "archive", "restore", "resend");

    /** 查询类动作词根 */
    private static final List<String> VIEW_ROOTS =
            List.of("query", "list", "get", "page", "tree", "detail", "count", "load",
                    "view", "check", "scan", "export", "stat", "polling", "validate");

    /**
     * 高危接口的显式动作声明（优先于词根/方法推断）。
     * <p>
     * 词根推断 + HTTP 方法兜底存在两类歧义，对高危接口必须显式钉死，避免弱授权越权：
     * <ul>
     *   <li>方法兜底把 POST 映射为 {@code [create, edit]}：名字不含动作词根的接口（如
     *       {@code forceLogout}/{@code toggleStatus}）会被 {@code create} 单独放行，
     *       但它们是修改类操作；</li>
     *   <li>{@code resetPassword}/{@code resetSecret} 等敏感操作不应被泛化的 {@code edit}
     *       顺带放行，单独声明为 {@code reset} 以便独立授权。</li>
     * </ul>
     * key = 去 contextPath 后的完整 servletPath。未列入者仍走推断。
     */
    private static final Map<String, List<String>> EXPLICIT_ACTIONS = Map.ofEntries(
            Map.entry("/user/resetPassword", List.of("reset")),
            Map.entry("/openApp/resetSecret", List.of("reset")),
            Map.entry("/onlineUser/forceLogout", List.of("edit")),
            Map.entry("/onlineUser/forceLogoutSession", List.of("edit")),
            Map.entry("/openApp/toggleStatus", List.of("edit")),
            Map.entry("/role/copyToTenant", List.of("create")),
            Map.entry("/admin/grant", List.of("create")),
            Map.entry("/admin/revoke", List.of("delete")));

    private ApiResourceRegistry() {
    }

    /**
     * 解析请求路径对应的资源。
     *
     * @param servletPath 去掉 contextPath 后的请求路径，如 /user/save
     * @return 资源标识；未登记返回 null（调用方应拒绝该请求）
     */
    public static ApiResource resolve(String servletPath) {
        String firstSegment = firstSegment(servletPath);
        if (StringUtils.isBlank(firstSegment)) {
            return null;
        }
        String resourceCode = PREFIX_RESOURCE.get(firstSegment);
        if (resourceCode == null) {
            return null;
        }
        return new ApiResource(PLATFORM_APP_CODE, resourceCode);
    }

    /**
     * 解析请求对应的候选动作集合（策略授予任意一个即可放行）。
     *
     * @param servletPath 去掉 contextPath 的请求路径
     * @param httpMethod  HTTP 方法
     * @return 候选动作集合；空集合表示无法确定所需动作（调用方应按 fail-closed 拒绝）
     */
    public static List<String> resolveActions(String servletPath, String httpMethod) {
        // 高危接口显式声明优先
        List<String> explicit = EXPLICIT_ACTIONS.get(StringUtils.stripEnd(servletPath, "/"));
        if (explicit != null) {
            return explicit;
        }
        String last = lastSegment(servletPath).toLowerCase(Locale.ROOT);
        if (StringUtils.isNotBlank(last)) {
            if (startsWithAny(last, DELETE_ROOTS)) {
                return List.of("delete");
            }
            if (startsWithAny(last, CREATE_ROOTS)) {
                return List.of("create", "edit");
            }
            if (startsWithAny(last, EDIT_ROOTS)) {
                return List.of("edit");
            }
            if (startsWithAny(last, VIEW_ROOTS)) {
                return List.of("view");
            }
        }
        // 兜底：按 HTTP 方法推导
        if (StringUtils.isBlank(httpMethod)) {
            return List.of();
        }
        return switch (httpMethod.toUpperCase(Locale.ROOT)) {
            case "GET" -> List.of("view");
            case "POST" -> List.of("create", "edit");
            case "PUT", "PATCH" -> List.of("edit");
            case "DELETE" -> List.of("delete");
            default -> List.of();
        };
    }

    private static boolean startsWithAny(String value, List<String> roots) {
        for (String root : roots) {
            if (value.startsWith(root)) {
                return true;
            }
        }
        return false;
    }

    private static String firstSegment(String path) {
        String trimmed = StringUtils.strip(path, "/");
        if (StringUtils.isBlank(trimmed)) {
            return null;
        }
        int idx = trimmed.indexOf('/');
        return idx > 0 ? trimmed.substring(0, idx) : trimmed;
    }

    private static String lastSegment(String path) {
        String trimmed = StringUtils.strip(path, "/");
        if (StringUtils.isBlank(trimmed)) {
            return "";
        }
        int idx = trimmed.lastIndexOf('/');
        return idx >= 0 ? trimmed.substring(idx + 1) : trimmed;
    }

    /**
     * 请求对应的资源标识。
     *
     * @param appCode      应用编码
     * @param resourceCode 资源标识
     */
    public record ApiResource(String appCode, String resourceCode) {
    }
}