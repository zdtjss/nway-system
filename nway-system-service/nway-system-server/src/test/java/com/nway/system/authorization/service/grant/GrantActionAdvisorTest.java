package com.nway.system.authorization.service.grant;

import com.nway.system.authorization.dao.ResourceDao;
import com.nway.system.authorization.entity.SysResource;
import com.nway.system.authorization.entity.SysResourceAction;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 授权动作组合告警：只对「目录确实声明了 read 的资源」判定“有写无读”。
 */
class GrantActionAdvisorTest {

    private final ResourceDao resourceDao = mock(ResourceDao.class);
    private final GrantActionAdvisor advisor = new GrantActionAdvisor(resourceDao);

    private void resourceDeclaresRead() {
        SysResource resource = new SysResource();
        resource.setId("res_wform_data");
        when(resourceDao.getByAppAndCode("wform", "wform:data")).thenReturn(resource);
        when(resourceDao.getAction("res_wform_data", "read")).thenReturn(new SysResourceAction());
    }

    @Test
    @DisplayName("只授 write：资源声明了 read → 判定为有写无读")
    void writeWithoutReadWarns() {
        resourceDeclaresRead();

        assertThat(advisor.isWriteWithoutRead("wform", "wform:data", "[\"write\"]")).isTrue();
        assertThat(advisor.isWriteWithoutRead("wform", "wform:data", "[\"delete\"]")).isTrue();
        assertThat(advisor.isWriteWithoutRead("wform", "wform:data", "[\"export\",\"import\"]")).isTrue();
    }

    @Test
    @DisplayName("含 read 或 *：不告警")
    void readOrAllActionsIgnored() {
        resourceDeclaresRead();

        assertThat(advisor.isWriteWithoutRead("wform", "wform:data", "[\"read\",\"write\"]")).isFalse();
        assertThat(advisor.isWriteWithoutRead("wform", "wform:data", "[\"*\"]")).isFalse();
        assertThat(advisor.isWriteWithoutRead("wform", "wform:data", "[\"read\"]")).isFalse();
    }

    @Test
    @DisplayName("资源目录未声明 read（如 wform:dict 只有 write）：不误报")
    void resourceWithoutReadIgnored() {
        SysResource resource = new SysResource();
        resource.setId("res_wform_dict");
        when(resourceDao.getByAppAndCode("wform", "wform:dict")).thenReturn(resource);
        when(resourceDao.getAction("res_wform_dict", "read")).thenReturn(null);

        assertThat(advisor.isWriteWithoutRead("wform", "wform:dict", "[\"write\"]")).isFalse();
    }

    @Test
    @DisplayName("通配资源 / 未登记资源 / 空动作：不校验")
    void wildcardOrUnknownResourceIgnored() {
        assertThat(advisor.isWriteWithoutRead("wform", "*", "[\"write\"]")).isFalse();
        assertThat(advisor.isWriteWithoutRead("wform", "wform:unknown", "[\"write\"]")).isFalse();
        assertThat(advisor.isWriteWithoutRead("wform", "wform:data", "")).isFalse();
        assertThat(advisor.isWriteWithoutRead("wform", "wform:data", null)).isFalse();
    }
}
