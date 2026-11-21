package com.nway.system.metadata.web.model;

import com.fasterxml.jackson.annotation.JsonFormat;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
public class DictTypeDto {

    private String id;

    private String tenantCode;

    private String tenantName;

    @NotBlank(message = "字典编码不能为空")
    private String dictCode;

    @NotBlank(message = "字典名称不能为空")
    private String dictName;

    private String remark;

    /**
     * 字段定义列表（前端直接以数组形式交互，后端落库前序列化为 JSON）
     */
    private List<DictFieldDef> fieldDefs;

    private String status;

    private String modifierId;

    private String modifierName;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private LocalDateTime modifiedTime;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private LocalDateTime createTime;
}
