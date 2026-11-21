package com.nway.system.identity.web.admin;

import com.nway.system.authorization.annotation.RequiresPermission;

import com.nway.system.identity.web.model.OnlineUserBo;
import com.nway.system.identity.service.OnlineUserService;
import com.nway.system.common.web.Result;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 在线用户管理。
 *
 * @author nway
 */
@RequiresPermission(resource = "online_user")
@RestController
@RequestMapping("onlineUser")
public class OnlineUserController {

    @Autowired
    private OnlineUserService onlineUserService;

    /**
     * 查询在线用户列表（可选关键字搜索工号/姓名）。
     *
     * @param keyword 关键字
     * @return 在线用户列表
     */
    @GetMapping("list")
    public Result<List<OnlineUserBo>> list(String keyword) {
        return Result.ok(onlineUserService.listOnline(keyword));
    }

    /**
     * 强制下线（踢出用户全部会话，使所有已签发 Token 失效）。
     *
     * @param employeeId 工号
     * @return Result
     */
    @PostMapping("forceLogout")
    public Result<Void> forceLogout(String employeeId) {
        onlineUserService.forceLogout(employeeId);
        return Result.ok();
    }

    /**
     * 踢出指定会话（不影响该用户的其他设备登录）。
     *
     * @param employeeId 工号
     * @param sessionId  会话标识
     * @return Result
     */
    @PostMapping("forceLogoutSession")
    public Result<Void> forceLogoutSession(String employeeId, String sessionId) {
        onlineUserService.forceLogoutSession(employeeId, sessionId);
        return Result.ok();
    }
}
