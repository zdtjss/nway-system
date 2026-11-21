package com.nway.system.openplatform.web.open;

import com.nway.system.identity.service.UserService;
import com.nway.system.identity.service.LoginService;
import com.nway.system.openplatform.service.OpenDataScopeService;
import com.nway.system.openplatform.service.OpenUserPickService;
import com.nway.system.common.security.LoginUser;
import com.nway.system.common.web.PageVo;
import com.nway.system.common.web.Result;
import com.nway.system.identity.entity.User;
import com.nway.system.identity.dao.UserDao;
import com.nway.system.openplatform.web.model.OpenUserPickVo;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 开放平台用户接口。
 * <p>
 * 所有查询均受开放数据范围约束（应用数据范围 ∩ 代理用户数据权限）。
 *
 * @author nway
 */
@RestController
@RequestMapping("/open/user")
public class OpenUserController {

    @Autowired
    private UserService userService;

    @Autowired
    private LoginService loginService;

    @Autowired
    private UserDao userDao;

    @Autowired
    private OpenDataScopeService openDataScopeService;

    @Autowired
    private OpenUserPickService openUserPickService;

    /**
     * 按工号查询用户基本信息。
     */
    @GetMapping("getByEmployeeId")
    public Result<LoginUser> getByEmployeeId(@RequestParam String employeeId) {
        LoginUser user = userService.getByEmployeeId(employeeId);
        if (user == null) {
            return Result.error(404, "用户不存在");
        }
        // 数据范围校验：范围外用户按不存在处理（不泄露存在性）
        OpenDataScopeService.OpenDataScope scope = openDataScopeService.resolve();
        if (!scope.allowsUser(user.getEmployeeId(), user.getMainDeptId())) {
            return Result.error(404, "用户不存在");
        }
        // 清除敏感信息
        user.setToken(null);
        return Result.ok(user);
    }

    /**
     * 按部门查询用户列表。
     */
    @GetMapping("queryByDept")
    public Result<List<LoginUser>> queryByDept(@RequestParam String deptId) {
        // 数据范围校验：请求部门必须在范围内
        OpenDataScopeService.OpenDataScope scope = openDataScopeService.resolve();
        if (!scope.allowsDept(deptId)) {
            return Result.ok(List.of());
        }
        List<User> users = userDao.getByDeptIds(List.of(deptId));
        List<LoginUser> result = users.stream().map(u -> {
            LoginUser lu = new LoginUser();
            lu.setEmployeeId(u.getEmployeeId());
            lu.setRealName(u.getRealName());
            lu.setStatus(u.getStatus());
            return lu;
        })
        // SELF 范围时仅保留本人
        .filter(u -> scope.allowsUser(u.getEmployeeId(), deptId))
        .toList();
        return Result.ok(result);
    }

    /**
     * 关键字分页搜索用户（选人组件用）：姓名 / 工号 / 手机号模糊匹配，可按部门（含子树）过滤。
     */
    @GetMapping("search")
    public Result<PageVo<OpenUserPickVo>> search(@RequestParam(required = false) String keyword,
                                                 @RequestParam(required = false) String deptId,
                                                 @RequestParam(defaultValue = "false") boolean includeSub,
                                                 @RequestParam(defaultValue = "1") int current,
                                                 @RequestParam(defaultValue = "15") int size) {
        return Result.ok(openUserPickService.search(keyword, deptId, includeSub, current, size));
    }

    /**
     * 验证用户 Token 是否有效。
     */
    @PostMapping("validateToken")
    public Result<Map<String, Object>> validateToken(@RequestParam String token) {
        boolean valid = loginService.validateToken(token);
        return Result.ok(Map.of("valid", valid));
    }
}
