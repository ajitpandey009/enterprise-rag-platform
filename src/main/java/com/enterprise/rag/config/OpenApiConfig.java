package com.enterprise.rag.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * OpenAPI / Swagger Configuration — Provides interactive API documentation
 * with JWT authentication support in the Swagger UI.
 */
@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI enterpriseRagOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("Enterprise RAG Platform API")
                        .description("""
                            Production-grade Retrieval-Augmented Generation (RAG) platform
                            for enterprise knowledge management. Upload documents, ask questions,
                            and get AI-powered answers grounded in your organization's knowledge base.
                            """)
                        .version("1.0.0")
                        .contact(new Contact()
                                .name("Enterprise RAG Team")
                                .email("rag-platform@enterprise.com")))
                .addSecurityItem(new SecurityRequirement().addList("Bearer Authentication"))
                .components(new Components()
                        .addSecuritySchemes("Bearer Authentication",
                                new SecurityScheme()
                                        .type(SecurityScheme.Type.HTTP)
                                        .bearerFormat("JWT")
                                        .scheme("bearer")
                                        .description("Enter your JWT token")));
    }
}
