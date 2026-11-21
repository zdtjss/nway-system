package com.nway.system.message.web.admin;

import com.nway.system.authorization.annotation.RequiresPermission;

import com.nway.system.message.service.MessageManageService;
import com.nway.system.message.service.MessagePollingService;
import com.nway.system.message.service.MessageService;
import com.nway.system.common.security.PublicApi;
import com.nway.system.common.web.PageVo;
import com.nway.system.common.web.Result;
import com.nway.system.message.entity.MessageSendLog;
import com.nway.system.message.api.SendMessageDto;
import com.nway.system.message.web.model.MessageQuery;
import com.nway.system.message.web.model.MessageManageVO;
import com.nway.system.message.web.model.MessagePollingVO;
import com.nway.system.message.web.model.MessageVO;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RequiresPermission(resource = "message")
@RestController
@RequestMapping("message")
public class MessageController {

    private final MessageService messageService;
    private final MessagePollingService pollingService;
    private final MessageManageService manageService;

    public MessageController(MessageService messageService,
                             MessagePollingService pollingService,
                             MessageManageService manageService) {
        this.messageService = messageService;
        this.pollingService = pollingService;
        this.manageService = manageService;
    }

    @PostMapping("/send")
    public Result<String> sendMessage(@RequestBody SendMessageDto request) {
        return Result.ok(messageService.sendMessage(request));
    }

    @PublicApi(reason = "消息轮询，登录用户均可访问")
    @GetMapping("/polling")
    public Result<PageVo<MessagePollingVO>> pollNewMessages() {
        return Result.ok(pollingService.getNewUnreadMessages());
    }

    @PublicApi(reason = "个人消息列表，登录用户均可访问")
    @GetMapping("/list")
    public Result<List<MessageVO>> getPersonalMessages(MessageQuery query) {
        return Result.ok(messageService.getPersonalMessages(query));
    }

    @PublicApi(reason = "标记消息已读，登录用户均可访问")
    @PostMapping("/read")
    public Result<Void> markAsRead(@RequestParam String receiveId) {
        messageService.markAsRead(receiveId);
        return Result.ok();
    }

    @PublicApi(reason = "个人消息失败渠道重发，登录用户均可访问（service 层校验接收记录归属）")
    @PostMapping("/resend")
    public Result<Void> resend(@RequestParam String messageId,
                               @RequestParam String receiverId,
                               @RequestParam String channel) {
        manageService.resend(messageId, receiverId, channel);
        return Result.ok();
    }

    /**
     * 管理后台：分页查询消息发送列表，含接收人数/已读数统计
     */
    @GetMapping("/manage/list")
    public Result<PageVo<MessageManageVO>> getManageList(MessageQuery query) {
        return Result.ok(manageService.getManageList(query));
    }

    /**
     * 管理后台：查询指定消息的发送日志（按渠道、按接收人记录发送结果）
     */
    @GetMapping("/sendLog")
    public Result<List<MessageSendLog>> getSendLogs(@RequestParam String messageId) {
        return Result.ok(manageService.getSendLogs(messageId));
    }
}
