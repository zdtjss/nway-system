package com.nway.system.ai.web.model;

import lombok.Data;

import java.util.List;
import java.util.Map;

/**
 * 历史消息视图。
 *
 * @author nway
 */
@Data
public class AiMessageVo {

    private String id;

    private String role;

    private String timestamp;

    private List<Map<String, Object>> blocks;
}
