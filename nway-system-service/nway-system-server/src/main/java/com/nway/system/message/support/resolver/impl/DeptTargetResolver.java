package com.nway.system.message.support.resolver.impl;

import com.nway.system.message.support.resolver.MessageTargetResolver;
import com.nway.system.message.domain.enums.MessageTargetTypeEnum;
import com.nway.system.identity.dao.SysDeptDao;
import com.nway.system.identity.entity.SysDept;
import com.nway.system.message.entity.MessageTarget;
import com.nway.system.identity.dao.UserDao;
import com.nway.system.identity.entity.User;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 部门目标解析：按部门层级编码查询子树内所有用户（含子部门）
 * 层级编码设计的核心优势：LIKE 'code%' 一次查询覆盖整棵子树，无需递归
 */
@Component
@RequiredArgsConstructor
public class DeptTargetResolver implements MessageTargetResolver {

    private final UserDao userDao;
    private final SysDeptDao sysDeptDao;

    @Override
    public int getTargetType() {
        return MessageTargetTypeEnum.DEPT.getCode();
    }

    @Override
    public Set<String> resolveUserIds(List<MessageTarget> targets) {
        List<String> deptIds = targets.stream()
                .map(MessageTarget::getTargetId)
                .distinct()
                .toList();
        // 查询部门信息获取层级编码
        List<SysDept> depts = sysDeptDao.queryByIds(deptIds);
        // 按每个部门的层级编码前缀查询子树用户（含子部门）
        List<User> allUsers = new ArrayList<>();
        for (SysDept dept : depts) {
            if (dept.getDeptCode() != null) {
                allUsers.addAll(userDao.getByDeptCodePrefix(dept.getDeptCode()));
            }
        }
        return allUsers.stream()
                .map(User::getEmployeeId)
                .collect(Collectors.toSet());
    }
}
