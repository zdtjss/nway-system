package com.nway.system.file.web.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 文件短时票据。
 *
 * @author nway
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class FileTicketVo {

    /** 不透明随机票据（不是 JWT，不含用户身份明文） */
    private String ticket;

    /** 浏览器直链地址（相对 context-path） */
    private String url;

    /** 有效期（秒） */
    private long expiresIn;
}
