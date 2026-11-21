package com.nway.system.openplatform.web.open;

import com.nway.system.tenant.service.TenantService;
import com.nway.system.openplatform.service.OpenTenantScopeResolver;
import com.nway.system.tenant.web.model.TenantDto;
import com.nway.system.common.web.Result;
import com.nway.system.openplatform.web.context.OpenAppContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 开放平台租户接口。
 *
 * @author nway
 */
@RestController
@RequestMapping("/open/tenant")
public class OpenTenantController {

    @Autowired
    private TenantService tenantService;

    @Autowired
    private OpenTenantScopeResolver openTenantScopeResolver;

    /**
     * 查询当前应用所属租户信息。
     */
    @GetMapping("current")
    public Result<TenantDto> current() {
        OpenAppContext.OpenAppInfo appInfo = OpenAppContext.get();
        if (appInfo == null || appInfo.getTenantCode() == null) {
            return Result.error(400, "无法获取租户信息");
        }
        TenantDto tenant = tenantService.getByCode(appInfo.getTenantCode());
        return Result.ok(tenant);
    }

    /**
     * 查询代理用户可管理的租户列表（超管=全部启用租户；管理员=所属租户+授权租户；普通用户=所属租户）。
     * <p>
     * 与 {@code /open/token} 的 {@code targetTenantCode} 可选范围同源：低代码平台据此渲染租户切换/下拉。
     */
    @GetMapping("manageable")
    public Result<List<TenantDto>> manageable() {
        OpenAppContext.OpenAppInfo appInfo = OpenAppContext.get();
        if (appInfo == null) {
            return Result.error(400, "无法获取代理用户信息");
        }
        if (appInfo.getEmployeeId() == null || appInfo.getEmployeeId().isBlank()) {
            return Result.error(400, "该接口仅支持 user_delegate 授权模式（需携带代理用户上下文）");
        }
        return Result.ok(openTenantScopeResolver.manageableTenantDetails(appInfo.getEmployeeId()));
    }
}
