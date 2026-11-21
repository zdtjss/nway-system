package com.nway.system.tenant.web.model;

import com.nway.system.common.web.PageParam;
import lombok.Data;

@Data
public class TenantQuery extends PageParam {

    private String tenantCode;
    private String tenantName;
    private String status;
}
