package com.nway.system.metadata.service;

import com.alibaba.fastjson2.JSON;
import com.nway.system.metadata.service.DictImportParser;
import com.nway.system.metadata.web.model.DictFieldDef;
import com.nway.system.metadata.web.model.DictImportResult;
import com.nway.system.metadata.web.model.DictItemDto;
import com.nway.system.metadata.web.model.DictTypeDto;
import com.nway.system.tenant.web.model.TenantDto;
import com.nway.system.common.exception.NwaySystemException;
import com.nway.system.common.util.ConverterUtil;
import com.nway.system.common.tenant.TenantUtil;
import com.nway.system.metadata.dao.DictItemDao;
import com.nway.system.metadata.dao.DictTypeDao;
import com.nway.system.metadata.entity.DictItem;
import com.nway.system.metadata.entity.DictType;
import com.nway.system.tenant.dao.TenantDao;
import com.nway.system.tenant.entity.Tenant;
import com.nway.system.metadata.web.model.DictItemQuery;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * 字典管理服务：传统字典项模式与自定义字段模式由字典类别是否定义字段决定
 */
@Service
@RequiredArgsConstructor
public class DictService {

    /**
     * 字段编码合法格式，防SQL注入（编码会拼接进 JSON_EXTRACT 路径）
     */
    private static final Pattern FIELD_CODE_PATTERN = Pattern.compile("^[a-zA-Z0-9_]+$");

    private final DictTypeDao dictTypeDao;
    private final DictItemDao dictItemDao;
    private final TenantDao tenantDao;

    // ====== 字典类别 ======

    public List<DictTypeDto> queryType(String keyword, String tenantCode) {
        // 显式校验租户参数，避免越权参数静默返回空列表
        List<String> readableTenants = TenantUtil.currentTenantCodesForIsolation();
        if (StringUtils.isNotBlank(tenantCode) && readableTenants != null && !readableTenants.contains(tenantCode)) {
            throw new NwaySystemException("无权限查看该租户数据：" + tenantCode);
        }
        List<DictType> list = dictTypeDao.query(keyword, tenantCode);
        List<DictTypeDto> dtoList = ConverterUtil.convert2(list, DictTypeDto.class,
                (src, t) -> t.setFieldDefs(parseFieldDefs(src.getFieldDefs())));
        fillTenantNames(dtoList);
        return dtoList;
    }

    /**
     * 字典查询的租户下拉：仅返回当前用户可读的租户（超管为全部启用租户）
     */
    public List<TenantDto> queryTenantOptions() {
        List<Tenant> tenants;
        if (TenantUtil.isSuperAdmin()) {
            tenants = tenantDao.queryAll();
        } else {
            List<String> codes = TenantUtil.currentTenantCodesForIsolation();
            tenants = codes == null ? List.of() : tenantDao.queryByTenantCode(codes).stream()
                    .filter(t -> "using".equalsIgnoreCase(t.getStatus()))
                    .toList();
        }
        return ConverterUtil.convert(tenants, TenantDto.class);
    }

    public DictTypeDto getTypeById(String id) {
        DictType entity = dictTypeDao.queryById(id);
        if (entity == null) {
            return null;
        }
        DictTypeDto dto = ConverterUtil.convert(entity, DictTypeDto.class);
        dto.setFieldDefs(parseFieldDefs(entity.getFieldDefs()));
        return dto;
    }

    @Transactional(rollbackFor = Exception.class)
    public void saveType(DictTypeDto dto) {
        // 编码唯一性校验
        if (dictTypeDao.countByCode(dto.getDictCode(), dto.getId()) > 0) {
            throw new NwaySystemException("字典编码已存在：" + dto.getDictCode());
        }
        // 写守卫：显式指定租户时校验管理权（含 public 全局共享字典）
        if (StringUtils.isNotBlank(dto.getTenantCode())) {
            TenantUtil.assertAdminCanManageTenant(dto.getTenantCode());
        }
        DictType entity = ConverterUtil.convert(dto, DictType.class);
        entity.setFieldDefs(serializeFieldDefs(dto.getFieldDefs()));
        if (StringUtils.isBlank(entity.getStatus())) {
            entity.setStatus("using");
        }
        if (StringUtils.isBlank(entity.getId())) {
            dictTypeDao.insert(entity);
        } else {
            entity.setModifierId(null);
            entity.setModifierName(null);
            entity.setModifiedTime(null);
            dictTypeDao.updateById(entity);
        }
    }

