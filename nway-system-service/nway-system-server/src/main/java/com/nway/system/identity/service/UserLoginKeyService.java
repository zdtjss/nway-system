package com.nway.system.identity.service;

import com.nway.system.identity.support.TokenInvalidator;
import com.nway.system.common.enums.ErrorCodeEnum;
import com.nway.system.common.exception.NwaySystemException;
import com.nway.system.common.security.LoginUserContext;
import com.nway.system.identity.dao.UserLoginKeyDao;
import com.nway.system.identity.entity.User;
import com.nway.system.identity.entity.UserLoginKey;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;

/**
 * 用户登录凭证服务。
 * <p>
 * 密码使用 BCrypt 哈希存储（无需手动加盐，BCrypt 自带盐值）。
 * 系统未上线，直接使用 BCrypt，不兼容旧 SHA-256。
 *
 * @author nway
 */
@Service
public class UserLoginKeyService {

    private static final BCryptPasswordEncoder PASSWORD_ENCODER = new BCryptPasswordEncoder();

    /** 初始密码字符集（排除易混淆字符 0/O/1/I/l） */
    private static final String PWD_CHARS =
            "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnpqrstuvwxyz23456789!@#$%^&*";

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    @Autowired
    private UserLoginKeyDao loginKeyDao;

    @Autowired
    private PasswordPolicyService passwordPolicyService;

    @Autowired
    private TokenInvalidator tokenInvalidator;

    /**
     * 初始化登录凭证（新用户）。
     * <p>
     * 生成一次性随机初始密码（不再使用固定弱口令），并置"需修改密码"标记，
     * 用户首次登录后必须自行改密才能访问业务接口。
     *
     * @param user 新用户
     * @return 明文初始密码（仅本次调用返回，供创建者转告用户）
     */
    public String initLoginKey(User user) {
        String initialPassword = generateInitialPassword();
        UserLoginKey loginKey = new UserLoginKey();
        loginKey.setEmployeeId(user.getEmployeeId());
        loginKey.setLoginName(user.getEmployeeId());
        loginKey.setPassword(PASSWORD_ENCODER.encode(initialPassword));
        loginKey.setIsValid(Boolean.TRUE);
        loginKey.setPwdResetRequired(Boolean.TRUE);
        // 登录凭证必须归属于目标用户的租户：显式指定，避免 nway-jdbc 按“当前操作者上下文”
        // 自动填充租户（管理员在默认租户下为其它租户建用户时会被错填），
        // 否则后续该用户在其租户上下文内改密等操作将因租户不匹配而静默更新 0 行。
        loginKey.setTenantCode(user.getTenantCode());
        loginKeyDao.insert(loginKey);
        return initialPassword;
    }

    public UserLoginKey getByLoginName(String loginName) {
        return loginKeyDao.getByLoginName(loginName);
    }

    public UserLoginKey getByBioMobile(String bioMobile) {
        return loginKeyDao.getByBioMobile(hashBio(bioMobile));
    }

    public UserLoginKey getByBioPc(String bioPc) {
        return loginKeyDao.getByBioPc(hashBio(bioPc));
    }

    /**
     * 重置密码（BCrypt 哈希）。
     * <p>
     * 重置前校验密码策略与历史，成功后记录密码历史。
     *
     * @param employeeId 工号
     * @param password   原始密码
     */
    public void resetPassword(String employeeId, String password) {
        // 校验密码策略
        passwordPolicyService.validatePassword(password);
        // 校验密码历史（不允许与最近 5 次重复）
        if (passwordPolicyService.checkHistory(employeeId, password)) {
            throw new NwaySystemException(ErrorCodeEnum.PASSWORD_POLICY_VIOLATION, "新密码不能与最近使用过的密码重复");
        }
        String encodedPassword = PASSWORD_ENCODER.encode(password);
        UserLoginKey loginKey = new UserLoginKey();
        loginKey.setEmployeeId(employeeId);
        loginKey.setPassword(encodedPassword);
        // 用户已自行设置新密码，解除"需修改密码"限制
        loginKey.setPwdResetRequired(Boolean.FALSE);
        int effect = loginKeyDao.updateByEmployeeId(loginKey);
        // 守卫：更新 0 行（如登录凭证租户与当前上下文不匹配）必须显式报错，
        // 不能静默“成功”导致旧密码仍生效、pwd_reset_required 未清除
        if (effect != 1) {
            throw new NwaySystemException("密码修改失败，请重新登录后重试");
        }
        // 记录密码历史
        passwordPolicyService.recordPassword(employeeId, encodedPassword);
        // 使所有已签发 Token 失效（密码修改后旧 Token 不应继续有效）
        tokenInvalidator.invalidateUserTokens(employeeId);
    }

