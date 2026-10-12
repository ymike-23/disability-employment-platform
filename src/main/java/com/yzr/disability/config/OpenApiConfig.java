package com.yzr.disability.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * OpenAPI（Swagger）接口文档配置。
 *
 * <p>配置 JWT Bearer 认证方案，使 Swagger UI 支持填写 Token 调试受保护接口。
 * 注意：此处不包含任何真实 Token、密码等敏感信息。
 */
@Configuration
public class OpenApiConfig {

    /** 安全方案名称，接口上通过 {@code @SecurityRequirement(name = ...)} 引用 */
    public static final String SECURITY_SCHEME_NAME = "bearerAuth";

    @Bean
    public OpenAPI disabilityOpenAPI() {
        Info info = new Info()
                .title("残疾人培训就业服务管理平台 API")
                .version("v1.0.0")
                .description("后端接口文档。调试受保护接口时，请先调用登录接口获取 JWT，"
                        + "再点击页面右上角 Authorize 按钮填入 Token 后即可访问。");

        SecurityScheme bearerScheme = new SecurityScheme()
                .name(SECURITY_SCHEME_NAME)
                .type(SecurityScheme.Type.HTTP)
                .scheme("bearer")
                .bearerFormat("JWT")
                .description("请输入登录接口返回的 JWT Token（无需自行添加 Bearer 前缀）");

        return new OpenAPI()
                .info(info)
                .addSecurityItem(new SecurityRequirement().addList(SECURITY_SCHEME_NAME))
                .components(new Components().addSecuritySchemes(SECURITY_SCHEME_NAME, bearerScheme));
    }
}