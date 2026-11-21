package com.nway.system.identity.event;

import com.nway.system.common.event.SystemEvent;
import java.util.Map;

/**
 * 用户变更事件。
 * <p>
 * 事件类型：user.created / user.updated / user.disabled
 *
 * @author nway
 */
public class UserChangedEvent extends SystemEvent {

    public UserChangedEvent(Object source, String action, Map<String, Object> data) {
        super(source, "user." + action, data);
    }
}
