package com.nway.system.tenant.domain;

import com.nway.system.common.exception.NwaySystemException;
import org.apache.commons.lang3.StringUtils;

import java.util.regex.Pattern;

/**
 * 租户编码规则（单一事实源，对应 contracts/host-tenant.json）。
 * <p>
 * 规则：字母开头（大小写均可），字母/数字/下划线，长度 2-20。
 * <ul>
 *   <li>上限 20：与除 {@code sys_tenant} 外所有业务表 {@code tenant_code varchar(20)} 对齐，
 *       否则长编码会在业务表写入时报 DataError 1406；</li>
 *   <li>大小写敏感、原样传递：编码兼作 wform 物理表名片段（{@code d_{code}_{domain}}），
 *       不做归一化。</li>
 * </ul>
 */
public final class TenantCodePolicy {

    public static final String PATTERN_TEXT = "^[A-Za-z][A-Za-z0-9_]{1,19}$";
    public static final Pattern PATTERN = Pattern.compile(PATTERN_TEXT);
    public static final int MAX_LENGTH = 20;

    private TenantCodePolicy() {
    }

    public static boolean isValid(String tenantCode) {
        return StringUtils.isNotBlank(tenantCode) && PATTERN.matcher(tenantCode).matches();
    }

    /**
     * 校验并返回租户编码；非法时抛出业务异常（fail-closed）。
     */
    public static String requireValid(String tenantCode) {
        if (StringUtils.isBlank(tenantCode)) {
            throw new NwaySystemException("租户编码不能为空");
        }
        if (!PATTERN.matcher(tenantCode).matches()) {
            throw new NwaySystemException("租户编码格式非法：" + tenantCode
                    + "（须字母开头，字母/数字/下划线，长度 2-" + MAX_LENGTH + "）");
        }
        return tenantCode;
    }
}
