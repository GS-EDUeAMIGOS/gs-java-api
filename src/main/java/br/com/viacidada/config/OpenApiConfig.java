package br.com.viacidada.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.servers.Server;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Configuration
public class OpenApiConfig {

    @Value("${viacidada.openapi.azure-url:https://viacidada-3a46802b7054.eastus.cloudapp.azure.com}")
    private String azureServerUrl;

    @Value("${viacidada.openapi.local-url:http://localhost:8080}")
    private String localServerUrl;

    @Bean
    public OpenAPI viaCidadaOpenAPI() {
        final String securitySchemeName = "bearerAuth";
        return new OpenAPI()
                .servers(List.of(
                        new Server().url("/").description("Servidor atual (mesma origem)"),
                        new Server().url(azureServerUrl).description("Producao Azure (HTTPS)"),
                        new Server().url(localServerUrl).description("Desenvolvimento Local (localhost:8080)")
                ))
                .info(new Info()
                        .title("ViaCidada API - Global Solution FIAP CitizenTech")
                        .description("Backend em Spring Boot para mobilidade urbana acessivel, participacao cidada e integracao com SPTrans Olho Vivo.")
                        .version("1.0.0")
                        .contact(new Contact()
                                .name("Equipe ViaCidada - FIAP")
                                .email("contato@viacidada.com.br")))
                .addSecurityItem(new SecurityRequirement().addList(securitySchemeName))
                .components(new Components()
                        .addSecuritySchemes(securitySchemeName,
                                new SecurityScheme()
                                        .name(securitySchemeName)
                                        .type(SecurityScheme.Type.HTTP)
                                        .scheme("bearer")
                                        .bearerFormat("JWT")));
    }
}