    @Transactional(rollbackFor = Exception.class)
    public void deleteType(String id) {
        DictType entity = dictTypeDao.queryById(id);
        if (entity == null) {
            throw new NwaySystemException("字典类别不存在");
        }
        if (dictItemDao.countByTypeId(id) > 0) {
            throw new NwaySystemException("该字典下存在字典项，不能删除");
        }
        TenantUtil.assertAdminCanManageTenant(entity.getTenantCode());
        dictTypeDao.deleteById(id);
    }

    // ====== 字典项 ======

    public List<DictItemDto> queryItem(DictItemQuery query) {
        if (StringUtils.isBlank(query.getDictTypeId())) {
            return List.of();
        }
        DictType type = dictTypeDao.queryById(query.getDictTypeId());
        if (type == null) {
            return List.of();
        }
        List<DictFieldDef> defs = parseFieldDefs(type.getFieldDefs());
        Map<String, String> likeFilters = new LinkedHashMap<>();
        Map<String, String> exactFilters = new LinkedHashMap<>();
        if (query.getFieldFilters() != null) {
            query.getFieldFilters().forEach((code, value) -> {
                if (StringUtils.isBlank(value)) {
                    return;
                }
                validateFilterFieldCode(code, defs);
                String fieldType = fieldTypeOf(code, defs);
                if ("string".equals(fieldType)) {
                    likeFilters.put(code, value);
                } else {
                    exactFilters.put(code, value);
                }
            });
        }
        List<DictItem> list = dictItemDao.queryByTypeId(query.getDictTypeId(), query.getStatus(), likeFilters, exactFilters);
        return ConverterUtil.convert2(list, DictItemDto.class,
                (src, t) -> t.setItemValue(parseItemValue(src.getItemValue())));
    }

    public DictItemDto getItemById(String id) {
        DictItem entity = dictItemDao.queryById(id);
        if (entity == null) {
            return null;
        }
        DictItemDto dto = ConverterUtil.convert(entity, DictItemDto.class);
        dto.setItemValue(parseItemValue(entity.getItemValue()));
        return dto;
    }

    @Transactional(rollbackFor = Exception.class)
    public void saveItem(DictItemDto dto) {
        if (StringUtils.isBlank(dto.getDictTypeId())) {
            throw new NwaySystemException("字典类别id不能为空");
        }
        DictType type = dictTypeDao.queryById(dto.getDictTypeId());
        if (type == null) {
            throw new NwaySystemException("字典类别不存在");
        }
        // 字典项随字典类别归属同一租户（含 public 共享字典），写守卫与类别一致
        TenantUtil.assertAdminCanManageTenant(type.getTenantCode());
        if (StringUtils.isBlank(type.getFieldDefs())) {
            // 传统字典项模式：key 必填
            Object key = dto.getItemValue() == null ? null : dto.getItemValue().get("key");
            if (key == null || StringUtils.isBlank(String.valueOf(key))) {
                throw new NwaySystemException("字典项Key不能为空");
            }
        } else {
            // 自定义字段模式：必填字段校验
            validateItemValue(dto.getItemValue(), type.getFieldDefs());
        }

        DictItem entity = ConverterUtil.convert(dto, DictItem.class);
        entity.setTenantCode(type.getTenantCode());
        entity.setItemValue(serializeItemValue(dto.getItemValue()));
        if (StringUtils.isBlank(entity.getStatus())) {
            entity.setStatus("using");
        }
        if (entity.getSort() == null) {
            entity.setSort(0);
        }
        if (StringUtils.isBlank(entity.getId())) {
            dictItemDao.insert(entity);
        } else {
            entity.setModifierId(null);
            entity.setModifierName(null);
            entity.setModifiedTime(null);
            dictItemDao.updateById(entity);
        }
    }

    @Transactional(rollbackFor = Exception.class)
    public void deleteItem(String id) {
        DictItem entity = dictItemDao.queryById(id);
        if (entity != null) {
            TenantUtil.assertAdminCanManageTenant(entity.getTenantCode());
        }
        dictItemDao.deleteById(id);
    }

    /**
     * 批量导入字典项：按 Key（传统模式）或指定字段编码（多字段模式）判断新增或更新。
     * <p>
     * 所有行校验通过后才落库；任一行校验失败则整体不导入并返回行级错误。
     *
     * @param dictTypeId 字典类别id
     * @param matchField 多字段模式的匹配字段编码，为空时取第一个字段
     * @param file       Excel（.xlsx/.xls）或 CSV 文件
     */
    @Transactional(rollbackFor = Exception.class)
    public DictImportResult importItems(String dictTypeId, String matchField, MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new NwaySystemException("请选择要导入的文件");
        }
        if (StringUtils.isBlank(dictTypeId)) {
            throw new NwaySystemException("字典类别id不能为空");
        }
        DictType type = dictTypeDao.queryById(dictTypeId);
        if (type == null) {
            throw new NwaySystemException("字典类别不存在");
        }
        TenantUtil.assertAdminCanManageTenant(type.getTenantCode());
        List<DictFieldDef> defs = parseFieldDefs(type.getFieldDefs());
        String matchCode = defs.isEmpty() ? DictImportParser.MATCH_KEY : resolveMatchField(defs, matchField);

