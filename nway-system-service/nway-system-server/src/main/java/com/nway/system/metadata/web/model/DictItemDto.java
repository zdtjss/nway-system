package com.nway.system.metadata.web.model;

import com.fasterxml.jackson.annotation.JsonFormat;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.Map;

@Data
public class DictItemDto {

    private String id;

    @NotBlank(message = "字典类别id不能为空")
    private String dictTypeId;

    /**
     * 字典项值：传统模式 {"key": "...", "value": "..."}，自定义字段模式 {fieldCode: value}
     */
    private Map<String, Object> itemValue;

    private Integer sort;

    private String status;

    private String modifierId;

    private String modifierName;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private LocalDateTime modifiedTime;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private LocalDateTime createTime;
}
