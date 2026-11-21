package com.nway.system.identity.web.admin;

import com.nway.system.identity.service.UserLoginKeyService;
import com.nway.system.openplatform.service.OpenSsoService;
import com.nway.system.common.security.PublicApi;
import com.nway.system.identity.web.model.ExternalChangePasswordDto;
import com.nway.system.identity.web.model.LoginDto;
import com.nway.system.common.security.LoginUser;
import com.nway.system.common.web.Result;
import com.nway.system.common.enums.ErrorCodeEnum;
import com.nway.system.identity.service.LoginService;
import com.nway.system.common.security.LoginUserContext;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@PublicApi(reason = "登录相关接口，登录用户均可访问")
@RestController
@RequestMapping("login")
public class LoginController {

    @Autowired
    private LoginService loginService;
    @Autowired
    private UserLoginKeyService loginKeyService;
    @Autowired
    private OpenSsoService openSsoService;

    /**
     * 登录认证，成功后返回 JWT Token
     */
    @PostMapping
    public Result<LoginUser> authenticate(@Validated @RequestBody LoginDto loginDTO) {
        return Result.ok(loginService.authenticate(loginDTO));
    }

    /**
     * 登出（JWT 无状态，后端仅清除权限缓存）
     */
    @PostMapping("logout")
    public Result<Boolean> logout(@RequestHeader("Authorization") String token) {
        String jwt = token.startsWith("Bearer ") ? token.substring(7) : token;
        return Result.ok(loginService.logout(jwt));
    }

    /**
     * 获取当前登录用户信息。
     * <p>无 Token、Token 过期/篡改、用户被禁用时统一返回 HTTP 401（与受保护接口的鉴权语义一致），
     * 不再出现“缺少请求头 400 / 无效 Token 200+null”的多套分支。
     */
    @GetMapping("getLoginUser")
    public ResponseEntity<Result<LoginUser>> getLoginUser(
            @RequestHeader(value = "Authorization", required = false) String token) {
        if (StringUtils.isBlank(token)) {
            return unauthorized("未登录");
        }
        String jwt = token.startsWith("Bearer ") ? token.substring(7) : token;
        LoginUser loginUser = loginService.getLoginUser(jwt);
        if (loginUser == null) {
            return unauthorized(ErrorCodeEnum.UNAUTHORIZED.getMsg());
        }
        return ResponseEntity.ok(Result.ok(loginUser));
    }

    /**
     * 鉴权失败统一出口：HTTP 401 + 统一 Result 结构（body code=401）。
     */
    private static ResponseEntity<Result<LoginUser>> unauthorized(String message) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(Result.error(ErrorCodeEnum.UNAUTHORIZED, message));
    }

    /**
     * Token 续期：旧 JWT 未过期且剩余时间不足续期阈值（nway.jwt.refresh-threshold-millis）时签发新 JWT。
     * 已过期的 JWT 无法续期，前端应跳转登录页。
     */
    @PostMapping("refresh")
    public Result<String> refreshToken(@RequestHeader("Authorization") String token) {
        String jwt = token.startsWith("Bearer ") ? token.substring(7) : token;
        String newToken = loginService.refreshToken(jwt);
        if (newToken == null) {
            return Result.error(401, "Token 已过期，请重新登录");
        }
        return Result.ok(newToken);
    }

    /**
     * 外部身份认证账号自助改密（登录前密码过期场景）。
     * <p>匿名接口：凭旧密码证明身份，成功后需用新密码重新登录。
     */
    @PostMapping("external/changePassword")
    public Result<Void> changeExternalPassword(@Validated @RequestBody ExternalChangePasswordDto dto) {
        loginService.changeExternalPassword(dto.getUsername(), dto.getOldPassword(), dto.getNewPassword());
        return Result.ok();
    }

    /**
     * 验证 Token 是否有效
     */
    @PostMapping("validateToken")
    public Result<Boolean> validateToken(@RequestHeader("Authorization") String token) {
        String jwt = token.startsWith("Bearer ") ? token.substring(7) : token;
        return Result.ok(loginService.validateToken(jwt));
    }

    @PostMapping("resetPassword")
    public Result<Void> resetPassword(String password) {
        String employeeId = LoginUserContext.current().getEmployeeId();
        loginKeyService.resetPassword(employeeId, password);
        return Result.ok();
    }

    /**
     * 绑定PC端指纹/面容标识
     */
    @PostMapping("updateBioPc")
    public Result<Void> updateBioPc(String bioKey) {
        loginKeyService.updateBioPc(bioKey);
        return Result.ok();
    }

    @PostMapping("updateBioMobile")
    public Result<Void> updateBioMobile(String bioKey) {
        loginKeyService.updateBioMobile(bioKey);
        return Result.ok();
    }

    /**
     * SSO 授权码生成。
     * <p>
     * 用户登录成功后，前端调用此接口生成授权码，
     * 然后重定向回子系统的 redirect_uri?code=xxx。
     *
     * @param appCode     应用编码
     * @param redirectUri 回调地址
     * @return 包含 code 和 redirect_uri 的响应
     */
    @PostMapping("sso/authorize")
    public Result<Map<String, String>> ssoAuthorize(String appCode, String redirectUri) {
        LoginUser user = LoginUserContext.current();
        if (user == null) {
            return Result.error(401, "未登录");
        }
        if (StringUtils.isBlank(appCode) || StringUtils.isBlank(redirectUri)) {
            return Result.error(400, "appCode 和 redirectUri 不能为空");
        }
        String code = openSsoService.generateAuthCode(appCode, user.getEmployeeId(), redirectUri);
        return Result.ok(Map.of("code", code, "redirectUri", redirectUri));
    }
}
