package com.nway.system.openplatform.service;

import com.nway.system.tenant.service.TenantNameResolver;
import com.nway.system.common.util.ConverterUtil;
import com.nway.system.openplatform.dao.SysOpenApiLogDao;
import com.nway.system.openplatform.entity.SysOpenApiLog;
import com.nway.system.openplatform.web.model.OpenApiLogVo;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 开放平台调用日志服务。
 * <p>
 * 负责调用日志记录（异步）和统计查询。
 *
 * @author nway
 */
@Slf4j
@Service
public class OpenApiLogService {

    @Autowired
    private SysOpenApiLogDao sysOpenApiLogDao;

    @Autowired
    private TenantNameResolver tenantNameResolver;

    /**
     * 异步记录调用日志。
     *
     * @param appCode        应用编码
     * @param apiPath        接口路径
     * @param httpMethod     HTTP 方法
     * @param requestParams  请求参数（JSON 字符串）
     * @param responseStatus 响应状态码
     * @param durationMs     耗时（毫秒）
     * @param ip             调用方 IP
     * @param employeeId     代理用户工号（可为 null）
     * @param errorMsg       错误信息（可为 null）
     * @param tenantCode   租户编码
     */
    @Async
    public void logAsync(String appCode, String apiPath, String httpMethod, String requestParams,
                          int responseStatus, long durationMs, String ip,
                          String employeeId, String errorMsg, String tenantCode) {
        try {
            SysOpenApiLog logEntity = new SysOpenApiLog();
            logEntity.setAppCode(appCode);
            logEntity.setApiPath(apiPath);
            logEntity.setHttpMethod(httpMethod);
            logEntity.setRequestParams(requestParams);
            logEntity.setResponseStatus(responseStatus);
            logEntity.setDurationMs((int) durationMs);
            logEntity.setIp(ip);
            logEntity.setEmployeeId(employeeId);
            logEntity.setErrorMsg(errorMsg);
            logEntity.setTenantCode(tenantCode);
            sysOpenApiLogDao.insert(logEntity);
        } catch (Exception e) {
            log.error("记录开放平台调用日志失败: appCode={}, apiPath={}", appCode, apiPath, e);
        }
    }

    /**
     * 按应用查询调用日志。
     */
    public List<OpenApiLogVo> queryByApp(String appCode) {
        return toVoList(sysOpenApiLogDao.queryByApp(appCode));
    }

    /**
     * 查询全部调用日志。
     */
    public List<OpenApiLogVo> queryAll() {
        return toVoList(sysOpenApiLogDao.queryAll());
    }

    /**
     * 日志实体转 VO 并批量填充租户名称
     */
    private List<OpenApiLogVo> toVoList(List<SysOpenApiLog> logs) {
        if (logs == null || logs.isEmpty()) {
            return List.of();
        }
        List<OpenApiLogVo> voList = ConverterUtil.convert(logs, OpenApiLogVo.class);
        Map<String, String> tenantNameMap = tenantNameResolver.resolveNames(
                voList.stream().map(OpenApiLogVo::getTenantCode).collect(Collectors.toSet()));
        voList.forEach(vo -> {
            if (StringUtils.isNotBlank(vo.getTenantCode())) {
                vo.setTenantName(tenantNameMap.getOrDefault(vo.getTenantCode(), ""));
            }
        });
        return voList;
    }

    /**
     * 按应用维度统计调用量和成功率。
     *
     * @return 统计列表，每项包含 appCode, totalCalls, successCount, avgDurationMs
     */
    public List<Map<String, Object>> statsByApp() {
        List<SysOpenApiLog> all = sysOpenApiLogDao.queryAll();
        return all.stream()
                .collect(Collectors.groupingBy(SysOpenApiLog::getAppCode))
                .entrySet().stream()
                .map(entry -> {
                    List<SysOpenApiLog> logs = entry.getValue();
                    long total = logs.size();
                    long success = logs.stream().filter(l -> l.getResponseStatus() != null && l.getResponseStatus() == 200).count();
                    double avgDuration = logs.stream()
                            .filter(l -> l.getDurationMs() != null)
                            .mapToInt(SysOpenApiLog::getDurationMs)
                            .average()
                            .orElse(0);
                    // 应用归属租户（取首条非空租户编码）
                    String tenantCode = logs.stream()
                            .map(SysOpenApiLog::getTenantCode)
                            .filter(StringUtils::isNotBlank)
                            .findFirst().orElse(null);
                    String tenantName = tenantCode != null
                            ? tenantNameResolver.resolveNames(List.of(tenantCode)).getOrDefault(tenantCode, "")
                            : "";
                    Map<String, Object> stat = new java.util.HashMap<>();
                    stat.put("appCode", entry.getKey());
                    stat.put("tenantName", tenantName);
                    stat.put("totalCalls", total);
                    stat.put("successCount", success);
                    stat.put("successRate", total > 0 ? (double) success / total : 0);
                    stat.put("avgDurationMs", (int) avgDuration);
                    return stat;
                })
                .toList();
    }

    /**
     * 按接口维度统计调用量。
     */
    public List<Map<String, Object>> statsByApi() {
        List<SysOpenApiLog> all = sysOpenApiLogDao.queryAll();
        return all.stream()
                .collect(Collectors.groupingBy(l -> l.getApiPath() + ":" + l.getHttpMethod()))
                .entrySet().stream()
                .map(entry -> {
                    List<SysOpenApiLog> logs = entry.getValue();
                    long total = logs.size();
                    long success = logs.stream().filter(l -> l.getResponseStatus() != null && l.getResponseStatus() == 200).count();
                    double avgDuration = logs.stream()
                            .filter(l -> l.getDurationMs() != null)
                            .mapToInt(SysOpenApiLog::getDurationMs)
                            .average()
                            .orElse(0);
                    Map<String, Object> stat = new java.util.HashMap<>();
                    stat.put("api", entry.getKey());
                    stat.put("totalCalls", total);
                    stat.put("successCount", success);
                    stat.put("successRate", total > 0 ? (double) success / total : 0);
                    stat.put("avgDurationMs", (int) avgDuration);
                    return stat;
                })
                .toList();
    }
}
