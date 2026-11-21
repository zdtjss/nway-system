package com.nway.system.tenant.web.model;

import com.fasterxml.jackson.annotation.JsonFormat;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.time.LocalDate;

@Data
public class TenantDto {

    private String id;

    @NotBlank(message = "租户编码不能为空")
    private String tenantCode;

    @NotBlank(message = "租户名称不能为空")
    private String tenantName;

    private String status;

    @JsonFormat(pattern = "yyyy-MM-dd", timezone = "GMT+8")
    private LocalDate expireDate;

    private String remark;
}
