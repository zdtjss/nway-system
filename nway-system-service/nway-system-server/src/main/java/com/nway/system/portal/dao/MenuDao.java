package com.nway.system.portal.dao;

import com.nway.spring.jdbc.SqlExecutor;
import com.nway.spring.jdbc.sql.SQL;
import com.nway.spring.jdbc.sql.builder.QueryBuilder;
import com.nway.system.portal.entity.Menu;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;
import org.springframework.util.CollectionUtils;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 菜单 DAO
 */
@Repository
public class MenuDao {

    @Autowired
    private SqlExecutor sqlExecutor;

    public void insert(Menu menu) {
        sqlExecutor.insert(menu);
    }

    public void batchInsert(List<Menu> menus) {
        if (CollectionUtils.isEmpty(menus)) {
            return;
        }
        sqlExecutor.batchInsert(menus);
    }

    public void update(Menu menu) {
        sqlExecutor.updateById(menu);
    }

    public void delete(String id) {
        sqlExecutor.deleteById(id, Menu.class);
    }

    public Menu getById(String id) {
        return sqlExecutor.queryById(id, Menu.class);
    }

    public List<Menu> getByIds(Collection<String> ids) {
        if (CollectionUtils.isEmpty(ids)) {
            return Collections.emptyList();
        }
        return sqlExecutor.queryList(SQL.query(Menu.class).in(Menu::getId, ids));
    }

    public Map<String, String> queryMenuNameMap(Collection<String> ids) {
        if (CollectionUtils.isEmpty(ids)) {
            return Map.of();
        }
        QueryBuilder qb = SQL.query(Menu.class).withColumn(Menu::getId, Menu::getName).in(Menu::getId, ids);
        List<Menu> menuList = sqlExecutor.queryList(qb);
        return menuList.stream().collect(Collectors.toMap(Menu::getId, Menu::getName, (a, b) -> a));
    }

    /**
     * 按应用查询全部菜单（树构建用）
     */
    public List<Menu> queryByApp(String appCode) {
        QueryBuilder qb = SQL.query(Menu.class)
                .eq(Menu::getAppCode, appCode)
                .eq(Menu::getIsEnabled, true)
                .orderBy(Menu::getSequence);
        return sqlExecutor.queryList(qb);
    }

    /**
     * 按应用查询菜单并限定归属租户（策略配置弹窗用：只展示目标角色租户 + public 共享租户的菜单）
     */
    public List<Menu> queryByAppInTenants(String appCode, Collection<String> tenantCodes) {
        if (CollectionUtils.isEmpty(tenantCodes)) {
            return Collections.emptyList();
        }
        QueryBuilder qb = SQL.query(Menu.class)
                .eq(Menu::getAppCode, appCode)
                .in(Menu::getTenantCode, tenantCodes)
                .orderBy(Menu::getSequence);
        return sqlExecutor.queryList(qb);
    }

    /**
     * 查询全部启用菜单（按应用+排序）
     */
    public List<Menu> queryAllEnabled() {
        QueryBuilder qb = SQL.query(Menu.class)
                .eq(Menu::getIsEnabled, true)
                .orderBy(Menu::getAppCode, Menu::getSequence);
        return sqlExecutor.queryList(qb);
    }

    /**
     * 查询指定归属租户下启用的菜单（显式租户条件，不依赖当前登录上下文）。
     * <p>
     * 用于读取「资源归属租户」的共享菜单：平台内置资源归 {@code default}、共享应用资源归
     * {@code public}，按 {@code sys_menu.tenant_code} 的既定语义对<b>所有租户只读可见</b>
     * （见 {@link Menu#isResourceOwnerTenant()} 与迁移 30 的列注释）。
     * <p>
     * 必须在 {@code TenantUtil.callAsSystem} 中调用：否则行级租户策略会再叠加当前租户条件，
     * 把共享菜单一并过滤掉。
     */
    public List<Menu> queryEnabledByTenants(Collection<String> tenantCodes) {
        if (CollectionUtils.isEmpty(tenantCodes)) {
            return Collections.emptyList();
        }
        QueryBuilder qb = SQL.query(Menu.class)
                .eq(Menu::getIsEnabled, true)
                .in(Menu::getTenantCode, tenantCodes)
                .orderBy(Menu::getAppCode, Menu::getSequence);
        return sqlExecutor.queryList(qb);
    }

    /**
     * 按 resourceCode 集合查询菜单（权限推导时用）
     */
    public List<Menu> queryByResourceCodes(String appCode, Collection<String> resourceCodes) {
        if (CollectionUtils.isEmpty(resourceCodes)) {
            return Collections.emptyList();
        }
        QueryBuilder qb = SQL.query(Menu.class)
                .eq(Menu::getAppCode, appCode)
                .in(Menu::getResourceCode, resourceCodes)
                .eq(Menu::getIsEnabled, true)
                .orderBy(Menu::getSequence);
        return sqlExecutor.queryList(qb);
    }

