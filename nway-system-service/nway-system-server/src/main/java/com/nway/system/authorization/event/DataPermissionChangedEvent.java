package com.nway.system.authorization.event;

import com.nway.system.common.event.SystemEvent;
import java.util.Map;

/**
 * 数据权限变更事件。
 * <p>
 * 事件类型：data_permission.changed
 *
 * @author nway
 */
public class DataPermissionChangedEvent extends SystemEvent {

    public DataPermissionChangedEvent(Object source, Map<String, Object> data) {
        super(source, "data_permission.changed", data);
    }
}
