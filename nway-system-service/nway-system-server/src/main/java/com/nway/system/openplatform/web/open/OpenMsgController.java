package com.nway.system.openplatform.web.open;

import com.nway.system.common.web.Result;
import com.nway.system.message.api.SendMessageDto;
import com.nway.system.message.service.MessageService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 开放平台消息接口。
 *
 * @author nway
 */
@RestController
@RequestMapping("/open/msg")
public class OpenMsgController {

    @Autowired
    private MessageService messageService;

    /**
     * 发送消息。
     */
    @PostMapping("send")
    public Result<String> send(@RequestBody SendMessageDto messageReq) {
        String messageId = messageService.sendMessage(messageReq);
        return Result.ok(messageId);
    }
}
