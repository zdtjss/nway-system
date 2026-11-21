package com.nway.system.openplatform.web.open;

import com.nway.system.openplatform.service.OpenTokenService;
import com.nway.system.common.web.Result;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 开放平台 Token 端点。
 * <p>
 * 三种授权模式：client_credentials / user_delegate / authorization_code
 *
 * @author nway
 */
@RestController
public class OpenTokenController {

    @Autowired
    private OpenTokenService openTokenService;

    /**
     * 获取开放平台 Token。
     *
     * @param appCode          应用编码
     * @param appSecret        应用密钥（明文）
     * @param grantType        授权模式
     * @param userToken        用户 JWT（user_delegate 模式）
     * @param code             授权码（authorization_code 模式）
     * @param targetTenantCode 数据作用域租户（可空，仅 user_delegate；须 ∈ 代理用户可管理租户，
     *                         缺省=应用所属租户）
     */
    @PostMapping("/open/token")
    public Result<Map<String, Object>> token(
            @RequestParam String appCode,
            @RequestParam String appSecret,
            @RequestParam String grantType,
            @RequestParam(required = false) String userToken,
            @RequestParam(required = false) String code,
            @RequestParam(required = false) String targetTenantCode) {
        try {
            Map<String, Object> token = openTokenService.issueToken(appCode, appSecret, grantType, userToken, code,
                    targetTenantCode);
            return Result.ok(token);
        } catch (Exception e) {
            return Result.error(401, e.getMessage());
        }
    }
}
