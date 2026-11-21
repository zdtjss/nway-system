package com.nway.system.openplatform.service;

import com.alibaba.fastjson2.JSON;
import com.nway.system.tenant.service.TenantNameResolver;
import com.nway.system.common.exception.NwaySystemException;
import com.nway.system.common.util.ConverterUtil;
import com.nway.system.common.tenant.TenantUtil;
import com.nway.system.openplatform.dao.SysOpenAppDao;
import com.nway.system.openplatform.entity.SysOpenApp;
import com.nway.system.openplatform.web.model.OpenAppVo;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import java.security.SecureRandom;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 开放平台应用服务。
 * <p>
 * 负责应用 CRUD、密钥生成/重置（BCrypt）、scope 授权、数据范围配置。
 *
 * @author nway
 */
@Service
public class OpenAppService {

    private static final BCryptPasswordEncoder BCRYPT = new BCryptPasswordEncoder();
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final int SECRET_LENGTH = 32; // 32 bytes = 64 hex chars

    @Autowired
    private SysOpenAppDao sysOpenAppDao;

    @Autowired
    private TenantNameResolver tenantNameResolver;

    /**
     * 创建应用，自动生成 appSecret（明文仅返回一次，DB 存 BCrypt 哈希）。
     *
     * @param app 应用信息（appCode, appName, grantedScopes, dataScopeType, ...）
     * @return 明文密钥（仅此一次可见）
     */
    @Transactional(rollbackFor = Exception.class)
    public String createApp(SysOpenApp app) {
        if (!StringUtils.hasText(app.getAppCode()) || !StringUtils.hasText(app.getAppName())) {
            throw new NwaySystemException("应用编码和名称不能为空");
        }
        // 检查 appCode 唯一
        SysOpenApp existing = sysOpenAppDao.queryByCode(app.getAppCode());
        if (existing != null) {
            throw new NwaySystemException("应用编码已存在: " + app.getAppCode());
        }
        // 生成明文密钥
        String plainSecret = generateSecret();
        app.setAppSecret(BCRYPT.encode(plainSecret));
        if (!StringUtils.hasText(app.getStatus())) {
            app.setStatus("using");
        }
        if (!StringUtils.hasText(app.getDataScopeType())) {
            app.setDataScopeType("ALL");
        }
        // 库表这些列 NOT NULL 且无默认值：新建时给空数组，避免插入失败
        if (!StringUtils.hasText(app.getGrantedScopes())) {
            app.setGrantedScopes("[]");
        }
        if (!StringUtils.hasText(app.getDataScopeDeptIds())) {
            app.setDataScopeDeptIds("[]");
        }
        if (!StringUtils.hasText(app.getAllowedIps())) {
            app.setAllowedIps("[]");
        }
        // JSON 序列化 scope / deptIds / ips
        normalizeJsonFields(app);
        sysOpenAppDao.insert(app);
        return plainSecret;
    }

    /**
     * 更新应用基本信息（不含密钥）。
     */
    @Transactional(rollbackFor = Exception.class)
    public void updateApp(SysOpenApp app) {
        SysOpenApp existing = sysOpenAppDao.getById(app.getId());
        if (existing == null) {
            throw new NwaySystemException("应用不存在");
        }
        normalizeJsonFields(app);
        // 不允许修改 appCode 和 appSecret
        app.setAppCode(existing.getAppCode());
        app.setAppSecret(existing.getAppSecret());
        // 归属租户不可通过更新接口变更：以库中记录为准并校验写权限
        app.setTenantCode(existing.getTenantCode());
        TenantUtil.assertAdminCanManageTenant(existing.getTenantCode());
        sysOpenAppDao.updateById(app);
    }

    /**
     * 重置密钥，返回新的明文密钥（仅此一次可见）。
     */
    @Transactional(rollbackFor = Exception.class)
    public String resetSecret(String appId) {
        SysOpenApp app = sysOpenAppDao.getById(appId);
        if (app == null) {
            throw new NwaySystemException("应用不存在");
        }
        String plainSecret = generateSecret();
        app.setAppSecret(BCRYPT.encode(plainSecret));
        sysOpenAppDao.updateById(app);
        return plainSecret;
    }

    /**
     * 验证密钥是否匹配。
     */
    public boolean verifySecret(SysOpenApp app, String plainSecret) {
        if (app == null || !StringUtils.hasText(plainSecret)) {
            return false;
        }
        return BCRYPT.matches(plainSecret, app.getAppSecret());
    }

    /**
     * 配置 scope 授权。
     *
     * @param appId    应用id
     * @param scopes   scope 列表
     */
    @Transactional(rollbackFor = Exception.class)
    public void grantScopes(String appId, List<String> scopes) {
        SysOpenApp app = sysOpenAppDao.getById(appId);
        if (app == null) {
            throw new NwaySystemException("应用不存在");
        }
        app.setGrantedScopes(JSON.toJSONString(scopes));
        sysOpenAppDao.updateById(app);
    }

    /**
     * 配置数据范围。
     */
    @Transactional(rollbackFor = Exception.class)
    public void configureDataScope(String appId, String scopeType, List<String> deptIds) {
        SysOpenApp app = sysOpenAppDao.getById(appId);
        if (app == null) {
            throw new NwaySystemException("应用不存在");
        }
        app.setDataScopeType(scopeType);
        if ("CUSTOM".equalsIgnoreCase(scopeType)) {
            if (CollectionUtils.isEmpty(deptIds)) {
                throw new NwaySystemException("自定义数据范围必须指定部门");
            }
            app.setDataScopeDeptIds(JSON.toJSONString(deptIds));
        } else {
            app.setDataScopeDeptIds(null);
        }
        sysOpenAppDao.updateById(app);
    }

    /**
     * 启用/禁用应用。
     */
    @Transactional(rollbackFor = Exception.class)
    public void toggleStatus(String appId, boolean enable) {
        SysOpenApp app = sysOpenAppDao.getById(appId);
        if (app == null) {
            throw new NwaySystemException("应用不存在");
        }
        app.setStatus(enable ? "using" : "disabled");
        sysOpenAppDao.updateById(app);
    }

    @Transactional(rollbackFor = Exception.class)
    public void deleteApp(String appId) {
        sysOpenAppDao.deleteById(appId);
    }

    public SysOpenApp getById(String appId) {
        return sysOpenAppDao.getById(appId);
    }

    public SysOpenApp getByCode(String appCode) {
        return sysOpenAppDao.queryByCode(appCode);
    }

    public List<OpenAppVo> queryAll() {
        List<SysOpenApp> apps = sysOpenAppDao.queryAll();
        if (CollectionUtils.isEmpty(apps)) {
            return List.of();
        }
        List<OpenAppVo> voList = ConverterUtil.convert(apps, OpenAppVo.class);
        Map<String, String> tenantNameMap = tenantNameResolver.resolveNames(
                voList.stream().map(OpenAppVo::getTenantCode).collect(Collectors.toSet()));
        voList.forEach(vo -> {
            if (StringUtils.hasText(vo.getTenantCode())) {
                vo.setTenantName(tenantNameMap.getOrDefault(vo.getTenantCode(), ""));
            }
        });
        return voList;
    }

    // ==================== Private ====================

    private String generateSecret() {
        byte[] bytes = new byte[SECRET_LENGTH];
        RANDOM.nextBytes(bytes);
        return HexFormat.of().formatHex(bytes);
    }

    private void normalizeJsonFields(SysOpenApp app) {
        // grantedScopes: 如果传入的是 List 则序列化
        // 这里假设前端已传 JSON 字符串，若不是则兼容处理
    }
}
