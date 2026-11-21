package com.nway.system.audit.service;

import com.nway.system.tenant.service.TenantNameResolver;
import com.nway.spring.jdbc.pagination.Page;
import com.nway.system.common.web.Result;
import com.nway.system.common.util.ConverterUtil;
import com.nway.system.audit.dao.AuditLogDao;
import com.nway.system.audit.entity.AuditLog;
import com.nway.system.audit.web.model.AuditLogVo;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 审计日志查询服务。
 *
 * @author nway
 */
@Service
public class AuditLogService {

    @Autowired
    private AuditLogDao auditLogDao;

    @Autowired
    private TenantNameResolver tenantNameResolver;

    /**
     * 分页查询审计日志。
     *
     * @return Result.data 包含 data（当前页列表）与 total（总数）
     */
    public Result<Map<String, Object>> queryPage(String operatorId, String operationType, String apiPath,
                                                 LocalDateTime startTime, LocalDateTime endTime,
                                                 int page, int pageSize) {
        Page<AuditLog> pageResult = auditLogDao.queryPage(operatorId, operationType, apiPath,
                startTime, endTime, page, pageSize);
        // 转 VO 并批量填充租户名称
        List<AuditLogVo> voList = ConverterUtil.convert(pageResult.getPageData(), AuditLogVo.class);
        Map<String, String> tenantNameMap = tenantNameResolver.resolveNames(
                voList.stream().map(AuditLogVo::getTenantCode).collect(Collectors.toSet()));
        voList.forEach(vo -> {
            if (StringUtils.isNotBlank(vo.getTenantCode())) {
                vo.setTenantName(tenantNameMap.getOrDefault(vo.getTenantCode(), ""));
            }
        });
        Map<String, Object> data = new HashMap<>(4);
        data.put("data", voList);
        data.put("total", pageResult.getTotal());
        return Result.ok(data);
    }
}