        DictImportParser.ParseResult parsed;
        try {
            parsed = DictImportParser.parse(file, defs, matchCode);
        } catch (IOException e) {
            throw new NwaySystemException("导入文件读取失败：" + e.getMessage(), e);
        }
        DictImportResult result = new DictImportResult();
        if (parsed.hasErrors()) {
            result.setErrors(parsed.errors());
            return result;
        }

        // 文件内相同匹配值视为同一项，后出现的行覆盖前面的字段
        Map<String, DictImportParser.ImportRow> rows = new LinkedHashMap<>();
        for (DictImportParser.ImportRow row : parsed.rows()) {
            String key = matchKey(row.itemValue().get(matchCode));
            DictImportParser.ImportRow previous = rows.get(key);
            if (previous == null) {
                rows.put(key, row);
                continue;
            }
            Map<String, Object> mergedValue = new LinkedHashMap<>(previous.itemValue());
            mergedValue.putAll(row.itemValue());
            rows.put(key, new DictImportParser.ImportRow(row.rowNum(), mergedValue,
                    row.sort() == null ? previous.sort() : row.sort(),
                    row.status() == null ? previous.status() : row.status()));
        }

        Map<String, DictItem> existing = new HashMap<>();
        for (DictItem item : dictItemDao.queryByTypeId(dictTypeId, null, null, null)) {
            String key = matchKey(parseItemValue(item.getItemValue()).get(matchCode));
            if (!key.isEmpty()) {
                existing.putIfAbsent(key, item);
            }
        }

        List<DictItem> inserts = new ArrayList<>();
        List<DictItem> updates = new ArrayList<>();
        rows.forEach((key, row) -> {
            DictItem existingItem = existing.get(key);
            if (existingItem == null) {
                DictItem entity = new DictItem();
                entity.setDictTypeId(dictTypeId);
                entity.setTenantCode(type.getTenantCode());
                entity.setItemValue(serializeItemValue(row.itemValue()));
                entity.setSort(row.sort() == null ? 0 : row.sort());
                entity.setStatus(row.status() == null ? "using" : row.status());
                inserts.add(entity);
                return;
            }
            Map<String, Object> mergedValue = parseItemValue(existingItem.getItemValue());
            mergedValue.putAll(row.itemValue());
            DictItem entity = new DictItem();
            entity.setId(existingItem.getId());
            entity.setDictTypeId(dictTypeId);
            entity.setTenantCode(type.getTenantCode());
            entity.setItemValue(serializeItemValue(mergedValue));
            entity.setSort(row.sort() == null ? existingItem.getSort() : row.sort());
            entity.setStatus(row.status() == null ? existingItem.getStatus() : row.status());
            entity.setModifierId(null);
            entity.setModifierName(null);
            entity.setModifiedTime(null);
            updates.add(entity);
        });

