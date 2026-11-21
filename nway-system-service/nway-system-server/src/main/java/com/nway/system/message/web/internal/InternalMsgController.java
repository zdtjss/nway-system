package com.nway.system.message.web.internal;

import com.nway.system.message.service.MessageService;
import com.nway.system.common.web.Result;
import com.nway.system.message.api.SendMessageDto;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 平台内部服务接口：消息发送。
 * <p>
 * 仅供 workflow-engine 等平台内服务通过内部服务令牌调用，不对外暴露、不走开放平台。
 */
@RestController
@RequestMapping("/internal/msg")
public class InternalMsgController {

    private final MessageService messageService;

    public InternalMsgController(MessageService messageService) {
        this.messageService = messageService;
    }

    /**
     * 发送站内消息。
     */
    @PostMapping("send")
    public Result<String> send(@RequestBody SendMessageDto request) {
        return Result.ok(messageService.sendMessage(request));
    }
}
