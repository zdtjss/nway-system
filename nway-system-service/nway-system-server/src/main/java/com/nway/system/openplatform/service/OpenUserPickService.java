package com.nway.system.openplatform.service;

import com.nway.spring.jdbc.pagination.Page;
import com.nway.system.common.web.PageVo;
import com.nway.system.common.util.ConverterUtil;
import com.nway.system.identity.dao.SysDeptDao;
import com.nway.system.identity.entity.SysDept;
import com.nway.system.identity.dao.UserDao;
import com.nway.system.identity.entity.User;
import com.nway.system.identity.web.model.UserQuery;
import com.nway.system.openplatform.web.model.OpenUserPickVo;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 开放平台选人搜索服务（/open/user/search）。
 * <p>
 * 刻意不复用 {@code UserService.queryPage}，原因：
 * <ol>
 *   <li>其数据权限按平台登录用户注入；开放 Token 的占位用户（open-system）无策略，
 *       会被收敛为"仅看本人"而恒返回 0 条。本服务的数据范围改由
 *       {@link OpenDataScopeService}（应用范围 ∩ 代理用户策略）统一裁剪。</li>
 *   <li>其输出对姓名/手机号脱敏；选择器需要展示真实姓名与手机号，并支持按手机号搜索。</li>
 * </ol>
 * 租户隔离仍由开放 Token 的租户上下文自动兜底（与既有 /open/* 接口一致）。
 *
 * @author nway
 */
@Service
public class OpenUserPickService {

    /** 单页上限，防止外部系统一次拉取全量 */
    private static final int MAX_PAGE_SIZE = 100;
    private static final int DEFAULT_PAGE_SIZE = 15;

    @Autowired
    private UserDao userDao;

    @Autowired
    private SysDeptDao sysDeptDao;

    @Autowired
    private OpenDataScopeService openDataScopeService;

    /**
     * 关键字分页搜索用户（姓名 / 工号 / 手机号模糊，可按部门含子树过滤）。
     *
     * @param keyword    模糊关键字，可空
     * @param deptId     部门 id，可空
     * @param includeSub deptId 非空时是否包含下级部门
     * @param current    页码，从 1 开始
     * @param size       每页条数，夹紧到 [1, 100]
     */
    public PageVo<OpenUserPickVo> search(String keyword, String deptId, boolean includeSub, int current, int size) {
        int pageNo = current < 1 ? 1 : current;
        int pageSize = size < 1 ? DEFAULT_PAGE_SIZE : Math.min(size, MAX_PAGE_SIZE);

        OpenDataScopeService.OpenDataScope scope = openDataScopeService.resolve();
        List<String> deptIdIn = resolveDeptFilter(scope, deptId, includeSub);
        if (deptIdIn != null && deptIdIn.isEmpty()) {
            // 部门不在数据范围内：返回空页（fail-closed，不泄露范围外数据的存在性）
            return new PageVo<>(List.of(), 0L);
        }

        UserQuery query = new UserQuery();
        query.setKeyword(StringUtils.hasText(keyword) ? keyword : null);
        query.setKeywordMatchPhone(true);
        // 选择器只展示启用人员
        query.setStatus("using");
        query.setDeptIdIn(deptIdIn);
        query.setCurrent(pageNo);
        query.setSize(pageSize);

        Page<User> page = userDao.queryPage(query);
        if (page == null || CollectionUtils.isEmpty(page.getPageData())) {
            return new PageVo<>(List.of(), page == null ? 0L : page.getTotal());
        }

        List<User> users = page.getPageData();
        if (scope.selfOnlyEmployeeId() != null) {
            // 代理用户 SELF 范围：仅保留本人（client_credentials 模式为 null，属防御性分支）
            users = users.stream()
                    .filter(u -> scope.selfOnlyEmployeeId().equals(u.getEmployeeId()))
                    .toList();
        }

        List<OpenUserPickVo> vos = ConverterUtil.convert(users, OpenUserPickVo.class);
        fillDeptNames(vos);
        return new PageVo<>(vos, page.getTotal());
    }

    /**
     * 计算部门过滤集合。
     *
     * @return {@code null} = 不加部门条件（ALL 范围且未指定部门）；空集合 = 拒绝，直接返回空页
     */
    private List<String> resolveDeptFilter(OpenDataScopeService.OpenDataScope scope, String deptId, boolean includeSub) {
        if (StringUtils.hasText(deptId)) {
            if (!scope.allowsDept(deptId)) {
                return List.of();
            }
            List<String> ids = includeSub ? subtreeDeptIds(deptId) : List.of(deptId);
            if (!scope.unrestricted()) {
                ids = ids.stream().filter(scope.allowedDeptIds()::contains).toList();
            }
            return ids;
        }
        if (scope.unrestricted()) {
            return null;
        }
        return List.copyOf(scope.allowedDeptIds());
    }

    /** 部门子树（含自身）id 集合；部门不存在时返回空集合 */
    private List<String> subtreeDeptIds(String deptId) {
        SysDept dept = sysDeptDao.queryById(deptId);
        if (dept == null) {
            return List.of();
        }
        List<SysDept> subtree = sysDeptDao.querySubTree(dept.getDeptCode());
        if (CollectionUtils.isEmpty(subtree)) {
            return List.of(dept.getId());
        }
        return ConverterUtil.extract(subtree, SysDept::getId);
    }

    /** 批量回填部门名称（用户实体的 deptId 即主部门） */
    private void fillDeptNames(List<OpenUserPickVo> users) {
        List<String> deptIds = users.stream()
                .map(OpenUserPickVo::getDeptId)
                .filter(StringUtils::hasText)
                .distinct()
                .toList();
        if (deptIds.isEmpty()) {
            return;
        }
        Map<String, String> deptNames = sysDeptDao.queryByIds(deptIds).stream()
                .collect(Collectors.toMap(SysDept::getId, SysDept::getDeptName, (first, second) -> first));
        users.forEach(u -> u.setDeptName(deptNames.get(u.getDeptId())));
    }

}