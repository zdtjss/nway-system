package com.nway.system.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * SpringDoc OpenAPI 文档配置。
 * <p>
 * 访问地址：
 * - Swagger UI: /swagger-ui.html
 * - OpenAPI JSON: /v3/api-docs
 */
@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI openAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("nway-system API")
                        .description("统一身份与权限管理平台接口文档")
                        .version("1.0.0"))
                .addSecurityItem(new SecurityRequirement().addList("Bearer Token"))
                .schemaRequirement("Bearer Token", new SecurityScheme()
                        .type(SecurityScheme.Type.HTTP)
                        .scheme("bearer")
                        .bearerFormat("JWT")
                        .in(SecurityScheme.In.HEADER)
                        .name("Authorization"));
    }
}
