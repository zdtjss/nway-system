package com.nway.system.portal.web.admin;

import com.nway.system.authorization.annotation.RequiresPermission;

import com.nway.system.tenant.service.TenantNameResolver;
import com.nway.system.common.web.Result;
import com.nway.system.common.exception.NwaySystemException;
import com.nway.system.common.util.ConverterUtil;
import com.nway.system.common.tenant.TenantUtil;
import com.nway.system.portal.dao.AppDao;
import com.nway.system.portal.entity.App;
import com.nway.system.portal.dao.MenuDao;
import com.nway.system.portal.web.model.AppVo;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.Strings;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 应用管理 Controller
 */
@Slf4j
@RequiresPermission(resource = "app")
@RestController
@RequestMapping("app")
public class AppController {

    @Autowired
    private AppDao appDao;

    @Autowired
    private MenuDao menuDao;

    @Autowired
    private TenantNameResolver tenantNameResolver;

    @GetMapping("queryAll")
    public Result<List<AppVo>> queryAll() {
        return Result.ok(toVos(appDao.queryAll()));
    }

    /**
     * 按状态查询应用
     *
     * @param tenantCode 目标角色租户（策略配置弹窗传入）：仅返回该租户 + public 共享租户的应用
     */
    @GetMapping("queryByStatus")
    public Result<List<AppVo>> queryByStatus(@RequestParam String status,
                                             @RequestParam(required = false) String tenantCode) {
        List<App> apps = StringUtils.isBlank(tenantCode)
                ? appDao.queryByStatus(status)
                : appDao.queryByStatusInTenants(status, List.of(tenantCode, TenantUtil.PUBLIC_TENANT_CODE));
        return Result.ok(toVos(apps));
    }

    @GetMapping("getById")
    public Result<AppVo> getById(@RequestParam String id) {
        return Result.ok(toVo(appDao.getById(id)));
    }

    @GetMapping("getByCode")
    public Result<AppVo> getByCode(@RequestParam String appCode) {
        return Result.ok(toVo(appDao.getByCode(appCode)));
    }

    @PostMapping("save")
    @Transactional(rollbackFor = Exception.class)
    public Result<Void> save(@RequestBody App app) {
        if (StringUtils.isBlank(app.getAppCode())) {
            throw new NwaySystemException("应用标识不能为空");
        }
        if (StringUtils.isBlank(app.getAppName())) {
            throw new NwaySystemException("应用名称不能为空");
        }
        // 非超管强制归属本租户，防止绕过前端指定其他租户（超管可指定任意租户）
        if (!TenantUtil.isSuperAdmin()) {
            app.setTenantCode(TenantUtil.currentTenantCode());
        }
        // 租户不可留空：菜单租户继承自应用，空值会把菜单一起带成空租户
        if (StringUtils.isBlank(app.getTenantCode())) {
            throw new NwaySystemException("归属租户不能为空");
        }
        TenantUtil.assertAdminCanManageTenant(app.getTenantCode());
        if (StringUtils.isBlank(app.getId())) {
            if (app.getStatus() == null) {
                app.setStatus("using");
            }
            appDao.insert(app);
        } else {
            // 编辑：先取原记录，既校验可管理性，也用于识别租户变更
            App existing = appDao.getById(app.getId());
            if (existing == null) {
                throw new NwaySystemException("应用不存在或无权限修改");
            }
            TenantUtil.assertAdminCanManageTenant(existing.getTenantCode());
            if (!Strings.CS.equals(existing.getTenantCode(), app.getTenantCode())) {
                if (TenantUtil.PUBLIC_TENANT_CODE.equals(existing.getTenantCode())) {
                    // 公共共享应用承载各租户的页面菜单，改其归属租户会整体破坏共享语义
                    throw new NwaySystemException("公共共享应用的归属租户不可变更：" + app.getAppCode());
                }
                // 只同步平台菜单的租户；低代码页面菜单记的是「页面租户」，其数据属各租户，须保持原样
                int affected = menuDao.updateTenantByApp(app.getAppCode(), app.getTenantCode());
                log.warn("应用 {} 租户 {} -> {}，已同步平台菜单 {} 条（低代码页面菜单按页面租户保留）",
                        app.getAppCode(), existing.getTenantCode(), app.getTenantCode(), affected);
            }
            appDao.updateById(app);
        }
        return Result.ok();
    }

    /** 转换 VO 并批量填充租户名 */
    private List<AppVo> toVos(List<App> apps) {
        List<AppVo> vos = ConverterUtil.convert(apps, AppVo.class);
        fillTenantNames(vos);
        return vos;
    }

    private AppVo toVo(App app) {
        if (app == null) {
            return null;
        }
        AppVo vo = ConverterUtil.convert(app, AppVo.class);
        fillTenantNames(List.of(vo));
        return vo;
    }

    private void fillTenantNames(List<AppVo> vos) {
        Map<String, String> nameMap = tenantNameResolver.resolveNames(
                ConverterUtil.extract(vos, AppVo::getTenantCode));
        vos.forEach(vo -> vo.setTenantName(nameMap.getOrDefault(vo.getTenantCode(), "")));
    }

    @PostMapping("deleteById")
    public Result<Void> deleteById(@RequestParam String id) {
        App app = appDao.getById(id);
        TenantUtil.assertAdminCanManageTenant(app != null ? app.getTenantCode() : null);
        appDao.deleteById(id);
        return Result.ok();
    }
}
