package com.nway.system.file.domain;

import lombok.Data;

/**
 * 短时票据内容（仅存于 Redis，URL 中只出现不透明随机串）。
 *
 * @author nway
 */
@Data
public class FileTicket {

    public static final String PURPOSE_PREVIEW = "preview";

    public static final String PURPOSE_DOWNLOAD = "download";

    private String fileId;

    private String tenantCode;

    private String employeeId;

    private String purpose;
}