    /**
     * 管理员重置指定用户密码为一次性随机密码。
     * <p>
     * 跳过密码策略与历史校验，重置后置"需修改密码"标记并使用户所有 Token 失效（强制重新登录）。
     *
     * @param employeeId 目标用户工号
     * @return 明文初始密码（仅本次调用返回，供管理员转告用户）
     */
    public String adminResetPassword(String employeeId) {
        String initialPassword = generateInitialPassword();
        UserLoginKey loginKey = new UserLoginKey();
        loginKey.setEmployeeId(employeeId);
        loginKey.setPassword(PASSWORD_ENCODER.encode(initialPassword));
        loginKey.setPwdResetRequired(Boolean.TRUE);
        int effect = loginKeyDao.updateByEmployeeId(loginKey);
        // 守卫：更新 0 行时不静默成功（避免旧密码仍可用却已广播 Token 失效）
        if (effect != 1) {
            throw new NwaySystemException("重置密码失败，登录凭证不存在或租户不匹配");
        }
        // 使所有已签发 Token 失效
        tokenInvalidator.invalidateUserTokens(employeeId);
        return initialPassword;
    }

    /**
     * 是否要求该用户先修改密码（新建/重置后为 true）。
     *
     * @param employeeId 工号
     * @return true 表示需先改密
     */
    public boolean isPwdResetRequired(String employeeId) {
        UserLoginKey loginKey = loginKeyDao.getByEmployeeId(employeeId);
        return loginKey != null && Boolean.TRUE.equals(loginKey.getPwdResetRequired());
    }

    /**
     * 生成一次性随机初始密码（12 位，含大小写字母、数字与特殊字符，满足密码策略）。
     */
    private static String generateInitialPassword() {
        StringBuilder sb = new StringBuilder(12);
        for (int i = 0; i < 12; i++) {
            sb.append(PWD_CHARS.charAt(SECURE_RANDOM.nextInt(PWD_CHARS.length())));
        }
        return sb.toString();
    }

    public void disableByEmployeeId(String employeeId) {
        loginKeyDao.disableByEmployeeId(employeeId);
    }

    /**
     * 绑定PC端指纹/面容标识
     */
    @Transactional(rollbackFor = Exception.class)
    public void updateBioPc(String bioPc) {
        String employeeId = LoginUserContext.current().getEmployeeId();
        UserLoginKey loginKey = new UserLoginKey();
        loginKey.setEmployeeId(employeeId);
        loginKey.setBioPc(hashBio(bioPc));
        int effect = loginKeyDao.updateByEmployeeId(loginKey);
        if (effect != 1) {
            throw new NwaySystemException("登录信息修改失败");
        }
    }

    /**
     * 绑定移动端指纹/面容标识
     */
    @Transactional(rollbackFor = Exception.class)
    public void updateBioMobile(String bioMobile) {
        String employeeId = LoginUserContext.current().getEmployeeId();
        UserLoginKey loginKey = new UserLoginKey();
        loginKey.setEmployeeId(employeeId);
        loginKey.setBioMobile(hashBio(bioMobile));
        int effect = loginKeyDao.updateByEmployeeId(loginKey);
        if (effect != 1) {
            throw new NwaySystemException("登录信息修改失败");
        }
    }

    /**
     * 验证密码是否匹配（BCrypt）
     *
     * @param rawPassword    原始密码
     * @param encodedPassword 数据库中的 BCrypt 哈希
     * @return true 匹配
     */
    public boolean matchPassword(String rawPassword, String encodedPassword) {
        return PASSWORD_ENCODER.matches(rawPassword, encodedPassword);
    }

    /**
     * 生物识别标识哈希（SHA-256，用于精确匹配查找）。
     * 生物识别场景不需要慢哈希（非密码），用 SHA-256 足够。
     */
    private String hashBio(String key) {
        try {
            java.security.MessageDigest md = java.security.MessageDigest.getInstance("SHA-256");
            byte[] hash = md.digest(key.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : hash) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 not available", e);
        }
    }
}
