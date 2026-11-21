package com.nway.system.message.support.resolver.impl;

import com.nway.system.message.support.resolver.MessageTargetResolver;
import com.nway.system.message.domain.enums.MessageTargetTypeEnum;
import com.nway.system.message.entity.MessageTarget;
import com.nway.system.identity.dao.UserRoleDao;
import com.nway.system.identity.entity.UserRole;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 角色目标解析：按角色 id 查询关联用户工号
 */
@Component
@RequiredArgsConstructor
public class RoleTargetResolver implements MessageTargetResolver {

    private final UserRoleDao userRoleDao;

    @Override
    public int getTargetType() {
        return MessageTargetTypeEnum.ROLE.getCode();
    }

    @Override
    public Set<String> resolveUserIds(List<MessageTarget> targets) {
        List<String> roleIds = targets.stream()
                .map(MessageTarget::getTargetId)
                .distinct()
                .toList();
        List<UserRole> userRoles = userRoleDao.queryByRole(roleIds);
        return userRoles.stream()
                .map(UserRole::getEmployeeId)
                .collect(Collectors.toSet());
    }
}
