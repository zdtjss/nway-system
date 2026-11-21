package com.nway.system.metadata.web.admin;

import com.nway.system.authorization.annotation.RequiresPermission;

import com.nway.system.metadata.service.DictService;
import com.nway.system.common.security.PublicApi;
import com.nway.system.metadata.web.model.DictImportResult;
import com.nway.system.metadata.web.model.DictItemDto;
import com.nway.system.metadata.web.model.DictTypeDto;
import com.nway.system.tenant.web.model.TenantDto;
import com.nway.system.common.web.Result;
import com.nway.system.metadata.web.model.DictItemQuery;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * 字典管理（通用多字段类字典）
 */
@RequiresPermission(resource = "dict")
@RestController
@RequestMapping("dict")
public class DictController {

    @Autowired
    private DictService dictService;

    // ====== 字典类别 ======

    @GetMapping("queryType")
    public Result<List<DictTypeDto>> queryType(String keyword, String tenantCode) {
        return Result.ok(dictService.queryType(keyword, tenantCode));
    }

    /**
     * 字典查询的租户下拉：仅返回当前用户权限范围内的租户，
     * 避免业务页面直接调用 tenant/queryAll（租户管理权限）导致 403。
     */
    @GetMapping("tenantOptions")
    public Result<List<TenantDto>> tenantOptions() {
        return Result.ok(dictService.queryTenantOptions());
    }

    @GetMapping("getTypeById")
    public Result<DictTypeDto> getTypeById(String id) {
        return Result.ok(dictService.getTypeById(id));
    }

    @PostMapping("saveType")
    public Result<Void> saveType(@Validated @RequestBody DictTypeDto dto) {
        dictService.saveType(dto);
        return Result.ok();
    }

    @PostMapping("deleteType")
    public Result<Void> deleteType(String id) {
        dictService.deleteType(id);
        return Result.ok();
    }

    // ====== 字典项 ======

    @GetMapping("queryItem")
    public Result<List<DictItemDto>> queryItem(DictItemQuery query) {
        return Result.ok(dictService.queryItem(query));
    }

    @GetMapping("getItemById")
    public Result<DictItemDto> getItemById(String id) {
        return Result.ok(dictService.getItemById(id));
    }

    @PostMapping("saveItem")
    public Result<Void> saveItem(@Validated @RequestBody DictItemDto dto) {
        dictService.saveItem(dto);
        return Result.ok();
    }

    /**
     * 批量导入字典项：支持 Excel（.xlsx/.xls）与 CSV。
     * 传统字典按 Key 判断新增/更新；多字段字典按 matchField 指定字段判断。
     */
    @PostMapping("importItems")
    public Result<DictImportResult> importItems(@RequestParam String dictTypeId,
                                                @RequestParam(required = false) String matchField,
                                                @RequestParam("file") MultipartFile file) {
        return Result.ok(dictService.importItems(dictTypeId, matchField, file));
    }

    @PostMapping("deleteItem")
    public Result<Void> deleteItem(String id) {
        dictService.deleteItem(id);
        return Result.ok();
    }

    /**
     * 通用查询：按字典编码查询所有启用字典项，仅返回字典项数据内容（itemValue），供业务方调用
     */
    @PublicApi(reason = "通用字典查询，登录用户均可访问")
    @GetMapping("queryByCode")
    public Result<List<Map<String, Object>>> queryByCode(String dictCode) {
        List<Map<String, Object>> items = dictService.queryByCode(dictCode).stream()
                .map(DictItemDto::getItemValue)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
        return Result.ok(items);
    }
}
