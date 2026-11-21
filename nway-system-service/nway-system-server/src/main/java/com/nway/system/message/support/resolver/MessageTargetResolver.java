package com.nway.system.message.support.resolver;

import com.nway.system.message.entity.MessageTarget;

import java.util.List;
import java.util.Set;

/**
 * 消息目标解析器，将目标（用户/部门/角色）解析为用户工号集合
 */
public interface MessageTargetResolver {

    /**
     * 目标类型，对应 MessageTargetTypeEnum.code
     */
    int getTargetType();

    /**
     * 解析目标为用户工号集合
     *
     * @param targets 同类型的目标列表
     * @return 用户工号集合
     */
    Set<String> resolveUserIds(List<MessageTarget> targets);
}
