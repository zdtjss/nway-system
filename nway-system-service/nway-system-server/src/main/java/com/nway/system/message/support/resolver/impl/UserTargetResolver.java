package com.nway.system.message.support.resolver.impl;

import com.nway.system.message.support.resolver.MessageTargetResolver;
import com.nway.system.message.domain.enums.MessageTargetTypeEnum;
import com.nway.system.message.entity.MessageTarget;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 用户目标解析：target_id 即为用户工号
 */
@Component
public class UserTargetResolver implements MessageTargetResolver {

    @Override
    public int getTargetType() {
        return MessageTargetTypeEnum.USER.getCode();
    }

    @Override
    public Set<String> resolveUserIds(List<MessageTarget> targets) {
        return targets.stream()
                .map(MessageTarget::getTargetId)
                .collect(Collectors.toSet());
    }
}
