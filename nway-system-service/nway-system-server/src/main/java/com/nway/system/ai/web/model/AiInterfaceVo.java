package com.nway.system.ai.web.model;

import com.nway.system.ai.domain.AiInterfaceConfig;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 管理端接口配置视图（配置 + 行标识/审计信息）。
 *
 * @author nway
 */
@Data
@EqualsAndHashCode(callSuper = false)
public class AiInterfaceVo extends AiInterfaceConfig {

    private String id;

    /** 本接口直接授权的有效主体数（不含 ai:* 分组授权） */
    private Integer grantCount;

    /** “全部 AI 接口”（ai:*）授权的有效主体数 */
    private Integer allGrantCount;

    private LocalDateTime createTime;

    private LocalDateTime modifiedTime;
}