    /**
     * 菜单列表页查询（管理后台）
     * parentId 留空返回全部菜单（前端构建树形表格），填写时返回该父级下的直接子级
     */
    public List<Menu> queryForAdmin(String appCode, String parentId, String name) {
        QueryBuilder qb = SQL.query(Menu.class).ignoreInvalidDeep(true)
                .eq(Menu::getAppCode, appCode)
                .like(Menu::getName, name);
        if (StringUtils.isNotBlank(parentId)) {
            qb.eq(Menu::getParentId, parentId);
        }
        qb.orderBy(Menu::getSequence);
        return sqlExecutor.queryList(qb);
    }

    /**
     * 按应用查询简化列表（选择上级菜单用）
     */
    public List<Menu> listSimpleByApp(String appCode) {
        QueryBuilder qb = SQL.query(Menu.class).ignoreInvalidDeep(true)
                .eq(Menu::getAppCode, appCode)
                .eq(Menu::getIsEnabled, true)
                .orderBy(Menu::getSequence);
        return sqlExecutor.queryList(qb);
    }

    /**
     * 判断某应用下是否存在指定资源码的 PAGE 菜单（策略保存的资源存在性校验用）。
     * <p>
     * 资源本体可能记在 default（平台应用）/ public（共享应用）等其他租户，而策略主体在业务租户，
     * 故调用方应以 {@code TenantUtil.callAsSystem} 跨租户查询，不能受当前租户上下文限制。
     *
     * @param appCode      应用编码
     * @param resourceCode 资源标识
     * @return true=存在该资源的 PAGE（已登记，非孤儿）
     */
    public boolean existsPageResource(String appCode, String resourceCode) {
        return sqlExecutor.count(SQL.query(Menu.class)
                .eq(Menu::getAppCode, appCode)
                .eq(Menu::getResourceCode, resourceCode)
                .eq(Menu::getType, "PAGE")) > 0;
    }

    /**
     * 统计子菜单数量
     */
    public int countChildren(String parentId) {
        return sqlExecutor.count(SQL.query(Menu.class).eq(Menu::getParentId, parentId));
    }

    /**
     * 按来源和应用删除菜单（子应用注册菜单的全量替换）
     */
    public void deleteByAppAndSource(String appCode, String source) {
        sqlExecutor.delete(SQL.delete(Menu.class)
                .eq(Menu::getAppCode, appCode)
                .eq(Menu::getSource, source));
    }

    /**
     * 应用租户变更时级联同步其下<b>平台菜单</b>的租户。
     * <p>
     * 严格限定 {@code source='PLATFORM'}：低代码同步来的页面菜单记的是「页面租户」（消费方租户），
     * 其表单数据也在该租户的库表中，不能随应用租户一起搬走，否则页面与数据会分属两个租户。
     * <p>
     * Builder API 会自动追加 is_deleted=0（BaseEntity 的 LogicFieldStrategy），已逻辑删除的菜单不迁移；
     * 其复活路径（低代码 upsert / 子应用自注册）都会按应用当前租户写入新行，无需额外处理。
     *
     * @return 受影响行数
     */
    public int updateTenantByApp(String appCode, String tenantCode) {
        return sqlExecutor.update(SQL.update(Menu.class)
                .set(Menu::getTenantCode, tenantCode)
                .eq(Menu::getAppCode, appCode)
                .eq(Menu::getSource, Menu.SOURCE_PLATFORM));
    }

    /**
     * 按外部标识批量查询菜单（低代码菜单同步 upsert 命中判断，自动过滤逻辑删记录）
     */
    public List<Menu> queryByAppAndExternalIds(String appCode, Collection<String> externalIds) {
        if (CollectionUtils.isEmpty(externalIds)) {
            return Collections.emptyList();
        }
        QueryBuilder qb = SQL.query(Menu.class)
                .eq(Menu::getAppCode, appCode)
                .eq(Menu::getSource, Menu.SOURCE_LOWCODE)
                .in(Menu::getExternalId, externalIds);
        return sqlExecutor.queryList(qb);
    }

    /**
     * 查询应用下 DIR/PAGE 菜单（低代码平台选择父级菜单用，不含 BTN）
     */
    public List<Menu> queryDirAndPageByApp(String appCode) {
        QueryBuilder qb = SQL.query(Menu.class)
                .eq(Menu::getAppCode, appCode)
                .eq(Menu::getIsEnabled, true)
                .in(Menu::getType, List.of("DIR", "PAGE"))
                .orderBy(Menu::getSequence);
        return sqlExecutor.queryList(qb);
    }
}
