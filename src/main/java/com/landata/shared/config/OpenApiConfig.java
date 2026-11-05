package com.landata.shared.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {
  public static final String BEARER = "bearerAuth";

  @Bean
  OpenAPI landataOpenApi() {
    return new OpenAPI()
        .info(new Info()
            .title("Landata API")
            .version("v1")
            .description("Multi-tenant BI API. Authorize with the access token from POST /api/v1/auth/login."))
        .components(new Components().addSecuritySchemes(BEARER, new SecurityScheme()
            .type(SecurityScheme.Type.HTTP)
            .scheme("bearer")
            .bearerFormat("JWT")))
        .addSecurityItem(new SecurityRequirement().addList(BEARER));
  }
}
