package com.nway.system.openplatform.support;

import com.nway.system.common.event.SystemEvent;
import com.nway.system.openplatform.service.OpenWebhookService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Webhook 事件监听器。
 * <p>
 * 监听 SystemEvent，在事务提交后调用 OpenWebhookService 异步发送回调，
 * 避免事务回滚后仍向外部子应用发送了已撤销的变更通知。
 * <p>
 * {@code fallbackExecution = true}：Webhook 属于 best-effort 外部通知，
 * 当发布点未处于事务时也立即执行，宁可多发不漏发（子应用轮询版本号会自我修正）。
 *
 * @author nway
 */
@Slf4j
@Component
public class WebhookEventListener {

    @Autowired
    private OpenWebhookService openWebhookService;

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onSystemEvent(SystemEvent event) {
        try {
            openWebhookService.publishEvent(event.getEventType(), event.getData());
        } catch (Exception e) {
            log.error("Webhook 事件处理失败: eventType={}", event.getEventType(), e);
        }
    }
}
