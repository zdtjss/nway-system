package com.nway.system.common.event;

import org.springframework.context.ApplicationEvent;

import java.util.Map;

/**
 * 系统事件基类。
 * <p>
 * 所有需要通知外部子应用的事件均继承此类。
 * 通过 Spring ApplicationEvent 机制发布，由 WebhookEventListener 监听并触发回调。
 *
 * @author nway
 */
public class SystemEvent extends ApplicationEvent {

    private final String eventType;
    private final Map<String, Object> data;

    public SystemEvent(Object source, String eventType, Map<String, Object> data) {
        super(source);
        this.eventType = eventType;
        this.data = data;
    }

    public String getEventType() {
        return eventType;
    }

    public Map<String, Object> getData() {
        return data;
    }
}
