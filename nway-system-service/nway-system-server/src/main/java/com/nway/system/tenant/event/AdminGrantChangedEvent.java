package com.nway.system.tenant.event;

import com.nway.system.common.event.SystemEvent;
import java.util.Map;

/**
 * 管理员授权变更事件（审计用）。
 * <p>
 * 事件类型：admin_grant.changed
 *
 * @author nway
 */
public class AdminGrantChangedEvent extends SystemEvent {

    public AdminGrantChangedEvent(Object source, Map<String, Object> data) {
        super(source, "admin_grant.changed", data);
    }
}
