package com.nway.system.tenant.web.model;

import com.alibaba.fastjson2.JSON;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 管理员授权 DTO 的缓存往返。
 * <p>
 * L2（Redis）经 Jackson 反序列化后是 {@code LinkedHashMap}，{@link AdminGrantDto#from(Object)}
 * 负责还原。新增 {@code superAdmin} 字段后必须保证往返不丢，否则超管会在缓存周期内被降权。
 */
class AdminGrantDtoTest {

    @Test
    @DisplayName("经 Map 往返（Redis 场景）保留 superAdmin")
    void mapRoundTripKeepsSuperAdmin() {
        AdminGrantDto dto = new AdminGrantDto();
        dto.setEmployeeId("E1");
        dto.setRealName("张三");
        dto.setSuperAdmin(true);
        dto.setAdmin(true);
        dto.setTenantCodes(List.of("t1", "t2"));

        Object cached = JSON.parseObject(JSON.toJSONString(dto));
        AdminGrantDto restored = AdminGrantDto.from(cached);

        assertThat(restored).isNotNull();
        assertThat(restored.isSuperAdmin()).isTrue();
        assertThat(restored.isAdmin()).isTrue();
        assertThat(restored.getTenantCodes()).containsExactly("t1", "t2");
    }

    @Test
    @DisplayName("旧缓存缺 superAdmin 字段时按 false 处理（因此升级需换 key 前缀）")
    void legacyPayloadWithoutFieldDefaultsFalse() {
        AdminGrantDto restored = AdminGrantDto.from(JSON.parseObject("{\"employeeId\":\"E1\",\"admin\":true}"));

        assertThat(restored).isNotNull();
        assertThat(restored.isSuperAdmin()).isFalse();
        assertThat(restored.isAdmin()).isTrue();
    }
}
