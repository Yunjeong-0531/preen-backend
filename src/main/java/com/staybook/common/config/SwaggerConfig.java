package com.staybook.common.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class SwaggerConfig {

    @Bean
    public OpenAPI openAPI() {
        String jwtSchemeName = "jwtAuth";

        // 1. 헤더에 JWT 토큰을 담는 보안 규칙 정의
        SecurityRequirement securityRequirement = new SecurityRequirement().addList(jwtSchemeName);

        SecurityScheme securityScheme = new SecurityScheme()
                .name(jwtSchemeName)
                .type(SecurityScheme.Type.HTTP)
                .scheme("bearer")
                .bearerFormat("JWT");

        // 2. Swagger UI에 자물쇠 버튼 활성화
        return new OpenAPI()
                .info(new Info()
                        .title("Staybook API 명세서")
                        .description("Staybook 숙소 예약 서비스 API 테스트")
                        .version("v1.0.0"))
                .addSecurityItem(securityRequirement)
                .components(new Components().addSecuritySchemes(jwtSchemeName, securityScheme));
    }
}