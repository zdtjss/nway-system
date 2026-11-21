package com.nway.system.message.service;

import com.nway.spring.jdbc.pagination.Page;
import com.nway.system.common.web.PageVo;
import com.nway.system.common.util.ConverterUtil;
import com.nway.system.common.security.LoginUserContext;
import com.nway.system.common.tenant.TenantUtil;
import com.nway.system.message.dao.MessageInfoDao;
import com.nway.system.message.dao.MessageReceiverDao;
import com.nway.system.message.entity.MessageInfo;
import com.nway.system.message.entity.MessageReceiver;
import com.nway.system.message.web.model.MessagePollingVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class MessagePollingService {

    private final MessageReceiverDao receiverDao;
    private final MessageInfoDao messageInfoDao;

    @Transactional(rollbackFor = Exception.class)
    public PageVo<MessagePollingVO> getNewUnreadMessages() {
        String userId = LoginUserContext.current().getEmployeeId();
        Page<MessageReceiver> receivers = receiverDao.getNewUnreadMessages(userId);

        List<String> messageIds = ConverterUtil.extract(receivers.getPageData(), MessageReceiver::getMessageId);
        // 消息主体归属发送人租户，接收人（可能属于其他租户）按主键取内容时需跳出租户隔离；
        // 接收记录已按接收人租户过滤，归属安全由上的 receiver 查询保证
        Map<String, MessageInfo> messageMap = TenantUtil.callAsSystem(
                () -> messageInfoDao.queryByIds(messageIds));

        // 标记为已拉取（不再弹窗）
        List<String> newReceiveId = receivers.getPageData().stream()
                .filter(MessageReceiver::getIsNew)
                .map(MessageReceiver::getId)
                .toList();
        receiverDao.markAsNotNew(newReceiveId);

        LocalDateTime now = LocalDateTime.now();
        List<MessagePollingVO> voList = receivers.getPageData().stream()
                .map(receiver -> {
                    MessageInfo info = messageMap.get(receiver.getMessageId());
                    if (info == null) {
                        return null;
                    }
                    // 过滤过期消息
                    if (info.getExpireTime() != null && info.getExpireTime().isBefore(now)) {
                        return null;
                    }
                    MessagePollingVO vo = ConverterUtil.convert(receiver, MessagePollingVO.class);
                    vo.setTitle(info.getTitle());
                    vo.setSender(info.getSenderName());
                    vo.setContent(info.getContent());
                    vo.setReceiveTime(receiver.getCreateTime());
                    return vo;
                })
                .filter(Objects::nonNull)
                .toList();

        PageVo<MessagePollingVO> page = new PageVo<>();
        page.setTotal(receivers.getTotal());
        page.setData(voList);
        return page;
    }
}
