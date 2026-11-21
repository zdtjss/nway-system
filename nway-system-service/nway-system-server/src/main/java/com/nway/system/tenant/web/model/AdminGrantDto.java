package com.nway.system.tenant.web.model;

import com.alibaba.fastjson2.JSON;
import lombok.Data;

import java.util.List;

/**
 * 管理员授权信息。
 * <p>
 * 管理员授权维度仅为租户（业务大类从属于租户，无需独立授权）。
 * </p>
 */
@Data
public class AdminGrantDto {

    /**
     * 管理员工号
     */
    private String employeeId;

    /**
     * 管理员姓名（查询输出，便于前端列表展示）
     */
    private String realName;

    /**
     * 是否平台管理员（查询输出）
     */
    private boolean admin;

    /**
     * 是否超级管理员（以 {@code sys_user.is_super_admin} 为准）。
     * <p>
     * 授权判定不得信任 JWT 声明：超管资格只能来自库（经 60s 缓存），
     * 否则降权后旧 Token 仍能以超管身份操作，直到 Token 过期。
     */
    private boolean superAdmin;

    /**
     * 可管理的租户编码列表（授权租户）
     */
    private List<String> tenantCodes;

    /**
     * 从缓存对象还原 {@link AdminGrantDto}。
     * <p>
     * Redis 缓存经 Jackson 反序列化后为 LinkedHashMap，用 fastjson2 还原为 DTO。
     * </p>
     */
    public static AdminGrantDto from(Object cached) {
        if (cached == null) {
            return null;
        }
        if (cached instanceof AdminGrantDto dto) {
            return dto;
        }
        return JSON.parseObject(JSON.toJSONString(cached), AdminGrantDto.class);
    }
}
