package com.nway.system.ai.entity;

import com.nway.spring.jdbc.annotation.Table;
import com.nway.system.common.persistence.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * AI 接口元数据：业务接口是数据，不是工具。
 * <p>
 * 字段 Schema、调用配置以 JSON 文本存储，运行时由通用工具读取与调用。
 *
 * @author nway
 */
@Data
@Table("ai_interface")
@EqualsAndHashCode(callSuper = false)
public class AiInterface extends BaseEntity {

    /** 全局唯一接口编码 */
    private String interfaceCode;

    /** 接口名称 */
    private String name;

    /** 面向模型的场景/动作描述 */
    private String description;

    /** 目标应用，对应 sys_app.app_code */
    private String appCode;

    /** GET/POST/PUT/DELETE */
    private String httpMethod;

    /** 相对路径，支持 {pathParam} */
    private String path;

    /** 默认 application/json */
    private String contentType;

    /** READ/WRITE/HIGH_RISK */
    private String riskLevel;

    /** 写操作必须为 true */
    private Boolean confirmRequired;

    /** 入参字段 JSON 数组 */
    private String inputSchema;

    /** 出参字段 JSON 数组 */
    private String outputSchema;

    /** body 模板、响应提取、固定请求头、超时 */
    private String invokeConfig;

    /** 名称+描述+关键词+字段标签，保存时生成 */
    private String searchText;

    /** 同义词/简称/业务黑话 */
    private String searchKeywords;

    /** 用户表达示例 JSON 数组 */
    private String examples;

    /** AUTO/MERGED/HUMAN_REVIEWED */
    private String metadataConfidence;

    /** DRAFT/PUBLISHED/OFFLINE */
    private String status;

    /** PUBLIC：所有具备 AI 对话权限的用户可调用；RESTRICTED：需按 sys_grant 显式授权 execute */
    private String accessMode;

    /** 版本号 */
    private Integer version;
}
