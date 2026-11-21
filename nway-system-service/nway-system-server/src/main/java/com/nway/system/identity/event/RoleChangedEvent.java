package com.nway.system.identity.event;

import com.nway.system.common.event.SystemEvent;
import java.util.Map;

/**
 * 角色变更事件。
 * <p>
 * 事件类型：role.changed
 *
 * @author nway
 */
public class RoleChangedEvent extends SystemEvent {

    public RoleChangedEvent(Object source, Map<String, Object> data) {
        super(source, "role.changed", data);
    }
}
