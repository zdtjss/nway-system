package com.nway.system.metadata.web.model;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * 字典项导入结果
 */
@Data
public class DictImportResult {

    /**
     * 新增条数
     */
    private int inserted;
    /**
     * 更新条数
     */
    private int updated;
    /**
     * 行级校验错误，非空时表示未写入任何数据
     */
    private List<String> errors = new ArrayList<>();
}