        if (!inserts.isEmpty()) {
            dictItemDao.batchInsert(inserts);
        }
        if (!updates.isEmpty()) {
            dictItemDao.batchUpdateById(updates);
        }
        result.setInserted(inserts.size());
        result.setUpdated(updates.size());
        return result;
    }

    /**
     * 通用查询：按字典编码查询所有启用字典项，供业务方调用
     */
    public List<DictItemDto> queryByCode(String dictCode) {
        DictType type = dictTypeDao.queryByCode(dictCode);
        if (type == null) {
            return List.of();
        }
        List<DictItem> list = dictItemDao.queryByTypeId(type.getId(), "using", null, null);
        return ConverterUtil.convert2(list, DictItemDto.class,
                (src, t) -> t.setItemValue(parseItemValue(src.getItemValue())));
    }

    // ====== 工具方法 ======

    private List<DictFieldDef> parseFieldDefs(String json) {
        if (StringUtils.isBlank(json)) {
            return Collections.emptyList();
        }
        return JSON.parseArray(json, DictFieldDef.class);
    }

    private String serializeFieldDefs(List<DictFieldDef> fieldDefs) {
        if (fieldDefs == null || fieldDefs.isEmpty()) {
            return null;
        }
        return JSON.toJSONString(fieldDefs);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> parseItemValue(String json) {
        if (StringUtils.isBlank(json)) {
            return Collections.emptyMap();
        }
        return JSON.parseObject(json, Map.class);
    }

    private String serializeItemValue(Map<String, Object> itemValue) {
        if (itemValue == null || itemValue.isEmpty()) {
            return null;
        }
        return JSON.toJSONString(itemValue);
    }

    /**
     * 校验过滤字段编码：格式合法且属于当前字典类别（传统模式仅允许保留编码 key/value）
     */
    private void validateFilterFieldCode(String code, List<DictFieldDef> defs) {
        if (code == null || !FIELD_CODE_PATTERN.matcher(code).matches()) {
            throw new NwaySystemException("非法的过滤字段编码：" + code);
        }
        if (defs == null || defs.isEmpty()) {
            if (!"key".equals(code) && !"value".equals(code)) {
                throw new NwaySystemException("传统字典项模式仅支持按 key/value 过滤：" + code);
            }
            return;
        }
        if (defs.stream().noneMatch(d -> code.equals(d.getCode()))) {
            throw new NwaySystemException("过滤字段不属于该字典类别：" + code);
        }
        // 仅允许配置为查询条件的字段参与过滤
        if (defs.stream().noneMatch(d -> code.equals(d.getCode()) && Boolean.TRUE.equals(d.getSearchable()))) {
            throw new NwaySystemException("字段未配置为查询条件：" + code);
        }
    }

    /**
     * 获取字段类型，传统模式保留编码 key/value 视为 string
     */
    private String fieldTypeOf(String code, List<DictFieldDef> defs) {
        if (defs == null || defs.isEmpty()) {
            return "string";
        }
        return defs.stream()
                .filter(d -> code.equals(d.getCode()))
                .map(d -> d.getType() == null ? "string" : d.getType())
                .findFirst()
                .orElse("string");
    }

    /**
     * 按字段定义校验字典项值：必填字段不能为空
     */
    private void validateItemValue(Map<String, Object> itemValue, String fieldDefsJson) {
        if (StringUtils.isBlank(fieldDefsJson)) {
            return;
        }
        List<DictFieldDef> defs = JSON.parseArray(fieldDefsJson, DictFieldDef.class);
        if (defs == null || defs.isEmpty()) {
            return;
        }
        Map<String, Object> value = itemValue == null ? Collections.emptyMap() : itemValue;
        for (DictFieldDef def : defs) {
            if (Boolean.TRUE.equals(def.getRequired())) {
                Object v = value.get(def.getCode());
                if (v == null || (v instanceof String && StringUtils.isBlank((String) v))) {
                    throw new NwaySystemException("字段[" + def.getName() + "]不能为空");
                }
            }
        }
    }

    /**
     * 解析多字段模式的匹配字段：未指定时取第一个字段，且必须属于当前字典类别
     */
    private String resolveMatchField(List<DictFieldDef> defs, String matchField) {
        String code = StringUtils.isBlank(matchField) ? defs.get(0).getCode() : matchField;
        if (defs.stream().noneMatch(d -> code.equals(d.getCode()))) {
            throw new NwaySystemException("匹配字段不属于该字典类别：" + code);
        }
        return code;
    }

    /**
     * 匹配值归一化：数字去掉多余小数位，避免 1 与 1.0 被识别为两条数据
     */
    private String matchKey(Object value) {
        if (value == null) {
            return "";
        }
        if (value instanceof Number number) {
            try {
                return new BigDecimal(number.toString()).stripTrailingZeros().toPlainString();
            } catch (NumberFormatException e) {
                return String.valueOf(number).trim();
            }
        }
        return String.valueOf(value).trim();
    }

    private void fillTenantNames(List<DictTypeDto> list) {
        if (list == null || list.isEmpty()) {
            return;
        }
        Set<String> tenantCodes = list.stream()
                .map(DictTypeDto::getTenantCode)
                .filter(StringUtils::isNotBlank)
                .collect(Collectors.toSet());
        if (tenantCodes.isEmpty()) {
            return;
        }
        Map<String, String> nameMap = new HashMap<>();
        for (Tenant t : tenantDao.queryByTenantCode(tenantCodes)) {
            nameMap.put(t.getTenantCode(), t.getTenantName());
        }
        for (DictTypeDto dto : list) {
            if (StringUtils.isNotBlank(dto.getTenantCode())) {
                dto.setTenantName(nameMap.get(dto.getTenantCode()));
            }
        }
    }
}
