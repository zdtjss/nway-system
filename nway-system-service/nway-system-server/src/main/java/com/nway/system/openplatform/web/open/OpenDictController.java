package com.nway.system.openplatform.web.open;

import com.nway.system.metadata.service.DictService;
import com.nway.system.metadata.web.model.DictItemDto;
import com.nway.system.common.web.Result;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 开放平台字典接口。
 *
 * @author nway
 */
@RestController
@RequestMapping("/open/dict")
public class OpenDictController {

    @Autowired
    private DictService dictService;

    /**
     * 按字典编码查询所有启用字典项。
     */
    @GetMapping("items")
    public Result<List<DictItemDto>> items(@RequestParam String dictCode) {
        return Result.ok(dictService.queryByCode(dictCode));
    }
}
