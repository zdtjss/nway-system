package com.nway.system.identity.domain.auth.external;

import com.alibaba.fastjson2.JSON;
import com.nway.system.common.exception.NwaySystemException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 外部认证响应解析：错误码透传与用户映射。
 */
@DisplayName("ExternalAuthClient：外部认证响应解析")
class ExternalAuthClientTest {

    @Test
    @DisplayName("code=200 视为成功")
    void acceptsSuccess() {
        ExternalAuthClient.assertSuccess(JSON.parseObject("{\"code\":200,\"data\":{}}"));
    }

    @Test
    @DisplayName("非 200 透传外部系统 code 与 msg")
    void propagatesErrorCode() {
        assertThatThrownBy(() -> ExternalAuthClient.assertSuccess(
                JSON.parseObject("{\"code\":1010,\"msg\":\"工号或密码错误\"}")))
                .isInstanceOf(NwaySystemException.class)
                .hasMessage("工号或密码错误")
                .satisfies(e -> assertThat(((NwaySystemException) e).getCode())
                        .isEqualTo(1010));
    }

    @Test
    @DisplayName("用户档案：姓名/邮箱/手机映射，empid 缺失时回退登录名")
    void mapsUser() {
        ExternalAuthClient.ExternalUser user = ExternalAuthClient.toUser(
                JSON.parseObject("{\"cnname\":\"张三\",\"email\":\"a@b.com\",\"mobilephone\":\"13800000000\"}"));

        assertThat(user.getEmployeeId()).isEqualTo("10086");
        assertThat(user.getRealName()).isEqualTo("张三");
        assertThat(user.getEmail()).isEqualTo("a@b.com");
        assertThat(user.getMobile()).isEqualTo("13800000000");
    }
}
