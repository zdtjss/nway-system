package com.nway.system.metadata.service;

import com.nway.system.tenant.service.TenantNameResolver;
import com.nway.spring.jdbc.pagination.Page;
import com.nway.system.common.web.PageVo;
import com.nway.system.common.exception.NwaySystemException;
import com.nway.system.common.util.ConverterUtil;
import com.nway.system.common.tenant.TenantUtil;
import com.nway.system.metadata.dao.SysConfigDao;
import com.nway.system.metadata.entity.SysConfig;
import com.nway.system.metadata.web.model.SysConfigVo;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.stream.Collectors;

@Service
public class SysConfigService {

    @Autowired
    private SysConfigDao sysConfigDao;

    @Autowired
    private TenantNameResolver tenantNameResolver;

    @Transactional(rollbackFor = Exception.class)
    public void save(SysConfig config) {
        if (StringUtils.isBlank(config.getId())) {
            // 新增：显式指定租户时校验写权限（含 public 全局共享配置），避免单租户管理员越权写入
            if (StringUtils.isNotBlank(config.getTenantCode())) {
                TenantUtil.assertAdminCanManageTenant(config.getTenantCode());
            }
        } else {
            // 更新：归属租户不可通过更新接口变更（UPDATE 的 SET 值不受数据层隔离约束）
            SysConfig existing = sysConfigDao.queryById(config.getId());
            if (existing == null) {
                throw new NwaySystemException("配置不存在或无权限修改");
            }
            config.setTenantCode(existing.getTenantCode());
            TenantUtil.assertAdminCanManageTenant(existing.getTenantCode());
        }
        if (sysConfigDao.countByKey(config.getConfigKey(), config.getId()) > 0) {
            throw new NwaySystemException("配置键已存在：" + config.getConfigKey());
        }
        if (StringUtils.isBlank(config.getStatus())) {
            config.setStatus("using");
        }
        if (StringUtils.isBlank(config.getConfigGroup())) {
            config.setConfigGroup("system");
        }
        if (StringUtils.isBlank(config.getId())) {
            sysConfigDao.insert(config);
        } else {
            config.setModifierId(null);
            config.setModifierName(null);
            config.setModifiedTime(null);
            sysConfigDao.updateById(config);
        }
    }

    @Transactional(rollbackFor = Exception.class)
    public void delete(String id) {
        SysConfig config = sysConfigDao.queryById(id);
        if (config == null) {
            throw new NwaySystemException("配置不存在");
        }
        TenantUtil.assertAdminCanManageTenant(config.getTenantCode());
        sysConfigDao.deleteById(id);
    }

    public PageVo<SysConfigVo> queryPage(String configKey, String configName, String configGroup, String status, int page, int pageSize) {
        Page<SysConfig> pageResult = sysConfigDao.queryPage(configKey, configName, configGroup, status, page, pageSize);
        PageVo<SysConfigVo> voPage = ConverterUtil.convert(pageResult, SysConfigVo.class);
        // 批量填充租户名称
        Map<String, String> tenantNameMap = tenantNameResolver.resolveNames(
                voPage.getData().stream().map(SysConfigVo::getTenantCode).collect(Collectors.toSet()));
        voPage.getData().forEach(vo -> {
            if (StringUtils.isNotBlank(vo.getTenantCode())) {
                vo.setTenantName(tenantNameMap.getOrDefault(vo.getTenantCode(), ""));
            }
        });
        return voPage;
    }

    public SysConfig getByKey(String configKey) {
        return sysConfigDao.getByKey(configKey);
    }
}
