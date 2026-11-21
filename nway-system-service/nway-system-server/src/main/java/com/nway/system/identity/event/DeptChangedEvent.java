package com.nway.system.identity.event;

import com.nway.system.common.event.SystemEvent;
import java.util.Map;

/**
 * 部门变更事件。
 * <p>
 * 事件类型：dept.changed
 *
 * @author nway
 */
public class DeptChangedEvent extends SystemEvent {

    public DeptChangedEvent(Object source, Map<String, Object> data) {
        super(source, "dept.changed", data);
    }
}
